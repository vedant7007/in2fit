package io.github.vedant7007.katori.domain

import io.github.vedant7007.katori.domain.model.Completeness
import io.github.vedant7007.katori.domain.model.Confidence
import io.github.vedant7007.katori.domain.model.DataSource
import io.github.vedant7007.katori.domain.model.NutrientProfile
import io.github.vedant7007.katori.domain.model.NutrientUnit
import io.github.vedant7007.katori.domain.model.NutritionFigure
import io.github.vedant7007.katori.domain.model.Outcome
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The seams the orchestrator reads and writes the local store through. Same pattern as the
 * arbiter's: domain states the contract, data/local implements it against Room, and the JVM tests
 * hand in fakes. Nothing here is a live query.
 */

/**
 * The person's own standing context, as of one moment. Value types, assembled by the store.
 *
 * This is what makes an ANSWER or a RECOMMEND about THEM. Every field is read before the model is
 * called and rendered into the request by [ContextText]; a path that calls the model with an
 * empty one of these has a chatbot's answer, not the app's.
 */
data class UserContext(
    val profile: ProfileSnapshot,
    val declaredConditions: List<DeclaredCondition>,
    /** Every lab value on file, most recent report first. The rules engine picks what fires. */
    val labValues: List<LabValue>,
    /** Newest first, bounded by the source. Each with its figures as the store computed them. */
    val recentMeals: List<LoggedMeal>,
    /**
     * Derived totals over today and the last seven days, computed by the store's query, never by
     * anything downstream. MEASURED ON THE PHONE (`logs/hw-report-conversational.txt`, 20 Sep):
     * asked "did I get enough iron this week" with only per-meal figures, the model added the
     * two meals' iron itself, and the numeric guard refused both passes, so the person got no
     * answer. The sum the question needs has to be in the request, computed in code.
     */
    val periodTotals: List<PeriodTotals>,
    /**
     * The constrained ingredient list for this profile (spec 4.3), ALREADY FILTERED by diet type
     * and by avoided foods. The rules engine ranks what it is given; it does not know what a
     * vegetarian is, and neither does the model, so the filtering is the store's job.
     */
    val candidates: List<CandidateFood>,
)

/** The store's derived totals over one period. */
data class PeriodTotals(val period: Period, val figures: List<NutritionFigure>)

enum class Period { TODAY, LAST_SEVEN_DAYS }

/** A meal on the timeline with the figures the store computed for it, completeness and all. */
data class LoggedMeal(
    val snapshot: MealSnapshot,
    val figures: List<NutritionFigure>,
)

interface UserContextSource {
    suspend fun current(): UserContext
}

/**
 * Parsed items in, foods, grams and figures out. Composes the food lookup per item.
 *
 * Never invents a quantity, never writes, and returns [Outcome.Unavailable] with NO_MATCH or
 * BELOW_CONFIDENCE_THRESHOLD when an item cannot be resolved with confidence, so the orchestrator
 * asks rather than guesses (spec 10.7). A resolved meal is not yet on the timeline: it is what
 * LOG saves and what SUGGEST evaluates and discards.
 */
interface MealResolver {
    suspend fun resolve(parsed: ParsedMeal, languageTag: String): Outcome<ResolvedMeal>
}

data class ResolvedMeal(
    /** The parse, with each item's matched code and final confidence filled in. */
    val parsed: ParsedMeal,
    val items: List<ResolvedItem>,
    /** Per-nutrient totals across the items, completeness and all. What the UI shows. */
    val figures: List<NutritionFigure>,
)

/**
 * One item, resolved. [nutrients] keeps the three-state values the store must write, because a
 * [MealItemSnapshot] carries only the measured amounts and the rules engine needs no more; a
 * store that wrote from the snapshot would turn every Unknown into an absent row and, on the
 * next read, into a zero.
 */
data class ResolvedItem(
    val snapshot: MealItemSnapshot,
    /** Null for a recognised item the database deliberately holds no figures for. */
    val source: DataSource?,
    val nutrients: NutrientProfile,
    val confidence: Confidence,
)

/**
 * THE ONLY WRITE TO THE TIMELINE, and the orchestrator calls it on LOG and on nothing else.
 * A test that hands in a recording fake and drives a SUGGEST through must see zero calls.
 */
interface MealStore {
    /** @return the new meal id. */
    suspend fun save(meal: ResolvedMeal, loggedAt: Instant): Outcome<Long>
}

