package com.dremarc.grokbot.domain.repository

import com.dremarc.grokbot.domain.model.AppSettings
import com.dremarc.grokbot.domain.model.Attachment
import com.dremarc.grokbot.domain.model.ChatMessage
import com.dremarc.grokbot.domain.model.Conversation
import com.dremarc.grokbot.domain.model.NewMessage
import com.dremarc.grokbot.domain.model.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Streams a Claude reply. The flow fails with ChatException on API errors. */
interface AssistantRepository {
    fun streamReply(
        settings: AppSettings,
        history: List<ChatMessage>,
        userText: String,
        attachments: List<Attachment>,
    ): Flow<StreamEvent>
}

interface ConversationRepository {
    fun observeConversations(): Flow<List<Conversation>>
    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>
    suspend fun getHistory(conversationId: String): List<ChatMessage>
    suspend fun createConversation(title: String): String
    suspend fun renameConversation(id: String, title: String)
    suspend fun deleteConversation(id: String)
    suspend fun addMessage(message: NewMessage): Long
}

interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    fun save(settings: AppSettings)
}

/** File and media work that must not leak Android types into the domain layer. */
interface MediaRepository {
    /** Reads a content URI chosen by the user and prepares it for the API. */
    suspend fun readAttachment(uri: String): Result<Attachment>

    /** Stores generated image bytes privately and returns their local path. */
    suspend fun saveImage(bytes: ByteArray, mimeType: String): String

    /** Copies a stored image to a user-chosen content URI (SAF). */
    suspend fun exportImage(localPath: String, destinationUri: String): Result<Unit>
}
