# 12 — Roadmap, Risks & Open Questions

> Status: Draft v0.3 · 2026-11-18

## 1. Phase overview

| Phase | Playable outcome |
|---|---|
| **0 · Vertical slice** | One scripted feed, change and settle sequence with a simulated clock, a care log and an Android notification prototype |
| **1 · Newborn Weekend Trial** | One three-week-old child, 48 real hours, timed care, settings for workload and nights, a small practice set and a Care Report |
| **2 · Year One Journey** | Monthly chapters, growth speed, new capabilities and needs, Memory Book, later-infant scenarios |
| **3 · Toddler and preschool** | Supervision, toilet readiness, relationships, play, choices, daycare and household planning |
| **4 · Multiple children** | Start with twins on one phone, then a baby with an older sibling. Add triplets after capacity and safety rules are tested |
| **5 · School age** | Three-meal routine, school handoffs, friendships, activities, reading and homework through age 12 |
| **6 · Extended** | Teen years, connected co-parenting, localization or additional family settings as justified |

## 2. Phase details

### Phase 0 — Foundations

Build the minimal Kotlin simulation with fixtures for day 21, age 3 months and age 8. Use the day-21 fixture to open simultaneous hunger and diaper needs, test an alternative action, complete care, and inspect the next forecast.

On Android, prove that the normal notification can open the right active need on a locked phone. Try the opt-in night channel and assess exact timing viability alongside ordinary reminders before investing in night-specific features. Record behavior with disabled notifications, a muted channel, Doze and force-stop.

**Exit criteria**

- Two replays of the same seed and commands produce the same log. Advancing in one step and in small steps yields the same completed events.
- Automated checks resolve the planned monthly and yearly ages, every stage boundary, and the terminal cap. Standard pace reaches ages 1 and 5 after 32 and 62 active days.
- A device run posts a routine reminder outside the app and opens current care after a tap. A disabled channel and a stale need both produce the expected fallback.

### Phase 1 — MVP: Newborn Weekend Trial

At start, choose a feeding route, a player sleep window, a realism preset and optional scheduled night coverage. The three-week age stays fixed. Both daily care and the 48-hour clock progress while the app is closed. Take an immediate pause, hand care to a helper or end the run from the app.

| Slice | Minimum playable behavior |
|---|---|
| Hunger and gas | Recognize feeding cues, select bottle or breast/chestfeeding, pace a timed feed, stop at fullness, and respond to gas or a partial feed |
| Diaper | Check the diaper. Gather supplies, change, clean up and log it. Group with a feed when both occur in one wake |
| Sleep and comfort | Read tiredness or overstimulation cues. Pick up, soothe and transfer to a safe virtual sleep space. Allow some fussing to persist after responsive care |
| Helper care | Schedule automatic night coverage and hand off an active care group. Show the helper's actions in the log |
| Realism | Gentle, Standard and Realistic exposure with the same age-based routine. Extreme Real can offer at most two opt-in audible attempts per sleep window on a supported configuration |
| Teaching | A short practice loop, contextual explanations for cue-based feeding, changing, safe sleep, soothing and taking a break, and a short knowledge check. Start with the first-trial lessons in [09](09-education-curriculum.md) |
| Report | Feeds and changes, active session time, helper coverage, audible alert attempts, overnight care sessions and reflection prompts |
| Input | Touch and TalkBack-compatible care steps, cue descriptions, scalable text and optional in-app sound |

In the first released content, authored variation can include a cluster-feeding evening, a dirty diaper during a feed and persistent fussing after routine checks. Author and review the core actions before moving into broader medical or emergency scenarios. Custom presets, more starting ages, a 72-hour option, inventory, sensor gestures and detailed character animation can follow the first trial.

**Exit criteria**