/**
 * Renders a [UserContext] into the strings a request carries, in the user's language.
 *
 * Same discipline as [TriggerText]: numbers and dates are formatted here and handed to the string
 * table as positional arguments, so every figure in a rendered line traces to a field and the
 * numeric guard may permit exactly those figures. Units are the symbols (kcal, g, mg, µg), which
 * are not translated. Nutrient names and life-context phrases come from the trigger table so the
 * two never say the same thing two ways.
 */
class ContextText(
    private val strings: Strings,
    private val trigger: TriggerText.Strings,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    interface Strings {
        /** 1 nutrient, 2 number, 3 unit. */
        fun figure(): String
        /** 1 nutrient, 2 number, 3 unit, 4 the items with no value. Worded as a floor. */
        fun figurePartial(): String
        /** 1 nutrient. */
        fun figureNone(): String
        /** 1 when, 2 items, 3 figures. */
        fun meal(): String
        /** 1 period name, 2 figures. */
        fun period(): String
        fun periodName(period: Period): String
        /** 1 test name, 2 value, 3 unit, 4 report date. */
        fun lab(): String
        /** 1 test name, 2 value, 3 unit, 4 low, 5 high, 6 report date. */
        fun labWithRange(): String
        /** The "never suggest" line for a diet type, or null when it forbids nothing. */
        fun neverSuggest(diet: DietType): String?
    }

    fun figure(f: NutritionFigure): String {
        val t = f.total
        val name = trigger.nutrient(t.nutrient)
        return when (t.completeness) {
            Completeness.COMPLETE -> String.format(strings.figure(), name, num(t.amount), unit(t.unit))
            Completeness.PARTIAL -> String.format(
                strings.figurePartial(), name, num(t.amount), unit(t.unit), t.unknownContributors.joinToString(", "),
            )
            Completeness.NONE -> String.format(strings.figureNone(), name)
        }
    }

    fun meal(m: LoggedMeal, locale: Locale): String {
        val at = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)
            .format(m.snapshot.loggedAt.atZone(zone))
        val items = m.snapshot.items.joinToString(", ") { it.displayName }
        val figures = m.figures.joinToString("; ") { figure(it) }
        return String.format(strings.meal(), at, items, figures)
    }

    fun period(p: PeriodTotals): String =
        String.format(strings.period(), strings.periodName(p.period), p.figures.joinToString("; ") { figure(it) })

    fun lab(l: LabValue): String = if (l.referenceLow != null && l.referenceHigh != null) {
        String.format(
            strings.labWithRange(), l.testName, num(l.value), l.unit, num(l.referenceLow), num(l.referenceHigh), l.reportDate,
        )
    } else {
        String.format(strings.lab(), l.testName, num(l.value), l.unit, l.reportDate)
    }

    fun lifeContext(c: LifeContext): String = trigger.lifeContext(c)

    fun neverSuggest(diet: DietType): String? = strings.neverSuggest(diet)

    private fun unit(u: NutrientUnit): String = when (u) {
        NutrientUnit.KCAL -> "kcal"
        NutrientUnit.GRAM -> "g"
        NutrientUnit.MILLIGRAM -> "mg"
        NutrientUnit.MICROGRAM -> "µg"
    }

    /** Same rule as [TriggerText]: at most one decimal, none when it is whole. */
    private fun num(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.ROOT, "%.1f", v)

    companion object {
        /** English defaults; the Android string table is checked against these, not the reverse. */
        val ENGLISH: Strings = object : Strings {
            override fun figure() = "%1\$s: %2\$s %3\$s"
            override fun figurePartial() = "%1\$s: at least %2\$s %3\$s (no value for %4\$s)"
            override fun figureNone() = "%1\$s: not known"
            override fun meal() = "%1\$s: %2\$s. %3\$s"
            override fun period() = "%1\$s: %2\$s"
            override fun periodName(period: Period): String = when (period) {
                Period.TODAY -> "Today so far"
                Period.LAST_SEVEN_DAYS -> "The last seven days"
            }
            override fun lab() = "%1\$s: %2\$s %3\$s (report dated %4\$s)"
            override fun labWithRange() = "%1\$s: %2\$s %3\$s, printed range %4\$s to %5\$s (report dated %6\$s)"
            override fun neverSuggest(diet: DietType): String? = when (diet) {
                DietType.VEGETARIAN -> "meat, fish or eggs (vegetarian)"
                DietType.VEGAN -> "meat, fish, eggs, milk or any dairy (vegan)"
                DietType.EGGETARIAN -> "meat or fish (eggetarian)"
                DietType.JAIN -> "meat, fish, eggs, onion, garlic or root vegetables (Jain)"
                DietType.NON_VEGETARIAN -> null
            }
        }
    }
}
