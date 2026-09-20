# 0019. Spoken output: Piper voices through sherpa-onnx, and the platform's own engine for English

Date: 20 September 2026. Status: accepted for the code and the desktop measurements below.
**Nothing in this record has run on a phone.** Every hardware claim is Rao's to make, and the
list of what he needs from this slice is at the end.

## What was decided

| Language | Voice | Runtime | Licence |
| --- | --- | --- | --- |
| Telugu | Piper `te_IN-padmavathi-medium` | sherpa-onnx 1.13.8, static-link AAR (the artefact and its checksum are Jacob's, in the ASR record) | CC-BY-4.0, already in `0005` |
| Hindi | Piper `hi_IN-pratham-medium` | same | **CC-BY-NC-SA-4.0**, added to the non-commercial register in `0005` today |
| English | the device's `android.speech.tts.TextToSpeech` | ships with the OS | none to record |

All three sit behind the one `TtsEngine` contract: `PiperTtsEngine` for the two Piper voices,
`AndroidTtsEngine` for English, and `RoutingTtsEngine` in front of both so the app injects one
engine and the user's language choice picks the path. A language no engine claims is
MODEL_NOT_LOADED. It is never another language's voice, which the contract forbids because
Telugu read with Hindi phonology sounds worse than silence.

The spec's FastPitch plan (10.3) is dead for the reason `0005` gave: no ONNX exists and
sherpa-onnx has no FastPitch runtime. Piper replaced it there; this record is Piper landing.

`0005` said the platform engine "stays as an opportunistic fallback only, never the only path".
That was written about Telugu, where the platform voice is a network download. For English it
is the only path, deliberately: on-device, no file to ship, no licence question, and no
memory taken from the arbiter's ceiling. The offline claim is checked on the device rather than
assumed: only a voice whose `isNetworkConnectionRequired` is false and whose features do not
say `notInstalled` is used, and with none the answer is MODEL_NOT_LOADED with the text left on
screen. The demo build has no INTERNET permission, but the engine is another process with its
own, so this check is what keeps the claim honest.

## The prerequisite that blocked everything, now measured rather than described

`0005` and `HANDOVER.md` §7 both said the Piper download carries no sherpa metadata. It is now
a measurement: opened through onnxruntime 1.23.1, the file rhasspy/piper-voices serves reads
`custom_metadata_map {}`. sherpa-onnx reads the sample rate and speaker count from that map, and
when a key is missing it logs one line and calls `_Exit(-1)` (`csrc/macros.h`,
`SHERPA_ONNX_READ_META_DATA`). On Android that is the process gone, with nothing for the arbiter
to catch. So the raw download is not "a model that fails to load"; it is a model that kills
the app.

`tools/stamp_piper_voice.py` does what sherpa-onnx's own `scripts/piper/add_meta_data.py`
does, stdlib only. It rewrites the ONNX protobuf at the top level, copying every field except
`metadata_props` (14) byte for byte and writing the nine keys that script writes
(`model_type=vits`, `comment=piper`, `language`, `voice`, `version=1`, `has_espeak=1`,
`has_g2pw=0`, `n_speakers`, `sample_rate`), and it writes `tokens.txt` from the config's
`phoneme_id_map` in the same one-line-per-symbol format, leading-space line included. Read back
through onnxruntime, which is the reader sherpa-onnx uses:

    te_IN-padmavathi-medium  63,516,050 B -> 63,516,206 B
      {'version': '1', 'model_type': 'vits', 'comment': 'piper', 'has_espeak': '1',
       'language': 'Telugu', 'voice': 'te', 'has_g2pw': '0', 'n_speakers': '1', 'sample_rate': '22050'}
    hi_IN-pratham-medium     63,516,050 B -> 63,516,205 B, same keys with Hindi / hi

`tokens.txt` has 157 symbols for both voices.

The same check exists in Kotlin. `SherpaPiperVoice.load` reads the metadata itself
(`PiperModelFile`, the same top-level protobuf walk) and refuses a file without `comment=piper`
and `sample_rate` BEFORE sherpa-onnx sees it, along with a missing model, a missing
`tokens.txt` and an incomplete espeak-ng-data directory. Each refusal is an exception the
arbiter turns into MODEL_LOAD_FAILED with the path in `detail`. A wrong file pushed to the
phone is then a sentence on a screen, not a dead process. `PiperModelFileTest` checks the
reader against hand-built protobuf bytes and, when the stamped voice is on the machine, against
the real 63 MB file; both pass.

## espeak-ng-data: 18 MB upstream, 1.07 MB shipped

Piper phonemises through espeak-ng, which opens its tables with plain file calls, so the data
must be on disk and the app ships it as an asset and copies it out once (`EspeakData`, keyed on
the APK's `lastUpdateTime`, not a constant: HANDOVER §7 bug 2 was a "versioned" copy keyed on a
filename).

The upstream tarball sherpa-onnx's own packaging script fetches,
`https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2`
(7,252,012 B, sha256 `4135ccf82e1f40613491c0874d4945ae9e9c7840933d8e25a6f9e003d9ebf533`),
unpacks to 17,991,651 B across 355 files, of which the dictionaries for languages this app does
not speak are about 17 MB. `stamp_piper_voice.py espeak` keeps the four phoneme tables, `lang/`,
`voices/`, and the `en`, `te` and `hi` dictionaries: 244 files, 1,067,073 B. `en` stays because
espeak-ng loads the English voice at initialise time and switches to it for Latin-script words
inside Telugu or Hindi text.

That the trim removed nothing that matters was measured, not assumed. VITS is stochastic, so
two runs on the same input differ; with `noise_scale = noise_scale_w = 0` it is deterministic,
and under that setting the full and the trimmed directory produce **byte-identical audio** for
`ఇడ్లీ సాంబార్`, `అన్నం పప్పు నెయ్యి` and `idli sambar` (the Telugu is from
`data-authoring/utterance-test-set.csv`; nobody on this slice writes Telugu and none was
invented). If a language is added, its dictionary is added to that command and this check is
repeated.

## Desktop smoke, the evidence that the three files work together

sherpa-onnx 1.13.8 Python on this laptop, the desktop build of the same runtime the AAR is,
stamped model + `tokens.txt` + the trimmed data directory, default noise, two threads:

| voice | text | audio | samples @ Hz | peak |
| --- | --- | ---: | --- | ---: |
| te padmavathi | `ఇడ్లీ సాంబార్` | 1.15 s | 25,344 @ 22,050 | 0.409 |
| hi pratham | `रोटी दाल` | 0.79 s | 17,405 @ 22,050 | 0.726 |

Audio came out, at the rate the metadata declares, with a peak that is neither silence nor
clipping. The WAV files are `logs/tts-smoke-te-trimmed.wav` and `logs/tts-smoke-hi-trimmed.wav`
and exist so a fluent listener can judge them; **whether they sound like Telugu and Hindi is
not judged here and cannot be.** The Hindi text is two food names from `recipes.csv`.

Synthesis time on this laptop was 0.25-1.9 s per utterance across runs and is not reported as
a figure: Jacob measured the same machine swinging 20x with free RAM, and the phone has
different cores. Piper's real-time factor on the device is unmeasured.

## Design, and the corners it cuts

- **The voice is leased, never held.** `PiperTtsEngine.speak` acquires the voice through
  `ModelArbiter.withModel` for exactly one utterance: synthesis and playback inside the lease,
  unpinned when the audio has been heard. `prepare` is the arbiter's `preload`. Nothing in
  `ml/tts` opens a file except `PiperVoiceLoader`, which is the `ModelFamily.TTS` branch of the
  arbiter's loader and is refused for any other family.
- **Two seams keep it on the JVM.** `PiperVoice` stands in front of sherpa-onnx and `AudioSink`
  in front of `AudioTrack`, for the reason `LlamaRuntime` and `FoodDbSource` earned theirs.
  `PiperTtsEngineTest` runs the real `DefaultModelArbiter` with a fake voice and a fake sink:
  text through unchanged, one load for two utterances, MODEL_NOT_LOADED for an unclaimed
  language without touching the arbiter, the arbiter's MODEL_LOAD_FAILED detail surfaced,
  refused focus as CANCELLED, blank text as nothing, and `stop()` during synthesis abandoning
  the audio rather than playing it late. 23 tests in `ml/tts`, 0 failures, read from the JUnit
  XML; whole suite 188/0/0 in the shared checkout at 02:40.
- **Playback is one static `AudioTrack` buffer per utterance**, not streaming. The audio exists
  in full before playback begins and an utterance is seconds long. The cost is that the first
  sound waits for the whole synthesis; if the device RTF makes that wait visible, the upgrade is
  `generateWithCallback` feeding a streaming track, and the seam is already shaped for it. The
  end of playback is the position marker at the last frame with a timeout of the audio's
  length plus a second as the net, because a `speak()` that never returns would hang the round
  trip.
- **Audio focus is transient and shared by both paths.** Refused focus (a call in progress) is
  CANCELLED before anything plays; a real loss during playback stops it; a transient loss that
  may duck (a notification chime) is ignored, because a health sentence is not cut short for a
  chime.
- **Sentence splitting for the platform engine** is on `.!?` and the danda, then on words only
  when one sentence exceeds `getMaxSpeechInputLength()`. Every character is spoken exactly
  once, in order, at every limit from 1 upward, which is what `SentenceChunksTest` asserts. The
  Piper path needs no splitter: sherpa-onnx splits into sentences internally and never
  truncates.
- **Thread count is 2 and is a starting point**, not a measurement.

## What Rao needs to do, in order

1. Stage the two voices as directories, mirroring `data-sources/models/tts/sherpa/`:
   `models/tts/te_IN-padmavathi-medium/{model.onnx,tokens.txt}` and the same for
   `hi_IN-pratham-medium`. The `model.onnx` is the STAMPED one; the `piper-te-model.onnx` the
   probe pushed is the raw download and will not load. Delete it first: storage is at 98-100%
   and the stamped file is 156 bytes larger.
2. `LlamaCppModelLoader`: add `ModelFamily.TTS -> ttsLoader.load(handle)` and route `unload`
   the same way, with `ttsLoader = PiperVoiceLoader(modelsDir, espeakDataDir)`. The espeak
   directory is `EspeakData.install(context, File(context.filesDir, "espeak-ng-data"))`, called
   once where the arbiter is built.
3. `AppModule`: provide `TtsEngine` as
   `RoutingTtsEngine(listOf(AndroidTtsEngine(context), PiperTtsEngine(arbiter, AudioTrackSink(context))))`
   — platform engine FIRST since the addendum below, Piper the fallback for a language this
   phone has no offline voice for. It meets the bar the module's comment sets only once step 1
   is on the phone; until then a Telugu `speak` on a phone without a platform Telugu voice
   returns MODEL_LOAD_FAILED naming the missing file, which is the honest state.
4. Add the co-residency row. `canCoReside(listOf(asr, llm, PiperVoices.TELUGU))` through the
   arbiter is the number `0013` has been waiting for: the first one with the phonemiser and
   espeak-ng resident rather than a raw session.

## What Nila needs to do

Put `data-sources/espeak-ng-data-trimmed/` into `app/src/main/assets/espeak-ng-data/` and
commit it: 244 files, 1.07 MB, the same class of asset as the food database. No build
configuration changes; assets are packaged as they are. `EspeakData` reads that path.

## Open, and stated as open

- **TTS quality.** Two WAV files exist and nobody on this slice can judge them. A fluent Telugu
  listener and a fluent Hindi listener are the check; the words are food names, so a plausible
  first question is "which foods did the voice say?".
- **Nothing has run on the phone**: not sherpa-onnx, not `AudioTrack`, not `TextToSpeech`, not
  the asset copy. `AudioTrackSink` and `AndroidTtsEngine` are WRITTEN, NEVER RUN in the
  HANDOVER sense.
- **The resident cost of a Piper voice** is unmeasured. `estimatedResidentBytes` on the handles
  is the file size, which is the only manifest figure there is; the arbiter measures the rest.
- **`hi_IN-pratham` versus `hi_IN-priyamvada`** is a listening preference; both are the same
  licence, size and trainer. Pratham was taken because it is listed first in `0005`.

---

## Addendum, 20 September 2026, 03:10: the listeners' verdict, and what is in front of them next

**Vedant's native Telugu listeners heard the two WAVs above. Verdict: completely robotic, not like
a person at all.** That is the right people answering the right question, and it is treated as
decisive, not as tuning noise. Two things about the test itself are stated so nobody over-reads
it: the input was two food names, which no voice reads as speech; and this record still holds no
Telugu sentence, because nobody on the slice writes Telugu. A sentence from the listeners is the
next input.

### What changed in the code

- `AndroidTtsEngine` now claims all three languages and answers PER DEVICE: `prepare` is
  MODEL_NOT_LOADED for a language with no installed offline voice. Voice choice is the same
  language code only, Indian regional voice first, then the platform's own quality rating.
- `RoutingTtsEngine` tries the engines in order and falls through on MODEL_NOT_LOADED only; any
  other answer is final. So the wiring becomes platform engine first, Piper second, and a phone
  with an offline Google Telugu voice never reaches Piper while a phone without one still speaks.
  `RoutingTtsEngineTest` covers both orders and the never-another-language rule. 25 tests in
  `ml.tts`, 0 failures, read from the JUnit XML.
- `TtsVoiceProbeTest` (androidTest, compiled, NEVER RUN) lists every voice on the phone with its
  locale, quality, latency, network flag and features, states what the engine would pick per
  language, and writes `platform-<lang>-<voice>-A/B.wav` with the same texts as the candidates
  below. Only Rao runs it; the answer is a device fact and this record does not guess it.

### The candidates, `logs/tts-candidates/` on the laptop, same two texts each

| candidate | licence | on-phone cost | status |
| --- | --- | --- | --- |
| Google / OEM platform voice for `te-IN` | none to record | none: no file, no arbiter | UNKNOWN until the probe runs. May be the whole answer, and removes nothing from the register (the NC entry is Hindi) |
| Piper `padmavathi`, six parameter variants: `noise_scale` 0.667/0.5/0.333, `noise_w` 0.8/0.5, `length_scale` 1.0/1.15/1.3 | CC-BY-4.0 | 63.5 MB, measured to load on desktop | generated. The trimming test in this record ran at noise 0, which is NOT how the voice is shipped; the shipped default is 0.667/0.8/1.0 |
| Piper `venkatesh`, default and 0.5/0.8/1.15 | CC-BY-4.0, sha256 `dfaa5b7833cd48d946f3fe18c9c934aaa4e8590aac6922fddf34783a694c3c87` | same as padmavathi | generated, stamped the same way |
| facebook MMS `tel` (`willwade/mms-tts-multilingual-models-onnx`) | **CC-BY-NC-4.0**; goes in 0005's register the day it ships | 114 MB, 16 kHz, character frontend, no espeak; its frontend skips punctuation | generated |
| `prasadvittaldev/pocket-tts-telugu-female-syspin` (Kyutai pocket-tts, 110M params, 24 kHz) | claimed MIT; base MIT; SYSPIN data CC-BY-4.0 | 86 MB int4; LM-based, "5.5x real-time on desktop x86", unmeasured on a 2xA76; ONNX present but NOT in sherpa-onnx's Pocket layout | the author's own 74 s sample copied as-is; NOT our text |

### Surveyed and not made into candidates, with the reason

- `SYSPIN/tts_vits_coquiai_TeluguMale` and `TeluguFemale`: **CC-BY-4.0**, Coqui VITS on 30 h of
  studio Telugu. Coqui does export VITS to ONNX and sherpa-onnx runs Coqui VITS (`vits-coqui-*`
  in its zoo, character tokens, no espeak). The checkpoint is a `.pt` with a `jit_infer.py`, so
  the export needs the Coqui TTS package and a Python that still runs it. This is the strongest
  permissively-licensed lead if Piper and the platform both fail the ear, and it is an evening
  of toolchain work with a real chance of a recorded retreat. Not attempted tonight.
- `multilingual-tts/VITS-OpenBible-Telugu`: CC-BY-SA-4.0, Bible readings, Coqui `.pth`. Same
  conversion cost, narrower data. Behind SYSPIN.
- AI4Bharat Indic Parler-TTS and IndicF5: permissive, and 0.9B / large; not phone models.
- `te_IN-maya-medium`: licence PDF still unreachable, per `0005`.

### The honest ceiling

Offline TTS at 60-100 MB is behind cloud TTS and no parameter pass closes that. If the platform
voice is absent from this phone and none of the candidates passes the ear, the decision is a
product one, not an engineering one: whether the app speaks full sentences at all, or shows text
and speaks only short confirmations. That decision is Vedant's, on the listeners' word, and this
record will carry whichever way it goes.

### The APK figure, closed

The 76,662,020-byte APK sized earlier carried 10,491,272 bytes of dead space between entries:
AGP packages incrementally and leaves holes where replaced entries were, so the on-disk size of
an incrementally packaged APK is build history, not content. That is why adding a 24 MB library
moved it by 153 bytes, and it means the two figures in `0016` are comparable with each other
(same session, same holes) but not with this one. Packaged from scratch (APK deleted, then
`assembleDemoDebug`, `logs/meera-apk-size.log`), at HEAD `d871c0f` with 22 uncommitted paths in
the shared tree, 02:57: **66,889,734 bytes**, sha256
`28c2a1d7fb15ed825ad935334c1103db082cdc6d065c0c25c854b86807221797`, 813 entries, 81,626 bytes of
slack, `lib/arm64-v8a/libsherpa-onnx-jni.so` 24,169,352 stored. Any APK figure quoted from now
on says whether it was a clean package.

---

## Addendum 2, 20 September 2026, 03:40: Vedant's pick, and what it survives

Vedant listened and picked `te-pocket-tts-syspin_female-int4` on the author's own passage. Treated
as a direction, not a verdict: that WAV is the author's curated demo, not our pipeline, and it is a
passage where every other candidate was a food-name list. Three checks were run in the order
that costs least. The platform-voice probe (Rao's run) is still first in importance and still
not run.

### The licence chain, to `0005`'s standard: it does not hold from here

| link | what it says | verified? |
| --- | --- | --- |
| Kyutai `pocket-tts` (architecture, codec, recipe) | MIT | yes, GitHub licence field `MIT` |
| `prasadvittaldev/pocket-tts-telugu-female-syspin` weights | "MIT, matching upstream pocket-tts. The training corpora carry their own licences — check the dataset pages above before commercial use." | the claim is the author's own, and the author defers on the data |
| training corpus 1, `SPRINGLab/IndicTTS_Telugu` (IIT Madras Indic TTS) | "subject to the original Indic TTS license terms … `https://www.iitm.ac.in/donlab/indictts/downloads/license.pdf`" | **NO.** The PDF returns nothing from here (http 000, and a 302 to nothing over http), exactly the block that excluded `te_IN-maya` and `hi_IN-rohan` in `0005`. A third party's paper calls it "CC-BY-4.0 or similar"; hearsay, not the text |
| training corpus 2, `arpit-tiwari/syspin-telugu-tts` (a re-upload of SYSPIN, IISc) | no licence field, no README text | **NO.** SYSPIN's own site is script-rendered and unreadable from here; SYSPIN's HF org lists only LIMMITS few-shot samples, CC-BY-4.0, which is not this corpus |

The student was distilled on the SYSPIN speaker alone, but its teacher was trained on both corpora,
so both are in the chain. `0005`'s rule is verbatim or it does not ship, and there is no verbatim
text for either corpus. What would close it: Vedant opening the IITM PDF from an Indian
connection and pasting its terms (which would also settle `maya` and `rohan`), and the SYSPIN
dataset page's licence line. If IndicTTS turns out non-commercial, the register takes it; if it
stays unreadable, this voice does not ship, whatever it sounds like.

### Reproducibility: our text through the model, on the laptop

Kyutai's `pocket_tts` 3.1.0 runtime, the 4-bit weights unpacked to fp16 as the card instructs,
`--quantize` (int8 dynamic), CPU:

| text | audio | synthesis | desktop RTF |
| --- | ---: | ---: | ---: |
| A, the two words the listeners heard | 1.68 s | 1.5 s | 0.91 |
| B, five food names | 4.32 s | 3.5 s | 0.81 |
| the card's own example sentence | 5.92 s | 4.9 s | 0.83 |

So the voice reproduces on text it was not demoed on, and those three files
(`logs/tts-candidates/te-pocket-tts-syspin_female-int4-OURS-*.wav`) are what the listeners
should judge, not the demo passage. RTF 0.8-0.9 on an x86 laptop against the card's claimed 0.18
is not a phone number, but it is the wrong side of real time before the phone's two A76 cores are
in the picture: an LM-based 110M-parameter model synthesising a four-second confirmation could
take longer than the confirmation itself, on top of `0014`'s 10.7 s extraction.

### The Android path does not work as-is

sherpa-onnx 1.13.8 has a Pocket TTS runtime, and the fine-tune ships ONNX graphs with the same
five roles (`flow_lm_flow`, `flow_lm_main`, `mimi_encoder`, `mimi_decoder`, `text_conditioner`)
plus a SentencePiece model that sherpa's `convert_tokenizer.py` turns into `vocab.json` and
`token_scores.json`. It loads (`validate` true, 24 kHz, one speaker; sherpa also warns the
tokenizer lacks byte-fallback tokens) and needs the voice prompt as `reference_audio`. The output
is wrong: 8.43 s of audio for the two-word text with 2.55 s voiced, 15.11 s for text B, at desktop
RTF 5.4-5.7. Measured, not listened to; the files are in `not-for-listening/` as evidence.
Whether that is the int4 `MatMulNBits` graphs, the fine-tune's `bos_before_voice` convention, or
sherpa's stop criterion is not established and is not guessed here. Shipping this voice therefore
means either fixing that path (open, uncertain) or a second native runtime on the phone, which
is a cost this project has refused twice.

### Where that leaves the choice

1. The platform voice, if the phone has one offline: no file, no licence, no runtime. Still the
   first answer to get.
2. Piper (`padmavathi` / `venkatesh`): licence clean, runtime shipped, phone cost known to be
   small; the listeners' verdict on it stands until they hear a real sentence.
3. pocket-tts Telugu: the voice the listener preferred, reproducible on the desktop, with an
   unverifiable licence chain and no working Android path today.

The next input that moves any of this is the natural Telugu sentence from the listeners; every
surviving candidate, including the platform voice from the probe, gets re-rendered on it and the
set goes back for ranking.

### Rao's loader change

`FamilyModelLoader` (`domain/ModelArbiterSeams.kt`) replaces the "one branch in
`LlamaCppModelLoader`" instruction in step 2 above: the TTS entry is
`ModelFamily.TTS to PiperVoiceLoader(modelsDir, espeakDataDir)` in that map. Nothing in `ml/tts`
changes for it.

---

## Addendum 3, 20 September 2026, 04:05: speed is now a pass/fail column, and the licence gate

### Why speed matters more than it did yesterday

Rao's Orchestrator now has ANSWER and RECOMMEND paths whose model output is prose, not a 55-token
JSON object. Nobody has measured a conversational turn, but `0014` measured generation at about
five times the cost of prompt processing, at 9-10 tok/s, so a spoken answer can be twenty
seconds of generation before a single word is synthesised. A voice slower than real time on top
of that makes a spoken answer unusable however it sounds. Every candidate therefore has three
columns: licence, ear, and real-time factor ON THE PHONE. A candidate fails on any one.

### The table, as it stands

| candidate | licence | ear (listeners) | RTF, desktop, this laptop, 2 threads, text B | RTF, phone |
| --- | --- | --- | --- | --- |
| platform voice (`te-IN`, offline) | none to record | not heard yet | n/a | **unmeasured**; `TtsVoiceProbeTest` now prints it per sample (synthesis ms / WAV seconds) |
| Piper `padmavathi` | CC-BY-4.0, clean | robotic, on two words | 0.75 / 0.84 / 1.33 (three warm runs) | unmeasured |
| Piper `venkatesh` | CC-BY-4.0, clean | not heard yet | 0.53 / 0.67 / 0.71 | unmeasured |
| MMS `tel` | CC-BY-NC-4.0, register if chosen | not heard yet | 2.88 / 4.20 / 4.65 | unmeasured, and the desktop figure already fails |
| pocket-tts Telugu | **gated on the IITM licence text** (`0005`) | preferred, on the author's demo; OURS files not yet ranked | 0.81 / 0.91 (Kyutai runtime, int8 dynamic); sherpa path broken | no working runtime on the phone |

The desktop column was measured with **954 MB free of 16 GB** on this laptop (the RAM-starved
condition Jacob flagged), which is why `padmavathi` reads 0.75-1.33 tonight against 0.23 earlier
in the night on the same file. The column orders the candidates; it does not predict the phone.
Only the last column decides, and every cell in it is empty.

### What follows for the code

If Piper stays, streaming playback (`generateWithCallback` into a streaming `AudioTrack`) stops
being an optimisation and becomes how a spoken answer starts inside a second rather than after
the whole synthesis: the first sentence plays while the rest is made. The seam is shaped for it
and it is not written until a phone RTF says it is needed; the number that triggers it is a
device RTF above roughly 0.5, where a five-sentence answer would otherwise wait several seconds
in silence after twenty seconds of generation.

### The listeners' next message

Approved, and cheaper than the sentence: the three `te-pocket-tts-syspin_female-int4-OURS-*.wav`
files ranked against `te-padmavathi-default-*` and `te-venkatesh-default-*` on the same texts. It
answers whether the preference survives the author's curation before any sentence exists.

### The licence gate, restated

The IITM document is now known to exist and to be readable from India; it is not readable from
this laptop and it is not in the repository. `0005` records the reported summary as a summary and
keeps the gate closed until the text is here. That one document rules on `maya`, `rohan` and
pocket-tts together.

---

## Addendum 4, 20 September 2026, 15:15: the demo language changed, and the ladder is decided in advance

### Telugu TTS is off the critical path, and that is a ruling, not a failure

Ruled today by Vedant with Jacob: **the demo runs in the language the presenter speaks, and
Vedant does not speak Telugu.** Hindi or Indian English on stage unless he says otherwise;
Telugu stays in the demo as the language picker and the reviewed interface strings.

So the ship blocker for the 26th is a Hindi or Indian-English voice that is intelligible, faster
than real time on the phone, and licence-clean. That is a smaller problem than the one this
record has been fighting, and the platform's own voices are far more likely to be adequate in
Hindi and Indian English than in Telugu. **Telugu spoken output is now a post-battle quality
item.** Nothing above is wasted by that: the stamping script, the trimmed espeak data, the
engine, the loader, the probe and the listening evidence are what a Telugu voice will ship on
when one passes the ear, and the descope is a decision about the stage, not about the work.

### The Hindi candidates, generated for the person who can judge them

Vedant speaks Hindi, so for the demo language the listener and the decision-maker are the same
person. `logs/tts-candidates/`:

| file | voice | licence footing | phone RTF |
| --- | --- | --- | --- |
| `hi-rohan-default-A/B.wav` | Piper `hi_IN-rohan-medium`, sha256 `b65dc80fb34d9dcd1cf684cb297966a34983bbc93bb1696fe207f32b0b33a091`, stamped, 161 tokens | IITM licence, permissive with the notice (`0005`); provenance is the card's claim, Vedant's call | unmeasured |
| `hi-pratham-default-A/B.wav` | Piper `hi_IN-pratham-medium` | CC-BY-NC-SA-4.0, in the register | unmeasured |
| `platform-hi-<voice>-A.wav` | the phone's own Hindi voice | none to record | the probe prints it |

Text A `रोटी दाल`, text B `रोटी, दाल, दही, चावल, आलू.`, all from the authored CSVs. Desktop RTF
was measured with 588 MB free of 16 GB and is not reported as a number: on that laptop tonight
both voices read 1.6-2.1, against 0.23 for the same class of model earlier in the night.

### The fallback ladder, decided before the probe returns

Each rung is a lookup against the probe report and the ear, in this order. The first rung that
holds ships; nothing below it is discussed on the 25th.

1. **The platform voice for the demo language is installed offline, Vedant finds it
   intelligible, and its RTF in the probe is below 1.0.** Ship it. No file, no licence, no
   arbiter memory, `AndroidTtsEngine` is already first in `RoutingTtsEngine`. Telugu on the
   same phone: whatever the probe says, it is not on the stage.
2. **The platform voice is absent, network-only, or Vedant rejects it.** Piper Hindi through
   sherpa-onnx: `hi_IN-rohan-medium` if Vedant accepts the provenance position in `0005` (then
   the notice ships in the About screen and the voice is permissive), otherwise
   `hi_IN-pratham-medium` (already registered, non-commercial, acceptable under the scope
   ruling). Both are stamped and one line apart in `PiperVoices.byLanguage`. Requires: the
   voice staged on the phone, `espeak-ng-data` in assets, and a phone RTF below 1.0; if the RTF
   is above about 0.5 the streaming playback in addendum 3 gets built first.
3. **Neither Hindi path clears the ear or the clock.** The demo speaks Indian English through
   the platform voice (English is the platform's home ground and `en-IN` or any offline English
   voice is accepted), and Hindi and Telugu are text on screen with the spoken lead-in and the
   progress spec (`0026`) carrying the wait. This rung is a product decision already written
   down in `0001`: spoken confirmation degrades to on-screen confirmation, never to a wrong
   voice.

Telugu, post-battle, follows the same three rungs on its own: platform voice if the phone has
one offline and the listeners accept it; Piper `padmavathi`/`venkatesh` when a natural sentence
has been heard; text-only otherwise. pocket-tts stays where addendum 2 left it: blocked on the
SYSPIN corpus's missing licence and on a runtime that does not exist on the phone.

### What "shipping the platform path is a switch" now means

`AndroidTtsEngine` claims all three languages, picks the same-language offline voice with the
Indian regional variant first, refuses network voices, says in its `detail` WHY a voice is
missing (not supported; supported but data not installed, with the settings path; only network
voices), splits long text without truncating, holds transient audio focus, and is already
wired first. What remains is the phone's fact, from the probe, and one preparation on the demo
phone before the day: if the report says `LANG_MISSING_DATA`, the Hindi and English voice data
are installed once from the phone's text-to-speech settings, on a network, days before the
demo, so that on stage the build with no INTERNET permission finds them already there.
