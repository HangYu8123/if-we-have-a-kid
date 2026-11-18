package app.childrensimulator.sim.engine

import app.childrensimulator.sim.DAY
import app.childrensimulator.sim.HOUR
import app.childrensimulator.sim.MINUTE
import app.childrensimulator.sim.Stream
import app.childrensimulator.sim.TimeWindow
import app.childrensimulator.sim.model.*
import app.childrensimulator.sim.profile.Range
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.min

// ------------------------------------------------------------------ infant milk (docs/11 §3.5)

private const val QUIET_FRACTION = 8.0 / 24.0

internal fun Ctx.sampleDailyFeeds(): Double {
    val band = profile.milkFeeds(settings.route.profileKey) ?: return 0.0
    val u = (child.temperament.appetite + rng.normal(Stream.MILK) * 0.12).coerceIn(0.0, 1.0)
    return band.at(u)
}

/** Milk demand per minute at [t]: slower in the quiet phase, faster in a cluster-feeding window. */
internal fun Ctx.milkRatePerMinute(t: Long): Double {
    val n = child.milk.dailyFeeds
    val r = profile.quietToActiveIntervalRatio ?: return 0.0
    val active = n / (1440.0 * ((1 - QUIET_FRACTION) + QUIET_FRACTION / r))
    var rate = if (clock.inWindow(t, TimeWindow.REFERENCE_NIGHT)) active / r else active
    activeCluster(t)?.let { rate *= 1.0 + 1.3 * it.intensity }
    return rate
}

private fun Ctx.nextRateBoundary(t: Long): Long {
    var b = clock.nextWindowBoundary(t, TimeWindow.REFERENCE_NIGHT)
    for (e in w.scenario.evenings) {
        if (e.kind != EveningKind.CLUSTER_FEEDING) continue
        val s = clock.logical(e.wallStart)
        val en = clock.logical(e.wallEnd)
        if (s > t) b = min(b, s)
        if (en > t) b = min(b, en)
    }
    return b
}

internal fun Ctx.accumulateDemand(from: Long, to: Long): Double {
    var t = from
    var sum = 0.0
    var guard = 0
    while (t < to && guard++ < 500) {
        val seg = min(to, nextRateBoundary(t))
        sum += milkRatePerMinute(t) * (seg - t) / MINUTE.toDouble()
        t = seg
    }
    return sum
}

/** Logical time at which [amount] more demand accumulates after [from] (null if the rate is zero). */
internal fun Ctx.timeToAccumulate(from: Long, amount: Double): Long? {
    if (amount <= 0) return from
    var t = from
    var left = amount
    var guard = 0
    while (guard++ < 500) {
        val rate = milkRatePerMinute(t)
        val boundary = nextRateBoundary(t)
        if (rate > 0) {
            val segAmount = rate * (boundary - t) / MINUTE.toDouble()
            if (segAmount >= left) return t + (left / rate * MINUTE).toLong()
            left -= segAmount
        }
        t = boundary
        if (t - from > 4 * DAY) return null
    }
    return null
}

/** Folds accumulated demand into the reference point (before a rate change or pause). */
internal fun Ctx.checkpointMilk() {
    val m = child.milk
    if (now > m.refTime) {
        m.demandAtRef += accumulateDemand(m.refTime, now)
        m.refTime = now
    }
}

internal fun Ctx.recomputeHungerDue() {
    val m = child.milk
    if (profile.milkFeeds(settings.route.profileKey) == null) {
        m.hungerDueAt = null
        return
    }
    m.hungerDueAt = timeToAccumulate(m.refTime, m.threshold - m.demandAtRef)?.let { max(it, now) }
}

internal fun Ctx.sampleThreshold(factor: Double = 1.0): Double {
    val sigma = e.profiles.tuning.intervalJitterSigma[child.temperament.regularity.key] ?: 0.14
    return rng.jitter(Stream.MILK, sigma) * factor
}

internal fun Ctx.onHungerDue() {
    child.milk.hungerDueAt = null
    if (child.sleep == SleepState.ASLEEP) wakeUp("hunger")
    openNeed(NeedKind.HUNGER)
}

/**
 * Records a feed that started at [transferStart] and ended now. Interrupted feeds shorten the next interval
 * by `clamp(base + weight × acceptedFraction, min, max)` (docs/11 §3.5).
 */
