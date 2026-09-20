# IN2FIT status

Updated 20 Sep 2026, 15:30, by Nila, for the sections that are hers: BLOCKED, COMPILE AND
UNIT TEST, LOCALISATION, LICENCES, HOW WE WORK, NEXT. The hardware section is Rao's and is as
he last wrote it. What the other five sessions built today is in their records, `0018` to
`0024`, and in `COORDINATION.md`; this file does not restate their claims.

The product was renamed from Katori on 20 September (`0015`); display strings carry the new
name, the package and `applicationId` keep `katori` until after the hackathon because the JNI
symbol names encode it.

---

## BLOCKED ON VEDANT

**A fluent Telugu speaker, once.** The reviewer packet is `docs/localisation/telugu-review-queue.md`,
frozen at 22:00 IST tonight, one file, one trip (`0017`). Its first line says which part
matters if they only do one: the nine lines of Part 1 decide whether the app ships Telugu at
all. Every one of its 136 lines is machine-generated and marked so; the reviewer confirms or
corrects, in the file or as a numbered chat reply, and it goes in through
`python tools/import_review_queue.py te`, never by hand. The check file it writes goes back to
the speaker with its question at the top.

**The IITM licence PDF**, to diff against the machine-converted text now in
`docs/licences/iitm-tts-eula.txt`. Nothing is ruled from that text until the diff is recorded
in `0005`. Vedant has it as his task.

**The domain question from `HANDOVER.md`.** If a domain is still coming, the package rename gets
more expensive with every commit that touches JNI. Deferred until after the hackathon by ruling,
so this is a question for later, not now.

The cloud container (`0009`) is still the fast path for JVM tests; the laptop builds everything
native and is the source of truth. The phone is Rao's, and only Rao's (`COORDINATION.md`).

---

## VERIFIED ON HARDWARE

**realme RMX3780, MediaTek MT6835, Android 15 (API 35), 2x Cortex-A76 + 6x Cortex-A55,
7,619.4 MB RAM.** Probe run 19 Sep 2026, `HardwareProbeTest`, 5 tests, 0 failures.
Report in `logs/hw-report-run3.txt`.

The app builds, installs, launches without a crash, and **llama.cpp loads and runs Qwen 2.5 1.5B
Q4_K_M on the phone**, returning schema-valid JSON on the first attempt from the real extraction
prompt. **ML Kit OCR reads text in the demo build with the INTERNET permission removed**, which
closes the residual risk in `0004`. **ASR, LLM and TTS are co-resident** at a measured peak of
2,229.9 MB against a provisional ceiling of half of device RAM, so spoken confirmation survives
and the stage-1 co-residency question from `0001` is answered.

### Extraction latency, before and after the ARM misbuild was fixed

`libllama.so` had been built as baseline ARMv8.0 on a CPU advertising `asimddp` and `asimdhp`,
so ggml used scalar fallbacks. `0011` has the detail. Same device, same prompt, warm pass:

| | prompt tok/s | generation tok/s | round trip |
| --- | ---: | ---: | ---: |
| baseline ARMv8.0, 4 threads | 11.11 | 7.08 | 26,568 ms |
| baseline ARMv8.0, 8 threads | 14.34 | 5.18 | 25,215 ms |
| **+dotprod+fp16, 4 threads** | **39.82** | **9.73** | **10,977 ms** |
| **+dotprod+fp16, 8 threads** | **48.07** | **8.98** | **10,632 ms** |

Prompt processing 3.4x, generation 1.4x, round trip **2.4x**. Thread count barely matters once
the build is right: 3% between 4 and 8.

### Extraction correctness, measured alongside the speed

Five transcripts run against the real model on the device, checking properties rather than exact
strings: every named food present, a stated quantity captured as the number they said, and an
UNSTATED quantity left null. **4 of 5 pass.** The failure is "I drank 200 ml of milk and ate one
boiled egg", where the model returns the milk and drops the egg. It fails the same way with a
longer and a shorter prompt, so it is a model-capability finding rather than a prompt one.

