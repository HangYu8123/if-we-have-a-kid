package app.childrensimulator.sim.age

import app.childrensimulator.sim.DAY
import app.childrensimulator.sim.profile.AgeProfiles
import app.childrensimulator.sim.profile.StageStart
import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.math.min

/** Growth pace presets (docs/01 §4). The Weekend Trial uses [HOLD]. */
enum class GrowthPace { FAST, STANDARD, SLOW, REAL_TIME, HOLD }

/** Saved whenever the pace changes: age is integrated forward from here (docs/11 §3.2). */
@Serializable
data class AgeCheckpoint(val activeMs: Long, val ageDays: Double, val pace: GrowthPace)

/** The development clock: piecewise-constant growth rates per Standard band. */
class AgeModel(val profiles: AgeProfiles) {
    private class Band(val start: Double, val end: Double, val realDays: Double)

    val terminalAgeDays: Double = profiles.calendar.terminalAgeDays
    private val yearDays = profiles.calendar.modelYearDays
    private val bands: List<Band>

    init {
        var start = 0.0
        bands = profiles.growthPaces.standardBands.map { b -> Band(start, b.endAgeDays, b.realDays).also { start = b.endAgeDays } }
    }

    private fun bandAt(ageDays: Double): Band = bands.firstOrNull { ageDays < it.end } ?: bands.last()

    private fun multiplier(pace: GrowthPace): Double = when (pace) {
        GrowthPace.FAST -> profiles.growthPaces.durationMultiplier["fast"] ?: 0.5
        GrowthPace.SLOW -> profiles.growthPaces.durationMultiplier["slow"] ?: 2.0
        else -> profiles.growthPaces.durationMultiplier["standard"] ?: 1.0
    }

    /** Simulated age days per active real day. */
    fun rate(ageDays: Double, pace: GrowthPace): Double = when (pace) {
        GrowthPace.HOLD -> 0.0
        GrowthPace.REAL_TIME -> 1.0
        else -> bandAt(ageDays).let { (it.end - it.start) / it.realDays } / multiplier(pace)
    }

    /** Advances [ageDays] by [realDays] of active time, spending only the time needed to cross each band (docs/01 §4). */
    fun advance(ageDays: Double, realDays: Double, pace: GrowthPace): Double {
        if (pace == GrowthPace.HOLD || realDays <= 0.0) return ageDays
        if (pace == GrowthPace.REAL_TIME) return min(terminalAgeDays, ageDays + realDays)
        var age = ageDays
        var remaining = realDays
        while (remaining > 1e-12 && age < terminalAgeDays) {
            val band = bandAt(age)
            val r = rate(age, pace)
            val toEnd = (band.end - age) / r
            if (remaining < toEnd) {
                age += remaining * r
                remaining = 0.0
            } else {
                age = band.end
                remaining -= toEnd
            }
        }
        return min(age, terminalAgeDays)
    }

    /** Active real days needed to grow from [from] to [to] (infinite when the age is held). */
    fun realDaysToReach(from: Double, to: Double, pace: GrowthPace): Double {
        if (to <= from) return 0.0
        if (pace == GrowthPace.HOLD) return Double.POSITIVE_INFINITY
        if (pace == GrowthPace.REAL_TIME) return to - from
        var age = from
        var days = 0.0
        while (age < to - 1e-12) {
            val band = bandAt(age)
            val segmentEnd = min(band.end, to)
            days += (segmentEnd - age) / rate(age, pace)
            age = segmentEnd
            if (band === bands.last() && age >= band.end) break
        }
        return days
    }

    fun ageAt(checkpoint: AgeCheckpoint, activeMs: Long): Double =
        advance(checkpoint.ageDays, (activeMs - checkpoint.activeMs).toDouble() / DAY, checkpoint.pace)

    fun stageAt(ageDays: Double): StageStart = profiles.calendar.stageStarts.last { it.ageDays <= ageDays.coerceAtLeast(0.0) }

    fun chapterIndex(ageDays: Double): Int = profiles.calendar.chapterStartDays.indexOfLast { it <= ageDays }

    fun isTerminal(ageDays: Double): Boolean = ageDays >= terminalAgeDays

    /** Days through day 13, weeks through day 29, months up to age 2, then years (docs/01 §2). */
    fun display(ageDays: Double): String {
        val d = floor(ageDays).toInt()
        return when {
            d < 14 -> plural(d, "day")
            d < 30 -> plural(d / 7, "week")
            d < AgeProfiles.yearBoundary(2, yearDays) -> plural((1..24).last { AgeProfiles.monthBoundary(it, yearDays) <= d }, "month")
            else -> plural((2..18).last { AgeProfiles.yearBoundary(it, yearDays) <= d }, "year")
        }
    }

    private fun plural(n: Int, unit: String) = if (n == 1) "1 $unit" else "$n ${unit}s"
}
