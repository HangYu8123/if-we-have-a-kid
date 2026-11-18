# 01 — Age & Time Model

> Status: Draft v0.2 · 2026-11-17
> The numeric age, stage and growth boundaries live in [age-profiles.json](data/age-profiles.json). For care cadence see [03](03-realistic-frequency-tables.md). For scheduling and persistence see [11](11-technical-architecture.md).

## 1. Two clocks

The **rhythm clock** advances one second for each real second of an active run. Feeding intervals, naps, care sessions, response windows and the 48-hour trial use elapsed time. The child's daily schedule supplies local times for recurring events such as bedtime and school. The player's sleep window controls how alerts are routed.

The **development clock** moves the child through ages, chapters, capabilities and age-dependent care profiles. Speed up this clock to see more stages while retaining the same elapsed-time rules for a feed, diaper or nap. A fixed-age trial still has a full day and night.

## 2. Age, display and chapters

Age is a fractional number of simulated days. A model year is 365.25 days. Month boundary `M(n) = floor(n × 365.25 / 12 + 0.5)`, and year boundary `Y(n) = M(12n)`. This gives one ordered set of boundaries across the profile, UI and engine.

- Show days through day 13, weeks through day 29, and months from `M(1)` up to age 2. Show years from age 2, with months available in the child's profile.
- Chapters open at birth, at each month from 1 to 23, and at each year from 2 to 17. Early weekly check-ins are optional report cards. Crossing age 18 ends the planned journey.
- On stage start and after a deliberate skip, summarize changes in care, new abilities and useful lessons. A chapter can begin while a care session is active. That session keeps its starting rules and duration.

## 3. Life stages

Stage ranges include the lower boundary. The next stage takes over at its start day.

| Stage | Starts at | Care and gameplay |
|---|---|---|
| S0 Newborn | Birth · day 0 | Frequent feeds and changes, safe sleep, soothing, day and night shifts |
| S1 Early infant | 1 month · day 30 | Social smiles and cue-reading, stretches of sleep, crying that can resist soothing |
| S2 Infant | 4 months · day 122 | Rolling signs, changing naps, supervised floor play, readiness for solids around 6 months |
| S3 Mobile infant | 7 months · day 213 | Sitting and movement, safety checks, separation, finger-food practice |
| S4 Late infant | 10 months · day 304 | Cruising and grasping, texture changes, supervised cup practice |
| S5 Young toddler | 12 months · day 365 | Family meals, walking, bedtime routines and more active supervision |
| S6 Toddler | 18 months · day 548 | Language, choices, climbing, big feelings and toilet readiness |
| S7 Two-year-old | 2 years · day 731 | Potty practice when ready, transition support, play and safety |
| S8 Preschooler | 3 years · day 1,096 | Preschool handoffs, social play, bedtime fears and conversations |
| S9 Early school age | 5 years · day 1,826 | School schedules, meals and snacks, reading, transport and friends |
| S10 Middle childhood | 8 years · day 2,922 | Homework, activities, peer issues, autonomy and online boundaries |
| S11 Teen | 12 years · day 4,383 | Dialogue, privacy, independence and coordination in a later phase |

Stage cutovers are game chapter boundaries. Rolling, teeth, mobility, sleep changes and school start each use their own capability or schedule rules. For example, swaddling becomes unavailable when rolling attempts appear, including if they occur before S2. School starts with enrollment on the selected school schedule.

## 4. Growth pace

| Standard band | Real days in band | Real days elapsed at end |
|---|---:|---:|
| Birth → 1 month | 7 | 7 |
| 1 → 4 months | 9 | 16 |
| 4 months → 1 year | 16 | 32 |
| 1 → 2 years | 12 | 44 |
| 2 → 5 years | 18 | 62 |
| 5 → 12 years | 21 | 83 |
| 12 → 18 years | 12 | 95 |

For each band, advance at `simulated age days in the band / real days in the band`. If a step crosses a boundary, spend only the elapsed time needed to reach it and apply the next rate to the remainder. Save an age checkpoint whenever pace changes and process each crossed chapter once.

| Preset | Rule |
|---|---|
| **Fast** | Each Standard band takes half as long |
| **Standard** | The bands above |
| **Slow** | Each Standard band takes twice as long |
| **Real-time** | One simulated age day per active real day |
| **Hold age** | The child stays at their current age and daily care continues |
| **Weekend Trial** | A 48-hour run at 21 simulated days old in the MVP. Hold age for the whole run |

