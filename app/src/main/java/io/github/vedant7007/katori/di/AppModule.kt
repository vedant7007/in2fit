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
import io.github.vedant7007.katori.domain.DefaultRulesEngine
import io.github.vedant7007.katori.domain.RulesEngine
import javax.inject.Singleton

/**
 * Wiring only. No logic lives here.
 *
 * Note what is NOT provided: any ml/ engine. Those need the ModelArbiter and a real runtime, and
 * none of them is implemented. Binding a stub here would give the UI something that compiles and
 * returns nothing useful, which is how a not-implemented state quietly turns into a fake one.
 * When an engine is real, it gets a provider here and not before.
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
}
