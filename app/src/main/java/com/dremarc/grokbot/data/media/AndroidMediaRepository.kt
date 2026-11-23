package com.dremarc.grokbot.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import com.dremarc.grokbot.domain.model.Attachment
import com.dremarc.grokbot.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max

class AndroidMediaRepository(private val context: Context) : MediaRepository {

    private val generatedDir = File(context.filesDir, "generated").apply { mkdirs() }

    override suspend fun readAttachment(uri: String): Result<Attachment> = withContext(Dispatchers.IO) {
        try {
            val parsed = Uri.parse(uri)
            val mime = context.contentResolver.getType(parsed) ?: "application/octet-stream"
            val name = queryDisplayName(parsed) ?: "adjunto"
            val attachment = when {
                mime.startsWith("image/") -> Attachment.Image(
                    name = name,
                    mediaType = "image/jpeg",
                    base64 = Base64.encodeToString(compressImage(parsed), Base64.NO_WRAP),
                )
                mime == "application/pdf" -> Attachment.Pdf(
                    name = name,
                    base64 = Base64.encodeToString(readBounded(parsed, MAX_PDF_BYTES), Base64.NO_WRAP),
                )
                mime.startsWith("text/") || mime == "application/json" -> Attachment.Text(
                    name = name,
                    content = readBounded(parsed, MAX_TEXT_BYTES).toString(Charsets.UTF_8),
                )
                else -> throw IllegalArgumentException("Tipo de archivo no soportado: $mime")
            }
            Result.success(attachment)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveImage(bytes: ByteArray, mimeType: String): String = withContext(Dispatchers.IO) {
        val extension = if (mimeType.contains("png")) "png" else "jpg"
        val file = File(generatedDir, "img-${UUID.randomUUID()}.$extension")
        file.writeBytes(bytes)
        file.absolutePath
    }

    override suspend fun exportImage(localPath: String, destinationUri: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val output = context.contentResolver.openOutputStream(Uri.parse(destinationUri))
                    ?: throw IllegalStateException("No se puede escribir en el destino elegido")
                output.use { stream ->
                    File(localPath).inputStream().use { input -> input.copyTo(stream) }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    /** Reads at most [limit] bytes; fails early instead of loading a huge file. */
    private fun readBounded(uri: Uri, limit: Int): ByteArray {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("No se puede abrir el archivo")
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                total += read
                require(total <= limit) { "Archivo demasiado grande (máximo ${limit / (1024 * 1024)} MB)" }
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    /**
     * Downscales to [MAX_IMAGE_SIDE] on the long edge, applies EXIF rotation (camera photos)
     * and re-encodes as JPEG so the base64 payload stays small.
     */
    private fun compressImage(uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longEdge = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)

        var sample = 1
        while (longEdge / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
            ?: throw IllegalStateException("No se pudo leer la imagen")

        val rotation = exifRotation(uri)
        val scale = MAX_IMAGE_SIDE.toFloat() / max(decoded.width, decoded.height)
        val needsTransform = rotation != 0f || scale < 1f
        val bitmap = if (needsTransform) {
            val matrix = Matrix().apply {
                if (rotation != 0f) postRotate(rotation)
                if (scale < 1f) postScale(scale, scale)
            }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else {
            decoded
        }

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (bitmap !== decoded) bitmap.recycle()
        decoded.recycle()
        return out.toByteArray()
    }

    private fun exifRotation(uri: Uri): Float {
        val stream = context.contentResolver.openInputStream(uri) ?: return 0f
        return stream.use { input ->
            when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }
    }

    private companion object {
        const val MAX_IMAGE_SIDE = 1568
        const val JPEG_QUALITY = 85
        const val MAX_PDF_BYTES = 20 * 1024 * 1024
        const val MAX_TEXT_BYTES = 512 * 1024
        const val BUFFER_SIZE = 8 * 1024
    }
}
