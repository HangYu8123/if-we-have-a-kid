package app.childrensimulator.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import app.childrensimulator.sim.engine.Mouth
import app.childrensimulator.sim.engine.Pose
import app.childrensimulator.sim.model.Caregiver
import app.childrensimulator.sim.model.Place
import app.childrensimulator.sim.model.Technique
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** What the caregiver is doing with the baby right now, so the matching body parts animate. */
enum class BabyActivity { NONE, BOTTLE_OFFER, BOTTLE_FEED, BREASTFEED, BURP, DIAPER_ON, DIAPER_CLEAN, DIAPER_FRESH, PLAY, TUMMY }

/** Parts of the baby the player can touch to check them (feedback v0.2: hints come from checking). */
enum class BodyPart { FACE, HAND, TUMMY, DIAPER, FEET, WHOLE }

/** Short pet-game style emotes that answer a care action. */
enum class Reaction { HEARTS, NOTES, SPARKLE, STARS, ZZZ, BURP, DROP, STINK, EXCLAIM, MAGNIFY, PUFF }

/** Everything the illustration needs, derived from the simulation view. */
data class BabyVisual(
    val pose: Pose,
    val place: Place,
    val heldBy: Caregiver?,
    val techniques: Set<Technique>,
    val skinTone: Int,
    val hazards: List<String> = emptyList(),
    val night: Boolean = false,
    /** False draws only the scene, for example the bassinet during the sleep-space check. */
    val showBaby: Boolean = true,
    val activity: BabyActivity = BabyActivity.NONE,
    /** Draws the nursery around the scene. */
    val room: Boolean = false,
    /** Local hour of day for the window sky. */
    val hour: Float = 12f,
)

/** Transient touch and reaction animations layered over the simulated pose. */
@Stable
class BabyFxState(private val scope: CoroutineScope) {
    var touch by mutableStateOf<BodyPart?>(null)
        private set
    var touchPoint by mutableStateOf(Offset.Zero)
        private set
    val touchProgress = Animatable(1f)
    var reaction by mutableStateOf<Reaction?>(null)
        private set
    val reactionProgress = Animatable(1f)

    fun touch(part: BodyPart, point: Offset) {
        touch = part
        touchPoint = point
        scope.launch {
            touchProgress.snapTo(0f)
            touchProgress.animateTo(1f, tween(1300, easing = LinearEasing))
        }
    }

    fun react(r: Reaction) {
        reaction = r
        scope.launch {
            reactionProgress.snapTo(0f)
            reactionProgress.animateTo(1f, tween(1900, easing = LinearEasing))
        }
    }
}

@Composable
fun rememberBabyFx(): BabyFxState {
    val scope = rememberCoroutineScope()
    return remember { BabyFxState(scope) }
}

private class StageGeom(val s: Float, val cx: Float, val cy: Float)

private fun stageGeom(w: Float, h: Float, room: Boolean): StageGeom {
    val base = min(w / 360f, h / 336f)
    return if (room) StageGeom(base * 0.8f, w / 2f, h * 0.57f) else StageGeom(base, w / 2f, h / 2f)
}

/** Which part of the drawn baby is under a touch, in canvas pixels. */
fun hitTestBaby(v: BabyVisual, x: Float, y: Float, w: Float, h: Float): BodyPart? {
    if (!v.showBaby) return null
    val g = stageGeom(w, h, v.room)
    val k = g.s * (if (v.place == Place.BASSINET) 0.88f else 1f)
    val dx = (x - g.cx) / k
    val dy = (y - g.cy) / k
    val p = v.pose
    fun near(px: Float, py: Float, r: Float) = (dx - px) * (dx - px) + (dy - py) * (dy - py) <= r * r
    if (p.onTummy) {
        return when {
            near(0f, -25f, 54f) -> BodyPart.FACE
            dy in 5f..95f && abs(dx) <= 88f -> BodyPart.TUMMY
            else -> null
        }
    }
    val turn = p.headTurn * 14f
    if (p.handsToMouth) {
        if (near(-20f + turn, -18f, 15f) || near(18f + turn, -16f, 15f)) return BodyPart.HAND
    } else if (near(-64f, 68f, 22f) || near(64f, 68f, 22f)) {
        return BodyPart.HAND
    }
    if (p.legsUp && (near(-32f, 66f, 22f) || near(32f, 66f, 22f))) return BodyPart.FEET
    return when {
        near(turn, -45f, 62f) -> BodyPart.FACE
        abs(dx) <= 62f && dy in -5f..52f -> BodyPart.TUMMY
        abs(dx) <= 62f && dy in 52f..106f -> BodyPart.DIAPER
        abs(dx) <= 58f && dy in 106f..162f -> BodyPart.FEET
        else -> null
    }
}

private class Anim(
    val breath: Float, val cry: Float, val rock: Float, val drift: Float, val wig: Float,
    val fast: Float, val pat: Float, val slow: Float, val blink: Float, val motion: Boolean,
)

private fun bump(p: Float) = if (p in 0f..1f) sin(PI * p).toFloat() else 0f
private fun sw(x: Float, phase: Float = 0f) = sin(2 * PI * x + phase).toFloat()

/**
 * A living 2D baby (docs/10 §11): breathing, blinking, wiggling and kicking, crying with flailing limbs, sucking during
 * feeds, reflexes when touched, plus the scene (nursery, bassinet, arms, changing table, play mat). Always paired with
 * a text description for screen readers.
 */
