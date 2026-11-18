package app.childrensimulator.sim.engine

import app.childrensimulator.sim.MINUTE
import app.childrensimulator.sim.Stream
import app.childrensimulator.sim.model.*
import app.childrensimulator.sim.profile.Range
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

// ------------------------------------------------------------------ need lifecycle (docs/02 §1, docs/03 §9)

internal fun Ctx.openNeed(kind: NeedKind): NeedEpisode {
    w.needs.firstOrNull { it.kind == kind }?.let { return it }
    val n = NeedEpisode(
        id = w.ids.take(), kind = kind, openedAt = now,
        phaseDurationMs = samplePhaseMs(kind, NeedPhase.CUE), lastUpdate = now,
    )
    n.wakeId = child.currentWakeId
    n.owner = ownerForNewNeed(n.wakeId)
    if (kind == NeedKind.GAS) n.expiresAt = now + (rng.range(Stream.GAS, 20.0, 45.0) * MINUTE).toLong()
    w.needs += n
    w.session?.let { s -> if (sessionAddresses(s, kind) || (kind == NeedKind.GAS && (s.kind == SessionKind.BOTTLE_FEED || s.kind == SessionKind.BREASTFEED))) s.needIds += n.id }
    w.stats.inc("needs")
    w.stats.inc("needs.${kind.name}")
    val text = when (kind) {
        NeedKind.HUNGER -> "$name is stirring and rooting, with hands to mouth."
        NeedKind.GAS -> "$name is squirming and grimacing."
        NeedKind.WET -> "$name's diaper is wet (no obvious cue)."
        NeedKind.DIRTY -> "$name is grunting and straining. There's a smell."
        NeedKind.TIRED -> "$name is staring off and yawning."
        NeedKind.CLOSENESS -> "$name is looking around and making little sounds."
        NeedKind.OVERSTIM -> "$name is turning away, with hiccups and jerky movements."
    }
    log(LogType.NEED_OPENED, text, needIds = listOf(n.id), tag = kind.name)
    when (kind) {
        NeedKind.HUNGER -> {
            coach("first_hunger_cue")
            if (settings.route == FeedingRoute.FORMULA) coach("route_formula") else coach("route_breast")
            if (activeCluster() != null) coach("cluster_feeding")
        }
        NeedKind.GAS -> coach("first_gas")
        NeedKind.OVERSTIM -> coach("overstim")
        else -> Unit
    }
    if (n.owner == Caregiver.HELPER) w.helper.nextActionAt = w.helper.nextActionAt ?: helperResponseTime()
    return n
}

internal fun Ctx.samplePhaseMs(kind: NeedKind, from: NeedPhase): Long {
    val p = profile
    val band: Range = when (from) {
        NeedPhase.CUE -> p.cueToFussMin
        NeedPhase.FUSS -> p.fussToCryMin
        NeedPhase.CRY -> p.cryToHardCryMin
        NeedPhase.HARD_CRY -> p.handoffAfterCryingMin
    } ?: Range(5.0, 10.0)
    val kindFactor = when (kind) {
        NeedKind.WET -> if (from == NeedPhase.CUE) 12.0 else 3.0
        NeedKind.DIRTY -> 1.5
        NeedKind.TIRED -> 1.2
        NeedKind.CLOSENESS -> 1.5
        NeedKind.GAS -> 0.8
        NeedKind.OVERSTIM -> 0.6
        NeedKind.HUNGER -> 1.0
    }
    val minutes = rng.range(Stream.ESCALATION, band.min, band.max) * child.temperament.escalationFactor * kindFactor
    return (max(0.5, minutes) * MINUTE).toLong()
}

internal fun Ctx.phaseDue(n: NeedEpisode): Long? {
    if (n.rate <= 0.0) return null
    if (n.phase == NeedPhase.HARD_CRY && (w.mode == RunMode.PRACTICE || n.interventionDone)) return null
    val remaining = n.phaseDurationMs - n.phaseElapsedMs
    return n.lastUpdate + max(0L, ceil(remaining / n.rate).toLong())
}

private fun Ctx.checkpoint(n: NeedEpisode) {
    n.phaseElapsedMs += (now - n.lastUpdate) * n.rate
    n.lastUpdate = now
}

