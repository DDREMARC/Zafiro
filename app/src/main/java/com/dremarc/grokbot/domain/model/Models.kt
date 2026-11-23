package com.dremarc.grokbot.domain.model

enum class Role { USER, ASSISTANT }

enum class AttachmentKind { IMAGE, PDF, TEXT }

data class Conversation(
    val id: String,
    val title: String,
    val updatedAt: Long,
)

data class AttachmentInfo(
    val name: String,
    val kind: AttachmentKind,
)

data class WebSource(
    val title: String,
    val url: String,
)

data class ChatMessage(
    val id: Long,
    val conversationId: String,
    val role: Role,
    val text: String,
    val createdAt: Long,
    val attachments: List<AttachmentInfo> = emptyList(),
    val sources: List<WebSource> = emptyList(),
    val imagePath: String? = null,
)

data class NewMessage(
    val conversationId: String,
    val role: Role,
    val text: String,
    val createdAt: Long,
    val attachments: List<AttachmentInfo> = emptyList(),
    val sources: List<WebSource> = emptyList(),
    val imagePath: String? = null,
)

data class AppSettings(
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val webSearchEnabled: Boolean = false,
    val darkTheme: Boolean = true,
) {
    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-5-5"

        val DEFAULT_SYSTEM_PROMPT: String = """
            Eres Z4firo, un asistente de IA con personalidad: ingenio seco, humor ligero y
            alguna frase memorable, pero nunca cruel ni sarcástico con la persona que pregunta.
            Respondes con precisión y honestidad; si no sabes algo, lo dices sin rodeos y sin
            inventar datos. El humor acompaña la respuesta, nunca la sustituye. Responde en el
            mismo idioma que el usuario. Usa markdown con moderación y listas solo cuando aporten claridad.
        """.trimIndent()
    }
}