- Complete a 48-hour soak on at least two physical devices from different manufacturers. Check pause, handoff, sensor-free inputs, reminder denial and recovery after restart. Run the locked-device night test and document observed delay or missed posts before enabling audible wake-ups.
- Show that a disabled channel, revoked exact alarm access, Do Not Disturb and any delayed callback produce the documented experience. Cover both player-handled and helper-handled nights. Deliver zero stale audible attempts after pause, handoff, expiration or trial end in tests.
- Sample reproducible 24-hour periods across multiple seeds after warm-up. Report feed counts, completed changes, rest, care groups and alert groups against the day-21 profile in [03](03-realistic-frequency-tables.md). Tune out systematic under- or over-scheduling. At age 3 months a seeded day can reach eight milk feeds. The school-age fixture has exactly three meal slots at age 8.
- Core steps work with TalkBack, large text and silent cues. All released lesson, quiz and safety-scenario claims have a dated source and review record. Test participants can describe at least two recurring responsibilities and a practical plan for help after the trial.

### Phase 2 — Year One Journey

Turn on growth checkpoints, first-year monthly chapters, pace presets, and ability-triggered changes. Add supported routes for solids, teething, mobility and safe sleep changes. Link new scenarios to cue-based decisions, practice and recap. Add introductory look and voice changes.

### Phase 3 — Toddler and preschool

Add more supervision settings, routines, toilet readiness, tantrum and repair scenes, daycare handoffs, and optional supply and budget planning. Show run-time work beyond prompts and notifications.

### Phase 4 — Multiple children

Give each child a separate history, schedule and care state. Model caregiver capacity, overlapping needs, group wake-ups and handoffs. Build twins first, test a newborn and a mobile sibling next, and add triplets when workload and safety cases work. See [08](08-multiple-children.md).

### Phase 5 — School age

Model school-day meals, preparation, drop-off and pickup, homework support, extracurricular activity and connection. Expand the 8-year-old test day into playable school-age scenarios.

### Phase 6 — Extended

Add optional two-phone co-parenting, more regional educational material and late-stage dialogue. Treat early birth, health conditions and any classroom use as separate content and audience work.

## 3. Content review plan

Draft with direct, dated sources. For the first US English release, prioritize responsive feeding, safe sleep, diapering, crying and caregiver support. A pediatric reviewer checks health and safety across lessons and scenarios. Seek feeding expertise for lactation content. Later emergency drills use a region-specific, reviewed first-aid module. See [09](09-education-curriculum.md) §5.

## 4. Project risks

| Risk | Response |
|---|---|
| Notifications are late, silent or suppressed | Prototype early and record measured behavior. Provide ordinary reminders and helper coverage |
| Periodic alerts generate the wrong workload | Use full-day balance replays, clear care-group metrics, and 3-month and 8-year spot checks |
| Draft game numbers are read as child-care instructions | Keep them in profile data and source the teaching cards independently |
| Night play disrupts the player or household | Bound audible attempts, expire consent per window, and provide pause and handoff |
| The first release becomes too large | Complete the four core needs, night handling and reflection before adding new ages and household mechanics |
| Multiple alerts or care actions race | Use durable run and group IDs, transactionally saved progress and reconciled scheduling |

## 5. Working defaults

The initial audience is adults exploring caregiving. Plan for offline Android, US English education, one virtual child, a fixed-age 48-hour trial at three weeks, Standard realism by default, and no account. Later Life Journey defaults to Standard growth pace. Treat bedtime, feeding route, schedule, names and appearance as player choices within the supported content.

## 6. Product decisions for later

Confirm the launch region and distribution channel before final content or Play submission. Choose an art direction after cue tests with placeholders, pick a public name before distribution and revisit whether a connected co-parent mode belongs before or after the school-age scenarios.

## 7. Implementation order

Start with the age resolver, event queue, feed/change/settle fixture and basic UI. Exercise notifications on hardware alongside the first loop. Use the logged behavior to tune care workload, then build the full trial and first lessons.

## 8. Build status

### v0.2.0 (2026-11-18): playtest feedback

A playtest of v0.1.0 gave five notes. All five are addressed in the app; the simulation, balance and alerts are unchanged.

