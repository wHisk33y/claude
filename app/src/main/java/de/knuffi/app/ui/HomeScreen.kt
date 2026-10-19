package de.knuffi.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.ui.components.ClayButton
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.EmojiTile
import de.knuffi.app.ui.components.GlassIconButton
import de.knuffi.app.ui.components.GlassPanel
import de.knuffi.app.ui.components.CoinPill
import de.knuffi.app.ui.components.LevelBadge
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.components.lighter
import de.knuffi.app.ui.components.rememberHaptic
import de.knuffi.app.ui.components.staggered
import de.knuffi.app.ui.theme.LocalPalette
import androidx.compose.ui.layout.onSizeChanged
import de.knuffi.core.Action
import de.knuffi.core.Catalog
import de.knuffi.core.DailyRewards
import de.knuffi.core.Effect
import de.knuffi.core.Engine
import de.knuffi.core.GameState
import de.knuffi.core.Item
import de.knuffi.core.MiniGame
import de.knuffi.core.Pet
import de.knuffi.core.RoomGame
import de.knuffi.core.RoomGames
import de.knuffi.core.Wish
import de.knuffi.core.WishKind
import de.knuffi.core.Wishes
import androidx.compose.runtime.LaunchedEffect

private enum class Sheet { FOOD, CARE, PLAY }

