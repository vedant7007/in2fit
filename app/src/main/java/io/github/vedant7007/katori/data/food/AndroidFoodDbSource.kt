package io.github.vedant7007.katori.data.food

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.security.MessageDigest

/**
 * [FoodDbSource] over the SQLite file bundled in assets.
 *
 * The asset is copied to the app's files directory once, because SQLite needs a real file and an
 * asset is a compressed zip entry. The copy is keyed by the CONTENT of the asset, not by a version
 * number: the bundled database is rebuilt whenever an alias or a recipe changes, with the schema
 * version staying at 1, so a version key would keep serving the previous build forever. The local
 * file is named after the asset's SHA-256, and any sibling copy under another name is deleted, so
 * shipping a new database replaces the copy instead of silently keeping the old one.
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
        private const val LOCAL_PREFIX = "katori-food-"
        private const val LOCAL_SUFFIX = ".db"

        /**
         * Opens the bundled database, copying it out of assets on first use of THIS build of it.
         *
         * Throws if the asset is missing or unreadable. That is deliberate: the app cannot do its
         * job without the food database, and a silent fallback to an empty one would produce
         * "no match" for every food, which reads like a matching bug rather than a packaging
         * failure.
         */
        fun open(context: Context): AndroidFoodDbSource {
            val bytes = context.assets.open(ASSET).use { it.readBytes() }
            val target = stage(context.filesDir, bytes)
            val db = SQLiteDatabase.openDatabase(
                target.absolutePath, null, SQLiteDatabase.OPEN_READONLY,
            )
            return AndroidFoodDbSource(db)
        }

        /**
         * Places [assetBytes] in [filesDir] under a name derived from its hash, and removes every
         * other copy. Pure file I/O, no Android, so the stale-copy behaviour has a JVM test.
         *
         * A 311 KB read and hash on every open costs single-digit milliseconds and is what makes
         * the key impossible to forget to bump.
         */
        internal fun stage(filesDir: File, assetBytes: ByteArray): File {
            val key = MessageDigest.getInstance("SHA-256").digest(assetBytes)
                .joinToString("") { "%02x".format(it) }.take(16)
            val target = File(filesDir, "$LOCAL_PREFIX$key$LOCAL_SUFFIX")
            if (target.length() != assetBytes.size.toLong()) {
                filesDir.listFiles { f ->
                    f.name.startsWith(LOCAL_PREFIX) && f.name.endsWith(LOCAL_SUFFIX)
                }?.forEach { it.delete() }
                target.writeBytes(assetBytes)
            }
            return target
        }
    }
}