internal fun Ctx.completeFeed(acceptedFraction: Double, interrupted: Boolean, who: Caregiver, transferStart: Long) {
    val m = child.milk
    val tuning = e.profiles.tuning.incompleteFeedDemand
    val hunger = w.needs.firstOrNull { it.kind == NeedKind.HUNGER }
    if (acceptedFraction < 0.15 && interrupted) {
        log(LogType.NOTE, "$name took very little. Hunger cues may continue.", who)
        return
    }
    val factor = if (interrupted) (tuning.base + tuning.intakeWeight * acceptedFraction).coerceIn(tuning.minFactor, tuning.maxFactor) else 1.0
    m.lastFeedStart = transferStart
    m.lastFeedEnd = now
    m.lastAcceptedFraction = acceptedFraction
    m.refTime = now
    m.demandAtRef = accumulateDemand(transferStart, now)
    m.threshold = sampleThreshold(factor)
    recomputeHungerDue()
    child.lastFeedEndedAt = now
    val routeText = if (settings.route == FeedingRoute.FORMULA) "formula" else if (who == Caregiver.HELPER) "expressed milk" else "breast/chestfeed"
    log(
        LogType.CARE_DONE,
        "${if (who == Caregiver.PLAYER) "You" else helperName} fed $name ($routeText${if (interrupted) ", partial" else ""}).",
        who, tag = "feed",
    )
    w.stats.inc("feed.${who.name}")
    if (inSleepWindow(transferStart)) w.stats.inc("feed.night.${who.name}")
    if (inReferenceNight(transferStart)) w.stats.inc("feed.quiet")
    hunger?.let { resolveNeed(it, who, if (interrupted) "Feed ended early (${(acceptedFraction * 100).toInt()}% of a full feed). Hunger will return sooner." else "Feed finished at fullness.") }
    // Stools often follow feeds (gastrocolic reflex).
    val d = child.diaper
    ensureStoolRate()
    val pFeedStool = min(0.85, 0.75 * d.stoolsPerDay / max(4.0, m.dailyFeeds))
    if (d.feedStoolAt == null && rng.chance(Stream.DIAPER, pFeedStool)) {
        // Often during or just after the feed, so one change can cover it.
        d.feedStoolAt = max(now + 30_000, transferStart + (rng.range(Stream.DIAPER, 2.0, 15.0) * MINUTE).toLong())
    }
    // Newborns often drift off at the end of a full feed, especially at night (docs/09 L-SLEEP-02).
    if (!interrupted && child.sleep == SleepState.AWAKE) {
        val awake = now - child.sleepSince
        val p = when {
            inReferenceNight() -> 0.85
            awake >= child.wakeWindowMs * 3 / 10 -> 0.65
            else -> 0.25
        } + if (settings.route == FeedingRoute.BREAST) 0.1 else 0.0
        val blocking = w.needs.any { it.kind == NeedKind.DIRTY || it.kind == NeedKind.GAS || it.kind == NeedKind.OVERSTIM }
        if (!blocking && !boutActive() && rng.chance(Stream.SLEEP, p)) {
            if (child.heldBy == null) {
                child.heldBy = who
                child.place = Place.ARMS
                child.holdStartedAt = now
            }
            onFallAsleep()
        }
    }
}

// ------------------------------------------------------------------ diapers (docs/03 §4)

internal fun Ctx.ensureStoolRate() {
    val d = child.diaper
    val day = dayIndex()
    if (d.stoolDay != day) {
        d.stoolDay = day
        val band = profile.stools(settings.route.profileKey) ?: Range(1.0, 3.0)
        d.stoolsPerDay = band.at(rng.range(Stream.DIAPER, 0.25, 0.75))
    }
}

internal fun Ctx.scheduleNextWet() {
    val changes = profile.changesPerDay?.mid ?: 8.0
    val meanMin = 1440.0 / (changes * 1.5)
    child.diaper.nextWetAt = now + (max(15.0, meanMin * rng.range(Stream.DIAPER, 0.5, 1.5)) * MINUTE).toLong()
}

