package app.childrensimulator.sim.engine

import app.childrensimulator.sim.MINUTE
import app.childrensimulator.sim.SECOND
import app.childrensimulator.sim.Stream
import app.childrensimulator.sim.model.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// ------------------------------------------------------------------ hands-on care sessions (docs/05 §6, docs/07 Part A)

private val HAZARDS = linkedMapOf(
    "blanket" to "the loose blanket",
    "pillow" to "the pillow",
    "toy" to "the stuffed toy",
    "bumper" to "the crib bumpers",
    "nursing_pillow" to "the nursing pillow",
)

internal fun Ctx.scaledMs(refMs: Long, s: CareSession): Long = max(3 * SECOND, (refMs * s.effortScale).toLong())

private fun SessionStep.isTimed() = refMs > 0 && choices.isEmpty() && actionLabel == null

internal fun Ctx.sessionStepDue(): Long? {
    val s = w.session ?: return null
    val since = s.runningSince ?: return null
    val step = s.currentStep ?: return null
    if (!step.isTimed()) return null
    val left = scaledMs(step.refMs, s) - s.stepDoneMs
    return since + max(0L, ceil(left).toLong())
}

private fun Ctx.checkpointSession(s: CareSession) {
    val since = s.runningSince ?: return
    val dt = now - since
    s.handsOnMs += dt
    val step = s.currentStep
    if (step != null && step.isTimed()) {
        s.stepDoneMs += dt
        s.effortMs += dt
    }
    s.runningSince = now
}

internal fun Ctx.onSessionStepDue() {
    val s = w.session ?: return
    checkpointSession(s)
    val step = s.currentStep ?: return
    if (s.stepDoneMs + 1 < scaledMs(step.refMs, s)) return
    s.refDoneMs += step.refMs
    if (!step.id.endsWith("_do")) s.stepLog += step.title
    onTimedStepDone(s, step)
    if (w.session === s && s.currentStep === step) nextStep(s)
}

private fun Ctx.nextStep(s: CareSession) {
    s.stepIndex++
    s.stepDoneMs = 0.0
    if (s.stepIndex >= s.steps.size) {
        finishSession(s)
        return
    }
    onStepEnter(s, s.steps[s.stepIndex])
}

private fun CareSession.insertNext(step: SessionStep) = steps.add(stepIndex + 1, step)

private fun CareSession.replaceCurrent(step: SessionStep) {
    steps[stepIndex] = step
}

// ------------------------------------------------------------------ starting

