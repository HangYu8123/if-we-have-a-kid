package app.childrensimulator.sim.engine

import app.childrensimulator.sim.MINUTE
import app.childrensimulator.sim.model.*
import kotlin.math.max

// ------------------------------------------------------------------ what the player can observe (docs/02 §3, docs/10 §5)

/** What a caregiver can observe for a need at a phase (docs/02 §3). Public so the app can show it when the player checks the baby. */
fun cueFor(kind: NeedKind, phase: NeedPhase): String = when (kind) {
    NeedKind.HUNGER -> when (phase) {
        NeedPhase.CUE -> "Stirring, rooting, hands to mouth"
        NeedPhase.FUSS -> "Fussing, rooting and sucking on fists"
        NeedPhase.CRY -> "Crying in rhythmic bursts, still rooting"
        NeedPhase.HARD_CRY -> "Crying hard and red-faced, hard to console"
    }
    NeedKind.GAS -> when (phase) {
        NeedPhase.CUE -> "Squirming and grimacing"
        NeedPhase.FUSS -> "Pulling legs up to the belly, fussing"
        NeedPhase.CRY -> "Crying, arching, legs drawn up"
        NeedPhase.HARD_CRY -> "Screaming, stiff and arching"
    }
    NeedKind.WET -> when (phase) {
        NeedPhase.CUE -> "No obvious cue"
        NeedPhase.FUSS -> "Squirming, a bit uncomfortable"
        NeedPhase.CRY -> "Fussy and uncomfortable"
        NeedPhase.HARD_CRY -> "Crying, uncomfortable"
    }
    NeedKind.DIRTY -> when (phase) {
        NeedPhase.CUE -> "Grunting and straining, and a smell"
        NeedPhase.FUSS -> "Squirming and fussing"
        NeedPhase.CRY -> "Crying, uncomfortable"
        NeedPhase.HARD_CRY -> "Crying hard, uncomfortable"
    }
    NeedKind.TIRED -> when (phase) {
        NeedPhase.CUE -> "Staring off, quieter, yawning"
        NeedPhase.FUSS -> "Rubbing eyes, red eyelids, fussing"
        NeedPhase.CRY -> "Overtired crying, arching"
        NeedPhase.HARD_CRY -> "Frantic, overtired crying"
    }
    NeedKind.CLOSENESS -> when (phase) {
        NeedPhase.CUE -> "Looking around, making little sounds"
        NeedPhase.FUSS -> "Fussing when nobody is close"
        NeedPhase.CRY -> "Crying until picked up"
        NeedPhase.HARD_CRY -> "Crying hard for you"
    }
    NeedKind.OVERSTIM -> when (phase) {
        NeedPhase.CUE -> "Turning away, hiccups, jerky movements"
        NeedPhase.FUSS -> "Fussing and looking away"
        NeedPhase.CRY -> "Crying, overwhelmed"
        NeedPhase.HARD_CRY -> "Crying hard, overwhelmed"
    }
}

