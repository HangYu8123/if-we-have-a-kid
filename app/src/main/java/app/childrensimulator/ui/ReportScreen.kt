package app.childrensimulator.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.childrensimulator.data.RunUi
import app.childrensimulator.sim.engine.CareReport

/** The Care Report (docs/10 §6.4). It describes the run; it is not a readiness score. */
@Composable
fun ReportScreen(state: AppState, ui: RunUi) {
    var report by remember { mutableStateOf<CareReport?>(null) }
    val tick = ui.log.size / 5
    LaunchedEffect(tick, ui.view?.ended) { report = state.repo.report() }
    val r = report ?: return
    val context = LocalContext.current
    val helper = r.helperName
    val (preRight, preTotal) = state.content.score(r.quizPre, post = false)
    val (postRight, postTotal) = state.content.score(r.quizPost, post = true)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (r.ended) "Care Report" else "Care Report (so far)", style = MaterialTheme.typography.headlineSmall)
        Text(
            "${r.childName} · ${"%.1f".format(r.activeHours)} active hours · ${r.preset.label} · ${r.route.label}" +
                if (r.pausedMin > 0) " · paused ${fmtMinutes(r.pausedMin)}" else "",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        MomentsSection(ui.log, ui.settings?.skinTone ?: 1, r.route)

        Title("Workload")
        SectionCard {
            StatRow("Feeds (you / $helper)", "${r.feedsPlayer} / ${r.feedsHelper}")
            StatRow("Diaper changes (you / $helper)", "${r.changesPlayer} / ${r.changesHelper}")
            StatRow("Settled to sleep (you / $helper)", "${r.settlesPlayer} / ${r.settlesHelper}")
            if (r.bottlesPrepared > 0) StatRow("Bottles you prepared", "${r.bottlesPrepared}")
            StatRow("Diaper checks", "${r.diaperChecks}")
            StatRow("Burps, play and tummy time", "${r.burps + r.playSessions}")
            StatRow("Your hands-on time", fmtMinutes(r.playerHandsOnMin))
            StatRow("$helper's time caring", fmtMinutes(r.helperBusyMin))
        }
        SectionCard(container = MaterialTheme.colorScheme.tertiaryContainer) {
            val realMin = r.referencePlayerMin + r.referenceHelperMin
            Text("In real life", style = MaterialTheme.typography.titleMedium)
            val pct = (app.childrensimulator.sim.engine.PresetRules.of(r.preset).effortScale * 100).toInt()
            Text(
                "At typical real-life durations, the care tasks completed so far would take about ${fmtMinutes(realMin)} " +
                    "(${fmtMinutes(r.referencePlayerMin)} by you, ${fmtMinutes(r.referenceHelperMin)} by $helper). " +
                    "${r.preset.label} runs each care step at $pct% of that time. Your ${fmtMinutes(r.playerHandsOnMin)} of hands-on time above also counts holding, soothing and deciding. " +
                    "The estimate leaves out waiting, cleanup between tasks and overlapping work.",
            )
        }

        Title("Nights and alerts")
        SectionCard {
            StatRow("Night feeds (you / $helper)", "${r.nightFeedsPlayer} / ${r.nightFeedsHelper}")
            StatRow("Care sessions you started in your sleep window", "${r.overnightSessionsPlayer}")
            StatRow("Your hands-on time at night", fmtMinutes(r.playerNightHandsOnMin))
            StatRow("Reminders sent (night)", "${r.alertsPosted} (${r.alertsPostedNight})")
            if (r.alertsAudibleNight > 0) StatRow("Audible night alerts", "${r.alertsAudibleNight}")
            StatRow("Reminders you opened", "${r.alertsOpened}")
            if (r.alertsSuppressed > 0) StatRow("Late reminders skipped (no penalty)", "${r.alertsSuppressed}")
            StatRow("Handoffs and breaks", "${r.handoffs}")
            StatRow("Times $helper stepped in after long crying", "${r.interventions}")
            r.longestGapMin?.let { StatRow("Longest gap between your care sessions", fmtMinutes(it)) }
            Text("The phone doesn't measure your sleep. These numbers describe the simulation only.", style = MaterialTheme.typography.bodySmall)
        }

        Title("The baby's days")
        SectionCard {
            StatRow("Wakes (in your sleep window)", "${r.wakes} (${r.nightWakes})")
            StatRow("Sleep", "%.1f h".format(r.asleepHours))
            StatRow("Fussing / crying", "${fmtMinutes(r.fussingMin)} / ${fmtMinutes(r.cryingMin)}")
            StatRow("Separate needs that came up", "${r.needEpisodes}")
            r.perDay.filter { it.hours >= 6 }.forEach { d ->
                Text(
                    "Day ${d.day}: ${d.feeds} feeds, ${d.changes} changes, %.1f h sleep, ${d.quietWakes} night wakes".format(d.sleepHours),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Title("Learning")
        SectionCard {
            StatRow("Knowledge check before", if (r.quizPre.isEmpty()) "not taken" else "$preRight / $preTotal")
            StatRow("Knowledge check after", if (r.quizPost.isEmpty()) "not taken" else "$postRight / $postTotal")
            if (r.ended && r.quizPost.isEmpty()) Button(onClick = { state.nav.navigate("quiz/post") }) { Text("Take the after-trial check") }
            if (r.tips.isNotEmpty()) {
                Text("Tips you met", fontWeight = FontWeight.SemiBold)
                r.tips.forEach { Text("• $it") }
            }
            if (r.misstepTexts.isNotEmpty()) {
                Text("Choices that needed a safer approach", fontWeight = FontWeight.SemiBold)
                r.misstepTexts.take(6).forEach { Text("• $it") }
                Text("Try the matching drill in the Practice Room.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Title("Reflection")
        SectionCard {
            r.reflections.forEach { Text("• $it") }
        }
        Text("This report describes the simulated run. It is not a readiness rating, and whether to have children is a personal decision.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, exportText(r))
                context.startActivity(Intent.createChooser(send, "Share Care Report"))
            }) { Text("Share as text") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun exportText(r: CareReport): String = buildString {
    appendLine("Children Simulator · Care Report")
    appendLine("${r.childName}, ${"%.1f".format(r.activeHours)} active hours, ${r.preset.label}, ${r.route.label}")
    appendLine("Feeds: ${r.feedsPlayer} by me, ${r.feedsHelper} by ${r.helperName}")
    appendLine("Diaper changes: ${r.changesPlayer} by me, ${r.changesHelper} by ${r.helperName}")
    appendLine("My hands-on time: ${fmtMinutes(r.playerHandsOnMin)}")
    appendLine("Real-life estimate of all care: ${fmtMinutes(r.referencePlayerMin + r.referenceHelperMin)}")
    appendLine("Night feeds: ${r.nightFeedsPlayer} by me, ${r.nightFeedsHelper} by ${r.helperName}")
    appendLine("Reminders: ${r.alertsPosted} (${r.alertsPostedNight} at night)")
    appendLine("Handoffs and breaks: ${r.handoffs}")
    appendLine()
    appendLine("Talk about it:")
    r.reflections.forEach { appendLine("- $it") }
}
