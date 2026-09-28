package de.knuffi.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.ui.theme.LocalHapticsEnabled
import de.knuffi.app.ui.theme.LocalTokens
import de.knuffi.core.TimeUtil

/** Returns a function that gives a short haptic tick (if enabled in the settings). */
@Composable
fun rememberHaptic(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(haptics, enabled) {
        { if (enabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
}

@Composable
fun KCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    border: BorderStroke? = LocalTokens.current.border,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalTokens.current
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && onClick != null) 0.97f else 1f, spring(dampingRatio = 0.5f), label = "cardScale")
    Surface(
        modifier = modifier
            .scale(scale)
            .clip(tokens.cardShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = LocalIndication.current) {
                        haptic()
                        onClick()
                    }
                } else Modifier,
            ),
        shape = tokens.cardShape,
        color = color,
        border = border,
        shadowElevation = tokens.cardElevation,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun KButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emoji: String? = null,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
    small: Boolean = false,
) {
    val tokens = LocalTokens.current
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "btnScale")
    val bg = if (enabled) container else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val fg = if (enabled) content else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    Box(
        modifier = modifier
            .scale(scale)
            .clip(tokens.buttonShape)
            .background(bg)
            .then(if (tokens.pixel) Modifier.border(3.dp, fg.copy(alpha = 0.6f), tokens.buttonShape) else Modifier)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled) {
                haptic()
                onClick()
            }
            .padding(horizontal = if (small) 14.dp else 20.dp, vertical = if (small) 8.dp else 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (emoji != null) {
                Text(emoji, fontSize = if (small) 15.sp else 18.sp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                color = fg,
                style = if (small) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ActionButton(
    emoji: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlight: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    val tokens = LocalTokens.current
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow), label = "actScale")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "ring")
    val wiggle by pulse.animateFloat(-1f, 1f, infiniteRepeatable(tween(260), RepeatMode.Reverse), label = "wiggle")
    val shape: Shape = if (tokens.pixel) CutCornerShape(4.dp) else CircleShape
    val ringColor = tokens.danger
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
            if (highlight && enabled) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f * (0.8f + 0.25f * ring)
                    drawCircle(ringColor.copy(alpha = (1f - ring) * 0.6f), radius = r, style = Stroke(width = 3.dp.toPx()))
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(54.dp)
                    .scale(scale)
                    .clip(shape)
                    .background(if (enabled) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .then(if (tokens.pixel) Modifier.border(3.dp, MaterialTheme.colorScheme.outline, shape) else Modifier)
                    .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled) {
                        haptic()
                        onClick()
                    },
            ) {
                Text(
                    emoji,
                    fontSize = 25.sp,
                    modifier = Modifier.scale(if (highlight && enabled) 1f + 0.06f * wiggle else 1f),
                    color = if (enabled) Color.Unspecified else Color.Gray,
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
            maxLines = 1,
        )
    }
}

@Composable
fun StatGauge(emoji: String, label: String, value: Float, color: Color, modifier: Modifier = Modifier) {
    val tokens = LocalTokens.current
    val animated by animateFloatAsState(value.coerceIn(0f, 1f), tween(700), label = "gauge")
    val low = value < 0.25f
    val pulse = rememberInfiniteTransition(label = "low")
    val blink by pulse.animateFloat(0.4f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "blink")
    val barColor by animateColorAsState(if (low) tokens.danger else color, label = "gaugeColor")
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (tokens.pixel) {
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val filled = (animated * 5f + 0.49f).toInt()
                for (i in 0 until 5) {
                    Box(
                        Modifier
                            .size(width = 8.dp, height = 12.dp)
                            .background(if (i < filled) barColor.copy(alpha = if (low) blink else 1f) else track),
                    )
                }
            }
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = if (tokens.style == de.knuffi.core.VisualStyle.MINIMAL) 5.dp.toPx() else 6.dp.toPx()
                    val inset = sw / 2f
                    val arcSize = Size(size.width - sw, size.height - sw)
                    drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw))
                    drawArc(
                        barColor.copy(alpha = if (low) blink else 1f),
                        -90f,
                        360f * animated,
                        false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(sw, cap = StrokeCap.Round),
                    )
                }
                Text(emoji, fontSize = 20.sp)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
fun KProgress(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val tokens = LocalTokens.current
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "progress")
    val shape = if (tokens.pixel) CutCornerShape(0.dp) else CircleShape
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
    ) {
        if (tokens.pixel) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val blocks = 12
                val filled = (animated * blocks + 0.001f).toInt()
                for (i in 0 until blocks) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (i < filled) color else Color.Transparent),
                    )
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(shape)
                    .background(color),
            )
        }
    }
}

@Composable
fun CoinChip(coins: Int, modifier: Modifier = Modifier) {
    val tokens = LocalTokens.current
    val animated by animateIntAsState(coins, tween(700), label = "coins")
    Row(
        modifier
            .clip(tokens.pillShape)
            .background(tokens.gold.copy(alpha = 0.18f))
            .then(if (tokens.pixel) Modifier.border(2.dp, tokens.gold, tokens.pillShape) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🪙", fontSize = 15.sp)
        Spacer(Modifier.width(5.dp))
        Text(
            TimeUtil.formatNumber(animated),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.secondaryContainer, textColor: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    val tokens = LocalTokens.current
    Text(
        text,
        modifier = modifier
            .clip(tokens.pillShape)
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = textColor,
        maxLines = 1,
    )
}

@Composable
fun SectionTitle(text: String, emoji: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (emoji != null) {
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** Header for full screen pages with a back button. */
@Composable
fun PageHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val tokens = LocalTokens.current
    val haptic = rememberHaptic()
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(if (tokens.pixel) CutCornerShape(3.dp) else CircleShape)
                .clickable {
                    haptic()
                    onBack()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück", tint = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing()
    }
}

@Composable
fun EmojiBadge(emoji: String, modifier: Modifier = Modifier, size: Dp = 48.dp, color: Color = MaterialTheme.colorScheme.surfaceVariant) {
    val tokens = LocalTokens.current
    Box(
        modifier
            .size(size)
            .clip(if (tokens.pixel) CutCornerShape(3.dp) else CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.5f).sp, textAlign = TextAlign.Center)
    }
}