internal fun Ctx.scheduleNextRandomStool() {
    ensureStoolRate()
    val meanMin = 1440.0 / max(0.3, 0.25 * child.diaper.stoolsPerDay)
    child.diaper.nextRandomStoolAt = now + (max(20.0, rng.exponential(Stream.DIAPER, meanMin)) * MINUTE).toLong()
}

internal fun Ctx.onWet() {
    child.diaper.wet++
    scheduleNextWet()
    // A little wet rarely bothers a newborn; a quite wet diaper becomes a need (docs/03 §4).
    if (child.diaper.wet >= 2 && w.needs.none { it.kind == NeedKind.WET || it.kind == NeedKind.DIRTY }) openNeed(NeedKind.WET)
}

internal fun Ctx.onStool(fromFeed: Boolean) {
    val d = child.diaper
    if (fromFeed) d.feedStoolAt = null else scheduleNextRandomStool()
    d.dirty = true
    if (rng.chance(Stream.DIAPER, if (settings.route == FeedingRoute.BREAST) 0.08 else 0.05)) d.blowout = true
    w.stats.inc("stools")
    val s = w.session
    if (s != null && (s.kind == SessionKind.BOTTLE_FEED || s.kind == SessionKind.BREASTFEED)) {
        s.lastFeedback = "Mid-feed: $name is grunting and straining. Probably a dirty diaper — you can change it after the feed."
    }
    if (child.sleep == SleepState.ASLEEP && rng.chance(Stream.DIAPER, if (inReferenceNight()) 0.2 else 0.4)) wakeUp("dirty diaper")
    w.needs.firstOrNull { it.kind == NeedKind.WET }?.let { w.needs.remove(it) }
    openNeed(NeedKind.DIRTY)
}

internal fun Ctx.diaperChanged(who: Caregiver) {
    val d = child.diaper
    val what = when {
        d.blowout -> "a blowout (diaper and clothes)"
        d.dirty && d.wet > 0 -> "a wet and dirty diaper"
        d.dirty -> "a dirty diaper"
        d.wet > 0 -> "a wet diaper"
        else -> "a dry diaper"
    }
    w.stats.inc("change.${who.name}")
    if (inSleepWindow()) w.stats.inc("change.night.${who.name}")
    if (d.blowout) w.stats.inc("blowout")
    d.wet = 0
    d.dirty = false
    d.blowout = false
    d.lastChangeAt = now
    w.needs.filter { it.kind == NeedKind.WET || it.kind == NeedKind.DIRTY }.forEach { resolveNeed(it, who, "Changed $what.") }
    if (w.needs.none { it.kind == NeedKind.WET || it.kind == NeedKind.DIRTY }) {
        log(LogType.CARE_DONE, "${if (who == Caregiver.PLAYER) "You" else helperName} changed $what.", who, tag = "change")
    }
}

// ------------------------------------------------------------------ sleep (docs/03 §5)

internal fun Ctx.sampleWakeWindowMs(): Long {
    val band = profile.wakeWindowMin ?: Range(45.0, 60.0)
    return (rng.range(Stream.SLEEP, band.min, band.max) * MINUTE).toLong()
}

internal fun Ctx.wakeUp(reason: String) {
    if (child.sleep == SleepState.AWAKE) return
    val slept = now - child.sleepSince
    child.sleep = SleepState.AWAKE
    child.sleepSince = now
    child.wakeWindowMs = sampleWakeWindowMs()
    child.currentWakeId = w.ids.take()
    child.napEndsAt = null
    child.fallAsleepAt = null
    child.playRefMsThisWake = 0
    child.wakeCauses.clear()
    // A wake after less than 20 minutes of sleep belongs to the same interruption (one care group).
    val newGroup = slept >= 20 * MINUTE
    w.stats.inc("wake")
    if (newGroup && inReferenceNight()) w.stats.inc("wake.quiet")
    if (inSleepWindow()) {
        w.stats.inc("wake.night")
        coach("first_night")
    }
    w.needs.forEach { if (it.wakeId == null) it.wakeId = child.currentWakeId }
    log(LogType.WAKE, "$name woke up ($reason) after ${formatDuration(slept)} asleep.", tag = (if (newGroup) "wake:" else "rewake:") + reason)
}

internal fun Ctx.onNapEnd() {
    child.napEndsAt = null
    wakeUp("end of a sleep cycle")
}

