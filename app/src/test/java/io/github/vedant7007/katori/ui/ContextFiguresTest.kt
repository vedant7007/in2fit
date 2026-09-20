package io.github.vedant7007.katori.ui

import io.github.vedant7007.katori.ui.components.FigureLine
import io.github.vedant7007.katori.ui.components.contextFigures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The screen builds figure rows from the rendered lines the contract hands it today (Ira, 20 Sep;
 * the parts field is asked for). These are the shapes `ContextText` writes, verbatim from
 * `res/values/strings.xml`; a line that is not a list of figures must come back null so it is
 * shown as the sentence it is, never as a wrong row.
 */
class ContextFiguresTest {

    @Test
    fun `a period line becomes a heading and one row per figure`() {
        val parsed = contextFigures("Today so far: energy: 459.8 kcal; protein: 15.9 g; vitamin B12: 0 µg; sodium: 615 mg")
        assertEquals("Today so far", parsed!!.first)
        assertEquals(listOf("energy", "protein", "vitamin B12", "sodium"), parsed.second.map { it.name })
        assertEquals(listOf("459.8 kcal", "15.9 g", "0 µg", "615 mg"), parsed.second.map { it.value })
    }

    @Test
    fun `a lab line and a meal line are sentences, not rows`() {
        assertNull(contextFigures("Haemoglobin: 9.8 g/dL, printed range 12 to 15 (report dated 2026-09-12)"))
        assertNull(contextFigures("Saturday 20 Sep, 1:10 pm: roti, dal. iron: 2.5 mg; protein: at least 11 g (no value for dal)"))
        assertNull(contextFigures("No meals logged today"))
    }

    @Test
    fun `a figure line splits at the first colon and keeps the unit with the number`() {
        assertEquals(FigureLine("protein", "15.9 g", null), FigureLine.fromRendered("protein: 15.9 g"))
        assertEquals(FigureLine("protein", "not known", null), FigureLine.fromRendered("protein: not known"))
    }
}
