package app.childrensimulator.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.childrensimulator.data.RunUi
import app.childrensimulator.sim.engine.ChildView
import app.childrensimulator.sim.engine.Command
import app.childrensimulator.sim.engine.DistressLevel
import app.childrensimulator.sim.engine.Mouth
import app.childrensimulator.sim.engine.PracticeDrill
import app.childrensimulator.sim.engine.PresetRules
import app.childrensimulator.sim.engine.SessionView
import app.childrensimulator.sim.model.Caregiver
import app.childrensimulator.sim.model.FeedingRoute
import app.childrensimulator.sim.model.LogEntry
import app.childrensimulator.sim.model.LogType
import app.childrensimulator.sim.model.Place
import app.childrensimulator.sim.model.RealismPreset
import app.childrensimulator.sim.model.RunSettings
import app.childrensimulator.sim.model.SessionKind
import app.childrensimulator.sim.model.SleepState
import app.childrensimulator.sim.model.Technique
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

@Composable
fun HomeScreen(state: AppState, ui: RunUi, reducedMotion: Boolean) {
    val view = ui.view ?: return
    val settings = ui.settings ?: return
    CareView(
        state = state,
        view = view,
        settings = settings,
        reducedMotion = reducedMotion,
        feedback = ui.lastFeedback,
        log = ui.log,
        send = { state.repo.send(it) },
    )
}