internal fun Ctx.onTiredDue() {
    if (child.sleep != SleepState.AWAKE) return
    openNeed(NeedKind.TIRED)
}

private fun Ctx.sleepyEnough(): Boolean =
    w.needs.any { it.kind == NeedKind.TIRED } || now - child.sleepSince >= child.wakeWindowMs / 2

private fun Ctx.blockingNeed(): Boolean = w.needs.any { n ->
    when (n.kind) {
        NeedKind.TIRED -> false
        NeedKind.WET -> n.phase.ordinal >= NeedPhase.FUSS.ordinal
        NeedKind.CLOSENESS -> child.heldBy == null
        else -> true
    }
}

/** Starts or cancels the fall-asleep timer depending on the current soothing (docs/04 §4). */
internal fun Ctx.refreshSleepOnset() {
    if (child.sleep != SleepState.AWAKE) {
        child.fallAsleepAt = null
        return
    }
    val s = w.session
    val busy = s != null && s.kind != SessionKind.SETTLE
    val bout = boutActive(now) && !soothedForBout()
    val held = child.heldBy != null
    val rocking = held && (Technique.ROCK in child.techniques || Technique.SHUSH in child.techniques)
    val settling = child.place == Place.BASSINET && (s?.kind == SessionKind.SETTLE || Technique.SHUSH in child.techniques || Technique.CALM_ROOM in child.techniques)
    val selfSettle = child.place == Place.BASSINET && !held && now - child.sleepSince >= child.wakeWindowMs * 6 / 10 &&
        (w.needs.firstOrNull { it.kind == NeedKind.TIRED }?.phase?.ordinal ?: 0) <= NeedPhase.FUSS.ordinal
    val helperSettling = w.helper.task?.kind == HelperTaskKind.SETTLE
    // A drowsy baby held calmly often dozes off even without rocking.
    val heldCalm = held && now - child.sleepSince >= child.wakeWindowMs * 6 / 10
    val can = !busy && !bout && sleepyEnough() && !blockingNeed() && (rocking || settling || selfSettle || helperSettling || heldCalm || (held && child.heldBy == Caregiver.HELPER))
    if (!can) {
        child.fallAsleepAt = null
        return
    }
    if (child.fallAsleepAt == null) {
        val (lo, hi) = when {
            helperSettling -> 4.0 to 9.0
            rocking -> 3.0 to 8.0
            settling -> 4.0 to 10.0
            heldCalm -> 6.0 to 14.0
            else -> 8.0 to 20.0
        }
        val scale = 0.5 + 0.5 * rules.effortScale
        child.fallAsleepAt = now + (rng.range(Stream.SLEEP, lo, hi) * scale * MINUTE / (0.6 + 0.4 * child.temperament.soothability)).toLong()
    }
}

internal fun Ctx.onFallAsleep() {
    child.fallAsleepAt = null
    if (child.sleep == SleepState.ASLEEP) return
    w.needs.filter { it.kind == NeedKind.TIRED }.forEach {
        resolveNeed(it, child.heldBy ?: Caregiver.PLAYER, "$name fell asleep.")
    }
    child.sleep = SleepState.ASLEEP
    child.sleepSince = now
    child.currentWakeId = null
    child.techniques.remove(Technique.ROCK)
    val night = inReferenceNight()
    val napMin = if (night) rng.range(Stream.SLEEP, 150.0, 300.0) else rng.range(Stream.SLEEP, 60.0, 180.0)
    child.napEndsAt = now + (napMin * MINUTE).toLong()
    w.stats.inc("sleep.bouts")
    val where = if (child.heldBy != null) "in ${if (child.heldBy == Caregiver.PLAYER) "your" else "$helperName's"} arms" else "in the ${child.place.label}"
    log(LogType.ASLEEP, "$name fell asleep $where.", child.heldBy, tag = child.place.name)
    if (child.heldBy == Caregiver.PLAYER) coach("first_contact_nap")
}

