package de.knuffi.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.knuffi.app.R

@Immutable
data class KnuffiPalette(
    val dark: Boolean,
    val bgTop: Color,
    val bgBottom: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val glass: Color,
    val glassBorder: Color,
    val text: Color,
    val textMuted: Color,
    val pink: Color,
    val pinkDeep: Color,
    val violet: Color,
    val violetDeep: Color,
    val mint: Color,
    val mintDeep: Color,
    val gold: Color,
    val goldDeep: Color,
    val sky: Color,
    val skyDeep: Color,
    val orange: Color,
    val orangeDeep: Color,
    val red: Color,
    val redDeep: Color,
    val track: Color,
    val shadow: Color,
) {
    val background: Brush get() = Brush.verticalGradient(listOf(bgTop, bgBottom))
    val satiety get() = orange
    val joy get() = pink
    val energy get() = gold
    val hygiene get() = sky
    val health get() = mint
    val xp get() = violet
}

val LightPalette = KnuffiPalette(
    dark = false,
    bgTop = Color(0xFFFFF3F9),
    bgBottom = Color(0xFFEDE8FF),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFFBF1F8),
    glass = Color(0xE8FFFFFF),
    glassBorder = Color(0xFFFFFFFF),
    text = Color(0xFF3A2A45),
    textMuted = Color(0xFF8C7A96),
    pink = Color(0xFFFF6FA8),
    pinkDeep = Color(0xFFD64A86),
    violet = Color(0xFF8C7BFF),
    violetDeep = Color(0xFF6150D6),
    mint = Color(0xFF34C99F),
    mintDeep = Color(0xFF1E9C78),
    gold = Color(0xFFFFC23D),
    goldDeep = Color(0xFFDB9510),
    sky = Color(0xFF4FB6FF),
    skyDeep = Color(0xFF2587D6),
    orange = Color(0xFFFF9A4D),
    orangeDeep = Color(0xFFDB6F26),
    red = Color(0xFFFF5A6E),
    redDeep = Color(0xFFD1364C),
    track = Color(0x1F3A2A45),
    shadow = Color(0xFF5A3A70),
)

val DarkPalette = KnuffiPalette(
    dark = true,
    bgTop = Color(0xFF1E1838),
    bgBottom = Color(0xFF0E0B1F),
    surface = Color(0xFF261F42),
    surfaceAlt = Color(0xFF30284F),
    glass = Color(0xE0231C3D),
    glassBorder = Color(0x33FFFFFF),
    text = Color(0xFFF4EEFF),
    textMuted = Color(0xFFB3A7CC),
    pink = Color(0xFFFF7AB6),
    pinkDeep = Color(0xFFC94A84),
    violet = Color(0xFFA08FFF),
    violetDeep = Color(0xFF6D5BDB),
    mint = Color(0xFF4FE0B8),
    mintDeep = Color(0xFF26A583),
    gold = Color(0xFFFFC94D),
    goldDeep = Color(0xFFD19A16),
    sky = Color(0xFF62C0FF),
    skyDeep = Color(0xFF2E86CF),
    orange = Color(0xFFFFA560),
    orangeDeep = Color(0xFFD47830),
    red = Color(0xFFFF6B7E),
    redDeep = Color(0xFFCC4459),
    track = Color(0x26FFFFFF),
    shadow = Color(0xFF000000),
)

/** "Abenteuer" look: blue and teal instead of pink and violet. */
val AdventureLightPalette = LightPalette.copy(
    bgTop = Color(0xFFEFF8FF),
    bgBottom = Color(0xFFE2F4EC),
    surfaceAlt = Color(0xFFEEF6FB),
    text = Color(0xFF1F3345),
    textMuted = Color(0xFF6F8494),
    pink = Color(0xFF3D8BFF),
    pinkDeep = Color(0xFF2463C8),
    violet = Color(0xFF6C7CF0),
    violetDeep = Color(0xFF4452C4),
    sky = Color(0xFF22C4D0),
    skyDeep = Color(0xFF168E9C),
    track = Color(0x1F1F3345),
    shadow = Color(0xFF2A4A70),
)

val AdventureDarkPalette = DarkPalette.copy(
    bgTop = Color(0xFF14213A),
    bgBottom = Color(0xFF0A1220),
    surface = Color(0xFF1B2B45),
    surfaceAlt = Color(0xFF243757),
    glass = Color(0xE0192740),
    text = Color(0xFFEAF4FF),
    textMuted = Color(0xFFA3B6CC),
    pink = Color(0xFF5AA0FF),
    pinkDeep = Color(0xFF2F6FCC),
    violet = Color(0xFF8A98FF),
    violetDeep = Color(0xFF5A66D6),
    sky = Color(0xFF3ED6E0),
    skyDeep = Color(0xFF1A9AA6),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }
