# 03 — Age-Based Care and Attention

> Status: Draft v0.2 · 2026-11-17
> The table summarizes [age-profiles.json](data/age-profiles.json). See [01](01-age-and-time-model.md) for ages and transitions and [05](05-realism-levels-and-notifications.md) for alert settings.

## 1. How to read the numbers

These are **gameplay balance bands** for a modeled child over 24 real hours. They describe variation across sampled days with responsive care. They include authored assumptions about naps, crying and prompts. The newborn Weekend Trial represents fictional full-term infants. Health guidance for real children lives in the [curriculum](09-education-curriculum.md).

| Anchor | Age | Milk feeds per 24 h (breast/chest / formula) | Solid meals + snacks per day | Diaper changes per day | Total sleep (h) | Daytime naps | Realistic alert groups per day | Overnight care groups |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| d0 | Birth | 8–12 / 8–12 | 0 + 0 | 6–10 | 14–17 | 4–7 | 18–25 | 3–4 |
| d7 | 7 days | 8–12 / 7–10 | 0 + 0 | 8–12 | 14–17 | 4–7 | 18–25 | 2–4 |
| d14 | 14 days | 8–12 / 7–9 | 0 + 0 | 8–12 | 14–17 | 4–6 | 18–24 | 2–3 |
| m1 | 1 month | 8–10 / 6–8 | 0 + 0 | 8–10 | 14–17 | 4–6 | 17–23 | 2–3 |
| m2 | 2 months | 7–9 / 6–8 | 0 + 0 | 7–9 | 14–17 | 4–5 | 16–22 | 1–3 |
| m3 | 3 months | 6–8 / 5–7 | 0 + 0 | 6–8 | 14–17 | 4–5 | 15–21 | 1–2 |
| m4 | 4 months | 6–8 / 5–6 | 0 + 0 | 6–8 | 12–16 | 3–4 | 14–20 | 1–3 |
| m6 | 6 months | 5–6 / 4–5 | 1–2 + 0 | 5–7 | 12–16 | 2–3 | 13–18 | 0–2 |
| m7 | 7 months | 4–6 / 4–5 | 1–2 + 0 | 5–7 | 12–16 | 2–3 | 13–18 | 0–2 |
| m9 | 9 months | 4–5 / 3–4 | 2–3 + 0–1 | 5–7 | 12–16 | 2 | 14–19 | 0–1 |
| m10 | 10 months | 3–5 / 3–4 | 2–3 + 0–1 | 5–7 | 12–16 | 2 | 14–19 | 0–1 |
| m12 | 12 months | — | 3 + 2–3 | 5–6 | 11–14 | 1–2 | 14–19 | 0–1 |
| m18 | 18 months | — | 3 + 2–3 | 4–6 | 11–14 | 1 | 14–18 | 0–1 |
| y2 | 2 years | — | 3 + 2–3 | 4–6 | 11–14 | 1 | 15–20 | 0–1 |
| y3 | 3 years | — | 3 + 2 | — | 10–13 | 0–1 | 12–16 | 0–1 |
| y4 | 4 years | — | 3 + 2 | — | 10–13 | 0 | 10–15 | 0–1 |
| y5 | 5 years | — | 3 + 1–2 | — | 10–13 | 0 | 9–13 | 0 |
| y6 | 6 years | — | 3 + 1–2 | — | 9–12 | 0 | 8–12 | 0 |
| y8 | 8 years | — | 3 + 1–2 | — | 9–12 | 0 | 7–11 | 0 |
| y9 | 9 years | — | 3 + 1–2 | — | 9–12 | 0 | 6–10 | 0 |
| y12 | 12 years | — | 3 + 1–3 | — | 9–12 | 0 | 5–8 | 0 |
| y15 | 15 years | — | 3 + 1–3 | — | 8–10 | 0 | 4–7 | 0 |
| y18 | 18 years | — | 3 + 1–3 | — | 8–10 | 0 | 4–7 | 0 |

