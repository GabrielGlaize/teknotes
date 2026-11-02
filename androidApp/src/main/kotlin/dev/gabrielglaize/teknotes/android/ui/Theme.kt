package dev.gabrielglaize.teknotes.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Paper and ink. The numbers carry the design: big ranks, big scores, thin rules, no colour.

val Archivo = FontFamily.Default

private val darkColors = darkColorScheme(
    primary = Color(0xFFF1EEE6),
    onPrimary = Color(0xFF121210),
    background = Color(0xFF121210),
    onBackground = Color(0xFFF1EEE6),
    surface = Color(0xFF121210),
    onSurface = Color(0xFFF1EEE6),
    surfaceContainer = Color(0xFF201F1C),
    surfaceContainerHigh = Color(0xFF201F1C),
    surfaceContainerHighest = Color(0xFF34332F),
    onSurfaceVariant = Color(0xFF96928A),
    outline = Color(0xFF34332F),
    outlineVariant = Color(0xFF34332F),
    secondaryContainer = Color(0xFF34332F),
)

private val lightColors = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color(0xFFF1EEE6),
    background = Color(0xFFF1EEE6),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFF1EEE6),
    onSurface = Color(0xFF111111),
    surfaceContainer = Color(0xFFE6E2D7),
    surfaceContainerHigh = Color(0xFFE6E2D7),
    surfaceContainerHighest = Color(0xFFD2CDC0),
    onSurfaceVariant = Color(0xFF6E6A61),
    outline = Color(0xFFD2CDC0),
    outlineVariant = Color(0xFFD2CDC0),
    secondaryContainer = Color(0xFFD2CDC0),
)

private val typography = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontFamily = Archivo),
        bodyMedium = bodyMedium.copy(fontFamily = Archivo),
        bodySmall = bodySmall.copy(fontFamily = Archivo),
        titleLarge = titleLarge.copy(fontFamily = Archivo, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Bold),
        titleSmall = titleSmall.copy(fontFamily = Archivo, fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontFamily = Archivo, fontWeight = FontWeight.Bold),
        labelMedium = labelMedium.copy(fontFamily = Archivo),
        labelSmall = labelSmall.copy(fontFamily = Archivo),
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp),
)

/** [dark]: the appearance chosen in the profile. */
@Composable
fun TeknotesTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) darkColors else lightColors, typography = typography, shapes = shapes, content = content)
}

fun textStyle(size: TextUnit = 16.sp, weight: Int = 400) = TextStyle(fontFamily = Archivo, fontSize = size, fontWeight = FontWeight(weight))