internal fun Ctx.startSession(kind: SessionKind): Outcome {
    if (w.session != null) return Outcome.fail("Finish, stop or hand off the current care first.")
    val asleep = child.sleep == SleepState.ASLEEP
    when (kind) {
        SessionKind.BOTTLE_PREP -> {
            if (settings.route != FeedingRoute.FORMULA) return Outcome.fail("You chose breast/chestfeeding for this trial.")
            if (child.bottle.preparedAt != null) return Outcome.fail("A bottle is already prepared.")
            if (child.heldBy == Caregiver.PLAYER) return Outcome.fail("Preparing a bottle takes both hands. Lay $name down in the bassinet first.")
        }
        SessionKind.BOTTLE_FEED -> {
            if (settings.route != FeedingRoute.FORMULA) return Outcome.fail("You chose breast/chestfeeding for this trial.")
            if (child.bottle.preparedAt == null) return Outcome.fail("Prepare a bottle first.")
            if (asleep) return Outcome.fail("$name is asleep. Feeding works best when they wake and show hunger cues.")
        }
        SessionKind.BREASTFEED -> {
            if (settings.route != FeedingRoute.BREAST) return Outcome.fail("You chose formula feeding for this trial.")
            if (asleep) return Outcome.fail("$name is asleep. Feeding works best when they wake and show hunger cues.")
        }
        SessionKind.BURP -> if (asleep) return Outcome.fail("$name is asleep and comfortable. No need to burp now.")
        SessionKind.SETTLE -> if (asleep && child.place == Place.BASSINET && child.heldBy == null) return Outcome.fail("$name is already asleep in the bassinet.")
        SessionKind.PLAY_TALK, SessionKind.TUMMY_TIME -> if (asleep) return Outcome.fail("$name is asleep. Play is for awake time.")
        SessionKind.DIAPER_CHANGE -> Unit
    }
    // Taking over from the helper.
    w.helper.task?.let { t ->
        val overlaps = t.needIds.isNotEmpty() || t.kind == HelperTaskKind.SETTLE
        if (overlaps) {
            w.helper.task = null
            log(LogType.HANDOFF, "You took over from $helperName.", Caregiver.PLAYER)
        }
    }
    if (asleep && kind == SessionKind.DIAPER_CHANGE) wakeUp("diaper change")

    val s = CareSession(
        id = w.ids.take(), kind = kind, startedAt = now, effortScale = rules.effortScale,
        steps = buildSteps(kind).toMutableList(), night = inSleepWindow(),
    )
    w.needs.filter { it.owner == Caregiver.HELPER || it.owner == null }.forEach { n ->
        if (sessionAddresses(s, n.kind) || ((kind == SessionKind.BOTTLE_FEED || kind == SessionKind.BREASTFEED) && n.kind == NeedKind.GAS)) {
            s.needIds += n.id
            n.owner = null
        }
    }
    when (kind) {
        SessionKind.BOTTLE_FEED, SessionKind.BREASTFEED -> {
            val hungry = w.needs.any { it.kind == NeedKind.HUNGER }
            val m = child.milk
            val demandNow = m.demandAtRef + accumulateDemand(m.refTime, now)
            val readiness = (demandNow / max(0.01, m.threshold)).coerceIn(0.0, 1.5)
            val awakeFor = now - child.sleepSince
            val sleepy = awakeFor > child.wakeWindowMs * 0.6
            val satiety = when {
                hungry -> rng.range(Stream.SESSION, 0.85, 1.0) * (if (sleepy) 0.85 else 1.0)
                else -> (readiness * rng.range(Stream.SESSION, 0.5, 0.8)).coerceIn(0.1, 0.9)
            }
            val planned = if (kind == SessionKind.BOTTLE_FEED) rng.range(Stream.SESSION, 12.0, 20.0) else rng.range(Stream.SESSION, 15.0, 30.0)
            s.feed = FeedProgress((planned * MINUTE).toLong(), satiety, startDistress = distressAt(), sleepy = sleepy)
            s.steps.clear()
            s.steps += if (kind == SessionKind.BOTTLE_FEED) bottleFeedSteps(s.feed!!) else breastfeedSteps(s.feed!!)
            if (!hungry && distressAt() >= 26) {
                s.lastFeedback = "No clear hunger cues right now. $name may not take much — check other needs too."
            }
        }
        SessionKind.SETTLE -> {
            val count = if (w.practice?.drillId == "safe_sleep") 3 else listOf(0, 0, 1, 1, 2).let { it[rng.intRange(Stream.SESSION, 0, it.size - 1)] }
            val keys = HAZARDS.keys.toMutableList()
            repeat(count) { if (keys.isNotEmpty()) s.hazards += keys.removeAt(rng.intRange(Stream.SESSION, 0, keys.size - 1)) }
            s.steps[0] = sleepSpaceStep(s)
            coach("first_settle")
        }
        SessionKind.DIAPER_CHANGE -> coach("first_diaper")
        SessionKind.TUMMY_TIME -> {
            child.lastFeedEndedAt?.let { if (now - it < 15 * MINUTE) s.lastFeedback = "$name fed less than 15 minutes ago. Tummy time right after a feed can bring milk up — a short session is fine." }
        }
        else -> Unit
    }
    if (kind == SessionKind.BOTTLE_FEED || kind == SessionKind.BURP || kind == SessionKind.BREASTFEED) pickUpInternal()
    w.session = s
    if (w.clock.presence == Presence.FOREGROUND) s.runningSince = now
    w.stats.inc("sessions.PLAYER")
    if (s.night) w.stats.inc("sessions.night.PLAYER")
    log(LogType.SESSION_STARTED, "You started: ${kind.label}.", Caregiver.PLAYER, s.needIds.toList(), tag = kind.name)
    onStepEnter(s, s.steps[0])
    return Outcome.OK
}

// ------------------------------------------------------------------ step definitions

