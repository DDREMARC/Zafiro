package com.dremarc.grokbot.data.imagegen

import com.dremarc.grokbot.domain.imagegen.GeneratedImage
import com.dremarc.grokbot.domain.imagegen.ImageGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/**
 * Default [ImageGenerator]: the free, keyless Pollinations endpoint.
 * This is a separate third-party service (not Anthropic); see the README.
 */
class PollinationsImageGenerator(
    private val client: OkHttpClient,
) : ImageGenerator {

    override suspend fun generate(prompt: String): Result<GeneratedImage> = withContext(Dispatchers.IO) {
        try {
            // URLEncoder produces "+" for spaces; a path segment needs "%20".
            val encoded = URLEncoder.encode(prompt, Charsets.UTF_8.name()).replace("+", "%20")
            val request = Request.Builder()
                .url("$BASE_URL$encoded")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Pollinations respondió HTTP ${response.code}"))
                }
                val body = response.body
                    ?: return@withContext Result.failure(IOException("Respuesta vacía de Pollinations"))
                val contentType = body.contentType()
                val mimeType = contentType?.let { "${it.type}/${it.subtype}" } ?: "image/jpeg"
                if (!mimeType.startsWith("image/")) {
                    return@withContext Result.failure(IOException("La respuesta no es una imagen ($mimeType)"))
                }
                Result.success(GeneratedImage(bytes = body.bytes(), mimeType = mimeType))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val BASE_URL = "https://image.pollinations.ai/prompt/"
    }
}