@Composable
fun BabyCanvas(visual: BabyVisual, description: String, reducedMotion: Boolean, modifier: Modifier = Modifier, fx: BabyFxState? = null) {
    val transition = rememberInfiniteTransition(label = "baby")
    val breath by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3800, easing = LinearEasing)), label = "breath")
    val cry by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "cry")
    val rock by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "rock")
    val drift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "drift")
    val wig by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "wiggle")
    val fast by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing)), label = "fast")
    val pat by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(760, easing = LinearEasing)), label = "pat")
    val slow by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "slow")
    val blink by transition.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes { durationMillis = 4300; 1f at 0; 1f at 3900; 0f at 4010; 1f at 4140 }),
        label = "blink",
    )
    val textMeasurer = rememberTextMeasurer()
    val motion = !reducedMotion

    Canvas(modifier.semantics { contentDescription = description }) {
        val a = if (motion) Anim(breath, cry, rock, drift, wig, fast, pat, slow, blink, true)
        else Anim(0f, 0.5f, 0f, 0.3f, 0f, 0f, 0f, 0f, 1f, false)
        val g = stageGeom(size.width, size.height, visual.room)
        val s = g.s
        val cx = g.cx
        val cy = g.cy
        if (visual.room) drawRoom(visual, a, size.width / 360f)
        drawScene(visual, s, cx, cy, visual.night)
        if (visual.room && visual.place == Place.BASSINET) drawMobile(s, cx, cy, a, visual.night)
        if (!visual.showBaby) return@Canvas

        // Interaction state, read here so only drawing (not composition) follows the animation.
        val touch = fx?.touch
        val tp = fx?.touchProgress?.value ?: 1f
        val tb = if (touch != null && tp < 1f) bump(tp) else 0f
        val touchSide = if ((fx?.touchPoint?.x ?: cx) < cx) -1f else 1f
        val reaction = fx?.reaction
        val rp = fx?.reactionProgress?.value ?: 1f

        val base = visual.pose
        val crying = base.mouth == Mouth.CRY
        var pose = base
        if (visual.activity == BabyActivity.BREASTFEED) pose = pose.copy(headTurn = -0.75f)
        if (tb > 0f) {
            pose = when (touch) {
                BodyPart.FACE -> when {
                    base.asleep -> pose.copy(headTurn = touchSide * 0.25f * tb)
                    crying || base.mouth == Mouth.SUCKING -> pose
                    else -> pose.copy(mouth = Mouth.ROOTING, headTurn = touchSide * 0.85f * tb, eyesOpen = true)
                }
                BodyPart.TUMMY -> if (base.asleep) pose else pose.copy(legsUp = true)
                else -> pose
            }
        }
        val positive = reaction in setOf(Reaction.HEARTS, Reaction.NOTES, Reaction.SPARKLE, Reaction.BURP)
        val happy = positive && rp < 0.92f && !crying && !base.asleep && base.mouth != Mouth.SUCKING
        if (happy) pose = pose.copy(mouth = Mouth.SMILE)

        val rocking = Technique.ROCK in visual.techniques && visual.heldBy != null
        val angle = if (rocking && motion) 6f * sw(a.rock) else 0f
        val breathe = if (motion) 1f + (if (base.asleep) 0.024f else 0.016f) * sw(a.breath) else 1f
        val fit = if (visual.place == Place.BASSINET) 0.88f else 1f
        val shake = if (crying && motion) (if (base.redFace >= 1f) 2.2f else 1.2f) * s * sw(a.fast, 0.7f) else 0f

        if (visual.activity == BabyActivity.BREASTFEED) drawNursingParent(s, cx, cy, visual.night)
        withTransform({
            translate(shake, 0f)
            rotate(angle, Offset(cx, cy + 120 * s))
            scale(breathe * fit, breathe * fit, Offset(cx, cy))
        }) {
            drawBaby(visual, pose, s, cx, cy, a, happy, touch.takeIf { tb > 0f }, tb, touchSide)
        }
        if (visual.activity == BabyActivity.PLAY) drawPlayFace(s, cx, cy, a, visual.night)

        // Comfort overlays.
        if (Technique.SHUSH in visual.techniques) {
            for (i in 0 until 3) {
                val k = if (motion) ((a.drift + i / 3f) % 1f) else 0.5f
                drawArc(
                    color = Color(0xFF6E8FB0).copy(alpha = 0.6f * (1f - k)),
                    startAngle = -40f, sweepAngle = 80f, useCenter = false,
                    topLeft = Offset(cx + 60 * s + k * 30 * s, cy - 70 * s - k * 10 * s),
                    size = Size(30 * s + k * 40 * s, 50 * s + k * 30 * s),
                    style = Stroke(width = 3 * s, cap = StrokeCap.Round),
                )
            }
        }
        if (base.asleep) {
            val k = if (motion) a.drift else 0.3f
            drawText(
                textMeasurer, "z", topLeft = Offset(cx + 55 * s + k * 20 * s, cy - 95 * s - k * 40 * s),
                style = TextStyle(color = Color(0xFF7C8BA6).copy(alpha = 1f - k), fontSize = (16 + 10 * k).sp),
            )
            drawText(
                textMeasurer, "z", topLeft = Offset(cx + 35 * s + k * 15 * s, cy - 70 * s - k * 25 * s),
                style = TextStyle(color = Color(0xFF7C8BA6).copy(alpha = 0.8f * (1f - k)), fontSize = 12.sp),
            )
        }
        if (Technique.CALM_ROOM in visual.techniques) {
            drawRect(Color(0xFF000814).copy(alpha = if (visual.night) 0.15f else 0.28f))
            if (!visual.room) {
                drawCircle(Color(0xFFF3E3A1), radius = 14 * s, center = Offset(size.width - 34 * s, 30 * s))
                drawCircle(if (visual.night) Color(0xFF181B25) else Color(0xFF4A4F5E), radius = 12 * s, center = Offset(size.width - 28 * s, 26 * s))
            }
        }
        // Touch ripple and emotes.
        if (fx != null && tp < 1f) {
            drawCircle(Color.White.copy(alpha = 0.7f * (1f - tp)), radius = (10f + 34f * tp) * s, center = fx.touchPoint, style = Stroke(width = 3 * s))
        }
        if (reaction != null && rp < 1f) {
            val headY = cy + (if (base.onTummy) -25f else -45f) * s * fit
            drawReaction(reaction, rp, s, cx, cy, headY, textMeasurer)
        }
    }
}

// ------------------------------------------------------------------ room and scene

