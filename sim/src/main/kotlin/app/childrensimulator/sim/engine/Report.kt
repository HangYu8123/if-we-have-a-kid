package app.childrensimulator.sim.engine

import app.childrensimulator.sim.DAY
import app.childrensimulator.sim.MINUTE
import app.childrensimulator.sim.model.*

/** The Care Report (docs/10 §6.4): describes the run without a readiness rating. */
data class CareReport(
    val childName: String,
    val helperName: String,
    val preset: RealismPreset,
    val route: FeedingRoute,
    val activeHours: Double,
    val ended: Boolean,
    val endReason: String?,
    val feedsPlayer: Int,
    val feedsHelper: Int,
    val changesPlayer: Int,
    val changesHelper: Int,
    val settlesPlayer: Int,
    val settlesHelper: Int,
    val bottlesPrepared: Int,
    val burps: Int,
    val playSessions: Int,
    val diaperChecks: Int,
    val playerHandsOnMin: Double,
    val playerNightHandsOnMin: Double,
    val helperBusyMin: Double,
    val referencePlayerMin: Double,
    val referenceHelperMin: Double,
    val overnightSessionsPlayer: Int,
    val nightFeedsPlayer: Int,
    val nightFeedsHelper: Int,
    val alertsPosted: Int,
    val alertsPostedNight: Int,
    val alertsAudibleNight: Int,
    val alertsOpened: Int,
    val alertsSuppressed: Int,
    val handoffs: Int,
    val interventions: Int,
    val breaks: Int,
    val longestGapMin: Double?,
    val cryingMin: Double,
    val fussingMin: Double,
    val asleepHours: Double,
    val needEpisodes: Int,
    val wakes: Int,
    val nightWakes: Int,
    val missteps: Int,
    val tips: List<String>,
    val misstepTexts: List<String>,
    val perDay: List<DayStats>,
    val quizPre: Map<String, String>,
    val quizPost: Map<String, String>,
    val reflections: List<String>,
    val pausedMin: Double,
)

data class DayStats(val day: Int, val hours: Double, val feeds: Int, val changes: Int, val sleepHours: Double, val quietWakes: Int, val needEpisodes: Int)

internal fun Ctx.buildReport(): CareReport {
    val s = w.stats
    val active = (now - w.clock.startLogical).toDouble()
    val playerStarts = w.log.filter { it.type == LogType.SESSION_STARTED && it.who == Caregiver.PLAYER }.map { it.t }
    val longestGap = if (playerStarts.size >= 6) playerStarts.zipWithNext { a, b -> b - a }.maxOrNull()?.let { it / MINUTE.toDouble() } else null
    return CareReport(
        childName = settings.childName,
        helperName = settings.helperName,
        preset = settings.preset,
        route = settings.route,
        activeHours = active / 3_600_000.0,
        ended = w.ended,
        endReason = w.endReason,
        feedsPlayer = s.count("feed.PLAYER"),
        feedsHelper = s.count("feed.HELPER"),
        changesPlayer = s.count("change.PLAYER"),
        changesHelper = s.count("change.HELPER"),
        settlesPlayer = s.count("settle.PLAYER"),
        settlesHelper = s.count("settle.HELPER"),
        bottlesPrepared = s.count("prep.PLAYER"),
        burps = s.count("burp.PLAYER"),
        playSessions = s.count("play.PLAYER") + s.count("tummy.PLAYER"),
        diaperChecks = s.count("check.PLAYER"),
        playerHandsOnMin = s.duration("handsOn.PLAYER") / MINUTE,
        playerNightHandsOnMin = s.duration("handsOn.night.PLAYER") / MINUTE,
        helperBusyMin = s.duration("busy.HELPER") / MINUTE,
        referencePlayerMin = s.duration("reference.PLAYER") / MINUTE,
        referenceHelperMin = s.duration("reference.HELPER") / MINUTE,
        overnightSessionsPlayer = s.count("sessions.night.PLAYER"),
        nightFeedsPlayer = s.count("feed.night.PLAYER"),
        nightFeedsHelper = s.count("feed.night.HELPER"),
        alertsPosted = s.count("alert.posted"),
        alertsPostedNight = s.count("alert.posted.night"),
        alertsAudibleNight = s.count("alert.posted.${Channels.NIGHT}"),
        alertsOpened = s.count("alert.opened"),
        alertsSuppressed = s.count("alert.suppressed"),
        handoffs = s.count("handoff"),
        interventions = s.count("intervention"),
        breaks = s.count("break"),
        longestGapMin = longestGap,
        cryingMin = s.duration("crying") / MINUTE,
        fussingMin = s.duration("fussing") / MINUTE,
        asleepHours = s.duration("asleep") / 3_600_000.0,
        needEpisodes = s.count("needs"),
        wakes = s.count("wake"),
        nightWakes = s.count("wake.night"),
        missteps = s.count("misstep"),
        tips = w.log.filter { it.type == LogType.COACHING }.map { it.text.removePrefix("Tip: ") }.distinct(),
        misstepTexts = w.log.filter { it.type == LogType.MISSTEP }.map { it.text }.distinct(),
        perDay = perDayStats(),
        quizPre = w.quiz.pre.toMap(),
        quizPost = w.quiz.post.toMap(),
        reflections = listOf(
            "Which moment interrupted your plans the most, and what would have helped?",
            "Who would take the night feeds in your household, and how would you split them?",
            "Who are three people you could call for help at 2 AM?",
            "How did you react to long crying? What is your plan for a safe break?",
        ),
        pausedMin = w.clock.totalPausedMs / MINUTE.toDouble(),
    )
}

/** Per-24 h counts from the event log, used by the report and the balance harness (docs/12 Phase 1 exit criteria). */
internal fun Ctx.perDayStats(): List<DayStats> {
    val start = w.clock.startLogical
    val days = ((now - start) / DAY).toInt() + 1
    val result = mutableListOf<DayStats>()
    // Sleep intervals from ASLEEP/WAKE pairs.
    val sleeps = mutableListOf<Pair<Long, Long>>()
    var sleepStart: Long? = if (w.log.firstOrNull()?.let { false } == true) start else null
    for (e in w.log) {
        if (e.type == LogType.ASLEEP && sleepStart == null) sleepStart = e.t
        if (e.type == LogType.WAKE && sleepStart != null) {
            sleeps += sleepStart!! to e.t
            sleepStart = null
        }
    }
    sleepStart?.let { sleeps += it to now }
    for (d in 0 until days) {
        val a = start + d * DAY
        val b = minOf(now, a + DAY)
        if (b <= a) continue
        val inDay = w.log.filter { it.t in a until b }
        val sleepMs = sleeps.sumOf { (x, y) -> maxOf(0L, minOf(y, b) - maxOf(x, a)) }
        result += DayStats(
            day = d + 1,
            hours = (b - a) / 3_600_000.0,
            feeds = inDay.count { it.type == LogType.CARE_DONE && it.tag == "feed" },
            changes = inDay.count { it.type == LogType.CARE_DONE && it.tag == "change" },
            sleepHours = sleepMs / 3_600_000.0,
            quietWakes = inDay.count { it.type == LogType.WAKE && it.tag?.startsWith("wake:") == true && inReferenceNight(it.t) },
            needEpisodes = inDay.count { it.type == LogType.NEED_OPENED },
        )
    }
    return result
}
