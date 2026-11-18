package app.childrensimulator.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.childrensimulator.data.QuizQuestion
import app.childrensimulator.sim.TimeWindow
import app.childrensimulator.sim.engine.Mouth
import app.childrensimulator.sim.engine.Pose
import app.childrensimulator.sim.engine.TrialSetup
import app.childrensimulator.sim.model.Caregiver
import app.childrensimulator.sim.model.FeedingRoute
import app.childrensimulator.sim.model.Place
import app.childrensimulator.sim.model.RealismPreset
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId

private fun presetLine(p: RealismPreset, helper: String) = when (p) {
    RealismPreset.GENTLE -> "15% of real care time. Lots of help and hints."
    RealismPreset.STANDARD -> "A third of real time. $helper covers your nights."
    RealismPreset.REALISTIC -> "60% of real time. On call day and night."
    RealismPreset.EXTREME -> "Full real time. Opt-in night wake-ups."
}

/**
 * Quick start (feedback v0.2: "setup too complex"): a ready-made baby and one question, how real it should feel.
 * Name, look, feeding route, helper and sleep window keep sensible defaults and sit behind "Change details".
 */
@Composable
fun OnboardingScreen(state: AppState, onDone: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("Mia") }
    var skin by rememberSaveable { mutableIntStateOf(1) }
    var route by rememberSaveable { mutableStateOf(FeedingRoute.FORMULA) }
    var sleepStart by rememberSaveable { mutableIntStateOf(23 * 60) }
    var sleepEnd by rememberSaveable { mutableIntStateOf(7 * 60) }
    var helperName by rememberSaveable { mutableStateOf("Sam") }
    var preset by rememberSaveable { mutableStateOf(RealismPreset.STANDARD) }
    var customize by rememberSaveable { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val fx = rememberBabyFx()

    fun start() {
        if (starting) return
        starting = true
        scope.launch {
            state.repo.startTrial(
                TrialSetup(
                    childName = name.trim().ifBlank { "Baby" }, route = route, preset = preset,
                    sleepWindow = TimeWindow(sleepStart, sleepEnd), helperName = helperName.trim().ifBlank { "Helper" },
                    helperRelation = "partner", zoneId = ZoneId.systemDefault().id, takeOneNightWake = false,
                    skinTone = skin, seed = System.nanoTime(),
                ),
            )
            if (preset == RealismPreset.EXTREME && state.repo.canScheduleExact()) state.prefs.exactNightAlarms = true
            state.prefs.onboarded = true
            onDone()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val pose = Pose(
            asleep = false, eyesOpen = true, mouth = Mouth.NEUTRAL, handsToMouth = false, legsUp = false, headTurn = 0f,
            tears = false, redFace = 0f, yawning = false, activeSleep = false, onTummy = false,
        )
        val now = LocalTime.now()
        BabyStage(
            BabyVisual(pose, Place.ARMS, Caregiver.PLAYER, emptySet(), skin, room = true, hour = now.hour + now.minute / 60f),
            "${name.ifBlank { "Your baby" }}, three weeks old, awake and calm in your arms",
            state.prefs.reducedMotion, fx,
            Modifier.fillMaxWidth().height(270.dp),
            onTouch = { part -> if (part != BodyPart.FACE) fx.react(Reaction.HEARTS) },
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Meet ${name.ifBlank { "your baby" }}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Three weeks old. 48 real hours together, with ${helperName.ifBlank { "a helper" }} to help. Tap ${name.ifBlank { "the baby" }}: try a cheek or a hand.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text("How real should it feel?", style = MaterialTheme.typography.titleMedium)
        RealismGrid(preset, helperName.ifBlank { "Your helper" }) { preset = it }
        if (preset == RealismPreset.EXTREME) {
            Text("Turn on audible night wake-ups from the home screen on any night you want them.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { customize = !customize }) { Text(if (customize) "Hide details" else "Change name, look, feeding or nights") }
        AnimatedVisibility(customize) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(16) }, label = { Text("Baby's name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkinTones.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(44.dp).background(c, CircleShape)
                                .border(if (skin == i) 3.dp else 1.dp, if (skin == i) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                                .clickable(role = Role.RadioButton, onClickLabel = "Skin tone ${i + 1}") { skin = i },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionTile(CareIcon.BOTTLE, "Formula", TileTone.FEED, { route = FeedingRoute.FORMULA }, Modifier.weight(1f), selected = route == FeedingRoute.FORMULA, reducedMotion = state.prefs.reducedMotion)
                    ActionTile(CareIcon.BREAST, "Breast/chest", TileTone.FEED, { route = FeedingRoute.BREAST }, Modifier.weight(1f), selected = route == FeedingRoute.BREAST, reducedMotion = state.prefs.reducedMotion)
                }
                OutlinedTextField(value = helperName, onValueChange = { helperName = it.take(16) }, label = { Text("Helper's name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TimeStepper("Your sleep starts", sleepStart) { sleepStart = it }
                TimeStepper("Your sleep ends", sleepEnd) { sleepEnd = it }
                Text("You can change realism, nights and your helper later in Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= 33 && !state.container.notifier.permissionGranted()) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else start()
            },
            enabled = name.isNotBlank() && !starting,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Start", style = MaterialTheme.typography.titleMedium) }
        Text(
            "A learning simulation, not medical advice. The health content is a draft that hasn't been reviewed by a pediatric professional.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RealismGrid(selected: RealismPreset, helper: String, onSelect: (RealismPreset) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in RealismPreset.entries.chunked(2)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (p in row) {
                    val on = p == selected
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        border = if (on) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.weight(1f).fillMaxHeight().selectable(selected = on, onClick = { onSelect(p) }, role = Role.RadioButton),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            EffortBars(p.ordinal + 1, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                            Text(p.label, style = MaterialTheme.typography.titleMedium)
                            Text(presetLine(p, helper), style = MaterialTheme.typography.bodySmall)
                            if (p == RealismPreset.STANDARD) Text("Recommended", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/** One to four bars: how much effort the realism level asks for. */
@Composable
private fun EffortBars(level: Int, color: Color) {
    Canvas(Modifier.width(52.dp).height(22.dp)) {
        val w = size.width / 4f
        for (i in 0 until 4) {
            val h = size.height * (0.4f + 0.2f * i)
            drawRoundRect(
                if (i < level) color else color.copy(alpha = 0.2f),
                topLeft = Offset(i * w + 1.5f, size.height - h), size = Size(w - 3f, h), cornerRadius = CornerRadius(3f),
            )
        }
    }
}

@Composable
private fun TimeStepper(label: String, minute: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((minute - 30 + 1440) % 1440) }) { Text("−30") }
        Text(TimeWindow.hhmm(minute), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 10.dp))
        OutlinedButton(onClick = { onChange((minute + 30) % 1440) }) { Text("+30") }
    }
}

@Composable
fun QuizStep(questions: List<QuizQuestion>, answers: MutableMap<String, String>, reveal: Boolean = false) {
    Text("Quick knowledge check", style = MaterialTheme.typography.headlineSmall)
    Text("Optional. There's a matching set after the trial so you can compare.", style = MaterialTheme.typography.bodyMedium)
    questions.forEachIndexed { i, q ->
        SectionCard {
            Text("${i + 1}. ${q.text}", style = MaterialTheme.typography.titleMedium)
            q.options.forEach { o ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = answers[q.id] == o.id, onClick = { answers[q.id] = o.id }, role = Role.RadioButton).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = answers[q.id] == o.id, onClick = null)
                    Text(o.text)
                }
            }
            if (reveal && answers[q.id] != null) {
                val right = answers[q.id] == q.answer
                Text((if (right) "Right. " else "Not quite. ") + q.explain, color = if (right) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
            }
        }
    }
}
