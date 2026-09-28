package de.knuffi.app.ui.theme

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.knuffi.app.R
import de.knuffi.app.render.FlatColors
import de.knuffi.core.VisualStyle

data class KnuffiTokens(
    val style: VisualStyle,
    val cardShape: Shape,
    val buttonShape: Shape,
    val pillShape: Shape,
    val border: BorderStroke?,
    val background: Brush,
    val pixel: Boolean,
    val cardElevation: Dp,
    val satiety: Color,
    val joy: Color,
    val energy: Color,
    val hygiene: Color,
    val health: Color,
    val gold: Color,
    val xp: Color,
    val danger: Color,
    val flatColors: FlatColors,
)

val LocalTokens = staticCompositionLocalOf<KnuffiTokens> { error("KnuffiTheme missing") }
val LocalHapticsEnabled = staticCompositionLocalOf { true }

private val Fredoka = FontFamily(
    Font(R.font.fredoka_regular, FontWeight.Normal),
    Font(R.font.fredoka_medium, FontWeight.Medium),
    Font(R.font.fredoka_semibold, FontWeight.SemiBold),
    Font(R.font.fredoka_bold, FontWeight.Bold),
)
private val PressStart = FontFamily(Font(R.font.press_start, FontWeight.Normal))
private val Vt323 = FontFamily(Font(R.font.vt323, FontWeight.Normal))

private val KawaiiColors = lightColorScheme(
    primary = Color(0xFFEC5C9B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E8),
    onPrimaryContainer = Color(0xFF5A1037),
    secondary = Color(0xFF8C7BFF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7E1FF),
    onSecondaryContainer = Color(0xFF2A1F66),
    tertiary = Color(0xFF2FB88F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFF7EA),
    onTertiaryContainer = Color(0xFF0C4A38),
    background = Color(0xFFFFF5FA),
    onBackground = Color(0xFF3B2A40),
    surface = Color(0xFFFFFCFE),
    onSurface = Color(0xFF3B2A40),
    surfaceVariant = Color(0xFFFBE7F1),
    onSurfaceVariant = Color(0xFF7A5C74),
    outline = Color(0xFFE7B9D0),
    outlineVariant = Color(0xFFF5D9E6),
    surfaceContainer = Color(0xFFFFF0F7),
    surfaceContainerLow = Color(0xFFFFF5FA),
    surfaceContainerHigh = Color(0xFFFCE8F2),
    surfaceContainerHighest = Color(0xFFF8E0EC),
)

private val PixelColors = darkColorScheme(
    primary = Color(0xFF7CFF6B),
    onPrimary = Color(0xFF0E2A0A),
    primaryContainer = Color(0xFF1F4D1A),
    onPrimaryContainer = Color(0xFFB8FF9E),
    secondary = Color(0xFFFFD93D),
    onSecondary = Color(0xFF2A2200),
    secondaryContainer = Color(0xFF4D4210),
    onSecondaryContainer = Color(0xFFFFEE9E),
    tertiary = Color(0xFFFF6B9A),
    onTertiary = Color(0xFF3A0016),
    tertiaryContainer = Color(0xFF5A1E33),
    onTertiaryContainer = Color(0xFFFFC2D4),
    background = Color(0xFF14142A),
    onBackground = Color(0xFFE8E8FF),
    surface = Color(0xFF1C1C38),
    onSurface = Color(0xFFE8E8FF),
    surfaceVariant = Color(0xFF2A2A52),
    onSurfaceVariant = Color(0xFFB5B5E0),
    outline = Color(0xFF5A5AA0),
    outlineVariant = Color(0xFF3A3A70),
    surfaceContainer = Color(0xFF22224A),
    surfaceContainerLow = Color(0xFF1C1C38),
    surfaceContainerHigh = Color(0xFF2A2A52),
    surfaceContainerHighest = Color(0xFF32325E),
)

private val MinimalLight = lightColorScheme(
    primary = Color(0xFF5B5BD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E0FF),
    onPrimaryContainer = Color(0xFF16145C),
    secondary = Color(0xFF5E5C71),
    secondaryContainer = Color(0xFFE4E0F9),
    tertiary = Color(0xFF7A5264),
    tertiaryContainer = Color(0xFFFFD8E6),
    background = Color(0xFFFCF8FF),
    surface = Color(0xFFFCF8FF),
    surfaceVariant = Color(0xFFE4E1EC),
)

private val MinimalDark = darkColorScheme(
    primary = Color(0xFFC2C1FF),
    onPrimary = Color(0xFF2A2A8C),
    primaryContainer = Color(0xFF4242BD),
    onPrimaryContainer = Color(0xFFE2E0FF),
    secondary = Color(0xFFC8C4DD),
    secondaryContainer = Color(0xFF464559),
    tertiary = Color(0xFFEBB8CD),
    tertiaryContainer = Color(0xFF603B4D),
    background = Color(0xFF131318),
    surface = Color(0xFF131318),
    surfaceVariant = Color(0xFF47464F),
)

private fun TextStyle.using(family: FontFamily, scale: Float, weight: FontWeight? = null): TextStyle = copy(
    fontFamily = family,
    fontSize = fontSize * scale,
    lineHeight = lineHeight * scale,
    fontWeight = weight ?: fontWeight,
)

