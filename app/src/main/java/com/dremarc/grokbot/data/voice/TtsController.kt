package com.dremarc.grokbot.data.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Read-aloud for chat messages with [TextToSpeech]. [speakingId] holds the id of the
 * message currently being read (or null), so the UI can show a stop button.
 */
class TtsController(context: Context) {

    private val speakingIdState = MutableStateFlow<String?>(null)
    val speakingId: StateFlow<String?> = speakingIdState.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, String>? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        speakingIdState.value = null
                    }
                    override fun onError(utteranceId: String?) {
                        speakingIdState.value = null
                    }
                })
                pending?.let { (id, text) ->
                    pending = null
                    speakNow(id, text)
                }
            }
        }
    }

    fun speak(messageId: Long, text: String) {
        val id = messageId.toString()
        val clean = stripMarkdown(text)
        if (clean.isBlank()) return
        if (!ready) {
            pending = id to clean
            return
        }
        speakNow(id, clean)
    }

    fun stop() {
        tts?.stop()
        speakingIdState.value = null
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun speakNow(id: String, text: String) {
        speakingIdState.value = id
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    /** Removes the most common markdown symbols so the engine does not read them aloud. */
    private fun stripMarkdown(text: String): String =
        text.replace(Regex("[*_`#>]+"), "").replace(Regex("\\s+"), " ").trim()
}
