package io.github.vedant7007.katori.data.food

/**
 * The narrowest possible seam over the bundled read-only SQLite file.
 *
 * WHY THIS EXISTS. Every piece of logic worth testing in this package, which is the fuzzy
 * multilingual matching, the unit resolution and the three-state nutrient assembly, would
 * otherwise be locked behind `android.database.sqlite` and only runnable on a device or an
 * emulator. Behind this seam it runs as a plain JVM unit test against the REAL shipped database
 * file, which is far stronger than testing against hand-written fixtures: it catches a bad import
 * as well as a bad query.
 *
 * Implementations: an Android one over SQLiteDatabase, and a JDBC one used by tests.
 *
 * CONTRACT
 * - Read only. There is no write method and there never will be; this database ships in the APK
 *   and is replaced wholesale, never migrated (see the separation described in FoodLookup).
 * - [query] returns plain rows. No cursors escape, so nothing can leak a live cursor.
 * - Implementations must use parameter binding. Callers pass values in [args], never interpolated.
 */
interface FoodDbSource {
    fun query(sql: String, args: List<Any?> = emptyList()): List<Map<String, Any?>>
    fun close()
}

/** Column helpers. A missing or null column is an error in our own SQL, not a runtime condition. */
internal fun Map<String, Any?>.str(col: String): String =
    this[col]?.toString() ?: error("column '$col' missing or null in row: $this")

internal fun Map<String, Any?>.strOrNull(col: String): String? = this[col]?.toString()

internal fun Map<String, Any?>.dbl(col: String): Double =
    (this[col] as? Number)?.toDouble()
        ?: this[col]?.toString()?.toDoubleOrNull()
        ?: error("column '$col' is not a number in row: $this")

internal fun Map<String, Any?>.dblOrNull(col: String): Double? =
    (this[col] as? Number)?.toDouble() ?: this[col]?.toString()?.toDoubleOrNull()
