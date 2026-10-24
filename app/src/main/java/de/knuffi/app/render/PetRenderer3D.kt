package de.knuffi.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.opengl.Matrix
import de.knuffi.app.render.gl.Pet3D
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Real 3D look: the body is a round 3D model, the face, belly and pattern are painted onto it,
 * and every drawn part around it (ears, horns, wings, tails, hats, glasses, ...) is puffed up into
 * a soft 3D relief. Lighting, turning and gloss come from OpenGL. Falls back to the drawn look.
 */

private const val MAX_TEX = 560f
private const val MAX_OUT = 760f

private fun rgb(c: Int, out: FloatArray): FloatArray {
    out[0] = Color.red(c) / 255f
    out[1] = Color.green(c) / 255f
    out[2] = Color.blue(c) / 255f
    return out
}

internal fun PetRenderer.draw3D(c: Canvas, cx: Float, groundY: Float, size: Float, look: PetLook, pose: PetPose, silhouette: Int?): Boolean {
    u = size / 2f
    val fl = look.formLook
    val col = fl.colors
    val lying = pose.lying.coerceIn(0f, 1f)
    val bw = u * 0.70f * fl.width * (1f + 0.18f * lying)
    val bh = u * 0.60f * fl.height * (1f - 0.3f * lying)
    val footH = if (fl.feet) u * 0.12f * (1f - lying) else 0f
    val ghostBob = if (fl.ghost) u * 0.1f + sin(pose.t * 2f) * u * 0.04f else 0f
    val bottom = -footH * 0.5f - ghostBob
    val top = bottom - 2f * bh
    val cy = bottom - bh
    val turn = pose.turn.coerceIn(-1f, 1f)
    val fx = turn * bw * 0.22f
    val baseY = groundY - pose.lift * u
    val sx = pose.scaleX
    val sy = pose.scaleY
    bounds.set(cx - bw * 1.15f * sx, baseY + top * sy - u * 0.1f, cx + bw * 1.15f * sx, baseY + u * 0.02f)
    head.set(cx + fx * sx, baseY + (top - earHeight(fl)) * sy)

    // region of the picture in body space
    val left = -(bw + u * 1.05f)
    val right = -left
    val rTop = top - u * 1.25f
    val rBottom = u * 0.16f
    val rw = right - left
    val rh = rBottom - rTop
    val ko = min(1f, MAX_OUT / max(rw, rh))
    val kt = min(1f, MAX_TEX / max(rw, rh))
    val ow = max(8, (rw * ko).toInt())
    val oh = max(8, (rh * ko).toInt())
    val tw = max(8, (rw * kt).toInt())
    val th = max(8, (rh * kt).toInt())

    val p0 = flat.copyFrom(pose)
    p0.turn = 0f
    val withArms = lying < 0.5f && !fl.claws
    val fwd = 0.86f

    // keep the last picture if this pet was updated very recently or the GPU budget is used up
    val old = out3d
    val now = System.nanoTime()
    val fresh = old != null && old.width == ow && old.height == oh && look == last3dLook
    val reuse = fresh && silhouette == last3dSilhouette && (now - last3dAt < min3dIntervalNs || !Pet3D.mayUpdate())
    val grid = (max(ow, oh) / 7).coerceIn(36, 92)
    val img = if (reuse) old!! else Pet3D.render(out3d) { s ->
        val f = s.frame
        f.left = left; f.top = rTop; f.right = right; f.bottom = rBottom
        f.width = ow; f.height = oh
        f.yaw = turn * 0.55f
        f.bodyBottom = bottom
        f.bodyH = bh
        rgb(col.body, f.bodyColor)
        rgb(col.light, f.lightColor)
        when {
            pose.sickTint > 0.01f -> {
                rgb(0xFF74C476.toInt(), f.tint); f.tint[3] = 0.3f * pose.sickTint
            }
            pose.glow > 0.01f -> {
                rgb(0xFFFFF1B8.toInt(), f.tint); f.tint[3] = 0.38f * pose.glow.coerceAtMost(1f)
            }
        }

        // body
        if (!buildShapedBody(body, fl, bw, bh, bottom, p0.t)) buildBody(body, bw, bh, bottom, false, p0.t)
        val prof = s.profile
        prof.fromPath(body)
        prof.power = if (fl.shape == Shape.SQUARE) 3.4f else 2f
        prof.depth = fwd
        val t = p0.t
        prof.lathe(
            s.bodyMesh,
            wave = if (fl.ghost) { x: Float, y: Float ->
                val k = ((y - (bottom - bh * 0.35f)) / (bh * 0.35f)).coerceIn(0f, 1f)
                sin(x / bw * 7f + t * 3f) * bh * 0.07f * k
            } else null,
        )
        f.body = s.bodyMesh.build()

        // painted skin and face
        run {
            val (bmp, dc) = s.layer(0, tw, th)
            dc.save()
            dc.scale(kt, kt)
            dc.translate(-left, -rTop)
            dc.save()
            dc.clipPath(body)
            drawSkin(dc, fl, look, p0, bw, bh, bottom, top, cy, 0f)
            dc.restore()
            drawFace(dc, fl, p0, bw, bh, cy, 0f)
            dc.restore()
            f.decal = bmp
        }

        // everything behind the body, puffed up
        run {
            val (bmp, bc) = s.layer(1, tw, th)
            bc.save()
            bc.scale(kt, kt)
            bc.translate(-left, -rTop)
            if (fl.ring) drawRing(bc, fl, bw, bh, cy, front = false)
            drawBack(bc, fl, look, p0, bw, bh, top, cy, 0f)
            bc.restore()
            if (s.relief.build(s.backMesh, bmp, left, rTop, right, rBottom, grid, u * 0.07f, 1f) { _, _ -> 0f }) {
                f.back = s.backMesh.build()
                f.backTex = bmp
            }
        }

        // everything in front of the body (hats, glasses, crowns, held things ...), draped over the body
        run {
            val (bmp, fc) = s.layer(2, tw, th)
            fc.save()
            fc.scale(kt, kt)
            fc.translate(-left, -rTop)
            if (fl.ring) drawRing(fc, fl, bw, bh, cy, front = true)
            drawFront(fc, fl, look, p0, bw, bh, top, cy, 0f)
            drawAccessories(fc, look, p0, bw, bh, top, cy, 0f)
            if (lying < 0.5f && fl.claws) drawClaws(fc, fl, p0, bw, bh, cy, 0f)
            if (p0.foam > 0.01f) drawFoam(fc, p0, bw, top, 0f)
            fc.restore()
            val lift = u * 0.012f
            // in front of the body surface, and always in front of the parts behind (antennae under a hat)
            val minZ = u * 0.085f
            if (s.relief.build(s.frontMesh, bmp, left, rTop, right, rBottom, grid, u * 0.045f, 0.3f) { x, y -> max(prof.front(x, y) + lift, minZ) }) {
                f.front = s.frontMesh.build()
                f.frontTex = bmp
            }
        }

        // feet
        if (fl.feet && lying < 0.6f) {
            val fw = u * 0.2f
            val footCol = pawColor(fl) ?: Colors.lerp(col.body, col.shade, 0.12f)
            for (side in intArrayOf(-1, 1)) {
                val liftF = if (side < 0) pose.footL else pose.footR
                val stride = if (side < 0) pose.strideL else pose.strideR
                val x = side * bw * 0.42f + stride * u * 0.1f
                val y = -footH * 0.5f - liftF * u * 0.1f
                val m = s.matrix()
                Matrix.setIdentityM(m, 0)
                Matrix.translateM(m, 0, x, y, bw * fwd * 0.42f)
                Matrix.scaleM(m, 0, fw * 0.55f, max(footH * 0.6f, u * 0.05f), fw * 0.62f)
                f.parts += Pet3D.Part(m, rgb(footCol, FloatArray(3)), 0.5f)
            }
        }

        // arms
        if (withArms) {
            val len = u * 0.26f
            val thick = u * 0.13f
            val paw = pawColor(fl)
            for (side in intArrayOf(-1, 1)) {
                val raise = if (side < 0) pose.armL else pose.armR
                val wave = if (side < 0) pose.armWaveL else pose.armWaveR
                val px = side * bw * 0.9f
                val py = cy + bh * 0.22f
                var angle = 28f + raise * 125f + wave
                angle = angle * (1f - pose.hold) + (-40f) * pose.hold
                val pz = prof.front(px * 0.92f, py) * 0.75f
                val m = s.matrix()
                Matrix.setIdentityM(m, 0)
                Matrix.translateM(m, 0, px, py, pz)
                Matrix.rotateM(m, 0, -side * angle, 0f, 0f, 1f)
                Matrix.rotateM(m, 0, 18f + pose.hold * 55f, 1f, 0f, 0f)
                val tipM = s.matrix()
                System.arraycopy(m, 0, tipM, 0, 16)
                Matrix.translateM(m, 0, 0f, (len - thick * 0.35f) * 0.5f, 0f)
                Matrix.scaleM(m, 0, thick * 0.62f, (len + thick * 0.35f) * 0.56f, thick * 0.6f)
                f.parts += Pet3D.Part(m, rgb(col.body, FloatArray(3)), 0.6f)
                if (paw != null) {
                    Matrix.translateM(tipM, 0, 0f, len * 0.82f, 0f)
                    Matrix.scaleM(tipM, 0, thick * 0.5f, thick * 0.42f, thick * 0.5f)
                    f.parts += Pet3D.Part(tipM, rgb(paw, FloatArray(3)), 0.5f)
                }
            }
        }
        f
    } ?: return false
    if (!reuse) {
        out3d = img
        last3dAt = now
        last3dLook = look
        last3dSilhouette = silhouette
    }

    val save = c.save()
    c.translate(cx, baseY)
    c.rotate(pose.tilt)
    c.scale(sx, sy)
    if (silhouette == null) {
        val cyA = bottom - bh
        if (look.level >= 25) drawAura(c, bw, bh, cyA, pose.t, col.accent)
        if (fl.aura != Aura.NONE) drawAuraFx(c, fl, bw, bh, cyA, pose.t)
    }
    bmpPaint.colorFilter = silhouette?.let { PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN) }
    dst.set(left, rTop, right, rBottom)
    c.drawBitmap(img, null, dst, bmpPaint)
    if (silhouette == null) {
        if (fl.sparkles) drawSparkles(c, bw, bh, cy, pose.t)
        if (pose.sweat > 0.01f) {
            val bob = sin(pose.t * 3f) * u * 0.03f
            Shapes.drop(path, fx + bw * 0.72f, top + bh * 0.45f + bob, u * 0.055f)
            shaderFill(radial(fx + bw * 0.7f, top + bh * 0.4f + bob, u * 0.09f, intArrayOf(0xFFE8F7FF.toInt(), 0xFF7CC8FF.toInt())))
            c.drawPath(path, fill)
            fill.shader = null
        }
    }
    c.restoreToCount(save)
    return true
}

