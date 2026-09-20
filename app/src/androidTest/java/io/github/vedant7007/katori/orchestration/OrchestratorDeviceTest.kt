package io.github.vedant7007.katori.orchestration

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vedant7007.katori.data.food.AndroidFoodDbSource
import io.github.vedant7007.katori.data.food.LookupMealResolver
import io.github.vedant7007.katori.data.food.SqliteFoodLookup
import io.github.vedant7007.katori.data.knowledge.KnowledgeFacts
import io.github.vedant7007.katori.data.local.AndroidContextStrings
import io.github.vedant7007.katori.data.local.AndroidTriggerStrings
import io.github.vedant7007.katori.data.local.KatoriDatabase
import io.github.vedant7007.katori.data.local.RoomAdviceStore
import io.github.vedant7007.katori.data.local.RoomLabStore
import io.github.vedant7007.katori.data.local.RoomMealStore
import io.github.vedant7007.katori.data.local.RoomUserContextSource
import io.github.vedant7007.katori.data.local.entity.ConditionEntity
import io.github.vedant7007.katori.data.local.entity.LabValueEntity
import io.github.vedant7007.katori.data.local.entity.ProfileEntity
import io.github.vedant7007.katori.domain.ContextText
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
import io.github.vedant7007.katori.ml.llm.ConversationPrompts
import io.github.vedant7007.katori.ml.llm.LlamaCppLlmEngine
import io.github.vedant7007.katori.ml.llm.LlamaCppRuntime
import io.github.vedant7007.katori.ml.llm.LlmEngine
import io.github.vedant7007.katori.ml.tts.TtsEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.time.LocalDate

