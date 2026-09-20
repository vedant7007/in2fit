# Katori — handover report

Written on 19 September 2026 by an agent taking over from the one that built this, after reading
`STATUS.md`, all ten decision records, `docs/review-packets/phase-1b.md`,
`data-authoring/README.md`, the full git log with per-commit stats, every source file, the build
files, the authoring CSVs and the logs on disk.

**Read this before `STATUS.md`.** `STATUS.md` is largely accurate but it is out of date on the one
question it cares most about: whether anything has run on a phone. Something has. See §4.

> **Addendum, 20 September 2026.** This report is a snapshot and is not rewritten. What has
> changed since it was written, with the file that proves it:
> - §7 bug 1 (no copy step in `build-llama-android.ps1`) was fixed in `d23cc53`; the script now
>   stages the four libraries and fails if one is missing (`logs/llama-android-build.log:96-101`).
> - §7 bugs 2 and 3 (stale food-database copy; millilitres at density 1.0 reaching GOOD) were
>   fixed in `6aea21e`, with tests.
> - §4C item 1: the model was pushed and the whole llama.cpp path has now run on the phone.
>   `COORDINATION.md` and `STATUS.md` carry the measured figures; `0011`–`0014` the findings.
> - §3: `ModelArbiter` is implemented (`0c20de3`, `DefaultModelArbiter.kt`, 19 tests).
> - §6 rule 7 (commit authorship): resolved by Vedant. Commits are authored solely by him, with
>   no `Co-Authored-By` trailer and no attribution line. Every commit since follows this.
> - §7 "Documentation that disagrees with the code": every row in that table is reconciled as of
>   this date; see the commit that added this addendum.
> - The product is now IN2FIT (`0015`). Display strings only; the package stays `katori`.
> - `onnxruntime-android` is now `androidTestImplementation` (`0016`).
> - §3 and §6 rule 2 count thirteen no-data items and 81 ingredients; on 20 September the
>   corpus is 93 ingredients, 626 aliases, 52 recipes and FIFTEEN no-data items, paneer and
>   fried rice added (`0026-code-mixed-food-words`, `logs/food-db-build.log`). The rule is
>   unchanged: none of the fifteen may resolve to a food.
> - The command order in §2 is stale for a fresh clone: `tools/4-fetch-models.bat` must run
>   BEFORE `tools/3-build.bat`, because the build now refuses to start without the sherpa-onnx
>   AAR that step 4 fetches (with its sha256 checked). The numbers were not changed; the
>   dependency is stated here and in `app/build.gradle.kts`'s preBuild message.
> - Six sessions now work in one worktree each; see `COORDINATION.md` 03:19 and
>   `tools/new-worktree.ps1`. The "logs on one laptop" risk in §7 is unchanged.
>
> **Nothing Nila owns depends on her session being awake.** Standing note from Vedant, 20
> September, after the packet freeze was set for 22:00: every artefact in her scope is
> produced by a command anyone can run from a worktree, with no state in the session.
>
> | thing | the command, from a worktree | then |
> | --- | --- | --- |
> | The Telugu reviewer packet (regenerate, freeze) | `python tools/make_review_queue.py te` | commit `docs/localisation/telugu-review-queue.md`, `tools\land.ps1` |
> | A reviewer's reply into the app | `python tools/import_review_queue.py te --reply <file> --reviewer "<name>"`, or `--sheet <file>`; add `--unreviewed --origin machine` for model output | send back `docs/localisation/telugu-review-check.md`; regenerate the packet; commit; land |
> | The APK size ledger | `powershell -File tools\apk-size.ps1` after any assemble (build.ps1 does it) | quote only rows of `logs/apk-size.log` in the main tree |
> | Models, the sherpa AAR, espeak data, with hashes checked | `tools\4-fetch-models.bat` (before `3-build.bat` on a fresh clone) | MISMATCH deletes the file and exits 1; run again |
> | The food database and its 17 assertions | `python tools/build_food_db.py` | `logs/food-db-build.log`; commit the `.db` with the CSVs that made it |
> | A session's worktree; landing on master | `powershell -File tools\new-worktree.ps1 -Name <name>`; `powershell -File tools\land.ps1` | never build in the main tree |
> | The string rules for anyone writing a screen | `docs/localisation/string-conventions.md` | `StringResourcesTest` enforces the ones a test can |
> | Licence texts and duties | `docs/licences/`, `docs/decisions/0005` | the IITM PDF diff is recorded there before anyone rules from the text |
> | The demo | `docs/demo/run-of-show.md`, `docs/demo/deck-audit-2026-09-20.md` | budgets are replaced by rows from the clean run; the deck is re-read against the repo before every resubmission |

There is no README at the repo root and **no copy of the spec anywhere in the repository**, yet 30
distinct spec sections (`spec 2.3` through `spec 18.3`) are cited as the authority across the code
and the decision records. Everything those citations justify has had to be reconstructed from the
decision records. **Get the spec into this repo, or into a known location, before the next
architectural decision is made.**

---

## 1. WHAT THE PROJECT IS

Katori is a fully offline Android health assistant for the iQOO Hackathon 2026, built for
code-mixed Telugu/Hindi/English speech in India. A person says what they ate, in their own words
and their own script; on-device ASR transcribes it, an on-device LLM extracts *only* the food names
and quantities as strict JSON, a bundled read-only USDA food database resolves those names to real
nutrition values, and a deterministic rules engine — no model involved — decides what, if anything,
to say about it. The camera reads printed lab reports with on-device OCR so a lab value can change
the advice, and that change is provable because it comes from a templated trigger sentence produced
by the rules engine rather than from generated prose. The design constraint that shapes every file
is that **the model never computes, estimates or invents a number**: it extracts and it phrases,
and three separate defences (a strict schema, a numeric guard, and a three-state nutrient type with
no `getOrZero`) exist to keep it that way. The demo build (`demo` flavour) carries no INTERNET
permission at all, which is the proof the pitch leads with.
(Sources: `docs/decisions/0001`, `0002`, `0008`, `0010`, `docs/review-packets/phase-1b.md`.)

---

## 2. BUILD AND RUN

### Toolchain, all pinned and all verified on disk

