package app.childrensimulator.sim.model

import app.childrensimulator.sim.RngState
import app.childrensimulator.sim.TimeWindow
import app.childrensimulator.sim.age.AgeCheckpoint
import kotlinx.serialization.Serializable

enum class RunMode { WEEKEND_TRIAL, PRACTICE }

enum class FeedingRoute(val profileKey: String, val label: String) {
    BREAST("breast", "Breast/chestfeeding"),
    FORMULA("formula", "Formula by bottle"),
}

enum class RealismPreset(val label: String) {
    GENTLE("Gentle"), STANDARD("Standard"), REALISTIC("Realistic"), EXTREME("Extreme Real"),
}

enum class Caregiver { PLAYER, HELPER }

/** Needs built for the first trial (docs/02 §1). */
enum class NeedKind(val docId: String, val label: String) {
    HUNGER("N01", "Hunger"),
    GAS("N04", "Gas"),
    WET("N05", "Wet diaper"),
    DIRTY("N06", "Dirty diaper"),
    TIRED("N08", "Tiredness"),
    CLOSENESS("N20", "Closeness"),
    OVERSTIM("N24", "Too much stimulation"),
}

/** cue → fuss → cry → hard cry (docs/03 §9). */
enum class NeedPhase { CUE, FUSS, CRY, HARD_CRY }

enum class SleepState { AWAKE, ASLEEP }

enum class Place(val label: String) {
    BASSINET("bassinet"), ARMS("arms"), CHANGING_TABLE("changing table"), PLAY_MAT("play mat"),
}

/** Comfort techniques that run while the player keeps them going (holding is tracked by [ChildState.heldBy]). */
enum class Technique(val label: String) { ROCK("Rock or sway"), SHUSH("Shush or hum"), CALM_ROOM("Dim, quiet room") }

enum class Presence { FOREGROUND, BACKGROUND }

enum class SessionKind(val label: String, val mechanic: String) {
    BOTTLE_PREP("Prepare a formula bottle", "M02"),
    BOTTLE_FEED("Bottle feed", "M03"),
    BREASTFEED("Breast/chestfeed", "M04"),
    BURP("Burp", "M05"),
    DIAPER_CHANGE("Change the diaper", "M06"),
    SETTLE("Settle to sleep", "M12"),
    PLAY_TALK("Talk and play", "M18"),
    TUMMY_TIME("Tummy time", "M17"),
}

enum class Regularity(val key: String) { REGULAR("regular"), AVERAGE("average"), IRREGULAR("irregular") }

@Serializable
data class Temperament(
    /** 0..1 percentile used to place daily feed targets inside the profile band. */
    val appetite: Double,
    val regularity: Regularity,
    /** Multiplier on escalation durations, from tuning.escalationDurationFactor. */
    val escalationFactor: Double,
    /** 0..1: how readily comfort works. */
    val soothability: Double,
)

@Serializable
class MilkState(
    var lastFeedStart: Long,
    var refTime: Long,
    var demandAtRef: Double,
    var threshold: Double,
    var rateDay: Int = -1,
    var dailyFeeds: Double = 8.0,
    var hungerDueAt: Long? = null,
    var lastFeedEnd: Long? = null,
    var lastAcceptedFraction: Double = 1.0,
)

@Serializable
class DiaperState(
    var wet: Int = 0,
    var dirty: Boolean = false,
    var blowout: Boolean = false,
    var lastChangeAt: Long,
    var nextWetAt: Long,
    var nextRandomStoolAt: Long,
    var feedStoolAt: Long? = null,
    var stoolDay: Int = -1,
    var stoolsPerDay: Double = 3.0,
    var lastCheckedAt: Long? = null,
)

@Serializable
class BottleState(var preparedAt: Long? = null)

@Serializable
class ChildState(
    var name: String,
    var skinTone: Int,
    var age: AgeCheckpoint,
    val temperament: Temperament,
    var sleep: SleepState = SleepState.AWAKE,
    var sleepSince: Long,
    var wakeWindowMs: Long,
    var currentWakeId: Long? = null,
    var place: Place = Place.BASSINET,
    var heldBy: Caregiver? = null,
    var holdStartedAt: Long? = null,
    val techniques: MutableMap<Technique, Long> = mutableMapOf(),
    var fallAsleepAt: Long? = null,
    var napEndsAt: Long? = null,
    var aloneSince: Long? = null,
    var closenessDueAt: Long? = null,
    var playRefMsThisWake: Long = 0,
    val milk: MilkState,
    val diaper: DiaperState,
    val bottle: BottleState = BottleState(),
    var gasRisk: Double = 0.0,
    var gasDueAt: Long? = null,
    var lastFeedEndedAt: Long? = null,
    val wakeCauses: MutableList<String> = mutableListOf(),
    var onTummy: Boolean = false,
)

