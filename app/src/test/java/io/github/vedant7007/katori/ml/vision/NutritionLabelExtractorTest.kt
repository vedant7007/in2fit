package io.github.vedant7007.katori.ml.vision

import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.NutrientUnit
import io.github.vedant7007.katori.domain.model.NutrientValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Measures [NutritionLabelExtractor] the way `LabReportExtractorTest` measures the lab parser:
 * whole panels in the layouts ML Kit is expected to return, then a corpus of single rows with a
 * printed table, and the numbers that must be zero asserted at zero.
 *
 * THE NUMBERS THAT MATTER ARE WRONG VALUE AND WRONG BASIS. A panel prints per 100 g and per
 * serving side by side; the wrong column is a silent factor of two that looks reasonable, which
 * is the biryani-to-bay-leaf shape. A missed row is silence and the person types it in.
 *
 * THE CORPUS IS AUTHORED. Every panel and row here is what an FSSAI-format pack looks like after
 * ML Kit has read it, as far as anyone on this project knows. Zero real OCR observations of a
 * nutrition panel exist. This is a regression guard over the parser; the OCR measurement is the
 * instrumented probe, and until Rao runs it nothing here says a photographed pack reads.
 */
class NutritionLabelExtractorTest {

    // --- whole panels ----------------------------------------------------------------------------

    /** The FSSAI two-column panel, every cell its own ML Kit line with a box: the x rule. */
    private fun twoColumnPanelByCells(): RecognisedText {
        val lines = mutableListOf(
            line("NUTRITIONAL INFORMATION", 40, 20),
            line("Per 100 g", 480, 80), line("Per serve (30 g)", 700, 80), line("%RDA per serve", 940, 80),
        )
        val rows = listOf(
            listOf("Energy (kcal)", "520", "156", "7.8%"),
            listOf("Protein (g)", "7.2", "2.2", "4.4%"),
            listOf("Carbohydrate (g)", "58.0", "17.4", "6.7%"),
            listOf("of which Sugars (g)", "2.1", "0.6", null),
            listOf("Added Sugars (g)", "0", null, null),
            listOf("Total Fat (g)", "29.0", "8.7", "13.0%"),
            listOf("Saturated Fat (g)", "13.0", "3.9", "17.7%"),
            listOf("Trans Fat (g)", "0", "0", null),
            listOf("Cholesterol (mg)", "0", "0", null),
            listOf("Sodium (mg)", "650", "195", "9.8%"),
            listOf("Dietary Fibre (g)", "3.1", "0.9", null),
        )
        rows.forEachIndexed { i, r ->
            val y = 140 + i * 50
            lines += line(r[0]!!, 40, y)
            r[1]?.let { lines += line(it, 500, y) }
            r[2]?.let { lines += line(it, 740, y) }
            r[3]?.let { lines += line(it, 960, y) }
        }
        return RecognisedText(lines)
    }

    /** The same panel with every row read as ONE line: the order-and-count rule. */
    private fun twoColumnPanelByRows(): RecognisedText = RecognisedText(listOf(
        line("NUTRITIONAL INFORMATION", 40, 20),
        line("Per 100 g   Per serve (30 g)   %RDA per serve", 480, 80),
        line("Energy (kcal)   520   156   7.8%", 40, 140),
        line("Protein (g)   7.2   2.2   4.4%", 40, 190),
        line("Carbohydrate (g)   58.0   17.4   6.7%", 40, 240),
        line("of which Sugars (g)   2.1   0.6", 40, 290),
        line("Added Sugars (g)   0", 40, 340),
        line("Total Fat (g)   29.0   8.7   13.0%", 40, 390),
        line("Saturated Fat (g)   13.0   3.9   17.7%", 40, 440),
        line("Sodium (mg)   650   195   9.8%", 40, 490),
        line("Dietary Fibre (g)   3.1   0.9", 40, 540),
    ))

    private val expectedPer100 = mapOf(
        Nutrient.ENERGY to 520.0, Nutrient.PROTEIN to 7.2, Nutrient.CARBOHYDRATE to 58.0,
        Nutrient.FAT to 29.0, Nutrient.SODIUM to 650.0, Nutrient.FIBRE to 3.1,
    )

