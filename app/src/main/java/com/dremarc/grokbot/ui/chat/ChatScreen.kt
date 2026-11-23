package com.dremarc.grokbot.ui.chat

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dremarc.grokbot.data.voice.SpeechInput
import com.dremarc.grokbot.data.voice.TtsController
import com.dremarc.grokbot.domain.model.Conversation
import com.dremarc.grokbot.domain.model.Role
import com.dremarc.grokbot.ui.chat.components.AssistantMessage
import com.dremarc.grokbot.ui.chat.components.Composer
import com.dremarc.grokbot.ui.chat.components.ConversationDrawer
import com.dremarc.grokbot.ui.chat.components.DeleteDialog
import com.dremarc.grokbot.ui.chat.components.RenameDialog
import com.dremarc.grokbot.ui.chat.components.UserMessage
import com.dremarc.grokbot.ui.common.UiEvent
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val speech = remember { SpeechInput(context.applicationContext) }
    val tts = remember { TtsController(context.applicationContext) }
    val speakingId by tts.speakingId.collectAsStateWithLifecycle()

    var listening by remember { mutableStateOf(false) }
    var draftBeforeSpeech by remember { mutableStateOf("") }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingSavePath by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            speech.release()
            tts.shutdown()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            if (event is UiEvent.Message) snackbarHostState.showSnackbar(event.text)
        }
    }

    // Keep the newest content visible while streaming unless the user scrolled far up.
    LaunchedEffect(state.messages.size, state.streamingText?.length, state.isGeneratingImage) {
        if (listState.firstVisibleItemIndex < 3) listState.animateScrollToItem(0)
    }

    // Speech-to-text: the recognised text is appended to whatever was typed before.
    fun startListening() {
        val base = state.draft
        draftBeforeSpeech = if (base.isBlank()) "" else base.trimEnd() + " "
        listening = true
        speech.start(
            onPartialText = { viewModel.onDraftChange(draftBeforeSpeech + it) },
            onFinalText = { viewModel.onDraftChange(draftBeforeSpeech + it) },
            onFailure = { message ->
                listening = false
                viewModel.notify(message)
            },
            onFinished = { listening = false },
        )
    }

    val requestMicPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startListening() else viewModel.notify("Permiso de micrófono denegado")
    }

    fun onMicClick() {
        if (listening) {
            speech.stop()
            listening = false
            return
        }
        if (!speech.isAvailable()) {
            viewModel.notify("Reconocimiento de voz no disponible en este dispositivo")
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) startListening() else requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.addAttachment(uri.toString())
    }
    val pickDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.addAttachment(uri.toString())
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) viewModel.addAttachment(uri.toString())
    }
    val saveImage = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/jpeg"),
    ) { uri ->
        val path = pendingSavePath
        if (uri != null && path != null) viewModel.exportImage(path, uri.toString())
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraUri = uri
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            viewModel.notify("No hay ninguna aplicación de cámara disponible")
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawer(
                conversations = state.conversations,
                activeId = state.activeConversationId,
                onNewChat = {
                    viewModel.newChat()
                    scope.launch { drawerState.close() }
                },
                onSelect = { id ->
                    viewModel.selectConversation(id)
                    scope.launch { drawerState.close() }
                },
                onRename = { renameTarget = it },
                onDelete = { deleteTarget = it },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = state.activeTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Abrir conversaciones")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.newChat() }) {
                            Icon(Icons.Filled.Add, contentDescription = "Nuevo chat")
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Composer(
                    draft = state.draft,
                    onDraftChange = viewModel::onDraftChange,
                    attachments = state.attachments,
                    onRemoveAttachment = viewModel::removeAttachment,
                    webSearchEnabled = state.webSearchEnabled,
                    onToggleWebSearch = viewModel::setWebSearch,
                    isStreaming = state.isStreaming,
                    isGeneratingImage = state.isGeneratingImage,
                    isListening = listening,
                    onAttachGallery = {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onAttachCamera = { launchCamera() },
                    onAttachDocument = {
                        pickDocument.launch(arrayOf("application/pdf", "text/*", "application/json"))
                    },
                    onMicClick = { onMicClick() },
                    onImageClick = { viewModel.onImageButton() },
                    onSend = { viewModel.send() },
                    onStop = { viewModel.stopGeneration() },
                )
            },
        ) { padding ->
            val streamingText = state.streamingText
            if (state.messages.isEmpty() && streamingText == null && !state.isGeneratingImage) {
                EmptyState(modifier = Modifier.padding(padding).fillMaxSize())
            } else {
                // reverseLayout keeps the newest item anchored at the bottom.
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (state.isGeneratingImage) {
                        item(key = "generating-image") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Generando imagen…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (streamingText != null) {
                        item(key = "streaming") {
                            AssistantMessage(
                                text = streamingText,
                                sources = state.streamingSources,
                                imagePath = null,
                                isStreaming = true,
                                isSpeaking = false,
                                onSpeak = {},
                                onStopSpeaking = {},
                                onSaveImage = {},
                            )
                        }
                    }
                    items(state.messages.asReversed(), key = { it.id }) { message ->
                        if (message.role == Role.USER) {
                            UserMessage(text = message.text, attachments = message.attachments)
                        } else {
                            AssistantMessage(
                                text = message.text,
                                sources = message.sources,
                                imagePath = message.imagePath,
                                isStreaming = false,
                                isSpeaking = speakingId == message.id.toString(),
                                onSpeak = { tts.speak(message.id, message.text) },
                                onStopSpeaking = { tts.stop() },
                                onSaveImage = { path ->
                                    pendingSavePath = path
                                    saveImage.launch("grok-bot-${message.id}.jpg")
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            initialTitle = target.title,
            onConfirm = { newTitle ->
                viewModel.renameConversation(target.id, newTitle)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { target ->
        DeleteDialog(
            title = target.title,
            onConfirm = {
                viewModel.deleteConversation(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Z4firo",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Pregúntame lo que quieras. Activa la búsqueda web para datos recientes " +
                "o escribe /imagine seguido de una idea para generar una imagen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
