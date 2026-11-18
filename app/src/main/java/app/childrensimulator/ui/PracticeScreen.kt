package app.childrensimulator.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.childrensimulator.data.RunUi
import app.childrensimulator.sim.engine.DistressLevel
import app.childrensimulator.sim.engine.PracticeDrill
import app.childrensimulator.sim.engine.PresetRules
import app.childrensimulator.sim.model.RealismPreset
import app.childrensimulator.sim.model.RunSettings

/** Practice Room drill (docs/10 §2): same care surface, local clock, no reminders. */
@Composable
fun PracticeScreen(state: AppState, ui: RunUi, drill: PracticeDrill, reducedMotion: Boolean, onBack: () -> Unit) {
    val practice by state.repo.practice.collectAsStateWithLifecycle()
    val p = practice?.takeIf { it.drill == drill } ?: return
    val trialView = ui.view
    val settings = RunSettings(
        childName = p.view.name, route = drill.forceRoute ?: ui.settings?.route ?: app.childrensimulator.sim.model.FeedingRoute.FORMULA,
        preset = RealismPreset.GENTLE, sleepWindow = app.childrensimulator.sim.TimeWindow(0, 0), helperName = "Helper", helperRelation = "helper",
        zoneId = java.time.ZoneId.systemDefault().id, skinTone = ui.settings?.skinTone ?: 1,
    )
    Scaffold(modifier = Modifier.fillMaxSize(), snackbarHost = { SnackbarHost(state.snackbar) }) { padding ->
        CareView(
            state = state,
            view = p.view,
            settings = settings,
            reducedMotion = reducedMotion,
            feedback = null,
            log = p.log,
            send = { state.repo.practiceCommand(it) },
            practiceDrill = drill,
            modifier = Modifier.padding(padding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Practice · ${drill.title}", style = MaterialTheme.typography.titleLarge)
                    Text(drill.summary, style = MaterialTheme.typography.bodyMedium)
                    Text("Care steps run at ${(PresetRules.of(RealismPreset.GENTLE).effortScale * 100).toInt()}% speed. Lessons: ${drill.lessons.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onBack) { Text("Leave") }
            }
            if (trialView != null && !trialView.ended && trialView.level.ordinal >= DistressLevel.FUSSING.ordinal && trialView.onDuty == app.childrensimulator.sim.model.Caregiver.PLAYER) {
                SectionCard(container = MaterialTheme.colorScheme.errorContainer) {
                    Text("Meanwhile, in your trial: ${trialView.name} is ${trialView.level.label.lowercase()}.")
                    TextButton(onClick = onBack) { Text("Go back to ${trialView.name}") }
                }
            }
            if (p.practice.objectiveMet) {
                SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
                    Text("Practice complete", style = MaterialTheme.typography.titleMedium)
                    Text(p.practice.debrief ?: "")
                    TextButton(onClick = onBack) { Text("Back to Learn") }
                }
            }
        }
    }
}
