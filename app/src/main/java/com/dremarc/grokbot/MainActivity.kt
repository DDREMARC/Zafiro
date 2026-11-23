package com.dremarc.grokbot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dremarc.grokbot.ui.navigation.AppNavHost
import com.dremarc.grokbot.ui.theme.GrokBotTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as GrokBotApplication).container

        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
            GrokBotTheme(darkTheme = settings.darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(container = container)
                }
            }
        }
    }
}