internal fun Ctx.onPhaseDue(n: NeedEpisode) {
    checkpoint(n)
    if (n.phaseElapsedMs + 1 < n.phaseDurationMs) return
    when (n.phase) {
        NeedPhase.CUE -> setPhase(n, NeedPhase.FUSS, samplePhaseMs(n.kind, NeedPhase.FUSS))
        NeedPhase.FUSS -> {
            n.cryStartedAt = now
            n.capTotalMs = samplePhaseMs(n.kind, NeedPhase.HARD_CRY) + rules.graceMs
            setPhase(n, NeedPhase.CRY, samplePhaseMs(n.kind, NeedPhase.CRY))
            if (n.owner != Caregiver.HELPER && !inSleepWindow()) coach("crying_continues")
            if (n.kind == NeedKind.TIRED) coach("overtired")
        }
        NeedPhase.CRY -> setPhase(n, NeedPhase.HARD_CRY, max(5 * MINUTE, n.capTotalMs - n.phaseDurationMs))
        NeedPhase.HARD_CRY -> careIntervention(n)
    }
}

private fun Ctx.setPhase(n: NeedEpisode, phase: NeedPhase, durationMs: Long) {
    n.phase = phase
    if (phase.ordinal > n.peakPhase.ordinal) n.peakPhase = phase
    n.phaseElapsedMs = 0.0
    n.phaseDurationMs = durationMs
    n.lastUpdate = now
    val what = when (phase) {
        NeedPhase.CUE -> "is showing early cues"
        NeedPhase.FUSS -> "is fussing"
        NeedPhase.CRY -> "is crying"
        NeedPhase.HARD_CRY -> "is crying hard"
    }
    log(LogType.NEED_PHASE, "$name $what (${n.kind.label.lowercase()}).", needIds = listOf(n.id), tag = "${n.kind.name}:${phase.name}")
    if (child.sleep == SleepState.ASLEEP && phase.ordinal >= NeedPhase.FUSS.ordinal) wakeUp("discomfort")
}

/** The game-only unattended limit: the helper steps in and the log shows it (docs/04 §1, docs/10 §9). */
private fun Ctx.careIntervention(n: NeedEpisode) {
    n.interventionDone = true
    val crying = n.cryStartedAt?.let { now - it } ?: 0
    val group = w.needs.filter { it.owner != Caregiver.HELPER && !inPlayerSession(it) }
    group.forEach { it.owner = Caregiver.HELPER }
    w.stats.inc("intervention")
    log(
        LogType.INTERVENTION,
        "$helperName stepped in after ${formatDuration(crying)} of unanswered crying and took over ${group.joinToString { it.kind.label.lowercase() }}.",
        Caregiver.HELPER, group.map { it.id },
    )
    coach("safe_break")
    w.helper.nextActionAt = w.helper.nextActionAt ?: now
}

internal fun Ctx.inPlayerSession(n: NeedEpisode): Boolean = w.session?.needIds?.contains(n.id) == true

internal fun Ctx.resolveNeed(n: NeedEpisode, who: Caregiver?, how: String) {
    if (!w.needs.remove(n)) return
    w.stats.inc("resolved.${n.kind.name}.${who?.name ?: "SELF"}")
    log(LogType.NEED_RESOLVED, how, who, listOf(n.id), tag = n.kind.name)
    w.alerts.groups.filter { it.closedAt == null && n.id in it.needIds }.forEach { g ->
        if (g.needIds.all { id -> w.needs.none { it.id == id } }) g.closedAt = now
    }
    maybeDebrief(n)
}

/** A short "why?" debrief after a wake that reached crying (docs/04 §3). */
private fun Ctx.maybeDebrief(resolved: NeedEpisode) {
    val wake = resolved.wakeId ?: return
    if (wake == child.currentWakeId || child.currentWakeId == null) {
        val label = resolved.kind.label.lowercase()
        if (label !in child.wakeCauses) child.wakeCauses += label
    }
    if (w.needs.any { it.wakeId == wake }) return
    if (resolved.peakPhase.ordinal < NeedPhase.CRY.ordinal) return
    if (child.wakeCauses.isEmpty()) return
    val sinceFeed = now - child.milk.lastFeedStart
    log(
        LogType.DEBRIEF,
        "Why the crying: " + child.wakeCauses.joinToString(" + ") + ". Evidence you could see: the cues on screen, " +
            formatDuration(sinceFeed) + " since the last feed started, and the diaper last changed " +
            formatDuration(now - child.diaper.lastChangeAt) + " ago.",
        tag = "debrief",
    )
}

internal fun Ctx.onNeedExpire(n: NeedEpisode) {
    if (n.kind == NeedKind.GAS) resolveNeed(n, null, "The gas seems to have passed on its own.")
}

// ------------------------------------------------------------------ attendance and comfort (docs/02 §5, docs/04 §4)

