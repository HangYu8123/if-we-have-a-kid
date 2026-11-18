package app.childrensimulator.sim

import app.childrensimulator.sim.engine.Channels
import app.childrensimulator.sim.engine.Command
import app.childrensimulator.sim.engine.NotificationOp
import app.childrensimulator.sim.engine.SimEngine
import app.childrensimulator.sim.engine.planNextWake
import app.childrensimulator.sim.engine.reconcileAlerts
import app.childrensimulator.sim.model.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineTest {
    private val engine = TestSupport.engine

    private fun wall(w: World) = w.clock.now + w.clock.delta

    /** Steps a session through its safe choices until it ends. */
    private fun completeSession(w: World, maxSteps: Int = 5000) {
        var guard = 0
        while (w.session != null && guard++ < maxSteps) {
            val step = w.session!!.currentStep!!
            when {
                step.choices.isNotEmpty() -> {
                    val c = step.choices.firstOrNull { it.id.startsWith("remove:") } ?: step.choices.first { it.verdict == "ok" && it.id != "again" && it.id != "longer" }
                    engine.apply(w, Command.Choose(c.id), wall(w))
                }
                step.actionLabel != null -> {
                    val r = engine.apply(w, Command.DoStep, wall(w))
                    if (r.message?.contains("too hard") == true) {
                        engine.apply(w, Command.SetTechnique(Technique.ROCK, true), wall(w))
                        engine.advance(w, w.clock.now + 2 * MINUTE)
                    }
                }
                else -> engine.advance(w, w.clock.now + 5 * SECOND)
            }
        }
        assertNull(w.session, "session should finish")
    }

    // ------------------------------------------------------------------ Phase 0: determinism

    @Test
    fun sameSeedAndCommandsReplayToTheSameLog() {
        val a = TestSupport.trial(42)
        val b = TestSupport.trial(42)
        CareBot(engine, a).run(14.0)
        CareBot(engine, b).run(14.0)
        assertTrue(a.log.size > 50)
        assertEquals(a.log, b.log)
        assertEquals(SimEngine.encode(a), SimEngine.encode(b))
    }

    @Test
    fun oneStepAndSmallStepsProduceTheSameEvents() {
        val a = TestSupport.trial(7, preset = RealismPreset.STANDARD, fixture = false)
        val b = TestSupport.trial(7, preset = RealismPreset.STANDARD, fixture = false)
        val end = a.clock.now + 30 * HOUR
        engine.advance(a, end)
        var t = b.clock.now
        var i = 0
        while (t < end) {
            t = minOf(end, t + listOf(17_000L, 61_000L, 3_000L, 240_000L)[i++ % 4])
            engine.advance(b, t)
        }
        assertEquals(a.log, b.log)
        assertTrue(a.log.any { it.type == LogType.HELPER }, "the helper should have worked overnight")
    }

    @Test
    fun projectionDoesNotChangeTheLiveRun() {
        val w = TestSupport.trial(3, fixture = false)
        engine.apply(w, Command.SetPresence(Presence.BACKGROUND), wall(w))
        val before = SimEngine.encode(w)
        val wake = engine.planNextWake(w)
        assertNotNull(wake)
        assertEquals(before, SimEngine.encode(w))
    }

    @Test
    fun projectedWakeMatchesWhenTheLiveNeedOpens() {
        val w = TestSupport.trial(19, preset = RealismPreset.REALISTIC, fixture = false, startHour = 9)
        engine.apply(w, Command.SetPresence(Presence.BACKGROUND), wall(w))
        val wake = engine.planNextWake(w)!!
        engine.advance(w, wake.atLogical - 1)
        assertTrue(w.needs.none { it.eligibleSince != null && !it.alerted }, "nothing eligible before the planned wake")
        val ops = engine.reconcileAlerts(w, wake.atWall, foreground = false)
        assertTrue(ops.any { it is NotificationOp.Post }, "an alert is posted at the planned wake")
    }

    // ------------------------------------------------------------------ alerts and coverage

    /** Follows planned wake-ups with the app in the background until [untilWall]. */
    private fun runBackground(w: World, untilWall: Long): List<NotificationOp> {
        val ops = mutableListOf<NotificationOp>()
        engine.reconcileAlerts(w, wall(w), foreground = true)
        engine.apply(w, Command.SetPresence(Presence.BACKGROUND), wall(w))
        var guard = 0
        while (guard++ < 500) {
            val wake = engine.planNextWake(w) ?: break
            if (wake.atWall > untilWall) break
            ops += engine.reconcileAlerts(w, wake.atWall, foreground = false)
        }
        engine.advance(w, engine.logicalNow(w, untilWall))
        return ops
    }

    @Test
    fun standardDaytimeHungerPostsAnOrdinaryReminder() {
        val w = TestSupport.trial(11, preset = RealismPreset.STANDARD, fixture = false, startHour = 9)
        val ops = runBackground(w, wall(w) + 6 * HOUR)
        val posts = ops.filterIsInstance<NotificationOp.Post>().filter { !it.silent }
        assertTrue(posts.isNotEmpty())
        assertTrue(posts.all { it.channel == Channels.CARE })
        val trace = w.log.filter { it.type in setOf(LogType.NEED_OPENED, LogType.ALERT, LogType.INTERVENTION, LogType.HELPER, LogType.HANDOFF, LogType.WAKE, LogType.CARE_DONE) }
            .joinToString("\n") { java.time.Instant.ofEpochMilli(it.wall).atZone(java.time.ZoneId.of(TestSupport.ZONE)).toLocalTime().toString() + " " + it.type + " " + it.text }
        assertTrue(posts.any { it.text.contains("hunger") }, posts.joinToString { it.text } + "\n" + trace)
    }

    /** Found on the emulator: leaving the app with a fussing baby must still bring a reminder once crying starts. */
    @Test
    fun leavingWithAFussingBabyRemindsOnceCryingStarts() {
        val w = TestSupport.trial(41, preset = RealismPreset.REALISTIC, startHour = 10)
        val hunger = w.needs.first { it.kind == NeedKind.HUNGER }
        val ops = runBackground(w, wall(w) + 2 * HOUR)
        val audible = ops.filterIsInstance<NotificationOp.Post>().filter { !it.silent }
        assertTrue(audible.any { it.text.contains("Crying", ignoreCase = true) }, audible.joinToString { it.text })
        assertTrue(hunger.cryAlerted || w.needs.none { it.id == hunger.id }, "the hunger escalation was alerted")
        assertTrue(audible.size <= 6, "a bounded number of audible reminders: ${audible.size}")
    }

    @Test
    fun standardNightIsCoveredByTheHelper() {
        val w = TestSupport.trial(12, preset = RealismPreset.STANDARD, fixture = false, startHour = 22)
        val start = wall(w)
        runBackground(w, start + 10 * HOUR)
        val nightAlerts = w.alerts.ledger.filter { it.outcome == "POSTED" && w.log.isNotEmpty() }.filter { rec ->
            val h = java.time.Instant.ofEpochMilli(rec.wallAt).atZone(java.time.ZoneId.of(TestSupport.ZONE)).hour
            h >= 23 || h < 7
        }
        assertTrue(nightAlerts.isEmpty(), "no player alerts in the sleep window")
        assertTrue(w.stats.count("feed.night.HELPER") >= 1, "the helper fed overnight")
    }

    @Test
    fun extremeRealHasAtMostTwoAudibleAttemptsPerWindow() {
        val w = TestSupport.trial(13, preset = RealismPreset.EXTREME, fixture = false, startHour = 22)
        engine.apply(w, Command.NightConsent(true), wall(w))
        val start = wall(w)
        val ops = runBackground(w, start + 10 * HOUR)
        val audible = ops.filterIsInstance<NotificationOp.Post>().filter { it.channel == Channels.NIGHT && !it.silent }
        assertTrue(audible.size in 1..2, "audible attempts: ${audible.size}")
        assertTrue(w.alerts.nightWindows.values.all { it.attempts <= 2 })
        assertTrue(w.stats.count("feed.HELPER") >= 1, "after the limit or the cap, the helper takes over")
    }

    /** Regression: the planner and the receiver must agree, or the phone is woken again and again. */
    @Test
    fun plannerAlwaysMovesForwardEvenWithASuspendedSession() {
        for (preset in RealismPreset.entries) {
            val w = TestSupport.trial(31, preset = preset, startHour = 21)
            engine.apply(w, Command.StartSession(SessionKind.DIAPER_CHANGE), wall(w))
            engine.reconcileAlerts(w, wall(w), foreground = true)
            engine.apply(w, Command.SetPresence(Presence.BACKGROUND), wall(w))
            var wakes = 0
            var wake = engine.planNextWake(w)
            while (wake != null && !w.ended && wakes < 500) {
                engine.reconcileAlerts(w, wake.atWall, foreground = false)
                val next = engine.planNextWake(w) ?: break
                assertTrue(next.atWall > wake.atWall, "$preset: wake at ${next.atWall} does not move past ${wake.atWall} (${next.reason})")
                wake = next
                wakes++
            }
            assertTrue(wakes < 500, "$preset: too many wake-ups ($wakes)")
            assertTrue(w.ended, "$preset: the background run reaches the end of the trial")
        }
    }

    @Test
    fun pauseFreezesBothClocksAndClearsAlerts() {
        val w = TestSupport.trial(14, preset = RealismPreset.REALISTIC, fixture = true, startHour = 10)
        val before = w.clock.now
        engine.apply(w, Command.Pause, wall(w))
        val pausedAt = wall(w)
        val later = pausedAt + 3 * HOUR
        engine.advanceToWall(w, later)
        assertEquals(before, w.clock.now, "logical time is frozen")
        val ops = engine.reconcileAlerts(w, later, foreground = false)
        assertTrue(ops.none { it is NotificationOp.Post })
        assertNull(engine.planNextWake(w))
        val needsBefore = w.needs.map { it.phase }
        engine.apply(w, Command.Resume, later)
        assertEquals(needsBefore, w.needs.map { it.phase }, "no escalation happened while paused")
        assertEquals(3 * HOUR, w.clock.totalPausedMs)
        assertEquals(w.clock.startLogical + 48 * HOUR, w.clock.endLogical, "trial time excludes the pause")
    }

    @Test
    fun unansweredCryingIsHandedToTheHelper() {
        val w = TestSupport.trial(15, preset = RealismPreset.REALISTIC, fixture = true, startHour = 10)
        engine.apply(w, Command.SetPresence(Presence.BACKGROUND), wall(w))
        engine.advance(w, w.clock.now + 90 * MINUTE)
        assertTrue(w.stats.count("intervention") >= 1)
        assertTrue(w.log.any { it.type == LogType.INTERVENTION })
    }

    // ------------------------------------------------------------------ hands-on care

    @Test
    fun oneWakeTwoNeedsFixtureCanBeCompleted() {
        val w = TestSupport.trial(21, preset = RealismPreset.REALISTIC, startHour = 10)
        assertTrue(w.needs.any { it.kind == NeedKind.HUNGER } && w.needs.any { it.kind == NeedKind.DIRTY })
        assertTrue(engine.apply(w, Command.StartSession(SessionKind.DIAPER_CHANGE), wall(w)).ok)
        completeSession(w)
        assertTrue(w.needs.none { it.kind == NeedKind.DIRTY })
        assertEquals(1, w.stats.count("change.PLAYER"))
        // Preparing a bottle needs both hands.
        val held = engine.apply(w, Command.StartSession(SessionKind.BOTTLE_PREP), wall(w))
        assertFalse(held.ok)
        engine.apply(w, Command.PutDown(Place.BASSINET), wall(w))
        assertTrue(engine.apply(w, Command.StartSession(SessionKind.BOTTLE_PREP), wall(w)).ok)
        completeSession(w)
        assertTrue(w.child.bottle.preparedAt != null)
        assertTrue(engine.apply(w, Command.StartSession(SessionKind.BOTTLE_FEED), wall(w)).ok)
        completeSession(w)
        assertTrue(w.needs.none { it.kind == NeedKind.HUNGER })
        assertEquals(1, w.stats.count("feed.PLAYER"))
        assertTrue(w.log.any { it.type == LogType.CARE_DONE && it.tag == "feed" })
    }

    @Test
    fun unsafeChoicesAreBlockedWithCoaching() {
        val w = TestSupport.trial(22, preset = RealismPreset.REALISTIC, startHour = 10)
        engine.apply(w, Command.StartSession(SessionKind.BOTTLE_PREP), wall(w))
        engine.advance(w, w.clock.now + 20 * SECOND)
        assertEquals("water", w.session!!.currentStep!!.id)
        engine.apply(w, Command.Choose("powder_first"), wall(w))
        assertEquals("water", w.session!!.currentStep!!.id, "blocked choices keep the step")
        assertEquals(1, w.stats.count("misstep"))
        engine.apply(w, Command.StopSession, wall(w))
        engine.apply(w, Command.StartSession(SessionKind.SETTLE), wall(w))
        val s = w.session!!
        while (s.currentStep!!.id == "space") {
            val c = s.currentStep!!.choices.first()
            engine.apply(w, Command.Choose(c.id), wall(w))
        }
        engine.apply(w, Command.Choose("tummy"), wall(w))
        assertEquals("position", w.session!!.currentStep!!.id)
        assertTrue(w.log.any { it.type == LogType.MISSTEP && it.lesson == "L-SLEEP-01" })
    }

    @Test
    fun sessionEffortStopsWhenTheAppIsInTheBackground() {
        val w = TestSupport.trial(23, preset = RealismPreset.EXTREME, route = FeedingRoute.BREAST, startHour = 10)
        engine.apply(w, Command.StartSession(SessionKind.BREASTFEED), wall(w))
        engine.apply(w, Command.Choose("cradle"), wall(w))
        engine.apply(w, Command.DoStep, wall(w))
        engine.advance(w, w.clock.now + 2 * MINUTE)
        engine.apply(w, Command.SetPresence(Presence.BACKGROUND), wall(w))
        val done = w.session!!.stepDoneMs
        engine.advance(w, w.clock.now + 10 * MINUTE)
        assertNotNull(w.session)
        assertTrue(w.session!!.runningSince == null)
        assertTrue(w.session!!.stepDoneMs - done < 1000.0, "no effort accrues in the background")
    }

    @Test
    fun handoffMovesTheGroupToTheHelper() {
        val w = TestSupport.trial(24, preset = RealismPreset.REALISTIC, startHour = 10)
        val r = engine.apply(w, Command.HandOffNeeds, wall(w))
        assertTrue(r.ok)
        assertTrue(w.needs.all { it.owner == Caregiver.HELPER })
        engine.advance(w, w.clock.now + 60 * MINUTE)
        assertTrue(w.stats.count("feed.HELPER") >= 1)
        assertTrue(w.stats.count("change.HELPER") >= 1)
    }
}