private fun Ctx.buildSteps(kind: SessionKind): List<SessionStep> = when (kind) {
    SessionKind.BOTTLE_PREP -> listOf(
        SessionStep("wash", "Wash your hands", "Soap and water for about 20 seconds.", 20 * SECOND),
        SessionStep(
            "water", "Measure the water", "Follow the fictional product label.",
            choices = listOf(
                StepChoice("water_first", "Pour the water first, to the line the label gives"),
                StepChoice("powder_first", "Put the powder in first", "blocked", "L-FEED-03", "Water first, then powder. Adding powder first makes the amount of water wrong."),
            ),
        ),
        SessionStep(
            "powder", "Add the formula powder", "The label says one level scoop per 60 mL (2 oz).",
            choices = listOf(
                StepChoice("exact", "Add level scoops exactly as the label says"),
                StepChoice("extra", "Add an extra scoop so the baby sleeps longer", "blocked", "L-FEED-03", "Extra powder makes formula too concentrated. It doesn't improve sleep and can harm the baby. Follow the label exactly."),
                StepChoice("fewer", "Use fewer scoops to make the tin last", "blocked", "L-FEED-03", "Watered-down formula doesn't give a baby enough nutrition and can be dangerous. If formula is hard to afford, a clinician or local services can help."),
            ),
        ),
        SessionStep("mix", "Mix", "Cap the bottle and swirl until the powder dissolves.", 60 * SECOND),
        SessionStep(
            "warm", "Warm it, if you like", "Babies can drink formula at room temperature.",
            choices = listOf(
                StepChoice("room", "Serve it at room temperature"),
                StepChoice("bowl", "Stand it in a bowl of warm water", addRefMs = 90 * SECOND),
                StepChoice("microwave", "Microwave it for a few seconds", "blocked", "L-FEED-03", "Microwaves heat unevenly and leave hot spots that can burn a baby's mouth. Use a bowl of warm water instead."),
            ),
        ),
        SessionStep("test", "Test the temperature", "A few drops on your wrist should feel lukewarm, not hot.", 15 * SECOND),
    )
    SessionKind.BURP -> listOf(
        SessionStep(
            "hold", "Choose a burping hold", "Support the head and neck.",
            choices = listOf(StepChoice("shoulder", "Upright against your shoulder"), StepChoice("lap", "Sitting on your lap, chin supported in your hand")),
        ),
        SessionStep("burp_pat", "Pat or rub the back gently", "Gentle, steady pats.", 90 * SECOND),
    )
    SessionKind.DIAPER_CHANGE -> listOf(
        SessionStep(
            "supplies", "Get ready", "A clean diaper, wipes and a spare outfit within reach.",
            choices = listOf(
                StepChoice("gather", "Gather everything first", addRefMs = 20 * SECOND),
                StepChoice("skip", "Start right away"),
            ),
        ),
        SessionStep("lay", "Lay $name on the changing surface", "Keep one hand on them the whole time.", 10 * SECOND),
        SessionStep("open", "Open the diaper", "Check what's there.", actionLabel = "Open and check"),
        SessionStep("clean", "Clean front to back", "Wipe gently from front to back.", 30 * SECOND),
        SessionStep(
            "fresh", "Fresh diaper", "Slide the clean diaper under and fasten it.",
            choices = listOf(
                StepChoice("below_stump", "Fasten it with the waistband folded below the cord stump"),
                StepChoice("over_stump", "Pull the waistband up over the belly", "poor", "L-HYG-01", "Fold the waistband below the umbilical cord stump so it stays dry and can heal."),
            ),
        ),
        SessionStep("dress", "Dress $name", "Snaps done, then pick them up.", 30 * SECOND),
        SessionStep("wash", "Clean up", "$name somewhere safe, diaper in the bin, wash your hands.", 30 * SECOND),
    )
    SessionKind.SETTLE -> listOf(
        SessionStep("space", "Check the sleep space", ""),
        SessionStep(
            "position", "Lay $name down", "How do you place them?",
            choices = listOf(
                StepChoice("back", "On their back"),
                StepChoice("side", "On their side", "blocked", "L-SLEEP-01", "Side sleeping isn't safe: babies can roll onto their tummy. Place them on their back for every sleep."),
                StepChoice("tummy", "On their tummy", "blocked", "L-SLEEP-01", "Tummy sleeping raises the risk of sudden infant death. Back for sleep; tummy time is for awake, supervised play."),
            ),
        ),
        SessionStep("watch", "Step back and watch", "Quiet voice, dim light. Give $name a few minutes.", 3 * MINUTE),
    )
    SessionKind.PLAY_TALK -> listOf(
        SessionStep("face", "Face to face", "About 20–30 cm (8–12 in) away: talk, smile, pause and wait for $name's response.", 4 * MINUTE),
    )
    SessionKind.TUMMY_TIME -> listOf(
        SessionStep("start", "Tummy time", "$name is awake. Where?", choices = listOf(StepChoice("floor", "On their tummy on a firm, flat surface, and stay right there"))),
        SessionStep("tummy", "Supervised tummy time", "Get down to $name's eye level and talk to them.", 3 * MINUTE),
        SessionStep("end_tummy", "Finish", "", actionLabel = "Roll $name onto their back and pick them up"),
    )
    SessionKind.BOTTLE_FEED, SessionKind.BREASTFEED -> emptyList()
}

private fun Ctx.bottleFeedSteps(f: FeedProgress): List<SessionStep> {
    val seg = (f.plannedRefMs * f.satiety / 3).toLong()
    val pace = { id: String ->
        SessionStep(
            id, "$name pauses", "Sucking slows and $name lets go for a moment.",
            choices = listOf(
                StepChoice("pause", "Pause, tilt the bottle down and let them breathe", addRefMs = 15 * SECOND),
                StepChoice("push", "Keep the bottle in so they finish faster", "poor", "L-FEED-04", "Paced feeding lets the baby take breaks. Pushing on can mean gulped air and spit-up."),
            ),
        )
    }
    return listOf(
        SessionStep(
            "hold", "Get into position", "How will you hold $name?",
            choices = listOf(
                StepChoice("close", "Hold $name close and semi-upright, head supported"),
                StepChoice("prop", "Prop the bottle on a pillow", "blocked", "L-FEED-04", "A propped bottle can cause choking and takes away the baby's control of the pace. Always hold the baby and the bottle."),
            ),
        ),
        SessionStep("offer", "Offer the bottle", "Touch the nipple to $name's lips and wait for the mouth to open.", actionLabel = "Offer the bottle"),
        SessionStep("feed1", "Feeding", "Bottle nearly horizontal, $name semi-upright.", seg),
        pace("pace1"),
        SessionStep("feed2", "Feeding", "Watch $name's cues.", seg),
        pace("pace2"),
        SessionStep("feed3", "Feeding", "Watch for fullness cues.", seg),
        fullnessStep(f),
        SessionStep(
            "burp", "After the feed", "Does $name need a burp?",
            choices = listOf(
                StepChoice("burp_now", "Burp: upright on your shoulder, gentle pats", addRefMs = 90 * SECOND),
                StepChoice("skip_burp", "Skip — $name seems comfortable"),
            ),
        ),
        SessionStep("cleanup", "Clean up", "Throw away formula left in the bottle and rinse it.", 30 * SECOND),
    )
}

