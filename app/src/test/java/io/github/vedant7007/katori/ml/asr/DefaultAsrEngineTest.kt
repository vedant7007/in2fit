package io.github.vedant7007.katori.ml.asr

import io.github.vedant7007.katori.domain.DefaultModelArbiter
import io.github.vedant7007.katori.domain.DeviceMemory
import io.github.vedant7007.katori.domain.MeasurementLog
import io.github.vedant7007.katori.domain.MeasurementRow
import io.github.vedant7007.katori.domain.ModelHandle
import io.github.vedant7007.katori.domain.ModelLoader
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The engine's job is the event sequence, the clip it cuts, and how a decoder's output becomes an
 * Outcome. All of that is exercised here against the REAL arbiter with a scripted loader, so the
 * lease path is the one the app will take. What is not tested here, and not pretended: whether
 * IndicConformer transcribes anyone correctly. That is `0021` on the desktop and the recorded set
 * on the phone.
 */
class DefaultAsrEngineTest {

    // --- doubles ------------------------------------------------------------------------------

    private class ScriptedDecoder(var next: AsrDecoder.Decoded) : AsrDecoder {
        val clips = mutableListOf<Pair<ShortArray, Int>>()
        var closed = false
        override fun decode(samples: ShortArray, sampleRateHz: Int): AsrDecoder.Decoded {
            clips += samples to sampleRateHz
            return next
        }
        override fun close() { closed = true }
    }

    private class ScriptedLoader(val decoder: ScriptedDecoder, val failWith: String? = null) : ModelLoader {
        val loaded = mutableListOf<ModelHandle>()
        override suspend fun load(handle: ModelHandle): Any {
            if (failWith != null) throw IllegalStateException(failWith)
            loaded += handle
            return decoder
        }
        override fun unload(handle: ModelHandle, native: Any) { (native as AsrDecoder).close() }
    }

    private class FakeAudio(val permitted: Boolean = true, val frames: List<ShortArray>, val hang: Boolean = false) : AudioSource {
        var requestedRate = -1
        var requestedFrameMs = -1
        override fun canRecord() = permitted
        override fun frames(sampleRateHz: Int, frameMs: Int): Flow<ShortArray> {
            requestedRate = sampleRateHz; requestedFrameMs = frameMs
            return if (hang) flow { frames.forEach { emit(it) }; awaitCancellation() } else frames.asFlow()
        }
    }

    private object EightGigabytes : DeviceMemory {
        override fun totalBytes() = 8L shl 30
        override fun availableBytes() = 3L shl 30
        override fun lowMemoryThresholdBytes() = 432L shl 20
        override fun processPssBytes() = 126L shl 20
    }

    private class NoLog : MeasurementLog {
        override fun append(row: MeasurementRow) = Unit
        override fun rows() = emptyList<MeasurementRow>()
    }

    private fun arbiter(loader: ModelLoader) = DefaultModelArbiter(EightGigabytes, loader, NoLog(), buildTag = "test", clockMs = { 0L })

    // 20 ms frames at 16 kHz = 320 samples. Level 0 is digital silence; 6000 is about 0.18 rms.
    private fun frame(amplitude: Int) = ShortArray(320) { if (it % 2 == 0) amplitude.toShort() else (-amplitude).toShort() }
    private fun quiet(n: Int) = List(n) { frame(0) }
    private fun loud(n: Int) = List(n) { frame(6_000) }

    private val speechDecoded = AsrDecoder.Decoded("నేను రెండు రొట్టెలు తిన్నాను", List(22) { "p$it" })
    private val endpointer = { EnergyEndpointer(frameMs = 20, calibrationMs = 200, onsetMs = 60, hangoverMs = 400, maxWaitMs = 4_000) }

    private fun engine(audio: AudioSource, decoder: ScriptedDecoder = ScriptedDecoder(speechDecoded), loader: ModelLoader = ScriptedLoader(decoder)) =
        DefaultAsrEngine(arbiter(loader), audio, endpointer)

    // --- listen -------------------------------------------------------------------------------

