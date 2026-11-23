package com.dremarc.grokbot.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dremarc.grokbot.AppContainer
import com.dremarc.grokbot.ui.chat.ChatScreen
import com.dremarc.grokbot.ui.chat.ChatViewModel
import com.dremarc.grokbot.ui.common.viewModelFactoryOf
import com.dremarc.grokbot.ui.settings.SettingsScreen
import com.dremarc.grokbot.ui.settings.SettingsViewModel

private object Routes {
    const val CHAT = "chat"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.CHAT) {
        composable(Routes.CHAT) {
            val viewModel: ChatViewModel = viewModel(
                factory = viewModelFactoryOf {
                    ChatViewModel(
                        conversations = container.conversationRepository,
                        assistant = container.assistantRepository,
                        imageGenerator = container.imageGenerator,
                        media = container.mediaRepository,
                        settings = container.settingsRepository,
                    )
                },
            )
            ChatScreen(
                viewModel = viewModel,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = viewModelFactoryOf { SettingsViewModel(container.settingsRepository) },
            )
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
