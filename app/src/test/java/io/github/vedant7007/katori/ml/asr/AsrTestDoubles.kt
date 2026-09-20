package io.github.vedant7007.katori.ml.asr

import io.github.vedant7007.katori.domain.DefaultModelArbiter
import io.github.vedant7007.katori.domain.DeviceMemory
import io.github.vedant7007.katori.domain.MeasurementLog
import io.github.vedant7007.katori.domain.MeasurementRow
import io.github.vedant7007.katori.domain.ModelHandle
import io.github.vedant7007.katori.domain.ModelLoader
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield

/**
 * The doubles behind the `ml/asr` tests. Everything the engine and the push-to-talk capture do
 * is exercised against the REAL `DefaultModelArbiter`; only the microphone and the recogniser
 * are scripted.
 */

/** Returns whatever it was last told to, and remembers every clip it was handed. */
class ScriptedDecoder(var next: AsrDecoder.Decoded) : AsrDecoder {
    val clips = mutableListOf<Pair<ShortArray, Int>>()
    var closed = false
    override fun decode(samples: ShortArray, sampleRateHz: Int): AsrDecoder.Decoded {
        clips += samples to sampleRateHz
        return next
    }
    override fun close() { closed = true }
}

class ScriptedLoader(val decoder: ScriptedDecoder, val failWith: String? = null) : ModelLoader {
    val loaded = mutableListOf<ModelHandle>()
    override suspend fun load(handle: ModelHandle): Any {
        if (failWith != null) throw IllegalStateException(failWith)
        loaded += handle
        return decoder
    }
    override fun unload(handle: ModelHandle, native: Any) { (native as AsrDecoder).close() }
}

/** A fixed list of frames; with [hang] it then stays open until cancelled, like a real microphone. */
class FakeAudio(val permitted: Boolean = true, val frames: List<ShortArray>, val hang: Boolean = false) : AudioSource {
    var requestedRate = -1
    var requestedFrameMs = -1
    override fun canRecord() = permitted
    override fun frames(sampleRateHz: Int, frameMs: Int): Flow<ShortArray> {
        requestedRate = sampleRateHz; requestedFrameMs = frameMs
        return if (hang) flow { frames.forEach { emit(it) }; awaitCancellation() } else frames.asFlow()
    }
}

/**
 * A microphone that never stops on its own, with a thumb attached: after [releaseAfter] frames it
 * calls [onRelease], which is how a push-to-talk test lets go at a known instant.
 */
class EndlessAudio(val releaseAfter: Int = Int.MAX_VALUE, val onRelease: () -> Unit = {}) : AudioSource {
    var emitted = 0
    override fun canRecord() = true
    override fun frames(sampleRateHz: Int, frameMs: Int): Flow<ShortArray> = flow {
        val frame = ShortArray(sampleRateHz * frameMs / 1000) { if (it % 2 == 0) 6_000 else -6_000 }
        while (true) {
            emit(frame)
            emitted++
            if (emitted == releaseAfter) onRelease()
            yield()
        }
    }
}

object EightGigabytes : DeviceMemory {
    override fun totalBytes() = 8L shl 30
    override fun availableBytes() = 3L shl 30
    override fun lowMemoryThresholdBytes() = 432L shl 20
    override fun processPssBytes() = 126L shl 20
}

class NoLog : MeasurementLog {
    override fun append(row: MeasurementRow) = Unit
    override fun rows() = emptyList<MeasurementRow>()
}

fun testArbiter(loader: ModelLoader) = DefaultModelArbiter(EightGigabytes, loader, NoLog(), buildTag = "test", clockMs = { 0L })

// 20 ms frames at 16 kHz = 320 samples. Level 0 is digital silence; 6000 is about 0.18 rms.
fun frame(amplitude: Int) = ShortArray(320) { if (it % 2 == 0) amplitude.toShort() else (-amplitude).toShort() }
fun quiet(n: Int) = List(n) { frame(0) }
fun loud(n: Int) = List(n) { frame(6_000) }
