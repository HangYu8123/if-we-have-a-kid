package app.childrensimulator.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import app.childrensimulator.sim.engine.Mouth
import app.childrensimulator.sim.engine.Pose
import app.childrensimulator.sim.model.Caregiver
import app.childrensimulator.sim.model.Place
import app.childrensimulator.sim.model.Technique
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Everything the illustration needs, derived from the simulation view. */
data class BabyVisual(
    val pose: Pose,
    val place: Place,
    val heldBy: Caregiver?,
    val techniques: Set<Technique>,
    val skinTone: Int,
    val hazards: List<String> = emptyList(),
    val bottle: Boolean = false,
    val night: Boolean = false,
    /** False draws only the scene, for example the bassinet during the sleep-space check. */
    val showBaby: Boolean = true,
)

/**
 * A readable 2D baby (docs/10 §11): poses for asleep, awake, rooting, fussing, crying, yawning, gas and feeding,
 * plus the scene (bassinet, arms, changing table, play mat). Always paired with a text description.
 */
@Composable
fun BabyCanvas(visual: BabyVisual, description: String, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "baby")
    val breath by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3800, easing = LinearEasing)), label = "breath")
    val cry by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "cry")
    val rock by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "rock")
    val drift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "drift")
    val textMeasurer = rememberTextMeasurer()
    val motion = !reducedMotion

    Canvas(modifier.semantics { contentDescription = description }) {
        val s = min(size.width / 360f, size.height / 336f)
        val cx = size.width / 2f
        val cy = size.height * 0.5f
        val night = visual.night
        drawScene(visual, s, cx, cy, night)
        if (!visual.showBaby) return@Canvas

        val rocking = Technique.ROCK in visual.techniques && visual.heldBy != null
        val angle = if (rocking && motion) 6f * sin(2 * PI * rock).toFloat() else 0f
        val breathe = if (motion) 1f + 0.018f * sin(2 * PI * breath).toFloat() else 1f
        val fit = if (visual.place == Place.BASSINET) 0.88f else 1f
        withTransform({
            rotate(angle, Offset(cx, cy + 120 * s))
            scale(breathe * fit, breathe * fit, Offset(cx, cy))
        }) {
            drawBaby(visual, s, cx, cy, if (motion) cry else 0.5f)
        }
        if (Technique.SHUSH in visual.techniques) {
            for (i in 0 until 3) {
                val a = if (motion) ((drift + i / 3f) % 1f) else 0.5f
                drawArc(
                    color = Color(0xFF6E8FB0).copy(alpha = 0.6f * (1f - a)),
                    startAngle = -40f, sweepAngle = 80f, useCenter = false,
                    topLeft = Offset(cx + 60 * s + a * 30 * s, cy - 70 * s - a * 10 * s),
                    size = Size(30 * s + a * 40 * s, 50 * s + a * 30 * s),
                    style = Stroke(width = 3 * s, cap = StrokeCap.Round),
                )
            }
        }
        if (visual.pose.asleep) {
            val a = if (motion) drift else 0.3f
            drawText(
                textMeasurer, "z", topLeft = Offset(cx + 55 * s + a * 20 * s, cy - 95 * s - a * 40 * s),
                style = TextStyle(color = Color(0xFF7C8BA6).copy(alpha = 1f - a), fontSize = (16 + 10 * a).sp),
            )
            drawText(
                textMeasurer, "z", topLeft = Offset(cx + 35 * s + a * 15 * s, cy - 70 * s - a * 25 * s),
                style = TextStyle(color = Color(0xFF7C8BA6).copy(alpha = 0.8f * (1f - a)), fontSize = 12.sp),
            )
        }
        if (Technique.CALM_ROOM in visual.techniques) {
            drawRect(Color(0xFF000814).copy(alpha = if (night) 0.15f else 0.28f))
            drawCircle(Color(0xFFF3E3A1), radius = 14 * s, center = Offset(size.width - 34 * s, 30 * s))
            drawCircle(if (night) Color(0xFF181B25) else Color(0xFF4A4F5E), radius = 12 * s, center = Offset(size.width - 28 * s, 26 * s))
        }
    }
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
            drawOval(c(Color(0xFFEFE2D6)), topLeft = Offset(cx - 160 * s, cy - 120 * s), size = Size(320 * s, 250 * s))
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

