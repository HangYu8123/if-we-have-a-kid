package app.childrensimulator.sim.engine

/**
 * Just-in-time coaching cards (docs/09 §1, first-trial lesson sequence). Each card links a lesson ID from
 * docs/09; the full draft lesson text and its sources ship in the app's lesson content.
 */
data class CoachText(val lessonId: String, val title: String, val text: String)

object Coaching {
    val cards: Map<String, CoachText> = mapOf(
        "first_hunger_cue" to CoachText(
            "L-FEED-01", "Early hunger cues",
            "Rooting, lip smacking and hands to the mouth can be early hunger cues. Check when the last feed started and offer a feed. Crying is a late cue. During the feed, turning away or closing the mouth can mean full.",
        ),
        "route_formula" to CoachText(
            "L-FEED-03", "Preparing formula",
            "Wash your hands and follow the product label exactly: water first, then level scoops. Never microwave a bottle. Discard formula left in the bottle after a feed.",
        ),
        "route_bottle" to CoachText(
            "L-FEED-04", "Responsive bottle feeding",
            "Hold the baby close and semi-upright and keep the bottle in your hand. Let them pause, and end the feed when they show fullness. Never prop the bottle.",
        ),
        "route_breast" to CoachText(
            "L-FEED-02", "Breast/chestfeeding basics",
            "Get comfortable, bring the baby close and wait for a wide-open mouth. Listen for swallowing. Persistent pain or trouble latching is a reason to ask for skilled feeding help.",
        ),
        "first_diaper" to CoachText(
            "L-HYG-01", "Diapering and cord care",
            "Gather supplies first and keep a hand on the baby on any raised surface. Clean front to back, fold the diaper below the cord stump, and wash your hands.",
        ),
        "first_settle" to CoachText(
            "L-SLEEP-01", "Safe infant sleep",
            "Every sleep: on the back, alone, in a clear infant sleep space with a firm, flat mattress and a fitted sheet. No blankets, pillows, bumpers or toys.",
        ),
        "crying_continues" to CoachText(
            "L-CRY-01", "Why babies cry",
            "Check plausible causes: hunger, the diaper, tiredness, gas or wanting closeness. Try one response and watch what changes. Two explanations can both fit.",
        ),
        "safe_break" to CoachText(
            "L-CRY-04", "A safe break",
            "Never shake a baby. If you feel overwhelmed, put the baby on their back in a clear crib, step away for a few minutes, ask for help and check back. The break controls are always on the screen.",
        ),
        "persistent_fussing" to CoachText(
            "L-CRY-03", "Persistent crying",
            "Some crying continues after good care, often in the evening. Keep checking needs, share the work and take safe breaks. Ask a clinician about crying that seems unusual.",
        ),
        "first_gas" to CoachText(
            "L-FEED-05", "Burping",
            "Squirming, grimacing or pulling off the bottle can mean trapped air. Pause and try a burp, then continue if the baby still shows hunger cues.",
        ),
        "first_contact_nap" to CoachText(
            "L-SLEEP-08", "A baby asleep in your arms",
            "Move a sleeping baby to a safe sleep space on their back. If you might doze off, put the baby down first or hand off.",
        ),
        "first_night" to CoachText(
            "L-SLEEP-02", "Newborn nights",
            "Newborns wake through the night to feed. Keep night care dim, quiet and boring, and plan who covers which wake.",
        ),
        "cluster_feeding" to CoachText(
            "L-FEED-02", "Cluster feeding",
            "Several feeds close together, often in the evening, are common at this age. Follow the cues, and keep water and a snack nearby for yourself.",
        ),
        "tummy_time" to CoachText(
            "L-DEV-02", "Tummy time",
            "Awake, supervised tummy time builds strength. Start with a few minutes a few times a day, and stop when the baby needs a break.",
        ),
        "handoff" to CoachText(
            "L-PAR-04", "Asking for help",
            "Handing off is part of care. Specific requests work best, for example: \"Can you take the 3 AM feed?\"",
        ),
        "trial_end" to CoachText(
            "L-PAR-02", "Rest and support",
            "Look at when care happened and how much the helper covered. Who would take another night, and what would you change?",
        ),
        "overstim" to CoachText(
            "L-CRY-02", "Too much stimulation",
            "Turning away, hiccups and jerky movements can mean \"that's enough\". Stop playing, move somewhere quiet and dim, and hold the baby calmly.",
        ),
        "overtired" to CoachText(
            "L-SLEEP-03", "Tired cues",
            "Staring off, yawning and red eyelids come before overtired crying. At three weeks a baby often tires after less than an hour awake.",
        ),
        "transfer_woke" to CoachText(
            "L-SLEEP-01", "Transfers",
            "Babies often wake in the first minutes of sleep. Wait for limp arms and slow breathing, then lay them on their back in the bassinet.",
        ),
        "late_feed" to CoachText(
            "L-FEED-01", "Calm first, then feed",
            "A baby who is crying hard often can't latch or drink well. Hold and calm them for a minute, then offer the feed. Earlier cues make feeding easier.",
        ),
    )
}
