# 11 — Technical Architecture (Android)

> Status: Draft v0.3 · 2026-11-18
> See [01 Age and time](01-age-and-time-model.md), [05 Alerts](05-realism-levels-and-notifications.md) and [12 Roadmap](12-roadmap-and-open-questions.md).

## 1. Platform and stack

Use native Android with Kotlin and Compose. Keep the simulation in a plain Kotlin/JVM module with an injected clock and a command interface. Start with an Android application module and the simulation module. Extract components as the feature set grows.

**v0.1.0 decisions.**
- **Run storage.** The Weekend Trial keeps one run, so it is stored as a single JSON snapshot with `AtomicFile` (§4). Move to Room once runs need history or queries.
- **Preferences.** Onboarding and display preferences use `SharedPreferences`.
- **Dependency injection.** A small manual container replaces a DI framework.
- **Toolchain.** AGP 8.13, Gradle 8.14, Kotlin 2.2 and JDK 21, with compile and target SDK 36.

Plan for `minSdk 26` for notification channels. Pick a compatible Gradle, Android Gradle Plugin, JDK and SDK combination when the project is scaffolded. The Android Studio bundled JDK is a starting point. Google Play currently lists API 36 as the target floor for new phone app submissions. Recheck the policy at release. [Android build JDKs](https://developer.android.com/build/jdks), [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)

## 2. Package layout

| Component | Responsibilities |
|---|---|
| `sim` | Age resolver, care processes, event queue, scenarios, deterministic projections and balance runner |
| `app` | Onboarding, care UI and mechanics, accessibility, repository, alarms and notifications, logs and reports |
| Bundled assets | Versioned age profile and approved lessons, scenarios and media |
| `docs` | Product and authoring specifications, and the source age data until the asset pipeline exists |

## 3. Simulation engine

### 3.1 Engine contract

- `advance(world, until)` processes time and due events up to a logical timestamp. It works in the app, a receiver or a JVM test.
- `apply(world, command)` reconciles earlier events and applies a valid action in the timestamp's ordered batch. It returns a new state plus events describing effects and completed steps.
- `project(world, horizon)` forecasts on a copy of that state if no further actions occur. Scheduling uses the derived alert opportunities. It consumes no live randomness and generates no live care events.
- Order equal timestamps by boundary priority then stable event ID. A trial end or coverage start suppresses any alert at that same instant. In the same batch, a completed care action at its deadline precedes unattended handling. An explicit pause takes effect before alert posting. Once a batch is committed, a later command acts on the committed state.

### 3.2 Clocks and age

Persist a logical rhythm timestamp, a wall timestamp, elapsed realtime at observation, and enough boot identity information to decide whether elapsed realtime is comparable. Use the Android elapsed-realtime delta during the same boot, including time spent asleep. After reboot or unclear boot identity, use the nonnegative wall delta. Over a long uncertain gap, advance at most the remaining trial duration or 48 hours of a Life Journey, apply helper coverage when alerts are unavailable, and record any remaining time as a gap awaiting resume. The 48-hour recovery cap is a gameplay default.

Use wall `Instant` for recovery or projections to the device clock. Apply the schedule zone when interpreting local slots and showing times. A change to wall time or zone replans local slots and associated wake-ups. It preserves elapsed intervals. Save an age checkpoint at changes in growth pace, and integrate the bands as defined in [01](01-age-and-time-model.md). A run records committed elapsed time separately from simulated age and pause duration.

### 3.3 State

A world has a run ID, simulation and content version, processed time, state revision, settings, coverage blocks, event queue, and per-child state. Each child has an age checkpoint and capabilities, feeding and diaper state, sleep and distress, seeded attributes and pending care. Persist stable IDs for child, need episode, session, wake and alert group.

Care sessions persist their completed steps, active elapsed time, child, underlying needs, configured duration and prompt state. Starting care for an already open need resumes its session where appropriate. Show a resumable view after activity recreation.

### 3.4 Commands and events

Commands carry a stable request ID, a run ID, logical timestamp where applicable, and the target need, session or group. Examples include `CheckDiaper`, `StartFeed`, `CompleteStep`, `ResolveNeed`, `StartCoverage`, `SilenceAlert`, `Pause` and `Resume`. Validate the active state and required steps in the engine. Duplicate or expired requests have no effect after reconciliation.

Events include `NeedOpened`, `NeedPhaseChanged`, `CareStarted`, `CareStepCompleted`, `CareFinished`, `CoverageHandoff`, `WakeOpened`, `StageChanged`, `SafetyCoaching` and `RunEnded`. Android alert attempts, responses and suppressions use a delivery ledger. Posting does not change the child's internal needs.

### 3.5 Need processes

| Process | Baseline behavior |
|---|---|
| **Infant milk** | Sample a seed-based daily target rate `n` from the age band's range for the selected feeding route. Let `q` be the fraction of a nominal day in the child's quiet phase, and `r` the age-band quiet-to-active interval ratio. The active hunger rate per minute is `n / (1440 × ((1−q) + q/r))`. In the quiet phase divide that rate by `r`. Build demand from the start of the last feed. Schedule a cue when accumulated demand reaches the sampled threshold. |
| **Feeding feedback** | The next demand threshold starts at one and varies by seeded interval jitter. If a session ended because it was interrupted, multiply the threshold by `clamp(0.3 + 0.7 × acceptedFraction, 0.4, 1)`. When the child signals fullness treat the session as complete. Apply short scenario modifiers for cluster feeding. `quietPhaseMilkStartsPerDay` is a balance target. Sleep and hunger share a wake ID when hunger starts or continues in a waking. |
| **Meals and snacks** | Use eligible local meal slots after readiness and age gates. Record who covers each slot. Set an actionable task if the player owns an upcoming meal or the child asks for help. |
| **Diaper** | Sample wet and stool events based on the selected route and profile. Combine them in the diaper state. One change clears the state and logs all events it addressed. |
| **Sleep** | Track awake time, sleep pressure, recent naps, quiet phase and readiness to settle. Coordinate waking with active needs. Record rest intervals to derive total sleep and nap counts. |
| **Distress** | Recompute on relevant events. Unmet needs and scenario modifiers raise pressure. Comfort changes the visible distress. Only an action addressing the active need or a helper can resolve it. See [04](04-crying-and-soothing.md). |
| **Connection and play** | Build context from the recent care log and the child's state. Offer short interactions the player can try. Expand age-related and social scenarios in later phases. |

Daily samples and intervals use simulation-only units. The engine accepts no real-child measurements and contains no dosing or diagnostic rules.

### 3.6 Reproducibility

Seed random streams per child and process, and sample at stable event boundaries. Persist the stream counters or the sampled times and parameters needed for replay. Pin data version and engine version to every fixture. Test that incremental advancement, one catch-up step and a read-only projection with no intervening commands forecast equivalent due events.

### 3.7 Coverage and unattended care

Resolve covered needs with a helper policy that records time, care actions and remaining items. Coverage can span only part of a day. A game-only escalation cap offers help and initiates a safe handoff for an unanswered routine need. A device delivery gap or a pause never creates a player score deduction. For emergency practice, switch to the reviewed educational branch without timing or audio pressure.

### 3.8 Scenarios

Use stable IDs, eligibility conditions, triggers, safety classes, player choices, effects on the virtual state and lesson links. Evaluate triggers on relevant state changes and at scheduled boundaries. Make repeatable practice explicit. Release scheduling and user participation follow [07](07-interactions-and-scenarios.md) Part D.

## 4. Data and persistence

Persist the world snapshot, appended events and alert state in one atomic file write (`AtomicFile`, v0.1.0) for each committed command. A database transaction can replace it later. Retain state and content revisions for debugging and migration. Store actual notification scheduling and post attempts by alert group and revision.

Serialize mutations through a repository. OS side effects happen after commit. Keep a durable record of desired alerts and retry reconciliation on next launch or receiver callback. Reuse stable notification IDs and pending-intent identities. A receiver validates the current plan generation, eligibility, need and consent before posting. Changes to unrelated care can retain the same alert plan generation. A retry can update a visible notification silently with the same ID. Record an uncertain attempt when a crash falls between claiming the post and receiving a result, and skip replaying its sound.

## 5. App lifecycle

Compose UI observes a snapshot from the repository. Use a foreground timer for session UI and due-event progress. Persist on commands and due transitions. Save and pause a hands-on session when the app backgrounds while continuing the rhythm clock. Background work runs only for scheduled wake-ups or maintenance. On return, rebuild the current screen and reconcile coverage and alerts. Unlocking a device does not complete care.

## 6. Notifications and alarms

### 6.1 Pipeline

1. Reconcile simulation state and compute the next alert opportunity with coverage, consent, age, current app activity, bundling and freshness from [05](05-realism-levels-and-notifications.md).
2. Schedule one next due wake-up on the OS. If several groups share it, handle their eligible requests together and schedule the next one. When an active foreground session moves to the background, reconcile its next actionable care opportunity.
3. The receiver advances the world, clears stale groups and posts only the still-actionable current group. Persist the post attempt or suppression reason and replan. Keep the receiver bounded. Use deferred work for maintenance or non-urgent reconciliation.

### 6.2 Scheduling methods

| Use | Android behavior |
|---|---|
| Routine background care | Inexact one-shot `AlarmManager` reminders. Use allow-while-idle only when a user-visible reminder calls for it. Idle restrictions can delay delivery |
| Eligible opt-in night wake-up | Prototype `setExactAndAllowWhileIdle()` where appropriate and available. Check `canScheduleExactAlarms()` on Android 12+. Catch a permission change during scheduling and reconcile to inexact reminders or helper coverage |
| Night audio | A single short system notification sound on a user-configured high-importance channel, with an actionable notification |
| Maintenance | WorkManager may run periodic cleanup and non-urgent work |

Android batches inexact work and limits allow-while-idle frequency. A settings or system change can affect timing at any time.

Inexact alarms can arrive up to 75% of their delay late. v0.1.0 therefore requests `target / 1.75` and re-plans when that alarm fires. Each step is shorter. A target under 2 minutes is requested directly, so a routine reminder is at most about 90 seconds late without exact-alarm access. Alarms use `ELAPSED_REALTIME_WAKEUP`, so a change to the phone's clock doesn't move them. [Android alarms](https://developer.android.com/develop/background-work/services/alarms), [Android Doze guidance](https://developer.android.com/training/monitoring-device-state/doze-standby)

### 6.3 Permissions and rescheduling

- MVP permissions are `POST_NOTIFICATIONS` where required, boot broadcasts for rescheduling, and optional vibration. An audible prototype may declare `SCHEDULE_EXACT_ALARM` and request special access with a clear user action. Many fresh Android 14 installs begin with that access denied. [Android exact alarms behavior](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)
- Use the normal notification shade and lock-screen visibility controls for the game. Google Play restricts `USE_EXACT_ALARM` and the automatic grant of full-screen intent to eligible use cases. [Google Play permissions policy](https://support.google.com/googleplay/android-developer/answer/16558241)
- Reconcile on launch and resume, when a care action changes due events, after reboot and first unlock, on wall-time or zone change, on app update, and when exact alarm access is granted. Recheck notification and channel states after Settings. Reboot recovery can wait for normal credential-protected storage to become available.
- Android can stop background work after a user force-stop. Reconcile on the next launch and leave missed opportunities in the log. [Android stopped-state behavior](https://developer.android.com/about/versions/15/behavior-changes-all#package-stopped-state)

### 6.4 Delivery records

Track requested time, receiver time, post attempt, channel configuration when known, suppression reason, notification open and actual care start. Suppressed or late alerts can explain a gap in the timeline. The ledger allows no inference about what the player heard or when they slept.

## 7. Inputs and accessibility

Start with touch and accessible equivalents. Touching the drawn baby is hit-tested against the same geometry the canvas draws (face, hands, tummy, diaper, feet), and the magnifier button is the accessible equivalent of a touch check. Provide labeled steps, large targets, screen-reader descriptions for cues, text and optional haptics for audio, reduced motion and flexible prompting. Later prototypes can add movement or voice while the app is in use. Alternative controls share the same simulated result. Motion is a game control. A phone reading carries no information about real infant handling safety.

## 8. Content pipeline

Validate [age-profiles.json](data/age-profiles.json) on load. Require unique sorted anchors, stage coverage (including cutovers), contiguous growth bands, all chapter starts, ordered ranges, consistent units and a terminal anchor. Keep one versioned copy when bundling Android assets. Validate references to scenario, action and lesson IDs before release. Attach official source URLs and dated review records to released education. See [09](09-education-curriculum.md) §5.

## 9. Art and audio

Use a small set of readable 2D poses and licensed sounds for newborn cues. The baby, nursery, care overlays, reactions and every action icon are drawn in Compose `Canvas` code, with no bitmap assets, so they scale, dim at night and follow reduced motion. Provide simultaneous text descriptions. Distress intensity can affect in-app presentation and feedback. A particular sound never provides a diagnosis. Additional stages can add animation and voice sets after the newborn loop works.

## 10. Privacy

Play offline without an account. Store run data locally. Confirm the intended handling of operating-system backups before release. Use generic lock-screen copy by default and remove a run and its scheduled work when the player deletes it. Future sensor work keeps any audio processing on-device and ephemeral. Share a report only on an explicit export action.

## 11. Permissions summary

The baseline build uses notification and reboot permissions. Opt-in exact timing depends on special access and device tests.

## 12. Testing strategy

| Test | Cases |
|---|---|
| Simulation | Profile table fixtures, source data validation, age cutovers, overlap, partial actions, due times, deterministic save and replay |
| Time and lifecycle | On the same boot, wall-clock forward/backward, time-zone/DST fold and gap, reboot, long absence, pause/resume and the trial end |
| Alert reconciliation | Covered or resolved need, delayed or duplicate receiver, permission denied or revoked, muted channel, Doze, shutdown, force-stop and tap on stale notification |
| Effort and learning | Complete the same feed, diaper, soothe and settle flows with TalkBack and large text. Compare responses and partial-time accounting |
| Physical-device trial | At least one Pixel and one other manufacturer. Exercise a locked phone with battery saver, Do Not Disturb, permission changes and a reboot. Report latency distribution and omissions |

## 13. Debugging

A debug-only virtual clock, fixture seed selector and event ledger allow repeatable episodes without waiting for a weekend. Simulate background and permission changes in test doubles. Use actual device tests for Android delivery.

## 14. Performance budget

Avoid continuous background polling and persistent audio. Project from the saved state at actionable changes, and schedule a bounded next wake-up. Measure 48-hour cache and log growth, cold-start catch-up time and overnight battery use on the test devices.

## 15. Build conventions

Use the Gradle wrapper and Android lint. Run pure JVM simulation checks on each change to scheduling or bundled profiles. Version authored data and keep a migration plan as soon as released saves exist.

## 16. Launch verification

Check the Play target SDK floor, current alarm and notification policy, notification channel behavior and selected Android tooling on supported devices before release. Gate the audible path on observed device behavior and indicate which fallback the player is using.

## 17. Implementation map (v0.2.0)

| Doc concept | Code |
|---|---|
| `advance` / `apply` / `project` (§3.1) | `SimEngine.advance`, `SimEngine.apply`, `planNextWake` (projection on a copy) |
| Due-event ordering (§3.1) | `DueKind`: trial end, coverage, hands-off, session step, helper, need resolve, phase, … |
| Clocks (§3.2) | Logical time freezes during a pause (`wall = logical + delta`). The app's `TrustedClock` uses elapsed realtime within a boot |
| Need processes (§3.5) | `Processes.kt` (milk demand integration, diapers, sleep, gas, closeness, evenings), `Needs.kt` (phases, comfort, distress) |
| Coverage (§3.7) | `Coverage.kt`, `Helper.kt` |
| Delivery records (§6.4) | `AlertState.ledger`, `Alerts.kt` (`reconcileAlerts`) |
| Balance runner (§12) | `sim/src/test/.../BalanceTest.kt` with the `CareBot` caregiver bot |
| Content pipeline (§8) | `tools/content/build-lessons.mjs` (docs/09 → `lessons.json`); the app bundles `docs/data/age-profiles.json` and validates it on load |
| Living baby and nursery (§9, docs/10 §11) | `ui/BabyCanvas.kt`: `BabyVisual` (pose, place, `BabyActivity`, room, hour), `BabyFxState` (touch and reaction animations), `hitTestBaby` |
| Touch checks (§7, docs/10 §5.1) | `ui/BabyStage.kt`: `BabyStage`, `observations` (uses the sim's public `cueFor`), `CheckPanel`, `HintMode` |
| Picture controls (docs/10 §5.2) | `ui/CareIcons.kt` (`CareIcon`, `stepIcon`, `choiceIcon`, `choiceCaption`); `ActionTile` and `ChoiceTile` in `ui/BabyStage.kt` |
| Reactions (docs/10 §1.1) | `reactionFor` in `ui/HomeScreen.kt` maps new care-log entries to emotes |
| Moments together (docs/10 §1.1) | `ui/Moments.kt`, derived from the care log and shown in the Report tab |
| Quick start (docs/10 §3) | `ui/OnboardingScreen.kt` |
