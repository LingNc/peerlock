package com.peerlock.data.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.peerlock.domain.pairing.PairingRequest
import com.peerlock.domain.pairing.PairingResponse
import kotlinx.serialization.json.Json
import java.util.Base64
import java.util.EnumMap

/**
 * 二维码编解码器。
 * 将配对请求/响应序列化为可扫描的字符串，以及反向解码。
 * 位图渲染在 UI 层完成（需要 Android Bitmap API）。
 */
object QrCodec {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 将配对请求编码为 QR 码内容字符串。
     * 格式: "PL:req:" + Base64(JSON)
     */
    fun encodeRequest(request: PairingRequest): String {
        val jsonString = json.encodeToString(PairingRequest.serializer(), request)
        val encoded = Base64.getEncoder().withoutPadding().encodeToString(jsonString.toByteArray())
        return "PL:req:$encoded"
    }

    /**
     * 将配对响应编码为 QR 码内容字符串。
     * 格式: "PL:resp:" + Base64(JSON)
     */
    fun encodeResponse(response: PairingResponse): String {
        val jsonString = json.encodeToString(PairingResponse.serializer(), response)
        val encoded = Base64.getEncoder().withoutPadding().encodeToString(jsonString.toByteArray())
        return "PL:resp:$encoded"
    }

    /**
     * 从 QR 码内容解码为配对请求。
     */
    fun decodeRequest(content: String): PairingRequest {
        require(content.startsWith("PL:req:")) { "不是 PeerLock 配对请求码" }
        val base64 = content.removePrefix("PL:req:")
        val jsonString = String(Base64.getDecoder().decode(base64))
        return json.decodeFromString(PairingRequest.serializer(), jsonString)
    }

    /**
     * 从 QR 码内容解码为配对响应。
     */
    fun decodeResponse(content: String): PairingResponse {
        require(content.startsWith("PL:resp:")) { "不是 PeerLock 配对响应码" }
        val base64 = content.removePrefix("PL:resp:")
        val jsonString = String(Base64.getDecoder().decode(base64))
        return json.decodeFromString(PairingResponse.serializer(), jsonString)
    }

    /**
     * 自动检测 QR 内容类型并解码。
     */
    fun decode(content: String): Any {
        return when {
            content.startsWith("PL:req:") -> decodeRequest(content)
            content.startsWith("PL:resp:") -> decodeResponse(content)
            else -> throw IllegalArgumentException("未知的 PeerLock 二维码格式")
        }
    }

    /**
     * 将字符串内容渲染为 QR 码的 BitMatrix。
     * 位图转换在 UI 层完成。
     */
    fun toBitMatrix(content: String, size: Int = 512): BitMatrix {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
        hints[EncodeHintType.MARGIN] = 1
        return QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    }
}
