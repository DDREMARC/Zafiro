package com.dremarc.grokbot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dremarc.grokbot.domain.model.AppSettings
import com.dremarc.grokbot.ui.common.UiEvent

private val MODEL_SUGGESTIONS = listOf("claude-sonnet-5-5", "claude-haiku-5-5")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showKey by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            if (event is UiEvent.Message) snackbarHostState.showSnackbar(event.text)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Anthropic", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = draft.apiKey,
                onValueChange = { value -> viewModel.edit { it.copy(apiKey = value) } },
                label = { Text("API key") },
                placeholder = { Text("sk-ant-…") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            imageVector = if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showKey) "Ocultar" else "Mostrar",
                        )
                    }
                },
                supportingText = {
                    Text("Se guarda cifrada en este dispositivo. Nunca se incluye en la app.")
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = draft.model,
                onValueChange = { value -> viewModel.edit { it.copy(model = value) } },
                label = { Text("ID del modelo") },
                singleLine = true,
                supportingText = { Text("Por defecto: ${AppSettings.DEFAULT_MODEL}") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                MODEL_SUGGESTIONS.forEach { model ->
                    FilterChip(
                        selected = draft.model == model,
                        onClick = { viewModel.edit { it.copy(model = model) } },
                        label = { Text(model) },
                    )
                }
            }

            OutlinedTextField(
                value = draft.systemPrompt,
                onValueChange = { value -> viewModel.edit { it.copy(systemPrompt = value) } },
                label = { Text("Prompt del sistema (personalidad)") },
                minLines = 4,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { viewModel.restoreDefaultPrompt() }) {
                Text("Restaurar personalidad por defecto")
            }

            HorizontalDivider()

            SwitchRow(
                title = "Búsqueda web activada por defecto",
                checked = draft.webSearchEnabled,
                onCheckedChange = { value -> viewModel.edit { it.copy(webSearchEnabled = value) } },
            )
            SwitchRow(
                title = "Tema oscuro",
                checked = draft.darkTheme,
                onCheckedChange = { value -> viewModel.edit { it.copy(darkTheme = value) } },
            )

            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Guardar")
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
