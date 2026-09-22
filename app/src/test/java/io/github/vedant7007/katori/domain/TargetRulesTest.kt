package io.github.vedant7007.katori.domain

import io.github.vedant7007.katori.domain.model.Nutrient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The day's targets: deterministic, from the profile, every figure traceable to a row in the
 * knowledge file, and null rather than a guess when the profile lacks what the rule needs.
 */
class TargetRulesTest {

    private val now = Instant.parse("2026-09-26T07:30:00Z")
    /** The demo person: 19, 62 kg, 172 cm, male, hostel student, maintaining. */
    private val demo = ProfileSnapshot(19, 62.0, 172.0, Sex.MALE, Goal.MAINTAIN, LifeContext.HOSTEL_STUDENT, DietType.VEGETARIAN, emptySet())

    @Test fun `the demo person's targets, and where each number comes from`() {
        val t = TargetRules.targetsFor(demo, "SEDENTARY", 1L, now)
        assertNotNull(t); t!!
        // 2110 kcal for the 65 kg reference man, sedentary (ICMR-NIN 2020 Table 1a) = 32.46 kcal/kg; at 62 kg = 2012.6, to the nearest 10
        assertEquals(2010.0, t.energyKcal, 0.0)
        // 0.83 g/kg (ICMR-NIN 2020 RDA) at 62 kg = 51.46
        assertEquals(51.0, t.proteinG, 0.0)
        // WHO: fat at most 30% of energy, at 9 kcal/g: 0.30 * 2010 / 9 = 67
        assertEquals(67.0, t.fatG, 0.0)
        // the remainder at 4 kcal/g: (2010 - 51*4 - 67*9) / 4 = 300.75
        assertEquals(301.0, t.carbohydrateG, 0.0)
        assertEquals("no sourced water target", 0.0, t.waterMl, 0.0)
        assertTrue(t.rule.contains("ICMR-NIN 2020") && t.rule.contains("2110 kcal") && t.rule.contains("62 kg") && t.rule.contains("0.83 g per kg") && t.rule.contains("WHO 30%"))
        assertEquals(1L, t.profileVersionMs)
    }

    @Test fun `a woman's reference row is the other table`() {
        val t = TargetRules.targetsFor(demo.copy(sex = Sex.FEMALE, weightKg = 55.0), "MODERATE", 2L, now)!!
        assertEquals("2130 kcal for the 55 kg reference woman, moderate work", 2130.0, t.energyKcal, 0.0)
        assertTrue(t.rule.contains("woman"))
    }

    @Test fun `heavier and lighter people scale by weight, as the tables do`() {
        val heavy = TargetRules.targetsFor(demo.copy(weightKg = 80.0), "SEDENTARY", 1L, now)!!
        val light = TargetRules.targetsFor(demo.copy(weightKg = 50.0), "SEDENTARY", 1L, now)!!
        assertEquals(2600.0, heavy.energyKcal, 0.0)
        assertEquals(1620.0, light.energyKcal, 0.0)
        assertTrue(heavy.proteinG > light.proteinG)
    }

    /** Never a guess: what the rule needs and does not have is null, not a default. */
    @Test fun `no target without weight, a stated sex, an activity, or an adult age`() {
        assertNull(TargetRules.targetsFor(demo.copy(weightKg = null), "SEDENTARY", 1L, now))
        assertNull(TargetRules.targetsFor(demo.copy(sex = Sex.NOT_STATED), "SEDENTARY", 1L, now))
        assertNull(TargetRules.targetsFor(demo.copy(sex = Sex.OTHER), "SEDENTARY", 1L, now))
        assertNull(TargetRules.targetsFor(demo, null, 1L, now))
        assertNull(TargetRules.targetsFor(demo, "ACTIVE", 1L, now))
        assertNull(TargetRules.targetsFor(demo.copy(ageYears = 16), "SEDENTARY", 1L, now))
        assertNull(TargetRules.targetsFor(demo.copy(ageYears = null), "SEDENTARY", 1L, now))
    }

    /** A weight-loss goal gets the maintenance requirement and says so; the file has no sourced deficit. */
    @Test fun `a goal to lose or gain weight does not invent a deficit`() {
        val maintain = TargetRules.targetsFor(demo, "SEDENTARY", 1L, now)!!
        val lose = TargetRules.targetsFor(demo.copy(goal = Goal.LOSE_WEIGHT), "SEDENTARY", 1L, now)!!
        assertEquals(maintain.energyKcal, lose.energyKcal, 0.0)
        assertTrue(lose.rule.contains("maintenance requirement"))
        assertTrue(!maintain.rule.contains("maintenance requirement"))
    }

    @Test fun `progress reads today's totals against the targets with the rule's own bands`() {
        val t = TargetRules.targetsFor(demo, "SEDENTARY", 1L, now)!!
        val p = TargetRules.progress(t, mapOf(Nutrient.ENERGY to 1233.1, Nutrient.PROTEIN to 48.8, Nutrient.FAT to 44.6))
        val byNutrient = p.associateBy { it.nutrient }
        assertEquals(setOf(Nutrient.ENERGY, Nutrient.PROTEIN, Nutrient.CARBOHYDRATE, Nutrient.FAT), byNutrient.keys)
        assertEquals(TargetStatus.UNDER, byNutrient.getValue(Nutrient.ENERGY).status)
        assertEquals(2010.0 - 1233.1, byNutrient.getValue(Nutrient.ENERGY).remaining, 0.01)
        assertEquals(TargetStatus.WITHIN, byNutrient.getValue(Nutrient.PROTEIN).status)   // 48.8 of 51 is 96%
        assertEquals(TargetStatus.WITHIN, byNutrient.getValue(Nutrient.FAT).status)        // a ceiling: under it is fine
        assertEquals(TargetStatus.UNDER, byNutrient.getValue(Nutrient.CARBOHYDRATE).status)  // nothing counted yet
        assertEquals(0.0, byNutrient.getValue(Nutrient.CARBOHYDRATE).consumed, 0.0)
        val over = TargetRules.progress(t, mapOf(Nutrient.FAT to 80.0, Nutrient.ENERGY to 2300.0))
        assertEquals(TargetStatus.OVER, over.single { it.nutrient == Nutrient.FAT }.status)
        assertEquals(TargetStatus.OVER, over.single { it.nutrient == Nutrient.ENERGY }.status)
        assertEquals(0.0, over.single { it.nutrient == Nutrient.ENERGY }.remaining, 0.0)
    }

    /** The demo person is sedentary; the seed writes the name and the rule reads it back. */
    @Test fun `the activity level names round-trip as stored`() {
        assertEquals("SEDENTARY", ActivityLevel.SEDENTARY.name)
        assertNotNull(TargetRules.targetsFor(demo, ActivityLevel.SEDENTARY.name, 1L, now))
        assertTrue(ActivityLevel.entries.all { it.plainWords.isNotBlank() })
    }
}
