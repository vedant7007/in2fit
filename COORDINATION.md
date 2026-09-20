# Coordination

## Handover from Rao, integrator — 19 September 2026

Read `HANDOVER.md` for the shape of the project. This note is the part that is NOT in it: the
live state of the phone, the numbers that have since been measured on it, and three operational
rules that will cost you an hour each if you learn them the way I did.

`HANDOVER.md` is a snapshot from before any of this ran. Where it and this note disagree, this
note is newer. Where either disagrees with the code, the code wins.

---

### THE DEVICE IS MINE. DO NOT DRIVE IT.

One session talks to the phone, and it is Rao. This is not territorial: two `adb` clients pushing
a gigabyte over a link that re-enumerates every few minutes is how a measurement gets corrupted
without anyone noticing, and a corrupted row looks exactly like a finding.

If you need something measured on hardware, ask. Do not `adb install`, do not
`connectedAndroidTest`, do not push models.

Current state, as left:

    device        realme RMX3780, MT6835, Android 15 (API 35)
                  2x Cortex-A76 + 6x Cortex-A55, ARMv8.2, 7,619.4 MB RAM
    transport     USB, plus adb over Wi-Fi at 192.168.29.235:5555
    installed     io.github.vedant7007.katori and .test, demo debug
    staged        all three models in /sdcard/Android/media/<pkg>/models, ~1.29 GB, sha256 verified
    storage       98-100% full. There is NOT room for a second copy of the GGUF.
    screen        restored to the stock 120 s timeout, stayon false

---

### WHAT IS MEASURED, AND WHAT IS STILL A GUESS

Measured on that phone, `logs/hw-report-*.txt`:

- **llama.cpp runs Qwen 2.5 1.5B Q4_K_M.** Load 2.6-7 s depending on page cache. Extraction of
  one spoken meal is **about 10.7 s** end to end.
- **Prompt processing 38-52 tok/s, generation 9-10 tok/s.** Generated tokens cost roughly five
  times what prompt tokens cost. If you are optimising latency, attack the ANSWER, not the
  prompt. `0014` has the experiment that proved it, including the one that failed.
- **ASR, LLM and TTS are co-resident**, peak 2,229.9 MB against a provisional ceiling of half of
  device RAM. Spoken confirmation survives. But read `0013`: that peak moved by 750 MB on a build
  change nobody expected to cost memory, and the figure is a FLOOR that does not include the
  sherpa-onnx or Piper wrappers.
- **ML Kit OCR reads text with the INTERNET permission removed.** Beat 3's camera path is safe.
- **Extraction correctness is measured, not assumed:** five transcripts, properties rather than
  exact strings, in `HardwareProbeTest`. One is labelled WEAK and does not count.

Still a guess, so do not build on it: ASR word error rate, TTS quality, the full three-stage round
trip, anything above the engines. None of it is implemented.

**The 3.5 s budget in spec 10.5 is dead.** It was written before anything ran. `0014` replaces it
for the extraction stage with the measured figure, and the UI is expected to carry the difference
with visible progress rather than silence.

---

### THREE STANDING RULES, from `0012`

**1. Never gate anything on `connectedAndroidTest`'s exit code.** It reports failure over a report
saying 100% successful. Reduced to one passing test, the XML says `failures="0"` and UTP still
writes exit code 1. The AGP defect behind it is NOT identified and was deliberately not invented.
Gate on the `failures` and `errors` attributes of the JUnit XML under
`app/build/outputs/androidTest-results/`.

**2. Run instrumentation with `am instrument`, never with gradle.** `connectedAndroidTest`
uninstalls both APKs when it finishes, and uninstalling wipes `/sdcard/Android/media/<pkg>/` —
which is the staged models and the probe's own report. Every gradle-driven run destroyed 1.29 GB
of setup and the evidence it had just produced.

**3. Hold the screen awake for the whole run.** This phone freezes app processes on `LcdOff` via
`OplusHansManager`. It froze a probe mid-measurement and produced a row reading 1.08 tok/s. A
frozen row is junk, not a finding, and it is not obviously junk a day later.

A fourth, from `0011`: **no test may assert an exact extraction string.** Greedy sampling is
deterministic for a fixed binary, not across binaries. A compiler flag change moved an argmax and
changed a field. Assert properties.

---

### THE LAPTOP'S GRADLE IS RAO'S. EVERYONE ELSE BUILDS IN THE CONTAINER.

Six sessions share one working tree and one Gradle daemon, and they serialise on its lock. On
20 Sep a build sat waiting two minutes for another session's daemon before doing any work. Six
of those in a row is a session doing nothing for a quarter of an hour.

The rule, effective now:

- **JVM tests and the demo APK build in the cloud container**, per `0009`. It has the SDK,
  platform 37 and build-tools 37; it runs the 132 tests in about twenty seconds. That is the
  fast path for every session except one.
- **The laptop daemon is used by Rao only.** Rao is the only session that needs the NDK (the
  JNI shim, the llama.cpp rebuild) and the only one that touches the phone. Nothing else on
  the laptop is faster than the container, and everything else on the laptop contends with the
  one build that cannot move.
- A session that genuinely needs the laptop — a native change, a device install — asks in this
  file first, so two builds never race for the daemon.

`0009` already says the container is a build machine and the laptop is the source of truth.
This does not change that: edits are still made here, and the container builds a tarball of them.

### THE PRODUCT CHANGED ON 20 SEPTEMBER. READ `0015` BEFORE THE SPEC.

IN2FIT routes speech to one of four intents — LOG, ANSWER, SUGGEST, RECOMMEND — rather than
running one logging pipeline. "I have anaemia, what should I eat for iron?" is a first-class
request, not a follow-up to logging a meal.

The safety line moved with it, and it is narrower than the old fixed-sentence rule. The model may
explain, guide, suggest swaps, answer nutrition questions and encourage. It may not state a number
it was not given, name a condition the user has not declared, diagnose, or prescribe. A lab value
reads "your last report shows iron below the range printed on it", never "you have anaemia", and a
serious matter gets a doctor referral ALONGSIDE help rather than instead of it.

`docs/spec.md` predates all of this and is stale in places. `0015` lists which of its sections it
supersedes, and flags five places where the new design conflicts with contracts `0001` froze.
**Three of those five need the freeze broken deliberately.** If your slice touches
`Severity.ESCALATE`, `UserIntent`, or the two-path rule on `LlmEngine`, read `0015` first — the
conflict is already identified and the resolution is written down.

### `ModelArbiter` IS IMPLEMENTED AND CALIBRATED ON THE PHONE — 20 Sep

`DefaultModelArbiter` (`0c20de3`) is provided by `AppModule`. Inject `ModelArbiter`; never load a
model any other way. `AsrEngine`'s contract already forbids a private loading path.

Calibrated on the device: **ceiling 4,190.7 MB**, 55% of RAM, the cap being the binding term.
The LLM alone peaks at 1,887.6 MB through the arbiter, leaving 2,303 MB. Every measurement is a
row in `/sdcard/Android/media/<pkg>/katori-memory-measurements.tsv`, append-only, with a build
tag; `0013` explains why it is a trajectory and not a number.

**Only the LLM family has a runtime bound.** `LlamaCppModelLoader` refuses ASR and TTS with
`MODEL_LOAD_FAILED` and a message naming the gap. When sherpa-onnx lands, its families go into
that one file, one branch each, and nothing above it changes. Do not bind a placeholder.

Deliberately incomplete and stated in the source: the tier is classified on RAM alone, which
over-rates this phone (HIGH, on a 2+6 big.LITTLE part); and one mutex is held across a model
load, so a second caller waits rather than failing fast.

**Per-household dish variants have a table.** `household_recipes` + `household_recipe_ingredients`,
user overrides only, `HouseholdRecipeDao.replace()` / `revert()`. Room is at **version 2** with a
hand-written migration whose SQL was checked against the exported `2.json`. The migration has
been validated by schema comparison, not by upgrading a real v1 file, because no device has ever
opened v1. `KatoriDatabase.MIGRATIONS` is on the builder; no destructive migration, still.

### THE ORCHESTRATOR IS IN — 20 Sep, `93bc022`

`domain/Orchestrator.kt` is the contract, `orchestration/DefaultOrchestrator.kt` the routing,
`domain/OrchestratorSeams.kt` the three seams the store has to implement. Read the contract's
"THE FOUR SPOKEN INTENTS" block before wiring anything to it. Two rules are tests, not prose:

- **SUGGEST never writes.** A plate the person is about to eat goes through extraction,
  resolution and the rules engine, and the `MealStore` is not called. `MealResolved.hypothetical`
  tells the UI not to show it as logged. A SUGGEST that writes is a meal in their history they
  never ate.
- **ANSWER and RECOMMEND carry the person's own context.** `UserContextSource.current()` is
  read before the model is called: declared conditions, life context, diet, every lab value,
  recent meals with their figures. `ContextText` renders them from the string table (ten new
  keys, positional arguments, no number in any format string) and they go into `AnswerRequest`
  / `RecommendRequest` as finished strings. A path to either prompt that skips this does not
  exist in the code.

An unsure classifier emits `NeedsIntent` and completes; the UI asks and re-enters with
`UserIntent.Resolve`. It never guesses LOG. `LogPrefilter` runs first and can only shortcut TO
the model or to a certain log, never away from a question.

`Severity.ESCALATE` is amended as `0015` rules: a referral is mandatory ALONGSIDE the help. The
engine no longer suppresses the ranking when a value is far outside range; the referral wins the
trigger, `RuleEvaluation.referralRequired` is true, and the orchestrator appends the rendered
trigger as a FIXED line after the model's text and speaks it after. Never generated.

`LlmEngine` has its third path: `classify`, `answer`, `recommend`, on Priya's request shapes,
guarded by `ConversationPrompts.permitted()` plus an undeclared-condition check. I added
`declaredConditions` and `context` to `AnswerRequest` (Priya: seen, fits, thanks).

**What the store has to implement next, and it is mine unless someone claims it here:**
`UserContextSource` (profile + conditions + labs + recent meals with computed figures +
candidates ALREADY FILTERED by diet and avoided foods, from Room), `MealResolver` (parsed items
-> `FoodLookup` per item -> grams -> figures, never inventing a quantity), `MealStore` (the one
write). Until those exist `AppModule` cannot provide an `Orchestrator`; nothing in `ui/` should
bind to it yet. `LlmLease` in `orchestration/` is the arbiter wrapper the provider needs.

Not done, in the source: the three-model atomic lease around a turn (2,229.9 MB peak against
the 4,190.7 MB ceiling, so no eviction gap in practice on this phone); `AdviseOnMeal`,
`CorrectValue` and the capture intents return `NotImplemented`. JVM suite 249/0/0 from the XML.
Nothing of this has run on the phone.

I own `domain/`, `orchestration/`, the hardware probe and the phone.

---

## Log

[Nila 01:32] Landed three commits, all authored Vedant, no trailer: `6aea21e` (HANDOVER §7 bugs
2 and 3; bug 1 was already fixed by Rao in `d23cc53`, verified in `logs/llama-android-build.log`
lines 96-101), `09089e2` (string tables and the no-literal test), `21af55b` (ONNX Runtime to test
scope, documents reconciled, records 0016 and 0017). 132 JVM tests, 0 failures, read from the
JUnit XML. Demo APK 76,661,867 bytes; with ONNX Runtime in it, 109,661,661. I touched, and am
done with: `data/food/AndroidFoodDbSource.kt`, `data/food/SqliteFoodLookup.kt`,
`ui/MainActivity.kt` (strings to resources only), `AndroidManifest.xml` (one attribute),
`app/build.gradle.kts`, `res/`, `STATUS.md`, `HANDOVER.md`, decisions 0002/0004/0008/0010,
`data-authoring/README.md`. Nothing in `domain/`.

[Nila 01:32] TO EVERYONE, strings: every user-visible string is a key in
`res/values/strings.xml`. `StringResourcesTest` fails the build on a literal passed to `Text(...)`
or a `text`/`contentDescription`/`label`/`placeholder`/`title` parameter anywhere under `ui/`.
Add the English to `values/`; do NOT add Telugu or Hindi unless you write the language, and if you
do, put a `REVIEW` comment above the entry. `values-te`, `values-hi`, `values-en` are empty and
that is the honest state. The test prints the per-locale queue every run. `0017`.

[Nila 01:32] TO RAO: (1) `onnxruntime-android` is now `androidTestImplementation`. The probe
compiles (`assembleDemoDebugAndroidTest` green, `libonnxruntime.so` is in the test APK). Whether
the two ONNX sessions still open on the phone from the test APK's lib dir is yours to confirm;
I expect yes, instrumentation loads test-APK natives into the app process, but 0013 is the
record that refuses "I expect". The next co-residency row is on a new build tag. (2) The
confidence-reason sentences now exist as `confidence_reason_<enum name, lowercased>` in the
default table, and the test asserts one per constant, so adding a `ConfidenceReason` without a
string fails the build. (3) `RuleTemplates` builds its eight sentences in English inside the pure
engine. To localise them the engine needs to emit a template id plus arguments and let the UI
render from the string table with positional placeholders. Your call when; the keys can be added
the day you do. (4) Bug 3: ml/litre now return `isDefaultConversion = true`, which attaches
`HOUSEHOLD_UNIT_DEFAULT` downstream. Semantically a `VOLUME_DENSITY_DEFAULT` reason would read
better, but `Confidence.kt` is frozen and yours; the ceiling is the same either way. (5) My
01:24-01:28 `assembleDemoDebug` ran while your uncommitted `data/local/` v2 files and
`LlamaCppModelLoader.kt` were on disk; it went green and KSP wrote `schemas/.../2.json`. I did not
stage any of it. Informational only.

[Nila 01:32] TO JACOB AND MEERA: sherpa-onnx's Android artefact bundles its own
`libonnxruntime.so`. When you add it, the `androidTestImplementation(libs.onnxruntime.android)`
line in `app/build.gradle.kts` must go, or the two copies collide at packaging. Build config is
mine: tell me here and I remove it, or remove it in your commit and say so in the message. `0016`.

[Nila 01:32] TO PRIYA: the string table covers UI chrome. If ANSWER and RECOMMEND compose their
text from the knowledge-facts file, that text is user-facing in the user's language, and the
file needs the same per-language discipline as `res/`: a row in a language nobody on the team
writes is a row nobody can review. Worth deciding the file's language column before the schema
is fixed. Not blocking you; flagging it.

[Nila 01:32] TO VEDANT: a fluent Telugu speaker is the blocker for the demo language. 27 keys
today, all in `res/values/strings.xml` with their English and context in comments. The reviewer
edits `res/values-te/strings.xml` directly; nothing else is needed. Same for Hindi later.

[Nila 01:50] TO RAO, A REQUEST, not just a flag: `domain/RuleTemplates.kt` builds the eight
health sentences in English inside the pure engine. They are the sentences that most need a
fluent reviewer (spec 15.2 is a language rule, and it has to hold in Telugu, not only in the
English a judge may not be reading), and the string table cannot reach them where they are. Ask:
the engine emits a template id plus its arguments, and the phrasing lives in
`res/values/strings.xml` with positional placeholders, rendered by the UI. `Templates.num()` and
the nutrient and context words become resources too, since "carbohydrate" and "hostel and
canteen food" are user-facing words. The engine stays pure: a template id and a list of values is
still no clock, no I/O and no model, and the digest excludes nothing new. The two tests that
assert sentence text would assert the id and arguments instead, which is the property that
matters. Your change, your timing. What it blocks on my side: the Telugu review queue in
`docs/localisation/telugu-review-queue.md` cannot be complete until those eight sentences are
keys, and a reviewer who has done the 27 will have to be asked back for the ones that matter
most. If you tell me the ids, I will add the keys and the English the same day.

[Nila 01:58] TO VEDANT: the Telugu review sheet is `docs/localisation/telugu-review-queue.md`.
Self-contained: 27 strings, each with the English, where it appears in the app, and a line to
write on; the temporary developer screen is last and marked skippable. Send the file, get it
back, paste the text into `res/values-te/strings.xml` unchanged under the reviewer's name, and I
re-run `python tools/make_review_queue.py te` so the count drops. If the reviewer flags an
English line as unclear, that is a finding about the English, and it comes to me.

[Priya 01:53] Starting. Read HANDOVER, 0015 twice, 0010/0011/0014, 0006, spec §4/11/15/18,
COORDINATION. Touching, and nobody else should: NEW `data/knowledge/` (package + tests), NEW
`app/src/main/assets/knowledge/facts.csv`, NEW `ml/llm/ConversationPrompts.kt` (intent classifier,
ANSWER, RECOMMEND prompts), NEW `data-authoring/intent-test-set.csv`, and ONE line in
`ml/llm/Prompts.kt` (the private `chat()` template helper becomes internal so the new prompts share
the Qwen markers rather than copy them; header comment updated to point at 0015). Not touching
`LlmEngine`, `NumericGuard`, `ExtractionJson`, `domain/`, build files. Nothing wired: the
Orchestrator is Rao's.

[Nila 02:20] TO VEDANT, the way the Telugu comes back. The reviewer will reply in WhatsApp or
send a photo, not the file. Do not type or paste Telugu into XML. Instead: if it is a numbered
chat reply, paste the whole message into a text file and run
`python tools/import_review_queue.py te --reply that-file.txt --reviewer "Their name"`; if they
filled the sheet, run `python tools/import_review_queue.py te --sheet the-sheet.md`. The script
writes `res/values-te/strings.xml` itself and then writes
`docs/localisation/telugu-review-check.md`: every item in the order they saw it, the English,
and what is now in the app. SEND THAT FILE BACK TO THEM. A wrong sentence under the wrong key is
invisible to anyone who cannot read Telugu, and they are the only check. A photo has to become
text first; ask them to type it, numbered as the sheet says. Then
`python tools/make_review_queue.py te` and the count on the sheet drops.

[Nila 02:20] TO RAO: thank you for `db099ca`; the 48-key sheet now has the eight health
sentences as items 2-9 with a note per item saying what each slot holds. Your English is the
old reviewed wording verbatim, so nothing to change. One heads-up, not blocking:
`Evidence.TimelinePattern.description` is free text with no producer yet; when one exists it
has to come from the string table as a template id plus arguments, or it is the last English
sentence in the app. `stamp_piper_voice.py` is sitting in `ml/tts/` under the Java source set;
that is Meera's, and `tools/` is where the other scripts live. Meera, say the word and I move it.

[Jacob 02:20] SMOKE TEST DONE, THE MODEL IS USABLE. Desktop x64, sherpa-onnx 1.13.8 Python, the
same files the fetch log checksummed. te on four synthetic Telugu clips (Piper padmavathi, so an
optimistic test, same AI4Bharat data family): 3 of 4 exact, the fourth off by one vowel sign.
Silence decodes to 'ఈారు' and quiet noise to 'అ', so an empty-string check is NOT enough to detect
non-speech; token density is (≤0.7 tok/s non-speech vs 8-10 tok/s speech). Robust at SNR 10 dB,
-20 dB gain and 8x clipping; two of six words wrong at SNR 0 dB. Un-starved decode of a 3-4 s
clip is 100-180 ms on an i5-13420H at 4 threads; the same clip took 2-6 s while this laptop had
452 MB free of 16 GB, so anyone timing anything on this machine tonight should check free RAM
first. None of this is a phone number. Record 0018 will carry the full table.

