package de.knuffi.app.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.knuffi.app.data.GameRepository
import de.knuffi.app.debug.DebugScenes
import de.knuffi.app.ui.components.rememberHaptic
import de.knuffi.app.ui.games.GameHost
import de.knuffi.app.ui.theme.KnuffiTheme
import de.knuffi.app.ui.theme.LocalHapticsEnabled
import de.knuffi.app.ui.theme.LocalTokens
import de.knuffi.core.Engine
import de.knuffi.core.MiniGame
import de.knuffi.core.StepRewards
import de.knuffi.core.GameState
import de.knuffi.core.VisualStyle
import kotlinx.coroutines.delay

enum class Tab(val emoji: String, val label: String) {
    HOME("🏠", "Zuhause"),
    GAMES("🎮", "Spiele"),
    SHOP("🛍️", "Shop"),
    GOALS("🏆", "Ziele"),
    WALK("👟", "Gassi"),
}

sealed interface Route {
    data object Main : Route
    data class Game(val game: MiniGame) : Route
    data object Settings : Route
    data object Gallery : Route
    data object WidgetPreview : Route
}

@Composable
fun AppRoot(debug: DebugScenes.Launch? = null) {
    val state by GameRepository.state.collectAsStateWithLifecycle()
    KnuffiTheme(state.style) {
        SystemBars(state.style)
        LaunchedEffect(Unit) {
            // Keep stats moving while the app is open.
            while (true) {
                delay(10_000)
                if (!GameRepository.previewMode) GameRepository.tick(userPresent = true)
            }
        }
        CompositionLocalProvider(LocalHapticsEnabled provides state.settings.haptics) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(LocalTokens.current.background),
            ) {
                val pet = state.pet
                when {
                    !state.onboarded || pet == null -> OnboardingScreen()
                    !pet.alive -> MemorialScreen(state)
                    else -> MainNavigation(state, debug)
                }
                OverlayHost(state, debug?.scene)
            }
        }
    }
}

@Composable
private fun SystemBars(style: VisualStyle) {
    val activity = LocalContext.current as? ComponentActivity ?: return
    val systemDark = isSystemInDarkTheme()
    val darkBars = style == VisualStyle.PIXEL || (style == VisualStyle.MINIMAL && systemDark)
    LaunchedEffect(darkBars) {
        val bar = if (darkBars) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
    }
}

@Composable
private fun MainNavigation(state: GameState, debug: DebugScenes.Launch?) {
    var route by remember { mutableStateOf(DebugScenes.initialRoute(debug?.scene)) }
    var tab by remember { mutableStateOf(DebugScenes.initialTab(debug?.scene)) }

    BackHandler(enabled = route != Route.Main || tab != Tab.HOME) {
        if (route != Route.Main) route = Route.Main else tab = Tab.HOME
    }

    AnimatedContent(
        targetState = route,
        transitionSpec = {
            (fadeIn(tween(260)) + scaleIn(initialScale = 0.96f, animationSpec = tween(260))) togetherWith fadeOut(tween(180))
        },
        label = "route",
    ) { r ->
        when (r) {
            Route.Main -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (slideInHorizontally(tween(260)) { it / 6 * dir } + fadeIn(tween(260))) togetherWith
                                (slideOutHorizontally(tween(200)) { -it / 6 * dir } + fadeOut(tween(160)))
                        },
                        label = "tab",
                    ) { t ->
                        when (t) {
                            Tab.HOME -> HomeScreen(
                                state,
                                debugHour = debug?.hour,
                                onOpenSettings = { route = Route.Settings },
                                onOpenShop = { tab = Tab.SHOP },
                            )
                            Tab.GAMES -> GamesScreen(state, onStart = { route = Route.Game(it) })
                            Tab.SHOP -> ShopScreen(state)
                            Tab.GOALS -> GoalsScreen(state)
                            Tab.WALK -> WalkScreen(state)
                        }
                    }
                }
                BottomBar(state, tab) { tab = it }
            }
            is Route.Game -> GameHost(r.game, state, onExit = { route = Route.Main })
            Route.Settings -> SettingsScreen(state, onBack = { route = Route.Main })
            Route.Gallery -> DebugScenes.Gallery(state)
            Route.WidgetPreview -> DebugScenes.WidgetPreview(state)
        }
    }
}

@Composable
private fun BottomBar(state: GameState, selected: Tab, onSelect: (Tab) -> Unit) {
    val tokens = LocalTokens.current
    val haptic = rememberHaptic()
    val now = System.currentTimeMillis()
    val stepTierReady = StepRewards.tiers.withIndex().any { (i, t) -> state.steps.today >= t.first && i !in state.steps.claimedTiers }
    val goalsBadge = Engine.canClaimDaily(state, now, GameRepository.zone) ||
        state.daily.quests.any { it.done && !it.claimed } ||
        (!state.daily.bonusClaimed && state.daily.quests.isNotEmpty() && state.daily.quests.all { it.claimed })
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (tokens.pixel) 0.dp else 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (tokens.pixel) Modifier.border(3.dp, MaterialTheme.colorScheme.outline) else Modifier),
    ) {
        Row(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            for (t in Tab.entries) {
                val active = t == selected
                val bg by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    label = "tabBg",
                )
                val scale by animateFloatAsState(if (active) 1.15f else 1f, spring(dampingRatio = 0.4f), label = "tabScale")
                val badge = when (t) {
                    Tab.GOALS -> goalsBadge
                    Tab.WALK -> stepTierReady
                    else -> false
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(if (tokens.pixel) CutCornerShape(3.dp) else RoundedCornerShape(18.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            haptic()
                            onSelect(t)
                        }
                        .padding(vertical = 2.dp),
                ) {
                    Box(
                        Modifier
                            .clip(if (tokens.pixel) CutCornerShape(3.dp) else RoundedCornerShape(50))
                            .background(bg)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text(t.emoji, fontSize = 20.sp, modifier = Modifier.scale(scale))
                        if (badge) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(tokens.danger),
                            )
                        }
                    }
                    Text(
                        t.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