    @Test
    fun `two columns as separate cells, per 100 g is taken and saturated fat never becomes fat`() {
        val label = NutritionLabelExtractor.extract(twoColumnPanelByCells())
        assertEquals(LabelBasis.Per100("g"), label.basis)
        assertEquals(expectedPer100, label.nutrients.mapValues { it.value.amount })
        assertEquals(NutrientUnit.MILLIGRAM, label.nutrients.getValue(Nutrient.SODIUM).unit)
        // Every printed row survives as a row, in the per-100 column, including the one-cell row.
        assertEquals(listOf(520.0, 7.2, 58.0, 2.1, 0.0, 29.0, 13.0, 0.0, 0.0, 650.0, 3.1), label.rows.map { it.value })
        assertEquals("Added Sugars (g)", label.rows[4].name)
    }

    @Test
    fun `two columns as single lines, order and count, and a short row is dropped not guessed`() {
        val label = NutritionLabelExtractor.extract(twoColumnPanelByRows())
        assertEquals(LabelBasis.Per100("g"), label.basis)
        assertEquals(expectedPer100, label.nutrients.mapValues { it.value.amount })
        // "Added Sugars (g) 0" has one cell under three columns: it cannot be placed, so it is absent.
        assertNull(label.rows.firstOrNull { it.name.startsWith("Added") })
        assertEquals(2.1, label.rows.single { it.name.startsWith("of which") || it.name.startsWith("Sugars") }.value, 0.0)
    }

