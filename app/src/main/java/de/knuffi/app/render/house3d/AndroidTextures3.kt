package de.knuffi.app.render.house3d

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import de.knuffi.app.render.Furnishing
import de.knuffi.app.render.FurnitureRenderer
import de.knuffi.app.render.LayoutKind
import de.knuffi.app.render.RoomRenderer
import de.knuffi.app.render.SceneLayout
import de.knuffi.app.render.Weather
import de.knuffi.core.Season
import de.knuffi.core.SeasonEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/**
 * Paints the textures of the 3D house with the same painters as the drawn rooms: wallpaper with
 * window and curtains, the themed rooms, rugs and pictures. Used on the 3D thread only.
 */
class AndroidTextures3 : TextureSource {
    private val room = RoomRenderer()
    private val furniture = FurnitureRenderer()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun texture(key: String): TexImage? {
        val p = key.split("|")
        val bmp = when {
            p[0] == "back" -> backWall(p)
            p[0] == "side" -> sideWall(p)
            p[0] == "floor" -> floor(p.getOrElse(1) { "room_cozy" })
            p[0] == "lawn" -> lawn(season(p.getOrNull(1)))
            p[0] == "facade" -> facade(season(p.getOrNull(1)), (p.getOrNull(2)?.toFloatOrNull() ?: 500f) / 100f)
            p[0] == "emoji" -> emoji(p.getOrElse(1) { "⭐" })
            key.startsWith("rug:") -> rug(key.removePrefix("rug:"))
            key.startsWith("pic:") -> picture(key.removePrefix("pic:"))
            else -> null
        } ?: return null
        val repeat = p[0] == "floor" || p[0] == "lawn"
        val buf = ByteBuffer.allocateDirect(bmp.byteCount).order(ByteOrder.nativeOrder())
        bmp.copyPixelsToBuffer(buf)
        buf.position(0)
        val img = TexImage(bmp.width, bmp.height, buf, repeat, true)
        bmp.recycle()
        return img
    }

    private fun season(name: String?): Season? = Season.entries.firstOrNull { it.name == name }

    // ------------------------------------------------------------------ walls

    /** A layout for painting a wall of [wUnits] × [Room3.TEX_H] units, [u] pixels per unit. */
    private fun wallLayout(wPx: Int, u: Float, withWindow: Boolean): SceneLayout {
        val L = SceneLayout()
        val texH = Room3.TEX_H * u
        L.w = wPx.toFloat()
        L.h = 6.97f * u
        L.kind = LayoutKind.HOME
        L.floorY = texH
        L.groundY = texH + u
        L.backGroundY = texH + u * 0.4f
        L.petSize = u
        L.vpX = L.w / 2f
        L.vpY = 0f
        if (withWindow) {
            L.window.set(L.w * 0.07f, texH - 2.42f * u, L.w * 0.43f, texH - 0.95f * u)
        } else {
            // no window: the wallpaper's lower panel still needs a height
            L.window.set(-10f, texH - 2.42f * u, -5f, texH - 0.95f * u)
        }
        return L
    }

    private fun backWall(p: List<String>): Bitmap {
        val theme = p.getOrElse(1) { "room_cozy" }
        val wall = p.getOrElse(2) { "wall_rose" }
        val shelf = p.getOrNull(3) == "1"
        val trophies = p.getOrNull(4)?.toIntOrNull() ?: 0
        val event = SeasonEvent.entries.firstOrNull { it.name == p.getOrNull(5) }
        val baked = p.getOrNull(6) == "baked"
        val roomW = (p.getOrNull(7)?.toFloatOrNull() ?: 240f) / 100f
        val u = 512f / 2.4f
        val wPx = (roomW * u).toInt().coerceIn(64, 1024)
        val hPx = (Room3.TEX_H * u).toInt()
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (theme == "room_cozy") {
            val L = wallLayout(wPx, u, true)
            if (baked) room.drawSky(c, L.window.left, L.window.top, L.window.right, L.window.bottom, 11f, 0f, Weather.CLEAR)
            room.drawCozyWall(c, L, Furnishing(wall = wall, trophies = trophies, event = event), shelf)
        } else {
            themed(c, wPx, hPx, theme, baked)
        }
        return bmp
    }

