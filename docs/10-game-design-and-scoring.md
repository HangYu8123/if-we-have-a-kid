# 10 — Game Design, Feedback & Progression

> Status: Draft v0.3 · 2026-11-18 (v0.2.0 playtest feedback)
> See [05 Realism](05-realism-levels-and-notifications.md), [07 Interactions](07-interactions-and-scenarios.md) and [09 Education](09-education-curriculum.md).

## 1. Player experience

Make the child and their cues easy to notice. The satisfaction comes from figuring out what to try, seeing a response and learning the rhythm over time. A rough wake can end with a safe handoff and a useful explanation.

### 1.1 What we borrow from pet-raising games

The v0.2.0 playtest asked for the warmth of pet-raising games such as Travel Frog (旅行的青蛙), QQ Pet (QQ宠物) and Tamagotchi-style games. The app borrows their *feel*, not their scoring:

| Pet-game idea | How it appears here |
|---|---|
| A cozy home scene that follows the real clock (Travel Frog) | A nursery with a window whose sky follows the local time of day: sun and drifting clouds, dusk, moon and stars. A mobile turns over the bassinet and a night-light glows in the sleep window |
| Touch the pet and it reacts (QQ Pet) | Touching the baby checks a body part and triggers a real newborn reflex: rooting toward a stroked cheek, gripping a finger, kicking back |
| Emote bubbles | Short reactions answer care: hearts after a feed or being picked up, a "burp!" bubble, sparkles after a change, notes when shushed, a "!" after an unsafe choice |
| Picture buttons instead of menus | Every care action, step and choice is a drawn, animated picture with a two- or three-word caption |
| Postcards and albums (Travel Frog) | **Moments together**: polaroid snapshots collected from real events in the care log ("First feed together", "A good burp", "Asking for help"). Locked ones show "Still ahead" |

What we deliberately leave out: always-on need meters in the higher realism levels, streaks, daily login rewards, scores and anything that makes the baby's needs pause for the player. Moments describe the run and never rate it (§6.4).

## 2. Modes

| Mode | Play |
|---|---|
| **Weekend Trial** | One three-week-old, 48 active real hours, Standard realism by default, and a Care Report at the end |
| **Practice Room** | Replay core tasks and short scenarios. Practice uses a local clock and sends no background reminders |
| **Life Journey** | Later, run monthly and yearly chapters with a chosen pace, age transitions and Memory Book moments |
| **Challenges** | Later, focus on a repeatable event such as a cluster-feeding evening, a preschool drop-off or twins' first night |

## 3. Onboarding

Since v0.2.0 the start is one screen (playtest: "setup too complex, too wordy").

1. **Meet the baby.** A ready-made three-week-old (default name Mia, formula feeding, helper Sam, sleep window 23:00–07:00) is awake in the player's arms in the nursery. The player can tap a cheek or a hand and see a reflex straight away.
2. **One question: how real should it feel?** Four cards with an effort meter (one to four bars) and one line each. Standard is preselected and marked recommended.
3. **Optional details.** "Change name, look, feeding or nights" opens the name, skin tone, feeding route (picture tiles), helper's name and sleep window. Everything can also be changed later in Settings.
4. **Start.** The 48-hour clock starts only when the player taps Start. On Android 13+ the notification permission is requested at this moment.

The Extreme Real audible opt-in moved to the home screen's duty strip, where it applies per night. The channel tests stay in Settings. The before-trial knowledge check moved to the Learn tab; the Care Report shows "not taken" if it was skipped.

## 4. Care loop

```text
Observe cue → Check context → Choose care → Take steps and spend time → See response
     ↑                                                       ↓
Next need becomes eligible ← Log and optional debrief ← Resolve or hand off
```

A care session can be incomplete. Keep completed valid steps. For example, a paused feed can resume or be handed off, with any accepted feeding already in the log. Show the child and any other ongoing needs while the player is working.

## 5. Screens

| Screen | Main jobs |
|---|---|
| **Home** | The baby in the nursery (touch to check), a picture dock of care actions, status of current sessions, and reachable pause or handoff |
| **Care session** | An animated picture of the current step with a progress ring, picture choices, the child's animated response, and how to resume or hand off |
| **Log** | Feeds, changes, settling, breaks, alert statuses, choices and helper notes |
| **Learn** | Short cards, practice and knowledge checks |
| **Settings and report** | Realism, sleep and alert settings, accessibility, privacy and Care Report |

### 5.1 Checking the baby

The baby's behavior is always visible in the animation. Cue text, context and hints appear **only when the player checks** (playtest: "hints can be found only when checking the baby"):

| Check | What the player notices |
|---|---|
| Touch the **face** | Rooting toward the finger when hungry; hunger, tiredness, overstimulation and closeness cues; tears; active or quiet sleep |
| Touch a **hand** | The grasp reflex, sucking on fists, clenched fists when crying |
| Touch the **tummy** | Gas cues (squirming, legs drawn up) or a soft, relaxed belly |
| Touch the **diaper** | A diaper check (the same as the "Check diaper" tile): dry, wet, dirty or a blowout |
| Touch the **feet** | A kick back, and a reminder that cool feet are normal: check warmth at the chest or neck |
| The **magnifier** | A closer look at everything: the main cue and any other cues |

Below the observations, the check card shows what the realism preset allows ([05](05-realism-levels-and-notifications.md) §2): Gentle, Standard and the Practice Room show meters and hints; Realistic shows a "Show hints" button; Extreme Real shows none. Every check ends with the care-log line (last feed, last change, time awake or asleep). A check fades after a minute so the player looks again. Screen readers still get the current cue in the stage description, so cue accessibility does not depend on touch.

