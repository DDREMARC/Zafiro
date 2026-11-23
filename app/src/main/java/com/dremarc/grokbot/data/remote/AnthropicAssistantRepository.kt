package com.dremarc.grokbot.data.remote

import com.dremarc.grokbot.domain.model.AppError
import com.dremarc.grokbot.domain.model.AppSettings
import com.dremarc.grokbot.domain.model.Attachment
import com.dremarc.grokbot.domain.model.ChatException
import com.dremarc.grokbot.domain.model.ChatMessage
import com.dremarc.grokbot.domain.model.StreamEvent
import com.dremarc.grokbot.domain.repository.AssistantRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * Streams replies from https://api.anthropic.com/v1/messages using Server-Sent Events.
 * Cancelling the collector cancels the underlying OkHttp call (Stop button).
 */
class AnthropicAssistantRepository(
    private val client: OkHttpClient,
) : AssistantRepository {

    override fun streamReply(
        settings: AppSettings,
        history: List<ChatMessage>,
        userText: String,
        attachments: List<Attachment>,
    ): Flow<StreamEvent> = flow {
        if (settings.apiKey.isBlank()) throw ChatException(AppError.MissingApiKey)

        val body = MessagesRequestBuilder.build(settings, history, userText, attachments)
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("x-api-key", settings.apiKey.trim())
            .header("anthropic-version", API_VERSION)
            .header("accept", "text/event-stream")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val call = client.newCall(request)
        // If the collector is cancelled while we block on the socket, abort the HTTP call.
        val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            val response = try {
                call.execute()
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                throw ChatException(AppError.Network)
            }

            response.use { resp ->
                if (!resp.isSuccessful) {
                    throw ChatException(parseHttpError(resp))
                }
                val source = resp.body?.source()
                    ?: throw ChatException(AppError.Unknown("respuesta vacía"))
                val decoder = StreamDecoder()
                var eventName: String? = null
                val data = StringBuilder()
                try {
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            line.isEmpty() -> {
                                // A blank line terminates one SSE event.
                                if (data.isNotEmpty()) {
                                    for (event in decoder.decode(eventName, data.toString())) {
                                        emit(event)
                                    }
                                }
                                eventName = null
                                data.setLength(0)
                            }
                            line.startsWith("event:") -> eventName = line.substringAfter(':').trim()
                            line.startsWith("data:") -> {
                                if (data.isNotEmpty()) data.append('\n')
                                data.append(line.substringAfter(':').trimStart())
                            }
                        }
                    }
                } catch (e: IOException) {
                    currentCoroutineContext().ensureActive()
                    throw ChatException(AppError.Network)
                }
            }
        } finally {
            cancelHandle?.dispose()
        }
    }.flowOn(Dispatchers.IO)

    private fun parseHttpError(response: Response): AppError {
        val detail = runCatching {
            val text = response.body?.string().orEmpty()
            Json.parseToJsonElement(text).jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.content
        }.getOrNull()

        return when (response.code) {
            401, 403 -> AppError.InvalidApiKey
            429 -> AppError.RateLimited
            else -> AppError.Server(detail ?: "HTTP ${response.code}")
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val API_VERSION = "2023-06-01"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
