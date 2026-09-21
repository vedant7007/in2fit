package io.github.vedant7007.katori.ui

import io.github.vedant7007.katori.data.local.ProfileStore
import io.github.vedant7007.katori.data.local.dao.ProfileDao
import io.github.vedant7007.katori.data.local.entity.ProfileEntity
import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.MealItemSnapshot
import io.github.vedant7007.katori.domain.Orchestrator
import io.github.vedant7007.katori.domain.OrchestratorEvent
import io.github.vedant7007.katori.domain.ParsedItem
import io.github.vedant7007.katori.domain.ParsedMeal
import io.github.vedant7007.katori.domain.ResolvedItem
import io.github.vedant7007.katori.domain.Stage
import io.github.vedant7007.katori.domain.TriggerText
import io.github.vedant7007.katori.domain.UserIntent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import io.github.vedant7007.katori.domain.model.ConfidenceBand
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.Nutrient
import io.github.vedant7007.katori.domain.model.NutrientProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The view-model's half of push-to-talk and the language default, against a recording
 * orchestrator. No Android: the test constructor takes no context and no preferences.
 */
class TalkViewModelTest {

    /** Records every intent; replays a scripted event list per intent; counts down when asked. */
    private class Recording(private val script: (UserIntent) -> List<OrchestratorEvent>) : Orchestrator {
        val intents = CopyOnWriteArrayList<UserIntent>()
        val seen = CountDownLatch(1)
        override fun handle(intent: UserIntent): Flow<OrchestratorEvent> = flow {
            intents += intent
            script(intent).forEach { emit(it) }
            seen.countDown()
        }
    }

    /** The profile row in memory: what the store reads and what it wrote. */
    private class FakeProfileDao(initial: ProfileEntity? = null) : ProfileDao {
        val row = MutableStateFlow(initial)
        override fun observe(): Flow<ProfileEntity?> = row
        override suspend fun get(): ProfileEntity? = row.value
        override suspend fun upsert(profile: ProfileEntity) { row.value = profile }
    }

    private fun vm(orchestrator: Orchestrator, dao: ProfileDao = FakeProfileDao()) = TalkViewModel(
        orchestrator, TriggerText(TriggerText.ENGLISH), ContextText(ContextText.ENGLISH, TriggerText.ENGLISH),
        scripted = null, profileStore = ProfileStore(dao, conditionDao = null),
    )

    @Test
    fun `the language defaults to Hindi`() {
        assertEquals("hi", vm(Recording { emptyList() }).state.value.language)
        assertEquals("hi", TalkViewModel.DEFAULT_LANGUAGE)
        // A profile row that has not chosen a language is Hindi too, not null and not English.
        val vm = vm(Recording { emptyList() }, FakeProfileDao(ProfileStore.EMPTY.copy(name = "Vedant")))
        Thread.sleep(200)
        assertEquals("hi", vm.state.value.language)
    }

    @Test
    fun `the chosen language lives on the profile row and comes back from it`() {
        val dao = FakeProfileDao()
        vm(Recording { emptyList() }, dao).setLanguage("te")
        waitUntil { dao.row.value?.speech_language_tag == "te" }
        // A new process: the view-model reads the row, not a default.
        val again = vm(Recording { emptyList() }, dao)
        waitUntil { again.state.value.language == "te" }
    }

    @Test
    fun `release sends EndSpeech and nothing else`() {
        val o = Recording { listOf(OrchestratorEvent.Completed) }
        vm(o).endSpeech()
        assertTrue("EndSpeech never reached the orchestrator", o.seen.await(5, TimeUnit.SECONDS))
        assertEquals(listOf<UserIntent>(UserIntent.EndSpeech), o.intents.toList())
    }

    @Test
    fun `the cue lights on MicrophoneLive, not on the press or on RECORDING`() {
        val afterRecording = CountDownLatch(1)
        val afterLive = CountDownLatch(1)
        val vm = vm(object : Orchestrator {
            override fun handle(intent: UserIntent): Flow<OrchestratorEvent> = flow {
                emit(OrchestratorEvent.Progress(Stage.RECORDING))
                afterRecording.await(5, TimeUnit.SECONDS)
                emit(OrchestratorEvent.MicrophoneLive)
                afterLive.await(5, TimeUnit.SECONDS)
                emit(OrchestratorEvent.Progress(Stage.TRANSCRIBING))
                emit(OrchestratorEvent.Completed)
            }
        })
        vm.speak()
        waitUntil { vm.state.value.stage == Stage.RECORDING }
        assertFalse("the cue lit on the press: RECORDING is the turn beginning, not the microphone", vm.state.value.micLive)
        afterRecording.countDown()
        waitUntil { vm.state.value.micLive }
        afterLive.countDown()
        waitUntil { !vm.state.value.busy }
        assertFalse("the cue must clear once the turn moves past recording", vm.state.value.micLive)
    }

