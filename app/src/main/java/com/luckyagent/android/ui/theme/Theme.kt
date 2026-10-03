package com.luckyagent.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CloverShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

private val LightColors = lightColorScheme(
    primary = CloverAccent,
    onPrimary = Color.White,
    primaryContainer = CloverLeaf,
    onPrimaryContainer = CloverLeafInk,
    secondary = CloverLeaf,
    onSecondary = CloverLeafInk,
    secondaryContainer = Color(0xFFE8F0DE),
    onSecondaryContainer = CloverAccent,
    tertiary = CloverAccent,
    tertiaryContainer = Color(0xFFE8F0DE),
    onTertiaryContainer = CloverAccent,
    outlineVariant = CloverLine,
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
    primary = CloverDarkAccent,
    onPrimary = CloverLeafInk,
    primaryContainer = CloverAccent,
    onPrimaryContainer = Color.White,
    secondary = CloverAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF30422E),
    onSecondaryContainer = CloverDarkAccent,
    tertiary = CloverDarkAccent,
    tertiaryContainer = Color(0xFF30422E),
    onTertiaryContainer = CloverDarkAccent,
    outlineVariant = CloverDarkLine,
    background = CloverDarkBg,
    onBackground = CloverDarkText,
    surface = CloverDarkSurface,
    onSurface = CloverDarkText,
    surfaceVariant = CloverDarkSurface2,
    onSurfaceVariant = CloverDarkText2,
    outline = CloverDarkLine,
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
        shapes = CloverShapes,
        content = content,
    )
}
