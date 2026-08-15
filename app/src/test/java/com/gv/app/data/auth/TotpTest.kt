package com.gv.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * RFC 6238 test vectors for HMAC-SHA1.
 *
 * The published vectors are 8-digit; the 6-digit codes below are their low six digits, which
 * is what a standard authenticator emits. The shared secret is the ASCII string
 * "12345678901234567890", i.e. base32 `GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ`.
 *
 * Worth having as a test rather than a one-off check: a TOTP bug is invisible until it locks
 * the app out of its own API, and only at the 30-second boundary that happened to be wrong.
 */
class TotpTest {

    private val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test
    fun `matches RFC 6238 vectors`() {
        assertEquals("287082", Totp.generate(secret, atEpochSeconds = 59))
        assertEquals("081804", Totp.generate(secret, atEpochSeconds = 1_111_111_109))
        assertEquals("050471", Totp.generate(secret, atEpochSeconds = 1_111_111_111))
        assertEquals("005924", Totp.generate(secret, atEpochSeconds = 1_234_567_890))
        assertEquals("279037", Totp.generate(secret, atEpochSeconds = 2_000_000_000))
    }

    @Test
    fun `code is stable within a 30 second window and changes across it`() {
        val early = Totp.generate(secret, atEpochSeconds = 1_234_567_890)
        val sameWindow = Totp.generate(secret, atEpochSeconds = 1_234_567_890 + 5)
        val nextWindow = Totp.generate(secret, atEpochSeconds = 1_234_567_890 + 30)

        assertEquals(early, sameWindow)
        assert(early != nextWindow) { "expected a different code in the next 30s window" }
    }

    @Test
    fun `accepts secrets the way people paste them`() {
        val canonical = Totp.generate(secret, atEpochSeconds = 59)
        assertEquals(canonical, Totp.generate(secret.lowercase(), atEpochSeconds = 59))
        assertEquals(canonical, Totp.generate("$secret====", atEpochSeconds = 59))
        assertEquals(canonical, Totp.generate("GEZD GNBV GY3T QOJQ GEZD GNBV GY3T QOJQ", atEpochSeconds = 59))
    }

    @Test
    fun `returns null rather than a wrong code for unusable secrets`() {
        assertNull(Totp.generate(""))
        assertNull(Totp.generate("   "))
        // 0, 1, 8 and 9 are not in the base32 alphabet.
        assertNull(Totp.generate("01890189"))
    }
}
