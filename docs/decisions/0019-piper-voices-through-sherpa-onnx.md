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

---

## Addendum 5, 20 September 2026, 16:20: the loaner problem, and the ladder inverted on one condition

### The hole in rung 1

Everything measured so far is a realme RMX3780. The battle runs on an iQOO handset nobody on
this team has held. The platform voice is a property of that device: its voice data is
per-device and per-install, the demo runs in airplane mode, and a network-only voice fails
silently rather than loudly. So rung 1 as written above depends on a fact about a phone we
cannot inspect, cannot pre-install onto, and meet for the first time on the day.

Rung 2 does not. A Piper voice ships inside the APK: no install, no network, no device lottery.
**The actual requirement is "works on an unknown device with the radios off", and only a
bundled model satisfies it.**

### The ruling, conditional, and the condition said out loud

**If this team cannot get hands on the actual demo handset before the 26th, rung 2 is the
DEFAULT: the bundled Piper Hindi voice speaks, and the platform voice is the optimisation the
app switches to only if `TtsVoiceProbeTest` on that real device, in airplane mode, reports an
offline Hindi voice with an RTF below 1.0 and Vedant accepts its sound.** The condition is
Vedant's to answer: is the handset reachable before the day, or not.

In code the inversion is one constant, `TtsFlags.PLATFORM_VOICE_FIRST` in `SpokenLeadIn.kt`,
which `AppModule` reads to order `RoutingTtsEngine`'s engines. It is `true` today, because the
condition has not been answered and flipping it silently would change what Rao's probes on the
realme exercise; it becomes `false` the day the answer is "no handset". The ladder's rungs are
otherwise unchanged; only which of the first two is the default moves.

Two consequences, both stated so they are not discovered later:

- **The provenance call in `0005` is now load-bearing, not academic.** Under the inverted
  ladder `hi_IN-rohan-medium` or `hi_IN-pratham-medium` is the shipping voice. Accepting the
  card claim puts a permissive licence with a notice on the stage; holding it puts the
  non-commercial `pratham` there, which the scope ruling permits. Either ships; the choice is
  which licence footing the demo stands on.
- **Piper's phone RTF becomes the number that decides streaming playback**, not an
  optimisation for later. If the staged Hindi voice reads above about 0.5 on the realme, the
  streaming `AudioTrack` path in addendum 3 is built before the 26th.

### English on the same logic

Indian English is the other permitted stage language and today it has no bundled voice: it
rides the platform engine alone, which on an unknown device is the same lottery with better
odds (an offline English voice ships with the platform engine on nearly every GMS phone). The
insurance is one more Piper voice in the APK. `en_GB-cori-medium`'s card gives its dataset as
LibriVox, licence "public domain"; `en_US-lessac` points at an Edinburgh licence page unread;
`en_US-ryan` is CC-BY-NC-SA. Not done: it is about 60 MB more APK and a British voice for an
Indian-English demo, and it is only needed if the presenter chooses English AND the handset is
unreachable. Recorded so the decision is a lookup if both turn out true.

### Still done regardless: the voice-data install

It costs nothing and it makes rung 1 real if the handset does arrive early. The numbered step
is in Nila's run of show; the short form:

1. On the phone, with a network: Settings → System → Languages & input → Text-to-speech output
   (on some OEM skins: Settings → Accessibility → Text-to-speech output). Or from a laptop:
   `adb shell am start -a com.android.settings.TTS_SETTINGS`.
2. Preferred engine: Speech Services by Google → its settings gear → Install voice data →
   Hindi (India) and English (India) → download both.
3. Airplane mode on. Run `TtsVoiceProbeTest`. The report must show a `hi-IN` and an `en-IN`
   voice with `network=false` and a written WAV with an RTF; only then does rung 1 exist on
   that phone.

### One check for Rao, five seconds, on his own device

With the radios off, does the platform engine's Hindi voice still resolve and synthesise? The
probe run in airplane mode answers it; a second run online, same day, shows whether anything
changes. That is the assumption "local means local" that rung 1 rests on, tested rather than
believed.

---

## Addendum 6, 20 September 2026, 17:30: whether the judges can hear the phone

A handset speaker in a crowded hackathon hall is close to inaudible, and this product's output
is spoken. Nobody had asked the question. Four answers, in the order they fail.

### Level