    @Test
    fun `per serving only keeps values per serving, and per-100 is arithmetic on the printed serving`() {
        val label = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("Nutrition Facts", 40, 20),
            line("Serving size: 30 g", 40, 60),
            line("Amount per serving", 40, 100),
            line("Energy 156 kcal", 40, 150),
            line("Protein 2.2 g", 40, 200),
            line("Sodium 195 mg", 40, 250),
        )))
        assertEquals(LabelBasis.PerServing(30.0, "g"), label.basis)
        assertEquals(156.0, label.nutrients.getValue(Nutrient.ENERGY).amount, 0.0)
        val per100 = label.nutrientsPer100()
        assertEquals(520.0, per100.getValue(Nutrient.ENERGY).amount, 1e-9)
        assertEquals(650.0, per100.getValue(Nutrient.SODIUM).amount, 1e-9)
    }

    @Test
    fun `per serving with no printed amount cannot become per 100`() {
        val label = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("Per serving", 40, 20), line("Energy 156 kcal", 40, 70),
        )))
        assertEquals(LabelBasis.PerServing(null, null), label.basis)
        assertEquals(156.0, label.nutrients.getValue(Nutrient.ENERGY).amount, 0.0)
        assertTrue(label.nutrientsPer100().isEmpty())
    }

    @Test
    fun `no basis header means no values, even with a net weight and a serving size on the pack`() {
        // The hazard: "Net Wt 100 g" is not a basis, and one unlabelled column of numbers is not
        // per 100 g because the pack weighs 100 g.
        val label = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("Net Wt: 100 g", 40, 20),
            line("Serving size 30 g", 40, 60),
            line("Energy 156 kcal", 40, 110),
            line("Protein 2.2 g", 40, 160),
            line("Ingredients: Wheat flour, Sugar, Palm oil, Salt", 40, 220),
        )))
        assertNull(label.basis)
        assertTrue(label.nutrients.isEmpty())
        assertTrue(label.rows.isEmpty())
        assertEquals("Wheat flour, Sugar, Palm oil, Salt", label.ingredients)
    }

    @Test
    fun `energy printed in kJ and kcal takes the kcal, and kJ alone is no energy`() {
        val both = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("Per 100 g", 400, 20), line("Energy", 40, 70), line("2176 kJ / 520 kcal", 400, 70),
        )))
        assertEquals(520.0, both.nutrients.getValue(Nutrient.ENERGY).amount, 0.0)
        val kj = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("Per 100 g", 400, 20), line("Energy (kJ)", 40, 70), line("2176", 400, 70),
        )))
        assertNull(kj.nutrients[Nutrient.ENERGY])
        assertEquals(1, kj.rows.size)
    }

    @Test
    fun `a less-than value is not a value`() {
        val label = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("Per 100 g", 400, 20), line("Sugars (g)", 40, 70), line("<0.5", 400, 70), line("Protein (g)", 40, 120), line("7.2", 400, 120),
        )))
        assertEquals(listOf("Protein (g)"), label.rows.map { it.name })
    }

    @Test
    fun `units convert by factor and a missing unit stays out of the domain map`() {
        val label = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("Per 100 g", 400, 20),
            line("Sodium", 40, 70), line("0.65 g", 400, 70),
            line("Iron", 40, 120), line("2500 mcg", 400, 120),
            line("Protein", 40, 170), line("7.2", 400, 170),
        )))
        assertEquals(650.0, label.nutrients.getValue(Nutrient.SODIUM).amount, 1e-9)
        assertEquals(2.5, label.nutrients.getValue(Nutrient.IRON).amount, 1e-9)
        assertNull(label.nutrients[Nutrient.PROTEIN])
        assertEquals(LabelRow("Protein", 7.2, null, "Protein 7.2"), label.rows.last())
    }

    @Test
    fun `a title carrying the basis is the header, and a drink is per 100 ml`() {
        val label = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("Nutritional Information (Approx. values per 100 ml)", 40, 20),
            line("Energy 45 kcal", 40, 70), line("Carbohydrate 10.5 g", 40, 120), line("Sugars 10.2 g", 40, 170),
        )))
        assertEquals(LabelBasis.Per100("ml"), label.basis)
        assertEquals(45.0, label.nutrients.getValue(Nutrient.ENERGY).amount, 0.0)
        assertEquals(10.5, label.nutrients.getValue(Nutrient.CARBOHYDRATE).amount, 0.0)
        assertEquals(3, label.rows.size)
    }

    @Test
    fun `ingredients run over rows and stop at the next section`() {
        val label = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("INGREDIENTS: Refined wheat flour (maida), Sugar,", 40, 20),
            line("Edible vegetable oil (palm oil), Invert syrup,", 40, 60),
            line("Raising agents (500(ii), 503(ii)), Salt.", 40, 100),
            line("ALLERGEN INFORMATION: Contains wheat.", 40, 140),
            line("Per 100 g", 400, 200), line("Energy", 40, 250), line("480 kcal", 400, 250),
        )))
        assertEquals(
            "Refined wheat flour (maida), Sugar, Edible vegetable oil (palm oil), Invert syrup, Raising agents (500(ii), 503(ii)), Salt.",
            label.ingredients,
        )
        assertEquals(480.0, label.nutrients.getValue(Nutrient.ENERGY).amount, 0.0)
    }

    @Test
    fun `a header token split over two lines still reads, and the split column keeps no box`() {
        val label = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("Per 100", 480, 80), line("g", 590, 80), line("Per serve (30 g)", 700, 80),
            line("Protein (g)", 40, 140), line("7.2", 500, 140), line("2.2", 740, 140),
        )))
        assertEquals(LabelBasis.Per100("g"), label.basis)
        assertEquals(7.2, label.nutrients.getValue(Nutrient.PROTEIN).amount, 0.0)
    }

    @Test
    fun `a unit after a comma, a trailing unit, and a bracketed unit with a qualifier are all read`() {
        val label = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("Per 100 g", 400, 20),
            line("Total Fat, g", 40, 70), line("29.0", 400, 70),
            line("Sodium mg", 40, 120), line("650", 400, 120),
            line("Energy (kcal/100g)", 40, 170), line("520", 400, 170),
        )))
        assertEquals(29.0, label.nutrients.getValue(Nutrient.FAT).amount, 0.0)
        assertEquals(650.0, label.nutrients.getValue(Nutrient.SODIUM).amount, 0.0)
        assertEquals(520.0, label.nutrients.getValue(Nutrient.ENERGY).amount, 0.0)
        assertEquals(listOf("g", "mg", "kcal"), label.rows.map { it.unit })
    }

    @Test
    fun `a small pack that prints the basis on every row proves it row by row`() {
        val label = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("Energy 520 kcal per 100 g", 40, 20),
            line("Protein 7.2 g per 100 g", 40, 70),
            line("Sodium 650 mg", 40, 120),
        )))
        assertEquals(LabelBasis.Per100("g"), label.basis)
        assertEquals(mapOf(Nutrient.ENERGY to 520.0, Nutrient.PROTEIN to 7.2), label.nutrients.mapValues { it.value.amount })
        // Sodium proved no basis of its own, so it is not read.
        assertNull(label.rows.firstOrNull { it.name == "Sodium" })
    }

    @Test
    fun `rows that prove different bases prove nothing`() {
        val label = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("Energy 520 kcal per 100 g", 40, 20),
            line("Protein 2.2 g per serve", 40, 70),
        )))
        assertNull(label.basis)
        assertTrue(label.rows.isEmpty())
    }

    // Found by probing with panels the corpus was not written against.

    @Test
    fun `a percent column printed left of the basis columns does not swallow the basis`() {
        // Before the fix the "%RDA" token absorbed "Per 100 g", the panel became per-serving, and a
        // one-cell row would have been reported per serving: the factor-of-two this file exists to stop.
        val label = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("%RDA", 300, 80), line("Per 100 g", 480, 80), line("Per serve (30 g)", 700, 80),
            line("Energy (kcal) 7.8% 520 156", 40, 140),
            line("Sodium (mg)", 40, 190), line("650", 500, 190),
        )))
        assertEquals(LabelBasis.Per100("g"), label.basis)
        assertEquals(mapOf(Nutrient.ENERGY to 520.0, Nutrient.SODIUM to 650.0), label.nutrients.mapValues { it.value.amount })
    }

    @Test
    fun `the per-serve column printed first still yields per 100`() {
        val label = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("Per serve (30 g)", 480, 80), line("Per 100 g", 700, 80),
            line("Energy (kcal)", 40, 140), line("156", 500, 140), line("520", 740, 140),
            line("Protein (g) 2.2 7.2", 40, 190),
        )))
        assertEquals(mapOf(Nutrient.ENERGY to 520.0, Nutrient.PROTEIN to 7.2), label.nutrients.mapValues { it.value.amount })
    }

    @Test
    fun `per pack and per 30 g serving are serving columns, never the basis when per 100 is printed`() {
        val pack = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("Per 100 g", 480, 80), line("Per pack (150 g)", 700, 80), line("Energy (kcal) 520 780", 40, 140),
        )))
        assertEquals(520.0, pack.nutrients.getValue(Nutrient.ENERGY).amount, 0.0)
        val serving = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("Per 100 g", 480, 80), line("Per 30 g serving", 700, 80), line("Protein (g) 7.2 2.2", 40, 140),
        )))
        assertEquals(7.2, serving.nutrients.getValue(Nutrient.PROTEIN).amount, 0.0)
    }

    @Test
    fun `a slash proves per 100 as plainly as the word per`() {
        val label = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("Energy: 520 kcal/100 g", 40, 20), line("Protein 7.2 g/100g", 40, 70),
        )))
        assertEquals(LabelBasis.Per100("g"), label.basis)
        assertEquals(mapOf(Nutrient.ENERGY to 520.0, Nutrient.PROTEIN to 7.2), label.nutrients.mapValues { it.value.amount })
    }

    @Test
    fun `a title saying per serving does not outrank a header saying per 100`() {
        val label = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("Nutrition facts per serving", 40, 20), line("Per 100 g", 480, 80), line("Per serve (30 g)", 700, 80),
            line("Energy (kcal)", 40, 140), line("520", 500, 140), line("156", 740, 140),
        )))
        assertEquals(LabelBasis.Per100("g"), label.basis)
        assertEquals(520.0, label.nutrients.getValue(Nutrient.ENERGY).amount, 0.0)
    }

    @Test
    fun `one-line typical values and a mirrored panel are silence, stated ceilings`() {
        val oneLine = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("Typical values per 100 g: Energy 520 kcal, Protein 7.2 g, Fat 29 g", 40, 20),
        )))
        assertTrue(oneLine.rows.isEmpty())
        val mirrored = NutritionLabelExtractor.extract(RecognisedText(listOf(
            line("Per 100 g", 100, 20), line("520", 100, 70), line("Energy (kcal)", 400, 70),
        )))
        assertTrue(mirrored.rows.isEmpty())
    }

    @Test
    fun `nothing readable is an empty label, not an invented one`() {
        val label = NutritionLabelExtractor.extract(RecognisedText(listOf(line("Best before 6 months", 40, 20))))
        assertEquals(NutritionLabel(null, emptyList(), emptyMap(), null), label)
    }

    // --- a corpus of rows under a fixed two-column header ---------------------------------------

    /** A row as OCR returns it, under "Per 100 g | Per serve (30 g)", and what the print means. */
    private data class Case(val row: String, val name: String?, val value: Double? = null, val unit: String? = null,
                            val nutrient: Nutrient? = null, val note: String = "")

    private val corpus = listOf(
        Case("Energy (kcal) 520 156", "Energy (kcal)", 520.0, "kcal", Nutrient.ENERGY),
        Case("Energy 520 kcal 156 kcal", "Energy", 520.0, "kcal", Nutrient.ENERGY),
        Case("Energy 2176 kJ / 520 kcal 653 kJ / 156 kcal", "Energy", 520.0, "kcal", Nutrient.ENERGY, "kJ dropped in both cells"),
        Case("Energy value (kcal) 520 156", "Energy value (kcal)", 520.0, "kcal", Nutrient.ENERGY),
        Case("Calories 520 156", "Calories", 520.0, null, null, "no unit: a row, not a nutrient"),
        Case("Protein (g) 7.2 2.2", "Protein (g)", 7.2, "g", Nutrient.PROTEIN),
        Case("Protein 7.2g 2.2g", "Protein", 7.2, "g", Nutrient.PROTEIN, "glued unit"),
        Case("Proteins (g) 7.2 2.2", "Proteins (g)", 7.2, "g", Nutrient.PROTEIN),
        Case("Carbohydrate (g) 58.0 17.4", "Carbohydrate (g)", 58.0, "g", Nutrient.CARBOHYDRATE),
        Case("Total Carbohydrates (g) 58.0 17.4", "Total Carbohydrates (g)", 58.0, "g", Nutrient.CARBOHYDRATE),
        Case("Carbohydrates 58.0 g 17.4 g", "Carbohydrates", 58.0, "g", Nutrient.CARBOHYDRATE),
        Case("of which Sugars (g) 2.1 0.6", "of which Sugars (g)", 2.1, "g", null, "not carbohydrate"),
        Case("- Sugars (g) 2.1 0.6", "Sugars (g)", 2.1, "g", null),
        Case("Total Sugars (g) 2.1 0.6", "Total Sugars (g)", 2.1, "g", null),
        Case("Added Sugars (g) 0 0", "Added Sugars (g)", 0.0, "g", null),
        Case("Total Fat (g) 29.0 8.7", "Total Fat (g)", 29.0, "g", Nutrient.FAT),
        Case("Fat 29.0 g 8.7 g", "Fat", 29.0, "g", Nutrient.FAT),
        Case("Saturated Fat (g) 13.0 3.9", "Saturated Fat (g)", 13.0, "g", null, "never FAT"),
        Case("Saturated fatty acids (g) 13.0 3.9", "Saturated fatty acids (g)", 13.0, "g", null),
        Case("Trans Fat (g) 0 0", "Trans Fat (g)", 0.0, "g", null),
        Case("MUFA (g) 10.0 3.0", "MUFA (g)", 10.0, "g", null),
        Case("Cholesterol (mg) 0 0", "Cholesterol (mg)", 0.0, "mg", null),
        Case("Sodium (mg) 650 195", "Sodium (mg)", 650.0, "mg", Nutrient.SODIUM),
        Case("Sodium 650 mg 195 mg", "Sodium", 650.0, "mg", Nutrient.SODIUM),
        Case("Sodium (g) 0.65 0.195", "Sodium (g)", 0.65, "g", Nutrient.SODIUM, "converted downstream to 650 mg"),
        Case("Dietary Fibre (g) 3.1 0.9", "Dietary Fibre (g)", 3.1, "g", Nutrient.FIBRE),
        Case("Dietary Fiber 3.1 g 0.9 g", "Dietary Fiber", 3.1, "g", Nutrient.FIBRE),
        Case("Fibre (g) 3.1 0.9", "Fibre (g)", 3.1, "g", Nutrient.FIBRE),
        Case("Iron (mg) 2.5 0.75", "Iron (mg)", 2.5, "mg", Nutrient.IRON),
        Case("Vitamin B12 (mcg) 0.5 0.15", "Vitamin B12 (mcg)", 0.5, "mcg", Nutrient.VITAMIN_B12, "digit in the name"),
        Case("Vitamin B12 0.5 µg 0.15 µg", "Vitamin B12", 0.5, "ug", Nutrient.VITAMIN_B12, "micro sign"),
        Case("Calcium (mg) 120 36", "Calcium (mg)", 120.0, "mg", null),
        Case("Salt (g) 1.6 0.5", "Salt (g)", 1.6, "g", null, "salt is not sodium"),
        Case("Energy (kcal) 520 156 7.8%", "Energy (kcal)", 520.0, "kcal", Nutrient.ENERGY, "a marked percent is not a cell"),
        Case("Sodium 650 mg (28%) 195 mg (9%)", "Sodium", 650.0, "mg", Nutrient.SODIUM, "inline percents"),
        Case("Energy 2176 kJ (520 kcal) 653 kJ (156 kcal)", "Energy", 520.0, "kcal", Nutrient.ENERGY, "kJ first, kcal in brackets"),
        Case("Energy kcal 520 156", "Energy kcal", 520.0, "kcal", Nutrient.ENERGY, "unit before the value"),
        Case("Protein: 7.2 g 2.2 g", "Protein", 7.2, "g", Nutrient.PROTEIN, "colon after the name"),
        Case("Total Fat, g 29.0 8.7", "Total Fat, g", 29.0, "g", Nutrient.FAT, "unit after a comma"),
        Case("Energy (kcal/100g) 520 156", "Energy (kcal/100g)", 520.0, "kcal", Nutrient.ENERGY, "qualified bracket unit"),
        Case("Sodium* (mg) 650 195", "Sodium* (mg)", 650.0, "mg", Nutrient.SODIUM, "footnote marker"),
        Case("Sodium (mg) 1,250 375", "Sodium (mg)", 1250.0, "mg", Nutrient.SODIUM, "thousands separator"),
        Case("Protein (g) 7,2 2,2", null, note = "comma decimals are refused, not read as 7"),
        Case("Added Sugars (g) 0", null, note = "one cell under two columns: dropped"),
        Case("Total Carbohydrate 58.0 g of which Sugars 2.1 g", "Total Carbohydrate", 58.0, "g", Nutrient.CARBOHYDRATE,
            "CEILING two nutrients on one line: the first reads right only because per-100 is the first column"),
        Case("Sugars (g) <0.5 <0.5", null, note = "less-than is not a value"),
        Case("Net Wt. 150 g", null, note = "not a nutrient"),
        Case("MRP Rs. 45.00 (incl. of all taxes)", null),
        Case("Best before 6 months from packaging", null),
        Case("Batch No. 2207 Mfg 12/03/2026", null),
        Case("Servings per pack 5 approx.", null),
    )

    @Test
    fun `no wrong value and no wrong basis anywhere in the row corpus`() {
        var read = 0; var missed = 0; var wrongValue = 0; var wrongBasis = 0; var junk = 0
        val lines = mutableListOf<String>()
        for (c in corpus) {
            val label = NutritionLabelExtractor.extract(RecognisedText(listOf(
                line("Per 100 g   Per serve (30 g)", 400, 20), line(c.row, 40, 80),
            )))
            val got = label.rows.singleOrNull()
            val domain = label.nutrients.keys.singleOrNull()
            val verdict = when {
                c.name == null && got == null -> "dropped"
                c.name == null -> { junk++; "JUNK KEPT" }
                got == null -> { missed++; "MISSED" }
                got.value == c.value?.let { v -> corpusServingValue(c.row, v) } -> { wrongBasis++; "WRONG BASIS" }
                got.value != c.value -> { wrongValue++; "WRONG VALUE" }
                got.name != c.name -> { wrongValue++; "WRONG NAME" }
                !got.unit.equals(c.unit, ignoreCase = true) -> { wrongValue++; "WRONG UNIT" }
                domain != c.nutrient -> { wrongValue++; "WRONG NUTRIENT" }
                else -> { read++; "ok" }
            }
            lines += "%-13s %-52s -> %s".format(verdict, c.row.take(52),
                got?.let { "${it.name} | ${it.value} | ${it.unit} | ${domain ?: "-"}" } ?: "-")
        }
        val expected = corpus.count { it.name != null }
        println("=== nutrition label rows, ${corpus.size} rows under a two-column header ===")
        lines.forEach(::println)
        println("read $read/$expected  missed $missed  wrong value $wrongValue  wrong basis $wrongBasis  junk kept $junk")

        assertEquals("WRONG VALUE must be zero", 0, wrongValue)
        assertEquals("WRONG BASIS must be zero: the per-serving column is a silent factor of two", 0, wrongBasis)
        assertEquals("junk rows must be dropped", 0, junk)
        assertTrue("read rate fell below the floor: $read/$expected", read >= expected - 1)
    }

    /** The per-serving figure printed on the same row, so a wrong-column read is named as such. */
    private fun corpusServingValue(row: String, per100: Double): Double? {
        val nums = Regex("""(?<![A-Za-z0-9.])\d+(?:\.\d+)?""").findAll(row).map { it.value.toDouble() }.toList()
        val i = nums.indexOf(per100)
        return if (i >= 0 && i + 1 < nums.size && nums[i + 1] != per100) nums[i + 1] else null
    }

    // --- helpers ------------------------------------------------------------------------------------

    private fun line(text: String, left: Int, top: Int, height: Int = 36) =
        TextBlock(text, left, top, left + 11 * text.length, top + height)
}
