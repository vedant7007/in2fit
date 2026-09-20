package io.github.vedant7007.katori.data.food

import io.github.vedant7007.katori.domain.model.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test

/**
 * FOUND ON THE PHONE (21 Sep, typed): "I ate two chapatiis" and the LOG said "I do not know a
 * food in that". The deck claims 626 name variants, and roti and chapati are one food in the
 * database; a plural with a typo, which is what a keyboard and a mishearing both produce, is
 * what the 25-of-25 set did not contain.
 *
 * What each layer did: the extraction hands the matcher "chapatiis"; the exact and contained
 * stages miss; Rao's singular retry gives "chapatii" to the exact and contained stages only;
 * the fuzzy stage then compares the ORIGINAL "chapatiis" (nine letters, tolerance one) with
 * "chapati" at two edits and refuses. "chapatii" at one edit was never tried. Now the fuzzy
 * stage runs on the singular too, and every spelling below is the chapati recipe.
 */
class ChapatiSpellingsTest {

    companion object {
        private lateinit var db: JdbcFoodDbSource
        private lateinit var lookup: SqliteFoodLookup
        @JvmStatic @BeforeClass fun open() { db = JdbcFoodDbSource.openBundled(); lookup = SqliteFoodLookup(db) }
        @JvmStatic @AfterClass fun close() = db.close()
    }

    private fun code(q: String): String = when (val r = runBlocking { lookup.resolve(FoodQuery(q, "en-IN")) }) {
        is Outcome.Ok -> r.value.code.id
        else -> "NO MATCH ($r)"
    }

    @Test fun `seven spellings of chapati, and the one from the phone, are one food`() {
        for (q in listOf("chapati", "chapathi", "chappati", "chapatti", "chapatis", "chapatiis", "rotis", "roti", "rotti", "chapathis", "chapattis")) {
            assertEquals("'$q'", "chapati", code(q))
        }
    }

    /** The same shape on the other demo foods: a plural with one typo still lands. */
    @Test fun `a plural with a typo lands on the other demo foods too`() {
        assertEquals("idli", code("idlies"))   // singular "idly", an alias
        assertEquals("idli", code("idlis"))
        assertEquals("egg", code("eggs"))
        assertEquals("sambar", code("sambhars"))  // singular "sambhar", an alias
        assertEquals("sambar", code("sambars"))
    }

    /** Tolerance is not loosened: the short words the rule protects stay protected. */
    @Test fun `dal does not fuzzy onto dahi and a four-letter word still needs an exact hit`() {
        assertEquals("toor_dal_tadka", code("dal"))
        assertEquals("curd", code("dahi"))
        assertEquals(0, FoodTextMatching.toleranceFor(4))
    }
}
