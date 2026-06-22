package com.gv.app.data.repository

import com.google.gson.Gson
import com.gv.app.domain.model.ErrorResponse
import retrofit2.Response
import java.io.IOException

/**
 * Outcome of a single network call, with enough detail for both UI error display and the
 * outbox worker's retry/dead-letter decision. [Failure.code] is the HTTP status when the
 * server responded; null means the request never completed (no network / timeout / parse).
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val message: String, val code: Int? = null) : ApiResult<Nothing>
}

/** A failure that came back from the server (has an HTTP status) is permanent for 4xx. */
val ApiResult.Failure.isClientError: Boolean
    get() = code != null && code in 400..499

/** No HTTP status → the call never reached/heard-back from the server; worth retrying. */
val ApiResult.Failure.isTransient: Boolean
    get() = code == null || code in 500..599

private val errorGson = Gson()

private fun <T> Response<T>.extractError(): String {
    val raw = try {
        errorBody()?.string()
    } catch (_: Exception) {
        null
    }
    if (!raw.isNullOrBlank()) {
        runCatching { errorGson.fromJson(raw, ErrorResponse::class.java)?.error }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
    }
    return "Request failed (${code()})"
}

/** Wraps a body-returning call, centralising Response unwrapping + error parsing. */
suspend fun <T : Any> safeApiCall(call: suspend () -> Response<T>): ApiResult<T> =
    try {
        val response = call()
        if (response.isSuccessful) {
            response.body()?.let { ApiResult.Success(it) }
                ?: ApiResult.Failure("Empty response body", response.code())
        } else {
            ApiResult.Failure(response.extractError(), response.code())
        }
    } catch (e: IOException) {
        ApiResult.Failure(e.message ?: "Network error")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Unexpected error")
    }

/** Wraps a call whose success carries no body (204 / Unit), e.g. DELETE. */
suspend fun safeApiCallNoBody(call: suspend () -> Response<Unit>): ApiResult<Unit> =
    try {
        val response = call()
        if (response.isSuccessful) {
            ApiResult.Success(Unit)
        } else {
            ApiResult.Failure(response.extractError(), response.code())
        }
    } catch (e: IOException) {
        ApiResult.Failure(e.message ?: "Network error")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Unexpected error")
    }
