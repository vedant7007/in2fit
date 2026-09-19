package io.github.vedant7007.katori.domain

import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.Nutrient
import java.security.MessageDigest

/**
 * The deterministic rules engine.
 *
 * Read the contract on [RulesEngine] first. The obligations it states are absolute and this
 * implementation is built around them rather than around convenience:
 *
 *  - no clock is read anywhere in this file
 *  - no randomness, and every sort has an explicit final tiebreak
 *  - no I/O; everything arrives in [RuleInput]
 *  - no model is consulted, for ranking or for wording
 *  - the digest covers the data and deliberately excludes [RuleInput.evaluatedAt]
 *
 * SAFETY, spec 15. Every sentence this engine can emit is in [Templates] below, so the language
 * rules are reviewed once per template rather than audited per output. No template names a
 * disease. A value outside its printed range produces a statement about the value and the range
 * that is printed on the user's own report, and nothing more.
 */
class DefaultRulesEngine : RulesEngine {

    override val rules: List<RuleDescriptor> = listOf(
        RuleDescriptor(
            RuleIds.LAB_FAR_OUTSIDE_RANGE,
            "A lab value sits far outside the range printed on the report. A doctor referral is " +
                "mandatory in the response, ALONGSIDE the ranking the value also adjusts (0015).",
            Severity.ESCALATE,
        ),
        RuleDescriptor(
            RuleIds.LAB_ABOVE_RANGE,
            "A lab value is above the range printed on the report. Adjusts how candidate swaps " +
                "are ranked. Never names a condition.",
            Severity.ADJUST,
        ),
        RuleDescriptor(
            RuleIds.LAB_BELOW_RANGE,
            "A lab value is below the range printed on the report. Adjusts ranking.",
            Severity.ADJUST,
        ),
        RuleDescriptor(
            RuleIds.DECLARED_CONDITION,
            "The user declared a condition themselves. Applies that condition's nutrient " +
                "preferences to ranking.",
            Severity.ADJUST,
        ),
        RuleDescriptor(
            RuleIds.AVOIDED_FOODS,
            "Foods the user avoids, including allergies and diet type, are excluded outright. " +
                "Never relaxed to produce a suggestion.",
            Severity.INFORM,
        ),
        RuleDescriptor(
            RuleIds.LIFE_CONTEXT,
            "Restricts candidates to what this life context can obtain and cook.",
            Severity.INFORM,
        ),
        RuleDescriptor(
            RuleIds.MEAL_NUTRIENT_DOMINANT,
            "One item supplies most of a nutrient in the logged meal. Reported as an observation.",
            Severity.INFORM,
        ),
    )

