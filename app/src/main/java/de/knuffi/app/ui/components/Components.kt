package de.knuffi.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import de.knuffi.app.ui.theme.LocalHapticsEnabled
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.TimeUtil
import kotlinx.coroutines.delay

/** Returns a function that gives a short haptic tick (if enabled in the settings). */
@Composable
fun rememberHaptic(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(haptics, enabled) {
        { if (enabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
}

fun Color.lighter(f: Float) = lerp(this, Color.White, f)
fun Color.darker(f: Float) = lerp(this, Color.Black, f)

/** Slides and fades children in one after another. */
fun Modifier.staggered(index: Int, distance: Dp = 28.dp): Modifier = composed {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index * 45L).coerceAtMost(500L))
        anim.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 260f))
    }
    graphicsLayer {
        val v = anim.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * distance.toPx()
        scaleX = 0.95f + 0.05f * v
        scaleY = 0.95f + 0.05f * v
    }
}

/** A frosted, softly shadowed panel. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Column(
        modifier
            .shadow(10.dp, shape, ambientColor = p.shadow.copy(alpha = 0.25f), spotColor = p.shadow.copy(alpha = 0.25f))
            .clip(shape)
            .background(p.glass)
            .border(1.dp, Brush.verticalGradient(listOf(p.glassBorder, p.glassBorder.copy(alpha = 0.15f))), shape)
            .padding(contentPadding),
        content = content,
    )
}

/** Solid content card for pages. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = LocalPalette.current.surface,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && onClick != null) 0.97f else 1f, spring(dampingRatio = 0.5f), label = "cardScale")
    Column(
        modifier
            .scale(scale)
            .shadow(if (p.dark) 2.dp else 8.dp, shape, ambientColor = p.shadow.copy(alpha = 0.2f), spotColor = p.shadow.copy(alpha = 0.2f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(color.lighter(if (p.dark) 0.04f else 0.0f), color)))
            .border(1.dp, if (p.dark) Color(0x1FFFFFFF) else Color(0x14000000), shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null) {
                        haptic()
                        onClick()
                    }
                } else Modifier,
            )
            .padding(contentPadding),
        content = content,
    )
}

/** A chunky, pressable 3D button with a coloured "lip". */
@Composable
fun ClayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.pink,
    deep: Color = LocalPalette.current.pinkDeep,
    shape: Shape = RoundedCornerShape(50),
    enabled: Boolean = true,
    depth: Dp = 5.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val p = LocalPalette.current
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed && enabled) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 1200f), label = "press")
    val face = if (enabled) color else lerp(p.textMuted, p.surface, 0.55f)
    val lip = if (enabled) deep else lerp(p.textMuted, p.surface, 0.3f)
    Box(
        modifier.clickable(interactionSource = interaction, indication = null, enabled = enabled) {
            haptic()
            onClick()
        },
        // Lets a button with fillMaxWidth() stretch its face, not just its lip.
        propagateMinConstraints = true,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .padding(top = depth)
                .clip(shape)
                .background(lip),
        )
        Row(
            Modifier
                .offset(y = depth * press)
                .padding(bottom = depth)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(face.lighter(0.22f), face)))
                .border(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.05f))), shape)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

