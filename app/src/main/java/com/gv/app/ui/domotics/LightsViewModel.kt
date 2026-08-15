package com.gv.app.ui.domotics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.container
import com.gv.app.data.repository.ApiResult
import com.gv.app.data.repository.LightsRepository
import com.gv.app.domain.model.LightState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LightsUiState(
    val lights: List<LightState> = emptyList(),
    /** Only true for the very first load, so a refresh never blanks the list. */
    val initialLoading: Boolean = true,
    val error: String? = null,
)

/**
 * Lights tab state.
 *
 * Two things shape it, both because BLE is slow — a cold connect through the bridge can take
 * several seconds:
 *
 * 1. **Optimistic updates.** A tap applies locally at once and is reconciled with whatever the
 *    server answers. Waiting for the round trip would make every control feel broken.
 * 2. **One write in flight per (bulb, control), with a trailing send.** Dragging a slider
 *    otherwise queues dozens of writes and the bulb stops answering entirely. Intermediate
 *    positions are dropped on purpose; only where the finger stops matters.
 *
 * Polling keeps the screen honest about changes made elsewhere (the wall switch, gv-web), but
 * never overwrites a bulb the user just touched — see [touchedAt].
 */
class LightsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: LightsRepository = app.container.lightsRepository

    private val _state = MutableStateFlow(LightsUiState())
    val state: StateFlow<LightsUiState> = _state.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    /** id → when the user last acted, so a poll in flight cannot walk their change back. */
    private val touchedAt = mutableMapOf<String, Long>()

    /** "id:control" → the write currently in flight, plus the newest value waiting behind it. */
    private val inFlight = mutableSetOf<String>()
    private val queued = mutableMapOf<String, () -> Unit>()

    private var pollJob: Job? = null

    init {
        refresh(initial = true)
        startPolling()
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                refresh(initial = false)
            }
        }
    }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            if (initial) _state.value = _state.value.copy(initialLoading = true)
            when (val r = repo.loadStates()) {
                is ApiResult.Success -> _state.value = LightsUiState(
                    lights = merge(r.data),
                    initialLoading = false,
                    error = null,
                )

                is ApiResult.Failure -> _state.value = _state.value.copy(
                    initialLoading = false,
                    error = r.message,
                )
            }
        }
    }

    /** Keep a just-touched bulb's local values; take the server's word on everything else. */
    private fun merge(incoming: List<LightState>): List<LightState> {
        val now = System.currentTimeMillis()
        val current = _state.value.lights.associateBy { it.id }
        return incoming.map { next ->
            val local = current[next.id] ?: return@map next
            val touched = touchedAt[next.id] ?: 0L
            if (now - touched < POLL_GRACE_MS) {
                // Reachability is always server truth — only the settings are held back.
                local.copy(online = next.online, error = next.error)
            } else {
                next
            }
        }
    }

    // ----- local state -----

    private fun patch(id: String, transform: (LightState) -> LightState) {
        touchedAt[id] = System.currentTimeMillis()
        _state.value = _state.value.copy(
            lights = _state.value.lights.map { if (it.id == id) transform(it) else it },
        )
    }

    private fun replace(next: LightState) {
        touchedAt[next.id] = System.currentTimeMillis()
        _state.value = _state.value.copy(
            lights = _state.value.lights.map { if (it.id == next.id) next else it },
        )
    }

    // ----- commands -----

    private fun send(id: String, call: suspend () -> ApiResult<LightState>) {
        viewModelScope.launch {
            when (val r = call()) {
                // The bulb is the authority on what it actually did.
                is ApiResult.Success -> {
                    replace(r.data)
                    if (!r.data.online && !r.data.error.isNullOrBlank()) {
                        _toast.emit("${r.data.name}: ${r.data.error}")
                    }
                }

                is ApiResult.Failure -> {
                    _toast.emit(r.message)
                    refresh()
                }
            }
        }
    }

    /**
     * Coalescing send: while a write for this (bulb, control) is open, keep only the newest
     * follow-up and fire it when the open one finishes.
     */
    private fun throttled(id: String, control: String, call: suspend () -> ApiResult<LightState>) {
        val slot = "$id:$control"
        if (slot in inFlight) {
            queued[slot] = { throttled(id, control, call) }
            return
        }
        inFlight += slot
        viewModelScope.launch {
            try {
                when (val r = call()) {
                    is ApiResult.Success -> {
                        replace(r.data)
                        if (!r.data.online && !r.data.error.isNullOrBlank()) {
                            _toast.emit("${r.data.name}: ${r.data.error}")
                        }
                    }

                    is ApiResult.Failure -> {
                        _toast.emit(r.message)
                        refresh()
                    }
                }
            } finally {
                inFlight -= slot
                queued.remove(slot)?.invoke()
            }
        }
    }

    fun togglePower(light: LightState) {
        val on = !light.power
        patch(light.id) { it.copy(power = on) }
        send(light.id) { repo.setPower(light.id, on) }
    }

    fun setBrightness(light: LightState, value: Int) {
        val clamped = value.coerceIn(0, 100)
        // Deliberately does NOT assume this turns the bulb on. The LEXMAN takes brightness
        // and power as separate frames — dimming a bulb that is off changes what it will look
        // like when switched on, nothing more. Guessing otherwise is what made the web UI claim
        // "on" while the room stayed dark.
        patch(light.id) { it.copy(brightness = clamped.toDouble()) }
        throttled(light.id, "brightness") { repo.setBrightness(light.id, clamped) }
    }

    fun setColorTemp(light: LightState, kelvin: Int) {
        val clamped = kelvin.coerceIn(light.minColorTemp.toInt(), light.maxColorTemp.toInt())
        patch(light.id) { it.copy(colorTemp = clamped.toDouble(), mode = "white") }
        throttled(light.id, "colorTemp") { repo.setColorTemp(light.id, clamped) }
    }

    /**
     * Sends to every bulb unconditionally, including ones already believed to be in the target
     * state. Skipping those saves a write but makes the button do nothing whenever the believed
     * state is wrong — which is exactly when the user is reaching for "all off".
     */
    fun setAll(on: Boolean) {
        _state.value.lights.forEach { light ->
            patch(light.id) { it.copy(power = on) }
            send(light.id) { repo.setPower(light.id, on) }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 8_000L

        /** How long a local change outranks a polled value. Covers a slow BLE write. */
        const val POLL_GRACE_MS = 6_000L
    }
}