private fun DrawScope.drawRoom(v: BabyVisual, a: Anim, unit: Float) {
    val dim = if (v.night) 0.5f else 1f
    fun c(color: Color) = Color(color.red * dim, color.green * dim, color.blue * dim, color.alpha)
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(c(Color(0xFFFBEFE3)), c(Color(0xFFF2DCCB)))))
    // Wallpaper dots.
    val step = 30 * unit
    var row = 0
    var y = step / 2
    while (y < h * 0.82f) {
        var x = if (row % 2 == 0) step / 2 else step
        while (x < w) {
            drawCircle(c(Color(0xFFE9CDBB)).copy(alpha = 0.5f), radius = 2.4f * unit, center = Offset(x, y))
            x += step
        }
        y += step * 0.8f
        row++
    }
    // Floor.
    val floorY = h * 0.84f
    drawRect(c(Color(0xFFD8B48E)), topLeft = Offset(0f, floorY), size = Size(w, h - floorY))
    drawLine(c(Color(0xFFC29C76)), Offset(0f, floorY), Offset(w, floorY), strokeWidth = 3 * unit)
    for (i in 1 until 5) drawLine(c(Color(0xFFC9A47E)), Offset(w * i / 5f, floorY), Offset(w * i / 5f - 30 * unit, h), strokeWidth = 1.5f * unit)

    // Window with the real time of day.
    val wx = 14 * unit
    val wy = 12 * unit
    val ww = 92 * unit
    val wh = 74 * unit
    val hour = v.hour
    val (top, bottom) = when {
        hour >= 6f && hour < 8f -> Color(0xFFF7C6A3) to Color(0xFFFDE7C8)
        hour >= 8f && hour < 17f -> Color(0xFF9ED3F2) to Color(0xFFD7EEFA)
        hour >= 17f && hour < 20f -> Color(0xFFEE9A7E) to Color(0xFFF8D2A0)
        else -> Color(0xFF1C2647) to Color(0xFF34406B)
    }
    val nightSky = hour >= 20f || hour < 6f
    drawRoundRect(Brush.verticalGradient(listOf(c(top), c(bottom)), wy, wy + wh), Offset(wx, wy), Size(ww, wh), CornerRadius(6 * unit))
    if (nightSky) {
        for (i in 0 until 6) {
            val tw = if (a.motion) 0.5f + 0.5f * sw(a.slow * 6f, i * 1.7f) else 0.8f
            drawCircle(Color(0xFFFFF4C8).copy(alpha = 0.4f + 0.6f * tw), radius = 1.6f * unit, center = Offset(wx + ww * ((i * 37 % 89) / 89f), wy + wh * ((i * 53 % 61) / 80f + 0.08f)))
        }
        drawCircle(Color(0xFFF6EBC0), radius = 11 * unit, center = Offset(wx + ww * 0.7f, wy + wh * 0.32f))
        drawCircle(c(top), radius = 9 * unit, center = Offset(wx + ww * 0.7f + 5 * unit, wy + wh * 0.32f - 3 * unit))
    } else {
        val sunY = if (hour < 8f || hour >= 17f) wy + wh * 0.62f else wy + wh * 0.3f
        drawCircle(c(Color(0xFFFFE08A)), radius = 11 * unit, center = Offset(wx + ww * 0.72f, sunY))
        val cloudX = wx + ((a.slow * 1.3f) % 1f) * (ww + 40 * unit) - 20 * unit
        for ((dx, r) in listOf(0f to 8f, 9f to 10f, 19f to 7f)) {
            drawCircle(c(Color.White).copy(alpha = 0.9f), radius = r * unit, center = Offset(cloudX + dx * unit, wy + wh * 0.5f))
        }
    }
    drawRoundRect(c(Color.White), Offset(wx, wy), Size(ww, wh), CornerRadius(6 * unit), style = Stroke(width = 5 * unit))
    drawLine(c(Color.White), Offset(wx + ww / 2, wy), Offset(wx + ww / 2, wy + wh), strokeWidth = 3 * unit)
    drawLine(c(Color.White), Offset(wx, wy + wh / 2), Offset(wx + ww, wy + wh / 2), strokeWidth = 3 * unit)
    // Curtains.
    for (side in listOf(-1f, 1f)) {
        val x0 = if (side < 0) wx - 8 * unit else wx + ww - 10 * unit
        val curtain = Path().apply {
            moveTo(x0, wy - 6 * unit)
            lineTo(x0 + 18 * unit, wy - 6 * unit)
            quadraticTo(x0 + 6 * unit + (if (a.motion) 2 * unit * sw(a.slow * 4f) else 0f), wy + wh * 0.5f, x0 + 16 * unit, wy + wh + 10 * unit)
            lineTo(x0, wy + wh + 10 * unit)
            close()
        }
        drawPath(curtain, c(Color(0xFFF3B9B2)))
    }
    drawLine(c(Color(0xFFB98552)), Offset(wx - 14 * unit, wy - 6 * unit), Offset(wx + ww + 14 * unit, wy - 6 * unit), strokeWidth = 3 * unit, cap = StrokeCap.Round)

    // Shelf with a plant and a toy.
    val sx = w - 104 * unit
    val sy = 58 * unit
    drawRoundRect(c(Color(0xFFC79A6E)), Offset(sx, sy), Size(90 * unit, 6 * unit), CornerRadius(3 * unit))
    drawRoundRect(c(Color(0xFFE48F6E)), Offset(sx + 8 * unit, sy - 16 * unit), Size(16 * unit, 16 * unit), CornerRadius(3 * unit))
    for (i in -1..1) drawOval(c(Color(0xFF8CC49A)), Offset(sx + 12 * unit + i * 7 * unit, sy - 32 * unit + abs(i) * 6 * unit), Size(8 * unit, 18 * unit))
    drawCircle(c(Color(0xFFC79265)), radius = 9 * unit, center = Offset(sx + 50 * unit, sy - 9 * unit))
    drawCircle(c(Color(0xFFC79265)), radius = 7 * unit, center = Offset(sx + 50 * unit, sy - 22 * unit))
    drawCircle(c(Color(0xFFC79265)), radius = 3 * unit, center = Offset(sx + 45 * unit, sy - 28 * unit))
    drawCircle(c(Color(0xFFC79265)), radius = 3 * unit, center = Offset(sx + 55 * unit, sy - 28 * unit))
    drawRoundRect(c(Color(0xFF9DB9DF)), Offset(sx + 66 * unit, sy - 20 * unit), Size(7 * unit, 20 * unit), CornerRadius(1.5f * unit))
    drawRoundRect(c(Color(0xFFF5D27A)), Offset(sx + 74 * unit, sy - 17 * unit), Size(6 * unit, 17 * unit), CornerRadius(1.5f * unit))
    if (v.night) {
        // A warm night-light glow.
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFD99A).copy(alpha = 0.35f), Color.Transparent), Offset(w - 40 * unit, h * 0.7f), 90 * unit),
            radius = 90 * unit, center = Offset(w - 40 * unit, h * 0.7f),
        )
        drawCircle(Color(0xFFFFE3A8), radius = 6 * unit, center = Offset(w - 40 * unit, h * 0.7f))
    }
}

private fun DrawScope.drawMobile(s: Float, cx: Float, cy: Float, a: Anim, night: Boolean) {
    val dim = if (night) 0.6f else 1f
    fun c(color: Color) = Color(color.red * dim, color.green * dim, color.blue * dim, color.alpha)
    val barY = cy - 190 * s
    drawLine(c(Color(0xFFB98552)), Offset(cx, 0f), Offset(cx, barY), strokeWidth = 2 * s)
    drawLine(c(Color(0xFFB98552)), Offset(cx - 60 * s, barY), Offset(cx + 60 * s, barY), strokeWidth = 3 * s, cap = StrokeCap.Round)
    val colors = listOf(Color(0xFFF7D774), Color(0xFFF3A5B6), Color(0xFF8DB8DE))
    for (i in 0 until 3) {
        val theta = 2 * PI * (a.slow * 2f + i / 3f)
        val x = cx + 58 * s * cos(theta).toFloat()
        val depth = 0.8f + 0.2f * sin(theta).toFloat()
        val y = barY + 22 * s + 4 * s * sin(theta * 2).toFloat()
        drawLine(c(Color(0xFFB98552)).copy(alpha = 0.7f), Offset(x, barY), Offset(x, y - 7 * s), strokeWidth = 1.2f * s)
        when (i) {
            0 -> drawStar(Offset(x, y), 9 * s * depth, c(colors[0]))
            1 -> drawCircle(c(colors[1]), radius = 7 * s * depth, center = Offset(x, y))
            else -> {
                drawCircle(c(colors[2]), radius = 6 * s * depth, center = Offset(x - 4 * s, y))
                drawCircle(c(colors[2]), radius = 7 * s * depth, center = Offset(x + 4 * s, y - 1 * s))
            }
        }
    }
}

