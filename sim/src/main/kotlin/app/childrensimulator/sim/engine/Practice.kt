package app.childrensimulator.sim.engine

import app.childrensimulator.sim.HOUR
import app.childrensimulator.sim.MINUTE
import app.childrensimulator.sim.TimeWindow
import app.childrensimulator.sim.model.*

/** Practice Room drills: no background reminders, local clock only (docs/10 §2). */
enum class PracticeDrill(val id: String, val title: String, val summary: String, val lessons: List<String>, val forceRoute: FeedingRoute? = null) {
    ONE_WAKE("one_wake_two_needs", "One wake, two needs", "Hungry and a dirty diaper at once. Check, change, feed and settle safely.", listOf("L-FEED-01", "L-HYG-01", "L-SLEEP-01")),
    BOTTLE("bottle", "Bottle prep and paced feeding", "Prepare formula safely, hold the baby, pace the feed and stop at fullness.", listOf("L-FEED-03", "L-FEED-04"), FeedingRoute.FORMULA),
    BREAST("breast", "Breast/chestfeeding basics", "Position, latch and follow fullness cues.", listOf("L-FEED-02"), FeedingRoute.BREAST),
    DIAPER("diaper", "Diaper change", "Gather supplies, keep a hand on the baby, clean front to back, fold below the cord.", listOf("L-HYG-01", "L-SAFE-05")),
    SAFE_SLEEP("safe_sleep", "Safe sleep transfer", "The baby fell asleep in your arms. Clear the bassinet and lay them on their back.", listOf("L-SLEEP-01")),
    FUSSY("fussy", "A fussy, tired baby", "Try soothing techniques and see what helps. Some fussing continues anyway.", listOf("L-CRY-01", "L-CRY-02", "L-CRY-04")),
}

fun SimEngine.newPractice(drill: PracticeDrill, childName: String, route: FeedingRoute, skinTone: Int, zoneId: String, nowWall: Long, seed: Long): World {
    val settings = RunSettings(
        childName = childName, route = drill.forceRoute ?: route, preset = RealismPreset.GENTLE,
        sleepWindow = TimeWindow(0, 0), helperName = "Helper", helperRelation = "helper", zoneId = zoneId, skinTone = skinTone,
    )
    val w = createWorld(seed, RunMode.PRACTICE, settings, nowWall, 21.0, nowWall + 3 * HOUR)
    w.practice = PracticeState(drill.id)
    val c = Ctx(this, w)
    when (drill) {
        PracticeDrill.ONE_WAKE -> c.openingFixture(hunger = true, dirty = true)
        PracticeDrill.BOTTLE, PracticeDrill.BREAST -> c.openingFixture(hunger = true, dirty = false)
        PracticeDrill.DIAPER -> c.openingFixture(hunger = false, dirty = true)
        PracticeDrill.SAFE_SLEEP -> {
            c.openingFixture(hunger = false, dirty = false)
            w.child.sleep = SleepState.ASLEEP
            w.child.sleepSince = nowWall - 12 * MINUTE
            w.child.currentWakeId = null
            w.child.heldBy = Caregiver.PLAYER
            w.child.holdStartedAt = nowWall - 15 * MINUTE
            w.child.place = Place.ARMS
            w.child.napEndsAt = nowWall + 2 * HOUR
        }
        PracticeDrill.FUSSY -> {
            c.openingFixture(hunger = false, dirty = false, tired = true)
            w.scenario.evenings += EveningPlan(EveningKind.FUSSY_EVENING, nowWall - MINUTE, nowWall + 25 * MINUTE, 0.9, started = true)
        }
    }
    c.log(LogType.RUN_STARTED, "Practice: ${drill.title}.")
    c.afterChange()
    return w
}

internal fun Ctx.checkPracticeObjective() {
    val p = w.practice ?: return
    if (p.objectiveMet || w.session != null) return
    val s = w.stats
    val met = when (p.drillId) {
        PracticeDrill.ONE_WAKE.id -> s.count("feed.PLAYER") >= 1 && s.count("change.PLAYER") >= 1
        PracticeDrill.BOTTLE.id, PracticeDrill.BREAST.id -> s.count("feed.PLAYER") >= 1
        PracticeDrill.DIAPER.id -> s.count("change.PLAYER") >= 1
        PracticeDrill.SAFE_SLEEP.id -> p.guidedSleepTransfer &&
            child.sleep == SleepState.ASLEEP && child.place == Place.BASSINET && child.heldBy == null
        PracticeDrill.FUSSY.id -> child.sleep == SleepState.ASLEEP
        else -> false
    }
    if (!met) return
    p.objectiveMet = true
    val minutes = (now - w.clock.startLogical) / MINUTE
    val slips = s.count("misstep")
    p.debrief = buildString {
        append("Done in $minutes min. ")
        append(if (slips == 0) "No unsafe choices." else "$slips choice${if (slips > 1) "s" else ""} needed a safer approach: review the tips.")
    }
    log(LogType.NOTE, "Practice complete. ${p.debrief}")
}
