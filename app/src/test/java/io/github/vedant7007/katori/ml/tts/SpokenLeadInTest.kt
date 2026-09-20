package io.github.vedant7007.katori.ml.tts

import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.ml.asr.SpeechLanguage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lead-in overlaps generation, finishes before the caller continues, and dies with it. */
class SpokenLeadInTest {

    private class GatedTts(override val supportedLanguages: Set<SpeechLanguage> = SpeechLanguage.entries.toSet()) : TtsEngine {
        val spoken = mutableListOf<String>()
        /** Playback holds until the test releases it, the way real audio takes time. */
        val playing = CompletableDeferred<Unit>()
        var started = false
        var finished = false
        var cancelled = false
        override suspend fun prepare(language: SpeechLanguage) = Outcome.Ok(Unit)
        override suspend fun speak(text: String, language: SpeechLanguage): Outcome<Unit> {
            spoken += text
            started = true
            try {
                playing.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw e
            }
            finished = true
            return Outcome.Ok(Unit)
        }
        override suspend fun stop() = Unit
    }

    @Test
    fun `generation runs while the lead-in plays, and the result waits for the lead-in to finish`() = runBlocking {
        val tts = GatedTts()
        var generatedWhileSpeaking = false

        val turn = async {
            tts.withSpokenLeadIn("checking", SpeechLanguage.TELUGU) {
                withTimeout(5_000) { while (!tts.started) yield() }
                generatedWhileSpeaking = !tts.finished
                "the answer"
            }
        }
        withTimeout(5_000) { while (!tts.started) yield() }
        yield()
        assertFalse("the turn must not complete before the lead-in has finished playing", turn.isCompleted)

        tts.playing.complete(Unit)
        assertEquals("the answer", withTimeout(5_000) { turn.await() })
        assertTrue(generatedWhileSpeaking)
        assertEquals(listOf("checking"), tts.spoken)
    }

    @Test
    fun `disabled, blank, or an unsupported language means generation alone`() = runBlocking {
        val english = GatedTts(setOf(SpeechLanguage.ENGLISH_INDIA))
        assertEquals(1, english.withSpokenLeadIn("x", SpeechLanguage.TELUGU) { 1 })
        assertEquals(2, english.withSpokenLeadIn("   ", SpeechLanguage.ENGLISH_INDIA) { 2 })
        assertEquals(3, english.withSpokenLeadIn("x", SpeechLanguage.ENGLISH_INDIA, enabled = false) { 3 })
        assertTrue(english.spoken.isEmpty())
    }

    @Test
    fun `a failing generation cancels the lead-in rather than leaving it talking`() = runBlocking {
        val tts = GatedTts()
        val failed = runCatching {
            tts.withSpokenLeadIn("checking", SpeechLanguage.HINDI) {
                withTimeout(5_000) { while (!tts.started) yield() }
                throw IllegalStateException("model gone")
            }
        }
        assertTrue(failed.exceptionOrNull() is IllegalStateException)
        assertTrue("the lead-in was cancelled", tts.cancelled)
    }

    @Test
    fun `cancelling the turn cancels the lead-in`() = runBlocking {
        val tts = GatedTts()
        val turn = async {
            tts.withSpokenLeadIn("checking", SpeechLanguage.TELUGU) { CompletableDeferred<Unit>().await() }
        }
        withTimeout(5_000) { while (!tts.started) yield() }
        turn.cancel()
        withTimeout(5_000) { turn.join() }
        assertTrue(tts.cancelled)
    }
}
