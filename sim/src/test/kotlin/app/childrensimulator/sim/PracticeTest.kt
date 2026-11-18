package app.childrensimulator.sim

import app.childrensimulator.sim.engine.PracticeDrill
import app.childrensimulator.sim.engine.newPractice
import app.childrensimulator.sim.engine.planNextWake
import app.childrensimulator.sim.model.FeedingRoute
import app.childrensimulator.sim.model.RunMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Practice Room drills (docs/10 §2): each can be completed and never schedules reminders. */
class PracticeTest {
    private val engine = TestSupport.engine

    @Test
    fun everyDrillCanBeCompletedWithoutReminders() {
        for (drill in PracticeDrill.entries) {
            val w = engine.newPractice(drill, "Mia", FeedingRoute.FORMULA, 1, TestSupport.ZONE, TestSupport.startWall(10), seed = 99L + drill.ordinal)
            assertEquals(RunMode.PRACTICE, w.mode)
            assertNull(engine.planNextWake(w), "${drill.id}: practice sends no background reminders")
            CareBot(engine, w, responseDelayMs = 30_000).run(1.5, stepMs = 5_000)
            assertTrue(w.practice!!.objectiveMet, "${drill.id}: objective met within 90 minutes; log: " + w.log.takeLast(8).joinToString(" | ") { it.text })
            assertTrue(w.practice!!.debrief!!.isNotBlank())
        }
    }

    @Test
    fun drillsForceTheirFeedingRoute() {
        val bottle = engine.newPractice(PracticeDrill.BOTTLE, "Mia", FeedingRoute.BREAST, 1, TestSupport.ZONE, TestSupport.startWall(10), seed = 1)
        assertEquals(FeedingRoute.FORMULA, bottle.settings.route)
        val breast = engine.newPractice(PracticeDrill.BREAST, "Mia", FeedingRoute.FORMULA, 1, TestSupport.ZONE, TestSupport.startWall(10), seed = 1)
        assertEquals(FeedingRoute.BREAST, breast.settings.route)
    }
}
