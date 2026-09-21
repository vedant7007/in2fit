package io.github.vedant7007.katori.ml.asr

import android.os.Build
import java.text.Normalizer

/**
 * THE HOP BETWEEN THE RECOGNISER AND THE MODEL. The recogniser writes Devanagari and writes it
 * exactly (Jacob: 16 of 16 food words on Vedant's voice); the 1.5B model reads it badly: the
 * frozen Beat 1 sentence "मैंने दो रोटी और थोड़ी दाल खाई" came back as "डॉटरी: 2" three times
 * on the realme (21 Sep, twice through the microphone, once typed into the rig), and the same
 * sentence in roman Hinglish, "maine do roti aur thodi dal khayi", resolved to roti and dal
 * with a plate at 11.9 s. So the model is given roman text. The person still speaks Hindi, the
 * transcript on screen stays in Devanagari, the reply is unchanged; only the model's input moves.
 *
 * TWO STEPS, ONE OF THEM SOMEBODY ELSE'S. The letters are ICU's `Devanagari-Latin` transform
 * (ISO 15919), which ships in the platform as `android.icu` from API 29 and needs no dependency;
 * on the JVM the tests run the same transform from icu4j. What ICU cannot know is how a Hindi
 * speaker SPELLS the result: ISO 15919 writes every inherent vowel ("iḍalī", "dāla") and marks
 * with diacritics, while a person types "idli", "dal", "chawal", "sambar". [HinglishSpelling]
 * is that second step: the diacritics folded, ISO's `c`/`ś`/`ṭ` written as `ch`/`sh`/`t`, the
 * anusvara as the nasal the mouth makes, and the schwa dropped where Hindi drops it (word-final,
 * and between two consonants that both have vowels). It is a spelling convention, not a
 * transliteration scheme, and every rule in it has a food word in the test that would break
 * without it.
 *
 * Below API 29 there is no transform and the model reads Devanagari as it did before; the demo
 * phones are on Android 15.
 */
fun interface Romaniser {
    /** [text] with every Devanagari run rewritten in roman Hinglish; text with no Devanagari is returned as is. */
    fun romanise(text: String): String
}

object Devanagari {
    private val block = Regex("[\\u0900-\\u097F]")

    fun contains(text: String): Boolean = block.containsMatchIn(text)

    /** The ICU transform that turns Devanagari into ISO 15919; the same ID on the phone and on the JVM. */
    const val ICU_TRANSFORM = "Devanagari-Latin"
}

/** ISO 15919 as ICU writes it, to the spelling a Hindi speaker types. Pure, so the JVM tests it word by word. */
object HinglishSpelling {

    fun fromIso15919(iso: String): String = iso.split(' ').joinToString(" ") { word(it) }

    private fun word(token: String): String {
        // Punctuation after the word is not part of it: "dāla," is dal with a comma.
        val core = token.trimEnd { !it.isLetter() && !isMark(it) }
        val tail = token.substring(core.length)
        if (core.isEmpty()) return token
        // 0. ICU's reversibility marks: the apostrophe between two vowels that would otherwise
        //    read as one (khā'ī) and between a consonant and the vowel sign after it (cam'mac).
        var s = core.replace("'", "")
        // 1. Letters ICU writes with a diacritic or a letter a Hindi speaker would not use.
        for ((from, to) in LETTERS) s = s.replace(from, to)
        // 2. The anusvara is the nasal of the consonant after it: सांबर is sambar, not sanbar.
        s = s.replace(Regex("$ANUSVARA(?=[pbm])"), "m").replace(ANUSVARA, "n")
        // 3. Schwa deletion, the way Hindi reads it, while the long vowels still carry their marks
        //    so only the inherent a is ever a candidate: the final one goes (dāla -> dāl, never
        //    dosā), and a medial one between two consonants that both carry vowels goes
        //    (iḍalī -> iḍlī, caṭanī -> caṭnī).
        s = dropSchwas(s)
        // 4. Every other mark folds to its base letter (ā -> a, ē -> e, ī -> i, ṭ -> t, ḍ -> d, ṇ -> n ...).
        return Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "") + tail
    }

    private fun dropSchwas(s: String): String {
        val chars = s.toMutableList()
        // Final inherent a after a consonant, in a word that has another vowel.
        if (chars.size >= 3 && chars.last() == 'a' && !isVowel(chars[chars.size - 2]) && chars.dropLast(1).any { isVowel(it) }) chars.removeAt(chars.size - 1)
        // Medial: V C a C V  ->  V C C V, right to left so one deletion does not feed the next.
        var i = chars.size - 3
        while (i >= 2) {
            if (chars[i] == 'a' && !isVowel(chars[i - 1]) && !isVowel(chars[i + 1]) && isVowel(chars[i - 2]) && isVowel(chars[i + 2])) {
                chars.removeAt(i)
                i -= 2
            } else i--
        }
        return chars.joinToString("")
    }

    private fun isVowel(c: Char) = c in VOWELS

    /** A combining mark belongs to the letter before it (the candrabindu on hūm̐ is not punctuation). */
    private fun isMark(c: Char) = Character.getType(c).toByte().let { it == Character.NON_SPACING_MARK || it == Character.COMBINING_SPACING_MARK }

    private const val ANUSVARA = "ṁ"
    /** ISO vowels with their marks still on; `a` is the inherent vowel and the only one schwa deletion may touch. */
    private val VOWELS = "aāiīuūeēoō".toSet()   // ISO 15919 writes ए as ē and ओ as ō

    /** In the order they must apply: a digraph before the single letter inside it. */
    private val LETTERS = listOf(
        Regex("ch") to "chh",      // छ, ISO ch, is aspirated: chhole
        Regex("c(?!h)") to "ch",   // च, ISO c: chawal, chatni
        Regex("ś|ṣ") to "sh",
        Regex("ṛh") to "dh", Regex("ṛ") to "d",   // the nukta flap ड़/ढ़ as typed: thodi, bada, pakoda
        Regex("r̥") to "ri",       // the vowel ऋ: krishna
        Regex("m\u0310") to ANUSVARA,   // candrabindu ँ (ICU: m + U+0310) reads as the anusvara
        Regex("ḥ") to "h",
        Regex("k͟h") to "kh", Regex("ġ") to "g", Regex("q") to "k",
    )
}

/** [Romaniser] over the platform's ICU. Constructed once; the transform is thread-safe for `transliterate`. */
class IcuRomaniser : Romaniser {
    private val transform: android.icu.text.Transliterator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) android.icu.text.Transliterator.getInstance(Devanagari.ICU_TRANSFORM) else null

    override fun romanise(text: String): String {
        val t = transform ?: return text
        if (!Devanagari.contains(text)) return text
        return HinglishSpelling.fromIso15919(t.transliterate(text))
    }
}