internal fun Ctx.buildView(): ChildView {
    val d = distressAt()
    val level = distressLevel(d)
    val wallNow = clock.wall(now)
    val unmet = w.needs.filter { !(it.kind == NeedKind.WET && it.phase == NeedPhase.CUE) }
    val strongest = unmet.maxByOrNull { it.phase.ordinal * 10 + priority(it.kind) }
    val held = child.heldBy != null
    val s = w.session
    val activeFeed = s?.feed?.transferStartedAt != null && s.feed?.fullnessShown == false && s.runningSince != null &&
        (s.currentStep?.id?.startsWith("feed") == true || s.currentStep?.id?.startsWith("side") == true)
    val cue = when {
        child.sleep == SleepState.ASLEEP -> if (now - child.sleepSince < 10 * MINUTE || w.needs.isNotEmpty()) "Asleep, squirming and grunting now and then (normal active sleep)" else "Asleep, breathing slowly and evenly"
        activeFeed -> if (s?.kind == SessionKind.BOTTLE_FEED) "Sucking and swallowing from the bottle" else "Latched, sucking and swallowing"
        strongest != null -> {
            val base = cueFor(strongest.kind, strongest.phase)
            if (held && d < 30 && strongest.phase.ordinal >= NeedPhase.FUSS.ordinal) "Calmer in arms for now, but: ${base.lowercase()}" else base
        }
        boutActive() -> if (d < 20) "Calmer for the moment" else "Fussing on and off with no obvious cause"
        held -> "Awake and calm, watching your face"
        else -> "Awake and calm, looking around"
    }
    val details = unmet.filter { if (activeFeed) it.kind != NeedKind.HUNGER else it !== strongest }
        .map { cueFor(it.kind, it.phase) }.toMutableList()
    if (boutActive() && strongest != null) details += "Fussy evening"
    val sessionView = s?.let { sessionView(it) }
    val milk = child.milk
    val demandNow = milk.demandAtRef + accumulateDemand(milk.refTime, now)
    val awakeFor = now - child.sleepSince
    val hints = buildList {
        val sinceFeed = now - milk.lastFeedStart
        val hunger = w.needs.firstOrNull { it.kind == NeedKind.HUNGER }
        add((if (hunger != null) "Likely hungry: " else "") + "${formatDuration(sinceFeed)} since the last feed started.")
        val dp = child.diaper
        add(
            when {
                dp.dirty -> "Diaper: dirty (smell, grunting)."
                rules.metersAlways && dp.wet > 0 -> "Diaper: wet."
                else -> "Diaper last changed ${formatDuration(now - dp.lastChangeAt)} ago" + (dp.lastCheckedAt?.let { ", checked ${formatDuration(now - it)} ago." } ?: ".")
            },
        )
        if (child.sleep == SleepState.AWAKE) {
            val ww = profile.wakeWindowMin
            add("Awake ${formatDuration(awakeFor)}." + (ww?.let { " At this age, tired cues often start after ${it.min.toInt()}–${it.max.toInt()} min." } ?: ""))
        } else {
            add("Asleep for ${formatDuration(awakeFor)}.")
        }
        if (w.needs.any { it.kind == NeedKind.GAS }) add("Gas likely: squirming and legs up after a feed.")
        if (w.needs.any { it.kind == NeedKind.CLOSENESS }) add("Wants closeness: calms when held or engaged.")
        if (w.needs.any { it.kind == NeedKind.OVERSTIM }) add("Too much going on: try a quiet, dim room.")
        if (boutActive()) add("Fussy evening: some fussing can continue even after good care.")
        activeCluster()?.let { add("Cluster feeding: expect feeds close together this evening.") }
    }
    val meters = mapOf(
        "Hunger" to (demandNow / max(0.05, milk.threshold)).coerceIn(0.0, 1.0),
        "Diaper" to (if (child.diaper.dirty) 1.0 else (child.diaper.wet * 0.4).coerceAtMost(0.9)),
        "Sleepiness" to if (child.sleep == SleepState.ASLEEP) 0.0 else (awakeFor.toDouble() / max(1L, child.wakeWindowMs)).coerceIn(0.0, 1.0),
        "Comfort" to (1.0 - d / 100.0).coerceIn(0.0, 1.0),
    )
    val helperBusy = w.helper.task?.let {
        when (it.kind) {
            HelperTaskKind.FEED -> "$helperName is feeding $name"
            HelperTaskKind.CHANGE -> "$helperName is changing $name"
            HelperTaskKind.BURP -> "$helperName is burping $name"
            HelperTaskKind.SETTLE -> "$helperName is settling $name"
            HelperTaskKind.COMFORT -> "$helperName is holding $name"
            HelperTaskKind.FINISH_SESSION -> "$helperName is finishing up"
        }
    }
    val block = activeBlock()
    val onDutyHelper = helperOnDuty()
    val coverageText = when {
        block?.kind == CoverageKind.BREAK -> "$helperName is covering your break until ${hhmm(block.end)}."
        block?.kind == CoverageKind.NIGHT_HANDOFF -> "$helperName has the rest of the night."
        onDutyHelper && inSleepWindow() -> "$helperName covers your sleep window (${settings.sleepWindow.label()})" +
            if (rules.night == NightPolicy.HELPER_EXCEPT_ONE_WAKE && settings.takeOneNightWake) ", except one wake you chose to take." else "."
        onDutyHelper -> "$helperName is on duty."
        else -> null
    }
    val nwKey = nightWindowKey()
    val nw = nwKey?.let { w.alerts.nightWindows[it] }
    val extreme = settings.preset == RealismPreset.EXTREME && w.mode == RunMode.WEEKEND_TRIAL
    val pose = Pose(
        asleep = child.sleep == SleepState.ASLEEP,
        eyesOpen = child.sleep == SleepState.AWAKE && level.ordinal < DistressLevel.CRYING.ordinal && !(strongest?.kind == NeedKind.TIRED && strongest.phase == NeedPhase.CUE && (now / 4000) % 3 == 0L),
        mouth = when {
            child.sleep == SleepState.ASLEEP -> Mouth.NEUTRAL
            activeFeed -> Mouth.SUCKING
            level.ordinal >= DistressLevel.CRYING.ordinal -> Mouth.CRY
            level == DistressLevel.FUSSING -> Mouth.FUSS
            strongest?.kind == NeedKind.HUNGER -> Mouth.ROOTING
            strongest?.kind == NeedKind.TIRED -> Mouth.YAWN
            held || s?.kind == SessionKind.PLAY_TALK -> Mouth.SMILE
            else -> Mouth.NEUTRAL
        },
        handsToMouth = w.needs.any { it.kind == NeedKind.HUNGER } && level.ordinal <= DistressLevel.FUSSING.ordinal && !activeFeed,
        legsUp = w.needs.any { it.kind == NeedKind.GAS },
        headTurn = when {
            w.needs.any { it.kind == NeedKind.OVERSTIM } -> 0.8f
            strongest?.kind == NeedKind.HUNGER && level.ordinal <= DistressLevel.STIRRING.ordinal -> 0.4f
            s?.currentStep?.id == "fullness" -> 0.7f
            else -> 0f
        },
        tears = level.ordinal >= DistressLevel.CRYING.ordinal && ageDays >= 30,
        redFace = when (level) {
            DistressLevel.HARD_CRYING -> 1f
            DistressLevel.CRYING -> 0.55f
            DistressLevel.FUSSING -> 0.2f
            else -> 0f
        },
        yawning = strongest?.kind == NeedKind.TIRED && level.ordinal <= DistressLevel.STIRRING.ordinal,
        activeSleep = child.sleep == SleepState.ASLEEP && (now - child.sleepSince < 10 * MINUTE || w.needs.isNotEmpty()),
        onTummy = child.onTummy,
    )
    return ChildView(
        now = now,
        wallNow = wallNow,
        name = name,
        ageText = e.ageModel.display(ageDays),
        stageLabel = profile.stage.label,
        sleep = child.sleep,
        place = child.place,
        heldBy = child.heldBy,
        distress = d,
        level = level,
        cue = cue,
        cueDetails = details,
        needs = w.needs.map { NeedView(it.id, it.kind, it.phase, clock.wall(it.openedAt), it.owner, it.rate == 0.0) },
        hints = hints,
        meters = meters,
        techniques = child.techniques.keys.toSet(),
        session = sessionView,
        helperBusy = helperBusy,
        onDuty = if (onDutyHelper) Caregiver.HELPER else Caregiver.PLAYER,
        coverageText = coverageText,
        lastFeedStartWall = clock.wall(milk.lastFeedStart),
        lastChangeWall = clock.wall(child.diaper.lastChangeAt),
        awakeOrAsleepSinceWall = clock.wall(child.sleepSince),
        bottleReady = child.bottle.preparedAt != null,
        trialRemainingMs = w.clock.endLogical?.let { max(0L, it - now) },
        paused = w.clock.pausedAtWall != null,
        ended = w.ended,
        nightNow = inSleepWindow(),
        sleepWindowLabel = settings.sleepWindow.label(),
        coaching = w.coaching.pending.toList(),
        pose = pose,
        presence = w.clock.presence,
        breakUntilWall = block?.takeIf { it.kind == CoverageKind.BREAK }?.let { clock.wall(it.end) },
        nightConsentAvailable = extreme,
        nightConsentActive = extreme && (nw?.consent ?: w.alerts.consentPending),
        nightAttemptsLeft = if (extreme && nw != null && nw.consent) PresetRules.NIGHT_AUDIBLE_LIMIT - nw.attempts else null,
    )
}