private fun DrawScope.drawStar(center: Offset, r: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.45f
        val ang = -PI / 2 + i * PI / 5
        val px = center.x + rr * cos(ang).toFloat()
        val py = center.y + rr * sin(ang).toFloat()
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawScene(v: BabyVisual, s: Float, cx: Float, cy: Float, night: Boolean) {
    val dim = if (night) 0.55f else 1f
    fun c(color: Color) = Color(color.red * dim, color.green * dim, color.blue * dim, color.alpha)
    when (v.place) {
        Place.BASSINET -> {
            val rim = Offset(cx - 150 * s, cy - 153 * s)
            val mattress = Offset(cx - 132 * s, cy - 138 * s)
            drawOval(
                Brush.verticalGradient(listOf(c(Color(0xFFF0E1CC)), c(Color(0xFFC8AD91))), rim.y, cy + 155 * s),
                topLeft = rim, size = Size(300 * s, 308 * s),
            )
            drawOval(
                Brush.verticalGradient(listOf(c(Color(0xFFE9F3FA)), c(Color(0xFFB9D0E2))), mattress.y, cy + 145 * s),
                topLeft = mattress, size = Size(264 * s, 283 * s),
            )
            drawOval(
                c(Color(0xFFF5F9FC)).copy(alpha = 0.55f),
                topLeft = Offset(mattress.x + 5 * s, mattress.y + 5 * s),
                size = Size(254 * s, 273 * s), style = Stroke(width = 1.5f * s),
            )
            drawHazards(v.hazards, s, cx, cy, ::c)
        }
        Place.CHANGING_TABLE -> {
            drawRoundRect(c(Color(0xFFD9C399)), topLeft = Offset(cx - 150 * s, cy - 120 * s), size = Size(300 * s, 277 * s), cornerRadius = CornerRadius(40 * s))
            drawRoundRect(c(Color(0xFFF1E4C6)), topLeft = Offset(cx - 142 * s, cy - 113 * s), size = Size(284 * s, 259 * s), cornerRadius = CornerRadius(34 * s))
            for (i in 0 until 5) {
                val y = cy + (-85 + i * 48) * s
                drawLine(c(Color(0xFFE1CDA5)), Offset(cx - 123 * s, y), Offset(cx + 123 * s, y), strokeWidth = 2 * s, cap = StrokeCap.Round)
            }
        }
        Place.PLAY_MAT -> {
            val colors = listOf(Color(0xFFF6C9C0), Color(0xFFC9E3C6), Color(0xFFC7DAF0), Color(0xFFF4E3B0))
            drawRoundRect(c(Color(0xFFAE9B88)), topLeft = Offset(cx - 150 * s, cy - 115 * s), size = Size(300 * s, 244 * s), cornerRadius = CornerRadius(18 * s))
            for (i in 0 until 4) {
                val x = cx - 146 * s + (i % 2) * 147 * s
                val y = cy - 111 * s + (i / 2) * 119 * s
                drawRoundRect(c(colors[i]), topLeft = Offset(x, y), size = Size(145 * s, 117 * s), cornerRadius = CornerRadius(13 * s))
            }
        }
        Place.ARMS -> {
            drawOval(c(Color(0xFFEFE2D6)).copy(alpha = if (v.room) 0.75f else 1f), topLeft = Offset(cx - 160 * s, cy - 120 * s), size = Size(320 * s, 250 * s))
        }
    }
}

private fun DrawScope.drawHazards(hazards: List<String>, s: Float, cx: Float, cy: Float, c: (Color) -> Color) {
    for (h in hazards) {
        when (h) {
            "blanket" -> {
                val p = Path().apply {
                    moveTo(cx - 120 * s, cy + 40 * s)
                    cubicTo(cx - 60 * s, cy + 10 * s, cx + 20 * s, cy + 70 * s, cx + 120 * s, cy + 30 * s)
                    lineTo(cx + 110 * s, cy + 95 * s)
                    cubicTo(cx + 20 * s, cy + 120 * s, cx - 60 * s, cy + 80 * s, cx - 115 * s, cy + 100 * s)
                    close()
                }
                drawPath(p, c(Color(0xFFE7A9B8)).copy(alpha = 0.9f))
            }
            "pillow" -> drawRoundRect(c(Color(0xFFFFFFFF)), topLeft = Offset(cx - 70 * s, cy - 100 * s), size = Size(140 * s, 55 * s), cornerRadius = CornerRadius(25 * s))
            "toy" -> {
                drawCircle(c(Color(0xFFB98552)), radius = 22 * s, center = Offset(cx - 95 * s, cy - 30 * s))
                drawCircle(c(Color(0xFFB98552)), radius = 9 * s, center = Offset(cx - 110 * s, cy - 50 * s))
                drawCircle(c(Color(0xFFB98552)), radius = 9 * s, center = Offset(cx - 80 * s, cy - 50 * s))
            }
            "bumper" -> drawOval(c(Color(0xFFB7D3A8)), topLeft = Offset(cx - 124 * s, cy - 130 * s), size = Size(248 * s, 267 * s), style = Stroke(width = 14 * s))
            "nursing_pillow" -> drawArc(c(Color(0xFFF2D27E)), startAngle = 200f, sweepAngle = 140f, useCenter = false, topLeft = Offset(cx + 40 * s, cy - 40 * s), size = Size(90 * s, 110 * s), style = Stroke(width = 22 * s, cap = StrokeCap.Round))
        }
    }
}

// ------------------------------------------------------------------ caregiver details

private val ParentSkin = Color(0xFFE6BE9C)

private fun DrawScope.drawNursingParent(s: Float, cx: Float, cy: Float, night: Boolean) {
    val dim = if (night) 0.7f else 1f
    fun c(color: Color) = Color(color.red * dim, color.green * dim, color.blue * dim, color.alpha)
    drawOval(c(Color(0xFF9C8BB4)), topLeft = Offset(cx - 230 * s, cy - 170 * s), size = Size(170 * s, 330 * s))
    drawOval(c(ParentSkin), topLeft = Offset(cx - 96 * s, cy - 82 * s), size = Size(34 * s, 40 * s))
}

private fun DrawScope.drawPlayFace(s: Float, cx: Float, cy: Float, a: Anim, night: Boolean) {
    val dim = if (night) 0.7f else 1f
    fun c(color: Color) = Color(color.red * dim, color.green * dim, color.blue * dim, color.alpha)
    val fx = cx - 140 * s
    val fy = cy - 150 * s
    drawCircle(c(ParentSkin), radius = 48 * s, center = Offset(fx, fy))
    drawArc(c(Color(0xFF5B4032)), 180f, 180f, false, Offset(fx - 50 * s, fy - 54 * s), Size(100 * s, 70 * s), style = Stroke(width = 14 * s, cap = StrokeCap.Round))
    drawCircle(c(Color(0xFF2E2420)), radius = 4.5f * s, center = Offset(fx + 6 * s, fy - 2 * s))
    drawCircle(c(Color(0xFF2E2420)), radius = 4.5f * s, center = Offset(fx + 30 * s, fy + 4 * s))
    val talk = if (a.motion) abs(sw(a.pat)) else 0.5f
    drawOval(c(Color(0xFF8A3B32)), topLeft = Offset(fx + 12 * s, fy + 20 * s), size = Size(16 * s, (5 + 8 * talk) * s))
    // Serve and return: little hearts drift toward the baby.
    for (i in 0 until 2) {
        val k = if (a.motion) (a.drift + i * 0.5f) % 1f else 0.5f
        drawHeart(Offset(fx + 50 * s + k * 70 * s, fy + 30 * s + k * 40 * s), (7 + 4 * k) * s, Color(0xFFF3A5B6).copy(alpha = 1f - k))
    }
}

private fun DrawScope.drawHeart(center: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y + r * 0.9f)
        cubicTo(center.x - r * 1.6f, center.y - r * 0.2f, center.x - r * 0.7f, center.y - r * 1.3f, center.x, center.y - r * 0.45f)
        cubicTo(center.x + r * 0.7f, center.y - r * 1.3f, center.x + r * 1.6f, center.y - r * 0.2f, center.x, center.y + r * 0.9f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawSparkle(center: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x, center.y, center.x + r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + r)
        quadraticTo(center.x, center.y, center.x - r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - r)
        close()
    }
    drawPath(path, color)
}

// ------------------------------------------------------------------ the baby

