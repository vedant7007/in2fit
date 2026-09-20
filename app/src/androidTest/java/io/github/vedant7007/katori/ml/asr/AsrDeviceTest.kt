package io.github.vedant7007.katori.ml.asr

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vedant7007.katori.data.food.AndroidFoodDbSource
import io.github.vedant7007.katori.data.food.FoodQuery
import io.github.vedant7007.katori.data.food.SqliteFoodLookup
import io.github.vedant7007.katori.data.local.AndroidDeviceMemory
import io.github.vedant7007.katori.data.local.FileMeasurementLog
import io.github.vedant7007.katori.domain.DefaultModelArbiter
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.ml.llm.ExtractionRequest
import io.github.vedant7007.katori.ml.llm.LlamaCppLlmEngine
import io.github.vedant7007.katori.ml.llm.LlamaCppRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer

/**
 * THE ASR HARDWARE PROBE. The first device figures for the speech path, all of which are unknown
 * as this is written: whether IndicConformer opens through sherpa-onnx on this phone at all, what
 * it costs resident, how long a clip takes, what it transcribes, and whether the LLM then recovers
 * the foods from what it transcribed.
 *
 * WRITTEN BY JACOB, RUN BY RAO. Only one session drives the phone (`COORDINATION.md`). Nothing in
 * this file is a claim about the device until its report has been read.
 *
 * STAGED, NOT BUNDLED. Models live where the hardware probe stages them, `filesDir/models` first
 * and external media second, laid out as `AsrModels` describes. The clips and their manifest are
 * staged beside them in `asr-test-set/` (the output of `asr_eval.py synth`, or the recorded set
 * when it exists, same manifest format). A missing file FAILS with the path it looked in; it does
 * not skip, because a skipped hardware check reads as a pass three days later.
 *
 * WHAT THE NUMBERS ARE. Test b prints WER and CER per language and labels every line with the
 * manifest's `source`. SYNTHETIC rows are circular by construction (`asr_eval.py` says why) and
 * measure that the model works here, not that it is accurate. Test c prints foods recovered and
 * foods WRONG, which is the metric that matters (`HANDOVER.md` §6 rule 17): a miss is honest and
 * the person gets asked; a wrong food is a wrong number in a health app. Neither test asserts an
 * accuracy figure on synthetic data. They assert that the path runs.
 *
 * Run with `am instrument`, not gradle (`0012`): gradle uninstalls the APKs and wipes the staging.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AsrDeviceTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private val modelsDir: File
        get() = File(ctx.filesDir, "models")
            .takeIf { File(it, AsrModels.handleFor(SpeechLanguage.TELUGU).relativePath).length() > 0L }
            ?: File(ctx.externalMediaDirs.first(), "models")

    private val testSetDir: File get() = File(modelsDir.parentFile, "asr-test-set")

    private fun say(line: String) {
        Log.i(TAG, line)
        report.appendText(line + "\n")
    }

    private fun mb(bytes: Long) = "%,.1f MB".format(bytes / 1024.0 / 1024.0)

    private fun arbiter() = DefaultModelArbiter(
        memory = AndroidDeviceMemory(ctx),
        loader = SherpaOnnxAsrLoader(modelsDir, threads = THREADS),
        log = FileMeasurementLog(ctx),
        buildTag = "asr-probe",
    )

    /** transcribe() never touches the microphone; this makes that visible if it ever did. */
    private object NoMicrophone : AudioSource {
        override fun canRecord() = false
        override fun frames(sampleRateHz: Int, frameMs: Int): Flow<ShortArray> = emptyFlow()
    }

    // --- a. does it open at all, and what does it cost ------------------------------------------

    @Test
    fun a_teModelOpensThroughTheArbiter() {
        say(""); say("=== ASR: IndicConformer te through the arbiter, ${modelsDir.absolutePath} ===")
        val handle = AsrModels.handleFor(SpeechLanguage.TELUGU)
        val model = File(modelsDir, handle.relativePath)
        val tokens = File(modelsDir, AsrModels.tokensFileFor(handle.relativePath))
        say("model            ${model.absolutePath}  ${if (model.isFile) mb(model.length()) else "MISSING"}")
        say("tokens           ${tokens.absolutePath}  ${if (tokens.isFile) "${tokens.length()} B" else "MISSING"}")
        assertTrue("stage the te model at ${model.absolutePath}", model.length() > 0L)
        assertTrue("stage the shared tokens.txt at ${tokens.absolutePath}", tokens.length() > 0L)

        val arbiter = arbiter()
        val t0 = System.nanoTime()
        val report = runBlocking { arbiter.canCoReside(listOf(handle)) }
        val ms = (System.nanoTime() - t0) / 1_000_000
        when (report) {
            is Outcome.Ok -> {
                val r = report.value
                say("opened in        $ms ms (load + one PSS read + release), $THREADS threads")
                say("PSS peak         ${mb(r.measuredPeakBytes)}  ceiling ${mb(r.ceilingBytes)}  fits=${r.fits}")
                say("0013 saw a raw ORT session on this file cost 165-260 MB. This row includes sherpa-onnx's")
                say("feature extractor and decoder state, which that floor did not.")
                assertTrue("the te ASR model must fit through the arbiter", r.fits)
            }
            is Outcome.Unavailable -> fail("ASR did not open: ${report.reason} ${report.detail}")
            is Outcome.NotImplemented -> fail("not built: ${report.component}")
        }
    }

    // --- b. what it transcribes, and how long it takes -----------------------------------------

    @Test
    fun b_transcribesTheStagedClips() {
        say(""); say("=== ASR: staged clips, ${testSetDir.absolutePath} ===")
        val rows = manifest()
        val arbiter = arbiter()
        val engine = DefaultAsrEngine(arbiter, NoMicrophone)

        val warmed = mutableSetOf<SpeechLanguage>()
        val perLang = mutableMapOf<String, Totals>()
        var anyOk = false
        for (row in rows) {
            val language = SpeechLanguage.entries.first { it.tag.substringBefore('-') == row.language }
            val clip = readWav(File(testSetDir, row.path))
            if (language !in warmed) {
                // COLD AGAINST WARM, the number 0032's warm-up spec rests on. The first clip of each
                // language is decoded three times: once truly cold (model not resident), once after
                // prepare(), and once more warm. Only the last counts in the WER table's timing.
                val t0 = System.nanoTime()
                val cold = runBlocking { engine.transcribe(clip, language) }
                val coldMs = (System.nanoTime() - t0) / 1_000_000
                val t1 = System.nanoTime()
                val prepared = runBlocking { engine.prepare(language) }
                val prepareMs = (System.nanoTime() - t1) / 1_000_000
                val t2 = System.nanoTime()
                runBlocking { engine.transcribe(clip, language) }
                val warmMs = (System.nanoTime() - t2) / 1_000_000
                say("[${row.language}] COLD first transcribe (load + first decode) ${coldMs} ms -> ${cold::class.java.simpleName}; prepare() ${prepareMs} ms -> ${prepared::class.java.simpleName}; WARM transcribe ${warmMs} ms; ${THREADS} threads")
                warmed += language
            }
            val t0 = System.nanoTime()
            val outcome = runBlocking { engine.transcribe(clip, language) }
            val ms = (System.nanoTime() - t0) / 1_000_000
            val secs = clip.samples.size.toDouble() / clip.sampleRateHz
            when (outcome) {
                is Outcome.Ok -> {
                    anyOk = true
                    val t = outcome.value
                    val wErr = Wer.words(row.reference, t.text)
                    val cErr = Wer.chars(row.reference, t.text)
                    perLang.getOrPut(row.language) { Totals() }.add(row.reference, wErr, cErr, ms, row.source)
                    transcripts[row.path] = t.text
                    say("[${row.language}] ${row.source.uppercase()} ${row.path}  ${"%.1f".format(secs)}s audio  $ms ms  ${t.confidence}  ${if (wErr == 0) "exact" else "$wErr word err, $cErr char err"}")
                    say("      ref: ${row.reference}")
                    say("      hyp: ${t.text}")
                }
                is Outcome.Unavailable -> {
                    perLang.getOrPut(row.language) { Totals() }.add(row.reference, Wer.words(row.reference, ""), Wer.chars(row.reference, ""), ms, row.source)
                    say("[${row.language}] ${row.source.uppercase()} ${row.path}  ${"%.1f".format(secs)}s audio  $ms ms  UNAVAILABLE ${outcome.reason} ${outcome.detail}")
                }
                is Outcome.NotImplemented -> fail("not built: ${outcome.component}")
            }
        }
        say("")
        say("lang  source     clips   WER     CER   decode ms/clip   (device, $THREADS threads)")
        for ((lang, t) in perLang.toSortedMap()) {
            say("$lang    ${t.sources.joinToString("+").padEnd(9)} ${t.n.toString().padStart(5)}  ${"%5.1f".format(t.wer())}%  ${"%5.1f".format(t.cer())}%  ${(t.ms / t.n).toString().padStart(8)}")
        }
        if (perLang.values.any { "SYNTHETIC" in it.sources }) {
            say("SYNTHETIC rows are circular by construction and are not an accuracy claim.")
        }
        assertTrue("the model transcribed nothing on this device; read the UNAVAILABLE lines above", anyOk)
    }

    // --- c. does the LLM recover the foods from what was transcribed ---------------------------

    /**
     * Extraction accuracy, measured separately from WER on purpose: a transcript with a wrong
     * vowel sign can still yield the right meal, and a perfect transcript can lose a food in
     * extraction (`0014` records the 1.5B model dropping the egg). Two counts per row:
     * FOUND, expected foods that some extracted item resolves to; and WRONG, extracted items that
     * resolve to a food nobody said. WRONG is the one that matters.
     *
     * Resolution goes through the same `SqliteFoodLookup` the app uses, so "the same food" means
     * the same `FoodCode` the matcher would give the canonical English word.
     */
    @Test
    fun c_extractsFoodsFromTheTranscripts() {
        say(""); say("=== ASR -> LLM: foods recovered from the transcripts ===")
        val llmFile = File(modelsDir, LLM_FILE)
        assertTrue("stage the LLM at ${llmFile.absolutePath}", llmFile.length() > 0L)
        if (transcripts.isEmpty()) {
            // Test b did not run in THIS process: `am instrument` on one method is a fresh process, so
            // the companion map is empty however b went. Transcribe here rather than depend on order.
            say("test b did not run in this process; transcribing the staged clips first")
            val engine = DefaultAsrEngine(arbiter(), NoMicrophone)
            for (row in manifest()) {
                val language = SpeechLanguage.entries.first { it.tag.substringBefore('-') == row.language }
                when (val o = runBlocking { engine.transcribe(readWav(File(testSetDir, row.path)), language) }) {
                    is Outcome.Ok -> transcripts[row.path] = o.value.text
                    is Outcome.Unavailable -> say("  ${row.path}: UNAVAILABLE ${o.reason} ${o.detail}")
                    is Outcome.NotImplemented -> fail("not built: ${o.component}")
                }
            }
        }
        assertTrue("no clip transcribed; read the UNAVAILABLE lines above", transcripts.isNotEmpty())
        val rows = manifest().filter { it.path in transcripts }

        val runtime = LlamaCppRuntime.load(llmFile, contextTokens = 2048, threads = THREADS)
        val lookup = SqliteFoodLookup(AndroidFoodDbSource.open(ctx))
        try {
            val llm = LlamaCppLlmEngine(runtime)
            var expectedTotal = 0
            var found = 0
            var wrong = 0
            var refused = 0
            val sources = sortedSetOf<String>()
            for (row in rows) {
                sources += row.source.uppercase()
                val transcript = transcripts.getValue(row.path)
                val tag = SpeechLanguage.entries.first { it.tag.substringBefore('-') == row.language }.tag
                val t0 = System.nanoTime()
                val extracted = runBlocking { llm.extract(ExtractionRequest(transcript = transcript, languageTag = tag, maxReasks = 2)) }
                val ms = (System.nanoTime() - t0) / 1_000_000
                val expected = row.expectedFoods.associateWith { code(lookup, it, "en-IN") }
                expectedTotal += expected.size
                when (extracted) {
                    is Outcome.Ok -> {
                        val got = extracted.value.items.map { it.name to code(lookup, it.name, tag) }
                        val hits = expected.filter { (_, c) -> c != null && got.any { it.second == c } }
                        val wrongHere = got.filter { (_, c) -> c != null && c !in expected.values }
                        found += hits.size; wrong += wrongHere.size
                        say("[${row.language}] ${row.source.uppercase()} ${row.path}  $ms ms  found ${hits.size}/${expected.size}  wrong ${wrongHere.size}")
                        say("      transcript: $transcript")
                        say("      extracted:  ${got.map { (n, c) -> "$n->${c?.id ?: "NO_MATCH"}" }}")
                        if (wrongHere.isNotEmpty()) say("      WRONG FOOD: ${wrongHere.map { it.first }}")
                    }
                    is Outcome.Unavailable -> {
                        refused++
                        say("[${row.language}] ${row.source.uppercase()} ${row.path}  $ms ms  extraction UNAVAILABLE ${extracted.reason} ${extracted.detail}")
                        say("      transcript: $transcript")
                    }
                    is Outcome.NotImplemented -> fail("not built: ${extracted.component}")
                }
            }
            say("")
            say("extraction (${sources.joinToString("+")}): $found of $expectedTotal expected foods recovered, $wrong WRONG, $refused refused, over ${rows.size} transcripts")
            if ("SYNTHETIC" in sources) say("SYNTHETIC rows are circular by construction and are not an accuracy claim.")
            say("WRONG is the metric. A miss asks the person; a wrong food logs a wrong number.")
        } finally {
            runtime.close()
        }
    }

    private fun code(lookup: SqliteFoodLookup, name: String, tag: String) =
        (runBlocking { lookup.resolve(FoodQuery(spokenName = name, languageTag = tag)) } as? Outcome.Ok)?.value?.code

    // --- fixtures -----------------------------------------------------------------------------

    private class Row(val path: String, val language: String, val source: String, val reference: String, val expectedFoods: List<String>)

    private fun manifest(): List<Row> {
        val file = File(testSetDir, "manifest.csv")
        assertTrue("stage the clips and manifest.csv at ${testSetDir.absolutePath} (asr_eval.py synth writes them)", file.isFile)
        val lines = file.readLines().filter { it.isNotBlank() }
        val header = lines.first().split(',')
        fun col(fields: List<String>, name: String) = fields[header.indexOf(name)]
        return lines.drop(1).map { line ->
            // The reference may contain no commas; asr_eval.py writes none. Split on the first four.
            val f = line.split(',', limit = header.size)
            Row(col(f, "path"), col(f, "language"), col(f, "source"), col(f, "reference"), col(f, "expected_foods").split(';').filter { it.isNotBlank() })
        }
    }

    private class Totals {
        var n = 0; var wErr = 0; var wRef = 0; var cErr = 0; var cRef = 0; var ms = 0L
        val sources = sortedSetOf<String>()
        fun add(reference: String, w: Int, c: Int, millis: Long, source: String) {
            n++; wErr += w; wRef += Wer.norm(reference).size; cErr += c; cRef += Wer.norm(reference).joinToString("").length; ms += millis
            sources += source.uppercase()
        }
        fun wer() = 100.0 * wErr / maxOf(1, wRef)
        fun cer() = 100.0 * cErr / maxOf(1, cRef)
    }

    /** The same normalisation and edit distance as asr_eval.py, so a desktop row and a device row compare. */
    private object Wer {
        private val punct = ".,!?;:\"'()[]{}।॥-–—".toSet()
        fun norm(s: String): List<String> =
            Normalizer.normalize(s, Normalizer.Form.NFC).map { if (it in punct) ' ' else it }.joinToString("").lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun words(ref: String, hyp: String) = distance(norm(ref), norm(hyp))
        fun chars(ref: String, hyp: String) = distance(norm(ref).joinToString("").toList(), norm(hyp).joinToString("").toList())
        private fun <T> distance(a: List<T>, b: List<T>): Int {
            var prev = IntArray(b.size + 1) { it }
            for (i in 1..a.size) {
                val cur = IntArray(b.size + 1); cur[0] = i
                for (j in 1..b.size) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = cur
            }
            return prev[b.size]
        }
    }

    /** Minimal RIFF/WAVE reader: PCM 16-bit, mono or averaged to mono, any rate. */
    private fun readWav(file: File): AudioClip {
        assertTrue("clip missing: ${file.absolutePath}", file.isFile)
        val bytes = file.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        check(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "${file.name} is not a WAV" }
        var pos = 12
        var channels = 1; var rate = 16_000; var bits = 16
        var data: ShortArray? = null
        while (pos + 8 <= bytes.size && data == null) {
            val id = String(bytes, pos, 4); val size = buf.getInt(pos + 4)
            when (id) {
                "fmt " -> { channels = buf.getShort(pos + 10).toInt(); rate = buf.getInt(pos + 12); bits = buf.getShort(pos + 22).toInt() }
                "data" -> {
                    check(bits == 16) { "${file.name}: $bits-bit, only 16-bit PCM is read here" }
                    val frames = size / 2 / channels
                    data = ShortArray(frames) { i ->
                        var sum = 0
                        for (c in 0 until channels) sum += buf.getShort(pos + 8 + (i * channels + c) * 2)
                        (sum / channels).toShort()
                    }
                }
            }
            pos += 8 + size + (size and 1)
        }
        return AudioClip(checkNotNull(data) { "${file.name}: no data chunk" }, rate)
    }

    companion object {
        private const val TAG = "katori-asr"
        /** Four, as the desktop figures in 0021 were taken. The first knob to turn on this device. */
        private const val THREADS = 4
        private const val LLM_FILE = "qwen2.5-1.5b-instruct-q4_k_m.gguf"

        /**
         * Test b's transcripts for test c, when both run in one process. JUnit makes a new instance
         * per method, hence the companion; a separate `am instrument` per method is a new PROCESS,
         * hence test c filling it for itself when it finds it empty.
         */
        private val transcripts = linkedMapOf<String, String>()

        private val report: File by lazy {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            File(ctx.externalMediaDirs.first(), "katori-asr-report.txt").also { it.parentFile?.mkdirs() }
        }
    }
}
