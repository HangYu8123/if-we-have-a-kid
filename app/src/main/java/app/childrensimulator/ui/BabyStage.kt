package app.childrensimulator.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.childrensimulator.sim.engine.ChildView
import app.childrensimulator.sim.engine.DistressLevel
import app.childrensimulator.sim.engine.Mouth
import app.childrensimulator.sim.engine.cueFor
import app.childrensimulator.sim.model.NeedKind
import app.childrensimulator.sim.model.NeedPhase
import app.childrensimulator.sim.model.SessionKind
import app.childrensimulator.sim.model.SleepState

/**
 * The baby in their room. Touching a body part checks it (feedback v0.2): the baby reacts with a reflex and the
 * caller shows what the player notices. The magnifier takes a closer look at everything.
 */
@Composable
fun BabyStage(
    visual: BabyVisual,
    description: String,
    reducedMotion: Boolean,
    fx: BabyFxState,
    modifier: Modifier = Modifier,
    onTouch: (BodyPart) -> Unit = {},
    onCheck: (() -> Unit)? = null,
) {
    val current by rememberUpdatedState(visual)
    val touched by rememberUpdatedState(onTouch)
    Box(modifier.clip(RoundedCornerShape(24.dp))) {
        BabyCanvas(
            visual, description, reducedMotion,
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { o ->
                    val part = hitTestBaby(current, o.x, o.y, size.width.toFloat(), size.height.toFloat())
                    if (part != null) {
                        fx.touch(part, o)
                        touched(part)
                    }
                }
            },
            fx,
        )
        if (onCheck != null) {
            Surface(
                onClick = onCheck,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 3.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(54.dp).semantics { contentDescription = "Check the baby" },
            ) {
                CareIconView(CareIcon.CHECK, Modifier.padding(9.dp), animate = true, reducedMotion = reducedMotion)
            }
        }
    }
}

// ------------------------------------------------------------------ what a check reveals

private fun partTitle(part: BodyPart, name: String) = when (part) {
    BodyPart.FACE -> "$name's face"
    BodyPart.HAND -> "$name's hands"
    BodyPart.TUMMY -> "$name's tummy"
    BodyPart.DIAPER -> "The diaper"
    BodyPart.FEET -> "$name's feet"
    BodyPart.WHOLE -> "A closer look at $name"
}

private fun partIcon(part: BodyPart) = when (part) {
    BodyPart.DIAPER -> CareIcon.DIAPER_CHECK
    BodyPart.TUMMY -> CareIcon.BURP
    BodyPart.HAND -> CareIcon.HOLD
    BodyPart.FEET -> CareIcon.PLAY_MAT
    else -> CareIcon.CHECK
}

/** What the player notices when they check one part of the baby. Cues come from the simulation (docs/02 §3). */
fun observations(view: ChildView, part: BodyPart, diaperText: String?): List<String> {
    val asleep = view.sleep == SleepState.ASLEEP
    val visible = view.needs.filter { !(it.kind == NeedKind.WET && it.phase == NeedPhase.CUE) }
    fun cuesOf(vararg kinds: NeedKind) = visible.filter { it.kind in kinds }.map { cueFor(it.kind, it.phase) }
    val hungry = view.needs.any { it.kind == NeedKind.HUNGER }
    val feeding = view.pose.mouth == Mouth.SUCKING
    val crying = view.level.ordinal >= DistressLevel.CRYING.ordinal
    return when (part) {
        BodyPart.FACE -> buildList {
            if (asleep) {
                add(if (view.pose.activeSleep) "Eyes closed, little twitches and grunts: normal active sleep" else "Eyes closed, breathing slowly and evenly")
            } else {
                if (feeding) add(view.cue)
                else if (!crying) add(if (hungry) "Turns toward your finger with an open mouth (rooting)" else "Turns toward your touch for a moment, then settles")
                addAll(cuesOf(NeedKind.HUNGER, NeedKind.TIRED, NeedKind.OVERSTIM, NeedKind.CLOSENESS))
                if (crying && none { it == view.cue }) add(view.cue)
                if (size <= 1 && !crying && !hungry) add(if (view.heldBy != null) "Calm, watching your face" else "Calm and alert, looking around")
            }
            if (view.pose.tears) add("Tears on the cheeks")
        }.distinct()
        BodyPart.HAND -> buildList {
            add(
                when {
                    view.pose.handsToMouth -> "Hands up at the mouth, sucking on fists"
                    asleep -> "Fingers curl loosely around yours"
                    else -> "Grips your finger tightly (the grasp reflex)"
                },
            )
            if (crying) add("Fists clenched, arms flailing")
        }
        BodyPart.TUMMY -> buildList {
            addAll(cuesOf(NeedKind.GAS))
            if (isEmpty()) {
                val fedRecently = view.lastFeedStartWall?.let { view.wallNow - it < 45 * 60_000L } == true
                add(if (fedRecently) "Round and soft after the feed" else "Soft and relaxed")
            }
            if (view.pose.legsUp && none { it.contains("legs", ignoreCase = true) }) add("Legs pulled up to the belly")
        }
        BodyPart.DIAPER -> listOf(diaperText ?: "Checking the diaper…")
        BodyPart.FEET -> listOf(
            if (asleep) "Toes curl, then go still"
            else "Kicks back against your hand. Feet feel a little cool, which is normal: check warmth at the chest or the back of the neck",
        )
        BodyPart.WHOLE -> (listOf(view.cue) + view.cueDetails).distinct()
    }
}

