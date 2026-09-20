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

    /**
     * The engine emits a template id and evidence, never words. These tests check the WORDS,
     * because the safety rules (no undeclared disease named, the printed range cited, the referral
     * present) are properties of the sentence a person reads, so they are asserted on the rendered
     * English defaults. A locale file is reviewed by a speaker; this is what it is reviewed against.
     */
    private val words = TriggerText(TriggerText.ENGLISH)
    private fun RuleEvaluation.triggerText(): String? = trigger?.let { words.render(it) }


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
    fun `a value far outside its range makes a referral mandatory, alongside the help`() {
        // 0015: referral ALONGSIDE help, not instead of it. The old rule suppressed every
        // suggestion the moment a value was far outside range, which made the app useless at
        // exactly the moment someone needed it most.
        val r = engine.evaluate(input(labs = listOf(glucoseAbove(260.0))))

        assertTrue("the referral must be flagged as required", r.referralRequired)
        assertTrue(r.firedRules.any { it.severity == Severity.ESCALATE })
        assertEquals("the referral sentence wins the trigger", TriggerTemplate.ESCALATE_ABOVE_RANGE, r.trigger!!.template)
        assertTrue(r.triggerText()!!.contains("worth showing to a doctor"))

        // AND the same value still adjusts the ranking, exactly as a milder reading would.
        assertTrue("a far-above glucose must still fire the ordinary above-range rule",
            r.firedRules.any { it.id == RuleIds.LAB_ABOVE_RANGE })
        assertTrue("the help must not be suppressed", r.constraints.isNotEmpty())
        assertTrue("the help must not be suppressed", r.rankedCandidates.isNotEmpty())
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
        ).mapNotNull { it.triggerText() }

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
        assertEquals(TriggerTemplate.DECLARED_CONDITION, r.trigger!!.template)
        assertTrue(r.triggerText()!!.contains("you told us", ignoreCase = true))
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
        // fibre and less carbohydrate than white rice, so they are suggested; and white rice,
        // which the constraint argues against, is not a suggestion at all (RankingDefectsTest):
        // a food the preferences score below zero does not appear under the sentence that
        // explains why the list changed.
        val order = after.rankedCandidates.map { it.candidate.foodCode }
        assertTrue("ranking must reflect the fired constraint, got $order", order.first() == "sprouts")
        assertTrue("a food the constraint argues against is not offered, got $order", "white_rice" !in order)
        assertEquals(TriggerTemplate.LAB_ABOVE_RANGE, after.trigger!!.template)
        val t = after.triggerText()!!
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

    /** Ruled 20 Sep: the first end-to-end run ranked cumin, turmeric, bay leaf and fenugreek top for iron. Wrong denominator. */
    @Test
    fun `candidates rank by nutrient per serving, so a spice never tops a green on iron`() {
        val cumin = CandidateFood("cumin", "cumin seed", setOf(LifeContext.HOSTEL_STUDENT), mapOf(Nutrient.IRON to 66.4), servingGrams = 2.0)
        val palak = CandidateFood("palak", "spinach", setOf(LifeContext.HOSTEL_STUDENT), mapOf(Nutrient.IRON to 2.7), servingGrams = 100.0)
        val r = engine.evaluate(
            RuleInput(
                profile = ProfileSnapshot(19, 62.0, 172.0, Sex.MALE, Goal.MAINTAIN, LifeContext.HOSTEL_STUDENT, DietType.VEGETARIAN, emptySet()),
                declaredConditions = emptyList(),
                labValues = listOf(LabValue("Haemoglobin", 9.8, "g/dL", 12.0, 15.0, LocalDate.parse("2026-09-12"))),
                meal = null, candidates = listOf(cumin, palak), evaluatedAt = Instant.parse("2026-09-19T08:00:00Z"),
            )
        )
        assertEquals(listOf("palak", "cumin"), r.rankedCandidates.map { it.candidate.foodCode })
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
    fun `the referral is never dropped when other rules fire too`() {
        // The failure this guards against is the mirror of the old one: with several rules
        // competing for the trigger, the referral must still be the sentence shown.
        val r = engine.evaluate(
            input(
                labs = listOf(glucoseAbove(260.0)),
                conditions = listOf(DeclaredCondition("Anaemia", ConditionSource.USER_DECLARED)),
            )
        )
        assertTrue(r.referralRequired)
        assertEquals(TriggerTemplate.ESCALATE_ABOVE_RANGE, r.trigger!!.template)
        assertTrue("the declared condition still contributes its help",
            r.firedRules.any { it.id == RuleIds.DECLARED_CONDITION })
        assertTrue(r.rankedCandidates.isNotEmpty())
    }

    // --- the rendered sentences ---------------------------------------------------------------

    /**
     * Every template renders, and no rendered sentence carries a digit the evidence did not
     * supply. The second half is the property NumericGuard relies on when the sentence is handed
     * to the model as a permitted source of figures: a template that smuggled in a number of its
     * own would licence that number in generated prose. This runs against the English defaults;
     * a locale file needs the same check against its own wording.
     */
    @Test
    fun `every template renders and cites only the evidence's own numbers`() {
        val lab = Evidence.LabValueOutsideRange("Glucose", 142.0, "mg/dL", 70.0, 100.0, LocalDate.parse("2026-09-12"))
        val low = Evidence.LabValueOutsideRange("Haemoglobin", 9.8, "g/dL", 12.0, 15.0, LocalDate.parse("2026-09-12"))
        val samples = mapOf(
            TriggerTemplate.ESCALATE_ABOVE_RANGE to lab,
            TriggerTemplate.ESCALATE_BELOW_RANGE to low,
            TriggerTemplate.LAB_ABOVE_RANGE to lab,
            TriggerTemplate.LAB_BELOW_RANGE to low,
            TriggerTemplate.DECLARED_CONDITION to Evidence.UserDeclaredCondition(DeclaredCondition("Diabetes", ConditionSource.USER_DECLARED)),
            // VITAMIN_B12 on purpose (0028): "vitamin B12" must render, and its 12 must not read as a figure.
            TriggerTemplate.MEAL_COMPOSITION to Evidence.MealComposition(nutrient = Nutrient.VITAMIN_B12, shareOfMeal = 0.62, dominantItem = "curd"),
            TriggerTemplate.LIFE_CONTEXT to Evidence.ProfileContext(LifeContext.HOSTEL_STUDENT),
            TriggerTemplate.TIMELINE to Evidence.TimelinePattern(description = "iron has been low on most days", daysObserved = 7),
        )
        assertEquals("every template needs a sample here", TriggerTemplate.values().toSet(), samples.keys)

        // 0028: a digit run glued to a letter on either side is a word ("B12"), not a figure. The
        // same boundary DefaultNumericGuard applies; stated once, used here as there.
        val digits = Regex("""(?<![\p{L}\d])\d+(?:\.\d+)?(?![\p{L}\d])""")
        samples.forEach { (template, evidence) ->
            val text = words.render(TriggerStatement(RuleIds.LAB_ABOVE_RANGE, template, evidence))
            assertTrue("$template rendered empty", text.isNotBlank())
            val supplied = evidenceNumbers(evidence)
            digits.findAll(text).map { it.value }.forEach { n ->
                assertTrue(
                    "$template cites '$n', which is not in the evidence $supplied: $text",
                    n in supplied,
                )
            }
        }
    }

    /** Every number the evidence could legitimately put in a sentence, as the renderer formats it. */
    private fun evidenceNumbers(e: Evidence): Set<String> = when (e) {
        is Evidence.LabValueOutsideRange -> setOfNotNull(
            e.value, e.referenceLow, e.referenceHigh,
        ).flatMap { v -> listOf(v.toString(), v.toLong().toString(), String.format("%.1f", v)) }.toSet() +
            e.reportDate.toString().split("-").toSet() + e.reportDate.toString()
        is Evidence.MealComposition -> setOf((e.shareOfMeal * 100).toInt().toString())
        is Evidence.TimelinePattern -> setOf(e.daysObserved.toString()) +
            Regex("""\d+""").findAll(e.description).map { it.value }.toSet()
        is Evidence.UserDeclaredCondition, is Evidence.ProfileContext -> emptySet()
    }

}
