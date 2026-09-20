package io.github.vedant7007.katori.ml.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The guard that stops the model inventing a health figure.
 *
 * These tests are written as the attack it is defending against, not as a feature list. The
 * failure being prevented is a fluent sentence with a fabricated number in it, which is the most
 * convincing wrong output this app can produce.
 */
class NumericGuardTest {

    private val guard = DefaultNumericGuard()

    /** The figures a phrasing call would realistically be handed. */
    private val given = listOf(
        "about 320 kcal",
        "12 g protein",
        "2.5 mg iron",
        "0.4 µg vitamin B12",
        "You have had 2 servings of dal today.",
        "toor dal",
        "palak",
    )

    private fun check(output: String) = guard.firstInventedNumber(output, given)

    // --- the inventions -------------------------------------------------------------------

    @Test fun `a number the model was never given is rejected`() {
        assertEquals("450", check("That comes to roughly 450 calories."))
    }

    @Test fun `the first invented number is the one reported`() {
        assertEquals("450", check("Roughly 450 kcal, or 900 for two."))
    }

    @Test fun `a plausible near miss is still an invention`() {
        assertEquals("321", check("That is about 321 kcal."))
    }

    @Test fun `an invented percentage is rejected`() {
        assertEquals("80", check("You have met 80% of your iron target."))
    }

    @Test fun `arithmetic on the figures it was given is still an invention`() {
        // 320 and 12 were given; 332 was not. This is the model doing sums, which it must not.
        assertEquals("332", check("Together that is 332."))
    }

    /**
     * The hole worth being strict about. "B12" contains a 12, and a looser guard would let that
     * licence a free-standing 12 anywhere in the sentence.
     */
    @Test fun `a number seen only glued to letters does not licence a bare one`() {
        val onlyB12 = listOf("0.4 µg vitamin B12", "toor dal")
        assertEquals("12", guard.firstInventedNumber("You need 12 more of those.", onlyB12))
        assertNull(guard.firstInventedNumber("Your B12 is low.", onlyB12))
    }

    // --- the correct outputs that must not be rejected -------------------------------------

    @Test fun `repeating the figures it was given passes`() {
        assertNull(check("Today's dal came to about 320 kcal with 12 g protein."))
    }

    @Test fun `no numbers at all passes`() {
        assertNull(check("Palak would add iron to this meal."))
    }

    @Test fun `spacing between the number and its unit does not matter`() {
        // "12 g" in, "12g" out. Same number; rejecting it would make the guard fire on correct
        // output often enough that somebody would eventually be tempted to weaken it.
        assertNull(check("That is 12g of protein."))
    }

    @Test fun `trailing punctuation is not swallowed into the number`() {
        assertNull(check("It is 320."))
        assertNull(check("Protein was 12, iron 2.5."))
    }

    @Test fun `a decimal keeps its value and trailing zeros do not matter`() {
        assertNull(check("Iron came to 2.50 mg."))
        assertEquals("2.6", check("Iron came to 2.6 mg."))
    }

    @Test fun `telugu and devanagari numerals for a given figure pass`() {
        // The demo language is Telugu. A model answering in Telugu script with the figure it was
        // handed has not invented anything.
        assertNull(check("\u0C69\u0C68\u0C66 \u0C15\u0C46.\u0C15\u0C3E."))   // 320 in Telugu digits
        assertNull(check("\u0967\u0968 \u0917\u094D\u0930\u093E\u092E"))      // 12 in Devanagari digits
    }

    @Test fun `telugu numerals for a figure it was not given are still rejected`() {
        val fourFiveZero = "\u0C6A\u0C6B\u0C66"  // 450 in Telugu digits
        assertEquals(fourFiveZero, check(fourFiveZero))
    }

    @Test fun `a thousands separator does not change the value`() {
        val big = listOf("1,200 kcal")
        assertNull(guard.firstInventedNumber("That is 1200 kcal.", big))
        assertNull(guard.firstInventedNumber("That is 1,200 kcal.", big))
        assertEquals("1300", guard.firstInventedNumber("That is 1300 kcal.", big))
    }

    @Test fun `a fraction only matches the same fraction and never a bare digit`() {
        val half = listOf("1/2 katori")
        assertNull(guard.firstInventedNumber("Have 1/2 a katori.", half))
        // The 1 and the 2 inside "1/2" must not licence a standalone 2.
        assertEquals("2", guard.firstInventedNumber("Have 2 katoris.", half))
    }

    @Test fun `an empty permitted list rejects any number at all`() {
        assertEquals("5", guard.firstInventedNumber("Add 5 grams.", emptyList()))
        assertNull(guard.firstInventedNumber("Add a little more.", emptyList()))
    }

    @Test fun `words for numbers are out of scope and pass, as documented`() {
        // Documented limitation, asserted so nobody later believes it is covered. Spec 15's
        // string review is what handles phrasing like this, not the digit guard.
        assertNull(check("Have two more katoris."))
    }

    @Test fun `the guard is pure and repeats itself`() {
        val out = "That comes to roughly 450 calories."
        assertEquals(check(out), check(out))
    }

    // --- the unit travels with the value (0024, Priya's finding) ----------------------------

    @Test fun `a value shown in one unit does not licence the same value in another`() {
        val g = DefaultNumericGuard()
        assertEquals("18 mg", g.firstInventedNumber("about 18 mg a day", listOf("Absorption rises from 14% to 18% with vitamin C.")))
        assertNull(g.firstInventedNumber("absorption rises to 18% with vitamin C", listOf("Absorption rises from 14% to 18% with vitamin C.")))
        assertNull("a bare value the input showed with a unit is not an invention", g.firstInventedNumber("about 18 with vitamin C", listOf("14% to 18%")))
    }

    @Test fun `unit spellings agree and non-units are not units`() {
        val g = DefaultNumericGuard()
        assertNull(g.firstInventedNumber("42g of protein", listOf("protein: 42 g")))
        assertNull(g.firstInventedNumber("42 grams of protein", listOf("protein: 42 g")))
        assertNull(g.firstInventedNumber("320 calories", listOf("energy: 320 kcal")))
        assertNull(g.firstInventedNumber("your haemoglobin of 9.8 g/dL", listOf("Haemoglobin: 9.8 g/dL, printed range 12 to 15")))
        assertEquals("42 mg", g.firstInventedNumber("42 mg of protein", listOf("protein: 42 g")))
        assertNull("rotis is not a unit; the 2 is the person's own", g.firstInventedNumber("2 rotis", listOf("I had 2 rotis")))
        assertEquals("a bare figure given, a unit the model added, is an invention", "12 g", g.firstInventedNumber("about 12 g", listOf("printed range 12 to 15")))
    }

}
