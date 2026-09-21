package io.github.vedant7007.katori.ml.asr

import com.ibm.icu.text.Transliterator
import io.github.vedant7007.katori.data.food.FoodDbSource
import io.github.vedant7007.katori.data.food.JdbcFoodDbSource
import io.github.vedant7007.katori.data.food.LookupMealResolver
import io.github.vedant7007.katori.data.food.SqliteFoodLookup
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.ParsedItem
import io.github.vedant7007.katori.domain.ParsedMeal
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * The recogniser's Devanagari to the roman the model reads, on the JVM with icu4j's
 * `Devanagari-Latin` (the phone runs the platform's copy of the same transform). What is pinned:
 * the nine food words Vedant named come out as something the real matcher resolves; the long
 * vowels, the anusvara and the schwa are checked one by one rather than assumed; the frozen
 * Beat 1 sentence comes out as the Hinglish that resolved on the phone.
 */
class DevanagariTest {

    companion object {
        private lateinit var db: FoodDbSource
        @JvmStatic @BeforeClass fun open() { db = JdbcFoodDbSource.openBundled() }
        @JvmStatic @AfterClass fun close() = db.close()
    }

    /** The test's [Romaniser]: icu4j on the JVM, the same transform ID and the same spelling step as the phone. */
    private val romaniser = Romaniser { text ->
        if (!Devanagari.contains(text)) text
        else HinglishSpelling.fromIso15919(Transliterator.getInstance(Devanagari.ICU_TRANSFORM).transliterate(text))
    }

    private fun roman(word: String) = romaniser.romanise(word)

    @Test fun `text without Devanagari passes through untouched`() {
        assertEquals("I had two rotis and a little dal.", roman("I had two rotis and a little dal."))
    }

    @Test fun `the nine food words come out as a Hindi speaker types them`() {
        assertEquals("roti", roman("रोटी"))
        assertEquals("dal", roman("दाल"))
        assertEquals("chaval", roman("चावल"))
        assertEquals("dahi", roman("दही"))
        assertEquals("anda", roman("अंडा"))
        assertEquals("idli", roman("इडली"))
        assertEquals("sambar", roman("सांबर"))
        assertEquals("dosa", roman("दोसा"))
        assertEquals("chatni", roman("चटनी"))
    }

    /** Vowel length: the long vowel keeps its letter and is never mistaken for a schwa. */
    @Test fun `long vowels fold to their letter and are never dropped`() {
        assertEquals("dosa", roman("दोसा"))        // final ā stays
        assertEquals("kela", roman("केला"))        // final ā stays
        assertEquals("doodh".replace("oo", "u"), roman("दूध"))  // ū -> u: dudh
        assertEquals("paneer".replace("ee", "i"), roman("पनीर")) // ī -> i: panir
        assertEquals("aam".replace("aa", "a"), roman("आम"))     // ā -> a: am
        assertEquals("chay", roman("चाय"))         // य is y: chay (Priya: "chai" is the alias people type)
    }

    /** Nasals: the anusvara before a labial is m, elsewhere n; the candrabindu reads the same. */
    @Test fun `the anusvara is the nasal the mouth makes`() {
        assertEquals("sambar", roman("सांबर"))     // before b: m
        assertEquals("anda", roman("अंडा"))        // before ḍ: n
        assertEquals("mainne", roman("मैंने"))     // word-internal before n: n
        assertEquals("hun", roman("हूँ"))          // candrabindu at the end: n
    }

    /** The schwa: gone at the end and between vowelled consonants, kept where Hindi keeps it. */
    @Test fun `schwa deletion follows the word`() {
        assertEquals("dal", roman("दाल"))          // final
        assertEquals("idli", roman("इडली"))        // medial V C a C V
        assertEquals("chatni", roman("चटनी"))      // medial
        assertEquals("chaval", roman("चावल"))      // final, after a long vowel
        assertEquals("palak", roman("पालक"))       // final
        assertEquals("kam", roman("कम"))           // one syllable: the a stays
        assertEquals("do", roman("दो"))
    }

    @Test fun `the frozen Beat 1 sentence comes out as the Hinglish that resolved on the phone`() {
        assertEquals("mainne do roti aur thodi dal khai", roman("मैंने दो रोटी और थोड़ी दाल खाई"))
    }

    @Test fun `the other Hindi demo rows read as Hinglish`() {
        assertEquals("do roti, ek katori dal, aur do chammach tel", roman("दो रोटी, एक कटोरी दाल, और दो चम्मच तेल"))
        assertEquals("ek plet chaval, dal aur ek katori dahi", roman("एक प्लेट चावल, दाल और एक कटोरी दही"))
        assertEquals("nashte men tin idli aur sambar khaya", roman("नाश्ते में तीन इडली और सांबर खाया"))   // में is mēṁ: men
    }

    // --- and the matcher must know them -------------------------------------------------------

    private fun item(name: String) = ParsedItem(
        spokenName = name, quantity = 1.0, unit = null, matchedFoodCode = null,
        confidence = ConfidenceRules.of(ConfidenceReason.QUANTITY_STATED),
    )

    /**
     * Eight of the nine resolve today. चटनी comes out as "chatni" and the database holds chutney
     * only as "coconut chutney" and "peanut chutney" (recipe aliases): PRIYA'S, listed for her on
     * 21 Sep; when the alias lands this test tightens itself, because the allowance is exact.
     */
    @Test fun `every one of the nine resolves through the real matcher, chutney pending its alias`() {
        val resolver = LookupMealResolver(SqliteFoodLookup(db))
        val misses = mutableListOf<String>()
        val resolvedAs = mutableListOf<String>()
        for (word in listOf("रोटी", "दाल", "चावल", "दही", "अंडा", "इडली", "सांबर", "दोसा", "चटनी")) {
            val r = roman(word)
            val out = runBlocking { resolver.resolve(ParsedMeal(listOf(item(r)), ConfidenceRules.of(ConfidenceReason.QUANTITY_STATED), "test"), "hi") }
            val code = (out as? Outcome.Ok)?.value?.items?.singleOrNull()?.snapshot?.foodCode
            if (code == null) misses += "$word -> '$r'" else resolvedAs += "$r -> $code"
        }
        assertEquals("PRIYA, aliases for these: $misses (resolved: $resolvedAs)", listOf("चटनी -> 'chatni'"), misses)
    }
}