internal enum class Effect { FULL, PARTIAL, NONE, WORSE }

internal fun holdEffect(kind: NeedKind): Effect = when (kind) {
    NeedKind.CLOSENESS -> Effect.FULL
    else -> Effect.PARTIAL
}

internal fun techniqueEffect(t: Technique, kind: NeedKind): Effect = when (t) {
    Technique.ROCK -> when (kind) {
        NeedKind.TIRED -> Effect.FULL
        NeedKind.HUNGER, NeedKind.GAS, NeedKind.WET, NeedKind.DIRTY, NeedKind.OVERSTIM, NeedKind.CLOSENESS -> Effect.PARTIAL
    }
    Technique.SHUSH -> when (kind) {
        NeedKind.TIRED, NeedKind.OVERSTIM -> Effect.FULL
        NeedKind.HUNGER, NeedKind.GAS -> Effect.PARTIAL
        else -> Effect.NONE
    }
    Technique.CALM_ROOM -> when (kind) {
        NeedKind.TIRED, NeedKind.OVERSTIM -> Effect.FULL
        NeedKind.CLOSENESS -> Effect.WORSE
        else -> Effect.NONE
    }
}

/** Effects of the comfort currently given by whoever holds the baby. */
internal fun Ctx.comfortEffects(kind: NeedKind): List<Effect> {
    val effects = mutableListOf<Effect>()
    if (child.heldBy != null) effects += holdEffect(kind)
    if (child.heldBy == Caregiver.PLAYER || child.place == Place.BASSINET) {
        child.techniques.keys.forEach { t ->
            if (t == Technique.ROCK && child.heldBy == null) return@forEach
            effects += techniqueEffect(t, kind)
        }
    } else {
        child.techniques[Technique.CALM_ROOM]?.let { effects += techniqueEffect(Technique.CALM_ROOM, kind) }
    }
    val s = w.session
    if (s != null && s.runningSince != null && (s.kind == SessionKind.PLAY_TALK || s.kind == SessionKind.TUMMY_TIME)) {
        effects += when (kind) {
            NeedKind.CLOSENESS -> Effect.FULL
            NeedKind.HUNGER, NeedKind.TIRED, NeedKind.OVERSTIM, NeedKind.GAS -> Effect.WORSE
            else -> Effect.NONE
        }
    }
    return effects
}

internal fun sessionAddresses(s: CareSession, kind: NeedKind): Boolean = when (s.kind) {
    SessionKind.BOTTLE_FEED, SessionKind.BREASTFEED -> kind == NeedKind.HUNGER || (kind == NeedKind.GAS && s.currentStep?.id?.let { it.contains("burp") || it == "pat2" || it.endsWith("_switch") } == true)
    SessionKind.BURP -> kind == NeedKind.GAS
    SessionKind.DIAPER_CHANGE -> kind == NeedKind.WET || kind == NeedKind.DIRTY
    SessionKind.SETTLE -> kind == NeedKind.TIRED
    SessionKind.PLAY_TALK, SessionKind.TUMMY_TIME -> kind == NeedKind.CLOSENESS
    SessionKind.BOTTLE_PREP -> false
}

internal fun Ctx.escalationRate(n: NeedEpisode): Double {
    if (n.owner == Caregiver.HELPER) return 0.0
    if (w.helper.task?.needIds?.contains(n.id) == true) return 0.0
    val s = w.session
    if (s != null && s.runningSince != null && n.id in s.needIds && sessionAddresses(s, n.kind)) return 0.0
    if (child.sleep == SleepState.ASLEEP) return if (n.kind == NeedKind.WET) 0.0 else 0.5
    val effects = comfortEffects(n.kind)
    return when {
        Effect.FULL in effects -> 0.0
        Effect.PARTIAL in effects -> 0.3
        Effect.WORSE in effects -> 1.5
        else -> 1.0
    }
}

internal fun Ctx.refreshNeeds() {
    for (n in w.needs) {
        val r = escalationRate(n)
        if (r != n.rate) {
            n.phaseElapsedMs += (now - n.lastUpdate) * n.rate
            n.lastUpdate = now
            n.rate = r
        }
    }
}