/** How much of the simulation's reasoning a check reveals at each realism level (docs/05 §2). */
enum class HintMode { FULL, ON_REQUEST, NONE }

@Composable
fun CheckPanel(
    view: ChildView,
    part: BodyPart,
    hintMode: HintMode,
    diaperText: String?,
    showHints: Boolean,
    onShowHints: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CareIconView(partIcon(part), Modifier.size(36.dp))
            Spacer(Modifier.width(10.dp))
            Text(partTitle(part, view.name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
        }
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            observations(view, part, diaperText).forEach {
                Text("• $it", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        if (hintMode != HintMode.NONE) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f))
            if (hintMode == HintMode.ON_REQUEST && !showHints) {
                TextButton(onClick = { onShowHints(true) }) { Text("Show hints") }
            } else {
                view.meters.forEach { (name, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, modifier = Modifier.width(92.dp), style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { value.toFloat() }, modifier = Modifier.weight(1f).height(8.dp))
                    }
                }
                view.hints.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                if (hintMode == HintMode.ON_REQUEST) TextButton(onClick = { onShowHints(false) }) { Text("Hide hints") }
            }
        }
        Text(notesLine(view), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
    }
}

/** The care log in one line: what a parent would remember or jot down. */
fun notesLine(view: ChildView): String = buildList {
    view.lastFeedStartWall?.let { add("Last feed ${fmtClock(it)} (${fmtAgo(view.wallNow, it)})") }
    view.lastChangeWall?.let { add("last change ${fmtClock(it)}") }
    add((if (view.sleep == SleepState.ASLEEP) "asleep " else "awake ") + fmtDuration(view.wallNow - view.awakeOrAsleepSinceWall))
    if (view.bottleReady && view.session?.kind != SessionKind.BOTTLE_FEED) add("a bottle is ready")
}.joinToString(" · ")

// ------------------------------------------------------------------ tiles

/** A round picture button with a short caption, pet-game style. */
@Composable
fun ActionTile(
    icon: CareIcon,
    label: String,
    tone: TileTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    reducedMotion: Boolean = false,
    tileSize: Dp = 62.dp,
) {
    val dark = isDarkUi()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !reducedMotion) 0.88f else 1f, label = "press")
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .scale(scale)
                .size(tileSize)
                .background(tone.color(dark), RoundedCornerShape(tileSize / 3))
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(tileSize / 3)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            CareIconView(icon, Modifier.fillMaxSize().padding(tileSize / 8), animate = selected, reducedMotion = reducedMotion)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** A picture choice inside a care step. The full sentence stays available to screen readers. */
@Composable
fun ChoiceTile(icon: CareIcon, caption: String, fullText: String, onClick: () -> Unit, modifier: Modifier = Modifier, reducedMotion: Boolean = false) {
    val dark = isDarkUi()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !reducedMotion) 0.94f else 1f, label = "press")
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(20.dp),
        color = TileTone.CHOICE.color(dark),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = modifier.scale(scale).semantics { contentDescription = fullText },
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CareIconView(icon, Modifier.size(68.dp), animate = true, reducedMotion = reducedMotion)
            Spacer(Modifier.height(6.dp))
            Text(caption, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
