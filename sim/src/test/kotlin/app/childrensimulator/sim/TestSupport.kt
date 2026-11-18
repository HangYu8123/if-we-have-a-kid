package app.childrensimulator.sim

import app.childrensimulator.sim.engine.Command
import app.childrensimulator.sim.engine.SimEngine
import app.childrensimulator.sim.engine.TrialSetup
import app.childrensimulator.sim.model.*
import app.childrensimulator.sim.profile.AgeProfiles
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

object TestSupport {
    val profilesText: String by lazy {
        val path = System.getProperty("profiles.path") ?: "../docs/data/age-profiles.json"
        File(path).readText()
    }
    val profiles: AgeProfiles by lazy { AgeProfiles.parse(profilesText) }
    val engine: SimEngine by lazy { SimEngine(profiles) }

    const val ZONE = "America/New_York"

    /** A fixed local start (Friday 18:00) so runs are reproducible. */
    fun startWall(hour: Int = 18, minute: Int = 0): Long =
        LocalDateTime.of(2026, 11, 13, hour, minute).atZone(ZoneId.of(ZONE)).toInstant().toEpochMilli()

    fun trial(
        seed: Long,
        preset: RealismPreset = RealismPreset.REALISTIC,
        route: FeedingRoute = FeedingRoute.FORMULA,
        startHour: Int = 18,
        ageDays: Double = 21.0,
        takeOneNightWake: Boolean = false,
        fixture: Boolean = true,
        hours: Int = 48,
    ): World = engine.newTrial(
        TrialSetup(
            childName = "Mia", route = route, preset = preset, zoneId = ZONE, seed = seed, ageDays = ageDays,
            takeOneNightWake = takeOneNightWake, startWithFixture = fixture, durationHours = hours,
        ),
        startWall(startHour),
    )
}

/**
 * A responsive caregiver bot used by the balance harness. It always picks the safe option, answers needs after
 * [responseDelayMs] (sooner once the baby fusses) and stays "in the app" so hands-on time accrues.
 */
class CareBot(private val engine: SimEngine, private val w: World, private val responseDelayMs: Long = 3 * MINUTE) {
    private var comfortStartedAt: Long? = null

    fun wall(): Long = w.clock.now + w.clock.delta

    private fun cmd(c: Command) = engine.apply(w, c, wall())

    fun run(hours: Double, stepMs: Long = 20_000) {
        val end = w.clock.now + (hours * HOUR).toLong()
        while (w.clock.now < end && !w.ended) {
            act()
            engine.advance(w, minOf(end, w.clock.now + stepMs))
        }
    }

    private fun act() {
        val s = w.session
        val now = w.clock.now
        if (s != null) {
            if (s.runningSince == null) cmd(Command.ResumeSession)
            val step = s.currentStep ?: return
            when {
                step.choices.isNotEmpty() -> {
                    val choice = step.choices.firstOrNull { it.id.startsWith("remove:") }
                        ?: step.choices.firstOrNull { it.verdict == "ok" && it.id != "again" && it.id != "longer" }
                        ?: step.choices.first()
                    cmd(Command.Choose(choice.id))
                }
                step.actionLabel != null -> {
                    val r = cmd(Command.DoStep)
                    if (r.message?.contains("too hard") == true) cmd(Command.SetTechnique(Technique.ROCK, true))
                }
            }
            return
        }
        val c = w.child
        val needs = w.needs.filter { it.owner != Caregiver.HELPER }
        fun due(n: NeedEpisode) = now - n.openedAt >= responseDelayMs || n.phase.ordinal >= NeedPhase.FUSS.ordinal
        val hunger = needs.firstOrNull { it.kind == NeedKind.HUNGER && due(it) }
        val hungerOpen = needs.any { it.kind == NeedKind.HUNGER }
        // Wet diapers are usually changed alongside a feed; dirty ones right away.
        val justFed = c.lastFeedEndedAt?.let { now - it < 20 * MINUTE } == true
        val awake = c.sleep == SleepState.AWAKE
        val night = java.time.Instant.ofEpochMilli(wall()).atZone(java.time.ZoneId.of(w.settings.zoneId)).hour.let { it >= 23 || it < 7 }
        // A typical caregiver: dirty diapers right away, wet ones alongside a feed (by day even a little wet),
        // and never waking a sleeping baby for a wet diaper.
        val diaper = needs.firstOrNull { (it.kind == NeedKind.DIRTY && due(it)) || (awake && it.kind == NeedKind.WET && (hungerOpen || justFed || it.phase.ordinal >= NeedPhase.FUSS.ordinal)) }
            ?: if (awake && justFed && !night && c.diaper.wet >= 1 && w.session == null) needs.firstOrNull { it.kind == NeedKind.HUNGER }?.let { null } ?: run {
                cmd(Command.StartSession(SessionKind.DIAPER_CHANGE)); return
            } else null
        val gas = needs.firstOrNull { it.kind == NeedKind.GAS && due(it) }
        val tired = needs.firstOrNull { it.kind == NeedKind.TIRED && due(it) }
        val close = needs.firstOrNull { it.kind == NeedKind.CLOSENESS && due(it) }
        val over = needs.firstOrNull { it.kind == NeedKind.OVERSTIM }
        when {
            over != null -> if (Technique.CALM_ROOM !in c.techniques) cmd(Command.SetTechnique(Technique.CALM_ROOM, true))
            hunger != null && c.sleep == SleepState.AWAKE -> {
                if (w.settings.route == FeedingRoute.FORMULA) {
                    if (c.bottle.preparedAt == null) {
                        if (c.heldBy == Caregiver.PLAYER) cmd(Command.PutDown(Place.BASSINET))
                        cmd(Command.StartSession(SessionKind.BOTTLE_PREP))
                    } else cmd(Command.StartSession(SessionKind.BOTTLE_FEED))
                } else cmd(Command.StartSession(SessionKind.BREASTFEED))
            }
            diaper != null -> cmd(Command.StartSession(SessionKind.DIAPER_CHANGE))
            gas != null -> cmd(Command.StartSession(SessionKind.BURP))
            tired != null -> {
                if (c.heldBy != Caregiver.PLAYER && c.sleep == SleepState.AWAKE && Technique.ROCK !in c.techniques) {
                    cmd(Command.StartSession(SessionKind.SETTLE))
                } else if (c.sleep == SleepState.AWAKE) {
                    cmd(Command.StartSession(SessionKind.SETTLE))
                }
            }
            close != null -> cmd(Command.PickUp)
            c.sleep == SleepState.ASLEEP && c.heldBy == Caregiver.PLAYER && now - c.sleepSince >= 15 * MINUTE -> cmd(Command.StartSession(SessionKind.SETTLE))
            else -> {
                if (Technique.CALM_ROOM in c.techniques && needs.none { it.kind == NeedKind.OVERSTIM }) cmd(Command.SetTechnique(Technique.CALM_ROOM, false))
                if (Technique.ROCK in c.techniques && (comfortStartedAt?.let { now - it > 10 * MINUTE } != false)) {
                    cmd(Command.SetTechnique(Technique.ROCK, false))
                    comfortStartedAt = null
                }
                if (Technique.ROCK in c.techniques && comfortStartedAt == null) comfortStartedAt = now
            }
        }
    }
}
