package io.github.vedant7007.katori.data.food

import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * The five words the integrator's Devanagari romaniser produces that the tables did not hold
 * (his `DevanagariTest`, 21 Sep): chatni, chaval, chay, dudh, panir. Four are aliases now, one
 * is a refusal by design.
 */
class TransliteratedWordsTest {

    companion object {
        private lateinit var db: JdbcFoodDbSource
        private lateinit var lookup: SqliteFoodLookup
        @JvmStatic @BeforeClass fun open() { db = JdbcFoodDbSource.openBundled(); lookup = SqliteFoodLookup(db) }
        @JvmStatic @AfterClass fun close() = db.close()
    }

    private fun resolve(q: String) = runBlocking { lookup.resolve(FoodQuery(q, "hi-IN")) }
    private fun code(q: String) = (resolve(q) as? Outcome.Ok)?.value?.let { "${it.code.id} (${it.matchKind})" } ?: resolve(q).toString()

    /** A bare chutney is the coconut one beside idli and dosa; the plate shows "Coconut chutney", so the assumption is on the face. */
    @Test fun `chatni and a bare chutney are the coconut chutney`() {
        assertEquals("coconut_chutney (EXACT)", code("chatni"))
        assertEquals("coconut_chutney (EXACT)", code("chutney"))
        assertEquals("coconut_chutney (EXACT)", code("चटनी"))
    }

    /** The bare alias must not swallow a named chutney (0034): each goes to its own recipe or is refused by name. */
    @Test fun `a named chutney is never the coconut one`() {
        assertEquals("peanut_chutney (EXACT)", code("peanut chutney"))
        assertEquals("tomato_pachadi (EXACT)", code("tomato chutney"))
        assertEquals("gongura_pachadi (EXACT)", code("gongura chutney"))
        for (q in listOf("mint chutney", "green chutney", "pudina chatni", "garlic chutney", "mango chutney")) {
            val r = resolve(q)
            assertTrue("'$q' -> $r", r is Outcome.Unavailable && r.reason == UnavailableReason.KNOWN_ITEM_NO_DATA)
        }
    }

    @Test fun `chaval chay and dudh are exact aliases now`() {
        assertEquals("rice_cooked (EXACT)", code("chaval"))
        assertEquals("chai (EXACT)", code("chay"))
        assertEquals("milk_whole (EXACT)", code("dudh"))
    }

    /** पनीर comes out "panir", and panir IS in the tables: as the no-data item paneer, refused by name, by design (0034). */
    @Test fun `panir is refused by name, not unknown`() {
        val r = resolve("panir")
        assertTrue("$r", r is Outcome.Unavailable && r.reason == UnavailableReason.KNOWN_ITEM_NO_DATA)
    }
}