The voices are not at one loudness. Measured on the desktop, peak of full scale per utterance:
Piper `padmavathi` 0.41, `venkatesh` 0.41, `pratham` 0.66-0.73, `rohan` 0.79-0.81, MMS 0.87-0.91;
the platform voice is whatever its engine decides. So `PiperTtsEngine` now peak-normalises
every utterance to 0.89 of full scale before it reaches the sink: exact for a bounded VITS
output, never clipping, gain capped at 8x so near-silence is not amplified into hiss, and
already-loud audio brought DOWN to the same ceiling so two voices sit at one level
(`normalisePeak`, tested). It is peak, not loudness; if listeners report the level wandering
between voices, the upgrade is an RMS or LUFS target with a limiter, and not before a phone has
been heard. The platform engine's volume parameter stays at its default of 1.0.

The level that matters most is not in code: the phone's media stream at maximum. Our track
plays under `USAGE_ASSISTANT`, whose volume control stream the probe now prints, alongside the
music stream's current and maximum. That is a checklist item on the day, not something the app
forces; an app that sets the volume on its own is the app that startles a judge.

### Routing after a microphone capture

Every spoken turn starts with a capture, and the capture-to-playback transition is where
Android routing goes wrong: an audio mode left in communication, and the answer comes out of
the earpiece at earpiece level. `ml/asr/AudioSource` records with `VOICE_RECOGNITION` and sets
no mode, which should leave routing alone; "should" is not a measurement, so
`TtsVoiceProbeTest.c_outputRouteAndLevelAfterMicrophoneCapture` records for half a second the
way the turn does, releases, plays a 0.4 s tone through the exact attributes `AudioTrackSink`
uses, and prints `AudioTrack.routedDevice`: it must read `BUILTIN_SPEAKER` (or the wired
device), never `BUILTIN_EARPIECE`. The audio mode and speakerphone flag before and after the
capture are printed with it. Rao runs it once with nothing plugged in and once with the wired
speaker.

### A wired speaker is insurance, and Bluetooth is not

A USB-C or 3.5 mm speaker is a cable, not a radio: it costs nothing against the airplane-mode
claim and Android routes media to it on its own. **A Bluetooth speaker is a radio.** Android
lets Bluetooth be re-enabled inside airplane mode, and a judge who sees the Bluetooth icon next
to the airplane icon has a reasonable question we do not want to answer on stage. So: wired
only, and this is written down so nobody reaches for a Bluetooth speaker on the morning. The
probe's route line is the proof that the wired path is live: it reads `USB_HEADSET`,
`USB_DEVICE`, `LINE_ANALOG` or `WIRED_HEADPHONES`, and the tone is heard from the speaker.

### The rule that makes it survivable

The screen carries the full answer as text, always, so audio is a bonus and never a dependency.
That was already rule one of `0026` ("text before speech"); it is now stated there in those
words, and it is Arjun's build to honour. If the hall eats the sound, the demo is a screen a
judge can read over a shoulder, with a voice they may or may not catch.

### For the run of show

1. Days before: media volume to maximum (volume keys during playback, or
   `adb shell cmd media_session volume --stream 3 --set 15`; the probe prints the maximum for
   this phone). Do-not-disturb off.
2. Plug the wired speaker (USB-C or 3.5 mm; never Bluetooth) into the demo phone and run
   `TtsVoiceProbeTest`; the route line must name the wired device and the tone must be heard
   from it. Repeat with it unplugged: the route must be `BUILTIN_SPEAKER`.
3. On the morning: airplane mode on, Bluetooth icon absent, speaker plugged, one spoken turn
   end to end from the back of the room before the judges arrive.

---

## Addendum 7, 20 September 2026, 18:10: what the app says back, and in which language

The question nobody had asked: the input is Hindi, the voices can speak Hindi, but is there
anything Hindi to say? Traced end to end in the code and measured where the code could not
answer. **Today a Hindi-profile user hears English, and hears it through a Hindi voice.**

### Where each spoken word comes from

| what is spoken | source | language today | why |
| --- | --- | --- | --- |
| the lead-in ("Noting that down.") | `tts_lead_in_*` in `res/values/strings.xml`, read by `AndroidTriggerStrings` through the app's resources | English | resources follow the app's UI locale, which the run of show sets to English; `values-hi/strings.xml` is empty by design ("Telugu is filled first") |
| the rules engine's trigger and referral sentences | `trigger_*` keys, same path | English | same |
| the figures the model is shown ("Iron: 2.9 mg") | `context_*` keys, same path | English | same |
| the LOG confirmation (`phrase`) and the ANSWER / RECOMMEND prose | the model, whose prompt says `Language to reply in: <tag>` with the profile's speech language | **English** | measured below: the model answers in English whatever the tag says |
| the voice it is all read in | `speak(text, language)` picks the voice from the PROFILE'S speech language | the Hindi voice | so English words go through Hindi phonology, the mirror image of what the contract forbids |

