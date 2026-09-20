package io.github.vedant7007.katori.domain

import io.github.vedant7007.katori.domain.model.Nutrient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * RED ON MASTER BY DESIGN, for the integrator (his 16:50 "2d: land it and I take it").
 *
 * The defect, measured in `DemoSentencesTest` on the shipped database: with "fibre higher,
 * carbohydrate lower" (beat 4, the glucose report) every item with no carbohydrate and no fibre
 * scores exactly zero and ties, and the tiebreak (food code, alphabetical) presents raw chicken
 * breast, brewed coffee, raw carp and water as the top suggestions under a correct trigger
 * sentence. The engine already refuses to rank when there is NO preference ("an arbitrary order
 * presented as advice is the same failure as a made-up number"). The same reasoning applies per
 * candidate: a candidate the preferences gave no reason to prefer is not a suggestion, and a
 * candidate the preferences argue against (a negative score) is not one either.
 *
 * THE PROPERTY: a ranked candidate has a positive score. Zero and below are not ranked.
 */
class RankingDefectsTest {

    private val engine = DefaultRulesEngine()

    private fun candidate(code: String, vararg n: Pair<Nutrient, Double>) =
        CandidateFood(code, code.replace('_', ' '), setOf(LifeContext.HOSTEL_STUDENT), n.toMap(), servingGrams = 100.0)

    private fun input(candidates: List<CandidateFood>) = RuleInput(
        profile = ProfileSnapshot(22, 62.0, 168.0, null, null, LifeContext.HOSTEL_STUDENT, null, emptySet()),
        declaredConditions = emptyList(),
        labValues = listOf(LabValue("Fasting glucose", 118.0, "mg/dL", 70.0, 100.0, LocalDate.of(2026, 9, 24))),
        meal = null,
        candidates = candidates,
        evaluatedAt = Instant.parse("2026-09-26T07:30:00Z"),
    )

    @Test fun `a candidate the preferences gave no reason to prefer is not ranked`() {
        val r = engine.evaluate(input(listOf(
            candidate("water"),
            candidate("coffee_brewed", Nutrient.CARBOHYDRATE to 0.0, Nutrient.FIBRE to 0.0),
            candidate("chicken_raw", Nutrient.CARBOHYDRATE to 0.0, Nutrient.FIBRE to 0.0, Nutrient.IRON to 0.4),
            candidate("sprouts", Nutrient.FIBRE to 6.0, Nutrient.CARBOHYDRATE to 20.0),
        )))
        assertTrue("the report must produce a preference", r.constraints.filterIsInstance<Constraint.PreferNutrient>().isNotEmpty())
        val ranked = r.rankedCandidates.map { it.candidate.foodCode }
        assertTrue("a zero-scored candidate was presented as a suggestion: $ranked", listOf("water", "coffee_brewed", "chicken_raw").none { it in ranked })
        r.rankedCandidates.forEach { assertTrue("${it.candidate.foodCode} ranked at ${it.score}", it.score > 0) }
    }

    @Test fun `a candidate the preferences argue against is not ranked`() {
        val r = engine.evaluate(input(listOf(
            candidate("white_rice", Nutrient.FIBRE to 0.4, Nutrient.CARBOHYDRATE to 28.0),
            candidate("sprouts", Nutrient.FIBRE to 6.0, Nutrient.CARBOHYDRATE to 2.0),
        )))
        assertEquals("white rice scores below zero on 'fibre higher, carbohydrate lower' and is not a suggestion", listOf("sprouts"), r.rankedCandidates.map { it.candidate.foodCode })
    }

    @Test fun `when every candidate scores zero there are no suggestions, as when there is no preference`() {
        val r = engine.evaluate(input(listOf(candidate("water"), candidate("tea_brewed", Nutrient.CARBOHYDRATE to 0.0))))
        assertTrue("an alphabetical list of zero-scored items is not advice: ${r.rankedCandidates}", r.rankedCandidates.isEmpty())
    }
}
