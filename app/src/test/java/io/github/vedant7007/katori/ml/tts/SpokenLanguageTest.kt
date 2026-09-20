package io.github.vedant7007.katori.ml.tts

import io.github.vedant7007.katori.ml.asr.SpeechLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

/** The voice follows the script of the text; the profile only breaks a tie with no letters. */
class SpokenLanguageTest {

    @Test
    fun `english text is spoken by the english voice whatever the profile says`() {
        val english = "Two rotis, a katori of dal and two spoons of oil: 428 kilocalories."
        assertEquals(SpeechLanguage.ENGLISH_INDIA, spokenLanguageOf(english, SpeechLanguage.HINDI))
        assertEquals(SpeechLanguage.ENGLISH_INDIA, spokenLanguageOf(english, SpeechLanguage.TELUGU))
    }

    @Test
    fun `devanagari and telugu text pick their own voices`() {
        assertEquals(SpeechLanguage.HINDI, spokenLanguageOf("रोटी दाल", SpeechLanguage.ENGLISH_INDIA))
        assertEquals(SpeechLanguage.TELUGU, spokenLanguageOf("ఇడ్లీ సాంబార్", SpeechLanguage.HINDI))
    }

    @Test
    fun `a sentence mostly in one script with a figure or a food name in another follows the majority`() {
        assertEquals(SpeechLanguage.HINDI, spokenLanguageOf("आज आपने 2.9 mg आयरन खाया", SpeechLanguage.ENGLISH_INDIA))
        assertEquals(SpeechLanguage.ENGLISH_INDIA, spokenLanguageOf("You had idli (ఇడ్లీ) and sambar this morning", SpeechLanguage.TELUGU))
    }

    @Test
    fun `text with no letters at all falls back to the profile`() {
        assertEquals(SpeechLanguage.HINDI, spokenLanguageOf("428 / 19", SpeechLanguage.HINDI))
        assertEquals(SpeechLanguage.TELUGU, spokenLanguageOf("   ", SpeechLanguage.TELUGU))
    }
}
