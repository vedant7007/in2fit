package io.github.vedant7007.katori.ml.tts

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vedant7007.katori.ml.asr.SpeechLanguage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.time.LocalDateTime

/**
 * Which spoken voices THIS phone has, and what they sound like.
 *
 * Telugu listeners called the Piper voice robotic (`0019`). The cheapest thing to put in front
 * of them next is the platform's own `TextToSpeech`, whose Indian-language voices run offline
 * once their data is on the device. Whether this phone has them is a device fact: voice
 * availability varies by OEM and by what the user has downloaded, so this probe reads it off the
 * phone rather than a spec sheet, and writes WAV files with the same texts as the Piper
 * candidates in `logs/tts-candidates/` so the listeners compare like with like.
 *
 * WRITTEN BY MEERA, NEVER RUN BY MEERA. Only Rao drives the phone. Run with `am instrument`,
 * never gradle (COORDINATION.md rule 2), screen awake (rule 3), and read the answers from
 * `tts-probe/katori-tts-report.txt` in the app's external media directory or logcat tag
 * `IN2FIT-TTS`, never from the exit code (rule 1):
 *
 *     adb shell am instrument -w -e class \
 *       io.github.vedant7007.katori.ml.tts.TtsVoiceProbeTest \
 *       io.github.vedant7007.katori.test/androidx.test.runner.AndroidJUnitRunner
 *     adb pull /sdcard/Android/media/io.github.vedant7007.katori/tts-probe logs/tts-probe
 *
 * WHAT IS ASSERTED: only that the platform engine binds. No voice for a language is a finding
 * the report states, not a test failure; the app falls through to Piper for that language.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TtsVoiceProbeTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val outDir = File(ctx.externalMediaDirs.first(), "tts-probe").apply { mkdirs() }
    private val report = File(outDir, "katori-tts-report.txt")

    private fun say(line: String) {
        Log.i(TAG, line)
        report.appendText(line + "\n")
    }

    private fun bind(): TextToSpeech {
        val status = CompletableDeferred<Int>()
        val tts = TextToSpeech(ctx) { status.complete(it) }
        val s = runBlocking { withTimeout(15_000) { status.await() } }
        say("TextToSpeech init status $s (0 = SUCCESS), default engine ${tts.defaultEngine}")
        assertEquals("the platform engine must bind for this probe to say anything", TextToSpeech.SUCCESS, s)
        return tts
    }

    @Test
    fun a_voicesOnThisDevice() {
        report.delete()
        say("== TTS voice probe  ${Build.MANUFACTURER} ${Build.MODEL}  API ${Build.VERSION.SDK_INT}  ${LocalDateTime.now()}")
        val tts = bind()
        say("engines installed: ${tts.engines.map { "${it.name} (${it.label})" }}")
        val voices = runCatching { tts.voices }.getOrNull().orEmpty()
        say("${voices.size} voices in total across all languages")
        for (language in SpeechLanguage.entries) {
            val locale = AndroidTtsEngine.locale(language)
            say("-- $language ${locale.toLanguageTag()}  isLanguageAvailable=${tts.isLanguageAvailable(locale)}" +
                "  (0 lang, 1 lang+country, 2 lang+country+variant; -1 missing data, -2 not supported)")
            voices.filter { it.locale.language == locale.language }.sortedBy { it.name }.forEach { v ->
                say("   ${v.name}  ${v.locale.toLanguageTag()}  quality=${v.quality}  latency=${v.latency}" +
                    "  network=${v.isNetworkConnectionRequired}  features=${v.features}")
            }
            val pick = AndroidTtsEngine.offlineVoiceFor(tts, language)
            say("   AndroidTtsEngine would use: ${pick?.name ?: "NONE -> RoutingTtsEngine falls through to Piper"}")
        }
        tts.shutdown()
    }

    @Test
    fun b_synthesiseSamplesToWav() {
        val tts = bind()
        for ((language, texts) in SAMPLES) {
            val voice = AndroidTtsEngine.offlineVoiceFor(tts, language)
            if (voice == null) {
                say("$language: no offline voice on this device, no sample written")
                continue
            }
            val set = tts.setVoice(voice)
            say("$language: voice ${voice.name} (setVoice=$set)")
            for ((key, text) in texts) {
                val out = File(outDir, "platform-${language.tag}-${voice.name}-$key.wav")
                out.delete()
                val id = "probe-${language.tag}-$key"
                val done = CompletableDeferred<String>()
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) { if (utteranceId == id) done.complete("done") }
                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onError(utteranceId: String?) { done.complete("error") }
                    override fun onError(utteranceId: String?, errorCode: Int) { done.complete("error $errorCode") }
                })
                val t0 = SystemClock.elapsedRealtime()
                val queued = tts.synthesizeToFile(text, Bundle(), out, id)
                val result = if (queued == TextToSpeech.SUCCESS) {
                    runBlocking { withTimeoutOrNull(30_000) { done.await() } ?: "TIMEOUT after 30 s" }
                } else {
                    "synthesizeToFile returned $queued"
                }
                val ms = SystemClock.elapsedRealtime() - t0
                say("   $key: $result in $ms ms, ${out.length()} B  ${out.name}")
            }
        }
        tts.shutdown()
        say("pull with: adb pull ${outDir.absolutePath} logs/tts-probe")
    }

    private companion object {
        const val TAG = "IN2FIT-TTS"

        /**
         * The same texts as the Piper candidates. Telugu and Hindi are food names from the
         * authored CSVs; the English line is the probe's own transcript. No sentence in either
         * Indian language exists in this repository, and none was invented for this.
         */
        val SAMPLES: Map<SpeechLanguage, List<Pair<String, String>>> = mapOf(
            SpeechLanguage.TELUGU to listOf(
                "A" to "ఇడ్లీ సాంబార్",
                "B" to "ఇడ్లీ, సాంబార్, పెరుగన్నం, కోడి కూర, బిర్యానీ.",
            ),
            SpeechLanguage.HINDI to listOf("A" to "रोटी दाल"),
            SpeechLanguage.ENGLISH_INDIA to listOf("A" to "I had two rotis and a katori of dal with some curd"),
        )
    }
}