@Composable
fun HomeScreen(state: GameState, debugHour: Float? = null, onOpenSettings: () -> Unit, onOpenShop: () -> Unit, onOpen: (Route) -> Unit = {}) {
    val pet = state.pet ?: return
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var moodSheet by remember { mutableStateOf(false) }
    val scene = remember { SceneHandle() }
    var editMode by remember { mutableStateOf(false) }
    val voice = rememberTrickVoice()
    // In the house the pet first walks through the doors: eating in the kitchen, sleeping in the bedroom …
    val act: (Action) -> Unit = { a ->
        val room = de.knuffi.core.House.roomFor(a, GameRepository.current)
        if (room != null) scene.travel(room) { GameRepository.perform(a) } else GameRepository.perform(a)
    }
    val navSpace = bottomBarSpace()
    var hudPx by remember { mutableStateOf(0) }
    val dockHeight = if (pet.isEgg) (if (state.count(Catalog.GLITTER) > 0 || pet.shiny) 160.dp else 112.dp) else 124.dp
    var roomGame by remember { mutableStateOf<RoomGameSession?>(null) }
    val running = roomGame
    LaunchedEffect(running?.finished) {
        val g = running ?: return@LaunchedEffect
        if (!g.finished) return@LaunchedEffect
        if (g.juggled) GameRepository.perform(Action.FindSecret(de.knuffi.core.Secret.JUGGLER))
        if (g.score > 0) {
            GameRepository.perform(Action.RoomGameFinished(g.game, g.score))
            GameRepository.message("${g.game.emoji} ${g.game.title}: ${g.score} ${if (g.score == 1) "Punkt" else "Punkte"}! +${RoomGames.coins(g.score)} 🪙")
        } else {
            GameRepository.message("${g.game.emoji} ${g.game.title} beendet. Nächstes Mal klappt's bestimmt!")
        }
        roomGame = null
    }
    fun startRoomGame(game: RoomGame) {
        val blocker = RoomGames.blocker(state)
        if (blocker != null) {
            GameRepository.message(blocker)
            return
        }
        roomGame = RoomGameSession(game, pet.name)
    }
    fun fulfil(w: Wish) {
        when (w.kind) {
            WishKind.FOOD -> sheet = Sheet.FOOD
            WishKind.GAME -> MiniGame.entries.firstOrNull { it.name == w.target }?.let { onOpen(Route.Game(it)) }
            WishKind.PLAY -> act(Action.Play)
            WishKind.CUDDLE -> GameRepository.message("Streichle ${pet.name} oder kitzle es am Bauch! 🥰")
            WishKind.BATH -> if (pet.hygiene < 90 || pet.poops > 0) act(Action.Clean) else sheet = Sheet.CARE
            WishKind.TRICK -> onOpen(Route.Tricks)
            WishKind.VISIT -> onOpen(Route.Kuschelhaus)
            WishKind.COOK -> onOpen(Route.Kitchen)
            WishKind.HARVEST -> onOpen(Route.Garden)
            WishKind.OUTFIT -> onOpen(Route.Wardrobe)
            WishKind.PHOTO -> onOpen(Route.Photos)
            WishKind.ROOM_GAME -> RoomGame.entries.firstOrNull { it.name == w.target }?.let { startRoomGame(it) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PetScene(
                state,
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .shadow(12.dp, RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                    .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)),
                hourOverride = debugHour,
                topInsetPx = hudPx,
                onPetTap = { act(if (pet.isEgg) Action.HatchTap else Action.Stroke) },
                onStroke = { act(Action.Stroke) },
                onTouch = { zone -> act(Action.Touch(zone)) },
                roomGame = roomGame,
                handle = scene,
                editMode = editMode,
                onFixture = { name ->
                    when (name) {
                        "piano" -> onOpen(Route.Music)
                        "cards" -> onOpen(Route.Cards)
                        "kitchen" -> onOpen(Route.Kitchen)
                        "beds" -> onOpen(Route.Garden)
                        "tub" -> act(Action.Clean)
                        "pool" -> act(Action.Play)
                    }
                },
            )
            Spacer(Modifier.height(navSpace + dockHeight - 34.dp))
        }

        Hud(
            state, pet, onOpenSettings, Modifier.align(Alignment.TopCenter).onSizeChanged { hudPx = it.height },
            onMood = { moodSheet = true }, onOpen = onOpen, onWish = { fulfil(it) },
            roomBar = {
                RoomBar(state, pet, voice, editMode, onHouse = { onOpen(Route.House) }, onEdit = {
                    // in 3D the room is furnished on its own page
                    val shown = de.knuffi.core.House.shownRoom(state)
                    if (house3dOn(state) && shown?.outdoor != true) onOpen(Route.Furnish(shown, fromHouse = false)) else editMode = !editMode
                })
            },
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = navSpace)
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = pet.sleeping,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                Pill(
                    "💤 ${pet.name} schläft …",
                    Modifier.padding(bottom = 8.dp),
                    color = Color(0xE61B1F4F),
                    textColor = Color.White,
                )
            }
            if (pet.sick && state.count(Catalog.MEDICINE) == 0) {
                Pill(
                    "Keine Medizin mehr – im Shop gibt es Nachschub!",
                    Modifier.padding(bottom = 8.dp),
                    color = LocalPalette.current.red,
                    textColor = Color.White,
                )
            }
            if (editMode) {
                EditDock(state, Modifier.height(dockHeight), onReset = { GameRepository.perform(Action.ResetFurniture) }, onDone = { editMode = false })
            } else if (pet.isEgg) {
                EggDock(pet, Modifier.height(dockHeight), glitter = state.count(Catalog.GLITTER)) { act(Action.UseItem(Catalog.GLITTER)) }
            } else if (running != null && !running.finished) {
                RoomGameDock(running, Modifier.height(dockHeight)) { running.stop(scene.director) }
            } else {
                ActionDock(pet, Modifier.height(dockHeight), onFeed = { sheet = Sheet.FOOD }, onCare = { sheet = Sheet.CARE }, onPlay = { sheet = Sheet.PLAY }, act = act)
            }
        }
    }

    if (moodSheet) MoodSheet(state) { moodSheet = false }
    when (sheet) {
        Sheet.FOOD -> ItemSheet(
            title = "Was gibt's zu essen?",
            emoji = "🍽️",
            items = Catalog.foods,
            state = state,
            onDismiss = { sheet = null },
            onUse = {
                act(Action.Feed(it.id))
                sheet = null
            },
            onShop = {
                sheet = null
                onOpenShop()
            },
        )
        Sheet.CARE -> ItemSheet(
            title = "Pflege & Extras",
            emoji = "🧴",
            items = Catalog.careItems,
            state = state,
            onDismiss = { sheet = null },
            onUse = {
                act(Action.UseItem(it.id))
                sheet = null
            },
            onShop = {
                sheet = null
                onOpenShop()
            },
        )
        Sheet.PLAY -> PlaySheet(
            pet = pet,
            onDismiss = { sheet = null },
            onBall = {
                sheet = null
                act(Action.Play)
            },
            onRoomGame = {
                sheet = null
                startRoomGame(it)
            },
            onTricks = {
                sheet = null
                onOpen(Route.Tricks)
            },
        )
        null -> Unit
    }
}