    private fun sideWall(p: List<String>): Bitmap {
        val theme = p.getOrElse(1) { "room_cozy" }
        val wall = p.getOrElse(2) { "wall_rose" }
        val baked = p.getOrNull(3) == "baked"
        val u = 512f / 2.4f
        val wPx = (Room3.ROOM_D * u).toInt()
        val hPx = (Room3.TEX_H * u).toInt()
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (theme == "room_cozy") room.drawWallpaper(c, wallLayout(wPx, u, false), wall) else themed(c, wPx, hPx, theme, baked)
        return bmp
    }

    /** The back of a themed room (forest, ocean …): the drawn scene above the floor. */
    private fun themed(c: Canvas, wPx: Int, hPx: Int, theme: String, baked: Boolean) {
        val L = SceneLayout()
        L.compute(wPx.toFloat(), hPx / 0.595f, LayoutKind.HOME)
        c.save()
        c.clipRect(0f, 0f, wPx.toFloat(), hPx.toFloat())
        if (baked || theme == "room_ocean" || theme == "room_space") room.drawBehind(c, L, theme, 11f, 0f, Weather.CLEAR)
        room.drawStatic(c, L, theme, Furnishing())
        c.restore()
    }

    // ------------------------------------------------------------------ floors

    private fun tile(draw: (Canvas, Float) -> Unit): Bitmap {
        val s = 512
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), s.toFloat())
        return bmp
    }

    private fun floor(theme: String): Bitmap = tile { c, s ->
        when (theme) {
            "room_castle" -> {
                c.drawColor(0xFFB8B0C0.toInt())
                for (i in 0 until 2) for (j in 0 until 2) {
                    paint.color = if ((i + j) % 2 == 0) 0xFFC8C0D0.toInt() else 0xFFB0A8BA.toInt()
                    c.drawRoundRect(i * s / 2f + 4f, j * s / 2f + 4f, (i + 1) * s / 2f - 4f, (j + 1) * s / 2f - 4f, 14f, 14f, paint)
                }
            }
            "room_forest" -> {
                c.drawColor(0xFF7CC86A.toInt())
                spots(c, s, 0xFF6AB85A.toInt(), 60, 18f)
                spots(c, s, 0xFF8ED87A.toInt(), 40, 12f)
                for (k in 0 until 6) {
                    val x = (k * 97 % 512).toFloat()
                    val y = (k * 173 % 512).toFloat()
                    paint.color = if (k % 2 == 0) 0xFFFFFFFF.toInt() else 0xFFFFD84D.toInt()
                    for (a in 0 until 5) c.drawCircle(x + cos(a * 1.256f) * 7f, y + sin(a * 1.256f) * 7f, 5f, paint)
                }
            }
            "room_ocean", "room_beach" -> {
                c.drawColor(if (theme == "room_ocean") 0xFFF0DCB0.toInt() else 0xFFF6E2B8.toInt())
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f
                paint.color = 0x30A07040
                for (k in 0 until 8) {
                    path.reset()
                    val y = k * s / 8f + 20f
                    path.moveTo(0f, y)
                    path.cubicTo(s * 0.3f, y - 16f, s * 0.6f, y + 16f, s, y)
                    c.drawPath(path, paint)
                }
                paint.style = Paint.Style.FILL
                spots(c, s, if (theme == "room_ocean") 0xFFFFB3CF.toInt() else 0xFFFFFFFF.toInt(), 8, 9f)
            }
            "room_space" -> {
                c.drawColor(0xFF4A4078.toInt())
                paint.color = 0xFF5A5090.toInt()
                c.drawRect(8f, 8f, s / 2f - 8f, s / 2f - 8f, paint)
                c.drawRect(s / 2f + 8f, s / 2f + 8f, s - 8f, s - 8f, paint)
                paint.color = 0xFF9FE8FF.toInt()
                for ((x, y) in listOf(24f to 24f, s / 2f - 24f to 24f, 24f to s / 2f - 24f, s / 2f - 24f to s / 2f - 24f)) c.drawCircle(x, y, 5f, paint)
                paint.color = 0x664DE8FF
                c.drawRect(0f, s / 2f - 3f, s, s / 2f + 3f, paint)
            }
            "room_candy" -> {
                for (i in 0 until 4) for (j in 0 until 4) {
                    paint.color = if ((i + j) % 2 == 0) 0xFFFFD6E8.toInt() else 0xFFFFFFFF.toInt()
                    c.drawRect(i * s / 4f, j * s / 4f, (i + 1) * s / 4f, (j + 1) * s / 4f, paint)
                }
                spots(c, s, 0xFF7FD4FF.toInt(), 12, 5f)
                spots(c, s, 0xFFFFD84D.toInt(), 12, 5f)
            }
            else -> {
                // warm wooden planks
                val rows = 8
                for (r in 0 until rows) {
                    val y0 = r * s / rows
                    val tone = if (r % 2 == 0) 0xFFEDBA8C.toInt() else 0xFFE6B080.toInt()
                    paint.shader = LinearGradient(0f, y0.toFloat(), 0f, (y0 + s / rows).toFloat(), tone, darker(tone, 0.06f), Shader.TileMode.CLAMP)
                    c.drawRect(0f, y0.toFloat(), s, (y0 + s / rows).toFloat(), paint)
                    paint.shader = null
                    paint.color = 0x55A0643A
                    c.drawRect(0f, y0.toFloat(), s, y0 + 2.5f, paint)
                    val off = (r * 157 % 256).toFloat()
                    c.drawRect(off, y0.toFloat(), off + 2.5f, (y0 + s / rows).toFloat(), paint)
                    c.drawRect(off + s / 2f, y0.toFloat(), off + s / 2f + 2.5f, (y0 + s / rows).toFloat(), paint)
                }
            }
        }
    }

    private fun lawn(season: Season?): Bitmap = tile { c, s ->
        val (a, b) = when (season) {
            Season.WINTER -> 0xFFF4F8FF.toInt() to 0xFFE8F0FA.toInt()
            Season.AUTUMN -> 0xFFC8D07A.toInt() to 0xFFBCC46E.toInt()
            Season.SUMMER -> 0xFFA8DC6A.toInt() to 0xFF9AD05E.toInt()
            else -> 0xFF9EDC7A.toInt() to 0xFF8ED06E.toInt()
        }
        paint.color = a
        c.drawRect(0f, 0f, s, s / 2f, paint)
        paint.color = b
        c.drawRect(0f, s / 2f, s, s, paint)
        when (season) {
            Season.WINTER -> spots(c, s, 0xFFFFFFFF.toInt(), 30, 6f)
            Season.AUTUMN -> spots(c, s, 0xFFE8883A.toInt(), 14, 8f)
            Season.SPRING -> spots(c, s, 0xFFFFFFFF.toInt(), 10, 5f)
            else -> spots(c, s, 0x2A2E7A3E, 40, 4f)
        }
    }

    private fun spots(c: Canvas, s: Float, color: Int, n: Int, r: Float) {
        paint.color = color
        for (k in 0 until n) {
            val x = ((k * 7919 + 13) % 509).toFloat() / 509f * s
            val y = ((k * 104729 + 71) % 503).toFloat() / 503f * s
            c.drawCircle(x, y, r * (0.7f + (k % 3) * 0.2f), paint)
        }
    }

    private fun darker(c: Int, k: Float): Int = Color.argb(Color.alpha(c), (Color.red(c) * (1 - k)).toInt(), (Color.green(c) * (1 - k)).toInt(), (Color.blue(c) * (1 - k)).toInt())

    // ------------------------------------------------------------------ garden house

    /** The back of the house seen from the garden: windows with flower boxes, roof above. */
    private fun facade(season: Season?, gardenW: Float): Bitmap {
        val totalW = gardenW + 3.2f
        val u = 1024f / totalW
        val wPx = 1024
        val hPx = (Room3.TEX_H * u).toInt()
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        fun y(units: Float) = hPx - units * u
        fun x(units: Float) = (units + totalW / 2f) * u
        // wall with siding
        val top = y(3.7f)
        paint.shader = LinearGradient(0f, top, 0f, hPx.toFloat(), 0xFFFFF0DC.toInt(), 0xFFF6DCC0.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, top, wPx.toFloat(), hPx.toFloat(), paint)
        paint.shader = null
        paint.color = 0x22A0643A
        var sy = top + u * 0.25f
        while (sy < hPx) {
            c.drawRect(0f, sy, wPx.toFloat(), sy + 2f, paint)
            sy += u * 0.25f
        }
        // the roof edge
        paint.color = 0xFFE8806A.toInt()
        c.drawRect(0f, top - u * 0.22f, wPx.toFloat(), top + u * 0.05f, paint)
        paint.color = 0xFFC85A48.toInt()
        c.drawRect(0f, top + u * 0.02f, wPx.toFloat(), top + u * 0.07f, paint)
        if (season == Season.WINTER) {
            paint.color = 0xFFFFFFFF.toInt()
            c.drawRoundRect(0f, top - u * 0.3f, wPx.toFloat(), top - u * 0.16f, 12f, 12f, paint)
        }
        // windows (not where the doors are)
        for (wx in listOf(-3.0f, -1.6f, 0.75f, 3.3f)) window(c, x(wx), y(1.85f), u * 0.8f, u * 0.85f, season)
        for (wx in listOf(-2.2f, 0.2f, 2.6f)) window(c, x(wx), y(3.35f), u * 0.7f, u * 0.7f, season)
        return bmp
    }

    private fun window(c: Canvas, cx: Float, top: Float, w: Float, h: Float, season: Season?) {
        val r = RectF(cx - w / 2f, top, cx + w / 2f, top + h)
        paint.color = 0xFFFFFFFF.toInt()
        c.drawRoundRect(r.left - 8f, r.top - 8f, r.right + 8f, r.bottom + 8f, 10f, 10f, paint)
        paint.shader = LinearGradient(0f, r.top, 0f, r.bottom, 0xFFBFE6FF.toInt(), 0xFF8CC8F0.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 6f, 6f, paint)
        paint.shader = null
        paint.color = 0xFFFFFFFF.toInt()
        c.drawRect(r.centerX() - 3f, r.top, r.centerX() + 3f, r.bottom, paint)
        c.drawRect(r.left, r.centerY() - 3f, r.right, r.centerY() + 3f, paint)
        // shutters
        paint.color = 0xFF8FCFC0.toInt()
        c.drawRoundRect(r.left - w * 0.32f, r.top - 4f, r.left - 10f, r.bottom + 4f, 6f, 6f, paint)
        c.drawRoundRect(r.right + 10f, r.top - 4f, r.right + w * 0.32f, r.bottom + 4f, 6f, 6f, paint)
        // flower box
        paint.color = 0xFFC8925A.toInt()
        c.drawRoundRect(r.left - 6f, r.bottom + 8f, r.right + 6f, r.bottom + h * 0.2f, 6f, 6f, paint)
        if (season != Season.WINTER) {
            val colors = intArrayOf(0xFFFF6B9A.toInt(), 0xFFFFD84D.toInt(), 0xFFFF8A3D.toInt(), 0xFFB28CFF.toInt())
            for (k in 0 until 5) {
                paint.color = 0xFF5CC26E.toInt()
                c.drawCircle(r.left + (k + 0.5f) * r.width() / 5f, r.bottom + 6f, w * 0.07f, paint)
                paint.color = colors[k % 4]
                c.drawCircle(r.left + (k + 0.5f) * r.width() / 5f, r.bottom - 2f, w * 0.05f, paint)
            }
        }
    }

    // ------------------------------------------------------------------ small things

    private fun emoji(e: String): Bitmap {
        val s = 128
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = s * 0.78f
        paint.color = Color.BLACK
        val fm = paint.fontMetrics
        c.drawText(e, s / 2f, s / 2f - (fm.ascent + fm.descent) / 2f, paint)
        paint.textAlign = Paint.Align.LEFT
        return bmp
    }

    private fun picture(id: String): Bitmap {
        val w = 320
        val h = 224
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.clipRect(0, 0, w, h)
        furniture.picture(c, id, 0f, 0f, w.toFloat(), h.toFloat())
        return bmp
    }

    /** A rug seen from above: the drawn rug is flattened for the look from the side, so it is stretched back. */
    private fun rug(id: String): Bitmap {
        val p = Furniture3.piece(id)
        val aspect = (p.d / p.w).coerceIn(0.4f, 1.5f)
        val tw = 512
        val th = (512 * aspect).toInt()
        val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val L = SceneLayout()
        L.compute(tw / 0.72f, tw / 0.72f * 2f, LayoutKind.HOME)
        val rcx = L.w * 0.5f
        val rcy = L.groundY + L.h * 0.005f
        c.translate(tw / 2f, th / 2f)
        // the rug was drawn 0.2 times as deep as wide
        c.scale(1f, aspect / 0.2f)
        c.translate(-rcx, -rcy)
        furniture.rug(c, L, id)
        return bmp
    }
}
