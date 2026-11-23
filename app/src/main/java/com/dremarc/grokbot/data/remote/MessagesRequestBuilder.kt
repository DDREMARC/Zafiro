package com.dremarc.grokbot.data.remote

import com.dremarc.grokbot.domain.model.AppSettings
import com.dremarc.grokbot.domain.model.Attachment
import com.dremarc.grokbot.domain.model.ChatMessage
import com.dremarc.grokbot.domain.model.Role
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Builds the JSON body of POST /v1/messages (Messages API, streaming enabled). */
internal object MessagesRequestBuilder {

    private const val MAX_TOKENS = 4096
    private const val WEB_SEARCH_MAX_USES = 5

    fun build(
        settings: AppSettings,
        history: List<ChatMessage>,
        userText: String,
        attachments: List<Attachment>,
    ): JsonObject = buildJsonObject {
        put("model", settings.model.ifBlank { AppSettings.DEFAULT_MODEL })
        put("max_tokens", MAX_TOKENS)
        put("stream", true)
        if (settings.systemPrompt.isNotBlank()) {
            put("system", settings.systemPrompt)
        }

        putJsonArray("messages") {
            for (message in history) {
                if (message.text.isBlank()) continue
                add(
                    buildJsonObject {
                        put("role", message.role.apiName())
                        put("content", message.text)
                    },
                )
            }
            // The current turn: attachments first, then the user's text.
            add(
                buildJsonObject {
                    put("role", "user")
                    put(
                        "content",
                        buildJsonArray {
                            attachments.forEach { add(it.toContentBlock()) }
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", userText.ifBlank { "Analiza los adjuntos." })
                                },
                            )
                        },
                    )
                },
            )
        }

        if (settings.webSearchEnabled) {
            putJsonArray("tools") {
                add(
                    buildJsonObject {
                        put("type", "web_search_20250305")
                        put("name", "web_search")
                        put("max_uses", WEB_SEARCH_MAX_USES)
                    },
                )
            }
        }
    }

    private fun Role.apiName(): String = when (this) {
        Role.USER -> "user"
        Role.ASSISTANT -> "assistant"
    }

    private fun Attachment.toContentBlock(): JsonObject = when (this) {
        is Attachment.Image -> buildJsonObject {
            put("type", "image")
            putJsonObject("source") {
                put("type", "base64")
                put("media_type", mediaType)
                put("data", base64)
            }
        }
        is Attachment.Pdf -> buildJsonObject {
            put("type", "document")
            putJsonObject("source") {
                put("type", "base64")
                put("media_type", "application/pdf")
                put("data", base64)
            }
        }
        is Attachment.Text -> buildJsonObject {
            put("type", "text")
            put("text", "Contenido del archivo \"$name\":\n\n$content")
        }
    }
}
