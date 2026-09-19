# Katori status

Updated 19 Sep 2026, 11:45.

---

## BLOCKED ON VEDANT

**Nothing.**

Laptop control timed out once more (30 minutes idle) and I re-requested it immediately, as
agreed. Cost this time was minutes, not an hour, because I switched to work that needs no run.

---

## VERIFIED ON HARDWARE

**Nothing. Still not one line.** No phone connected, nothing installed on a device.

Unverified and unverifiable until the Realme 11: model load on a phone, inference speed on a
phone, ASR accuracy, ASR/LLM/TTS co-residency, camera, OCR, every latency number.

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

**48 tests, 0 failures.** APK 34.17 MB, arm64-v8a only. Demo manifest permissions asserted as
exactly CAMERA and RECORD_AUDIO against a whitelist.

### Measured match rate, the spec 13.5 replacement

    ingredient utterances    144    resolved correctly  144  (100.0 %)
    no-data utterances        22    refused correctly    22  (100.0 %)
    expected-miss             10    correctly missed     10
    WRONG FOOD                 0    <- the number that matters

> **THIS 100% IS CIRCULAR AND MUST NOT BE QUOTED TO JUDGES.**
>
> The utterance set was written by the same people who wrote the aliases, so it can only
> contain phrasings somebody already thought of. It cannot contain the phrasing nobody
> anticipated, which is exactly the case that will come up on stage.
>
> What the number IS: a regression guard. If a change breaks a phrasing that used to work,
> or resolves something to the wrong food, the build fails.
>
> What it is NOT: a measure of real-world coverage, and not an accuracy figure. There is no
> real-world number yet and there will not be one until Abhinav's recorded transcripts
> replace the authored set. Saying "100% accurate" on a slide would be false.

The match rate is not the number to watch. WRONG FOOD is. A miss is honest and the user gets
asked; a wrong food silently puts a wrong number in a health app. The test fails the build on
either a wrong food or a rate regression.

The utterance set is **authored, not recorded**, which is what makes the number circular. It gets replaced by Abhinav's real transcripts
when they exist.

### Corpus

81 foods, 513 aliases in roman, Telugu and Devanagari, 13 deliberate no-data items.

**Gongura is in USDA**, as "Roselle, raw". An earlier sweep had reported it absent from every
source. Same species, no caveat needed.

---

## WHAT MEASURING FOUND, WHICH READING DID NOT

Five wrong answers on the first run, all real:

- `biryani` resolved to **bay leaf**, through the alias "biryani aaku"
- `upma` resolved to **semolina**, through "upma rava"
- `atta` was refused as **curry leaves**, through "kadi patta"
- `avalu` was refused as **horse gram**, through "ulavalu"
- `kandi pappu` resolved to the **cooked** dal when it was authored as raw

Three of those are a whole dish collapsing onto one of its ingredients, which is the worst shape
of wrong answer here, because the number that follows looks completely reasonable.

Fixed four ways, and the new importer assertion then found three more nobody had noticed: "rice",
"chawal" and "chana" each sat on both a raw and a cooked record, and whichever loaded last won
silently. Full write-up in `docs/decisions/0006`.

**A separate one worth knowing:** Gradle had been marking the test task UP-TO-DATE after the food
database was rebuilt, so a green build was reporting the previous run's numbers. The database and
the utterance set are now declared test inputs. A green build that did not run is worse than a
red one.

---

## DECIDED WITHOUT ASKING

- Smoke-tested the LLM runtime before Hilt, since the spec has been wrong twice about what
  exists. `docs/decisions/0006`
- Matcher containment is one-directional and word-bounded; the no-data list is never
  fuzzy-matched. `0006`
- Bare food names mean the cooked form, because that is what a person logging a meal means. `0006`
- No nutrient preference means no suggestions at all. `0006`, and it is why beat 4 is now provable

---

## NEXT

D: KSP, Room compiler, Hilt, timeboxed. Then E: the JNI bridge and NumericGuard.
