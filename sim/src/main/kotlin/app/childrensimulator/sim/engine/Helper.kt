package app.childrensimulator.sim.engine

import app.childrensimulator.sim.MINUTE
import app.childrensimulator.sim.Stream
import app.childrensimulator.sim.model.*

// ------------------------------------------------------------------ the on-duty helper (docs/10 §7, docs/11 §3.7)

internal fun Ctx.helperResponseTime(): Long = now + (rng.range(Stream.HELPER, 1.0, 3.0) * MINUTE).toLong()

internal fun Ctx.refreshHelper() {
    syncDuty()
    val h = w.helper
    if (h.task != null) return
    val pending = w.needs.any { it.owner == Caregiver.HELPER && workableByHelper(it) }
    if (pending && h.nextActionAt == null) h.nextActionAt = helperResponseTime()
    if (!pending && h.nextActionAt != null && !(helperOnDuty() && helperWantsToSettle())) h.nextActionAt = null
}

private fun Ctx.workableByHelper(n: NeedEpisode): Boolean =
    child.sleep == SleepState.AWAKE || n.kind == NeedKind.HUNGER || n.phase.ordinal >= NeedPhase.FUSS.ordinal

private fun Ctx.helperWantsToSettle(): Boolean =
    child.sleep == SleepState.AWAKE && w.session == null && child.heldBy != Caregiver.PLAYER &&
        now - child.sleepSince >= child.wakeWindowMs / 2

internal fun Ctx.onHelperStart() {
    val h = w.helper
    h.nextActionAt = null
    if (h.task != null) return
    val mine = w.needs.filter { it.owner == Caregiver.HELPER && workableByHelper(it) }
    val order = listOf(NeedKind.HUNGER, NeedKind.DIRTY, NeedKind.GAS, NeedKind.WET, NeedKind.OVERSTIM, NeedKind.CLOSENESS, NeedKind.TIRED)
    val first = order.firstNotNullOfOrNull { k -> mine.firstOrNull { it.kind == k } }
    if (first == null) {
        if (helperOnDuty() && helperWantsToSettle() && w.session == null) startHelperTask(HelperTaskKind.SETTLE, emptyList())
        return
    }
    if (w.session != null && w.session!!.needIds.contains(first.id)) return
    when (first.kind) {
        NeedKind.HUNGER -> startHelperTask(HelperTaskKind.FEED, mine.filter { it.kind == NeedKind.HUNGER || it.kind == NeedKind.GAS })
        NeedKind.DIRTY, NeedKind.WET -> startHelperTask(HelperTaskKind.CHANGE, mine.filter { it.kind == NeedKind.WET || it.kind == NeedKind.DIRTY })
        NeedKind.GAS -> startHelperTask(HelperTaskKind.BURP, listOf(first))
        NeedKind.OVERSTIM, NeedKind.CLOSENESS -> startHelperTask(HelperTaskKind.COMFORT, mine.filter { it.kind == NeedKind.OVERSTIM || it.kind == NeedKind.CLOSENESS })
        NeedKind.TIRED -> startHelperTask(HelperTaskKind.SETTLE, listOf(first))
    }
}

private fun Ctx.startHelperTask(kind: HelperTaskKind, needs: List<NeedEpisode>) {
    if (child.sleep == SleepState.ASLEEP && kind == HelperTaskKind.FEED) wakeUp("hunger")
    var prepMs = 0L
    val refMs: Long = when (kind) {
        HelperTaskKind.FEED -> {
            prepMs = if (settings.route == FeedingRoute.FORMULA && child.bottle.preparedAt == null) {
                (rng.range(Stream.HELPER, 3.0, 5.0) * MINUTE).toLong()
            } else if (settings.route == FeedingRoute.BREAST) (rng.range(Stream.HELPER, 1.5, 3.0) * MINUTE).toLong() else 0L
            prepMs + (rng.range(Stream.HELPER, 12.0, 20.0) * MINUTE).toLong() + 2 * MINUTE
        }
        HelperTaskKind.CHANGE -> (rng.range(Stream.HELPER, if (child.diaper.dirty) 4.0 else 3.0, if (child.diaper.blowout) 12.0 else 5.5) * MINUTE).toLong()
        HelperTaskKind.BURP -> (rng.range(Stream.HELPER, 2.0, 4.0) * MINUTE).toLong()
        HelperTaskKind.SETTLE -> (rng.range(Stream.HELPER, 5.0, 12.0) * MINUTE).toLong()
        HelperTaskKind.COMFORT -> (rng.range(Stream.HELPER, 4.0, 8.0) * MINUTE).toLong()
        HelperTaskKind.FINISH_SESSION -> 5 * MINUTE
    }
    w.helper.task = HelperTask(kind, now, now + refMs, needs.map { it.id }, refMs, if (kind == HelperTaskKind.FEED) now + prepMs else null)
    needs.forEach { it.owner = Caregiver.HELPER }
    if (kind == HelperTaskKind.CHANGE) {
        child.place = Place.CHANGING_TABLE
        child.heldBy = null
        child.holdStartedAt = null
    } else {
        child.place = Place.ARMS
        if (child.heldBy != Caregiver.HELPER) child.holdStartedAt = now
        child.heldBy = Caregiver.HELPER
    }
    child.techniques.remove(Technique.ROCK)
    val what = when (kind) {
        HelperTaskKind.FEED -> if (settings.route == FeedingRoute.FORMULA) "is preparing a bottle and feeding $name" else "is giving $name expressed milk by bottle"
        HelperTaskKind.CHANGE -> "is changing $name's diaper"
        HelperTaskKind.BURP -> "is burping $name"
        HelperTaskKind.SETTLE -> "is settling $name to sleep"
        HelperTaskKind.COMFORT -> "is holding and calming $name"
        HelperTaskKind.FINISH_SESSION -> "is finishing the care you started"
    }
    log(LogType.HELPER, "$helperName $what.", Caregiver.HELPER, needs.map { it.id }, tag = kind.name)
}

