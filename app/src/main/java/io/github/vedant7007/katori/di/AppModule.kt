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
        val modelsDir = java.io.File(
            context.externalMediaDirs.firstOrNull() ?: context.filesDir, "models",
        )
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        return DefaultModelArbiter(
            memory = AndroidDeviceMemory(context),
            loader = LlamaCppModelLoader(modelsDir),
            log = FileMeasurementLog(context),
            buildTag = "${pkg.versionName}@${pkg.lastUpdateTime}",
        )
    }
}
