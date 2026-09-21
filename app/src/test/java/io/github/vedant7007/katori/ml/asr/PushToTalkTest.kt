package io.github.vedant7007.katori.ml.asr

import io.github.vedant7007.katori.domain.model.UnavailableReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Press, speak, release. What is pinned here: the clip is exactly the frames between press and
 * release, the events are the ones the open-listening flow emits so the screen does not change,
 * a tap is refused, a thumb that never lets go is cut, and a release that arrives before the
 * coroutine starts collecting is still a release. What is not tested: whether a hall is quieter
 * than a laugh. `0031` measured that on the desktop.
 */
class PushToTalkTest {

    private val heard = AsrDecoder.Decoded("दो रोटी और दाल", List(12) { "p$it" })

    private fun engine(decoder: ScriptedDecoder, audio: AudioSource, loader: ScriptedLoader = ScriptedLoader(decoder)) =
        DefaultAsrEngine(testArbiter(loader), audio)

    /** The realme, 21 Sep 01:38: five quiet seconds then a sentence, and the guard read 1.4 pieces/s over the hold. */
    @Test
    fun `a sentence inside a long hold goes to the recogniser as its voiced span, not the whole hold`() = runBlocking {
        val decoder = ScriptedDecoder(heard)
        // 5 s of room (not digital zero, so the microphone counts as live), 1 s of speech, 2 s of room, then the release.
        val room = List(250) { frame(50) }; val speech = List(50) { frame(6_000) }; val after = List(100) { frame(50) }
        val audio = FakeAudio(frames = room + speech + after)
        val events = PushToTalk(engine(decoder, audio), audio).hold(SpeechLanguage.HINDI).toList()
        assertTrue("$events", events.last() is AsrEvent.Result)
        val (samples, _) = decoder.clips.single()
        val keptMs = samples.size / 16
        // Pre-roll before the onset, the second of speech, the hangover after: about 1.9 s of 8, never 8.
        assertTrue("kept $keptMs ms", keptMs in 900..2200)
        // Nothing the hold recorded is lost from inside the sentence: the loud second is all there.
        assertEquals(50 * 320, samples.count { it == 6_000.toShort() || it == (-6_000).toShort() })
    }

    @Test
    fun `the clip is the frames between press and release, and the events are the listen events`() = runBlocking {
        val decoder = ScriptedDecoder(heard)
        lateinit var ptt: PushToTalk
        val audio = EndlessAudio(releaseAfter = 60, onRelease = { ptt.release() })   // 60 frames = 1,200 ms
        ptt = PushToTalk(engine(decoder, audio), audio)
        val events = ptt.hold(SpeechLanguage.HINDI).toList()

        val shape = events.filterNot { it is AsrEvent.Level }
        assertEquals(listOf(AsrEvent.SpeechStarted, AsrEvent.SpeechEnded, AsrEvent.Transcribing), shape.dropLast(1))
        val result = shape.last() as AsrEvent.Result
        assertEquals("दो रोटी और दाल", result.transcript.text)
        assertEquals(SpeechLanguage.HINDI, result.transcript.language)
        assertEquals(1, events.count { it is AsrEvent.Result })
        // One level per frame heard, held frames plus the release tail, and the clip is exactly those.
        val tailFrames = PushToTalk.RELEASE_TAIL_MS / PushToTalk.FRAME_MS
        assertEquals(60 + tailFrames, events.count { it is AsrEvent.Level })
        val (samples, rate) = decoder.clips.single()
        assertEquals(DefaultAsrEngine.SAMPLE_RATE_HZ, rate)
        assertEquals((60 + tailFrames) * 320, samples.size)
        assertEquals(1_200L + PushToTalk.RELEASE_TAIL_MS, result.transcript.audioDurationMs)
    }

    @Test
    fun `speech started means the microphone is live, and silent lead-in frames are neither counted nor kept`() = runBlocking {
        // AudioRecord delivers all-zero buffers before it is really capturing; the presenter's recorder
        // wrote 0.14 s of them and he lost his first word twice. 15 zero frames (300 ms), then signal.
        val decoder = ScriptedDecoder(heard)
        lateinit var ptt: PushToTalk
        var emitted = 0
        val audio = object : AudioSource {
            override fun canRecord() = true
            override fun frames(sampleRateHz: Int, frameMs: Int) = kotlinx.coroutines.flow.flow {
                while (true) {
                    emitted++
                    emit(if (emitted <= 15) ShortArray(320) else frame(6_000))
                    if (emitted == 15 + 40) ptt.release()
                    yield()
                }
            }
        }
        ptt = PushToTalk(engine(decoder, audio), audio)
        val events = ptt.hold(SpeechLanguage.HINDI).toList()
        // No SpeechStarted and no Level until the first frame with signal.
        assertTrue(events.first() is AsrEvent.SpeechStarted)
        assertEquals(40 + PushToTalk.RELEASE_TAIL_MS / PushToTalk.FRAME_MS, events.count { it is AsrEvent.Level })
        assertTrue(events.last() is AsrEvent.Result)
        // The clip holds only live frames: 40 held plus the tail, none of the 15 silent ones.
        val (samples, _) = decoder.clips.single()
        assertEquals((40 + PushToTalk.RELEASE_TAIL_MS / PushToTalk.FRAME_MS) * 320, samples.size)
    }

