package app.childrensimulator.sim.engine

import app.childrensimulator.sim.DAY
import app.childrensimulator.sim.HOUR
import app.childrensimulator.sim.model.*

// ------------------------------------------------------------------ alert eligibility and delivery (docs/05 §3, §5; docs/11 §6)

/** A need the player is actively working on in the app (a running session) never needs a reminder. */
private fun Ctx.beingHandled(n: NeedEpisode): Boolean = inPlayerSession(n) && w.session?.runningSince != null

internal fun Ctx.needAlertable(n: NeedEpisode): Boolean {
    if (w.mode == RunMode.PRACTICE || w.ended) return false
    if (n.owner == Caregiver.HELPER) return false
    if (beingHandled(n)) return false
    return when (n.kind) {
        NeedKind.HUNGER, NeedKind.DIRTY -> true
        else -> if (rules.priorityAlertsOnly) n.phase.ordinal >= NeedPhase.CRY.ordinal else n.phase.ordinal >= NeedPhase.FUSS.ordinal
    }
}

internal fun Ctx.refreshEligibility() {
    for (n in w.needs) {
        if (needAlertable(n)) {
            if (n.eligibleSince == null) n.eligibleSince = now
        } else if (n.owner == Caregiver.HELPER) {
            n.eligibleSince = null
        }
    }
}

/**
 * The single definition of "an alert is owed" shared by the receiver and the planner (they must agree).
 * A need is owed one alert when it becomes actionable, and one more if it escalates to crying after the player
 * last saw it (for example, they left the app while the baby was fussing).
 */
internal fun Ctx.pendingAlertNeeds(): List<NeedEpisode> =
    w.needs.filter { n ->
        n.eligibleSince != null && n.owner != Caregiver.HELPER && !beingHandled(n) &&
            (!n.alerted || (n.phase.ordinal >= NeedPhase.CRY.ordinal && !n.cryAlerted))
    }

private fun NeedEpisode.markAlerted() {
    alerted = true
    if (phase.ordinal >= NeedPhase.CRY.ordinal) cryAlerted = true
}

/** When the current reason to alert began: first eligibility, or reaching crying after an earlier alert. */
private fun alertMoment(n: NeedEpisode): Long =
    if (n.alerted && n.cryStartedAt != null) maxOf(n.cryStartedAt!!, n.eligibleSince!!) else n.eligibleSince!!

private fun Ctx.groupsToClose(): List<AlertGroup> = w.alerts.groups.filter { g ->
    g.closedAt == null && g.needIds.none { id -> w.needs.any { it.id == id && it.owner != Caregiver.HELPER } }
}

private fun Ctx.nightChannelAllowed(): Boolean {
    if (settings.preset != RealismPreset.EXTREME) return false
    val key = nightWindowKey() ?: return false
    return nightWindow(key).consent
}

private fun Ctx.alertText(needs: List<NeedEpisode>): String {
    val labels = rules.metersAlways || rules.hintsOnRequest
    return if (labels) {
        "Likely: " + needs.joinToString(" + ") { it.kind.label.lowercase() } + "."
    } else {
        val strongest = needs.maxByOrNull { it.phase.ordinal * 10 + if (it.kind == NeedKind.HUNGER) 1 else 0 }
        strongest?.let { cueFor(it.kind, it.phase) } ?: "Your virtual baby needs attention."
    }
}

