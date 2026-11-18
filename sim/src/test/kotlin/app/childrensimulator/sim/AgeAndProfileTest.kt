package app.childrensimulator.sim

import app.childrensimulator.sim.age.AgeModel
import app.childrensimulator.sim.age.GrowthPace
import app.childrensimulator.sim.engine.MealPlanner
import app.childrensimulator.sim.profile.ProfileResolver
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** docs/12 Phase 0 exit criteria for the age resolver and profile data. */
class AgeAndProfileTest {
    private val profiles = TestSupport.profiles
    private val age = AgeModel(profiles)
    private val resolver = ProfileResolver(profiles)

    @Test
    fun profileFileValidates() {
        assertTrue(profiles.validate().isEmpty(), profiles.validate().joinToString())
        assertEquals(6575.0, profiles.calendar.terminalAgeDays)
    }

    @Test
    fun standardPaceReachesMilestonesOnPlannedDays() {
        assertEquals(7.0, age.realDaysToReach(0.0, 30.0, GrowthPace.STANDARD), 1e-9)
        assertEquals(32.0, age.realDaysToReach(0.0, 365.0, GrowthPace.STANDARD), 1e-9)
        assertEquals(44.0, age.realDaysToReach(0.0, 731.0, GrowthPace.STANDARD), 1e-9)
        assertEquals(62.0, age.realDaysToReach(0.0, 1826.0, GrowthPace.STANDARD), 1e-9)
        assertEquals(83.0, age.realDaysToReach(0.0, 4383.0, GrowthPace.STANDARD), 1e-9)
        assertEquals(95.0, age.realDaysToReach(0.0, 6575.0, GrowthPace.STANDARD), 1e-9)
        assertEquals(365.0, age.advance(0.0, 32.0, GrowthPace.STANDARD), 1e-6)
        assertEquals(1826.0, age.advance(0.0, 62.0, GrowthPace.STANDARD), 1e-6)
    }

    @Test
    fun presetsScaleTheBands() {
        assertEquals(16.0, age.realDaysToReach(0.0, 365.0, GrowthPace.FAST), 1e-9)
        assertEquals(64.0, age.realDaysToReach(0.0, 365.0, GrowthPace.SLOW), 1e-9)
        assertEquals(23.0, age.advance(21.0, 2.0, GrowthPace.REAL_TIME), 1e-9)
        assertEquals(21.0, age.advance(21.0, 2.0, GrowthPace.HOLD), 1e-9)
    }

    @Test
    fun terminalAgeCaps() {
        assertEquals(6575.0, age.advance(6000.0, 500.0, GrowthPace.STANDARD), 1e-9)
        assertEquals(6575.0, age.advance(0.0, 1000.0, GrowthPace.FAST), 1e-9)
        assertTrue(age.isTerminal(6575.0))
    }

    @Test
    fun incrementalAdvanceMatchesOneStep() {
        val oneStep = age.advance(0.0, 70.0, GrowthPace.STANDARD)
        var a = 0.0
        repeat(700) { a = age.advance(a, 0.1, GrowthPace.STANDARD) }
        assertEquals(oneStep, a, 1e-6)
    }

    @Test
    fun everyStageBoundaryResolves() {
        val starts = profiles.calendar.stageStarts
        for ((i, s) in starts.withIndex()) {
            assertEquals(s.id, age.stageAt(s.ageDays.toDouble()).id, "stage at its start day")
            if (i > 0) assertEquals(starts[i - 1].id, age.stageAt(s.ageDays - 0.001).id, "previous stage just before ${s.id}")
            assertEquals(s.id, resolver.resolve(s.ageDays.toDouble()).stage.id)
        }
    }

    @Test
    fun chaptersOpenOnMonthAndYearBoundaries() {
        val days = profiles.calendar.chapterStartDays
        assertEquals(40, days.size)
        for ((i, d) in days.withIndex()) {
            assertEquals(i, age.chapterIndex(d.toDouble()))
            if (i > 0) assertEquals(i - 1, age.chapterIndex(d - 0.01))
        }
    }

    @Test
    fun ageDisplayFollowsTheRules() {
        assertEquals("0 days", age.display(0.0))
        assertEquals("13 days", age.display(13.9))
        assertEquals("2 weeks", age.display(14.0))
        assertEquals("3 weeks", age.display(21.0))
        assertEquals("4 weeks", age.display(29.9))
        assertEquals("1 month", age.display(30.0))
        assertEquals("3 months", age.display(91.0))
        assertEquals("23 months", age.display(730.0))
        assertEquals("2 years", age.display(731.0))
        assertEquals("8 years", age.display(2922.0))
        assertEquals("18 years", age.display(6575.0))
    }

    @Test
    fun day21InterpolatesBetweenD14AndM1() {
        val p = resolver.resolve(21.0)
        assertEquals("S0", p.stage.id)
        val formula = p.milkFeeds("formula")!!
        assertEquals(6.5625, formula.min, 1e-9)
        assertEquals(8.5625, formula.max, 1e-9)
        val breast = p.milkFeeds("breast")!!
        assertEquals(8.0, breast.min, 1e-9)
        assertEquals(11.125, breast.max, 1e-9)
        assertEquals(1.0875, p.quietToActiveIntervalRatio!!, 1e-9)
        assertEquals(66.5625, p.wakeWindowMin!!.max, 1e-9)
        assertEquals(11.125, p.changesPerDay!!.max, 1e-9)
    }

    @Test
    fun missingEndpointHoldsTheLowerAnchor() {
        // Between m10 (milk present) and m12 (milk null) the lower value holds.
        val p = resolver.resolve(330.0)
        assertEquals(3.0, p.milkFeeds("breast")!!.min, 1e-9)
        // From m12 the lower anchor's null holds.
        assertNull(resolver.resolve(400.0).milkFeedsPerDay)
        // Discrete routines step at anchors.
        assertEquals(2.0, resolver.resolve(300.0).solidMealsPerDay!!.min, 1e-9)
    }

    @Test
    fun eightYearOldHasExactlyThreeMealSlots() {
        val p = resolver.resolve(2922.0)
        assertNull(p.milkFeedsPerDay)
        val slots = MealPlanner.slots(p)
        assertEquals(3, slots.count { !it.snack }, "three meal slots at age 8")
        assertTrue(slots.count { it.snack } in 1..2)
        assertEquals(listOf("breakfast", "lunch", "dinner"), slots.filter { !it.snack }.map { it.id })
        assertNotNull(p.repeatRequestMin)
    }
}
