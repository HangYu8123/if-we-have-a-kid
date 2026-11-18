package app.childrensimulator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.childrensimulator.data.RunUi
import app.childrensimulator.sim.model.Caregiver
import app.childrensimulator.sim.model.LogEntry
import app.childrensimulator.sim.model.LogType

private enum class LogFilter(val label: String, val types: Set<LogType>?) {
    ALL("All", null),
    CARE("Care", setOf(LogType.CARE_DONE, LogType.SESSION_STARTED, LogType.SESSION_ENDED, LogType.CHECK, LogType.COMFORT, LogType.ASLEEP, LogType.WAKE)),
    CUES("Cues", setOf(LogType.NEED_OPENED, LogType.NEED_PHASE, LogType.NEED_RESOLVED, LogType.DEBRIEF)),
    HELP("Helper", setOf(LogType.HELPER, LogType.HANDOFF, LogType.COVERAGE, LogType.INTERVENTION)),
    ALERTS("Alerts", setOf(LogType.ALERT, LogType.PAUSE, LogType.RESUME)),
    LEARN("Learning", setOf(LogType.COACHING, LogType.MISSTEP, LogType.DEBRIEF)),
}

/** The care log (docs/10 §5): feeds, changes, settling, breaks, alert statuses, choices and helper notes. */
@Composable
fun LogScreen(state: AppState, ui: RunUi) {
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    val helper = ui.settings?.helperName ?: "Helper"
    val entries = remember(ui.log, filter) {
        ui.log.asReversed().filter { e -> filter.types == null || e.type in filter.types!! }.filter { it.type != LogType.STEP }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Care log", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LogFilter.entries.take(4).forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }) }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LogFilter.entries.drop(4).forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }) }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(entries, key = { it.seq }) { e -> LogRow(e, helper) }
        }
    }
}

@Composable
private fun LogRow(e: LogEntry, helper: String) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row {
            Text(fmtClock(e.wall), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(56.dp))
            Column(Modifier.weight(1f)) {
                Text(e.text, style = MaterialTheme.typography.bodyMedium, fontWeight = if (e.type in setOf(LogType.CARE_DONE, LogType.INTERVENTION, LogType.HANDOFF, LogType.DEBRIEF)) FontWeight.SemiBold else FontWeight.Normal)
                val tags = buildList {
                    when (e.who) {
                        Caregiver.PLAYER -> add("You")
                        Caregiver.HELPER -> add(helper)
                        null -> {}
                    }
                    if (e.night) add("night")
                    e.lesson?.let { add(it) }
                }
                if (tags.isNotEmpty()) Text(tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}
