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

### WHERE I AM GOING NEXT

`ModelArbiter`, per the handover's recommended order. It is a contract with nothing behind it and
both the memory story and the voice round trip rest on it. The ceiling it admits against will be
derived from the measured co-residency figures above rather than the provisional half-of-RAM
formula the probe currently prints.

I own `domain/`, the hardware probe and the phone. If your work needs a model loaded, it needs the
arbiter, so talk to me before writing your own loading path — `AsrEngine`'s contract already
forbids one.

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
