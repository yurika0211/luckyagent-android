package com.luckyagent.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = CloverAccent,
    onPrimary = Color.White,
    primaryContainer = CloverLeaf,
    onPrimaryContainer = CloverLeafInk,
    secondary = CloverLeaf,
    onSecondary = CloverLeafInk,
    background = CloverBg,
    onBackground = CloverText,
    surface = CloverSurface,
    onSurface = CloverText,
    surfaceVariant = CloverSurface2,
    onSurfaceVariant = CloverText2,
    outline = CloverLine,
    error = CloverError,
)

private val DarkColors = darkColorScheme(
    primary = CloverLeaf,
    onPrimary = CloverLeafInk,
    primaryContainer = CloverAccent,
    onPrimaryContainer = Color.White,
    secondary = CloverAccent,
    onSecondary = Color.White,
    background = Color(0xFF12140F),
    onBackground = Color(0xFFE8ECDC),
    surface = Color(0xFF1A1D15),
    onSurface = Color(0xFFE8ECDC),
    surfaceVariant = Color(0xFF262B1E),
    onSurfaceVariant = Color(0xFFBFC4B0),
    outline = Color(0xFF3A4030),
    error = Color(0xFFFFB4AB),
)

@Composable
fun CloverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CloverTypography,
        content = content,
    )
}
