package app.childrensimulator.sim.engine

import app.childrensimulator.sim.MINUTE
import app.childrensimulator.sim.model.*

// ------------------------------------------------------------------ who is on duty (docs/05 §2-3, docs/10 §7)

internal fun Ctx.nightWindowKey(t: Long = now): String? = clock.windowKey(t, settings.sleepWindow)

internal fun Ctx.nightWindow(key: String): NightWindow = w.alerts.nightWindows.getOrPut(key) {
    NightWindow(key, consent = false).also {
        if (w.alerts.consentPending) {
            it.consent = true
            w.alerts.consentPending = false
        }
    }
}

internal fun Ctx.activeBlock(t: Long = now): CoverageBlock? = w.coverage.firstOrNull { b ->
    when (b.kind) {
        CoverageKind.BREAK -> t >= b.start && t < b.end
        CoverageKind.NIGHT_HANDOFF -> b.windowKey != null && b.windowKey == nightWindowKey(t)
    }
}

private fun Ctx.protectedNow(t: Long = now): Boolean = settings.protectedWindow?.let { clock.inWindow(t, it) } ?: false

/** True when the helper covers new routine needs at [t] (not counting per-wake exceptions). */
internal fun Ctx.helperOnDuty(t: Long = now): Boolean {
    if (w.mode == RunMode.PRACTICE) return activeBlock(t) != null
    if (activeBlock(t) != null || protectedNow(t)) return true
    val key = nightWindowKey(t) ?: return false
    val nw = w.alerts.nightWindows[key]
    if (nw?.handedOff == true) return true
    return when (rules.night) {
        NightPolicy.HELPER -> true
        NightPolicy.HELPER_EXCEPT_ONE_WAKE -> true
        NightPolicy.PLAYER_ORDINARY -> false
        NightPolicy.PLAYER_AUDIBLE_OPT_IN -> nw != null && nw.consent && nw.attempts >= PresetRules.NIGHT_AUDIBLE_LIMIT
    }
}

/** Ownership for a need opening now. `null` means the player is responsible. */
internal fun Ctx.ownerForNewNeed(wakeId: Long?): Caregiver? {
    if (w.mode == RunMode.PRACTICE) return if (activeBlock() != null) Caregiver.HELPER else null
    if (activeBlock() != null || protectedNow()) return Caregiver.HELPER
    // Needs joining a wake the player is already handling stay with the player.
    if (wakeId != null && w.needs.any { it.wakeId == wakeId && it.owner == null } && w.session != null) return null
    val key = nightWindowKey() ?: return null
    val nw = nightWindow(key)
    if (nw.handedOff) return Caregiver.HELPER
    return when (rules.night) {
        NightPolicy.HELPER -> Caregiver.HELPER
        NightPolicy.HELPER_EXCEPT_ONE_WAKE -> {
            if (settings.takeOneNightWake && wakeId != null && (nw.playerWakeId == null || nw.playerWakeId == wakeId)) {
                nw.playerWakeId = wakeId
                null
            } else Caregiver.HELPER
        }
        NightPolicy.PLAYER_ORDINARY -> null
        NightPolicy.PLAYER_AUDIBLE_OPT_IN -> {
            val alertedWake = w.alerts.groups.any { it.night && it.wakeId == wakeId && it.postedAt != null }
            if (nw.consent && nw.attempts >= PresetRules.NIGHT_AUDIBLE_LIMIT && !alertedWake) Caregiver.HELPER else null
        }
    }
}

internal fun Ctx.nextCoverageBoundary(): Long? {
    var best = clock.nextWindowBoundary(now, settings.sleepWindow)
    settings.protectedWindow?.let { best = minOf(best, clock.nextWindowBoundary(now, it)) }
    for (b in w.coverage) {
        if (b.kind == CoverageKind.BREAK) {
            if (b.start > now) best = minOf(best, b.start)
            if (b.end > now) best = minOf(best, b.end)
        }
    }
    return best
}

/** Tracks duty changes at window and block boundaries and hands open needs over (docs/05 §2). */
internal fun Ctx.onCoverageBoundary() {
    w.coverage.removeAll { it.kind == CoverageKind.BREAK && it.end <= now }
    w.coverage.removeAll { it.kind == CoverageKind.NIGHT_HANDOFF && it.windowKey != nightWindowKey() }
    nightWindowKey()?.let { nightWindow(it) }
    syncDuty()
}

/** Applies a change in who is on duty. */
internal fun Ctx.syncDuty() {
    val helperDuty = helperOnDuty()
    val wasHelper = w.stats.count("state.helperDuty") == 1
    if (helperDuty == wasHelper) return
    w.stats.counts["state.helperDuty"] = if (helperDuty) 1 else 0
    if (helperDuty) {
        val until = activeBlock()?.let { b -> if (b.kind == CoverageKind.BREAK) " until ${hhmm(b.end)}" else " for the rest of the night" }
            ?: nightWindowKey()?.let { " until ${hhmm(clock.windowEnd(now, settings.sleepWindow))}" } ?: ""
        val moved = w.needs.filter { it.owner == null && !inPlayerSession(it) && !playerWakeException(it) }
        moved.forEach { it.owner = Caregiver.HELPER }
        w.stats.inc("coverage.starts")
        log(LogType.COVERAGE, "$helperName is on duty$until." + if (moved.isNotEmpty()) " They took over ${moved.joinToString { it.kind.label.lowercase() }}." else "", Caregiver.HELPER)
        if (moved.isNotEmpty() || w.needs.any { it.owner == Caregiver.HELPER }) w.helper.nextActionAt = w.helper.nextActionAt ?: helperResponseTime()
    } else {
        val taskNeeds = w.helper.task?.needIds ?: emptyList()
        val back = w.needs.filter { it.owner == Caregiver.HELPER && it.id !in taskNeeds }
        back.forEach { it.owner = null; it.eligibleSince = null; it.alerted = false }
        log(LogType.COVERAGE, "You're on duty again." + if (back.isNotEmpty()) " Open: ${back.joinToString { it.kind.label.lowercase() }}." else "", Caregiver.PLAYER)
    }
}

