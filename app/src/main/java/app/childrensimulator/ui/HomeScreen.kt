package app.childrensimulator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.childrensimulator.data.RunUi
import app.childrensimulator.sim.engine.ChildView
import app.childrensimulator.sim.engine.Command
import app.childrensimulator.sim.engine.DistressLevel
import app.childrensimulator.sim.engine.Mouth
import app.childrensimulator.sim.engine.PresetRules
import app.childrensimulator.sim.engine.PracticeDrill
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
    val haptics = LocalHapticFeedback.current
    val sound = state.container.crySound
    val lastDiaperCheck = remember(log) { log.lastOrNull { it.type == LogType.CHECK } }
    val diaperFeedback = lastDiaperCheck?.takeIf {
        view.now - it.t < 25_000 && it.wall >= (view.lastChangeWall ?: Long.MIN_VALUE)
    }?.text

    LaunchedEffect(view.level, view.paused, view.ended) {
        sound.update(if (view.paused || view.ended) null else view.level, state.prefs.soundOn, quiet = view.nightNow)
        if (state.prefs.hapticsOn && view.level.ordinal >= DistressLevel.CRYING.ordinal && !view.paused) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    DisposableEffect(Unit) { onDispose { sound.stop() } }

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
        if (!practice) DutyCard(view, settings, send)
        BabyCard(view, settings, reducedMotion, practice)
        ContextLine(view)
        feedback?.let { SectionCard(container = MaterialTheme.colorScheme.tertiaryContainer) { Text(it, color = MaterialTheme.colorScheme.onTertiaryContainer) } }
        CarePanel(view, settings, practiceDrill, diaperFeedback, send)
        if (rules.metersAlways || (rules.hintsOnRequest && !practice) || settings.preset == RealismPreset.REALISTIC || practice) {
            HintsCard(view, rules.metersAlways || practice, showHints) {
                showHints = it
                state.prefs.showHints = it
            }
        }
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
            HelpRow(view, practice, send, onBreak = { breakDialog = true }, onEnd = { endDialog = true })
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

@Composable
private fun CarePanel(view: ChildView, settings: RunSettings, practiceDrill: PracticeDrill?, diaperFeedback: String?, send: (Command) -> Unit) {
    val session = view.session
    if (view.ended) {
        SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
            Text(if (practiceDrill != null) "Practice finished" else "The trial has ended", style = MaterialTheme.typography.titleMedium)
            Text(if (practiceDrill != null) "Leave the Practice Room when you're ready." else "Open the Report tab for your Care Report and reflection prompts.")
        }
    } else if (view.paused) {
        SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
            Text("Paused", style = MaterialTheme.typography.titleMedium)
            Text("Both clocks are frozen and alerts are off. Nothing is missed while paused.")
            Button(onClick = { send(Command.Resume) }) { Text("Resume") }
        }
    } else if (session != null) {
        SessionPanel(session, view, send)
    } else {
        ActionPanel(view, settings, diaperFeedback, practiceDrill == PracticeDrill.SAFE_SLEEP, send)
    }
}

@Composable
private fun TrialHeader(view: ChildView, settings: RunSettings, onSettings: () -> Unit, onPause: () -> Unit, onResume: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${view.name} · ${view.ageText}", style = MaterialTheme.typography.headlineSmall)
            val left = view.trialRemainingMs?.let { "${fmtDuration(it)} left" } ?: ""
            Text("Weekend Trial · $left · ${settings.preset.label}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!view.ended) {
            if (view.paused) TextButton(onClick = onResume) { Text("Resume") } else TextButton(onClick = onPause) { Text("Pause") }
        }
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
    }
}

@Composable
private fun DutyCard(view: ChildView, settings: RunSettings, send: (Command) -> Unit) {
    if (view.ended) return
    val helper = view.onDuty == Caregiver.HELPER
    SectionCard(container = if (helper) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            when {
                view.breakUntilWall != null -> "On a break until ${fmtClock(view.breakUntilWall!!)}. ${settings.helperName} is covering."
                helper -> view.coverageText ?: "${settings.helperName} is on duty."
                view.nightNow -> "You're on duty tonight (${view.sleepWindowLabel})."
                else -> "You're on duty."
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        view.helperBusy?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (view.breakUntilWall != null) TextButton(onClick = { send(Command.EndBreak) }) { Text("End break early") }
        if (view.nightConsentAvailable) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (view.nightNow) "Audible wake-ups tonight" else "Audible wake-ups next sleep window", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (view.nightConsentActive) "On: at most ${PresetRules.NIGHT_AUDIBLE_LIMIT} per night" + (view.nightAttemptsLeft?.let { ", $it left" } ?: "") + ". Then ${settings.helperName} takes over."
                        else "Off. Nights use ordinary notifications.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(checked = view.nightConsentActive, onCheckedChange = { send(Command.NightConsent(it)) })
            }
        }
    }
}

