package com.dremarc.grokbot.data.local

import com.dremarc.grokbot.domain.model.AttachmentInfo
import com.dremarc.grokbot.domain.model.AttachmentKind
import com.dremarc.grokbot.domain.model.ChatMessage
import com.dremarc.grokbot.domain.model.Conversation
import com.dremarc.grokbot.domain.model.NewMessage
import com.dremarc.grokbot.domain.model.Role
import com.dremarc.grokbot.domain.model.WebSource
import com.dremarc.grokbot.domain.repository.ConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class StoredAttachment(val name: String, val kind: String)

@Serializable
data class StoredSource(val title: String, val url: String)

class RoomConversationRepository(database: AppDatabase) : ConversationRepository {

    private val conversations = database.conversationDao()
    private val messages = database.messageDao()
    private val json = Json { ignoreUnknownKeys = true }

    override fun observeConversations(): Flow<List<Conversation>> =
        conversations.observeAll().map { rows ->
            rows.map { Conversation(it.id, it.title, it.updatedAt) }
        }

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        messages.observeForConversation(conversationId).map { rows ->
            rows.map { it.toDomain() }
        }

    override suspend fun getHistory(conversationId: String): List<ChatMessage> =
        messages.getForConversation(conversationId).map { it.toDomain() }

    override suspend fun createConversation(title: String): String {
        val id = UUID.randomUUID().toString()
        conversations.insert(ConversationEntity(id = id, title = title, updatedAt = System.currentTimeMillis()))
        return id
    }

    override suspend fun renameConversation(id: String, title: String) {
        conversations.rename(id, title)
    }

    override suspend fun deleteConversation(id: String) {
        conversations.delete(id)
    }

    override suspend fun addMessage(message: NewMessage): Long {
        val id = messages.insert(message.toEntity())
        conversations.touch(message.conversationId, message.createdAt)
        return id
    }

    private fun MessageEntity.toDomain(): ChatMessage = ChatMessage(
        id = id,
        conversationId = conversationId,
        role = Role.valueOf(role),
        text = text,
        createdAt = createdAt,
        attachments = attachmentsJson?.let { json.decodeFromString<List<StoredAttachment>>(it) }
            .orEmpty()
            .map { AttachmentInfo(it.name, AttachmentKind.valueOf(it.kind)) },
        sources = sourcesJson?.let { json.decodeFromString<List<StoredSource>>(it) }
            .orEmpty()
            .map { WebSource(it.title, it.url) },
        imagePath = imagePath,
    )

    private fun NewMessage.toEntity(): MessageEntity = MessageEntity(
        conversationId = conversationId,
        role = role.name,
        text = text,
        createdAt = createdAt,
        attachmentsJson = attachments.takeIf { it.isNotEmpty() }?.let { list ->
            json.encodeToString(list.map { StoredAttachment(it.name, it.kind.name) })
        },
        sourcesJson = sources.takeIf { it.isNotEmpty() }?.let { list ->
            json.encodeToString(list.map { StoredSource(it.title, it.url) })
        },
        imagePath = imagePath,
    )
}
