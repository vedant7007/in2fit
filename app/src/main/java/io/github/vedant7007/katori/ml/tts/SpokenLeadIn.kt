package io.github.vedant7007.katori.ml.tts

import io.github.vedant7007.katori.ml.asr.SpeechLanguage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Switches for spoken output. ON by default; they exist so a device measurement can turn one off. */
object TtsFlags {
    /**
     * Speak a short fixed phrase while the model generates the real answer.
     *
     * ponytail: a compile-time constant, flipped here, not remote config. Rao's conversational-turn
     * timings tune it (a lead-in on a sub-second turn is noise); they do not gate it.
     */
    const val SPOKEN_LEAD_IN = true
}

/**
 * Run [generate] while a short lead-in phrase is spoken, and return only when both are done.
 *
 * WHY. `0014` measured generation at 9-10 tok/s and about five times the cost of prompt
 * processing, so a prose ANSWER or RECOMMEND can be twenty seconds of silence before the first
 * synthesised word. A fixed two-second phrase spoken the moment the intent is classified turns
 * that into speech, then a pause, then the answer, and it is worth more to the person waiting
 * than any choice of voice (`0019` addendum 3). Priya's SHORT default for spoken turns attacks
 * the same twenty seconds from the other side.
 *
 * ORDERING. The lead-in has finished playing before this returns, so the caller's next `speak()`
 * never talks over it. If [generate] throws or the caller is cancelled, the lead-in is cancelled
 * with it and the audio stops. If the lead-in cannot be spoken (no voice, focus refused) nothing
 * is retried: the answer's own `speak()` reports availability, and the text is on screen anyway.
 *
 * COST. With a Piper voice the lead-in is synthesised on the same CPU the model is generating on,
 * for about a second. With the platform engine it is another process. Whether that second is
 * visible in the generation time is one of the numbers Rao's batch measures.
 *
 * [text] is the caller's, from the string table in the person's language; nothing here invents a
 * phrase. Blank text, a disabled flag, or a language this engine cannot speak means [generate]
 * runs alone.
 */
suspend fun <T> TtsEngine.withSpokenLeadIn(
    text: String,
    language: SpeechLanguage,
    enabled: Boolean = TtsFlags.SPOKEN_LEAD_IN,
    generate: suspend () -> T,
): T {
    if (!enabled || text.isBlank() || language !in supportedLanguages) return generate()
    return coroutineScope {
        val leadIn = launch { speak(text, language) }
        val result = generate()
        leadIn.join()
        result
    }
}
