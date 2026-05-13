package com.peerlock.system.adb

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * SPAKE2 over Ed25519 for ADB pairing.
 * Pure Kotlin implementation of Ed25519 curve arithmetic.
 */
internal class Ed25519Spake2 private constructor(password: ByteArray) {

    // Ed25519 field prime: 2^255 - 19
    private val p = BigInteger("57896044618658097711785492504343953926634992332820282019728792003956564819949")

    // Curve parameter d = -121665 * modInverse(121666)
    private val d = BigInteger("37095705934669439343138083508754565189542113879843219016388785533085940283555")

    // Base point y-coordinate (for decoding)
    private val baseY = BigInteger("46316835694926478169428394003475163141307993866256225615783033603165251855960")

    // Random scalar
    private val scalar = ByteArray(32)

    // Our public message
    private val myMsg: ByteArray

    // Shared key (derived after processing peer message)
    private var sharedKey: ByteArray? = null
    private var encSequence = 0L
    private var decSequence = 0L

    // Password for later use
    private val initPassword = password.copyOf()

    init {
        SecureRandom().nextBytes(scalar)
        // Clamp Ed25519 scalar
        scalar[0] = (scalar[0].toInt() and 0xF8).toByte()
        scalar[31] = (scalar[31].toInt() and 0x7F).toByte()
        scalar[31] = (scalar[31].toInt() or 0x40).toByte()

        // Generate our public message: passwordScalar*BasePoint + randomScalar*BasePoint
        val passwordScalar = passwordToScalar(password)
        val sumScalar = addScalarsModL(passwordScalar, scalar)
        myMsg = scalarMulBase(sumScalar)
    }

    fun getPublicMessage(): ByteArray = myMsg.copyOf()

    fun processPeerMessage(peerMsg: ByteArray): ByteArray {
        val passwordScalar = passwordToScalar(initPassword)

        // Decode peer's message point
        val (peerX, peerY) = decodePoint(peerMsg)

        // Subtract passwordScalar*BasePoint from peer message
        val passwordPoint = scalarMulBase(passwordScalar)
        val negPasswordPoint = negatePoint(passwordPoint)
        val peerPointBytes = encodePoint(peerX, peerY)
        val intermediate = pointAdd(peerPointBytes, negPasswordPoint)

        // Multiply by our scalar: sharedPoint = scalar * intermediate
        val sharedPoint = scalarMul(intermediate, scalar)

        // HKDF to derive AES-128 key
        sharedKey = hkdfDerive(sharedPoint)
        return sharedKey!!
    }

    fun encrypt(plaintext: ByteArray): ByteArray? {
        val key = sharedKey ?: return null
        return AesGcmHelper.encrypt(key, encSequence++, plaintext)
    }

    fun decrypt(ciphertext: ByteArray): ByteArray? {
        val key = sharedKey ?: return null
        return AesGcmHelper.decrypt(key, decSequence++, ciphertext)
    }

    // --- Ed25519 Point Arithmetic ---

    /** Encode a point to 32 bytes (compressed: x with sign bit of y) */
    private fun encodePoint(x: BigInteger, y: BigInteger): ByteArray {
        val result = ByteArray(32)
        val xBytes = x.toByteArray()
        // Little-endian
        for (i in xBytes.indices) {
            val idx = xBytes.size - 1 - i
            if (idx in 0 until 32) result[idx] = xBytes[i]
        }
        // Set sign bit of y in MSB of last byte
        if (y.testBit(0)) {
            result[31] = (result[31].toInt() or 0x80).toByte()
        }
        return result
    }