| Thing | Version | Pinned where |
| --- | --- | --- |
| AGP | 9.4.1 | `gradle/libs.versions.toml` |
| Gradle | 9.6.0 | `gradle/wrapper/gradle-wrapper.properties` |
| Kotlin (KGP) | 2.2.20 | `libs.versions.toml` |
| KSP | 2.2.20-2.0.4 | `libs.versions.toml` |
| Hilt | 2.60.1 | `libs.versions.toml` |
| Room | 2.8.5 | `libs.versions.toml` |
| ONNX Runtime | 1.30.0 | `libs.versions.toml` (uncommitted) |
| compileSdk | 37 | `app/build.gradle.kts` |
| targetSdk | 36 | `app/build.gradle.kts` |
| minSdk | 26 | `app/build.gradle.kts` |
| NDK | 28.2.13676358 (r28c) | `ndkVersion` in `app/build.gradle.kts` |
| CMake | 3.22.1 | `externalNativeBuild` in `app/build.gradle.kts` |
| JDK | Temurin 21.0.11 | set as `JAVA_HOME` by every script in `tools/` |
| ABI | `arm64-v8a` only | `ndk { abiFilters }` |

**SDK root in use: `C:\Users\vedan\AppData\Local\Android\Sdk`.** Set in `local.properties`, which is
gitignored and machine-specific. A second SDK root exists at `C:\Users\vedan\Android\Sdk`; it lacks
`android-37.0` which AGP 9 requires. Decision `0003` chose the first and warns that installing
packages without an explicit `--sdk_root` lands them where the build cannot see them.

### Commands

Everything is driven by numbered `.bat` files in `tools/`, launched by double-click from Explorer,
each redirecting output into `logs/`. That is deliberate (`0003`): the session that built this could
not type into a Windows terminal, so every claim had to come from a file the build itself wrote.
Gradle writes UTF-16 on Windows, so each script also writes a `utf8-` copy of each log. **Read the
`utf8-` copies.**

    tools/1-setup-toolchain.bat      installs NDK + CMake into the chosen SDK root
    tools/2-bootstrap-gradle.bat     generates the Gradle wrapper
    tools/3-build.bat                probeVersions, :app:assembleDemoDebug, :app:testDemoDebugUnitTest
    tools/4-fetch-models.bat         downloads ASR/TTS/LLM weights into data-sources/models (~1.67 GB)
    tools/5-build-llama-android.bat  cross-compiles llama.cpp for arm64-v8a  ** SEE THE BUG IN §7 **
    tools/6-hardware-probe.bat       build, install, launch, push models, run HardwareProbeTest  (UNTRACKED)

Directly, with `JAVA_HOME` pointing at Temurin 21:

    gradlew.bat :app:assembleDemoDebug               # also runs verifyDemoDebugPermissions
    gradlew.bat :app:testDemoDebugUnitTest           # 103 JVM tests
    gradlew.bat :app:installDemoDebug                # needs a device over USB
    gradlew.bat :app:connectedDemoDebugAndroidTest   # runs HardwareProbeTest on the device
    gradlew.bat probeVersions                        # newest stable versions -> logs/versions-resolved.log
    python tools/build_food_db.py                    # rebuilds app/src/main/assets/food/katori-food.db

Tests and the demo APK also build in a cloud Linux container in ~20 s (`0009`). The container has no
NDK, so anything native goes back to the laptop.

### Settings that look wrong and are deliberate

| Setting | Why | Record |
| --- | --- | --- |
| `android.builtInKotlin=false` | KSP refuses to run alongside AGP 9's built-in Kotlin, and Room + Hilt both need KSP | `0007` |
| `android.newDsl=false` | with built-in Kotlin off, the standard Kotlin plugin then refuses AGP 9's new DSL. These are the only two exits and they are mutually exclusive | `0007` |
| no `org.jetbrains.kotlin.android`… except there is | `0004` removed it because AGP 9 refused it; `0007` put it back because KSP forced `builtInKotlin=false`. Both refusals are real, in different configurations | `0004`, `0007` |
| `org.gradle.configuration-cache=false` | `probeVersions` resolves configurations at execution time, which the configuration cache rejects | `gradle.properties` |
| `abiFilters = arm64-v8a` only | 32-bit devices cannot run the model; building armeabi-v7a doubles NDK time for hardware that would fail anyway | `app/build.gradle.kts` |
| no `fallbackToDestructiveMigration` anywhere | the user database holds health history that cannot be recovered | `0007` |
| llama.cpp is **not** built by Gradle | a CMake subproject would add minutes to every build and vendor a large uncommitted tree; CMake builds only the JNI shim | `0010` |
| `targetSdk = 36` under `compileSdk = 37` | not explained in any record. Probably fine, but it is unexplained — see §7 | — |

**Both `builtInKotlin` and `newDsl` flags print a deprecation warning saying they will be removed in
AGP 10.** That is visible in `logs/container-test6.log` lines 6–13. The expiry in `0007` is not
hypothetical.

---

## 3. WHAT EXISTS, FILE BY FILE

Status vocabulary, used strictly:
**CONTRACT** = interface/doc only, no implementation exists anywhere ·
**IMPLEMENTED** = real code with JVM tests that pass ·
**WRITTEN, NEVER RUN** = real code, compiles, has never executed even once.

### `domain/` — the deterministic core

| File | Lines | Status |
| --- | ---: | --- |
| `model/Outcome.kt` | 99 | IMPLEMENTED (types). `Outcome<T>` = Ok / Unavailable / NotImplemented. 12 `UnavailableReason` values. |
| `model/Confidence.kt` | 139 | IMPLEMENTED. Three bands, 13 reasons each with a ceiling, `ConfidenceRules` is pure worst-ceiling-wins. |
| `model/Nutrients.kt` | 148 | IMPLEMENTED (types). `NutrientValue` = Measured / AssumedZero / Unknown. `NutrientTotal` carries completeness. |
| `RulesEngine.kt` | 339 | CONTRACT. |
| `DefaultRulesEngine.kt` | 343 | IMPLEMENTED. 7 rules, SHA-256 input digest excluding the clock. 16 tests. |
| `RuleTemplates.kt` | 148 | IMPLEMENTED. Fixed catalogue of eight safety-reviewed sentences. |
| `RangeDirection.kt` | 4 | IMPLEMENTED. |
| `ModelArbiter.kt` | 214 | **CONTRACT ONLY. No implementation exists.** Leases, atomic `withModels`, LRU eviction, `canCoReside`. This is one of the two contracts the whole demo rests on and there is nothing behind it. |
| `Orchestrator.kt` | 114 | **CONTRACT ONLY. No implementation exists.** Nothing routes an intent to a pipeline. |

