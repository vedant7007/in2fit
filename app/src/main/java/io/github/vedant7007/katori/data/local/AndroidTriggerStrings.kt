package io.github.vedant7007.katori.data.local

import android.content.Context
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.DietType
import io.github.vedant7007.katori.domain.LifeContext
import io.github.vedant7007.katori.domain.Period
import io.github.vedant7007.katori.domain.SpokenIntent
import io.github.vedant7007.katori.domain.TriggerTemplate
import io.github.vedant7007.katori.domain.TriggerText
import io.github.vedant7007.katori.domain.model.Nutrient

/**
 * [TriggerText.Strings] from the string table, in whatever language the app is running in.
 *
 * Every mapping is an explicit `when` over the enum, not a name lookup, so a template with no
 * key fails to COMPILE rather than failing on a phone at the moment a health sentence is needed.
 * Adding a constant to [TriggerTemplate] means adding a branch here and a key in
 * `res/values/strings.xml`, and `0017` rule 5's test shape covers the second.
 *
 * The format strings carry positional arguments (`%1$s`) that [TriggerText.render] supplies in a
 * fixed order documented on [TriggerText.ENGLISH]. A translator may reorder the arguments in
 * their sentence, which is what positional arguments are for; they must not add a number.
 */
class AndroidTriggerStrings(context: Context) : TriggerText.Strings {

    private val res = context.applicationContext.resources

    override fun template(template: TriggerTemplate): String = res.getString(
        when (template) {
            TriggerTemplate.ESCALATE_ABOVE_RANGE -> R.string.trigger_escalate_above_range
            TriggerTemplate.ESCALATE_BELOW_RANGE -> R.string.trigger_escalate_below_range
            TriggerTemplate.LAB_ABOVE_RANGE -> R.string.trigger_lab_above_range
            TriggerTemplate.LAB_BELOW_RANGE -> R.string.trigger_lab_below_range
            TriggerTemplate.DECLARED_CONDITION -> R.string.trigger_declared_condition
            TriggerTemplate.MEAL_COMPOSITION -> R.string.trigger_meal_composition
            TriggerTemplate.LIFE_CONTEXT -> R.string.trigger_life_context
            TriggerTemplate.TIMELINE -> R.string.trigger_timeline
        }
    )

    override fun nutrient(nutrient: Nutrient): String = res.getString(
        when (nutrient) {
            Nutrient.ENERGY -> R.string.nutrient_energy
            Nutrient.PROTEIN -> R.string.nutrient_protein
            Nutrient.CARBOHYDRATE -> R.string.nutrient_carbohydrate
            Nutrient.FAT -> R.string.nutrient_fat
            Nutrient.FIBRE -> R.string.nutrient_fibre
            Nutrient.IRON -> R.string.nutrient_iron
            Nutrient.VITAMIN_B12 -> R.string.nutrient_vitamin_b12
            Nutrient.SODIUM -> R.string.nutrient_sodium
        }
    )

    override fun lifeContext(context: LifeContext): String = res.getString(
        when (context) {
            LifeContext.HOSTEL_STUDENT -> R.string.life_context_hostel_student
            LifeContext.PG_OWN_COOKING -> R.string.life_context_pg_own_cooking
            LifeContext.FIELD_OR_MANUAL_WORKER -> R.string.life_context_field_or_manual_worker
            LifeContext.DESK_PROFESSIONAL -> R.string.life_context_desk_professional
            LifeContext.HOMEMAKER -> R.string.life_context_homemaker
        }
    )
}

/**
 * [ContextText.Strings] from the string table: the lines that carry a person's own meals, lab
 * values and diet into a request. Same rule as above: an explicit `when`, positional arguments,
 * no number in any format string.
 */
class AndroidContextStrings(context: Context) : ContextText.Strings {

    private val res = context.applicationContext.resources

    override fun figure(): String = res.getString(R.string.context_figure)
    override fun figurePartial(): String = res.getString(R.string.context_figure_partial)
    override fun figureNone(): String = res.getString(R.string.context_figure_none)
    override fun meal(): String = res.getString(R.string.context_meal)
    override fun period(): String = res.getString(R.string.context_period)
    override fun periodName(period: Period): String = res.getString(
        when (period) {
            Period.TODAY -> R.string.context_period_today
            Period.LAST_SEVEN_DAYS -> R.string.context_period_last_seven_days
        }
    )
    override fun lab(): String = res.getString(R.string.context_lab)
    override fun labWithRange(): String = res.getString(R.string.context_lab_with_range)

    override fun referral(): String = res.getString(R.string.context_referral)

    override fun leadIn(intent: SpokenIntent): String = res.getString(
        when (intent) {
            SpokenIntent.LOG -> R.string.tts_lead_in_log
            SpokenIntent.ANSWER -> R.string.tts_lead_in_answer
            SpokenIntent.SUGGEST -> R.string.tts_lead_in_suggest
            SpokenIntent.RECOMMEND -> R.string.tts_lead_in_recommend
        }
    )

    override fun neverSuggest(diet: DietType): String? = when (diet) {
        DietType.VEGETARIAN -> R.string.context_never_suggest_vegetarian
        DietType.VEGAN -> R.string.context_never_suggest_vegan
        DietType.EGGETARIAN -> R.string.context_never_suggest_eggetarian
        DietType.JAIN -> R.string.context_never_suggest_jain
        DietType.NON_VEGETARIAN -> null
    }?.let(res::getString)
}
