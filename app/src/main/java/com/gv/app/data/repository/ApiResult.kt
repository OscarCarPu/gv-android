package com.gv.app.data.repository

import com.google.gson.Gson
import com.gv.app.domain.model.ErrorResponse
import retrofit2.Response
import java.io.IOException

/**
 * Outcome of a single network call.
 *
 * [Failure.code] is the HTTP status when the server answered; null means the request never
 * completed (no network / timeout / parse error). [Failure.offline] marks the specific case
 * of "we knew there was no connection", which the UI phrases differently from a real error —
 * being offline is a normal state here, not a fault.
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(
        val message: String,
        val code: Int? = null,
        val offline: Boolean = false,
        /** The API's machine-readable reason (`code` in the error body), when it sends one. */
        val errorCode: String? = null,
    ) : ApiResult<Nothing>

    companion object {
        /**
         * The single refusal every write funnels through when there is no connection.
         * Returned by [requireOnline] rather than thrown, so callers surface it as a normal
         * error state.
         */
        fun offline(): Failure = Failure(OFFLINE_MESSAGE, offline = true)

        const val OFFLINE_MESSAGE = "You're offline — connect to make changes"

        /** gv-api is running on the backup server, which can't reach the home LAN. */
        const val CODE_UNAVAILABLE_ON_FAILOVER = "unavailable_on_failover"
    }
}

/** Convenience for the common `is Success` check at call sites. */
val ApiResult<*>.isSuccess: Boolean
    get() = this is ApiResult.Success

/** The backend is on the backup server and this feature needs the home LAN. */
val ApiResult.Failure.isUnavailableOnFailover: Boolean
    get() = errorCode == ApiResult.CODE_UNAVAILABLE_ON_FAILOVER

/** Maps a success's payload while carrying a failure through untouched. */
inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Failure -> this
}

private val errorGson = Gson()

private fun <T> Response<T>.parseError(): ErrorResponse? {
    val raw = try {
        errorBody()?.string()
    } catch (_: Exception) {
        null
    }
    if (raw.isNullOrBlank()) return null
    return runCatching { errorGson.fromJson(raw, ErrorResponse::class.java) }
        .getOrNull()
        ?.takeIf { it.error.isNotBlank() }
}

private fun <T> Response<T>.toFailure(): ApiResult.Failure {
    val parsed = parseError()
    return ApiResult.Failure(
        message = parsed?.error ?: "Request failed (${code()})",
        code = code(),
        errorCode = parsed?.code,
    )
}

/** Wraps a body-returning call, centralising Response unwrapping + error parsing. */
suspend fun <T : Any> safeApiCall(call: suspend () -> Response<T>): ApiResult<T> =
    try {
        val response = call()
        if (response.isSuccessful) {
            response.body()?.let { ApiResult.Success(it) }
                ?: ApiResult.Failure("Empty response body", response.code())
        } else {
            response.toFailure()
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
            response.toFailure()
        }
    } catch (e: IOException) {
        ApiResult.Failure(e.message ?: "Network error")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Unexpected error")
    }
