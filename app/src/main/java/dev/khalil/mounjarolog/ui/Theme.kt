package dev.khalil.mounjarolog.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.khalil.mounjarolog.R

// "Plakat" palette: ink on white, one accent reserved for actions and "today".
// Weight changes are never coloured good/bad.
val Ink = Color(0xFF141414)
val Muted = Color(0xFF6E6E6A)
val Faint = Color(0xFF9A9A95)
val Hairline = Color(0xFFECECEA)
val Fill = Color(0xFFF1F1EF)
val Accent = Color(0xFFE8432A)
val Paper = Color.White

val DisplayFont = FontFamily(
    Font(R.font.funnel_display_light, FontWeight.Light),
    Font(R.font.funnel_display_semibold, FontWeight.SemiBold)
)

val SansFont = FontFamily(
    Font(R.font.funnel_sans_regular, FontWeight.Normal),
    Font(R.font.funnel_sans_medium, FontWeight.Medium),
    Font(R.font.funnel_sans_semibold, FontWeight.SemiBold),
    Font(R.font.funnel_sans_semibold, FontWeight.Bold)
)

private val Colors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Fill,
    onPrimaryContainer = Ink,
    secondary = Accent,
    onSecondary = Paper,
    secondaryContainer = Ink,
    onSecondaryContainer = Paper,
    tertiary = Muted,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    onSurfaceVariant = Muted,
    surfaceVariant = Fill,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Paper,
    surfaceContainer = Paper,
    surfaceContainerHigh = Paper,
    surfaceContainerHighest = Fill,
    outline = Faint,
    outlineVariant = Hairline,
    error = Color(0xFFB3261E)
)

private fun TextStyle.sans() = copy(fontFamily = SansFont, fontFeatureSettings = "tnum")

private val base = Typography()

private val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Light, fontSize = 104.sp, lineHeight = 96.sp, letterSpacing = (-5).sp, fontFeatureSettings = "tnum"),
    displayMedium = base.displayMedium.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Light, fontSize = 72.sp, lineHeight = 72.sp, letterSpacing = (-3).sp, fontFeatureSettings = "tnum"),
    headlineLarge = base.headlineLarge.copy(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-0.6).sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.4).sp, fontFeatureSettings = "tnum"),
    titleLarge = base.titleLarge.copy(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    titleMedium = base.titleMedium.sans().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.sans(),
    bodyLarge = base.bodyLarge.sans(),
    bodyMedium = base.bodyMedium.sans(),
    bodySmall = base.bodySmall.sans(),
    labelLarge = base.labelLarge.sans().copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.sans().copy(fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.sans()
)

@Composable
fun MounjaroLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = AppTypography, content = content)
}
