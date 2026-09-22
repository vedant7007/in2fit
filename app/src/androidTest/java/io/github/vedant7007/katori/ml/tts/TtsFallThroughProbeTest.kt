package io.github.vedant7007.katori.ml.tts

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vedant7007.katori.data.local.AndroidDeviceMemory
import io.github.vedant7007.katori.data.local.FileMeasurementLog
import io.github.vedant7007.katori.domain.DefaultModelArbiter
import io.github.vedant7007.katori.domain.FamilyModelLoader
import io.github.vedant7007.katori.domain.ModelFamily
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import io.github.vedant7007.katori.ml.asr.SpeechLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.time.LocalDateTime

/**
 * THE INSURANCE, TESTED. Does the bundled Piper voice speak, audibly, out of this phone, when the
 * platform voice is not there?
 *
 * The abandon rule in the device queue treats the bundled voice as what saves the demo if the
 * loaner has no English voice data installed. On 22 September nothing behind that rule had ever
 * run on a phone: not `PiperTtsEngine`, not `AudioTrackSink`, not the normalisation, not the
 * fall-through in `RoutingTtsEngine`. This test runs the SAME stack the app wires in
 * `AppModule` (arbiter, `FamilyModelLoader` with `PiperVoiceLoader`, `EspeakData` from the APK's
 * assets, `AudioTrackSink` on the real speaker), with the platform engine replaced by one that
 * always says it has no voice, and speaks the three demo sentences out loud.
 *
 * THE PASS CRITERION IS A PERSON. The report and logcat carry the engine that spoke, the audio
 * length, the synthesis time and the peak level, and the same audio is written to WAV files in
 * `tts-probe/` so it can be pulled and heard again; but the row passes only when the person
 * holding the phone at arm's length writes down which words they heard. Not "it did not crash".
 *
 * TWO RUNS, BOTH IN THE ROW:
 *   1. As is. Test a uses the REAL platform engine first, so it records which engine spoke on
 *      this phone today; test b forces the fall-through inside the process.
 *   2. With the platform voice genuinely unavailable at the device level, so the real
 *      `AndroidTtsEngine` refuses and `RoutingTtsEngine` falls through on its own:
 *        adb shell settings put secure tts_default_synth com.example.no.such.engine
 *      then run this class again; test a must now report the BUNDLED engine spoke. Restore:
 *        adb shell settings put secure tts_default_synth com.google.android.tts
 *      (`settings get secure tts_default_synth` first, and restore whatever it printed.)
 *
 * WRITTEN BY MEERA, NEVER RUN BY MEERA. Rao drives the phone (`am instrument`, screen awake,
 * media volume at maximum, nothing else playing):
 *
 *     adb shell am instrument -w -e class \
 *       io.github.vedant7007.katori.ml.tts.TtsFallThroughProbeTest \
 *       io.github.vedant7007.katori.test/androidx.test.runner.AndroidJUnitRunner
 *     adb pull /sdcard/Android/media/io.github.vedant7007.katori/tts-probe logs/tts-probe
 *
 * Read `tts-probe/katori-tts-fallthrough.txt` or logcat tag `IN2FIT-TTS`; never the exit code.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TtsFallThroughProbeTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val outDir = File(ctx.externalMediaDirs.first(), "tts-probe").apply { mkdirs() }
    private val report = File(outDir, "katori-tts-fallthrough.txt")

    /** Exactly `AppModule.modelsDir`. If the app's resolution changes, this must change with it. */
    private val modelsDir = File(ctx.externalMediaDirs.firstOrNull() ?: ctx.filesDir, "models")

    private fun say(line: String) {
        Log.i(TAG, line)
        report.appendText(line + "\n")
    }

    /** A platform engine with no voice for anything: what the loaner looks like without voice data. */
    private object NoPlatformVoice : TtsEngine {
        override val supportedLanguages: Set<SpeechLanguage> = SpeechLanguage.entries.toSet()
        override suspend fun prepare(language: SpeechLanguage) = refuse(language)
        override suspend fun speak(text: String, language: SpeechLanguage) = refuse(language)
        override suspend fun stop() = Unit
        private fun refuse(language: SpeechLanguage) =
            Outcome.Unavailable(UnavailableReason.MODEL_NOT_LOADED, "forced by the probe: no ${language.tag} voice")
    }

    /** Records which engine actually spoke, without changing what it does. */
    private class Named(val name: String, private val inner: TtsEngine, val spoke: MutableList<String>) : TtsEngine {
        override val supportedLanguages get() = inner.supportedLanguages
        override suspend fun prepare(language: SpeechLanguage) = inner.prepare(language)
        override suspend fun speak(text: String, language: SpeechLanguage): Outcome<Unit> {
            val r = inner.speak(text, language)
            if (r is Outcome.Ok) spoke += name
            return r
        }
        override suspend fun stop() = inner.stop()
    }

    /** The real sink, with the audio it was handed also written to a WAV and measured. */
    private class RecordingSink(private val inner: AudioSink, private val outDir: File, private val tag: () -> String) : AudioSink {
        var lastSamples = 0
        var lastRate = 0
        var lastPeak = 0f
        var lastFile: File? = null
        override suspend fun play(pcm: FloatArray, sampleRateHz: Int): Outcome<Unit> {
            lastSamples = pcm.size; lastRate = sampleRateHz
            lastPeak = pcm.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
            lastFile = File(outDir, "fallthrough-${tag()}.wav").also { writeWav(it, pcm, sampleRateHz) }
            return inner.play(pcm, sampleRateHz)
        }
        override fun stop() = inner.stop()
    }

    private fun bundledEngine(sink: AudioSink): PiperTtsEngine {
        val espeak = EspeakData.install(ctx, File(ctx.filesDir, "espeak-ng-data"))
        val arbiter = DefaultModelArbiter(
            memory = AndroidDeviceMemory(ctx),
            loader = FamilyModelLoader(mapOf(ModelFamily.TTS to PiperVoiceLoader(modelsDir, espeak))),
            log = FileMeasurementLog(ctx),
            buildTag = "tts-fallthrough-probe",
        )
        say("espeak-ng-data at $espeak: ${espeak.listFiles()?.size ?: 0} entries, required present = " +
            EspeakData.REQUIRED_FILES.all { File(espeak, it).isFile })
        for ((lang, handle) in PiperVoices.byLanguage) {
            val f = File(modelsDir, handle.relativePath)
            val tokens = File(f.parentFile, "tokens.txt")
            say("staged $lang: ${handle.relativePath} = ${if (f.isFile) "${f.length()} B" else "MISSING"}, tokens ${if (tokens.isFile) "present" else "MISSING"}")
        }
        return PiperTtsEngine(arbiter, sink)
    }

    private fun speakAll(engine: TtsEngine, sink: RecordingSink, label: String, spoke: MutableList<String>) {
        for ((key, text) in SENTENCES) {
            currentTag = "$label-$key"
            spoke.clear()
            val t0 = SystemClock.elapsedRealtime()
            val outcome = runBlocking { engine.speak(text, SpeechLanguage.ENGLISH_INDIA) }
            val wall = SystemClock.elapsedRealtime() - t0
            val audio = if (sink.lastRate > 0) sink.lastSamples.toDouble() / sink.lastRate else 0.0
            val synth = wall / 1000.0 - audio
            when (outcome) {
                is Outcome.Ok -> say("   $key: spoke via ${spoke.joinToString().ifEmpty { "platform (not via the bundled sink)" }}; " +
                    "audio ${"%.2f".format(audio)} s, wall ${wall} ms, synthesis ~${"%.2f".format(synth)} s " +
                    "(RTF ~${if (audio > 0) "%.2f".format(synth / audio) else "n/a"}), peak ${"%.2f".format(sink.lastPeak)}, ${sink.lastFile?.name ?: "-"}")
                is Outcome.Unavailable -> say("   $key: NOT SPOKEN: ${outcome.reason} ${outcome.detail}")
                is Outcome.NotImplemented -> say("   $key: NOT BUILT: ${outcome.component}")
            }
            SystemClock.sleep(400)
        }
    }

    @Volatile private var currentTag = ""

    @Test
    fun a_asWired_platformFirstThenBundled() {
        report.delete()
        say("== TTS fall-through probe  ${Build.MANUFACTURER} ${Build.MODEL}  API ${Build.VERSION.SDK_INT}  ${LocalDateTime.now()}")
        say("-- a. the app's own order: platform first, bundled second (PLATFORM_VOICE_FIRST=${TtsFlags.PLATFORM_VOICE_FIRST})")
        val spoke = mutableListOf<String>()
        val sink = RecordingSink(AudioTrackSink(ctx), outDir) { currentTag }
        val engine = demoTtsEngine(Named("platform", AndroidTtsEngine(ctx), spoke), Named("bundled", bundledEngine(sink), spoke))
        speakAll(engine, sink, "a", spoke)
        say("   read this as: which engine spoke is the device's state today; with tts_default_synth pointed at nothing it must say bundled")
    }

    @Test
    fun b_forcedFallThrough_bundledMustSpeak() {
        say("-- b. platform engine forced to refuse; the bundled voice must speak, audibly")
        val spoke = mutableListOf<String>()
        val sink = RecordingSink(AudioTrackSink(ctx), outDir) { currentTag }
        val engine = demoTtsEngine(Named("platform", NoPlatformVoice, spoke), Named("bundled", bundledEngine(sink), spoke))
        speakAll(engine, sink, "b", spoke)
        say("   PASS ONLY WHEN A PERSON WRITES THE WORDS THEY HEARD. Then: adb pull the fallthrough-b-*.wav files for Vedant.")
        assertTrue(
            "the bundled voice produced no audio for any sentence; the insurance does not exist on this phone",
            sink.lastSamples > 0,
        )
    }

    private companion object {
        const val TAG = "IN2FIT-TTS"

        /** The demo's lead-in, a plate confirmation, an answer-shaped sentence: what is spoken on stage. */
        val SENTENCES = listOf(
            "leadin" to "Noting that down.",
            "plate" to "Two rotis, a katori of dal and two spoons of oil: 428 kilocalories and 19 grams of protein.",
            "answer" to "Today so far you have had 2.9 milligrams of iron; your report from 19 September shows haemoglobin below the range printed on it.",
        )

        /** 16-bit PCM WAV, the canonical 44-byte header, so the pulled file opens anywhere. */
        fun writeWav(file: File, pcm: FloatArray, rate: Int) {
            val data = ByteArray(pcm.size * 2)
            for (i in pcm.indices) {
                val v = (pcm[i].coerceIn(-1f, 1f) * 32767f).toInt()
                data[2 * i] = (v and 0xFF).toByte(); data[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
            }
            val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVE".toByteArray())
                .put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(data.size)
            file.outputStream().use { it.write(header.array()); it.write(data) }
        }
    }
}
