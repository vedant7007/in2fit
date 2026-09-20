# Audit of the submission deck against the repository, 20 September 2026

For Vedant. Deck audited: `IN2FIT — iQOO Hackathon 2026 Submission.pdf` (nine pages, text
extracted with pypdf, quoted verbatim below). Every claim checked against a file in the
repository or a log on the laptop, named per row. Written by Nila.

**Verdicts:** RIGHT = supported by a named file today. STALE = was right when written, the
repository has moved. UNSUPPORTED = no file supports it as worded, or a file contradicts it.
Where a row says "say instead", that wording is supportable today.

A judge asking about a number we cannot stand behind costs more than the number earned. The
rows to fix before anything else are marked **FIX FIRST**.

## Page 8, "Measured, not promised"

| deck says | repository says | verdict |
| --- | --- | --- |
| "Every figure below came off a real handset ... Android 15, eight cores, 7.6 GB of RAM, arm64" | `logs/hw-report-run3.txt`: realme RMX3780, Android 15 (API 35), 8 cores, 7,619.4 MB, arm64-v8a. The device facts are right. The sentence also covers the "Built and tested" tiles, which are build figures from a laptop, not the handset; the claim is over-broad by one word. | RIGHT for the device; say "every timing below" rather than "every figure" |
| **"2.6 s to load the language model into memory, cold, on a mid-range handset"** | No log on this laptop has a 2.6 s load row. `hw-report-*.txt` load times: 928 ms (page-cached, misbuilt library, run2), then 3,439, 5,402, 5,675, 5,759, 5,983, 6,064, 6,096, 6,206, 6,206, 6,367, 6,371, 6,411, 7,222, 8,456 ms. `COORDINATION.md` line 39 (Rao): "Load 2.6–7 s depending on page cache". The only source of 2.6 is that summary line, and it describes a WARM load. The cold loads on record are 5.4–8.5 s. | **FIX FIRST. UNSUPPORTED as worded.** Say instead: "loads in under a second when it is still in memory, and 5 to 8 seconds from cold", or let Rao name the row. Never "2.6 s cold". |
| **"10.6 s for a whole spoken meal to come back as foods, amounts and a citation"** | `logs/hw-report-run3.txt`: `pass 2 (warm) ROUND TRIP 10632 ms`, 8 threads, and the same file says beside it "ASR and TTS are not in this figure". It is the language model turning a TRANSCRIPT into a list of foods and amounts: prompt processing plus generation. Speech recognition before it, the database lookup after it, and any citation are not in the 10.6 s. `0014`, `0026`. | **FIX FIRST. UNSUPPORTED as worded.** Say instead: "10.6 s for the model to turn a transcript into the foods and amounts, on the handset; speech and the lookup are measured separately". Rao's clean run gives the whole-turn number. |
| **"2,230 MB peak, with all three models resident at once, under a 4,191 MB ceiling"** | Two figures from two runs. 2,229.9 MB: `logs/hw-report-run3.txt`, the three model FILES opened as raw sessions (the probe's own words: "Floor, not final: sherpa-onnx adds a feature extractor and decoder state ... Piper adds a phonemiser. Those are not loaded here"), against the provisional 3,809.7 MB ceiling. 4,190.7 MB: the arbiter's calibrated ceiling from a later run whose LLM-only peak was 1,887.6 MB (`0013`, `62ff175`). `0013` also records the peak moving 750 MB across a build change with no explanation established. The real engines (sherpa-onnx ASR, Piper through sherpa-onnx) are in the app since today and their co-residency is not yet measured. | UNSUPPORTED as one sentence. Say instead: "the three model files loaded together measured 2,230 MB, under a ceiling the app calibrates on the device, 4,191 MB on the test handset", and be ready for "is that with the engines running?" with "that is the floor; the run with the engines is this week's". |
| "zero network permissions in the demo build, and the camera still reads a report" | `[verify] demoDebug: permissions are exactly [CAMERA, RECORD_AUDIO, DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION]` on every assemble (`app/build.gradle.kts`, `logs/merged-manifest-demoDebug.xml`); OCR probe `OCR USABLE true` (`hw-report-run3.txt`, `0004`). | RIGHT |
| "81 ingredients with full macro data" | `logs/food-db-build.log` today: `authored: 93 ingredients`. Rebuilt hash matches the committed database (`32c4934a…`). Macro coverage: 641/648 values present on the earlier 81; the phrase "full macro data" is fibre 78/81 and B12 77/81 on that build, so "full" is one nutrient off; energy, protein, carbohydrate and fat are 100%. | STALE: 93. "full macro data" is right for the four macros. |
| "503 name variants, three scripts" | `aliases: 626` today (same log). Three scripts: Latin, Telugu, Devanagari, yes. | STALE: 626 |
| "131 facts, every one cited" | `app/src/main/assets/knowledge/facts.csv`: 131 data rows, columns include `source` and `source_url` (`0020`). | RIGHT |
| **"252 tests green on every commit"** | 300 tests at 15:23 today (`logs/packet3-build.log`, my worktree). But `ebd88cf` (Priya, today) added nine tests that are RED BY DESIGN until Rao lands four engine fixes: "Nine fail on the shipped path, two pass as the controls." On master right now the suite is not green. The run at the bottom of this file is the count as of this audit. | **FIX FIRST. STALE and, today, WRONG.** Say "300 tests" only when the nine are green; until then "over 300 tests, with the ones that are red named for the fix they wait on" is the honest sentence, or leave the tile until Rao lands the fixes. |
| "50 Indian dishes with their recipes" | `recipes: 52` today. | STALE: 52 |
| **"48 interface strings translated into Telugu and queued for review by a native speaker"** | 136 strings today, and NONE were translated by a person: every one is machine-generated, marked so in the file, unreviewed (`0017`, `values-te/strings.xml` header). The packet goes to a fluent speaker tonight. | **FIX FIRST. STALE and the verb is wrong.** Say instead: "136 interface strings drafted into Telugu, every one marked for review by a native speaker before the battle". A Telugu-speaking judge asking "who translated these?" must hear the true answer. |
| "Not built yet: The screens. Voice in and voice out. Both are written and tested in isolation. Neither has run on a handset end to end." | Stale in the other direction: the shell exists (Talk, Scan, About tabs, `docs/screenshots/2026-09-20-shell/`), the ASR engine is bound (`28c4620`), spoken output exists (`d7e2b71`, `1de3e7d`). Whether the first end-to-end journey has run on the handset is Rao's to state; the emulator screenshots carry no hardware claim. | STALE; the honest line depends on Rao's run this week |
| "Kotlin and Jetpack Compose · Room · llama.cpp through a native bridge · Qwen 2.5 1.5B · IndicConformer · Piper · Google ML Kit · USDA public-domain food data. Every dependency disclosed and licence-checked before it went in." | Missing from the list: **sherpa-onnx** (the ASR and TTS runtime, Apache-2.0), **NVIDIA FastConformer** (the English recogniser, CC-BY-4.0, not IndicConformer: `0005` corrected today), **espeak-ng** (GPL-3.0-or-later, inside the app for Piper: `0005`). "Every dependency disclosed" is exposed while the GPL one is not on the slide. Hilt is also absent (fine). Piper may drop out if the platform voice wins (`0019`). | UNSUPPORTED as "every dependency disclosed" until sherpa-onnx, the NVIDIA model and espeak-ng are on the line. The About screen in the app already lists them. |

## Page 7, "Why the phone"

| deck says | repository says | verdict |
| --- | --- | --- |
| "One sentence, three languages. Recognition runs on the handset, tuned for how people here actually mix Telugu, Hindi and English mid-sentence." | `0022-one-model-for-mixed-speech`, measured today: no available model transcribes code-mixed speech cleanly; the app runs ONE checkpoint per the language the profile selects (option A), and "A does not literally meet 'one model for any mix', and nobody is pretending it does". Language is a setting, never detected (`0015`, spec 10.2). A Telugu-profile user's Hindi sentence comes back as Telugu-script transliteration; Priya's `0026-code-mixed-food-words` recovers English food words said the Telugu way inside a Telugu sentence. | **FIX FIRST. UNSUPPORTED as worded.** Say instead: "Recognition runs on the handset in the language you choose, and English food words mixed into that language are understood". Do not say "tuned for mixing three languages mid-sentence". |
| "Steps and movement come from the phone's own sensors. No band to buy" | No sensor code exists: no `SensorManager`, no step counter, no activity tracking anywhere in `app/src/main`. Spec 5.9 lists it; nothing was built. | **UNSUPPORTED.** Remove the SENSORS tile or move it to "Later" on page 9, where exercise form already sits. |
| "The photo is never uploaded and never stored on anyone's server" | No INTERNET permission; OCR on device. | RIGHT |
| "That one build flag made it 2.4 times faster ... dot product and half precision on, the newer matrix instructions correctly off" | `0011`, `STATUS.md`: round trip 26,568 → 10,977 ms at 4 threads = 2.42x; i8mm deliberately absent because the cores lack it. | RIGHT (it is the extraction round trip that got 2.4x faster; prompt processing 3.4x, generation 1.4x) |
| "a test fails the build if any dependency quietly adds one back" | `verifyDemoDebugPermissions`, an allowlist on the MERGED manifest, hooked to every `assembleDemo*`. | RIGHT |

## Page 6, "Technical depth"

| deck says | repository says | verdict |
| --- | --- | --- |
| "01 A katori is not a unit ... fill your katori with water, tell it the measure once, and every later 'one katori' means your bowl" | `unit_conversions` is a user-overrides table and `resolveUnit` reports `isDefaultConversion`, so the design supports it (`0015`). No calibration screen exists in `ui/`; no path writes a user override today. | UNSUPPORTED as a thing that can be shown. Say "the design carries it" or move the sentence to "Before the battle" if it will be built. |
| "When someone says 'some rice', the app asks what some means instead of inventing a figure" | `QUANTITY_INFERRED` caps at Rough and forces a correctable display (`Confidence.kt`); the extraction leaves an unstated quantity null (`0014`); the app never invents one. Whether it ASKS is the orchestrator's confirmation path (Rao). | RIGHT in substance; "asks" is Rao's to confirm for the shipped turn |
| "02 One dish, many kitchens ... the model asks the one question that moves the number most, remembers the answer for that dish, and stops asking" | The household recipe table exists (`62ff175`); the reference recipes are editable by design (`0008`). No screen asks the question or edits a recipe today. | UNSUPPORTED as a thing that can be shown; the data model exists. |
| "03 Three languages in one sentence ... it goes through one multilingual model that was never asked to pick a language first" | Directly contradicted by `0022-one-model-for-mixed-speech`: one checkpoint per selected language, the language is picked first, by the profile. | **FIX FIRST. UNSUPPORTED**, and the opposite of what shipped. Replace with what `0022` found: three single-language checkpoints, the user chooses, English food words inside the chosen language are recovered by the matcher. That is still a real technical story and it has a measurement behind it. |
| "04 The database we were not allowed to ship ... India's own food composition tables forbid storing them inside a product ... rebuilt on public-domain USDA values plus recipes our team authored" | `0002`: IFCT 2017 / INDB excluded on licence grounds; USDA CC0; 52 authored recipes with their reasoning in `data-authoring/`. | RIGHT |

## Pages 3 and 4, the screen mock-ups

| deck says | repository says | verdict |
| --- | --- | --- |
| "Rendu roti and one katori dal ... 412 kcal, 14 g protein, 62 g carbs, 9 g fat ... Roti 2 × 40 g, Dal 1 katori" | From the shipped database: chapati 258 kcal / 10.0 g protein / 54.6 g carbohydrate / 1.9 g fat per 100 g; dal tadka 130 / 6.6 / 19.9 / 3.2 per 100 g; a katori of cooked dal is 150 g. Two 40 g rotis plus 150 g dal = **402 kcal, 18 g protein, 74 g carbs, 6 g fat**. The deck's four figures are not what the app would show. | **UNSUPPORTED**: the app's own rule is that no number is invented, and these were. Replace with the database's figures above, or with a screenshot from the app once the plate renders. |
| "How much protein today? ... 58 g so far, about 40 g short of your target" | No protein target exists in the profile or the rules; the app never states "your target". | UNSUPPORTED as a feature. Reword to what a RECOMMEND actually returns (one sentence, from the person's records and cited facts, no target). |
| "Swap one roti for more dal. Your last report shows fasting glucose above the printed range, so fewer fast carbs at one sitting helps." | The shipped sentence is the template: "Your report from … shows … above the … printed on it, so suggestions are ranked differently now." The deck's "so fewer fast carbs at one sitting helps" asserts that a food change helps a clinical value, which spec 15.2 and `RuleTemplates` forbid ("never say a food will change a clinical value"). | **FIX FIRST. UNSUPPORTED, and it breaks the app's own safety line on the slide that is about the safety line.** Use the real template sentence and the candidate list. |
| "142 mg/dL fasting glucose, 6.4 per cent HbA1c ... both above the range the sheet itself prints" | Illustrative; fine if the printed report used on the day carries values above its printed range (`0018` counts a range only when it is printed beside the value). | RIGHT as an illustration; make the demo report match it |

## Page 2, the problem

| deck says | repository says | verdict |
| --- | --- | --- |
| "101 million adults in India living with diabetes, plus 136 million more who are prediabetic. Sources: ICMR-INDIAB national study, 2023" | External. Not in the repository; the ICMR-INDIAB figures (Lancet Diabetes & Endocrinology, 2023) are widely quoted as 101 million and 136 million. Keep the citation on the slide. | Cannot be supported from the repo; plausible; keep the source line |
| "122–273 kcal in one bowl of sambar, depending entirely on whose kitchen it came from" | `docs/spec.md` 15.1.1 asserts "the 122 to 273 calorie sambar spread" with no source. Our own authored sambar is one recipe with one figure; it does not measure a spread. | Cannot be supported from the repo. Either find the source or say "our own recipe layer needs a stated composition because no two kitchens agree" without the numbers. |
| "~33% how far photo-based calorie apps ran low on calories and fat in a 2026 test. Source: NUTRITION 2026 evaluation of four photo calorie apps" | External; not in the repository. | Cannot be supported from the repo; keep only with the citation in hand |

## Page 9, the team and the plan

| deck says | repository says | verdict |
| --- | --- | --- |
| "01 [Name] [one-line role, for example: ...]" × 3 | Template placeholders are still in the submitted PDF. | **FIX FIRST**: unfilled placeholders on the team slide. |
| "Right after: Telugu wording signed off by native speakers" | Consistent with `0017`. | RIGHT |
| "Later: Exercise form checking through the camera, dish recognition" | Dish recognition: `0022-dish-classifier-sourcing`, ruled to keep, ordered after the probes. Exercise form: contract only. | RIGHT |

## Page 1 and 5

| deck says | repository says | verdict |
| --- | --- | --- |
| "Speak a meal in Telugu, Hindi or English" | One language at a time, chosen in the profile (`0022`). True per language. | RIGHT with "in the language you choose"; not three at once |
| "Demonstrated with the radios off" | Run of show Beat 0; permission allowlist. | RIGHT |
| Page 5, the model may / may never; "Those paths are code, and code is testable" | `0015`, `NumericGuard`, `RulesEngine`, the digit test on every template (`db099ca`). | RIGHT |

## The test count at the time of this audit

Run on master at `1d57746`+ (the tree of 15:50, 20 Sep) in the `IQOOOOO-nila` worktree,
`logs/audit-test.log`, every XML written by that run: **315 tests in 29 classes, 306 pass,
9 fail, 0 errors.** The nine are `EngineDefectsTest` (6) and `ReferralDefectsTest` (3),
which `ebd88cf` added red by design until the integrator lands four engine fixes. So today
the honest tile is "315 tests; nine of them are red on purpose, each named for the fix it
waits on", and "green on every commit" is not true until Rao's fixes land.

## The corrections, re-checked against the repository (17:05)

Vedant corrected and republished the deck at about 17:00 and listed the changes. The corrected
PDF is not on this laptop (the file in Downloads is still the 14:07 original), so the pages
themselves are re-checked when it lands; what follows checks each listed correction against the
repository now.

| correction as listed | repository | verdict |
| --- | --- | --- |
| 2.6 s removed; replaced with Priya's 25 of 25 | `CodeMixRenderingsTest`, in my run of 16:43: `25 of 25`, `WRONG FOOD 0`, on `data-authoring/codemix-renderings.csv`, Jacob's exact renderings from the Telugu recogniser. The CSV's own header: "synthetic and one voice: a recorded speaker will vary the vowel signs, and the recorded transcripts replace this file when they exist". | RIGHT, with one word owed on the slide: the 25 are the recogniser's renderings of SYNTHETIC speech. Say "25 of 25 English food words, as the Telugu recogniser renders them from synthetic speech, resolve to the right food" or a judge asking "recorded speakers?" breaks it. |
| 10.6 s reworded to "the model turns a transcript into foods, amounts and a citation" | Extraction returns foods and amounts (`ExtractionJson`); the citation is the database lookup's, which runs after and is not in the 10.6 s. | Half right. Drop "and a citation" or say "and the database then cites each one". |
| 315 automated tests, no claim about green | 315 in my run of 16:43, 9 red by design. | RIGHT |
| 136 machine-translated and marked unreviewed | 136 at 15:21; Arjun adds keys before 22:00, so the number moves tonight. | RIGHT now; re-read the count on the day |
| three-languages claim replaced on all three pages | Needs the file. | pending the PDF |
| the clinical causal clause struck from page 4 | Needs the file. | pending the PDF |
| SENSORS tile replaced | Needs the file to see with what. | pending the PDF |
| 93 / 626 / 52 and 402 / 18 | `logs/food-db-build.log` today; the meal from the shipped tables at two 40 g rotis. Note: the app's default roti is the recipe's 45 g (`chapati`, 270 g / 6), which gives 428 kcal / 19 g; 402 / 18 is right only with the "2 × 40 g" caption, i.e. a person-corrected weight. | RIGHT as captioned |
| sherpa-onnx and espeak-ng named, copyleft acknowledged | `0005`. The English recogniser is NVIDIA's FastConformer (CC-BY-4.0), which wants naming wherever models are credited; not in the list of corrections. | RIGHT; check NVIDIA is on the line too |

## Two more rows, 18:45, from Meera's end-to-end trace (`0019` addendum 7)

Appended, not merged into the tables above: the rule for this file is that a correction is
added with its date, never written over what was there.

| deck says | repository says | verdict |
| --- | --- | --- |
| Page 5, "Say it: In the language you chose, out loud", and "answer a nutrition question in your own language" | Traced end to end by Meera: the lead-in, the rules engine's sentences and the figures come from `res/values/strings.xml` through the app's UI locale, which the run of show sets to English, and `values-hi` is empty by design; the model is told "Language to reply in: hi" and replies in English anyway, seven of seven on the desktop with the phone's exact GGUF, greedy, including three questions asked in Devanagari (`logs/meera-hindi-reply.log`); the phone has only ever been run with `en-IN`. | **FIX FIRST. UNSUPPORTED**, and it was marked RIGHT in the first pass above, which was wrong: I checked the string tables and the guard, not the language of the reply. Say instead (Meera's wording, supportable today): "You speak in Hindi, Telugu or English; the app answers in English today, on screen and aloud, and the interface follows your language setting." A judge who asks "can it answer in Hindi?" hears "not this build; the recogniser is per-language, the answers are English". |
| Page 5, "The model may never: state a number it was not handed, name a condition you have not declared ... Those paths are code, and code is testable" | The guards exist and are tested on the JVM (`NumericGuard`, `SafetyLine`, the template digit test). But the desktop model produced BOTH forbidden shapes with the prompts as written (Meera, 18:28: a number not given, and a condition verdict), and whether the guards catch them ON THE PHONE, in the demo's own ANSWER turns, is a measurement that does not exist yet: it is Rao's device run of the ten sentences, read by a person. | **UNVERIFIED on the device** as of 18:45. The sentence is a design claim with JVM tests behind it, not yet a device claim. Keep the wording, but do not present it as measured until that run is read; and the run of show's Beat 2 line ("there is a test for that") stays true only of the JVM test. |

## Correction to the hard-problem-01 row, from the demo-condition log (20:45)

The row above for "when someone says 'some rice', the app asks what some means instead of
inventing a figure" says the extraction leaves an unstated quantity null and the app never
invents one. The device log says otherwise for the demo sentence: `e2e-demo-condition-20sep.txt`
row #1, "I had two rotis and a little dal." extracted as `dal=1.0 none`. The model wrote the 1.
`LookupMealResolver.weigh` then took a quantity with no unit on an authored dish as one piece and
marked it QUANTITY_STATED (the COMPOSED_DISH guard skips QUANTITY_INFERRED when a quantity is
present), so the plate reads one serving at full confidence. **Verdict changes to UNSUPPORTED
for "some / a little" as of this log**: the figure was invented upstream and not marked. Nothing
in the app asks. Sent to Rao and Priya at 20:45; the run of show carries both outcomes for Beat 1
until Tuesday's rehearsal. The row above is not edited.

## Three numbers from the demo-condition run, verified against the log (20:15)

Asked for at `ea75984`. Checked against the device report itself, not Rao's summary of it:
`logs/e2e-demo-condition-20sep.txt` in his worktree, header `end to end on RMX3780,
2026-09-20T19:40:00, 8 threads`, and its driver `demo-window-20sep.txt`: airplane mode on,
Wi-Fi disabled, USB-powered (so charging), battery 32%, **thermal status 0 at the start of the
four-turn test and 3 by its end**, so the ten-sentence run that followed ran throttled.

| claim | the log says | verdict |
| --- | --- | --- |
| 0.55 s to first figure on ANSWER | ANSWER "did I get enough iron this week": `548 ms OWN FIGURES on screen (4 lines)`. In the ten-sentence sequence the same event read 562 and 526 ms. | RIGHT, with the words that make it true: the figures on screen at 0.55 s are the PERSON'S OWN diary lines, put up before the model runs. The model's answer arrived at 9.17 s. "First figure" must not be read as "the answer". |
| 9.2 s to the spoken sentence | Same turn: `9172 ms ANSWERED` (text "No, you did not get enough iron this week."), `9174 ms SPEAKING`; prompt 380 tokens at 54.4 tok/s, 11 generated. In the ten-sentence sequence, warmer, the two ANSWER rows read 10.3 s and 10.7 s. | RIGHT as one measurement; say "about 9 to 11 s" or quote the run. Ten-sentence LOG rows for the record: first figure 8.3 to 28.7 s, spoken 15.2 to 39.3 s; the deck's own Beat 1 sentence (#2, three foods) was the slowest at 28.7 / 39.3 s under thermal 3. |
| 0.47 s for AdviseOnMeal after the report, no model call | `ADVISE ON MEAL 1 again (after the report; expected instant, with the referral)`: `474 ms ADVICE`, `481 ms SPEAKING`; its "last model call" line repeats the save's call unchanged (prompt 371 tok / 6890 ms), so no new call. **This is the read-back of advice that the SAVE LAB REPORT turn had just regenerated**: that save took `9328 ms ... regenerated meal 1`, with the model call inside it. The FIRST AdviseOnMeal in the same log, before any report, was `9242 ms` with a model call: the digest mismatch `ea75984` then fixed. | RIGHT for what it is: the post-report read-back, measured PRE-FIX, on a path the fix did not change. What is NOT yet measured on the device: the first AdviseOnMeal being instant after `ea75984`. So "0.47 s after the report" is claimable with the caveat that the report's save costs 9.3 s (a model call, hidden inside the save); "instant on the first tap" is not claimable until the post-fix run. |

## What to do, in order

1. Fix the FIX FIRST rows: the "cold" 2.6 s; the 10.6 s wording; "252 tests green on every
   commit"; "48 strings translated"; the three-languages-in-one-sentence claims on pages 6
   and 7; the page-4 sentence that asserts a food helps a clinical value; the `[Name]`
   placeholders.
2. Refresh the counts: 93, 626, 52.
3. Put sherpa-onnx, the NVIDIA English recogniser and espeak-ng on the stack line, or drop
   "every dependency disclosed".
4. Replace the page-3 mock figures with the database's, or with a real screenshot.
5. Remove or defer the SENSORS tile and the calibration and dish-variant paragraphs, or mark
   them as designed and not built.
6. Decide the "Not built yet" box with Rao after the clean run.