    override fun evaluate(input: RuleInput): RuleEvaluation {
        val fired = mutableListOf<FiredRule>()
        val constraints = mutableListOf<Constraint>()

        // --- safety first: anything far outside its printed range makes a referral mandatory ----
        val escalations = input.labValues.mapNotNull { lab ->
            val outside = outsideRange(lab) ?: return@mapNotNull null
            if (!isFarOutside(lab, outside)) return@mapNotNull null
            FiredRule(RuleIds.LAB_FAR_OUTSIDE_RANGE, evidenceFor(lab), Severity.ESCALATE)
        }.sortedBy { (it.evidence as Evidence.LabValueOutsideRange).testName }

        // 0015: a referral is mandatory, and it comes ALONGSIDE the help rather than instead of it.
        // The escalations are fired here, first, and the same values fall through to the ordinary
        // lab handling below so they also adjust the ranking exactly as a milder reading would.
        // The referral sentence wins the trigger at the end, whatever else fired.
        fired += escalations
        val referral: TriggerStatement? = escalations.firstOrNull()?.let { first ->
            TriggerStatement(
                ruleId = first.id,
                template = (first.evidence as Evidence.LabValueOutsideRange).let { e ->
                    if (e.referenceHigh != null && e.value > e.referenceHigh) {
                        TriggerTemplate.ESCALATE_ABOVE_RANGE
                    } else {
                        TriggerTemplate.ESCALATE_BELOW_RANGE
                    }
                },
                evidence = first.evidence,
            )
        }

        // --- exclusions. These are never relaxed, whatever else fires. ------------------------
        val excluded = mutableSetOf<String>()
        if (input.profile.avoidedFoodCodes.isNotEmpty()) {
            excluded += input.profile.avoidedFoodCodes
            constraints += Constraint.Exclude(
                sourceRule = RuleIds.AVOIDED_FOODS,
                foodCodes = input.profile.avoidedFoodCodes.toSortedSet(),
                reason = "You told us to avoid these.",
            )
            fired += FiredRule(
                RuleIds.AVOIDED_FOODS,
                Evidence.ProfileContext(input.profile.context ?: LifeContext.HOSTEL_STUDENT),
                Severity.INFORM,
            )
        }

        // --- life context ---------------------------------------------------------------------
        val context = input.profile.context
        if (context != null) {
            constraints += Constraint.RestrictToContext(RuleIds.LIFE_CONTEXT, context)
            fired += FiredRule(RuleIds.LIFE_CONTEXT, Evidence.ProfileContext(context), Severity.INFORM)
        }

        // --- lab values inside the ordinary out-of-range band ----------------------------------
        val labFired = mutableListOf<Pair<FiredRule, List<Constraint>>>()
        for (lab in input.labValues.sortedWith(compareBy({ it.testName }, { it.reportDate.toString() }))) {
            val direction = outsideRange(lab) ?: continue
            val rule = if (direction == RangeDirection.ABOVE) RuleIds.LAB_ABOVE_RANGE else RuleIds.LAB_BELOW_RANGE
            val ev = evidenceFor(lab)
            val prefs = NutrientPolicy.forLabValue(lab.testName, direction, rule)
            labFired += FiredRule(rule, ev, Severity.ADJUST) to prefs
        }
        labFired.forEach { (f, c) -> fired += f; constraints += c }

        // --- conditions the user declared themselves -------------------------------------------
        for (cond in input.declaredConditions.sortedBy { it.name.lowercase() }) {
            val prefs = NutrientPolicy.forCondition(cond.name)
            if (prefs.isEmpty()) continue
            fired += FiredRule(RuleIds.DECLARED_CONDITION, Evidence.UserDeclaredCondition(cond), Severity.ADJUST)
            constraints += prefs
        }

        // --- an observation about the meal itself -----------------------------------------------
        input.meal?.let { meal ->
            dominantContributor(meal)?.let { (nutrient, share, item) ->
                fired += FiredRule(
                    RuleIds.MEAL_NUTRIENT_DOMINANT,
                    Evidence.MealComposition(nutrient, share, item),
                    Severity.INFORM,
                )
            }
        }

        // --- rank ---------------------------------------------------------------------------------
        val ranked = rank(input, constraints, excluded, context)

        return RuleEvaluation(
            firedRules = fired,
            constraints = constraints,
            rankedCandidates = ranked,
            // A trigger explains why the suggestions look the way they do. With no suggestions
            // there is nothing to explain, and a sentence on its own would be a claim about a
            // change the user cannot see. A referral is the exception: it is shown whether or not
            // there is a list, because "show this to a doctor" is a message in its own right.
            trigger = referral ?: if (ranked.isEmpty()) null else triggerFor(fired),
            inputDigest = digest(input),
        )
    }

    // --- ranking -----------------------------------------------------------------------------

