package com.gv.app.ui.voz

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gv.app.container
import com.gv.app.data.repository.ApiResult
import com.gv.app.domain.model.AiUsage
import com.gv.app.domain.model.VozKind
import com.gv.app.domain.model.VozSuggestResponse
import com.gv.app.voice.SpeechToText
import com.gv.app.voice.SttError
import com.gv.app.voice.SttEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId

/** The request/approval state machine for the Voz screen. */
sealed interface VozUiState {
    data object Idle : VozUiState
    data object Loading : VozUiState
    data class Suggestion(val data: VozSuggestResponse) : VozUiState
    data object Executing : VozUiState
    data class Result(val kind: VozKind, val summary: String, val rowCount: Int?) : VozUiState
    data class Rejected(val reason: String) : VozUiState
    data class Error(val message: String) : VozUiState
}

/** Sub-state for the "AI spend this month" card, isolated from the conversation. */
sealed interface UsageState {
    data object Loading : UsageState
    data class Ready(val usage: AiUsage) : UsageState
    data object Hidden : UsageState
}

private val MADRID: ZoneId = ZoneId.of("Europe/Madrid")

class VozViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = app.container.vozRepository

    private val _state = MutableStateFlow<VozUiState>(VozUiState.Idle)
    val state: StateFlow<VozUiState> = _state.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _usage = MutableStateFlow<UsageState>(UsageState.Loading)
    val usage: StateFlow<UsageState> = _usage.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    /** Opaque signed token carried across feedback rounds and into execute. */
    private var token: String? = null
    private var sttJob: Job? = null

    fun onInputChange(text: String) {
        _input.value = text
    }

    fun startDictation() {
        if (_listening.value) return
        _listening.value = true
        sttJob = viewModelScope.launch {
            SpeechToText.listen(getApplication()).collect { ev ->
                when (ev) {
                    is SttEvent.Partial -> _input.value = ev.text
                    is SttEvent.Final -> {
                        if (ev.text.isNotBlank()) _input.value = ev.text
                        stopDictation()
                    }
                    is SttEvent.Failed -> {
                        _toast.emit(sttMessage(ev.reason))
                        stopDictation()
                    }
                    SttEvent.EndOfSpeech, SttEvent.ReadyForSpeech -> Unit
                }
            }
        }
    }

    fun stopDictation() {
        sttJob?.cancel()
        sttJob = null
        _listening.value = false
    }

    fun submit() {
        val text = _input.value.trim()
        if (text.isEmpty()) {
            viewModelScope.launch { _toast.emit("Escribe o dicta algo primero") }
            return
        }
        if (_state.value is VozUiState.Loading || _state.value is VozUiState.Executing) return
        stopDictation()
        _state.value = VozUiState.Loading
        viewModelScope.launch {
            when (val r = repo.suggest(text, token)) {
                is ApiResult.Success -> {
                    val data = r.data
                    when (VozKind.from(data.kind)) {
                        VozKind.REJECT -> {
                            token = null
                            _state.value = VozUiState.Rejected(data.explanation)
                        }
                        else -> {
                            token = data.token
                            _state.value = VozUiState.Suggestion(data)
                        }
                    }
                }
                is ApiResult.Failure -> _state.value = VozUiState.Error(r.message)
            }
        }
    }

    fun approve() {
        val t = token ?: return
        if (_state.value is VozUiState.Executing) return
        _state.value = VozUiState.Executing
        viewModelScope.launch {
            when (val r = repo.execute(t)) {
                is ApiResult.Success -> {
                    _state.value = VozUiState.Result(VozKind.from(r.data.kind), r.data.summary, r.data.row_count)
                    token = null
                    loadUsage()
                }
                is ApiResult.Failure -> _state.value = VozUiState.Error(r.message)
            }
        }
    }

    /** Return to input to refine; the token is retained so the backend refines the prior suggestion. */
    fun giveFeedback() {
        _state.value = VozUiState.Idle
    }

    fun reset() {
        token = null
        _input.value = ""
        _state.value = VozUiState.Idle
    }

    fun loadUsage() {
        viewModelScope.launch {
            when (val r = repo.usage(currentMonth())) {
                is ApiResult.Success -> _usage.value = UsageState.Ready(r.data)
                is ApiResult.Failure ->
                    // Keep the last good value on a transient refresh failure.
                    if (_usage.value !is UsageState.Ready) _usage.value = UsageState.Hidden
            }
        }
    }

    override fun onCleared() {
        stopDictation()
    }

    private fun currentMonth(): String = YearMonth.now(MADRID).toString()

    private fun sttMessage(reason: SttError): String = when (reason) {
        SttError.UNAVAILABLE -> "Reconocimiento de voz no disponible"
        SttError.PERMISSION -> "Permiso de micrófono denegado"
        SttError.NO_MATCH -> "No te he entendido, inténtalo de nuevo"
        SttError.NETWORK -> "Error de red en el reconocimiento"
        SttError.BUSY -> "El reconocedor está ocupado"
        SttError.TIMEOUT -> "Tiempo de espera agotado"
        SttError.LANGUAGE_UNAVAILABLE -> "Falta el modelo de voz en español (descárgalo en Ajustes)"
        SttError.OTHER -> "Error de reconocimiento de voz"
    }
}
