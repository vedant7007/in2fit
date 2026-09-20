package io.github.vedant7007.katori.ml.tts

import android.Manifest
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.vedant7007.katori.ml.asr.SpeechLanguage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.sin

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
 *
 * Test c is about whether the judges can HEAR the phone (`0019` addendum 6): after a microphone
 * capture like the one every spoken turn starts with, where does our audio come out (speaker,
 * earpiece, a wired device), and how far below maximum is the volume it plays at. Run it once
 * with nothing plugged in and once with the wired speaker the run of show names.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TtsVoiceProbeTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val outDir = File(ctx.externalMediaDirs.first(), "tts-probe").apply { mkdirs() }
    private val report = File(outDir, "katori-tts-report.txt")

    /** Test c opens the microphone the way a spoken turn does; the app already declares this. */
    @get:Rule
    val microphone: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

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
            val picked = AndroidTtsEngine.offlineVoiceFor(tts, language)
            if (picked == null) {
                say("$language: no offline voice on this device, no sample written")
                continue
            }
            // English: every offline en-IN voice at every rate, for the ear that decides. The other
            // languages: the voice the engine would pick, at the shipped rate.
            val english = language == SpeechLanguage.ENGLISH_INDIA
            val voices = if (english) {
                runCatching { tts.voices }.getOrNull().orEmpty()
                    .filter { it.locale.toLanguageTag() == "en-IN" && !it.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
                    .sortedBy { it.name }.ifEmpty { listOf(picked) }
            } else listOf(picked)
            val rates = if (english) ENGLISH_RATES else listOf(TtsFlags.SPEECH_RATE)
            for (voice in voices) for (rate in rates) {
            val set = tts.setVoice(voice)
            tts.setSpeechRate(rate)
            say("$language: voice ${voice.name} rate $rate (setVoice=$set)")
            for ((key, text) in texts) {
                val out = File(outDir, "platform-${language.tag}-${voice.name}-rate$rate-$key.wav")
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
                // The platform writes 16-bit PCM WAV; the audio length falls out of the file size,
                // and synthesis time over audio length is the real-time factor the choice of voice
                // now hinges on (0019 addendum 3). A 44-byte header is the platform's; if a vendor
                // engine writes another container the seconds read as nonsense and say so.
                val seconds = wavSeconds(out)
                val rtf = if (seconds > 0) "%.2f".format(ms / 1000.0 / seconds) else "n/a"
                say("   $key: $result in $ms ms for ${"%.2f".format(seconds)} s of audio, RTF $rtf, ${out.length()} B  ${out.name}")
            }
            }
        }
        tts.shutdown()
        say("pull with: adb pull ${outDir.absolutePath} logs/tts-probe")
        say("note: the first synthesis after binding is the slow one (RTF 1.9-3.6 on the realme, 14:47); read RTF from the second and later lines")
    }

    @Test
    fun c_outputRouteAndLevelAfterMicrophoneCapture() {
        val audio = ctx.getSystemService(AudioManager::class.java)
        say("-- output route and level")
        say("   before capture: mode=${audio.mode} speakerphone=${audio.isSpeakerphoneOn}" +
            "  outputs=${audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { deviceName(it) }}")

        // A capture like the one every spoken turn starts with: same source as ml/asr/AudioSource,
        // half a second, then released. Routing after THIS is what the demo hears.
        val rate = 16_000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate))
        val captured = if (recorder.state == AudioRecord.STATE_INITIALIZED) {
            recorder.startRecording()
            val buf = ShortArray(rate / 2)
            val n = recorder.read(buf, 0, buf.size)
            recorder.stop(); recorder.release()
            "read $n samples"
        } else "AudioRecord did not initialise (state ${recorder.state})"
        say("   capture: $captured; after capture: mode=${audio.mode} speakerphone=${audio.isSpeakerphoneOn}")

        // Then our own output path: the same attributes AudioTrackSink uses, a 0.4 s tone at the
        // level the engine normalises to, and the device the track actually routed to.
        val outRate = 22_050
        val tone = FloatArray(outRate * 2 / 5) { (0.89 * sin(2 * PI * 440 * it / outRate)).toFloat() }
        val track = AudioTrack.Builder()
            .setAudioAttributes(speechAttributes)
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(outRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(tone.size * 4).build()
        track.write(tone, 0, tone.size, AudioTrack.WRITE_BLOCKING)
        track.play()
        SystemClock.sleep(150)
        val routed = track.routedDevice
        say("   our track routed to: ${routed?.let { deviceName(it) } ?: "UNKNOWN (null)"}")
        SystemClock.sleep(400)
        track.stop(); track.release()

        val stream = speechAttributes.volumeControlStream
        say("   volume: stream $stream (3 = music) at ${audio.getStreamVolume(stream)} of ${audio.getStreamMaxVolume(stream)};" +
            " music ${audio.getStreamVolume(AudioManager.STREAM_MUSIC)} of ${audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}")
        say("   read this as: the route must be BUILTIN_SPEAKER or the wired device, never BUILTIN_EARPIECE," +
            " and the stream volume must be at its maximum on the day; both are checklist items, not code.")
    }

    private fun deviceName(d: AudioDeviceInfo): String = when (d.type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "BUILTIN_SPEAKER"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "BUILTIN_EARPIECE"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "LINE_ANALOG"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
        else -> "type ${d.type}"
    } + "(${d.productName})"

    /** Seconds of audio in a canonical 44-byte-header PCM WAV, or 0 if it is not one. */
    private fun wavSeconds(f: File): Double {
        if (f.length() < 44) return 0.0
        val h = ByteArray(44)
        f.inputStream().use { input ->
            var n = 0
            while (n < 44) {
                val r = input.read(h, n, 44 - n)
                if (r < 0) break
                n += r
            }
        }
        if (String(h, 0, 4) != "RIFF" || String(h, 8, 4) != "WAVE") return 0.0
        fun u16(i: Int) = (h[i].toInt() and 0xFF) or ((h[i + 1].toInt() and 0xFF) shl 8)
        fun u32(i: Int) = u16(i).toLong() or (u16(i + 2).toLong() shl 16)
        val channels = u16(22)
        val rate = u32(24)
        val bitsPerSample = u16(34)
        val bytesPerSecond = rate * channels * bitsPerSample / 8
        return if (bytesPerSecond > 0) (f.length() - 44).toDouble() / bytesPerSecond else 0.0
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
            // The same three sentences as the bundled English candidates in logs/tts-candidates/,
            // so Vedant compares the platform voices with what he already rejected, like for like.
            SpeechLanguage.ENGLISH_INDIA to listOf(
                "leadin" to "Noting that down.",
                "plate" to "Two rotis, a katori of dal and two spoons of oil: 428 kilocalories and 19 grams of protein.",
                "answer" to "Today so far you have had 2.9 milligrams of iron; your report from 19 September shows haemoglobin below the range printed on it.",
            ),
        )

        /** English is the language the app speaks back; every offline en-IN voice gets a hearing, at three rates. */
        val ENGLISH_RATES = listOf(1.0f, 0.9f, 0.85f)
    }
}
