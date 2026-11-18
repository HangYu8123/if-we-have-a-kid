# if-we-have-a-kid

An Android game for adults exploring what it takes to care for a child. Read a virtual child's cues, work through feeding, diaper changes, soothing and settling, and find help when tasks overlap. Care takes time and can interrupt the day. Short lessons explain the choices players make.

**Status** · First playable build (v0.1.0, November 17, 2026): Phase 0 foundations and the Phase 1 **Newborn Weekend Trial** from the [roadmap](docs/12-roadmap-and-open-questions.md). Design docs are draft v0.2. The lesson and health content is a draft that has not been reviewed by a pediatric professional.

## The first playable trial

Care for one three-week-old for **48 real hours**. Start with **Standard** realism and choose what the overnight helper covers. The trial opens with both hunger and a dirty diaper in one wake. Check the cues, carry out timed care steps and review how the care went. **Extreme Real** can offer up to two opt-in audible wake-ups per sleep window through Android notifications.

What's in the app:

- **The baby.** An animated 2D baby with poses and text cues: rooting, hands to mouth, fussing, crying, yawning, gas, active sleep. Needs are hunger, wet and dirty diapers, gas, tiredness, closeness and overstimulation. Each escalates from cue to fuss to cry to hard cry on age-based timers ([03](docs/03-realistic-frequency-tables.md) §9).
- **Hands-on care that takes time.**
  - Guided sessions: prepare a formula bottle, paced bottle feed, breast/chestfeed, burp, diaper change, safe-sleep settle and transfer, talk and play, tummy time.
  - Each step has a real duration scaled by realism: Gentle 15%, Standard 33%, Realistic 60%, Extreme 100%.
  - Effort only accrues while the app is open.
  - Unsafe choices are blocked with an explanation: propping a bottle, microwaving formula, leaving the baby on the changing table, side or tummy sleeping, a cluttered crib.
- **Comfort that partly works.** Hold, rock, shush and dim the room. Comfort eases crying but only the right care resolves a need. Evenings bring cluster feeding or a fussy spell that persists after good care.
- **A helper and safe breaks.** The helper covers your sleep window in Gentle and Standard, any break you take, and any wake you hand off. Unanswered crying eventually goes to the helper. Pause freezes both clocks at no cost.
- **Notifications.**
  - Reminders while you're on duty and the app is closed, bundled per wake, with a 15-minute freshness check.
  - Opt-in audible night alerts in Extreme Real: at most two per night, then the helper takes over.
  - A morning summary of what the helper did. Alarms are re-planned after reboot, an app update or a time change.
- **Learning.**
  - Just-in-time tips and safety coaching, plus "why?" debriefs after crying spells.
  - 98 draft lessons generated from the curriculum, and a Practice Room with six drills.
  - A before-and-after knowledge check, and a **Care Report**: workload by caregiver, nights and alerts, a real-life time estimate, tips met and reflection prompts. No readiness score.

## How the child grows

The child's routine uses real elapsed time. A separate development clock moves through monthly chapters in infancy and toddlerhood, then yearly chapters from age two. Later stages can advance faster without shrinking the time between feeds or meals. The age model and all 23 profile anchors are implemented and tested. Later chapters (growth, multiple children, school age) are planned in the [roadmap](docs/12-roadmap-and-open-questions.md).

## Build and run

Requirements: JDK 17 or newer and the Android SDK (platform 36). Android Studio provides both.

```sh
./gradlew :sim:test            # simulation tests, including the balance harness
./gradlew :app:assembleDebug   # APK at app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows use `gradlew.bat`. The debug build has testing tools under Settings that skip the simulation ahead, so you can try nights without waiting.

To play on a phone (Android 8.0 or newer), download the [debug APK](./app-debug.apk?raw=true), open it on the phone, and allow installs from the app you used to open it if Android asks. With USB debugging enabled, you can also install the downloaded file with `adb install -r app-debug.apk`.

Notification and exact-alarm permissions are requested inside the app when they're needed.

The balance harness writes `sim/build/reports/balance.txt`. It compares bot-played days with the day-21 profile bands. In the current tuning, 71–100% of sampled days fall within each band, depending on the measure and feeding route; milk feeds are 90–94% (see [12](docs/12-roadmap-and-open-questions.md) §8).

To regenerate content after editing the docs:

```sh
node tools/content/build-lessons.mjs     # docs/09 → app/src/main/assets/content/lessons.json
node tools/audio/make-cry-sounds.mjs     # synthesized cry sounds → app/src/main/res/raw
```

The app bundles `docs/data/age-profiles.json` directly, so the profile has one versioned copy.

## Repository layout

| Path | Contents |
|---|---|
| `sim/` | Pure Kotlin/JVM simulation: age model, profile resolver, deterministic event engine, care sessions, helper, alerts, report, practice drills, tests |
| `app/` | Android app (Kotlin, Jetpack Compose): onboarding, care screens, log, learn, report, settings, notifications, alarms, audio |
| `docs/` | Product and design specifications, and the canonical age data |
| `tools/` | Content and audio generators |

## Design reference

| Topic | File |
|---|---|
| Audience and experience goals | [Vision](docs/00-vision-and-goals.md) |
| Age bands and growth pace | [Age and time model](docs/01-age-and-time-model.md) · [Age profiles](docs/data/age-profiles.json) |
| Needs, cues and actions | [Care catalog](docs/02-needs-cues-and-actions.md) |
| Everyday cadence and workload by age | [Care and attention tables](docs/03-realistic-frequency-tables.md) |
| Soothing and overlapping needs | [Crying and soothing](docs/04-crying-and-soothing.md) |
| Realism and night wake-ups | [Realism and notifications](docs/05-realism-levels-and-notifications.md) |
| New abilities and age-related changes | [Growth and development](docs/06-growth-and-development.md) |
| Everyday and exceptional situations | [Interactions and scenarios](docs/07-interactions-and-scenarios.md) |
| Twins, siblings and shared care | [Multiple children](docs/08-multiple-children.md) |
| Lessons, practice and review | [Education curriculum](docs/09-education-curriculum.md) |
| Player flow and reports | [Game design](docs/10-game-design-and-scoring.md) |
| Simulation, Android lifecycle and tests | [Technical architecture](docs/11-technical-architecture.md) |
| First implementation slice and phase gates | [Roadmap](docs/12-roadmap-and-open-questions.md) |