| Feedback | Change |
|---|---|
| Setup too complex and wordy; give a default baby and only ask for effort | One-screen quick start: a ready-made baby and four realism cards with effort bars. Details are optional and editable later ([10](10-game-design-and-scoring.md) §3) |
| Options are all words; make actions visual and animate the baby | Every action, step and choice is a drawn, animated picture. The baby's body shows the care: sucking, patting, wiping, playing, tummy lifts ([10](10-game-design-and-scoring.md) §5.2, §11) |
| Hints only when checking the baby | Cue text, meters and hints appear only after touching a body part or using the magnifier ([10](10-game-design-and-scoring.md) §5.1, [05](05-realism-levels-and-notifications.md) §2) |
| The baby should be alive and react | Breathing, blinking, wiggles and kicks, crying with flailing limbs, newborn reflexes on touch, emote reactions after care |
| Learn from pet-raising games (Travel Frog, QQ Pet) | A nursery that follows the real time of day, touch interactions, emote bubbles and a Moments album ([10](10-game-design-and-scoring.md) §1.1) |

Checked on the Android 16 emulator: quick start, cheek and hand reflexes, face and diaper checks, bottle-prep picture steps, the "!" reaction on a blocked choice, the diaper-change animation and Moments unlocking. `:sim:test` (including the balance harness) passes unchanged.

Still open from this round: a playtest of the new controls with TalkBack, and whether Gentle should open the check card automatically for first-time players.

### v0.1.0 (2026-11-17)

**Phase 0 is done in code.**
- Deterministic replay and step-size independence are tested.
- So are the age resolver (milestones, stage and chapter boundaries, terminal cap), the profile validator, and the day-21, 3-month and 8-year fixtures.
- On an Android 16 emulator, a routine reminder was scheduled from the projection and posted outside the app on time, with its sound and actions. Tapping "Care now" opened the current state and logged the open. Rescheduling after an app update was observed too.

**Phase 1 is playable end to end.**
- Onboarding: route, sleep window, helper, realism with Extreme opt-in and channel tests, and the knowledge check.
- The 48-hour trial with the SC-S0-41 opening fixture.
- Timed sessions: bottle prep, paced bottle feed, breast/chestfeed, burp, diaper, safe-sleep settle, play and tummy time.
- Comfort techniques and evening scenarios: cluster feeding and a fussy evening.
- Helper coverage, breaks and handoffs, with an escalation cap.
- Alerts: bundling, freshness, the Gentle cap and the two-per-night audible limit.
- Pause.
- The care log, Learn tab (98 draft lessons), Practice Room (six drills), Care Report and share export.

**Balance harness.** Responsive bot, Realistic preset, days 2–3 of 24 seeds per route, ±0.5 tolerance.

| Day-21 band | Formula: in band | Breast/chest: in band |
|---|---:|---:|
| Milk feeds / 24 h | 94% (mean 7.9) | 90% (mean 10.0) |
| Diaper changes / 24 h | 88% (mean 8.9) | 71% (mean 10.8) |
| Sleep hours / 24 h | 73% (mean 15.6) | 81% (mean 14.5) |
| Quiet-phase feed starts | 100% | 90% |
| Overnight wakes (23:00–07:00) | 92% | 79% |
| Need episodes / 24 h | 81% | 88% |

The 3-month breast/chest fixture reaches eight feeds on some seeded days (mean 7.0, band 6–8).

**Alert groups over 24 h** with the app closed and no response (6 seeds). The helper steps in at the escalation cap.

| Realism | Per 24 h (mean) | In the sleep window |
|---|---:|---:|
| Gentle | 8.0 (capped) | 0 |
| Standard | 15.5 | 0 |
| Realistic | 22.0 (day-21 band 17.6–23.6) | 5.5 |
| Extreme Real, opted in | 18.7 | 2.0 audible, then the helper |

**Still open before the Phase 1 exit criteria are met.**
- A 48-hour soak on two physical devices from different manufacturers, including locked-device night tests and documented delivery latency.
- Pediatric and lactation review of all lesson, quiz and safety copy. Everything is marked draft in the app.
- A TalkBack and large-text audit with participants.
- Recorded or licensed cry audio, if desired. v0.1.0 ships synthesized sounds.
- Tuning breast/chest-route changes and formula-route sleep toward ≥90% of days in band.
