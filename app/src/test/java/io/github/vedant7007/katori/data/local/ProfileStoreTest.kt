package io.github.vedant7007.katori.data.local

import io.github.vedant7007.katori.data.local.dao.ConditionDao
import io.github.vedant7007.katori.data.local.dao.ProfileDao
import io.github.vedant7007.katori.data.local.dao.WeightDao
import io.github.vedant7007.katori.data.local.entity.ConditionEntity
import io.github.vedant7007.katori.data.local.entity.ProfileEntity
import io.github.vedant7007.katori.data.local.entity.WeightEntity
import io.github.vedant7007.katori.domain.LifeContext
import io.github.vedant7007.katori.domain.Sex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The profile's one write path, over in-memory DAOs: a first save creates the row, a later save
 * keeps what it does not touch, a blank name is no name, the language defaults to Hindi and
 * only the row changes it, a weight goes to the history and to the profile in that order.
 */
class ProfileStoreTest {

    private class FakeProfileDao : ProfileDao {
        val row = MutableStateFlow<ProfileEntity?>(null)
        override fun observe(): Flow<ProfileEntity?> = row
        override suspend fun get(): ProfileEntity? = row.value
        override suspend fun upsert(profile: ProfileEntity) { row.value = profile }
    }

    private class FakeConditionDao : ConditionDao {
        val rows = MutableStateFlow<List<ConditionEntity>>(emptyList())
        override fun observeAll(): Flow<List<ConditionEntity>> = rows
        override suspend fun upsert(condition: ConditionEntity) { rows.value = rows.value.filter { it.name != condition.name } + condition.copy(id = rows.value.size + 1L) }
        override suspend fun delete(id: Long) { rows.value = rows.value.filter { it.id != id } }
    }

    private class FakeWeightDao : WeightDao {
        val rows = MutableStateFlow<List<WeightEntity>>(emptyList())
        override suspend fun insert(row: WeightEntity): Long { rows.value = rows.value + row.copy(id = rows.value.size + 1L); return rows.value.size.toLong() }
        override fun history(): Flow<List<WeightEntity>> = rows
        override suspend fun delete(id: Long) { rows.value = rows.value.filter { it.id != id } }
    }

    private val profiles = FakeProfileDao()
    private val conditions = FakeConditionDao()
    private val weights = FakeWeightDao()
    private val store = ProfileStore(profiles, conditions, weights, reminderDao = null)

    @Test
    fun `the first save creates the row and a later save keeps what it does not touch`() = runBlocking {
        assertNull(profiles.row.value)
        store.save(name = "  Vedant ", ageYears = 19, weightKg = 62.0, heightCm = 172.0, sex = "MALE", activity = null, goal = null, lifeContext = "HOSTEL_STUDENT", dietType = "VEGETARIAN")
        val first = profiles.row.value!!
        assertEquals("Vedant", first.name)
        assertEquals(19, first.age_years)
        assertEquals(1, first.id)
        assertTrue(first.updated_at_epoch_ms > 0)
        assertNull("no language was chosen; the row does not invent one", first.speech_language_tag)

        store.setSpeechLanguage("te")
        assertEquals("te", profiles.row.value!!.speech_language_tag)
        assertEquals("Vedant", profiles.row.value!!.name)

        // A blank name is no name: the greeting drops it.
        store.save(name = "   ", ageYears = 19, weightKg = 62.0, heightCm = 172.0, sex = "MALE", activity = "SEDENTARY", goal = "MAINTAIN", lifeContext = "HOSTEL_STUDENT", dietType = "VEGETARIAN")
        val second = profiles.row.value!!
        assertNull(second.name)
        assertEquals("SEDENTARY", second.activity)
        assertEquals("te", second.speech_language_tag)
    }

    @Test
    fun `the language reads Hindi until the row says otherwise`() = runBlocking {
        assertEquals("hi", store.speechLanguage.first())
        store.save(name = "V", ageYears = null, weightKg = null, heightCm = null, sex = null, activity = null, goal = null, lifeContext = null, dietType = null)
        assertEquals("a row without a choice is still Hindi", "hi", store.speechLanguage.first())
        store.setSpeechLanguage("en-IN")
        assertEquals("en-IN", store.speechLanguage.first())
    }

    @Test
    fun `a weight is written to the history and then to the profile`() = runBlocking {
        store.recordWeight(61.5)
        assertEquals(listOf(61.5), weights.rows.value.map { it.kg })
        assertEquals("USER_ENTERED", weights.rows.value.single().source)
        assertEquals(61.5, profiles.row.value!!.weight_kg)
        try { store.recordWeight(0.0); throw AssertionError("zero was accepted") } catch (e: IllegalArgumentException) { }
        assertEquals(1, weights.rows.value.size)
    }

    @Test
    fun `a declared condition carries its source and a blank one is refused`() = runBlocking {
        store.declareCondition("  anaemia ")
        store.declareCondition("")
        val declared = store.conditions.first().single()
        assertEquals("anaemia", declared.name)
        assertEquals("USER_DECLARED", declared.source)
        store.removeCondition(declared.id)
        assertTrue(store.conditions.first().isEmpty())
    }

    @Test
    fun `the snapshot the rules engine reads is the row's enums, and a null row is an empty profile`() {
        val row = ProfileStore.EMPTY.copy(sex = "MALE", life_context = "HOSTEL_STUDENT", diet_type = "NOT_AN_ENUM")
        val snap = row.toSnapshot(setOf("x"))
        assertEquals(Sex.MALE, snap.sex)
        assertEquals(LifeContext.HOSTEL_STUDENT, snap.context)
        assertNull("an unknown stored name reads as absent, not as a crash", snap.dietType)
        assertEquals(setOf("x"), snap.avoidedFoodCodes)
        assertNull((null as ProfileEntity?).toSnapshot().ageYears)
    }
}
