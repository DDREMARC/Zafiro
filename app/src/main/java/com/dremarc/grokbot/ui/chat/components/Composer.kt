package com.dremarc.grokbot.ui.chat.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dremarc.grokbot.domain.model.Attachment
import androidx.compose.material.icons.automirrored.filled.Send

@Composable
fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    attachments: List<Attachment>,
    onRemoveAttachment: (Int) -> Unit,
    webSearchEnabled: Boolean,
    onToggleWebSearch: (Boolean) -> Unit,
    isStreaming: Boolean,
    isGeneratingImage: Boolean,
    isListening: Boolean,
    onAttachGallery: () -> Unit,
    onAttachCamera: () -> Unit,
    onAttachDocument: () -> Unit,
    onMicClick: () -> Unit,
    onImageClick: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    var attachMenuOpen by remember { mutableStateOf(false) }
    val canSend = draft.isNotBlank() || attachments.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = webSearchEnabled,
                onClick = { onToggleWebSearch(!webSearchEnabled) },
                label = { Text("Búsqueda web") },
                leadingIcon = {
                    Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
            attachments.forEachIndexed { index, attachment ->
                InputChip(
                    selected = false,
                    onClick = { onRemoveAttachment(index) },
                    label = { Text(attachment.name, maxLines = 1) },
                    trailingIcon = {
                        Icon(Icons.Filled.Close, contentDescription = "Quitar adjunto", modifier = Modifier.size(16.dp))
                    },
                )
            }
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box {
                IconButton(onClick = { attachMenuOpen = true }) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "Adjuntar")
                }
                DropdownMenu(expanded = attachMenuOpen, onDismissRequest = { attachMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Imagen de la galería") },
                        leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                        onClick = {
                            attachMenuOpen = false
                            onAttachGallery()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Hacer una foto") },
                        leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                        onClick = {
                            attachMenuOpen = false
                            onAttachCamera()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("PDF o archivo de texto") },
                        leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                        onClick = {
                            attachMenuOpen = false
                            onAttachDocument()
                        },
                    )
                }
            }

            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text("Pregunta a Z4firo… (o escribe /imagine)") },
                maxLines = 6,
                modifier = Modifier.weight(1f),
            )

            IconButton(onClick = onMicClick) {
                Icon(
                    imageVector = if (isListening) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = if (isListening) "Detener dictado" else "Dictar por voz",
                    tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }

            IconButton(onClick = onImageClick, enabled = !isGeneratingImage) {
                Icon(Icons.Filled.Image, contentDescription = "Generar imagen (/imagine)")
            }

            if (isStreaming) {
                FilledIconButton(onClick = onStop) {
                    Icon(Icons.Filled.Stop, contentDescription = "Detener respuesta")
                }
            } else {
                FilledIconButton(onClick = onSend, enabled = canSend) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                }
            }
        }
    }
}