@Serializable
class NeedEpisode(
    val id: Long,
    val kind: NeedKind,
    val openedAt: Long,
    var phase: NeedPhase = NeedPhase.CUE,
    var phaseElapsedMs: Double = 0.0,
    var phaseDurationMs: Long,
    var rate: Double = 1.0,
    var lastUpdate: Long,
    var capTotalMs: Long = 0,
    var cryStartedAt: Long? = null,
    var owner: Caregiver? = null,
    var wakeId: Long? = null,
    var groupKey: String? = null,
    var eligibleSince: Long? = null,
    var alerted: Boolean = false,
    /** Set once the player has been told (or saw in the app) that this need reached crying. */
    var cryAlerted: Boolean = false,
    var resolveAt: Long? = null,
    var peakPhase: NeedPhase = NeedPhase.CUE,
    var interventionDone: Boolean = false,
    var expiresAt: Long? = null,
)

@Serializable
data class StepChoice(
    val id: String,
    val label: String,
    /** "ok" advances, "blocked" coaches and stays, "poor" advances with a coaching note. */
    val verdict: String = "ok",
    val lessonId: String? = null,
    val coaching: String? = null,
    /** Extra reference time this choice adds as a timed step. */
    val addRefMs: Long = 0,
)

@Serializable
data class SessionStep(
    val id: String,
    val title: String,
    val instruction: String,
    /** Reference effort in ms (scaled by the preset). 0 for decision or one-tap steps. */
    val refMs: Long = 0,
    val choices: List<StepChoice> = emptyList(),
    /** Label of the one-tap button for ACTION steps. */
    val actionLabel: String? = null,
)

@Serializable
class FeedProgress(
    val plannedRefMs: Long,
    val satiety: Double,
    var intake: Double = 0.0,
    var transferStartedAt: Long? = null,
    var pacedOk: Int = 0,
    var pacedSkipped: Int = 0,
    var gasDuringFeed: Boolean = false,
    var burpedDuringFeed: Boolean = false,
    var fullnessShown: Boolean = false,
    var forcedFinish: Boolean = false,
    var startDistress: Double = 0.0,
    var sleepy: Boolean = false,
)

@Serializable
class CareSession(
    val id: Long,
    val kind: SessionKind,
    val startedAt: Long,
    val effortScale: Double,
    val steps: MutableList<SessionStep>,
    var stepIndex: Int = 0,
    var stepDoneMs: Double = 0.0,
    var runningSince: Long? = null,
    var handsOnMs: Long = 0,
    var effortMs: Double = 0.0,
    var refDoneMs: Long = 0,
    val needIds: MutableList<Long> = mutableListOf(),
    var feed: FeedProgress? = null,
    val hazards: MutableList<String> = mutableListOf(),
    var suppliesReady: Boolean = false,
    var revealed: String? = null,
    var lastFeedback: String? = null,
    var night: Boolean = false,
    val stepLog: MutableList<String> = mutableListOf(),
) {
    val currentStep: SessionStep? get() = steps.getOrNull(stepIndex)
}

enum class HelperTaskKind(val verb: String) {
    FEED("fed"), CHANGE("changed"), BURP("burped"), SETTLE("settled"), COMFORT("comforted"), FINISH_SESSION("finished"),
}

@Serializable
class HelperTask(
    val kind: HelperTaskKind,
    val startedAt: Long,
    val endsAt: Long,
    val needIds: List<Long>,
    val refMs: Long,
    val feedTransferStart: Long? = null,
    val sessionKind: SessionKind? = null,
)

@Serializable
class HelperState(
    var name: String,
    var relation: String,
    var task: HelperTask? = null,
    var nextActionAt: Long? = null,
)

enum class CoverageKind { BREAK, NIGHT_HANDOFF }

@Serializable
class CoverageBlock(val id: Long, val kind: CoverageKind, val start: Long, var end: Long, val windowKey: String? = null)

enum class EveningKind(val label: String) { CLUSTER_FEEDING("Cluster-feeding evening"), FUSSY_EVENING("Fussy evening") }

@Serializable
class EveningPlan(
    val kind: EveningKind,
    val wallStart: Long,
    val wallEnd: Long,
    val intensity: Double,
    var started: Boolean = false,
    var ended: Boolean = false,
)

@Serializable
class ScenarioState(val evenings: MutableList<EveningPlan> = mutableListOf())

@Serializable
class AlertGroup(
    val key: String,
    val openedAt: Long,
    val night: Boolean,
    val channel: String,
    val needIds: MutableList<Long> = mutableListOf(),
    var postedAt: Long? = null,
    var silenced: Boolean = false,
    var closedAt: Long? = null,
    var wakeId: Long? = null,
    var text: String = "",
)

@Serializable
class NightWindow(
    val key: String,
    var consent: Boolean = false,
    var attempts: Int = 0,
    var handedOff: Boolean = false,
    var playerWakeId: Long? = null,
    var summaryPosted: Boolean = false,
)