Read **—** as a routine managed by another process or outside this profile. A zero is no default occurrence. For example, older-child comfort can still happen at night through a scenario. The profile interpolates numeric targets between ages. Capabilities and scheduled routines follow their own rules.

## 2. Milk feeding

- Infant milk demand derives from one base rate per feeding route. The engine samples that rate by age and temperament, then distributes feeds through the child's active and quiet periods. Count start to start. The model increases spacing at night as the child develops. Cluster feeds and partial sessions create variation. See [11](11-technical-architecture.md) §3.5 for the process.
- A trial at 21 days can have a demanding night and multiple daytime feeds, including cluster feeding. A 3-month-old profile can generate **eight breast/chestfeeds**, or **five to seven formula feeds**, across 24 hours. Night feeds are part of those totals. [CDC guidance on breast/chestfeeding frequency](https://www.cdc.gov/infant-toddler-nutrition/breastfeeding/how-much-and-how-often.html), [CDC guidance on formula feeding frequency](https://www.cdc.gov/infant-toddler-nutrition/formula-feeding/how-much-and-how-often.html)
- Hinting uses rooting, hand-to-mouth and feeding history. The player can offer a feed when there are cues. Completing a session records the child's acceptance and fullness, with no reward for finishing a set volume.

## 3. Solids, meals and drinks

Starting around 6 months and once readiness is observed, introduce practice servings and gradually add meal opportunities. Milk remains part of infant play through the first year. From 12 months, the routine shifts to meals and snacks, with continued breast/chestfeeding available as a family choice. [CDC guidance on introducing solids](https://www.cdc.gov/infant-toddler-nutrition/foods-and-drinks/when-to-introduce-solid-foods.html)

An **8-year-old** has **three meals** and **one or two snack opportunities** in this model. A typical school day has one after-school snack. Lunch may be covered by school. Count a meal even when someone else handles it, and omit an alert when the player has no task.

## 4. Diapers and toileting

Void and stool events update one diaper state. A change clears that state. Sample and calibrate the rate of **actual changes**, since one change can cover several events. Dirty diapers can create an immediate request. Routine wet checks can share a feeding or wake-up. Change prompts evolve into support for toileting when a later-stage child shows readiness. Avoid comparing wet-diaper counts in gameplay with medical thresholds.

## 5. Sleep

Sleep consists of multiple rest episodes, including fragmented sleep in the newborn trial and more consolidated sleep in older stages. Awake duration, recent sleep, settling, noise, hunger and the local schedule shape naps and wake-ups. Sleep bands include naps. Wake-window bands and bedtimes are authored controls. The sleep lessons use reviewed age ranges and explain variation. [AASM pediatric sleep consensus](https://aasm.org/wp-content/uploads/2017/04/Pediatricsleepdurationconsensus.pdf)

## 6. Crying, fussing and behavior

Crying pressure can increase around early infancy. A routine need, overstimulation, a scenario or a combination can contribute. An infant can settle partway and still need care, or continue to fuss after comfort and care. Toddler behavior scenarios include transitions, tiredness and frustration. Older children may ask again or start a conversation. See [04](04-crying-and-soothing.md).

## 7. Health calendar

Checkups, vaccinations, screenings and dental visits can become story moments as a child ages. Use dated, reviewable regional content for schedules. Store triggers and scene choices as gameplay data. The curriculum owns explanations and links to care guidance.

## 8. Episodes, including illustrative variations

| Situation | Simulation effect | Scheduling |
|---|---|---|
| Cluster-feeding evening | Several short feeding intervals followed by a return to baseline | Seeded newborn scenario or occasional time-bounded modifier |
| Gas, spit-up or a blowout | A check, cleanup, a shorter feed or an extra change | Tied to the relevant care event with a cooldown |
| Sleep disruption | Shorter naps, settling effort or extra waking | Optional late-infant chapter scenario |
| Teething, colds, illness | Comfort and schedule changes, with reviewed help-seeking choices | Later scenarios with a fixed duration and cooldown |
| Separation, potty setbacks or nightmares | Reassurance, coordination or extra care | Age-appropriate situations with limits on repeats |

Use scenario probabilities for pacing. Review claims about population frequency or medical duration in the curriculum before using them in teaching.

## 9. Escalation

| Modeled stage | Cue → fuss | Fuss → cry | Cry → harder cry | Unanswered crying → help |
|---|---:|---:|---:|---:|
| Early newborn | 2–8 min | 2–6 min | 3–8 min | 20–40 min |
| Young infant | 3–10 min | 3–8 min | 5–10 min | 20–40 min |
| Later infant | 5–15 min | 5–10 min | 5–15 min | 20–45 min |
| Toddler | 5–20 min | 5–15 min | 3–10 min | 10–30 min |
| Older child | Time to repeat a routine request starts around 10–30 min in the game | — | — | — |

These timers drive gameplay and helper coverage. Respond to real infant cues based on the situation and seek help if something seems wrong. In the simulation, red flags switch directly to a reviewed help-seeking choice without a countdown.

## 10. Hygiene

Schedule baths, clothing changes, tooth care once teeth appear, and nail care as tasks or practice scenes. Weekly rates in the profile control variety. Cloth and disposable diapers can share the same sequence with different cleanup steps. See [02](02-needs-cues-and-actions.md) for actions.

## 11. Play, activity and supervision

Offer age-appropriate things to do when the child is awake. Start with talking and awake, supervised tummy or floor time, then add turn-taking, games, outdoor play, reading and school-age activities. Treat these as opportunities and time commitments. For age-related safety and lessons, see [06](06-growth-and-development.md) and [09](09-education-curriculum.md).

## 12. Attention calls and alert groups

- A **need episode** starts when a separate actionable need opens. Its later states share its ID. A hungry infant who cries has one episode. An infant who is hungry and needs a change has two.
- A **care group** is a set of needs addressed during one interruption, such as feeding, changing, and settling during one wake. The table's overnight band refers to care groups with the standard 23:00–07:00 reference window.
- An **alert group** is an eligible interruption after the planner applies coverage, the foreground state and bundling. The table assumes background eligibility in Realistic mode with regular care. Revisions to the same notification ID count once. Android may delay, silence or suppress posting or presentation. See [05](05-realism-levels-and-notifications.md) for caps and [11](11-technical-architecture.md) for delivery records.
- Compare opening need episodes, completed tasks, grouped alerts and observed player actions independently in the balance harness. Optional hygiene, coverage, proactive care, bedtime settings and scenario variation can change any one of those numbers.

### 12.1 What each realism level sends

Gentle and Standard cover most or all sleeping hours. Realistic can ask for the same overnight care with an ordinary notification. Extreme Real adds a bounded, opt-in audible route for eligible wake groups. The number of child wakings can exceed the audible limit. The report tracks the resulting helper and player workload.

### 12.2 Continuous supervision

Mobile children need supervision represented by a persistent state and occasional age-appropriate decisions, such as moving an activity to a safe space or assigning another caregiver. Log coverage and focused care time. Use a prompt when the game needs a decision.

## 13. Care workload in reports

Report time spent in completed or partial actions, player and helper task counts, and overnight session starts. An optional **reference workload estimate** uses authored action durations and merges overlapping tasks. Display its assumptions. Count time between in-game care sessions as a gap between sessions. The app has no measurement of the player's sleep.

## 14. Source notes

Clinical materials use direct sources with region and review records. The playtest measures here are implementation targets. See the [curriculum review rules](09-education-curriculum.md#5-content-standards--review-workflow) and the [roadmap gates](12-roadmap-and-open-questions.md#2-phase-details).