    private fun rank(
        input: RuleInput,
        constraints: List<Constraint>,
        excluded: Set<String>,
        context: LifeContext?,
    ): List<RankedCandidate> {
        val prefs = constraints.filterIsInstance<Constraint.PreferNutrient>()

        // NO PREFERENCES MEANS NO SUGGESTIONS.
        //
        // Found by a test that asserted beat 4's own claim. With nothing to rank by, every
        // candidate scored zero and the list fell back to its tiebreak, which is alphabetical.
        // That produced a confident-looking ordering that meant nothing, and it could coincide
        // with the ranked order, making "the advice changed" silently false.
        //
        // An arbitrary order presented as advice is the same failure as a made-up number. If no
        // rule gave us a reason to prefer one food over another, we have nothing to say, and the
        // UI shows no suggestions rather than an ordering with no basis.
        if (prefs.isEmpty()) return emptyList()

        val surviving = input.candidates.filter { c ->
            if (c.foodCode in excluded) return@filter false
            if (context != null && c.contexts.isNotEmpty() && context !in c.contexts) return@filter false
            true
        }

        val scored = surviving.map { c ->
            var score = 0
            val applied = mutableListOf<Constraint>()
            for (p in prefs) {
                val per100 = c.nutrientsPer100g[p.nutrient] ?: continue
                // Integer score so ordering cannot drift with floating point across devices.
                val contribution = (per100 * p.weight).toInt()
                score += if (p.direction == Constraint.PreferNutrient.Direction.HIGHER) contribution else -contribution
                applied += p
            }
            RankedCandidate(
                candidate = c,
                score = score,
                appliedConstraints = applied,
                confidence = ConfidenceRules.of(ConfidenceReason.EXACT_FOOD_MATCH),
            )
        }

        // Total order: score descending, then food code. The final tiebreak is what stops two
        // equally scored candidates swapping places between runs.
        return scored.sortedWith(
            compareByDescending<RankedCandidate> { it.score }.thenBy { it.candidate.foodCode }
        )
    }

    // --- trigger ------------------------------------------------------------------------------

    private fun triggerFor(fired: List<FiredRule>): TriggerStatement? {
        // The most specific thing that changed wins: a lab value beats a declared condition,
        // which beats a context restriction. Deterministic, and it matches what a reader expects
        // to be told first.
        val order = listOf(
            RuleIds.LAB_ABOVE_RANGE, RuleIds.LAB_BELOW_RANGE,
            RuleIds.DECLARED_CONDITION, RuleIds.MEAL_NUTRIENT_DOMINANT, RuleIds.LIFE_CONTEXT,
        )
        val chosen = order.firstNotNullOfOrNull { id -> fired.firstOrNull { it.id == id } } ?: return null
        // WHICH sentence, never the sentence. The engine is pure and knows no locale; the words
        // come from the string table through TriggerText, in the language the person chose.
        val template = when (val e = chosen.evidence) {
            is Evidence.LabValueOutsideRange ->
                if (e.referenceHigh != null && e.value > e.referenceHigh) TriggerTemplate.LAB_ABOVE_RANGE
                else TriggerTemplate.LAB_BELOW_RANGE
            is Evidence.UserDeclaredCondition -> TriggerTemplate.DECLARED_CONDITION
            is Evidence.MealComposition -> TriggerTemplate.MEAL_COMPOSITION
            is Evidence.ProfileContext -> TriggerTemplate.LIFE_CONTEXT
            is Evidence.TimelinePattern -> TriggerTemplate.TIMELINE
        }
        return TriggerStatement(chosen.id, template, chosen.evidence)
    }

    // --- helpers ------------------------------------------------------------------------------

    /** Null when the value is inside its range, or when the report printed no range to compare to. */
    private fun outsideRange(lab: LabValue): RangeDirection? {
        val hi = lab.referenceHigh
        val lo = lab.referenceLow
        // No printed range means no comparison. The app carries no ranges of its own, because
        // they differ by laboratory and assay, so an unreadable range degrades to silence.
        if (hi == null && lo == null) return null
        if (hi != null && lab.value > hi) return RangeDirection.ABOVE
        if (lo != null && lab.value < lo) return RangeDirection.BELOW
        return null
    }

    /** Far outside means half again past the printed bound. Deliberately conservative. */
    private fun isFarOutside(lab: LabValue, direction: RangeDirection): Boolean = when (direction) {
        RangeDirection.ABOVE -> lab.referenceHigh?.let { it > 0 && lab.value >= it * 1.5 } ?: false
        RangeDirection.BELOW -> lab.referenceLow?.let { it > 0 && lab.value <= it * 0.5 } ?: false
    }

