package com.gv.app.data.auth

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 TOTP, so the app can answer its own 2FA challenge instead of asking the user to
 * read a code off another device.
 *
 * Hand-rolled because it is ~40 lines and the alternative is a dependency: HMAC-SHA1 over the
 * big-endian time counter, then the standard dynamic truncation. Base32 decoding is included
 * because that is the format every authenticator app hands out secrets in.
 */
object Totp {

    private const val PERIOD_SECONDS = 30L
    private const val DIGITS = 6
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /**
     * Current code for [base32Secret], or null if the secret is blank or not valid base32.
     * [atEpochSeconds] is injectable so the time window can be tested deterministically.
     */
    fun generate(base32Secret: String, atEpochSeconds: Long = System.currentTimeMillis() / 1000): String? {
        val key = decodeBase32(base32Secret) ?: return null
        if (key.isEmpty()) return null

        val counter = atEpochSeconds / PERIOD_SECONDS
        val message = ByteArray(8)
        var value = counter
        for (i in 7 downTo 0) {
            message[i] = (value and 0xFF).toByte()
            value = value shr 8
        }

        val hash = try {
            Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(key, "HmacSHA1")) }.doFinal(message)
        } catch (_: Exception) {
            return null
        }

        // Dynamic truncation: the low nibble of the last byte picks the 4-byte window.
        val offset = (hash[hash.size - 1].toInt() and 0x0F)
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)

        val otp = binary % 1_000_000
        return otp.toString().padStart(DIGITS, '0')
    }

    /** Lenient base32: ignores padding, spaces and case, the way secrets are usually pasted. */
    private fun decodeBase32(input: String): ByteArray? {
        val clean = input.trim().replace(" ", "").replace("-", "").trimEnd('=').uppercase()
        if (clean.isEmpty()) return null

        var buffer = 0
        var bitsLeft = 0
        val out = ArrayList<Byte>(clean.length * 5 / 8 + 1)
        for (c in clean) {
            val index = BASE32_ALPHABET.indexOf(c)
            if (index < 0) return null
            buffer = (buffer shl 5) or index
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }
}
