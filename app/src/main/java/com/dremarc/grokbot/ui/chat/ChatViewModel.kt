package com.dremarc.grokbot.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dremarc.grokbot.domain.imagegen.ImageGenerator
import com.dremarc.grokbot.domain.model.AppError
import com.dremarc.grokbot.domain.model.AppSettings
import com.dremarc.grokbot.domain.model.Attachment
import com.dremarc.grokbot.domain.model.ChatException
import com.dremarc.grokbot.domain.model.ChatMessage
import com.dremarc.grokbot.domain.model.Conversation
import com.dremarc.grokbot.domain.model.NewMessage
import com.dremarc.grokbot.domain.model.Role
import com.dremarc.grokbot.domain.model.StreamEvent
import com.dremarc.grokbot.domain.model.WebSource
import com.dremarc.grokbot.domain.model.toInfo
import com.dremarc.grokbot.domain.repository.AssistantRepository
import com.dremarc.grokbot.domain.repository.ConversationRepository
import com.dremarc.grokbot.domain.repository.MediaRepository
import com.dremarc.grokbot.domain.repository.SettingsRepository
import com.dremarc.grokbot.ui.common.UiEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val activeConversationId: String? = null,
    val activeTitle: String = "Z4firo",
    val messages: List<ChatMessage> = emptyList(),
    /** Partial answer of the stream belonging to the active conversation, or null. */
    val streamingText: String? = null,
    val streamingSources: List<WebSource> = emptyList(),
    val isStreaming: Boolean = false,
    val isGeneratingImage: Boolean = false,
    val draft: String = "",
    val attachments: List<Attachment> = emptyList(),
    val webSearchEnabled: Boolean = false,
)

/** Input and transient state that is not persisted. */
private data class Transient(
    val draft: String = "",
    val attachments: List<Attachment> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingConversationId: String? = null,
    val streamingText: String = "",
    val streamingSources: List<WebSource> = emptyList(),
    val isGeneratingImage: Boolean = false,
)

