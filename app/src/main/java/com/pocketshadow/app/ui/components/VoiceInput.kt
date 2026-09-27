package com.pocketshadow.app.ui.components

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

private const val TAG = "VoiceInput"

/**
 * Thin state holder around [SpeechRecognizer] for dictation into the composer.
 *
 * Privacy: only the on-device recognizer is used. Devices without it can
 * still use typed input; we never fall back to a network recognition service.
 */
@Stable
class VoiceInputState internal constructor(
    private val context        : Context,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult  : (String) -> Unit
) {
    var isListening by mutableStateOf(false)
        private set

    val isAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= 31 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var recognizer: SpeechRecognizer? = null

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { isListening = true }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        // Keep isListening=true after end-of-speech: results/error arrive next
        // and flipping early makes the mic flicker.
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            isListening = false
            errorMessage = "On-device dictation could not finish. Check your offline speech language in device settings, or type your message."
            // NO_MATCH / SPEECH_TIMEOUT are normal "heard nothing" outcomes.
            if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            ) Log.w(TAG, "Recognition error $error")
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let(onFinalResult)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let(onPartialResult)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /** Caller must hold RECORD_AUDIO before calling. */
    fun start() {
        if (isListening) return
        if (!isAvailable) {
            errorMessage = "On-device dictation is unavailable on this device. You can still type; audio is never sent to a cloud recognizer."
            return
        }
        errorMessage = null
        val r = runCatching { recognizer ?: createRecognizer().also {
            it.setRecognitionListener(listener)
            recognizer = it
        } }.getOrElse {
            errorMessage = "Could not start on-device dictation. Please type your message."
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                     RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        isListening = true
        runCatching { r.startListening(intent) }
            .onFailure {
                isListening = false
                errorMessage = "Could not start on-device dictation. Check microphone access or type your message."
                Log.w(TAG, "startListening failed: ${it.message}")
            }
    }

    fun stop() {
        // stopListening() finishes the utterance → onResults delivers what was
        // heard so far (unlike cancel(), which discards it).
        runCatching { recognizer?.stopListening() }
        isListening = false
    }

    private fun createRecognizer(): SpeechRecognizer =
        if (Build.VERSION.SDK_INT >= 31 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        else error("On-device recognition is unavailable")

    internal fun destroy() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        isListening = false
    }
}

@Composable
fun rememberVoiceInputState(
    onPartialResult: (String) -> Unit,
    onFinalResult  : (String) -> Unit
): VoiceInputState {
    val context = LocalContext.current.applicationContext
    // rememberUpdatedState: the recognizer is created once, but callbacks must
    // always see the latest lambdas from the current composition.
    val partial by rememberUpdatedState(onPartialResult)
    val final   by rememberUpdatedState(onFinalResult)
    val state = remember {
        VoiceInputState(
            context         = context,
            onPartialResult = { partial(it) },
            onFinalResult   = { final(it) }
        )
    }
    DisposableEffect(Unit) { onDispose { state.destroy() } }
    return state
}
