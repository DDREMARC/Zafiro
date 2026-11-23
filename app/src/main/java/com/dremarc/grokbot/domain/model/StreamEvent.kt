package com.dremarc.grokbot.domain.model

/** Incremental output of a streamed assistant reply. */
sealed interface StreamEvent {
    /** A chunk of answer text, appended in order. */
    data class TextDelta(val text: String) : StreamEvent

    /** The complete list of web sources found so far (replaces the previous list). */
    data class Sources(val sources: List<WebSource>) : StreamEvent
}