### The measurement

`logs/meera-hindi-reply.log`, 18:00. The phone's exact GGUF (`qwen2.5-1.5b-instruct-q4_k_m.gguf`,
the same file, same quantisation) through Ollama on the laptop, temperature 0, context 2048, the
system and user text copied verbatim from `Prompts.phrasing` and `ConversationPrompts.answer`
(SHORT), figures composed by hand from the string templates. Seven runs; the count that matters
is the script of the reply:

| prompt | tag | question | Devanagari letters in the reply | Latin letters |
| --- | --- | --- | ---: | ---: |
| phrasing (LOG) | `hi` | — | 0 | 296 |
| phrasing (LOG) | `hi-IN` | — | 0 | 231 |
| phrasing (LOG) | `en-IN` | — | 0 | 255 |
| answer SHORT | `hi` | aaj maine kitna iron khaya | 0 | 144 |
| answer SHORT | `hi` | इस हफ्ते मुझे कितना आयरन मिला | 0 | 149 |
| answer SHORT | `hi-IN` | मैंने आज दो रोटी और दाल खाई, कितना प्रोटीन था | 0 | 184 |
| answer SHORT | `en-IN` | how much iron did I get this week | 0 | 189 |

**Seven of seven English, including three questions asked in Devanagari with a Hindi tag.** The
`Language to reply in` line does not make this model reply in Hindi, at this size and
quantisation, on this runtime. This is a desktop measurement of the LANGUAGE of the reply; `0011`
says an argmax can move across binaries, so the phone could differ in wording, and nothing here
says it would differ in script. The phone has never been asked: every conversational row in
`logs/hw-report-conversational.txt` was run with `languageTag = "en-IN"`.

Two things seen in the same runs that are not mine and are passed on rather than judged: the
SHORT answer with the `hi` tag said "which indicates anaemia" (a condition the person did not
declare; Priya's safety line is the guard that must catch it on the phone), and the `en-IN`
answer invented "9.5 mg" and did the subtraction out loud (the numeric guard's case, and the
`0014` finding again).

### What follows

1. **The demo speaks English back.** Input Hindi, output English, which is how people here
   actually use software, and it is not a failure; it is a fact the deck must not contradict.
   The line "says it back in the language you chose" cannot be shown live on Saturday. Nila has
   the wording that can.
2. **The voice must follow the text, not the profile.** `spokenLanguageOf(text, preferred)` in
   `ml/tts` reads the script of the text (Telugu, Devanagari, otherwise Latin as English) and
   falls back to the profile only when there are no letters at all. One line in the
   orchestrator's `speak()`: `tts.speak(text, spokenLanguageOf(text, lang))`. Until Rao lands
   it, English answers go through the Hindi voice; with it, they go through the English voice,
   which on the platform is the most widely installed voice there is.
3. **The ladder's critical rung is English now, not Hindi.** Platform `en-IN` (or any offline
   English voice; `AndroidTtsEngine` already accepts one) first; the bundled insurance is
   `en_GB-cori-medium` (dataset LibriVox, "public domain" on its card, a British voice, sha256
   `1899f98e5fb8310154f3c2973f4b8a929ba7245e722b3d3a85680b833d95f10d`, stamped, handle
   `PiperVoices.ENGLISH_CORI`, staged and bound to nothing), under the same handset condition as
   addendum 5. Candidates for Vedant, who judges English himself:
   `logs/tts-candidates/en-cori-default-{leadin,plate,answer}.wav`. The Hindi voices keep their
   place for the day a Hindi sentence exists to speak; the provenance call on `rohan` stops
   being load-bearing for the 26th and stays open on its merits.
4. **Making the app say Hindi back is real work with an unknown outcome, and it is not TTS
   work.** Either the model is made to reply in Devanagari (a prompt change Priya would have to
   measure on the phone, against a 1.5B model whose Hindi has never been seen) or the string
   table gets a Hindi column (`values-hi`, ruled not to be built this week). Neither is on the
   critical path once the deck says what the app does.

---