private fun Ctx.breastfeedSteps(f: FeedProgress): List<SessionStep> {
    val total = f.plannedRefMs * f.satiety
    return listOf(
        SessionStep(
            "position", "Get comfortable", "Support your back and bring $name close, tummy to you.",
            choices = listOf(
                StepChoice("cradle", "Cradle hold"),
                StepChoice("cross", "Cross-cradle hold"),
                StepChoice("football", "Football (clutch) hold"),
                StepChoice("laidback", "Laid-back, $name on your chest"),
            ),
        ),
        SessionStep("latch", "Latch", "Nose to nipple; wait for a wide-open mouth, then bring $name in.", actionLabel = "Bring $name in to latch"),
        SessionStep("side1", "Feeding, first side", "Listen for swallowing; watch $name's hands and face.", (total * 0.55).toLong()),
        SessionStep(
            "switch", "$name slows and lets go", "",
            choices = listOf(
                StepChoice("switch", "Burp, then offer the other side", addRefMs = 60 * SECOND),
                StepChoice("end_one", "End here — $name seems content"),
            ),
        ),
        SessionStep("side2", "Feeding, second side", "$name may take less on this side.", (total * 0.45).toLong()),
        fullnessStep(f),
    )
}

private fun Ctx.fullnessStep(f: FeedProgress) = SessionStep(
    "fullness", "Fullness cues",
    if (f.sleepy) "$name's eyes are closing, sucking slows to gentle flutters, hands relaxed." else "$name turns away, closes their mouth and relaxes their hands.",
    choices = listOf(
        StepChoice("end_feed", "End the feed"),
        StepChoice("finish", "Encourage $name to finish", "blocked", "L-FEED-04", "Let the baby decide how much. Pushing past fullness can lead to spit-up and discomfort."),
    ),
)

private fun Ctx.sleepSpaceStep(s: CareSession): SessionStep {
    val hazardChoices = s.hazards.map { StepChoice("remove:$it", "Remove ${HAZARDS[it]}") }
    val desc = if (s.hazards.isEmpty()) "Bassinet: firm, flat mattress and a fitted sheet." else
        "In the bassinet: a firm mattress, a fitted sheet, " + s.hazards.joinToString(", ") { HAZARDS[it]!!.removePrefix("the ") } + "."
    return SessionStep(
        "space", "Check the sleep space", desc,
        choices = hazardChoices + StepChoice("clear", "It's clear: firm, flat mattress, fitted sheet, nothing else"),
    )
}

// ------------------------------------------------------------------ entering and finishing steps

private fun Ctx.onStepEnter(s: CareSession, step: SessionStep) {
    when (step.id) {
        "lay" -> {
            child.place = Place.CHANGING_TABLE
            child.heldBy = null
            child.holdStartedAt = null
        }
        "watch" -> {
            transferToBassinet(Caregiver.PLAYER)
            w.practice?.guidedSleepTransfer = true
        }
        "tummy" -> {
            child.heldBy = null
            child.holdStartedAt = null
            child.place = Place.PLAY_MAT
            child.onTummy = true
        }
    }
}

private fun Ctx.onTimedStepDone(s: CareSession, step: SessionStep) {
    val f = s.feed
    when {
        step.id.startsWith("feed") && f != null -> {
            val i = step.id.removePrefix("feed").toIntOrNull() ?: 1
            f.intake = f.satiety * i / 3.0
            if (i < 3 && !f.gasDuringFeed && rng.chance(Stream.GAS, gasRisk(f) * 0.45)) {
                f.gasDuringFeed = true
                openNeed(NeedKind.GAS)
                s.insertNext(
                    SessionStep(
                        "gas", "$name pulls off the bottle", "Squirming, grimacing, legs pulled up.",
                        choices = listOf(
                            StepChoice("burp_mid", "Pause and try a burp", addRefMs = 90 * SECOND),
                            StepChoice("keep", "Keep offering the bottle", "poor", "L-FEED-05", "Pulling off and squirming can mean trapped air. A short burping break usually helps the feed go better."),
                        ),
                    ),
                )
            }
        }
        step.id == "side1" && f != null -> f.intake = f.satiety * 0.55
        step.id == "side2" && f != null -> f.intake = f.satiety
        step.id.startsWith("burp") || step.id == "pat2" || step.id.endsWith("_burp_mid") || step.id.endsWith("_burp_now") || step.id.endsWith("_switch") -> handleBurpDone(s, step)
        step.id == "watch" -> {
            s.lastFeedback = when {
                child.sleep == SleepState.ASLEEP -> "$name is asleep on their back in a clear bassinet."
                child.fallAsleepAt != null -> "$name is drowsy and quiet, and likely to drift off soon."
                w.needs.any { it.kind != NeedKind.TIRED && it.kind != NeedKind.WET } -> "$name isn't settling. Check other needs: when did they last feed? Diaper? Gas?"
                else -> "$name is awake and content in the bassinet for now."
            }
            if (child.sleep == SleepState.ASLEEP || child.fallAsleepAt != null) w.stats.inc("settle.PLAYER")
        }
        step.id.startsWith("face") -> {
            child.playRefMsThisWake += step.refMs
            w.stats.inc("play.PLAYER")
            val p = ((child.playRefMsThisWake - 3 * MINUTE).toDouble() / (8 * MINUTE)).coerceIn(0.0, 0.85)
            if (rng.chance(Stream.PLAY, p)) {
                openNeed(NeedKind.OVERSTIM)
                s.lastFeedback = "$name is turning away and hiccuping. That's enough play for now."
                s.insertNext(
                    SessionStep(
                        "overstim", "$name has had enough", "Turning away, hiccups, jerky movements.",
                        choices = listOf(
                            StepChoice("rest", "Stop and give quiet time"),
                            StepChoice("more", "Keep playing anyway", "poor", "L-CRY-02", "Turning away and hiccups can mean \"that's enough\". Quiet, dim time helps a baby recover."),
                        ),
                    ),
                )
            } else {
                s.lastFeedback = "$name watched your face and made little sounds back. That's serve and return."
                s.insertNext(
                    SessionStep(
                        "more", "Keep going?", "",
                        choices = listOf(
                            StepChoice("rest", "Let $name rest now"),
                            StepChoice("again", "Another round", addRefMs = 4 * MINUTE),
                        ),
                    ),
                )
            }
        }
        step.id == "tummy" -> {
            child.playRefMsThisWake += step.refMs
            w.stats.inc("tummy.PLAYER")
            coach("tummy_time")
            s.lastFeedback = if (rng.chance(Stream.PLAY, 0.5)) "$name lifted their head for a moment, then started to fuss. A good time to stop." else "$name lifted their head and looked around. Nice work."
        }
        step.id == "dress" -> {
            child.place = Place.ARMS
            child.heldBy = Caregiver.PLAYER
            child.holdStartedAt = now
        }
        step.id == "cleanup" -> child.bottle.preparedAt = null
    }
}

