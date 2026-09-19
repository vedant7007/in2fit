package io.github.vedant7007.katori.domain

import io.github.vedant7007.katori.domain.model.ConfidenceBand
import io.github.vedant7007.katori.domain.model.Nutrient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * The rules engine carries demo beat 4, so these tests are about the properties the demo claims,
 * not about coverage for its own sake.
 */
class RulesEngineTest {

    private val engine = DefaultRulesEngine()

    private fun candidate(code: String, vararg n: Pair<Nutrient, Double>) = CandidateFood(
        foodCode = code,
        displayName = code.replace('_', ' '),
        contexts = setOf(LifeContext.HOSTEL_STUDENT),
        nutrientsPer100g = n.toMap(),
    )

    private val candidates = listOf(
        candidate("sprouts", Nutrient.FIBRE to 6.0, Nutrient.CARBOHYDRATE to 20.0, Nutrient.IRON to 2.6),
        candidate("boiled_egg", Nutrient.FIBRE to 0.0, Nutrient.CARBOHYDRATE to 1.1, Nutrient.IRON to 1.2),
        candidate("white_rice", Nutrient.FIBRE to 0.4, Nutrient.CARBOHYDRATE to 28.0, Nutrient.IRON to 0.2),
    )

    private fun input(
        labs: List<LabValue> = emptyList(),
        conditions: List<DeclaredCondition> = emptyList(),
        avoided: Set<String> = emptySet(),
        meal: MealSnapshot? = null,
        at: Instant = Instant.parse("2026-09-19T08:00:00Z"),
    ) = RuleInput(
        profile = ProfileSnapshot(
            ageYears = 19, weightKg = 62.0, heightCm = 172.0,
            sex = Sex.MALE, goal = Goal.MAINTAIN,
            context = LifeContext.HOSTEL_STUDENT, dietType = DietType.VEGETARIAN,
            avoidedFoodCodes = avoided,
        ),
        declaredConditions = conditions,
        labValues = labs,
        meal = meal,
        candidates = candidates,
        evaluatedAt = at,
    )

    private fun glucoseAbove(value: Double = 142.0) = LabValue(
        testName = "Fasting glucose", value = value, unit = "mg/dL",
        referenceLow = 70.0, referenceHigh = 100.0, reportDate = LocalDate.parse("2026-09-12"),
    )

    // --- THE REQUIRED TEST -------------------------------------------------------------------

    @Test
    fun `identical input at two different times yields an identical digest and evaluation`() {
        val a = engine.evaluate(input(labs = listOf(glucoseAbove()), at = Instant.parse("2026-09-19T08:00:00Z")))
        val b = engine.evaluate(input(labs = listOf(glucoseAbove()), at = Instant.parse("2027-01-01T23:59:59Z")))

        assertEquals("the clock must not reach the digest", a.inputDigest, b.inputDigest)
        assertEquals(a.firedRules, b.firedRules)
        assertEquals(a.constraints, b.constraints)
        assertEquals(a.rankedCandidates, b.rankedCandidates)
        assertEquals(a.trigger, b.trigger)
    }

    @Test
    fun `a changed lab value changes the digest`() {
        val before = engine.evaluate(input())
        val after = engine.evaluate(input(labs = listOf(glucoseAbove())))
        assertNotEquals(
            "a digest that does not move when the data moves proves nothing at beat 4",
            before.inputDigest, after.inputDigest,
        )
    }

    @Test
    fun `evaluation is repeatable and ordering is stable`() {
        val i = input(labs = listOf(glucoseAbove()))
        val runs = (1..20).map { engine.evaluate(i) }
        runs.forEach { assertEquals(runs.first(), it) }
    }

    // --- safety ------------------------------------------------------------------------------

    @Test
    fun `a value far outside its range escalates and emits no dietary suggestion`() {
        val r = engine.evaluate(input(labs = listOf(glucoseAbove(260.0))))

        assertTrue(r.firedRules.all { it.severity == Severity.ESCALATE })
        assertTrue("escalation must not carry swaps", r.rankedCandidates.isEmpty())
        assertTrue("escalation must not carry constraints", r.constraints.isEmpty())
        assertTrue(r.trigger!!.text.contains("worth showing to a doctor"))
    }

    @Test
    fun `no template ever names a disease the user did not declare`() {
        val banned = listOf("diabetes", "diabetic", "prediabetes", "anaemia", "anemia",
            "hypertension", "disease", "disorder", "syndrome", "deficiency")
        val outputs = listOf(
            engine.evaluate(input(labs = listOf(glucoseAbove()))),
            engine.evaluate(input(labs = listOf(glucoseAbove(260.0)))),
            engine.evaluate(input(labs = listOf(LabValue("Haemoglobin", 9.0, "g/dL", 13.0, 17.0, LocalDate.parse("2026-09-12"))))),
            engine.evaluate(input(labs = listOf(LabValue("Vitamin B12", 140.0, "pg/mL", 200.0, 900.0, LocalDate.parse("2026-09-12"))))),
        ).mapNotNull { it.trigger?.text }

        assertTrue("expected some trigger sentences to check", outputs.isNotEmpty())
        outputs.forEach { text ->
            banned.forEach { word ->
                assertTrue("trigger names '$word': $text", !text.lowercase().contains(word))
            }
        }
    }

    @Test
    fun `a declared condition may be named back to the user because they said it`() {
        val r = engine.evaluate(input(conditions = listOf(DeclaredCondition("Diabetes", ConditionSource.USER_DECLARED))))
        assertTrue(r.trigger!!.text.contains("you told us", ignoreCase = true))
    }

