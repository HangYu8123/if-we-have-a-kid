package app.childrensimulator.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.childrensimulator.data.QuizQuestion
import app.childrensimulator.sim.TimeWindow
import app.childrensimulator.sim.engine.Channels
import app.childrensimulator.sim.engine.Mouth
import app.childrensimulator.sim.engine.Pose
import app.childrensimulator.sim.engine.TrialSetup
import app.childrensimulator.sim.model.FeedingRoute
import app.childrensimulator.sim.model.Place
import app.childrensimulator.sim.model.RealismPreset
import app.childrensimulator.sim.model.Technique
import kotlinx.coroutines.launch
import java.time.ZoneId

private val presetDescriptions = mapOf(
    RealismPreset.GENTLE to listOf(
        "Hands-on care takes about 15% of real time",
        "Your helper covers your sleep window and anything past 8 reminders a day",
        "Clear labels, meters and hints; 20 extra minutes before the helper steps in",
    ),
    RealismPreset.STANDARD to listOf(
        "Care takes about a third of real time",
        "Reminders for hunger, dirty diapers and sustained crying",
        "Your helper covers your sleep window. You can choose to take one night wake",
        "Hints on request; 10 extra minutes of grace",
    ),
    RealismPreset.REALISTIC to listOf(
        "Care takes about 60% of real time",
        "Reminders for every need, day and night, as ordinary notifications",
        "Cues and the care log; hints are optional",
    ),
    RealismPreset.EXTREME to listOf(
        "Care takes its full real time",
        "Opt in each night to up to two audible wake-ups; after that your helper takes over",
        "Cues and the care log only. Presence check-ins during long care",
    ),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(state: AppState, onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("Mia") }
    var skin by rememberSaveable { mutableIntStateOf(1) }
    var route by rememberSaveable { mutableStateOf(FeedingRoute.FORMULA) }
    var sleepStart by rememberSaveable { mutableIntStateOf(23 * 60) }
    var sleepEnd by rememberSaveable { mutableIntStateOf(7 * 60) }
    var helperName by rememberSaveable { mutableStateOf("Sam") }
    var relation by rememberSaveable { mutableStateOf("partner") }
    var preset by rememberSaveable { mutableStateOf(RealismPreset.STANDARD) }
    var oneWake by rememberSaveable { mutableStateOf(false) }
    var nightOptIn by rememberSaveable { mutableStateOf(false) }
    val answers = remember { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()
    val steps = 6

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        LinearProgressIndicator(progress = { (step + 1) / steps.toFloat() }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        // A fresh scroll position for each step.
        key(step) { Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (step) {
                0 -> WelcomeStep(state)
                1 -> {
                    Text("Your baby", style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(value = name, onValueChange = { name = it.take(16) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SkinTones.forEachIndexed { i, c ->
                            Box(
                                Modifier.size(48.dp).background(c, CircleShape)
                                    .border(if (skin == i) 3.dp else 1.dp, if (skin == i) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                                    .clickable(role = Role.RadioButton, onClickLabel = "Skin tone ${i + 1}") { skin = i },
                            )
                        }
                    }
                    Text("Feeding", style = MaterialTheme.typography.titleMedium)
                    Choice(route == FeedingRoute.FORMULA, "Formula by bottle", "Prepare each bottle safely, then a paced feed. About 7–9 feeds a day at this age.") { route = FeedingRoute.FORMULA }
                    Choice(route == FeedingRoute.BREAST, "Breast/chestfeeding", "Position, latch and follow cues. Often 8–11 feeds a day at this age. A helper gives expressed milk.") { route = FeedingRoute.BREAST }
                    Text("Every feeding choice is respected here. The trial practices the steps either way.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                2 -> {
                    Text("Your nights and your helper", style = MaterialTheme.typography.headlineSmall)
                    Text("Your sleep window decides how night care reaches you. ${name.ifBlank { "The baby" }}'s own rhythm doesn't change.")
                    TimeStepper("Sleep starts", sleepStart) { sleepStart = it }
                    TimeStepper("Sleep ends", sleepEnd) { sleepEnd = it }
                    OutlinedTextField(value = helperName, onValueChange = { helperName = it.take(16) }, label = { Text("Helper's name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (r in listOf("partner", "parent", "relative", "friend", "nanny")) {
                            FilterChip(selected = relation == r, onClick = { relation = r }, label = { Text(r) })
                        }
                    }
                    Text("Your helper covers your sleep window in Gentle and Standard, any break you take, and any wake you hand off. Covered care still happens and shows in your report.", style = MaterialTheme.typography.bodyMedium)
                }
                3 -> RealismStep(state, preset, { preset = it }, oneWake, { oneWake = it }, nightOptIn, { nightOptIn = it }, helperName)
                4 -> QuizStep(state.content.quiz.pre, answers)
                5 -> {
                    Text("Ready when you are", style = MaterialTheme.typography.headlineSmall)
                    SectionCard {
                        StatRow("Baby", "$name, 3 weeks old")
                        StatRow("Feeding", route.label)
                        StatRow("Your sleep window", TimeWindow(sleepStart, sleepEnd).label())
                        StatRow("Helper", "$helperName ($relation)")
                        StatRow("Realism", preset.label)
                        StatRow("Length", "48 active hours")
                    }
                    Text("The clock starts when you tap Start. Care continues while the app is closed. You can pause, hand off or take a break at any time, and pausing costs nothing.")
                    Text("This is a simulation for learning. The health content is a draft that has not been reviewed by a pediatric professional, and it is not medical advice.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step > 0) TextButton(onClick = { step-- }) { Text("Back") } else Spacer(Modifier)
            if (step < steps - 1) {
                Button(onClick = { step++ }, enabled = step != 1 || name.isNotBlank()) { Text(if (step == 4) (if (answers.isEmpty()) "Skip" else "Continue") else "Next") }
            } else {
                Button(onClick = {
                    state.prefs.practiceQuizPre = answers.entries.joinToString(";") { "${it.key}=${it.value}" }
                    scope.launch {
                        state.repo.startTrial(
                            TrialSetup(
                                childName = name.trim().ifBlank { "Baby" }, route = route, preset = preset,
                                sleepWindow = TimeWindow(sleepStart, sleepEnd), helperName = helperName.trim().ifBlank { "Helper" },
                                helperRelation = relation, zoneId = ZoneId.systemDefault().id, takeOneNightWake = oneWake,
                                skinTone = skin, seed = System.nanoTime(),
                            ),
                        )
                        if (preset == RealismPreset.EXTREME && nightOptIn) state.repo.dispatch(app.childrensimulator.sim.engine.Command.NightConsent(true))
                        state.prefs.onboarded = true
                        onDone()
                    }
                }) { Text("Start the 48-hour trial") }
            }
        }
    }
}

@Composable
private fun WelcomeStep(state: AppState) {
    var rocking by remember { mutableStateOf(false) }
    Text("Children Simulator", style = MaterialTheme.typography.headlineSmall)
    Text("Care for a virtual three-week-old for 48 real hours. Read the baby's cues, then feed, change, soothe and settle them. Care takes real time and continues day and night, even when the app is closed.")
    SectionCard {
        StatRow("Feeds", "every 2–3 hours, day and night")
        StatRow("Diapers", "about 8–11 a day")
        StatRow("Sleep", "14–17 hours, in short stretches")
        StatRow("Fussing and crying", "can add up to 1½–2½ hours a day")
    }
    Text("Preview a cue", style = MaterialTheme.typography.titleMedium)
    val pose = Pose(asleep = false, eyesOpen = true, mouth = Mouth.ROOTING, handsToMouth = true, legsUp = false, headTurn = 0.4f, tears = false, redFace = 0f, yawning = false, activeSleep = false, onTummy = false)
    BabyCanvas(
        BabyVisual(pose, Place.ARMS, app.childrensimulator.sim.model.Caregiver.PLAYER, if (rocking) setOf(Technique.ROCK) else emptySet(), 1),
        "Preview: a baby rooting with hands to mouth", state.prefs.reducedMotion, Modifier.fillMaxWidth().height(200.dp),
    )
    Text("Turning toward your hand with an open mouth, hands to mouth: early hunger cues. Crying comes later.", fontWeight = FontWeight.SemiBold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilterChip(selected = rocking, onClick = { rocking = !rocking }, label = { Text("Try it: rock or sway") })
        Spacer(Modifier.size(8.dp))
        Text(if (rocking) "Comfort helps for a while, but hunger needs a feed." else "", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Choice(selected: Boolean, title: String, body: String, onClick: () -> Unit) {
    SectionCard(
        modifier = Modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.size(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TimeStepper(label: String, minute: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((minute - 30 + 1440) % 1440) }) { Text("−30 min") }
        Text(TimeWindow.hhmm(minute), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp))
        OutlinedButton(onClick = { onChange((minute + 30) % 1440) }) { Text("+30 min") }
    }
}

@Composable
private fun RealismStep(
    state: AppState,
    preset: RealismPreset,
    onPreset: (RealismPreset) -> Unit,
    oneWake: Boolean,
    onOneWake: (Boolean) -> Unit,
    nightOptIn: Boolean,
    onNightOptIn: (Boolean) -> Unit,
    helperName: String,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(state.container.notifier.permissionGranted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    Text("How real?", style = MaterialTheme.typography.headlineSmall)
    Text("Realism changes how much of the work reaches you. The baby's needs are the same in every level. You can change it later.")
    for (p in RealismPreset.entries) {
        Choice(preset == p, p.label + if (p == RealismPreset.STANDARD) " (recommended)" else "", presetDescriptions[p]!!.joinToString("\n") { "• $it" }) { onPreset(p) }
    }
    if (preset == RealismPreset.STANDARD) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = oneWake, onCheckedChange = onOneWake)
            Text("I'll take one night wake myself ($helperName covers the rest)")
        }
    }
    SectionCard {
        Text("Notifications", style = MaterialTheme.typography.titleMedium)
        Text("Reminders reach you when the baby needs care and you're on duty. Android decides sound, vibration and Do Not Disturb.")
        if (!granted && Build.VERSION.SDK_INT >= 33) {
            Button(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
        } else {
            Text("Notifications are allowed.", fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { state.container.notifier.test(Channels.CARE) }) { Text("Send a test reminder") }
        }
    }
    if (preset == RealismPreset.EXTREME) {
        SectionCard(container = MaterialTheme.colorScheme.tertiaryContainer) {
            Text("Audible night wake-ups", style = MaterialTheme.typography.titleMedium)
            Text("If you opt in for a night, up to two night alerts can play a short crying sound during your sleep window. After two, $helperName covers new wakes. The sound may wake other people nearby. Consent ends with each sleep window, and you can silence or hand off from the notification.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = nightOptIn, onCheckedChange = onNightOptIn)
                Text("Enable for my first night")
            }
            if (granted) OutlinedButton(onClick = { state.container.notifier.test(Channels.NIGHT) }) { Text("Test the night sound now") }
            if (Build.VERSION.SDK_INT >= 31) {
                val exact = state.repo.canScheduleExact()
                Text(if (exact) "Exact timing is available on this phone." else "Without exact timing, night alerts are best effort and may be late.", style = MaterialTheme.typography.bodyMedium)
                if (!exact) {
                    OutlinedButton(onClick = {
                        state.prefs.exactNightAlarms = true
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }) { Text("Allow exact timing") }
                } else {
                    state.prefs.exactNightAlarms = true
                }
            }
        }
    }
}

@Composable
fun QuizStep(questions: List<QuizQuestion>, answers: MutableMap<String, String>, reveal: Boolean = false) {
    Text("Quick knowledge check", style = MaterialTheme.typography.headlineSmall)
    Text("Optional. You'll see a matching set after the trial so you can compare.", style = MaterialTheme.typography.bodyMedium)
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