/** Reconciles posted notifications with the current state. Runs on launch, resume and in the alarm receiver. */
internal fun Ctx.reconcile(foreground: Boolean): List<NotificationOp> {
    val ops = mutableListOf<NotificationOp>()
    val a = w.alerts
    val paused = w.clock.pausedAtWall != null
    if (paused || w.ended) {
        a.groups.filter { it.closedAt == null }.forEach { g ->
            g.closedAt = now
            if (g.postedAt != null) {
                ops += NotificationOp.Cancel(g.key)
                record(g, "CLEARED_${if (paused) "PAUSE" else "END"}")
            }
        }
        if (w.ended && !a.trialEndAnnounced && w.mode == RunMode.WEEKEND_TRIAL) {
            a.trialEndAnnounced = true
            ops += NotificationOp.Summary("trial_end", "Your Weekend Trial is complete", "See the Care Report: time in care, nights and what the helper covered.")
        }
        return ops
    }
    for (g in groupsToClose()) {
        g.closedAt = now
        if (g.postedAt != null) {
            ops += NotificationOp.Cancel(g.key)
            record(g, "CLEARED")
        }
    }
    nightSummary(post = !foreground)?.let { ops += it }
    if (foreground) {
        // In the app the cues update directly.
        a.groups.filter { it.closedAt == null && it.postedAt != null }.forEach { ops += NotificationOp.Cancel(it.key) }
        pendingAlertNeeds().forEach { it.markAlerted() }
        return ops
    }
    val all = pendingAlertNeeds()
    if (all.isEmpty()) return ops
    val night = inSleepWindow()
    // Distress changes on a need whose notification is still showing update it silently (docs/05 §5.2).
    val showing = a.groups.filter { it.closedAt == null && it.postedAt != null && !it.silenced }
    val (updates, pending) = all.partition { n -> n.groupKey != null && showing.any { it.key == n.groupKey } }
    for (n in updates) {
        n.markAlerted()
        val g = showing.first { it.key == n.groupKey }
        g.text = alertText(g.needIds.mapNotNull { id -> w.needs.find { it.id == id } })
        ops += NotificationOp.Post(g.key, g.channel, title(g.night), g.text, silent = true, nightActions = g.night)
        record(g, "UPDATED_SILENT")
    }
    if (pending.isEmpty()) return ops
    val recent = showing.lastOrNull { now - it.postedAt!! <= PresetRules.BUNDLE_WINDOW_MS }
    if (recent != null) {
        pending.forEach {
            it.markAlerted()
            it.groupKey = recent.key
            if (it.id !in recent.needIds) recent.needIds += it.id
        }
        recent.text = alertText(recent.needIds.mapNotNull { id -> w.needs.find { it.id == id } })
        ops += NotificationOp.Post(recent.key, recent.channel, title(recent.night), recent.text, silent = true, nightActions = recent.night)
        record(recent, "UPDATED_SILENT")
        return ops
    }
    val newest = pending.maxOf { alertMoment(it) }
    val key = "g${++a.groupSeq}"
    if (now - newest > PresetRules.FRESHNESS_MS) {
        pending.forEach { it.markAlerted() }
        a.ledger += DeliveryRecord(key, if (night) "night" else Channels.CARE, newest, now, clock.wall(now), "SUPPRESSED_STALE")
        w.stats.inc("alert.suppressed")
        log(LogType.ALERT, "A reminder arrived too late to be useful and was skipped (device delay). No penalty.", tag = "stale")
        return ops
    }
    val cap = rules.alertCapPer24h
    if (cap != null && a.postedTimes.count { it > now - DAY } >= cap) {
        pending.forEach { it.owner = Caregiver.HELPER; it.markAlerted() }
        a.ledger += DeliveryRecord(key, Channels.CARE, newest, now, clock.wall(now), "SUPPRESSED_LIMIT")
        log(LogType.HANDOFF, "Gentle limit reached for today: $helperName took ${pending.joinToString { it.kind.label.lowercase() }}.", Caregiver.HELPER, pending.map { it.id })
        w.helper.nextActionAt = w.helper.nextActionAt ?: helperResponseTime()
        return ops
    }
    val channel = if (night && nightChannelAllowed()) Channels.NIGHT else Channels.CARE
    if (channel == Channels.NIGHT) {
        val nw = nightWindow(nightWindowKey()!!)
        if (nw.attempts >= PresetRules.NIGHT_AUDIBLE_LIMIT) {
            pending.forEach { it.owner = Caregiver.HELPER; it.markAlerted() }
            a.ledger += DeliveryRecord(key, channel, newest, now, clock.wall(now), "SUPPRESSED_LIMIT")
            log(LogType.HANDOFF, "Audible limit reached for tonight: $helperName took this wake.", Caregiver.HELPER, pending.map { it.id })
            w.helper.nextActionAt = w.helper.nextActionAt ?: helperResponseTime()
            return ops
        }
        nw.attempts++
    }
    val g = AlertGroup(key, now, night, channel, pending.map { it.id }.toMutableList(), postedAt = now, wakeId = child.currentWakeId)
    g.text = alertText(pending)
    pending.forEach { it.markAlerted(); it.groupKey = key }
    a.groups += g
    a.postedTimes += now
    a.postedTimes.removeAll { it < now - 2 * DAY }
    ops += NotificationOp.Post(key, channel, title(night), g.text, silent = false, nightActions = night)
    record(g, "POSTED", plannedAt = pending.minOf { alertMoment(it) })
    w.stats.inc("alert.posted")
    w.stats.inc("alert.posted.$channel")
    if (night) w.stats.inc("alert.posted.night")
    log(LogType.ALERT, if (channel == Channels.NIGHT) "Audible night alert sent (${nightWindow(nightWindowKey()!!).attempts} of ${PresetRules.NIGHT_AUDIBLE_LIMIT})." else "Reminder sent: ${g.text}", tag = channel)
    // Keep at most the recent groups.
    if (a.groups.size > 200) a.groups.removeAll { it.closedAt != null && it.closedAt!! < now - DAY }
    return ops
}

private fun title(night: Boolean) = if (night) "Night care · Your virtual baby is awake" else "Your virtual baby needs you"

