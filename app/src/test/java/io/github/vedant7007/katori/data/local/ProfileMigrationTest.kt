package io.github.vedant7007.katori.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Version 2 to 3 on a POPULATED database, not an empty one: a person with a profile, meals,
 * items, nutrients and a lab value, migrated, keeps every row and every value, and gains three
 * absent columns. The same SQL the app runs (`KatoriDatabase.MIGRATION_2_3_SQL`) runs here over
 * sqlite-jdbc against tables created from the exported `2.json`, and the result is checked
 * against the exported `3.json`, which is what Room validates the opened database against on
 * the device. A column out of place here is the mismatch Room would refuse there.
 */
class ProfileMigrationTest {

    private val schemas = File(System.getProperty("katori.projectDir") ?: error("katori.projectDir not set"), "app/schemas/io.github.vedant7007.katori.data.local.KatoriDatabase")

    /** `tableName` and `createSql` per entity, from an exported schema; nothing else is parsed. */
    private fun tables(version: Int): Map<String, String> =
        Regex(""""tableName":\s*"(\w+)",\s*"createSql":\s*"((?:[^"\\]|\\.)*)"""")
            .findAll(File(schemas, "$version.json").readText())
            .associate { it.groupValues[1] to it.groupValues[2].replace("\${TABLE_NAME}", it.groupValues[1]).replace("\\\"", "\"") }

    private fun columns(db: Connection, table: String): List<String> =
        db.createStatement().executeQuery("PRAGMA table_info(`$table`)").use { rs ->
            generateSequence { if (rs.next()) rs.getString("name") else null }.toList()
        }

    /** The column names in the order the CREATE statement declares them. */
    private fun declared(createSql: String): List<String> =
        Regex("""`(\w+)` (?:INTEGER|TEXT|REAL|BLOB)""").findAll(createSql.substringAfter("(")).map { it.groupValues[1] }.toList()

    @Test
    fun `a populated v2 database migrates to v3 keeping every row and value`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            val v2 = tables(2)
            v2.values.forEach { db.createStatement().execute(it) }
            db.createStatement().apply {
                execute("INSERT INTO profile VALUES (1, 19, 62.0, 172.0, 'MALE', 'MAINTAIN', 'HOSTEL_STUDENT', 'VEGETARIAN', 1758400000000)")
                execute("INSERT INTO meals (id, logged_at_epoch_ms, raw_transcript, confidence_band, language_tag) VALUES (7, 1758400100000, 'two rotis and a little dal', 'ROUGH', 'hi')")
                execute("INSERT INTO meal_items (id, meal_id, spoken_name, quantity, unit, grams, food_source, food_id, confidence_band, confidence_reasons) VALUES (11, 7, 'dal', 1.0, 'katori', 180.0, 'AUTHORED', 'toor_dal_tadka', 'ROUGH', 'QUANTITY_INFERRED,HOUSEHOLD_UNIT_DEFAULT')")
                execute("INSERT INTO meal_item_nutrients VALUES (11, 'PROTEIN', 'MEASURED', 10.8, 'G')")
                execute("INSERT INTO meal_item_nutrients VALUES (11, 'VITAMIN_B12', 'UNKNOWN', NULL, 'UG')")
                execute("INSERT INTO lab_values (test_name, value, unit, reference_low, reference_high, report_date, captured_at_epoch_ms) VALUES ('Haemoglobin', 9.8, 'g/dL', 13.0, 17.0, '2026-09-12', 1758300000000)")
            }
            val before = listOf("profile", "meals", "meal_items", "meal_item_nutrients", "lab_values").associateWith { count(db, it) }

            KatoriDatabase.MIGRATION_2_3_SQL.forEach { db.createStatement().execute(it) }

            // Every row survived, in every table.
            before.forEach { (table, n) -> assertEquals(table, n, count(db, table)) }
            // The profile keeps every value it had; the three new fields are absent, not defaulted.
            db.createStatement().executeQuery("SELECT * FROM profile WHERE id = 1").use { rs ->
                rs.next()
                assertEquals(19, rs.getInt("age_years"))
                assertEquals(62.0, rs.getDouble("weight_kg"), 0.0)
                assertEquals(172.0, rs.getDouble("height_cm"), 0.0)
                assertEquals("MALE", rs.getString("sex"))
                assertEquals("MAINTAIN", rs.getString("goal"))
                assertEquals("HOSTEL_STUDENT", rs.getString("life_context"))
                assertEquals("VEGETARIAN", rs.getString("diet_type"))
                assertEquals(1758400000000L, rs.getLong("updated_at_epoch_ms"))
                assertNull(rs.getString("name"))
                assertNull(rs.getString("activity"))
                assertNull(rs.getString("speech_language_tag"))
            }
            // The unknown nutrient is still UNKNOWN with no amount, not a zero.
            db.createStatement().executeQuery("SELECT state, amount FROM meal_item_nutrients WHERE nutrient = 'VITAMIN_B12'").use { rs ->
                rs.next(); assertEquals("UNKNOWN", rs.getString("state")); assertNull(rs.getObject("amount"))
            }
            // The migrated shape is the exported v3 shape, column for column, for every table.
            val v3 = tables(3)
            assertEquals(v2.keys, v3.keys)
            v3.forEach { (table, createSql) -> assertEquals(table, declared(createSql), columns(db, table)) }
        }
    }

    /** The exported v3 differs from v2 in the three profile columns and nothing else. */
    @Test
    fun `the schema change is the three profile columns and nothing else`() {
        val v2 = tables(2)
        val v3 = tables(3)
        assertEquals(v2.keys, v3.keys)
        v2.keys.filter { it != "profile" }.forEach { assertEquals(it, v2[it], v3[it]) }
        assertEquals(declared(v2.getValue("profile")) + listOf("name", "activity", "speech_language_tag"), declared(v3.getValue("profile")))
    }

    private fun count(db: Connection, table: String): Int =
        db.createStatement().executeQuery("SELECT COUNT(*) FROM `$table`").use { it.next(); it.getInt(1) }
}
