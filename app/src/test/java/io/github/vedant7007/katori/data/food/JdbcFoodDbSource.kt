package io.github.vedant7007.katori.data.food

import java.sql.DriverManager

/**
 * Test-only [FoodDbSource] over JDBC, so the food layer can be tested on the JVM against the REAL
 * bundled database rather than against fixtures.
 *
 * Testing the shipped file matters: it catches a bad import, a renamed column and a wrong alias,
 * none of which a fixture would ever show.
 */
class JdbcFoodDbSource(path: String) : FoodDbSource {
    private val conn = DriverManager.getConnection("jdbc:sqlite:$path")

    override fun query(sql: String, args: List<Any?>): List<Map<String, Any?>> {
        conn.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setObject(i + 1, a) }
            st.executeQuery().use { rs ->
                val md = rs.metaData
                val out = mutableListOf<Map<String, Any?>>()
                while (rs.next()) {
                    val row = LinkedHashMap<String, Any?>(md.columnCount)
                    for (i in 1..md.columnCount) row[md.getColumnLabel(i)] = rs.getObject(i)
                    out += row
                }
                return out
            }
        }
    }

    override fun close() = conn.close()

    companion object {
        /** The real database, as produced by tools/build_food_db.py and shipped in assets. */
        fun openBundled(): JdbcFoodDbSource {
            val projectDir = System.getProperty("katori.projectDir")
                ?: error("katori.projectDir not set; see app/build.gradle.kts testOptions")
            val f = java.io.File(projectDir, "app/src/main/assets/food/katori-food.db")
            check(f.isFile) { "bundled food database not found at ${f.absolutePath}; run tools/build_food_db.py" }
            return JdbcFoodDbSource(f.absolutePath)
        }
    }
}