### 5.2 Picture controls

Care actions are round picture tiles in soft category colors (feeding, diaper, comfort, sleep, play, help) with short captions. Comfort techniques glow and animate while they are on. Step choices are large picture cards; the full sentence is the accessibility label, so the screen reads two or three words. Choice pictures never hint at which option is safe. In-app night care uses readable dim colors and large controls.

## 6. Feedback and reports

### 6.1 Per-episode feedback

Record whether the player checked plausible causes, addressed the active need and followed the care steps. Credit safe soothing, sharing work and seeking help. Use the child's response and a short explanation as feedback. A reasonable attempt can be helpful even when crying continues.

### 6.2 Safety feedback

Block unsafe actions, show why and present a next step. A healthy outcome such as transferring to a safe sleep space stays achievable after a mistake. For practice with health concerns, show the supported next step before the debrief.

### 6.3 Progress

Track completed practice, learned cues and knowledge-check results. If a lesson is replayed, keep the prior answer for reflection and give immediate feedback for the new attempt. Changes in a play session describe the virtual state and the player's observed choices.

### 6.4 The Care Report

- Show **workload** by type and caregiver, number of feed and diaper sessions, and hands-on minutes. Keep player time, overlapping time and helper time clear.
- Show **night interruptions** as attempted notifications, overnight sessions started, handoffs and time in overnight care. Report the longest gap between logged sessions only when there are enough observations. The device does not measure sleep for this feature.
- Show **learning** through examples, improvements on the optional quiz and tips tied to actual actions. Describe an upcoming practice opportunity.
- Add **reflection** on interrupted plans, availability of help and who would handle another night. Offer an export only when the player chooses to share it.

The report describes the run. It assigns no readiness rating or clinical labels.

## 7. Support network and coverage

The trial has one configurable on-duty helper. Their name and relationship are player choices. An automatic shift handles covered needs and logs a summary. On-demand handoff can cover one group or the rest of a night. Real-world breaks and technical delivery gaps carry no score penalty.

Later, add partner schedules, relatives, daycare and paid care where a budget is enabled. Availability, coordination, timing and costs can produce new decisions. Keep immediate pause and safe handoff accessible.

## 8. Household

Later scenarios can track diapers, clean bottles, clothes or food. A stock-out adds an interruption and choices for getting help. Time estimates and household costs are illustrative and can vary by setting.

## 9. Care intervention

An unanswered routine need can reach the game's time limit and move to a helper. Mark the handoff in the log. In an interactive safety scene, guide the player to safe care immediately. If a phone failed to deliver an alert, show the delivery state when known. Offer a replay, coverage or a lower workload setting after a hard sequence.

## 10. Progression and challenges

Use illustrations, new play interactions and Memory Book entries to mark the passage of time. The Weekend Trial already collects twelve **Moments together** in the Report tab, derived from the care log (§1.1). Explain what the child can do now and why it changes the work. Give players credit for asking for help and making a safe choice. Starting at a stage for practice can be available before playing through the earlier journey.

| ID | Scenario | Planned format |
|---|---|---|
| CH-01 | Newborn Weekend | 48-hour trial |
| CH-02 | Solo Parent Weekend | Later alternate coverage schedule |
| CH-03 | Changing infant sleep | Later multi-day scenario |
| CH-04 | Fussy Evenings | Repeatable short practice |
| CH-05 | Daycare Calls | Later sick-day and pickup decisions |
| CH-06 | Twins' First Night | Later two-child practice |
| CH-07 | Toddler Grocery Run | Short later scenario |
| CH-08 | Potty Readiness | Later routine across days |
| CH-09 | Road Trip With a Toddler | Later scheduling and transport |
| CH-10 | School-Morning Rush | Later weekday routine |
| CH-11 | Holiday Visit | Later family and logistics scenario |
| CH-12 | Newborn and Toddler | Later two-child trial |

## 11. Art and audio

Start with readable 2D poses for awake, asleep, hungry, fussy and content. Use calm background art and add age-appropriate animations in later chapters. Provide descriptions for the behavior on screen and a way to play silently.

Since v0.2.0 the baby is never a still picture (playtest: "it should be like a real baby and react"):

- **Always alive:** breathing (slower and deeper asleep), blinking, small arm and leg movements, twitches in active sleep, yawns that open and close.
- **Distress shows in the body:** fussing adds restless limbs; crying adds flailing arms, clenched fists, a trembling body and falling tears (from about one month).
- **Care shows on the baby:** sucking with moving cheeks and bubbles rising in the bottle, a parent's hand patting during a burp, an open diaper and a moving wipe on the changing table, sparkles on a fresh diaper, a parent's face and cooing during talk and play, head lifts during tummy time.
- **Reactions:** short emotes after care (§1.1) and reflexes when touched (§5.1).
- Reduced motion freezes all of this to a still, readable pose.

## 12. Accessibility and inclusion

Support scalable type, TalkBack, reduced motion, text cues, flexible timing windows and touch alternatives for any sensor input. Preserve the same care outcomes and report credit across input methods. Let players choose caregiving roles and family language.

## 13. Later features

After the core trial, consider a widget, wearable reminders or a second phone for coordinated shifts. Align these with device capabilities and the same consent and helper rules. Classroom use requires its own audience and content design.

## 14. Monetization

The working design assumes offline play without ads, paid care skips or loot boxes. Pricing is open.
