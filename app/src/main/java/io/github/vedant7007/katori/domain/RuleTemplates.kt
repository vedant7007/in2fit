package io.github.vedant7007.katori.domain

import io.github.vedant7007.katori.domain.model.Nutrient

/**
 * Puts words around a [TriggerStatement], in the user's language.
 *
 * WHAT MOVED, AND WHY. The eight health sentences used to be English string literals inside the
 * pure engine, where no string table could reach them: the sentences that most need a fluent
 * Telugu reviewer were the only ones in the app that could not be localised at all. Now the
 * engine emits a [TriggerTemplate] and the evidence, and this class asks the string table for the
 * words. The engine stays pure; the words stay reviewable per language.
 *
 * THE RULES EVERY SENTENCE STILL OBEYS, from spec 15.1 and 15.2, now enforced on the RENDERED
 * output by the tests rather than on a literal:
 *  - never name a disease the user has not declared themselves
 *  - never prescribe, and never say a food will change a clinical value
 *  - state what the data says, offer what someone might consider, hand the decision back
 *  - cite the reference range PRINTED ON THE USER'S OWN REPORT, never one the app carries
 *
 * NUMBERS AND DATES ARE FORMATTED HERE, AS STRINGS, and handed to the template as positional
 * arguments. The template never carries a number. That keeps every figure in the sentence
 * traceable to a field on the evidence, which is what lets `NumericGuard` permit exactly those
 * figures and no others when the sentence is later handed to the model.
 *
 * Pure: no Android. [Strings] is the seam, so the arguments are unit-tested on the JVM against
 * the English defaults and the Android side is one `getString` call.
 */
class TriggerText(private val strings: Strings) {

    /** The string table. Format strings use positional arguments, `%1$s` and so on. */
    interface Strings {
        fun template(template: TriggerTemplate): String
        fun nutrient(nutrient: Nutrient): String
        fun lifeContext(context: LifeContext): String
    }

    fun render(t: TriggerStatement): String {
        val fmt = strings.template(t.template)
        val args: Array<Any> = when (val e = t.evidence) {
            is Evidence.LabValueOutsideRange -> {
                val above = e.referenceHigh != null && e.value > e.referenceHigh
                val bound = if (above) e.referenceHigh else e.referenceLow
                arrayOf(e.reportDate, e.testName, num(e.value), e.unit, num(bound))
            }
            is Evidence.UserDeclaredCondition -> arrayOf(e.condition.name.lowercase())
            is Evidence.MealComposition ->
                arrayOf(strings.nutrient(e.nutrient), e.dominantItem, (e.shareOfMeal * 100).toInt().toString())
            is Evidence.ProfileContext -> arrayOf(strings.lifeContext(e.context))
            is Evidence.TimelinePattern -> arrayOf(e.daysObserved.toString(), e.description)
        }
        return String.format(fmt, *args)
    }

    /** Two significant figures at most. The underlying data does not support more. */
    private fun num(v: Double?): String {
        if (v == null) return "range"
        return if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)
    }

    companion object {
        /**
         * The English defaults, verbatim what the engine used to emit, kept here so the JVM tests
         * check the rendered sentences against exactly the wording that was reviewed, and so the
         * default string table can be checked against this list rather than the other way round.
         *
         * Positional arguments, in the order [render] supplies them:
         *   lab templates    1 report date, 2 test name, 3 value, 4 unit, 5 printed bound
         *   DECLARED_CONDITION  1 condition name
         *   MEAL_COMPOSITION    1 nutrient word, 2 dominant item, 3 percent
         *   LIFE_CONTEXT        1 context phrase
         *   TIMELINE            1 days observed, 2 description
         */
        val ENGLISH: Strings = object : Strings {
            override fun template(template: TriggerTemplate): String = when (template) {
                TriggerTemplate.ESCALATE_ABOVE_RANGE, TriggerTemplate.ESCALATE_BELOW_RANGE ->
                    "Your report from %1\$s shows %2\$s at %3\$s %4\$s, well outside the %5\$s printed on it. " +
                        "This is worth showing to a doctor."
                TriggerTemplate.LAB_ABOVE_RANGE ->
                    "Your report from %1\$s shows %2\$s at %3\$s %4\$s, above the %5\$s printed on it, " +
                        "so suggestions are ranked differently now."
                TriggerTemplate.LAB_BELOW_RANGE ->
                    "Your report from %1\$s shows %2\$s at %3\$s %4\$s, below the %5\$s printed on it, " +
                        "so suggestions are ranked differently now."
                TriggerTemplate.DECLARED_CONDITION ->
                    "You told us you are managing %1\$s, so suggestions are ranked with that in mind."
                TriggerTemplate.MEAL_COMPOSITION ->
                    "Most of the %1\$s in this meal comes from %2\$s, about %3\$s percent of it."
                TriggerTemplate.LIFE_CONTEXT ->
                    "Suggestions are limited to what is realistic for %1\$s."
                TriggerTemplate.TIMELINE ->
                    "Over the last %1\$s days, %2\$s."
            }

            override fun nutrient(nutrient: Nutrient): String = when (nutrient) {
                Nutrient.CARBOHYDRATE -> "carbohydrate"
                Nutrient.FAT -> "fat"
                Nutrient.PROTEIN -> "protein"
                Nutrient.FIBRE -> "fibre"
                Nutrient.IRON -> "iron"
                Nutrient.VITAMIN_B12 -> "vitamin B12"
                Nutrient.SODIUM -> "sodium"
                Nutrient.ENERGY -> "energy"
            }

            override fun lifeContext(context: LifeContext): String = when (context) {
                LifeContext.HOSTEL_STUDENT -> "hostel and canteen food"
                LifeContext.PG_OWN_COOKING -> "cooking for yourself with limited time"
                LifeContext.FIELD_OR_MANUAL_WORKER -> "long physical shifts and eating out"
                LifeContext.DESK_PROFESSIONAL -> "a desk day with a full kitchen"
                LifeContext.HOMEMAKER -> "cooking for the household"
            }
        }
    }
}

/**
 * Maps a lab value or a declared condition to RANKING PREFERENCES.
 *
 * WHAT THIS IS NOT. It is not a diagnosis table and it is not a treatment table. Nothing here
 * says a food changes a clinical value. It only decides the order in which already-permitted
 * candidate foods are offered, and the sentence the user sees is the one [TriggerText] renders, which
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
