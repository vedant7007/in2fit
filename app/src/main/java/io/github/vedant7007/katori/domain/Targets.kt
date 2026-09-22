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
 * PRIYA'S RULE. Both functions are pure. Until she lands them this object returns no target,
 * which the screens show as nothing, per the rule that a stub never returns a plausible value.
 */
object TargetRules {
    /**
     * @param activity the profile's activity level as stored, an enum name Priya defines with the
     *   rule; null until the person has declared one.
     * @return null until the profile carries what the rule needs (age, weight, height, sex, goal
     *   kind, activity); never a guess.
     */
    @Suppress("UNUSED_PARAMETER")
    fun targetsFor(profile: ProfileSnapshot, activity: String?, profileVersionMs: Long, at: Instant): Targets? = null

    /** Per nutrient, what the screens read; empty until the rule names its bands. */
    @Suppress("UNUSED_PARAMETER")
    fun progress(targets: Targets, consumed: Map<Nutrient, Double>): List<TargetProgress> = emptyList()
}