/** What to play: the quick ball, the room games and the tricks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaySheet(pet: Pet, onDismiss: () -> Unit, onBall: () -> Unit, onRoomGame: (RoomGame) -> Unit, onTricks: () -> Unit) {
    val p = LocalPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (p.dark) p.surface else p.bgTop,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiTile("🎾", size = 42.dp, color = p.pink)
                Spacer(Modifier.width(12.dp))
                Text("Was spielen wir?", style = MaterialTheme.typography.titleLarge, color = p.text)
            }
            PlayOption(if (pet.isCat) "🧶" else "🎾", if (pet.isCat) "Wollknäuel" else "Ballspielen", "Schnell zwischendurch: ${pet.name} jagt den Ball.", p.pink, onBall)
            for (g in RoomGame.entries) {
                PlayOption(g.emoji, g.title, g.description, if (g == RoomGame.HIDE) p.violet else if (g == RoomGame.TAG) p.orange else p.sky) { onRoomGame(g) }
            }
            PlayOption("🌟", "Kunststücke", "Bring ${pet.name} Tricks bei: Sitz, Pfote, Rolle, Salto …", p.gold, onTricks)
        }
    }
}

@Composable
private fun PlayOption(emoji: String, title: String, text: String, color: Color, onClick: () -> Unit) {
    val p = LocalPalette.current
    SurfaceCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(emoji, size = 46.dp, color = color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = p.text)
                Text(text, style = MaterialTheme.typography.bodySmall, color = p.textMuted)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = p.textMuted)
        }
    }
}

/** Score, time and the stop button while a room game runs. */
@Composable
private fun RoomGameDock(game: RoomGameSession, modifier: Modifier = Modifier, onStop: () -> Unit) {
    val p = LocalPalette.current
    GlassPanel(modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(game.game.emoji, fontSize = 30.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(game.game.title, style = MaterialTheme.typography.titleMedium, color = p.text)
                    Text(
                        if (game.game == RoomGame.HIDE) "Runde ${game.round} von ${RoomGames.HIDE_ROUNDS} · ⭐ ${game.score}" else "⭐ ${game.score} · ⏱ ${game.secondsLeft} s",
                        style = MaterialTheme.typography.labelLarge,
                        color = p.textMuted,
                    )
                }
                ClayTextButton("Fertig", onStop, small = true, color = Color(0xFF9A8FB0), deep = Color(0xFF6E6484))
            }
            game.hint?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = p.text, maxLines = 2)
            }
        }
    }
}

/** Which room the pet is in (opens the house), moving furniture and spoken trick commands. */
@Composable
private fun RoomBar(state: GameState, pet: Pet, voice: de.knuffi.app.sound.TrickVoice, editMode: Boolean, onHouse: () -> Unit, onEdit: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val house = de.knuffi.core.House.enabled(state)
        val room = state.house.current
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(p.glass)
                .border(1.dp, p.glassBorder, RoundedCornerShape(50))
                .clickable { onHouse() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (house) room.emoji else "🏠", fontSize = 18.sp)
            Spacer(Modifier.width(6.dp))
            Text(if (house) room.title else "Kinderzimmer", style = MaterialTheme.typography.labelLarge, color = p.text)
            Spacer(Modifier.width(4.dp))
            Text("🗺️", fontSize = 14.sp)
        }
        Spacer(Modifier.weight(1f))
        if (!pet.isEgg && pet.alive && de.knuffi.core.Tricks.learnedCount(pet) > 0) {
            VoiceButton(voice)
            Spacer(Modifier.width(6.dp))
        }
        GlassIconButton(if (editMode) "✅" else "✏️", onEdit, size = 42.dp)
    }
}

