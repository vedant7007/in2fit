package io.github.vedant7007.katori.domain

import io.github.vedant7007.katori.domain.model.Nutrient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * RED ON MASTER BY DESIGN, for the integrator. THE RULING (Vedant, 20 September, evening):
 * **make the terms comparable; do not filter by sign.**
 *
 * The defect, measured in `DemoSentencesTest` on the shipped database with the spec 4.3 list:
 * under "fibre higher, carbohydrate lower" (beat 4, the glucose report) the additive integer
 * score is the fibre term minus the carbohydrate term, per serving, and a katori of dal is
 * 40 g of carbohydrate against 11 g of fibre. So every real food scores below zero, the only
 * non-negative candidates are the ones with nothing in them (an egg at zero carbohydrate; water
 * and black tea until the list dropped them), and they tie at the top in alphabetical order.
 *
 * The plausible wrong fix is "rank positive scores only": under the additive scoring that
 * leaves beat 4 EMPTY, and the first version of this file asked for exactly that. The defect is
 * not the sign; it is that the two terms were never on the same scale. A per-nutrient rank (a
 * percentile within the candidate set, best = 100) or a normalisation makes them comparable:
 * on the hostel list it gives fresh coconut, cooked moong dal, carrot, beetroot, chicken curry,
 * which a person would recognise as food. Three tests: two red today, one green that turns red
 * under the fix this file itself first proposed.
 */
class RankingDefectsTest {

    private val engine = DefaultRulesEngine()

    private fun candidate(code: String, vararg n: Pair<Nutrient, Double>) =
        CandidateFood(code, code.replace('_', ' '), setOf(LifeContext.HOSTEL_STUDENT), n.toMap(), servingGrams = 100.0)

    /** The glucose report: "fibre higher, carbohydrate lower". */
    private fun input(candidates: List<CandidateFood>) = RuleInput(
        profile = ProfileSnapshot(22, 62.0, 168.0, null, null, LifeContext.HOSTEL_STUDENT, null, emptySet()),
        declaredConditions = emptyList(),
        labValues = listOf(LabValue("Fasting glucose", 118.0, "mg/dL", 70.0, 100.0, LocalDate.of(2026, 9, 24))),
        meal = null,
        candidates = candidates,
        evaluatedAt = Instant.parse("2026-09-26T07:30:00Z"),
    )

    private val rice = candidate("white_rice", Nutrient.FIBRE to 0.4, Nutrient.CARBOHYDRATE to 28.0)
    private val dal = candidate("chana_dal_cooked", Nutrient.FIBRE to 7.6, Nutrient.CARBOHYDRATE to 27.4)
    private val egg = candidate("boiled_egg", Nutrient.FIBRE to 0.0, Nutrient.CARBOHYDRATE to 1.1)
    private val sprouts = candidate("sprouts_salad", Nutrient.FIBRE to 4.0, Nutrient.CARBOHYDRATE to 12.0)
    private val coconut = candidate("fresh_coconut", Nutrient.FIBRE to 9.0, Nutrient.CARBOHYDRATE to 15.2)

    private fun ranked(vararg c: CandidateFood) = engine.evaluate(input(c.toList())).rankedCandidates.map { it.candidate.foodCode }

    /** The measured defect: the empty cup leads because it is the only candidate not below zero. */
    @Test fun `a candidate with nothing in it does not lead a list that has real food on it`() {
        val order = ranked(candidate("black_tea", Nutrient.FIBRE to 0.0, Nutrient.CARBOHYDRATE to 0.0), rice, dal, sprouts, coconut, egg)
        assertTrue("the top suggestion must be a food with fibre, not the empty cup: $order", order.first() in setOf("fresh_coconut", "chana_dal_cooked", "sprouts_salad"))
    }

    /**
     * THE RULING IN ONE ASSERTION. Coconut is best on fibre and middling on carbohydrate; the
     * egg is worst on fibre and nearly best on carbohydrate. With the terms comparable, coconut
     * wins; with the carbohydrate term ten times the fibre term, the egg wins, and today it does.
     */
    @Test fun `a food that is best on fibre and middling on carbohydrate beats one with nothing in it`() {
        val order = ranked(rice, dal, egg, coconut)
        assertTrue("coconut must rank above the egg: $order", order.indexOf("fresh_coconut") < order.indexOf("boiled_egg"))
        assertEquals("rice is what the preference argues against: $order", "white_rice", order.last())
    }

    /** The guard on the fix: "positive scores only" under the additive scoring empties this list. Green today. */
    @Test fun `the list is never emptied by the scoring when real food is on it`() {
        val order = ranked(rice, dal, sprouts, coconut)
        assertEquals("every candidate is ranked; the preference orders them, it does not delete them: $order", 4, order.size)
        assertTrue(order.indexOf("fresh_coconut") < order.indexOf("white_rice"))
        assertTrue(order.indexOf("chana_dal_cooked") < order.indexOf("white_rice"))
    }
}
