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

    /**
     * Found by the spec 4.3 list, on the shipped database, hostel context: under "fibre higher,
     * carbohydrate lower" the additive integer score is dominated by the carbohydrate term (a
     * katori of dal is 40 g of carbohydrate and 11 g of fibre, so it scores -290), every real
     * food is negative, and the only non-negative candidates are the zero-carbohydrate ones. So
     * "positive only" alone would leave beat 4 with NO suggestions. The two terms must be
     * comparable: a per-nutrient rank or a normalisation within the candidate set, so that the
     * dal, which is high in fibre and moderate in carbohydrate, ranks with the egg and above
     * the rice, and the list is not empty.
     *
     * GREEN TODAY, because today everything is ranked. It is the guard on the fix for the three
     * above: a fix that drops non-positive scores under the additive scoring turns this red.
     */
    @Test fun `a high-fibre dal is a suggestion under fibre higher carbohydrate lower, and rice is not`() {
        val r = engine.evaluate(input(listOf(
            candidate("white_rice", Nutrient.FIBRE to 0.4, Nutrient.CARBOHYDRATE to 28.0),
            candidate("chana_dal_cooked", Nutrient.FIBRE to 7.6, Nutrient.CARBOHYDRATE to 27.4),
            candidate("boiled_egg", Nutrient.FIBRE to 0.0, Nutrient.CARBOHYDRATE to 1.1),
            candidate("sprouts_salad", Nutrient.FIBRE to 4.0, Nutrient.CARBOHYDRATE to 12.0),
        )))
        val ranked = r.rankedCandidates.map { it.candidate.foodCode }
        assertTrue("beat 4 must have suggestions: $ranked", ranked.isNotEmpty())
        assertTrue("the dal and the sprouts help and must be ranked: $ranked", "chana_dal_cooked" in ranked && "sprouts_salad" in ranked)
        assertTrue("rice is what the preference argues against: $ranked", "white_rice" !in ranked || ranked.indexOf("white_rice") == ranked.lastIndex)
    }

    @Test fun `when every candidate scores zero there are no suggestions, as when there is no preference`() {
        val r = engine.evaluate(input(listOf(candidate("water"), candidate("tea_brewed", Nutrient.CARBOHYDRATE to 0.0))))
        assertTrue("an alphabetical list of zero-scored items is not advice: ${r.rankedCandidates}", r.rankedCandidates.isEmpty())
    }
}