    @Test
    fun `listen without the permission is PERMISSION_DENIED and touches nothing`() = runBlocking {
        val decoder = ScriptedDecoder(speechDecoded)
        val loader = ScriptedLoader(decoder)
        val events = engine(FakeAudio(permitted = false, frames = loud(100)), decoder, loader).listen(SpeechLanguage.TELUGU).toList()
        assertEquals(1, events.size)
        assertEquals(UnavailableReason.PERMISSION_DENIED, (events.single() as AsrEvent.Unavailable).outcome.reason)
        assertTrue(loader.loaded.isEmpty())
    }

    @Test
    fun `listen emits levels, start, end, transcribing and exactly one result, in that order`() = runBlocking {
        val decoder = ScriptedDecoder(speechDecoded)
        val audio = FakeAudio(frames = quiet(20) + loud(60) + quiet(30))
        val events = engine(audio, decoder).listen(SpeechLanguage.TELUGU).toList()

        assertEquals(DefaultAsrEngine.SAMPLE_RATE_HZ, audio.requestedRate)
        assertEquals(20, audio.requestedFrameMs)

        val shape = events.filterNot { it is AsrEvent.Level }
        assertEquals(listOf(AsrEvent.SpeechStarted, AsrEvent.SpeechEnded, AsrEvent.Transcribing), shape.dropLast(1))
        val result = shape.last() as AsrEvent.Result
        assertEquals("నేను రెండు రొట్టెలు తిన్నాను", result.transcript.text)
        assertEquals(SpeechLanguage.TELUGU, result.transcript.language)
        assertEquals(AsrConfidence.HIGH, result.transcript.confidence)
        assertEquals(1, events.count { it is AsrEvent.Result })

        // A level for every frame heard, and the meter reads the loud frames as loud.
        val levels = events.filterIsInstance<AsrEvent.Level>()
        assertEquals(20 + 60 + 20, levels.size)   // capture stops at the hangover, 20 quiet frames in
        assertTrue(levels[30].rms > 0.1f && levels[5].rms == 0f)

        // The clip is pre-roll (200 ms = 10 frames) + 60 loud + 20 hangover frames, at 16 kHz.
        val (samples, rate) = decoder.clips.single()
        assertEquals(DefaultAsrEngine.SAMPLE_RATE_HZ, rate)
        assertEquals((10 + 60 + 20) * 320, samples.size)
        assertEquals((10 + 60 + 20) * 20L, result.transcript.audioDurationMs)
    }

    @Test
    fun `listen with nobody speaking is INPUT_NOT_USABLE and the model is never asked`() = runBlocking {
        val decoder = ScriptedDecoder(speechDecoded)
        val loader = ScriptedLoader(decoder)
        val events = engine(FakeAudio(frames = quiet(250)), decoder, loader).listen(SpeechLanguage.HINDI).toList()
        val last = events.last() as AsrEvent.Unavailable
        assertEquals(UnavailableReason.INPUT_NOT_USABLE, last.outcome.reason)
        assertTrue(events.none { it is AsrEvent.SpeechStarted || it is AsrEvent.Transcribing })
        assertTrue(loader.loaded.isEmpty())
        assertTrue(decoder.clips.isEmpty())
    }

    @Test
    fun `a cough is too short to transcribe`() = runBlocking {
        val decoder = ScriptedDecoder(speechDecoded)
        // 5 loud frames = 100 ms of speech, under MIN_SPEECH_MS.
        val events = engine(FakeAudio(frames = quiet(20) + loud(5) + quiet(30)), decoder).listen(SpeechLanguage.TELUGU).toList()
        assertTrue(AsrEvent.SpeechStarted in events)
        assertEquals(UnavailableReason.INPUT_NOT_USABLE, (events.last() as AsrEvent.Unavailable).outcome.reason)
        assertTrue(decoder.clips.isEmpty())
    }

