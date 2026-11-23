package com.dremarc.grokbot.domain.imagegen

/**
 * Pluggable image generation. Claude cannot create images, so the app delegates to
 * an implementation (by default a free third-party service, see the README).
 */
interface ImageGenerator {
    suspend fun generate(prompt: String): Result<GeneratedImage>
}

class GeneratedImage(
    val bytes: ByteArray,
    val mimeType: String,
)