### `data/food/` — the bundled USDA layer

| File | Lines | Status |
| --- | ---: | --- |
| `FoodLookup.kt` | 153 | CONTRACT. |
| `SqliteFoodLookup.kt` | 373 | IMPLEMENTED. resolve / candidates / nutrientsFor / resolveUnit / recipe. 20 + 16 tests. |
| `FoodTextMatching.kt` | 168 | IMPLEMENTED. Normalise, word-bounded containment, length-scaled Levenshtein. 11 tests. |
| `FoodDbSource.kt` | 38 | CONTRACT (the test seam). |
| `AndroidFoodDbSource.kt` | 66 | **WRITTEN, NEVER RUN.** The on-device path: copies the asset to filesDir, opens read-only. No test covers it; every JVM test uses `JdbcFoodDbSource`. See the stale-copy bug in §7. |
| `DefaultRecipeCalculator.kt` | 79 | IMPLEMENTED. The user-edited-recipe path. Cross-checked against the shipped table by a test. |
| `JdbcFoodDbSource.kt` (test) | 43 | IMPLEMENTED. Reads the real shipped `.db` on the JVM. |

### `data/local/` — the user's Room database

| File | Lines | Status |
| --- | ---: | --- |
| `entity/Entities.kt` | 225 | IMPLEMENTED (12 entities). `meal_item_nutrients` is a deliberate twelfth table; `unit_conversions` and `context_foods` are USER OVERRIDES ONLY. |
| `dao/Daos.kt` | 215 | CONTRACT (9 DAO interfaces; Room generates the implementations). **Not one query has ever executed.** Totals are derived in SQL with completeness counts. |
| `KatoriDatabase.kt` | 91 | IMPLEMENTED. Enums stored by NAME. Schema exported to `app/schemas/…/1.json`, committed. |

### `ml/llm/` — the only ML package with real code

| File | Lines | Status |
| --- | ---: | --- |
| `LlmEngine.kt` | 121 | CONTRACT (the two-path interface + `NumericGuard`). |
| `LlamaCppLlmEngine.kt` | 125 | IMPLEMENTED. extract with a re-ask budget, phrase with the guard. 19 tests against a scripted runtime. |
| `Prompts.kt` | 110 | IMPLEMENTED. Exactly two prompts, Qwen 2.5 chat template. |
| `ExtractionJson.kt` | 236 | IMPLEMENTED. Hand-written strict parser that refuses rather than repairs. |
| `DefaultNumericGuard.kt` | 166 | IMPLEMENTED. Value-based, script-aware; `B12` does not licence a bare `12`. 18 tests. |
| `LlamaRuntime.kt` | 36 | CONTRACT (the native seam). |
| `LlamaCppRuntime.kt` | 107 | **WRITTEN, NEVER RUN.** 4 external functions. `lastTimings()` is uncommitted. |
| `cpp/katori_llama.cpp` | 302 | **WRITTEN, NEVER RUN.** Compiles, links, exports its symbols. `nativeLastTimings` is uncommitted. |
| `cpp/CMakeLists.txt` | 36 | IMPLEMENTED. Builds the shim only; fails at configure with the name of the script to run. |
| `cpp/include/*.h` | ~5,900 | Vendored llama.cpp headers, committed on purpose. |

### `ml/asr/`, `ml/tts/`, `ml/vision/` — nothing behind them

| File | Lines | Status |
| --- | ---: | --- |
| `asr/AsrEngine.kt` | 96 | **CONTRACT ONLY.** Whole-utterance, language as a parameter, never detected. |
| `tts/TtsEngine.kt` | 37 | **CONTRACT ONLY.** |
| `vision/VisionEngines.kt` | 104 | **CONTRACT ONLY.** `OcrEngine`, `PoseEngine`, `DishClassifier`. |

### `ui/`, `di/`, application

| File | Lines | Status |
| --- | ---: | --- |
| `ui/MainActivity.kt` | 73 | IMPLEMENTED. A static list of seven pipelines all reading "Not implemented". No figures, no sample data — by design. This is the entire UI. |
| `di/AppModule.kt` | 78 | IMPLEMENTED. Provides the Room database, the food lookup and the rules engine, and wires the unmatched-utterance sink. **Provides no `ml/` engine, deliberately.** |
| `KatoriApp.kt` | 15 | IMPLEMENTED. `@HiltAndroidApp`, deliberately empty. |

### Tests

`app/src/test/` — **103 `@Test` methods**, counted from source:
NumericGuardTest 18 · LlmEngineTest 19 · SqliteFoodLookupTest 20 · RecipeLayerTest 16 ·
RulesEngineTest 16 · FoodTextMatchingTest 11 · NetworkIsolationTest 2 · MatchRateTest 1.

`app/src/androidTest/java/…/HardwareProbeTest.kt` (319 lines) — **UNTRACKED IN GIT.** 5 tests.
3 pass on the phone, 2 fail. See §4.

### Data and artefacts

- `app/src/main/assets/food/katori-food.db` — 311,296 bytes,
  sha256 `797151368ad8a41d774898214ae08eb0a5009becdaf490eb846f4dcc285a2b31`, which **matches** the
  hash in `logs/food-db-build.log`. The shipped database is provably the one that passed all 17
  import assertions.
- `data-authoring/*.csv` — 81 ingredients, 503 aliases, 13 no-data items, 50 recipes over 434
  ingredient rows, 21 unit conversions, 264-line utterance test set. All committed with reasoning
  in comment headers.
- `app/src/main/jniLibs/arm64-v8a/` — the four llama.cpp libraries, 55 MB, **gitignored**.
- `data-sources/` — llama.cpp clone, USDA zips, 1.67 GB of model weights. **Gitignored.**
- `logs/` — 40 files, the entire evidence trail. **Gitignored. Not in git. A fresh clone has none of
  it.**

101 files are tracked in git. Everything else above exists only on this machine.

---

## 4. VERIFICATION STATUS

### A. Verified on hardware

**`STATUS.md` says "Nothing. Still not one line." That is now wrong.** A hardware run happened at
17:38–17:44 on 19 Sep 2026, after `STATUS.md` was last written at 15:55. Evidence:
`logs/hardware.log`, `logs/utf8-hw-probe-logcat.log`, `logs/hw-launch.png`,
`app/build/outputs/androidTest-results/connected/debug/flavors/demo/TEST-RMX3780 - 15.xml`.