Later, a player can start at a supported age or skip forward at a checkpoint. Seed appropriate capabilities and routines for the destination and show a recap. Skipped ages contribute zero care minutes, completed lessons or responses.

## 5. Resolving a profile

1. Find the current stage from `calendar.stageStarts`. Find the surrounding numeric anchors independently. Every stage start has an anchor.
2. Interpolate both endpoints of a supported numeric range by fractional `ageDays`. Use this for infant feeding rates, quiet-phase interval ratios, sleep and diaper balance bands, and applicable distress bands. Sample integer counts after interpolation. These values support gameplay calibration.
3. A field with a missing endpoint holds the lower anchor's value, including `null`, until the upper anchor is reached. Discrete routines such as meal and snack slots and local bedtime ranges step at anchors. Capability gates still apply. For example, solid meals require readiness and caregiver introduction around 6 months.
4. A child keeps a seeded temperament. Sample once per interval or episode according to the process in [11](11-technical-architecture.md), and store scheduled events. Interpolation and stage changes affect subsequent scheduling. An existing need, active care session or elapsed deadline keeps its identity and schedule unless care or a specific state change supersedes it.

## 6. Daily schedule and episodes

- A child has a schedule zone and a quiet phase. In early infancy the modeled feeding rate is similar across both phases. Later, a quiet phase can have longer intervals. Moving the player's sleep window routes alerts differently and leaves the child's feeding process on its existing schedule.
- Elapsed-time intervals survive daylight-saving and time-zone changes. Resolve a recurring slot in the schedule zone. Store its slot ID and local date as the occurrence key. On a skipped local time use the first valid time at or after it. On a repeated time use the earlier occurrence. A new zone applies to future slots and retains fulfilled occurrence keys.
- Short care events such as a feed or tantrum run on the rhythm clock. Milestones can occur when the development clock crosses their seeded age. For later episodes such as a temporary sleep disruption, choose a real elapsed duration at onset and store it. A faster growth pace can select a shorter authored duration. Hold age and the fixed-age trial use their authored real-hour scenario durations. Fast journeys use episode cooldowns and a concurrency cap to give players time to experience each event.
- Calendar-based education such as checkups can arrive as an age-appropriate scene. Region-specific schedules come from reviewed educational content.

## 7. App closed, covered or paused

The engine catches up against the same elapsed-time rules when the app reopens or a reminder receiver runs. It processes events in timestamp order through the trial end. Resolved or expired alert opportunities appear in the report rather than generating a backlog of alerts.

A **coverage block** assigns responsibility to a helper and both clocks continue. The report records the care the helper performed. A **pause** is immediately available in every level and freezes both clocks, timed care and trial time. Cancel pending alerts. On resume, rebuild the plan from the state at the pause boundary. A paused interval adds no care or missed responses.

If an episode reaches its game-only unattended limit, route it through the help policy in [04](04-crying-and-soothing.md) and [10](10-game-design-and-scoring.md). Keep it eligible for a current alert only while the need is still active and the opportunity is fresh. Offline handling and paused time have their own log states.

Within a device boot, elapsed realtime is the source for intervals. After reboot, reconstruct elapsed time from the saved wall timestamp and clamp negative elapsed time to zero. Large or uncertain gaps use the bounded reconciliation in [11](11-technical-architecture.md). Phone clock changes alone cannot rewind an existing run.

## 8. Two example days

### 3 months, seven formula feeds in 24 hours

One sampled day has feeds at **00:50, 05:20, 08:25, 11:20, 14:35, 17:35 and 20:45**. The first two fall in the default 23:00–07:00 sleep window. Diaper changes can share a feed session and several naps and bouts of play occupy the day. A breast/chestfeeding profile at this age can yield eight feed requests in a day. See the balance bands in [03](03-realistic-frequency-tables.md).

### 8 years, three school-day meals

Breakfast at **07:15**, lunch at **12:00** and dinner at **18:30**, plus an after-school snack around **15:45**. School can cover lunch, with a decision earlier about packing it. The player's work shifts toward morning prep, pickup, homework, play and an evening conversation. Routine night feeding is absent. An optional nightmare scenario creates a comfort request independently of meals.
