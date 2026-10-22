package com.gv.app.data.repository

import com.gv.app.domain.model.MergeStatus
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class MergePendingTest {

    @Before
    fun reset() = MergePending.clear()

    @After
    fun cleanUp() = MergePending.clear()

    private fun refused(json: String, status: Int = 503): Response<String> =
        Response.error(status, json.toResponseBody("application/json".toMediaType()))

    @Test
    fun `merge_pending code on a 503 is recognised`() {
        val failure = ApiResult.Failure("restoring", code = 503, errorCode = ApiResult.CODE_MERGE_PENDING)
        assertTrue(failure.isMergePending)
        assertFalse(failure.isUnavailableOnFailover)
    }

    @Test
    fun `a plain 503 is not merge pending`() {
        assertFalse(ApiResult.Failure("down", code = 503).isMergePending)
    }

    @Test
    fun `an answer carrying merge_pending raises the hold`() = runBlocking {
        assertFalse(MergePending.active.value)

        val result = safeApiCall { refused("""{"error":"Restoring data","code":"merge_pending"}""") }

        val failure = result as ApiResult.Failure
        assertTrue(failure.isMergePending)
        assertEquals("Restoring data", failure.message)
        assertTrue(MergePending.active.value)
    }

    @Test
    fun `other refusals leave the hold alone`() = runBlocking {
        safeApiCall { refused("""{"error":"nope","code":"unavailable_on_failover"}""") }
        safeApiCall { refused("""{"error":"bad"}""", status = 400) }

        assertFalse(MergePending.active.value)
    }

    @Test
    fun `the hold ends when the server is back to normal`() = runBlocking {
        val monitor = MergeMonitor({ ApiResult.Success(MergeStatus("normal")) }, semiprivate = false)
        assertFalse(monitor.stillPending())
    }

    @Test
    fun `the hold continues while the server holds or merges`() = runBlocking {
        for (mode in listOf("hold", "merge")) {
            val monitor = MergeMonitor({ ApiResult.Success(MergeStatus(mode, "running")) }, semiprivate = false)
            assertTrue(mode, monitor.stillPending())
        }
    }

    @Test
    fun `the hold continues when the server cannot be asked`() = runBlocking {
        val monitor = MergeMonitor({ ApiResult.Failure("timeout") }, semiprivate = false)
        assertTrue(monitor.stillPending())
    }

    @Test
    fun `the semiprivate flavour only retries, it cannot ask`() = runBlocking {
        var asked = false
        val monitor = MergeMonitor({ asked = true; ApiResult.Success(MergeStatus("hold")) }, semiprivate = true)

        assertFalse(monitor.stillPending())
        assertFalse(asked)
    }
}
