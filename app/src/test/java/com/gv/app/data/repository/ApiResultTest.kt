package com.gv.app.data.repository

import com.gv.app.data.sync.SyncOutcome
import com.gv.app.data.sync.toSyncOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiResultTest {

    @Test
    fun `4xx is a client error and dead-letters`() {
        val failure = ApiResult.Failure("bad", code = 422)
        assertTrue(failure.isClientError)
        assertFalse(failure.isTransient)
        assertTrue(failure.toSyncOutcome() is SyncOutcome.DeadLetter)
    }

    @Test
    fun `no status code is transient and retries`() {
        val failure = ApiResult.Failure("network", code = null)
        assertFalse(failure.isClientError)
        assertTrue(failure.isTransient)
        assertEquals(SyncOutcome.Retry, failure.toSyncOutcome())
    }

    @Test
    fun `5xx is transient and retries`() {
        val failure = ApiResult.Failure("server", code = 503)
        assertFalse(failure.isClientError)
        assertTrue(failure.isTransient)
        assertEquals(SyncOutcome.Retry, failure.toSyncOutcome())
    }
}
