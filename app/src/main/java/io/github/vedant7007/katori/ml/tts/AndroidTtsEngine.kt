package io.github.vedant7007.katori.ml.tts

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import io.github.vedant7007.katori.ml.asr.SpeechLanguage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * [TtsEngine] for English on the platform's own `TextToSpeech`.
 *
 * On-device, ships with the OS, no licence question, no model file: the cheapest correct answer
 * for English (`0005`). Nothing is downloaded and nothing goes through the arbiter, because
 * there is no model of ours to admit.
 *
 * OFFLINE IS CHECKED, NOT ASSUMED. The system engine can offer voices that synthesise on a
 * server. Only a voice whose `isNetworkConnectionRequired` is false and whose features do not
 * say `notInstalled` is used; with none installed, the answer is MODEL_NOT_LOADED and the text
 * stays on screen. The demo build has no INTERNET permission, but the engine is another process
 * with its own, so this check is what keeps the claim honest.
 *
 * WRITTEN, NOT RUN. `TextToSpeech` cannot exist on the JVM; the sentence splitting below is the
 * only part with a test. The first evidence that this speaks is a device run.
 */
class AndroidTtsEngine(private val context: Context) : TtsEngine {

    override val supportedLanguages: Set<SpeechLanguage> = setOf(SpeechLanguage.ENGLISH_INDIA)

    private val audioManager: AudioManager = context.getSystemService(AudioManager::class.java)
    private val initLock = Mutex()
    private var engine: TextToSpeech? = null

    override suspend fun prepare(language: SpeechLanguage): Outcome<Unit> {
        if (language !in supportedLanguages) {
            return Outcome.Unavailable(UnavailableReason.MODEL_NOT_LOADED, "AndroidTtsEngine speaks English only, not $language")
        }
        val tts = when (val e = engine()) {
            is Outcome.Ok -> e.value
            is Outcome.Unavailable -> return e
            is Outcome.NotImplemented -> return e
        }
        val voice = offlineEnglishVoice(tts)
            ?: return Outcome.Unavailable(UnavailableReason.MODEL_NOT_LOADED, "no installed offline English voice in the system engine")
        if (tts.setVoice(voice) != TextToSpeech.SUCCESS) {
            return Outcome.Unavailable(UnavailableReason.MODEL_NOT_LOADED, "system engine refused voice ${voice.name}")
        }
        return Outcome.Ok(Unit)
    }

    override suspend fun speak(text: String, language: SpeechLanguage): Outcome<Unit> {
        when (val ready = prepare(language)) {
            is Outcome.Ok -> Unit
            is Outcome.Unavailable -> return ready
            is Outcome.NotImplemented -> return ready
        }
        val tts = engine ?: return Outcome.Unavailable(UnavailableReason.INTERNAL_ERROR, "engine vanished after prepare")
        val chunks = SentenceChunks.split(text, TextToSpeech.getMaxSpeechInputLength())
        if (chunks.isEmpty()) return Outcome.Ok(Unit)

        return audioManager.withTransientFocus(onLoss = { tts.stop() }) {
            suspendCancellableCoroutine { cont ->
                val ids = chunks.indices.map { "in2fit-${System.nanoTime()}-$it" }
                val finished = AtomicBoolean(false)
                fun finish(outcome: Outcome<Unit>) {
                    if (finished.compareAndSet(false, true) && cont.isActive) cont.resume(outcome)
                }
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == ids.last()) finish(Outcome.Ok(Unit))
                    }
                    @Suppress("OVERRIDE_DEPRECATION") // abstract on the platform class; engines still call it
                    override fun onError(utteranceId: String?) = finish(Outcome.Unavailable(UnavailableReason.INTERNAL_ERROR, "TextToSpeech error"))
                    override fun onError(utteranceId: String?, errorCode: Int) =
                        finish(Outcome.Unavailable(UnavailableReason.INTERNAL_ERROR, "TextToSpeech error $errorCode"))
                    override fun onStop(utteranceId: String?, interrupted: Boolean) =
                        finish(Outcome.Unavailable(UnavailableReason.CANCELLED, "stopped"))
                })
                // Every chunk is queued up front: the first flushes anything earlier, the rest
                // follow it, and the utterance is complete when the last id reports done. A
                // sentence is never dropped to fit the engine's input limit.
                for ((i, chunk) in chunks.withIndex()) {
                    val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                    if (tts.speak(chunk, mode, null, ids[i]) != TextToSpeech.SUCCESS) {
                        finish(Outcome.Unavailable(UnavailableReason.INTERNAL_ERROR, "speak() refused chunk $i of ${chunks.size}"))
                        break
                    }
                }
                cont.invokeOnCancellation { tts.stop() }
            }
        }
    }

    override suspend fun stop() {
        engine?.stop()
    }

    /** Bind the system engine once. `onInit` reports ERROR when the device has no engine at all. */
    private suspend fun engine(): Outcome<TextToSpeech> = initLock.withLock {
        engine?.let { return@withLock Outcome.Ok(it) }
        val status = CompletableDeferred<Int>()
        val tts = TextToSpeech(context) { status.complete(it) }
        if (status.await() != TextToSpeech.SUCCESS) {
            tts.shutdown()
            return@withLock Outcome.Unavailable(UnavailableReason.MODEL_NOT_LOADED, "no system TextToSpeech engine")
        }
        engine = tts
        Outcome.Ok(tts)
    }

    private fun offlineEnglishVoice(tts: TextToSpeech): Voice? {
        val voices = runCatching { tts.voices }.getOrNull().orEmpty()
        return voices
            .filter { it.locale.language == Locale.ENGLISH.language }
            .filter { !it.isNetworkConnectionRequired }
            .filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
            // en-IN first; any other installed English voice is still English, and better than
            // silence. Never another language's voice: that rule is the contract's, and this
            // engine only ever sees English.
            .minByOrNull { if (it.locale.country == "IN") 0 else 1 }
    }
}

/**
 * Splits text for an engine with a per-utterance input limit, on sentence boundaries first and
 * on words only when one sentence alone exceeds the limit. Every character of the input ends up
 * in exactly one chunk, in order: the contract forbids truncation because a health sentence cut
 * short can invert its meaning.
 */
internal object SentenceChunks {

    /** Sentence enders followed by whitespace: Latin punctuation plus the Indic danda. */
    private val sentenceEnd = Regex("(?<=[.!?।॥])\\s+")
    private val whitespace = Regex("\\s+")

    fun split(text: String, maxChars: Int): List<String> {
        require(maxChars > 0) { "maxChars must be positive" }
        val units = text.trim().split(sentenceEnd).flatMap { sentence ->
            if (sentence.length <= maxChars) listOf(sentence)
            else sentence.split(whitespace).flatMap { word -> word.chunked(maxChars) }
        }.filter { it.isNotEmpty() }

        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (unit in units) {
            if (current.isNotEmpty() && current.length + 1 + unit.length > maxChars) {
                out += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(unit)
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }
}
