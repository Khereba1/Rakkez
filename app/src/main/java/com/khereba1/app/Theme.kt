package com.khereba1.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}