### The budget was a guess and the measurement replaced it. `0014`

Spec 10.5's 3.5 s was written before anything had run. Extraction alone measures **10.7 s**, with
ASR and TTS neither written nor included. The prompt was shortened from 207 to 162 tokens to buy
that back; it bought nothing and cost a food, because **generated tokens cost about five times
what prompt tokens cost on this device** (38-52 tok/s in, 9-10 tok/s out). A cheaper prompt that
makes the model emit a fenced, pretty-printed answer is a net loss. The shortening was reverted.

Attacking the ANSWER instead was tried next, since generated tokens are where the time is: the
model was told to leave `quantity`, `unit` and `method` out when nothing was said, which the
strict reader already accepts. **Also reverted.** The correctness cases passed and the very next
test refused the same transcript with `'quantity' is not a number` at a different thread count.
Valid at eight threads and invalid at four is a prompt sitting on a decision boundary, not a
working one. Two latency ideas, both killed by measurement, both for the same reason: the saving
was real and something else got worse.

**The UI carries the budget.** Ten seconds of silence reads as a hang; ten seconds of visible
progress reads as work. Recording meter, acknowledgement at endpoint, progress through extraction.

Still unverified on hardware: ASR accuracy, TTS quality, camera capture, the Room database, and
every pipeline above the engines, because none of them is implemented.

---

## VERIFIED ON DESKTOP, WHICH IS NOT HARDWARE

**llama.cpp loads and runs Qwen 2.5 1.5B Q4_K_M.** On a 2-core Linux VM, CPU only:
load 10.1 s, 9.89 tok/s eval, and on the real extraction prompt it returned valid JSON first
try: `{"rotis": 2, "dal": "a katori"}`.

**Nobody may quote 9.89 tok/s as a Katori number.** Two desktop cores are not a phone. It means
the runtime and this model file work together, and nothing else.

**libllama.so cross-compiles for arm64-v8a** against NDK 28.2.13676358 at minSdk 26, plus the
three ggml libraries, and `tools/build-llama-android.ps1` now stages all four into `jniLibs` and
fails if one is missing (`logs/llama-android-build.log:96-101`; the copy step was absent until
`d23cc53`). The paragraph that stood here saying the libraries had never been loaded is
superseded by the hardware section above.

---

## VERIFIED ON EMULATOR

Nothing. Not built.

---

## COMPILE AND UNIT TEST ONLY

**300 tests in 27 classes, 0 failures, 0 errors**, summed from the per-class JUnit XML under
`app/build/test-results/testDemoDebugUnitTest/` in the `IQOOOOO-nila` worktree, every file
written at 15:23 on 20 Sep by the run in `logs/packet3-build.log`. A test count is valid only
if every XML in that directory was written by your own run (binding rule, `COORDINATION.md`
03:19); before the worktree rule, a run from another session cleared that directory under one
of mine and it held 13 tests where 245 had run. The earlier "103 tests" figure was a count of
`@Test` methods in source, not a number read from a log; the Gradle logs it cited print no
count.

**Demo APK 67,201,412 bytes**, `lib/arm64-v8a` only, sha256 `26d81ef9…`, row of 15:21:46 in
`logs/apk-size.log` (main tree), built in `IQOOOOO-nila` at `046df66` with the string tables
of that moment. Every APK figure is a row in that append-only ledger, written by
`tools/apk-size.ps1` after each assemble with the commit, a dirty flag, the APK's own hash and
the tree that built it; a size that is not a row there is not a figure. Composition of the
03:00 build, read from the file (the later ones differ by string tables only): 46,401,680 B of
native libraries stored uncompressed (sherpa-onnx 24.2 MB, ML Kit OCR 11.1 MB, barcode
4.9 MB, llama.cpp 6.1 MB), 44,544,336 B of dex deflated to 17,057,717, 3.8 MB of assets.

