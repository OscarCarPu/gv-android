package com.gv.app.data.api

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** What arrived on the stream, plus the two connection facts the UI shows as "live". */
sealed interface CalendarStreamEvent {
    data object Connected : CalendarStreamEvent
    data object Disconnected : CalendarStreamEvent
    data class Changed(val message: CalendarStreamMessage) : CalendarStreamEvent
}

/**
 * One notification. It says *that* something changed and never what: the client refetches the
 * range it is showing, so a live update and a fresh open take the same code path and cannot
 * drift out of step with the server the way an incremental patch stream can.
 */
data class CalendarStreamMessage(
    val type: String = "",
    val calendar_id: Int = 0,
    val account_email: String = "",
    val at: String = "",
) {
    companion object {
        const val CALENDAR_CHANGED = "calendar.changed"
        const val ACCOUNT_CONNECTED = "account.connected"
        const val ACCOUNT_DISCONNECTED = "account.disconnected"
        const val ACCOUNT_NEEDS_REAUTH = "account.needs_reauth"
    }
}

/**
 * Reader for gv-api's `GET /calendar/stream`.
 *
 * Hand-rolled rather than `EventSource`-shaped because the framing is three lines long and the
 * one thing a library would add — reconnection — has to be gated on connectivity here anyway.
 * Unlike a browser, this client *can* send the bearer token as a header, so it talks to the API
 * directly and needs no proxy (gv-web has to route the same stream through its own server).
 *
 * The flow reconnects for as long as it is collected, with exponential backoff, and emits
 * [CalendarStreamEvent.Connected] / [CalendarStreamEvent.Disconnected] around each attempt so
 * the screen can stop claiming to be live the moment it is not.
 */
class CalendarStream(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val isOnline: () -> Boolean,
) {

    private val gson = Gson()

    fun events(): Flow<CalendarStreamEvent> = channelFlow {
        var backoffMs = INITIAL_BACKOFF_MS
        while (isActive) {
            // Skip the socket entirely while there is no connection: it would fail at once and
            // the retry would just spin the radio.
            if (!isOnline()) {
                delay(OFFLINE_RETRY_MS)
                continue
            }
            val connected = readStream()
            send(CalendarStreamEvent.Disconnected)
            // A stream that ran before ending is a healthy one that dropped, not a broken
            // endpoint, so the backoff starts over rather than climbing towards a minute.
            backoffMs = if (connected) INITIAL_BACKOFF_MS else (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            delay(backoffMs)
        }
    }

    /**
     * Returns true if the stream was established at all, which decides the next backoff.
     *
     * Failures are swallowed here rather than at the call site so the answer survives them: a
     * connection that ran for an hour and then dropped must not be told apart from a refused one
     * by whether it happened to end with an exception.
     */
    private suspend fun ProducerScope<CalendarStreamEvent>.readStream(): Boolean =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/calendar/stream")
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .build()
            val call = client.newCall(request)
            // A blocking socket read does not notice coroutine cancellation, so leaving this
            // scope has to cancel the call by hand or the connection outlives the screen.
            val cancelOnExit = coroutineContext.job.invokeOnCompletion { call.cancel() }
            var established = false
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val source = response.body?.source() ?: return@use
                    established = true
                    send(CalendarStreamEvent.Connected)

                    var eventName: String? = null
                    while (isActive) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            // ": ping" every 25s, which is what keeps the tunnel from dropping
                            // an idle connection. Nothing to do but notice it arrived.
                            line.startsWith(":") -> Unit
                            line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                            line.startsWith("data:") -> {
                                val payload = line.removePrefix("data:").trim()
                                send(CalendarStreamEvent.Changed(parse(eventName, payload)))
                                eventName = null
                            }
                        }
                    }
                }
            } catch (_: IOException) {
                // A dropped socket, a refused connection, a timeout: all normal here, and all
                // answered the same way — say so and let the caller back off.
            } catch (_: IllegalStateException) {
                // OkHttp refusing to execute a call twice. Not recoverable by retrying faster.
            } finally {
                cancelOnExit.dispose()
            }
            established
        }

    /**
     * The event name is authoritative for the type; the payload only adds detail. A message
     * whose body cannot be read is still worth reporting — the type alone is enough to refetch.
     */
    private fun parse(eventName: String?, payload: String): CalendarStreamMessage {
        val parsed = runCatching { gson.fromJson(payload, CalendarStreamMessage::class.java) }.getOrNull()
        val type = eventName?.takeIf { it.isNotBlank() } ?: parsed?.type.orEmpty()
        return (parsed ?: CalendarStreamMessage()).copy(type = type)
    }

    private companion object {
        const val INITIAL_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 60_000L
        const val OFFLINE_RETRY_MS = 10_000L
    }
}
