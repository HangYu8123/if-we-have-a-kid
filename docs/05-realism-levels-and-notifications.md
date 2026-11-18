# 05 — Realism Levels, Notifications & Night Wake-ups

> Status: Draft v0.2 · 2026-11-17
> For age and growth pace see [01](01-age-and-time-model.md). For Android scheduling see [11](11-technical-architecture.md) §6.

## 1. Two independent dials

**Realism** controls the player's exposure to the child's routine, the time care takes, and how much help and information the game offers. **Growth pace** controls how quickly needs and capabilities change with age.

Changing realism takes effect for newly started sessions and future alerts. A care session already under way keeps its duration. A downgrade, pause or handoff cancels the affected pending alerts and clears their active notifications.

## 2. Realism presets

The age-based routine runs in all four presets. In Gentle, additional background groups after the daily cap go to the helper. Standard prioritizes hunger, dirty diapers and sustained distress. These are starting balance settings for the first trial.

| Setting | Gentle | Standard (default) | Realistic | Extreme Real |
|---|---|---|---|---|
| Workload shown to player | Helper covers selected routine needs | Most daytime needs | All eligible needs | All eligible needs |
| Player sleep window | Helper covers it | Helper covers it, with the option to take one wake | Ordinary notifications governed by Android settings | Opt-in audible wake-ups when supported, with helper coverage after the limit |
| Bonus response grace | +20 min | +10 min | None | None |
| Hands-on action duration | ~15% of reference | ~33% | ~60% | Full reference |
| Long-session prompts | Optional guided steps | Optional guided steps | Optional, adjustable presence prompts | Enabled by default, with adjustable timing |
| In-app information | Clear labels, meters and hints | Meters and hints on request | Cues, care log and optional hints | Cues and care log. Accessibility descriptions remain enabled |
| Routine background alerts | Up to 8 per active 24 h | Prioritize actionable care | Bundle any eligible care | Same daytime rules as Realistic |
| If care goes unanswered | Helper handles it. The outcome appears in the log | Escalation and helper handoff | Escalation and helper handoff | Escalation and helper handoff |

**Custom** can vary hinting, grace, interaction time, coverage and night delivery. The release shares the same limits and consent behavior for any audible night setting. Display a preview based on the selected age and schedule.

## 3. Night wake-ups in Extreme Real

1. Set the player's sleep window and choose **Enable audible wake-ups for this window**. Show the limit, the active channel settings and the best-effort delivery behavior. Let the player start a separate one-shot test notification before the trial.
2. Schedule the next eligible **sustained need** within that window. Ordinary sleep movement stays in-game. One infant waking can lead to a feed, a diaper change and settling in one care group.
3. At the scheduled time, reconcile with the current simulation. If the need is still actionable, within 15 minutes of its planned group opening, and the window and consent still apply, post one notification on the night channel. Android controls sound, volume, vibration, Do Not Disturb and lock-screen visibility.
4. **Care now** opens the current cue and starts the player-directed care flow. **Silence** dismisses the current group and prevents further posts for it. The underlying needs stay in the care log. **Hand off the night** assigns available helper coverage for the rest of the window and cancels remaining alerts. Expose handoff and pause in the app as well.
5. After **two audible alert attempts per sleep window**, assign any newly opened wakes in that window to the helper. The second alerted wake stays available for player care or handoff. A new window requires a fresh opt-in. Store consent and attempts against the window's fixed start and end. Midnight, a restart, or a schedule edit cannot replenish the budget for an open window. The report shows attempts, care sessions and helper activity.

Use a short, recognizable notification sound and a text description such as “Night care · Your virtual baby is awake.” The lock screen can show generic copy. Audio in the foreground and a player's in-app controls are independent from Android notification sounds.

## 4. Permission and fallback states

