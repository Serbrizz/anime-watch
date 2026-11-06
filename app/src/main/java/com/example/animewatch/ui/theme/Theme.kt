package com.example.animewatch.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF4527A0),
    secondary = Color(0xFF7E57C2),
    tertiary = Color(0xFFE91E63),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB39DDB),
    secondary = Color(0xFFD1C4E9),
    tertiary = Color(0xFFF48FB1),
)

@Composable
fun AnimeWatchTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        // Android 12+: colori presi dallo sfondo dell'utente (Material You).
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