/** Shown instead of the care buttons while furniture is being moved. */
@Composable
private fun EditDock(state: GameState, modifier: Modifier = Modifier, onReset: () -> Unit, onDone: () -> Unit) {
    val p = LocalPalette.current
    GlassPanel(modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text("✏️ Möbel verschieben", style = MaterialTheme.typography.titleMedium, color = p.text)
            Text(
                "Zieh ein Möbelstück mit dem Finger zur Seite. ${if (de.knuffi.core.House.enabled(state)) "Jedes Zimmer merkt sich seine Einrichtung." else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = p.textMuted,
                maxLines = 2,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClayTextButton("Zurücksetzen", onReset, Modifier.weight(1f), emoji = "↩️", small = true, color = Color(0xFF9A8FB0), deep = Color(0xFF6E6484))
                ClayTextButton("Fertig", onDone, Modifier.weight(1f), emoji = "✅", small = true, color = p.mint, deep = p.mintDeep)
            }
        }
    }
}

@Composable
private fun Hud(
    state: GameState,
    pet: Pet,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onMood: () -> Unit = {},
    onOpen: (Route) -> Unit = {},
    onWish: (Wish) -> Unit = {},
    roomBar: @Composable () -> Unit = {},
) {
    val p = LocalPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        GlassPanel(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            contentPadding = PaddingValues(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pet.isEgg) {
                    EmojiTile("🥚", size = 50.dp, color = p.gold)
                } else {
                    LevelBadge(pet.level, pet.xp / pet.xpToNext.toFloat())
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(pet.name, style = MaterialTheme.typography.titleLarge, color = p.text, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        MiniTag(pet.form.title, p.violet)
                        if (state.daily.streak > 1) MiniTag("🔥 ${state.daily.streak}", p.orange)
                    }
                }
                CoinPill(state.coins)
                Spacer(Modifier.width(6.dp))
                GlassIconButton("⚙️", onOpenSettings, size = 42.dp)
            }
        }
        if (!pet.isEgg && pet.alive) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val e = pet.emotion(System.currentTimeMillis())
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(p.glass)
                        .border(1.dp, p.glassBorder, RoundedCornerShape(50))
                        .clickable { onMood() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(e.emoji, fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(e.title, style = MaterialTheme.typography.labelLarge, color = p.text)
                }
                Spacer(Modifier.width(6.dp))
                val w = de.knuffi.core.Weather.current(state, System.currentTimeMillis(), GameRepository.zone)
                Text(
                    "${w.kind.emoji} ${w.tempC}°",
                    style = MaterialTheme.typography.labelLarge,
                    color = p.text,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(p.glass)
                        .border(1.dp, p.glassBorder, RoundedCornerShape(50))
                        .clickable { onMood() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                HudButton("🎓", null) { onOpen(Route.Tricks) }
                Spacer(Modifier.width(6.dp))
                HudButton("📸", null) { onOpen(Route.Photos) }
                Spacer(Modifier.width(6.dp))
                HudButton("👗", null) { onOpen(Route.Wardrobe) }
                Spacer(Modifier.width(6.dp))
                HudButton("💼", if (pet.level < de.knuffi.core.Jobs.UNLOCK_LEVEL) "${de.knuffi.core.Jobs.UNLOCK_LEVEL}" else null) { onOpen(Route.Job) }
                Spacer(Modifier.width(6.dp))
                HudButton("💬", if (pet.level < de.knuffi.core.Chat.UNLOCK_LEVEL) "${de.knuffi.core.Chat.UNLOCK_LEVEL}" else null) { onOpen(Route.Chat) }
            }
        }
        roomBar()
        val wish = state.wish
        if (wish != null && pet.alive && !pet.isEgg) {
            val left = ((wish.until - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(p.pink.copy(alpha = 0.9f), p.violet.copy(alpha = 0.9f))))
                    .clickable { onWish(wish) }
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("💭", fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Wunsch: ${Wishes.label(wish, pet.isCat)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (left >= 60) "${left / 60}:${(left % 60).toString().padStart(2, '0')} h" else "$left Min", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
            }
        }
        val guests = state.visitors
        val guest = guests.firstOrNull()
        if (guest != null) {
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(p.glass)
                    .border(1.dp, p.glassBorder, RoundedCornerShape(50))
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (guests.size > 1) "🏠 ${guests[0].name} und ${guests[1].name} sind da" else "🏠 ${guest.name} ist zu Besuch",
                    style = MaterialTheme.typography.labelLarge,
                    color = p.text,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                ClayTextButton("Zusammen spielen", { GameRepository.perform(Action.PlayTogether) }, small = true, emoji = "🤝", color = p.mint, deep = p.mintDeep)
            }
        }
        if (Engine.canClaimDaily(state, System.currentTimeMillis(), GameRepository.zone)) {
            GiftButton(state, Modifier.align(Alignment.End).padding(top = 10.dp, end = 4.dp))
        }
    }
}

