package app.childrensimulator.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import app.childrensimulator.sim.model.SessionKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * Hand-drawn pictures for every care action, step and choice (feedback v0.2: "make the options visual").
 * Each icon is drawn in a 48×48 unit box and can animate with a looping phase `t` in 0..1.
 */
enum class CareIcon {
    BOTTLE, MAKE_BOTTLE, BREAST, BURP, DIAPER, DIAPER_CHECK, HOLD, BASSINET, PLAY_MAT, ROCK, SHUSH, DIM, SLEEP, PLAY, TUMMY,
    CHECK, HANDOFF, BREAK, NIGHT, END, PAUSE, RESUME, STOP,
    WASH, WATER, POWDER, POWDER_EXTRA, POWDER_FEWER, MIX, WARM, MICROWAVE, WRIST, THERMO,
    SHOULDER, LAP, WIPES, GATHER, FORWARD, CHANGING, CLOTHES, CORD_BELOW, CORD_OVER,
    BACK, SIDE, FACE_DOWN, BLANKET, PILLOW, TOY, BUMPER, NURSING_PILLOW, CLEAR,
    FACE, REST, AGAIN, BOTTLE_DOWN, BOTTLE_PUSH, TILT, PROP, CRADLE, CROSS, FOOTBALL, LAIDBACK, PINCH, STEP_AWAY, TOGETHER,
}

/** Soft category colors for tiles, so groups read at a glance without headings. */
enum class TileTone(private val day: Color) {
    FEED(Color(0xFFFFE1CF)), DIAPER(Color(0xFFD9F1E3)), COMFORT(Color(0xFFE8E0FA)), SLEEP(Color(0xFFDCE7F8)),
    PLAY(Color(0xFFFFF0C4)), HELP(Color(0xFFEFE8E3)), CHOICE(Color(0xFFFFF8EE));

    fun color(dark: Boolean): Color = if (dark) lerp(day, Color(0xFF1D2030), 0.74f) else day
}

@Composable
fun isDarkUi(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.3f

@Composable
fun CareIconView(icon: CareIcon, modifier: Modifier = Modifier, animate: Boolean = false, reducedMotion: Boolean = false) {
    val dim = if (isDarkUi()) 0.84f else 1f
    if (animate && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "icon")
        val t = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = LinearEasing)), label = "t")
        Canvas(modifier) { drawCareIcon(icon, t.value, dim) }
    } else {
        Canvas(modifier) { drawCareIcon(icon, 0f, dim) }
    }
}

// ------------------------------------------------------------------ palette

private val INK = Color(0xFF4A3B35)
private val CREAM = Color(0xFFFFF4DF)
private val MILK = Color(0xFFFFFFFF)
private val TAN = Color(0xFFE9B98A)
private val BLUE = Color(0xFF8DB8DE)
private val PINK = Color(0xFFF3A5B6)
private val GREEN = Color(0xFF9FD4B0)
private val YELLOW = Color(0xFFF7D774)
private val LAV = Color(0xFFC3B3EA)
private val SKIN = Color(0xFFF2C9A8)
private val WATER = Color(0xFF94CFF2)
private val CORAL = Color(0xFFE5736F)
private val BROWN = Color(0xFFB98552)
private val GREY = Color(0xFFCFC6C0)
private val NAVY = Color(0xFF51608A)
private val SHEET = Color(0xFFDDEAF6)

