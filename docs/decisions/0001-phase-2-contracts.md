# 0001 - Phase 2 contracts

Status: reviewed, rulings applied, COMPILED GREEN on 19 Sep 2026. Frozen.

Interface-only: no implementation, no sample data, no default values.

## Files

    domain/model/Outcome.kt        Outcome<T>, UnavailableReason
    domain/model/Confidence.kt     three bands, reasons with ceilings, deterministic rule
    domain/model/Nutrients.kt      NutrientValue three-state, totals with completeness, DataSource
    domain/RulesEngine.kt          rules, constraints, evidence, trigger statement, input digest
    domain/ModelArbiter.kt         residency, leases, LRU eviction, co-residency measurement
    domain/Orchestrator.kt         intents, event stream, atomic model acquisition
    ml/asr/AsrEngine.kt            VAD endpointed, whole utterance, language as a parameter
    ml/tts/TtsEngine.kt
    ml/llm/LlmEngine.kt            extraction and phrasing paths, NumericGuard
    ml/vision/VisionEngines.kt     OCR, pose, dish classifier
    data/food/FoodLookup.kt        source-agnostic lookup, units, reference recipes
    data/local/entity/Entities.kt  the user database
    data/local/dao/Daos.kt         derived totals, never stored

## Product name and namespace

> SUPERSEDED, 19 Sep 2026, name only. The order is: **Swasth** (the spec's working name, historical)
> → **KATORI** (this record) → **IN2FIT** (current product name). The package, the source tree and
> the rest of this record deliberately stay `io.github.vedant7007.katori`: the rename below was
> cheap only while no implementations existed, and implementations now exist, including JNI symbol
> names that encode the package. Nothing else in this record is superseded.

Product name is KATORI. Swasth was dropped: it collides with a healthcare coalition's
Super App, a Ministry of Health app, and a Haryana state government app.

The namespace is `<domain>.katori`, pending the domain Vedant is registering. NOT
`app.katori`. Display name is decoupled from the namespace and can change later without
touching it.

The source tree still reads `io.github.vedant7007.katori` at the time of writing. It is renamed in ONE
pass at Phase 1B when the domain is known, rather than twice. Renaming is a find and
replace plus a directory move, and it is cheap only while no implementations exist.

## Decisions

1. Single Gradle module with the package layout from spec 9.4.

2. `Outcome<T>` has three variants and no `Partial`. A pipeline produced a real value from
   real data, or it did not.

3. Confidence is computed, never chosen. Worst ceiling wins. No numeric confidence exists
   anywhere in the type, so a percentage cannot leak into the UI by accident.

4. `NutrientValue` distinguishes Measured, AssumedZero and Unknown. There is no
   `getOrZero` helper anywhere, on purpose.

5. Meal totals are derived in SQL with completeness counts, so a partial total is
   presented as a floor rather than as a total.

6. `java.time` is used directly. minSdk 26 has it natively, so no core library desugaring.

7. NETWORK ISOLATION, ruled. One JVM unit test carries two assertions:
   a. No core package references the network package, `java.net`, OkHttp or Retrofit.
   b. The merged `demo`-flavour manifest contains no INTERNET permission.
   The second assertion is the one that actually protects the stage demo. A Gradle module
   split is revisited only if a network feature is ever built.

## Schema decisions, accepted

Both were raised as deviations from spec 8.3 and accepted as correct. Recorded here as
decisions, not deviations.

1. `meal_item_nutrients` is a twelfth table. A three-state value cannot live in a nullable
   column without an encoding a future reader misreads, and deriving totals in SQL with a
   correct partial flag needs the values as rows. Adding a nutrient becomes a data change
   rather than a schema migration.

2. `unit_conversions` and `context_foods` hold USER OVERRIDES ONLY, with shipped defaults
   in the read-only bundled database. A data refresh must not destroy a correction the
   user made, and a correction must outlive a refresh.

## Contract problems found in review, both fixed

1. LEASE DURATION VERSUS LATENCY. The first draft said a lease covers one inference. That
   was wrong. Meal logging is ASR, then LLM extraction, then TTS, and three sequential
   leases leave two gaps in which eviction forces a reload that consumes the entire 3.5 s
   budget for beat 1.

   Fixed: a lease spans one user-facing ROUND TRIP, and every model that round trip needs
   is acquired atomically up front through `withModels`. A lease still may not span waiting
   for user input. `ModelArbiter.canCoReside` was added so co-residency of ASR, LLM and TTS
   is MEASURED on the weakest device as a stage-1 item. If they do not co-fit, that is a
   tier-degradation finding, handled by dropping spoken confirmation to on-screen
   confirmation on that tier, not by splitting the lease back up.

2. INPUT DIGEST INCLUDED THE CLOCK. If `evaluatedAt` contributed to `inputDigest`, every
   evaluation would differ trivially and beat 4's proof would be worthless.

   Fixed: the digest covers the profile snapshot, declared conditions, lab values, the meal
   snapshot and the candidate food codes, each in canonical order. `evaluatedAt` is
   explicitly excluded, and the exclusion is documented on both fields. A required test
   asserts that identical input evaluated at two different times produces an identical
   digest and an identical evaluation. A consequence is recorded too: a rule whose outcome
   depends on elapsed time must compare dates found in the data, not the wall clock.

## Open item still on the table

`DishGuess.score` and `RankedCandidate.score` are documented as internal-only and must
never reach the UI. A reviewer may prefer them removed from the public types entirely.

## Verification status

These files now compile. `:app:assembleDemoDebug` and `:app:testDemoDebugUnitTest` are
both green, producing a 34.06 MB APK containing arm64-v8a native libraries only.

What that does and does not mean. It means the contracts are syntactically valid Kotlin,
their types agree with each other, and they resolve against the real dependency versions.
It does not mean any of them behaves correctly, because not one of them has an
implementation yet. The only executable assertions in the project so far are the two in
NetworkIsolationTest and the merged-manifest check.

Namespace is now `io.github.vedant7007.katori`; the rename happened in one pass as planned.
