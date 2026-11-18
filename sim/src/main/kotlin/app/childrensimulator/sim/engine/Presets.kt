package app.childrensimulator.sim.engine

import app.childrensimulator.sim.MINUTE
import app.childrensimulator.sim.model.RealismPreset

/** How the player's sleep window is covered (docs/05 §2). */
enum class NightPolicy { HELPER, HELPER_EXCEPT_ONE_WAKE, PLAYER_ORDINARY, PLAYER_AUDIBLE_OPT_IN }

/** Starting balance settings for the first trial (docs/05 §2). */
data class PresetRules(
    val preset: RealismPreset,
    /** Hands-on action duration as a fraction of the reference duration. */
    val effortScale: Double,
    /** Bonus response grace added before an unanswered need is handed to the helper. */
    val graceMs: Long,
    val night: NightPolicy,
    /** Background alerts allowed per active 24 h before groups go to the helper (null = no cap). */
    val alertCapPer24h: Int?,
    /** Gentle and Standard only alert for hunger, dirty diapers and sustained distress. */
    val priorityAlertsOnly: Boolean,
    val metersAlways: Boolean,
    val hintsOnRequest: Boolean,
    val presencePromptsDefault: Boolean,
) {
    companion object {
        fun of(preset: RealismPreset): PresetRules = when (preset) {
            RealismPreset.GENTLE -> PresetRules(preset, 0.15, 20 * MINUTE, NightPolicy.HELPER, 8, true, true, true, false)
            RealismPreset.STANDARD -> PresetRules(preset, 0.33, 10 * MINUTE, NightPolicy.HELPER_EXCEPT_ONE_WAKE, null, true, false, true, false)
            RealismPreset.REALISTIC -> PresetRules(preset, 0.60, 0, NightPolicy.PLAYER_ORDINARY, null, false, false, false, false)
            RealismPreset.EXTREME -> PresetRules(preset, 1.0, 0, NightPolicy.PLAYER_AUDIBLE_OPT_IN, null, false, false, false, true)
        }

        /** At most two audible night alert attempts per sleep window (docs/05 §3). */
        const val NIGHT_AUDIBLE_LIMIT = 2

        /** Needs that join an existing alert group within this window are silent updates (docs/05 §5.2). */
        const val BUNDLE_WINDOW_MS = 10 * MINUTE

        /** Alerts older than this are stale and suppressed (docs/05 §3 step 3). */
        const val FRESHNESS_MS = 15 * MINUTE

        /** Hands-on care stops this long after the app leaves the foreground. */
        const val HANDS_OFF_GRACE_MS = 60_000L
    }
}