private fun DrawScope.drawBaby(
    v: BabyVisual, p: Pose, s: Float, cx: Float, cy: Float, a: Anim, happy: Boolean,
    touch: BodyPart?, tb: Float, touchSide: Float,
) {
    val skin = SkinTones.getOrElse(v.skinTone) { SkinTones[1] }
    val outline = Color(0xFF5B3A2E)
    val onesie = Color(0xFFBFE3D0)
    val dim = if (v.night) 0.7f else 1f
    fun c(color: Color) = Color(color.red * dim, color.green * dim, color.blue * dim, color.alpha)
    val crying = p.mouth == Mouth.CRY

    if (v.heldBy != null && v.place == Place.ARMS) {
        // Supporting arms (behind the baby).
        val sleeve = if (v.heldBy == Caregiver.PLAYER) Color(0xFF7D93AE) else Color(0xFF9C8BB4)
        drawRoundRect(c(sleeve), topLeft = Offset(cx - 170 * s, cy + 40 * s), size = Size(150 * s, 70 * s), cornerRadius = CornerRadius(35 * s))
        drawRoundRect(c(sleeve), topLeft = Offset(cx + 20 * s, cy + 40 * s), size = Size(150 * s, 70 * s), cornerRadius = CornerRadius(35 * s))
    }

    // How lively the limbs are.
    val liveliness = when {
        p.asleep -> 0f
        crying -> 10f
        p.mouth == Mouth.FUSS -> 6f
        v.activity == BabyActivity.PLAY -> 6f
        else -> 3f
    } * (if (v.heldBy != null) 0.6f else 1f) * (if (a.motion) 1f else 0f)
    val phase = if (crying) a.fast else a.wig
    var armL = liveliness * sw(phase)
    var armR = liveliness * sw(phase, 2.1f)
    val legAmp = liveliness * (if (v.place == Place.CHANGING_TABLE) 1.6f else 1f)
    var legL = legAmp * sw(phase * 2f)
    var legR = legAmp * sw(phase * 2f, PI.toFloat())
    if (p.asleep && p.activeSleep && a.motion && a.drift in 0.62f..0.67f) {
        legL -= 6f
        armR -= 4f
    }
    if (touch == BodyPart.FEET) {
        legL -= 16f * tb
        legR -= 16f * tb
    }
    if (touch == BodyPart.TUMMY && !p.asleep) {
        armL -= 5f * tb
        armR -= 5f * tb
    }

    if (p.onTummy) {
        val lift = if (v.activity == BabyActivity.TUMMY && a.motion) 7f * (0.5f + 0.5f * sw(a.wig)) else 0f
        drawOval(c(onesie), topLeft = Offset(cx - 80 * s, cy + 5 * s), size = Size(160 * s, 85 * s))
        drawOval(c(skin), topLeft = Offset(cx - 70 * s, cy + 55 * s + armL * 0.3f * s), size = Size(40 * s, 30 * s))
        drawOval(c(skin), topLeft = Offset(cx + 30 * s, cy + 55 * s + armR * 0.3f * s), size = Size(40 * s, 30 * s))
        drawFace(p, s, cx, cy - 25 * s - lift * s, 48f, skin, outline, a, happy, v.activity, ::c)
        if (v.activity == BabyActivity.TUMMY) {
            // A bright rattle to look at.
            drawCircle(c(Color(0xFFF7D774)), radius = 12 * s, center = Offset(cx + 95 * s, cy - 60 * s))
            drawLine(c(Color(0xFFF3A5B6)), Offset(cx + 100 * s, cy - 50 * s), Offset(cx + 115 * s, cy - 25 * s), strokeWidth = 6 * s, cap = StrokeCap.Round)
        }
        return
    }

    // Legs.
    if (p.legsUp) {
        val pull = if (a.motion && !p.asleep) 3f * sw(a.fast) else 0f
        drawOval(c(onesie), topLeft = Offset(cx - 52 * s, cy + (55 + pull) * s), size = Size(40 * s, 46 * s))
        drawOval(c(onesie), topLeft = Offset(cx + 12 * s, cy + (55 - pull) * s), size = Size(40 * s, 46 * s))
        drawCircle(c(skin), radius = 11 * s, center = Offset(cx - 32 * s, cy + (58 + pull) * s))
        drawCircle(c(skin), radius = 11 * s, center = Offset(cx + 32 * s, cy + (58 - pull) * s))
    } else {
        drawOval(c(onesie), topLeft = Offset(cx - 48 * s, cy + 70 * s), size = Size(36 * s, (70 + legL) * s))
        drawOval(c(onesie), topLeft = Offset(cx + 12 * s, cy + 70 * s), size = Size(36 * s, (70 + legR) * s))
        drawCircle(c(skin), radius = 11 * s, center = Offset(cx - 30 * s, cy + (140 + legL) * s))
        drawCircle(c(skin), radius = 11 * s, center = Offset(cx + 30 * s, cy + (140 + legR) * s))
    }
    // Body.
    drawOval(c(onesie), topLeft = Offset(cx - 58 * s, cy - 5 * s), size = Size(116 * s, 105 * s))
    drawCircle(c(Color(0xFF9CCDB4)), radius = 4 * s, center = Offset(cx, cy + 30 * s))
    drawCircle(c(Color(0xFF9CCDB4)), radius = 4 * s, center = Offset(cx, cy + 50 * s))
    // Diaper, when on the changing table.
    if (v.place == Place.CHANGING_TABLE || v.activity == BabyActivity.DIAPER_CLEAN || v.activity == BabyActivity.DIAPER_FRESH) {
        drawDiaper(s, cx, cy, a, v.activity, ::c)
    }
    // Arms.
    var leftHand = Offset(cx - 64 * s - armL * 0.3f * s, cy + (68 + armL) * s)
    var rightHand = Offset(cx + 64 * s + armR * 0.3f * s, cy + (68 + armR) * s)
    if (p.handsToMouth) {
        drawOval(c(onesie), topLeft = Offset(cx - 62 * s, cy - 8 * s), size = Size(34 * s, 52 * s))
        drawOval(c(onesie), topLeft = Offset(cx + 28 * s, cy - 8 * s), size = Size(34 * s, 52 * s))
    } else {
        if (v.activity == BabyActivity.PLAY || (crying && liveliness > 8f)) {
            // Arms up and waving.
            leftHand = Offset(cx - 78 * s, cy + (8 + armL) * s)
            rightHand = Offset(cx + 78 * s, cy + (8 + armR) * s)
        }
        drawArm(Offset(cx - 44 * s, cy + 14 * s), leftHand, s, c(onesie))
        drawArm(Offset(cx + 44 * s, cy + 14 * s), rightHand, s, c(onesie))
        drawCircle(c(skin), radius = 12 * s, center = leftHand)
        drawCircle(c(skin), radius = 12 * s, center = rightHand)
        if (crying) {
            // Clenched fists.
            for (hand in listOf(leftHand, rightHand)) {
                drawArc(c(outline).copy(alpha = 0.4f), 200f, 140f, false, Offset(hand.x - 7 * s, hand.y - 6 * s), Size(14 * s, 8 * s), style = Stroke(1.4f * s))
            }
        }
    }
    drawFace(p, s, cx, cy - 45 * s, 54f, skin, outline, a, happy, v.activity, ::c)
    if (p.handsToMouth) {
        // Slightly darker than the face, with an outline, so the fists read clearly against it.
        val fistShade = Color(skin.red * 0.93f, skin.green * 0.86f, skin.blue * 0.82f)
        for ((i, pair) in listOf(-20f to -18f, 18f to -16f).withIndex()) {
            val (dx, dy) = pair
            val wobble = if (a.motion) 2f * sw(a.fast, i * 1.5f) else 0f
            val center = Offset(cx + dx * s + p.headTurn * 14 * s, cy + (dy + wobble) * s)
            drawCircle(c(fistShade), radius = 12 * s, center = center)
            drawCircle(c(outline).copy(alpha = 0.45f), radius = 12 * s, center = center, style = Stroke(width = 1.8f * s))
            drawArc(
                c(outline).copy(alpha = 0.35f), startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(center.x - 7 * s, center.y - 6 * s), size = Size(14 * s, 8 * s), style = Stroke(width = 1.4f * s),
            )
        }
    } else if (touch == BodyPart.HAND) {
        // Palmar grasp reflex: the baby's fingers curl around your finger.
        val hand = if (touchSide < 0) leftHand else rightHand
        val reach = 40f * (1f - tb.coerceAtMost(1f) * 0.6f)
        val dir = if (touchSide < 0) -1f else 1f
        drawRoundRect(c(ParentSkin), topLeft = Offset(if (dir > 0) hand.x - 4 * s else hand.x - (reach + 30) * s, hand.y - 6 * s), size = Size((reach + 34) * s, 12 * s), cornerRadius = CornerRadius(6 * s))
        drawCircle(c(skin), radius = 11 * s, center = hand)
        drawArc(c(outline).copy(alpha = 0.5f), 200f, 140f, false, Offset(hand.x - 8 * s, hand.y - 8 * s), Size(16 * s, 10 * s), style = Stroke(1.6f * s))
    }
    when (v.activity) {
        BabyActivity.BOTTLE_FEED -> drawBottle(p, s, cx, cy, a, 0f, ::c)
        BabyActivity.BOTTLE_OFFER -> drawBottle(p, s, cx, cy, a, 16f + (if (a.motion) 3f * sw(a.wig) else 0f), ::c)
        BabyActivity.BURP -> {
            val patY = if (a.motion) abs(sw(a.pat)) * 12f else 6f
            drawOval(c(ParentSkin), topLeft = Offset(cx + 46 * s, cy + (2 - patY) * s), size = Size(46 * s, 30 * s))
            drawArc(c(outline).copy(alpha = 0.35f), -70f, 60f, false, Offset(cx + 84 * s, cy - (8 + patY) * s), Size(20 * s, 20 * s), style = Stroke(2 * s, cap = StrokeCap.Round))
            for (i in 0 until 3) {
                val k = if (a.motion) (a.drift + i / 3f) % 1f else 0.5f
                drawCircle(Color(0xFF94CFF2).copy(alpha = 0.6f * (1f - k)), radius = (3 + i) * s, center = Offset(cx - 20 * s + i * 14 * s, cy - 95 * s - k * 40 * s), style = Stroke(1.5f * s))
            }
        }
        else -> Unit
    }
}