The device: **realme RMX3780, Android 15 (API 35), arm64-v8a, 8 cores, 7,619.4 MB RAM,** heap class
384 MB / large 512 MB, low-memory threshold 432 MB.
(`STATUS.md` and several records call the target "the Realme 11". The attached device reports
`RMX3780`. Whether that is the same handset is not determinable from this repository.)

Genuinely verified on that phone:

1. **`assembleDemoDebug` + `assembleDemoDebugAndroidTest` succeed** on the laptop toolchain
   (exit 0), and the permission allowlist fired on that build: merged manifest is exactly
   `CAMERA, RECORD_AUDIO, DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
   (`logs/utf8-hw-assemble.log:85`, `logs/merged-manifest-demoDebug.xml`, written 17:40).
2. **`installDemoDebug` succeeds** (exit 0).
3. **The app launches and does not crash.** PID 4979 alive after 6 s, no `FATAL EXCEPTION` /
   `ANR in` / `AndroidRuntime` markers in logcat, screenshot pulled to `logs/hw-launch.png`.
4. **Device facts read on-device**: baseline process PSS 120.5 MB.
5. **ML Kit OCR works in the demo build with the INTERNET permission removed.** Rendered
   `"Haemoglobin 9.8 g/dL"`, recognised `"Haemoglobin 9.8g/dL"`, 1 block, figure read, label read,
   `OCR USABLE true`. **This closes the open residual risk in `0004` and "least confident #1" in the
   Phase 1B review packet.** Stripping the telemetry transport's network permission does not break
   recognition.

That is the whole hardware list. It is small, but it is no longer empty, and item 5 is a real answer
to a question that had been open since the first commit.

### B. Verified by unit test, container build, or desktop only — NOT hardware

1. **103 JVM tests, 0 failures**, in the cloud container. Caveat: `logs/container-test3.log` and
   `container-test6.log` record `BUILD SUCCESSFUL` but **do not contain a test count or the
   match-rate table** — Gradle did not print them. The "103 tests" figure in `STATUS.md` is not
   readable from that log. It does match a count of `@Test` methods in the source, so it is almost
   certainly right, but it was not read off a log the way the rest of this project's claims were.
   The only test-result XML on this machine is from an older run (48 tests, 06:33).
2. **Demo APK builds, arm64-v8a only, permission allowlist enforced** — container and laptop.
3. **Match rate 210/210 ingredients, 22/22 no-data refusals, 0 WRONG FOOD** — and this number is
   circular by construction. See §7.
4. **17 food-database import assertions pass** (`logs/food-db-build.log`), and the shipped `.db`
   hash matches.
5. **llama.cpp runs Qwen 2.5 1.5B Q4_K_M on a 2-core Linux VM**: load 10.08 s, 9.89 tok/s eval,
   valid JSON first try on the real extraction prompt (`0006`, `logs/llama-smoke.log`).
   **This is not a Katori number and must never be quoted as one.**
6. **`libllama.so` + 3 ggml libraries cross-compile for arm64-v8a** against NDK 28.2.13676358 at
   `android-26` (`logs/llama-android-build.log`).
7. **`libkatori_llama.so` compiles, links and exports its three symbols** under the exact names the
   Kotlin class expects (`logs/container-native.log`).

**Emulator: nothing. Never built for one.**

### C. Written but NEVER EXECUTED — the list that matters

1. **The entire llama.cpp inference path on any phone.**
   `HardwareProbeTest.b_llamaLoadsAndRunsOnThisPhone` and `c_coResidency` both FAILED on the device
   — but **not because of a code defect**. The 1,065 MB GGUF push died after 18.3 s with
   `adb: error: … no response: Broken pipe`, so the model file was simply not there. The failure is
   `AssertionError: the model was not pushed`. Nothing about load time, tokens/second, memory or
   output quality on a phone is known.
2. **Co-residency of ASR + LLM + TTS.** Reported as `null`. Because `c_coResidency` loads the LLM
   first and that throws, the two ONNX sessions were never even attempted — so **ONNX Runtime has
   never opened the IndicConformer or Piper model on this device either.** `canCoReside`, the
   stage-1 item the `ModelArbiter` contract exists to answer, is unanswered.
3. **`AndroidFoodDbSource`** — the asset copy and the Android SQLite read path. Never run. Every
   food test uses the JDBC seam instead.
4. **The entire Room database.** No DAO query has ever executed, on a device or on the JVM. The
   database was never even opened during the on-device launch, because nothing injects it yet.
5. **`AppModule`'s providers** beyond whatever Hilt instantiated at launch — which, given
   `MainActivity` injects nothing, is nothing.
6. **`DefaultRulesEngine` on a device.** Tested thoroughly on the JVM; never run on Android.
7. **`LlamaCppLlmEngine` against a real model.** Tested only against a scripted runtime.
8. **Every ASR, TTS and vision engine** — they have no implementations at all.
9. **`recordUnmatched`** — wired through `AppModule` but there is no caller anywhere.
10. **`UnavailableReason.BELOW_CONFIDENCE_THRESHOLD`** — declared, never produced by any code path.
11. **The `full` product flavour.** Never built, never tested.
12. **The release build type.** Never built. `isMinifyEnabled = false`, proguard rules are a stub.
13. **The report file the hardware probe writes.** The test logged
    `report written to /storage/emulated/0/Android/media/…/katori-hardware-report.txt`, and the
    subsequent `adb pull` failed with `No such file or directory`. The logcat capture is the only
    surviving copy. Unresolved.

---

## 5. DECISIONS ALREADY MADE, AND WHICH ARE BINDING

| # | Summary | Binding? |
| --- | --- | --- |
| **0001** Phase 2 contracts | Thirteen interface-only files: `Outcome` with no `Partial`, computed-not-chosen three-band confidence, three-state `NutrientValue`, derived meal totals, `java.time` directly (no desugaring), network isolation enforced by test. Product name KATORI (Swasth dropped over three name collisions). | **FROZEN.** States one open item: `DishGuess.score` / `RankedCandidate.score` are internal-only and a reviewer may prefer them removed from the public types. Also states the namespace is `<domain>.katori` "pending the domain Vedant is registering" — the tree is `io.github.vedant7007.katori` and the record says the rename already happened "in one pass as planned". **If a domain is still coming, that is a second rename the record says would not happen.** |
| **0002** USDA import rules | SR Legacy is primary, Foundation is a selective overlay, Branded is excluded. Four enforced rules: never coalesce a missing nutrient to zero; energy coalesces 1008→2048→2047; dairy/B12 never from Foundation; prefer unenriched/unfortified records from a named fdcId list. Ragi, bajra and jaggery are BLOCKING no-data. | **BINDING.** Carries an explicit **revisit condition**: ragi/bajra/jaggery stay no-data "until NIN responds or a licensed source appears". Its own status line still says "Not yet implemented" — that is stale; the importer exists and enforces all four rules. |
| **0003** SDK root and toolchain | Chose `AppData\Local\Android\Sdk` over the home-directory root because only it has android-37.0. JDK 21 Temurin. Builds run from `.bat` scripts with output to `logs/`, read off disk rather than from a screen. | **BINDING** for the SDK root and JDK. The script-and-log workflow was a workaround for click-only terminals and is already partly superseded by `0009`. |
| **0004** AGP 9 and the telemetry permission | Moved AGP 8.13.2 → 9.4.1 because androidx required it; each version was named by an error message. Dropped the Kotlin plugin. **Found INTERNET arriving transitively from `com.google.android.datatransport:transport-backend-cct` via ML Kit**, removed with `tools:node="remove"`. | **BINDING.** Its stated residual risk — whether ML Kit initialises with its telemetry transport denied network — **is now resolved: it does.** (§4A item 5.) Update this record. |
| **0005** Model sourcing and licences | Scope ruled non-commercial student project. The spec's named Indic Whisper ONNX packs **do not exist**; IndicConformer NeMo CTC INT8 is used instead (Apache-2.0). Piper `te_IN-padmavathi-medium` for TTS (CC-BY-4.0). Qwen 2.5 1.5B Q4_K_M (Apache-2.0). ~1.75 GB total. | **BINDING**, and carries the sharpest **expiry**: "**If Katori ever goes commercial, every licence in this record is re-audited first.**" The non-commercial register is currently EMPTY — nothing shipped needs the allowance. Anything added with a non-commercial or unclear licence goes in that table **the day it is added**. Also flags that the IndicConformer repo is one person's export with 4 likes and **needs a hardware smoke test before anyone trusts it** — still not done. |
| **0006** Runtime smoke test and matcher findings | llama.cpp + Qwen verified on desktop only. Five matcher bugs found by *measuring*: biryani→bay leaf, upma→semolina, atta→curry leaves, avalu→horse gram, and an ambiguous alias on two foods. Four fixes: one-directional word-bounded containment, no fuzzy matching of the no-data list, tolerance 2→1 in the 5–12 char band, and an importer assertion on ambiguous aliases. | **BINDING.** Explicit revisit: the authored utterance set "gets replaced by those transcripts when they exist" (spec 18.3, Abhinav's 100+ recorded meal logs). |
| **0007** KSP, Room and Hilt on AGP 9 | KSP refuses AGP's built-in Kotlin; the Kotlin plugin then refuses AGP 9's new DSL; Room and Hilt need KSP, so `newDsl=false`. Added `KatoriDatabase`, enums by name, no destructive migration, committed schema export, empty `KatoriApp`, and an `AppModule` that deliberately provides no `ml/` engine. | **BINDING with a stated EXPIRY.** AGP calls `newDsl=false` a temporary bypass; **the build's own deprecation warnings say both flags are removed in AGP 10.** When that lands: move to built-in Kotlin and drop KSP (replacing Room's compiler and Hilt), or freeze on the last AGP that honours the flag. Both are real work. |
| **0008** Authored reference recipe layer | 50 dishes, 434 ingredient rows, in two committed CSVs. Absorbed oil is what the dish RETAINS as a fraction of the **pre-fry** weight. Bare dish names mean the dish. Dish and ingredient matched together, stronger wins, ties to the dish. Two nutrition paths cross-checked by a test. One unknown ingredient makes the dish unknown on the shipped path. | **BINDING.** Contains the single best finding in the project: idli was authored at a wrong yield, every existing assertion passed it, and a new moisture-band assertion catches it. The bands "are wide and they are not an accuracy check" — narrowing them without weighed plates "would be inventing precision". |
| **0009** Building in the cloud container | JVM tests and the demo APK now build in a cloud container in ~20 s, off the critical path of desktop control. The laptop stays the source of truth. A container build is explicitly **not** a hardware claim. | **BINDING**, and partly superseded: desktop control evidently came back, since a full hardware run happened. The container is still the fast path for tests. |
| **0010** The JNI bridge | llama.cpp is prebuilt into `jniLibs`, not built by Gradle. The native surface is exactly `nativeLoad`/`nativeGenerate`/`nativeFree` and must stay that narrow. A prompt that will not fit the context window is **refused, not trimmed**. Greedy sampling, KV cache cleared per call, CPU only. | **BINDING**, and it is explicit that it is "accepted for the code, NOT for any behaviour claim". Its closing instruction — "the first thing to do when a phone is attached: load the model, run the real extraction prompt, read the result out of logcat" — **has been attempted and has not succeeded.** |

---

## 6. HARD RULES THAT MUST NOT BE BROKEN

These are extracted from the decision records and from source comments. Every one has a reason
attached, and in several cases the reason is a bug that was actually found.

1. **The demo build's permissions are a WHITELIST, and it is exactly three.**
   `CAMERA`, `RECORD_AUDIO`, and the platform's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
   `ALLOWED_DEMO_PERMISSIONS` in `app/build.gradle.kts` is checked against the **merged** manifest on
   every `assembleDemo*`. It is a whitelist and not a ban on INTERNET because a denylist only finds
   what it already knows to look for. **Adding an entry requires a written reason next to it.** Do
   not "fix" a merge conflict by deleting the `tools:node="remove"` lines in
   `app/src/demo/AndroidManifest.xml`. (`0004`, commit `1c72bbc`)

2. **No-data items must NEVER resolve to a food.** Thirteen of them: curry leaves, asafoetida,
   ajwain, amchur, **ragi, bajra, jaggery**, ivy gourd, snake gourd, cluster beans, methi leaves,
   horse gram, wheat vermicelli. They are checked FIRST in `SqliteFoodLookup.resolve`, before any
   other match, and **the no-data list is never fuzzy-matched** (that rule exists because "gajar" was
   being refused as "gawar"). The importer asserts it and `MatchRateTest` asserts it. Substituting a
   generic millet record for ragi would put a wrong calcium number on a nutrient people track
   deliberately. (`0002`, `0006`)

3. **The LLM never computes a number.** Two prompt paths and no third. Extraction is defended by a
   strict hand-written schema reader that refuses rather than repairs; phrasing is defended by
   `DefaultNumericGuard`, which rejects any numeric value in the output that was not in the input.
   The native surface is deliberately three functions so no general completion endpoint can become a
   third path. **The numeric guard is deliberately NOT applied to extraction** — "two rotis" is
   correctly 2 with no digit in the transcript — and an unstated quantity is protected by
   `QUANTITY_INFERRED` capping the figure at Rough instead. (`0001`, `0010`, `LlamaCppLlmEngine`)

4. **No destructive migration on the user database, ever.** `fallbackToDestructiveMigration` is
   called nowhere and must not be added. The Room database holds meals, lab values and declared
   conditions the user typed in and cannot recover. Schemas are exported to `app/schemas` and
   committed so a change is a reviewable diff. (`0007`, `KatoriDatabase`)

5. **Enums are stored by NAME, never by ordinal.** `KatoriConverters` does this explicitly. An
   ordinal silently changes meaning when someone reorders an enum, and here that would relabel the
   confidence band on every meal the user has ever logged. (`0007`)

6. **A stub returns `Outcome.NotImplemented`, never a plausible value.** `NotImplemented` is the only
   legitimate placeholder in the codebase. Never a hardcoded sample, a mock number or a "typical"
   figure, **in any build variant, at any time**. This is why `AppModule` provides no `ml/` engine
   and why `MainActivity` shows seven "Not implemented" lines and no nutrition figures. Binding a
   stub is how a not-implemented state quietly becomes a fake one. (`0001`, `0007`, `Outcome.kt`,
   `MainActivity.kt`)

7. **Commit authorship.** All twelve existing commits are authored
   `Vedant Manmath Idlgave <vedantidlgave16@gmail.com>` with **no `Co-Authored-By` trailer** and no
   attribution lines. **The repository records no authorship convention at all** — there is no
   `CLAUDE.md`, no contributing guide, nothing in the decision records. The instruction in force in
   the session that produced this handover is that new commits end with
   `Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>` and PR descriptions end with
   the Claude Code generation line. That instruction comes from the harness, not from this repo, so
   **confirm with Vedant which convention applies before committing** — adopting it silently would
   make the history inconsistent from commit 13 onward.

8. **`Unknown` is never collapsed to zero.** Not on import, not in a sum, not in the UI. There is
   deliberately no `getOrZero()` helper and no default value anywhere on `NutrientValue`. USDA states
   plainly that a missing value does not mean zero. For B12 in an app used by vegetarians, collapsing
   it is the single most consequential bug available to this project. A partial total is presented as
   a floor ("at least"), never as a total. (`0001`, `0002`, `Nutrients.kt`)

9. **Confidence is computed, never chosen, and there is no numeric confidence anywhere.** Worst
   ceiling wins. No percentage may reach the UI, because no defensible formula exists for composing
   ASR confidence, name-match confidence, unit error and recipe variance into one number. An empty
   reason list throws rather than defaulting to GOOD. (`0001`, `Confidence.kt`)

10. **Every figure from the recipe layer is capped at Approximate** by `AUTHORED_REFERENCE_RECIPE`,
    however exact the name match. Knowing the word is not knowing the plate. **Absorbed oil is what
    the dish retains, as a fraction of the PRE-FRY weight** — a fraction of the finished weight is
    circular because the finished weight includes the oil being measured. (`0008`)

11. **The rules engine is pure.** No clock, no randomness, no I/O, no model, not suspending. The
    input digest deliberately excludes `evaluatedAt`, because if the clock contributed, every
    evaluation would differ trivially and beat 4's proof would be worthless. A rule whose outcome
    depends on elapsed time must compare dates found in the data. (`0001`, `RulesEngine.kt`)

12. **No template ever names a disease the user did not declare**, and an escalation emits **no
    dietary suggestion at all**. Safety constraints (allergies, diet type) are never relaxed to
    produce a suggestion. No nutrient preference means no suggestions, rather than an ordering with
    no basis. (`0001`, `0006`, `DefaultRulesEngine`)

13. **A prompt that does not fit the context window is REFUSED, not trimmed.** Trimming removes the
    head first, and the head is the system block telling the model not to invent numbers.
    (`0010`, `katori_llama.cpp:191`)

14. **No IFCT 2017, no INDB, no `ifct2017` package, in any form** — including as a reference to check
    values against. Licence grounds. (`0002`, `data-authoring/README.md`)

15. **Containment matches in ONE direction only, on word boundaries.** The alias must appear inside
    the utterance as a whole run of words. This rule exists because the reverse direction produced
    biryani→bay leaf, upma→semolina, atta→curry leaves and avalu→horse gram. (`0006`)

16. **One spoken name maps to exactly one food, and no name means both a food and a dish.** Asserted
    at import; the build deletes the database rather than ship a violation. This assertion found
    three collisions nobody had noticed. (`0006`, `0008`)

17. **WRONG FOOD is the metric, not the match rate.** A miss is honest and the user gets asked; a
    wrong food silently puts a wrong number in a health app. `MatchRateTest` asserts zero wrong foods
    and a floor on the rate. **The 100% figure is circular and must not be shown to judges.**
    (`0006`, `0008`)

18. **`data-sources/`, `logs/`, `jniLibs/` and model weights are never committed.** The llama.cpp
    headers beside `jniLibs` ARE committed, because the shim does not compile without them.
    (`.gitignore`, `0010`)

---

## 7. KNOWN BUGS, RISKS AND UNFINISHED WORK

### Real bugs found while reading

1. **`tools/build-llama-android.ps1` does not do what three documents say it does.**
   `docs/decisions/0010`, `app/src/main/cpp/CMakeLists.txt` and the commit message for `89ec32d` all
   state the libraries are built "into `app/src/main/jniLibs/arm64-v8a`". **The script contains no
   copy step.** It builds into `data-sources/llama.cpp/build-android-arm64/bin/` and then just lists
   the files. The four `.so` files in `jniLibs` have a hard-link count of 2 and a timestamp 3½ hours
   after the build log, so they were placed there by hand. A fresh clone that follows the documented
   instruction will hit `FATAL_ERROR: libllama.so is missing` at CMake configure. Fix: add the copy
   to the script (four lines), or correct all three documents.

2. **`AndroidFoodDbSource` will serve a stale food database forever.** The comment says the copy is
   "keyed by the database's schema version, so shipping a new database replaces the copy instead of
   silently keeping the old one." It is not. The target filename is the hard-coded constant
   `katori-food-v1.db`, and the copy runs only `if (!target.exists() || target.length() == 0L)`.
   Nothing reads a schema version. The bundled database has already been rebuilt three times
   (118 KB → 180 KB → 311 KB); any device that ran an earlier build keeps the old one. The code has
   never run, so this has not bitten yet — it will on the first update. Fix: key the local filename
   on the asset's hash or a version row, or delete-and-recopy when sizes differ.

3. **Millilitres are converted to grams at density 1.0, at GOOD confidence.**
   `SqliteFoodLookup.GRAM_SYNONYMS` maps `ml`/`litre` to the same factors as `g`/`kg`, and returns
   `isDefaultConversion = false`, so no `HOUSEHOLD_UNIT_DEFAULT` reason is attached and the figure
   can reach GOOD. Cooking oil is about 0.92 g/ml. "Two spoons of oil" spoken in ml is then ~8%
   wrong, presented as the best confidence band the app has. Either attach `HOUSEHOLD_UNIT_DEFAULT`
   to volume units or carry a per-class density.

4. **`SqliteFoodLookup.resolve` has no confidence threshold, but its contract promises one.**
   `FoodLookup.resolve` says it "returns ONE match only when it is confident enough to act on". The
   implementation returns whatever the matcher found, including a FUZZY match, and
   `UnavailableReason.BELOW_CONFIDENCE_THRESHOLD` is declared but produced nowhere. Today the
   matcher's tolerances do the gatekeeping. That is defensible, but the contract and the code
   disagree and one of them should change.

5. **`recordUnmatched` has no caller.** The sink is wired through `AppModule` into the Room DAO, but
   nothing invokes it. "Every unmatched utterance is recorded" is a contract obligation on a caller
   that does not exist yet. Whoever writes the orchestrator owns this.

6. **The hardware probe's report cannot be retrieved.** The test writes it to
   `externalMediaDirs.first()` and logs the path; the script's `adb pull` of the same path returns
   `No such file or directory`, even though the models pushed to that same directory are visible to
   `adb shell ls`. Unexplained. Until it is fixed, the only copy of a probe run is the logcat
   capture.

### Bugs that assertions and tests currently guard against — these encode real defects found

Each of these was a live bug at some point. The guard is the only thing keeping it dead.

- `biryani` → **bay leaf**, `upma` → **semolina**, `atta` → **curry leaves**, `avalu` → **horse
  gram**, via reverse containment on their own aliases. Guarded by `containsAsWords` + the
  one-directional rule, and by `MatchRateTest`'s WRONG FOOD assertion.
- `gajar` (carrot) **refused as** `gawar` (cluster beans), one edit apart. Guarded by
  `allowFuzzy = false` on the no-data table.
- `bendakaya` (okra) → `dondakaya` (ivy gourd), two edits at nine characters. Guarded by
  `toleranceFor` dropping the 5–12 char band from 2 to 1.
- `"rice"`, `"chawal"`, `"chana"` each sitting on **both a raw and a cooked record**, with whichever
  loaded last winning silently. Guarded by the importer's ambiguous-alias assertion.
- `pulihora` pointing at **lemon rice** instead of tamarind rice. Guarded by the utterance set.
- `dal`, `daal` and `दाल` resolving to **nothing at all**, orphaned by the alias-collision fix.
  Guarded by the utterance set.
- **Idli authored at a yield wrong by nearly a factor of two** (227 kcal/100 g for a steamed cake,
  against 258 for a dry griddle roti). Every existing assertion passed it because they all take
  `yield_g` as given. Guarded by import assertion 7, which derives moisture from the dish's own
  macros and rejects it against a band for its cooking method. **The build deletes the database
  rather than ship it.**
- A public dataset reading **745 kcal/100 g for a vada** by counting the whole frying bath. Guarded
  by the absorbed-oil-band assertion and by `RecipeLayerTest`'s deep-fried range test; ours reads
  294.
- **Gradle marking the test task UP-TO-DATE after the food database was rebuilt**, so a green build
  reported the previous run's numbers. Guarded by declaring the `.db` and the utterance CSV as test
  inputs in `app/build.gradle.kts`. A green build that did not run is worse than a red one.
- **INTERNET arriving from a transitive Google telemetry uploader.** Guarded by the merged-manifest
  permission allowlist.
- A **stale shipped recipe table** drifting from what the ingredient rows add up to. Guarded by the
  two-path cross-check test in `RecipeLayerTest`.

### Risks

- **`ModelArbiter` and `Orchestrator` are contracts with nothing behind them**, and the review packet
  already named them as the two the demo rests on. Every memory, eviction and co-residency claim in
  the architecture is currently a document.
- **Co-residency is still unmeasured**, and the probe's own comment says the figure it would produce
  is a **floor**, not the real footprint: sherpa-onnx adds a feature extractor and decoder state,
  Piper adds a phonemiser, and neither is loaded. If the floor does not fit, spoken confirmation has
  to degrade to on-screen — a **product change, not a bug**.
- **The IndicConformer ASR export is one person's repository with 4 likes and no recorded downloads**
  (`0005`). Its Apache-2.0 claim is its own. It has never been loaded. The whole ASR plan rests on it.
- **Piper integration has known unfinished prerequisites** that nobody has started: the voice carries
  no sherpa metadata, so a script must stamp `model_type` and generate `tokens.txt` from the
  `phoneme_id_map`, and the app must ship ~7 MB of `espeak-ng-data`. **`espeak-ng-data` is not
  downloaded and no stamping script exists.**
- **AGP 10 removes both compatibility flags.** Already warned about in every build log.
- **`onnxruntime-android` is now an `implementation` dependency of every variant**, added for the
  probe. The demo APK on disk is **75.6 MB**, not the 46 MB `STATUS.md` reports. It does not breach
  the permission allowlist (verified at 17:40), but shipping an unused ~20 MB runtime in the stage
  build is worth a decision. Consider `androidTestImplementation` until slice F lands.
- **The 100% match rate is circular and `STATUS.md` says so in bold.** The utterance set was written
  by the same hand as the aliases, on the same day for the dish rows. It is a regression guard. It is
  not accuracy and it does not go on a slide. It stops being circular when Abhinav's recorded
  transcripts arrive.
- **`NetworkIsolationTest` skips comment lines**, so a network call on the same line as a trailing
  comment slips through. Noted in the review packet; still true.
- **The permission allowlist hooks `assemble<Variant>` only.** Verify it also gates any path that
  produces a shippable artefact.
- **The release build type has never been built**, minification is off, and proguard rules are a
  two-line stub. JNI + Room + Hilt + minification is exactly where a release build breaks first.
- **`targetSdk = 36` under `compileSdk = 37`** is not explained anywhere.
- **`logs/` is gitignored**, so the entire evidence trail for every claim in this project exists on
  one laptop and in no backup. Given how much of this project's discipline is "read it off the log
  the build wrote", that is a real single point of failure.
- **The namespace may still need a rename.** `0001` says the namespace is `<domain>.katori` "pending
  the domain Vedant is registering", and separately says the rename already happened. The tree reads
  `io.github.vedant7007.katori`. If a domain is still coming, the rename now costs more than it would
  have, because implementations exist and the JNI symbol names encode the package
  (`Java_io_github_vedant7007_katori_ml_llm_LlamaCppRuntime_nativeLoad`).
- **Uncommitted work.** `HardwareProbeTest.kt` (319 lines), `tools/hardware-probe.ps1`,
  `tools/6-hardware-probe.bat` are untracked; `app/build.gradle.kts`, `katori_llama.cpp`,
  `LlamaCppRuntime.kt` and `libs.versions.toml` are modified. **This is the only hardware
  instrumentation the project has and it is not in git.** Commit it before touching anything else.

### Documentation that disagrees with the code — code wins

| Says | Actually |
| --- | --- |
| `STATUS.md`: "VERIFIED ON HARDWARE — Nothing. Still not one line." | The app builds, installs, launches without crashing, and ML Kit OCR reads text with no network permission, all on RMX3780. §4A. |
| `STATUS.md`: "Demo APK 46 MB" | 75,590,883 bytes on disk after `onnxruntime-android` was added. |
| `STATUS.md`: "103 tests, 0 failures… read out of `logs/container-test3.log`" | That log contains no test count. 103 matches a count of `@Test` methods in source. |
| `data-authoring/README.md`: "Not started. Waiting on the Phase 2 schema review" | 50 recipes and 434 ingredient rows shipped in commit `07b41fc`. The README was never updated. |
| `0002` status: "Not yet implemented; the bundled database build does not exist" | `tools/build_food_db.py` (721 lines) exists and enforces every rule in that record. |
| `0008` and `STATUS.md`: "twelve assertions" at import | `logs/food-db-build.log` prints **17** assertion results. |
| `0010` / `CMakeLists.txt` / commit `89ec32d`: the script builds into `jniLibs` | It does not. Bug 1 above. |
| `AndroidFoodDbSource` comment: the copy is "keyed by the database's schema version" | It is keyed by a hard-coded filename. Bug 2 above. |
| `0004`: "RESIDUAL RISK, not yet tested: ML Kit OCR" | Tested. It works. |

---

## 8. WHAT I WOULD DO NEXT

In order, and with reasons.

**1. Commit the hardware probe. Today, before anything else.**
`HardwareProbeTest.kt` plus its two runner scripts are untracked, and they are the only means this
project has of turning an assertion into a measurement. Losing them costs more than any code in the
repo. Include the `lastTimings()` / `nativeLastTimings` changes in the same commit — they exist
purely so a tokens-per-second figure is a measurement rather than a guess from a character count.

**2. Get the model onto the phone and finish the probe run.**
This is the one blocking unknown. `adb push` of the 1,065 MB GGUF died at 18.3 s with a broken pipe.
Three things to try, cheapest first: re-run the push (the script is already idempotent — it skips
files present on the device); push over a different cable or with `adb push --sync`; or, if USB keeps
dropping, copy via MTP once by hand and let the test find it. The probe is written to fail loudly
with the exact path it looked in, so once the file is there it either works or tells you why.
Everything downstream — load time, tokens/second, memory, co-residency, whether beat 1 can hold its
3.5 s budget, whether spoken confirmation survives — is gated on this single file transfer. It is the
cheapest high-information action available and it has been the stated next step since `0010`.

**3. Fix the two real bugs while the phone is attached** (§7 bugs 1 and 2). Both are a handful of
lines, both are currently invisible because the code has never run, and both will present as
something else entirely when they do: a fresh clone that cannot configure CMake, and a device serving
an eight-hour-old food database.

**4. Then `ModelArbiter`, not `AsrEngine`.**
`STATUS.md` says next is F (ASR) then G (UI). I would put a minimal `ModelArbiter` first, and the
project's own reasoning supports that. `0006` records the principle explicitly — "wiring dependency
injection around a runtime nobody had run would have been building on the same kind of assumption
that has already failed twice" — and the same argument applies here: the ASR engine's contract says
models are obtained through the arbiter and that it "never loads a file itself". Build ASR first and
you either write it against an arbiter that does not exist, or you give it a private loading path
that the contract forbids and that will have to be unpicked. Once step 2 produces real PSS numbers,
the arbiter's memory ceiling can be a measurement rather than the provisional half-of-RAM formula the
probe currently uses. Minimal means: admission against a measured ceiling, `withModels` atomic
acquisition, LRU eviction of unpinned models, and `canCoReside` reporting what step 2 measured. Not
the full contract.

**5. Reconcile the documents, in one pass.**
`STATUS.md`'s hardware section, the APK size, the test-count provenance, `0002`'s and
`data-authoring/README.md`'s "not started" lines, the twelve-vs-seventeen assertion count, and
`0004`'s now-resolved residual risk. This project's whole credibility model is that a claim is
traceable to a file the build wrote; stale claims in the status document erode exactly that. Add a
line to `0004` recording that ML Kit OCR works without network — that is a resolved risk and it is
worth a reviewer seeing it resolved rather than open.

**6. Decide the `onnxruntime` scope and the namespace question, explicitly.**
The ONNX runtime is a large share of the demo APK and nothing in the shipped app uses it. Move it to
`androidTestImplementation` until slice F needs it, or write down why it stays. And ask Vedant
whether the domain is still coming: if it is, the rename gets more expensive every commit, and the
JNI symbol names make it more than a find-and-replace now.

**What I would not do yet:** any UI beyond the status screen. `MainActivity` showing seven
"Not implemented" lines and zero numbers is the single most disciplined thing in this repository. It
is also the thing most likely to be quietly broken by someone wiring up a screen with a placeholder
figure "just to see the layout", three days before a demo. The rule in `0001` is right and the empty
screen is what enforces it.
