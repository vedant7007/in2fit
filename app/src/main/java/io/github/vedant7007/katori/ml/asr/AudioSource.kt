package io.github.vedant7007.katori.ml.asr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Where PCM frames come from. The seam between [DefaultAsrEngine] and the microphone, so the
 * endpointing and the event sequence are tested on the JVM with synthetic frames.
 */
interface AudioSource {

    /** Whether recording may start at all. False becomes PERMISSION_DENIED before any capture. */
    fun canRecord(): Boolean

    /**
     * Mono 16-bit frames of [frameMs] each at [sampleRateHz], until the collector cancels.
     * Cancelling releases the microphone; that is the only way capture stops.
     */
    fun frames(sampleRateHz: Int, frameMs: Int): Flow<ShortArray>
}

/**
 * The phone's microphone through [AudioRecord]. NEVER RUN ON A DEVICE; the flow is what the
 * contract describes and nothing more.
 *
 * `VOICE_RECOGNITION` rather than `MIC`: it asks the platform for the tuning meant for a
 * recogniser, without the aggressive gain and noise processing a voice call gets, which is what
 * the model was trained without.
 */
class AndroidAudioSource(private val context: Context) : AudioSource {

    override fun canRecord(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun frames(sampleRateHz: Int, frameMs: Int): Flow<ShortArray> = flow {
        val frameSamples = sampleRateHz * frameMs / 1000
        val minBuffer = AudioRecord.getMinBufferSize(sampleRateHz, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minBuffer > 0) { "AudioRecord does not support $sampleRateHz Hz mono 16-bit here: $minBuffer" }
        // Several frames of slack so a slow collector loses nothing while the decoder runs.
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, frameSamples * 2 * 8),
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialise" }
        try {
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "AudioRecord did not start; is another app holding the microphone?" }
            val buffer = ShortArray(frameSamples)
            while (true) {
                val n = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                check(n >= 0) { "AudioRecord.read failed: $n" }
                if (n > 0) emit(buffer.copyOf(n))
            }
        } finally {
            // Cancellation lands here. Stop first so release does not race an in-flight read.
            runCatching { recorder.stop() }
            recorder.release()
        }
    }.flowOn(Dispatchers.IO)
}