private fun Ctx.gasRisk(f: FeedProgress): Double {
    var r = if (w.session?.kind == SessionKind.BREASTFEED) 0.18 else 0.30
    if (f.startDistress >= 60) r += 0.25
    r += 0.15 * f.pacedSkipped
    if (f.burpedDuringFeed) r *= 0.5
    return r.coerceIn(0.0, 0.9)
}

private fun Ctx.handleBurpDone(s: CareSession, step: SessionStep) {
    val gas = w.needs.firstOrNull { it.kind == NeedKind.GAS }
    s.feed?.burpedDuringFeed = true
    if (gas != null) {
        if (rng.chance(Stream.GAS, 0.75)) {
            resolveNeed(gas, Caregiver.PLAYER, "A good burp! $name relaxes.")
            s.lastFeedback = "A good burp. $name relaxes."
            w.stats.inc("burp.PLAYER")
        } else {
            s.lastFeedback = "No burp yet."
            if (s.kind == SessionKind.BURP && step.id != "pat2") {
                s.insertNext(
                    SessionStep(
                        "more_burp", "Still squirming", "",
                        choices = listOf(StepChoice("longer", "Keep going a little longer", addRefMs = 60 * SECOND), StepChoice("stop", "Stop for now")),
                    ),
                )
            }
        }
    } else {
        s.lastFeedback = if (s.kind == SessionKind.BURP) "No burp, and $name seems comfortable. No need to keep going." else "Burping done."
        if (step.id.contains("burp")) w.stats.inc("burp.PLAYER")
    }
}

private fun Ctx.finishSession(s: CareSession) {
    checkpointSession(s)
    w.session = null
    w.stats.add("reference.PLAYER", s.refDoneMs.toDouble())
    w.stats.add("effort.PLAYER", s.handsOnMs.toDouble())
    when (s.kind) {
        SessionKind.BOTTLE_PREP -> {
            child.bottle.preparedAt = now
            w.stats.inc("prep.PLAYER")
            log(LogType.CARE_DONE, "You prepared a bottle (${formatDuration(s.handsOnMs)}).", Caregiver.PLAYER, tag = "prep")
            coach("route_bottle")
        }
        SessionKind.BOTTLE_FEED, SessionKind.BREASTFEED -> {
            val f = s.feed!!
            if (!f.burpedDuringFeed || s.kind == SessionKind.BREASTFEED) {
                child.gasRisk = gasRisk(f)
                if (!f.burpedDuringFeed && w.needs.none { it.kind == NeedKind.GAS } && rng.chance(Stream.GAS, child.gasRisk)) {
                    child.gasDueAt = now + (rng.range(Stream.GAS, 3.0, 20.0) * MINUTE).toLong()
                }
            }
            if (s.kind == SessionKind.BOTTLE_FEED) child.bottle.preparedAt = null
            log(LogType.SESSION_ENDED, "Feed finished (${formatDuration(s.handsOnMs)} of your time).", Caregiver.PLAYER, tag = s.kind.name)
        }
        SessionKind.DIAPER_CHANGE -> {
            if (child.place == Place.CHANGING_TABLE) {
                child.place = Place.ARMS
                child.heldBy = Caregiver.PLAYER
                child.holdStartedAt = now
            }
            log(LogType.SESSION_ENDED, if (s.revealed == "clean") "Diaper check done." else "Diaper change done (${formatDuration(s.handsOnMs)}).", Caregiver.PLAYER, tag = s.kind.name)
        }
        SessionKind.TUMMY_TIME -> {
            child.onTummy = false
            log(LogType.SESSION_ENDED, "Tummy time done.", Caregiver.PLAYER, tag = s.kind.name)
        }
        else -> log(LogType.SESSION_ENDED, "${s.kind.label} done (${formatDuration(s.handsOnMs)}).", Caregiver.PLAYER, tag = s.kind.name)
    }
    w.lastFeedback = s.lastFeedback
    w.lastFeedbackAt = now
}