private fun DrawScope.drawArm(shoulder: Offset, hand: Offset, s: Float, color: Color) {
    drawLine(color, shoulder, hand, strokeWidth = 30 * s, cap = StrokeCap.Round)
}

private fun DrawScope.drawDiaper(s: Float, cx: Float, cy: Float, a: Anim, activity: BabyActivity, c: (Color) -> Color) {
    val open = activity == BabyActivity.DIAPER_CLEAN
    if (open) {
        // Open diaper: the front folded down under the baby.
        drawRoundRect(c(Color(0xFFF7F3EA)), topLeft = Offset(cx - 52 * s, cy + 60 * s), size = Size(104 * s, 50 * s), cornerRadius = CornerRadius(16 * s))
    }
    val top = if (open) cy + 90 * s else cy + 52 * s
    val path = Path().apply {
        moveTo(cx - 54 * s, top)
        lineTo(cx + 54 * s, top)
        lineTo(cx + 40 * s, top + 40 * s)
        quadraticTo(cx, top + 56 * s, cx - 40 * s, top + 40 * s)
        close()
    }
    drawPath(path, c(Color.White))
    drawPath(path, c(Color(0xFFC9C2BC)), style = Stroke(1.6f * s))
    if (!open) {
        drawRoundRect(c(Color(0xFF8DB8DE)), topLeft = Offset(cx - 58 * s, top - 2 * s), size = Size(18 * s, 12 * s), cornerRadius = CornerRadius(3 * s))
        drawRoundRect(c(Color(0xFF8DB8DE)), topLeft = Offset(cx + 40 * s, top - 2 * s), size = Size(18 * s, 12 * s), cornerRadius = CornerRadius(3 * s))
    }
    if (open) {
        val k = if (a.motion) sw(a.pat * 0.5f) else 0f
        rotate(-20f + 15f * k, Offset(cx + 30 * k * s, cy + 75 * s)) {
            drawRoundRect(c(Color.White), topLeft = Offset(cx - 20 * s + 30 * k * s, cy + 62 * s), size = Size(40 * s, 28 * s), cornerRadius = CornerRadius(5 * s))
            for (i in 0 until 3) drawCircle(c(Color(0xFF94CFF2)), radius = 2.5f * s, center = Offset(cx - 10 * s + i * 10 * s + 30 * k * s, cy + 76 * s))
        }
    }
    if (activity == BabyActivity.DIAPER_FRESH) {
        for (i in 0 until 3) {
            val k = if (a.motion) (a.drift + i / 3f) % 1f else 0.5f
            drawSparkle(Offset(cx - 60 * s + i * 60 * s, cy + 70 * s - k * 20 * s), (6 + 6 * bump(k)) * s, Color(0xFFF7D774))
        }
    }
}

private fun DrawScope.drawBottle(p: Pose, s: Float, cx: Float, cy: Float, a: Anim, away: Float, c: (Color) -> Color) {
    val mouth = Offset(cx + p.headTurn * 14 * s + away * s, cy - 25 * s)
    rotate(10f, mouth) {
        drawOval(c(Color(0xFFE8C8A0)), topLeft = mouth + Offset(-2 * s, -7 * s), size = Size(21 * s, 14 * s))
        drawRoundRect(
            Brush.verticalGradient(listOf(c(Color(0xFFFFFDF7)), c(Color(0xFFE8D9AA))), mouth.y - 15 * s, mouth.y + 15 * s),
            topLeft = mouth + Offset(22 * s, -15 * s), size = Size(76 * s, 30 * s), cornerRadius = CornerRadius(8 * s),
        )
        drawRoundRect(c(Color(0xFF8FB7D9)), topLeft = mouth + Offset(14 * s, -17 * s), size = Size(14 * s, 34 * s), cornerRadius = CornerRadius(4 * s))
        for (i in 0 until 3) {
            val x = mouth.x + (48 + i * 11) * s
            drawLine(c(Color(0xFF776E59)), Offset(x, mouth.y + 3 * s), Offset(x, mouth.y + 9 * s), strokeWidth = 1.5f * s, cap = StrokeCap.Round)
        }
        if (away == 0f && a.motion) {
            // Air bubbles rising in the milk with each swallow.
            for (i in 0 until 3) {
                val k = (a.drift * 2f + i / 3f) % 1f
                drawCircle(Color.White.copy(alpha = 0.9f * (1f - k)), radius = 2.4f * s, center = Offset(mouth.x + 40 * s + k * 50 * s, mouth.y + 6 * s - (i % 2) * 5 * s))
            }
        }
    }
}

