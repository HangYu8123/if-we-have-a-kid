package app.childrensimulator.sim.engine

import app.childrensimulator.sim.DAY
import app.childrensimulator.sim.HOUR
import app.childrensimulator.sim.LocalClock
import app.childrensimulator.sim.MINUTE
import app.childrensimulator.sim.Rng
import app.childrensimulator.sim.Stream
import app.childrensimulator.sim.TimeWindow
import app.childrensimulator.sim.age.AgeCheckpoint
import app.childrensimulator.sim.age.AgeModel
import app.childrensimulator.sim.age.GrowthPace
import app.childrensimulator.sim.model.*
import app.childrensimulator.sim.profile.AgeProfiles
import app.childrensimulator.sim.profile.ProfileResolver
import app.childrensimulator.sim.profile.ResolvedProfile
import kotlinx.serialization.json.Json
import java.time.ZoneId
import kotlin.math.max

/**
 * Deterministic discrete-event simulation of one child (docs/11 §3).
 *
 * State only changes at due events or commands, so advancing in one step or in many small steps
 * produces the same events. All randomness comes from counter-based streams stored in the world.
 */
class SimEngine(val profiles: AgeProfiles) {
    val resolver = ProfileResolver(profiles)
    val ageModel = AgeModel(profiles)

    companion object {
        const val ENGINE_VERSION = 1
        private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        fun encode(w: World): String = json.encodeToString(World.serializer(), w)
        fun decode(text: String): World = json.decodeFromString(World.serializer(), text)
        fun copy(w: World): World = decode(encode(w))
    }

    /** Creates a Weekend Trial run that starts now (docs/12 Phase 1). */
    fun newTrial(setup: TrialSetup, nowWall: Long): World {
        val settings = RunSettings(
            childName = setup.childName, route = setup.route, preset = setup.preset, sleepWindow = setup.sleepWindow,
            helperName = setup.helperName, helperRelation = setup.helperRelation, zoneId = setup.zoneId,
            takeOneNightWake = setup.takeOneNightWake, skinTone = setup.skinTone,
            presencePrompts = PresetRules.of(setup.preset).presencePromptsDefault,
        )
        val w = createWorld(setup.seed, RunMode.WEEKEND_TRIAL, settings, nowWall, setup.ageDays, nowWall + setup.durationHours * HOUR)
        val c = Ctx(this, w)
        c.planEvenings(days = setup.durationHours / 24 + 3)
        c.log(LogType.RUN_STARTED, "Weekend Trial started: ${settings.childName}, ${ageModel.display(setup.ageDays)} old, ${settings.preset.label} realism.")
        if (setup.startWithFixture) c.openingFixture(hunger = true, dirty = true) else c.startAsleep()
        c.afterChange()
        return w
    }

    internal fun createWorld(seed: Long, mode: RunMode, settings: RunSettings, nowWall: Long, ageDays: Double, endWall: Long?): World {
        val rngState = app.childrensimulator.sim.RngState()
        val rng = Rng(seed, rngState)
        val f = profiles.tuning.escalationDurationFactor
        val regU = rng.uniform(Stream.TEMPERAMENT)
        val temperament = Temperament(
            appetite = rng.range(Stream.TEMPERAMENT, 0.15, 0.85),
            regularity = when {
                regU < 0.3 -> Regularity.REGULAR
                regU < 0.8 -> Regularity.AVERAGE
                else -> Regularity.IRREGULAR
            },
            escalationFactor = rng.range(Stream.TEMPERAMENT, f[0], f[1]),
            soothability = rng.range(Stream.TEMPERAMENT, 0.35, 0.9),
        )
        val child = ChildState(
            name = settings.childName,
            skinTone = settings.skinTone,
            age = AgeCheckpoint(0, ageDays, if (mode == RunMode.WEEKEND_TRIAL) GrowthPace.HOLD else GrowthPace.HOLD),
            temperament = temperament,
            sleepSince = nowWall,
            wakeWindowMs = 50 * MINUTE,
            milk = MilkState(lastFeedStart = nowWall - 2 * HOUR, refTime = nowWall, demandAtRef = 0.0, threshold = 1.0),
            diaper = DiaperState(lastChangeAt = nowWall - HOUR, nextWetAt = nowWall + 40 * MINUTE, nextRandomStoolAt = nowWall + 6 * HOUR),
        )
        return World(
            runId = "run-" + java.lang.Long.toHexString(Rng.mix(seed xor nowWall)),
            seed = seed,
            mode = mode,
            engineVersion = ENGINE_VERSION,
            contentVersion = profiles.version,
            createdWall = nowWall,
            settings = settings,
            clock = ClockState(startLogical = nowWall, endLogical = endWall, now = nowWall),
            child = child,
            helper = HelperState(settings.helperName, settings.helperRelation),
            rng = rngState,
        ).also { it.stats.accumulatedTo = nowWall }
    }