/** The shared care surface for the trial and the Practice Room. */
@Composable
fun CareView(
    state: AppState,
    view: ChildView,
    settings: RunSettings,
    reducedMotion: Boolean,
    feedback: String?,
    log: List<LogEntry>,
    send: (Command) -> Unit,
    practiceDrill: PracticeDrill? = null,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    val rules = PresetRules.of(settings.preset)
    val practice = practiceDrill != null
    var showHints by remember { mutableStateOf(state.prefs.showHints) }
    var endDialog by remember { mutableStateOf(false) }
    var breakDialog by remember { mutableStateOf(false) }
    var presencePrompt by remember { mutableStateOf(false) }
    var check by remember { mutableStateOf<BodyPart?>(null) }
    var checkAt by remember { mutableLongStateOf(0L) }
    var checkTipSeen by remember { mutableStateOf(state.prefs.checkTipSeen) }
    val haptics = LocalHapticFeedback.current
    val sound = state.container.crySound
    val fx = rememberBabyFx()
    val lastDiaperCheck = remember(log) { log.lastOrNull { it.type == LogType.CHECK } }
    val diaperFeedback = lastDiaperCheck?.takeIf {
        view.now - it.t < 25_000 && it.wall >= (view.lastChangeWall ?: Long.MIN_VALUE)
    }?.text

    fun openCheck(part: BodyPart) {
        check = part
        checkAt = System.currentTimeMillis()
        if (!checkTipSeen) {
            checkTipSeen = true
            state.prefs.checkTipSeen = true
        }
    }

    LaunchedEffect(view.level, view.paused, view.ended) {
        sound.update(if (view.paused || view.ended) null else view.level, state.prefs.soundOn, quiet = view.nightNow)
        if (state.prefs.hapticsOn && view.level.ordinal >= DistressLevel.CRYING.ordinal && !view.paused) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    DisposableEffect(Unit) { onDispose { sound.stop() } }
    // A check is a snapshot in time: it fades after a while so the player looks again.
    LaunchedEffect(checkAt) {
        if (checkAt == 0L) return@LaunchedEffect
        delay(60_000L)
        check = null
    }
    // The baby answers care with a short reaction, driven by what actually happened in the log.
    var lastSeq by remember { mutableLongStateOf(log.lastOrNull()?.seq ?: 0L) }
    LaunchedEffect(log.lastOrNull()?.seq) {
        val fresh = log.takeLastWhile { it.seq > lastSeq }
        lastSeq = log.lastOrNull()?.seq ?: lastSeq
        reactionFor(fresh)?.let { fx.react(it) }
    }

    // Presence prompts for long sessions (docs/05 §6): a missed prompt pauses the session, nothing more.
    val session = view.session
    LaunchedEffect(session?.id, session?.running, settings.presencePrompts) {
        if (session == null || !session.running || !settings.presencePrompts || practice) return@LaunchedEffect
        while (true) {
            delay(4 * 60_000L)
            presencePrompt = true
            delay(60_000L)
            if (presencePrompt) {
                presencePrompt = false
                send(Command.SuspendSession)
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        header()
        if (!practice) TrialHeader(view, settings, onSettings = { state.nav.navigate("settings") }, onPause = { send(Command.Pause) }, onResume = { send(Command.Resume) })
        if (!practice) DutyStrip(view, settings, send)
        StageCard(
            view, settings, reducedMotion, practice, fx,
            onTouch = { part ->
                if (state.prefs.hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (part == BodyPart.DIAPER) send(Command.CheckDiaper)
                openCheck(part)
            },
            onCheck = { openCheck(BodyPart.WHOLE) },
        )
        if (check == null && !checkTipSeen) {
            Text(
                "Tap ${view.name} to check on them: face, hands, tummy, diaper or feet. The magnifier takes a closer look.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(check != null) {
            val part = check ?: BodyPart.WHOLE
            val hintMode = when {
                practice || rules.metersAlways || settings.preset == RealismPreset.STANDARD -> HintMode.FULL
                settings.preset == RealismPreset.REALISTIC -> HintMode.ON_REQUEST
                else -> HintMode.NONE
            }
            CheckPanel(
                view, part, hintMode, diaperFeedback, showHints,
                onShowHints = {
                    showHints = it
                    state.prefs.showHints = it
                },
                onClose = { check = null },
            )
        }
        feedback?.let { SectionCard(container = MaterialTheme.colorScheme.tertiaryContainer) { Text(it, color = MaterialTheme.colorScheme.onTertiaryContainer) } }
        CarePanel(view, settings, practiceDrill, reducedMotion, send, onCheckDiaper = { send(Command.CheckDiaper); openCheck(BodyPart.DIAPER) })
        view.coaching.lastOrNull()?.let { card ->
            SectionCard(container = if (card.kind == "misstep") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
                Text(card.title, style = MaterialTheme.typography.titleMedium)
                Text(card.text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (card.lessonId.isNotBlank()) TextButton(onClick = { state.nav.navigate("lesson/${card.lessonId}") }) { Text("Lesson ${card.lessonId}") }
                    TextButton(onClick = { send(Command.DismissCoaching(card.id)) }) { Text("Got it") }
                }
            }
        }
        if (!view.ended && !view.paused) {
            HelpDock(view, practice, reducedMotion, send, onBreak = { breakDialog = true }, onEnd = { endDialog = true })
        }
        Spacer(Modifier.height(24.dp))
    }

    if (presencePrompt) {
        AlertDialog(
            onDismissRequest = { presencePrompt = false },
            confirmButton = { Button(onClick = { presencePrompt = false }) { Text("I'm here") } },
            title = { Text("Still with ${view.name}?") },
            text = { Text("Long care sessions check in now and then. If you don't answer within a minute, the session pauses and keeps your progress.") },
        )
    }
    if (breakDialog) {
        AlertDialog(
            onDismissRequest = { breakDialog = false },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { breakDialog = false }) { Text("Cancel") } },
            title = { Text("Take a break") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${view.name} goes on their back in a clear bassinet and ${settings.helperName} covers. Taking a break is part of safe care.")
                    for (m in listOf(15, 30, 60, 120)) {
                        OutlinedButton(onClick = { breakDialog = false; send(Command.StartBreak(m)) }, modifier = Modifier.fillMaxWidth()) { Text("$m minutes") }
                    }
                }
            },
        )
    }
    if (endDialog) {
        AlertDialog(
            onDismissRequest = { endDialog = false },
            confirmButton = { TextButton(onClick = { endDialog = false; send(Command.EndRun) }) { Text("End trial") } },
            dismissButton = { TextButton(onClick = { endDialog = false }) { Text("Keep going") } },
            title = { Text("End the trial now?") },
            text = { Text("Your Care Report will cover the time so far. You can't resume this run afterwards.") },
        )
    }
}

/** Maps new log entries onto a short reaction, the most recent meaningful one first. */
private fun reactionFor(entries: List<LogEntry>): Reaction? {
    for (e in entries.asReversed()) {
        val mine = e.who == Caregiver.PLAYER
        val r = when {
            e.type == LogType.MISSTEP -> Reaction.EXCLAIM
            e.type == LogType.NEED_RESOLVED && e.tag == "GAS" && mine -> Reaction.BURP
            e.type == LogType.CHECK && mine -> when {
                e.text.contains("clean and dry") -> Reaction.SPARKLE
                e.text.contains("dirty") || e.text.contains("blowout") -> Reaction.STINK
                else -> Reaction.DROP
            }
            e.type == LogType.CARE_DONE && mine && (e.tag == "change" || e.tag == "prep") -> Reaction.SPARKLE
            e.type == LogType.SESSION_ENDED && mine && (e.tag == SessionKind.BOTTLE_FEED.name || e.tag == SessionKind.BREASTFEED.name) -> Reaction.HEARTS
            e.type == LogType.SESSION_ENDED && mine && e.tag == SessionKind.PLAY_TALK.name -> Reaction.NOTES
            e.type == LogType.SESSION_ENDED && mine && e.tag == SessionKind.TUMMY_TIME.name -> Reaction.STARS
            e.type == LogType.ASLEEP -> Reaction.ZZZ
            e.type == LogType.COMFORT && e.text.startsWith("You picked") -> Reaction.HEARTS
            e.type == LogType.COMFORT && e.tag == Technique.SHUSH.name -> Reaction.NOTES
            e.type == LogType.COMFORT && e.tag == Technique.CALM_ROOM.name -> Reaction.STARS
            e.type == LogType.COMFORT && e.text.startsWith("You laid") -> Reaction.PUFF
            else -> null
        }
        if (r != null) return r
    }
    return null
}

private fun activityFor(view: ChildView): BabyActivity {
    val s = view.session ?: return BabyActivity.NONE
    val id = s.step?.id ?: ""
    val sucking = view.pose.mouth == Mouth.SUCKING
    return when (s.kind) {
        SessionKind.BOTTLE_FEED -> when {
            sucking -> BabyActivity.BOTTLE_FEED
            id == "offer" -> BabyActivity.BOTTLE_OFFER
            id.contains("burp") -> BabyActivity.BURP
            else -> BabyActivity.NONE
        }
        SessionKind.BREASTFEED -> when {
            sucking -> BabyActivity.BREASTFEED
            id.contains("switch") -> BabyActivity.BURP
            else -> BabyActivity.NONE
        }
        SessionKind.BURP -> if (id.startsWith("burp") || id == "pat2") BabyActivity.BURP else BabyActivity.NONE
        SessionKind.DIAPER_CHANGE -> when (id) {
            "clean", "fountain", "missing", "fresh" -> BabyActivity.DIAPER_CLEAN
            "dress", "wash" -> BabyActivity.DIAPER_FRESH
            else -> if (view.place == Place.CHANGING_TABLE) BabyActivity.DIAPER_ON else BabyActivity.NONE
        }
        SessionKind.PLAY_TALK -> if (id.startsWith("face")) BabyActivity.PLAY else BabyActivity.NONE
        SessionKind.TUMMY_TIME -> if (id == "tummy") BabyActivity.TUMMY else BabyActivity.NONE
        else -> BabyActivity.NONE
    }
}

private fun localHour(wall: Long): Float = Instant.ofEpochMilli(wall).atZone(ZoneId.systemDefault()).let { it.hour + it.minute / 60f }

fun babyVisualFor(view: ChildView, settings: RunSettings, practice: Boolean, room: Boolean = true): BabyVisual = BabyVisual(
    pose = view.pose,
    place = view.place,
    heldBy = view.heldBy,
    techniques = view.techniques,
    skinTone = settings.skinTone,
    night = view.nightNow && !view.paused && !view.ended && !practice,
    activity = activityFor(view),
    room = room,
    hour = localHour(view.wallNow),
)

@Composable
private fun CarePanel(
    view: ChildView,
    settings: RunSettings,
    practiceDrill: PracticeDrill?,
    reducedMotion: Boolean,
    send: (Command) -> Unit,
    onCheckDiaper: () -> Unit,
) {
    val session = view.session
    if (view.ended) {
        SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
            Text(if (practiceDrill != null) "Practice finished" else "The trial has ended", style = MaterialTheme.typography.titleMedium)
            Text(if (practiceDrill != null) "Leave the Practice Room when you're ready." else "Open the Report tab for your Care Report, your moments together and reflection prompts.")
        }
    } else if (view.paused) {
        SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CareIconView(CareIcon.PAUSE, Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Paused", style = MaterialTheme.typography.titleMedium)
                    Text("Both clocks are frozen. Nothing is missed.", style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = { send(Command.Resume) }) { Text("Resume") }
            }
        }
    } else if (session != null) {
        SessionPanel(session, view, settings, reducedMotion, send)
    } else {
        ActionDock(view, settings, practiceDrill == PracticeDrill.SAFE_SLEEP, reducedMotion, send, onCheckDiaper)
    }
}