@Serializable
class DeliveryRecord(
    val groupKey: String,
    val channel: String,
    val plannedAt: Long,
    val attemptAt: Long,
    val wallAt: Long,
    val outcome: String,
    var openedAt: Long? = null,
)

@Serializable
class AlertState(
    val groups: MutableList<AlertGroup> = mutableListOf(),
    val nightWindows: MutableMap<String, NightWindow> = mutableMapOf(),
    val ledger: MutableList<DeliveryRecord> = mutableListOf(),
    val postedTimes: MutableList<Long> = mutableListOf(),
    var groupSeq: Long = 0,
    /** Extreme Real: the player opted in for the next (or current) sleep window. */
    var consentPending: Boolean = false,
    var trialEndAnnounced: Boolean = false,
)

@Serializable
class RunStats(
    val counts: MutableMap<String, Int> = mutableMapOf(),
    val durations: MutableMap<String, Double> = mutableMapOf(),
    var accumulatedTo: Long = 0,
) {
    fun inc(key: String, by: Int = 1) { counts[key] = (counts[key] ?: 0) + by }
    fun add(key: String, ms: Double) { durations[key] = (durations[key] ?: 0.0) + ms }
    fun count(key: String): Int = counts[key] ?: 0
    fun duration(key: String): Double = durations[key] ?: 0.0
}

enum class LogType {
    RUN_STARTED, NEED_OPENED, NEED_PHASE, NEED_RESOLVED, WAKE, ASLEEP, SESSION_STARTED, STEP, SESSION_ENDED,
    CARE_DONE, CHECK, COMFORT, HELPER, COVERAGE, HANDOFF, INTERVENTION, COACHING, MISSTEP, ALERT, PAUSE,
    RESUME, SCENARIO, SETTINGS, DEBRIEF, RUN_ENDED, NOTE,
}

@Serializable
data class LogEntry(
    val seq: Long,
    val t: Long,
    val wall: Long,
    val type: LogType,
    val text: String,
    val who: Caregiver? = null,
    val needIds: List<Long> = emptyList(),
    val tag: String? = null,
    val lesson: String? = null,
    val night: Boolean = false,
)

@Serializable
data class CoachCard(
    val id: Long,
    val lessonId: String,
    val title: String,
    val text: String,
    val kind: String,
    val createdAt: Long,
)

@Serializable
class CoachingState(
    val seen: MutableSet<String> = mutableSetOf(),
    val pending: MutableList<CoachCard> = mutableListOf(),
)

@Serializable
class ClockState(
    val startLogical: Long,
    var endLogical: Long?,
    var now: Long,
    /** wall = logical + delta. Grows by the length of each pause. */
    var delta: Long = 0,
    var pausedAtWall: Long? = null,
    var presence: Presence = Presence.FOREGROUND,
    var handsOffAt: Long? = null,
    var totalPausedMs: Long = 0,
)

@Serializable
class IdState(var next: Long = 1) {
    fun take(): Long = next++
}

@Serializable
data class RunSettings(
    val childName: String,
    val route: FeedingRoute,
    val preset: RealismPreset,
    val sleepWindow: TimeWindow,
    val helperName: String,
    val helperRelation: String,
    val zoneId: String,
    val takeOneNightWake: Boolean = false,
    val protectedWindow: TimeWindow? = null,
    val presencePrompts: Boolean = false,
    val skinTone: Int = 1,
)

@Serializable
class QuizState(
    val pre: MutableMap<String, String> = mutableMapOf(),
    val post: MutableMap<String, String> = mutableMapOf(),
)

@Serializable
class PracticeState(val drillId: String, var objectiveMet: Boolean = false, var debrief: String? = null)

@Serializable
class World(
    val runId: String,
    val seed: Long,
    val mode: RunMode,
    val engineVersion: Int,
    val contentVersion: Int,
    val createdWall: Long,
    var settings: RunSettings,
    val clock: ClockState,
    val child: ChildState,
    val needs: MutableList<NeedEpisode> = mutableListOf(),
    var session: CareSession? = null,
    val helper: HelperState,
    val coverage: MutableList<CoverageBlock> = mutableListOf(),
    val scenario: ScenarioState = ScenarioState(),
    val alerts: AlertState = AlertState(),
    val stats: RunStats = RunStats(),
    val log: MutableList<LogEntry> = mutableListOf(),
    val ids: IdState = IdState(),
    val rng: RngState = RngState(),
    val coaching: CoachingState = CoachingState(),
    val quiz: QuizState = QuizState(),
    var practice: PracticeState? = null,
    var ended: Boolean = false,
    var endReason: String? = null,
    var logSeq: Long = 0,
    var lastFeedback: String? = null,
    var lastFeedbackAt: Long = 0,
)
