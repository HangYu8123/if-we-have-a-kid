package app.childrensimulator.sim.engine

import app.childrensimulator.sim.TimeWindow
import app.childrensimulator.sim.model.Caregiver
import app.childrensimulator.sim.model.FeedingRoute
import app.childrensimulator.sim.model.LogEntry
import app.childrensimulator.sim.model.NeedKind
import app.childrensimulator.sim.model.NeedPhase
import app.childrensimulator.sim.model.Place
import app.childrensimulator.sim.model.Presence
import app.childrensimulator.sim.model.RealismPreset
import app.childrensimulator.sim.model.RunSettings
import app.childrensimulator.sim.model.SessionKind
import app.childrensimulator.sim.model.SleepState
import app.childrensimulator.sim.model.Technique
import app.childrensimulator.sim.model.CoachCard

/** Player and system commands (docs/11 §3.4). */
sealed interface Command {
    data class StartSession(val kind: SessionKind) : Command
    data class Choose(val choiceId: String) : Command
    data object DoStep : Command
    data object SuspendSession : Command
    data object ResumeSession : Command
    data object StopSession : Command
    data object HandOffSession : Command
    data object CheckDiaper : Command
    data object PickUp : Command
    data class PutDown(val place: Place) : Command
    data class SetTechnique(val technique: Technique, val on: Boolean) : Command
    data class StartBreak(val minutes: Int) : Command
    data object EndBreak : Command
    data object HandOffNeeds : Command
    data object HandOffNight : Command
    data object Pause : Command
    data object Resume : Command
    data object EndRun : Command
    data class SetPresence(val presence: Presence) : Command
    data class UpdateSettings(val settings: RunSettings) : Command
    data class NightConsent(val granted: Boolean) : Command
    data class AlertOpened(val groupKey: String) : Command
    data class SilenceAlert(val groupKey: String) : Command
    data class DismissCoaching(val cardId: Long) : Command
    data class QuizAnswer(val post: Boolean, val questionId: String, val answerId: String) : Command
    data class DebugSkip(val ms: Long) : Command
}

data class CommandResult(val ok: Boolean, val message: String?, val events: List<LogEntry>)

/** Parameters chosen during onboarding (docs/10 §3, docs/12 Phase 1). */
data class TrialSetup(
    val childName: String,
    val route: FeedingRoute,
    val preset: RealismPreset,
    val sleepWindow: TimeWindow = TimeWindow(23 * 60, 7 * 60),
    val helperName: String = "Sam",
    val helperRelation: String = "partner",
    val zoneId: String,
    val takeOneNightWake: Boolean = false,
    val skinTone: Int = 1,
    val seed: Long,
    val ageDays: Double = 21.0,
    val durationHours: Int = 48,
    val startWithFixture: Boolean = true,
)

enum class DistressLevel(val label: String) {
    ASLEEP("Asleep"), CALM("Calm"), STIRRING("Stirring"), FUSSING("Fussing"), CRYING("Crying"), HARD_CRYING("Crying hard"),
}

data class NeedView(
    val id: Long,
    val kind: NeedKind,
    val phase: NeedPhase,
    val openedAt: Long,
    val owner: Caregiver?,
    val attended: Boolean,
)

data class StepView(
    val id: String,
    val title: String,
    val instruction: String,
    val timed: Boolean,
    val progress: Double,
    val remainingMs: Long,
    val choices: List<ChoiceView>,
    val actionLabel: String?,
)

data class ChoiceView(val id: String, val label: String)

data class SessionView(
    val id: Long,
    val kind: SessionKind,
    val title: String,
    val running: Boolean,
    val step: StepView?,
    val stepNumber: Int,
    val stepCount: Int,
    val completedSteps: List<String>,
    val feedback: String?,
    val handsOnMs: Long,
    val intake: Double?,
)

data class ChildView(
    val now: Long,
    val wallNow: Long,
    val name: String,
    val ageText: String,
    val stageLabel: String,
    val sleep: SleepState,
    val place: Place,
    val heldBy: Caregiver?,
    val distress: Double,
    val level: DistressLevel,
    val cue: String,
    val cueDetails: List<String>,
    val needs: List<NeedView>,
    val hints: List<String>,
    val meters: Map<String, Double>,
    val techniques: Set<Technique>,
    val session: SessionView?,
    val helperBusy: String?,
    val onDuty: Caregiver,
    val coverageText: String?,
    val lastFeedStartWall: Long?,
    val lastChangeWall: Long?,
    val awakeOrAsleepSinceWall: Long,
    val bottleReady: Boolean,
    val trialRemainingMs: Long?,
    val paused: Boolean,
    val ended: Boolean,
    val nightNow: Boolean,
    val sleepWindowLabel: String,
    val coaching: List<CoachCard>,
    val pose: Pose,
    val presence: Presence,
    val breakUntilWall: Long?,
    val nightConsentAvailable: Boolean,
    val nightConsentActive: Boolean,
    val nightAttemptsLeft: Int?,
)

/** Visual pose for the illustration (docs/10 §11: readable 2D poses). */
data class Pose(
    val asleep: Boolean,
    val eyesOpen: Boolean,
    val mouth: Mouth,
    val handsToMouth: Boolean,
    val legsUp: Boolean,
    val headTurn: Float,
    val tears: Boolean,
    val redFace: Float,
    val yawning: Boolean,
    val activeSleep: Boolean,
    val onTummy: Boolean,
)

enum class Mouth { SMILE, NEUTRAL, ROOTING, FUSS, CRY, YAWN, SUCKING }

/** Notification work for the Android layer (docs/11 §6.1). */
sealed interface NotificationOp {
    data class Post(
        val groupKey: String,
        val channel: String,
        val title: String,
        val text: String,
        val silent: Boolean,
        val nightActions: Boolean,
    ) : NotificationOp

    data class Cancel(val groupKey: String) : NotificationOp
    data class Summary(val key: String, val title: String, val text: String) : NotificationOp
}

/** The next OS wake-up the app should schedule. */
data class AlertWake(val atLogical: Long, val atWall: Long, val exact: Boolean, val reason: String)

object Channels {
    const val CARE = "care"
    const val NIGHT = "night_wake"
    const val SUMMARY = "summary"
}