Two earlier figures do not survive: "46 MB" was a container build with no NDK and no `jniLibs`;
76,661,867 B (01:28, `logs/nila-ort-final.log`, before sherpa-onnx) is 9.8 MB LARGER than
today's build although today's carries 26 MB more content, and the listing taken of that APK at
the time summed to less than the file itself. That APK is gone from disk and the difference is
NOT explained here; it is recorded so nobody quotes either number as a trend. ONNX Runtime stays
test scope (`0016`, amended); with it in the app the APK measured 109,661,661 B on 20 Sep 01:23.

Demo manifest permissions asserted against a whitelist as exactly CAMERA, RECORD_AUDIO and the
platform receiver permission on every `assembleDemo*`; `logs/merged-manifest-demoDebug.xml` is
the merged result. No INTERNET permission survives the merge.

The JNI shim compiles, links, and has run on the phone: see the hardware section. The claim
boundary in `docs/decisions/0010` is superseded on that point.

### Measured match rate, the spec 13.5 replacement

    ingredient utterances    210    resolved correctly  210  (100.0 %)
    no-data utterances        22    refused correctly    22  (100.0 %)
    WRONG FOOD                 0    <- the number that matters

> **THIS 100% IS CIRCULAR AND MUST NOT BE QUOTED TO JUDGES.**
>
> The utterance set was written by the same people who wrote the aliases, so it can only
> contain phrasings somebody already thought of. It cannot contain the phrasing nobody
> anticipated, which is exactly the case that will come up on stage. The dish rows added with
> the recipe layer are worse: the dish names and the recipes they point at were written by the
> same hand on the same day.
>
> What the number IS: a regression guard. If a change breaks a phrasing that used to work,
> or resolves something to the wrong food, the build fails.
>
> What it is NOT: a measure of real-world coverage, and not an accuracy figure. There is no
> real-world number yet and there will not be one until Abhinav's recorded transcripts
> replace the authored set. Saying "100% accurate" on a slide would be false.

The match rate is not the number to watch. WRONG FOOD is. A miss is honest and the user gets
asked; a wrong food silently puts a wrong number in a health app.

### Corpus

81 foods, 503 aliases in roman, Telugu and Devanagari, 13 deliberate no-data items,
**50 authored reference recipes over 434 ingredient rows**.

**Gongura is in USDA**, as "Roselle, raw". An earlier sweep had reported it absent from every
source. Same species, no caveat needed.

---

## THE RECIPE LAYER

Composed dishes now resolve. `sambar`, `idli`, `biryani`, `pappu`, `vada` and 45 more used to be
honest misses and are now dishes with a visible, editable composition. Every figure from them is
capped at Approximate by `AUTHORED_REFERENCE_RECIPE`, however exact the name match, because
knowing the word is not knowing the plate.

The absorbed oil is recorded as what the dish RETAINS, with its arithmetic and its reasoning in
the committed CSV, so a reviewer can challenge the fraction without reverse-engineering it from a
total. Deep-fried dishes land at 248-307 kcal per 100 g. The rejected public dataset read 745 for
a vada, by counting the whole frying bath; ours reads 294.

Full write-up in `docs/decisions/0008`.

---

## WHAT MEASURING FOUND, WHICH READING DID NOT

The recipe layer, this round:

- **Idli was authored at 227 kcal per 100 g**, within reach of a dry griddle roti at 258. A
  steamed rice-and-dal cake cannot be that dense. The yield was wrong by nearly a factor of two.
  Every existing assertion passed it, because they all take the yield as given and only check
  that the arithmetic is self-consistent.
  A new assertion derives each dish's moisture from its own macros and compares it to a band for
  its cooking method. Idli at the old yield implies 45% moisture against 58-80% for a steamed
  dish, and the build now deletes the database rather than ship it. Corrected from moisture, not
  from a calorie target: 172.4 g of solids at 68% moisture is a 539 g yield, ten idlis of 54 g.
- **`pulihora` pointed at lemon rice.** Bare pulihora is the tamarind one; lemon rice is
  nimmakaya pulihora.
- **`dal`, `daal` and Devanagari `दाल` resolved to nothing at all**, orphaned when the alias
  collision between the pulse and the dish was fixed. They now mean the dish.