/** Ends a session before its last step, keeping completed steps (docs/10 §4). */
internal fun Ctx.finishSessionEarly(s: CareSession, why: String) {
    checkpointSession(s)
    w.session = null
    w.stats.add("reference.PLAYER", s.refDoneMs.toDouble())
    w.stats.add("effort.PLAYER", s.handsOnMs.toDouble())
    val f = s.feed
    if (f != null && f.transferStartedAt != null && !f.fullnessShown) {
        val step = s.currentStep
        val segFraction = if (step != null && step.isTimed() && (step.id.startsWith("feed") || step.id.startsWith("side"))) {
            (s.stepDoneMs / scaledMs(step.refMs, s)).coerceIn(0.0, 1.0) * step.refMs / max(1.0, f.plannedRefMs.toDouble())
        } else 0.0
        val intake = min(f.satiety, f.intake + segFraction)
        completeFeed(intake / f.satiety, interrupted = true, who = Caregiver.PLAYER, transferStart = f.transferStartedAt!!)
        if (s.kind == SessionKind.BOTTLE_FEED) child.bottle.preparedAt = null
    }
    if (child.place == Place.CHANGING_TABLE) {
        child.place = Place.ARMS
        child.heldBy = Caregiver.PLAYER
        child.holdStartedAt = now
    }
    child.onTummy = false
    log(LogType.SESSION_ENDED, "${s.kind.label} stopped ($why) after ${formatDuration(s.handsOnMs)}. Completed steps are kept.", Caregiver.PLAYER, s.needIds.toList(), tag = s.kind.name + ":STOPPED")
    w.lastFeedback = null
}

internal fun Ctx.stopSession(): Outcome {
    val s = w.session ?: return Outcome.fail("No care in progress.")
    finishSessionEarly(s, "you stopped")
    return Outcome.OK
}

internal fun Ctx.suspendSession(reason: String) {
    val s = w.session ?: return
    if (s.runningSince == null) return
    checkpointSession(s)
    s.runningSince = null
    s.lastFeedback = "Paused ($reason). Your completed steps are saved."
}

internal fun Ctx.resumeSession(): Outcome {
    val s = w.session ?: return Outcome.fail("No care in progress.")
    if (w.clock.presence != Presence.FOREGROUND) return Outcome.fail("Open the app to continue care.")
    if (s.runningSince == null) {
        s.runningSince = now
        s.lastFeedback = null
        if (child.place != Place.CHANGING_TABLE && s.currentStep?.id in setOf("clean", "fresh", "dress", "missing", "open")) {
            child.place = Place.CHANGING_TABLE
            child.heldBy = null
        }
    }
    return Outcome.OK
}

internal fun Ctx.handOffSession(): Outcome {
    val s = w.session ?: return Outcome.fail("No care in progress.")
    handOffSessionInternal(s)
    return Outcome.info("$helperName is taking over.")
}

internal fun Ctx.handOffSessionInternal(s: CareSession) {
    finishSessionEarly(s, "handed off to $helperName")
    val ids = s.needIds.toSet()
    w.needs.filter { it.id in ids }.forEach { it.owner = Caregiver.HELPER }
    w.stats.inc("handoff.session")
    if (child.heldBy == Caregiver.PLAYER) {
        child.heldBy = Caregiver.HELPER
    }
    w.helper.nextActionAt = w.helper.nextActionAt ?: now + 30_000
}

// ------------------------------------------------------------------ choices and one-tap steps

