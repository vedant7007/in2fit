package io.github.vedant7007.katori.orchestration

import android.os.Build
import android.os.Debug
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vedant7007.katori.data.food.AndroidFoodDbSource
import io.github.vedant7007.katori.data.food.LookupMealResolver
import io.github.vedant7007.katori.data.food.SqliteFoodLookup
import io.github.vedant7007.katori.data.food.SqliteSpokenNames
import io.github.vedant7007.katori.data.knowledge.KnowledgeFacts
import io.github.vedant7007.katori.data.local.AndroidContextStrings
import io.github.vedant7007.katori.data.local.AndroidDeviceMemory
import io.github.vedant7007.katori.data.local.AndroidTriggerStrings
import io.github.vedant7007.katori.data.local.FileMeasurementLog
import io.github.vedant7007.katori.data.local.KatoriDatabase
import io.github.vedant7007.katori.data.local.RoomAdviceStore
import io.github.vedant7007.katori.data.local.RoomLabStore
import io.github.vedant7007.katori.data.local.RoomMealStore
import io.github.vedant7007.katori.data.local.RoomUserContextSource
import io.github.vedant7007.katori.data.local.entity.ConditionEntity
import io.github.vedant7007.katori.data.local.entity.LabValueEntity
import io.github.vedant7007.katori.data.local.entity.ProfileEntity
import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.DefaultModelArbiter
import io.github.vedant7007.katori.domain.DefaultRulesEngine
import io.github.vedant7007.katori.domain.LabValue
import io.github.vedant7007.katori.domain.OrchestratorEvent
import io.github.vedant7007.katori.domain.SpeechLanguageRef
import io.github.vedant7007.katori.domain.TriggerText
import io.github.vedant7007.katori.domain.UserIntent
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.ml.asr.AsrEngine
import io.github.vedant7007.katori.ml.asr.AsrEvent
import io.github.vedant7007.katori.ml.asr.AudioClip
import io.github.vedant7007.katori.ml.asr.SpeechLanguage
import io.github.vedant7007.katori.ml.asr.Transcript
import io.github.vedant7007.katori.ml.llm.LlamaCppLlmEngine
import io.github.vedant7007.katori.ml.llm.LlamaCppModelLoader
import io.github.vedant7007.katori.ml.llm.LlamaCppRuntime
import io.github.vedant7007.katori.ml.llm.LlmEngine
import io.github.vedant7007.katori.ml.tts.TtsEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking

import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * THE MEASUREMENT PASS: everything the deck claims, in one unattended run, every raw figure
 * written the moment it exists so a phone that drops off mid-run leaves the stages it finished.
 *
 * Driven by `tools/measurement-pass.ps1`, which asserts airplane mode and adb ownership before
 * this starts, writes the header (device, build, timestamp, commit), and appends
 * `AsrDeviceTest.b` cold and warm as stage 5. This class is stages 1 to 4:
 *
 *   1. ANSWER: first figure on screen and spoken sentence, cold (first call after a fresh load
 *      in a fresh process) and warm (three more calls).
 *   2. AdviseOnMeal, first tap: after a LOG (precomputed), three taps; then after a lab report
 *      is saved (regenerated inside the save), three more.
 *   3. LOG, two foods and three foods: plate on screen and spoken sentence, twice each.
 *   4. Peak PSS across the pass against the arbiter's calibrated ceiling.
 *
 * RAW FIGURES ONLY, never a mean. Lines are `RAW stage=<n> <key>=<value> ...`; the script does
 * not summarise them either. `-e commit <sha>` and `-e claimable false` (an emulator) are
 * echoed into the report so the numbers cannot be detached from what produced them.
 */