private fun Ctx.playerWakeException(n: NeedEpisode): Boolean {
    if (rules.night != NightPolicy.HELPER_EXCEPT_ONE_WAKE || !settings.takeOneNightWake) return false
    val key = nightWindowKey() ?: return false
    return w.alerts.nightWindows[key]?.playerWakeId?.let { it == n.wakeId } == true
}

internal fun Ctx.hhmm(logical: Long): String = clock.zoned(logical).let { "%02d:%02d".format(it.hour, it.minute) }

// ------------------------------------------------------------------ breaks and handoffs (docs/04 §6, docs/05 §3)

internal fun Ctx.startBreak(minutes: Int): Outcome {
    val m = minutes.coerceIn(5, 240)
    w.coverage.removeAll { it.kind == CoverageKind.BREAK }
    w.coverage += CoverageBlock(w.ids.take(), CoverageKind.BREAK, now, now + m * MINUTE)
    w.session?.let { handOffSessionInternal(it) }
    if (child.heldBy == Caregiver.PLAYER || child.place == Place.CHANGING_TABLE) transferToBassinet(Caregiver.PLAYER)
    child.techniques.remove(Technique.ROCK)
    child.techniques.remove(Technique.SHUSH)
    w.stats.inc("handoff")
    w.stats.inc("break")
    log(LogType.HANDOFF, "You took a $m-minute break. $helperName is covering.", Caregiver.PLAYER)
    coach("handoff")
    syncDuty()
    return Outcome.info("$helperName is covering for $m minutes.")
}

internal fun Ctx.endBreak(): Outcome {
    val had = w.coverage.removeAll { it.kind == CoverageKind.BREAK }
    if (!had) return Outcome.OK
    log(LogType.COVERAGE, "You ended your break early.", Caregiver.PLAYER)
    syncDuty()
    return Outcome.OK
}

/** Hands the current care group to the helper while the simulation keeps running. */
internal fun Ctx.handOffNeeds(): Outcome {
    w.session?.let { handOffSessionInternal(it) }
    val open = w.needs.filter { it.owner == null }
    if (open.isEmpty()) return Outcome.info("Nothing to hand off right now.")
    open.forEach { it.owner = Caregiver.HELPER }
    if (child.place == Place.CHANGING_TABLE) transferToBassinet(Caregiver.PLAYER)
    w.stats.inc("handoff")
    log(LogType.HANDOFF, "You handed ${open.joinToString { it.kind.label.lowercase() }} to $helperName.", Caregiver.PLAYER, open.map { it.id })
    coach("handoff")
    w.helper.nextActionAt = w.helper.nextActionAt ?: helperResponseTime()
    return Outcome.info("$helperName is taking over.")
}

/** "Hand off the night": the helper covers the rest of the sleep window and pending alerts are cancelled. */
internal fun Ctx.handOffNight(): Outcome {
    val key = nightWindowKey() ?: return startBreak(120).let { Outcome.info("It's not your sleep window, so $helperName is covering for 2 hours.") }
    nightWindow(key).handedOff = true
    w.coverage.removeAll { it.kind == CoverageKind.NIGHT_HANDOFF }
    w.coverage += CoverageBlock(w.ids.take(), CoverageKind.NIGHT_HANDOFF, now, clock.windowEnd(now, settings.sleepWindow), key)
    w.session?.let { handOffSessionInternal(it) }
    if (child.heldBy == Caregiver.PLAYER || child.place == Place.CHANGING_TABLE) transferToBassinet(Caregiver.PLAYER)
    w.stats.inc("handoff")
    w.stats.inc("handoff.night")
    log(LogType.HANDOFF, "You handed off the rest of the night to $helperName.", Caregiver.PLAYER)
    syncDuty()
    return Outcome.info("$helperName has the rest of the night.")
}

internal fun Ctx.nightConsent(granted: Boolean): Outcome {
    if (settings.preset != RealismPreset.EXTREME) return Outcome.fail("Audible night wake-ups are part of Extreme Real.")
    val key = nightWindowKey()
    if (key != null) {
        val nw = nightWindow(key)
        nw.consent = granted
    } else {
        w.alerts.consentPending = granted
    }
    log(
        LogType.SETTINGS,
        if (granted) "You enabled up to ${PresetRules.NIGHT_AUDIBLE_LIMIT} audible wake-ups for ${if (key != null) "this" else "the next"} sleep window."
        else "Audible wake-ups are off for ${if (key != null) "this" else "the next"} sleep window.",
        Caregiver.PLAYER,
    )
    return Outcome.OK
}
