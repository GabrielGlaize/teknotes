package dev.gabrielglaize.teknotes.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.gabrielglaize.teknotes.android.R

// The "Flyer" style of gabrielglaize.dev: black, chrome titles, typewriter labels, pink accent.

object Flyer {
    val pink = Color(0xFFFF4FA3)
    val pinkOnLight = Color(0xFFC81E72)
    /** Field mode: the accent dimmed so the screen doesn't light up a dark field. */
    val pinkDimmed = Color(0xFF9C3A68)
}

@OptIn(ExperimentalTextApi::class)
private fun variable(resource: Int, weight: Int, width: Float? = null) = Font(
    resource,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        *listOfNotNull(FontVariation.weight(weight), width?.let { FontVariation.width(it) }).toTypedArray(),
    ),
)

/** Archivo, wide and heavy, for titles. */
val ArchivoWide = FontFamily(variable(R.font.archivo, 800, width = 125f))
val Manrope = FontFamily(
    variable(R.font.manrope, 400),
    variable(R.font.manrope, 600),
    variable(R.font.manrope, 700),
)
val Typewriter = FontFamily(Font(R.font.special_elite))
val Grotesk = FontFamily(variable(R.font.space_grotesk, 500))

private val darkColors = darkColorScheme(
    primary = Flyer.pink,
    onPrimary = Color(0xFF0A0A0A),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFF1EEE6),
    surface = Color(0xFF0A0A0A),
    onSurface = Color(0xFFF1EEE6),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF8F8C85),
    outline = Color(0xFF262626),
    outlineVariant = Color(0xFF262626),
)

private val lightColors = lightColorScheme(
    primary = Flyer.pinkOnLight,
    onPrimary = Color.White,
    background = Color(0xFFF4F1EA),
    onBackground = Color(0xFF121212),
    surface = Color(0xFFF4F1EA),
    onSurface = Color(0xFF121212),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFECE8DF),
    onSurfaceVariant = Color(0xFF6B6760),
    outline = Color(0xFFDCD6CA),
    outlineVariant = Color(0xFFDCD6CA),
)

private val typography = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontFamily = Manrope),
        bodyMedium = bodyMedium.copy(fontFamily = Manrope),
        bodySmall = bodySmall.copy(fontFamily = Manrope),
        titleLarge = titleLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
        titleSmall = titleSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
        labelMedium = labelMedium.copy(fontFamily = Typewriter, letterSpacing = 1.sp),
        labelSmall = labelSmall.copy(fontFamily = Typewriter, letterSpacing = 1.sp),
    )
}

private fun Typography.scaled(factor: Float): Typography = copy(
    bodyLarge = bodyLarge.copy(fontSize = bodyLarge.fontSize * factor, lineHeight = bodyLarge.lineHeight * factor),
    bodyMedium = bodyMedium.copy(fontSize = bodyMedium.fontSize * factor, lineHeight = bodyMedium.lineHeight * factor),
    bodySmall = bodySmall.copy(fontSize = bodySmall.fontSize * factor, lineHeight = bodySmall.lineHeight * factor),
    titleLarge = titleLarge.copy(fontSize = titleLarge.fontSize * factor),
    titleMedium = titleMedium.copy(fontSize = titleMedium.fontSize * factor),
    labelLarge = labelLarge.copy(fontSize = labelLarge.fontSize * factor),
)

/**
 * [dark]: the appearance chosen in the profile (system by default).
 * [fieldMode]: dark, dimmed accent and larger text, for use in the middle of a night.
 */
@Composable
fun TeknotesTheme(dark: Boolean = isSystemInDarkTheme(), fieldMode: Boolean = false, content: @Composable () -> Unit) {
    val colors = when {
        fieldMode -> darkColors.copy(primary = Flyer.pinkDimmed, onPrimary = Color.Black, onSurfaceVariant = Color(0xFF6F6C66))
        dark -> darkColors
        else -> lightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = if (fieldMode) typography.scaled(1.18f) else typography,
        content = content,
    )
}

/** Big title in brushed chrome, like the name on the portfolio. */
@Composable
@ReadOnlyComposable
fun chromeTitle(): TextStyle {
    val colors = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        listOf(Color.White, Color(0xFFCFCCC4), Color(0xFF5E5B55), Color(0xFFEBE8E0), Color(0xFF8F8C84))
    } else {
        listOf(Color(0xFF121212), Color(0xFF4A4741), Color(0xFF9A968E), Color(0xFF2B2925), Color(0xFF5E5B55))
    }
    return TextStyle(
        fontFamily = ArchivoWide,
        fontSize = 28.sp,
        lineHeight = 30.sp,
        brush = Brush.verticalGradient(colors),
    )
}

/** Small typewriter label, upper case. */
@Composable
@ReadOnlyComposable
fun flyerLabel(): TextStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
