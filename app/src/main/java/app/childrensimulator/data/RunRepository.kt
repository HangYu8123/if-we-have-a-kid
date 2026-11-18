package app.childrensimulator.data

import android.content.Context
import app.childrensimulator.notify.AlarmScheduler
import app.childrensimulator.notify.Notifier
import app.childrensimulator.sim.engine.CareReport
import app.childrensimulator.sim.engine.ChildView
import app.childrensimulator.sim.engine.Command
import app.childrensimulator.sim.engine.CommandResult
import app.childrensimulator.sim.engine.PracticeDrill
import app.childrensimulator.sim.engine.SimEngine
import app.childrensimulator.sim.engine.TrialSetup
import app.childrensimulator.sim.engine.newPractice
import app.childrensimulator.sim.engine.planNextWake
import app.childrensimulator.sim.engine.reconcileAlerts
import app.childrensimulator.sim.model.LogEntry
import app.childrensimulator.sim.model.LogType
import app.childrensimulator.sim.model.PracticeState
import app.childrensimulator.sim.model.Presence
import app.childrensimulator.sim.model.RunSettings
import app.childrensimulator.sim.model.World
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId

/** What the UI renders. Rebuilt from the world after every tick or command. */
data class RunUi(
    val loaded: Boolean = false,
    val hasRun: Boolean = false,
    val view: ChildView? = null,
    val settings: RunSettings? = null,
    val log: List<LogEntry> = emptyList(),
    val quizPre: Map<String, String> = emptyMap(),
    val quizPost: Map<String, String> = emptyMap(),
    val lastFeedback: String? = null,
    val scheduling: String? = null,
    val runId: String? = null,
)

data class PracticeUi(val drill: PracticeDrill, val view: ChildView, val practice: PracticeState, val log: List<LogEntry>)

/**
 * Single owner of the run (docs/11 §4-5): serializes commands, persists after each commit, then performs OS side
 * effects (notifications and the next alarm). A foreground ticker advances the simulation once per second.
 */
