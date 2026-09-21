package io.github.vedant7007.katori.data.local

import io.github.vedant7007.katori.data.local.entity.LabValueEntity
import io.github.vedant7007.katori.data.local.entity.MealItemEntity
import io.github.vedant7007.katori.data.local.entity.MealItemNutrientEntity
import io.github.vedant7007.katori.domain.model.ConfidenceBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** The export is the rows as stored: an Unknown has no amount, a quoted cell survives a comma, nothing is summed. */
class ExportTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    @Test
    fun `a meal exports one line per item per nutrient with the stored state and no total`() {
        val items = listOf(
            MealItemEntity(11, 7, "dal", 1.0, "katori", 180.0, "AUTHORED_RECIPE", "toor_dal_tadka", ConfidenceBand.ROUGH, "QUANTITY_INFERRED,HOUSEHOLD_UNIT_DEFAULT", display_name = "Dal tadka"),
            MealItemEntity(12, 7, "kadha, homemade", 1.0, "cup", null, null, null, ConfidenceBand.ROUGH, "QUANTITY_STATED", display_name = null),
        )
        val meal = Diary.Meal(7, Instant.parse("2026-09-21T01:13:00Z"), items, emptyMap(), emptyList(), "a little dal and a cup of kadha", "SPOKEN")
        val nutrients = mapOf(
            11L to listOf(
                MealItemNutrientEntity(11, "PROTEIN", "MEASURED", 10.8, "G"),
                MealItemNutrientEntity(11, "VITAMIN_B12", "UNKNOWN", null, "UG"),
            ),
        )
        val csv = Export.mealsCsv(listOf(meal), nutrients, zone)
        val lines = csv.trimEnd().split("\r\n")
        assertEquals(Export.MEAL_HEADER.joinToString(","), lines[0])
        assertEquals("2026-09-21 06:43,7,SPOKEN,Dal tadka,dal,1.0,katori,180.0,AUTHORED_RECIPE,toor_dal_tadka,ROUGH,PROTEIN,10.8,G,MEASURED", lines[1])
        assertEquals("an Unknown has no amount, and is not 0", "2026-09-21 06:43,7,SPOKEN,Dal tadka,dal,1.0,katori,180.0,AUTHORED_RECIPE,toor_dal_tadka,ROUGH,VITAMIN_B12,,UG,UNKNOWN", lines[2])
        assertEquals("a no-data item is one line with its name quoted for the comma", "2026-09-21 06:43,7,SPOKEN,\"kadha, homemade\",\"kadha, homemade\",1.0,cup,,,,ROUGH,,,,", lines[3])
        assertEquals(4, lines.size)
        assertTrue("no total anywhere", "TOTAL" !in csv.uppercase())
    }

    @Test
    fun `lab values export by report date with the printed range or blanks`() {
        val csv = Export.labsCsv(listOf(
            LabValueEntity(2, "Vitamin D", 18.5, "ng/mL", null, null, "2026-09-12", 0L),
            LabValueEntity(1, "Haemoglobin", 9.8, "g/dL", 13.0, 17.0, "2026-09-12", 0L),
            LabValueEntity(3, "Haemoglobin", 11.2, "g/dL", 13.0, 17.0, "2026-06-01", 0L),
        ))
        assertEquals(
            listOf(
                "report_date,test_name,value,unit,reference_low,reference_high",
                "2026-06-01,Haemoglobin,11.2,g/dL,13.0,17.0",
                "2026-09-12,Haemoglobin,9.8,g/dL,13.0,17.0",
                "2026-09-12,Vitamin D,18.5,ng/mL,,",
            ),
            csv.trimEnd().split("\r\n"),
        )
    }

    @Test
    fun `a cell with a quote is doubled and quoted`() {
        assertEquals("\"say \"\"hi\"\"\"", Export.cell("say \"hi\""))
        assertEquals("plain", Export.cell("plain"))
    }
}