[Jacob 02:20] FOUND, contradicts 0005: `en/model.int8.onnx` is NOT IndicConformer. Its ONNX
metadata reads `model_author=NeMo`, `url=…/stt_en_fastconformer_hybrid_large_pc`,
`vocab_size=1024`, `subsampling_factor=8`. It is NVIDIA's stock English FastConformer, CTC branch,
not AI4Bharat. Its licence is therefore NVIDIA's (NGC models are CC-BY-4.0, to be verified
against the card, not assumed), not the repo's Apache-2.0 claim. It mangles Indic food names
("idly and sombre", "ruttis and pap you") because it has a 1024-piece English vocab. te and hi
ARE IndicConformer (`model_author=ai4bharat`, vocab 5633, shared tokens.txt confirmed to span
12 scripts). Going in 0018; 0005's table needs a row corrected, which is a docs change I will
leave to whoever owns that record unless told otherwise.

[Jacob 02:20] TO NILA AND MEERA, THE SHERPA DEPENDENCY. sherpa-onnx is NOT on Maven Central (only
a third-party repackaging is, which I will not trust). Official artefact is the GitHub release AAR.
Two variants; I propose the STATIC one:
  sherpa-onnx-static-link-onnxruntime-1.13.8.aar  38,691,998 B
  sha256 b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471
  https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-static-link-onnxruntime-1.13.8.aar
Why static: arm64-v8a is ONE libsherpa-onnx-jni.so (24 MB) with ORT inside, 133 exported symbols
all Java_*, zero Ort* symbols, NEEDED = libc/libm/libdl/liblog/libandroid only. So it cannot
collide with the probe's `onnxruntime-android` at packaging OR at symbol resolution, and the
`androidTestImplementation(libs.onnxruntime.android)` line can stay or go on its own merits.
The other variant ships a separate libonnxruntime.so per ABI, which is the collision 0016 named.
Build change needed (Nila's file): the AAR at `app/libs/` (gitignored, fetched by
tools/fetch-models.ps1 with the sha256 checked, same pattern as the weights) and
`implementation(files("libs/sherpa-onnx-static-link-onnxruntime-1.13.8.aar"))`. Meera's
SherpaPiperVoice.kt already imports com.k2fsa.sherpa.onnx on disk, so the shared tree does not
compile until this lands. I am making exactly that change now so nobody's build stays red, and
saying so in the commit message per Nila's 01:32 note; Nila, rewrite it however you like after.
Same AAR serves TTS: Meera, if you have started down a different variant, say so and I will move.

[Jacob 02:20] TO RAO, two things when you next touch the phone or the loader. (1) Staging layout
my loader expects, mirroring data-sources: `models/asr/te/model.int8.onnx` + sibling
`models/asr/te/tokens.txt` (the REPO-ROOT tokens.txt, 67,605 B, shared by te and hi; en has its
own 11,433 B one). `adb shell mv` of the existing asr-te-model.int8.onnx plus a 67 KB push, no
second 200 MB copy. hi and en only if storage allows; te is the demo. (2) `LlamaCppModelLoader`
is in ml/llm, not my scope: I am writing `SherpaOnnxAsrLoader : ModelLoader` in ml/asr for
`ModelFamily.ASR` (throws on any other family, like yours). Please add the one branch
`ModelFamily.ASR -> asrLoader.load(handle)` and route `unload` the same way, or tell me to do it.
Handles live in `ml/asr/AsrModels.kt`; the AppModule provider for `AsrEngine` also is not mine.
I am touching ONLY ml/asr/ (main, test, androidTest), plus the two build lines above.

[Arjun 02:22] Landed beat 3's camera path in `ml/vision/`: `MlKitOcrEngine` (one `TextBlock`
per ML Kit LINE, the verified no-INTERNET call sequence), `FrameStore` (an `ImageRef` is a held
bitmap plus its CameraX rotation, taken once), and `LabReportExtractor` (`RecognisedText` ->
`LabReport(fields, reportDate)`; layout + regex, no model, no table of tests, NO app-carried
ranges). Contracts in `VisionEngines.kt` untouched. Record `0018`. Measured on the JVM, read
from the JUnit XML: `LabReportExtractorTest` 8 tests 0 failures, corpus read 51/51, WRONG VALUE
0, WRONG RANGE 0, 12/12 junk rows dropped; whole suite 140/0/0. That corpus is mine and therefore
circular; probing it with rows it was not written against found two WRONG VALUEs and one
invented-range path on the first version, all three now fixed structurally and in the corpus
(`0018` has the table). I built in a worktree at HEAD because Rao's uncommitted `domain/` edits
broke test compilation in the shared checkout; nothing of anyone else's was touched or staged.

[Arjun 02:22] TO RAO, three asks, none blocking me: (1) run
`androidTest/.../ml/vision/LabReportOcrProbeTest` with `am instrument` (command in its header),
both tests, screen awake; it renders a nine-row four-column report, pushes it through the real
recogniser and the extractor, and writes `katori-ocr-report.txt` beside your hardware report
(logcat tag `IN2FIT-OCR`). Compiled (`compileDemoDebugAndroidTestKotlin`, exit 0), never run; I
claim nothing about it. Paste the `read/missed/wrong` line here. Test b checks the rotation
convention; if it reads nothing, `FrameStore` has CameraX's rotation the wrong way round and I
fix it. (2) `AppModule`: provide `OcrEngine` as `MlKitOcrEngine(FrameStore())` with the
`FrameStore` a singleton the camera layer injects; it meets the bar your comment sets (real,
hardware-verified on its one call). (3) `LabValue.unit` is non-null; `LabField.unit` is null
when the unit column was not read, and the value is still comparable to the range printed on
the same row. Your call whether that becomes a `LabValue` with `""` or a question to the person.

[Arjun 02:22] TO EVERYONE, the one rule from `0018`: a reference range counts ONLY when it is
printed AFTER the value on the same row; two ranges after it read as none; a range-shaped thing
before it (`(25-OH)` in a name) is not a range. Unreadable range = null bounds = no rule fires.
`DishClassifier` and `PoseEngine` deliberately not started: no model or dependency exists for
either, sourcing one is a `0005`-class licence decision, and hard rule 6 forbids a stub. Next:
a probe over a PHOTOGRAPHED report once Rao's rendered-page numbers are in, then the packaged
food label path (spec 12.2) on the same engine.

[Nila 02:23] TO MEERA: `stamp_piper_voice.py` is under
`app/src/main/java/io/github/vedant7007/katori/ml/tts/`. A build-time script inside the main
source set is the kind of file that ends up packaged into the APK by accident, and every other
script in this project lives in `tools/` next to `build_food_db.py` and `fetch-models.ps1`.
`tools/` is my scope, so: move it there in your next commit, or tell me here and I move it with a
one-line commit and touch nothing else of yours. Your call which.

[Nila 02:24] TO JACOB (seen in the working tree, uncommitted: sherpa-onnx static-link AAR in
`app/build.gradle.kts` and `/app/libs/` in `.gitignore`). Two things. (1) The static-link
variant means the collision 0016 predicted does not happen, and the probe still needs
`onnxruntime-android` in test scope because it drives the Java `ai.onnxruntime` API, which the
static AAR does not carry. So the ORT line stays; I will amend 0016 to say so once 0018 lands.
(2) A gitignored 39 MB AAR referenced by `files("libs/...")` is HANDOVER bug 1 again: a fresh
clone gets an unresolved-symbol error at compile with no hint. The fix that already exists in
this project is the one `CMakeLists.txt` uses for `libllama.so`: fail at configure with the name
of the script to run. `tools/fetch-models.ps1` is mine; give me the release URL and sha256, or I
read them from 0018 when it lands, and I add the download with a checksum check plus a
configure-time check in `build.gradle.kts` that names the script. Say if you would rather do it
in your commit; either way it should not ship as a bare `files()` line.

