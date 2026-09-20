package io.github.vedant7007.katori.ml.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Spoken output from this app, for audio focus and routing. Shared by both TTS paths. */
internal val speechAttributes: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ASSISTANT)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

/**
 * Hold transient audio focus around [block]. Refused focus, a call in progress for instance, is
 * CANCELLED without running the block. A real loss while it runs calls [onLoss]; a transient loss
 * that may duck (a notification chime) is ignored, because a health sentence is not cut short for
 * a chime. Focus is released however the block ends.
 */
internal suspend fun AudioManager.withTransientFocus(
    onLoss: () -> Unit,
    block: suspend () -> Outcome<Unit>,
): Outcome<Unit> {
    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(speechAttributes)
        .setOnAudioFocusChangeListener({ change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) onLoss()
        }, Handler(Looper.getMainLooper()))
        .build()
    if (requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        return Outcome.Unavailable(UnavailableReason.CANCELLED, "audio focus refused")
    }
    try {
        return block()
    } finally {
        abandonAudioFocusRequest(request)
    }
}

/**
 * [AudioSink] on `AudioTrack`, one static buffer per utterance.
 *
 * Static mode because an utterance is a few seconds of audio that already exists in full when
 * playback starts; there is nothing to stream. The end of playback is signalled by the position
 * marker at the last frame, with a timeout of the audio's own length plus a second as the net
 * under it, because marker delivery at the very end of a static buffer is the kind of thing an
 * OEM audio HAL gets subtly wrong and a speak() that never returns would hang the round trip.
 *
 * WRITTEN, NOT RUN. Every line here is Android framework and none of it can execute on the JVM.
 * The first evidence that it works is a device run.
 */
class AudioTrackSink(context: Context) : AudioSink {

    private val audioManager: AudioManager = context.getSystemService(AudioManager::class.java)

    private class Playback(val track: AudioTrack, val done: CompletableDeferred<Outcome<Unit>>)

    @Volatile
    private var current: Playback? = null

    override suspend fun play(pcm: FloatArray, sampleRateHz: Int): Outcome<Unit> {
        if (pcm.isEmpty()) return Outcome.Ok(Unit)
        val done = CompletableDeferred<Outcome<Unit>>()
        return audioManager.withTransientFocus(
            onLoss = { done.complete(Outcome.Unavailable(UnavailableReason.CANCELLED, "audio focus lost")) },
        ) {
            try {
                playOnce(pcm, sampleRateHz, done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                // An AudioTrack the HAL refuses to build, or a write it rejects, is a condition
                // the caller can show, not a bug to crash on: the contract says every engine
                // answers with an Outcome.
                Outcome.Unavailable(UnavailableReason.INTERNAL_ERROR, "AudioTrack: ${e::class.java.simpleName}: ${e.message}")
            }
        }
    }

    private suspend fun playOnce(pcm: FloatArray, sampleRateHz: Int, done: CompletableDeferred<Outcome<Unit>>): Outcome<Unit> {
        val track = AudioTrack.Builder()
            .setAudioAttributes(speechAttributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * Float.SIZE_BYTES)
            .build()
        current = Playback(track, done)
        try {
            val written = track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
            if (written != pcm.size) {
                return Outcome.Unavailable(UnavailableReason.INTERNAL_ERROR, "AudioTrack took $written of ${pcm.size} samples")
            }
            track.setNotificationMarkerPosition(pcm.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) { done.complete(Outcome.Ok(Unit)) }
                override fun onPeriodicNotification(t: AudioTrack) = Unit
            }, Handler(Looper.getMainLooper()))
            track.play()
            val lengthMs = pcm.size * 1000L / sampleRateHz
            return withTimeoutOrNull(lengthMs + 1_000) { done.await() } ?: Outcome.Ok(Unit)
        } finally {
            current = null
            runCatching { track.pause() }
            track.release()
        }
    }

    override fun stop() {
        val playing = current ?: return
        runCatching { playing.track.pause() }
        playing.done.complete(Outcome.Unavailable(UnavailableReason.CANCELLED, "stopped"))
    }
}
