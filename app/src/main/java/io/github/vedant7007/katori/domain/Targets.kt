package io.github.vedant7007.katori.domain

import io.github.vedant7007.katori.domain.model.Nutrient
import java.time.Instant

/**
 * THE DAY'S TARGETS, the shape Ira specced 21 Sep 07:27 and the boundary Arjun set 07:33:
 * computed by the rules engine from the profile, never by the model and never by a screen.
 *
 * WHO OWNS WHAT. [TargetRules] is Priya's: the rule, its source and its thresholds. The profile
 * behind it and `TargetsSource`, which reads the profile and calls the rule, are Arjun's. The
 * ring and the bars read [TargetProgress] and do no arithmetic on it. Until [TargetRules.targetsFor]
 * returns a value there is no target, and the screens render empty: no ring fill, no
 * denominator, no "left". Never a placeholder figure.
 */
data class Targets(
    /** The day's energy target, kcal. */
    val energyKcal: Double,
    val proteinG: Double,
    val carbohydrateG: Double,
    val fatG: Double,
    val waterMl: Double,
    /** The rule id and its source, e.g. "ICMR-NIN 2020 RDA, sedentary, maintain". Shown as the basis. */
    val rule: String,
    /** `ProfileEntity.updated_at_epoch_ms` the targets were computed from; a changed profile recomputes. */
    val profileVersionMs: Long,
)

/** One nutrient against its target, engine-computed. [status] is a threshold in the rule. */
data class TargetProgress(
    val nutrient: Nutrient,
    val target: Double,
    val consumed: Double,
    val remaining: Double,
    /** consumed / target, unclamped; the ring clamps for drawing and says so. */
    val fraction: Double,
    val status: TargetStatus,
)

enum class TargetStatus { WITHIN, UNDER, OVER }

/**
 * The three physical-activity classes of ICMR-NIN 2020 (`A Brief Note on Nutrient Requirements
 * for Indians`, the source the energy row `energy.india_adults` in the knowledge file cites),
 * which are the only classes the target rule has a sourced energy figure for. The profile
 * stores the NAME verbatim (`ProfileEntity.activity`); Ira's picker shows [plainWords].
 *
 * A hostel student who walks to class is [SEDENTARY]: ICMR-NIN classes students, office and
 * desk work as sedentary; "moderate" is a day of physical work (a field, a shop floor, a
 * delivery round); "heavy" is manual labour. The demo person (19, 62 kg, 172 cm, male, hostel
 * student) is SEDENTARY and the seed writes that name.
 */
enum class ActivityLevel(val plainWords: String) {
    SEDENTARY("Mostly sitting: class, desk, study; walking to and fro"),
    MODERATE("On my feet most of the day: field, shop floor, deliveries"),
    HEAVY("Hard physical work most of the day"),
}

/**
 * THE DAY'S TARGETS, computed deterministically from the profile, never by the model, and every
 * one carrying its basis so "where does 2,010 come from" has an answer from inside the app.
 *
 * WHAT IT STANDS ON, and only that (the rows are in `assets/knowledge/facts.csv`, cited there):
 *  - ENERGY: ICMR-NIN 2020, Tables 1a and 1b (`energy.india_adults`): 2110 / 2710 / 3470 kcal a
 *    day for a 65 kg man doing sedentary / moderate / heavy work, 1660 / 2130 / 2720 for a 55 kg
 *    woman. The requirement is per kilogram of body weight, so the reference figure is scaled
 *    by the person's weight against the reference weight, as the tables themselves are built.
 *  - PROTEIN: ICMR-NIN 2020 RDA, 0.83 g per kg of body weight (`protein.rda_india`).
 *  - FAT: WHO, total fat at most 30% of the day's energy (`fat.who_limits`), at 9 kcal per gram
 *    (`fat.energy_per_gram`). A ceiling, not a goal.
 *  - CARBOHYDRATE: the remainder of the energy target after the protein RDA at 4 kcal per gram
 *    (`fat.energy_per_gram`) and the fat ceiling, at 4 kcal per gram of carbohydrate (the Atwater
 *    general factor). Arithmetic on sourced figures, named as such in the basis.
 *  - WATER: no sourced figure in the file, so no target: `waterMl` is 0 and the basis says so.
 *    The water card treats 0 as no target.
 *
 * WHAT IT REFUSES TO GUESS. No weight, no sex stated as male or female (the tables have no
 * third column and the app does not average them), no activity level, or an age under 18 (no
 * adolescent table shipped): null, and the ring stays empty. A goal of losing or gaining weight
 * gets the MAINTENANCE requirement with the basis saying so; the file carries no sourced deficit
 * or surplus, and a number without one is a guess.
 *
 * THRESHOLDS, the rule's own, not a source's: a nutrient is WITHIN its target from 90% to 110%,
 * UNDER below, OVER above; the fat ceiling is WITHIN at or below 100% and OVER above it.
 */
object TargetRules {

