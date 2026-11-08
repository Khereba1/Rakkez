package com.khereba1.app

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// "Ink and saffron": warm paper in the light theme, deep ink in the dark theme.
// Amber = focus, mint = break.
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFB547),
    onPrimary = Color(0xFF1A1100),
    primaryContainer = Color(0xFF3A2A0B),
    onPrimaryContainer = Color(0xFFFFD99A),
    secondary = Color(0xFF9AA3AE),
    onSecondary = Color(0xFF0D1015),
    tertiary = Color(0xFF5EE0B1),
    onTertiary = Color(0xFF00241A),
    tertiaryContainer = Color(0xFF0F3A2D),
    onTertiaryContainer = Color(0xFFB5F5DD),
    background = Color(0xFF0D1015),
    onBackground = Color(0xFFECE8DF),
    surface = Color(0xFF151A21),
    onSurface = Color(0xFFECE8DF),
    surfaceVariant = Color(0xFF1E252E),
    onSurfaceVariant = Color(0xFF9AA3AE),
    outline = Color(0xFF3A4350),
    outlineVariant = Color(0xFF262E38),
    error = Color(0xFFFF7A70),
    onError = Color(0xFF2A0603),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFA85F00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE2B0),
    onPrimaryContainer = Color(0xFF3D2500),
    secondary = Color(0xFF6B665A),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF0E8F68),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC4F0DE),
    onTertiaryContainer = Color(0xFF002118),
    background = Color(0xFFF6F1E6),
    onBackground = Color(0xFF1D1B16),
    surface = Color(0xFFFFFBF3),
    onSurface = Color(0xFF1D1B16),
    surfaceVariant = Color(0xFFECE5D5),
    onSurfaceVariant = Color(0xFF6B665A),
    outline = Color(0xFFCFC6B2),
    outlineVariant = Color(0xFFE3DAC6),
    error = Color(0xFFC0392B),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun RakkezTheme(content: @Composable () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ff = remember { appFontFamily(ctx) }
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        typography = typographyFor(ff),
        content = content,
    )
}

val CairoFamily = FontFamily(
    Font(R.font.cairo_light, FontWeight.Light),
    Font(R.font.cairo_regular, FontWeight.Normal),
    Font(R.font.cairo_medium, FontWeight.Medium),
    Font(R.font.cairo_semibold, FontWeight.SemiBold),
    Font(R.font.cairo_bold, FontWeight.Bold),
)

val PlexFamily = FontFamily(
    Font(R.font.plex_light, FontWeight.Light),
    Font(R.font.plex_regular, FontWeight.Normal),
    Font(R.font.plex_medium, FontWeight.Medium),
    Font(R.font.plex_semibold, FontWeight.SemiBold),
    Font(R.font.plex_bold, FontWeight.Bold),
)

/** "cairo" (default), "plex" or "system" (the phone's own font). */
fun fontKey(c: Context): String = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("font", "cairo") ?: "cairo"

fun appFontFamily(c: Context): FontFamily? = when (fontKey(c)) {
    "plex" -> PlexFamily
    "system" -> null
    else -> CairoFamily
}

private fun typographyFor(ff: FontFamily?): Typography {
    val d = Typography()
    if (ff == null) return d
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = ff),
        displayMedium = d.displayMedium.copy(fontFamily = ff),
        displaySmall = d.displaySmall.copy(fontFamily = ff),
        headlineLarge = d.headlineLarge.copy(fontFamily = ff),
        headlineMedium = d.headlineMedium.copy(fontFamily = ff),
        headlineSmall = d.headlineSmall.copy(fontFamily = ff),
        titleLarge = d.titleLarge.copy(fontFamily = ff),
        titleMedium = d.titleMedium.copy(fontFamily = ff),
        titleSmall = d.titleSmall.copy(fontFamily = ff),
        bodyLarge = d.bodyLarge.copy(fontFamily = ff),
        bodyMedium = d.bodyMedium.copy(fontFamily = ff),
        bodySmall = d.bodySmall.copy(fontFamily = ff),
        labelLarge = d.labelLarge.copy(fontFamily = ff),
        labelMedium = d.labelMedium.copy(fontFamily = ff),
        labelSmall = d.labelSmall.copy(fontFamily = ff),
    )
}
