package com.dremarc.grokbot.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** One-shot UI messages, shown as snackbars. */
sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
}

/** Builds a ViewModel factory from a lambda, without a DI framework. */
fun <VM : ViewModel> viewModelFactoryOf(build: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = build() as T
    }
