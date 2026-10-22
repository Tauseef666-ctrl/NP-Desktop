package net.newpipe.app.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NewPipeDarkColors = darkColorScheme(
    primary = Color(0xFFFF0000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8C0000),
    onPrimaryContainer = Color(0xFFFFDAD4),
    secondary = Color(0xFFB0BEC5),
    onSecondary = Color(0xFF1C313A),
    secondaryContainer = Color(0xFF334A53),
    onSecondaryContainer = Color(0xFFB9CAD3),
    background = Color(0xFF0F0F0F),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF141414),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF2F2F2F),
    outlineVariant = Color(0xFF252525),
    surfaceContainer = Color(0xFF161616),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF222222),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFFFFFFFF),
)

val NewPipeLightColors = lightColorScheme(
    primary = Color(0xFFCD201F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD4),
    onPrimaryContainer = Color(0xFF410000),
    secondary = Color(0xFF546E7A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEFF8),
    onSecondaryContainer = Color(0xFF0F1D24),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1C1C1C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF424242),
    outline = Color(0xFFE0E0E0),
    outlineVariant = Color(0xFFEEEEEE),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFAFAFA),
    surfaceContainerHighest = Color(0xFFF5F5F5),
)

@Composable
fun getNewPipeColorScheme(dark: Boolean): ColorScheme = if (dark) NewPipeDarkColors else NewPipeLightColors
