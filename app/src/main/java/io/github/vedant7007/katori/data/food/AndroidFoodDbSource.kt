package io.github.vedant7007.katori.data.food

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * [FoodDbSource] over the SQLite file bundled in assets.
 *
 * The asset is copied to the app's files directory once, because SQLite needs a real file and an
 * asset is a compressed zip entry. The copy is keyed by the database's schema version, so shipping
 * a new database replaces the copy instead of silently keeping the old one.
 *
 * Opened READ ONLY. Nothing in the app writes to this file; user corrections live in the Room
 * database, which is a different file with a different lifecycle.
 */
class AndroidFoodDbSource private constructor(private val db: SQLiteDatabase) : FoodDbSource {

    override fun query(sql: String, args: List<Any?>): List<Map<String, Any?>> {
        val out = mutableListOf<Map<String, Any?>>()
        db.rawQuery(sql, args.map { it?.toString() }.toTypedArray()).use { cur ->
            val cols = cur.columnNames
            while (cur.moveToNext()) {
                val row = HashMap<String, Any?>(cols.size)
                for (i in cols.indices) {
                    row[cols[i]] = when (cur.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> null
                        android.database.Cursor.FIELD_TYPE_INTEGER -> cur.getLong(i)
                        android.database.Cursor.FIELD_TYPE_FLOAT -> cur.getDouble(i)
                        else -> cur.getString(i)
                    }
                }
                out += row
            }
        }
        return out
    }

    override fun close() = db.close()

    companion object {
        private const val ASSET = "food/katori-food.db"
        private const val LOCAL = "katori-food-v1.db"

        /**
         * Opens the bundled database, copying it out of assets on first use.
         *
         * Throws if the asset is missing or unreadable. That is deliberate: the app cannot do its
         * job without the food database, and a silent fallback to an empty one would produce
         * "no match" for every food, which reads like a matching bug rather than a packaging
         * failure.
         */
        fun open(context: Context): AndroidFoodDbSource {
            val target = File(context.filesDir, LOCAL)
            if (!target.exists() || target.length() == 0L) {
                context.assets.open(ASSET).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            val db = SQLiteDatabase.openDatabase(
                target.absolutePath, null, SQLiteDatabase.OPEN_READONLY,
            )
            return AndroidFoodDbSource(db)
        }
    }
}