/** Moves the baby to the bassinet. A sleeping baby can wake during the transfer (light sleep). */
internal fun Ctx.transferToBassinet(who: Caregiver?, reason: String? = null) {
    val wasHeld = child.heldBy != null || child.place != Place.BASSINET
    child.heldBy = null
    child.holdStartedAt = null
    child.place = Place.BASSINET
    child.onTummy = false
    child.techniques.remove(Technique.ROCK)
    if (!wasHeld) return
    if (reason != null) log(LogType.NOTE, reason, who)
    if (child.sleep == SleepState.ASLEEP) {
        val asleepFor = now - child.sleepSince
        val pWake = when {
            asleepFor < 5 * MINUTE -> 0.5
            asleepFor < 15 * MINUTE -> 0.25
            else -> 0.1
        }
        if (rng.chance(Stream.SLEEP, pWake)) {
            wakeUp("transfer to the bassinet")
            coach("transfer_woke")
            openNeed(NeedKind.TIRED)
        } else {
            w.stats.inc("transfer.ok")
            log(LogType.CARE_DONE, "$name stayed asleep on their back in the bassinet.", who, tag = "transfer")
        }
    }
}

// ------------------------------------------------------------------ gas and closeness

internal fun Ctx.onGasDue() {
    child.gasDueAt = null
    if (child.sleep == SleepState.ASLEEP && rng.chance(Stream.GAS, if (inReferenceNight()) 0.4 else 0.6)) wakeUp("gas")
    openNeed(NeedKind.GAS)
}

internal fun Ctx.refreshAlone() {
    val alone = child.sleep == SleepState.AWAKE && child.heldBy == null && w.helper.task == null &&
        (w.session == null || w.session?.kind == SessionKind.BOTTLE_PREP)
    if (!alone) {
        child.aloneSince = null
        child.closenessDueAt = null
        return
    }
    if (child.aloneSince == null) child.aloneSince = now
    if (child.closenessDueAt == null) {
        child.closenessDueAt = child.aloneSince!! + (rng.range(Stream.COMFORT, 8.0, 22.0) * MINUTE).toLong()
    }
}

internal fun Ctx.onClosenessDue() {
    child.closenessDueAt = null
    if (child.sleep == SleepState.AWAKE && child.heldBy == null) openNeed(NeedKind.CLOSENESS)
}

// ------------------------------------------------------------------ evenings: cluster feeding and fussy evenings (docs/12 Phase 1)

internal fun Ctx.planEvenings(days: Int) {
    val z = clock.zoned(now)
    var clusterFirst = rng.chance(Stream.SCENARIO, 0.5)
    for (i in 0 until days) {
        val date = z.toLocalDate().plusDays(i.toLong())
        val kind = if (clusterFirst xor (i % 2 == 1)) EveningKind.CLUSTER_FEEDING else EveningKind.FUSSY_EVENING
        val startMin = rng.intRange(Stream.SCENARIO, 17 * 60 + 30, 19 * 60 + 30)
        val durMin = if (kind == EveningKind.CLUSTER_FEEDING) rng.intRange(Stream.SCENARIO, 150, 210) else rng.intRange(Stream.SCENARIO, 50, 110)
        val start = ZonedDateTime.of(date, LocalTime.of(startMin / 60, startMin % 60), clock.zone).toInstant().toEpochMilli()
        if (start + durMin * MINUTE <= clock.wall(now)) continue
        w.scenario.evenings += EveningPlan(kind, start, start + durMin * MINUTE, rng.range(Stream.SCENARIO, 0.7, 1.0))
    }
    @Suppress("UNUSED_VALUE")
    clusterFirst = clusterFirst
}

internal fun Ctx.activeCluster(t: Long = now): EveningPlan? = w.scenario.evenings.firstOrNull {
    it.kind == EveningKind.CLUSTER_FEEDING && clock.wall(t) >= it.wallStart && clock.wall(t) < it.wallEnd
}

internal fun Ctx.boutActive(t: Long = now): Boolean = w.scenario.evenings.any {
    it.kind == EveningKind.FUSSY_EVENING && clock.wall(t) >= it.wallStart && clock.wall(t) < it.wallEnd
}

internal fun Ctx.activeBoutIntensity(t: Long): Double = w.scenario.evenings.firstOrNull {
    it.kind == EveningKind.FUSSY_EVENING && clock.wall(t) >= it.wallStart && clock.wall(t) < it.wallEnd
}?.intensity ?: 0.0

