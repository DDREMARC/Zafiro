package com.dremarc.grokbot.ui.settings

import androidx.lifecycle.ViewModel
import com.dremarc.grokbot.domain.model.AppSettings
import com.dremarc.grokbot.domain.repository.SettingsRepository
import com.dremarc.grokbot.ui.common.UiEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.receiveAsFlow

/** Holds an editable copy of the settings; nothing is persisted until [save]. */
class SettingsViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {

    private val editing = MutableStateFlow(settings.settings.value)
    val draft: StateFlow<AppSettings> = editing.asStateFlow()

    private val events = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents: Flow<UiEvent> = events.receiveAsFlow()

    fun edit(transform: (AppSettings) -> AppSettings) {
        editing.update(transform)
    }

    fun restoreDefaultPrompt() {
        editing.update { it.copy(systemPrompt = AppSettings.DEFAULT_SYSTEM_PROMPT) }
    }

    fun save() {
        settings.save(editing.value)
        events.trySend(UiEvent.Message("Ajustes guardados"))
    }
}