private fun Ctx.record(g: AlertGroup, outcome: String, plannedAt: Long = g.openedAt) {
    w.alerts.ledger += DeliveryRecord(g.key, g.channel, plannedAt, now, clock.wall(now), outcome)
    if (w.alerts.ledger.size > 1500) w.alerts.ledger.removeAt(0)
}

/** A low-importance morning summary of what the helper did overnight (docs/05 §5.1 `summary`). */
private fun Ctx.nightSummary(post: Boolean): NotificationOp? {
    if (w.mode != RunMode.WEEKEND_TRIAL) return null
    val current = nightWindowKey()
    val done = w.alerts.nightWindows.values.firstOrNull { !it.summaryPosted && it.key != current } ?: return null
    done.summaryPosted = true
    // In the app the care log already shows the night; only post when the player is away.
    if (!post) return null
    val since = now - 12 * HOUR
    val helperEntries = w.log.filter { it.t >= since && it.who == Caregiver.HELPER && it.type == LogType.HELPER && it.tag?.endsWith(":DONE") == true }
    if (helperEntries.isEmpty()) return null
    val feeds = helperEntries.count { it.tag == "FEED:DONE" }
    val changes = helperEntries.count { it.tag == "CHANGE:DONE" }
    val settles = helperEntries.count { it.tag == "SETTLE:DONE" }
    val text = listOfNotNull(
        if (feeds > 0) "$feeds feed${if (feeds > 1) "s" else ""}" else null,
        if (changes > 0) "$changes change${if (changes > 1) "s" else ""}" else null,
        if (settles > 0) "$settles resettle${if (settles > 1) "s" else ""}" else null,
    ).joinToString(", ")
    return NotificationOp.Summary("night_${done.key}", "Overnight, $helperName handled $text", "The care still happened. See the log for times.")
}

internal fun Ctx.alertOpened(groupKey: String): Outcome {
    val g = w.alerts.groups.firstOrNull { it.key == groupKey } ?: return Outcome.OK
    w.alerts.ledger.filter { it.groupKey == groupKey && it.openedAt == null }.forEach { it.openedAt = now }
    val after = g.postedAt?.let { now - it } ?: 0
    w.stats.inc("alert.opened")
    log(LogType.ALERT, "You opened the alert after ${formatDuration(after)}.", Caregiver.PLAYER, g.needIds.toList(), tag = "opened")
    return Outcome.OK
}

internal fun Ctx.silenceAlert(groupKey: String): Outcome {
    val g = w.alerts.groups.firstOrNull { it.key == groupKey } ?: return Outcome.OK
    g.silenced = true
    g.needIds.forEach { id ->
        w.needs.find { it.id == id }?.let {
            it.alerted = true
            it.cryAlerted = true
        }
    }
    record(g, "SILENCED")
    log(LogType.ALERT, "You silenced the alert. The needs stay in the care log.", Caregiver.PLAYER, g.needIds.toList(), tag = "silenced")
    return Outcome.OK
}

// ------------------------------------------------------------------ public entry points

/** Advances to [wallNow] and returns the notification work for the Android layer. */
fun SimEngine.reconcileAlerts(w: World, wallNow: Long, foreground: Boolean): List<NotificationOp> {
    val c = Ctx(this, w)
    c.runUntil(maxOf(w.clock.now, logicalNow(w, wallNow)))
    val ops = c.reconcile(foreground)
    c.afterChange()
    return ops
}

/**
 * Projects the run forward on a copy with no player actions (docs/11 §3.1 `project`) and returns the first
 * moment an alert would be posted or a posted alert should be cleared. Consumes no live randomness.
 */
fun SimEngine.planNextWake(w: World, horizonMs: Long = 26 * HOUR): AlertWake? {
    if (w.ended || w.clock.pausedAtWall != null || w.mode == RunMode.PRACTICE) return null
    val copy = SimEngine.copy(w)
    copy.clock.presence = Presence.BACKGROUND
    copy.session?.runningSince = null
    val c = Ctx(this, copy)
    val horizon = copy.clock.now + horizonMs
    fun wake(reason: String): AlertWake {
        val night = c.inSleepWindow() && copy.settings.preset == RealismPreset.EXTREME &&
            (c.nightWindowKey()?.let { copy.alerts.nightWindows[it]?.consent } ?: copy.alerts.consentPending)
        return AlertWake(copy.clock.now, c.clock.wall(copy.clock.now), exact = night, reason = reason)
    }
    var steps = 0
    while (steps++ < 20_000) {
        if (copy.ended) return wake("trial end")
        if (c.pendingAlertNeeds().isNotEmpty()) return wake("care needed")
        val closing = copy.alerts.groups.any { g -> g.closedAt == null && g.postedAt != null && g.needIds.none { id -> copy.needs.any { it.id == id && it.owner != Caregiver.HELPER } } }
        if (closing) return wake("clear a resolved alert")
        val d = c.nextDue() ?: return null
        if (d.time > horizon) return null
        c.stepTo(d)
    }
    return null
}