## Addendum 8, 20 September 2026, 18:50: both branches of the handset question, checked to be a flip

### A correction to addendum 5 first

Addendum 5 says a Piper voice "ships inside the APK: no install, no network, no device lottery".
The first clause is wrong and is corrected here rather than left. **No model ships in the APK.**
The LLM, the recogniser and every voice are staged in the app's external media directory by
`adb push` (`AppModule`, `0012`); the `full` flavour's downloader is a manifest comment, not
code. So the demo phone, whichever it is, has to be in the team's hands with a cable once before
the day for the 1 GB LLM regardless, and in that same session a 63 MB voice is pushed the same
way. What addendum 5 got right is the reason the ladder inverts: the bundled voice is
DETERMINISTIC (the same file, the same sound, tested on the realme), and the platform voice is
DEVICE-DEPENDENT (never heard on the iQOO until that session, per-install data, silent when
network-only). "No install" was the wrong word for it; "same on every device" is the right one.
The condition and the ruling stand on that ground.

### The two branches, as they are on master tonight

| | branch A: the handset is in hand and rung 1 passes on it | branch B: no handset, or rung 1 fails |
| --- | --- | --- |
| the flip | `TtsFlags.PLATFORM_VOICE_FIRST = true` (as it is) | `= false` |
| where the order is applied | `demoTtsEngine(platform, bundled)` in `ml/tts`, ordered by the flag; **`AppModule` still constructs `RoutingTtsEngine(listOf(...))` directly** (Rao's one-line swap is asked for below); until then branch B is two lines, the flag and his | same |
| the English voice | the platform's; `AndroidTtsEngine` takes any offline English voice, `en-IN` first | `PiperVoices.ENGLISH_CORI`, now IN `byLanguage`, so even with the flag `true` a phone with no offline English voice falls through to it, and with the flag `false` it is first |
| what must be on the phone | Hindi and English platform voice data installed once, on a network (run-of-show step) | `tts/en_GB-cori-medium/{model.onnx,tokens.txt}` pushed like the LLM; `espeak-ng-data` is in the APK's assets (Nila, landed); the `ModelFamily.TTS` loader entry is wired (Rao, landed) |
| what is measured | the probe on the iQOO: `en-IN` voice, `network=false`, RTF, and the route after capture | the same probe reports the bundled voice's RTF once staged; the realme run is the stand-in until then |
| the one thing that is not a flip | none | if the bundled voice's phone RTF is above about 0.5, streaming playback (addendum 3) is an evening's work before the 26th; the seam is shaped for it and it is not built until that number exists |
| Vedant's one word on the clips | irrelevant to A | "no" removes the `ENGLISH_INDIA` line from `byLanguage` (one line) and branch B has no English voice: English is text on screen, and the ladder's third rung is the demo |

So: A is a constant. B is a constant plus files that are pushed in the same cable session the LLM
needs anyway, with one conditional evening hanging on a number nobody has yet. Nothing in either
branch is a project by construction; the only project is conditional on the phone's speed.

### The reader of the guarded outputs, named before the run

Vedant's ruling on the two forbidden outputs (addendum 7) said a person reads the device
outputs and did not say which person. Rao wrote the prompts and Priya wrote the guards, and a
reader who wrote either side is the same circularity the safety set's own header warns about.
**Nila reads them first** — she wrote neither, auditing is her established role, and the deck
audit is the evidence she does it. **Vedant signs off after her.** Recorded here and in the case
file so the reader is named before the run rather than volunteered after it.

---

## Addendum 9, 20 September 2026, 19:20: the realme is the backup demo device. Ruled.

### Why

Every model reaches a phone by cable (addendum 8), so the handset the organisers provide is
loaded at a table on the morning. One step in that chain can fail in a way no cable, port or
Wi-Fi works around: a vivo or iQOO ROM that wants a vivo account and a network before it allows
an install over USB. Our own demo conditions forbid both. A demo that depends on a device nobody
has touched, prepared in a hall under time pressure, is one failure away from nothing.

### The ruling (Vedant, 20 Sep)

**The realme RMX3780 is the backup demo device: fully staged, fully rehearsed, in the bag on the
26th beside whatever handset the organisers provide.** It is already the test phone, the models
are already on it, and every number in the deck came off it, so it costs nothing. If the loaner
is fine, the loaner is used. If the loaner wants a login, or the cable drops, or the push takes
twenty minutes instead of four, the phone that already works comes out of the bag and the demo
carries on.

This is a first-class item in the countdown and the pre-demo checklist, not a contingency line:
**both phones staged, both rehearsed, the realme's battery charged the night before.**

### What that changes in this record

- The ladder (addenda 4, 5, 8) is walked on BOTH phones. On the realme every rung is a
  measurement already scheduled: the probe's `en-IN` and `hi-IN` rows, the route after capture,
  the bundled voices' RTF. The realme's answer is known before the loaner exists; the loaner's
  answer, if it arrives, is a lookup against the same rows.
- `TtsFlags.PLATFORM_VOICE_FIRST` is set for the phone that goes on stage. If the two phones
  disagree (the realme has an offline English voice, the loaner does not, say), the flag follows
  the realme, because a bundled-first order costs the realme nothing but a British accent while
  a platform-first order costs the loaner its voice; `demoTtsEngine` makes that one constant.
- The staging script Rao is asked for (addendum 8's log entry) is run once on each phone, timed
  on the realme first, so the loaner's push has a number to be compared against.
- The voice-data install, the volume, the wired speaker and the route check are done on the
  realme this week and on the loaner the hour it arrives; the realme's results are the ones the
  deck can quote.

---

## Addendum 10, 20 September 2026, 19:50: the platform voice is the English path, and the fallback is a voice Vedant rejected

### The two verdicts

**Vedant on the bundled English voice** (`en_GB-cori-medium`, the three clips at its default
rate): all bad, too fast, sounds machine-made. Taken as the answer on cori as a shipping voice:
if he can hear it is wrong, it is wrong.

**Rao's probe on the realme** (`logs/tts-probe/katori-tts-report.txt` in his worktree, 14:47,
quoted): the platform engine is `com.google.android.tts`, 473 voices; for `en-IN`,
`isLanguageAvailable=1` and four offline voices, `en-in-x-{ena,enc,end,ene}-local`, plus the
legacy `en-IN-language`; `AndroidTtsEngine would use: en-in-x-enc-local`; the sample rendered
`done in 2562 ms for 3.66 s of audio, RTF 0.70`. Hindi and Telugu likewise offline. Rao's
reading, which the report supports: only the FIRST synthesis after binding is slow (RTF 1.87 to
3.60 across the three languages, 2.3 to 3.5 s); the second Telugu call ran at RTF 0.04 (195 ms
for 4.81 s). So **rung 1 applies on the realme: `PLATFORM_VOICE_FIRST` stays `true`**, the
platform engine is warmed at app start so the first-call cost is paid before anyone speaks
(`prepare()` at launch; Rao's suggestion, the engine already binds once and keeps it), and the
English voice the demo uses is one of the four Google voices, chosen by Vedant's ear.

### What the fallback is, said plainly

`PiperVoices.ENGLISH_CORI` stays in `byLanguage` as the insurance for a handset with no English
voice data installed, which on an unseen iQOO is a real possibility. **That fallback is a voice
Vedant has rejected on quality.** If the app ever falls through to it, the demo sounds worse
than rehearsed, and this record is where the reason is: the ladder is not uniform, and it is
written that way rather than pretended otherwise. Falling through is a recoverable state (the
text is on screen, the voice is intelligible if machine-made); having no English voice at all
would not be.

### "Too fast" is a parameter

Speaking rate is `TtsFlags.SPEECH_RATE`, applied as the platform's `setSpeechRate` and Piper's
`speed`, 1.0 today. The bundled voice is re-rendered at 0.9, 0.85 and 0.8 on the same three
sentences (`for-vedant-english-slower.zip`), so the second verdict separates the rate from the
voice. `TtsVoiceProbeTest` now renders every offline `en-IN` voice at 1.0, 0.9 and 0.85 on the
same three sentences, so the platform candidates arrive in the same form from Rao's next run;
the one Vedant accepts sets the constant. His first platform sample, `en-in-x-enc-local` at the
default rate, is already in `for-vedant-platform-english.zip`.

### The handset-arrival list, top item

On the iQOO, before anything else and before airplane mode goes on: is English voice data
installed for the platform engine? Five minutes, on whatever network the hall has, in the
phone's text-to-speech settings; then the probe's `-- ENGLISH_INDIA` block must show an
`en-in-x-*-local` with `network=false`. If it cannot be installed, the demo speaks through the
rejected fallback or through the realme, which is the backup device (addendum 9), and that
decision is made in those five minutes, not on stage.