| Device state | Player experience |
|---|---|
| Notifications allowed and night channel enabled | Night wake-up option uses the configured channel. Offer a one-shot test on the current device |
| Exact scheduling access unavailable | Show that night reminders are best effort and may be late or absent. Offer inexact reminders or overnight helper coverage |
| Notifications or the night channel blocked | Show the limitation and settings entry point. Offer foreground care and overnight helper coverage |
| Android silences the device, or the phone is off or force-stopped | Resume from the care log on the next permitted launch. This delivery gap carries no care score penalty |
| Schedule, coverage or permission changes during a run | Reconcile current alerts and future work. Cancel or downgrade pending attempts according to the new state |

The simulation remains playable with the app open. If available and permitted for this use case, prototype `SCHEDULE_EXACT_ALARM` for the user-enabled night feature only. Baseline play uses inexact scheduling. [Android alarm scheduling](https://developer.android.com/develop/background-work/services/alarms), [Google Play permissions policy](https://support.google.com/googleplay/android-developer/answer/16558241)

## 5. Notification design

### 5.1 Channels

| Channel | Default | Use |
|---|---|---|
| `care` | Default importance | Timely, actionable daytime needs and ordinary overnight notifications |
| `night_wake` | High importance with short sound | Opt-in audible wake-ups and explicit tests |
| `summary` | Low importance | Handoffs, reminders and reports |

On Android 13 and later, request `POST_NOTIFICATIONS` after explaining and previewing the feature. The system allows channel settings to be changed independently. Detect disabled notifications and blocked channels when the user returns from Settings. [Android notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission), [Android notification channels](https://developer.android.com/develop/ui/views/notifications/channels)

### 5.2 Bundling and staleness

- Emit the first routine alert when a need becomes actionable and the player is on duty outside the app. Merge needs for that child into its existing group for the next **10 minutes** and use silent updates. Further distress changes use the same group. In the foreground, update cues directly.
- A need the player saw in the app but left unresolved gets one reminder if it escalates to crying while they're away. If its notification is still showing, the escalation updates that notification silently instead (v0.1.0, found in emulator testing).
- For night care use the waking as the group key. Simultaneous household needs in later multi-child play become one wake-up attempt. Use the 15-minute check for each group. Suppress alerts whose needs ended, were assigned to coverage or paused. Clear the group when its last need is resolved or handed off.
- Grouping and eligibility use the simulation log. Posting is a separate attempt in the delivery log. An attempt says nothing about whether the player heard the sound or saw the banner.
- Notifications open the current state on tap. A stale tap opens the relevant log entry or current care screen. It cannot recreate a completed need.

## 6. Real time and real effort

Care sessions have steps and elapsed-time targets. Feeding includes preparation, cue-based pacing and stopping at fullness. A change includes supply setup, safe positioning, cleanup and handwashing. Settling includes checking the underlying need and preparing a safe sleep space.

Presence prompts apply only to a running care session. Offer enough time for assistive input and use accessible controls for every action. Hands-on time accrues while the care session is advancing in the foreground. When the app backgrounds or the player pauses a session, save its steps and stop its effort timer. The child's daily rhythm continues. Use the separate run pause to freeze both clocks. A player can resume a session or let a helper take over. Motion and voice are optional later controls.

## 7. Safety and consent

- **Pause** is immediate in every level. Cancel pending alerts and freeze the clocks. **Hand off** keeps the simulation running with a helper. Both choices are available without cost or a score deduction in the Weekend Trial.
- Present the audible feature as an optional exposure to interrupted nights. Explicitly state that the sound can affect other people nearby, expires after the chosen sleep window, and follows system sound and notification settings.
- Keep a protected-time setting for times when the player wants silence. A protected interval uses helper coverage. Avoid escalating audio or repeating a sound until a care session finishes.
- Use a dim, low-stimulation in-app night care view with readable controls. Audio and vibration have text alternatives. Let the player move to Standard or another setting immediately.

## 8. What the first trial teaches

In a 48-hour trial at three weeks old, the child has many feed and diaper opportunities and several possible wakes per night. Gentle and Standard shift more of that work to the helper. Realistic shows the full rhythm in the care log. Extreme Real offers a limited opportunity to experience an interruption. The [Care Report](10-game-design-and-scoring.md#64-the-care-report) shows time in care, the longest gap between in-game sessions, completed handoffs and the tasks that made care take longer.
