package io.github.vedant7007.katori.ml.tts

import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import io.github.vedant7007.katori.ml.asr.SpeechLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingTtsEngineTest {

    /** Claims [supportedLanguages]; actually has a voice only for [installed]. */
    private class Recorder(
        override val supportedLanguages: Set<SpeechLanguage>,
        private val installed: Set<SpeechLanguage> = supportedLanguages,
        private val answer: Outcome<Unit> = Outcome.Ok(Unit),
    ) : TtsEngine {
        val spoken = mutableListOf<Pair<String, SpeechLanguage>>()
        var stops = 0
        override suspend fun prepare(language: SpeechLanguage) = voiceOr(language) { Outcome.Ok(Unit) }
        override suspend fun speak(text: String, language: SpeechLanguage) = voiceOr(language) {
            spoken += text to language
            answer
        }
        override suspend fun stop() { stops++ }
        private inline fun voiceOr(language: SpeechLanguage, body: () -> Outcome<Unit>): Outcome<Unit> =
            if (language in installed) body()
            else Outcome.Unavailable(UnavailableReason.MODEL_NOT_LOADED, "no ${language.tag} voice here")
    }

    private val all = SpeechLanguage.entries.toSet()

    @Test
    fun `each language goes to the first engine that has a voice for it`() = runBlocking {
        val platform = Recorder(all, installed = setOf(SpeechLanguage.ENGLISH_INDIA))
        val piper = Recorder(setOf(SpeechLanguage.TELUGU, SpeechLanguage.HINDI))
        val router = RoutingTtsEngine(listOf(platform, piper))

        router.speak("te", SpeechLanguage.TELUGU)
        router.speak("en", SpeechLanguage.ENGLISH_INDIA)

        assertEquals(listOf("en" to SpeechLanguage.ENGLISH_INDIA), platform.spoken)
        assertEquals(listOf("te" to SpeechLanguage.TELUGU), piper.spoken)
    }

    @Test
    fun `a device with an offline platform voice never reaches the fallback for that language`() = runBlocking {
        val platform = Recorder(all)
        val piper = Recorder(setOf(SpeechLanguage.TELUGU, SpeechLanguage.HINDI))
        RoutingTtsEngine(listOf(platform, piper)).speak("te", SpeechLanguage.TELUGU)
        assertEquals(1, platform.spoken.size)
        assertTrue(piper.spoken.isEmpty())
    }

    @Test
    fun `only MODEL_NOT_LOADED falls through, a real failure is returned as is`() = runBlocking {
        val cancelled = Outcome.Unavailable(UnavailableReason.CANCELLED, "call in progress")
        val platform = Recorder(all, answer = cancelled)
        val piper = Recorder(setOf(SpeechLanguage.TELUGU))
        assertEquals(cancelled, RoutingTtsEngine(listOf(platform, piper)).speak("te", SpeechLanguage.TELUGU))
        assertTrue(piper.spoken.isEmpty())
    }

    @Test
    fun `a language nobody has a voice for is MODEL_NOT_LOADED naming what was tried, never another language's voice`() = runBlocking {
        val platform = Recorder(all, installed = emptySet())
        val piper = Recorder(setOf(SpeechLanguage.HINDI))
        val r = RoutingTtsEngine(listOf(platform, piper)).speak("x", SpeechLanguage.TELUGU) as Outcome.Unavailable
        assertEquals(UnavailableReason.MODEL_NOT_LOADED, r.reason)
        assertTrue(r.detail!!, r.detail!!.contains("no te voice here"))
        assertTrue(platform.spoken.isEmpty() && piper.spoken.isEmpty())
    }

    @Test
    fun `supported languages are the union, and stop reaches every engine`() = runBlocking {
        val platform = Recorder(setOf(SpeechLanguage.ENGLISH_INDIA))
        val piper = Recorder(setOf(SpeechLanguage.TELUGU, SpeechLanguage.HINDI))
        val router = RoutingTtsEngine(listOf(platform, piper))
        assertEquals(all, router.supportedLanguages)
        router.stop()
        assertEquals(1, platform.stops)
        assertEquals(1, piper.stops)
    }
}