internal fun Ctx.sessionChoose(choiceId: String): Outcome {
    val s = w.session ?: return Outcome.fail("No care in progress.")
    val step = s.currentStep ?: return Outcome.fail("No step to answer.")
    val choice = step.choices.firstOrNull { it.id == choiceId } ?: return Outcome.fail("That option isn't available.")
    if (s.runningSince == null) {
        if (w.clock.presence != Presence.FOREGROUND) return Outcome.fail("Open the app to continue care.")
        s.runningSince = now
    }
    checkpointSession(s)
    // Sleep-space hazards are removed one by one.
    if (step.id == "space") {
        if (choiceId.startsWith("remove:")) {
            val h = choiceId.removePrefix("remove:")
            s.hazards.remove(h)
            s.replaceCurrent(sleepSpaceStep(s))
            s.lastFeedback = "Removed ${HAZARDS[h]}."
            return Outcome.OK
        }
        if (s.hazards.isNotEmpty()) {
            val text = "There's still ${HAZARDS[s.hazards.first()]} in the bassinet. Soft items raise the risk of suffocation: clear them first."
            misstep("L-SLEEP-01", text)
            s.lastFeedback = text
            return Outcome.info(text)
        }
        s.stepLog += step.title
        nextStep(s)
        return Outcome.OK
    }
    when (choice.verdict) {
        "blocked" -> {
            val text = choice.coaching ?: "That isn't safe."
            misstep(choice.lessonId, text)
            s.lastFeedback = text
            return Outcome.info(text)
        }
        "poor" -> {
            misstep(choice.lessonId, choice.coaching ?: "")
            s.lastFeedback = choice.coaching
        }
        else -> if (s.lastFeedback?.startsWith("Removed") == true || step.id != "fullness") s.lastFeedback = null
    }
    s.stepLog += "${step.title}: ${choice.label}"
    val f = s.feed
    when (step.id) {
        "supplies" -> s.suppliesReady = choiceId == "gather"
        "pace1", "pace2" -> if (choiceId == "pause") f?.pacedOk = (f?.pacedOk ?: 0) + 1 else f?.pacedSkipped = (f?.pacedSkipped ?: 0) + 1
        "fullness" -> if (f != null && choiceId == "end_feed") {
            f.fullnessShown = true
            f.intake = f.satiety
            completeFeed(1.0, interrupted = false, who = Caregiver.PLAYER, transferStart = f.transferStartedAt ?: s.startedAt)
            s.lastFeedback = "$name is full. You followed the fullness cues."
        }
        "switch" -> if (f != null && choiceId == "end_one") {
            f.fullnessShown = true
            val accepted = (f.intake / f.satiety).coerceIn(0.0, 1.0)
            completeFeed(accepted, interrupted = accepted < 0.95, who = Caregiver.PLAYER, transferStart = f.transferStartedAt ?: s.startedAt)
            // Skip the second side and the fullness step.
            while (s.steps.size > s.stepIndex + 1) s.steps.removeAt(s.stepIndex + 1)
        }
        "fresh" -> diaperChanged(Caregiver.PLAYER)
        "missing" -> if (choiceId == "together") s.suppliesReady = true
        "overstim" -> if (choiceId == "more") {
            w.needs.firstOrNull { it.kind == NeedKind.OVERSTIM }?.let { n -> n.phaseElapsedMs = n.phaseDurationMs.toDouble() }
        }
        "more" -> if (choiceId == "again") s.insertNext(SessionStep("face2", "Face to face", "Talk, smile, pause, wait.", 4 * MINUTE))
        "more_burp" -> if (choiceId == "longer") s.insertNext(SessionStep("pat2", "Pat a little longer", "Gentle, steady pats.", 60 * SECOND))
    }
    if (w.session !== s) return Outcome.OK
    if (choice.addRefMs > 0 && step.id != "more" && step.id != "more_burp") {
        val id = when (choiceId) {
            "burp_mid", "burp_now", "switch" -> "${step.id}_$choiceId"
            else -> "${step.id}_do"
        }
        s.insertNext(SessionStep(id, choice.label, "", choice.addRefMs))
    }
    nextStep(s)
    return Outcome.OK
}

internal fun Ctx.sessionDoStep(): Outcome {
    val s = w.session ?: return Outcome.fail("No care in progress.")
    val step = s.currentStep ?: return Outcome.fail("No step.")
    if (step.actionLabel == null) return Outcome.fail("This step continues on its own.")
    if (s.runningSince == null) {
        if (w.clock.presence != Presence.FOREGROUND) return Outcome.fail("Open the app to continue care.")
        s.runningSince = now
    }
    checkpointSession(s)
    when (step.id) {
        "offer", "latch" -> {
            val d = distressAt()
            if (d >= 75) {
                coach("late_feed")
                s.lastFeedback = "$name is crying too hard to ${if (step.id == "offer") "take the bottle" else "latch"}. Hold and rock or shush for a minute, then try again."
                return Outcome.info(s.lastFeedback!!)
            }
            s.feed?.transferStartedAt = now
            if (step.id == "latch" && rng.chance(Stream.SESSION, 0.22)) {
                s.insertNext(
                    SessionStep(
                        "shallow", "It pinches", "You hear clicking, and $name's lips are tucked in.",
                        choices = listOf(
                            StepChoice("relatch", "Break the seal gently with a finger and latch again", addRefMs = 30 * SECOND),
                            StepChoice("endure", "Keep going through the pain", "poor", "L-FEED-02", "A shallow latch can hurt and make feeds less effective. Re-latch, and ask for skilled feeding help if pain continues."),
                        ),
                    ),
                )
            }
            s.lastFeedback = if (step.id == "offer") "$name latches onto the bottle and starts to drink." else "$name latches and you hear swallowing."
        }
        "open" -> {
            val d = child.diaper
            s.revealed = when {
                d.blowout -> "blowout"
                d.dirty -> "dirty"
                d.wet > 0 -> "wet"
                else -> "clean"
            }
            d.lastCheckedAt = now
            w.stats.inc("check.PLAYER")
            if (s.revealed == "clean") {
                s.lastFeedback = "Clean and dry. No change needed."
                while (s.steps.size > s.stepIndex + 1) s.steps.removeAt(s.stepIndex + 1)
                s.insertNext(SessionStep("close", "Fasten it back up", "", actionLabel = "Fasten the diaper"))
            } else {
                val cleanRef = when (s.revealed) { "blowout" -> 150 * SECOND; "dirty" -> 60 * SECOND; else -> 20 * SECOND }
                val cleanIdx = s.steps.indexOfFirst { it.id == "clean" }
                if (cleanIdx >= 0) s.steps[cleanIdx] = s.steps[cleanIdx].copy(refMs = cleanRef)
                if (s.revealed == "blowout") {
                    val dressIdx = s.steps.indexOfFirst { it.id == "dress" }
                    if (dressIdx >= 0) s.steps[dressIdx] = s.steps[dressIdx].copy(title = "Clean clothes too", instruction = "The blowout reached the outfit: fresh clothes, soiled ones in a bag.", refMs = 120 * SECOND)
                }
                s.lastFeedback = when (s.revealed) {
                    "blowout" -> "A blowout, up the back. This one takes a while."
                    "dirty" -> "A dirty diaper."
                    else -> "A wet diaper."
                }
                if (d.wet > 0 && rng.chance(Stream.SESSION, 0.08)) {
                    s.lastFeedback += " Surprise: a pee fountain! A cloth over the area helps next time."
                    s.insertNext(SessionStep("fountain", "Quick cleanup", "Mop up and carry on.", 20 * SECOND))
                }
                if (!s.suppliesReady) {
                    s.insertNext(
                        SessionStep(
                            "missing", "The wipes are out of reach", "You didn't gather supplies first.",
                            choices = listOf(
                                StepChoice("step_away", "Step away for a second to grab them", "blocked", "L-SAFE-05", "Never leave a baby on a raised surface, even for a second: babies can wriggle off. Take them with you."),
                                StepChoice("together", "Pick $name up and get the wipes together", addRefMs = 30 * SECOND),
                            ),
                        ),
                    )
                }
            }
        }
        "close" -> {
            log(LogType.CHECK, "You checked the diaper: clean and dry.", Caregiver.PLAYER, tag = "check")
            child.place = Place.ARMS
            child.heldBy = Caregiver.PLAYER
            child.holdStartedAt = now
        }
        "end_tummy" -> {
            child.onTummy = false
            child.place = Place.ARMS
            child.heldBy = Caregiver.PLAYER
            child.holdStartedAt = now
        }
    }
    s.stepLog += step.title
    nextStep(s)
    return Outcome.OK
}

