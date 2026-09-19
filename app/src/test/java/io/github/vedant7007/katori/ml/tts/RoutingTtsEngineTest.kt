package io.github.vedant7007.katori.ml.tts

import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import io.github.vedant7007.katori.ml.asr.SpeechLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutingTtsEngineTest {

    private class Recorder(override val supportedLanguages: Set<SpeechLanguage>) : TtsEngine {
        val spoken = mutableListOf<Pair<String, SpeechLanguage>>()
        var stops = 0
        override suspend fun prepare(language: SpeechLanguage) = Outcome.Ok(Unit)
        override suspend fun speak(text: String, language: SpeechLanguage): Outcome<Unit> {
            spoken += text to language
            return Outcome.Ok(Unit)
        }
        override suspend fun stop() { stops++ }
    }

    private val piper = Recorder(setOf(SpeechLanguage.TELUGU, SpeechLanguage.HINDI))
    private val android = Recorder(setOf(SpeechLanguage.ENGLISH_INDIA))
    private val router = RoutingTtsEngine(listOf(piper, android))

    @Test
    fun `each language goes to the engine that claims it`() = runBlocking {
        router.speak("te", SpeechLanguage.TELUGU)
        router.speak("en", SpeechLanguage.ENGLISH_INDIA)
        assertEquals(listOf("te" to SpeechLanguage.TELUGU), piper.spoken)
        assertEquals(listOf("en" to SpeechLanguage.ENGLISH_INDIA), android.spoken)
    }

    @Test
    fun `a language nobody claims is MODEL_NOT_LOADED, never another language's voice`() = runBlocking {
        val r = RoutingTtsEngine(listOf(android)).speak("x", SpeechLanguage.TELUGU)
        assertEquals(UnavailableReason.MODEL_NOT_LOADED, (r as Outcome.Unavailable).reason)
        assertEquals(emptyList<Pair<String, SpeechLanguage>>(), android.spoken)
    }

    @Test
    fun `supported languages are the union, and stop reaches every engine`() = runBlocking {
        assertEquals(SpeechLanguage.entries.toSet(), router.supportedLanguages)
        router.stop()
        assertEquals(1, piper.stops)
        assertEquals(1, android.stops)
    }
}