/**
 * THE FIRST END-TO-END RUN ON A HANDSET: the real orchestrator over a real (in-memory) Room
 * database, the real bundled food database, the real rules engine, the real knowledge file and
 * the real model, driven by typed turns because the microphone and the speaker are the two
 * things a timing run should not wait on. What it reports is what the person waits for, event
 * by event: when their own figures appear, when the intent is known, when the model's sentence
 * lands, and the model's own prompt/generation split for each call.
 *
 * Run with `am instrument`, screen awake, 8 threads (pinned for the demo, `0014`). The report is
 * `katori-e2e-report.txt` beside the hardware report; read it, never the exit code (`0012`).
 * The demo-condition run (USB, airplane mode on, nothing else running, ambient) is this test.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class OrchestratorDeviceTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private val modelsDir: File
        get() = File(ctx.filesDir, "models").takeIf { File(it, LLM_FILE).length() > 0L }
            ?: File(ctx.externalMediaDirs.first(), "models")

    private fun say(line: String) {
        Log.i(TAG, line)
        report.appendText(line + "\n")
    }

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
        override suspend fun transcribe(clip: AudioClip, language: SpeechLanguage): Outcome<Transcript> = Outcome.NotImplemented("test")
    }

    /** The real orchestrator over an in-memory Room seeded with the demo person, and the real model. */
    private inner class Rig(diet: String = "VEGETARIAN", conditions: List<String> = listOf("low iron")) {
        val db = Room.inMemoryDatabaseBuilder(ctx, KatoriDatabase::class.java).build()
        val foods = AndroidFoodDbSource.open(ctx)
        val runtime: LlamaCppRuntime
        val trigger = TriggerText(AndroidTriggerStrings(ctx))
        val orchestrator: DefaultOrchestrator
        var mealId: Long? = null
        /** Milliseconds from the turn's start to its first figure on screen, and to the spoken sentence. */
        var firstFigureMs: Long? = null
        var spokenMs: Long? = null
        var intent: String? = null
        var resolved: String? = null
        var figures: List<String> = emptyList()
        var extractTimings: LlamaCppRuntime.Timings? = null

        init {
            val model = File(modelsDir, LLM_FILE)
            assertTrue("stage the LLM at ${model.absolutePath}", model.length() > 0L)
            val t0 = System.nanoTime()
            runtime = LlamaCppRuntime.load(model, contextTokens = 2048, threads = THREADS)
            say("model load ${(System.nanoTime() - t0) / 1_000_000} ms")
            val engine = LlamaCppLlmEngine(runtime)
            val lease = object : LlmLease {
                override suspend fun <T> use(block: suspend (LlmEngine) -> T): Outcome<T> = Outcome.Ok(block(engine))
            }
            orchestrator = DefaultOrchestrator(
                asr = NoAsr(), llm = lease, tts = Silent(), rules = DefaultRulesEngine(),
                resolver = LookupMealResolver(SqliteFoodLookup(foods)), store = RoomMealStore(db),
                advice = RoomAdviceStore(db), labs = RoomLabStore(db),
                contextSource = RoomUserContextSource(db, foods),
                knowledge = KnowledgeFacts.load { ctx.assets.open(KnowledgeFacts.ASSET_PATH) },
                triggerText = trigger, contextText = ContextText(AndroidContextStrings(ctx), AndroidTriggerStrings(ctx)),
            )
            // The person: a hostel student who declared their condition and has one report on file.
            runBlocking {
                db.profileDao().upsert(ProfileEntity(1, 19, 62.0, 172.0, "MALE", "MAINTAIN", "HOSTEL_STUDENT", diet, System.currentTimeMillis()))
                conditions.forEach { db.conditionDao().upsert(ConditionEntity(name = it, source = "USER_DECLARED", recorded_at_epoch_ms = System.currentTimeMillis())) }
                db.labValueDao().insertAll(listOf(LabValueEntity(test_name = "Haemoglobin", value = 9.8, unit = "g/dL", reference_low = 12.0, reference_high = 15.0, report_date = "2026-09-12", captured_at_epoch_ms = System.currentTimeMillis())))
            }
        }

        fun close() { runtime.close(); db.close() }

        fun turn(label: String, intent: UserIntent) {
            say(""); say("--- $label ---")
            val start = System.nanoTime()
            fun ms() = (System.nanoTime() - start) / 1_000_000
            fun at() = "%6d ms".format(ms())
            firstFigureMs = null; spokenMs = null; this.intent = null; resolved = null; figures = emptyList(); extractTimings = null
            runBlocking {
                orchestrator.handle(intent).collect { e ->
                    when (e) {
                        is OrchestratorEvent.IntentKnown -> this@Rig.intent = e.intent.name
                        is OrchestratorEvent.OwnFigures -> { if (firstFigureMs == null && e.lines.isNotEmpty()) firstFigureMs = ms(); figures = e.lines }
                        is OrchestratorEvent.MealResolved -> {
                            // For a plate the first figure that answers the turn is the plate's own.
                            firstFigureMs = ms()
                            extractTimings = runtime.lastTimings()
                            resolved = e.meal.items.joinToString("; ") { "${it.spokenName}=${it.quantity ?: "?"} ${it.unit ?: ""} -> ${it.matchedFoodCode}".trim() }
                            figures = e.figures.filter { f -> f.total.nutrient.name in setOf("ENERGY", "PROTEIN", "IRON") }.map { f -> "${f.total.nutrient.name.lowercase()} ${"%.1f".format(f.total.amount)} ${f.total.unit.name.lowercase()} ${f.total.completeness.name.lowercase()}" }
                        }
                        is OrchestratorEvent.Advice, is OrchestratorEvent.Answered -> spokenMs = ms()
                        else -> Unit
                    }
                    when (e) {
                        is OrchestratorEvent.Progress -> say("${at()}  ${e.stage}")
                        is OrchestratorEvent.IntentKnown -> say("${at()}  intent ${e.intent}  lead-in \"${e.leadIn}\"")
                        is OrchestratorEvent.OwnFigures -> { say("${at()}  OWN FIGURES on screen (${e.lines.size} lines)"); e.lines.forEach { say("            $it") } }
                        is OrchestratorEvent.MealResolved -> say("${at()}  resolved ${e.meal.items.map { it.spokenName + "->" + it.matchedFoodCode }}  hypothetical=${e.hypothetical}")
                        is OrchestratorEvent.MealLogged -> { mealId = e.mealId; say("${at()}  logged meal ${e.mealId}") }
                        is OrchestratorEvent.Advice -> { say("${at()}  ADVICE  phrased=${e.phrased?.replace('\n', ' ')}"); say("            trigger=${e.evaluation.trigger?.let(trigger::render)}  referral=${e.referral}  ranked=${e.evaluation.rankedCandidates.take(4).map { it.candidate.displayName }}  digest=${e.evaluation.inputDigest.take(12)}") }
                        is OrchestratorEvent.Answered -> say("${at()}  ANSWERED  text=${e.text?.replace('\n', ' ')}  refused=${e.refused}  referral=${e.referral}")
                        is OrchestratorEvent.LabReportSaved -> say("${at()}  lab report saved: ${e.count} values, regenerated meal ${e.regeneratedMealId}")
                        is OrchestratorEvent.NeedsIntent -> say("${at()}  NEEDS INTENT")
                        is OrchestratorEvent.NeedsConfirmation -> say("${at()}  NEEDS CONFIRMATION ${e.why}  parsed=${e.parsed.items.map { "${it.spokenName}=${it.quantity ?: "null"} ${it.unit ?: ""}".trim() }}")
                        is OrchestratorEvent.Failed -> say("${at()}  FAILED ${e.reason} ${e.detail}")
                        is OrchestratorEvent.NotImplemented -> say("${at()}  not implemented ${e.component}")
                        OrchestratorEvent.Completed -> say("${at()}  completed")
                        else -> say("${at()}  $e")
                    }
                }
            }
            runtime.lastTimings()?.let { t ->
                say("            last model call: prompt ${t.promptTokens} tok / ${"%.0f".format(t.promptMillis)} ms = ${"%.1f".format(t.promptTokensPerSecond)} tok/s   gen ${t.evalTokens} tok / ${"%.0f".format(t.evalMillis)} ms = ${"%.2f".format(t.evalTokensPerSecond)} tok/s")
            }
        }
    }

    @Test
    fun a_fourTurnsEndToEnd() {
        report.delete()
        say("=== end to end on ${android.os.Build.MODEL}, ${java.time.LocalDateTime.now()}, $THREADS threads ===")
        val rig = Rig()
        val en = SpeechLanguageRef("en-IN")
        with(rig) {
        turn("LOG: I had two rotis and a katori of dal", UserIntent.Type("I had two rotis and a katori of dal", en))
        turn("ANSWER: did I get enough iron this week", UserIntent.Type("did I get enough iron this week", en))
        turn("RECOMMEND: what should I eat for more iron", UserIntent.Type("what should I eat for more iron", en))
        mealId?.let { turn("ADVISE ON MEAL $it (precomputed at LOG; expected instant)", UserIntent.AdviseOnMeal(it)) }
        turn("SAVE LAB REPORT: fasting glucose 260 (regenerates the last meal's advice)", UserIntent.SaveLabReport(listOf(LabValue("Fasting glucose", 260.0, "mg/dL", 70.0, 100.0, LocalDate.of(2026, 9, 20)))))
        mealId?.let { turn("ADVISE ON MEAL $it again (after the report; expected instant, with the referral)", UserIntent.AdviseOnMeal(it)) }
        turn("SUGGEST: I'm having rice and palak paneer, what should I add", UserIntent.Type("I'm having rice and palak paneer, what should I add", en))
        }
        say(""); say("SHORT caps generation at ${ConversationPrompts.ANSWER_MAX_TOKENS} tokens for ANSWER; see 'last model call' lines for prompt tokens")
        rig.close()
        say("report written to ${report.absolutePath}")
    }

    /**
     * THE TEN DEMO SENTENCES (`data-authoring/demo-utterance-set.csv`, the English column), in
     * order, through the real orchestrator, one person, one diary. Per sentence: the intent the
     * words or the model decided, the foods and quantities as resolved, the figures, and THE TWO
     * NUMBERS THAT ARE THE PRODUCT: time to the first figure on screen, and time to the spoken
     * sentence. The file is staged beside the models; a missing file fails, it does not skip.
     */
    @Test
    fun b_tenDemoSentences() {
        say(""); say("=== the ten demo sentences, English, ${java.time.LocalDateTime.now()}, $THREADS threads ===")
        val file = File(modelsDir.parentFile, "demo-utterance-set.csv").takeIf { it.isFile }
            ?: File(ctx.externalMediaDirs.first(), "demo-utterance-set.csv")
        assertTrue("stage the demo set at ${file.absolutePath}", file.isFile)
        val rows = io.github.vedant7007.katori.data.knowledge.Csv.parse(file.readText()).drop(1).filter { it.getOrNull(3) == "en" }
        say("${rows.size} sentences")
        val rig = Rig(conditions = listOf("anaemia"))
        val en = SpeechLanguageRef("en-IN")
        val summary = mutableListOf<String>()
        for (r in rows) {
            val (id, beat, expectIntent, _, spoken) = r
            rig.turn("#$id beat $beat expect $expectIntent: $spoken", UserIntent.Type(spoken, en))
            val et = rig.extractTimings
            summary += "#%-2s %-9s -> %-9s  first figure %6s ms   spoken %6s ms   %s%s".format(
                id, expectIntent, rig.intent ?: "-", rig.firstFigureMs ?: "-", rig.spokenMs ?: "-",
                rig.resolved?.let { "[$it] " } ?: "", rig.figures.joinToString("; "),
            ) + (et?.let { "   extract prompt ${it.promptTokens} tok ${"%.0f".format(it.promptMillis)} ms, gen ${it.evalTokens} tok ${"%.0f".format(it.evalMillis)} ms" } ?: "")
        }
        say(""); say("=== summary: the two numbers that are the product ===")
        summary.forEach { say(it) }
        rig.close()
    }

    /** One ANSWER with languageTag "hi": the question in roman Hindi, the reply asked for in Hindi. */
    @Test
    fun c_oneHindiAnswer() {
        say(""); say("=== one ANSWER in Hindi (languageTag hi), ${java.time.LocalDateTime.now()} ===")
        val rig = Rig(conditions = listOf("anaemia"))
        val hi = SpeechLanguageRef("hi")
        with(rig) {
            turn("LOG (en) to give the diary a meal", UserIntent.Type("I had two rotis and a katori of dal", SpeechLanguageRef("en-IN")))
            turn("ANSWER (hi): aaj maine kitna protein khaya", UserIntent.Type("aaj maine kitna protein khaya", hi))
        }
        rig.close()
    }

    companion object {
        private const val TAG = "katori-e2e"
        private const val THREADS = 8
        private const val LLM_FILE = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        private val report: File by lazy {
            File(InstrumentationRegistry.getInstrumentation().targetContext.externalMediaDirs.first(), "katori-e2e-report.txt")
        }
        @JvmStatic @AfterClass fun done() = Unit
    }
}