/** Round glass button in the HUD; [lock] shows the level it opens at. */
@Composable
private fun HudButton(emoji: String, lock: String?, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box {
        GlassIconButton(emoji, onClick, size = 42.dp, modifier = Modifier.then(if (lock != null) Modifier.graphicsLayer { alpha = 0.6f } else Modifier))
        if (lock != null) {
            Text(
                "🔒$lock",
                fontSize = 9.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(50))
                    .background(p.violet)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun MiniTag(text: String, color: Color) {
    val p = LocalPalette.current
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (p.dark) color.lighter(0.35f) else color,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = if (p.dark) 0.22f else 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun GiftButton(state: GameState, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val haptic = rememberHaptic()
    val anim = rememberInfiniteTransition(label = "gift")
    val bounce by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "bounce")
    val shine by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(1800)), label = "shine")
    val coins = DailyRewards.coins[DailyRewards.dayIndex(state.daily.streak)]
    Column(
        modifier
            .graphicsLayer {
                translationY = -6.dp.toPx() * bounce
                rotationZ = (bounce - 0.5f) * 8f
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptic()
                GameRepository.perform(Action.ClaimDaily)
                GameRepository.message("Tägliche Belohnung: +$coins 🪙")
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Brush.radialGradient(listOf(p.gold.copy(alpha = 0.7f), Color.Transparent)), radius = size.minDimension / 2f * (0.8f + 0.2f * shine))
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0 + shine * 45.0)
                    val r0 = size.minDimension * 0.28f
                    val r1 = size.minDimension * 0.48f
                    drawLine(
                        p.gold.copy(alpha = 0.55f),
                        Offset(center.x + (Math.cos(a) * r0).toFloat(), center.y + (Math.sin(a) * r0).toFloat()),
                        Offset(center.x + (Math.cos(a) * r1).toFloat(), center.y + (Math.sin(a) * r1).toFloat()),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            Text("🎁", fontSize = 34.sp)
        }
        Text(
            "+$coins",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF4A3000),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Brush.verticalGradient(listOf(p.gold.lighter(0.3f), p.gold)))
                .padding(horizontal = 10.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EggDock(pet: Pet, modifier: Modifier = Modifier, glitter: Int = 0, onGlitter: () -> Unit = {}) {
    val p = LocalPalette.current
    GlassPanel(modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), contentPadding = PaddingValues(16.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Tippe aufs Ei, damit ${pet.name} schlüpft!", style = MaterialTheme.typography.titleMedium, color = p.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until Engine.HATCH_TAPS) {
                    val filled = i < pet.hatchTaps
                    val s by animateFloatAsState(if (filled) 1f else 0.7f, spring(dampingRatio = 0.35f), label = "dot")
                    Box(
                        Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                scaleX = s
                                scaleY = s
                            }
                            .clip(CircleShape)
                            .background(if (filled) Brush.verticalGradient(listOf(p.pink.lighter(0.3f), p.pinkDeep)) else Brush.verticalGradient(listOf(p.track, p.track))),
                    )
                }
            }
            if (glitter > 0 && !pet.shiny) {
                Spacer(Modifier.height(10.dp))
                ClayTextButton("Glitzerstaub streuen (×$glitter)", onGlitter, small = true, emoji = "✨", color = p.gold, deep = p.goldDeep)
            } else if (pet.shiny) {
                Spacer(Modifier.height(8.dp))
                Text("✨ Dieses Ei glitzert!", style = MaterialTheme.typography.labelLarge, color = p.goldDeep)
            }
        }
    }
}

