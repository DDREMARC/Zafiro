package com.dremarc.grokbot.ui.chat.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dremarc.grokbot.domain.model.AttachmentInfo
import com.dremarc.grokbot.domain.model.AttachmentKind
import com.dremarc.grokbot.domain.model.WebSource
import com.dremarc.grokbot.ui.common.copyToClipboard
import com.dremarc.grokbot.ui.common.openUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun UserMessage(
    text: String,
    attachments: List<AttachmentInfo>,
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                attachments.forEach { AttachmentLabel(it) }
                if (text.isNotBlank()) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
fun AssistantMessage(
    text: String,
    sources: List<WebSource>,
    imagePath: String?,
    isStreaming: Boolean,
    isSpeaking: Boolean,
    onSpeak: () -> Unit,
    onStopSpeaking: () -> Unit,
    onSaveImage: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (imagePath != null) {
            GeneratedImage(imagePath)
        }
        if (text.isNotBlank()) {
            MarkdownText(text = if (isStreaming) "$text ▍" else text)
        } else if (isStreaming && imagePath == null) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        if (sources.isNotEmpty()) {
            SourceChips(sources = sources, onOpen = { openUrl(context, it.url) })
        }
        if (!isStreaming) {
            Row {
                IconButton(onClick = { copyToClipboard(context, text) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar mensaje")
                }
                IconButton(onClick = if (isSpeaking) onStopSpeaking else onSpeak) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                        contentDescription = if (isSpeaking) "Detener lectura" else "Leer en voz alta",
                    )
                }
                if (imagePath != null) {
                    IconButton(onClick = { onSaveImage(imagePath) }) {
                        Icon(Icons.Filled.Download, contentDescription = "Guardar imagen")
                    }
                }
            }
        }
    }
}

@Composable
fun SourceChips(
    sources: List<WebSource>,
    onOpen: (WebSource) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sources.forEach { source ->
            AssistChip(
                onClick = { onOpen(source) },
                label = {
                    Text(text = source.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingIcon = {
                    Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier.widthIn(max = 240.dp),
            )
        }
    }
}

@Composable
private fun AttachmentLabel(info: AttachmentInfo) {
    val icon = when (info.kind) {
        AttachmentKind.IMAGE -> Icons.Filled.ImageIcon
        AttachmentKind.PDF -> Icons.Filled.PictureAsPdf
        AttachmentKind.TEXT -> Icons.Filled.Description
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            text = info.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GeneratedImage(path: String) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        }
    }
    bitmap?.let { image ->
        Image(
            bitmap = image,
            contentDescription = "Imagen generada",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
        )
    }
}