    /** Decode a 32-byte compressed point to (x, y) */
    private fun decodePoint(data: ByteArray): Pair<BigInteger, BigInteger> {
        val signBit = (data[31].toInt() and 0x80) != 0
        val xBytes = data.copyOf()
        xBytes[31] = (xBytes[31].toInt() and 0x7F).toByte()
        // Little-endian to BigInteger
        val reversed = xBytes.reversedArray()
        val x = BigInteger(1, reversed)

        // Solve for y: y^2 = (1 + x^2) / (1 - d*x^2)  [twisted Edwards: -x^2 + y^2 = 1 + d*x^2*y^2]
        val x2 = x.multiply(x).mod(p)
        val num = BigInteger.ONE.add(x2).mod(p)
        val den = BigInteger.ONE.subtract(d.multiply(x2)).mod(p)
        val y2 = num.multiply(den.modInverse(p)).mod(p)
        val y = modSqrt(y2, p)
        val finalY = if (y.testBit(0) != signBit) p.subtract(y) else y
        return Pair(x, finalY)
    }

    /** Scalar multiplication of base point: scalar * G */
    private fun scalarMulBase(scalarBytes: ByteArray): ByteArray {
        val k = BigInteger(1, scalarBytes)
        val baseX = BigInteger("15112221349535400772501151409588531511454012693041857206046113283949847762202")
        val (rx, ry) = scalarMulAffine(baseX, baseY, k)
        return encodePoint(rx, ry)
    }

    /** Scalar multiplication: k * point */
    private fun scalarMul(point: ByteArray, scalarBytes: ByteArray): ByteArray {
        val k = BigInteger(1, scalarBytes)
        val (px, py) = decodePoint(point)
        val (rx, ry) = scalarMulAffine(px, py, k)
        return encodePoint(rx, ry)
    }

    /** Point addition: p1 + p2 */
    private fun pointAdd(p1: ByteArray, p2: ByteArray): ByteArray {
        val (x1, y1) = decodePoint(p1)
        val (x2, y2) = decodePoint(p2)
        val (rx, ry) = pointAddAffine(x1, y1, x2, y2)
        return encodePoint(rx, ry)
    }

    /** Negate a point */
    private fun negatePoint(point: ByteArray): ByteArray {
        val (x, y) = decodePoint(point)
        return encodePoint(p.subtract(x).mod(p), y)
    }

    /** Affine point addition on twisted Edwards curve */
    private fun pointAddAffine(
        x1: BigInteger, y1: BigInteger,
        x2: BigInteger, y2: BigInteger,
    ): Pair<BigInteger, BigInteger> {
        // (x1*y2 + y1*x2) / (1 + d*x1*x2*y1*y2)
        // (y1*y2 + x1*x2) / (1 - d*x1*x2*y1*y2)
        val x1y2 = x1.multiply(y2).mod(p)
        val y1x2 = y1.multiply(x2).mod(p)
        val y1y2 = y1.multiply(y2).mod(p)
        val x1x2 = x1.multiply(x2).mod(p)
        val dx1x2y1y2 = d.multiply(x1x2).mod(p).multiply(y1y2).mod(p)

        val xNum = x1y2.add(y1x2).mod(p)
        val xDen = BigInteger.ONE.add(dx1x2y1y2).mod(p)
        val yNum = y1y2.add(x1x2).mod(p)
        val yDen = BigInteger.ONE.subtract(dx1x2y1y2).mod(p)

        val rx = xNum.multiply(xDen.modInverse(p)).mod(p)
        val ry = yNum.multiply(yDen.modInverse(p)).mod(p)
        return Pair(rx, ry)
    }

    /** Scalar multiplication using double-and-add */
    private fun scalarMulAffine(
        px: BigInteger, py: BigInteger, k: BigInteger,
    ): Pair<BigInteger, BigInteger> {
        var rx = BigInteger.ZERO
        var ry = BigInteger.ONE
        var qx = px
        var qy = py
        var bits = k

        while (bits > BigInteger.ZERO) {
            if (bits.testBit(0)) {
                if (rx == BigInteger.ZERO && ry == BigInteger.ONE) {
                    rx = qx; ry = qy
                } else {
                    val (nx, ny) = pointAddAffine(rx, ry, qx, qy)
                    rx = nx; ry = ny
                }
            }
            val (nx, ny) = pointAddAffine(qx, qy, qx, qy) // double
            qx = nx; qy = ny
            bits = bits.shiftRight(1)
        }
        return Pair(rx, ry)
    }