    /** Logical "now" for a wall time: frozen while paused. */
    fun logicalNow(w: World, wallNow: Long): Long = (w.clock.pausedAtWall ?: wallNow) - w.clock.delta

    /** Processes all due events up to [untilLogical]. */
    fun advance(w: World, untilLogical: Long): List<LogEntry> {
        val c = Ctx(this, w)
        c.runUntil(untilLogical)
        return c.out
    }

    fun advanceToWall(w: World, wallNow: Long): List<LogEntry> = advance(w, max(w.clock.now, logicalNow(w, wallNow)))

    /** Catches up to [wallNow], then applies [cmd] in that batch (docs/11 §3.1). */
    fun apply(w: World, cmd: Command, wallNow: Long): CommandResult {
        val c = Ctx(this, w)
        c.runUntil(max(w.clock.now, logicalNow(w, wallNow)))
        val outcome = c.command(cmd, wallNow)
        c.afterChange()
        return CommandResult(outcome.ok, outcome.message, c.out)
    }

    fun view(w: World): ChildView = Ctx(this, w).buildView()

    fun report(w: World): CareReport = Ctx(this, w).buildReport()

    fun resolvedProfile(w: World): ResolvedProfile = Ctx(this, w).profile
}

internal data class Outcome(val ok: Boolean, val message: String? = null) {
    companion object {
        val OK = Outcome(true)
        fun fail(msg: String) = Outcome(false, msg)
        fun info(msg: String) = Outcome(true, msg)
    }
}

/** Due-event kinds, in tie-break priority order (docs/11 §3.1). */
internal enum class DueKind {
    TRIAL_END, COVERAGE, HANDS_OFF, SESSION_STEP, HELPER_TASK_END, HELPER_START, NEED_RESOLVE, NEED_PHASE, NEED_EXPIRE,
    HUNGER, STOOL_FEED, STOOL_RANDOM, WET, GAS, FALL_ASLEEP, NAP_END, TIRED, CLOSENESS, SCENARIO, DAY, BOTTLE_EXPIRE,
}

internal data class Due(val time: Long, val kind: DueKind, val ref: Long = 0)

/** Working context for one batch of processing. */
internal class Ctx(val e: SimEngine, val w: World) {
    val out = mutableListOf<LogEntry>()
    val rng = Rng(w.seed, w.rng)
    val now: Long get() = w.clock.now
    val child: ChildState get() = w.child
    val settings: RunSettings get() = w.settings
    val rules: PresetRules get() = if (w.mode == RunMode.PRACTICE) PresetRules.of(RealismPreset.GENTLE) else PresetRules.of(settings.preset)
    val clock: LocalClock get() = LocalClock(ZoneId.of(settings.zoneId), w.clock.delta)
    val name: String get() = settings.childName
    val helperName: String get() = settings.helperName

    private var cachedAge = Double.NaN
    private var cachedProfile: ResolvedProfile? = null

    fun activeMs(): Long = now - w.clock.startLogical
    val ageDays: Double get() = e.ageModel.ageAt(child.age, activeMs())
    val profile: ResolvedProfile
        get() {
            val a = ageDays
            if (a != cachedAge || cachedProfile == null) {
                cachedProfile = e.resolver.resolve(a)
                cachedAge = a
            }
            return cachedProfile!!
        }

    fun dayIndex(t: Long = now): Int = ((t - w.clock.startLogical) / DAY).toInt()

    fun inSleepWindow(t: Long = now): Boolean = clock.inWindow(t, settings.sleepWindow)
    fun inReferenceNight(t: Long = now): Boolean = clock.inWindow(t, TimeWindow.REFERENCE_NIGHT)

    // ---------------------------------------------------------------- logging & coaching