private fun DrawScope.drawBaby(v: BabyVisual, s: Float, cx: Float, cy: Float, cryPhase: Float) {
    val p = v.pose
    val skin = SkinTones.getOrElse(v.skinTone) { SkinTones[1] }
    val outline = Color(0xFF5B3A2E)
    val onesie = Color(0xFFBFE3D0)
    val dim = if (v.night) 0.7f else 1f
    fun c(color: Color) = Color(color.red * dim, color.green * dim, color.blue * dim, color.alpha)

    if (v.heldBy != null && v.place == Place.ARMS) {
        // Supporting arms (behind the baby).
        val sleeve = if (v.heldBy == Caregiver.PLAYER) Color(0xFF7D93AE) else Color(0xFF9C8BB4)
        drawRoundRect(c(sleeve), topLeft = Offset(cx - 170 * s, cy + 40 * s), size = Size(150 * s, 70 * s), cornerRadius = CornerRadius(35 * s))
        drawRoundRect(c(sleeve), topLeft = Offset(cx + 20 * s, cy + 40 * s), size = Size(150 * s, 70 * s), cornerRadius = CornerRadius(35 * s))
    }

    if (p.onTummy) {
        drawOval(c(onesie), topLeft = Offset(cx - 80 * s, cy + 5 * s), size = Size(160 * s, 85 * s))
        drawOval(c(skin), topLeft = Offset(cx - 70 * s, cy + 55 * s), size = Size(40 * s, 30 * s))
        drawOval(c(skin), topLeft = Offset(cx + 30 * s, cy + 55 * s), size = Size(40 * s, 30 * s))
        drawFace(p, s, cx, cy - 25 * s, 48f, skin, outline, cryPhase, ::c)
        return
    }

    // Legs.
    if (p.legsUp) {
        drawOval(c(onesie), topLeft = Offset(cx - 52 * s, cy + 55 * s), size = Size(40 * s, 46 * s))
        drawOval(c(onesie), topLeft = Offset(cx + 12 * s, cy + 55 * s), size = Size(40 * s, 46 * s))
        drawCircle(c(skin), radius = 11 * s, center = Offset(cx - 32 * s, cy + 58 * s))
        drawCircle(c(skin), radius = 11 * s, center = Offset(cx + 32 * s, cy + 58 * s))
    } else {
        drawOval(c(onesie), topLeft = Offset(cx - 48 * s, cy + 70 * s), size = Size(36 * s, 70 * s))
        drawOval(c(onesie), topLeft = Offset(cx + 12 * s, cy + 70 * s), size = Size(36 * s, 70 * s))
        drawCircle(c(skin), radius = 11 * s, center = Offset(cx - 30 * s, cy + 140 * s))
        drawCircle(c(skin), radius = 11 * s, center = Offset(cx + 30 * s, cy + 140 * s))
    }
    // Body.
    drawOval(c(onesie), topLeft = Offset(cx - 58 * s, cy - 5 * s), size = Size(116 * s, 105 * s))
    drawCircle(c(Color(0xFF9CCDB4)), radius = 4 * s, center = Offset(cx, cy + 30 * s))
    drawCircle(c(Color(0xFF9CCDB4)), radius = 4 * s, center = Offset(cx, cy + 50 * s))
    // Arms.
    if (p.handsToMouth) {
        drawOval(c(onesie), topLeft = Offset(cx - 62 * s, cy - 8 * s), size = Size(34 * s, 52 * s))
        drawOval(c(onesie), topLeft = Offset(cx + 28 * s, cy - 8 * s), size = Size(34 * s, 52 * s))
    } else {
        drawOval(c(onesie), topLeft = Offset(cx - 80 * s, cy + 5 * s), size = Size(34 * s, 62 * s))
        drawOval(c(onesie), topLeft = Offset(cx + 46 * s, cy + 5 * s), size = Size(34 * s, 62 * s))
        drawCircle(c(skin), radius = 12 * s, center = Offset(cx - 64 * s, cy + 68 * s))
        drawCircle(c(skin), radius = 12 * s, center = Offset(cx + 64 * s, cy + 68 * s))
    }
    drawFace(p, s, cx, cy - 45 * s, 54f, skin, outline, cryPhase, ::c)
    if (p.handsToMouth) {
        // Slightly darker than the face, with an outline, so the fists read clearly against it.
        val fistShade = Color(skin.red * 0.93f, skin.green * 0.86f, skin.blue * 0.82f)
        for ((dx, dy) in listOf(-20f to -18f, 18f to -16f)) {
            val center = Offset(cx + dx * s + p.headTurn * 14 * s, cy + dy * s)
            drawCircle(c(fistShade), radius = 12 * s, center = center)
            drawCircle(c(outline).copy(alpha = 0.45f), radius = 12 * s, center = center, style = Stroke(width = 1.8f * s))
            drawArc(
                c(outline).copy(alpha = 0.35f), startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(center.x - 7 * s, center.y - 6 * s), size = Size(14 * s, 8 * s), style = Stroke(width = 1.4f * s),
            )
        }
    }
    if (v.bottle) {
        val mouth = Offset(cx + p.headTurn * 14 * s, cy - 25 * s)
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
        }
    }
}

