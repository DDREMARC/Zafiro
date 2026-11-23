package com.dremarc.grokbot.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Thin wrapper over Android's [SpeechRecognizer]. Must be used from the main thread.
 * The caller is responsible for requesting RECORD_AUDIO before calling [start].
 */
class SpeechInput(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(
        onPartialText: (String) -> Unit,
        onFinalText: (String) -> Unit,
        onFailure: (String) -> Unit,
        onFinished: () -> Unit,
    ) {
        release()
        if (!isAvailable()) {
            onFailure("Reconocimiento de voz no disponible en este dispositivo")
            onFinished()
            return
        }
        val created = SpeechRecognizer.createSpeechRecognizer(context)
        created.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.firstResult()?.let(onPartialText)
            }

            override fun onResults(results: Bundle?) {
                results?.firstResult()?.let(onFinalText)
                release()
                onFinished()
            }

            override fun onError(error: Int) {
                onFailure(errorMessage(error))
                release()
                onFinished()
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer = created
        created.startListening(intent)
    }

    fun stop() {
        recognizer?.stopListening()
        release()
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun Bundle.firstResult(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Sin conexión para el reconocimiento de voz"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permiso de micrófono denegado"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El reconocedor de voz está ocupado"
        else -> "Error de reconocimiento de voz ($error)"
    }
}