@Composable
private fun TrialHeader(view: ChildView, settings: RunSettings, onSettings: () -> Unit, onPause: () -> Unit, onResume: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${view.name} · ${view.ageText}", style = MaterialTheme.typography.titleLarge)
            val left = view.trialRemainingMs?.let { "${fmtDuration(it)} left" } ?: ""
            Text("$left · ${settings.preset.label}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!view.ended) {
            IconButton(onClick = if (view.paused) onResume else onPause, modifier = Modifier.semantics { contentDescription = if (view.paused) "Resume" else "Pause" }) {
                CareIconView(if (view.paused) CareIcon.RESUME else CareIcon.PAUSE, Modifier.size(26.dp))
            }
        }
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
    }
}

@Composable
private fun DutyStrip(view: ChildView, settings: RunSettings, send: (Command) -> Unit) {
    if (view.ended) return
    val helper = view.onDuty == Caregiver.HELPER
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (helper) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CareIconView(
                    when {
                        view.breakUntilWall != null -> CareIcon.BREAK
                        helper -> CareIcon.HANDOFF
                        view.nightNow -> CareIcon.NIGHT
                        else -> CareIcon.HOLD
                    },
                    Modifier.size(30.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        view.breakUntilWall != null -> "Break until ${fmtClock(view.breakUntilWall!!)}. ${settings.helperName} is covering."
                        helper -> view.coverageText ?: "${settings.helperName} is on duty."
                        view.nightNow -> "You're on duty tonight."
                        else -> "You're on duty."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (view.breakUntilWall != null) TextButton(onClick = { send(Command.EndBreak) }) { Text("End early") }
            }
            view.helperBusy?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (view.nightConsentAvailable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (view.nightNow) "Audible wake-ups tonight" else "Audible wake-ups next sleep window", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (view.nightConsentActive) "On: at most ${PresetRules.NIGHT_AUDIBLE_LIMIT} per night" + (view.nightAttemptsLeft?.let { ", $it left" } ?: "") + ". Then ${settings.helperName} takes over."
                            else "Off. Nights use ordinary notifications.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = view.nightConsentActive, onCheckedChange = { send(Command.NightConsent(it)) })
                }
            }
        }
    }
}

