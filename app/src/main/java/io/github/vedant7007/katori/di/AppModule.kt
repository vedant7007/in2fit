package io.github.vedant7007.katori.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.vedant7007.katori.data.food.AndroidFoodDbSource
import io.github.vedant7007.katori.data.food.FoodDbSource
import io.github.vedant7007.katori.data.food.FoodLookup
import io.github.vedant7007.katori.data.food.FoodQuery
import io.github.vedant7007.katori.data.food.SqliteFoodLookup
import io.github.vedant7007.katori.data.local.KatoriDatabase
import io.github.vedant7007.katori.data.local.dao.UnmatchedUtteranceDao
import io.github.vedant7007.katori.data.local.entity.UnmatchedUtteranceEntity
import io.github.vedant7007.katori.data.local.AndroidDeviceMemory
import io.github.vedant7007.katori.data.local.FileMeasurementLog
import io.github.vedant7007.katori.domain.DefaultModelArbiter
import io.github.vedant7007.katori.domain.DefaultRulesEngine
import io.github.vedant7007.katori.domain.ModelArbiter
import io.github.vedant7007.katori.ml.llm.LlamaCppModelLoader
import io.github.vedant7007.katori.domain.RulesEngine
import io.github.vedant7007.katori.data.food.LookupMealResolver
import io.github.vedant7007.katori.data.food.SqliteSpokenNames
import io.github.vedant7007.katori.data.knowledge.KnowledgeFacts
import io.github.vedant7007.katori.data.local.AndroidContextStrings
import io.github.vedant7007.katori.data.local.AndroidTriggerStrings
import io.github.vedant7007.katori.data.local.RoomAdviceStore
import io.github.vedant7007.katori.data.local.RoomLabStore
import io.github.vedant7007.katori.data.local.RoomMealStore
import io.github.vedant7007.katori.data.local.RoomUserContextSource
import io.github.vedant7007.katori.domain.AdviceStore
import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.LabStore
import io.github.vedant7007.katori.domain.FamilyModelLoader
import io.github.vedant7007.katori.domain.MealResolver
import io.github.vedant7007.katori.domain.MealStore
import io.github.vedant7007.katori.domain.ModelFamily
import io.github.vedant7007.katori.domain.Orchestrator
import io.github.vedant7007.katori.domain.SpokenNames
import io.github.vedant7007.katori.domain.TriggerText
import io.github.vedant7007.katori.domain.UserContextSource
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.ml.asr.AndroidAudioSource
import io.github.vedant7007.katori.ml.asr.AsrEngine
import io.github.vedant7007.katori.ml.asr.DefaultAsrEngine
import io.github.vedant7007.katori.ml.asr.IcuRomaniser
import io.github.vedant7007.katori.ml.asr.PushToTalk
import io.github.vedant7007.katori.ml.asr.SherpaOnnxAsrLoader
import io.github.vedant7007.katori.ml.llm.LlamaCppLlmEngine
import io.github.vedant7007.katori.ml.llm.LlamaRuntime
import io.github.vedant7007.katori.ml.llm.LlmEngine
import io.github.vedant7007.katori.ml.llm.LlmModels
import io.github.vedant7007.katori.ml.tts.AndroidTtsEngine
import io.github.vedant7007.katori.ml.tts.AudioTrackSink
import io.github.vedant7007.katori.ml.tts.EspeakData
import io.github.vedant7007.katori.ml.tts.PiperTtsEngine
import io.github.vedant7007.katori.ml.tts.PiperVoiceLoader
import io.github.vedant7007.katori.ml.tts.RoutingTtsEngine
import io.github.vedant7007.katori.ml.tts.TtsEngine
import io.github.vedant7007.katori.ml.vision.FrameStore
import io.github.vedant7007.katori.ml.vision.MlKitOcrEngine
import io.github.vedant7007.katori.ml.vision.OcrEngine
import io.github.vedant7007.katori.orchestration.DefaultOrchestrator
import io.github.vedant7007.katori.orchestration.CurrentTurn
import io.github.vedant7007.katori.orchestration.LlmLease
import javax.inject.Singleton

