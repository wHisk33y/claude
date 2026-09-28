package de.knuffi.app.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.knuffi.app.data.GameRepository
import de.knuffi.app.debug.DebugScenes
import de.knuffi.app.ui.components.rememberHaptic
import de.knuffi.app.ui.games.GameHost
import de.knuffi.app.ui.theme.KnuffiTheme
import de.knuffi.app.ui.theme.LocalHapticsEnabled
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Engine
import de.knuffi.core.GameState
import de.knuffi.core.MiniGame
import de.knuffi.core.StepRewards
import de.knuffi.core.ThemeMode
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
    data object WallpaperPreview : Route
}

/** Space that scrolling pages keep free at the bottom for the floating navigation bar. */
@Composable
fun bottomBarSpace(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun AppRoot(debug: DebugScenes.Launch? = null) {
    val state by GameRepository.state.collectAsStateWithLifecycle()
    val dark = isDarkTheme(state.settings.themeMode)
    KnuffiTheme(dark) {
        SystemBars(dark)
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
                    .background(LocalPalette.current.background),
            ) {
                val pet = state.pet
                val screen = when {
                    !state.onboarded || pet == null -> 0
                    !pet.alive -> 1
                    else -> 2
                }
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
                    label = "root",
                ) { s ->
                    when (s) {
                        0 -> OnboardingScreen()
                        1 -> MemorialScreen(state)
                        else -> MainNavigation(state, debug)
                    }
                }
                OverlayHost(state, debug?.scene)
                if (debug != null) DebugScenes.Driver(debug)
            }
        }
    }
}

@Composable
private fun SystemBars(dark: Boolean) {
    val activity = LocalContext.current as? ComponentActivity ?: return
    LaunchedEffect(dark) {
        val bar = if (dark) {
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
            if (targetState == Route.Main) {
                (fadeIn(tween(280)) + scaleIn(initialScale = 1.04f, animationSpec = tween(280))) togetherWith
                    (fadeOut(tween(200)) + scaleOut(targetScale = 0.94f, animationSpec = tween(220)))
            } else {
                (slideInVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) { it / 5 } + fadeIn(tween(260))) togetherWith
                    (fadeOut(tween(200)) + scaleOut(targetScale = 0.96f, animationSpec = tween(220)))
            }
        },
        label = "route",
    ) { r ->
        when (r) {
            Route.Main -> Box(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { it / 4 * dir } + fadeIn(tween(240))) togetherWith
                            (slideOutHorizontally(tween(220)) { -it / 4 * dir } + fadeOut(tween(160)))
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
                FloatingNavBar(state, tab, Modifier.align(Alignment.BottomCenter)) { tab = it }
            }
            is Route.Game -> GameHost(r.game, state, onExit = { route = Route.Main })
            Route.Settings -> SettingsScreen(state, onBack = { route = Route.Main })
            Route.Gallery -> DebugScenes.Gallery(state)
            Route.WidgetPreview -> DebugScenes.WidgetPreview(state)
            Route.WallpaperPreview -> DebugScenes.WallpaperPreview(state)
        }
    }
}

@Composable
private fun FloatingNavBar(state: GameState, selected: Tab, modifier: Modifier = Modifier, onSelect: (Tab) -> Unit) {
    val p = LocalPalette.current
    val haptic = rememberHaptic()
    val now = System.currentTimeMillis()
    val stepTierReady = StepRewards.tiers.withIndex().any { (i, t) -> state.steps.today >= t.first && i !in state.steps.claimedTiers }
    val goalsBadge = Engine.canClaimDaily(state, now, GameRepository.zone) ||
        state.daily.quests.any { it.done && !it.claimed } ||
        (!state.daily.bonusClaimed && state.daily.quests.isNotEmpty() && state.daily.quests.all { it.claimed })
    val index by animateFloatAsState(selected.ordinal.toFloat(), spring(dampingRatio = 0.68f, stiffness = 420f), label = "navIndex")
    val shape = RoundedCornerShape(30.dp)

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(70.dp)
                .shadow(18.dp, shape, ambientColor = p.shadow.copy(alpha = 0.4f), spotColor = p.shadow.copy(alpha = 0.4f))
                .clip(shape)
                .background(if (p.dark) Color(0xF2211A3A) else Color(0xF7FFFFFF))
                .border(1.dp, Brush.verticalGradient(listOf(p.glassBorder, p.glassBorder.copy(alpha = 0.1f))), shape)
                .padding(6.dp),
        ) {
            val itemW = maxWidth / Tab.entries.size
            // Sliding, glossy indicator
            Box(
                Modifier
                    .offset(x = itemW * index)
                    .width(itemW)
                    .fillMaxHeight()
                    .padding(horizontal = 3.dp)
                    .shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = p.pink, spotColor = p.pink)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(p.pink, p.violet))),
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .fillMaxWidth(0.6f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.3f)),
                )
            }
            Row(Modifier.fillMaxSize()) {
                for (t in Tab.entries) {
                    val active = t == selected
                    val lift by animateFloatAsState(if (active) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "lift")
                    val labelColor by animateColorAsState(if (active) Color.White else p.textMuted, label = "labelColor")
                    val badge = when (t) {
                        Tab.GOALS -> goalsBadge
                        Tab.WALK -> stepTierReady
                        else -> false
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                haptic()
                                onSelect(t)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                t.emoji,
                                fontSize = 22.sp,
                                modifier = Modifier.graphicsLayer {
                                    val s = 1f + 0.15f * lift
                                    scaleX = s
                                    scaleY = s
                                    translationY = -2.dp.toPx() * lift
                                    rotationZ = -8f * lift * (1f - lift) * 4f
                                },
                            )
                            Text(t.label, style = MaterialTheme.typography.labelSmall, color = labelColor, maxLines = 1)
                        }
                        AnimatedVisibility(
                            visible = badge,
                            enter = scaleIn(spring(dampingRatio = 0.4f)),
                            exit = scaleOut(),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 10.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(11.dp)
                                    .clip(CircleShape)
                                    .background(p.red)
                                    .border(2.dp, if (p.dark) Color(0xFF211A3A) else Color.White, CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}