internal fun Ctx.onHelperTaskEnd() {
    val task = w.helper.task ?: return
    w.helper.task = null
    val taskMs = (now - task.startedAt).toDouble()
    w.stats.add("helper.${task.kind.name}", taskMs)
    w.stats.add("reference.HELPER", task.refMs.toDouble())
    w.stats.inc("helperTasks")
    when (task.kind) {
        HelperTaskKind.FEED -> {
            if (settings.route == FeedingRoute.FORMULA) child.bottle.preparedAt = null
            completeFeed(1.0, interrupted = false, who = Caregiver.HELPER, transferStart = task.feedTransferStart ?: task.startedAt)
            w.needs.filter { it.kind == NeedKind.GAS && it.owner == Caregiver.HELPER }.forEach { resolveNeed(it, Caregiver.HELPER, "$helperName burped $name.") }
            child.gasRisk = 0.08
        }
        HelperTaskKind.CHANGE -> {
            diaperChanged(Caregiver.HELPER)
            child.place = Place.ARMS
            child.heldBy = Caregiver.HELPER
            child.holdStartedAt = now
        }
        HelperTaskKind.BURP -> w.needs.filter { it.kind == NeedKind.GAS }.forEach { resolveNeed(it, Caregiver.HELPER, "$helperName burped $name.") }
        HelperTaskKind.COMFORT -> w.needs.filter { (it.kind == NeedKind.CLOSENESS || it.kind == NeedKind.OVERSTIM) && it.owner == Caregiver.HELPER }
            .forEach { resolveNeed(it, Caregiver.HELPER, "$helperName calmed $name.") }
        HelperTaskKind.SETTLE -> {
            w.stats.inc("settle.HELPER")
            if (child.sleep == SleepState.AWAKE) {
                w.needs.filter { it.kind == NeedKind.TIRED }.forEach { resolveNeed(it, Caregiver.HELPER, "$name fell asleep.") }
                child.sleep = SleepState.ASLEEP
                child.sleepSince = now
                child.currentWakeId = null
                child.fallAsleepAt = null
                val night = inReferenceNight()
                child.napEndsAt = now + (rng.range(Stream.SLEEP, if (night) 100.0 else 40.0, if (night) 220.0 else 150.0) * MINUTE).toLong()
                w.stats.inc("sleep.bouts")
                log(LogType.ASLEEP, "$name fell asleep with $helperName, on their back in the bassinet.", Caregiver.HELPER, tag = "BASSINET")
            }
        }
        HelperTaskKind.FINISH_SESSION -> Unit
    }
    log(LogType.HELPER, "$helperName ${task.kind.verb} $name (${formatDuration(now - task.startedAt)}).", Caregiver.HELPER, task.needIds, tag = task.kind.name + ":DONE")
    // Put the baby down safely between tasks unless more helper work is waiting.
    val more = w.needs.any { it.owner == Caregiver.HELPER && workableByHelper(it) }
    if (!more) {
        if (child.sleep == SleepState.AWAKE && helperOnDuty() && helperWantsToSettle() && task.kind != HelperTaskKind.SETTLE) {
            w.helper.nextActionAt = now
        } else {
            child.heldBy = null
            child.holdStartedAt = null
            child.place = Place.BASSINET
        }
    } else {
        w.helper.nextActionAt = now + 30_000
    }
}
