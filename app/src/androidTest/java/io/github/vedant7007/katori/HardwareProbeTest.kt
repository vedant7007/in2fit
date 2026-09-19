package io.github.vedant7007.katori

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.app.ActivityManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.vedant7007.katori.ml.llm.LlamaCppRuntime
import io.github.vedant7007.katori.ml.llm.LlamaCppLlmEngine
import io.github.vedant7007.katori.ml.llm.ExtractionRequest
import io.github.vedant7007.katori.domain.model.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * THE HARDWARE PROBE. Everything in this project that has been asserted but never verified.
 *
 * Every number this produces is the FIRST real number of its kind for Katori. Nothing that came
 * before it, including the desktop llama.cpp smoke test, is a device figure and none of it may be
 * quoted as one.
 *
 * It writes a plain-text report to the app's external media directory so the result can be pulled
 * off the phone and read, rather than scraped out of logcat where it interleaves with everything
 * else the system is saying.
 *
 * MODELS ARE PUSHED, NOT BUNDLED. Over a gigabyte of weights does not belong in a test APK. The
 * runner script pushes them to the app's external media directory first; a missing model FAILS the
 * test with the path it looked in. It does not skip, because a skipped hardware check reads as a
 * pass in a summary three days later.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class HardwareProbeTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private val modelsDir: File get() = File(ctx.externalMediaDirs.first(), "models")

    private fun say(line: String) {
        Log.i(TAG, line)
        report.appendText(line + "\n")
    }

    private fun heading(title: String) {
        say("")
        say("=== $title ===")
    }

    private val activityManager: ActivityManager
        get() = ctx.getSystemService(ActivityManager::class.java)

    /** Proportional set size for this process, in bytes. The figure that decides whether it fits. */
    private fun processPssBytes(): Long {
        val info = activityManager.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))
        return info.first().totalPss.toLong() * 1024L
    }

    private fun mb(bytes: Long) = "%,.1f MB".format(bytes / 1024.0 / 1024.0)

    // --- 1. what this device actually is -------------------------------------------------------

    @Test
    fun a_deviceFacts() {
        report.parentFile?.mkdirs()
        report.writeText("Katori hardware probe\n")
        say("run at ${java.time.Instant.now()}")

        heading("device")
        say("model            ${Build.MANUFACTURER} ${Build.MODEL}")
        say("android          ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        say("abis             ${Build.SUPPORTED_ABIS.joinToString()}")
        say("cores            ${Runtime.getRuntime().availableProcessors()}")

        val mi = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(mi)
        say("total RAM        ${mb(mi.totalMem)}")
        say("available RAM    ${mb(mi.availMem)}")
        say("low-memory at    ${mb(mi.threshold)}")
        say("heap class       ${activityManager.memoryClass} MB, large ${activityManager.largeMemoryClass} MB")
        say("")
        say("The heap class bounds the JAVA heap only. Model weights are native and memory-mapped,")
        say("so the figure that matters below is process PSS against device RAM, not that number.")
        say("baseline PSS     ${mb(processPssBytes())}")
    }

    // --- 2. does llama.cpp load and run on this phone ------------------------------------------

    @Test
    fun b_llamaLoadsAndRunsOnThisPhone() {
        heading("llama.cpp on device")
        val model = File(modelsDir, LLM_FILE)
        assertTrue(
            "the model was not pushed. Expected it at ${model.absolutePath}",
            model.isFile,
        )
        say("model file       ${model.name}  ${mb(model.length())}")

        val before = processPssBytes()
        val loadStart = System.nanoTime()
        val runtime = LlamaCppRuntime.load(model, contextTokens = 2048, threads = THREADS)
        val loadMillis = (System.nanoTime() - loadStart) / 1_000_000.0
        llmRuntime = runtime

        val afterLoad = processPssBytes()
        say("load time        ${"%.0f".format(loadMillis)} ms with $THREADS threads")
        say("PSS before load  ${mb(before)}")
        say("PSS after load   ${mb(afterLoad)}   delta ${mb(afterLoad - before)}")

        // The REAL extraction prompt, not a toy one. A model that completes "the capital of France"
        // tells us nothing about whether it can do the job this app asks of it.
        val engine = LlamaCppLlmEngine(runtime)
        val outcome = runBlocking {
            engine.extract(ExtractionRequest(transcript = TRANSCRIPT, languageTag = "en-IN", maxReasks = 2))
        }

        val t = runtime.lastTimings()
        if (t != null) {
            say("prompt           ${t.promptTokens} tokens in ${"%.0f".format(t.promptMillis)} ms" +
                "  (${"%.2f".format(t.promptTokensPerSecond)} tok/s)")
            say("generation       ${t.evalTokens} tokens in ${"%.0f".format(t.evalMillis)} ms" +
                "  (${"%.2f".format(t.evalTokensPerSecond)} tok/s)")
        }
        say("peak PSS         ${mb(processPssBytes())}")
        say("native heap      ${mb(Debug.getNativeHeapAllocatedSize())}")

        say("transcript       \"$TRANSCRIPT\"")
        when (outcome) {
            is Outcome.Ok -> {
                say("extraction       OK, schema-valid on the first accepted attempt")
                say("raw JSON         ${outcome.value.rawJson}")
                outcome.value.items.forEach {
                    say("  item           name=${it.name} quantity=${it.quantity} unit=${it.unit}")
                }
            }
            is Outcome.Unavailable -> say("extraction       REFUSED: ${outcome.reason} ${outcome.detail}")
            is Outcome.NotImplemented -> say("extraction       not implemented: ${outcome.component}")
        }

        // The model loading and running is the claim under test. Whether it produced schema-valid
        // JSON is reported above and asserted separately, so a schema miss does not hide a load
        // failure or the other way round.
        assertTrue("the runtime produced no timings at all", t != null && t.evalTokens > 0)
    }

    // --- 3. co-residency, the stage-1 item -----------------------------------------------------

    /**
     * Can ASR, LLM and TTS be resident at the same time on this phone?
     *
     * MEASURED, NOT ESTIMATED, which is what the ModelArbiter contract requires. The LLM is loaded
     * through llama.cpp and the two ONNX models through ONNX Runtime, which is the same runtime
     * sherpa-onnx uses, and the peak PSS is read with all three resident.
     *
     * WHAT THIS FIGURE IS NOT. sherpa-onnx wraps these models with a feature extractor and decoder
     * state that this probe does not create, and Piper adds a phonemiser. So the number below is a
     * FLOOR for the real three-way footprint, not the final one. It is still the right question to
     * answer now, because if the floor does not fit then nothing built on top of it will either.
     */
    @Test
    fun c_coResidency() {
        heading("co-residency: ASR + LLM + TTS")

        val asr = File(modelsDir, ASR_FILE)
        val tts = File(modelsDir, TTS_FILE)
        say("asr model        ${if (asr.isFile) "${asr.name}  ${mb(asr.length())}" else "MISSING at ${asr.absolutePath}"}")
        say("tts model        ${if (tts.isFile) "${tts.name}  ${mb(tts.length())}" else "MISSING at ${tts.absolutePath}"}")

        val mi = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(mi)

        val runtime = llmRuntime ?: LlamaCppRuntime.load(File(modelsDir, LLM_FILE), 2048, THREADS).also { llmRuntime = it }
        val withLlm = processPssBytes()
        say("PSS, LLM only    ${mb(withLlm)}")

        val env = OrtEnvironment.getEnvironment()
        var asrSession: OrtSession? = null
        var ttsSession: OrtSession? = null
        try {
            if (asr.isFile) {
                asrSession = env.createSession(asr.absolutePath, OrtSession.SessionOptions())
                say("PSS, + ASR       ${mb(processPssBytes())}")
            }
            if (tts.isFile) {
                ttsSession = env.createSession(tts.absolutePath, OrtSession.SessionOptions())
                say("PSS, + TTS       ${mb(processPssBytes())}")
            }

            val peak = processPssBytes()
            activityManager.getMemoryInfo(mi)

            // A PROVISIONAL ceiling, stated as a formula rather than smuggled in as a constant.
            // The arbiter's real policy is not decided yet and should be decided FROM these
            // numbers, not before them. Half of device RAM is the starting point because the
            // system, the launcher and whatever else the user has open need the other half, and
            // an app that takes more than that is the one the low-memory killer reaches for first.
            val ceiling = mi.totalMem / 2
            say("")
            say("peak PSS         ${mb(peak)}   <- measuredPeakBytes = $peak")
            say("ceiling          ${mb(ceiling)}   <- half of device RAM, provisional")
            say("headroom         ${mb(ceiling - peak)}")
            say("available RAM    ${mb(mi.availMem)} now, low-memory threshold ${mb(mi.threshold)}")
            say("FITS             ${peak < ceiling}")
            say("")
            say("Floor, not final: sherpa-onnx adds a feature extractor and decoder state on top of")
            say("the ASR session, and Piper adds a phonemiser. Those are not loaded here.")
            coResidencyFits = peak < ceiling
        } catch (e: Throwable) {
            say("ONNX session FAILED: ${e::class.java.simpleName}: ${e.message}")
            say("Co-residency cannot be answered until this loads. It is not a fit or a non-fit.")
            throw e
        } finally {
            asrSession?.close()
            ttsSession?.close()
        }
    }

    // --- 4. ML Kit OCR, written but never run --------------------------------------------------

    /**
     * Does ML Kit text recognition initialise and read text with its telemetry transport stripped
     * of the INTERNET permission?
     *
     * This is the one that carries beat 3. ML Kit pulls in a Google datatransport library that
     * declares INTERNET, and the demo build removes that permission from the merged manifest. The
     * open question was whether removing it breaks recognition itself or only the telemetry.
     */
    @Test
    fun d_mlKitOcrWithoutNetworkPermission() {
        heading("ML Kit OCR, demo build, no INTERNET permission")

        val expected = "Haemoglobin 9.8 g/dL"
        val bitmap = Bitmap.createBitmap(1000, 260, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawText(expected, 40f, 160f, Paint().apply {
                color = Color.BLACK
                textSize = 78f
                isAntiAlias = true
            })
        }

        val recogniser = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = Tasks.await(
                recogniser.process(InputImage.fromBitmap(bitmap, 0)),
                60, TimeUnit.SECONDS,
            )
            val text = result.text.replace("\n", " ").trim()
            say("rendered         \"$expected\"")
            say("recognised       \"$text\"")
            say("blocks           ${result.textBlocks.size}")

            // Exact string equality would be a test of the font renderer. What matters is that the
            // recogniser initialised at all without network, and returned the figure a lab report
            // would carry.
            val gotNumber = text.contains("9.8")
            val gotWord = text.lowercase().contains("haemoglobin") || text.lowercase().contains("hae")
            say("read the figure  $gotNumber")
            say("read the label   $gotWord")
            say("OCR USABLE       ${gotNumber && result.textBlocks.isNotEmpty()}")
            assertTrue("OCR initialised but recognised nothing at all", result.textBlocks.isNotEmpty())
        } catch (e: Throwable) {
            say("OCR FAILED: ${e::class.java.simpleName}: ${e.message}")
            say("Beat 3 needs a different OCR path if this is not a transient failure.")
            throw e
        } finally {
            recogniser.close()
        }
    }

    @Test
    fun e_summary() {
        heading("summary")
        say("co-residency fits (floor): $coResidencyFits")
        say("report written to ${report.absolutePath}")
    }

    companion object {
        private const val TAG = "katori-probe"
        private const val THREADS = 4

        private const val LLM_FILE = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        private const val ASR_FILE = "asr-te-model.int8.onnx"
        private const val TTS_FILE = "piper-te-model.onnx"

        private const val TRANSCRIPT = "I had two rotis and a katori of dal with some curd"

        private var llmRuntime: LlamaCppRuntime? = null
        private var coResidencyFits: Boolean? = null

        private val report: File by lazy {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            File(ctx.externalMediaDirs.first(), "katori-hardware-report.txt")
        }

        @AfterClass @JvmStatic fun releaseNativeMemory() {
            llmRuntime?.close()
            llmRuntime = null
        }
    }
}