@Composable
private fun ActionDock(pet: Pet, modifier: Modifier = Modifier, onFeed: () -> Unit, onCare: () -> Unit, onPlay: () -> Unit, act: (Action) -> Unit) {
    val p = LocalPalette.current
    GlassPanel(modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            StatAction(if (pet.isCat) "🐟" else if (pet.isSmall) "🥕" else "🍙", "Füttern", pet.satiety, p.satiety, p.orangeDeep, onFeed, alert = pet.satiety < 30, modifier = Modifier.staggered(0))
            StatAction(
                "🎾", "Spielen", pet.joy, p.joy, p.pinkDeep, onPlay,
                enabled = !pet.sleeping, alert = pet.joy < 30 && !pet.sleeping, modifier = Modifier.staggered(1),
            )
            StatAction(
                if (pet.poops > 0) "💩" else if (pet.isReal) "✨" else "🫧", if (pet.isReal) "Bürsten" else "Putzen", pet.hygiene, p.hygiene, p.skyDeep, { act(Action.Clean) },
                alert = pet.poops > 0 || pet.hygiene < 30, modifier = Modifier.staggered(2),
            )
            StatAction(
                if (pet.sleeping) "☀️" else "🌙", if (pet.sleeping) "Wecken" else "Schlafen", pet.energy, p.energy, p.goldDeep,
                { act(Action.ToggleSleep) }, alert = !pet.sleeping && pet.energy < 25, modifier = Modifier.staggered(3),
            )
            StatAction("💊", "Pflege", pet.health, p.health, p.mintDeep, onCare, alert = pet.sick, modifier = Modifier.staggered(4))
        }
    }
}

