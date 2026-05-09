package com.peerlock.data.qr

import com.peerlock.domain.pairing.PairingRequest
import com.peerlock.domain.pairing.PairingResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class QrCodecTest {

    @Test
    fun `配对请求编码解码往返一致`() {
        val request = PairingRequest(
            id = UUID.randomUUID().toString(),
            pub = "BKyDx9examplePublicKey",
            name = "Pixel 7"
        )
        val encoded = QrCodec.encodeRequest(request)
        assertTrue(encoded.startsWith("PL:req:"))

        val decoded = QrCodec.decodeRequest(encoded)
        assertEquals(request, decoded)
    }

    @Test
    fun `配对响应编码解码往返一致`() {
        val response = PairingResponse(pub = "pubKey", signPub = "signPubKey", data = "YWJjZGVmZ2hpams=")
        val encoded = QrCodec.encodeResponse(response)
        assertTrue(encoded.startsWith("PL:resp:"))

        val decoded = QrCodec.decodeResponse(encoded)
        assertEquals(response, decoded)
    }

    @Test
    fun `自动检测请求类型`() {
        val request = PairingRequest(id = "test", pub = "pub", name = "dev")
        val encoded = QrCodec.encodeRequest(request)
        val decoded = QrCodec.decode(encoded)
        assertTrue(decoded is PairingRequest)
    }

    @Test
    fun `自动检测响应类型`() {
        val response = PairingResponse(pub = "pub", signPub = "signPub", data = "data")
        val encoded = QrCodec.encodeResponse(response)
        val decoded = QrCodec.decode(encoded)
        assertTrue(decoded is PairingResponse)
    }

    @Test
    fun `未知格式应抛出异常`() {
        assertThrows<IllegalArgumentException> {
            QrCodec.decode("unknown:format")
        }
    }

    @Test
    fun `生成 BitMatrix 不为空`() {
        val request = PairingRequest(id = "test", pub = "pub", name = "dev")
        val content = QrCodec.encodeRequest(request)
        val matrix = QrCodec.toBitMatrix(content, 256)
        assertTrue(matrix.width > 0)
        assertTrue(matrix.height > 0)
    }
}