@Composable
fun ClayTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    color: Color = LocalPalette.current.pink,
    deep: Color = LocalPalette.current.pinkDeep,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    ClayButton(
        onClick = onClick,
        modifier = modifier,
        color = color,
        deep = deep,
        enabled = enabled,
        depth = if (small) 4.dp else 5.dp,
        contentPadding = if (small) PaddingValues(horizontal = 14.dp, vertical = 8.dp) else PaddingValues(horizontal = 22.dp, vertical = 13.dp),
    ) {
        if (emoji != null) {
            Text(emoji, fontSize = if (small) 15.sp else 19.sp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            color = Color.White,
            style = if (small) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Big round action button with an emoji and a label pill underneath. */
@Composable
fun RoundAction(
    emoji: String,
    label: String,
    onClick: () -> Unit,
    color: Color,
    deep: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlight: Boolean = false,
    size: Dp = 60.dp,
) {
    val p = LocalPalette.current
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "ring")
    val wiggle by pulse.animateFloat(-1f, 1f, infiniteRepeatable(tween(220), RepeatMode.Reverse), label = "wiggle")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size + 12.dp)) {
            if (highlight && enabled) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = this.size.minDimension / 2f * (0.78f + 0.28f * ring)
                    drawCircle(p.red.copy(alpha = (1f - ring) * 0.7f), radius = r, style = Stroke(width = 4.dp.toPx()))
                }
            }
            ClayButton(
                onClick = onClick,
                modifier = Modifier.size(size),
                color = color,
                deep = deep,
                shape = CircleShape,
                enabled = enabled,
                depth = 5.dp,
                contentPadding = PaddingValues(0.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        emoji,
                        fontSize = (size.value * 0.42f).sp,
                        modifier = Modifier.graphicsLayer {
                            rotationZ = if (highlight && enabled) wiggle * 8f else 0f
                        },
                    )
                    // glossy highlight
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 5.dp)
                            .size(width = size * 0.5f, height = size * 0.16f)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.35f)),
                    )
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = p.text,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(p.glass)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Ring gauge with an emoji in the middle. */
@Composable
fun RingGauge(emoji: String, label: String, value: Float, color: Color, deep: Color, modifier: Modifier = Modifier, size: Dp = 50.dp) {
    val p = LocalPalette.current
    val animated by animateFloatAsState(value.coerceIn(0f, 1f), tween(800), label = "gauge")
    val low = value < 0.25f
    val pulse = rememberInfiniteTransition(label = "low")
    val blink by pulse.animateFloat(0.45f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "blink")
    val c1 = if (low) p.red else color
    val c2 = if (low) p.redDeep else deep
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 6.dp.toPx()
                val inset = sw / 2f
                val arc = Size(this.size.width - sw, this.size.height - sw)
                drawCircle(if (p.dark) Color(0x33000000) else Color(0x14FFFFFF), radius = this.size.minDimension / 2f - sw)
                drawArc(p.track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
                drawArc(
                    Brush.sweepGradient(listOf(c1.lighter(0.25f), c1, c2, c1.lighter(0.25f))),
                    -90f, 360f * animated, false, Offset(inset, inset), arc,
                    alpha = if (low) blink else 1f,
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
            Text(emoji, fontSize = (size.value * 0.4f).sp)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = p.textMuted, maxLines = 1)
    }
}

/** Glossy horizontal progress bar. */
@Composable
fun GlossyBar(fraction: Float, color: Color, deep: Color, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val p = LocalPalette.current
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(800), label = "bar")
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(p.track),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(RoundedCornerShape(50))
                .background(Brush.verticalGradient(listOf(color.lighter(0.25f), color, deep)))
                .drawBehind {
                    drawRoundRect(
                        Color.White.copy(alpha = 0.45f),
                        topLeft = Offset(this.size.height * 0.4f, this.size.height * 0.18f),
                        size = Size((this.size.width - this.size.height * 0.8f).coerceAtLeast(0f), this.size.height * 0.22f),
                        cornerRadius = CornerRadius(this.size.height),
                    )
                },
        )
    }
}

/** Circular level badge with an XP ring around it. */
@Composable
fun LevelBadge(level: Int, progress: Float, modifier: Modifier = Modifier, size: Dp = 54.dp) {
    val p = LocalPalette.current
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900), label = "xp")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 5.dp.toPx()
            val inset = sw / 2f
            val arc = Size(this.size.width - sw, this.size.height - sw)
            drawArc(Color.Black.copy(alpha = 0.15f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
            drawArc(
                Brush.sweepGradient(listOf(p.gold, p.orange, p.gold)),
                -90f, 360f * animated, false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round),
            )
            val r = this.size.minDimension / 2f - sw - 2.dp.toPx()
            drawCircle(p.violetDeep, radius = r, center = Offset(center.x, center.y + 2.dp.toPx()))
            drawCircle(Brush.verticalGradient(listOf(p.violet.lighter(0.25f), p.violet), startY = center.y - r, endY = center.y + r), radius = r - 1.dp.toPx())
            drawOval(Color.White.copy(alpha = 0.35f), topLeft = Offset(center.x - r * 0.5f, center.y - r * 0.8f), size = Size(r, r * 0.4f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Lv", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, lineHeight = 9.sp)
            Text("$level", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp)
        }
    }
}

