# Katori status

Updated 19 Sep 2026, 15:55.

---

## BLOCKED ON VEDANT

**Nothing.**

Laptop control is no longer on the critical path. It expired again, and terminals are now
click-only so a command cannot be typed into PowerShell at all. Rather than keep paying that
cost, the whole test-and-build loop moved into the cloud container: SDK, platform 37, build
tools, Gradle. Tests and the APK build now run there in about twenty seconds. The laptop stays
the source of truth for the repo. `docs/decisions/0009`.

Desktop control is still needed for the phone over USB, which is the entire hardware list below.

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

The remaining lever is the ANSWER, not the prompt: the model emits
`"quantity":null,"unit":null,"method":null` on every item, and the strict reader already accepts
those fields as absent. Untried, and measured against the correctness cases before it is believed.

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

**libllama.so cross-compiles for arm64-v8a** against NDK 28.2.13676358 at minSdk 26, 39.7 MB,
plus the three ggml libraries. So the native build path works. Those libraries have never been
loaded and the JNI bridge is not written.

---

## VERIFIED ON EMULATOR

Nothing. Not built.

---

## COMPILE AND UNIT TEST ONLY

Read out of `logs/container-test3.log` and `logs/container-assemble.log`.

**103 tests, 0 failures.** APK 46 MB, `lib/arm64-v8a` only. Demo manifest permissions asserted
against a whitelist as exactly CAMERA, RECORD_AUDIO and the platform receiver permission, and
`aapt2 dump permissions` on the built APK agrees. No INTERNET permission survives the merge.

The JNI shim compiles and links: `libkatori_llama.so` is in the APK alongside the four llama.cpp
libraries, and its three symbols export under the names the Kotlin class expects. **That is the
entire claim.** Nothing on that path has executed. `docs/decisions/0010`.

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
- No nutrient preference means no suggestions at all. `0006`, and it is why beat 4 is provable

---

## THE LLM PATH

Spec 11.5 says the model extracts and explains and never computes a number. The enforcement is
now written and tested, all of it without a phone:

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

F: AsrEngine via sherpa-onnx. Then G: the UI screens.

The moment a phone is attached, before anything else: load the model, run the real extraction
prompt, read the result out of logcat. Everything on the LLM path is written and nothing on it has
executed.
