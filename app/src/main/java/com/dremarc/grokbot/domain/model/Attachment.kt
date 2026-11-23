package com.dremarc.grokbot.domain.model

/** A file the user attached to the next message, already prepared for the API. */
sealed interface Attachment {
    val name: String

    /** Image re-encoded as JPEG and base64 encoded (sent as an `image` block). */
    data class Image(
        override val name: String,
        val mediaType: String,
        val base64: String,
    ) : Attachment

    /** PDF encoded as base64 (sent as a `document` block). */
    data class Pdf(
        override val name: String,
        val base64: String,
    ) : Attachment

    /** Plain text read as UTF-8 and inlined into the prompt. */
    data class Text(
        override val name: String,
        val content: String,
    ) : Attachment
}

fun Attachment.toInfo(): AttachmentInfo = AttachmentInfo(
    name = name,
    kind = when (this) {
        is Attachment.Image -> AttachmentKind.IMAGE
        is Attachment.Pdf -> AttachmentKind.PDF
        is Attachment.Text -> AttachmentKind.TEXT
    },
)
