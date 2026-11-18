package app.childrensimulator.sim.profile

/** Inclusive numeric range. Fractional endpoints are allowed until a process samples a count. */
data class Range(val min: Double, val max: Double) {
    val mid: Double get() = (min + max) / 2.0
    fun at(u: Double): Double = min + (max - min) * u
    fun contains(x: Double, tolerance: Double = 1e-9): Boolean = x >= min - tolerance && x <= max + tolerance
    fun scaled(f: Double) = Range(min * f, max * f)
    override fun toString(): String = if (min == max) fmt(min) else "${fmt(min)}–${fmt(max)}"

    companion object {
        fun of(list: List<Double>?): Range? = list?.let { Range(it[0], it[1]) }
        private fun fmt(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else "%.1f".format(v)
    }
}

/** Profile values resolved for one fractional age (docs/01 §5). */
data class ResolvedProfile(
    val ageDays: Double,
    val stage: StageStart,
    val lowerAnchorId: String,
    val upperAnchorId: String,
    val milkFeedsPerDay: Map<String, Range>?,
    val quietToActiveIntervalRatio: Double?,
    val quietPhaseMilkStartsPerDay: Range?,
    val solidMealsPerDay: Range?,
    val snacksPerDay: Range?,
    val changesPerDay: Range?,
    val stoolsPerDay: Map<String, Range>?,
    val totalSleepHours: Range?,
    val daytimeNaps: Range?,
    val wakeWindowMin: Range?,
    val bedtimeLocal: List<String>?,
    val circadianStrength: Double?,
    val fussCryMinutesPerDay: Range?,
    val tantrumsPerWeek: Range?,
    val cueToFussMin: Range?,
    val fussToCryMin: Range?,
    val cryToHardCryMin: Range?,
    val handoffAfterCryingMin: Range?,
    val repeatRequestMin: Range?,
    val bathsPerWeek: Range?,
    val nailCarePerWeek: Range?,
    val needEpisodesPerDay: Range?,
    val alertGroupsPerDay: Range?,
    val overnightWakeGroupsPerNight: Range?,
) {
    fun milkFeeds(routeKey: String): Range? = milkFeedsPerDay?.get(routeKey)
    fun stools(routeKey: String): Range? = stoolsPerDay?.let { it[routeKey] ?: it["any"] }
}

class ProfileResolver(val profiles: AgeProfiles) {
    private val anchors = profiles.anchors
    private val terminal = profiles.calendar.terminalAgeDays

    fun stageAt(ageDays: Double): StageStart =
        profiles.calendar.stageStarts.last { it.ageDays <= ageDays.coerceAtLeast(0.0) }

    fun resolve(ageDays: Double): ResolvedProfile {
        val age = ageDays.coerceIn(0.0, terminal)
        val i = anchors.indexOfLast { it.ageDays <= age }
        val lo = anchors[i]
        val hi = anchors.getOrNull(i + 1) ?: lo
        val t = if (hi === lo) 0.0 else (age - lo.ageDays) / (hi.ageDays - lo.ageDays)

        fun lerp(a: Double, b: Double) = a + (b - a) * t
        fun range(sel: (Anchor) -> List<Double>?): Range? {
            val a = sel(lo) ?: return null
            val b = sel(hi) ?: return Range.of(a)
            return Range(lerp(a[0], b[0]), lerp(a[1], b[1]))
        }
        fun number(sel: (Anchor) -> Double?): Double? {
            val a = sel(lo) ?: return null
            val b = sel(hi) ?: return a
            return lerp(a, b)
        }
        fun map(sel: (Anchor) -> Map<String, List<Double>>?): Map<String, Range>? {
            val a = sel(lo) ?: return null
            val b = sel(hi)
            return a.mapValues { (k, v) ->
                val bv = b?.get(k)
                if (bv == null) Range(v[0], v[1]) else Range(lerp(v[0], bv[0]), lerp(v[1], bv[1]))
            }
        }

        return ResolvedProfile(
            ageDays = age,
            stage = stageAt(age),
            lowerAnchorId = lo.id,
            upperAnchorId = hi.id,
            milkFeedsPerDay = map { it.feeding.milkFeedsPerDay },
            quietToActiveIntervalRatio = number { it.feeding.quietToActiveIntervalRatio },
            quietPhaseMilkStartsPerDay = range { it.feeding.quietPhaseMilkStartsPerDay },
            // Discrete routines step at anchors (docs/01 §5.3).
            solidMealsPerDay = Range.of(lo.feeding.solidMealsPerDay),
            snacksPerDay = Range.of(lo.feeding.snacksPerDay),
            changesPerDay = range { it.diaper.changesPerDay },
            stoolsPerDay = map { it.diaper.stoolsPerDay },
            totalSleepHours = range { it.sleep.totalHoursPerDay },
            daytimeNaps = range { it.sleep.daytimeNaps },
            wakeWindowMin = range { it.sleep.wakeWindowMin },
            bedtimeLocal = lo.sleep.bedtimeLocal,
            circadianStrength = number { it.sleep.circadianStrength },
            fussCryMinutesPerDay = range { it.distress.fussCryMinutesPerDay },
            tantrumsPerWeek = range { it.distress.tantrumsPerWeek },
            cueToFussMin = range { it.distress.cueToFussMin },
            fussToCryMin = range { it.distress.fussToCryMin },
            cryToHardCryMin = range { it.distress.cryToHardCryMin },
            handoffAfterCryingMin = range { it.distress.handoffAfterCryingMin },
            repeatRequestMin = range { it.distress.repeatRequestMin },
            bathsPerWeek = range { it.hygiene.bathsPerWeek },
            nailCarePerWeek = range { it.hygiene.nailCarePerWeek },
            needEpisodesPerDay = range { it.attention.needEpisodesPerDay },
            alertGroupsPerDay = range { it.attention.alertGroupsPerDay },
            overnightWakeGroupsPerNight = range { it.attention.overnightWakeGroupsPerNight },
        )
    }
}