    /** The per-item rows are copied from `MealResolved.items`, index for index; an absent number stays null. */
    @Test
    fun `the plate's rows carry the name shown, the amount said, the grams and whether the amount was ours`() {
        val stated = ConfidenceRules.of(ConfidenceReason.EXACT_FOOD_MATCH, ConfidenceReason.QUANTITY_STATED)
        val inferred = ConfidenceRules.of(ConfidenceReason.EXACT_FOOD_MATCH, ConfidenceReason.QUANTITY_INFERRED, ConfidenceReason.HOUSEHOLD_UNIT_DEFAULT)
        val noData = ConfidenceRules.of(ConfidenceReason.EXACT_FOOD_MATCH, ConfidenceReason.QUANTITY_STATED)
        val meal = ParsedMeal(
            items = listOf(
                ParsedItem("roti", 2.0, "piece", "chapati", stated),
                ParsedItem("dal", 1.0, "katori", "toor_dal_tadka", inferred),
                ParsedItem("kadha", 1.0, "cup", null, noData),
            ),
            confidence = inferred, rawTranscript = "two rotis, a little dal, and a cup of kadha",
        )
        val items = listOf(
            ResolvedItem(MealItemSnapshot("Chapati / roti", "chapati", 90.0, mapOf(Nutrient.PROTEIN to 7.7)), null, NutrientProfile(emptyMap()), stated),
            ResolvedItem(MealItemSnapshot("Dal tadka", "toor_dal_tadka", 180.0, mapOf(Nutrient.PROTEIN to 10.8)), null, NutrientProfile(emptyMap()), inferred),
            ResolvedItem(MealItemSnapshot("kadha", null, null, emptyMap()), null, NutrientProfile(emptyMap()), noData),
        )
        val vm = vm(Recording { listOf(OrchestratorEvent.MealResolved(meal, emptyList(), hypothetical = false, items = items), OrchestratorEvent.Completed) })
        vm.type(meal.rawTranscript)
        waitUntil { !vm.state.value.busy }

        val rows = vm.state.value.entries.filterIsInstance<TalkViewModel.Entry.Plate>().single().rows
        assertEquals(3, rows.size)
        assertEquals(TalkViewModel.Entry.PlateItem("Chapati / roti", 2.0, "piece", 90.0, mapOf(Nutrient.PROTEIN to 7.7), false, ConfidenceBand.GOOD, stated.reasons), rows[0])
        assertEquals("Dal tadka", rows[1].name)
        assertEquals(1.0, rows[1].quantity)
        assertEquals("katori", rows[1].unit)
        assertEquals(180.0, rows[1].grams)
        assertTrue(rows[1].inferred)
        assertEquals(ConfidenceBand.ROUGH, rows[1].band)
        assertNull("a no-data item has no grams, not 0", rows[2].grams)
        assertTrue(rows[2].nutrients.isEmpty())
        assertFalse(rows[2].inferred)
    }

    /** Until the orchestrator fills `items`, the entry has no rows and the card falls back to the parsed items. */
    @Test
    fun `an event without items gives a plate without rows`() {
        val meal = ParsedMeal(listOf(ParsedItem("roti", 2.0, "piece", "chapati", ConfidenceRules.of(ConfidenceReason.QUANTITY_STATED))), ConfidenceRules.of(ConfidenceReason.QUANTITY_STATED), "two rotis")
        val vm = vm(Recording { listOf(OrchestratorEvent.MealResolved(meal, emptyList(), hypothetical = false), OrchestratorEvent.Completed) })
        vm.type("two rotis")
        waitUntil { !vm.state.value.busy }
        val plate = vm.state.value.entries.filterIsInstance<TalkViewModel.Entry.Plate>().single()
        assertTrue(plate.rows.isEmpty())
        assertEquals(1, plate.items.size)
    }

    private fun waitUntil(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < end) Thread.sleep(10)
        assertTrue("condition not met in time", condition())
    }
}