@Composable
private fun BabyCard(view: ChildView, settings: RunSettings, reducedMotion: Boolean, practice: Boolean) {
    val session = view.session
    val sleepSpace = session?.step?.takeIf { it.id == "space" }
    val hazards = sleepSpace?.choices.orEmpty().mapNotNull { choice ->
        choice.id.takeIf { it.startsWith("remove:") }?.removePrefix("remove:")
    }
    val visual = BabyVisual(
        pose = view.pose,
        place = view.place,
        heldBy = view.heldBy,
        techniques = view.techniques,
        skinTone = settings.skinTone,
        hazards = hazards,
        bottle = session?.kind == SessionKind.BOTTLE_FEED && view.pose.mouth == Mouth.SUCKING,
        night = view.nightNow && !view.paused && !view.ended && !practice,
    )
    val description = "${view.name}, ${view.level.label.lowercase()}: ${view.cue}"
    SectionCard(container = MaterialTheme.colorScheme.surfaceContainer) {
        BabyCanvas(visual, description, reducedMotion, Modifier.fillMaxWidth().height(250.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(view.level.label, style = MaterialTheme.typography.labelLarge, color = levelColor(view.level))
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    view.heldBy == Caregiver.PLAYER -> "in your arms"
                    view.heldBy == Caregiver.HELPER -> "with ${settings.helperName}"
                    view.place == Place.BASSINET -> "in the bassinet"
                    view.pose.onTummy -> "on their tummy on the play mat"
                    else -> "on the ${view.place.label}"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(view.cue, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        view.cueDetails.forEach { Text("Also: $it", style = MaterialTheme.typography.bodyMedium) }
    }
    if (sleepSpace != null && view.place != Place.BASSINET) {
        val spaceDescription = if (hazards.isEmpty()) "The clear bassinet"
        else "The bassinet, with: " + hazards.joinToString(", ") { it.replace('_', ' ') }
        SectionCard {
            Text("The bassinet", style = MaterialTheme.typography.titleMedium)
            BabyCanvas(
                visual.copy(place = Place.BASSINET, heldBy = null, techniques = emptySet(), showBaby = false),
                spaceDescription,
                reducedMotion,
                Modifier.fillMaxWidth().height(170.dp),
            )
        }
    }
}

@Composable
private fun levelColor(level: DistressLevel) = when (level) {
    DistressLevel.HARD_CRYING, DistressLevel.CRYING -> MaterialTheme.colorScheme.error
    DistressLevel.FUSSING -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}

@Composable
private fun ContextLine(view: ChildView) {
    val parts = buildList {
        view.lastFeedStartWall?.let { add("Last feed started ${fmtClock(it)} (${fmtAgo(view.wallNow, it)})") }
        view.lastChangeWall?.let { add("Last change ${fmtClock(it)}") }
        add((if (view.sleep == SleepState.ASLEEP) "Asleep " else "Awake ") + fmtDuration(view.wallNow - view.awakeOrAsleepSinceWall))
        if (view.bottleReady && view.session?.kind != SessionKind.BOTTLE_FEED) add("A bottle is ready")
    }
    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun HintsCard(view: ChildView, always: Boolean, show: Boolean, onToggle: (Boolean) -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hints", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (!always) Switch(checked = show, onCheckedChange = onToggle)
        }
        if (always || show) {
            view.meters.forEach { (name, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, modifier = Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { value.toFloat() }, modifier = Modifier.weight(1f).height(8.dp))
                }
            }
            view.hints.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        } else {
            Text("Read the cues first. Turn hints on if you're stuck.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SessionPanel(session: SessionView, view: ChildView, send: (Command) -> Unit) {
    SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
        Text("${session.title} · step ${session.stepNumber} of ${session.stepCount}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        val step = session.step
        if (step != null) {
            Text(step.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (step.instruction.isNotBlank()) Text(step.instruction, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (step.timed) {
                LinearProgressIndicator(progress = { step.progress.toFloat() }, modifier = Modifier.fillMaxWidth().height(10.dp))
                Text(
                    if (session.running) "About ${fmtDuration(step.remainingMs)} left. Hands-on time only runs while the app is open."
                    else "Paused. Resume to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            step.choices.forEach { c ->
                OutlinedButton(
                    onClick = { send(Command.Choose(c.id)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                ) { Text(c.label) }
            }
            step.actionLabel?.let { label ->
                Button(onClick = { send(Command.DoStep) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(label) }
            }
        }
        session.intake?.let { Text("Feed so far: ${(it * 100).toInt()}% of a full feed", style = MaterialTheme.typography.bodyMedium) }
        session.feedback?.let { Text(it, fontWeight = FontWeight.SemiBold) }
        if (session.completedSteps.isNotEmpty()) {
            Text("Done: " + session.completedSteps.takeLast(3).joinToString(" → "), style = MaterialTheme.typography.bodySmall)
        }
        Text("Your time on this: ${fmtDuration(session.handsOnMs)}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!session.running) Button(onClick = { send(Command.ResumeSession) }) { Text("Resume") }
            OutlinedButton(onClick = { send(Command.StopSession) }) { Text("Stop") }
            OutlinedButton(onClick = { send(Command.HandOffSession) }) { Text("Hand off") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionPanel(
    view: ChildView,
    settings: RunSettings,
    diaperFeedback: String?,
    requiresSleepSpaceCheck: Boolean,
    send: (Command) -> Unit,
) {
    val asleep = view.sleep == SleepState.ASLEEP
    val held = view.heldBy == Caregiver.PLAYER
    val diaperSection = remember { BringIntoViewRequester() }
    LaunchedEffect(diaperFeedback) {
        if (diaperFeedback != null) {
            withFrameNanos { }
            diaperSection.bringIntoView()
        }
    }
    SectionCard {
        Text("Feed", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (settings.route == FeedingRoute.FORMULA) {
                if (!view.bottleReady) FilledTonalButton(onClick = { send(Command.StartSession(SessionKind.BOTTLE_PREP)) }) { Text("Prepare a bottle") }
                else Button(onClick = { send(Command.StartSession(SessionKind.BOTTLE_FEED)) }) { Text("Bottle feed") }
            } else {
                Button(onClick = { send(Command.StartSession(SessionKind.BREASTFEED)) }) { Text("Breast/chestfeed") }
            }
            FilledTonalButton(onClick = { send(Command.StartSession(SessionKind.BURP)) }) { Text("Burp") }
        }
        Column(Modifier.bringIntoViewRequester(diaperSection), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Diaper", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { send(Command.CheckDiaper) }) { Text("Check diaper") }
                FilledTonalButton(onClick = { send(Command.StartSession(SessionKind.DIAPER_CHANGE)) }) { Text("Change diaper") }
            }
            diaperFeedback?.let {
                Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        Text("Comfort", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (held) {
                if (!requiresSleepSpaceCheck) {
                    FilledTonalButton(onClick = { send(Command.PutDown(Place.BASSINET)) }) { Text("Lay in bassinet") }
                }
                if (!asleep) FilledTonalButton(onClick = { send(Command.PutDown(Place.PLAY_MAT)) }) { Text("Lay on play mat") }
            } else {
                Button(onClick = { send(Command.PickUp) }) { Text("Pick up and hold") }
            }
            for (t in Technique.entries) {
                val on = t in view.techniques
                FilterChip(selected = on, onClick = { send(Command.SetTechnique(t, !on)) }, label = { Text(t.label) })
            }
        }
        Text("Sleep and play", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { send(Command.StartSession(SessionKind.SETTLE)) }) { Text(if (asleep && held) "Transfer to bassinet" else "Settle to sleep") }
            if (!asleep) {
                FilledTonalButton(onClick = { send(Command.StartSession(SessionKind.PLAY_TALK)) }) { Text("Talk and play") }
                FilledTonalButton(onClick = { send(Command.StartSession(SessionKind.TUMMY_TIME)) }) { Text("Tummy time") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HelpRow(view: ChildView, practice: Boolean, send: (Command) -> Unit, onBreak: () -> Unit, onEnd: () -> Unit) {
    SectionCard(container = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text("Need a hand?", style = MaterialTheme.typography.titleMedium)
        Text("Never shake a baby. If crying gets to you, lay the baby on their back in a clear crib, step away, and ask for help.", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { send(Command.HandOffNeeds) }) { Text("Hand off current care") }
            if (!practice) {
                OutlinedButton(onClick = onBreak) { Text("Take a break") }
                if (view.nightNow) OutlinedButton(onClick = { send(Command.HandOffNight) }) { Text("Hand off the night") }
                TextButton(onClick = onEnd, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("End trial") }
            }
        }
    }
}