    fun log(type: LogType, text: String, who: Caregiver? = null, needIds: List<Long> = emptyList(), tag: String? = null, lesson: String? = null) {
        val entry = LogEntry(++w.logSeq, now, clock.wall(now), type, text, who, needIds, tag, lesson, inSleepWindow())
        w.log += entry
        out += entry
    }

    /** Shows a just-in-time card the first time [key] happens. */
    fun coach(key: String) {
        val card = Coaching.cards[key] ?: return
        if (!w.coaching.seen.add(key)) return
        w.coaching.pending += CoachCard(w.ids.take(), card.lessonId, card.title, card.text, "tip", now)
        if (w.coaching.pending.size > 4) w.coaching.pending.removeAt(0)
        w.stats.inc("coaching")
        log(LogType.COACHING, "Tip: ${card.title}", lesson = card.lessonId, tag = key)
    }

    fun misstep(lessonId: String?, text: String) {
        w.stats.inc("misstep")
        w.coaching.pending += CoachCard(w.ids.take(), lessonId ?: "", "Try a safer way", text, "misstep", now)
        if (w.coaching.pending.size > 4) w.coaching.pending.removeAt(0)
        log(LogType.MISSTEP, text, Caregiver.PLAYER, lesson = lessonId)
    }

    // ---------------------------------------------------------------- the run loop

    fun runUntil(until: Long) {
        if (w.ended || w.clock.pausedAtWall != null) return
        var lastT = Long.MIN_VALUE
        var sameTimeCount = 0
        while (true) {
            val d = nextDue() ?: break
            if (d.time > until) break
            val t = max(d.time, now)
            accumulate(t)
            w.clock.now = t
            if (t == lastT) {
                if (++sameTimeCount > 2000) error("Simulation stuck at $t on ${d.kind}")
            } else {
                lastT = t
                sameTimeCount = 0
            }
            handle(d)
            afterChange()
            if (w.ended) return
        }
        accumulate(until)
        if (until > now) w.clock.now = until
    }

    /** Processes exactly one due event (used by projections). */
    fun stepTo(d: Due) {
        val t = max(d.time, now)
        accumulate(t)
        w.clock.now = t
        handle(d)
        afterChange()
    }

    private fun better(a: Due?, b: Due): Due =
        if (a == null) b
        else if (b.time != a.time) (if (b.time < a.time) b else a)
        else if (b.kind.ordinal != a.kind.ordinal) (if (b.kind.ordinal < a.kind.ordinal) b else a)
        else if (b.ref < a.ref) b else a

    fun nextDue(): Due? {
        var best: Due? = null
        fun consider(time: Long?, kind: DueKind, ref: Long = 0) {
            if (time != null) best = better(best, Due(time, kind, ref))
        }
        w.clock.endLogical?.let { consider(it, DueKind.TRIAL_END) }
        consider(nextCoverageBoundary(), DueKind.COVERAGE)
        consider(w.clock.handsOffAt, DueKind.HANDS_OFF)
        consider(sessionStepDue(), DueKind.SESSION_STEP)
        consider(w.helper.task?.endsAt, DueKind.HELPER_TASK_END)
        consider(w.helper.nextActionAt, DueKind.HELPER_START)
        for (n in w.needs) {
            consider(n.resolveAt, DueKind.NEED_RESOLVE, n.id)
            consider(phaseDue(n), DueKind.NEED_PHASE, n.id)
            consider(n.expiresAt, DueKind.NEED_EXPIRE, n.id)
        }
        if (w.needs.none { it.kind == NeedKind.HUNGER }) consider(child.milk.hungerDueAt, DueKind.HUNGER)
        consider(child.diaper.feedStoolAt, DueKind.STOOL_FEED)
        consider(child.diaper.nextRandomStoolAt, DueKind.STOOL_RANDOM)
        consider(child.diaper.nextWetAt, DueKind.WET)
        consider(child.gasDueAt, DueKind.GAS)
        consider(child.fallAsleepAt, DueKind.FALL_ASLEEP)
        consider(child.napEndsAt, DueKind.NAP_END)
        if (child.sleep == SleepState.AWAKE && w.needs.none { it.kind == NeedKind.TIRED }) {
            consider(child.sleepSince + child.wakeWindowMs, DueKind.TIRED)
        }
        if (w.needs.none { it.kind == NeedKind.CLOSENESS }) consider(child.closenessDueAt, DueKind.CLOSENESS)
        consider(nextScenarioBoundary(), DueKind.SCENARIO)
        consider(w.clock.startLogical + (dayIndex() + 1) * DAY, DueKind.DAY)
        child.bottle.preparedAt?.let { consider(it + 2 * HOUR, DueKind.BOTTLE_EXPIRE) }
        return best
    }