// ------------------------------------------------------------------ quick actions (docs/02 §4)

internal fun Ctx.pickUpInternal() {
    if (child.heldBy == Caregiver.PLAYER) return
    if (child.heldBy == Caregiver.HELPER) w.helper.task = null
    child.heldBy = Caregiver.PLAYER
    child.holdStartedAt = now
    child.place = Place.ARMS
    child.onTummy = false
}

internal fun Ctx.pickUp(): Outcome {
    if (w.session != null && w.session!!.kind != SessionKind.SETTLE) return Outcome.fail("Finish or stop the current care first.")
    if (child.heldBy == Caregiver.PLAYER) return Outcome.OK
    val asleep = child.sleep == SleepState.ASLEEP
    pickUpInternal()
    w.stats.inc("pickup.PLAYER")
    if (asleep && rng.chance(Stream.SLEEP, 0.6)) {
        wakeUp("being picked up")
        log(LogType.COMFORT, "You picked $name up and they woke. Squirming and grunting in sleep is often normal active sleep: pausing a moment first can help.", Caregiver.PLAYER)
    } else {
        log(LogType.COMFORT, "You picked $name up.", Caregiver.PLAYER)
    }
    return Outcome.OK
}

internal fun Ctx.putDown(place: Place): Outcome {
    if (w.session != null && w.session!!.kind != SessionKind.SETTLE) return Outcome.fail("Finish or stop the current care first.")
    if (child.heldBy != Caregiver.PLAYER) return Outcome.fail("You're not holding $name.")
    if (place == Place.BASSINET && w.practice?.let { it.drillId == PracticeDrill.SAFE_SLEEP.id && !it.objectiveMet } == true) {
        return Outcome.fail("Use Transfer to bassinet to check the sleep space first.")
    }
    if (place == Place.PLAY_MAT) {
        if (child.sleep == SleepState.ASLEEP) return Outcome.fail("Sleeping babies go in the bassinet, on their back.")
        child.heldBy = null
        child.holdStartedAt = null
        child.place = Place.PLAY_MAT
        child.techniques.remove(Technique.ROCK)
        log(LogType.COMFORT, "You laid $name on their back on the play mat, within sight.", Caregiver.PLAYER)
    } else {
        transferToBassinet(Caregiver.PLAYER)
        log(LogType.COMFORT, "You laid $name on their back in the bassinet.", Caregiver.PLAYER)
    }
    return Outcome.OK
}

internal fun Ctx.setTechnique(t: Technique, on: Boolean): Outcome {
    if (!on) {
        if (child.techniques.remove(t) != null) log(LogType.COMFORT, "You stopped: ${t.label.lowercase()}.", Caregiver.PLAYER, tag = t.name + ":OFF")
        return Outcome.OK
    }
    if (t == Technique.ROCK && child.heldBy != Caregiver.PLAYER) {
        if (w.session != null) return Outcome.fail("Your hands are busy with the current care.")
        pickUpInternal()
    }
    if (t in child.techniques) return Outcome.OK
    child.techniques[t] = now
    w.stats.inc("comfort.${t.name}")
    log(LogType.COMFORT, "You started: ${t.label.lowercase()}.", Caregiver.PLAYER, tag = t.name)
    return Outcome.OK
}

internal fun Ctx.checkDiaper(): Outcome {
    val d = child.diaper
    d.lastCheckedAt = now
    w.stats.inc("check.PLAYER")
    val text = when {
        d.blowout -> "Diaper check: a blowout! It needs a change and clean clothes."
        d.dirty -> "Diaper check: dirty. It needs a change."
        d.wet > 1 -> "Diaper check: quite wet. Time for a change."
        d.wet == 1 -> "Diaper check: a little wet."
        else -> "Diaper check: clean and dry."
    }
    log(LogType.CHECK, text, Caregiver.PLAYER, tag = "check")
    return Outcome.info(text)
}
