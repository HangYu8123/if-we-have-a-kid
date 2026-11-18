package app.childrensimulator.sim

import app.childrensimulator.sim.engine.DayStats
import app.childrensimulator.sim.engine.planNextWake
import app.childrensimulator.sim.engine.reconcileAlerts
import app.childrensimulator.sim.model.FeedingRoute
import app.childrensimulator.sim.model.RealismPreset
import app.childrensimulator.sim.model.World
import app.childrensimulator.sim.profile.Range
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Balance harness (docs/12 Phase 1 exit criteria, docs/03 §12): seeded 24-hour periods after a warm-up day,
 * played by a responsive bot, compared with the age profile's balance bands.
 */
class BalanceTest {
    private val engine = TestSupport.engine
    private val report = StringBuilder()

    private data class Sample(val seed: Long, val day: DayStats, val quietFeeds: Int)

    private fun samples(route: FeedingRoute, ageDays: Double, seeds: LongRange, days: Int = 2): List<Sample> {
        val out = mutableListOf<Sample>()
        for (seed in seeds) {
            val w: World = TestSupport.trial(seed, preset = RealismPreset.REALISTIC, route = route, ageDays = ageDays, fixture = false, startHour = 12, hours = 24 * (days + 1) + 1)
            CareBot(engine, w).run(24.0 * (days + 1))
            val report = engine.report(w)
            val quiet = w.log.filter { it.type == app.childrensimulator.sim.model.LogType.CARE_DONE && it.tag == "feed" }
            for (d in report.perDay.drop(1).take(days)) {
                val a = w.clock.startLogical + (d.day - 1) * DAY
                val qf = quiet.count { it.t in a until a + DAY && isQuiet(it.wall) }
                out += Sample(seed, d, qf)
            }
        }
        return out
    }

    private fun isQuiet(wall: Long): Boolean {
        val h = java.time.Instant.ofEpochMilli(wall).atZone(java.time.ZoneId.of(TestSupport.ZONE)).hour
        return h >= 23 || h < 7
    }

    private fun share(values: List<Double>, band: Range, tolerance: Double = 0.0): Double =
        values.count { it >= band.min - tolerance && it <= band.max + tolerance }.toDouble() / values.size

    private fun line(name: String, values: List<Double>, band: Range, tolerance: Double = 0.0): Double {
        val sorted = values.sorted()
        val s = share(values, band, tolerance)
        report.appendLine(
            "  %-28s band %-12s mean %6.2f  median %6.2f  min %6.2f  max %6.2f  in band %5.1f%%".format(
                name, band.toString(), values.average(), sorted[sorted.size / 2], sorted.first(), sorted.last(), s * 100,
            ),
        )
        return s
    }

    private fun writeReport() {
        val path = System.getProperty("balance.report") ?: return
        File(path).apply { parentFile.mkdirs() }.appendText(report.toString())
        println(report)
    }

    @Test
    fun day21FormulaDaysFitTheProfile() = checkDay21(FeedingRoute.FORMULA)

    @Test
    fun day21BreastDaysFitTheProfile() = checkDay21(FeedingRoute.BREAST)

    private fun checkDay21(route: FeedingRoute) {
        val p = engine.resolver.resolve(21.0)
        val s = samples(route, 21.0, 1L..24L)
        report.appendLine("Day-21 profile, ${route.name.lowercase()} route, ${s.size} sampled days (Realistic, responsive bot):")
        val feeds = line("milk feeds / 24 h", s.map { it.day.feeds.toDouble() }, p.milkFeeds(route.profileKey)!!, 0.5)
        val changes = line("diaper changes / 24 h", s.map { it.day.changes.toDouble() }, p.changesPerDay!!, 0.5)
        val sleep = line("sleep hours / 24 h", s.map { it.day.sleepHours }, p.totalSleepHours!!, 0.5)
        val quiet = line("quiet-phase feed starts", s.map { it.quietFeeds.toDouble() }, p.quietPhaseMilkStartsPerDay!!, 0.5)
        line("overnight wakes 23-07", s.map { it.day.quietWakes.toDouble() }, p.overnightWakeGroupsPerNight!!, 0.5)
        line("need episodes / 24 h", s.map { it.day.needEpisodes.toDouble() }, p.needEpisodesPerDay!!, 0.5)
        writeReport()
        assertTrue(feeds >= 0.8, "feeds in band: $feeds")
        assertTrue(changes >= 0.7, "changes in band: $changes")
        assertTrue(sleep >= 0.7, "sleep in band: $sleep")
        assertTrue(quiet >= 0.5, "quiet-phase feeds in band: $quiet")
    }

    /** Alert groups per 24 h when the player stays away (docs/03 §12.1, docs/05 §2): Gentle is capped at 8. */
    @Test
    fun alertVolumeByRealismLevel() {
        report.appendLine("Alert groups posted over 24 h with the app closed and no response (helper steps in after the cap):")
        for (preset in RealismPreset.entries) {
            val counts = (1L..6L).map { seed ->
                val w = TestSupport.trial(seed, preset = preset, startHour = 12, hours = 30)
                if (preset == RealismPreset.EXTREME) engine.apply(w, app.childrensimulator.sim.engine.Command.NightConsent(true), w.clock.now + w.clock.delta)
                app.childrensimulator.sim.engine.Command.SetPresence(app.childrensimulator.sim.model.Presence.BACKGROUND).let { engine.apply(w, it, w.clock.now + w.clock.delta) }
                val end = w.clock.now + w.clock.delta + 24 * HOUR
                while (true) {
                    val wake = engine.planNextWake(w) ?: break
                    if (wake.atWall > end) break
                    engine.reconcileAlerts(w, wake.atWall, foreground = false)
                }
                w.stats.count("alert.posted") to w.stats.count("alert.posted.night")
            }
            val total = counts.map { it.first }
            report.appendLine("  %-12s posted/24 h: mean %.1f (min %d, max %d); in the sleep window: mean %.1f".format(preset.label, total.average(), total.min(), total.max(), counts.map { it.second }.average()))
            if (preset == RealismPreset.GENTLE) assertTrue(total.all { it <= 8 }, "Gentle caps background alerts at 8 per 24 h: $total")
        }
        writeReport()
    }

    @Test
    fun threeMonthBreastDayCanReachEightFeeds() {
        val s = samples(FeedingRoute.BREAST, 91.0, 1L..30L, days = 1)
        val p = engine.resolver.resolve(91.0)
        report.appendLine("3-month profile, breast route, ${s.size} sampled days:")
        line("milk feeds / 24 h", s.map { it.day.feeds.toDouble() }, p.milkFeeds("breast")!!, 0.5)
        writeReport()
        assertTrue(s.any { it.day.feeds >= 8 }, "a seeded day reaches eight feeds")
    }
}