/** Gold coin drawn with a canvas so it looks the same everywhere. */
@Composable
fun CoinIcon(modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val p = LocalPalette.current
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawCircle(p.goldDeep, radius = r, center = Offset(center.x, center.y + r * 0.1f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4C2), p.gold, p.goldDeep), center = Offset(center.x - r * 0.3f, center.y - r * 0.3f), radius = r * 1.3f), radius = r * 0.92f)
        drawCircle(p.goldDeep.copy(alpha = 0.6f), radius = r * 0.6f, style = Stroke(r * 0.1f))
        drawOval(Color.White.copy(alpha = 0.6f), topLeft = Offset(center.x - r * 0.55f, center.y - r * 0.65f), size = Size(r * 0.5f, r * 0.3f))
    }
}

@Composable
fun CoinPill(coins: Int, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val animated by animateIntAsState(coins, tween(700), label = "coins")
    var last by remember { mutableIntStateOf(coins) }
    val bump = remember { Animatable(1f) }
    LaunchedEffect(coins) {
        if (coins != last) {
            last = coins
            bump.snapTo(1.18f)
            bump.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 400f))
        }
    }
    Row(
        modifier
            .scale(bump.value)
            .shadow(6.dp, RoundedCornerShape(50), ambientColor = p.shadow.copy(alpha = 0.25f), spotColor = p.shadow.copy(alpha = 0.25f))
            .clip(RoundedCornerShape(50))
            .background(p.glass)
            .border(1.dp, p.glassBorder, RoundedCornerShape(50))
            .padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoinIcon(size = 22.dp)
        Spacer(Modifier.width(6.dp))
        Text(TimeUtil.formatNumber(animated), style = MaterialTheme.typography.titleSmall, color = p.text)
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = LocalPalette.current.violet.copy(alpha = 0.16f), textColor: Color = LocalPalette.current.text) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = textColor,
        maxLines = 1,
    )
}

@Composable
fun SectionHeader(text: String, emoji: String? = null, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPalette.current
    Row(modifier.padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (emoji != null) {
            EmojiTile(emoji, size = 34.dp, color = p.violet)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleLarge, color = p.text, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Big page header with an emoji sticker. */
@Composable
fun ScreenHeader(title: String, emoji: String, subtitle: String? = null, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiTile(emoji, size = 50.dp, color = p.pink, modifier = Modifier.graphicsLayer { rotationZ = -6f })
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = p.text, maxLines = 1)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = p.textMuted, maxLines = 2)
        }
        trailing()
    }
}

/** Rounded tile with a soft gradient and an emoji. */
@Composable
fun EmojiTile(emoji: String, modifier: Modifier = Modifier, size: Dp = 48.dp, color: Color = LocalPalette.current.violet) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(size * 0.32f)
    Box(
        modifier
            .size(size)
            .shadow(4.dp, shape, ambientColor = color.copy(alpha = 0.4f), spotColor = color.copy(alpha = 0.4f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(color.copy(alpha = if (p.dark) 0.45f else 0.28f).compositeOver(p.surface), color.copy(alpha = if (p.dark) 0.25f else 0.14f).compositeOver(p.surface))))
            .border(1.dp, Color.White.copy(alpha = if (p.dark) 0.12f else 0.7f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.5f).sp, textAlign = TextAlign.Center)
    }
}

/** Segmented control with a sliding 3D thumb. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val haptic = rememberHaptic()
    val animated by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.7f, stiffness = 500f), label = "seg")
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(p.track)
            .padding(4.dp),
    ) {
        val itemW = maxWidth / options.size
        Box(
            Modifier
                .offset(x = itemW * animated)
                .width(itemW)
                .fillMaxHeight()
                .shadow(4.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(Brush.verticalGradient(listOf(p.pink.lighter(0.2f), p.pink))),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            haptic()
                            onSelect(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (i == selected) Color.White else p.text,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Round icon button (emoji) in a glass circle. */
@Composable
fun GlassIconButton(emoji: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val p = LocalPalette.current
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.45f), label = "iconScale")
    Box(
        modifier
            .size(size)
            .scale(scale)
            .shadow(6.dp, CircleShape, ambientColor = p.shadow.copy(alpha = 0.25f), spotColor = p.shadow.copy(alpha = 0.25f))
            .clip(CircleShape)
            .background(p.glass)
            .border(1.dp, p.glassBorder, CircleShape)
            .clickable(interactionSource = interaction, indication = null) {
                haptic()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.44f).sp)
    }
}

/** Back button + title for full screen pages. */
@Composable
fun PageHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton("⬅️", onBack)
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = p.text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing()
    }
}