    @Test
    fun `a slow microphone cannot turn a real hold into a tap by itself, but a tap stays a tap`() = runBlocking {
        // 15 silent frames, then 5 live frames (100 ms of real audio) before release: only the live
        // part counts as held, so this is a tap however long the thumb was physically down.
        val decoder = ScriptedDecoder(heard)
        lateinit var ptt: PushToTalk
        var emitted = 0
        val audio = object : AudioSource {
            override fun canRecord() = true
            override fun frames(sampleRateHz: Int, frameMs: Int) = kotlinx.coroutines.flow.flow {
                while (true) {
                    emitted++
                    emit(if (emitted <= 15) ShortArray(320) else frame(6_000))
                    if (emitted == 15 + 5) ptt.release()
                    yield()
                }
            }
        }
        ptt = PushToTalk(engine(decoder, audio), audio)
        val events = ptt.hold(SpeechLanguage.HINDI).toList()
        assertEquals(UnavailableReason.INPUT_NOT_USABLE, (events.last() as AsrEvent.Unavailable).outcome.reason)
        assertTrue(decoder.clips.isEmpty())
    }

    @Test
    fun `a tap is not a sentence`() = runBlocking {
        val decoder = ScriptedDecoder(heard)
        lateinit var ptt: PushToTalk
        val audio = EndlessAudio(releaseAfter = 5, onRelease = { ptt.release() })    // 100 ms
        ptt = PushToTalk(engine(decoder, audio), audio)
        val events = ptt.hold(SpeechLanguage.TELUGU).toList()
        assertTrue(AsrEvent.SpeechEnded in events)
        val last = events.last() as AsrEvent.Unavailable
        assertEquals(UnavailableReason.INPUT_NOT_USABLE, last.outcome.reason)
        // The release tail does not rescue a tap: 100 ms held plus 300 ms of tail is still a tap.
        assertTrue(decoder.clips.isEmpty())
    }

    @Test
    fun `a release that arrives before collection starts is still a release`() = runBlocking {
        val decoder = ScriptedDecoder(heard)
        val audio = EndlessAudio()                                                  // would never let go
        val ptt = PushToTalk(engine(decoder, audio), audio)
        val flow = ptt.hold(SpeechLanguage.HINDI)
        ptt.release()                                                               // thumb up before the coroutine ran
        val events = flow.toList()
        assertEquals(UnavailableReason.INPUT_NOT_USABLE, (events.last() as AsrEvent.Unavailable).outcome.reason)
        assertTrue("only the release tail should have been recorded", audio.emitted <= PushToTalk.RELEASE_TAIL_MS / PushToTalk.FRAME_MS + 1)
        assertTrue(decoder.clips.isEmpty())
    }

    @Test
    fun `a thumb that never lets go is cut at the maximum hold and still transcribed`() = runBlocking {
        // Twenty seconds of speech decodes to far more than a sentence's worth of pieces; scripting the
        // one-sentence result here would trip the engine's non-speech density guard, correctly.
        val decoder = ScriptedDecoder(AsrDecoder.Decoded("दो रोटी और दाल", List(140) { "p$it" }))
        val audio = EndlessAudio()
        val ptt = PushToTalk(engine(decoder, audio), audio)
        val events = ptt.hold(SpeechLanguage.HINDI).toList()
        assertTrue(events.last() is AsrEvent.Result)
        val (samples, _) = decoder.clips.single()
        // The cap plus the tail: the thumb is still down, so the tail is 300 ms more of the same hold.
        assertEquals((PushToTalk.MAX_HOLD_MS + PushToTalk.RELEASE_TAIL_MS) * 16, samples.size)
        assertEquals((PushToTalk.MAX_HOLD_MS + PushToTalk.RELEASE_TAIL_MS) / PushToTalk.FRAME_MS, events.count { it is AsrEvent.Level })
    }

    @Test
    fun `without the permission nothing is recorded`() = runBlocking {
        val decoder = ScriptedDecoder(heard)
        val audio = FakeAudio(permitted = false, frames = loud(100))
        val events = PushToTalk(engine(decoder, audio), audio).hold(SpeechLanguage.HINDI).toList()
        assertEquals(1, events.size)
        assertEquals(UnavailableReason.PERMISSION_DENIED, (events.single() as AsrEvent.Unavailable).outcome.reason)
    }

    @Test
    fun `a second hold during a hold fails, and cancelling the first frees the microphone for a third`() = runBlocking {
        val decoder = ScriptedDecoder(heard)
        val audio = EndlessAudio()
        val ptt = PushToTalk(engine(decoder, audio), audio)
        val first = launch { ptt.hold(SpeechLanguage.HINDI).toList() }
        repeat(20) { yield() }
        val second = ptt.hold(SpeechLanguage.HINDI).first()
        assertEquals(UnavailableReason.INTERNAL_ERROR, (second as AsrEvent.Unavailable).outcome.reason)
        first.cancel()
        first.join()
        assertTrue(decoder.clips.isEmpty())                                         // cancelled, not transcribed
        // Same instance, same microphone: the guard was released by the cancellation, so a third
        // hold records, and a thumb lifting 30 frames in ends it with a transcript.
        launch { repeat(30) { yield() }; ptt.release() }
        assertTrue(ptt.hold(SpeechLanguage.HINDI).toList().last() is AsrEvent.Result)
        assertEquals(1, decoder.clips.size)
    }

    @Test
    fun `a model that will not load is reported, not thrown`() = runBlocking {
        val decoder = ScriptedDecoder(heard)
        lateinit var ptt: PushToTalk
        val audio = EndlessAudio(releaseAfter = 40, onRelease = { ptt.release() })
        ptt = PushToTalk(engine(decoder, audio, ScriptedLoader(decoder, failWith = "tokens.txt missing")), audio)
        val last = ptt.hold(SpeechLanguage.HINDI).toList().last() as AsrEvent.Unavailable
        assertEquals(UnavailableReason.MODEL_LOAD_FAILED, last.outcome.reason)
    }
}