class ChatViewModel(
    private val conversations: ConversationRepository,
    private val assistant: AssistantRepository,
    private val imageGenerator: ImageGenerator,
    private val media: MediaRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val activeId = MutableStateFlow<String?>(null)
    private val transient = MutableStateFlow(Transient())
    private val events = Channel<UiEvent>(Channel.BUFFERED)
    private var streamJob: Job? = null

    /** One-shot messages (snackbars). */
    val uiEvents: Flow<UiEvent> = events.receiveAsFlow()

    private val messages: Flow<List<ChatMessage>> = activeId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList<ChatMessage>()) else conversations.observeMessages(id)
    }

    val uiState: StateFlow<ChatUiState> = combine(
        conversations.observeConversations(),
        activeId,
        messages,
        transient,
        settings.settings,
    ) { list, id, msgs, t, s ->
        val streamingHere = t.isStreaming && t.streamingConversationId == id
        ChatUiState(
            conversations = list,
            activeConversationId = id,
            activeTitle = list.firstOrNull { it.id == id }?.title ?: "Z4firo",
            messages = msgs,
            streamingText = if (streamingHere) t.streamingText else null,
            streamingSources = if (streamingHere) t.streamingSources else emptyList(),
            isStreaming = t.isStreaming,
            isGeneratingImage = t.isGeneratingImage,
            draft = t.draft,
            attachments = t.attachments,
            webSearchEnabled = s.webSearchEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChatUiState())

    fun onDraftChange(text: String) {
        transient.update { it.copy(draft = text) }
    }

    fun notify(message: String) {
        events.trySend(UiEvent.Message(message))
    }

    fun newChat() {
        activeId.value = null
    }

    /** Toggles web search for following messages and remembers the choice in settings. */
    fun setWebSearch(enabled: Boolean) {
        settings.save(settings.settings.value.copy(webSearchEnabled = enabled))
    }

    fun selectConversation(id: String) {
        activeId.value = id
    }

    fun renameConversation(id: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { conversations.renameConversation(id, clean) }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            if (transient.value.streamingConversationId == id) streamJob?.cancel()
            conversations.deleteConversation(id)
            if (activeId.value == id) activeId.value = null
        }
    }

    fun addAttachment(uri: String) {
        if (transient.value.attachments.size >= MAX_ATTACHMENTS) {
            notify("Máximo $MAX_ATTACHMENTS adjuntos por mensaje")
            return
        }
        viewModelScope.launch {
            media.readAttachment(uri)
                .onSuccess { attachment ->
                    transient.update { current -> current.copy(attachments = current.attachments + attachment) }
                }
                .onFailure { error ->
                    notify("No se pudo adjuntar el archivo: ${error.message ?: "error desconocido"}")
                }
        }
    }

    fun removeAttachment(index: Int) {
        transient.update { current ->
            current.copy(attachments = current.attachments.filterIndexed { i, _ -> i != index })
        }
    }

    /** Sends the draft. Commands starting with /imagine go to the image generator. */
    fun send() {
        val current = transient.value
        if (current.isStreaming || current.isGeneratingImage) return
        val text = current.draft.trim()
        val attachments = current.attachments
        if (text.isEmpty() && attachments.isEmpty()) return

        if (text.startsWith(IMAGINE_COMMAND, ignoreCase = true)) {
            generateImage(text.substring(IMAGINE_COMMAND.length).trim())
            return
        }

        val appSettings = settings.settings.value
        if (appSettings.apiKey.isBlank()) {
            notify(AppError.MissingApiKey.userMessage)
            return
        }

        transient.update {
            it.copy(
                draft = "",
                attachments = emptyList(),
                isStreaming = true,
                streamingConversationId = activeId.value,
                streamingText = "",
                streamingSources = emptyList(),
            )
        }
        streamJob = viewModelScope.launch {
            runStream(text, attachments, appSettings)
        }
    }

    /** Image button: fills "/imagine " when the draft is empty, otherwise generates from the draft. */
    fun onImageButton() {
        val draft = transient.value.draft.trim()
        when {
            draft.isEmpty() -> onDraftChange("$IMAGINE_COMMAND ")
            draft.startsWith(IMAGINE_COMMAND, ignoreCase = true) ->
                generateImage(draft.substring(IMAGINE_COMMAND.length).trim())
            else -> generateImage(draft)
        }
    }

    fun stopGeneration() {
        streamJob?.cancel()
    }

    fun exportImage(localPath: String, destinationUri: String) {
        viewModelScope.launch {
            media.exportImage(localPath, destinationUri)
                .onSuccess { notify("Imagen guardada") }
                .onFailure { error -> notify("No se pudo guardar la imagen: ${error.message ?: "error"}") }
        }
    }

    private suspend fun runStream(
        text: String,
        attachments: List<Attachment>,
        appSettings: AppSettings,
    ) {
        val conversationId = activeId.value
            ?: conversations.createConversation(titleFor(text, attachments)).also { activeId.value = it }
        transient.update { it.copy(streamingConversationId = conversationId) }

        // History is read before the new user message is stored, so it is not duplicated.
        val history = conversations.getHistory(conversationId)
        conversations.addMessage(
            NewMessage(
                conversationId = conversationId,
                role = Role.USER,
                text = text,
                createdAt = now(),
                attachments = attachments.map { it.toInfo() },
            ),
        )

        val answer = StringBuilder()
        var sources: List<WebSource> = emptyList()
        try {
            assistant.streamReply(appSettings, history, text, attachments).collect { event ->
                when (event) {
                    is StreamEvent.TextDelta -> {
                        answer.append(event.text)
                        transient.update { it.copy(streamingText = answer.toString()) }
                    }
                    is StreamEvent.Sources -> {
                        sources = event.sources
                        transient.update { it.copy(streamingSources = event.sources) }
                    }
                }
            }
        } catch (e: ChatException) {
            notify(e.error.userMessage)
        } catch (e: IOException) {
            notify(AppError.Network.userMessage)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            notify(AppError.Unknown(e.message ?: "desconocido").userMessage)
        } finally {
            // Runs even when the user pressed Stop: keep the partial answer.
            withContext(NonCancellable) {
                if (answer.isNotEmpty()) {
                    runCatching {
                        conversations.addMessage(
                            NewMessage(
                                conversationId = conversationId,
                                role = Role.ASSISTANT,
                                text = answer.toString(),
                                createdAt = now(),
                                sources = sources,
                            ),
                        )
                    }
                }
                transient.update {
                    it.copy(
                        isStreaming = false,
                        streamingConversationId = null,
                        streamingText = "",
                        streamingSources = emptyList(),
                    )
                }
            }
        }
    }

    private fun generateImage(prompt: String) {
        if (prompt.isBlank()) {
            notify("Uso: /imagine <descripción de la imagen>")
            return
        }
        if (transient.value.isGeneratingImage) return
        transient.update { it.copy(draft = "", isGeneratingImage = true) }

        viewModelScope.launch {
            try {
                val conversationId = activeId.value
                    ?: conversations.createConversation(titleFor("Imagen: $prompt", emptyList()))
                        .also { activeId.value = it }
                conversations.addMessage(
                    NewMessage(conversationId, Role.USER, "$IMAGINE_COMMAND $prompt", now()),
                )
                imageGenerator.generate(prompt)
                    .onSuccess { image ->
                        val path = media.saveImage(image.bytes, image.mimeType)
                        conversations.addMessage(
                            NewMessage(
                                conversationId = conversationId,
                                role = Role.ASSISTANT,
                                text = "Imagen generada para: $prompt",
                                createdAt = now(),
                                imagePath = path,
                            ),
                        )
                    }
                    .onFailure { error ->
                        notify("No se pudo generar la imagen: ${error.message ?: "error desconocido"}")
                    }
            } finally {
                transient.update { it.copy(isGeneratingImage = false) }
            }
        }
    }

    private fun titleFor(text: String, attachments: List<Attachment>): String {
        val base = text.ifBlank { attachments.firstOrNull()?.name.orEmpty() }
            .replace('\n', ' ')
            .trim()
        return when {
            base.isEmpty() -> "Nueva conversación"
            base.length > TITLE_MAX_LENGTH -> base.take(TITLE_MAX_LENGTH) + "…"
            else -> base
        }
    }

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        const val MAX_ATTACHMENTS = 5
        const val TITLE_MAX_LENGTH = 40
        const val IMAGINE_COMMAND = "/imagine"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
