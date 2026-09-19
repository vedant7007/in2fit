package io.github.vedant7007.katori.ml.tts

import io.github.vedant7007.katori.domain.DefaultModelArbiter
import io.github.vedant7007.katori.domain.DeviceMemory
import io.github.vedant7007.katori.domain.MeasurementLog
import io.github.vedant7007.katori.domain.MeasurementRow
import io.github.vedant7007.katori.domain.ModelHandle
import io.github.vedant7007.katori.domain.ModelLoader
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import io.github.vedant7007.katori.ml.asr.SpeechLanguage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The engine's job is lease use, routing by language, passing the text through unchanged, and
 * stopping. None of that needs sherpa-onnx or an AudioTrack, so it runs here against the real
 * [DefaultModelArbiter] with a fake voice behind the loader seam and a fake sink.
 *
 * Whether the voice sounds like Telugu is not a question this file can answer, and it does not
 * pretend to. That needs a fluent listener and a device.
 */
class PiperTtsEngineTest {

    // --- doubles ------------------------------------------------------------------------------

    private class FakeVoice(override val sampleRateHz: Int = 22_050) : PiperVoice {
        val spoken = mutableListOf<String>()
        var closed = false
        var abandoned = false
        /** Set to make synthesis wait until the test lets it go, for the cancellation cases. */
        var gate: CompletableDeferred<Unit>? = null

        override fun synthesise(text: String, keepGoing: () -> Boolean): FloatArray {
            spoken += text
            gate?.let { runBlocking { it.await() } }
            if (!keepGoing()) {
                abandoned = true
                return FloatArray(0)
            }
            // One sample per character: enough for the sink to see something proportional.
            return FloatArray(text.length) { 0.1f }
        }

        override fun close() { closed = true }
    }

    private class FakeLoader(val voice: FakeVoice = FakeVoice(), val failWith: String? = null) : ModelLoader {
        var loads = 0
        override suspend fun load(handle: ModelHandle): Any {
            loads++
            failWith?.let { throw IllegalStateException(it) }
            return voice
        }
        override fun unload(handle: ModelHandle, native: Any) { (native as PiperVoice).close() }
    }

    private class FakeSink(val outcome: Outcome<Unit> = Outcome.Ok(Unit)) : AudioSink {
        val played = mutableListOf<Pair<Int, Int>>() // samples, rate
        var stops = 0
        override suspend fun play(pcm: FloatArray, sampleRateHz: Int): Outcome<Unit> {
            played += pcm.size to sampleRateHz
            return outcome
        }
        override fun stop() { stops++ }
    }

    private object PlentyOfMemory : DeviceMemory {
        override fun totalBytes() = 8L shl 30
        override fun availableBytes() = 3L shl 30
        override fun lowMemoryThresholdBytes() = 432L shl 20
        override fun processPssBytes() = 126L shl 20
    }

    private class NoLog : MeasurementLog {
        override fun append(row: MeasurementRow) = Unit
        override fun rows(): List<MeasurementRow> = emptyList()
    }

    private fun arbiter(loader: ModelLoader) =
        DefaultModelArbiter(PlentyOfMemory, loader, NoLog(), buildTag = "test", clockMs = { 0L })

    private fun engine(loader: FakeLoader = FakeLoader(), sink: FakeSink = FakeSink()) =
        PiperTtsEngine(arbiter(loader), sink, synthesisDispatcher = Dispatchers.Default)

    // --- the contract -------------------------------------------------------------------------

    @Test
    fun `speaks the text unchanged through a leased voice and plays it at the voice's rate`() = runBlocking {
        val loader = FakeLoader(FakeVoice(sampleRateHz = 22_050))
        val sink = FakeSink()
        val text = "ఇడ్లీ సాంబార్"

        val result = engine(loader, sink).speak(text, SpeechLanguage.TELUGU)

        assertEquals(Outcome.Ok(Unit), result)
        assertEquals(listOf(text), loader.voice.spoken)
        assertEquals(listOf(text.length to 22_050), sink.played)
    }

