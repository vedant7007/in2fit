package io.github.vedant7007.katori.data.local

import io.github.vedant7007.katori.domain.TargetProgress
import io.github.vedant7007.katori.domain.TargetRules
import io.github.vedant7007.katori.domain.Targets
import io.github.vedant7007.katori.domain.model.Completeness
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

/**
 * THE DAY'S TARGETS, from the profile through Priya's rule ([TargetRules]) and nothing else
 * (Ira's 07:27 shape, the 07:33 boundary). Null while the rule has no answer, which the screens
 * show as an empty ring; never a placeholder. The model never sees a target.
 */
class TargetsSource(private val profileStore: ProfileStore, private val diary: Diary, private val clock: Clock) {

    @Inject constructor(profileStore: ProfileStore, diary: Diary) : this(profileStore, diary, Clock.systemDefaultZone())

    fun current(): Flow<Targets?> = profileStore.profile.map { p ->
        p?.let { TargetRules.targetsFor(it.toSnapshot(), it.activity, it.updated_at_epoch_ms, clock.instant()) }
    }

    /**
     * Today's consumption against the targets, engine-computed. Only COMPLETE totals are
     * compared: a partial total is a floor, and a floor against a target would read as a
     * shortfall the person may not have.
     */
    fun progressToday(): Flow<List<TargetProgress>> =
        combine(current(), diary.totals(diary.bounds(diary.today()))) { targets, figures ->
            targets?.let { t ->
                TargetRules.progress(t, figures.filter { it.total.completeness == Completeness.COMPLETE }.associate { it.total.nutrient to it.total.amount })
            }.orEmpty()
        }
}
