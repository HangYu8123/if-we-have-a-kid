package app.childrensimulator.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.childrensimulator.BuildConfig
import app.childrensimulator.data.RunUi
import app.childrensimulator.sim.HOUR
import app.childrensimulator.sim.MINUTE
import app.childrensimulator.sim.TimeWindow
import app.childrensimulator.sim.engine.Channels
import app.childrensimulator.sim.engine.Command
import app.childrensimulator.sim.model.RealismPreset
import kotlinx.coroutines.launch

/** Settings and run controls (docs/10 §5 "Settings and report", docs/05 §4 permission states). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: AppState, ui: RunUi, onReducedMotion: (Boolean) -> Unit, onBack: () -> Unit) {
    val settings = ui.settings ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = state.prefs
    var sound by remember { mutableStateOf(prefs.soundOn) }
    var haptics by remember { mutableStateOf(prefs.hapticsOn) }
    var motion by remember { mutableStateOf(prefs.reducedMotion) }
    var helperName by remember { mutableStateOf(settings.helperName) }
    var deleteDialog by remember { mutableStateOf(false) }
    val notifier = state.container.notifier
    fun update(s: app.childrensimulator.sim.model.RunSettings) = state.repo.send(Command.UpdateSettings(s))

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Title("Realism")
        SectionCard {
            Text("Changes apply to new care sessions and future alerts. Care already under way keeps its length.", style = MaterialTheme.typography.bodyMedium)
            for (p in RealismPreset.entries) {
                Row(
                    Modifier.fillMaxWidth().selectable(selected = settings.preset == p, onClick = { update(settings.copy(preset = p, presencePrompts = p == RealismPreset.EXTREME)) }, role = Role.RadioButton),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.preset == p, onClick = null)
                    Text(p.label)
                }
            }
            if (settings.preset == RealismPreset.STANDARD) {
                ToggleRow("Take one night wake myself", settings.takeOneNightWake) { update(settings.copy(takeOneNightWake = it)) }
            }
            if (settings.preset == RealismPreset.REALISTIC || settings.preset == RealismPreset.EXTREME) {
                ToggleRow("Presence check-ins during long care", settings.presencePrompts) { update(settings.copy(presencePrompts = it)) }
            }
        }

        Title("Your sleep window and helper")
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sleep window ${settings.sleepWindow.label()}", modifier = Modifier.weight(1f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { update(settings.copy(sleepWindow = TimeWindow((settings.sleepWindow.startMinute + 1410) % 1440, settings.sleepWindow.endMinute))) }) { Text("Start −30") }
                OutlinedButton(onClick = { update(settings.copy(sleepWindow = TimeWindow((settings.sleepWindow.startMinute + 30) % 1440, settings.sleepWindow.endMinute))) }) { Text("Start +30") }
                OutlinedButton(onClick = { update(settings.copy(sleepWindow = TimeWindow(settings.sleepWindow.startMinute, (settings.sleepWindow.endMinute + 1410) % 1440))) }) { Text("End −30") }
                OutlinedButton(onClick = { update(settings.copy(sleepWindow = TimeWindow(settings.sleepWindow.startMinute, (settings.sleepWindow.endMinute + 30) % 1440))) }) { Text("End +30") }
            }
            OutlinedTextField(value = helperName, onValueChange = { helperName = it.take(16) }, label = { Text("Helper's name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (helperName.isNotBlank() && helperName != settings.helperName) Button(onClick = { update(settings.copy(helperName = helperName.trim())) }) { Text("Save name") }
        }

        Title("Protected time")
        SectionCard {
            val pw = settings.protectedWindow
            Text("A daily window when you want silence, such as work hours. ${settings.helperName} covers it (docs/05 §7).", style = MaterialTheme.typography.bodyMedium)
            ToggleRow("Use protected time", pw != null) { on -> update(settings.copy(protectedWindow = if (on) TimeWindow(9 * 60, 12 * 60) else null)) }
            if (pw != null) {
                Text("Protected ${pw.label()}")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { update(settings.copy(protectedWindow = TimeWindow((pw.startMinute + 1410) % 1440, pw.endMinute))) }) { Text("Start −30") }
                    OutlinedButton(onClick = { update(settings.copy(protectedWindow = TimeWindow((pw.startMinute + 30) % 1440, pw.endMinute))) }) { Text("Start +30") }
                    OutlinedButton(onClick = { update(settings.copy(protectedWindow = TimeWindow(pw.startMinute, (pw.endMinute + 1410) % 1440))) }) { Text("End −30") }
                    OutlinedButton(onClick = { update(settings.copy(protectedWindow = TimeWindow(pw.startMinute, (pw.endMinute + 30) % 1440))) }) { Text("End +30") }
                }
            }
        }

        Title("Notifications")
        SectionCard {
            val enabled = notifier.notificationsEnabled()
            Text(
                when {
                    !enabled -> "Notifications are off for this app. You'll only see cues while the app is open; ${settings.helperName} can cover nights."
                    !notifier.channelEnabled(Channels.CARE) -> "The \"Baby care\" channel is blocked."
                    else -> "Notifications are on."
                },
            )
            if (settings.preset == RealismPreset.EXTREME) {
                Text(if (notifier.channelEnabled(Channels.NIGHT)) "Night wake-up channel: on." else "Night wake-up channel: blocked.", style = MaterialTheme.typography.bodyMedium)
                if (Build.VERSION.SDK_INT >= 31) {
                    Text(if (state.repo.canScheduleExact()) "Exact timing for night wake-ups: allowed." else "Exact timing: not allowed, so night alerts are best effort and may be late.", style = MaterialTheme.typography.bodyMedium)
                    if (!state.repo.canScheduleExact()) OutlinedButton(onClick = {
                        prefs.exactNightAlarms = true
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }) { Text("Allow exact timing") }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { notifier.test(Channels.CARE) }) { Text("Test reminder") }
                if (settings.preset == RealismPreset.EXTREME) OutlinedButton(onClick = { notifier.test(Channels.NIGHT) }) { Text("Test night sound") }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text("Android settings") }
            }
            ui.scheduling?.let { Text("Next check: $it", style = MaterialTheme.typography.bodySmall) }
        }

        Title("In the app")
        SectionCard {
            ToggleRow("Crying sound", sound) { sound = it; prefs.soundOn = it; if (!it) state.container.crySound.stop() }
            ToggleRow("Vibration with crying", haptics) { haptics = it; prefs.hapticsOn = it }
            ToggleRow("Reduce motion", motion) { motion = it; prefs.reducedMotion = it; onReducedMotion(it) }
        }

        Title("This run")
        SectionCard {
            val view = ui.view
            if (view != null && !view.ended) {
                if (view.paused) Button(onClick = { state.repo.send(Command.Resume) }) { Text("Resume") } else OutlinedButton(onClick = { state.repo.send(Command.Pause) }) { Text("Pause (freezes both clocks)") }
                OutlinedButton(onClick = { state.repo.send(Command.EndRun) }) { Text("End the trial now") }
            }
            OutlinedButton(onClick = { deleteDialog = true }) { Text("Delete this run and start over") }
        }

        if (BuildConfig.DEBUG) {
            Title("Testing tools (debug build)")
            SectionCard {
                Text("Skip the simulation forward to try nights and later events without waiting. Only for testing.", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((label, ms) in listOf("+15 min" to 15 * MINUTE, "+1 h" to HOUR, "+4 h" to 4 * HOUR)) {
                        OutlinedButton(onClick = { state.repo.send(Command.DebugSkip(ms)) }) { Text(label) }
                    }
                }
            }
        }

        Title("About")
        Text(
            "Children Simulator v${BuildConfig.VERSION_NAME}. A learning simulation for adults. Lesson and health content is a draft summary of public guidance (CDC, AAP, NIH) and has not been reviewed by a pediatric professional. It is not medical advice. Runs stay on this phone; no account, no ads.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    deleteDialog = false
                    scope.launch {
                        state.repo.deleteRun()
                        state.nav.navigate("onboarding") { popUpTo(0) }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Cancel") } },
            title = { Text("Delete this run?") },
            text = { Text("The care log and report for this run will be removed from this phone, and scheduled reminders are cancelled.") },
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
