package io.github.vedant7007.katori.domain

import io.github.vedant7007.katori.domain.model.Nutrient

/**
 * Every sentence the rules engine can emit.
 *
 * WHY A FIXED CATALOGUE. Spec 15.2 sets the language rules for health output, and auditing them
 * output by output is not possible once a model is involved. Here there are eight sentences. They
 * are reviewed once, they are covered by tests, and no model rewrites them.
 *
 * THE RULES EVERY TEMPLATE OBEYS, from spec 15.1 and 15.2:
 *  - never name a disease the user has not declared themselves
 *  - never prescribe, and never say a food will change a clinical value
 *  - state what the data says, offer what someone might consider, hand the decision back
 *  - cite the reference range PRINTED ON THE USER'S OWN REPORT, never one the app carries
 *
 * Changing a sentence here is a health-safety change and goes through review, not a quick edit.
 */
internal object Templates {

    /** Far outside the printed range. Spec 15.1: escalate, do not handle. No suggestion follows. */
    fun escalate(e: Evidence.LabValueOutsideRange): String {
        val bound = e.referenceHigh?.takeIf { e.value > it } ?: e.referenceLow
        return "Your report from ${e.reportDate} shows ${e.testName} at ${num(e.value)} ${e.unit}, " +
            "well outside the ${num(bound)} printed on it. This is worth showing to a doctor."
    }

    fun labOutsideRange(e: Evidence.LabValueOutsideRange): String {
        val above = e.referenceHigh != null && e.value > e.referenceHigh
        val bound = if (above) e.referenceHigh else e.referenceLow
        val word = if (above) "above" else "below"
        return "Your report from ${e.reportDate} shows ${e.testName} at ${num(e.value)} ${e.unit}, " +
            "$word the ${num(bound)} printed on it, so suggestions are ranked differently now."
    }

    fun declaredCondition(e: Evidence.UserDeclaredCondition): String =
        "You told us you are managing ${e.condition.name.lowercase()}, so suggestions are ranked " +
            "with that in mind."

    fun mealComposition(e: Evidence.MealComposition): String =
        "Most of the ${nutrientWord(e.nutrient)} in this meal comes from ${e.dominantItem}, " +
            "about ${(e.shareOfMeal * 100).toInt()} percent of it."

    fun lifeContext(e: Evidence.ProfileContext): String =
        "Suggestions are limited to what is realistic for ${contextWord(e.context)}."

    fun timeline(e: Evidence.TimelinePattern): String =
        "Over the last ${e.daysObserved} days, ${e.description}."

    private fun nutrientWord(n: Nutrient) = when (n) {
        Nutrient.CARBOHYDRATE -> "carbohydrate"
        Nutrient.FAT -> "fat"
        Nutrient.PROTEIN -> "protein"
        Nutrient.FIBRE -> "fibre"
        Nutrient.IRON -> "iron"
        Nutrient.VITAMIN_B12 -> "vitamin B12"
        Nutrient.SODIUM -> "sodium"
        Nutrient.ENERGY -> "energy"
    }

    private fun contextWord(c: LifeContext) = when (c) {
        LifeContext.HOSTEL_STUDENT -> "hostel and canteen food"
        LifeContext.PG_OWN_COOKING -> "cooking for yourself with limited time"
        LifeContext.FIELD_OR_MANUAL_WORKER -> "long physical shifts and eating out"
        LifeContext.DESK_PROFESSIONAL -> "a desk day with a full kitchen"
        LifeContext.HOMEMAKER -> "cooking for the household"
    }

    /** Two significant figures at most. The underlying data does not support more. */
    private fun num(v: Double?): String {
        if (v == null) return "range"
        return if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)
    }
}

/**
 * Maps a lab value or a declared condition to RANKING PREFERENCES.
 *
 * WHAT THIS IS NOT. It is not a diagnosis table and it is not a treatment table. Nothing here
 * says a food changes a clinical value. It only decides the order in which already-permitted
 * candidate foods are offered, and the sentence the user sees is the one in [Templates], which
 * states what the report says and nothing more.
 *
 * Unknown test names and unknown conditions produce NO preferences. Silence is the correct
 * output for something we have no reviewed policy for; inventing one would be exactly the
 * overreach spec 15 forbids.
 */
internal object NutrientPolicy {

    private const val W = 10

    /**
     * DIRECTION MATTERS, and getting it backwards would be harmful.
     *
     * A glucose value ABOVE its printed range and one BELOW it are not mirror images, and
     * ranking swaps as though they were would be worse than doing nothing. So each marker has a
     * direction it responds to, and the other direction produces NO preferences at all.
     *
     * A marker we have no reviewed policy for also produces no preferences. Silence is the right
     * output for something nobody has reviewed; inventing a policy would be the overreach that
     * spec 15 forbids.
     */
    fun forLabValue(testName: String, direction: RangeDirection, rule: RuleId): List<Constraint> {
        val name = testName.lowercase()
        val above = direction == RangeDirection.ABOVE
        val prefers: List<Pair<Nutrient, Constraint.PreferNutrient.Direction>> = when {
            (name.contains("glucose") || name.contains("hba1c") || name.contains("sugar")) && above ->
                listOf(Nutrient.FIBRE to Constraint.PreferNutrient.Direction.HIGHER,
                       Nutrient.CARBOHYDRATE to Constraint.PreferNutrient.Direction.LOWER)

            (name.contains("cholesterol") || name.contains("ldl") || name.contains("triglyceride")) && above ->
                listOf(Nutrient.FIBRE to Constraint.PreferNutrient.Direction.HIGHER,
                       Nutrient.FAT to Constraint.PreferNutrient.Direction.LOWER)

            // Iron markers respond to a value BELOW the range, not above it.
            (name.contains("haemoglobin") || name.contains("hemoglobin") || name.contains("ferritin")) && !above ->
                listOf(Nutrient.IRON to Constraint.PreferNutrient.Direction.HIGHER)

            (name.contains("b12") || name.contains("cobalamin")) && !above ->
                listOf(Nutrient.VITAMIN_B12 to Constraint.PreferNutrient.Direction.HIGHER)

            name.contains("sodium") && above ->
                listOf(Nutrient.SODIUM to Constraint.PreferNutrient.Direction.LOWER)

            else -> emptyList()
        }
        return prefers.map { (n, d) -> Constraint.PreferNutrient(rule, n, d, W) }
    }

    fun forCondition(conditionName: String): List<Constraint> {
        val name = conditionName.lowercase()
        val prefers = when {
            name.contains("diabet") ->
                listOf(Nutrient.FIBRE to Constraint.PreferNutrient.Direction.HIGHER,
                       Nutrient.CARBOHYDRATE to Constraint.PreferNutrient.Direction.LOWER)
            name.contains("anaem") || name.contains("anem") ->
                listOf(Nutrient.IRON to Constraint.PreferNutrient.Direction.HIGHER)
            name.contains("pressure") || name.contains("hypertens") ->
                listOf(Nutrient.SODIUM to Constraint.PreferNutrient.Direction.LOWER)
            name.contains("thyroid") -> emptyList()
            else -> emptyList()
        }
        return prefers.map { (n, d) ->
            Constraint.PreferNutrient(RuleIds.DECLARED_CONDITION, n, d, W)
        }
    }
}