    @Test
    fun `a second listen while one is running fails instead of interleaving audio`() = runBlocking {
        val engine = engine(FakeAudio(frames = quiet(5), hang = true))
        val first = launch { engine.listen(SpeechLanguage.TELUGU).toList() }
        // Let the first capture start.
        repeat(20) { yield() }
        val second = engine.listen(SpeechLanguage.TELUGU).first()
        assertEquals(UnavailableReason.INTERNAL_ERROR, (second as AsrEvent.Unavailable).outcome.reason)
        first.cancel()
        first.join()
        // Cancelling released the microphone slot: a fresh listen is allowed again.
        val third = engine.listen(SpeechLanguage.TELUGU).first()
        assertTrue(third !is AsrEvent.Unavailable)
    }

    @Test
    fun `a microphone failure is a reason, not a crash`() = runBlocking {
        val broken = object : AudioSource {
            override fun canRecord() = true
            override fun frames(sampleRateHz: Int, frameMs: Int): Flow<ShortArray> = flow { throw IllegalStateException("AudioRecord did not start") }
        }
        val events = engine(broken).listen(SpeechLanguage.TELUGU).toList()
        val only = events.single() as AsrEvent.Unavailable
        assertEquals(UnavailableReason.INTERNAL_ERROR, only.outcome.reason)
        assertTrue(only.outcome.detail!!.contains("AudioRecord did not start"))
    }

    // --- transcribe ---------------------------------------------------------------------------

    private fun clip(ms: Int) = AudioClip(ShortArray(16 * ms), 16_000)

    @Test
    fun `phantom pieces on silence are refused, not logged as a meal`() = runBlocking {
        // What the te model actually did with 3 s of digital silence on the desktop (0021).
        val decoder = ScriptedDecoder(AsrDecoder.Decoded("ఈారు", listOf("ఈ", "ారు")))
        val r = engine(FakeAudio(frames = emptyList()), decoder).transcribe(clip(3_000), SpeechLanguage.TELUGU)
        val u = r as Outcome.Unavailable
        assertEquals(UnavailableReason.INPUT_NOT_USABLE, u.reason)
        assertTrue(u.detail!!.contains("2 pieces in 3000 ms"))
    }

    @Test
    fun `empty text is refused`() = runBlocking {
        val decoder = ScriptedDecoder(AsrDecoder.Decoded("  ", emptyList()))
        val r = engine(FakeAudio(frames = emptyList()), decoder).transcribe(clip(3_000), SpeechLanguage.ENGLISH_INDIA)
        assertEquals(UnavailableReason.INPUT_NOT_USABLE, (r as Outcome.Unavailable).reason)
    }

    @Test
    fun `dense output is HIGH and an unknown piece makes it LOW`() = runBlocking {
        val decoder = ScriptedDecoder(AsrDecoder.Decoded(" two rotis and dal ", List(12) { "p" }))
        val engine = engine(FakeAudio(frames = emptyList()), decoder)
        val high = engine.transcribe(clip(2_000), SpeechLanguage.ENGLISH_INDIA) as Outcome.Ok
        assertEquals("two rotis and dal", high.value.text)
        assertEquals(AsrConfidence.HIGH, high.value.confidence)
        assertEquals(2_000L, high.value.audioDurationMs)

        decoder.next = AsrDecoder.Decoded("two rotis and <unk>", List(11) { "p" } + "<unk>")
        val low = engine.transcribe(clip(2_000), SpeechLanguage.ENGLISH_INDIA) as Outcome.Ok
        assertEquals(AsrConfidence.LOW, low.value.confidence)
    }

    @Test
    fun `a clip too short for a feature frame is refused before the arbiter is asked`() = runBlocking {
        val decoder = ScriptedDecoder(speechDecoded)
        val loader = ScriptedLoader(decoder)
        val r = engine(FakeAudio(frames = emptyList()), decoder, loader).transcribe(clip(100), SpeechLanguage.TELUGU)
        assertEquals(UnavailableReason.INPUT_NOT_USABLE, (r as Outcome.Unavailable).reason)
        assertTrue(loader.loaded.isEmpty())
    }

    @Test
    fun `a model that will not load is MODEL_LOAD_FAILED from the arbiter, with the path`() = runBlocking {
        val decoder = ScriptedDecoder(speechDecoded)
        val r = engine(FakeAudio(frames = emptyList()), decoder, ScriptedLoader(decoder, failWith = "tokens.txt missing")).transcribe(clip(2_000), SpeechLanguage.TELUGU)
        val u = r as Outcome.Unavailable
        assertEquals(UnavailableReason.MODEL_LOAD_FAILED, u.reason)
        assertTrue(u.detail!!.contains("asr/te/model.int8.onnx") && u.detail!!.contains("tokens.txt missing"))
    }

