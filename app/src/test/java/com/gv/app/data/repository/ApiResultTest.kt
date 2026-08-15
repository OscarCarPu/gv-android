package com.gv.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These used to assert the outbox's retry/dead-letter classification. With no write queue
 * there is nothing to retry, so what matters instead is that an offline refusal is
 * distinguishable from a real server error — the UI phrases them differently.
 */
class ApiResultTest {

    @Test
    fun `offline refusal is flagged and carries the standard message`() {
        val failure = ApiResult.offline()
        assertTrue(failure.offline)
        assertEquals(ApiResult.OFFLINE_MESSAGE, failure.message)
        assertEquals(null, failure.code)
    }

    @Test
    fun `a server error is not an offline refusal`() {
        val failure = ApiResult.Failure("bad", code = 422)
        assertFalse(failure.offline)
        assertEquals(422, failure.code)
    }

    @Test
    fun `a network error without a status is still not an offline refusal`() {
        // The gate reports "offline"; an unreachable-but-connected server reports the failure
        // verbatim. Conflating them would tell the user to check a connection they have.
        val failure = ApiResult.Failure("timeout", code = null)
        assertFalse(failure.offline)
    }

    @Test
    fun `success is success`() {
        assertTrue(ApiResult.Success(42).isSuccess)
        assertFalse(ApiResult.Failure("nope").isSuccess)
    }
}
