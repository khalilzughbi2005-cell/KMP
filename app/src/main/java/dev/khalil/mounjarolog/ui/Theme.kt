package dev.khalil.mounjarolog.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B52),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA8F2D5),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF4C635A),
    secondaryContainer = Color(0xFFCFE9DC),
    tertiary = Color(0xFF3F6374),
    surface = Color(0xFFF8FAF8),
    surfaceContainer = Color(0xFFEDF2EF),
    surfaceContainerHigh = Color(0xFFE6ECE8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD6BA),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00513E),
    onPrimaryContainer = Color(0xFFA8F2D5),
    secondary = Color(0xFFB3CCC0),
    secondaryContainer = Color(0xFF354B42),
    tertiary = Color(0xFFA7CDDF),
    surface = Color(0xFF101512),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28)
)

@Composable
fun MounjaroLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
