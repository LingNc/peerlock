package com.peerlock.domain.pairing

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class PairingModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `PairingRequest 序列化往返一致`() {
        val request = PairingRequest(
            id = UUID.randomUUID().toString(),
            pub = "BKyDx9example",
            name = "Pixel 7"
        )
        val encoded = json.encodeToString(PairingRequest.serializer(), request)
        val decoded = json.decodeFromString(PairingRequest.serializer(), encoded)
        assertEquals(request, decoded)
    }

    @Test
    fun `PairingResponse 序列化往返一致`() {
        val response = PairingResponse(pub = "pubKey", signPub = "signPubKey", data = "base64encodeddata")
        val encoded = json.encodeToString(PairingResponse.serializer(), response)
        val decoded = json.decodeFromString(PairingResponse.serializer(), encoded)
        assertEquals(response, decoded)
    }

    @Test
    fun `PairingPayload 序列化往返一致`() {
        val payload = PairingPayload(
            id = UUID.randomUUID().toString(),
            seeds = mapOf(
                "setting" to "c2VlZDE=",
                "unlock" to "c2VlZDI=",
                "destroy" to "c2VlZDM="
            ),
            pub = "BKxYz7example",
            name = "Galaxy S24"
        )
        val encoded = json.encodeToString(PairingPayload.serializer(), payload)
        val decoded = json.decodeFromString(PairingPayload.serializer(), encoded)
        assertEquals(payload, decoded)
    }

    @Test
    fun `EnvelopeCodec 编码解码往返一致`() {
        val ciphertext = ByteArray(100) { it.toByte() }
        val iv = ByteArray(12) { (it + 50).toByte() }
        val signature = ByteArray(64) { (it + 100).toByte() }

        val envelope = EnvelopeCodec.encode(ciphertext, iv, signature)
        val (decodedCiphertext, decodedIv, decodedSignature) = EnvelopeCodec.decode(envelope)

        assertArrayEquals(ciphertext, decodedCiphertext)
        assertArrayEquals(iv, decodedIv)
        assertArrayEquals(signature, decodedSignature)
    }
}