    /** ICMR-NIN 2020 Tables 1a and 1b, kcal per day for the reference man (65 kg) and woman (55 kg), by activity. */
    private val REFERENCE_KCAL: Map<Pair<Sex, ActivityLevel>, Double> = mapOf(
        (Sex.MALE to ActivityLevel.SEDENTARY) to 2110.0, (Sex.MALE to ActivityLevel.MODERATE) to 2710.0, (Sex.MALE to ActivityLevel.HEAVY) to 3470.0,
        (Sex.FEMALE to ActivityLevel.SEDENTARY) to 1660.0, (Sex.FEMALE to ActivityLevel.MODERATE) to 2130.0, (Sex.FEMALE to ActivityLevel.HEAVY) to 2720.0,
    )
    private val REFERENCE_KG: Map<Sex, Double> = mapOf(Sex.MALE to 65.0, Sex.FEMALE to 55.0)
    private const val PROTEIN_G_PER_KG = 0.83
    private const val FAT_SHARE_MAX = 0.30
    private const val KCAL_PER_G_FAT = 9.0
    private const val KCAL_PER_G_PROTEIN = 4.0
    private const val KCAL_PER_G_CARBOHYDRATE = 4.0

    /**
     * @param activity the profile's activity level as stored, an [ActivityLevel] name; null until
     *   the person has declared one.
     * @return null until the profile carries what the rule needs (weight, sex as male or female,
     *   activity, age 18 or over); never a guess.
     */
    @Suppress("UNUSED_PARAMETER")
    fun targetsFor(profile: ProfileSnapshot, activity: String?, profileVersionMs: Long, at: Instant): Targets? {
        val weight = profile.weightKg?.takeIf { it > 0.0 } ?: return null
        val sex = profile.sex?.takeIf { it in REFERENCE_KG } ?: return null
        val level = activity?.let { a -> ActivityLevel.entries.firstOrNull { it.name == a } } ?: return null
        val age = profile.ageYears ?: return null
        if (age < 18) return null

        val referenceKcal = REFERENCE_KCAL.getValue(sex to level)
        val referenceKg = REFERENCE_KG.getValue(sex)
        val kcalPerKg = referenceKcal / referenceKg
        val energy = roundTo(kcalPerKg * weight, 10.0)
        val protein = roundTo(PROTEIN_G_PER_KG * weight, 1.0)
        val fat = roundTo(FAT_SHARE_MAX * energy / KCAL_PER_G_FAT, 1.0)
        val carbohydrate = roundTo((energy - protein * KCAL_PER_G_PROTEIN - fat * KCAL_PER_G_FAT) / KCAL_PER_G_CARBOHYDRATE, 1.0)
        val goalNote = when (profile.goal) {
            null, Goal.MAINTAIN, Goal.NOT_STATED -> ""
            else -> " This is the maintenance requirement; the app carries no sourced figure for a deficit or surplus."
        }
        val rule = "Energy: ICMR-NIN 2020 Tables 1a/1b, ${referenceKcal.toInt()} kcal for a ${referenceKg.toInt()} kg ${sexWord(sex)} doing ${level.name.lowercase()} work, " +
            "scaled to ${weight.toInt()} kg (${"%.1f".format(kcalPerKg)} kcal per kg). Protein: ICMR-NIN 2020 RDA, 0.83 g per kg. " +
            "Fat: a ceiling, WHO 30% of energy at 9 kcal per gram. Carbohydrate: the remainder at 4 kcal per gram. Water: no sourced target.$goalNote"
        return Targets(energy, protein, carbohydrate, fat, waterMl = 0.0, rule = rule, profileVersionMs = profileVersionMs)
    }

    /** Per nutrient with a target, against today's COMPLETE totals; a nutrient with nothing logged is UNDER at zero. */
    fun progress(targets: Targets, consumed: Map<Nutrient, Double>): List<TargetProgress> {
        val goals = listOf(Nutrient.ENERGY to targets.energyKcal, Nutrient.PROTEIN to targets.proteinG, Nutrient.CARBOHYDRATE to targets.carbohydrateG, Nutrient.FAT to targets.fatG)
        return goals.filter { (_, target) -> target > 0.0 }.map { (nutrient, target) ->
            val had = consumed[nutrient] ?: 0.0
            val fraction = had / target
            val status = when {
                nutrient == Nutrient.FAT -> if (fraction <= 1.0) TargetStatus.WITHIN else TargetStatus.OVER
                fraction < 0.9 -> TargetStatus.UNDER
                fraction <= 1.1 -> TargetStatus.WITHIN
                else -> TargetStatus.OVER
            }
            TargetProgress(nutrient, target, had, remaining = maxOf(target - had, 0.0), fraction = fraction, status = status)
        }
    }

    private fun sexWord(sex: Sex) = if (sex == Sex.MALE) "man" else "woman"
    private fun roundTo(value: Double, step: Double): Double = Math.round(value / step) * step
}
