package com.peerlock.system.adb

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLSocket

private const val TAG = "AdbPairClient"

private const val kCurrentKeyHeaderVersion = 1.toByte()
private const val kMinSupportedKeyHeaderVersion = 1.toByte()
private const val kMaxSupportedKeyHeaderVersion = 1.toByte()
private const val kMaxPeerInfoSize = 8192
private const val kMaxPayloadSize = kMaxPeerInfoSize * 2
private const val kExportedKeyLabel = "adb-label"
private const val kExportedKeySize = 64
private const val kPairingPacketHeaderSize = 6

private class PeerInfo(val type: Byte, data: ByteArray) {
    val data = ByteArray(kMaxPeerInfoSize - 1)
    init {
        data.copyInto(this.data, 0, 0, data.size.coerceAtMost(kMaxPeerInfoSize - 1))
    }
    fun writeTo(buffer: ByteBuffer) { buffer.put(type); buffer.put(data) }
    companion object {
        fun readFrom(buffer: ByteBuffer): PeerInfo {
            val type = buffer.get()
            val data = ByteArray(kMaxPeerInfoSize - 1)
            buffer.get(data)
            return PeerInfo(type, data)
        }
    }
}

private class PairingPacketHeader(val version: Byte, val type: Byte, val payload: Int) {
    companion object {
        const val SPAKE2_MSG = 0.toByte()
        const val PEER_INFO = 1.toByte()

        fun readFrom(buffer: ByteBuffer): PairingPacketHeader? {
            val version = buffer.get()
            val type = buffer.get()
            val payload = buffer.int
            if (version < kMinSupportedKeyHeaderVersion || version > kMaxSupportedKeyHeaderVersion) return null
            if (type != SPAKE2_MSG && type != PEER_INFO) return null
            if (payload <= 0 || payload > kMaxPayloadSize) return null
            return PairingPacketHeader(version, type, payload)
        }
    }
    fun writeTo(buffer: ByteBuffer) {
        buffer.put(version); buffer.put(type); buffer.putInt(payload)
    }
}

@RequiresApi(Build.VERSION_CODES.R)
internal class AdbPairingClient(
    private val host: String,
    private val port: Int,
    private val pairCode: String,
    private val key: AdbKey,
) : Closeable {

    private lateinit var socket: Socket
    private lateinit var input: DataInputStream
    private lateinit var output: DataOutputStream
    private lateinit var spake2: Ed25519Spake2
    private val peerInfo = PeerInfo(0, key.adbPublicKey)
    private var state = State.Ready

    private enum class State { Ready, ExchangingMsgs, ExchangingPeerInfo, Stopped }

    fun start(): Boolean {
        setupTlsConnection()
        state = State.ExchangingMsgs
        if (!doExchangeMsgs()) { state = State.Stopped; return false }
        state = State.ExchangingPeerInfo
        if (!doExchangePeerInfo()) { state = State.Stopped; return false }
        state = State.Stopped
        return true
    }

    private fun setupTlsConnection() {
        socket = Socket(host, port)
        socket.tcpNoDelay = true

        val sslSocket = key.sslContext.socketFactory.createSocket(socket, host, port, true) as SSLSocket
        sslSocket.startHandshake()
        Log.d(TAG, "TLS handshake succeeded")

        input = DataInputStream(sslSocket.inputStream)
        output = DataOutputStream(sslSocket.outputStream)

        // Derive password: pairCode + TLS exported keying material
        val pairCodeBytes = pairCode.toByteArray()
        val keyMaterial = javax.net.ssl.SSLSession::class.java.let {
            // Use Conscrypt.exportKeyingMaterial if available, otherwise derive from session
            try {
                val conscrypt = Class.forName("org.conscrypt.Conscrypt")
                val method = conscrypt.getMethod("exportKeyingMaterial", javax.net.ssl.SSLSocket::class.java, String::class.java, ByteArray::class.java, Int::class.javaPrimitiveType)
                method.invoke(null, sslSocket, kExportedKeyLabel, null, kExportedKeySize) as ByteArray
            } catch (_: Exception) {
                // Fallback: use session ID as key material
                val sessionId = sslSocket.session.id
                ByteArray(kExportedKeySize).also { buf ->
                    System.arraycopy(sessionId, 0, buf, 0, sessionId.size.coerceAtMost(kExportedKeySize))
                }
            }
        }
        val password = ByteArray(pairCodeBytes.size + keyMaterial.size)
        pairCodeBytes.copyInto(password)
        keyMaterial.copyInto(password, pairCodeBytes.size)

        spake2 = Ed25519Spake2.create(true, password)
    }

    private fun doExchangeMsgs(): Boolean {
        val msg = spake2.getPublicMessage()
        writeHeader(PairingPacketHeader(kCurrentKeyHeaderVersion, PairingPacketHeader.SPAKE2_MSG, msg.size), msg)

        val theirHeader = readHeader() ?: return false
        if (theirHeader.type != PairingPacketHeader.SPAKE2_MSG) return false
        val theirMsg = ByteArray(theirHeader.payload)
        input.readFully(theirMsg)

        spake2.processPeerMessage(theirMsg)
        return true
    }

    private fun doExchangePeerInfo(): Boolean {
        val buf = ByteBuffer.allocate(kMaxPeerInfoSize).order(ByteOrder.BIG_ENDIAN)
        peerInfo.writeTo(buf)
        val encrypted = spake2.encrypt(buf.array()) ?: return false

        writeHeader(PairingPacketHeader(kCurrentKeyHeaderVersion, PairingPacketHeader.PEER_INFO, encrypted.size), encrypted)

        val theirHeader = readHeader() ?: return false
        if (theirHeader.type != PairingPacketHeader.PEER_INFO) return false
        val theirData = ByteArray(theirHeader.payload)
        input.readFully(theirData)

        val decrypted = spake2.decrypt(theirData) ?: throw AdbInvalidPairingCodeException()
        if (decrypted.size != kMaxPeerInfoSize) {
            Log.e(TAG, "PeerInfo size mismatch: ${decrypted.size} vs $kMaxPeerInfoSize")
            return false
        }
        PeerInfo.readFrom(ByteBuffer.wrap(decrypted))
        return true
    }

    private fun readHeader(): PairingPacketHeader? {
        val bytes = ByteArray(kPairingPacketHeaderSize)
        input.readFully(bytes)
        return PairingPacketHeader.readFrom(ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN))
    }

    private fun writeHeader(header: PairingPacketHeader, payload: ByteArray) {
        val buf = ByteBuffer.allocate(kPairingPacketHeaderSize).order(ByteOrder.BIG_ENDIAN)
        header.writeTo(buf)
        output.write(buf.array())
        output.write(payload)
    }

    override fun close() {
        try { input.close() } catch (_: Throwable) {}
        try { output.close() } catch (_: Throwable) {}
        try { socket.close() } catch (_: Exception) {}
    }
}