@RunWith(AndroidJUnit4::class)
class MeasurementPassTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val args = InstrumentationRegistry.getArguments()

    private val report: File by lazy { File(ctx.externalMediaDirs.first(), "katori-measurement-pass.txt") }

    private fun say(line: String) { Log.i(TAG, line); report.appendText(line + "\n") }
    private fun raw(stage: Int, vararg kv: Pair<String, Any?>) = say("RAW stage=$stage " + kv.joinToString(" ") { "${it.first}=${it.second}" })
    private fun begin(stage: Int, title: String) = say("=== STAGE $stage BEGIN $title ===")
    private fun end(stage: Int) = say("=== STAGE $stage END ===")

    private val modelsDir: File
        get() = File(ctx.filesDir, "models").takeIf { File(it, LLM_FILE).length() > 0L }
            ?: File(ctx.externalMediaDirs.first(), "models")

    private class Silent : TtsEngine {
        override val supportedLanguages = SpeechLanguage.entries.toSet()
        override suspend fun prepare(language: SpeechLanguage) = Outcome.Ok(Unit)
        override suspend fun speak(text: String, language: SpeechLanguage) = Outcome.Ok(Unit)
        override suspend fun stop() {}
    }

    private class NoAsr : AsrEngine {
        override val supportedLanguages = SpeechLanguage.entries.toSet()
        override suspend fun prepare(language: SpeechLanguage) = Outcome.Ok(Unit)
        override fun listen(language: SpeechLanguage): Flow<AsrEvent> = emptyFlow()
        override suspend fun transcribe(clip: AudioClip, language: SpeechLanguage): Outcome<Transcript> = Outcome.NotImplemented("pass")
    }

    /** One turn's raw timings, taken off the event stream. */
    private class Turn {
        var firstFigureMs: Long? = null
        var plateMs: Long? = null
        var spokenMs: Long? = null
        /** Completed (or the stream ended) at this ms; the figure for a turn that speaks nothing. */
        var totalMs: Long? = null
        var completed = false
        var failed: String? = null
        var intent: String? = null
    }

    @Test
    fun pass() {
        report.delete()
        val claimable = args.getString("claimable", "true") == "true"
        say("=== MEASUREMENT PASS ${LocalDateTime.now()}  device ${Build.MANUFACTURER} ${Build.MODEL}  android ${Build.VERSION.RELEASE}  commit ${args.getString("commit", "unknown")}  claimable=$claimable ===")
        if (!claimable) say("NOT CLAIMABLE: this run is on an emulator; it proves the harness, not the numbers.")

        val model = File(modelsDir, LLM_FILE)
        if (model.length() <= 0L) {
            say("STAGES 1-4 NOT RUN: the model is not staged at ${model.absolutePath}")
            // Stage 4 can still say what the ceiling is.
            begin(4, "peak memory against the ceiling"); ceilingLines(); end(4)
            return
        }

        val peakPss = AtomicLong(0)
        val sampler = thread(isDaemon = true, name = "pss-sampler") {
            while (!Thread.currentThread().isInterrupted) {
                runCatching { val kb = Debug.getPss(); if (kb > peakPss.get()) peakPss.set(kb) }
                try { Thread.sleep(500) } catch (_: InterruptedException) { break }
            }
        }

        // The ceiling and the pre-load footprint go on file BEFORE the model loads: a low-memory
        // kill is SIGKILL, no finally runs, and the file must still say what the device had.
        // (The 4 GB emulator killed the process at 2.29 GB RSS during the load, 21 Sep 00:00.)
        begin(4, "peak memory against the ceiling"); ceilingLines(); raw(4, "pss_before_load_kb" to Debug.getPss())
        val db = Room.inMemoryDatabaseBuilder(ctx, KatoriDatabase::class.java).build()
        val foods = AndroidFoodDbSource.open(ctx)
        val t0 = System.nanoTime()
        val runtime = LlamaCppRuntime.load(model, contextTokens = 2048, threads = THREADS)
        say("model load ms=${(System.nanoTime() - t0) / 1_000_000} threads=$THREADS")
        raw(4, "pss_after_load_kb" to Debug.getPss())
        val engine = LlamaCppLlmEngine(runtime)
        val lease = object : LlmLease {
            override suspend fun <T> use(block: suspend (LlmEngine) -> T): Outcome<T> = Outcome.Ok(block(engine))
        }
        val orchestrator = DefaultOrchestrator(
            asr = NoAsr(), llm = lease, tts = Silent(), rules = DefaultRulesEngine(),
            resolver = LookupMealResolver(SqliteFoodLookup(foods)), store = RoomMealStore(db),
            advice = RoomAdviceStore(db), labs = RoomLabStore(db), contextSource = RoomUserContextSource(db, foods),
            knowledge = KnowledgeFacts.load { ctx.assets.open(KnowledgeFacts.ASSET_PATH) },
            triggerText = TriggerText(AndroidTriggerStrings(ctx)),
            contextText = ContextText(AndroidContextStrings(ctx), AndroidTriggerStrings(ctx)),
            spokenNames = SqliteSpokenNames(foods),
        )
        runBlocking {
            db.profileDao().upsert(ProfileEntity(1, 19, 62.0, 172.0, "MALE", "MAINTAIN", "HOSTEL_STUDENT", "VEGETARIAN", System.currentTimeMillis()))
            db.conditionDao().upsert(ConditionEntity(name = "anaemia", source = "USER_DECLARED", recorded_at_epoch_ms = System.currentTimeMillis()))
            db.labValueDao().insertAll(listOf(LabValueEntity(test_name = "Haemoglobin", value = 9.8, unit = "g/dL", reference_low = 12.0, reference_high = 15.0, report_date = "2026-09-12", captured_at_epoch_ms = System.currentTimeMillis())))
        }
        val en = SpeechLanguageRef("en-IN")
        var lastMealId: Long? = null

        fun turn(intent: UserIntent): Turn {
            val t = Turn()
            val start = System.nanoTime()
            fun ms() = (System.nanoTime() - start) / 1_000_000
            runBlocking {
                orchestrator.handle(intent).collect { e ->
                    when (e) {
                        is OrchestratorEvent.IntentKnown -> t.intent = e.intent.name
                        is OrchestratorEvent.OwnFigures -> if (t.firstFigureMs == null && e.lines.isNotEmpty()) t.firstFigureMs = ms()
                        is OrchestratorEvent.MealResolved -> t.plateMs = ms()
                        is OrchestratorEvent.MealLogged -> lastMealId = e.mealId
                        is OrchestratorEvent.Advice, is OrchestratorEvent.Answered -> t.spokenMs = ms()
                        is OrchestratorEvent.Failed -> t.failed = "${e.reason} ${e.detail}"
                        is OrchestratorEvent.NeedsConfirmation -> t.failed = "NEEDS_CONFIRMATION ${e.why}"
                        is OrchestratorEvent.NeedsIntent -> t.failed = "NEEDS_INTENT"
                        OrchestratorEvent.Completed -> t.completed = true
                        else -> Unit
                    }
                }
            }
            t.totalMs = ms()
            return t
        }
        fun timings(): String = runtime.lastTimings()?.let { "prompt_tok=${it.promptTokens} prompt_ms=${"%.0f".format(it.promptMillis)} gen_tok=${it.evalTokens} gen_ms=${"%.0f".format(it.evalMillis)}" } ?: "timings=none"

        try {
            // A meal in the diary first, so ANSWER has something to answer from. Also stage 3's first row.
            begin(3, "LOG two foods and three foods: plate and spoken")
            val logTwo = "I had two rotis and a katori of dal"
            val logThree = "One plate of rice, dal and a bowl of curd."
            repeat(2) { i ->
                val t = turn(UserIntent.Type(logTwo, en))
                raw(3, "foods" to 2, "run" to i + 1, "plate_ms" to t.plateMs, "spoken_ms" to t.spokenMs, "completed" to t.completed, "failed" to t.failed)
                say("    " + timings())
            }
            repeat(2) { i ->
                val t = turn(UserIntent.Type(logThree, en))
                raw(3, "foods" to 3, "run" to i + 1, "plate_ms" to t.plateMs, "spoken_ms" to t.spokenMs, "completed" to t.completed, "failed" to t.failed)
                say("    " + timings())
            }
            end(3)

            begin(1, "ANSWER first figure and spoken, cold then warm")
            val question = "did I get enough iron this week"
            // Cold, as far as a fresh process gives it: the first ANSWER this runtime has generated.
            run {
                val t = turn(UserIntent.Type(question, en))
                raw(1, "kind" to "cold", "first_figure_ms" to t.firstFigureMs, "spoken_ms" to t.spokenMs, "intent" to t.intent, "completed" to t.completed, "failed" to t.failed)
                say("    " + timings())
            }
            repeat(3) { i ->
                val t = turn(UserIntent.Type(question, en))
                raw(1, "kind" to "warm", "run" to i + 1, "first_figure_ms" to t.firstFigureMs, "spoken_ms" to t.spokenMs, "intent" to t.intent, "completed" to t.completed, "failed" to t.failed)
                say("    " + timings())
            }
            end(1)

            begin(2, "AdviseOnMeal first tap: precomputed at LOG, then regenerated by the report")
            val mealId = lastMealId
            if (mealId == null) {
                say("STAGE 2 NOT RUN: no meal was logged in stage 3")
            } else {
                repeat(3) { i ->
                    val t = turn(UserIntent.AdviseOnMeal(mealId))
                    raw(2, "kind" to "after_log", "tap" to i + 1, "advice_ms" to t.spokenMs, "completed" to t.completed, "failed" to t.failed)
                }
                val tSave = turn(UserIntent.SaveLabReport(listOf(LabValue("Fasting glucose", 260.0, "mg/dL", 70.0, 100.0, LocalDate.of(2026, 9, 20)))))
                raw(2, "kind" to "save_lab_report", "save_ms" to tSave.totalMs, "completed" to tSave.completed, "failed" to tSave.failed)
                say("    " + timings())
                repeat(3) { i ->
                    val t = turn(UserIntent.AdviseOnMeal(mealId))
                    raw(2, "kind" to "after_report", "tap" to i + 1, "advice_ms" to t.spokenMs, "completed" to t.completed, "failed" to t.failed)
                }
            }
            end(2)
        } finally {
            raw(4, "peak_pss_kb" to peakPss.get(), "current_pss_kb" to Debug.getPss())
            end(4)
            runtime.close()
            db.close()
            sampler.interrupt()
            say("=== PASS END ${LocalDateTime.now()} ===")
        }
    }

    private fun ceilingLines() {
        val memory = AndroidDeviceMemory(ctx)
        val arbiter = DefaultModelArbiter(
            memory = memory, loader = LlamaCppModelLoader(modelsDir, threads = THREADS), log = FileMeasurementLog(ctx), buildTag = "measurement-pass",
        )
        raw(4, "ceiling_bytes" to arbiter.memoryCeilingBytes(), "total_ram_bytes" to memory.totalBytes(), "low_memory_threshold_bytes" to memory.lowMemoryThresholdBytes())
    }

    private companion object {
        const val TAG = "katori-pass"
        const val THREADS = 8
        const val LLM_FILE = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    }
}