/** A tiny drawing helper working in 48-unit icon coordinates. */
private class Pen(val scope: DrawScope, val u: Float, val ox: Float, val oy: Float, val dim: Float) {
    fun c(col: Color) = if (dim == 1f) col else Color(col.red * dim, col.green * dim, col.blue * dim, col.alpha)
    fun p(x: Float, y: Float) = Offset(ox + x * u, oy + y * u)
    fun sz(w: Float, h: Float) = Size(w * u, h * u)
    fun st(w: Float = 1.6f) = Stroke(w * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun rect(col: Color, x: Float, y: Float, w: Float, h: Float, r: Float = 3f, outline: Boolean = true) {
        scope.drawRoundRect(c(col), p(x, y), sz(w, h), CornerRadius(r * u))
        if (outline) scope.drawRoundRect(c(INK), p(x, y), sz(w, h), CornerRadius(r * u), style = st())
    }

    fun circle(col: Color, x: Float, y: Float, r: Float, outline: Boolean = true) {
        scope.drawCircle(c(col), r * u, p(x, y))
        if (outline) scope.drawCircle(c(INK), r * u, p(x, y), style = st())
    }

    fun oval(col: Color, x: Float, y: Float, w: Float, h: Float, outline: Boolean = true) {
        scope.drawOval(c(col), p(x, y), sz(w, h))
        if (outline) scope.drawOval(c(INK), p(x, y), sz(w, h), style = st())
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float = 1.6f, col: Color = INK) =
        scope.drawLine(c(col), p(x1, y1), p(x2, y2), strokeWidth = w * u, cap = StrokeCap.Round)

    fun arc(col: Color, x: Float, y: Float, w: Float, h: Float, start: Float, sweep: Float, width: Float = 1.6f) =
        scope.drawArc(c(col), start, sweep, false, p(x, y), sz(w, h), style = st(width))

    fun path(fill: Color?, outline: Boolean = true, width: Float = 1.6f, build: PathBuilder.() -> Unit) {
        val path = PathBuilder(this).apply(build).path
        if (fill != null) scope.drawPath(path, c(fill))
        if (outline) scope.drawPath(path, c(INK), style = st(width))
    }

    fun rotated(deg: Float, px: Float, py: Float, block: () -> Unit) = scope.rotate(deg, p(px, py)) { block() }
}

private class PathBuilder(val pen: Pen) {
    val path = Path()
    fun m(x: Float, y: Float) = pen.p(x, y).let { path.moveTo(it.x, it.y) }
    fun l(x: Float, y: Float) = pen.p(x, y).let { path.lineTo(it.x, it.y) }
    fun q(x1: Float, y1: Float, x: Float, y: Float) {
        val a = pen.p(x1, y1); val b = pen.p(x, y)
        path.quadraticTo(a.x, a.y, b.x, b.y)
    }
    fun cub(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) {
        val a = pen.p(x1, y1); val b = pen.p(x2, y2); val e = pen.p(x, y)
        path.cubicTo(a.x, a.y, b.x, b.y, e.x, e.y)
    }
    fun close() = path.close()
}

private fun wave(t: Float, phase: Float = 0f) = sin(2 * PI * (t + phase)).toFloat()

// ------------------------------------------------------------------ shared parts

private fun Pen.bottle(cx: Float, top: Float, fill: Float, s: Float = 1f, liquid: Color = MILK) {
    oval(TAN, cx - 3.5f * s, top, 7f * s, 7.5f * s)
    rect(CREAM, cx - 6.5f * s, top + 9f * s, 13f * s, 17f * s, 3.5f * s)
    val h = 15f * s * fill.coerceIn(0f, 1f)
    if (h > 0.5f) rect(liquid, cx - 5f * s, top + 25f * s - h, 10f * s, h, 2.5f * s, outline = false)
    rect(BLUE, cx - 7.5f * s, top + 5.5f * s, 15f * s, 4.5f * s, 1.5f * s)
    line(cx + 3.5f * s, top + 14f * s, cx + 5f * s, top + 14f * s, 1.2f)
    line(cx + 3.5f * s, top + 18f * s, cx + 5f * s, top + 18f * s, 1.2f)
}

private fun Pen.babyHead(x: Float, y: Float, r: Float, asleep: Boolean = true, skin: Color = SKIN) {
    circle(skin, x, y, r)
    if (asleep) {
        arc(INK, x - r * 0.55f, y - r * 0.1f, r * 0.4f, r * 0.3f, 20f, 140f, 1.3f)
        arc(INK, x + r * 0.15f, y - r * 0.1f, r * 0.4f, r * 0.3f, 20f, 140f, 1.3f)
    } else {
        circle(INK, x - r * 0.35f, y - r * 0.05f, r * 0.11f, outline = false)
        circle(INK, x + r * 0.35f, y - r * 0.05f, r * 0.11f, outline = false)
    }
    circle(PINK.copy(alpha = 0.6f), x - r * 0.55f, y + r * 0.3f, r * 0.17f, outline = false)
    circle(PINK.copy(alpha = 0.6f), x + r * 0.55f, y + r * 0.3f, r * 0.17f, outline = false)
    arc(BROWN, x - r * 0.3f, y - r * 1.05f, r * 0.6f, r * 0.5f, 200f, 140f, 1.4f)
}

private fun Pen.heart(x: Float, y: Float, r: Float, col: Color = PINK) = path(col, width = 1.2f) {
    m(x, y + r * 0.9f)
    cub(x - r * 1.6f, y - r * 0.2f, x - r * 0.7f, y - r * 1.3f, x, y - r * 0.45f)
    cub(x + r * 0.7f, y - r * 1.3f, x + r * 1.6f, y - r * 0.2f, x, y + r * 0.9f)
    close()
}

private fun Pen.sparkle(x: Float, y: Float, r: Float, col: Color = YELLOW) = path(col, width = 1f) {
    m(x, y - r); q(x, y, x + r, y); q(x, y, x, y + r); q(x, y, x - r, y); q(x, y, x, y - r); close()
}

private fun Pen.zee(x: Float, y: Float, w: Float, col: Color = NAVY) {
    line(x, y, x + w, y, 1.8f, col)
    line(x + w, y, x, y + w, 1.8f, col)
    line(x, y + w, x + w, y + w, 1.8f, col)
}

private fun Pen.bubbleRing(x: Float, y: Float, r: Float, alpha: Float = 1f) {
    circle(WATER.copy(alpha = 0.35f * alpha), x, y, r, outline = false)
    scope.drawCircle(c(NAVY).copy(alpha = 0.7f * alpha), r * u, p(x, y), style = st(1.1f))
    circle(MILK.copy(alpha = 0.9f * alpha), x - r * 0.35f, y - r * 0.35f, r * 0.22f, outline = false)
}

private fun Pen.magnifier(x: Float, y: Float, r: Float) {
    line(x + r * 0.7f, y + r * 0.7f, x + r * 1.65f, y + r * 1.65f, 3.4f, BROWN)
    circle(MILK.copy(alpha = 0.75f), x, y, r)
    scope.drawCircle(c(INK), r * u, p(x, y), style = st(2.2f))
    arc(MILK, x - r * 0.6f, y - r * 0.6f, r * 0.8f, r * 0.8f, 180f, 80f, 1.4f)
}

private fun Pen.diaperShape(x: Float, y: Float, s: Float, bandHigh: Boolean = false) {
    path(MILK) {
        m(x - 16f * s, y); l(x + 16f * s, y)
        l(x + 10f * s, y + 15f * s); q(x, y + 24f * s, x - 10f * s, y + 15f * s); close()
    }
    rect(BLUE, x - 18f * s, y - 1f * s, 7f * s, 6f * s, 1.5f * s)
    rect(BLUE, x + 11f * s, y - 1f * s, 7f * s, 6f * s, 1.5f * s)
    if (!bandHigh) line(x - 13f * s, y + 3.5f * s, x + 13f * s, y + 3.5f * s, 1.1f, GREY)
}

private fun Pen.mattress(x: Float, y: Float, w: Float, h: Float) {
    rect(TAN, x, y, w, h, 7f)
    rect(SHEET, x + 3f, y + 3f, w - 6f, h - 6f, 5f, outline = false)
}

private fun Pen.scoop(x: Float, y: Float, heap: Float) {
    line(x + 12f, y + 2f, x + 24f, y + 10f, 3f, BLUE)
    path(MILK) { m(x - 2f, y); l(x + 14f, y); q(x + 13f, y + 10f, x + 6f, y + 10f); q(x - 1f, y + 10f, x - 2f, y); close() }
    if (heap > 0f) path(CREAM, width = 1.2f) { m(x - 1f, y); q(x + 6f, y - 8f * heap, x + 13f, y); close() }
}

private fun Pen.parent(x: Float, y: Float, sweater: Color = LAV) {
    path(sweater) { m(x - 14f, y + 32f); q(x - 14f, y + 8f, x, y + 8f); q(x + 14f, y + 8f, x + 14f, y + 32f); close() }
    circle(TAN, x, y, 6.5f)
    arc(BROWN, x - 6f, y - 7f, 12f, 8f, 180f, 180f, 2.2f)
}

private fun Pen.arrow(x1: Float, y: Float, x2: Float, col: Color = NAVY) {
    line(x1, y, x2, y, 3f, col)
    line(x2, y, x2 - 6f, y - 6f, 3f, col)
    line(x2, y, x2 - 6f, y + 6f, 3f, col)
}

private fun Pen.crescent(x: Float, y: Float, r: Float) {
    val a = Path().apply { addOval(Rect(p(x - r, y - r), sz(2 * r, 2 * r))) }
    val b = Path().apply { addOval(Rect(p(x - r * 0.35f, y - r * 1.15f), sz(2 * r, 2 * r))) }
    val moon = Path().apply { op(a, b, PathOperation.Difference) }
    scope.drawPath(moon, c(YELLOW))
    scope.drawPath(moon, c(INK), style = st())
}

// ------------------------------------------------------------------ icons

fun DrawScope.drawCareIcon(icon: CareIcon, t: Float, dim: Float = 1f) {
    val side = min(size.width, size.height)
    val pen = Pen(this, side / 48f, (size.width - side) / 2f, (size.height - side) / 2f, dim)
    pen.draw(icon, t)
}

private fun Pen.draw(icon: CareIcon, t: Float) {
    val w1 = wave(t)
    when (icon) {
        CareIcon.BOTTLE -> {
            bottle(24f, 7f, 0.75f + 0.08f * w1, 1.25f)
            for (i in 0 until 3) {
                val k = (t + i / 3f) % 1f
                circle(MILK, 22f + 4f * i - 4f, 36f - 12f * k, 1.2f + 0.4f * i, outline = false)
            }
        }
        CareIcon.MAKE_BOTTLE -> {
            bottle(17f, 9f, 0.55f, 1.15f, WATER)
            scoop(28f, 20f, 1f)
            for (i in 0 until 3) {
                val k = (t + i / 3f) % 1f
                circle(CREAM, 26f - 4f * k, 18f + 4f * k, 1f, outline = false)
            }
        }
        CareIcon.BREAST -> {
            parent(17f, 12f)
            oval(PINK, 15f, 25f, 25f, 12f)
            babyHead(19f, 28f, 5.5f)
            heart(38f, 12f - 2f * w1, 3.6f + 0.4f * w1)
        }
        CareIcon.BURP -> {
            oval(GREEN, 12f, 22f, 22f, 22f)
            babyHead(23f, 17f, 7.5f)
            val pat = abs(w1) * 4f
            oval(TAN, 30f, 26f - pat, 13f, 9f)
            arc(INK, 38f, 22f - pat, 8f, 8f, -60f, 60f, 1.2f)
            for (i in 0 until 3) {
                val k = (t + i / 3f) % 1f
                bubbleRing(14f + 6f * i, 10f - 8f * k, 1.6f + 0.6f * i, 1f - k)
            }
        }
        CareIcon.DIAPER -> diaperShape(24f, 14f, 1.25f)
        CareIcon.DIAPER_CHECK -> {
            diaperShape(19f, 12f, 1f)
            magnifier(31f + 2f * w1, 30f, 7f)
        }
        CareIcon.HOLD, CareIcon.TOGETHER -> {
            arc(BLUE, 5f, 4f, 38f, 34f, 20f, 140f, 7f)
            oval(PINK, 12f, 22f, 24f, 12f)
            babyHead(19f, 23f, 6.5f)
            if (icon == CareIcon.TOGETHER) rect(BLUE.copy(alpha = 0.9f), 32f, 34f, 14f, 9f, 3f) else heart(37f, 11f - 2f * w1, 3.4f)
        }
        CareIcon.BASSINET -> {
            babyHead(20f, 20f, 5.5f)
            path(TAN) { m(6f, 22f); l(42f, 22f); l(36f, 37f); l(12f, 37f); close() }
            line(9f, 27f, 39f, 27f, 1.1f)
            line(14f, 37f, 11f, 45f, 2f)
            line(34f, 37f, 37f, 45f, 2f)
        }
        CareIcon.PLAY_MAT -> {
            val cols = listOf(PINK, GREEN, BLUE, YELLOW)
            for (i in 0 until 4) rect(cols[i], 5f + i * 9.5f, 33f, 9.5f, 10f, 1f, outline = false)
            rect(Color.Transparent, 5f, 33f, 38f, 10f, 2f)
            arc(YELLOW, 9f, 8f, 30f, 46f, 180f, 180f, 3.2f)
            line(18f, 11f, 18f, 18f, 1f); circle(PINK, 18f, 20f, 2.5f + 0.3f * w1)
            line(30f, 11f, 30f, 17f, 1f); sparkle(30f, 20f, 3f + 0.4f * w1, BLUE)
        }
        CareIcon.ROCK -> rotated(9f * w1, 24f, 40f) {
            arc(BROWN, 6f, 24f, 36f, 18f, 20f, 140f, 2.6f)
            rect(TAN, 11f, 22f, 26f, 13f, 6f)
            babyHead(18f, 22f, 4.8f)
            arc(INK, 1f, 14f, 8f, 16f, 120f, 120f, 1.2f)
            arc(INK, 39f, 14f, 8f, 16f, -60f, 120f, 1.2f)
        }
        CareIcon.SHUSH -> {
            babyHead(15f, 26f, 9f)
            for (i in 0 until 3) {
                val k = (t + i / 3f) % 1f
                scope.drawArc(c(NAVY).copy(alpha = 1f - k), -50f, 100f, false, p(16f + 10f * k + 4f * i, 14f), sz(10f + 6f * i, 24f), style = st(1.8f))
            }
        }
        CareIcon.DIM -> {
            crescent(21f, 25f, 13f)
            sparkle(37f, 11f, 3.5f + 1f * w1)
            sparkle(40f, 31f, 2.5f - 0.8f * w1)
        }
        CareIcon.SLEEP -> {
            babyHead(20f, 26f, 5.5f)
            path(TAN) { m(6f, 28f); l(38f, 28f); l(33f, 41f); l(11f, 41f); close() }
            val k = t
            zee(31f, 14f - 4f * k, 5f)
            zee(39f, 6f - 3f * k, 3.5f)
        }
        CareIcon.PLAY -> rotated(12f * w1, 34f, 40f) {
            line(26f, 26f, 36f, 40f, 5f, PINK)
            circle(PINK, 37f, 41f, 3.5f)
            circle(YELLOW, 21f, 18f, 10f)
            circle(PINK, 17f, 15f, 2f, outline = false); circle(BLUE, 24f, 20f, 2f, outline = false); circle(GREEN, 21f, 13f, 1.6f, outline = false)
            sparkle(8f, 8f, 3f + w1)
        }
        CareIcon.TUMMY -> {
            rect(GREEN.copy(alpha = 0.7f), 3f, 37f, 42f, 6f, 3f)
            oval(GREEN, 9f, 26f, 24f, 12f)
            oval(SKIN, 28f, 32f, 7f, 5f)
            babyHead(34f, 24f - 3f * abs(w1), 7f, asleep = false)
        }
        CareIcon.CHECK -> {
            magnifier(20f, 20f, 11f)
            sparkle(38f, 9f, 3.5f + w1)
        }
        CareIcon.HANDOFF -> {
            parent(12f, 12f, BLUE)
            parent(36f, 12f, LAV)
            oval(PINK, 18f, 22f + 2f * w1, 12f, 8f)
            circle(SKIN, 22f, 23f + 2f * w1, 3.4f)
            arrow(17f, 40f, 31f)
        }
        CareIcon.BREAK -> {
            rect(MILK, 10f, 20f, 22f, 18f, 5f)
            arc(INK, 28f, 23f, 10f, 10f, -90f, 180f, 2f)
            line(6f, 40f, 38f, 40f, 2f)
            for (i in 0 until 2) {
                val x = 16f + i * 9f
                path(null, width = 1.6f) { m(x, 16f); q(x - 3f, 12f + w1, x, 9f); q(x + 3f, 6f, x, 3f) }
            }
        }
        CareIcon.NIGHT -> {
            crescent(20f, 24f, 14f)
            sparkle(37f, 10f, 3.5f + w1)
            sparkle(40f, 30f, 2.5f - 0.8f * w1)
            zee(30f, 34f, 5f)
        }
        CareIcon.END -> {
            line(14f, 8f, 14f, 42f, 2.4f, BROWN)
            path(CORAL) { m(14f, 9f); q(25f, 6f + 2f * w1, 36f, 12f); q(25f, 16f - 2f * w1, 14f, 24f); close() }
        }
        CareIcon.PAUSE -> { rect(NAVY, 13f, 11f, 8f, 26f, 2.5f); rect(NAVY, 27f, 11f, 8f, 26f, 2.5f) }
        CareIcon.RESUME -> path(GREEN) { m(15f, 10f); l(37f, 24f); l(15f, 38f); close() }
        CareIcon.STOP -> rect(CORAL, 13f, 13f, 22f, 22f, 5f)
        CareIcon.WASH -> {
            rotated(-18f, 17f, 26f) { oval(SKIN, 9f, 14f, 15f, 24f) }
            rotated(18f, 31f, 26f) { oval(SKIN, 24f, 14f, 15f, 24f) }
            for (i in 0 until 4) {
                val k = (t + i / 4f) % 1f
                bubbleRing(8f + 10.5f * i, 40f - 34f * k, 2f + (i % 2), 1f - k)
            }
        }
        CareIcon.WATER -> {
            bottle(34f, 16f, 0.25f + 0.6f * t, 0.95f, WATER)
            rotated(28f, 13f, 18f) {
                rect(MILK, 4f, 9f, 17f, 20f, 3f)
                rect(WATER, 6f, 17f, 13f, 10f, 2f, outline = false)
                arc(INK, 18f, 13f, 7f, 10f, -90f, 180f, 1.6f)
            }
            line(23f, 13f, 33f + 0.5f * w1, 22f, 2.4f, WATER)
        }
        CareIcon.POWDER -> {
            rect(YELLOW, 26f, 22f, 17f, 20f, 3f)
            line(29f, 30f, 40f, 30f, 1.2f)
            scoop(8f, 16f, 0f)
            rect(CREAM, 7f, 14.5f, 15f, 2.5f, 1f, outline = false)
            line(4f, 13f + 0.6f * w1, 24f, 13f + 0.6f * w1, 1.3f, GREY)
        }
        CareIcon.POWDER_EXTRA -> {
            scoop(6f, 22f, 1.2f)
            scoop(14f, 12f, 1.2f)
            line(38f, 6f, 38f, 16f, 2.4f); line(33f, 11f, 43f, 11f, 2.4f)
        }
        CareIcon.POWDER_FEWER -> {
            scoop(10f, 18f, 0f)
            rect(CREAM, 11f, 22f, 9f, 4f, 1.5f, outline = false)
            line(33f, 11f, 43f, 11f, 2.4f)
        }
        CareIcon.MIX -> {
            rotated(14f * w1, 24f, 26f) { bottle(24f, 8f, 0.6f, 1.15f) }
            arc(INK, 4f, 16f, 8f, 16f, 120f, 120f, 1.4f)
            arc(INK, 36f, 16f, 8f, 16f, -60f, 120f, 1.4f)
        }
        CareIcon.WARM -> {
            bottle(24f, 6f, 0.7f, 0.9f)
            path(WATER) { m(5f, 25f); l(43f, 25f); q(42f, 42f, 24f, 42f); q(6f, 42f, 5f, 25f); close() }
            for (x in listOf(10f, 38f)) path(null, width = 1.5f) { m(x, 20f); q(x - 3f, 16f + w1, x, 12f); q(x + 3f, 8f, x, 4f) }
        }
        CareIcon.MICROWAVE -> {
            rect(GREY, 4f, 11f, 40f, 27f, 4f)
            rect(NAVY.copy(alpha = 0.55f + 0.2f * w1), 8f, 15f, 23f, 19f, 2f)
            circle(MILK, 37.5f, 20f, 2.6f); circle(MILK, 37.5f, 29f, 2.6f)
        }
        CareIcon.WRIST -> {
            rect(SKIN, 2f, 30f, 44f, 12f, 6f)
            line(30f, 32f, 30f, 40f, 1f, BROWN)
            oval(TAN, 21f, 0f, 8f, 8f)
            val k = t
            path(MILK, width = 1.2f) { val y = 10f + 16f * k; m(25f, y); q(28f, y + 5f, 25f, y + 6f); q(22f, y + 5f, 25f, y); close() }
        }
        CareIcon.THERMO -> {
            bottle(16f, 9f, 0.7f, 1.05f)
            rect(MILK, 32f, 8f, 7f, 25f, 3.5f)
            rect(CORAL, 34.5f, 20f - 3f * w1, 2f, 13f + 3f * w1, 1f, outline = false)
            circle(CORAL, 35.5f, 36f, 4.8f)
        }
        CareIcon.SHOULDER -> {
            parent(17f, 13f)
            oval(GREEN, 24f, 16f, 14f, 18f)
            babyHead(31f, 13f, 6.5f)
            oval(TAN, 28f, 27f - 2f * abs(w1), 9f, 7f)
        }
        CareIcon.LAP -> {
            rect(LAV, 3f, 35f, 42f, 9f, 4.5f)
            oval(GREEN, 15f, 18f, 18f, 19f)
            babyHead(24f, 13f, 7f, asleep = false)
            oval(TAN, 27f, 18f, 10f, 6f)
        }
        CareIcon.WIPES -> {
            rect(BLUE, 5f, 22f, 38f, 18f, 6f)
            oval(SHEET, 15f, 24f, 18f, 6f)
            path(MILK) { m(17f, 27f); q(20f, 10f + 2f * w1, 24f, 8f + 2f * w1); q(28f, 10f + 2f * w1, 31f, 27f); close() }
        }
        CareIcon.GATHER -> {
            diaperShape(16f, 12f, 0.6f)
            rect(BLUE, 24f, 13f, 16f, 10f, 3f)
            rect(TAN, 5f, 22f, 38f, 18f, 5f)
            for (i in 0 until 3) line(10f + i * 13f, 25f, 12f + i * 13f, 37f, 1.1f, BROWN)
        }
        CareIcon.FORWARD -> {
            arrow(10f + 2f * w1, 24f, 38f + 2f * w1)
            line(4f, 17f, 8f, 17f, 1.5f); line(3f, 31f, 7f, 31f, 1.5f)
        }
        CareIcon.CHANGING -> {
            rect(CREAM, 3f, 23f, 42f, 15f, 7f)
            oval(GREEN, 15f, 22f, 20f, 11f)
            diaperShape(36f, 24f, 0.35f)
            babyHead(11f, 27f, 6f)
            val kick = 2f * w1
            oval(SKIN, 37f + kick, 22f, 5f, 4f, outline = false); oval(SKIN, 37f - kick, 29f, 5f, 4f, outline = false)
        }
        CareIcon.CLOTHES -> {
            rotated(-12f, 12f, 18f) { oval(GREEN, 4f, 13f, 14f, 9f) }
            rotated(12f, 36f, 18f) { oval(GREEN, 30f, 13f, 14f, 9f) }
            path(GREEN) { m(14f, 13f); l(34f, 13f); l(33f, 38f); q(24f, 44f, 15f, 38f); close() }
            arc(INK, 19f, 9f, 10f, 8f, 0f, 180f, 1.6f)
            for (i in 0 until 3) circle(MILK, 24f, 20f + i * 5f, 1.1f, outline = false)
            sparkle(40f, 36f, 3f + w1)
        }
        CareIcon.CORD_BELOW, CareIcon.CORD_OVER -> {
            rect(SKIN, 8f, 6f, 32f, 26f, 8f)
            circle(BROWN, 24f, 16f, 2.4f)
            if (icon == CareIcon.CORD_BELOW) {
                diaperShape(24f, 23f, 1.05f)
                line(10f, 23f, 38f, 23f, 1.4f, GREY)
            } else {
                diaperShape(24f, 12f, 1.05f, bandHigh = true)
                rect(MILK, 8f, 12f, 32f, 10f, 2f)
            }
        }
        CareIcon.BACK -> {
            mattress(6f, 6f, 36f, 38f)
            oval(GREEN, 17f, 21f, 14f, 18f)
            babyHead(24f, 16f, 6.5f)
        }
        CareIcon.SIDE -> {
            mattress(6f, 6f, 36f, 38f)
            oval(GREEN, 20f, 18f, 11f, 20f)
            circle(SKIN, 22f, 14f, 6.5f)
            arc(INK, 16.5f, 12.5f, 4f, 3f, 20f, 140f, 1.3f)
            arc(BROWN, 21f, 7.5f, 7f, 7f, 270f, 120f, 1.6f)
        }
        CareIcon.FACE_DOWN -> {
            mattress(6f, 6f, 36f, 38f)
            oval(GREEN, 17f, 21f, 14f, 18f)
            circle(SKIN, 24f, 16f, 6.5f)
            path(BROWN.copy(alpha = 0.8f), outline = false) { m(18f, 15f); q(24f, 6f, 30f, 15f); q(24f, 12f, 18f, 15f); close() }
        }
        CareIcon.BLANKET -> path(PINK) {
            m(6f, 12f); q(15f, 8f, 24f, 12f); q(33f, 16f, 42f, 12f); l(42f, 36f); q(33f, 40f, 24f, 36f); q(15f, 32f, 6f, 36f); close()
        }.also { for (i in 0 until 3) line(10f + i * 12f, 20f, 14f + i * 12f, 28f, 1f, MILK) }
        CareIcon.PILLOW -> {
            path(MILK) { m(6f, 14f); q(24f, 8f, 42f, 14f); q(46f, 24f, 42f, 34f); q(24f, 40f, 6f, 34f); q(2f, 24f, 6f, 14f); close() }
            line(14f, 24f, 34f, 24f, 1f, GREY)
        }
        CareIcon.TOY -> {
            circle(BROWN, 15f, 10f, 4.5f); circle(BROWN, 33f, 10f, 4.5f)
            oval(BROWN, 13f, 24f, 22f, 20f)
            circle(BROWN, 24f, 17f, 10f)
            oval(TAN, 19f, 18f, 10f, 7f)
            circle(INK, 20f, 14f, 1.2f, outline = false); circle(INK, 28f, 14f, 1.2f, outline = false)
        }
        CareIcon.BUMPER -> {
            rect(SHEET, 6f, 8f, 36f, 32f, 4f)
            scope.drawRoundRect(c(GREEN), p(8.5f, 10.5f), sz(31f, 27f), CornerRadius(4f * u), style = st(5f))
            for (i in 0 until 4) line(13f + i * 7.5f, 4f, 13f + i * 7.5f, 9f, 1.6f, BROWN)
        }
        CareIcon.NURSING_PILLOW -> {
            arc(YELLOW, 8f, 6f, 32f, 34f, 200f, 320f, 9f)
            arc(INK, 3.5f, 1.5f, 41f, 43f, 205f, 310f, 1.2f)
        }
        CareIcon.CLEAR -> {
            oval(TAN, 5f, 5f, 38f, 38f)
            oval(SHEET, 10f, 10f, 28f, 28f)
            arc(MILK, 14f, 14f, 20f, 20f, 200f, 60f, 1.4f)
        }
        CareIcon.FACE -> {
            circle(TAN, 13f, 22f, 10f)
            arc(BROWN, 4f, 11f, 18f, 10f, 180f, 180f, 2.6f)
            circle(INK, 17f, 20f, 1.2f, outline = false)
            arc(INK, 15f, 23f, 6f, 4f, 20f, 140f, 1.3f)
            babyHead(36f, 27f, 7f, asleep = false)
            val k = t
            heart(25f, 14f - 6f * k, 2.6f, PINK.copy(alpha = 1f - k))
        }
        CareIcon.REST -> {
            babyHead(21f, 27f, 11f)
            zee(33f, 7f + 2f * w1, 6f)
        }
        CareIcon.AGAIN -> {
            arc(NAVY, 10f, 10f, 28f, 28f, -50f, 280f, 3.4f)
            line(33f, 13f, 34f, 5f, 3.2f, NAVY); line(33f, 13f, 41f, 14f, 3.2f, NAVY)
        }
        CareIcon.BOTTLE_DOWN -> {
            line(4f, 36f, 44f, 36f, 2f, BROWN)
            rotated(90f, 24f, 22f) { bottle(24f, 10f, 0.25f, 1f) }
        }
        CareIcon.BOTTLE_PUSH -> {
            babyHead(10f, 24f, 7.5f, asleep = false)
            rotated(-70f, 28f, 22f) { bottle(28f, 9f, 0.6f, 0.95f) }
            arrow(34f, 40f, 22f + 2f * w1)
        }
        CareIcon.TILT -> {
            babyHead(11f, 26f, 7.5f, asleep = false)
            rotated(-115f, 29f, 22f) { bottle(29f, 9f, 0.6f, 0.95f) }
            rect(NAVY, 36f, 4f, 3f, 9f, 1f, outline = false); rect(NAVY, 41f, 4f, 3f, 9f, 1f, outline = false)
        }
        CareIcon.PROP -> {
            path(MILK) { m(2f, 32f); q(18f, 26f, 34f, 32f); q(36f, 38f, 34f, 44f); q(18f, 46f, 2f, 44f); q(0f, 38f, 2f, 32f); close() }
            rotated(-55f, 30f, 22f) { bottle(30f, 8f, 0.6f, 0.95f) }
        }
        CareIcon.CRADLE, CareIcon.CROSS, CareIcon.FOOTBALL, CareIcon.LAIDBACK -> holdVariant(icon, w1)
        CareIcon.PINCH -> {
            path(YELLOW) { m(26f, 4f); l(14f, 26f); l(23f, 26f); l(18f, 44f); l(34f, 18f); l(25f, 18f); close() }
            line(6f, 10f, 10f, 14f, 1.6f, CORAL); line(40f, 30f, 44f, 34f, 1.6f, CORAL); line(38f, 8f, 42f, 6f, 1.6f, CORAL)
        }
        CareIcon.STEP_AWAY -> {
            rect(CREAM, 2f, 26f, 22f, 9f, 4.5f)
            circle(SKIN, 7f, 27f, 3.5f)
            for (i in 0 until 3) circle(NAVY, 28f + i * 6f + 2f * w1, 30f, 1.4f, outline = false)
            line(44f, 30f, 40f, 26f, 2f, NAVY); line(44f, 30f, 40f, 34f, 2f, NAVY)
            parent(36f, 6f, BLUE)
        }
    }
}

private fun Pen.holdVariant(icon: CareIcon, w1: Float) {
    when (icon) {
        CareIcon.LAIDBACK -> {
            rotated(-35f, 22f, 30f) { parent(22f, 12f) }
            rotated(-35f, 24f, 28f) { oval(PINK, 16f, 22f, 20f, 9f) }
            babyHead(30f, 21f + w1, 5f)
        }
        else -> {
            parent(16f, 9f)
            when (icon) {
                CareIcon.CRADLE -> { oval(PINK, 10f, 24f, 26f, 10f); babyHead(13f, 27f, 5f) }
                CareIcon.CROSS -> { oval(PINK, 10f, 24f, 26f, 10f); babyHead(35f, 27f, 5f); line(8f, 34f, 34f, 22f, 3.4f, BLUE) }
                else -> { oval(PINK, 26f, 22f, 18f, 11f); babyHead(28f, 25f, 5f) }
            }
            heart(40f, 10f - 2f * w1, 3f)
        }
    }
}

// ------------------------------------------------------------------ mapping the simulation onto pictures

fun sessionIcon(kind: SessionKind): CareIcon = when (kind) {
    SessionKind.BOTTLE_PREP -> CareIcon.MAKE_BOTTLE
    SessionKind.BOTTLE_FEED -> CareIcon.BOTTLE
    SessionKind.BREASTFEED -> CareIcon.BREAST
    SessionKind.BURP -> CareIcon.BURP
    SessionKind.DIAPER_CHANGE -> CareIcon.DIAPER
    SessionKind.SETTLE -> CareIcon.SLEEP
    SessionKind.PLAY_TALK -> CareIcon.FACE
    SessionKind.TUMMY_TIME -> CareIcon.TUMMY
}

fun stepIcon(kind: SessionKind, stepId: String): CareIcon {
    val id = stepId.removeSuffix("_do")
    return when {
        id == "wash" && kind == SessionKind.DIAPER_CHANGE -> CareIcon.WASH
        id == "wash" -> CareIcon.WASH
        id == "water" -> CareIcon.WATER
        id == "powder" -> CareIcon.POWDER
        id == "mix" -> CareIcon.MIX
        id == "warm" -> CareIcon.WARM
        id == "test" -> CareIcon.WRIST
        id == "hold" && kind == SessionKind.BURP -> CareIcon.SHOULDER
        id == "hold" -> CareIcon.HOLD
        id.contains("burp") || id == "pat2" || id == "gas" -> CareIcon.BURP
        id == "supplies" || id == "missing" -> CareIcon.GATHER
        id == "lay" -> CareIcon.CHANGING
        id == "open" -> CareIcon.DIAPER_CHECK
        id == "clean" || id == "fountain" -> CareIcon.WIPES
        id == "fresh" || id == "close" -> CareIcon.DIAPER
        id == "dress" -> CareIcon.CLOTHES
        id == "space" -> CareIcon.CLEAR
        id == "position" && kind == SessionKind.SETTLE -> CareIcon.BACK
        id == "position" -> CareIcon.CRADLE
        id == "watch" -> CareIcon.SLEEP
        id.startsWith("face") || id == "more" -> CareIcon.FACE
        id == "overstim" -> CareIcon.REST
        id == "start" || id == "tummy" -> CareIcon.TUMMY
        id == "end_tummy" -> CareIcon.HOLD
        id == "offer" || id.startsWith("feed") -> CareIcon.BOTTLE
        id.startsWith("pace") -> CareIcon.TILT
        id == "fullness" -> CareIcon.BOTTLE_DOWN
        id == "cleanup" -> CareIcon.WASH
        id == "latch" || id.startsWith("side") || id == "switch" || id == "shallow" -> CareIcon.BREAST
        else -> sessionIcon(kind)
    }
}

fun choiceIcon(id: String): CareIcon = when (id) {
    "water_first" -> CareIcon.WATER
    "powder_first" -> CareIcon.POWDER
    "exact" -> CareIcon.POWDER
    "extra" -> CareIcon.POWDER_EXTRA
    "fewer" -> CareIcon.POWDER_FEWER
    "room" -> CareIcon.THERMO
    "bowl" -> CareIcon.WARM
    "microwave" -> CareIcon.MICROWAVE
    "shoulder" -> CareIcon.SHOULDER
    "lap" -> CareIcon.LAP
    "gather" -> CareIcon.GATHER
    "skip" -> CareIcon.FORWARD
    "below_stump" -> CareIcon.CORD_BELOW
    "over_stump" -> CareIcon.CORD_OVER
    "back" -> CareIcon.BACK
    "side" -> CareIcon.SIDE
    "tummy" -> CareIcon.FACE_DOWN
    "floor" -> CareIcon.TUMMY
    "pause" -> CareIcon.TILT
    "push" -> CareIcon.BOTTLE_PUSH
    "close" -> CareIcon.HOLD
    "prop" -> CareIcon.PROP
    "burp_now", "burp_mid", "switch" -> CareIcon.BURP
    "skip_burp", "keep", "more", "endure", "longer" -> CareIcon.FORWARD
    "cradle" -> CareIcon.CRADLE
    "cross" -> CareIcon.CROSS
    "football" -> CareIcon.FOOTBALL
    "laidback" -> CareIcon.LAIDBACK
    "end_one", "end_feed" -> CareIcon.BOTTLE_DOWN
    "finish" -> CareIcon.BOTTLE_PUSH
    "clear" -> CareIcon.CLEAR
    "rest" -> CareIcon.REST
    "again" -> CareIcon.AGAIN
    "stop" -> CareIcon.STOP
    "relatch" -> CareIcon.AGAIN
    "step_away" -> CareIcon.STEP_AWAY
    "together" -> CareIcon.TOGETHER
    "remove:blanket" -> CareIcon.BLANKET
    "remove:pillow" -> CareIcon.PILLOW
    "remove:toy" -> CareIcon.TOY
    "remove:bumper" -> CareIcon.BUMPER
    "remove:nursing_pillow" -> CareIcon.NURSING_PILLOW
    else -> CareIcon.FORWARD
}

/** Two or three words under each picture. The full sentence stays available to screen readers. */
fun choiceCaption(id: String, fallback: String): String = when (id) {
    "water_first" -> "Water first"
    "powder_first" -> "Powder first"
    "exact" -> "Level scoops, as labeled"
    "extra" -> "An extra scoop"
    "fewer" -> "Fewer scoops"
    "room" -> "Room temperature"
    "bowl" -> "Bowl of warm water"
    "microwave" -> "Microwave it"
    "shoulder" -> "On your shoulder"
    "lap" -> "Sitting on your lap"
    "gather" -> "Gather it all first"
    "skip" -> "Start right away"
    "below_stump" -> "Fold below the cord"
    "over_stump" -> "Up over the belly"
    "back" -> "On their back"
    "side" -> "On their side"
    "tummy" -> "On their tummy"
    "floor" -> "Firm floor, stay close"
    "pause" -> "Pause and tilt"
    "push" -> "Keep the bottle in"
    "close" -> "Hold close, semi-upright"
    "prop" -> "Prop the bottle"
    "burp_now" -> "Burp now"
    "skip_burp" -> "Skip it"
    "burp_mid" -> "Try a burp"
    "keep" -> "Keep feeding"
    "cradle" -> "Cradle"
    "cross" -> "Cross-cradle"
    "football" -> "Football"
    "laidback" -> "Laid-back"
    "switch" -> "Burp, other side"
    "end_one" -> "End here"
    "end_feed" -> "End the feed"
    "finish" -> "Encourage more"
    "clear" -> "It's clear"
    "rest" -> "Quiet rest"
    "more" -> "Keep playing"
    "again" -> "Another round"
    "longer" -> "A little longer"
    "stop" -> "Stop for now"
    "relatch" -> "Re-latch"
    "endure" -> "Keep going"
    "step_away" -> "Step away quickly"
    "together" -> "Take them with you"
    else -> if (id.startsWith("remove:")) "Remove the " + id.removePrefix("remove:").replace('_', ' ') else fallback
}

fun actionIcon(stepId: String): CareIcon = when (stepId) {
    "offer" -> CareIcon.BOTTLE
    "latch" -> CareIcon.BREAST
    "open" -> CareIcon.DIAPER_CHECK
    "close" -> CareIcon.DIAPER
    "end_tummy" -> CareIcon.HOLD
    else -> CareIcon.FORWARD
}

fun actionCaption(stepId: String, fallback: String): String = when (stepId) {
    "offer" -> "Offer the bottle"
    "latch" -> "Latch"
    "open" -> "Open and check"
    "close" -> "Fasten it"
    "end_tummy" -> "Roll over and pick up"
    else -> fallback
}
