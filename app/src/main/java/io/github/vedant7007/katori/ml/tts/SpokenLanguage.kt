package io.github.vedant7007.katori.ml.tts

import io.github.vedant7007.katori.ml.asr.SpeechLanguage

/**
 * The voice a piece of text should be read in, from the text itself.
 *
 * WHY THIS EXISTS. The profile's speech language says what the person SPEAKS; it does not say
 * what the app has to say back. Today everything the app says back is English by construction
 * or by the model's habit: the lead-in and the rules engine's sentences come from a string
 * table with no Hindi in it, and the model answers in English whatever `Language to reply in`
 * says (`0019` addendum 7, seven of seven on the desktop). Speaking that English through the
 * Hindi voice because the profile says Hindi is exactly the mismatch the `TtsEngine` contract
 * forbids in the other direction: the words and the phonology must agree. So the voice follows
 * the script of the text, and the profile language is only the tie-break for text with no
 * letters at all.
 *
 * Latin script is read as English. Romanised Hindi ("aaj maine roti khayi") would be read as
 * English too; nothing in the pipeline produces it today, and when something does, that is the
 * moment for a real language detector, not a script test.
 */
fun spokenLanguageOf(text: String, preferred: SpeechLanguage): SpeechLanguage {
    var telugu = 0
    var devanagari = 0
    var latin = 0
    for (c in text) when {
        c in 'ఀ'..'౿' -> telugu++
        c in 'ऀ'..'ॿ' -> devanagari++
        c in 'A'..'Z' || c in 'a'..'z' -> latin++
    }
    return when {
        telugu == 0 && devanagari == 0 && latin == 0 -> preferred
        telugu >= devanagari && telugu >= latin -> SpeechLanguage.TELUGU
        devanagari >= latin -> SpeechLanguage.HINDI
        else -> SpeechLanguage.ENGLISH_INDIA
    }
}