    @Test
    fun `transcribe hands the clip and its own sample rate to the decoder`() = runBlocking {
        val decoder = ScriptedDecoder(speechDecoded)
        val clip = AudioClip(ShortArray(22_050 * 2) { (it % 100).toShort() }, 22_050)
        engine(FakeAudio(frames = emptyList()), decoder).transcribe(clip, SpeechLanguage.TELUGU)
        val (samples, rate) = decoder.clips.single()
        assertEquals(22_050, rate)
        assertTrue(samples.contentEquals(clip.samples))
    }

    // --- models -------------------------------------------------------------------------------

    @Test
    fun `prepare loads the handle for the language and runs one throwaway decode of silence`() = runBlocking {
        val decoder = ScriptedDecoder(AsrDecoder.Decoded("ఈారు", listOf("ఈ", "ారు")))   // what silence really decodes to
        val loader = ScriptedLoader(decoder)
        val engine = engine(FakeAudio(frames = emptyList()), decoder, loader)
        assertTrue(engine.prepare(SpeechLanguage.HINDI) is Outcome.Ok)
        assertEquals(listOf("asr.indicconformer-hi"), loader.loaded.map { it.id })
        assertEquals("asr/hi/model.int8.onnx", loader.loaded.single().relativePath)

        // The warm-up is one decode of WARM_UP_MS of digital silence, at the model's rate.
        val (samples, rate) = decoder.clips.single()
        assertEquals(DefaultAsrEngine.SAMPLE_RATE_HZ, rate)
        assertEquals(DefaultAsrEngine.SAMPLE_RATE_HZ * DefaultAsrEngine.WARM_UP_MS / 1000, samples.size)
        assertTrue(samples.all { it == 0.toShort() })

        // Warm means resident and evictable, not pinned: a second prepare decodes again without reloading.
        assertTrue(engine.prepare(SpeechLanguage.HINDI) is Outcome.Ok)
        assertEquals(1, loader.loaded.size)
        assertEquals(2, decoder.clips.size)
    }

    @Test
    fun `prepare reports a model that will not load, and warms nothing`() = runBlocking {
        val decoder = ScriptedDecoder(speechDecoded)
        val r = engine(FakeAudio(frames = emptyList()), decoder, ScriptedLoader(decoder, failWith = "model missing")).prepare(SpeechLanguage.TELUGU)
        assertEquals(UnavailableReason.MODEL_LOAD_FAILED, (r as Outcome.Unavailable).reason)
        assertTrue(decoder.clips.isEmpty())
    }

    @Test
    fun `every language has a handle and its tokens file sits beside the model`() {
        for (language in SpeechLanguage.entries) {
            val h = AsrModels.handleFor(language)
            assertTrue(h.relativePath.startsWith("asr/") && h.relativePath.endsWith("/model.int8.onnx"))
            assertEquals(h.relativePath.substringBeforeLast('/') + "/tokens.txt", AsrModels.tokensFileFor(h.relativePath))
            assertTrue(h.estimatedResidentBytes > 100L shl 20)
        }
        assertEquals("asr/te/tokens.txt", AsrModels.tokensFileFor("asr/te/model.int8.onnx"))
        // The English model is NVIDIA's FastConformer, not IndicConformer, and its id must say so.
        assertEquals("asr.fastconformer-en", AsrModels.handleFor(SpeechLanguage.ENGLISH_INDIA).id)
        assertEquals(3, SpeechLanguage.entries.map { AsrModels.handleFor(it).id }.distinct().size)
    }

    @Test
    fun `supported languages are the three the contract names`() {
        val engine = engine(FakeAudio(frames = emptyList()))
        assertEquals(SpeechLanguage.entries.toSet(), engine.supportedLanguages)
        if (engine.supportedLanguages.isEmpty()) fail("an engine with no languages is a stub")
    }
}
