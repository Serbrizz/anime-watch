package com.example.animewatch.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.animewatch.R

/**
 * Lo stile della finestra degli script (scripts/gui.ps1): cielo di notte, colori piatti come nella cel animation,
 * contorni "a china" e ombre nette, niente bagliori. Sempre scuro, anche col tema chiaro del telefono, e senza
 * Material You: i colori presi dallo sfondo dell'utente lo cancellerebbero.
 */
object Night {
    /** Contorni e ombre: quasi nero, con una punta di blu. */
    val Ink = Color(0xFF0A0D1E)
    val Background = Color(0xFF141A33)
    val Panel = Color(0xFF1A2142)
    val Raised = Color(0xFF252D55)
    val Line = Color(0xFF3A4475)
    val Text = Color(0xFFFFF6E5)
    val Muted = Color(0xFFC2C6DD)
    val Faint = Color(0xFF8A90B4)

    val Accent = Color(0xFFF2994A)
    val AccentInk = Color(0xFF2B1400)
    val AccentSoft = Color(0xFF3B2E3A)
    val AccentText = Color(0xFFF7B977)
    val Blue = Color(0xFF5B9BD5)
    val Sakura = Color(0xFFEE8FA6)
    val Yellow = Color(0xFFF2C94C)
    val Red = Color(0xFFF28B82)
    val Moon = Color(0xFFFFF1C9)

    /** Il cielo dall'alto in basso: indaco, poi l'orizzonte caldo. */
    val Sky = arrayOf(
        0f to Color(0xFF121833),
        0.55f to Color(0xFF1C2347),
        0.85f to Color(0xFF3A2E52),
        1f to Color(0xFF6B4A4A),
    )
}

private val NightColors = darkColorScheme(
    primary = Night.Accent,
    onPrimary = Night.AccentInk,
    primaryContainer = Night.AccentSoft,
    onPrimaryContainer = Night.AccentText,
    inversePrimary = Color(0xFF8A4A12),
    secondary = Night.Blue,
    onSecondary = Color(0xFF0A1A2E),
    secondaryContainer = Color(0xFF22355A),
    onSecondaryContainer = Color(0xFFCFE3F7),
    tertiary = Night.Sakura,
    onTertiary = Color(0xFF2B0E15),
    tertiaryContainer = Color(0xFF4A2633),
    onTertiaryContainer = Color(0xFFFFD9E1),
    background = Night.Background,
    onBackground = Night.Text,
    surface = Night.Panel,
    onSurface = Night.Text,
    surfaceVariant = Night.Raised,
    onSurfaceVariant = Night.Muted,
    // Material schiarisce le superfici rialzate col colore primario: qui le superfici restano piatte.
    surfaceTint = Night.Panel,
    inverseSurface = Night.Text,
    inverseOnSurface = Night.Ink,
    error = Night.Red,
    onError = Color(0xFF3B0A0A),
    errorContainer = Color(0xFF4A2230),
    onErrorContainer = Color(0xFFFFD6D2),
    outline = Night.Line,
    outlineVariant = Color(0xFF2C3563),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2C3563),
    surfaceDim = Night.Background,
    surfaceContainerLowest = Color(0xFF10152B),
    surfaceContainerLow = Color(0xFF171D3B),
    surfaceContainer = Night.Panel,
    surfaceContainerHigh = Color(0xFF20284C),
    surfaceContainerHighest = Night.Raised,
)

/**
 * Nunito, lo stesso della finestra degli script (licenza OFL). I file sono quelli di scripts/fonts, ridotti a latino e
 * kana: un carattere che manca arriva dal font di sistema.
 */
private val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
)

private fun TextStyle.nunito(weight: FontWeight? = null) = copy(fontFamily = Nunito, fontWeight = weight ?: fontWeight)

private val NightTypography = Typography().run {
    copy(
        displayLarge = displayLarge.nunito(FontWeight.Bold),
        displayMedium = displayMedium.nunito(FontWeight.Bold),
        displaySmall = displaySmall.nunito(FontWeight.Bold),
        headlineLarge = headlineLarge.nunito(FontWeight.Bold),
        headlineMedium = headlineMedium.nunito(FontWeight.Bold),
        headlineSmall = headlineSmall.nunito(FontWeight.Bold),
        titleLarge = titleLarge.nunito(FontWeight.Bold),
        titleMedium = titleMedium.nunito(FontWeight.Bold),
        titleSmall = titleSmall.nunito(FontWeight.Bold),
        bodyLarge = bodyLarge.nunito(),
        bodyMedium = bodyMedium.nunito(),
        bodySmall = bodySmall.nunito(),
        labelLarge = labelLarge.nunito(FontWeight.Bold),
        labelMedium = labelMedium.nunito(FontWeight.SemiBold),
        labelSmall = labelSmall.nunito(FontWeight.SemiBold),
    )
}

private val NightShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun AnimeWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NightColors, typography = NightTypography, shapes = NightShapes) {
        // Gli Scaffold sono trasparenti (dietro c'è il cielo) e per un fondo trasparente Material non sa che colore
        // dare al testo: prenderebbe quello predefinito, nero. Il testo sul cielo è chiaro.
        CompositionLocalProvider(LocalContentColor provides Night.Text, content = content)
    }
}
