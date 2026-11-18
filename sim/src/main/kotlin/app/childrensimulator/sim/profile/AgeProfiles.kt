package app.childrensimulator.sim.profile

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.floor

/**
 * Typed view of docs/data/age-profiles.json. The JSON file is the canonical source; the app bundles
 * that exact file and the JVM tests read it from docs/data.
 */
@Serializable
data class AgeProfiles(
    val schema: String,
    val version: Int,
    val calendar: ProfileCalendar,
    val growthPaces: GrowthPaces,
    val tuning: Tuning,
    val anchors: List<Anchor>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): AgeProfiles {
            val profiles = json.decodeFromString(serializer(), text)
            val problems = profiles.validate()
            require(problems.isEmpty()) { "age-profiles.json is invalid:\n - " + problems.joinToString("\n - ") }
            return profiles
        }

        /** Month boundary M(n) = floor(n × 365.25 / 12 + 0.5) (docs/01 §2). */
        fun monthBoundary(n: Int, yearDays: Double = 365.25): Int = floor(n * yearDays / 12.0 + 0.5).toInt()

        fun yearBoundary(n: Int, yearDays: Double = 365.25): Int = monthBoundary(12 * n, yearDays)
    }

    /** Checks from docs/11 §8. Returns a list of human-readable problems (empty when valid). */
    fun validate(): List<String> {
        val p = mutableListOf<String>()
        if (schema != "children-simulator/age-profiles") p += "Unexpected schema '$schema'"
        val ages = anchors.map { it.ageDays }
        if (ages != ages.sorted() || ages.toSet().size != ages.size) p += "Anchors must be unique and sorted by ageDays"
        if (anchors.map { it.id }.toSet().size != anchors.size) p += "Anchor IDs must be unique"
        if (ages.firstOrNull() != 0.0) p += "The first anchor must be at age 0"
        if (ages.lastOrNull() != calendar.terminalAgeDays) p += "The last anchor must be the terminal age ${calendar.terminalAgeDays}"
        for (s in calendar.stageStarts) {
            if (anchors.none { it.ageDays == s.ageDays.toDouble() }) p += "Stage ${s.id} starts at day ${s.ageDays} without an anchor"
        }
        val stageDays = calendar.stageStarts.map { it.ageDays }
        if (stageDays != stageDays.sorted()) p += "Stage starts must be ascending"
        val expectedChapters = buildList {
            add(0)
            for (m in 1..23) add(monthBoundary(m, calendar.modelYearDays))
            for (y in 2..17) add(yearBoundary(y, calendar.modelYearDays))
        }
        if (calendar.chapterStartDays != expectedChapters) p += "chapterStartDays do not match the month/year boundary formula"
        var prevEnd = 0.0
        for (b in growthPaces.standardBands) {
            if (b.endAgeDays <= prevEnd) p += "Growth bands must be contiguous and ascending (band ending ${b.endAgeDays})"
            if (b.realDays <= 0) p += "Growth band ending ${b.endAgeDays} needs positive realDays"
            prevEnd = b.endAgeDays
        }
        if (growthPaces.standardBands.lastOrNull()?.endAgeDays != calendar.terminalAgeDays) p += "Growth bands must end at the terminal age"
        for (a in anchors) {
            a.allRanges().forEach { (name, r) ->
                if (r.size != 2) p += "${a.id}.$name must have two values"
                else if (r[0] > r[1]) p += "${a.id}.$name has min > max"
            }
        }
        return p
    }
}

@Serializable
data class ProfileCalendar(
    val modelYearDays: Double,
    val terminalAgeDays: Double,
    val chapterStartDays: List<Int>,
    val stageStarts: List<StageStart>,
)

@Serializable
data class StageStart(val id: String, val ageDays: Int, val label: String)

@Serializable
data class GrowthPaces(val standardBands: List<PaceBand>, val durationMultiplier: Map<String, Double>)

@Serializable
data class PaceBand(val endAgeDays: Double, val realDays: Double)

@Serializable
data class Tuning(
    val incompleteFeedDemand: IncompleteFeedDemand,
    val intervalJitterSigma: Map<String, Double>,
    val escalationDurationFactor: List<Double>,
)