private fun priority(kind: NeedKind) = when (kind) {
    NeedKind.HUNGER -> 6
    NeedKind.GAS -> 5
    NeedKind.DIRTY -> 4
    NeedKind.OVERSTIM -> 3
    NeedKind.TIRED -> 2
    NeedKind.CLOSENESS -> 1
    NeedKind.WET -> 0
}

private fun Ctx.sessionView(s: CareSession): SessionView {
    val step = s.currentStep
    val running = s.runningSince != null
    val stepView = step?.let {
        val timed = it.refMs > 0 && it.choices.isEmpty() && it.actionLabel == null
        val total = scaledMs(it.refMs, s).toDouble()
        val done = s.stepDoneMs + (if (running && timed) (now - s.runningSince!!).toDouble() else 0.0)
        StepView(
            id = it.id,
            title = it.title,
            instruction = it.instruction,
            timed = timed,
            progress = if (timed) (done / total).coerceIn(0.0, 1.0) else 0.0,
            remainingMs = if (timed) max(0L, (total - done).toLong()) else 0L,
            choices = it.choices.map { c -> ChoiceView(c.id, c.label) },
            actionLabel = it.actionLabel,
        )
    }
    val f = s.feed
    return SessionView(
        id = s.id,
        kind = s.kind,
        title = s.kind.label,
        running = running,
        step = stepView,
        stepNumber = s.stepIndex + 1,
        stepCount = s.steps.size,
        completedSteps = s.stepLog.toList(),
        feedback = s.lastFeedback,
        handsOnMs = s.handsOnMs + (if (running) now - s.runningSince!! else 0L),
        intake = f?.let { (it.intake / it.satiety).coerceIn(0.0, 1.0) },
    )
}