private fun typography(title: FontFamily, body: FontFamily, titleScale: Float, bodyScale: Float, titleWeight: FontWeight?): Typography {
    val b = Typography()
    return b.copy(
        displayLarge = b.displayLarge.using(title, titleScale, titleWeight),
        displayMedium = b.displayMedium.using(title, titleScale, titleWeight),
        displaySmall = b.displaySmall.using(title, titleScale, titleWeight),
        headlineLarge = b.headlineLarge.using(title, titleScale, titleWeight),
        headlineMedium = b.headlineMedium.using(title, titleScale, titleWeight),
        headlineSmall = b.headlineSmall.using(title, titleScale, titleWeight),
        titleLarge = b.titleLarge.using(title, titleScale, titleWeight),
        titleMedium = b.titleMedium.using(title, titleScale, titleWeight),
        titleSmall = b.titleSmall.using(title, titleScale, titleWeight),
        bodyLarge = b.bodyLarge.using(body, bodyScale),
        bodyMedium = b.bodyMedium.using(body, bodyScale),
        bodySmall = b.bodySmall.using(body, bodyScale),
        labelLarge = b.labelLarge.using(body, bodyScale),
        labelMedium = b.labelMedium.using(body, bodyScale),
        labelSmall = b.labelSmall.using(body, bodyScale),
    )
}

private val KawaiiType = typography(Fredoka, Fredoka, 1f, 1.02f, FontWeight.SemiBold)
private val PixelType = typography(PressStart, Vt323, 0.62f, 1.32f, FontWeight.Normal)
private val MinimalType = Typography()

fun isDarkScheme(scheme: ColorScheme): Boolean {
    val c = scheme.background
    return (0.299f * c.red + 0.587f * c.green + 0.114f * c.blue) < 0.5f
}

@Composable
fun KnuffiTheme(style: VisualStyle, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when (style) {
        VisualStyle.KAWAII -> KawaiiColors
        VisualStyle.PIXEL -> PixelColors
        VisualStyle.MINIMAL -> when {
            Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dark -> MinimalDark
            else -> MinimalLight
        }
    }
    val typography = when (style) {
        VisualStyle.KAWAII -> KawaiiType
        VisualStyle.PIXEL -> PixelType
        VisualStyle.MINIMAL -> MinimalType
    }
    val shapes = when (style) {
        VisualStyle.KAWAII -> Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(22.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(36.dp),
        )
        VisualStyle.PIXEL -> Shapes(
            extraSmall = CutCornerShape(2.dp),
            small = CutCornerShape(3.dp),
            medium = CutCornerShape(4.dp),
            large = CutCornerShape(6.dp),
            extraLarge = CutCornerShape(8.dp),
        )
        VisualStyle.MINIMAL -> Shapes()
    }
    val tokens = remember(style, scheme) {
        when (style) {
            VisualStyle.KAWAII -> KnuffiTokens(
                style = style,
                cardShape = RoundedCornerShape(26.dp),
                buttonShape = RoundedCornerShape(50),
                pillShape = RoundedCornerShape(50),
                border = BorderStroke(1.5.dp, Color(0xFFF7CFE2)),
                background = Brush.verticalGradient(listOf(Color(0xFFFFF0F7), Color(0xFFF0EBFF))),
                pixel = false,
                cardElevation = 3.dp,
                satiety = Color(0xFFFF9F5A),
                joy = Color(0xFFFF6FA5),
                energy = Color(0xFFFFC53D),
                hygiene = Color(0xFF4FC3F7),
                health = Color(0xFF3CC49B),
                gold = Color(0xFFFFB300),
                xp = Color(0xFF9C7CFF),
                danger = Color(0xFFFF4F6D),
                flatColors = flatFrom(scheme),
            )
            VisualStyle.PIXEL -> KnuffiTokens(
                style = style,
                cardShape = CutCornerShape(5.dp),
                buttonShape = CutCornerShape(4.dp),
                pillShape = CutCornerShape(3.dp),
                border = BorderStroke(3.dp, Color(0xFF4B4B8F)),
                background = Brush.verticalGradient(listOf(Color(0xFF14142A), Color(0xFF1E1440))),
                pixel = true,
                cardElevation = 0.dp,
                satiety = Color(0xFFFF9F43),
                joy = Color(0xFFFF6B9A),
                energy = Color(0xFFFFD93D),
                hygiene = Color(0xFF4DD2FF),
                health = Color(0xFF7CFF6B),
                gold = Color(0xFFFFD93D),
                xp = Color(0xFFB388FF),
                danger = Color(0xFFFF4D4D),
                flatColors = flatFrom(scheme),
            )
            VisualStyle.MINIMAL -> KnuffiTokens(
                style = style,
                cardShape = RoundedCornerShape(22.dp),
                buttonShape = RoundedCornerShape(16.dp),
                pillShape = RoundedCornerShape(50),
                border = null,
                background = Brush.verticalGradient(listOf(scheme.background, scheme.background)),
                pixel = false,
                cardElevation = 0.dp,
                satiety = scheme.primary,
                joy = scheme.tertiary,
                energy = scheme.secondary,
                hygiene = scheme.primary,
                health = scheme.tertiary,
                gold = Color(0xFFE0A100),
                xp = scheme.primary,
                danger = scheme.error,
                flatColors = flatFrom(scheme),
            )
        }
    }
    CompositionLocalProvider(LocalTokens provides tokens) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes) {
            // Text outside of a Surface should still use the theme's foreground colour.
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}

private fun flatFrom(s: ColorScheme) = FlatColors(
    wall = s.surfaceVariant.toArgb(),
    floor = s.secondaryContainer.toArgb(),
    accent1 = s.primaryContainer.toArgb(),
    accent2 = s.tertiaryContainer.toArgb(),
    accent3 = s.primary.toArgb(),
    outline = s.outline.toArgb(),
)