Earlier rounds, kept because the shape recurs:

- `biryani` resolved to **bay leaf**, through the alias "biryani aaku"
- `upma` resolved to **semolina**, `atta` was refused as **curry leaves**, `avalu` as
  **horse gram**
- "rice", "chawal" and "chana" each sat on both a raw and a cooked record

Three of those are a whole dish collapsing onto one of its ingredients, which is the worst shape
of wrong answer here, because the number that follows looks completely reasonable. Full write-up
in `docs/decisions/0006`.

**A separate one worth knowing:** Gradle had been marking the test task UP-TO-DATE after the food
database was rebuilt, so a green build was reporting the previous run's numbers. The database and
the utterance set are now declared test inputs. A green build that did not run is worse than a
red one.

---

## LOCALISATION

Three string tables, English default, Telugu and Hindi overlays; a missing key falls back to
English visibly (`0017`). `StringResourcesTest` refuses a literal in a composable and prints
the translation state per locale on every run; `res/values*/strings.xml` are declared test
inputs so that report cannot be a previous run's. Today's report: **values-te 136/136
present, 136 awaiting review, 0 missing; values-hi 0/136.**

**Every Telugu line in the app is MACHINE-GENERATED, AUTHOR UNVERIFIABLE, UNREVIEWED**, and
the XML comment on every entry says so. 48 arrived via Vedant; 89 were generated by Nila on
Vedant's ruling that the packet should carry candidates rather than holes. Nobody on the team
can read them. **Rule (`0017`): if the nine lines of Part 1 are not confirmed by a fluent
speaker before the demo build is cut, `values-te` leaves that build** (one `localeFilters`
line). A partial-review middle shape is recorded there and not built.

The reviewer packet ships once, frozen 22:00 IST 20 Sep, owned by Nila: Part 1 the nine
sentences, Part 2 the screens the demo shows, Part 3 the rest, Part 4 Priya's word lists and
Meera's listening ask, lower priority. Anything added after the freeze is English-only in the
demo build by default; `docs/localisation/string-conventions.md` says so to whoever writes a
screen.

## LICENCES

The repository is **Apache-2.0** (`LICENSE`, canonical text, ruled by Vedant). espeak-ng
(GPL-3.0-or-later, inside the sherpa-onnx AAR and as the `espeak-ng-data` asset) attaches
duties on CONVEYING the APK, recorded concretely in `0005`: demoing on a phone the team holds
is not conveying, handing a judge an APK file is. The question is separable: if the TTS
probe finds an offline Google Telugu voice, Piper and espeak-ng drop out with the duties. The
IITM Indic TTS EULA text is filed under `docs/licences/` with its provenance header; nothing
is ruled from it until the PDF diff. ASR rows corrected on Jacob's evidence: MIT (AI4Bharat)
for te/hi, NVIDIA CC-BY-4.0 for English. The About screen exists and lists most of this;
what it still owes is in the conventions file.

## HOW WE WORK, RULED TODAY

- One worktree per session (`tools/new-worktree.ps1`), landed on master by fast-forward only
  (`tools/land.ps1`). Nobody builds in the main tree.
- A test count is valid only if every XML was written by your own run.
- Any file whose content changes what a test reports is a declared input of the test task.
- Every APK size is a row in `logs/apk-size.log` or it is not a figure.
- A small documented build-config edit by the person holding the facts is fine; a feature
  change to build config, resources or strings comes to Nila.
- Claim a decision-record number in `COORDINATION.md` before writing the file; two files were
  numbered 0022 today.
- Commits authored solely by Vedant Manmath Idlgave, no trailer, no attribution anywhere.

## DECIDED WITHOUT ASKING

- Bare dish names mean the dish; the plain ingredient keeps a name that says so. `0008`
- Dish and ingredient are matched together and the stronger match wins, with ties to the dish. `0008`
- Absorbed oil is a fraction of the PRE-FRY weight, because a fraction of the finished weight is
  circular. `0008`
