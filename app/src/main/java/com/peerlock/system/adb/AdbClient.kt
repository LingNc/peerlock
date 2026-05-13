package com.peerlock.system.adb

import android.util.Log
import com.peerlock.system.adb.AdbProtocol.A_AUTH
import com.peerlock.system.adb.AdbProtocol.A_CLSE
import com.peerlock.system.adb.AdbProtocol.A_CNXN
import com.peerlock.system.adb.AdbProtocol.A_MAXDATA
import com.peerlock.system.adb.AdbProtocol.A_OKAY
import com.peerlock.system.adb.AdbProtocol.A_OPEN
import com.peerlock.system.adb.AdbProtocol.A_STLS
import com.peerlock.system.adb.AdbProtocol.A_STLS_VERSION
import com.peerlock.system.adb.AdbProtocol.A_VERSION
import com.peerlock.system.adb.AdbProtocol.A_WRTE
import com.peerlock.system.adb.AdbProtocol.ADB_AUTH_RSAPUBLICKEY
import com.peerlock.system.adb.AdbProtocol.ADB_AUTH_SIGNATURE
import com.peerlock.system.adb.AdbProtocol.ADB_AUTH_TOKEN
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLSocket

private const val TAG = "AdbClient"

internal class AdbClient(
    private val host: String,
    private val port: Int,
    private val key: AdbKey,
) : Closeable {

    private lateinit var socket: Socket
    private lateinit var plainIn: DataInputStream
    private lateinit var plainOut: DataOutputStream
    private var useTls = false
    private lateinit var tlsSocket: SSLSocket
    private lateinit var tlsIn: DataInputStream
    private lateinit var tlsOut: DataOutputStream

    private val input get() = if (useTls) tlsIn else plainIn
    private val output get() = if (useTls) tlsOut else plainOut

    fun connect() {
        socket = Socket(host, port)
        socket.tcpNoDelay = true
        plainIn = DataInputStream(socket.getInputStream())
        plainOut = DataOutputStream(socket.getOutputStream())

        write(A_CNXN, A_VERSION, A_MAXDATA, "host::")

        var msg = read()
        if (msg.command == A_STLS) {
            write(A_STLS, A_STLS_VERSION, 0)

            val sslSocket = key.sslContext.socketFactory.createSocket(socket, host, port, true) as SSLSocket
            sslSocket.startHandshake()
            Log.d(TAG, "TLS handshake succeeded")

            tlsIn = DataInputStream(sslSocket.inputStream)
            tlsOut = DataOutputStream(sslSocket.outputStream)
            tlsSocket = sslSocket
            useTls = true

            msg = read()
        } else if (msg.command == A_AUTH) {
            if (msg.arg0 != ADB_AUTH_TOKEN) throw AdbProtocolException("Expected A_AUTH token")
            write(A_AUTH, ADB_AUTH_SIGNATURE, 0, key.sign(msg.data))
            msg = read()
            if (msg.command != A_CNXN) {
                write(A_AUTH, ADB_AUTH_RSAPUBLICKEY, 0, key.adbPublicKey)
                msg = read()
            }
        }

        if (msg.command != A_CNXN) throw AdbProtocolException("Expected A_CNXN, got ${msg.command}")
    }

    fun shellCommand(command: String, listener: ((ByteArray) -> Unit)? = null): String {
        val localId = 1
        write(A_OPEN, localId, 0, "shell:$command")

        val output = StringBuilder()
        var msg = read()
        when (msg.command) {
            A_OKAY -> {
                while (true) {
                    msg = read()
                    val remoteId = msg.arg0
                    when (msg.command) {
                        A_WRTE -> {
                            if (msg.data_length > 0 && msg.data != null) {
                                val text = String(msg.data)
                                output.append(text)
                                listener?.invoke(msg.data)
                            }
                            write(A_OKAY, localId, remoteId)
                        }
                        A_CLSE -> {
                            write(A_CLSE, localId, remoteId)
                            break
                        }
                        else -> throw AdbProtocolException("Unexpected command ${msg.command}")
                    }
                }
            }
            A_CLSE -> write(A_CLSE, localId, msg.arg0)
            else -> throw AdbProtocolException("Expected A_OKAY or A_CLSE")
        }
        return output.toString()
    }

    private fun write(command: Int, arg0: Int, arg1: Int, data: ByteArray? = null) =
        write(AdbMessage(command, arg0, arg1, data))

    private fun write(command: Int, arg0: Int, arg1: Int, data: String) =
        write(AdbMessage(command, arg0, arg1, data))

    private fun write(msg: AdbMessage) {
        output.write(msg.toByteArray())
        output.flush()
    }

    private fun read(): AdbMessage {
        val buf = ByteBuffer.allocate(AdbMessage.HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN)
        input.readFully(buf.array(), 0, AdbMessage.HEADER_LENGTH)
        val command = buf.int; val arg0 = buf.int; val arg1 = buf.int
        val dataLength = buf.int; val checksum = buf.int; val magic = buf.int
        val data = if (dataLength > 0) ByteArray(dataLength).also { input.readFully(it) } else null
        return AdbMessage(command, arg0, arg1, dataLength, checksum, magic, data).also { it.validateOrThrow() }
    }

    override fun close() {
        try { plainIn.close() } catch (_: Throwable) {}
        try { plainOut.close() } catch (_: Throwable) {}
        try { socket.close() } catch (_: Exception) {}
        if (useTls) {
            try { tlsIn.close() } catch (_: Throwable) {}
            try { tlsOut.close() } catch (_: Throwable) {}
            try { tlsSocket.close() } catch (_: Exception) {}
        }
    }
}