    /** Modular square root for p ≡ 5 (mod 8) */
    private fun modSqrt(a: BigInteger, modulus: BigInteger): BigInteger {
        if (a == BigInteger.ZERO) return BigInteger.ZERO
        // p ≡ 5 (mod 8): use candidate = a^((p+3)/8) then check
        val exp = modulus.add(BigInteger.valueOf(3)).divide(BigInteger.valueOf(8))
        val candidate = a.modPow(exp, modulus)
        if (candidate.multiply(candidate).mod(modulus) == a.mod(modulus)) return candidate
        // Try candidate * sqrt(-1)
        val i = BigInteger.valueOf(2).modPow(modulus.subtract(BigInteger.ONE).divide(BigInteger.valueOf(4)), modulus)
        return candidate.multiply(i).mod(modulus)
    }

    /** Add two scalars mod L (Ed25519 group order) */
    private fun addScalarsModL(a: ByteArray, b: ByteArray): ByteArray {
        val l = BigInteger("7237005577332262213973186563042994240857116359379907606001950938285454250989")
        val sum = BigInteger(1, a).add(BigInteger(1, b)).mod(l)
        val result = ByteArray(32)
        val bytes = sum.toByteArray()
        for (i in bytes.indices) {
            val idx = bytes.size - 1 - i
            if (idx in 0 until 32) result[idx] = bytes[i]
        }
        return result
    }

    companion object {
        private const val HKDF_LABEL = "adb pairing_auth aes-128-gcm key"
        private const val HKDF_KEY_LENGTH = 16

        private fun passwordToScalar(password: ByteArray): ByteArray {
            val hash = MessageDigest.getInstance("SHA-512").digest(password)
            val scalar = ByteArray(32)
            System.arraycopy(hash, 0, scalar, 0, 32)
            scalar[0] = (scalar[0].toInt() and 0xF8).toByte()
            scalar[31] = (scalar[31].toInt() and 0x7F).toByte()
            scalar[31] = (scalar[31].toInt() or 0x40).toByte()
            return scalar
        }

        private fun hkdfDerive(inputKeyMaterial: ByteArray): ByteArray {
            val output = ByteArray(HKDF_KEY_LENGTH)
            val hkdf = HKDFBytesGenerator(SHA256Digest())
            hkdf.init(HKDFParameters(inputKeyMaterial, null, HKDF_LABEL.toByteArray()))
            hkdf.generateBytes(output, 0, HKDF_KEY_LENGTH)
            return output
        }

        fun create(isClient: Boolean, password: ByteArray): Ed25519Spake2 {
            return Ed25519Spake2(password)
        }
    }
}

/**
 * AES-128-GCM with sequence-number nonce.
 */
internal object AesGcmHelper {
    private const val TAG_SIZE_BITS = 128
    private const val NONCE_SIZE = 12

    fun encrypt(key: ByteArray, sequence: Long, plaintext: ByteArray): ByteArray? {
        return try {
            val nonce = sequenceToNonce(sequence)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE_BITS, nonce))
            cipher.doFinal(plaintext)
        } catch (_: Exception) { null }
    }

    fun decrypt(key: ByteArray, sequence: Long, ciphertext: ByteArray): ByteArray? {
        return try {
            val nonce = sequenceToNonce(sequence)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE_BITS, nonce))
            cipher.doFinal(ciphertext)
        } catch (_: Exception) { null }
    }

    private fun sequenceToNonce(sequence: Long): ByteArray {
        val nonce = ByteArray(NONCE_SIZE)
        for (i in 0 until 8) nonce[i] = ((sequence shr (i * 8)) and 0xFF).toByte()
        return nonce
    }
}
