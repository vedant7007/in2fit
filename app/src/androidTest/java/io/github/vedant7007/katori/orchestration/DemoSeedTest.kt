package io.github.vedant7007.katori.orchestration

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vedant7007.katori.data.food.AndroidFoodDbSource
import io.github.vedant7007.katori.data.food.LookupMealResolver
import io.github.vedant7007.katori.data.food.SqliteFoodLookup
import io.github.vedant7007.katori.data.local.KatoriDatabase
import io.github.vedant7007.katori.data.local.ProfileStore
import io.github.vedant7007.katori.data.local.RoomLabStore
import io.github.vedant7007.katori.data.local.RoomMealStore
import io.github.vedant7007.katori.domain.LabValue
import io.github.vedant7007.katori.domain.ParsedItem
import io.github.vedant7007.katori.domain.ParsedMeal
import io.github.vedant7007.katori.domain.model.ConfidenceReason
import io.github.vedant7007.katori.domain.model.ConfidenceRules
import io.github.vedant7007.katori.domain.model.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * THE COLD PHONE'S FIRST SIXTY SECONDS, MADE TRUE BEFORE THE JUDGES SEE THEM. A fresh iQOO has
 * no profile, no diary and no report, and the one time an empty diary was seen it made the worst
 * screenshot of the project. This writes the demo's starting state THROUGH THE APP'S OWN PATHS,
 * into the app's own database file (`KatoriDatabase.NAME`, the same migrations): the profile
 * through `ProfileStore.save`, a week of meals through the real `LookupMealResolver` and
 * `RoomMealStore.save` (dated over the last six days; the store takes the time, the orchestrator
 * only ever passes now), and, only when asked (`-e report true`), the lab report through
 * `RoomLabStore` as the fallback for a camera that will not read on the day. Nothing is written
 * behind the stores' backs. Run by `tools/cold-phone.ps1`; the app must not be running.
 *
 * What it does NOT seed: today's meal (Beat 1 logs it live, and the checklist's warm-up row does
 * it before the table), and any stored advice (a seeded meal's "Advise again" computes live).
 */
class DemoSeedTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val report = File(ctx.externalMediaDirs.first(), "katori-seed-report.txt").also { it.parentFile?.mkdirs(); it.delete() }
    private fun say(s: String) { android.util.Log.i("katori-seed", s); report.appendText(s + "\n") }

    private fun item(name: String, quantity: Double?, unit: String?) = ParsedItem(
        spokenName = name, quantity = quantity, unit = unit, matchedFoodCode = null,
        confidence = ConfidenceRules.of(if (quantity == null) ConfidenceReason.QUANTITY_INFERRED else ConfidenceReason.QUANTITY_STATED),
    )

    /** The week, as the demo set says it: rotis and dal, idli and sambar, rice, dal and curd, milk and an egg. */
    private val week: List<Pair<String, List<ParsedItem>>> = listOf(
        "I had two rotis and a katori of dal" to listOf(item("roti", 2.0, null), item("dal", 1.0, "katori")),
        "three idlis and sambar" to listOf(item("idli", 3.0, null), item("sambar", 1.0, "katori")),
        "one plate of rice, dal and a bowl of curd" to listOf(item("rice", 1.0, "plate"), item("dal", 1.0, "katori"), item("curd", 1.0, "bowl")),
        "a glass of milk and one boiled egg" to listOf(item("milk", 1.0, "glass"), item("egg", 1.0, null)),
        "two rotis and palak" to listOf(item("roti", 2.0, null), item("palak", 1.0, "katori")),
        "rice and dal" to listOf(item("rice", 1.0, "plate"), item("dal", 1.0, "katori")),
    )

    @Test
    fun seed() = runBlocking {
        val db = Room.databaseBuilder(ctx, KatoriDatabase::class.java, KatoriDatabase.NAME).addMigrations(*KatoriDatabase.MIGRATIONS).build()
        val foods = AndroidFoodDbSource.open(ctx)
        try {
            say("=== DEMO SEED ${java.time.LocalDateTime.now()} into ${ctx.getDatabasePath(KatoriDatabase.NAME)} ===")
            // The profile: the demo person, and Hindi as the speech language (0031; the picker shows it).
            val profile = ProfileStore(db)
            profile.save(name = args.getString("name", "Vedant"), ageYears = 19, weightKg = 62.0, heightCm = 172.0, sex = "male", activity = "moderate", goal = null, lifeContext = "hostel", dietType = null)
            profile.setSpeechLanguage("hi")
            say("profile: written (19 / 62 / 172, speech hi)")

            // The week: six meals on six different days, lunch and dinner times, none today.
            val resolver = LookupMealResolver(SqliteFoodLookup(foods))
            val store = RoomMealStore(db, languageTag = { "en-IN" }, source = { "TYPED" })
            val zone = ZoneId.systemDefault()
            var written = 0
            week.forEachIndexed { i, (said, items) ->
                val meal = ParsedMeal(items, ConfidenceRules.combine(items.map { it.confidence }), said)
                when (val r = resolver.resolve(meal, "en-IN")) {
                    is Outcome.Ok -> {
                        val at = LocalDate.now(zone).minusDays((6 - i).toLong()).atTime(if (i % 2 == 0) LocalTime.of(13, 10) else LocalTime.of(20, 30)).atZone(zone).toInstant()
                        when (val id = store.save(r.value, at)) {
                            is Outcome.Ok -> { written++; say("meal ${id.value} at $at: ${r.value.items.map { it.snapshot.foodCode }} from \"$said\"") }
                            else -> say("meal NOT saved for \"$said\": $id")
                        }
                    }
                    else -> say("meal NOT resolved for \"$said\": $r")
                }
            }
            say("meals: $written of ${week.size}")
            assertTrue("fewer than 5 meals seeded", written >= 5)

            // The report, only when asked: the fallback for Beat 3, and it changes Beat 1's advice.
            if (args.getString("report", "false") == "true") {
                val n = RoomLabStore(db).save(
                    listOf(
                        LabValue("Haemoglobin", 9.8, "g/dL", 12.0, 15.0, LocalDate.now(zone).minusDays(9)),
                        LabValue("Ferritin", 8.0, "ng/mL", 15.0, 150.0, LocalDate.now(zone).minusDays(9)),
                    ),
                )
                say("report: $n values saved (haemoglobin 9.8 and ferritin 8, both below the range printed)")
            } else say("report: not seeded (pass -e report true for the Beat 3 fallback)")
            say("=== SEED END ===")
        } finally {
            db.close(); foods.close()
        }
        Unit
    }
}