internal fun Ctx.nextScenarioBoundary(): Long? {
    var best: Long? = null
    for (e in w.scenario.evenings) {
        for (b in listOf(clock.logical(e.wallStart), clock.logical(e.wallEnd))) {
            if (b > now && (best == null || b < best)) best = b
        }
    }
    return best
}

internal fun Ctx.onScenarioBoundary() {
    checkpointMilk()
    val wallNow = clock.wall(now)
    for (e in w.scenario.evenings) {
        if (!e.started && wallNow >= e.wallStart && wallNow < e.wallEnd) {
            e.started = true
            log(LogType.SCENARIO, if (e.kind == EveningKind.CLUSTER_FEEDING) "Evening: $name may want to feed again and again for a while." else "Evening: $name is fussy on and off, even after the usual checks.", tag = e.kind.name)
            if (e.kind == EveningKind.FUSSY_EVENING) coach("persistent_fussing")
        }
        if (!e.ended && wallNow >= e.wallEnd) {
            e.ended = true
            if (e.started) log(LogType.SCENARIO, "${e.kind.label} is winding down.", tag = e.kind.name + ":END")
        }
    }
    recomputeHungerDue()
}

internal fun Ctx.onDayBoundary() {
    checkpointMilk()
    child.milk.dailyFeeds = sampleDailyFeeds()
    child.milk.rateDay = dayIndex()
    recomputeHungerDue()
    ensureStoolRate()
}

internal fun Ctx.onBottleExpire() {
    if (child.bottle.preparedAt == null) return
    if (w.session?.kind == SessionKind.BOTTLE_FEED) {
        // In use: leftovers are discarded at the end of the feed.
        child.bottle.preparedAt = now
        return
    }
    child.bottle.preparedAt = null
    log(LogType.NOTE, "A prepared bottle sat out for 2 hours and was thrown away (L-FEED-03).", lesson = "L-FEED-03")
}

// ------------------------------------------------------------------ presence

internal fun Ctx.onHandsOff() {
    w.clock.handsOffAt = null
    child.techniques.remove(Technique.ROCK)
    child.techniques.remove(Technique.SHUSH)
    if (child.place == Place.CHANGING_TABLE && w.helper.task == null) {
        transferToBassinet(Caregiver.PLAYER, "You left mid-change, so $name was moved to the bassinet. Resume the change when you're back.")
    } else if (child.heldBy == Caregiver.PLAYER) {
        transferToBassinet(Caregiver.PLAYER, "You left the app, so $name was laid on their back in the bassinet.")
    }
}

// ------------------------------------------------------------------ opening state

internal fun Ctx.initProcesses() {
    child.milk.dailyFeeds = sampleDailyFeeds()
    child.milk.rateDay = dayIndex()
    ensureStoolRate()
    scheduleNextWet()
    scheduleNextRandomStool()
}

/** SC-S0-41 "One wake, two needs": the trial opens with hunger and a dirty diaper in one wake (docs/07 Part D). */
internal fun Ctx.openingFixture(hunger: Boolean, dirty: Boolean, tired: Boolean = false) {
    initProcesses()
    child.sleep = SleepState.ASLEEP
    child.sleepSince = now - 90 * MINUTE
    wakeUp("hunger")
    val m = child.milk
    m.lastFeedStart = now - (1440.0 / max(1.0, m.dailyFeeds) * MINUTE).toLong()
    m.refTime = now
    m.threshold = sampleThreshold()
    m.demandAtRef = if (hunger) m.threshold else 0.6 * m.threshold
    m.hungerDueAt = null
    if (hunger) openNeed(NeedKind.HUNGER) else recomputeHungerDue()
    if (dirty) {
        child.diaper.dirty = true
        child.diaper.wet = 1
        openNeed(NeedKind.DIRTY)
    }
    if (tired) {
        child.sleepSince = now - child.wakeWindowMs
        openNeed(NeedKind.TIRED)
    }
}

internal fun Ctx.startAsleep() {
    initProcesses()
    child.sleep = SleepState.ASLEEP
    child.sleepSince = now - 20 * MINUTE
    child.napEndsAt = now + 3 * HOUR
    val m = child.milk
    m.lastFeedStart = now - 40 * MINUTE
    m.refTime = now
    m.threshold = sampleThreshold()
    m.demandAtRef = accumulateDemand(m.lastFeedStart, now)
    recomputeHungerDue()
}