private fun DrawScope.drawFace(p: Pose, s: Float, hx: Float, hy: Float, radius: Float, skin: Color, outline: Color, cryPhase: Float, c: (Color) -> Color) {
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
    // Eyes.
    if (p.eyesOpen) {
        for (dx in listOf(-17f, 17f)) {
            drawOval(c(Color(0xFF2E2420)), topLeft = Offset(fx + dx * s - 5 * s, eyeY - 7 * s), size = Size(10 * s, 13 * s))
            drawCircle(Color.White.copy(alpha = 0.9f), radius = 2.2f * s, center = Offset(fx + dx * s + 2 * s, eyeY - 3 * s))
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
    val blush = 0.3f + 0.4f * p.redFace
    drawCircle(Color(0xFFE88E7E).copy(alpha = blush), radius = 8 * s, center = Offset(fx - 28 * s, hy + 12 * s))
    drawCircle(Color(0xFFE88E7E).copy(alpha = blush), radius = 8 * s, center = Offset(fx + 28 * s, hy + 12 * s))
    // Mouth.
    val my = hy + 20 * s
    when (p.mouth) {
        Mouth.SMILE -> drawArc(c(outline), startAngle = 20f, sweepAngle = 140f, useCenter = false, topLeft = Offset(fx - 11 * s, my - 8 * s), size = Size(22 * s, 14 * s), style = Stroke(width = 2.6f * s, cap = StrokeCap.Round))
        Mouth.NEUTRAL -> drawLine(c(outline), Offset(fx - 6 * s, my), Offset(fx + 6 * s, my), strokeWidth = 2.4f * s, cap = StrokeCap.Round)
        Mouth.ROOTING -> drawCircle(c(Color(0xFF8A3B32)), radius = 6 * s, center = Offset(fx, my))
        Mouth.SUCKING -> drawCircle(c(Color(0xFF8A3B32)), radius = 4.5f * s, center = Offset(fx, my))
        Mouth.FUSS -> {
            drawArc(c(outline), startAngle = 200f, sweepAngle = 140f, useCenter = false, topLeft = Offset(fx - 10 * s, my - 2 * s), size = Size(20 * s, 12 * s), style = Stroke(width = 2.6f * s, cap = StrokeCap.Round))
        }
        Mouth.YAWN -> drawOval(c(Color(0xFF8A3B32)), topLeft = Offset(fx - 8 * s, my - 8 * s), size = Size(16 * s, 22 * s))
        Mouth.CRY -> {
            val h = (16f + 12f * cryPhase) * s
            drawOval(c(Color(0xFF8A3B32)), topLeft = Offset(fx - 15 * s, my - h / 2f), size = Size(30 * s, h))
            drawOval(c(Color(0xFFE57E7E)), topLeft = Offset(fx - 9 * s, my + h / 2f - 9 * s), size = Size(18 * s, 8 * s))
        }
    }
    if (p.tears) {
        drawCircle(Color(0xFF8EC3EB).copy(alpha = 0.85f), radius = 3.5f * s, center = Offset(fx - 22 * s, eyeY + 10 * s))
        drawCircle(Color(0xFF8EC3EB).copy(alpha = 0.85f), radius = 3.5f * s, center = Offset(fx + 22 * s, eyeY + 12 * s))
    }
    if (p.activeSleep && cryPhase > 0.7f) {
        drawLine(c(outline).copy(alpha = 0.5f), Offset(hx + r + 6 * s, hy + 18 * s), Offset(hx + r + 14 * s, hy + 14 * s), strokeWidth = 2 * s)
    }
}