/** An action button with a ring around it that shows the matching stat. */
@Composable
private fun StatAction(
    emoji: String,
    label: String,
    stat: Double,
    color: Color,
    deep: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    alert: Boolean = false,
) {
    val p = LocalPalette.current
    val value by animateFloatAsState((stat / 100.0).toFloat().coerceIn(0f, 1f), tween(900), label = "stat")
    val anim = rememberInfiniteTransition(label = "alert")
    val pulse by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(1100)), label = "pulse")
    val wiggle by anim.animateFloat(-1f, 1f, infiniteRepeatable(tween(180), RepeatMode.Reverse), label = "wiggle")
    val low = value < 0.3f
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 5.dp.toPx()
                val inset = sw / 2f + 1.dp.toPx()
                val arc = Size(size.width - inset * 2, size.height - inset * 2)
                if (alert) {
                    drawCircle(p.red.copy(alpha = (1f - pulse) * 0.55f), radius = size.minDimension / 2f * (0.8f + 0.25f * pulse), style = Stroke(3.dp.toPx()))
                }
                drawArc(p.track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
                val ring = if (low) p.red else color
                drawArc(
                    Brush.sweepGradient(listOf(ring.lighter(0.35f), ring, if (low) p.redDeep else deep, ring.lighter(0.35f))),
                    -90f, 360f * value, false, Offset(inset, inset), arc,
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
            ClayButton(
                onClick = onClick,
                modifier = Modifier
                    .size(50.dp)
                    .graphicsLayer { rotationZ = if (alert && enabled) wiggle * 6f * (if (pulse < 0.35f) 1f else 0f) else 0f },
                color = color,
                deep = deep,
                shape = CircleShape,
                enabled = enabled,
                depth = 4.dp,
                contentPadding = PaddingValues(0.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 4.dp)
                            .size(width = 24.dp, height = 7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.35f)),
                    )
                    Text(emoji, fontSize = 21.sp)
                }
            }
            if (alert) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(p.red)
                        .border(2.dp, if (p.dark) Color(0xFF231C3D) else Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("!", color = Color.White, fontSize = 11.sp, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = p.text, maxLines = 1)
        Text("${stat.toInt()}%", style = MaterialTheme.typography.labelSmall, color = if (low) p.red else p.textMuted, maxLines = 1)
    }
}

fun effectText(e: Effect): String = buildList {
    if (e.cures) add("heilt 🤒")
    if (e.satiety != 0) add("${sign(e.satiety)} 🍙")
    if (e.joy != 0) add("${sign(e.joy)} 😊")
    if (e.energy != 0) add("${sign(e.energy)} ⚡")
    if (e.hygiene != 0) add("${sign(e.hygiene)} 🫧")
    if (e.health != 0) add("${sign(e.health)} ❤️")
}.joinToString("  ")

private fun sign(v: Int) = if (v > 0) "+$v" else "$v"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemSheet(
    title: String,
    emoji: String,
    items: List<Item>,
    state: GameState,
    onDismiss: () -> Unit,
    onUse: (Item) -> Unit,
    onShop: () -> Unit,
) {
    val p = LocalPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (p.dark) p.surface else p.bgTop,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiTile(emoji, size = 42.dp, color = p.pink)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, color = p.text)
            }
            Spacer(Modifier.height(14.dp))
            val visible = items.filter { it.unlimited || state.count(it.id) > 0 }
            if (visible.isEmpty()) {
                Text("Dein Vorrat ist leer.", style = MaterialTheme.typography.bodyLarge, color = p.textMuted)
                Spacer(Modifier.height(10.dp))
            }
            var index = 0
            for (row in visible.chunked(3)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (item in row) {
                        SurfaceCard(
                            Modifier
                                .weight(1f)
                                .staggered(index++),
                            onClick = { onUse(item) },
                            contentPadding = PaddingValues(10.dp),
                        ) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                                Box(
                                    Modifier
                                        .align(Alignment.Center)
                                        .size(62.dp)
                                        .drawBehind {
                                            drawCircle(Brush.radialGradient(listOf(p.pink.copy(alpha = 0.22f), Color.Transparent)))
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(item.icon(state.catMode), fontSize = 36.sp)
                                }
                                Text(
                                    if (item.unlimited) "∞" else "×${state.count(item.id)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(p.violet)
                                        .padding(horizontal = 7.dp, vertical = 1.dp),
                                )
                            }
                            Text(
                                item.label(state.catMode),
                                style = MaterialTheme.typography.titleSmall,
                                color = p.text,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1,
                            )
                            Text(
                                effectText(item.effect),
                                style = MaterialTheme.typography.labelSmall,
                                color = p.textMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(4.dp))
            ClayTextButton(
                "Mehr im Shop",
                onShop,
                emoji = "🛍️",
                modifier = Modifier.fillMaxWidth(),
                color = p.violet,
                deep = p.violetDeep,
            )
        }
    }
}