class RunRepository(
    private val context: Context,
    val engine: SimEngine,
    private val store: RunStore,
    private val notifier: Notifier,
    private val scheduler: AlarmScheduler,
    val prefs: AppPrefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var world: World? = null
    private var loaded = false
    private var ticker: Job? = null
    private var lastPersistAt = 0L
    private var cachedLog: List<LogEntry> = emptyList()
    private var cachedLogSize = -1
    private var cachedRunId: String? = null
    private var scheduling: String? = null

    @Volatile
    var foreground = false
        private set

    private val _ui = MutableStateFlow(RunUi())
    val ui: StateFlow<RunUi> = _ui

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages

    private val _practice = MutableStateFlow<PracticeUi?>(null)
    val practice: StateFlow<PracticeUi?> = _practice
    private var practiceEntryId: String? = null
    private var practiceWorld: World? = null
    private var practiceDrill: PracticeDrill? = null
    private var practiceTicker: Job? = null

    fun now(): Long = TrustedClock.now(context)

    private suspend fun ensureLoaded() {
        if (!loaded) {
            world = withContext(Dispatchers.IO) { store.load() }
            loaded = true
        }
    }

    fun init() {
        scope.launch {
            mutex.withLock {
                ensureLoaded()
                val w = world
                if (w != null) engine.advanceToWall(w, now())
                publish()
            }
        }
    }

    // ------------------------------------------------------------------ run lifecycle

    suspend fun startTrial(setup: TrialSetup) {
        mutex.withLock {
            ensureLoaded()
            val w = engine.newTrial(setup.copy(zoneId = ZoneId.systemDefault().id), now())
            if (foreground) engine.apply(w, Command.SetPresence(Presence.FOREGROUND), now())
            // Answers from the onboarding knowledge check belong to this run.
            prefs.practiceQuizPre?.let { saved ->
                saved.split(';').filter { it.contains('=') }.forEach { kv ->
                    val (q, a) = kv.split('=', limit = 2)
                    engine.apply(w, Command.QuizAnswer(false, q, a), now())
                }
            }
            prefs.practiceQuizPre = null
            world = w
            commit(w)
        }
    }

    suspend fun deleteRun() {
        mutex.withLock {
            world = null
            withContext(Dispatchers.IO) { store.delete() }
            scheduler.cancel()
            notifier.cancelAll()
            cachedLogSize = -1
            publish()
        }
    }

    suspend fun dispatch(cmd: Command): CommandResult {
        val result = mutex.withLock {
            ensureLoaded()
            val w = world ?: return CommandResult(false, "Start a trial first.", emptyList())
            val r = engine.apply(w, cmd, now())
            commit(w)
            r
        }
        // Session feedback already shows in the care panel; pop up refusals and quick-action results only.
        val popup = !result.ok || cmd is Command.StartBreak || cmd is Command.HandOffNeeds ||
            cmd is Command.HandOffNight || cmd is Command.HandOffSession
        if (popup) result.message?.let { _messages.tryEmit(it) }
        return result
    }

    fun send(cmd: Command) {
        scope.launch { dispatch(cmd) }
    }

    suspend fun report(): CareReport? = mutex.withLock { world?.let { engine.report(it) } }

    /** Persist, publish, reconcile notifications and plan the next wake-up (OS side effects after commit). */
    private suspend fun commit(w: World, persist: Boolean = true) {
        val ops = engine.reconcileAlerts(w, now(), foreground)
        notifier.execute(ops)
        if (persist) persist(w, force = true)
        publish()
        if (!foreground) planAndSchedule(w) else scheduler.cancel()
    }

    private fun planAndSchedule(w: World) {
        val wake = engine.planNextWake(w)
        scheduling = if (wake == null) {
            scheduler.cancel()
            null
        } else {
            scheduler.schedule(wake, now(), prefs.exactNightAlarms) + " at " + java.time.Instant.ofEpochMilli(wake.atWall).atZone(ZoneId.systemDefault()).toLocalTime().withNano(0)
        }
    }

    private suspend fun persist(w: World, force: Boolean) {
        val t = now()
        if (!force && t - lastPersistAt < 15_000) return
        withContext(Dispatchers.IO) { store.save(w) }
        lastPersistAt = t
    }

    private fun publish() {
        val w = world
        if (w == null) {
            _ui.value = RunUi(loaded = true, hasRun = false)
            return
        }
        if (w.log.size != cachedLogSize || w.runId != cachedRunId) {
            cachedLog = w.log.toList()
            cachedLogSize = w.log.size
            cachedRunId = w.runId
        }
        val feedback = w.lastFeedback?.takeIf { w.clock.now - w.lastFeedbackAt < 25_000 }
        _ui.value = RunUi(
            loaded = true,
            hasRun = true,
            view = engine.view(w),
            settings = w.settings,
            log = cachedLog,
            quizPre = w.quiz.pre.toMap(),
            quizPost = w.quiz.post.toMap(),
            lastFeedback = feedback,
            scheduling = scheduling,
            runId = w.runId,
        )
    }

    // ------------------------------------------------------------------ foreground and background (docs/11 §5)

    fun onForeground() {
        foreground = true
        scope.launch {
            mutex.withLock {
                ensureLoaded()
                val w = world
                if (w != null) {
                    engine.apply(w, Command.SetPresence(Presence.FOREGROUND), now())
                    commit(w)
                } else publish()
            }
        }
        startTicker()
        practiceWorld?.let { scope.launch { practiceCommand(Command.Resume) } }
    }

    fun onBackground() {
        foreground = false
        ticker?.cancel()
        scope.launch {
            mutex.withLock {
                val w = world ?: return@withLock
                // Needs already visible in the app count as seen; only new ones alert later.
                engine.reconcileAlerts(w, now(), foreground = true)
                engine.apply(w, Command.SetPresence(Presence.BACKGROUND), now())
                commit(w)
            }
        }
        practiceWorld?.let { scope.launch { practiceCommand(Command.Pause) } }
    }

    /** Alarm receiver and system events. */
    suspend fun onAlarm() {
        mutex.withLock {
            ensureLoaded()
            val w = world ?: return
            commit(w)
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(1000)
                mutex.withLock {
                    val w = world ?: return@withLock
                    if (!foreground) return@withLock
                    val events = engine.advanceToWall(w, now())
                    if (events.isNotEmpty()) {
                        notifier.execute(engine.reconcileAlerts(w, now(), true))
                        persist(w, force = events.any { it.type != LogType.NEED_PHASE })
                    } else {
                        persist(w, force = false)
                    }
                    publish()
                }
            }
        }
    }

    // ------------------------------------------------------------------ Practice Room (local clock, no reminders)

    fun startPractice(drill: PracticeDrill, entryId: String): Boolean {
        if (practiceEntryId == entryId) return false
        val trial = world
        val w = engine.newPractice(
            drill,
            childName = trial?.settings?.childName ?: "Baby",
            route = trial?.settings?.route ?: app.childrensimulator.sim.model.FeedingRoute.FORMULA,
            skinTone = trial?.settings?.skinTone ?: 1,
            zoneId = ZoneId.systemDefault().id,
            nowWall = now(),
            seed = now(),
        )
        engine.apply(w, Command.SetPresence(Presence.FOREGROUND), now())
        practiceEntryId = entryId
        practiceWorld = w
        practiceDrill = drill
        publishPractice()
        practiceTicker?.cancel()
        practiceTicker = scope.launch {
            while (isActive) {
                delay(1000)
                synchronized(this@RunRepository) {
                    practiceWorld?.let { engine.advanceToWall(it, now()) }
                }
                publishPractice()
            }
        }
        return true
    }

    fun practiceCommand(cmd: Command) {
        val r = synchronized(this) { practiceWorld?.let { engine.apply(it, cmd, now()) } } ?: return
        if (!r.ok) r.message?.let { _messages.tryEmit(it) }
        publishPractice()
    }

    fun endPractice(entryId: String) {
        if (practiceEntryId != entryId) return
        practiceTicker?.cancel()
        practiceEntryId = null
        practiceWorld = null
        practiceDrill = null
        _practice.value = null
    }

    private fun publishPractice() {
        val w = practiceWorld ?: return
        val drill = practiceDrill ?: return
        val view = synchronized(this) { engine.view(w) }
        _practice.value = PracticeUi(drill, view, w.practice!!, w.log.toList())
    }

    fun schedulingSummary(): String? = scheduling

    fun canScheduleExact(): Boolean = scheduler.canExact()
}