    @Test
    fun `an avoided food is never suggested, whatever else fires`() {
        val r = engine.evaluate(input(labs = listOf(glucoseAbove()), avoided = setOf("sprouts")))
        assertTrue("an excluded food came back", r.rankedCandidates.none { it.candidate.foodCode == "sprouts" })
    }

    @Test
    fun `a lab value with no printed range fires nothing`() {
        val noRange = LabValue("Fasting glucose", 142.0, "mg/dL", null, null, LocalDate.parse("2026-09-12"))
        val r = engine.evaluate(input(labs = listOf(noRange)))
        assertTrue(r.firedRules.none { it.id == RuleIds.LAB_ABOVE_RANGE })
    }

    @Test
    fun `an unreviewed marker produces no nutrient preferences`() {
        val odd = LabValue("Serum widget", 99.0, "u/L", 1.0, 10.0, LocalDate.parse("2026-09-12"))
        val r = engine.evaluate(input(labs = listOf(odd)))
        assertTrue(
            "we must not invent a policy for a marker nobody reviewed",
            r.constraints.filterIsInstance<Constraint.PreferNutrient>().isEmpty(),
        )
    }

    @Test
    fun `direction matters, a low glucose does not rank like a high one`() {
        val low = LabValue("Fasting glucose", 50.0, "mg/dL", 70.0, 100.0, LocalDate.parse("2026-09-12"))
        val r = engine.evaluate(input(labs = listOf(low)))
        assertTrue(
            "a below-range glucose must not trigger carbohydrate-lowering preferences",
            r.constraints.filterIsInstance<Constraint.PreferNutrient>().isEmpty(),
        )
    }

    @Test
    fun `an iron marker responds to a low value, not a high one`() {
        val lowHb = LabValue("Haemoglobin", 9.0, "g/dL", 13.0, 17.0, LocalDate.parse("2026-09-12"))
        val r = engine.evaluate(input(labs = listOf(lowHb)))
        val prefs = r.constraints.filterIsInstance<Constraint.PreferNutrient>()
        assertTrue(prefs.any { it.nutrient == Nutrient.IRON && it.direction == Constraint.PreferNutrient.Direction.HIGHER })
    }

    // --- beat 4 ---------------------------------------------------------------------------------

    @Test
    fun `the same meal ranks differently once a report lands, and says why`() {
        val meal = MealSnapshot(
            mealId = 1,
            items = listOf(
                MealItemSnapshot("rice", "rice_cooked", 200.0, mapOf(Nutrient.CARBOHYDRATE to 56.0)),
                MealItemSnapshot("dal", "toor_dal_cooked", 150.0, mapOf(Nutrient.CARBOHYDRATE to 12.0)),
            ),
            loggedAt = Instant.parse("2026-09-19T07:00:00Z"),
        )
        val before = engine.evaluate(input(meal = meal))
        val after = engine.evaluate(input(meal = meal, labs = listOf(glucoseAbove())))

        assertNotEquals(before.inputDigest, after.inputDigest)

        // Before the report there is no rule-driven reason to prefer any food, so there are no
        // suggestions at all. Afterwards there are, ranked, with a sentence explaining why.
        assertTrue(
            "with nothing to rank by there must be no suggestions, not an arbitrary order",
            before.rankedCandidates.isEmpty(),
        )
        assertTrue("the report must produce suggestions", after.rankedCandidates.isNotEmpty())
        assertTrue("before the report there is nothing to explain", before.trigger == null)

        // The ordering must be driven by the constraint, not by the tiebreak. Sprouts carry more
        // fibre and less carbohydrate than white rice, so they must rank above it.
        val order = after.rankedCandidates.map { it.candidate.foodCode }
        assertTrue(
            "ranking must reflect the fired constraint, got \$order",
            order.indexOf("sprouts") < order.indexOf("white_rice"),
        )
        val t = after.trigger!!.text
        assertTrue("the trigger must cite the report date", t.contains("2026-09-12"))
        assertTrue("the trigger must cite the printed range", t.contains("100"))
        assertTrue("the trigger must cite the value", t.contains("142"))
    }

    @Test
    fun `with no data at all nothing fires and there is no trigger`() {
        val bare = RuleInput(
            profile = ProfileSnapshot(null, null, null, null, null, null, null, emptySet()),
            declaredConditions = emptyList(), labValues = emptyList(), meal = null,
            candidates = emptyList(), evaluatedAt = Instant.parse("2026-09-19T08:00:00Z"),
        )
        val r = engine.evaluate(bare)
        assertTrue(r.firedRules.isEmpty())
        assertTrue(r.rankedCandidates.isEmpty())
        assertNull(r.trigger)
    }

    @Test
    fun `ranked candidates carry a confidence band and never a percentage`() {
        val r = engine.evaluate(input(labs = listOf(glucoseAbove())))
        r.rankedCandidates.forEach {
            assertTrue(it.confidence.band in ConfidenceBand.entries)
            assertTrue(it.confidence.reasons.isNotEmpty())
        }
    }

    @Test
    fun `with no rule fired there are no suggestions at all`() {
        val r = engine.evaluate(input())
        assertTrue(
            "an ordering with no basis is as bad as an invented number",
            r.rankedCandidates.isEmpty(),
        )
    }

    @Test
    fun `an escalation never carries suggestions even when other rules would have fired`() {
        val r = engine.evaluate(
            input(
                labs = listOf(glucoseAbove(260.0)),
                conditions = listOf(DeclaredCondition("Anaemia", ConditionSource.USER_DECLARED)),
            )
        )
        assertTrue(r.rankedCandidates.isEmpty())
        assertTrue(r.firedRules.all { it.severity == Severity.ESCALATE })
    }
}