val LocalHapticsEnabled = staticCompositionLocalOf { true }

val Fredoka = FontFamily(
    Font(R.font.fredoka_regular, FontWeight.Normal),
    Font(R.font.fredoka_medium, FontWeight.Medium),
    Font(R.font.fredoka_semibold, FontWeight.SemiBold),
    Font(R.font.fredoka_bold, FontWeight.Bold),
)

private fun TextStyle.fredoka(weight: FontWeight? = null) = copy(fontFamily = Fredoka, fontWeight = weight ?: fontWeight)

private val KnuffiType: Typography = Typography().let { b ->
    b.copy(
        displayLarge = b.displayLarge.fredoka(FontWeight.Bold),
        displayMedium = b.displayMedium.fredoka(FontWeight.Bold),
        displaySmall = b.displaySmall.fredoka(FontWeight.Bold),
        headlineLarge = b.headlineLarge.fredoka(FontWeight.SemiBold),
        headlineMedium = b.headlineMedium.fredoka(FontWeight.SemiBold),
        headlineSmall = b.headlineSmall.fredoka(FontWeight.SemiBold),
        titleLarge = b.titleLarge.fredoka(FontWeight.SemiBold),
        titleMedium = b.titleMedium.fredoka(FontWeight.SemiBold),
        titleSmall = b.titleSmall.fredoka(FontWeight.SemiBold),
        bodyLarge = b.bodyLarge.fredoka(),
        bodyMedium = b.bodyMedium.fredoka(),
        bodySmall = b.bodySmall.fredoka(),
        labelLarge = b.labelLarge.fredoka(FontWeight.SemiBold),
        labelMedium = b.labelMedium.fredoka(FontWeight.Medium),
        labelSmall = b.labelSmall.fredoka(FontWeight.Medium),
    )
}

private val KnuffiShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun KnuffiTheme(dark: Boolean, adventure: Boolean = false, content: @Composable () -> Unit) {
    val p = when {
        adventure && dark -> AdventureDarkPalette
        adventure -> AdventureLightPalette
        dark -> DarkPalette
        else -> LightPalette
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.pink,
            onPrimary = Color.White,
            primaryContainer = lerp(p.surface, p.pink, 0.3f),
            onPrimaryContainer = p.text,
            secondary = p.violet,
            onSecondary = Color.White,
            secondaryContainer = lerp(p.surface, p.violet, 0.3f),
            onSecondaryContainer = p.text,
            tertiary = p.mint,
            onTertiary = Color(0xFF052A20),
            background = p.bgBottom,
            onBackground = p.text,
            surface = p.surface,
            onSurface = p.text,
            surfaceVariant = p.surfaceAlt,
            onSurfaceVariant = p.textMuted,
            surfaceContainer = p.surface,
            surfaceContainerLow = p.surface,
            surfaceContainerHigh = p.surfaceAlt,
            surfaceContainerHighest = p.surfaceAlt,
            outline = Color(0x40FFFFFF),
            outlineVariant = Color(0x26FFFFFF),
            error = p.red,
            errorContainer = lerp(p.surface, p.red, 0.3f),
            onErrorContainer = p.text,
        )
    } else {
        lightColorScheme(
            primary = p.pinkDeep,
            onPrimary = Color.White,
            primaryContainer = if (adventure) lerp(p.surface, p.pink, 0.22f) else Color(0xFFFFD9E8),
            onPrimaryContainer = p.text,
            secondary = p.violet,
            onSecondary = Color.White,
            secondaryContainer = if (adventure) lerp(p.surface, p.violet, 0.22f) else Color(0xFFE7E1FF),
            onSecondaryContainer = p.text,
            tertiary = p.mintDeep,
            onTertiary = Color.White,
            background = p.bgTop,
            onBackground = p.text,
            surface = p.surface,
            onSurface = p.text,
            surfaceVariant = p.surfaceAlt,
            onSurfaceVariant = p.textMuted,
            surfaceContainer = p.surface,
            surfaceContainerLow = p.surface,
            surfaceContainerHigh = p.surfaceAlt,
            surfaceContainerHighest = if (adventure) Color(0xFFE6F0F8) else Color(0xFFF6E8F1),
            outline = if (adventure) Color(0xFFC8D8E6) else Color(0xFFE3CCDB),
            outlineVariant = if (adventure) Color(0xFFDDE8F1) else Color(0xFFF1E1EB),
            error = p.redDeep,
            errorContainer = Color(0xFFFFDDE2),
            onErrorContainer = p.text,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = KnuffiType, shapes = KnuffiShapes) {
            CompositionLocalProvider(LocalContentColor provides p.text, content = content)
        }
    }
}
