package de.knuffi.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.LayoutKind
import de.knuffi.app.render.PetLook
import de.knuffi.app.render.Shapes
import de.knuffi.app.sound.Sfx
import de.knuffi.app.sound.Sounds
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.EmojiTile
import de.knuffi.app.ui.components.GlossyBar
import de.knuffi.app.ui.components.PageHeader
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.components.SectionHeader
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.components.staggered
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.Catalog
import de.knuffi.core.Engine
import de.knuffi.core.FindSet
import de.knuffi.core.Finds
import de.knuffi.core.GameState
import de.knuffi.core.MiniGame
import de.knuffi.core.Recipes
import de.knuffi.core.Stories
import de.knuffi.core.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ------------------------------------------------------------------ kitchen

@Composable
fun KitchenScreen(state: GameState, onBack: () -> Unit, onPlay: (MiniGame) -> Unit) {
    val p = LocalPalette.current
    val cat = state.catMode
    val level = Engine.playerLevel(state)
    Column(Modifier.fillMaxSize()) {
        PageHeader("Küche", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp)) {
            SurfaceCard(Modifier.fillMaxWidth(), color = if (p.dark) p.surfaceAlt else Color(0xFFFFF0DC)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiTile("🍳", size = 52.dp, color = p.orange)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Kochstudio", style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text("Koch im Minispiel um die Wette. Gute Runden füllen deinen Vorrat mit Gerichten!", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
                    }
                }
                Spacer(Modifier.height(10.dp))
                val blocker = Engine.gameBlocker(state)
                ClayTextButton("Kochstudio spielen", { if (blocker == null) onPlay(MiniGame.COOK) else GameRepository.message(blocker) }, emoji = "▶", color = p.orange, deep = p.orangeDeep, modifier = Modifier.fillMaxWidth(), small = true)
            }
            SectionHeader("Rezepte", "📖")
            Text("Zutaten wachsen im Garten. Mehl, Käse und Milch gibt es im Shop.", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
            Spacer(Modifier.height(8.dp))
            for ((i, r) in Recipes.all.withIndex()) {
                val dish = Catalog[r.dish] ?: continue
                val locked = level < r.minLevel
                val can = !locked && Recipes.canCook(state, r)
                SurfaceCard(Modifier.fillMaxWidth().staggered(i).alpha(if (locked) 0.55f else 1f), contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmojiTile(dish.emoji, size = 50.dp, color = if (can) p.mint else p.gold)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(dish.name, style = MaterialTheme.typography.titleSmall, color = p.text, modifier = Modifier.weight(1f, fill = false))
                                if (state.count(dish.id) > 0) {
                                    Spacer(Modifier.width(6.dp))
                                    Pill("×${state.count(dish.id)}")
                                }
                            }
                            Text(effectText(dish.effect), style = MaterialTheme.typography.labelSmall, color = p.textMuted)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for ((id, n) in r.ingredients) {
                                    val it = Catalog[id]
                                    val have = state.count(id)
                                    Text(
                                        "${it?.icon(cat) ?: "?"} $have/$n",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (have >= n) p.mintDeep else p.red,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background((if (have >= n) p.mint else p.red).copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        if (locked) {
                            Text("🔒 ${r.minLevel}", style = MaterialTheme.typography.labelLarge, color = p.textMuted)
                        } else {
                            ClayTextButton("Kochen", { GameRepository.perform(Action.Cook(r.id)) }, enabled = can, small = true, color = p.orange, deep = p.orangeDeep)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------ stories

@Composable
fun StoryScreen(state: GameState, onBack: () -> Unit) {
    val p = LocalPalette.current
    val pet = state.pet ?: return
    val now = rememberNow(60_000L)
    val today = TimeUtil.epochDay(now, GameRepository.zone)
    val story = Stories.current(state)
    val look = remember(state.pet, state.equipped) { PetLook.of(state) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Geschichten", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp)) {
            if (story == null) {
                val next = Stories.all.firstOrNull { it.id !in state.story.finished }
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Text(if (next == null) "🏆" else "🔒", fontSize = 48.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text(
                        if (next == null) "Du hast alle Geschichten erlebt! Bald gibt es neue Abenteuer." else "Die nächste Geschichte „${next.title}“ gibt es ab Level ${next.minLevel}.",
                        style = MaterialTheme.typography.bodyLarge, color = p.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                val done = state.story.progress[story.id] ?: 0
                SurfaceCard(Modifier.fillMaxWidth(), color = if (p.dark) p.surfaceAlt else Color(0xFFFFF6E0)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(story.emoji, fontSize = 40.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(story.title, style = MaterialTheme.typography.titleLarge, color = p.text)
                            Text(story.description, style = MaterialTheme.typography.bodySmall, color = p.textMuted)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    GlossyBar(done / story.chapters.size.toFloat(), p.gold, p.goldDeep)
                    Text("Kapitel $done von ${story.chapters.size} · am Ende wartet: ${Catalog[story.finalItem ?: ""]?.let { "${it.emoji} ${it.name}" } ?: ""}", style = MaterialTheme.typography.labelSmall, color = p.textMuted)
                }
                Spacer(Modifier.height(12.dp))
                if (state.story.lastResult.isNotEmpty() && state.story.lastDay == today) {
                    SurfaceCard(Modifier.fillMaxWidth()) {
                        Text("✨ ${state.story.lastResult}", style = MaterialTheme.typography.bodyLarge, color = p.text)
                        Spacer(Modifier.height(8.dp))
                        Text("Das nächste Kapitel gibt es morgen. Bis dann! 🌙", style = MaterialTheme.typography.bodyMedium, color = p.textMuted)
                    }
                } else {
                    val ch = story.chapters.getOrNull(done)
                    if (ch != null) {
                        SurfaceCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                                    Text(ch.emoji, fontSize = 64.sp)
                                }
                                PetPortrait(look, Modifier.size(96.dp), sizeFactor = 0.8f)
                            }
                            Text("Kapitel ${done + 1}: ${ch.title}", style = MaterialTheme.typography.titleMedium, color = p.text)
                            Spacer(Modifier.height(6.dp))
                            Text(Stories.text(ch.text, pet.name), style = MaterialTheme.typography.bodyLarge, color = p.text)
                            Spacer(Modifier.height(14.dp))
                            Text("Was soll ${pet.name} tun?", style = MaterialTheme.typography.titleSmall, color = p.textMuted)
                            Spacer(Modifier.height(8.dp))
                            for ((i, c) in ch.choices.withIndex()) {
                                ClayTextButton(
                                    c.text,
                                    { GameRepository.perform(Action.StoryChoose(i)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (i == 0) p.violet else p.pink,
                                    deep = if (i == 0) p.violetDeep else p.pinkDeep,
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
            SectionHeader("Alle Geschichten", "📚")
            for (s in Stories.all) {
                val finished = s.id in state.story.finished
                SurfaceCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmojiTile(s.emoji, size = 44.dp, color = if (finished) p.mint else p.violet)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = MaterialTheme.typography.titleSmall, color = p.text)
                            Text(if (finished) "Erlebt ✓" else "Ab Level ${s.minLevel} · ${s.chapters.size} Kapitel", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------ finds & trophies

@Composable
fun FindsScreen(state: GameState, onBack: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize()) {
        PageHeader("Fundstücke & Pokale", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp)) {
            SurfaceCard(Modifier.fillMaxWidth(), color = if (p.dark) p.surfaceAlt else Color(0xFFFFF6E0)) {
                Text("🏆 Pokalregal", style = MaterialTheme.typography.titleMedium, color = p.text)
                Spacer(Modifier.height(6.dp))
                Text("Fest-Preise: ${state.counters.prizes} · Geschichten: ${state.story.finished.size} · Sammlungen: ${state.findSetsDone.size}", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
                Text("Jeder Pokal steht auch im Regal deines Kinderzimmers.", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
            }
            Text(
                "Haustiere bringen von Ausflügen Fundstücke mit. Auch in Geschichten findest du welche. Eine volle Sammlung bringt Münzen!",
                style = MaterialTheme.typography.bodySmall, color = p.textMuted, modifier = Modifier.padding(top = 10.dp),
            )
            for (set in FindSet.entries) {
                val done = set.name in state.findSetsDone
                SectionHeader("${set.title}${if (done) " ✓" else " · +${set.reward} 🪙"}", set.emoji)
                val items = Finds.of(set)
                for (row in items.chunked(4)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        for (f in row) {
                            val n = state.finds[f.id] ?: 0
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (n > 0) p.surface else p.track)
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(if (n > 0) f.emoji else "❔", fontSize = 28.sp)
                                Text(if (n > 0) f.name else "???", style = MaterialTheme.typography.labelSmall, color = p.text, maxLines = 1, textAlign = TextAlign.Center)
                                if (n > 1) Text("×$n", style = MaterialTheme.typography.labelSmall, color = p.textMuted)
                                if (f.rare) Text("selten", style = MaterialTheme.typography.labelSmall, color = p.goldDeep)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------ photos

object PhotoStore {
    fun dir(context: android.content.Context): File = File(context.filesDir, "photos").apply { mkdirs() }

    fun list(context: android.content.Context): List<File> =
        dir(context).listFiles { f -> f.extension == "png" }?.sortedByDescending { it.name } ?: emptyList()

    fun save(context: android.content.Context, bmp: Bitmap): File {
        val f = File(dir(context), "foto_${System.currentTimeMillis()}.png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
        return f
    }

    fun thumb(f: File, size: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= size) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }
}

private val FRAMES = listOf("Kein Rahmen" to "⬜", "Polaroid" to "📷", "Herzen" to "💗", "Sterne" to "⭐", "Blumen" to "🌸")

@Composable
fun PhotoScreen(state: GameState, onBack: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val handle = remember { SceneHandle() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableIntStateOf(1) }
    val photos = remember { mutableStateListOf<File>() }
    var open by remember { mutableStateOf<File?>(null) }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(Unit) { photos.addAll(withContext(Dispatchers.IO) { PhotoStore.list(context) }) }

    fun shoot() {
        if (size.width <= 0) return
        Sounds.play(Sfx.CLICK)
        val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        handle.renderer.draw(c, size.width.toFloat(), size.height.toFloat(), LayoutKind.HOME, handle.model, handle.director, handle.particles, handle.lastT, handle.guest)
        drawFrame(c, size.width.toFloat(), size.height.toFloat(), frame, state.pet?.name ?: "")
        scope.launch {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(450))
        }
        scope.launch {
            val f = withContext(Dispatchers.IO) { PhotoStore.save(context, bmp) }
            photos.add(0, f)
            GameRepository.perform(Action.PhotoTaken)
            GameRepository.message("Foto gespeichert! 📸")
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Fotoalbum", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.9f)
                    .shadow(10.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .onSizeChanged { size = it },
            ) {
                PetScene(state, Modifier.fillMaxSize(), kind = LayoutKind.HOME, handle = handle, showBubble = true)
                FramePreview(frame, Modifier.fillMaxSize())
                if (flash.value > 0.01f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((i, fr) in FRAMES.withIndex()) {
                    val active = i == frame
                    Text(
                        "${fr.second} ${fr.first}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) Color.White else p.text,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (active) p.pink else p.track)
                            .clickable { frame = i }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            ClayTextButton("Foto machen", { shoot() }, emoji = "📸", modifier = Modifier.fillMaxWidth())
            SectionHeader("Deine Fotos (${photos.size})", "🖼️")
            if (photos.isEmpty()) Text("Noch keine Fotos. Mach dein erstes!", style = MaterialTheme.typography.bodyMedium, color = p.textMuted)
            for (row in photos.chunked(3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    for (f in row) {
                        val thumb = remember(f) { PhotoStore.thumb(f, 240)?.asImageBitmap() }
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(0.9f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(p.track)
                                .clickable { open = f },
                        ) {
                            if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    open?.let { f ->
        Dialog(onDismissRequest = { open = null }) {
            val img = remember(f) { PhotoStore.thumb(f, 1080)?.asImageBitmap() }
            Column(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(p.surface)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (img != null) Image(img, null, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Fit)
                val date = f.name.removePrefix("foto_").removeSuffix(".png").toLongOrNull()
                if (date != null) {
                    Text(
                        Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")),
                        style = MaterialTheme.typography.labelLarge, color = p.textMuted, modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ClayTextButton("Löschen", {
                        f.delete()
                        photos.remove(f)
                        open = null
                    }, emoji = "🗑️", small = true, color = p.red, deep = p.redDeep)
                    ClayTextButton("Schließen", { open = null }, small = true, color = p.violet, deep = p.violetDeep)
                }
            }
        }
    }
}

@Composable
private fun FramePreview(frame: Int, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        drawIntoCanvas { drawFrame(it.nativeCanvas, size.width, size.height, frame, null) }
    }
}

/** Draws a photo frame. [name] is written on the polaroid (null in the preview). */
private fun drawFrame(c: android.graphics.Canvas, w: Float, h: Float, frame: Int, name: String?) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    when (frame) {
        1 -> {
            val b = w * 0.04f
            paint.color = android.graphics.Color.WHITE
            c.drawRect(0f, 0f, w, b, paint)
            c.drawRect(0f, 0f, b, h, paint)
            c.drawRect(w - b, 0f, w, h, paint)
            c.drawRect(0f, h - b * 4f, w, h, paint)
            if (name != null) {
                paint.color = 0xFF5A4A7A.toInt()
                paint.textSize = b * 1.6f
                paint.textAlign = Paint.Align.CENTER
                val date = java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                c.drawText("$name · $date", w / 2f, h - b * 1.5f, paint)
            }
        }
        2, 3, 4 -> {
            val path = android.graphics.Path()
            val colors = when (frame) {
                2 -> intArrayOf(0xFFFF5C9A.toInt(), 0xFFFF9EC4.toInt())
                3 -> intArrayOf(0xFFFFC83D.toInt(), 0xFFFFE680.toInt())
                else -> intArrayOf(0xFFFF8FB8.toInt(), 0xFFFFFFFF.toInt())
            }
            val n = 9
            for (i in 0 until n * 4) {
                val side = i / n
                val k = (i % n + 0.5f) / n
                val (x, y) = when (side) {
                    0 -> k * w to w * 0.04f
                    1 -> w - w * 0.04f to k * h
                    2 -> (1f - k) * w to h - w * 0.04f
                    else -> w * 0.04f to (1f - k) * h
                }
                paint.color = colors[i % 2]
                val r = w * 0.035f
                when (frame) {
                    2 -> Shapes.heart(path, x, y, r * 2f)
                    3 -> Shapes.softStar(path, x, y, r, r * 0.45f, 5, i * 13f)
                    else -> {
                        path.reset()
                        for (pe in 0 until 5) {
                            val a = pe * Math.PI * 2 / 5
                            path.addCircle(x + (Math.cos(a) * r * 0.6).toFloat(), y + (Math.sin(a) * r * 0.6).toFloat(), r * 0.45f, android.graphics.Path.Direction.CW)
                        }
                    }
                }
                c.drawPath(path, paint)
                if (frame == 4) {
                    paint.color = 0xFFFFD84D.toInt()
                    c.drawCircle(x, y, r * 0.3f, paint)
                }
            }
        }
    }
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2f
    paint.color = 0x22000000
    c.drawRoundRect(RectF(0f, 0f, w, h), 0f, 0f, paint)
}
