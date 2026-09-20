package io.github.vedant7007.katori.ml.tts

import io.github.vedant7007.katori.domain.model.Outcome

/**
 * Where synthesised audio goes. The seam that keeps [PiperTtsEngine] testable on the JVM.
 */
interface AudioSink {
    /**
     * Play [pcm], mono float in -1..1 at [sampleRateHz], and return once it has been heard.
     *
     * CANCELLED when audio focus is refused or lost (a call in progress) or when [stop] is called
     * while playing; the caller keeps the text on screen. Cancelling the coroutine stops the
     * audio at once and releases the focus.
     */
    suspend fun play(pcm: FloatArray, sampleRateHz: Int): Outcome<Unit>

    /** Stop whatever is playing. Safe to call when nothing is. */
    fun stop()
}
