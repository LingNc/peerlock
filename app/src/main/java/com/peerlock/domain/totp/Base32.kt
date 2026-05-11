package com.peerlock.domain.totp

/**
 * Base32 编码工具（RFC 4648）。
 * 用于生成 otpauth URI 中的 secret 参数。
 */
object Base32 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(data: ByteArray): String {
        val result = StringBuilder()
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0
            val b3 = if (i + 3 < data.size) data[i + 3].toInt() and 0xFF else 0
            val b4 = if (i + 4 < data.size) data[i + 4].toInt() and 0xFF else 0

            result.append(ALPHABET[(b0 shr 3) and 0x1F])
            result.append(ALPHABET[((b0 shl 2) or (b1 shr 6)) and 0x1F])
            if (i + 1 < data.size) result.append(ALPHABET[(b1 shr 1) and 0x1F])
            if (i + 1 < data.size) result.append(ALPHABET[((b1 shl 4) or (b2 shr 4)) and 0x1F])
            if (i + 2 < data.size) result.append(ALPHABET[((b2 shl 1) or (b3 shr 7)) and 0x1F])
            if (i + 3 < data.size) result.append(ALPHABET[(b3 shr 2) and 0x1F])
            if (i + 3 < data.size) result.append(ALPHABET[((b3 shl 3) or (b4 shr 5)) and 0x1F])
            if (i + 4 < data.size) result.append(ALPHABET[b4 and 0x1F])

            i += 5
        }
        return result.toString()
    }
}