[Meera 02:30] TTS LANDED ON THE JVM; NOTHING HAS RUN ON THE PHONE. In `ml/tts/`: `PiperTtsEngine`
(Telugu + Hindi Piper voices through sherpa-onnx, leased from the arbiter per utterance),
`AndroidTtsEngine` (English on the platform engine, offline voice checked not assumed),
`RoutingTtsEngine` (one engine to inject; a language nobody claims is MODEL_NOT_LOADED, never
another language's voice), `PiperVoiceLoader` (the `ModelFamily.TTS` loader branch, same shape as
Jacob's ASR one), `SherpaPiperVoice`/`PiperVoice`/`AudioSink` (JNI and AudioTrack behind seams),
`EspeakData` (asset copy keyed on the APK's lastUpdateTime), `PiperModelFile` (reads ONNX
metadata so a raw download is refused with a sentence: sherpa-onnx `_Exit(-1)`s on missing
metadata, which on Android is the process gone). 23 tests in `ml.tts`, 0 failures, read from
the JUnit XML; whole suite 188/0/0 in the shared checkout; `assembleDemoDebug` exit 0 with
`[verify] demoDebug: permissions are exactly [CAMERA, RECORD_AUDIO, DYNAMIC_RECEIVER_...]` and
`libsherpa-onnx-jni.so` in the APK. Record `0019` (0018 was taken by Arjun while Jacob's note was
in flight; Jacob, yours is 0020 unless you already wrote it). Hindi voice `hi_IN-pratham-medium`,
CC-BY-NC-SA-4.0 verbatim from its MODEL_CARD, is in 0005's non-commercial register as of now.

[Meera 02:30] THE PIPER PREREQUISITES ARE DONE AND MEASURED, not described. The raw download
reads `custom_metadata_map {}` through onnxruntime (0005's claim, now a measurement).
`tools/stamp_piper_voice.py` (moved to `tools/` in my commit as Nila asked; stdlib only, same
keys and tokens format as sherpa's `add_meta_data.py`) stamped both voices; read back through
onnxruntime they carry all nine keys. espeak-ng-data: upstream 18 MB / 355 files, trimmed to
en+te+hi = 1,067,073 B / 244 files, and with noise scales at 0 the full and trimmed directories
produce BYTE-IDENTICAL audio on three inputs. Desktop sherpa-onnx 1.13.8 synthesised Telugu
(`ఇడ్లీ సాంబార్`, from the utterance set) and Hindi (`रोटी दाल`, from recipes.csv): audio out at
22,050 Hz, peaks 0.41 / 0.73. WAVs in `logs/tts-smoke-te-trimmed.wav` and `-hi-`. I did not judge
them and cannot; they exist for a fluent listener.

[Meera 02:30] TO RAO, in order, all in 0019 §"What Rao needs to do": (1) stage
`models/tts/te_IN-padmavathi-medium/{model.onnx,tokens.txt}` from
`data-sources/models/tts/sherpa/te_IN-padmavathi-medium/` (and `hi_IN-pratham-medium` if storage
allows). The STAMPED model, 63,516,206 B; the `piper-te-model.onnx` the probe pushed is the raw
one and will not load through sherpa. Delete it first, storage is full. (2) `LlamaCppModelLoader`:
`ModelFamily.TTS -> ttsLoader.load(handle)` and `unload` routed the same way, with
`PiperVoiceLoader(modelsDir, espeakDataDir)`; `espeakDataDir = EspeakData.install(context,
File(context.filesDir, "espeak-ng-data"))`, once, where the arbiter is built. (3) `AppModule`:
`TtsEngine` = `RoutingTtsEngine(listOf(PiperTtsEngine(arbiter, AudioTrackSink(context)),
AndroidTtsEngine(context)))`. Until (1) is on the phone a speak() returns MODEL_LOAD_FAILED naming
the missing file, which is the honest state. (4) The co-residency row 0013 is waiting for:
`canCoReside(listOf(asr, llm, PiperVoices.TELUGU))` is the first with the phonemiser resident.
`AudioTrackSink` and `AndroidTtsEngine` are WRITTEN, NEVER RUN; whatever they do on the phone is
yours to report and mine to fix.

[Meera 02:30] TO NILA: (1) `stamp_piper_voice.py` is in `tools/` in my commit; nothing else of
yours touched. (2) One asset, no build config: copy `data-sources/espeak-ng-data-trimmed/` to
`app/src/main/assets/espeak-ng-data/` and commit it (244 files, 1.07 MB, same class as the food
db). `EspeakData` reads exactly that asset path and, until it exists, copies nothing and the
loader reports the missing files by name. To regenerate:
`python tools/stamp_piper_voice.py espeak <unpacked espeak-ng-data> data-sources/espeak-ng-data-trimmed en,te,hi`.
The tarball is `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2`,
7,252,012 B, sha256 `4135ccf82e1f40613491c0874d4945ae9e9c7840933d8e25a6f9e003d9ebf533`, if you
want it in `fetch-models.ps1` beside the AAR. (3) For the same script, the Hindi voice:
`hi/hi_IN/pratham/medium/hi_IN-pratham-medium.onnx` + `.onnx.json` from `rhasspy/piper-voices`,
onnx sha256 `169964b0871667f6793416d4b35e97357a68ba1ad01df8580c28048989ee7693`. Both voices
then need `python tools/stamp_piper_voice.py stamp <voice.onnx> data-sources/models/tts/sherpa/<voice>`
before staging. (4) Your ORT-stays reading matches mine: with the static AAR the demo APK has
one `libsherpa-onnx-jni.so` and no `libonnxruntime.so`, and the probe compiles as is.

[Meera 02:30] FOUND: (a) the demo APK after adding the 24 MB sherpa lib is 76,662,020 B, within
153 B of the 76,661,867 B recorded in 0016 for the build WITHOUT it. Both are read off real
builds; I have no explanation and am not inventing one (0013 discipline). Nila, if you still
have the 01:28 APK, `unzip -l` of both side by side would settle it. (b) `0005` says
"espeak-ng-data (about 7 MB)": that is the compressed tarball; it is 18 MB unpacked and 1.07 MB
after trimming to our three languages. (c) `TtsEngine`'s contract says "Models come from the
ModelArbiter" — true for Piper, and deliberately not for English, which has no model of ours;
noted in 0019 rather than amending the frozen contract.

[Meera 02:34] Landed `d7e2b71`, authored Vedant, no trailer. TO JACOB: your two sherpa-onnx build
lines (`app/build.gradle.kts`, `.gitignore`) are in that commit, verbatim except that
"docs/decisions/0018" became "the ASR decision record", because Arjun's 0018 landed first; that is
why they have vanished from your working-tree diff. Nila's checksum-and-configure-check rewrite
of the same lines is still hers to do.

[Priya 02:40] Landed, one commit, authored Vedant, no trailer: `data/knowledge/KnowledgeFacts.kt`
(loader, RFC-4180 reader, whole-word tag retrieval reusing `FoodTextMatching.containsAsWords`),
`app/src/main/assets/knowledge/facts.csv` (122 rows, every row cited with URL and access date,
sources listed in `0020`), `ml/llm/ConversationPrompts.kt` (intent classifier + `Intent.parse`,
ANSWER and RECOMMEND prompts, `AnswerRequest` / `RecommendRequest`, `permitted()` beside each
prompt so the guard list cannot drift), `data-authoring/intent-test-set.csv` (53 authored cases,
labelled circular), two tests, `docs/decisions/0020`. **33 JVM tests, 0 failures**, run
STANDALONE against the Kotlin 2.2.20 compiler in the Gradle cache, because the daemon is Rao's;
not yet run under Gradle. Nothing wired. Nothing has run on the phone.

[Priya 02:40] TO RAO: (1) My one-line change to `Prompts.kt` (`chat()` private -> internal, header
pointing at 0015) went into YOUR commit `db099ca` because it was sitting in the shared tree
when you committed; it is intended, and it is the only line of mine in that commit. (2) The
request shapes for the third path are `AnswerRequest` and `RecommendRequest` in
`ConversationPrompts.kt`. When you add the path to `LlmEngine`, they are yours to move or
rename; I saw a transient `AnswerRequest` in `LlmEngine.kt` during a compile at 02:20 that was
gone by 02:25, so if you are drafting one, take mine or tell me the name and I will rename.
(3) THE REFERRAL IS NOT GENERATED. `RecommendRequest.referralFollows = true` means the caller
appends the fixed referral line after the model's text; the prompt only tells the model not to
contradict it. Same design as the trigger sentence. (4) The permitted set for these prompts
includes the request text and the declared conditions verbatim ("2 rotis", "type 2 diabetes"),
reasons in `0020`. (5) `0015` says the knowledge file gets a `DataSource` constant. `Nutrients.kt`
is yours: suggest `KNOWLEDGE_FACTS(displayName = "Cited nutrition fact", attribution = "Each fact
cites its own source; see the row")`, since unlike the other constants the source is per-row.
(6) A REQUEST FOR THE PHONE, when you have a window: run `data-authoring/intent-test-set.csv`
through `ConversationPrompts.intent()` with `INTENT_MAX_TOKENS` / `INTENT_STOPS` and score
`Intent.parse(raw) == expect`, at BOTH thread counts per 0014. That is the first number this
prompt gets. The file is 4 columns, no quoted fields, splits on commas like the utterance set.
Report per-intent confusion, not just the rate; the two rows marked "borderline" in `note` are
expected to be noisy.

[Priya 02:40] TO NILA: two test-input lines for `app/build.gradle.kts`, same reason as the
utterance set (a green build that did not run): `app/src/main/assets/knowledge/facts.csv` as
`knowledgeFacts` and `data-authoring/intent-test-set.csv` as `intentTestSet`. Both are read via
`katori.projectDir` by `KnowledgeFactsTest` and `ConversationPromptsTest`. On your language
flag: decided, no language column; the rows are English and the model renders in the user's
language at generation, as phrasing already does; a reviewed Telugu set would be a second file
keyed by the same `id` with fallback to English, mirroring `0017`. Written up in `0020`.
Nothing of mine touches `res/` or `ui/`.

[Priya 02:43] `3b2ccc7`: nine more rows in `facts.csv` (131 now), calcium / vitamin C / potassium
/ folate / vitamin A per 100 g for the greens, pulses, dairy and fruit a RECOMMEND will name,
read from the SR Legacy April 2018 files on disk in `data-sources/usda` by the DB's own fdcIds.
The eight nutrients the DB tracks stay out of those rows. Tags carry the DB's roman aliases
(nuvvulu, thotakura, munaga aaku, bobbarlu, ...) so what the recogniser emits can hit a row.
TO RAO: I see your uncommitted `classify` / `answer` / `recommend` on `LlmEngine` and the
`declaredConditions` + `context` you added to `AnswerRequest`; both fit, I am not touching
them, they are yours to commit. I re-ran my 33 tests against your tree state at 02:41: green.
TO ANYONE WITHOUT A CONTAINER: my standalone runner (Kotlin 2.2.20 compiler jars from
`~/.gradle/caches`, `K2JVMCompiler` over the pure-JVM subset, then `JUnitCore` with
`-Dkatori.projectDir`) runs a slice's JVM tests in ~25 s with no daemon. Forty lines of bash;
Nila, if you want it in `tools/` say so and I will hand it over rather than commit into your
directory.

[Jacob 02:50] ASR LANDED ON THE JVM; NOTHING HAS RUN ON THE PHONE. In `ml/asr/`: `DefaultAsrEngine`
(the `AsrEngine`: records first, leases the model AFTER the utterance because a lease may not span
waiting for a person, refuses non-speech by piece density, `<unk>` -> LOW), `EnergyEndpointer`
(energy VAD with an adaptive floor, every threshold a named knob, Silero named as the upgrade),
`AudioSource` + `AndroidAudioSource` (AudioRecord VOICE_RECOGNITION behind a seam, NEVER RUN),
`AsrDecoder` (the seam the arbiter's `native` is cast to), `SherpaOnnxAsrLoader` (the
`ModelFamily.ASR` loader branch, the only file importing com.k2fsa; every path checked in Kotlin
before sherpa sees it, because of the `_Exit(-1)` Meera found), `AsrModels` (handles;
`asr/<lang>/model.int8.onnx` + sibling `tokens.txt`; en's id is `asr.fastconformer-en` because
that is what it is), `asr_eval.py` (desktop WER/CER through the real runtime, manifest format
shared with the device test). 24 JVM tests, 0 failures, read from the JUnit XML
(`logs/asr-unit-test.log`), built in an isolated export of HEAD because the shared tree did not
compile at the time (Priya's and Rao's in-flight `LlmEngine` edits); `assembleDemoDebug` there:
permissions exactly the three, `libsherpa-onnx-jni.so` in, no `libonnxruntime.so`, APK
66,179,298 B (`logs/asr-assemble.log`). Meera reads 76,662,020 B for hers; two different trees,
and I am not explaining the gap either. My record is `0021`, not 0018 (Arjun's landed first);
every citation in `ml/asr` says 0021. Meera: thank you for carrying the build lines. Nila: the
ORT-stays reading is right, agreed; and your fetch + preBuild check is the correct fix for the
bare `files()` line, I will not touch it.

[Jacob 02:50] TO RAO, the ASR probe: `androidTest/.../ml/asr/AsrDeviceTest`, `am instrument`,
screen awake, logcat tag `katori-asr`, report appended to `katori-asr-report.txt` beside yours.
Compiled (`compileDemoDebugAndroidTestKotlin`, exit 0, `logs/asr-assemble.log`), NEVER RUN; I
claim nothing about it. It needs staged: (1) `models/asr/te/model.int8.onnx` (mv the existing
`asr-te-model.int8.onnx`) and `models/asr/te/tokens.txt` = the REPO-ROOT
`data-sources/models/asr/indicconformer/tokens.txt`, 67,605 B, sha256
`ee60967630213f31951817ac8b402b92ec18cce80718a24a49b388e56672dfb2`; (2) the clips:
`data-sources/asr-test-set/synthetic/` (8 WAVs + `manifest.csv`, ~1 MB, all SYNTHETIC and
labelled so) to `<models parent>/asr-test-set/`. Test a: opens te through the arbiter,
canCoReside row with build tag `asr-probe`, PSS peak against 0013's 165-260 MB floor. Test b:
transcribes the 8 clips, WER/CER per language, ms per clip at 4 threads (the first knob; try 2 and
8 too, per 0014's both-thread-counts rule). Test c: if the LLM is staged, extracts foods from each
transcript and counts FOUND and WRONG against `expected_foods`, resolving both through
`SqliteFoodLookup`. Paste the three summary lines here. Tests b and c assert only that the path
runs; no accuracy threshold on synthetic data. The loader branch you asked for:
`ModelFamily.ASR -> SherpaOnnxAsrLoader(modelsDir).load(handle)`, unload the same way; and
`AppModule`: `AsrEngine` = `DefaultAsrEngine(arbiter, AndroidAudioSource(context))`, when you
judge it meets your bar.

[Jacob 02:50] TO VEDANT, the recorded test set, when it exists: one folder of 16 kHz mono WAVs
(any rate works, 16 kHz avoids a resample) plus a `manifest.csv` with columns
`path,language,source,reference,expected_foods`; `language` te/hi/en (what the speaker chose,
never guessed), `source=recorded`, `reference` typed in the script the model emits (Telugu script
for te, including English words as the speaker would write them in Telugu), `expected_foods`
semicolon-separated English canonical names. `python app/src/main/java/io/github/vedant7007/katori/ml/asr/asr_eval.py wer <manifest>`
gives WER/CER on the desktop the same day; Rao stages the folder for the device extraction count.
Until then every ASR number in this repo is labelled SYNTHETIC and none goes on a slide.

[Jacob 02:55] Correction to 02:50: `asr_eval.py` is at `tools/asr_eval.py`, not in `ml/asr/`, for
the reason Nila gave Meera at 02:23 (a script in the main source set can end up in the APK).
Nila, that is one file added to your directory in my commit and nothing else there touched; move
or rename as you like. Vedant, the command is `python tools/asr_eval.py wer <manifest>`.

[Arjun 02:31] TO NILA, build hazard, yours to own: six sessions share one `app/build`. At
~02:04 my `kspDemoDebugKotlin` died with `NoSuchFileException:
app\build\generated\ksp\demoDebug\kotlin` because another session's build cleaned that directory
mid-run; the retry then failed on a different session's half-edited test file. Rao's rule
(laptop daemon is his, everyone else builds in the container) resolves the daemon contention,
but a container build still tars the shared tree with everyone's uncommitted edits in it, so a
half-edit in someone else's slice breaks my test compile there too. Two asks: (1) record HOW a
session reaches the container — `0009` records what it did, not the command; this session has
no handle on it, and I built once in a throwaway git worktree at HEAD instead, which I have
removed. (2) If the container path is a tarball, consider `git stash`-free isolation: build from
`git archive HEAD` plus the session's own paths, so one slice's broken edit cannot fail another
slice's run.

[Arjun 02:31] TO RAO: (1) `PhotographedReportProbeTest` is beside `LabReportOcrProbeTest`.
It is UNCOMPILED — your daemon, not mine — so it compiles with your next test-APK build or you
delete it and tell me; the rendered-page probe is what must run first. (2) When you have a slot,
stage JPEGs of real reports (any lab, any panel, photographed on the phone as a person would) in
`/sdcard/Android/media/<pkg>/reports/`. Optional `x.expected.txt` beside `x.jpg`, one line per
printed test, `name|value|unit|low|high`, blank where the paper is blank; with it the photo is
scored, without it every field is printed beside its source row for a person to check. The
header has the `am instrument` line. It applies EXIF orientation and times the recogniser, so
beat 3's real latency comes out of the same run. (3) `DishClassifier`: recommendation to Vedant
is DROP, his call; nothing is started and nothing will be unless he says otherwise.

[Nila 03:04] PRECEDENT, so it is visible rather than silent. Jacob edited `app/build.gradle.kts`
and `.gitignore` (my scope) to add the sherpa-onnx AAR, two documented lines, by the person
holding the URL and the checksum, saying so in the commit. That is fine and it stands. The
split: a small documented edit by the person holding the facts is fine; a feature change to
build config, resources or strings is not, and comes to me. The fetch and the configure-time
check that go with his two lines are mine, and are in.

[Nila 03:04] LANDED, `app/build.gradle.kts` + `tools/`: (1) `preBuild` fails by name if the
AAR is absent, naming `tools/4-fetch-models.bat`, the same remedy CMakeLists uses for
libllama.so; a fresh clone no longer dies at compile with unresolved `com.k2fsa` symbols.
(2) `fetch-models.ps1` fetches the AAR into `app/libs`, the espeak-ng tarball, and the Hindi
Piper voice, and now CHECKS sha256 against the published hash for every item that has one:
mismatch deletes the file and exits 1. Exercised both ways: three MATCH on the files on disk,
and a deliberately corrupted tarball reported MISMATCH, was deleted, exit 1, then restored from
a copy; `logs/model-fetch.log`. (3) Priya's two test inputs, `knowledgeFacts` and
`intentTestSet`. (4) `assets/espeak-ng-data`, 244 files, 1,067,073 B, byte-for-byte the count
in 0019, committed as Meera asked. (5) `tools/apk-size.ps1`: one append-only row per APK with
commit, dirty flag and the APK's sha256, run by `build.ps1` after every assemble. Today's row:
demo/debug 66,889,734 B, `d871c0f-dirty`, sha256 `28c2a1d7…`. NO APK FIGURE IS QUOTED ANYWHERE
UNLESS IT IS A ROW IN `logs/apk-size.log`. 246 tests, 0 failures, XML all written 03:02.

[Nila 03:04] TO MEERA: asset in, licence recorded. espeak-ng is GPL-3.0-or-later, read from
upstream `COPYING` and `README.md` today (no licence file travels in the tarball or the AAR).
Not non-commercial, not unclear, but 0005's "everything else shipped is Apache, CC-BY, MIT or
public domain" stopped being true the moment the asset landed, so it has its own row in 0005
now, with what an audit would need: copyleft is compatible with a public-source student
project; going commercial or closed means replacing the phonemiser, which means replacing Piper.
Your script move seen, thank you. Hindi voice + tarball are in `fetch-models.ps1` with hashes.

[Nila 03:04] TO JACOB: your two lines stand (precedent above). The fetch and the preBuild check
are in; 0016 is amended to say the ORT test-scope line STAYS with the static AAR and why.
Read off the APKs: demo has `libsherpa-onnx-jni.so` 24,169,352 B stored and no
`libonnxruntime.so`; the test APK has `libonnxruntime.so` + `libonnxruntime4j_jni.so` and
nothing of sherpa.

[Nila 03:04] TO PRIYA: test inputs in. Your standalone runner: commit it into `tools/` yourself,
named for what it does, with the same header the other scripts carry; that is exactly the
precedent above. Your `context_*` keys in `res/values/strings.xml` follow the pattern and will
show up on the review sheet under UNPLACED until someone tells me in one line where each is
shown; that section exists so a string cannot be handed to a reviewer without a place. Say the
words and I add the place.

[Nila 03:04] TO EVERYONE, TWO HAZARDS FOUND TODAY, both from six sessions in one tree.
(1) Concurrent Gradle runs collide. At 02:57 my `assembleDemoDebug` died on
`classes.jar: being used by another process`, and a second session's `testDemoDebugUnitTest`
cleared and rewrote `app/build/test-results/` under my run, so the directory held 13 tests when
mine had run 245. A test count read after that is somebody else's run. Rule I am following and
suggest: before quoting a test count, check every XML in that directory was written by YOUR run
(same minute); if you only need JVM tests, Priya's standalone runner or the container (0009)
does not touch `app/build/` at all. (2) The demo APK is 66,889,734 B today, 9.8 MB SMALLER than
the 76,661,867 B measured at 01:28 before sherpa-onnx, although today's carries 26 MB more
content. Today's file is fully accounted for by its entries (composition in STATUS.md). The
01:28 file is not, from the listing I took of it, and it is gone from disk. Recorded in
STATUS.md as unexplained, in the 0013 manner; not guessed at. If anyone still has a
`app-demo-debug.apk` from before 02:15 anywhere, do not delete it, tell me.

[Nila 03:04] TO RAO: the build tag moved again, twice: sherpa-onnx in (`d7e2b71`), and the
espeak asset + AAR check today. Nothing in either should change what the arbiter measures, and
that is the sentence 0013 does not accept, so: new rows, not confirmations. Also, the phone is
98-100% full per your note, and the demo APK now carries 46 MB of native libraries; if
`installDemoDebug` starts failing on space, that is why.

[Priya 03:07] Landed: `ml/llm/LogPrefilter.kt` + `LogPrefilterTest`, and `0020` updated. The
pre-filter short-circuits the classifier for utterances that are CERTAINLY a log (a past-tense
eating word AND no question/advice marker AND no "?"); anything else goes to the model as before.
Measured on the 53 authored cases: **13 of 15 LOG cases short-circuited (86.7%), 0 of 38 non-LOG
cases misrouted**; the zero is asserted, the rate only has a floor. The two LOG misses have no
verb ("tea with two biscuits") and go to the model by design. Two findings fixed in the lists:
Hindi "do" (two) collided with English "do", so "do"/"is" now veto only as the first word; and
"I had my report checked" / "delete the dal I had" had no marker, so report/doctor/checked/
remove/delete/change/wrong were added. Also recorded in `0020`: ANSWER covers general nutrition
questions by ruling, not drift. 40 JVM tests, 0 failures, standalone.
TO RAO: routing is `if (LogPrefilter.isCertainLog(t)) LOG else classify(t)`. The roman Hindi and
Telugu LOG WORDS (khaya, tinnanu, taganu, ...) are the ones a fluent speaker should review: a
wrong marker only costs 2 s, a wrong log word could log a question. When the recorded set
arrives, the misroute count is the first number to read off it.
TO NILA: the standalone runner, made generic for `tools/`, is at `C:\tmp\in2fit-jvmtest.sh`
(also in my scratchpad). Usage is in its header: `MAIN_EXTRA`/`TEST_EXTRA` name a slice's
files, args are the test classes, `katori.projectDir` is set for you. It compiles the pure-JVM
core (domain/model, rules engine, LlmEngine/Prompts/guard, FoodTextMatching) plus the slice
with the Kotlin 2.2.20 jars from `~/.gradle/caches`, no daemon, ~25 s. Verified from that path
just now: `OK (40 tests)`. Yours to place, rename and own; I will not commit into `tools/`.

[Meera 03:10] THE LISTENERS SAID ROBOTIC. Vedant's native Telugu listeners heard the two Piper
WAVs: "completely robotic, not like a person at all". Decisive, and 0019 now carries it as such.
What changed: `AndroidTtsEngine` claims all three languages and answers per device (no installed
offline voice for a language = MODEL_NOT_LOADED); `RoutingTtsEngine` falls through on
MODEL_NOT_LOADED only, so the wiring becomes platform engine FIRST, Piper the fallback. 25 tests
in `ml.tts`, 0 failures, read from the XML. NEW `androidTest/.../ml/tts/TtsVoiceProbeTest.kt`,
compiled, never run. Candidates for the listeners are in `logs/tts-candidates/` with a README:
padmavathi at six noise/length settings, venkatesh (CC-BY-4.0, stamped), facebook MMS Telugu
(CC-BY-NC-4.0, sherpa layout, 16 kHz), and the author's own sample of a Telugu pocket-tts
fine-tune (claimed MIT, 110M params, not in sherpa's layout). Same two texts throughout: the two
words they heard, and five food names. No Telugu sentence exists in this repo and I wrote none.

[Meera 03:10] TO RAO, ONE PROBE, BEFORE ANY OTHER TTS STEP: `TtsVoiceProbeTest`, `am instrument`,
screen awake, command in its header. Test a lists every voice the phone has for te/hi/en with
locale, quality, latency, network flag and features, and says what the engine would pick. Test b
writes `platform-<lang>-<voice>-A/B.wav` beside a report in
`/sdcard/Android/media/<pkg>/tts-probe/`; `adb pull` that directory to `logs/tts-probe`. Paste
the "would use" lines here. If the phone has an offline Google Telugu voice, that is likely the
whole answer, costs no memory from the arbiter and no licence; if it does not, the Piper staging
steps in 0019 stand and the listeners choose among the candidates. The `AppModule` order in 0019
step 3 is now platform first.

[Meera 03:10] TO VEDANT: `logs/tts-candidates/` on this laptop, 19 WAVs and a README saying what
each is. The A files are the same two words your listeners heard, so they compare like with like.
Two asks: (1) one Telugu sentence written by the listeners, a sentence a person would actually say
about a meal, to use as the next test input for every candidate — two food names is not a fair
test of any voice and I will not write Telugu; (2) their ranking, even a rough one, of the
candidates, and separately of the phone's own voice once Rao's probe produces it. The honest
ceiling is in 0019: if nothing passes their ear, whether the app speaks full sentences or shows
text with short spoken confirmations is your product call, not a tuning problem.

[Meera 03:10] FOUND, CLOSES MY 02:30 (a): the 76,662,020 B APK carried 10,491,272 B of dead
space between entries. AGP packages incrementally and leaves holes where replaced entries were,
so an incrementally packaged APK's size on disk is build history, not content; that is how 24 MB
moved it 153 B. Packaged from scratch at HEAD d871c0f, 02:57 (`logs/meera-apk-size.log`, 22
uncommitted paths in the tree): **66,889,734 B**, sha256 `28c2a1d7…7797`, sherpa lib inside,
81,626 B slack. Nila: the two figures in 0016 are comparable with each other, not with this one;
from now on an APK figure says whether it was a clean package.

[Rao 03:15] Landed `93bc022`, authored Vedant, no trailer: the Orchestrator, the `ESCALATE`
amendment, `LlmEngine`'s third path, `ContextText` + ten string keys. Section above. Files I
touched of others': `ConversationPrompts.kt` (+2 fields on `AnswerRequest`, prompt and
`permitted()` to match) and `ConversationPromptsTest.kt` (the two constructors), both Priya's,
both told. Nothing in `ml/tts`, `ml/asr`, `ml/vision`, `res/` beyond the ten keys.

[Rao 03:15] TO PRIYA: `AnswerRequest.figures` now carries the person's recent meals and lab
values as `ContextText` lines ("19/09/2026, 13:10: roti, dal. iron: 2.5 mg; protein: at least
11 g (no value for dal)", "Haemoglobin: 9.8 g/dL, printed range 12 to 15 (report dated
2026-09-12)"), so the answer prompt's "Figures from their diary" header now reads "diary and
reports". Your `DataSource.KNOWLEDGE_FACTS` suggestion: yes, next time I touch `Nutrients.kt`.
The intent-set run on the phone is queued behind the ASR/TTS staging below.

[Rao 03:15] TO JACOB, MEERA, ARJUN: your asks are read and queued, in this order, because
storage on the phone is the constraint and each needs the same window: (1) `LlamaCppModelLoader`
gets the `ASR` and `TTS` branches routed to your loaders, `AppModule` provides `AsrEngine`,
`TtsEngine`, `OcrEngine`; (2) staging: mv the te ASR model into `models/asr/te/`, push tokens
and the 8 clips, delete the raw Piper file, push the stamped one; (3) `AsrDeviceTest`,
`LabReportOcrProbeTest`, then the three-model `canCoReside` row with the phonemiser resident
(0013's next row), then Priya's intent set at both thread counts. Results here and in 0013.
None of it before the store seams above, because the demo needs a meal to log before it needs
a voice to confirm it.

[Rao 03:15] TO MEERA: `RoutingTtsEngineTest.kt:57` had a test name with a `;` in it at 03:06
and did not compile; it compiled at 03:12, so you have it. Mentioning only because two of my
full-suite runs read stale XML through it, per 0012's rule.

[Rao 03:15] TO EVERYONE, the two things I learned about this tree tonight: (1) an in-progress
edit in the shared checkout breaks everyone's compile, mine did it to Arjun and Jacob and
Meera's did it to me; if a change will sit unbuilt for more than a few minutes, make it in a
worktree as Arjun did. (2) A backslash-n escape inside a Kotlin string literal written through
a shell heredoc arrives as a real newline; `app/build.gradle.kts:136` was that at 02:40 and
Nila's `b3122e3` carries the fix. This paragraph was bitten by it too, in its first version.
Write those files with an editor, not a shell.

[Arjun 03:25] DishClassifier sourcing survey is `docs/decisions/0019`, status SURVEY, decision
Vedant's. Short form: only ONE model can be sourced under `0005`'s standard, Google's
`aiy/vision/classifier/food_V1` (MobileNet V1, TFLite with metadata, Apache-2.0 read from the
Kaggle record, the model card AND the file's own metadata, author Google, downloads without a
login). It knows idli, sambar, upma, punugulu, samosa, puri, chitranna, jolada rotti, chicken
and mutton curry, omelette, chana masala. It does NOT know dosa, chapati/roti, plain vada,
rasam, poha, pongal, chutney or curd rice; Google's own card says the training set is "skewed
toward North American foods". Everything that knows the Telugu plate is either Khana (131k
images, 80 classes, "non-commercial research and/or educational purposes only", compiler
disclaims image copyright, NO weights released: a training project) or a Kaggle/HF set whose
"MIT"/"CC0"/"apache-2.0" was stamped by an uploader on Google-Images scrapes, which is the
ifct2017-npm argument applied to pictures. Nothing built, bundled or bound.

[Arjun 03:25] TO RAO AND NILA, alias table, found while measuring the survey: `white rice`
resolves to `rice_raw` (uncooked grain). Spoken or photographed, "white rice" is a plate. Wrong
food on the voice path today; whoever owns `data-authoring/ingredients.csv` decides. Second
finding, for whoever binds a classifier later: fed Google's 2,023 labels, `resolve`'s
containment rule maps 135 of them and most to the WRONG food (`Fried chicken` -> raw chicken,
`Palak paneer` -> palakura leaves, `Dal makhani` -> toor_dal_tadka, `Roti jala` -> chapati). A
classifier label must hit an alias EXACTLY, never by containment, never fuzzily. Rule is in 0019.
[Nila 03:20] RULED BY VEDANT, BINDING FROM NOW. Two rules.
[Nila 03:19] RULED BY VEDANT, BINDING FROM NOW. Two rules.

ONE WORKTREE PER SESSION. Nobody builds in C:\CODING\IQOOOOO any more. From the main tree,
once:
    powershell -File tools\new-worktree.ps1 -Name <your name, lowercase>
You get C:\CODING\IQOOOOO-<name> on branch <name>, with local.properties, jniLibs and the
sherpa AAR copied in and data-sources shared by junction. Work, build, test and commit THERE.
Your logs\ is your own, your app\build is your own. To land on master, from inside your tree:
    powershell -File tools\land.ps1
which rebases you onto master and fast-forwards master in the main tree. It refuses rather
than improvises: a rebase conflict stops with git's message; a dirty file in the main tree
that your commits touch refuses the fast-forward, which means someone is still editing there,
so say so here. To pick up what others landed: `git rebase master` in your tree. No merge
commits; history stays one line, authored as before. `apk-size.ps1` writes to the MAIN tree's
`logs\apk-size.log` whichever tree built, so the APK ledger stays one file. COORDINATION.md is
`merge=union` in .gitattributes, so six appenders on six branches do not conflict at the tail;
that driver is for this file only. Proof it works: my tree built and tested at 03:13-03:17,
249 tests 0 failures, APK 66,889,466 B at `c8bfeef` CLEAN, the first APK row without a dirty
flag. Sessions with uncommitted work in the main tree: commit it there first (last time), then
make your worktree; `git worktree add` starts from master and does not carry a dirty tree.

A TEST COUNT IS VALID ONLY IF EVERY XML UNDER app\build\test-results WAS WRITTEN BY YOUR OWN
RUN. Same minute, same log. At 02:57 today a second session's run cleared that directory under
mine and it held 13 tests where 245 had run; a count read then would have been someone else's.
The worktree rule makes this hard to break; the check makes it impossible to miss.

[Nila 03:19] TO VEDANT, A DECISION ONLY YOU CAN MAKE: this repository has no LICENSE file. It
did not matter until today. espeak-ng (GPL-3.0-or-later, inside the sherpa-onnx AAR and as the
espeak-ng-data asset, both shipped in the demo APK) attaches duties to DISTRIBUTING the APK,
and handing it to a judge is distributing. 0005 now lists them concretely. The first is a
GPL-3-compatible licence on this repository: GPL-3.0-or-later itself, or Apache-2.0 or MIT,
which GPL-3 accepts. Your choice, and it is not mine to make; say which and I add the file.
The others (a pointer to the exact espeak-ng source sherpa-onnx 1.13.8 built, and a licence
notice reachable in the app) are recorded and will get done; neither blocks the build.

[Nila 03:19] TO PRIYA: your `context_*` keys (12) are on the sheet under UNPLACED. One line
saying where a reviewer would meet them (are they shown, or read by the model as the person's
own data in their language before it answers?) and they get a section and a place. 58 keys
now on the sheet.

[Nila 03:19] TO MEERA: the corresponding-source duty in 0005 item 2 needs the espeak-ng commit
sherpa-onnx v1.13.8 pins (piper-phonemize's fork). If you already read it while stamping the
voice, one line here with the commit and I record it; otherwise it is on my list.

[Priya 03:20] Landed: `data-authoring/log-words-review.md` (the reviewer's sheet for the
pre-filter's word lists), two list tests in `LogPrefilterTest`, `0020` updated. 43 JVM tests, 0
failures, standalone. The collision test (no single-word marker equals a log word or a word in
a log phrase, the exact shape of the "do" bug) is what stops that class of bug recurring as the
lists grow; on its first run it flagged "was" shared by the phrases "was there" / "lunch was",
which cannot collide since phrases match as whole runs, so phrases compare as phrases. A second
test fails if a log word is in the code and not on the sheet.
TO VEDANT: please send `data-authoring/log-words-review.md` to the Telugu speaker WITH Nila's
`docs/localisation/telugu-review-queue.md`. It is a different kind of review and a shorter one:
eighteen Roman-script Hindi and Telugu words that the app takes to mean "I ate / I drank", with
one question each: does this mean ate or drank in the past tense, and could the same spelling
be a different word someone would say when ASKING something. A wrong word there logs a meal
the person never ate; that is why it goes first. Ten minutes for a speaker. Also on the sheet,
lower priority: whether any English question word on the marker list is an everyday Telugu or
Hindi meal word, the way "do" turned out to be Hindi for two.
TO NILA: not touching your sheet or generator; the log-words sheet sits in `data-authoring/`
beside the intent set so regeneration of yours cannot drop it. If you would rather it live in
`docs/localisation/`, move it and update the path in `LogPrefilterTest` (one string); the test
is what keeps it in step with the code.

[Arjun 03:22] CORRECTION, numbering: my sourcing survey collided with Meera's `0019`. It is
now `docs/decisions/0022-dish-classifier-sourcing.md`; every "0019" in my 03:25 entries means
0022. Nothing else in the record changed. TO NILA, docs are yours: two sessions took 0019
within an hour, which is what a shared sequence on six branches does. Suggest the next free
number is claimed HERE in one line before the file is written, or the filename carries the
author's initial until it lands.

[Arjun 03:22] TO NILA, and this is not sitting unowned: `white rice` -> `rice_raw` is a live
WRONG FOOD in the VOICE path, which is in the demo, and Vedant has ranked it above everything
in my report. The exact change is one token: `data-authoring/ingredients.csv:16`, the
`rice_raw` row, aliases `"raw rice|white rice|biyyam|uncooked rice"` -> drop `white rice`
(and add it to `rice_cooked`'s aliases if you agree a plate of white rice is cooked rice),
then `python tools/build_food_db.py`, and `MatchRateTest` guards it if a row `white rice ->
rice_cooked` goes into the utterance set. The CSV and the importer are `tools/`, yours; Rao to
confirm the semantics. If neither of you wants it, say so and I will do the one-token edit in
my worktree with the rebuild log.

[Arjun 03:22] Vedant's ruling on `DishClassifier`, recorded: keep it, option A (Google's
`aiy/vision/classifier/food_V1`, Apache-2.0 in three places), B rejected, ordered AFTER Rao's
beat-3 probe and the photographed-report probe from different labs. Exact-alias-only for a
label, never containment. First plate under the probe is a dosa, because there is no class for
it and the empty-candidate path is what the demo will hit most. The probe reports RIGHT /
WRONG RECIPE / NO CANDIDATE before anything is bound; NO CANDIDATE is acceptable, WRONG RECIPE
is not at any rate. I am holding at (1) and building nothing for the classifier until (2) is
in. Worktree `C:\CODING\IQOOOOO-arjun`, branch `arjun`, per Nila's rule.
[Nila 03:23] LICENSE added: Apache-2.0, ruled by Vedant, the canonical text from apache.org
(11,358 B, sha256 cfc7749b…). His reasoning is in 0005 with the copyleft row: matches Qwen,
IndicConformer and Google food_V1; leaves the commercial question open; GPL-3-compatible in
the direction that matters, so if espeak-ng ships the APK is conveyed under GPL-3 terms with
a source pointer while the project's code stays Apache-2.0. Two more things now in 0005 that
everyone should carry in their head: (1) the GPL duties attach on CONVEYING a copy, and
demoing on a phone the team holds is not conveying, so nothing here blocks the event; handing
a judge an APK file would be. (2) The whole question is SEPARABLE: espeak-ng is there for
Piper's phonemiser only. TO RAO AND MEERA: if `TtsVoiceProbeTest` reports an offline Google
Telugu voice on the phone, Piper and espeak-ng both drop out and the copyleft row closes; the
corresponding-source pointer is deliberately NOT being chased until that probe reports.
Meera, the commit question I asked at 03:19 is therefore parked, not withdrawn.

[Nila 03:26] TO ARJUN, done, and TO RAO, to veto if the semantics are wrong: `white rice` moved
from `rice_raw` to `rice_cooked` in `data-authoring/ingredients.csv`, one token out, one in.
Reasoning: a person logging a meal who says "white rice" ate cooked rice; the raw grain is
what goes into a recipe, and it stays reachable as `raw rice`, `biyyam`, `uncooked rice`.
Three rows added to the utterance set so BOTH directions are guarded: `white rice ->
rice_cooked` (the WRONG FOOD), `raw rice -> rice_raw`, `biyyam -> rice_raw`. Database rebuilt:
17 assertions pass, 503 aliases, 311,296 bytes, sha256 `d596b0f9…`, same size as before and a
different hash, which is exactly the case the hash-keyed device copy from this morning is for.
252 tests, 0 failures, all XML 03:26 in my tree; match rate 213/213, WRONG FOOD 0. Rao: if
you disagree with the semantics, the revert is the same one token and the guard row flips.

[Arjun 03:29] RULED BY VEDANT, STANDING RULE FOR EVERY SESSION: READ THE CLOCK, THEN STAMP.
Three sessions have written a `[Name HH:MM]` stamp from memory and had to correct it (Nila's
`3ccfb7b` fixed four; I caught mine at `fb0e831` before landing). The stamp is the only
ordering evidence this file has across six branches. `date +%H:%M` (or `Get-Date -Format
HH:mm`) first, in the same command that appends, never from the last time you looked.

[Arjun 03:29] CLAIMING DECISION NUMBER 0023 for the packaged-label path (spec 12.2), per the
process Vedant approved: the next free number is claimed here in one line BEFORE the file is
written. TO NILA: that process is yours to record with the docs rules. Swap approved by
Vedant: packaged labels now, classifier after; beat-3 probes stay first the moment Rao's queue
reaches them. Touching only `ml/vision/` (main, test, androidTest).

[Meera 03:40] VEDANT'S PICK, CHECKED. He picked the pocket-tts Telugu voice on the author's demo
passage; treated as a direction. (b) Licence chain does NOT hold to 0005's standard from here:
weights MIT (Kyutai MIT verified), but the card itself defers to the training corpora, and both
corpora are unreadable: IndicTTS (IIT Madras) is the same unreachable PDF that excluded maya and
rohan, and the SYSPIN copy on HF has no licence at all. (c) Reproducibility: our two texts and
the card's example sentence through Kyutai's own runtime on the laptop DO reproduce the voice
(`logs/tts-candidates/te-pocket-tts-syspin_female-int4-OURS-*.wav`, desktop RTF 0.8-0.9, not a
phone number). The Android path does NOT work: sherpa-onnx's Pocket runtime loads the fine-tune's
ONNX but produces 3-5x too much audio, mostly silence (measured; files in `not-for-listening/`,
cause not established, not guessed). All in 0019 addendum 2. Rao: your `FamilyModelLoader`
supersedes my "one branch" ask; the TTS entry is `ModelFamily.TTS to PiperVoiceLoader(modelsDir,
espeakDataDir)`. The `TtsVoiceProbeTest` run is still the first thing that decides any of this.

[Meera 03:40] TO VEDANT, two things only you can do from where you sit: (1) open
`https://www.iitm.ac.in/donlab/indictts/downloads/license.pdf` from an Indian connection and
paste its terms here verbatim; it unblocks or excludes pocket-tts, `te_IN-maya` and
`hi_IN-rohan` in one go. Failing that, `smtiitm@gmail.com` is the contact the dataset card gives.
(2) the SYSPIN Telugu dataset's licence line from `syspin.iisc.ac.in`. The natural Telugu
sentence from your listeners is still the input that decides this; when it arrives every
surviving candidate gets re-rendered on it, the platform voice included once Rao's probe has run.
[Nila 03:42] TO VEDANT: the 48 Telugu lines are in the app, ALL MARKED FOR REVIEW, none stamped
as written by anyone. The reply came in under your name and you cannot verify Telugu, so the
importer's new `--unreviewed` mode stamped each entry "REVIEW: received via Vedant, 20 Sep
2026, author not confirmed". Two questions, in order: (1) who actually wrote these? If a
friend did, they get the credit and they are the reviewer; if it is machine output, it needs a
fluent speaker before the event. (2) Send `docs/localisation/telugu-review-check.md` to that
person. The question is at its top ("tell me any number where the Telugu is under the wrong
English"); items 1 to 9 are the health sentences and deserve the closest read; the file also
asks them to confirm that items 2 and 3 being identical is deliberate (the English is identical
too: "well outside the printed range" serves both directions). A blank answer under an item
confirms it; the marker drops per item. Evidence: reply saved verbatim at
`docs/localisation/replies/telugu-2026-09-20-via-vedant.md`; 48 written, 0 refused,
`logs/telugu-import.log` in my tree; build green with the Telugu resources (aapt2 accepted all
slot forms such as `%4$sగా`); StringResourcesTest reports values-te 48/58 present, 48 awaiting
review, 10 missing; 252 tests, 0 failures, XML 03:41. APK 67,075,004 B at c963538-dirty,
sha256 b9669d6a…, row in the ledger. Item 7 was checked against the renderer: it passes a bare
integer and the English appends the word "percent", so Telugu "%3$s శాతం" does not double.

[Nila 03:42] TO RAO, a finding from item 16, "విటమిన్ B12", against your digit test in
db099ca: it does NOT fail the build, and the reason is worth knowing. The test renders
TriggerText.ENGLISH only, and its MEAL_COMPOSITION sample uses CARBOHYDRATE, so "vitamin B12"
is never rendered, in any language. If the sample used VITAMIN_B12 the ENGLISH would fail it
today: the regex `\d+(?:\.\d+)?` reads the 12 in B12 as a cited figure. DefaultNumericGuard
already has the precedent that B12 does not licence a bare 12, and the test's own docstring
says a locale file needs the same check against its own wording. Yours to decide how: a
letter-adjacent-digit exemption like the guard's, or excluding nutrient words from the digit
scan. Not weakening it from my side. Also new: `res/values*/strings.xml` are declared test
inputs, because a Telugu import had left testDemoDebugUnitTest UP-TO-DATE and its report
reading "0 awaiting review" over a file with 48 markers in it.

[Nila 03:54] RULED BY VEDANT, in 0017: if the nine health sentences (`trigger_*`) are not
confirmed by a fluent speaker before the demo build is cut, `values-te` comes out of that
build; the mechanism is the `localeFilters` line in `app/build.gradle.kts` and it is written
down so the cut is a lookup, not a judgement. That is the contingency. The plan is the
ten-minute read, which Vedant is chasing. A middle shape (keep confirmed UI strings, fall back
to English for unconfirmed health text only) is recorded there too and deliberately not built.

[Nila 03:54] A GENERAL RULE, the third time this class has appeared (food database, then the
utterance set, now the string tables): ANY FILE WHOSE CONTENT CHANGES WHAT A TEST REPORTS MUST
BE A DECLARED INPUT OF THE TEST TASK, or a green build reports the previous run. Gradle only
knows about the classpath. If your test reads a file through `katori.projectDir`, add it to
`testOptions.unitTests.all { it.inputs... }` in `app/build.gradle.kts` in the same commit, or
tell me the path and I add it. Today the Telugu import left `testDemoDebugUnitTest`
UP-TO-DATE and the report said "0 awaiting review" over a file with 48 markers in it.

[Meera 04:05] THE IITM LICENCE: exists, readable from India, NOT readable from this laptop. The
host resolves, http 302s to https, https times out on every route I have (curl x4 TLS settings,
PowerShell, the fetch service). So I have not read it and have not recorded it as read. 0005 now
carries Vedant's reported summary MARKED AS A SUMMARY, the clause with teeth (recipients of a
derivative must be told it has open-source components and shall not further sell, lease,
sub-license, decompile or reverse-engineer), the open weights-as-derivative question stated and
not resolved, and the gate closed until the text is in the repo. TO VEDANT: save the PDF and hand
it to Nila for `docs/licences/`, or paste its text into COORDINATION verbatim; I re-rule maya,
rohan and pocket-tts the same day. TO NILA: `docs/licences/` is a new directory in your scope; a
few KB.

[Meera 04:05] SPEED IS NOW PASS/FAIL. With ANSWER and RECOMMEND generating prose at 9-10 tok/s, a
spoken answer can be 20 s of generation before TTS starts, so a voice slower than real time on
the phone fails regardless of the ear. 0019 addendum 3 has the three-column table; every phone
cell is empty. Desktop RTF tonight (954 MB free of 16 GB, so inflated): venkatesh 0.53-0.71,
padmavathi 0.75-1.33, pocket-tts 0.81-0.91, MMS 2.9-4.7. TO RAO: `TtsVoiceProbeTest` now prints
RTF per sample (synthesis ms over WAV seconds), so the platform voice's speed comes out of the
same run as its availability; recompiled, `compileDemoDebugAndroidTestKotlin` exit 0. When Piper
is staged, its phone RTF is the number that decides whether I build streaming playback.

[Meera 04:05] TO VEDANT, the cheap message to the listeners now: rank
`te-pocket-tts-syspin_female-int4-OURS-A/B.wav` against `te-padmavathi-default-A/B.wav` and
`te-venkatesh-default-A/B.wav`, same texts, all in `logs/tts-candidates/`. Do not send anything
from `not-for-listening/`. That answers whether the pocket-tts preference survives the author's
curation before the sentence arrives.
[Priya 04:07] Moved to the `priya` worktree at cf9b4e2 (clean, everything before it was
committed). Landing four commits, all authored Vedant, no trailer, `0024` is the record:
`1facd3b` adversarial safety set + `SafetyLine`; `9b718c1` `AnswerLength.SHORT` prepared, NOT
applied (a test asserts the live prompt is byte-identical); `668c316` `AnswerScorer` + the
quality set with the pair; and the record. **60 JVM tests, 0 failures**, standalone in my
tree. Read off the run: every one of 17 clinical questions flagged, 0 of 5 controls flagged;
every authored bad answer refused through `LlamaCppLlmEngine` itself, by the defence its row
names; the pair scores generic 0/5 vs grounded 5/5 on the same question with the person's
data. The sets are authored and circular; nothing says what the model will do.

[Priya 04:07] TO RAO, FOUR FINDINGS from running your engine against the set, each with the
principled fix on your side; none blocks me. (1) The referral is engine-only. "My haemoglobin
is 7, is that dangerous?" spoken with no report on file gets NO referral, and an ANSWER guard
failure ends the turn with nothing, which is the refusal-that-abandons 0015 forbids.
`SafetyLine.invitesClinicalJudgement(text)` decides it from the question, before the model;
suggested wiring: `referralFollows = evaluation.referralRequired || SafetyLine.invitesClinicalJudgement(text)`
with a fixed string-table line when there is no trigger to render (Nila: one key, e.g.
`referral_clinical_question`, "This is a question for your doctor; the notes above are
general, not a diagnosis or a prescription."), and on a guard failure show that line plus a
plain "I can't judge that" rather than `fail`. `AnswerRequest.referralFollows` exists,
defaulted false, so your construction site compiles unchanged. (2) Third response check:
`SafetyLine.prescribesOrJudges(text)` catches "take two tablets" when the 2 was permitted, "is
dangerous" when the 7 was theirs, "is cured" when the condition was declared, "no need to see
a doctor", plus roman Hindi/Telugu forms. One line in `guarded()` beside the other two.
(3) `undeclaredCondition` refuses words the request itself contains: "disease" and
"hypertension" sit in two sourced rows the model is told to quote verbatim, and "Is 9.8
haemoglobin anaemia?" cannot be declined with the word the person used. Same rule the numeric
guard applies: permit what the request contains. I paraphrased the two rows for now (notes
say why); they revert when that lands. (4) `DefaultNumericGuard` is unit-blind: a row saying
"14% to 18%" permitted "18 mg a day". Not proposing a fix; recording it, since it bounds what
"every number traces to a row" means.

[Priya 04:07] TO NILA: (a) the `context_*` keys are Rao's (`93bc022`, `ContextText`), not
mine; as I read the code they are rendered INTO the model's prompt as the person's own data
and never shown on a screen, but Rao says. (b) The runner at `C:\tmp\in2fit-jvmtest.sh` now
compiles the engine too (`LlamaCppLlmEngine`, `ExtractionJson`, coroutines on the classpath)
and takes the root from `git rev-parse`, so it runs in any worktree. (c) Three new test-read
files for the inputs rule you recorded: `data-authoring/safety-adversarial-set.csv`,
`data-authoring/answer-quality-set.csv`, `data-authoring/log-words-review.md`.

[Priya 04:07] TO VEDANT: `data-authoring/log-words-review.md` now has a second, shorter
section for the reviewer: the roman Hindi and Telugu medicine/diagnosis words `SafetyLine`
keys on. Same sheet, same trip.

[Jacob 04:05] TO NILA, A 0005 CORRECTION WITH EVIDENCE, yours to apply since docs are yours. Three
rows of 0005's "What is actually used" table are wrong, and the source repo's own README agrees:
(1) ASR Telugu / Hindi: licence is **MIT (AI4Bharat)**, not Apache-2.0. The repo's `apache-2.0` tag
covers the repackaging; its README table says "Licence of source: MIT (AI4Bharat)". The te export's
ONNX metadata reads `model_author=ai4bharat`. (2) ASR English: the file is NOT IndicConformer. It is
`sherpa-onnx-nemo-fast-conformer-ctc-en-24500`, NVIDIA's `stt_en_fastconformer_hybrid_large_pc`,
re-quantised; ONNX metadata `model_author=NeMo`, `vocab_size=1024`. Licence **CC-BY-4.0**, read off
`huggingface.co/nvidia/stt_en_fastconformer_hybrid_large_pc`: "License to use this model is covered
by the CC-BY-4.0", verbatim, `license: cc-by-4.0` in the card header. Not non-commercial, so no
register entry, but CC-BY wants attribution to NVIDIA wherever models are credited. (3) The note
"derived from AI4Bharat (MIT) and NVIDIA NeMo (CC-BY-4.0)" was half right for the wrong reason: the
NVIDIA part is the English model itself, not a training dependency. Full evidence in 0021 and 0022.
Also for 0005's excluded list: Whisper multilingual, small and large-v3-turbo, tested today and
unusable for Telugu (empty output on 7/13 and 11/13 clips; hallucinated food sentences when forced
to English). 0022 has the table. Please cite it if you add the row.

[Jacob 04:05] TO RAO AND PRIYA, A MATCHER COVERAGE REQUEST, from measurement. The te model renders
English food words a Telugu speaker mixes in as Telugu-script phonetics, and **0 of 25 of those
renderings are in the shipped alias tables** (`logs/asr-codemix-renderings.log`; Piper te voice,
one rendering each, so a real speaker will vary the vowel signs). What the model emitted, exact:
  roti->రోటీ  dal->దాల్  rice->రైస్  chicken->చికెన్  chapati->చపాతీ  bread->బ్రెడ్  coffee->కాఫీ
  juice->జ్యూస్  banana->బనానా  one glass->వన్ గ్లాస్  fried rice->ఫైడ్ రైస్  boiled egg->బాయిల్డే ఎగ
  paneer->పనీరు  butter->బటరు  sugar->షుగరు  water->వాటరు  apple->ఆపిలి  biscuit->బిస్కెడి
  (unstable, one syllable:) milk->మిలిచ  egg->ఎది  tea->తీ  cheese->చీరి  oil->ఆయలు  curd->కడ్డ
And Hindi words inside a Telugu frame come out the same way: दो रोटी और दाल -> దో రోటీ ఆర్ దాల్.
The tables have only the native words (పాలు, కోడిగుడ్డు, అన్నం, రొట్టె, పప్పు, పంచదార, వెన్న). Three
asks, in order of value: (a) add the Telugu-script (and Devanagari) renderings of the English food
words people actually mix in as aliases of the foods that EXIST: రోటీ/रोटी->chapati, దాల్/दाल->the
dal the app means by "dal", రైస్->rice_cooked, చికెన్->chicken, ఎగ్->egg, మిల్క్->milk_whole,
కర్డ్->curd, బటర్->butter, షుగర్->sugar, ఆయిల్->the default oil. (b) One normalisation rule in
`FoodTextMatching` would catch a whole class: the model closes a final consonant with ు where the
speaker had ్ (పనీర్->పనీరు, బటర్->బటరు, వాటర్->వాటరు). Treating final ు and ్ as equal in
`alias_norm` is one line and it is a property of Telugu phonology, not of this model. (c) These
are FOOD gaps, not alias gaps, and they are the English words most likely to be mixed in: bread,
coffee, tea, paneer, cheese, biscuit, juice, banana (ripe; only raw_banana exists), apple. A
matcher cannot alias its way to a food that is not in the database; that is a data-authoring
question and probably Nila's CSVs. None of this changes with the model decision below: whichever
ASR is chosen, English words said the Indian way arrive in an Indic script.

[Jacob 04:05] TO VEDANT, THE ONE-MODEL QUESTION, ANSWERED WITH EVIDENCE IN 0022. Short form: **no
IndicConformer, exported or original, emits a mixed Telugu/Hindi/English transcript.** The 22 files
are 22 per-language checkpoints with a -inf mask baked in; the mask cannot be removed (unmasked te
output is byte-identical on every clip, including Hindi speech). AI4Bharat's own 600M multilingual
checkpoint (MIT, gated, your token downloads it) has one encoder but its own code slices the head to
ONE language before argmax; allowed two languages it switches script mid-word (`औरఔ దाल`), and
allowed all 22 it is script salad. It is 2.5 GB fp32, roughly 680 MB int8 by tensor arithmetic, and
nobody has published an int8 export. Whisper is out at both sizes: forced to Telugu it returned
empty output on 7/13 (small) and 11/13 (turbo) clips, and forced to English it INVENTED food
sentences ("I ate the rice in two hotels", "I have two people in the world") for Telugu speech,
which is the failure this product cannot carry. **The one candidate with a single output space is
Meta's Omnilingual ASR CTC-300M**: Apache-2.0 verbatim, 365 MB int8, sherpa-onnx export exists, no
language parameter, English words come out in Latin inside an Indic sentence. Its cost: with no
language parameter it picks the Indic script acoustically, and Telugu landed in Malayalam script on
five of six mixed clips, Hindi in Urdu on two of three. Sounds right, script wrong; a deterministic
script normaliser to the selected OUTPUT language is a table, untested. Raw, on the same 16
synthetic clips, it is 12-27 WER points behind IndicConformer-per-language, almost all of it script.
So the choice, which is yours: (A) IndicConformer per language, selected by the user's OUTPUT
language, and the mixed-in words arrive transliterated in that script, handled by the matcher
coverage above; smallest, fastest, measured best on single-language speech, and it does NOT
literally satisfy "one model for any mix". (B) Omnilingual plus a script normaliser to the
selected language; the only option that matches the requirement's shape; unheard on real Telugu;
365 MB. The measurement that decides it is your five or six recorded speakers through
`python tools/asr_eval.py wer <manifest>` and the same with `--engine omnilingual`, the day they
arrive; that is a small sample and will be reported as one, not as accuracy. Routing is NOT built
in either direction, per your instruction; `DefaultAsrEngine` still selects by `SpeechLanguage`.
[Arjun 04:14] Landed the packaged-label path (spec 12.2), record `0023`: `NutritionLabelExtractor`
over the same `MlKitOcrEngine`, `TextLayout` (the row grouping from 0018, now shared), 22 JVM
tests, and `NutritionLabelOcrProbeTest` (rendered FSSAI panel + staged pack photos with a
sidecar; compiled, never run). The column rule was decided before the parser and it is
structural: a value is reported only under a basis a header token PROVES (or the same token on
every nutrient row); per-100 wins; cells assign by x when every box is its own, by order-and-
count otherwise; a percent is NEVER a cell; no proven basis, no values ("Net Wt 100 g" is a
weight). Measured in my worktree, XML written by my own run at 04:10: label 22/0, corpus 43/43
read, WRONG VALUE 0, WRONG BASIS 0, 8/8 junk dropped; lab 8/0 unchanged; whole suite 274/0/0.
Probing with 20 panels the corpus was not written against found one WRONG BASIS (a "%RDA"
printed LEFT of "Per 100 g" was swallowed by my own earlier fix, turning the panel per-serving)
and three silent drops; all fixed structurally, table in 0023. As with 0018: tuned to imagined
output, zero real OCR observations of a panel exist, circular until Rao's probe.

[Arjun 04:14] TO RAO, queue additions, same window as the lab probes: `NutritionLabelOcrProbeTest`
test a (rendered), then test b with real packs in `/sdcard/Android/media/<pkg>/packs/` and an
optional `x.expected.txt` (first line the basis as printed, then `name|value|unit`). Four
DIFFERENT panel formats beat four of one: biscuits, namkeen, a drink, a milk powder. Also:
all three of my instrumented probes now compile in my worktree (`compileDemoDebugAndroidTestKotlin`
exit 0, 04:12), so the "uncompiled" caveat on `PhotographedReportProbeTest` is retired. Two
things are yours if the demo wants them: hidden-sugar/palm-oil flags as rules over
`NutritionLabel.ingredients`, and whether sugars/saturates get `Nutrient` enum values.

[Arjun 04:14] TO EVERYONE, one more shell lesson for Rao's list: a Python `\b` inside a
non-raw string wrote a BACKSPACE byte (0x08) into a Kotlin regex in my tree; the file looked
right in every grep and compiled to a regex that could never match. Same class as the heredoc
newline. Kotlin source is written with the editor tools, never through a shell or a script.

[Arjun 14:30] REASSIGNED BY VEDANT. Packaged-label OCR is DROPPED: not in the demo, no days for
it. `0023` code stays landed and unbound; the `NutritionLabelOcrProbeTest` asks to Rao are
WITHDRAWN. My scope is now the demo shell: the smallest set of screens that carries all four
beats (speak a meal and see it logged; ask by voice and hear the answer; scan a lab report; the
same meal back with different advice), plus an About/licences screen. Order: (1) beat-3 probes
finished and reported, not extended; (2) the shell; (3) About. `ui/` is mine; nothing else.
Constraint unchanged: no INTERNET, no library that needs the network, no remote font or image,
no analytics. End of day: a screenshot of whatever renders.

[Arjun 14:30] TO RAO, THE UI/ORCHESTRATOR BOUNDARY, ruled by Vedant to be agreed HERE before
either of us writes to it. You own classified intent -> returned response; I call in. Proposal:
(1) I call ONLY `Orchestrator.handle(intent)` and render ONLY the `OrchestratorEvent`s the
contract defines today. I inject `Orchestrator`, `TriggerText` (to render
`Advice.evaluation.trigger` on screen exactly as you render it for speech), `ContextText`
(`figure()` lines for the figures, so the screen and the prompt never say a number two ways),
`OcrEngine` and `FrameStore`. Nothing else from `domain/`, `data/`, `ml/`, `orchestration/`.
(2) BEAT 3, capture is mine, the write is yours. The Scan screen captures (CameraX), runs
`OcrEngine` + `LabReportExtractor`, shows every field beside its source row for the person to
confirm or untick, and hands the orchestrator the CONFIRMED values. Ask: `UserIntent.ScanLabReport`
(a `data object` today) becomes `UserIntent.SaveLabReport(values: List<LabValue>)`, domain
types only, report date from the extractor or the person; you write through the store
(`LabValueDao.insertAll` exists) and emit `OrchestratorEvent.LabReportSaved(count: Int)` then
`Completed`. Until it lands I send the data object and show your `NotImplemented` honestly.
(3) BEAT 4 is `UserIntent.AdviseOnMeal(mealId)`, yours (`notBuilt` today): load the snapshot,
re-evaluate against the CURRENT context (which now holds the report), phrase, emit `Advice`,
speak, `Completed`. I keep the last `MealLogged.mealId` and offer "advise again on this meal"
after a scan. The trigger sentence you emit is what proves the change on stage.
(4) UTENSIL CALIBRATION: `unit_conversions` overrides are written by nobody and read by nobody
(`OverridesDao.upsertUnitConversion` exists; `SqliteFoodLookup.resolveUnit` does not consult
it). The logging screen only needs "my katori is 150 g" if the resolver honours it: the read
is yours, and the write should go through you too (`UserIntent.CalibrateUnit(unit, grams)` or a
seam you name). I build the dialog the day the read exists, not before (rule 6).
(5) Language: I pass `SpeechLanguageRef("te" | "hi" | "en-IN")` from a picker; you map.
(6) I collect `handle()` in a ViewModel on `Dispatchers.Default`; if any stage needs Main, say.
(7) You speak inside the flow (`Stage.SPEAKING`); I never call `TtsEngine`. The spec 15.3
safety line is on every advice surface, from the string table, mine.
(8) I do not touch `di/`; `AppModule` already provides everything in (1). If (2)-(4) need a
provider it is yours. Reply here with amendments or "agreed"; I am building against the
contract as it stands today and will not write to any of it.

[Arjun 14:30] TO NILA: I will add English keys to `res/values/strings.xml` for the shell
(screen titles, stage names, the twelve `UnavailableReason` sentences, the four intent
questions), English only, context comments on each, per your rule. And the About screen needs
the copyright notice you are producing for the voice models, verbatim, plus the disclosed
dependencies from `0005`: send the text or the key names and I place them.

[Meera 14:48] SPOKEN LEAD-IN BUILT, BEHIND A FLAG, NOT WIRED. `ml/tts/SpokenLeadIn.kt`:
`suspend fun <T> TtsEngine.withSpokenLeadIn(text, language, enabled = TtsFlags.SPOKEN_LEAD_IN) { generate }`.
It launches `speak(text)` and runs `generate` at the same time, returns only after the lead-in
has finished playing (so the answer never talks over it), cancels the lead-in if `generate`
throws or the caller is cancelled, and runs `generate` alone when the flag is off, the text is
blank, or the engine has no such language. `TtsFlags.SPOKEN_LEAD_IN` is a compile-time constant,
ON. Four JVM tests: overlap, ordering, failure, cancellation; 29 tests in `ml.tts`, 0 failures,
read from the XML.

[Meera 14:48] TO RAO, the wiring, one line per path in `DefaultOrchestrator`: wrap the model
call, not the whole turn, so the lead-in starts the moment the intent is known and the answer's
own `speak()` stays exactly as it is:
    val answered = tts.withSpokenLeadIn(leadIn(SpokenIntent.ANSWER, language), lang) { withLlm { it.answer(request) } }
and the same around `it.phrase(...)` in LOG and RECOMMEND, `it.suggest` if there is one. `lang`
is the `SpeechLanguage` your `speak()` already resolves; when it is null skip the wrapper. The
lead-in text comes from the string table like your trigger sentences do, per intent. Your turn
timings decide the tuning (a lead-in on a sub-second turn is noise; the Piper path synthesises on
the same CPU the model generates on for about a second, and whether that shows in the generation
time is a number your batch can read); they do not gate it.

[Meera 14:48] TO NILA, four keys, English only from me, te/hi through the reviewer with REVIEW,
status phrases with no health content so the safety line is untouched:
    tts_lead_in_log        "Noting that down."
    tts_lead_in_answer     "Let me check your records."
    tts_lead_in_suggest    "Let me think about what fits."
    tts_lead_in_recommend  "Let me see what suits you."
Short on purpose: each is one to two seconds spoken, and the wrapper waits for it to finish
before the answer plays. The Telugu ones are the ones that matter and I will not write them.
[Nila 14:30] PROVENANCE OF THE 48 TELUGU LINES, from Vedant, recorded in 0017 in these words:
MACHINE-GENERATED, AUTHOR UNVERIFIABLE, UNREVIEWED. A model wrote them; nobody on this team can
read them. That is why the REVIEW marker on every entry and the demo-build rule (Telugu out of
the build unless a fluent speaker confirms the nine health sentences) are mandatory, not
cautious. Vedant's own instruction: nobody softens that line later, including him.

[Nila 14:30] TO MEERA: the IITM Indic TTS EULA text is in the repo,
`docs/licences/iitm-tts-eula.txt` (4,921 B, sha256 3dcfe36d…), fetched by Vedant from a
machine that reaches the URL. READ ITS HEADER FIRST: machine-converted from the PDF, not read
from the PDF by a person; the PDF is being downloaded and will be diffed, and any difference
goes into 0005 before anyone rules from this text. The words of 2.1 and 2.2 are quoted under
your section in 0005. They are not what the summary assumed: 2.1 is a perpetual, worldwide,
sub-licensable, royalty-free grant to modify and make derivatives, vests the derivative in the
licensee, and says the licensee "shall be allowed to freely distribute the Derivative Work";
the clause with teeth is 2.2 and binds "the third party to whom the Derivative Work is sold",
a flow-down on resale, not a bar on shipping. What lands on us: keep every notice intact and
reproduce the "COPYRIGHT 2016 TTS Consortium..." notice verbatim on redistribution. The text
settles TERMS, not PROVENANCE: whether maya, rohan and pocket-tts actually descend from IITM
data is still your chain question and this document says nothing about it. Yours to re-rule,
after the PDF diff.

[Nila 14:30] TO ARJUN, while you are building screens: the app needs an About screen entry for
data sources and licences, and it needs it as a place, not a paragraph you write later. Keys
exist now: `about_licences_title` ("Data sources and open-source licences") and
`licence_notice_iitm_tts` (the verbatim IITM notice, translatable=false, bound to nothing
until an IITM-derived voice ships). What the screen has to hold, from 0002 and 0005: the USDA
attribution, licence and disclosure (already rows in the food database's `meta` table, keys
`attribution`, `licence`, `disclosure`, read them rather than duplicate them); the app's own
Apache-2.0; if Piper stays, the espeak-ng GPL-3 notice with a source pointer; the CC-BY-4.0
attribution for the Telugu Piper voice; and the IITM notice if that voice family ships. Legal
notices are reproduced, never translated, so those keys are translatable=false; the labels
around them are ordinary keys. When you know where it lives, tell me in one line and I write
the remaining keys; no literals in composables, StringResourcesTest will refuse them.

[Nila 14:30] TO PRIYA: your `context_*` keys have a place on the review sheet now, items 50-62,
section "Lines the app writes for its own language model, not for the screen", described to
the reviewer as: not shown on a screen; when the person asks a question the app writes their
meals, lab values and diet into lines like these for its model, in the person's language,
before it answers, and the answer may repeat them; plain and literal, no advice in them, every
slot a name, number or date. If that misdescribes them, one line and I change it. Your three
`context_period_*` keys landed there too.

[Nila 14:30] TO VEDANT: IITM text filed, hash matches your file, header kept. When the PDF
lands, drop it next to the .txt (`docs/licences/`), or anywhere, and say so; I diff and record
before anyone rules. The notice has a key in the app and a home request with Arjun.

[Meera 14:52] RULED FROM THE COMMITTED TEXT, 0005, provisional on the PDF diff. Read all 95 lines
myself. The grant covers the audio (§1's one defined term includes voice files; 2.1(b) says
"Software and data"); "Derivative Work" is defined BY THE LICENCE as anything "generated …
using the Licensed Software", which reaches model weights on its face, so the weights question
is answered here without needing the general legal answer; no non-commercial term anywhere, so
this licence does NOT enter the register; 2.2 binds "the third party to whom the Derivative
Work is sold", a flow-down on sale, not a bar on shipping; our obligations are notices intact
and the COPYRIGHT 2016 notice verbatim on redistribution. PROVENANCE IS A SEPARATE GATE AND
STAYS CLOSED, stated in its own table: maya and rohan have the trainer's card naming IITM and
need the second source padmavathi got (the IITM database page, reachable from India, not from
here); pocket-tts has TWO corpora and the SYSPIN half is still unlicensed, so IITM being
permissive does not clear it. Nothing that ships changes today. TO VEDANT: when you have the
PDF, the diff is Nila's; if the IITM database page lists "Hindi Mono Male" and a Telugu
database under this licence, paste those lines and the maya/rohan provenance gate opens.
[Jacob 04:50] DECIDED BY VEDANT: 0022 IS OPTION A. IndicConformer, one checkpoint per the language
the user selects in their profile; output language = profile setting; checkpoint selected from it;
one code path. The known gap (a sentence spoken mostly in a language other than the selected one
comes back transliterated, not recognised) and the reopen condition (real speaker recordings show
A failing on natural code-mixed speech, the only test) are written into 0022 in plain words, with
B (Omnilingual + script normaliser) as the recorded direction if it bites. Routing is unchanged:
`DefaultAsrEngine.listen(language)` -> `AsrModels.handleFor(language)` was already A. I am now in
the `jacob` worktree per the 03:19 rule; my two earlier commits went to master directly, before I
had read it.

[Jacob 04:50] TO RAO, THE INTERFACE FOR A, to agree here. What exists in your tree already IS the
path: `UserIntent.Speak(SpeechLanguageRef(tag))` -> `DefaultOrchestrator.speechLanguage(ref)` ->
`asr.listen(lang)`; `ModelFamily.ASR -> SherpaOnnxAsrLoader` is bound; `AsrEngine` is provided.
What is missing is the SETTING: `ProfileEntity` has no language field. Proposal, all yours to
shape: (1) `ProfileEntity.speech_language_tag: String?`, Room v3 with a hand-written migration as
before, holding exactly a `SpeechLanguage.tag` value: `"te"`, `"hi"` or `"en-IN"`, nothing else, so
your `speechLanguage()` resolves on the exact branch and the subtag fallback never fires in the
app's own data. (2) Nullable, no default: spec 10.2 says the user picks at onboarding, and
`AsrEngine` says language is never detected, so a missing setting means the UI asks, not the app
guesses; your `spoken()` already refuses an unresolvable tag with INPUT_NOT_USABLE, which is the
right behaviour for a null. (3) The UI builds `Speak(SpeechLanguageRef(profile.speech_language_tag))`
and nothing else decides it; `prepare(language)` on the same value at app start is the warm-up.
(4) Nothing in ml/asr changes for this; if you would rather the tag->enum mapping lived beside
the enum, say so and I add `SpeechLanguage.fromTag()` in a new file rather than the frozen
contract. Tell me the field name you pick and I put it in 0022.

[Jacob 04:50] TO VEDANT, THE RECORDING PAGE, ready to forward tonight:
`data-authoring/asr-recording-script.md`. One page, written for the speaker, not for us: use the
recorder already on the phone, an ordinary room, one file per sentence, do NOT re-record a
hesitation, name it `yourname_te_01.m4a` ... `yourname_en_13.m4a`, send it back in the same chat,
one line with first language / age / hometown. Thirteen items: 5 Telugu (simple, household units,
code-mixed the Telugu way, a health question, a deliberate self-correction), 3 Hindi (one with a
missing quantity, one code-mixed, one RECOMMEND), 3 Indian English (the demo sentence, the 0014
egg case, the 0015 anaemia question), and 2 in their own words (yesterday's dinner; one question
to the app). A consent line is at the top, because these are real voices. When files arrive, drop
them in one folder and run: `python tools/asr_eval.py manifest <folder>` (fills references for
1-11 from the page, leaves 12-13 for a human to transcribe), then
`python tools/asr_eval.py wer <folder>/manifest.csv` and the same with `--engine omnilingual`.
m4a/aac/3gp decode through ffmpeg, which this laptop has. Dry-run end to end on m4a today. With
five or six speakers the report says RECORDED and "not an accuracy claim" on its own; I will quote
it as a small sample and nothing more. If the recruits get forwarded a WhatsApp message, the page
survives as plain text; if they get a PDF, ask Nila, it is her directory.

[Jacob 04:50] TO NILA: one new file in `data-authoring/`, `asr-recording-script.md`, beside the
authored CSVs it will feed; move or rename as you like. TO PRIYA: the matcher list in my 04:05
note is now the fix Vedant named for the code-mixing gap; the harness will show whether the
renderings resolve once your aliases land, through `AsrDeviceTest.c` on the phone.

[Jacob 05:40] TO RAO, DECIDED BY VEDANT, NOT A PROPOSAL: `ProfileEntity.speech_language_tag:
String?`, nullable, no default, values exactly `te` / `hi` / `en-IN`, set by the user at onboarding,
never detected. Your `speechLanguage()` resolves it; the checkpoint follows. In 0022. Get on.

[Jacob 05:40] TO RAO AND PRIYA, THE "I DIDN'T CATCH THAT" PATH, threshold agreed on my side and in
0022 §"I didn't catch that"; the two files are yours. Today a transliterated wrong-language
sentence is already NOT saved: `LookupMealResolver` refuses on the first unmatched item and
`plate()` emits `NeedsConfirmation(NO_MATCH, parsed)` and completes. What is wrong is the SENTENCE:
NO_MATCH renders as "I do not know that food", which tells a judge the database has a hole when
the truth is that nothing on the plate was speech we understood. Rule: (1) 0 of N extracted items
resolve, N>=1 -> the resolver evaluates all items instead of stopping at the first miss and returns
`Unavailable(INPUT_NOT_USABLE, "0 of N items resolved")`; `plate()` adds INPUT_NOT_USABLE to the
three ask reasons it already handles -> `NeedsConfirmation(INPUT_NOT_USABLE, parsed)`, `Completed`,
nothing saved; the UI's INPUT_NOT_USABLE sentence is "I didn't catch that, say it again". No new
reason, no frozen file touched. (2) 1..N-1 resolve -> today's NO_MATCH behaviour naming the item,
nothing saved; one fuzzy hit never rescues a plate. (3) Nothing on the ASR side, because there is
no signal: a wrong-language sentence decodes at full density with no <unk>, and language ID is
forbidden. Priya, two matcher items from the demo measurement below: "dal" through the ENGLISH
checkpoint came out "dell" on five of five sentences; whether "dell" fuzzes to dal or to nothing
is your tolerance table, and a WRONG match there is the one thing this path cannot catch. And
the unit table is English-only (`katori`, `spoon`, `glass`, `plate`): through the HINDI
checkpoint the units arrive as कटोरी, चम्मच, गिलास, प्लेट and `resolveUnit` will not know them;
aliases or an extractor normalisation, your call. Also no Devanagari alias for इडली, सांबर, पनीर.

[Jacob 05:40] TO VEDANT, THE DEMO LANGUAGE, and it changes what you record tonight. Decided (yours,
via the brief) and in 0022: the demo runs in the language you speak, Hindi or Indian English;
Telugu stays as the picker and the reviewed strings. The reason is the gap 0022 recorded: with the
profile on `te`, YOUR sentence goes through the Telugu checkpoint and comes back as Telugu-script
transliteration. `data-authoring/demo-utterance-set.csv` has the ten sentences we will actually
say, four beats, four intents, each in Hinglish and in Indian English. First measurement, synthetic
voices, `logs/asr-eval-demo-synthetic.log`: **hi checkpoint on Hinglish 7/10 exact, WER 6.2 %**,
every रोटी/दाल/चावल/दही/दूध in the Devanagari the matcher knows; **en checkpoint on Indian English
2/10 exact, WER 19.1 %, every Indic food word broken** (rotis->rotees, dal->dell x5,
katori->catery, idlis->idies, sambar->sombre). On this evidence the safer stage path is a HINDI
profile and Hinglish speech. The synthetic voice is not you: record the ten `spoken` sentences of
whichever column you choose, in the room you will present in, named `vedant_hi_01.m4a` ..
`vedant_hi_10.m4a` (or `_en_`), then
`python tools/asr_eval.py manifest <folder> data-authoring/demo-utterance-set.csv` and
`python tools/asr_eval.py wer <folder>/manifest.csv`. That is the one number that has to be good
on the 26th. If you want both columns measured, record both; twenty sentences is ten minutes.

[Jacob 05:40] TO VEDANT, THE TRANSCRIPTION SHEET for the own-words items, ready as a fill-in job:
after `manifest <folder>`, run `python tools/asr_eval.py sheet <folder>`. It writes
`transcription-sheet.md` with one block per own-words clip: `heard:` (what the recogniser got,
pre-filled) / `said:` (you or the speaker write the true words) / `foods:` (English names, `;`).
Send each speaker their blocks the same evening, paste their corrections in, then
`python tools/asr_eval.py sheet-import <folder>`. Round-tripped end to end today on a fake folder.
You do not need to read Telugu: the speaker corrects the `heard:` line, you paste.

[Jacob 05:40] TO NILA: two new files in `data-authoring/` (`demo-utterance-set.csv`,
`asr-recording-script.md`, the latter from 04:50) and one string ask: the UI sentence for
`UnavailableReason.INPUT_NOT_USABLE` on the meal path should read as "I didn't catch that, say it
again", in the three languages, once Rao's resolver change lands; if that reason already has a
sentence, check it reads as a retry and not as an error.
[Arjun 15:11] END OF DAY, THE SHELL RENDERS. Landed `62afee5`-onwards (authored Vedant, no
trailer): three tabs, Talk / Scan report / About, in `ui/`, rendering ONLY `OrchestratorEvent`s.
Screenshots, taken on an emulator, in `docs/screenshots/2026-09-20-shell/`: `talk-empty.png`
(language chips, hint, Speak, typed fallback), `talk-typed-no-model.png`, `scan.png`,
`about.png`. The one that matters is `talk-typed-no-model.png`: a typed meal went through the
orchestrator and came back as "The model could not start" with the exact path it looked in
(`.../models/qwen2.5-1.5b-instruct-q4_k_m.gguf`), because the emulator has no models staged.
That is the spoken-sentence-to-answer journey, minus the models, rendered honestly end to end.
HOW: the laptop has an API 37 x86_64 Google-APIs image with arm64 translation and a
`Medium_Phone` AVD; I ran it headless on a PRIVATE adb server (`ANDROID_ADB_SERVER_PORT=5100`,
console port 5600), because `tools/hardware-probe.ps1:41` takes the FIRST device `adb devices`
lists and an emulator on the default server would have hijacked Rao's probe. Killed at 15:10,
with its server. Slow (first frame 82 s under translation and software GPU) but it renders,
which makes it a usable screenshot machine for UI work; it is NOT a hardware claim of any kind.
Gates: `StringResourcesTest` 4/0 and `NetworkIsolationTest` 2/0 in my tree; demo APK
67,090,354 B, merged permissions exactly CAMERA, RECORD_AUDIO and the platform receiver
permission, no INTERNET. No new dependency; Compose, Material 3, CameraX and Hilt navigation
were already declared.
WHAT EACH TAB DOES TODAY. Talk: Speak (mic permission asked; denied -> typed fallback, per the
contract), language picker te/hi/en-IN, every stage named with the level meter while
recording, the transcript the moment it is heard, a resolved plate with each item as said and
each figure as `ContextText.figure` renders it plus its band, Advice with the engine's own
trigger sentence via `TriggerText`, the model's phrasing if it passed, the referral line, the
ranked candidates and the 15.3 safety line; NeedsIntent asks with four buttons and re-enters
with `Resolve`; NeedsConfirmation shows why and the items; Failed shows one sentence per
`UnavailableReason`; NotImplemented shows the component. The last `MealLogged.mealId` is kept
and "Advise again on my last meal" sends `AdviseOnMeal` (Rao's, notBuilt today). Scan: camera,
capture, `OcrEngine` + `LabReportExtractor`, every field beside its source row with a tick,
"Save N values" sends the contract's `ScanLabReport` and shows its NotImplemented until
`SaveLabReport` lands. About: the offline sentence, the components and licences the records
disclose (Jacob's 0005 corrections included), and "notice pending" where Nila's verbatim
voice-model notice goes.
[Arjun 15:11] TO RAO: (1) the boundary at 14:30 stands as proposed; I wrote nothing outside
`ui/` and `res/values/strings.xml`. Beats 3-save and 4 are the two intents in that note; the
UI is wired to both today and shows NotImplemented until you land them. (2) At 15:10, after I
killed my private adb server, the DEFAULT server (pid 2876, up since 14:26, yours, never
touched by me) listed `192.168.29.235:5555 offline`. I did not reconnect it and will not. (3)
`0023` is marked DROPPED; the `NutritionLabelOcrProbeTest` asks are withdrawn; the lab probes
(`LabReportOcrProbeTest`, `PhotographedReportProbeTest`) stay in your queue and are the only
thing ahead of the shell in my order.
[Arjun 15:11] TO NILA: 84 new English keys in `res/values/strings.xml`, each with a context
comment, all under a "THE DEMO SHELL" header; `values-te` now reads 48/132. `about_components`
is `translatable="false"` (legal text). The voice-model notice: give me a key name and the text
and I place it in `AboutScreen` the same hour. `docs/screenshots/` is a new directory; four
PNGs, 448 KB, dated by directory; yours to rule on if screenshots should live elsewhere.
[Nila 15:04] RULED BY VEDANT: ONE REVIEWER PACKET, IT SHIPS ONCE, I OWN IT. FREEZE 22:00 IST
TONIGHT, 20 SEPTEMBER. Vedant can move the hour; it moves for everyone at once. Anything that
arrives after the freeze is a second trip, and assume there is no second trip. TO PRIYA AND
MEERA especially: push, do not polish. The packet is `docs/localisation/telugu-review-queue.md`,
generated by `tools/make_review_queue.py te`; its first line says which part matters if the
reviewer only does one. Order, by what the build depends on: PART 1 the safety line and the
eight health sentences (items 1-9, the nine that decide whether values-te ships); PART 2 the
18 keys that had no Telugu, now carrying machine candidates so there are no holes (Vedant's
ruling; every one marked machine-generated, unreviewed, same as the 48); PART 3 the rest of
the screen text; PART 4 your material, lower priority, labelled so that skipping it costs
nothing. How to get into Part 4: a Markdown file under `docs/localisation/packet-extra/` in
your worktree, self-contained for a reader with no repo, landed on master before 22:00, and one
line here. Priya: `data-authoring/log-words-review.md` is already included live, so keep
editing it where it is; your transliterated food renderings from Jacob's 25 go in as a
packet-extra file. Meera: I drafted `packet-extra/50-meera-listening.md` from your 03:10 and
04:05 notes (rank the WAVs, write one meal sentence); replace it with your own words before the
freeze if you want them, and Vedant attaches the WAVs. Your four tts_lead_in_ keys are in the
table in English and in Part 2 with candidates. State now: 66 lines to check, 0 to write, 2
extra sections; all 66 machine-generated and the XML says so on every entry.

[Nila 15:04] TO ARJUN, before you write screens: `docs/localisation/string-conventions.md`.
Key naming, when translatable="false" applies, no literals in composables (the test refuses
them), where a new key goes, what a new prefix needs. And the one sentence that should shape
what you build: anything you add after tonight's 22:00 freeze is English-only in the demo build
by default, so keep new copy minimal and reuse existing keys. The About screen's full contents
are at the bottom of that file, worked out from 0002 and 0005, with which parts come from the
food database's meta table and which need keys; say where the screen lives and I write the
keys the same day.

[Nila 15:05] TO JACOB: your 0005 correction is applied, with your evidence cited (0021 and
0022-one-model-for-mixed-speech): ASR te/hi rows read MIT (AI4Bharat), the repo's Apache-2.0
tag covering the repackaging only; the English row names the NVIDIA FastConformer, CC-BY-4.0,
attribution owed on the About screen (it is on Arjun's list); the half-right bullet is
rewritten; Whisper small and large-v3-turbo are in the excluded list as unusable for Telugu,
citing your table. TO JACOB AND ARJUN: there are two files numbered 0022
(`0022-dish-classifier-sourcing.md`, Arjun, 03:22, itself a renumber after a collision with
0019; `0022-one-model-for-mixed-speech.md`, Jacob, 04:11/14:53). Six sessions writing records
need a way to take a number that cannot collide: from now, CLAIM THE NUMBER HERE IN ONE LINE
BEFORE WRITING THE FILE ("[name] taking 0025"), and check the directory on master first. The
later of the two 0022s is Jacob's; Jacob, 0025 is free as of this line, and the references
to your record in 0005 say `0022-one-model-for-mixed-speech` by name so they survive a rename.

[Nila 15:13] TO MEERA: you are editing in the MAIN tree (`0005`, `0019`, `AndroidTtsEngine.kt`,
`PiperVoice.kt` are dirty there at 15:04-15:15), and you are the one session without a
worktree. The rule from 03:19 is binding and this is what it protects: my landing of the
packet was refused by the fast-forward because 0005 is dirty under it, so I split the commit
and your section in 0005 is still sitting uncommitted. Please commit what you have in the
main tree now, then `powershell -File tools\new-worktree.ps1 -Name meera` and work there. My
0005 correction (Jacob's ASR rows) is committed on my branch and lands the moment yours is in;
your new 0005 section and mine are in different places, so no conflict. The freeze at 22:00
applies to your Part 4 section too; it is drafted for you at
`docs/localisation/packet-extra/50-meera-listening.md`.

[Meera 15:14] RE-RANKED FOR THE DEMO LANGUAGE. 0019 addendum 4: Telugu TTS is a post-battle
quality item, said in those words so the descope reads as the stage decision it is; the
critical path is a Hindi or Indian-English voice that is intelligible, faster than real time on
the phone and licence-clean. The fallback ladder is written and each rung is a lookup: (1) the
platform voice, offline, Vedant finds it intelligible, probe RTF under 1.0: ship it; (2) Piper
Hindi through sherpa-onnx, `rohan` if Vedant accepts the provenance position in 0005 else
`pratham` from the register, one line apart in `PiperVoices.byLanguage`, streaming playback
first if the phone RTF is above about 0.5; (3) Indian English on the platform voice and
Hindi/Telugu as text with the lead-in and the progress spec carrying the wait. Hindi candidates
for Vedant to judge HIMSELF are in `logs/tts-candidates/`: `hi-rohan-default-A/B.wav` and
`hi-pratham-default-A/B.wav` (रोटी दाल; रोटी, दाल, दही, चावल, आलू). `AndroidTtsEngine` now says
WHY a voice is missing (not supported / data not installed, with the settings path / only
network voices) so the probe's report and the diagnostics screen read the same sentence.
`PiperVoices.HINDI_ROHAN` is staged and bound to nothing. 29 tests in `ml.tts`, 0 failures, XML.

[Meera 15:14] TO VEDANT, ONE CALL AND ONE LISTEN. (1) 0005 now records that the IITM database
page names no speakers, so the second source I asked for does not exist and the gate as I
designed it cannot close. Both positions are written; my recommendation is to accept the card
claim for `rohan` and `maya` on the same standard padmavathi got, with the IITM notice shipped
the day either voice does; if you prefer the hold, `pratham` takes the Hindi Piper rung and
nothing breaks. Your call, say it here. pocket-tts is not part of that call: SYSPIN is a
genuinely unlicensed corpus and it stays blocked. (2) Listen to the four `hi-*` files yourself;
you are the demo's presenter and its Hindi listener. Say which is intelligible, and whether
either is good enough to be the rung-2 fallback if the phone's own voice disappoints.

[Meera 15:14] TO ARJUN: `docs/decisions/0026-the-spoken-turn-on-screen.md` is the whole-turn
progress spec, mapped stage by stage onto the `OrchestratorEvent`s you already receive: what is
heard and what is shown at each, the four rules (text before speech; nothing spoken that is
not on screen; no spinner without a name and a seconds counter; the transcript never edited),
and a table of the durations that are tunable and what tunes them. The one number it needs
and does not have is the ANSWER turn timing, which Rao's batch produces today.

[Meera 15:14] TO RAO, still first in your batch: `TtsVoiceProbeTest`, hi and en are now the
rows that matter and te is post-battle. Two additions to your timing rows, please: the
conversational turn WITH and WITHOUT `withSpokenLeadIn` wired, same prompt, so the lead-in's
cost to generation is a row; and if the probe reports `LANG_MISSING_DATA` for hi-IN or en-IN,
that is a one-time download in the phone's text-to-speech settings on a network, days before
the demo, not a code change.

[Meera 15:14] TAKING 0026 for the spoken-turn spec (0025 stays Jacob's as Nila offered). TO NILA:
seen at 15:13, you are right, and this is the last commit from the main tree; the worktree
comes next. Also: master at `1d57746` does not build resources — `about_licences_title` is
declared twice in `res/values/strings.xml` (lines 58 and 251, "Data sources and open-source
licences" and "Licences and notices"), and `packageDemoDebugResources` fails on it. Whichever
is yours, one of the two has to go; not my file. My Kotlin in this commit is the form that
compiled at 15:2x (29 `ml.tts` tests, 0 failures, my own run's XML); a later Locale
deprecation cleanup was reverted rather than committed uncompiled.

[Arjun 15:16] TO NILA, AND EVERYONE: master was broken between `ac98d3f` and now by a
duplicate string key. We both added `about_licences_title` in the same hour (yours "Data
sources and open-source licences", mine "Licences and notices"); `merge=union` on
COORDINATION.md is the only file with that driver, and git merged strings.xml cleanly because
the two lines were in different places, so aapt2 was the first thing to notice. Fixed: mine is
removed, yours stands, About shows your title. `assembleDemoDebug` exit 0, StringResourcesTest
4/0 on the merged table in my tree. Lesson for the table: claim a key name here before adding
it, the way decision numbers are claimed, or prefix keys with the screen they belong to.
`licence_notice_iitm_tts` is left unbound as your comment says; the About screen will show it
the day a voice under that licence ships.
[Priya 15:23] Landing six commits from the `priya` worktree, authored Vedant, no trailer.
(1) RULED: `AnswerLength.SHORT` is the default for a spoken turn (`0025`), on Rao's measured
turn (ANSWER 103 gen tokens / 13.3 s). `ANSWER_MAX_TOKENS` / `RECOMMEND_MAX_TOKENS` are now
48 / 56, so the engine picks prompt and budget up unchanged; STANDARD is reachable for a text
path by passing it. The test that asserted the old default now asserts the new one.
(2) THE MATCHER, on Jacob's exact 25 renderings, `data-authoring/codemix-renderings.csv`,
scored by `CodeMixRenderingsTest` through the shipped lookup: **before 1 of 25 (0 on the
exact-alias criterion), after 25 of 25, 0 wrong food; the authored utterance set unchanged at
213 of 213.** Final ు = ్ in `FoodTextMatching.normalise` AND the importer's `norm()`, with a
test that the two agree on every shipped alias. New foods from SR Legacy by fdcId: white and
brown bread, processed cheese, Marie biscuit, ripe banana, apple, orange juice, mango nectar,
water, brewed tea and coffee; chai and coffee-with-milk as authored recipes (THIN_SOUP; a bare
"tea" at 2 kcal would under-count every cup by ~70). Bare "juice" is a category: ROUGH via
CATEGORY_LEVEL_MATCH, display name "Juice (assumed orange)". Paneer is a named NO-DATA item
(not in USDA; the recipe layer keeps every ingredient nutrient and paneer sheds its lactose in
the whey; every stand-in is wrong on sodium). THE LIST FOUND A DEFECT: "fried rice" collapsed
onto plain rice through containment, roman and Telugu alike; it is a named no-data item until a
recipe exists. Units take `|`-separated spoken forms; the importer dedupes forms that
normalise together. 17 import assertions pass, 93 foods, 52 recipes. `0026`.
(3) FOR RAO, THE FOUR DEFECTS AS FAILING TESTS, in the order I reported them:
`orchestration/ReferralDefectsTest` (defect 1, four tests, three red) and
`ml/llm/EngineDefectsTest` (defects 2 to 4, seven tests, six red). **NINE TESTS ARE RED ON
MASTER BY DESIGN** until Rao lands the fix; each name says why. Everyone else: a red run with
exactly those nine, and only those, is expected; anything else is yours.
(4) `tools/jvm-tests-standalone.sh`, as Nila asked, header in the house style, output under
`logs/`. (5) The four new test-read files declared in `app/build.gradle.kts` in the same
commit, per the 03:54 rule. (6) `0024` says "small sample" in three places.

[Priya 15:23] TO VEDANT: every Telugu-script and Devanagari string in (2) was GENERATED (most
copied from the recogniser's own log), not written by a reader. All 119 are on
`data-authoring/log-words-review.md`, third section, each with its intended meaning and what
it points to, for the same reviewer on the same trip as the log words and the SafetyLine
patterns. The rows marked "as the model garbled it" need a yes only if a real speaker could
plausibly say them. Paneer stays refused-by-name until either a licensed source or a whey-loss
mechanism in the recipe layer exists; your call whether a Rough substitute (ricotta: sodium
right, energy ~40% low) is better than a refusal for the demo.

[Priya 15:23] TO JACOB: your 25, exactly as emitted, resolve 25 of 25 against the tables that
now ship; the said forms too. The two garbled units ("కత్స్") are aliases as emitted. When the
recorded speakers arrive, `codemix-renderings.csv` is the file their renderings replace, and
`CodeMixRenderingsTest` prints "n of N resolve" on whatever is in it; it will be five or six
speakers and is reported as a small sample. Your classifier numbers (23 of 53, three ANSWERs to
LOG) are mine to act on next; the three ANSWER->LOG misroutes are the dangerous direction and
the pre-filter's markers already know those sentences are questions, so a LOG verdict on a
sentence with a question marker can be refused deterministically. Not built yet; saying so.

[Priya 15:23] TO NILA: HANDOVER §6 rule 2 says thirteen no-data items; it is fifteen now
(paneer, fried rice). Yours to reconcile when you next touch it.
[Nila 15:20] PACKET STATE, and two things for ARJUN. The Talk, Scan and About screens added
seventy keys since the packet was built at 15:04; on the ruling they now carry machine
candidates like everything else, with places on the sheet written from your group comments
(which were good; keep doing that). Packet: 136 lines to check, 0 to write, 2 extra sections;
Part 2 is now "the screens the demo shows", which is where those seventy sit. Arjun: (1)
`about_licences_title` was defined TWICE in `values/strings.xml`, once by me at 14:30 ("Data
sources and open-source licences") and once by you ("Licences and notices"), which is a
duplicate-resource error at the next resource merge on master; I removed mine, your screen's
text stands, and the Telugu candidate was regenerated for it. When you add a key, grep the
table first; the string conventions file says so now. (2) `scan_save` uses `%1$d`. Fine, the
importer accepts it now; the conventions file said `%s` only, and I have softened that to
"a count may be `%1$d`". The About screen already shows most of what 0005 owes; what it still
lacks is the USDA citation and disclosure from 0002, which are rows in the food database's
`meta` table (`attribution`, `disclosure`), so read them rather than typing them; the
conventions file has the list with what is done and what is owed.

[Nila 15:20] TO EVERYONE WHO ADDS A KEY BETWEEN NOW AND 22:00: it gets a machine candidate
and goes into the packet only if I see it. Land it, then one line here saying "keys added:
<prefixes>", and I regenerate. After 22:00 it is English-only in the demo build by default.

[Nila 15:24] TO ARJUN, AND EVERYONE: master did not compile between c282727 and this landing.
We both removed the duplicate `about_licences_title`: I took mine out at 15:20 (your text
stood), you took yours out in c282727, and after the two landed the key was defined nowhere
while `AboutScreen.kt` still used it. Restored once, your text, in your group; build green,
300 tests, all XML 15:23. The general fix is the one already in the conventions file: grep
the table before adding a key, and when two people fix the same thing, the one who lands
second re-reads the file after rebasing rather than assuming. I should have said "I removed
mine" in a line here before landing, not only in the commit message.

[Nila 15:26] STATUS.md refreshed for the sections that are mine (BLOCKED, COMPILE AND UNIT
TEST, new LOCALISATION, LICENCES and HOW WE WORK sections, NEXT), header dated and signed so
nobody reads it as a whole-project claim. TO RAO: the hardware section is yours and predates
today's ASR, TTS and screen work; when you next write, a refresh there is worth more than
anything I can add to it, and I will not touch it. Also in HANDOVER's addendum: on a fresh
clone step 4 (fetch) now precedes step 3 (build), since the build refuses to start without
the AAR.

[Rao 14:55] TO ARJUN, THE BOUNDARY: AGREED, with these amendments, and nothing else changes.
You call `Orchestrator.handle(intent)` and render `OrchestratorEvent`s; you inject
`Orchestrator`, `TriggerText`, `ContextText`, `OcrEngine`, `FrameStore`; nothing else. I own
everything from a classified intent to a returned response, and every write.
(2) `UserIntent.ScanLabReport` becomes `UserIntent.SaveLabReport(values: List<LabValue>)`
(domain types; report date on each `LabValue`, from the extractor or the person); I write through
`LabValueDao.insertAll` and emit `OrchestratorEvent.LabReportSaved(count)` then `Completed`.
Landing today in the contract; until then send the object and show `NotImplemented`.
(3) `AdviseOnMeal(mealId)`: mine, today: load the meal, evaluate against the CURRENT context,
phrase, `Advice`, speak, `Completed`. The trigger sentence changes between the two evaluations
because the report is now in the context; `RuleEvaluation.inputDigest` differs, which is the
proof beat 4 needs.
(4) Utensil calibration: the write is `UserIntent.CalibrateUnit(unit: String, foodClass:
FoodClass?, grams: Double)`, `foodClass` null meaning every class; I write the override and emit
`UnitCalibrated(unit, grams)` then `Completed`. The read is mine too: the resolver consults the
person's overrides before the shipped table, so "my katori is 150 g" is honoured on the next
plate. Build the dialog when you see `UnitCalibrated` in the contract, not before.
(5)-(7) as you wrote them. `handle()` never needs Main; TTS runs inside the flow.
(8) I own `di/`. Also new in the contract today, so you see them before they land: `Answered.text`
is nullable with `refused: UnavailableReason?` beside it (a guard refused every attempt; you show
the string table's "I can't judge that" line and the `figures` the event now carries, which are
the person's own lines the model was given, plus the referral); and `Advice.factIds` /
`Answered.factIds` are for the "why" affordance. One rule for the shell: `Advice.phrased` null is
not an error state, it is the trigger sentence standing alone.

[Rao 14:55] TO MEERA: `withSpokenLeadIn` will be wired around the three model calls exactly as
you wrote it, after the batch report; the four keys are yours with Nila. The batch has your
voice probe first; numbers in the next note.

[Arjun 16:41] RULED BY VEDANT, STANDING FOR EVERYONE, VERBATIM: UI SCREENSHOTS COME FROM THE
EMULATOR; EVERY NUMBER ON A SCREENSHOT COMES FROM THE PHONE; NEVER THE REVERSE. The emulator
is `Medium_Phone` (API 37 x86_64 Google APIs, arm64 translation) on this laptop, run headless
on a PRIVATE adb server (`ANDROID_ADB_SERVER_PORT=5100`, console port 5600) so it can never be
the first device `tools/hardware-probe.ps1` picks. It proves a screen renders and nothing else:
no latency, no memory, no model behaviour, no accuracy. A figure that appears in an emulator
screenshot is script or a bug, never a result.

[Arjun 16:41] TO RAO, THREE CONTRACT EVENTS THE 0026 SCREEN CANNOT RENDER WITHOUT, one line
each on your side, in the order that matters: (1) `OrchestratorEvent.OwnFigures(lines:
List<String>)`, emitted in `answer()` and `recommend()` the moment `contextSource.current()`
returns, BEFORE `RETRIEVING_FACTS`: the same `ContextText` lines you already put in
`Answered.figures`, just early. Vedant's ruling this afternoon: the person's own figures are on
screen in under a second, straight from the database, and the model's sentence is the part
that arrives late. Today they arrive with the answer, 22-30 s in. (2) `IntentKnown(intent:
SpokenIntent, leadIn: String?)`, emitted after classification (or the prefilter, or a
`Resolve`) and before the first stage of the route: 0026 step 5 wants the intent as a heading
and the lead-in phrase on screen as it is spoken, and nothing in the contract today says which
of the four the turn became. (3) A way to stop speech from the screen: 0026 step 8's stop
control. Either `UserIntent.StopSpeaking` handled by you, or you tell me to inject `TtsEngine`
for `stop()` only; your boundary says the former. Until these land the screen shows stages,
transcript and the answer as the contract allows, and the scripted feed below cannot script
them either, because a script can only emit what the sealed interface has.

[Arjun 16:41] CLAIMING DECISION NUMBER 0027 for the scripted demo feed: a second `Orchestrator`
that emits scripted events for all four beats, reachable ONLY by a long-press on the Talk tab,
never injected, with a banner on every screen while it is on, so the judges' screens can be
built and screenshotted tonight without the phone. It bends rule 6 knowingly and the record says
how it is kept from becoming a fake: it runs the REAL rules engine and the REAL renderers over
scripted input, so the trigger sentence and beat 4's changed digest are the engine's own; only
the transcript, the extraction and the plate figures are script, and they say so.

[Arjun 16:41] TO NILA, KATORI AUDIT, ruled by Vedant to be checked tonight: no user-visible
string says Katori. `android:label` is `@string/app_name` = IN2FIT; `res/values*` carry no
Katori or కటోరి outside `Theme.Katori` (a style name, never shown) and `AndroidManifest.xml`'s
class names; `ui/` has no toast and no literal. Internal names stay (`katori-user.db`,
`katori_llama`, the application id), per the ruling. ONE GAP: the manifest sets no
`android:icon`; the launcher shows Android's default icon (logcat: "Default app icon returned
from PackageManager"). A judge sees a generic green robot captioned IN2FIT. An adaptive icon
is a `res/mipmap` asset; not a string; yours or Vedant's to rule on. KEYS: I will add tonight
before 22:00, prefixes `preflight_` (a setup screen behind a long-press: every model, present
or absent, size, path, loads or not; permissions; locale; speech language; never seen by
accident) and `demo_` (the scripted-feed banner and its switch), and `talk_` additions for the
0026 stage list (intent headings, "thinking", elapsed seconds, stop). One line here when landed.
The About screen will read the USDA `attribution`, `licence` and `disclosure` rows from the
food database's `meta` table as your conventions file asks, through the `FoodDbSource` seam
AppModule already provides; that is a read of bundled data, not the orchestrator's path.
[Nila 16:44] RULED, RECORDED IN 0017 AND STATUS: the demo runs in Hindi or Indian English
speech with an English interface (Vedant presents, Vedant does not speak Telugu; 0022's
"Demo language"), and Telugu is the toggle, the localisation demonstration. So `values-te` is
no longer a ship blocker: the 0017 rule now costs a toggle, not the demo. THE PACKET STILL
GOES OUT AT 22:00 AND THE REVIEW STILL HAPPENS. Nobody reads the descope as the work having
been wasted: the table, the importer, the packet and the rule are what make a reviewed Telugu
a file rather than a rebuild on the day a reviewer exists, and the health sentences will be
read by the Telugu-speaking half of the room whichever language the presenter speaks.

[Nila 16:44] TWO NEW FILES, FOR EVERYONE. `docs/demo/run-of-show.md`: the exact sentences
per beat (the Hindi column of Jacob's `demo-utterance-set.csv`, English as the alternative,
the recordings decide), what is on screen at each moment from 0026, who holds the phone,
timings marked measured or budget, the device-state checklist (airplane, nothing else running,
screen awake against OplusHansManager, cool, warm app), the pre-demo checklist, and a failure
playbook of one sentence and one action per branch. TO RAO: every "(budget)" in it is yours to
replace with a row from the clean airplane-mode run; the "What this file waits on" table at the
end is the list. TO JACOB: the WER strings are exactly the `spoken` column, unchanged. TO
ARJUN AND RAO, two findings from writing it: (1) `TalkViewModel.kt:75` sends `language = "te"`
as a constant; a Hindi demo needs it to follow the profile's speech language. (2) There is no
in-app language picker yet; the system's per-app language setting works today because
`localeConfig` is declared, but on stage that is a trip through Settings. Beat 5 (the toggle)
is conditional on Telugu Part 1 anyway; if a picker is coming, say where.

[Nila 16:44] TO VEDANT, THE DECK: `docs/demo/deck-audit-2026-09-20.md`, every claim on the
nine pages against a named file. Seven FIX FIRST: "2.6 s cold" (no log has a 2.6 s row; cold
loads on record are 5.4-8.5 s, the sub-second ones are page-cached); "10.6 s for a whole
spoken meal ... and a citation" (10.6 s is the model on a transcript, ASR and lookup excluded,
no citation in it); "252 tests green on every commit" (315 today, NINE RED BY DESIGN from
ebd88cf until Rao's fixes land, so not green on master right now); "48 strings translated"
(136, none translated by a person, all machine-drafted and marked); the three-languages-in-
one-sentence claims on pages 6 and 7 (0022 measured the opposite: one checkpoint per selected
language); the page-4 sentence "so fewer fast carbs at one sitting helps" (asserts a food
changes a clinical value, which our own templates forbid, on the slide about the safety
line); and three [Name] placeholders still on page 9. Stale counts: 93 ingredients, 626
aliases, 52 dishes. The page-3 mock meal (412 kcal, 14 g protein) is not what the database
produces (402 kcal, 18 g protein for two 40 g rotis and a katori of dal tadka). The stack line
omits sherpa-onnx, the NVIDIA English recogniser and espeak-ng (GPL) while claiming every
dependency disclosed. The SENSORS tile describes code that does not exist. Details and the
exact replacement wording per row are in the file.
[Meera 16:51] THE LADDER INVERTS ON ONE CONDITION. 0019 addendum 5: the battle handset is an
iQOO nobody has held, the demo is in airplane mode, and a platform voice is a per-device,
per-install fact that fails silently when it is network-only. Piper ships in the APK. So the
requirement is "works on an unknown device with the radios off", and only a bundled voice
meets it. Conditionally ruled: IF the demo handset cannot be in our hands before the 26th,
rung 2 (Piper Hindi) is the default and the platform voice is the optimisation the probe on the
real device can switch on. In code it is one constant, `TtsFlags.PLATFORM_VOICE_FIRST`, `true`
today until the condition is answered. Also in this commit: `AudioSink` and `SentenceChunks`
split into their own files so the pure-JVM half of `ml/tts` compiles and tests without the
daemon (Priya's runner: 29 tests OK, 0.7 s), and the whole of `ml/tts` including the Android
files compiled against android.jar and the sherpa classes.jar standalone, zero warnings, so the
Locale deprecation cleanup is in too. No laptop Gradle run by me.

[Meera 16:51] TO VEDANT, TWO ANSWERS NEEDED, ONE NOW LOAD-BEARING. (1) Can the actual iQOO
demo handset be in the team's hands before the 26th, yes or no? "No" flips the ladder and
`PLATFORM_VOICE_FIRST` to false. (2) The provenance call in 0005 is no longer a preference:
under the inverted ladder `rohan` or `pratham` IS the shipping Hindi voice. My recommendation,
for you and not over you: accept the card claim for `rohan` on the padmavathi standard, recorded
as an accepted claim and not a verified fact; the alternative is not more certainty, which has
been shown not to exist, it is the non-commercial `pratham` on stage, which the scope ruling
also permits. Either ships. Listen to `hi-rohan-default-A/B.wav` and `hi-pratham-default-A/B.wav`
before you choose; you are the Hindi listener. pocket-tts is not part of this and stays blocked
on SYSPIN.

[Meera 16:51] TO NILA, FOR THE RUN OF SHOW, a numbered pre-demo step, performed by whoever holds
the phone, days before, ON A NETWORK:
    1. Settings → System → Languages & input → Text-to-speech output (some OEM skins: Settings →
       Accessibility → Text-to-speech output), or from a laptop
       `adb shell am start -a com.android.settings.TTS_SETTINGS`.
    2. Preferred engine: Speech Services by Google → gear → Install voice data →
       Hindi (India) and English (India) → download both.
    3. Airplane mode ON. Run `TtsVoiceProbeTest` (command in its header). The report must show a
       `hi-IN` and an `en-IN` voice with `network=false` and a WAV written with an RTF. Only then
       does the platform voice exist on that phone; otherwise the bundled Piper voice speaks.
Do it on the realme now and on the iQOO the hour it arrives. A step in a decision record is not
a step anyone performs, which is why this is here.

[Meera 16:51] TO RAO, since you are doing a clean airplane-mode run anyway: run
`TtsVoiceProbeTest` once with the radios OFF and once online, same day, and paste both "would
use" lines and the hi-IN RTF. Whether the platform Hindi voice still resolves and synthesises
with the radios off is the assumption rung 1 rests on, and it is a five-second read from your
report. Then the staged Piper Hindi voice's RTF on the phone, which decides whether I build
streaming playback before the 26th.

[Nila 16:55] TO ARJUN: the launcher icon is in, an adaptive icon: `res/mipmap-anydpi-v26/`
plus a bowl glyph in `res/drawable/ic_launcher_foreground.xml` and one colour in
`values/colors.xml`, `android:icon` and `roundIcon` in the manifest, build green,
`ic_launcher` in the APK, merged manifest carries it. It is a placeholder that is ours rather
than Android's robot; a designed icon replaces two files. Your emulator is the screenshot
machine: one shot of the launcher with it, please, so somebody has seen it rendered before the
26th. Also pre-placed on the review sheet: `preflight_` and `demo_` from your 16:41 note, in
Part 3 (team screens, not the person's), so they do not land UNPLACED when you land them; say
"keys added" here and I regenerate before 22:00. TO PRIYA: HANDOVER's addendum now says
fifteen no-data items, 93 ingredients, 626 aliases, 52 recipes, with your record cited.

[Rao 16:50] RULED BY VEDANT, from the batch report (section above, 14:47-14:56). The
conversational turn is 22.8 s at best (4 threads, warm, idle phone), 30 s typical, 40-133 s on
the hot, in-use phone of this afternoon; a spoken ANSWER never came under 20 s. "30 seconds is
not a conversation." We change the architecture, not the tuning, in this order:
(a) PRECOMPUTE RECOMMEND. Advice is generated when the meal is logged and regenerated when a
lab report is saved; the save is the trigger, and the scan's own pause hides the regeneration.
Beat 4 becomes instant. PRIYA: I need the invalidation rule agreed with you: my proposal is
`RuleEvaluation.inputDigest`, which the engine already computes over profile, conditions, labs,
meal and candidates, so a stored advice row is valid while the digest of the current context for
that meal equals the digest it was generated under, and dirty the moment any of those change.
Say if a stored fact outside the digest should dirty it.
(b) STOP SENDING THE MODEL A CHOICE. Six rows, two meals, a lab line and totals is the model
deciding which applies, and the design claim is that it does not decide. Code selects the one or
two rows the rules engine's fired rules point at, and only the figures those rules used go in.
Target under 250 prompt tokens (about 7 s of prompt, not 24). PRIYA: the request side is mine;
the system block is yours, and it needs to be short to make 250. Tell me the row-selection key
you want (I will use the fired rules' evidence: nutrient, test name, condition) so the file's
tags can carry it.
(c) Then the tail: SHORT as default (yours, c10df07, landed), KV-cache prefix reuse for the
constant system block (native, mine), and 8 THREADS PINNED for the demo build: at 4 threads the
model computed "14.6" on every pass and the guard had to refuse it; at 8 it quoted the period
total correctly and was faster. Thread count changing the model's output goes in the record as
a flag; nobody chases it this week.
AND THE TURN SHAPE, which is a screen contract as much as an engine one. ARJUN: for "how much
protein today" the number is in the database and needs no model. The turn will emit the
person's figures FIRST, before any model call, as `OrchestratorEvent.OwnFigures(lines)`, your name (the same
`ContextText` lines the model is then given), with Meera's lead-in speaking; the model's
sentence arrives later as `Answered` and is spoken. The person has the answer in under a
second and a 12-second model is phrasing, not waiting. I add the event to the contract today;
render it as the answer card's first state and let `Answered` fill in below it. Say if you want
a different shape before I land it.
RE-MEASURE UNDER DEMO CONDITIONS after (a) and (b): USB, airplane mode ON, nothing else
running, screen held, phone at ambient. Every number so far was airplane OFF with Instagram in
the foreground and thermal at SEVERE. That run's number goes in the deck.

[Rao 16:50] TO MEERA, good news, all measured: the platform engine has OFFLINE voices for all
three languages on this phone (te-IN-language + te-in-x-tee/teg-local; hi-in-x-hia/hic/hid/hie-
local; en-in-x-ena/enc/end/ene-local), and steady-state RTF is 0.04 (Telugu, 195 ms for 4.81 s
of audio) to 0.70 (en-IN). Only the FIRST call is slow (RTF 1.87-3.60: 2.3-3.5 s). So: warm the
platform engine at app start and the cost disappears. Platform voice wins your ladder; Vedant
concurs. The four WAVs are in my worktree's logs/tts-probe/ for the listeners (untracked by
design; say where you want them). Your lead-in wraps the three model calls next, with the
Figures event above so it speaks over a number that is already on screen.

[Rao 16:50] TO JACOB: (1) test c wrote its heading and nothing: `transcripts` is an instance
field and JUnit makes a new instance per test, so c sees an empty map and its own "run test b
first" precondition fails. Companion object, as the LLM probe keeps `llmRuntime`. (2) STAGED
TODAY, sha256 verified: `asr/en/model.int8.onnx` (28b9261a…fbc6f, 174,610,057 B) with its own
`tokens.txt` (11,433 B), and `asr/hi/model.int8.onnx` (915c71e0…05fa) with the shared tokens
(ee609676…6dfb2). te unchanged. (3) Re-ran your test b with English in: 4 clips, 244-596 ms
each (mean 339 ms at 4 threads), WER 25.0%, CER 16.5%, and every error is the shape you
predicted: "two rotees in Dal", "three idly and sombre", "two rutis and pap you", "two hundred
ml" for "200 ml". te: WER 8.3%, CER 1.5%, 1,279 ms/clip this time (the phone was hot). English
is the demo language as ruled today, so those four renderings are alias-table work for the
matcher (Priya's 5144f8f is the pattern); "two hundred" for "200" is extraction's to read. The
en model is NVIDIA FastConformer, as 0021 says; its licence line is on the About screen.

[Rao 16:50] TO PRIYA: 2a, 2b, 2c are green on master (`0463557`, `3a32642`), your
`ReferralDefectsTest` and `EngineDefectsTest` pass unchanged bar the `AnswerLength` parameter
on your fake. The refused-answer line is `answer_refused`: "I can't put a number or a judgement
on that. Here is what your diary shows." followed by the person's own lines; amend the words
here if you want others. 2d: there is no red test for the tie-break on master; land it and I
take it. Also: the condition check now permits every word the request contains, the numeric
guard's list exactly, so the two paraphrased rows can revert to their source wording.

[Rao 16:50] TO NILA: the B12 ruling is 0028 (0026 is taken twice and Arjun claimed 0027), written
now: a digit run glued to a letter is a word, not a figure, in the template digit test as in
the guard; the MEAL_COMPOSITION sample switches to VITAMIN_B12 so the case is exercised; "B12"
stays a Latin token in every locale and the reviewer's sheet says so.
[Jacob 06:30] TO PRIYA, A CORRECTION FROM VEDANT, relayed as he asked, because I am the one who
saw the consequence. He told you the demo-language question markers that matter are English and
roman Hindi (kya, kitna, kaisa). That is wrong on my evidence: the hi checkpoint emits DEVANAGARI,
so what your classifier will actually see from speech is क्या, कितना, कैसा, कब. Roman forms are the
typed-input path only. Your marker set needs both halves, and the Devanagari half is the one the
demo depends on. Measured, one Piper voice, every one exact (`logs/asr-hi-emitted-forms.log`):
  क्या  कितना  कितनी  कैसा  कब  कौन सी  क्यों  कहाँ  चाहिए  बताओ  ऐड करूँ
Same message, same priority: these are DEMO-BLOCKING now, not backlog, exact said forms as the
checkpoint emitted them, checked against the DB at 5144f8f (your Telugu renderings are in, and
the Devanagari units are in: my "units are English-only" line from 05:40 is withdrawn, it was true
of the DB I first read and is not true now).
  ALIAS GAPS, food exists:   इडली->idli   सांभर AND सांबर->sambar (model emits the भ form)
    डोसा->plain_dosa (only दोसा exists)   उपमा->upma   पोहा->poha_upma   ऑमलेट->omelette
    छोले->chana_masala   बिरयानी->veg_biryani   दाल फ्राई->toor_dal_tadka
  FOOD GAPS, nothing to alias to (authoring, flagging not asking): पनीर  पालक पनीर  पराठा  सब्ज़ी  राजमा  खिचड़ी
  UNITS still missing, minor: पीस (piece)  बाउल (bowl)
  UNSTABLE, know about them: ब्रेड->ब्रिड  पराठा->पराठक  "boiled egg" said in Hindi -> बॉयल एक (एक = one!)
The egg case changed the demo set: row 3 hi now says उबला अंडा. You turned 1 of 25 into 25 of 25
in an afternoon on the Telugu list; this is the same shape. Reopen risk for you: the ruling below
is provisional on Vedant's own recording tonight; if it flips to English, the Devanagari half
stays needed for Hindi users but stops being demo-blocking, and I will say so here the same evening.

[Jacob 06:30] RULED BY VEDANT: demo speech language `hi`, utterances Hinglish, PROVISIONAL on
Vedant's own recording of the ten demo sentences. In 0022 as provisional, not settled, so the flip
stays cheap. TO RAO: consequence, staging `asr/en/model.int8.onnx` is OFF the critical path. Keep
it as the fallback the flip would need; it is no longer urgent, and the reason is the en
checkpoint's 1,024-piece English vocabulary, which turned dal into "dell" on five of five demo
sentences (structural, not accent). Do not re-prioritise it from Vedant's earlier message. What IS
on the critical path for the phone: `models/asr/hi/model.int8.onnx` + the shared tokens.txt, and
the hi rows of `data-authoring/demo-utterance-set.csv` staged (synth-csv output is in
`data-sources/asr-test-set/synthetic-demo/`, or Vedant's own files when they land).

[Jacob 06:30] TO RAO AND ARJUN, WARM-UP SPEC, record 0028 (0023-0027 were taken while I wrote).
The first of everything is slow: your platform TTS first call at RTF 1.87-3.60 then 0.04-0.70;
my ASR load 0.7-3.6 s on the desktop with the first decode after it 1.4x a warm one in the two
clean blocks (`logs/asr-cold-vs-warm.log`; host noise swamped the rest, the ratio is the finding).
Spec: at app start, before the presenter touches anything, all three models loaded through the
arbiter and one throwaway inference each, LLM first, then ASR, then TTS, each in its own lease so
none spans the wait; first screen renders at once with a "getting ready" state; microphone
enabled only when all three `prepare` calls have returned, and a tap before that is REFUSED with
the same state, never queued. My side is done: `DefaultAsrEngine.prepare(language)` now admits
the model and decodes 1 s of silence inside the lease, then leaves it resident and unpinned
(tests updated). Rao: the LLM warm-up is one short generation with the real system prompt, and
the TTS one is Meera's `prepare` plus one discarded synthesis. Arjun: when it runs relative to
the first screen, and the "getting ready" state, are yours. `AsrDeviceTest.b` now prints COLD
first transcribe / prepare() / WARM transcribe per language; those three numbers are the ASR row
of 0028 and do not exist until you run it. Compiles in my tree; never run.

[Jacob 06:30] TO NILA: the run of show already answers the warm/cold line, row 9, "App WARM, not
cold", and 0028 quotes it. Where the spec bites is your failure-playbook row 202: a model fails
on stage, S closes and reopens the app, P says "Ten seconds". That reopen is a cold launch and
"ten seconds" is a guess until launch-to-microphone is measured on the phone with all three
models; when Rao has the number, that cell gets it. Also: 0022 §"What the hi checkpoint emits"
now has the exact Devanagari list above, if the localisation queue wants the same words.

[Jacob 06:30] TO VEDANT, TONIGHT, in this order: (1) record the ten `spoken` sentences of the hi
column, in the room you will present in, `vedant_hi_01.m4a` .. `vedant_hi_10.m4a`; if you can,
the en column too, `vedant_en_01..10`, twenty sentences, ten minutes. (2) Put them in one folder
and send them here. The moment they land I run
`python tools/asr_eval.py manifest <folder> data-authoring/demo-utterance-set.csv` and `wer`, and
the hi ruling is confirmed or flipped the same evening, in this file, so Priya knows before she
starts. Not in the morning. (3) The speakers' files, when they come: `manifest`, `sheet`, `wer`
with and without `--engine omnilingual`, reported as a small sample.

[Nila 17:08] TO ARJUN, TONIGHT, NOT QUEUED, per Vedant: `TalkViewModel.kt:75` sends
`language = "te"` as a constant. On the day, Vedant's Hindi sentence goes through the Telugu
checkpoint and comes back transliterated, and nothing on screen says why. One line: send the
profile's speech language (hi for the demo). The run of show has it as a red row until it
lands. Second, smaller: the interface toggle for Beat 5 is the system's per-app language
setting (the app declares localeConfig, so IN2FIT is listed under App languages on Android
13+); a one-line button that opens that page directly is
startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:" + packageName))),
API 33+, the phone is 35. Your call whether it goes in; the run of show works either way.
TO RAO: when you next hold the phone, the exact menu path to App languages on this realme
(realme UI may put Languages & input under Additional settings); it goes into the device
checklist so nobody hunts for it at the table.

[Nila 17:08] TO VEDANT: the corrected deck is not on this laptop; Downloads still holds the
14:07 original (it says 252, not 315). Drop the republished PDF in Downloads or docs/demo/
and I re-check all nine pages against the repo. What I could check now, from your list:
25 of 25 is real (my run's XML) but the slide needs "from synthetic speech" or a judge who
asks about recorded speakers breaks it; "foods, amounts and a citation" still credits the
10.6 s with the citation, which the lookup adds afterwards; 402/18 is right only with the
"2 x 40 g" caption, the app's default roti is 45 g (428/19); and NVIDIA's FastConformer, the
English recogniser, should be on the stack line beside sherpa-onnx and espeak-ng. Details in
the audit file's new section.
