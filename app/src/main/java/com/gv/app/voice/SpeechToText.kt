package com.gv.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The only file in the app that touches `android.speech.*` (mirrors the
 * single-file-per-integration rule used by `spotify/Spotify.kt`). Uses the
 * ON-DEVICE recognizer so audio never leaves the phone, honouring the feature's
 * privacy premise; callers see a plain [SttEvent] flow.
 */
sealed interface SttEvent {
    data object ReadyForSpeech : SttEvent
    data class Partial(val text: String) : SttEvent
    data class Final(val text: String) : SttEvent
    data object EndOfSpeech : SttEvent
    data class Failed(val reason: SttError) : SttEvent
}

enum class SttError { UNAVAILABLE, PERMISSION, NO_MATCH, NETWORK, BUSY, TIMEOUT, LANGUAGE_UNAVAILABLE, OTHER }

object SpeechToText {

    /** On-device recognition available (no audio leaves the device). */
    fun isAvailable(context: Context): Boolean =
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    /**
     * Cold flow: collecting starts listening; cancelling stops and destroys the
     * recognizer. Must be collected on the main thread (SpeechRecognizer requires
     * it) — `viewModelScope` satisfies this.
     */
    fun listen(context: Context, languageTag: String = "es-ES"): Flow<SttEvent> = callbackFlow {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            trySend(SttEvent.Failed(SttError.UNAVAILABLE))
            close()
            return@callbackFlow
        }

        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(SttEvent.ReadyForSpeech)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                firstResult(partialResults)?.let { trySend(SttEvent.Partial(it)) }
            }

            override fun onResults(results: Bundle?) {
                trySend(SttEvent.Final(firstResult(results).orEmpty()))
                close()
            }

            override fun onEndOfSpeech() {
                trySend(SttEvent.EndOfSpeech)
            }

            override fun onError(error: Int) {
                trySend(SttEvent.Failed(mapError(error)))
                close()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        recognizer.setRecognitionListener(listener)
        recognizer.startListening(intent)

        awaitClose {
            recognizer.stopListening()
            recognizer.destroy()
        }
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun mapError(code: Int): SttError = when (code) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SttError.PERMISSION
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        -> SttError.NO_MATCH
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        -> SttError.NETWORK
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SttError.BUSY
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        -> SttError.LANGUAGE_UNAVAILABLE
        else -> SttError.OTHER
    }
}