    private fun evidenceFor(lab: LabValue) = Evidence.LabValueOutsideRange(
        testName = lab.testName,
        value = lab.value,
        unit = lab.unit,
        referenceLow = lab.referenceLow,
        referenceHigh = lab.referenceHigh,
        reportDate = lab.reportDate,
    )

    /** The single largest contributor to one nutrient in the meal, when it dominates. */
    private fun dominantContributor(meal: MealSnapshot): Triple<Nutrient, Double, String>? {
        if (meal.items.isEmpty()) return null
        var best: Triple<Nutrient, Double, String>? = null
        for (nutrient in listOf(Nutrient.CARBOHYDRATE, Nutrient.FAT, Nutrient.PROTEIN)) {
            val total = meal.items.sumOf { it.nutrients[nutrient] ?: 0.0 }
            if (total <= 0.0) continue
            val top = meal.items
                .sortedWith(compareByDescending<MealItemSnapshot> { it.nutrients[nutrient] ?: 0.0 }
                    .thenBy { it.displayName })
                .first()
            val share = (top.nutrients[nutrient] ?: 0.0) / total
            if (share >= 0.6 && (best == null || share > best!!.second)) {
                best = Triple(nutrient, share, top.displayName)
            }
        }
        return best
    }

    /**
     * Canonical digest of the semantically relevant input.
     *
     * [RuleInput.evaluatedAt] is EXCLUDED. Including it would make every evaluation differ
     * trivially and the digest would prove nothing, which is the opposite of why it exists.
     * Everything else is serialised in a fixed order so that map and set iteration order cannot
     * change the answer.
     */
    private fun digest(input: RuleInput): String {
        val sb = StringBuilder()
        with(input.profile) {
            sb.append("profile|").append(ageYears).append('|').append(weightKg).append('|')
                .append(heightCm).append('|').append(sex).append('|').append(goal).append('|')
                .append(context).append('|').append(dietType).append('|')
                .append(avoidedFoodCodes.sorted().joinToString(",")).append('\n')
        }
        input.declaredConditions
            .sortedWith(compareBy({ it.name.lowercase() }, { it.source.name }))
            .forEach { sb.append("cond|").append(it.name.lowercase()).append('|').append(it.source).append('\n') }

        input.labValues
            .sortedWith(compareBy({ it.testName.lowercase() }, { it.reportDate.toString() }, { it.value }))
            .forEach {
                sb.append("lab|").append(it.testName.lowercase()).append('|').append(it.value)
                    .append('|').append(it.unit).append('|').append(it.referenceLow)
                    .append('|').append(it.referenceHigh).append('|').append(it.reportDate).append('\n')
            }

        input.meal?.let { m ->
            sb.append("meal|").append(m.mealId).append('\n')
            m.items.sortedWith(compareBy({ it.displayName }, { it.foodCode ?: "" })).forEach { i ->
                sb.append("item|").append(i.displayName).append('|').append(i.foodCode)
                    .append('|').append(i.grams).append('|')
                    .append(i.nutrients.entries.sortedBy { it.key.name }
                        .joinToString(",") { "${it.key.name}=${it.value}" })
                    .append('\n')
            }
        }

        input.candidates.map { it.foodCode }.sorted()
            .forEach { sb.append("cand|").append(it).append('\n') }

        val bytes = MessageDigest.getInstance("SHA-256").digest(sb.toString().toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/** Stable rule identifiers. Stored suggestions reference these, so this list is append-only. */
object RuleIds {
    val LAB_FAR_OUTSIDE_RANGE = RuleId("lab.far_outside_range")
    val LAB_ABOVE_RANGE = RuleId("lab.above_range")
    val LAB_BELOW_RANGE = RuleId("lab.below_range")
    val DECLARED_CONDITION = RuleId("profile.declared_condition")
    val AVOIDED_FOODS = RuleId("profile.avoided_foods")
    val LIFE_CONTEXT = RuleId("profile.life_context")
    val MEAL_NUTRIENT_DOMINANT = RuleId("meal.nutrient_dominant")
}