private fun DrawScope.drawFace(
    p: Pose, s: Float, hx: Float, hy: Float, radius: Float, skin: Color, outline: Color, a: Anim,
    happy: Boolean, activity: BabyActivity, c: (Color) -> Color,
) {
    val r = radius * s
    val turn = p.headTurn * 14 * s
    // Head, ears and hair.
    drawCircle(c(skin), radius = 9 * s, center = Offset(hx - r + 4 * s, hy + 4 * s))
    drawCircle(c(skin), radius = 9 * s, center = Offset(hx + r - 4 * s, hy + 4 * s))
    drawCircle(c(skin), radius = r, center = Offset(hx, hy))
    if (p.redFace > 0f) drawCircle(Color(0xFFE5585A).copy(alpha = 0.22f * p.redFace), radius = r, center = Offset(hx, hy))
    drawArc(c(Color(0xFF6B4A3A)), startAngle = 200f, sweepAngle = 140f, useCenter = false, topLeft = Offset(hx - r * 0.8f, hy - r * 1.02f), size = Size(r * 1.6f, r * 0.9f), style = Stroke(width = 3 * s, cap = StrokeCap.Round))
    drawArc(c(Color(0xFF6B4A3A)), startAngle = 240f, sweepAngle = 60f, useCenter = false, topLeft = Offset(hx - r * 0.4f, hy - r * 1.05f), size = Size(r * 0.8f, r * 0.6f), style = Stroke(width = 3 * s, cap = StrokeCap.Round))

    val fx = hx + turn
    val eyeY = hy - 6 * s
    val crying = p.mouth == Mouth.CRY
    val cryPhase = if (a.motion) a.cry else 0.5f
    // Eyes.
    if (happy) {
        for (dx in listOf(-17f, 17f)) {
            drawArc(c(outline), startAngle = 200f, sweepAngle = 140f, useCenter = false, topLeft = Offset(fx + dx * s - 8 * s, eyeY - 4 * s), size = Size(16 * s, 12 * s), style = Stroke(width = 2.8f * s, cap = StrokeCap.Round))
        }
    } else if (p.eyesOpen && a.blink > 0.25f) {
        val open = a.blink
        for (dx in listOf(-17f, 17f)) {
            drawOval(c(Color(0xFF2E2420)), topLeft = Offset(fx + dx * s - 5 * s, eyeY - 7 * s * open), size = Size(10 * s, 13 * s * open))
            if (open > 0.6f) drawCircle(Color.White.copy(alpha = 0.9f), radius = 2.2f * s, center = Offset(fx + dx * s + 2 * s, eyeY - 3 * s))
        }
    } else if (crying) {
        for (dx in listOf(-17f, 17f)) {
            val dir = if (dx < 0) 1f else -1f
            drawLine(c(outline), Offset(fx + dx * s - 8 * s, eyeY - 4 * s * dir), Offset(fx + dx * s + 8 * s, eyeY + 2 * s * dir), strokeWidth = 3 * s, cap = StrokeCap.Round)
        }
    } else {
        for (dx in listOf(-17f, 17f)) {
            drawArc(c(outline), startAngle = 20f, sweepAngle = 140f, useCenter = false, topLeft = Offset(fx + dx * s - 9 * s, eyeY - 7 * s), size = Size(18 * s, 12 * s), style = Stroke(width = 2.6f * s, cap = StrokeCap.Round))
        }
    }
    // Brows when upset.
    if (p.mouth == Mouth.CRY || p.mouth == Mouth.FUSS) {
        drawLine(c(outline), Offset(fx - 26 * s, eyeY - 16 * s), Offset(fx - 9 * s, eyeY - 21 * s), strokeWidth = 2.4f * s, cap = StrokeCap.Round)
        drawLine(c(outline), Offset(fx + 26 * s, eyeY - 16 * s), Offset(fx + 9 * s, eyeY - 21 * s), strokeWidth = 2.4f * s, cap = StrokeCap.Round)
    }
    // Cheeks.
    val blush = 0.3f + 0.4f * p.redFace + (if (happy) 0.2f else 0f)
    drawCircle(Color(0xFFE88E7E).copy(alpha = blush), radius = 8 * s, center = Offset(fx - 28 * s, hy + 12 * s))
    drawCircle(Color(0xFFE88E7E).copy(alpha = blush), radius = 8 * s, center = Offset(fx + 28 * s, hy + 12 * s))
    // Mouth.
    val my = hy + 20 * s
    val cooing = activity == BabyActivity.PLAY && !crying && a.motion && a.drift in 0.35f..0.6f
    when {
        cooing -> drawOval(c(Color(0xFF8A3B32)), topLeft = Offset(fx - 5 * s, my - 5 * s), size = Size(10 * s, 11 * s))
        else -> when (p.mouth) {
            Mouth.SMILE -> drawArc(c(outline), startAngle = 20f, sweepAngle = 140f, useCenter = false, topLeft = Offset(fx - (if (happy) 14 else 11) * s, my - 8 * s), size = Size((if (happy) 28 else 22) * s, 14 * s), style = Stroke(width = 2.6f * s, cap = StrokeCap.Round))
            Mouth.NEUTRAL -> drawLine(c(outline), Offset(fx - 6 * s, my), Offset(fx + 6 * s, my), strokeWidth = 2.4f * s, cap = StrokeCap.Round)
            Mouth.ROOTING -> drawCircle(c(Color(0xFF8A3B32)), radius = (6 + (if (a.motion) 1f * sw(a.pat) else 0f)) * s, center = Offset(fx, my))
            Mouth.SUCKING -> {
                val suck = if (a.motion) 1.3f * sw(a.pat) else 0f
                drawCircle(c(Color(0xFF8A3B32)), radius = (4.5f + suck) * s, center = Offset(fx, my))
                // Cheeks move with each suck.
                drawCircle(c(skin).copy(alpha = 0.6f), radius = (9 - suck) * s, center = Offset(fx - 20 * s, my - 2 * s))
                drawCircle(c(skin).copy(alpha = 0.6f), radius = (9 - suck) * s, center = Offset(fx + 20 * s, my - 2 * s))
            }
            Mouth.FUSS -> drawArc(c(outline), startAngle = 200f, sweepAngle = 140f, useCenter = false, topLeft = Offset(fx - 10 * s, my - 2 * s), size = Size(20 * s, 12 * s), style = Stroke(width = 2.6f * s, cap = StrokeCap.Round))
            Mouth.YAWN -> {
                val y = if (a.motion) 0.6f + 0.4f * abs(sw(a.wig * 0.5f)) else 1f
                drawOval(c(Color(0xFF8A3B32)), topLeft = Offset(fx - 8 * s, my - 8 * s * y), size = Size(16 * s, 22 * s * y))
            }
            Mouth.CRY -> {
                val h = (16f + 12f * cryPhase) * s
                drawOval(c(Color(0xFF8A3B32)), topLeft = Offset(fx - 15 * s, my - h / 2f), size = Size(30 * s, h))
                drawOval(c(Color(0xFFE57E7E)), topLeft = Offset(fx - 9 * s, my + h / 2f - 9 * s), size = Size(18 * s, 8 * s))
            }
        }
    }
    if (p.tears) {
        val fall = if (a.motion) a.drift * 10f else 0f
        drawCircle(Color(0xFF8EC3EB).copy(alpha = 0.85f), radius = 3.5f * s, center = Offset(fx - 22 * s, eyeY + (10 + fall) * s))
        drawCircle(Color(0xFF8EC3EB).copy(alpha = 0.85f), radius = 3.5f * s, center = Offset(fx + 22 * s, eyeY + (12 + fall) * s))
    }
    if (p.activeSleep && cryPhase > 0.7f) {
        drawLine(c(outline).copy(alpha = 0.5f), Offset(hx + r + 6 * s, hy + 18 * s), Offset(hx + r + 14 * s, hy + 14 * s), strokeWidth = 2 * s)
    }
}

// ------------------------------------------------------------------ emotes