    private fun handle(d: Due) {
        when (d.kind) {
            DueKind.TRIAL_END -> endRun("complete")
            DueKind.COVERAGE -> onCoverageBoundary()
            DueKind.HANDS_OFF -> onHandsOff()
            DueKind.SESSION_STEP -> onSessionStepDue()
            DueKind.HELPER_TASK_END -> onHelperTaskEnd()
            DueKind.HELPER_START -> onHelperStart()
            DueKind.NEED_RESOLVE -> w.needs.find { it.id == d.ref }?.let { onComfortResolve(it) }
            DueKind.NEED_PHASE -> w.needs.find { it.id == d.ref }?.let { onPhaseDue(it) }
            DueKind.NEED_EXPIRE -> w.needs.find { it.id == d.ref }?.let { onNeedExpire(it) }
            DueKind.HUNGER -> onHungerDue()
            DueKind.STOOL_FEED -> onStool(fromFeed = true)
            DueKind.STOOL_RANDOM -> onStool(fromFeed = false)
            DueKind.WET -> onWet()
            DueKind.GAS -> onGasDue()
            DueKind.FALL_ASLEEP -> onFallAsleep()
            DueKind.NAP_END -> onNapEnd()
            DueKind.TIRED -> onTiredDue()
            DueKind.CLOSENESS -> onClosenessDue()
            DueKind.SCENARIO -> onScenarioBoundary()
            DueKind.DAY -> onDayBoundary()
            DueKind.BOTTLE_EXPIRE -> onBottleExpire()
        }
    }

    /** Re-derives attendance, comfort timers, sleep onset and alert eligibility after any change. */
    fun afterChange() {
        if (w.ended) return
        refreshNeeds()
        refreshComfortResolutions()
        refreshAlone()
        refreshSleepOnset()
        refreshHelper()
        refreshEligibility()
        checkPracticeObjective()
    }

    // ---------------------------------------------------------------- stats accumulation (piecewise constant between events)

    fun accumulate(t: Long) {
        val from = w.stats.accumulatedTo
        if (t <= from) return
        val dt = (t - from).toDouble()
        val s = w.stats
        val night = inSleepWindow(from)
        val sessionRunning = w.session?.runningSince != null
        if (sessionRunning || child.heldBy == Caregiver.PLAYER) {
            s.add("handsOn.PLAYER", dt)
            if (night) s.add("handsOn.night.PLAYER", dt)
        }
        if (w.helper.task != null) {
            s.add("busy.HELPER", dt)
            if (night) s.add("busy.night.HELPER", dt)
        }
        if (child.sleep == SleepState.ASLEEP) s.add("asleep", dt)
        val maxPhase = w.needs.filter { it.rate > 0.0 }.maxOfOrNull { it.phase.ordinal } ?: -1
        when {
            maxPhase >= NeedPhase.CRY.ordinal -> s.add("crying", dt)
            maxPhase == NeedPhase.FUSS.ordinal || (boutActive(from) && !soothedForBout()) -> s.add("fussing", dt)
        }
        s.accumulatedTo = t
    }

    // ---------------------------------------------------------------- commands