/** The egg as a real 3D egg with its painted pattern and cracks. */
internal fun PetRenderer.draw3DEgg(c: Canvas, cx: Float, groundY: Float, look: PetLook, pose: PetPose, silhouette: Int?): Boolean {
    val col = Looks.egg(look.line)
    val ew = u * 0.6f * (1f - 0.02f * pose.breath)
    val eh = u * 0.78f * (1f + 0.02f * pose.breath)
    bounds.set(cx - ew * 1.4f, groundY - eh * 2.3f, cx + ew * 1.4f, groundY)
    head.set(cx, groundY - eh * 2f)
    val left = -ew * 1.3f
    val right = -left
    val rTop = -2f * eh - eh * 0.2f
    val rBottom = eh * 0.12f
    val rw = right - left
    val rh = rBottom - rTop
    val ko = min(1f, MAX_OUT / max(rw, rh))
    val kt = min(1f, MAX_TEX / max(rw, rh))
    val ow = max(8, (rw * ko).toInt())
    val oh = max(8, (rh * ko).toInt())
    val tw = max(8, (rw * kt).toInt())
    val th = max(8, (rh * kt).toInt())
    val old = out3d
    val now = System.nanoTime()
    val fresh = old != null && old.width == ow && old.height == oh && look == last3dLook
    val reuse = fresh && silhouette == last3dSilhouette && (now - last3dAt < min3dIntervalNs || !Pet3D.mayUpdate())
    val img = if (reuse) old!! else Pet3D.render(out3d) { s ->
        val f = s.frame
        f.left = left; f.top = rTop; f.right = right; f.bottom = rBottom
        f.width = ow; f.height = oh
        f.yaw = 0f
        f.bodyBottom = 0f
        f.bodyH = eh
        rgb(col.body, f.bodyColor)
        rgb(col.light, f.lightColor)
        eggPath(body, ew, eh)
        val prof = s.profile
        prof.fromPath(body)
        prof.power = 2f
        prof.depth = 0.95f
        prof.lathe(s.bodyMesh)
        f.body = s.bodyMesh.build()
        val (bmp, dc) = s.layer(0, tw, th)
        dc.save()
        dc.scale(kt, kt)
        dc.translate(-left, -rTop)
        dc.save()
        dc.clipPath(body)
        drawEggSkin(dc, look, col, ew, eh, pose.t)
        dc.restore()
        drawEggCracks(dc, look.hatchTaps, ew, eh)
        dc.restore()
        f.decal = bmp
        f
    } ?: return false
    if (!reuse) {
        out3d = img
        last3dAt = now
        last3dLook = look
        last3dSilhouette = silhouette
    }
    val save = c.save()
    c.translate(cx, groundY)
    c.rotate(pose.eggWobble)
    if (look.hatchTaps >= 3 && silhouette == null) {
        val pulse = (sin(pose.t * 6f) + 1f) / 2f
        val r = eh * (1.55f + 0.12f * pulse)
        shaderFill(radial(0f, -eh, r, intArrayOf(Colors.alpha(0xFFFFE680.toInt(), 0.6f), Colors.alpha(0xFFFFE680.toInt(), 0f))))
        c.drawCircle(0f, -eh, r, fill)
        fill.shader = null
    }
    bmpPaint.colorFilter = silhouette?.let { PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN) }
    dst.set(left, rTop, right, rBottom)
    c.drawBitmap(img, null, dst, bmpPaint)
    if (look.shiny && silhouette == null) drawSparkles(c, ew, eh * 0.7f, -eh, pose.t)
    c.restoreToCount(save)
    return true
}