- Two paths to a dish's nutrition, cross-checked against each other in a test. `0008`
- Tests and the APK build moved to the cloud container, off the critical path of desktop
  control. `0009`
- llama.cpp is prebuilt into jniLibs rather than built by Gradle; CMake builds only the shim. `0010`
- A prompt that does not fit the context window is refused, not trimmed, because trimming deletes
  the system rules first. `0010`
- Greedy sampling, cleared KV cache per call, CPU only. `0010`
- Smoke-tested the LLM runtime before Hilt, since the spec has been wrong twice about what
  exists. `0006`
- Matcher containment is one-directional and word-bounded; the no-data list is never
  fuzzy-matched. `0006`
- No nutrient preference means no suggestions at all. `0006`, and it is why beat 4 is provable.
  **Amended by `0015`:** this survives for UNPROMPTED suggestion only; an explicit SUGGEST or
  RECOMMEND is answered from declared conditions and the knowledge file, and says what it is
  working from.
- Speech routes to LOG, ANSWER, SUGGEST or RECOMMEND; the safety line is drawn around numbers
  and diagnosis rather than a fixed sentence catalogue. Ruled by Vedant. `0015`
- The model arbiter's ceiling is measured on the device at first run, not keyed off a device
  name, and every measurement is an appended row. `0c20de3`
- ONNX Runtime is test scope until shipped code opens a session; it cost 33 MB of the demo APK
  and nothing called it. `0016`
- Three string tables, English default, Telugu and Hindi filled only by a fluent speaker, a
  missing key falls back visibly rather than to a placeholder. `0017`
- The food database copy on the device is keyed by the asset's hash, not a version constant that
  never changed. `6aea21e`
- Millilitres convert at density 1.0 and are flagged as the default they are, so a volume of oil
  cannot reach GOOD. `6aea21e`

---

## THE LLM PATH

Spec 11.5 says the model extracts and explains and never computes a number. The enforcement is
written and tested on the JVM, and the extraction path has since run on the phone (hardware
section above; `0011`, `0014`):

- **NumericGuard** rejects any number in generated prose that was not in the input. It compares
  parsed values, so "12g" matches "12 g" and Telugu numerals match the figure they denote, because
  a guard that fires on correct output is one somebody eventually weakens. It stays strict in the
  place that matters: the 12 inside "B12" does not licence a bare 12.
- **A strict schema reader** for extraction, hand-written rather than a library, because the
  useful property here is refusal. Unknown field, duplicate key, trailing comma, quantity as a
  string, zero quantity, anything after the closing brace: refused and re-asked. When the budget
  is spent the orchestrator asks the person. The JSON is never repaired by inference.
- **A seam at the native boundary**, the same shape as `FoodDbSource`. 37 tests run the whole path
  against a runtime scripted to return exactly the truncated, fenced and fabricated output a model
  produces on a bad day.

The numeric guard is deliberately NOT applied to extraction. "two rotis" is correctly extracted as
2 with no digit anywhere in the transcript, and a digit guard would reject the right answer in
every language. An unstated quantity is protected instead by `QUANTITY_INFERRED`, which caps the
figure at Rough and forces the UI to show it as correctable.

---

## NEXT

Six sessions from 20 September, per `COORDINATION.md`: Rao on `domain/` and the phone (the
orchestrator, after the arbiter), Priya on conversation and the knowledge file, Jacob on ASR,
Meera on TTS, Arjun on vision, Nila on tools, docs, build and strings.

Open on the build and documentation side, 15:30:

- The reviewer packet freezes at 22:00; the check file comes back; confirmations drop markers
  per line; the count of Part 1 confirmed is the number that decides the build.
- The IITM PDF diff, then Meera's re-ruling of maya, rohan and pocket-tts.
- The espeak-ng corresponding-source pointer, parked until the TTS probe reports.
- The About screen's USDA citation and disclosure lines (Arjun, from the database's `meta`).
- The hardware section of this file is Rao's to refresh; it predates today's ASR, TTS and
  screen work.