/** Needs that a FULL comfort technique resolves over a few minutes (closeness, overstimulation). */
internal fun Ctx.refreshComfortResolutions() {
    for (n in w.needs) {
        val resolvable = when (n.kind) {
            NeedKind.CLOSENESS -> child.heldBy != null || (w.session?.let { it.runningSince != null && (it.kind == SessionKind.PLAY_TALK || it.kind == SessionKind.TUMMY_TIME) } == true)
            NeedKind.OVERSTIM -> (Technique.SHUSH in child.techniques || Technique.CALM_ROOM in child.techniques) &&
                w.session?.kind != SessionKind.PLAY_TALK && w.session?.kind != SessionKind.TUMMY_TIME
            else -> false
        }
        if (resolvable && n.resolveAt == null) {
            val minutes = if (n.kind == NeedKind.CLOSENESS) rng.range(Stream.COMFORT, 1.0, 3.0) else rng.range(Stream.COMFORT, 2.0, 4.0)
            n.resolveAt = now + (minutes * MINUTE / (0.5 + child.temperament.soothability)).toLong()
        } else if (!resolvable) {
            n.resolveAt = null
        }
    }
}

internal fun Ctx.onComfortResolve(n: NeedEpisode) {
    n.resolveAt = null
    val who = if (child.heldBy == Caregiver.HELPER) Caregiver.HELPER else Caregiver.PLAYER
    when (n.kind) {
        NeedKind.CLOSENESS -> resolveNeed(n, who, "$name calmed with closeness and attention.")
        NeedKind.OVERSTIM -> resolveNeed(n, who, "$name relaxed in the quieter, dimmer space.")
        else -> Unit
    }
}

/** Visible distress 0–100 at time [t] (docs/04 §1): the strongest unmet need, eased by comfort, plus any fussy bout. */
internal fun Ctx.distressAt(t: Long = now): Double {
    if (child.sleep == SleepState.ASLEEP) return 0.0
    var d = 0.0
    for (n in w.needs) {
        val base = when (n.phase) {
            NeedPhase.CUE -> if (n.kind == NeedKind.WET) 4.0 else 16.0
            NeedPhase.FUSS -> 38.0
            NeedPhase.CRY -> 62.0
            NeedPhase.HARD_CRY -> 86.0
        }
        d = max(d, base * (1.0 - relief(n.kind, t)))
    }
    if (boutActive(t)) d = max(d, 46.0 * (activeBoutIntensity(t)) * (1.0 - boutRelief(t)))
    return d.coerceIn(0.0, 100.0)
}

private fun Ctx.relief(kind: NeedKind, t: Long): Double {
    var keep = 1.0
    val soothe = 0.6 + 0.4 * child.temperament.soothability
    if (child.heldBy != null) {
        val ramp = ramp(t, child.holdStartedAt, 45_000.0)
        keep *= 1.0 - ramp * soothe * (if (holdEffect(kind) == Effect.FULL) 0.8 else 0.35)
    }
    child.techniques.forEach { (tech, start) ->
        if (tech == Technique.ROCK && child.heldBy == null) return@forEach
        val eff = techniqueEffect(tech, kind)
        val r = ramp(t, start, if (tech == Technique.SHUSH) 30_000.0 else 60_000.0)
        keep *= when (eff) {
            Effect.FULL -> 1.0 - r * soothe * 0.7
            Effect.PARTIAL -> 1.0 - r * soothe * 0.3
            Effect.NONE -> 1.0
            Effect.WORSE -> 1.0 + 0.15 * r
        }
    }
    return (1.0 - keep).coerceIn(-0.2, 0.85)
}

private fun ramp(t: Long, start: Long?, rampMs: Double): Double = if (start == null) 0.0 else min(1.0, max(0.0, (t - start) / rampMs))

internal fun Ctx.boutRelief(t: Long): Double {
    var keep = 1.0
    if (child.heldBy != null) keep *= 1.0 - 0.25 * ramp(t, child.holdStartedAt, 45_000.0)
    child.techniques.forEach { (tech, start) ->
        if (tech == Technique.ROCK && child.heldBy == null) return@forEach
        keep *= 1.0 - 0.22 * ramp(t, start, 60_000.0)
    }
    return (1.0 - keep).coerceIn(0.0, 0.7)
}

internal fun Ctx.soothedForBout(): Boolean = child.heldBy != null && (Technique.ROCK in child.techniques || Technique.SHUSH in child.techniques)

internal fun Ctx.distressLevel(d: Double = distressAt()): DistressLevel = when {
    child.sleep == SleepState.ASLEEP -> DistressLevel.ASLEEP
    d < 10 -> DistressLevel.CALM
    d < 26 -> DistressLevel.STIRRING
    d < 50 -> DistressLevel.FUSSING
    d < 75 -> DistressLevel.CRYING
    else -> DistressLevel.HARD_CRYING
}

@Suppress("unused")
private fun smooth(x: Double) = 1.0 - exp(-x)
