package com.dremarc.grokbot.data.remote

import com.dremarc.grokbot.domain.model.AppError
import com.dremarc.grokbot.domain.model.ChatException
import com.dremarc.grokbot.domain.model.StreamEvent
import com.dremarc.grokbot.domain.model.WebSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Turns Anthropic SSE events into domain [StreamEvent]s.
 *
 * Handled events:
 * - content_block_delta / text_delta      -> TextDelta
 * - content_block_start / web_search_tool_result -> Sources (search hits)
 * - content_block_delta / citations_delta -> Sources (cited pages)
 * - error                                 -> throws ChatException
 */
internal class StreamDecoder {

    private val sources = LinkedHashMap<String, WebSource>()

    fun decode(eventName: String?, data: String): List<StreamEvent> {
        val json = runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull()
            ?: return emptyList()
        val type = eventName ?: json.str("type") ?: return emptyList()

        return when (type) {
            "content_block_start" -> {
                val block = json["content_block"] as? JsonObject ?: return emptyList()
                if (block.str("type") == "web_search_tool_result") {
                    addSearchResults(block["content"] as? JsonArray)
                } else {
                    emptyList()
                }
            }

            "content_block_delta" -> {
                val delta = json["delta"] as? JsonObject ?: return emptyList()
                when (delta.str("type")) {
                    "text_delta" -> listOf(StreamEvent.TextDelta(delta.str("text").orEmpty()))
                    "citations_delta" -> {
                        val citation = delta["citation"] as? JsonObject
                        val url = citation?.str("url")
                        if (citation != null && url != null && addSource(url, citation.str("title"))) {
                            listOf(StreamEvent.Sources(sources.values.toList()))
                        } else {
                            emptyList()
                        }
                    }
                    else -> emptyList()
                }
            }

            "error" -> {
                val error = json["error"] as? JsonObject
                val message = error?.str("message") ?: "error desconocido"
                throw ChatException(
                    when (error?.str("type")) {
                        "authentication_error", "permission_error" -> AppError.InvalidApiKey
                        "rate_limit_error" -> AppError.RateLimited
                        else -> AppError.Server(message)
                    },
                )
            }

            else -> emptyList()
        }
    }

    private fun addSearchResults(results: JsonArray?): List<StreamEvent> {
        if (results == null) return emptyList()
        var changed = false
        for (item in results) {
            val obj = item as? JsonObject ?: continue
            val url = obj.str("url") ?: continue
            if (addSource(url, obj.str("title"))) changed = true
        }
        return if (changed) listOf(StreamEvent.Sources(sources.values.toList())) else emptyList()
    }

    /** Returns true when the URL was not seen before. */
    private fun addSource(url: String, title: String?): Boolean {
        if (sources.containsKey(url)) return false
        sources[url] = WebSource(title = title?.takeIf { it.isNotBlank() } ?: url, url = url)
        return true
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}