@Serializable
data class IncompleteFeedDemand(val base: Double, val intakeWeight: Double, val minFactor: Double, val maxFactor: Double)

@Serializable
data class Anchor(
    val id: String,
    val ageDays: Double,
    val feeding: Feeding,
    val diaper: Diaper,
    val sleep: Sleep,
    val distress: Distress,
    val hygiene: Hygiene,
    val attention: Attention,
) {
    fun allRanges(): List<Pair<String, List<Double>>> = buildList {
        feeding.milkFeedsPerDay?.forEach { (k, v) -> add("feeding.milkFeedsPerDay.$k" to v) }
        feeding.quietPhaseMilkStartsPerDay?.let { add("feeding.quietPhaseMilkStartsPerDay" to it) }
        feeding.solidMealsPerDay?.let { add("feeding.solidMealsPerDay" to it) }
        feeding.snacksPerDay?.let { add("feeding.snacksPerDay" to it) }
        diaper.changesPerDay?.let { add("diaper.changesPerDay" to it) }
        diaper.stoolsPerDay?.forEach { (k, v) -> add("diaper.stoolsPerDay.$k" to v) }
        sleep.totalHoursPerDay?.let { add("sleep.totalHoursPerDay" to it) }
        sleep.daytimeNaps?.let { add("sleep.daytimeNaps" to it) }
        sleep.wakeWindowMin?.let { add("sleep.wakeWindowMin" to it) }
        distress.fussCryMinutesPerDay?.let { add("distress.fussCryMinutesPerDay" to it) }
        distress.tantrumsPerWeek?.let { add("distress.tantrumsPerWeek" to it) }
        distress.cueToFussMin?.let { add("distress.cueToFussMin" to it) }
        distress.fussToCryMin?.let { add("distress.fussToCryMin" to it) }
        distress.cryToHardCryMin?.let { add("distress.cryToHardCryMin" to it) }
        distress.handoffAfterCryingMin?.let { add("distress.handoffAfterCryingMin" to it) }
        distress.repeatRequestMin?.let { add("distress.repeatRequestMin" to it) }
        hygiene.bathsPerWeek?.let { add("hygiene.bathsPerWeek" to it) }
        hygiene.nailCarePerWeek?.let { add("hygiene.nailCarePerWeek" to it) }
        attention.needEpisodesPerDay?.let { add("attention.needEpisodesPerDay" to it) }
        attention.alertGroupsPerDay?.let { add("attention.alertGroupsPerDay" to it) }
        attention.overnightWakeGroupsPerNight?.let { add("attention.overnightWakeGroupsPerNight" to it) }
    }
}

@Serializable
data class Feeding(
    val milkFeedsPerDay: Map<String, List<Double>>? = null,
    val quietToActiveIntervalRatio: Double? = null,
    val quietPhaseMilkStartsPerDay: List<Double>? = null,
    val solidMealsPerDay: List<Double>? = null,
    val snacksPerDay: List<Double>? = null,
)

@Serializable
data class Diaper(
    val changesPerDay: List<Double>? = null,
    val stoolsPerDay: Map<String, List<Double>>? = null,
)

@Serializable
data class Sleep(
    val totalHoursPerDay: List<Double>? = null,
    val daytimeNaps: List<Double>? = null,
    val wakeWindowMin: List<Double>? = null,
    val bedtimeLocal: List<String>? = null,
    val circadianStrength: Double? = null,
)

@Serializable
data class Distress(
    val fussCryMinutesPerDay: List<Double>? = null,
    val tantrumsPerWeek: List<Double>? = null,
    val cueToFussMin: List<Double>? = null,
    val fussToCryMin: List<Double>? = null,
    val cryToHardCryMin: List<Double>? = null,
    val handoffAfterCryingMin: List<Double>? = null,
    val repeatRequestMin: List<Double>? = null,
)

@Serializable
data class Hygiene(val bathsPerWeek: List<Double>? = null, val nailCarePerWeek: List<Double>? = null)

@Serializable
data class Attention(
    val needEpisodesPerDay: List<Double>? = null,
    val alertGroupsPerDay: List<Double>? = null,
    val overnightWakeGroupsPerNight: List<Double>? = null,
)