/**
 * Wiring only. No logic lives here.
 *
 * Note what is NOT provided: any ml/ engine. Those need a real runtime, and only the LLM's
 * exists. Binding a stub here would give the UI something that compiles and returns nothing
 * useful, which is how a not-implemented state quietly turns into a fake one. When an engine is
 * real, it gets a provider here and not before.
 *
 * The ModelArbiter IS provided, because it is implemented and tested, and its loader binds the
 * one runtime that exists. Asking it for an ASR or TTS model fails with MODEL_LOAD_FAILED and a
 * message naming the gap; it does not hand back a placeholder.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideUserDatabase(@ApplicationContext context: Context): KatoriDatabase =
        Room.databaseBuilder(context, KatoriDatabase::class.java, KatoriDatabase.NAME)
            // No fallbackToDestructiveMigration. This database holds health history the user
            // typed in and cannot recover. A missing migration must fail loudly in development,
            // not delete someone's meals on upgrade.
            .addMigrations(*KatoriDatabase.MIGRATIONS)
            .build()

    @Provides
    @Singleton
    fun provideUnmatchedUtteranceDao(db: KatoriDatabase): UnmatchedUtteranceDao =
        db.unmatchedUtteranceDao()

    @Provides
    @Singleton
    fun provideFoodDbSource(@ApplicationContext context: Context): FoodDbSource =
        AndroidFoodDbSource.open(context)

    @Provides
    @Singleton
    fun provideFoodLookup(
        source: FoodDbSource,
        unmatchedDao: UnmatchedUtteranceDao,
    ): FoodLookup = SqliteFoodLookup(
        db = source,
        unmatched = object : SqliteFoodLookup.UnmatchedSink {
            /** Every miss is recorded, which is how coverage gaps close against evidence. */
            override suspend fun record(query: FoodQuery) {
                unmatchedDao.insert(
                    UnmatchedUtteranceEntity(
                        spoken_name = query.spokenName,
                        full_transcript = null,
                        language_tag = query.languageTag,
                        recorded_at_epoch_ms = System.currentTimeMillis(),
                    )
                )
            }
        },
    )

    @Provides
    @Singleton
    fun provideRulesEngine(): RulesEngine = DefaultRulesEngine()

    /**
     * Models are staged in the app's external media directory, which is where the hardware probe
     * puts them and where `adb push` can reach without root. The `full` flavour's downloader will
     * write to the same place. A build tag that changes on every install goes into every
     * measurement row, so two rows from different builds are never mistaken for noise.
     */
    @Provides
    @Singleton
    fun provideModelArbiter(@ApplicationContext context: Context): ModelArbiter {
        val modelsDir = modelsDir(context)
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        return DefaultModelArbiter(
            memory = AndroidDeviceMemory(context),
            loader = FamilyModelLoader(
                mapOf(
                    ModelFamily.LLM to LlamaCppModelLoader(modelsDir),
                    ModelFamily.ASR to SherpaOnnxAsrLoader(modelsDir),
                    ModelFamily.TTS to PiperVoiceLoader(
                        modelsDir,
                        espeakDataDir = EspeakData.install(context, java.io.File(context.filesDir, "espeak-ng-data")),
                    ),
                )
            ),
            log = FileMeasurementLog(context),
            buildTag = "${pkg.versionName}@${pkg.lastUpdateTime}",
        )
    }

    private fun modelsDir(context: Context) = java.io.File(
        context.externalMediaDirs.firstOrNull() ?: context.filesDir, "models",
    )

    // --- the engines ---------------------------------------------------------------------------

    @Provides
    @Singleton
    fun provideAsrEngine(@ApplicationContext context: Context, arbiter: ModelArbiter): AsrEngine =
        DefaultAsrEngine(arbiter, AndroidAudioSource(context))

    /**
     * The platform engine first, Piper as the fallback for a language the phone has no offline
     * voice for; `RoutingTtsEngine` tries the next engine only on MODEL_NOT_LOADED, so a language
     * nobody can speak here is reported, never read in another language's voice.
     */
    @Provides
    @Singleton
    fun provideTtsEngine(@ApplicationContext context: Context, arbiter: ModelArbiter): TtsEngine =
        RoutingTtsEngine(listOf(AndroidTtsEngine(context), PiperTtsEngine(arbiter, AudioTrackSink(context))))

    @Provides
    @Singleton
    fun provideFrameStore(): FrameStore = FrameStore()

    @Provides
    @Singleton
    fun provideOcrEngine(frames: FrameStore): OcrEngine = MlKitOcrEngine(frames)

    // --- the orchestrator and what it reads and writes through ---------------------------------

    @Provides
    @Singleton
    fun provideKnowledgeFacts(@ApplicationContext context: Context): KnowledgeFacts =
        KnowledgeFacts.load { context.assets.open(KnowledgeFacts.ASSET_PATH) }

    @Provides
    @Singleton
    fun provideTriggerText(@ApplicationContext context: Context): TriggerText =
        TriggerText(AndroidTriggerStrings(context))

    @Provides
    @Singleton
    fun provideContextText(@ApplicationContext context: Context): ContextText =
        ContextText(AndroidContextStrings(context), AndroidTriggerStrings(context))

    @Provides
    @Singleton
    fun provideUserContextSource(db: KatoriDatabase, foods: FoodDbSource): UserContextSource =
        RoomUserContextSource(db, foods)

    @Provides
    @Singleton
    fun provideMealResolver(lookup: FoodLookup): MealResolver = LookupMealResolver(lookup)

    @Provides
    @Singleton
    fun provideMealStore(db: KatoriDatabase): MealStore =
        RoomMealStore(db, languageTag = { CurrentTurn.languageTag }, source = { CurrentTurn.source })

    @Provides
    @Singleton
    fun provideSpokenNames(foods: FoodDbSource): SpokenNames = SqliteSpokenNames(foods)

    @Provides
    @Singleton
    fun provideAdviceStore(db: KatoriDatabase): AdviceStore = RoomAdviceStore(db)

    @Provides
    @Singleton
    fun provideLabStore(db: KatoriDatabase): LabStore = RoomLabStore(db)

    /** The LLM is leased from the arbiter per call; the engine is built over the admitted runtime. */
    @Provides
    @Singleton
    fun provideLlmLease(arbiter: ModelArbiter): LlmLease = object : LlmLease {
        override suspend fun <T> use(block: suspend (LlmEngine) -> T): Outcome<T> =
            arbiter.withModel(LlmModels.QWEN_2_5_1_5B_Q4_K_M) { loaded ->
                block(LlamaCppLlmEngine(loaded.native as LlamaRuntime))
            }
    }

    @Provides
    @Singleton
    fun provideOrchestrator(
        @ApplicationContext context: Context,
        asr: AsrEngine, llm: LlmLease, tts: TtsEngine, rules: RulesEngine, resolver: MealResolver, store: MealStore,
        advice: AdviceStore, labs: LabStore,
        contextSource: UserContextSource, knowledge: KnowledgeFacts, triggerText: TriggerText, contextText: ContextText, spokenNames: SpokenNames,
    ): Orchestrator = DefaultOrchestrator(
        asr, llm, tts, rules, resolver, store, advice, labs, contextSource, knowledge, triggerText, contextText, spokenNames,
        pushToTalk = PushToTalk(asr, AndroidAudioSource(context)),
        romaniser = IcuRomaniser(),
    )
}
