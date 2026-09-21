# What Arjun needs from Priya, in what shape, and what happens if it never arrives

Written 21 September 2026, 14:30, by Arjun, on Vedant's word: one place, so a first five
minutes of reading replaces an hour of rediscovery; and the handover note if nobody reads it.
The boundary of 07:33 holds: none of the four below is built by Arjun. Every item says what
the app does today without it, and the app is honest without every one of them.

## 1. Activity levels

**What.** The names of the activity levels the target rule reads, as a Kotlin enum, and which
one a hostel student who walks to class is.

**Shape.** `enum class ActivityLevel { … }` in `domain/Targets.kt` (yours; the file exists with
`Targets`, `TargetProgress`, `TargetStatus` and the empty `TargetRules`). The profile row stores
the enum NAME verbatim: `ProfileEntity.activity: String?` (Room v3, `ProfileStore.save(activity =
…)`), read back by `TargetsSource` and passed to your rule as the string it is. Ira's first-run
picker writes `.name`; the seed (`DemoSeedTest`) writes the level you name for the demo person
(19, 62 kg, 172 cm, male, hostel student).

**If it never arrives.** `activity` stays null on every row, the seed writes null, the picker
shows no activity question, `toSnapshot()` is unaffected (it never read activity), and every
rule that runs today runs exactly as it does today. Nothing on any screen shows an activity.
Honest, and the engine survives it.

## 2. `TargetRules` (the ring, the macro bars, "kcal left today", the water target)

**What.** The two pure functions, with the source they stand on in `Targets.rule`.

**Shape, already in the tree, agreed with Ira at 07:27 and 07:33, exact:**

```kotlin
object TargetRules {
    fun targetsFor(profile: ProfileSnapshot, activity: String?, profileVersionMs: Long, at: Instant): Targets?
    fun progress(targets: Targets, consumed: Map<Nutrient, Double>): List<TargetProgress>
}
data class Targets(energyKcal, proteinG, carbohydrateG, fatG, waterMl, rule: String, profileVersionMs: Long)
data class TargetProgress(nutrient, target, consumed, remaining, fraction, status: WITHIN | UNDER | OVER)
```

`targetsFor` returns null until the profile carries what the rule needs, never a guess;
`rule` names the source ("ICMR-NIN 2020 RDA, sedentary, maintain" or whatever you stand
behind); `status` is a threshold you name. `TargetsSource` (Arjun) calls `targetsFor` with the
row's snapshot, its `activity` string and its `updated_at_epoch_ms`, and `progress` with
today's COMPLETE totals only (a partial total is a floor and is never compared to a target).
Ira's ring, bars and water card are built against `TargetProgress` and render empty until
`targetsFor` is non-null. No screen does arithmetic on a target; the model never sees one.

**If it never arrives.** Both functions return null and empty, as they do now. The ring shows
today's energy with no fill and no denominator, each macro bar shows the consumed figure
alone ("18.5 g", never "18.5 / 0"), "kcal left today" and the water target do not appear, and
the nudge card never carries a target-based sentence. Vedant, 14:20: an empty ring is demoed
before an invented target.

## 3. The चटनी alias

**What.** A roman alias so a spoken चटनी resolves. Rao's transliteration writes it as
`chatni` (`Devanagari.kt`, ISO c → ch), and the matcher today holds chutney only as
`coconut chutney | kobbari pachadi | thengai chutney` and `peanut chutney | palli chutney |
palli pachadi` (`data-authoring/recipes.csv`, rows 59-60, column 3, `|`-separated). A bare
"chatni" / "chutney" hits nothing, so the plate refuses the item.

**Shape.** Your call of which recipe a bare chutney IS (coconut is the one beside idli and
dosa in the demo set), then `chutney|chatni` appended to that recipe's alias column in
`recipes.csv`, `python tools/build_food_db.py` to rebuild the bundled database, and the row
in `DevanagariTest` that lists the miss (`"चटनी -> 'chatni'"`) tightens itself: Rao wrote the
allowance exact, so when the alias lands that test asserts no misses. If a bare chutney is
NOT one recipe in your judgement, say "refuse" and the word stays unresolved by design.

**If it never arrives.** "chatni" resolves to nothing, the plate says the item is unknown and
saves nothing (0 of N: "I didn't catch that"), and the demo avoids the word: the frozen Beat 1
is रोटी and दाल, row 10 is इडली and सांबर. Eight of the nine transliterated words resolve
today; this is the ninth.

## 4. Meal slots (Ira's (l))

**What.** A ruling: is "breakfast / lunch / dinner" a rule by hour you stand behind, or "no
slots"?

**Shape.** One sentence here or in COORDINATION. By hour: the bands (e.g. before 11:00
breakfast, 11:00-16:00 lunch, after 18:00 dinner) go in `domain/` as a pure function of the
meal's local time; Ira shows the word. No slots: nothing changes. Either way NO COLUMN is
needed, so Arjun added none.

**If it never arrives.** The diary shows the meal's time, as it does now; Today's card ends at
"kcal left today" with no "dinner still to log". Honest.

## Also open with you, not blocking

- `assets/knowledge/markers.csv` (0038): seven cited MedlinePlus rows, no threshold, no
  condition word; strike or rewrite any, add lipids / creatinine / the CBC if you stand behind
  sources. If never: the seven ship as they are.
- The weekly summary (Ira's (u)): Arjun shipped it as TEMPLATES (`weekly_summary_*` keys)
  filled from `TrendsViewModel`. If a prompt never lands, the templates are the summary.
- The seed's catalogue names: a spoken दही reads back as "Yogurt, plain, whole milk" and चावल
  as "Rice, white, cooked (unenriched)" on the diary, because those are the USDA records'
  display names. Yours and Nila's if you want an Indian display name on a plain food.

## Where things are

`domain/Targets.kt` (the shapes, your empty object), `data/local/TargetsSource.kt`,
`data/local/ProfileStore.kt` (`activity`), `ui/TodayViewModel.kt` (reads `TargetsSource`),
`ml/asr/Devanagari.kt` and `DevanagariTest` (the nine words), `data-authoring/recipes.csv` and
`tools/build_food_db.py` (aliases), `docs/decisions/0037`-`0039`.