    @Test
    fun `the voice stays resident and unpinned after speaking, so the next call does not reload`() = runBlocking {
        val loader = FakeLoader()
        val engine = engine(loader)
        engine.speak("one", SpeechLanguage.TELUGU)
        engine.speak("two", SpeechLanguage.TELUGU)
        assertEquals("one load for two utterances", 1, loader.loads)
        assertFalse("still resident, not unloaded", loader.voice.closed)
    }

    @Test
    fun `a language with no voice is MODEL_NOT_LOADED and nothing is loaded or played`() = runBlocking {
        val loader = FakeLoader()
        val sink = FakeSink()
        val result = engine(loader, sink).speak("hello", SpeechLanguage.ENGLISH_INDIA)
        assertEquals(UnavailableReason.MODEL_NOT_LOADED, (result as Outcome.Unavailable).reason)
        assertEquals(0, loader.loads)
        assertTrue(sink.played.isEmpty())
    }

    @Test
    fun `supported languages are exactly the voices it was given`() {
        assertEquals(setOf(SpeechLanguage.TELUGU, SpeechLanguage.HINDI), engine().supportedLanguages)
    }

    @Test
    fun `a voice that fails to load surfaces the arbiter's MODEL_LOAD_FAILED with the reason`() = runBlocking {
        val result = engine(FakeLoader(failWith = "tokens.txt missing")).speak("x", SpeechLanguage.TELUGU)
        val u = result as Outcome.Unavailable
        assertEquals(UnavailableReason.MODEL_LOAD_FAILED, u.reason)
        assertTrue("detail names the cause: ${u.detail}", u.detail!!.contains("tokens.txt missing"))
    }

    @Test
    fun `refused audio focus comes back as CANCELLED from the sink`() = runBlocking {
        val sink = FakeSink(Outcome.Unavailable(UnavailableReason.CANCELLED, "audio focus refused"))
        val result = engine(sink = sink).speak("x", SpeechLanguage.TELUGU)
        assertEquals(UnavailableReason.CANCELLED, (result as Outcome.Unavailable).reason)
    }

    @Test
    fun `blank text is spoken as nothing, without touching the voice`() = runBlocking {
        val loader = FakeLoader()
        assertEquals(Outcome.Ok(Unit), engine(loader).speak("   ", SpeechLanguage.TELUGU))
        assertEquals(0, loader.loads)
    }

    @Test
    fun `stop during synthesis abandons the audio rather than playing it late`() = runBlocking {
        val voice = FakeVoice().apply { gate = CompletableDeferred() }
        val sink = FakeSink()
        val engine = engine(FakeLoader(voice), sink)

        val speaking = async { engine.speak("long sentence", SpeechLanguage.TELUGU) }
        withTimeout(5_000) { while (voice.spoken.isEmpty()) kotlinx.coroutines.yield() }
        engine.stop()
        voice.gate!!.complete(Unit)

        val result = withTimeout(5_000) { speaking.await() }
        assertEquals(UnavailableReason.CANCELLED, (result as Outcome.Unavailable).reason)
        assertTrue("synthesis saw keepGoing() == false", voice.abandoned)
        assertTrue("nothing reached the sink", sink.played.isEmpty())
        assertEquals("the sink was told to stop too", 1, sink.stops)
    }

    @Test
    fun `cancelling the caller stops synthesis`() = runBlocking {
        val voice = FakeVoice().apply { gate = CompletableDeferred() }
        val sink = FakeSink()
        val engine = engine(FakeLoader(voice), sink)

        val speaking = async { engine.speak("long sentence", SpeechLanguage.TELUGU) }
        withTimeout(5_000) { while (voice.spoken.isEmpty()) kotlinx.coroutines.yield() }
        speaking.cancel()
        voice.gate!!.complete(Unit)
        withTimeout(5_000) { speaking.join() }

        assertTrue(speaking.isCancelled)
        assertTrue(voice.abandoned)
        assertTrue(sink.played.isEmpty())
    }

    @Test
    fun `prepare warms the voice without playing anything`() = runBlocking {
        val loader = FakeLoader()
        val sink = FakeSink()
        assertEquals(Outcome.Ok(Unit), engine(loader, sink).prepare(SpeechLanguage.HINDI))
        assertEquals(1, loader.loads)
        assertTrue(sink.played.isEmpty())
    }
}