@Composable
private fun StageCard(
    view: ChildView,
    settings: RunSettings,
    reducedMotion: Boolean,
    practice: Boolean,
    fx: BabyFxState,
    onTouch: (BodyPart) -> Unit,
    onCheck: () -> Unit,
) {
    val visual = babyVisualFor(view, settings, practice)
    val where = when {
        view.heldBy == Caregiver.PLAYER -> "in your arms"
        view.heldBy == Caregiver.HELPER -> "with ${settings.helperName}"
        view.place == Place.BASSINET -> "in the bassinet"
        view.pose.onTummy -> "on their tummy on the play mat"
        else -> "on the ${view.place.label}"
    }
    // Screen readers get the cue as text; sighted players read the animation and check by touch.
    val description = "${view.name}, ${view.level.label.lowercase()}, $where: ${view.cue}"
    BabyStage(
        visual, description, reducedMotion, fx,
        Modifier.fillMaxWidth().height(310.dp),
        onTouch = onTouch, onCheck = onCheck,
    )
}

@Composable
private fun SessionPanel(session: SessionView, view: ChildView, settings: RunSettings, reducedMotion: Boolean, send: (Command) -> Unit) {
    SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CareIconView(sessionIcon(session.kind), Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Text(session.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
            StepDots(session.stepNumber, session.stepCount)
        }
        val step = session.step
        if (step != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                    if (step.timed) {
                        CircularProgressIndicator(
                            progress = { step.progress.toFloat() },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 6.dp,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                        )
                    }
                    Surface(shape = CircleShape, color = TileTone.CHOICE.color(isDarkUi()), modifier = Modifier.size(88.dp)) {
                        CareIconView(stepIcon(session.kind, step.id), Modifier.padding(12.dp), animate = session.running, reducedMotion = reducedMotion)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(step.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    if (step.instruction.isNotBlank() && step.id != "space") Text(step.instruction, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    if (step.timed) {
                        Text(
                            if (session.running) "About ${fmtDuration(step.remainingMs)} left" else "Paused. Resume to continue.",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }
            if (step.id == "space") {
                val hazards = step.choices.mapNotNull { it.id.takeIf { id -> id.startsWith("remove:") }?.removePrefix("remove:") }
                val spaceDescription = if (hazards.isEmpty()) "The clear bassinet" else "The bassinet, with: " + hazards.joinToString(", ") { it.replace('_', ' ') }
                BabyCanvas(
                    BabyVisual(view.pose, Place.BASSINET, null, emptySet(), settings.skinTone, hazards = hazards, showBaby = false),
                    spaceDescription, reducedMotion,
                    Modifier.fillMaxWidth().height(150.dp),
                )
            }
            ChoiceGrid(step.choices.map { Triple(it.id, it.label, choiceIcon(it.id)) }, reducedMotion) { send(Command.Choose(it)) }
            step.actionLabel?.let { label ->
                Button(onClick = { send(Command.DoStep) }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                    CareIconView(actionIcon(step.id), Modifier.size(36.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(actionCaption(step.id, label), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        session.intake?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CareIconView(if (session.kind == SessionKind.BREASTFEED) CareIcon.BREAST else CareIcon.BOTTLE, Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(progress = { it.toFloat() }, modifier = Modifier.weight(1f).height(10.dp))
                Spacer(Modifier.width(8.dp))
                Text("${(it * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
            }
        }
        session.feedback?.let { Text(it, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!session.running) SmallIconButton(CareIcon.RESUME, "Resume") { send(Command.ResumeSession) }
            SmallIconButton(CareIcon.STOP, "Stop") { send(Command.StopSession) }
            SmallIconButton(CareIcon.HANDOFF, "Hand off") { send(Command.HandOffSession) }
            Spacer(Modifier.weight(1f))
            Text(fmtDuration(session.handsOnMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun StepDots(number: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.semantics { contentDescription = "Step $number of $count" }) {
        for (i in 1..count.coerceAtMost(12)) {
            Surface(
                shape = CircleShape,
                color = when {
                    i < number -> MaterialTheme.colorScheme.primary
                    i == number -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                },
                modifier = Modifier.size(if (i == number) 10.dp else 7.dp),
            ) {}
        }
    }
}

@Composable
private fun ChoiceGrid(choices: List<Triple<String, String, CareIcon>>, reducedMotion: Boolean, onChoose: (String) -> Unit) {
    if (choices.isEmpty()) return
    val columns = if (choices.size == 3) 3 else 2
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in choices.chunked(columns)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((id, label, icon) in row) {
                    ChoiceTile(icon, choiceCaption(id, label), label, { onChoose(id) }, Modifier.weight(1f).fillMaxHeight(), reducedMotion)
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SmallIconButton(icon: CareIcon, label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        CareIconView(icon, Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

private data class DockItem(val icon: CareIcon, val label: String, val tone: TileTone, val selected: Boolean = false, val onClick: () -> Unit)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TileGrid(items: List<DockItem>, reducedMotion: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = if (maxWidth < 330.dp) 3 else 4
        val tileWidth = (maxWidth - 8.dp * (perRow - 1)) / perRow
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (item in items) {
                ActionTile(item.icon, item.label, item.tone, item.onClick, Modifier.width(tileWidth), selected = item.selected, reducedMotion = reducedMotion)
            }
        }
    }
}

@Composable
private fun ActionDock(
    view: ChildView,
    settings: RunSettings,
    requiresSleepSpaceCheck: Boolean,
    reducedMotion: Boolean,
    send: (Command) -> Unit,
    onCheckDiaper: () -> Unit,
) {
    val asleep = view.sleep == SleepState.ASLEEP
    val held = view.heldBy == Caregiver.PLAYER
    val items = buildList {
        if (settings.route == FeedingRoute.FORMULA) {
            if (!view.bottleReady) add(DockItem(CareIcon.MAKE_BOTTLE, "Make a bottle", TileTone.FEED) { send(Command.StartSession(SessionKind.BOTTLE_PREP)) })
            else add(DockItem(CareIcon.BOTTLE, "Feed", TileTone.FEED) { send(Command.StartSession(SessionKind.BOTTLE_FEED)) })
        } else {
            add(DockItem(CareIcon.BREAST, "Feed", TileTone.FEED) { send(Command.StartSession(SessionKind.BREASTFEED)) })
        }
        add(DockItem(CareIcon.BURP, "Burp", TileTone.FEED) { send(Command.StartSession(SessionKind.BURP)) })
        add(DockItem(CareIcon.DIAPER_CHECK, "Check diaper", TileTone.DIAPER, onClick = onCheckDiaper))
        add(DockItem(CareIcon.DIAPER, "Change", TileTone.DIAPER) { send(Command.StartSession(SessionKind.DIAPER_CHANGE)) })
        if (held) {
            if (!requiresSleepSpaceCheck) add(DockItem(CareIcon.BASSINET, "Lay down", TileTone.SLEEP) { send(Command.PutDown(Place.BASSINET)) })
            if (!asleep) add(DockItem(CareIcon.PLAY_MAT, "Play mat", TileTone.PLAY) { send(Command.PutDown(Place.PLAY_MAT)) })
        } else {
            add(DockItem(CareIcon.HOLD, "Hold", TileTone.COMFORT) { send(Command.PickUp) })
        }
        for (t in Technique.entries) {
            val on = t in view.techniques
            val (icon, label) = when (t) {
                Technique.ROCK -> CareIcon.ROCK to "Rock"
                Technique.SHUSH -> CareIcon.SHUSH to "Shush"
                Technique.CALM_ROOM -> CareIcon.DIM to "Dim lights"
            }
            add(DockItem(icon, label, TileTone.COMFORT, selected = on) { send(Command.SetTechnique(t, !on)) })
        }
        add(DockItem(CareIcon.SLEEP, if (asleep && held) "Transfer" else "Settle", TileTone.SLEEP) { send(Command.StartSession(SessionKind.SETTLE)) })
        if (!asleep) {
            add(DockItem(CareIcon.FACE, "Talk & play", TileTone.PLAY) { send(Command.StartSession(SessionKind.PLAY_TALK)) })
            add(DockItem(CareIcon.TUMMY, "Tummy time", TileTone.PLAY) { send(Command.StartSession(SessionKind.TUMMY_TIME)) })
        }
    }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.padding(12.dp)) { TileGrid(items, reducedMotion) }
    }
}

@Composable
private fun HelpDock(view: ChildView, practice: Boolean, reducedMotion: Boolean, send: (Command) -> Unit, onBreak: () -> Unit, onEnd: () -> Unit) {
    val items = buildList {
        add(DockItem(CareIcon.HANDOFF, "Hand off", TileTone.HELP) { send(Command.HandOffNeeds) })
        if (!practice) {
            add(DockItem(CareIcon.BREAK, "Take a break", TileTone.HELP, onClick = onBreak))
            if (view.nightNow) add(DockItem(CareIcon.NIGHT, "Night off", TileTone.HELP) { send(Command.HandOffNight) })
            add(DockItem(CareIcon.END, "End trial", TileTone.HELP, onClick = onEnd))
        }
    }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TileGrid(items, reducedMotion)
            Text(
                "Never shake a baby. If crying gets to you, lay the baby on their back in a clear crib, step away, and ask for help.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