private fun DrawScope.drawReaction(kind: Reaction, p: Float, s: Float, cx: Float, cy: Float, headY: Float, tm: TextMeasurer) {
    val fade = if (p > 0.8f) (1f - p) / 0.2f else 1f
    val pop = min(1f, p * 6f)
    fun stagger(i: Int) = (p * 1.4f - i * 0.18f).coerceIn(0f, 1f)
    when (kind) {
        Reaction.HEARTS -> for (i in 0 until 3) {
            val q = stagger(i)
            if (q <= 0f) continue
            val x = cx + (-46 + 46 * i) * s + 8 * s * sin(q * 6f + i)
            drawHeart(Offset(x, headY - 50 * s - 90 * s * q), (10 + 6 * q) * s, Color(0xFFF07D98).copy(alpha = 1f - q))
        }
        Reaction.NOTES -> for (i in 0 until 3) {
            val q = stagger(i)
            if (q <= 0f) continue
            val x = cx + (40 + 26 * i) * s + 10 * s * sin(q * 7f + i)
            val y = headY - 30 * s - 80 * s * q
            val col = Color(0xFF6E8FB0).copy(alpha = 1f - q)
            drawOval(col, topLeft = Offset(x - 7 * s, y - 4 * s), size = Size(12 * s, 9 * s))
            drawLine(col, Offset(x + 4 * s, y), Offset(x + 4 * s, y - 22 * s), strokeWidth = 2.4f * s)
            drawLine(col, Offset(x + 4 * s, y - 22 * s), Offset(x + 12 * s, y - 16 * s), strokeWidth = 2.4f * s, cap = StrokeCap.Round)
        }
        Reaction.SPARKLE -> for (i in 0 until 6) {
            val q = stagger(i % 3)
            val ang = 2 * PI * i / 6 + 0.4
            val r = (85 + 20 * q) * s
            drawSparkle(Offset(cx + r * cos(ang).toFloat(), cy + 20 * s + r * sin(ang).toFloat() * 0.9f), (8 + 8 * bump(q)) * s, Color(0xFFF7C948).copy(alpha = fade))
        }
        Reaction.STARS -> for (i in 0 until 7) {
            val q = (p * 2f + i * 0.13f) % 1f
            drawSparkle(Offset(cx + (-140 + i * 46) * s, headY - 70 * s + ((i * 37) % 50) * s), (5 + 6 * bump(q)) * s, Color(0xFFFFF0B3).copy(alpha = fade))
        }
        Reaction.ZZZ -> for (i in 0 until 3) {
            val q = stagger(i)
            if (q <= 0f) continue
            val z = (10 + 6 * i) * s
            val x = cx + (50 + 20 * i) * s
            val y = headY - 30 * s - 70 * s * q
            val col = Color(0xFF7C8BA6).copy(alpha = 1f - q)
            drawLine(col, Offset(x, y), Offset(x + z, y), strokeWidth = 2.6f * s, cap = StrokeCap.Round)
            drawLine(col, Offset(x + z, y), Offset(x, y + z), strokeWidth = 2.6f * s, cap = StrokeCap.Round)
            drawLine(col, Offset(x, y + z), Offset(x + z, y + z), strokeWidth = 2.6f * s, cap = StrokeCap.Round)
        }
        Reaction.BURP -> {
            speechBubble(Offset(cx + 70 * s, headY - 60 * s), 70 * s * pop, 38 * s * pop, fade)
            if (pop > 0.9f) drawText(tm, "burp!", topLeft = Offset(cx + 46 * s, headY - 72 * s), style = TextStyle(color = Color(0xFF51608A).copy(alpha = fade), fontSize = (14 * s / 0.8f).coerceIn(10f, 18f).sp, fontWeight = FontWeight.Bold))
            for (i in 0 until 3) {
                val q = stagger(i)
                drawCircle(Color(0xFF94CFF2).copy(alpha = 0.8f * (1f - q)), radius = (4 + 2 * i) * s, center = Offset(cx + (10 + 10 * i) * s, headY - 10 * s - 60 * s * q), style = Stroke(2 * s))
            }
        }
        Reaction.DROP -> {
            speechBubble(Offset(cx + 70 * s, headY - 60 * s), 48 * s * pop, 42 * s * pop, fade)
            val d = Offset(cx + 70 * s, headY - 62 * s)
            val path = Path().apply {
                moveTo(d.x, d.y - 13 * s * pop)
                quadraticTo(d.x + 11 * s * pop, d.y + 2 * s, d.x, d.y + 10 * s * pop)
                quadraticTo(d.x - 11 * s * pop, d.y + 2 * s, d.x, d.y - 13 * s * pop)
                close()
            }
            drawPath(path, Color(0xFF6FB6E8).copy(alpha = fade))
        }
        Reaction.STINK -> for (i in 0 until 3) {
            val q = stagger(i)
            if (q <= 0f) continue
            val x = cx + (-40 + 40 * i) * s
            val y = cy + 70 * s - 80 * s * q
            val path = Path().apply {
                moveTo(x, y)
                quadraticTo(x - 10 * s, y - 10 * s, x, y - 20 * s)
                quadraticTo(x + 10 * s, y - 30 * s, x, y - 40 * s)
            }
            drawPath(path, Color(0xFF8DBF6A).copy(alpha = 0.9f * (1f - q)), style = Stroke(4 * s, cap = StrokeCap.Round))
        }
        Reaction.EXCLAIM -> {
            speechBubble(Offset(cx + 70 * s, headY - 60 * s), 40 * s * pop, 44 * s * pop, fade)
            if (pop > 0.9f) {
                drawLine(Color(0xFFE5736F).copy(alpha = fade), Offset(cx + 70 * s, headY - 76 * s), Offset(cx + 70 * s, headY - 58 * s), strokeWidth = 5 * s, cap = StrokeCap.Round)
                drawCircle(Color(0xFFE5736F).copy(alpha = fade), radius = 3 * s, center = Offset(cx + 70 * s, headY - 48 * s))
            }
        }
        Reaction.MAGNIFY -> {
            val x = cx + (-60 + 120 * p) * s
            val y = cy + 75 * s
            drawLine(Color(0xFFB98552).copy(alpha = fade), Offset(x + 14 * s, y + 14 * s), Offset(x + 34 * s, y + 34 * s), strokeWidth = 7 * s, cap = StrokeCap.Round)
            drawCircle(Color.White.copy(alpha = 0.55f * fade), radius = 22 * s, center = Offset(x, y))
            drawCircle(Color(0xFF4A3B35).copy(alpha = fade), radius = 22 * s, center = Offset(x, y), style = Stroke(4 * s))
        }
        Reaction.PUFF -> for (i in 0 until 5) {
            val q = stagger(i % 2)
            drawCircle(Color.White.copy(alpha = 0.7f * (1f - q)), radius = (8 + 14 * q) * s, center = Offset(cx + (-100 + i * 50) * s, cy + 125 * s - 12 * s * q))
        }
    }
}

private fun DrawScope.speechBubble(center: Offset, w: Float, h: Float, alpha: Float) {
    if (w <= 1f || h <= 1f) return
    val tail = Path().apply {
        moveTo(center.x - w * 0.3f, center.y + h * 0.3f)
        lineTo(center.x - w * 0.55f, center.y + h * 0.85f)
        lineTo(center.x - w * 0.05f, center.y + h * 0.4f)
        close()
    }
    drawPath(tail, Color.White.copy(alpha = alpha))
    drawRoundRect(Color.White.copy(alpha = alpha), topLeft = Offset(center.x - w / 2, center.y - h / 2), size = Size(w, h), cornerRadius = CornerRadius(h / 2))
    drawRoundRect(Color(0xFF4A3B35).copy(alpha = 0.25f * alpha), topLeft = Offset(center.x - w / 2, center.y - h / 2), size = Size(w, h), cornerRadius = CornerRadius(h / 2), style = Stroke(1.5f))
}