    fun command(cmd: Command, wallNow: Long): Outcome {
        if (w.ended && cmd !is Command.DismissCoaching && cmd !is Command.QuizAnswer && cmd !is Command.SetPresence) {
            return Outcome.fail("The trial has ended.")
        }
        val paused = w.clock.pausedAtWall != null
        if (paused && cmd !is Command.Resume && cmd !is Command.EndRun && cmd !is Command.DismissCoaching &&
            cmd !is Command.QuizAnswer && cmd !is Command.SetPresence && cmd !is Command.UpdateSettings
        ) {
            return Outcome.fail("The run is paused. Resume to continue care.")
        }
        return when (cmd) {
            is Command.StartSession -> startSession(cmd.kind)
            is Command.Choose -> sessionChoose(cmd.choiceId)
            Command.DoStep -> sessionDoStep()
            Command.SuspendSession -> { suspendSession("paused"); Outcome.OK }
            Command.ResumeSession -> resumeSession()
            Command.StopSession -> stopSession()
            Command.HandOffSession -> handOffSession()
            Command.CheckDiaper -> checkDiaper()
            Command.PickUp -> pickUp()
            is Command.PutDown -> putDown(cmd.place)
            is Command.SetTechnique -> setTechnique(cmd.technique, cmd.on)
            is Command.StartBreak -> startBreak(cmd.minutes)
            Command.EndBreak -> endBreak()
            Command.HandOffNeeds -> handOffNeeds()
            Command.HandOffNight -> handOffNight()
            Command.Pause -> pause(wallNow)
            Command.Resume -> resume(wallNow)
            Command.EndRun -> { endRun("ended early"); Outcome.OK }
            is Command.SetPresence -> setPresence(cmd.presence)
            is Command.UpdateSettings -> updateSettings(cmd.settings)
            is Command.NightConsent -> nightConsent(cmd.granted)
            is Command.AlertOpened -> alertOpened(cmd.groupKey)
            is Command.SilenceAlert -> silenceAlert(cmd.groupKey)
            is Command.DismissCoaching -> { w.coaching.pending.removeAll { it.id == cmd.cardId }; Outcome.OK }
            is Command.QuizAnswer -> {
                (if (cmd.post) w.quiz.post else w.quiz.pre)[cmd.questionId] = cmd.answerId
                Outcome.OK
            }
            is Command.DebugSkip -> debugSkip(cmd.ms)
        }
    }

    private fun pause(wallNow: Long): Outcome {
        if (w.clock.pausedAtWall != null) return Outcome.OK
        suspendSession("run paused")
        child.techniques.clear()
        checkpointMilk()
        w.clock.pausedAtWall = wallNow
        log(LogType.PAUSE, "Run paused. Both clocks are frozen and alerts are cancelled.", Caregiver.PLAYER)
        return Outcome.OK
    }

    private fun resume(wallNow: Long): Outcome {
        val pausedAt = w.clock.pausedAtWall ?: return Outcome.OK
        val pausedMs = max(0L, wallNow - pausedAt)
        w.clock.delta += pausedMs
        w.clock.totalPausedMs += pausedMs
        w.clock.pausedAtWall = null
        // Local-time anchored processes are re-planned from the state at the pause boundary.
        recomputeHungerDue()
        log(LogType.RESUME, "Run resumed after ${formatDuration(pausedMs)}.", Caregiver.PLAYER)
        return Outcome.OK
    }

    private fun debugSkip(ms: Long): Outcome {
        if (ms <= 0) return Outcome.OK
        w.clock.delta -= ms
        val target = now + ms
        suspendSession("time skipped")
        runUntil(target)
        log(LogType.NOTE, "Debug: skipped ahead ${formatDuration(ms)}.")
        return Outcome.OK
    }

    fun endRun(reason: String) {
        if (w.ended) return
        w.session?.let { finishSessionEarly(it, "the run ended") }
        w.session = null
        w.helper.task = null
        w.helper.nextActionAt = null
        child.techniques.clear()
        w.ended = true
        w.endReason = reason
        coach("trial_end")
        log(LogType.RUN_ENDED, if (reason == "complete") "The ${w.mode.name.lowercase().replace('_', ' ')} is complete." else "The run ended early.")
    }

    private fun setPresence(p: Presence): Outcome {
        if (w.clock.presence == p) return Outcome.OK
        w.clock.presence = p
        if (p == Presence.BACKGROUND) {
            suspendSession("app in background")
            w.clock.handsOffAt = now + PresetRules.HANDS_OFF_GRACE_MS
        } else {
            w.clock.handsOffAt = null
        }
        return Outcome.OK
    }

    private fun updateSettings(s: RunSettings): Outcome {
        val old = settings
        w.settings = s.copy(childName = old.childName, route = old.route)
        w.helper.name = s.helperName
        w.helper.relation = s.helperRelation
        if (old.preset != s.preset) {
            log(LogType.SETTINGS, "Realism changed to ${s.preset.label}. New sessions and alerts use it.", Caregiver.PLAYER)
        }
        if (old.sleepWindow != s.sleepWindow) log(LogType.SETTINGS, "Sleep window set to ${s.sleepWindow.label()}.", Caregiver.PLAYER)
        return Outcome.OK
    }

    fun formatDuration(ms: Long): String {
        val m = ms / MINUTE
        return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
    }
}
