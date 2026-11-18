package app.childrensimulator.sim.engine

import app.childrensimulator.sim.profile.ResolvedProfile

/** A recurring local meal or snack slot (docs/01 §8, docs/11 §3.5 "Meals and snacks"). */
data class MealSlot(val id: String, val label: String, val localMinute: Int, val snack: Boolean)

/**
 * Resolves the daily meal routine for older children. Discrete slots step at anchors (docs/01 §5.3); the
 * school-age chapters that use them are a later phase, but the 8-year fixture is checked now (docs/12 Phase 0).
 */
object MealPlanner {
    private val mealTimes = listOf(
        MealSlot("breakfast", "Breakfast", 7 * 60 + 15, false),
        MealSlot("lunch", "Lunch", 12 * 60, false),
        MealSlot("dinner", "Dinner", 18 * 60 + 30, false),
    )
    private val snackTimes = listOf(
        MealSlot("snack_pm", "After-school snack", 15 * 60 + 45, true),
        MealSlot("snack_am", "Morning snack", 10 * 60, true),
        MealSlot("snack_eve", "Evening snack", 19 * 60 + 45, true),
    )

    fun slots(profile: ResolvedProfile): List<MealSlot> {
        val meals = profile.solidMealsPerDay?.min?.toInt() ?: 0
        if (meals < 3 || profile.milkFeedsPerDay != null) return emptyList()
        val snacks = (profile.snacksPerDay?.min?.toInt() ?: 0).coerceIn(0, snackTimes.size)
        return (mealTimes.take(meals) + snackTimes.take(snacks)).sortedBy { it.localMinute }
    }
}
