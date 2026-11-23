package com.dremarc.grokbot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark is the default: near-black background with soft white text, Grok style.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7CCFF),
    onPrimary = Color(0xFF0B1F40),
    primaryContainer = Color(0xFF1E2F4D),
    onPrimaryContainer = Color(0xFFD9E4FF),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293041),
    background = Color(0xFF0B0B0F),
    onBackground = Color(0xFFE6E6EA),
    surface = Color(0xFF0B0B0F),
    onSurface = Color(0xFFE6E6EA),
    surfaceVariant = Color(0xFF1C1C24),
    onSurfaceVariant = Color(0xFFB9B9C4),
    outline = Color(0xFF5A5A66),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2B5CD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0D2A66),
)

@Composable
fun GrokBotTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
