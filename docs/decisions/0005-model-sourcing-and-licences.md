# 0005 - Model sourcing, and the licences that go with it

Status: accepted. Files downloaded and checksummed; none loaded or run.

## Scope ruling: Katori is a non-commercial student project

Ruled by Vedant, 19 Sep 2026. Non-commercial model and data licences are acceptable.

**If Katori ever goes commercial, every licence in this record is re-audited first.** The
register at the end of this file exists so that audit is a lookup rather than an
excavation. Anything added later that carries a non-commercial or unclear licence goes in
that register on the same day it is added, not at audit time.

## The spec's named ASR models do not exist

Spec 10.2 recommends "the vasista22 INT8 packs (whisper-tiny-te for Telugu,
whisper-small-hi for Hindi, whisper-tiny multilingual for English)" as
"already quantized, already in sherpa-onnx format, and downloadable per language".

Two of those three claims are wrong, and a reviewer will ask, so here is the evidence.

`vasista22/whisper-telugu-*` and `vasista22/whisper-hindi-*` are real and Apache-2.0, but
their repositories contain `pytorch_model.bin` and `flax_model.msgpack` only. **There are
no ONNX files.** No sherpa-onnx conversion of them exists, by anyone: a sweep of all 767
`csukuangfj` repositories found no Indic ASR at all, and searches for "sherpa-onnx telugu",
"whisper-telugu sherpa" and "telugu int8 onnx" returned nothing. The only Telugu Whisper
ONNX anywhere is `Susipriya/whisper-telugu-small-onnx`, which is fp32 in optimum format,
not sherpa-onnx layout, and 1.1 GB across two files.

This does NOT change the four demo beats, which is why work did not stop. Spec 10.2 already
named IndicConformer as the alternative, and the ASR ruling already said the model family
would be decided by measurement with the interface kept pluggable. This is that.

## What is actually used

| Role | Source | Licence | Size |
| --- | --- | --- | --- |
| ASR Telugu | `parismitaglobalsolutions/indicconformer-sherpa-onnx` `te/model.int8.onnx` | Apache-2.0 | 197,595,693 B |
| ASR Hindi | same repo, `hi/model.int8.onnx` | Apache-2.0 | 197,595,593 B |
| ASR English | same repo, `en/model.int8.onnx` | Apache-2.0 | 174,610,057 B |
| TTS Telugu | `rhasspy/piper-voices` `te/te_IN/padmavathi/medium` | CC-BY-4.0 | 63,516,050 B |
| LLM | `Qwen/Qwen2.5-1.5B-Instruct-GGUF` `qwen2.5-1.5b-instruct-q4_k_m.gguf` | Apache-2.0 | 1,117,320,736 B |

Roughly 1.75 GB, against the ~2 GB budget in spec 11.2. Checksums are in
`logs/model-fetch.log`.

Notes that matter later:

- The IndicConformer models are **NeMo CTC conformers, not Whisper**. They load through
  sherpa-onnx's NeMo CTC API, not its Whisper API, and `te/` and `hi/` share the repo-root
  `tokens.txt` while `en/` has its own. Getting this wrong looks like a model failure.
- That repository has 4 likes and no recorded downloads. It is one person's export, stated
  as derived from AI4Bharat (MIT) and NVIDIA NeMo (CC-BY-4.0). Apache-2.0 is its own claim.
  **It needs a smoke test on hardware before anyone trusts it**, which is why Q3 in
  STATUS.md asked before proceeding.
- The Qwen filename is **lowercase** `q4_k_m`, not `Q4_K_M`.

## Excluded, and why

- **`csukuangfj/sherpa-onnx-whisper-tiny` and the rest of that family: no licence at all.**
  No `license` field, no licence tag, no LICENSE file, and `README.md` returns "Entry not
  found". Upstream Whisper is MIT, but this repository says nothing, and absence of a
  licence is absence of a grant. This is the same reasoning that removed IFCT. English ASR
  now comes from the Apache-2.0 IndicConformer export instead, so nothing was lost.
- **`ai4bharat/vits-multilingual-itts` and `vits-multilingual-all`: `license: null`**, and
  they ship raw Coqui `.pth` checkpoints with no config or vocab. Unusable and ungranted.
- **AI4Bharat FastPitch plus HiFi-GAN, which spec 10.3 names: no ONNX exists.** The PyTorch
  checkpoints do exist on GitHub (`te.zip`, 1.51 GB: FastPitch 637 MB, HiFi-GAN 1,016 MB),
  so the spec was right that the models exist and wrong that ONNX conversions are
  available. Converting them is not an afternoon: Coqui implements `export_onnx` for VITS
  only, with zero ONNX support in its FastPitch or HiFi-GAN classes, sherpa-onnx has no
  FastPitch runtime at all, and the Telugu tokenizer would need porting. That is weeks of
  uncertain work for a quality gain Piper largely provides for free, having been trained on
  AI4Bharat's own IndicVoices-R data. Not attempted.

## Telugu TTS: better than expected

The demo is in Telugu (ruled 19 Sep), so Hindi TTS is not on the critical path.

Piper `te_IN-padmavathi-medium` is trained on `ai4bharat/indicvoices_r`, whose dataset
licence is **CC-BY-4.0**, verified on the dataset's own API record. Its MODEL_CARD states
`* License:  CC-BY-4.0` verbatim. So the Telugu voice needs no non-commercial allowance at
all, and the scope ruling above is not being spent here.

`te_IN-venkatesh-medium` is the same licence, same size and same config; the choice between
them is a listening preference and either works.

`te_IN-maya-medium` is EXCLUDED: its licence is a PDF at iitm.ac.in that returns an empty
reply, so the terms are unknown.

Integration cost, known in advance: Piper voices carry no sherpa metadata in the ONNX, so a
one-off script stamps `model_type`, generates `tokens.txt` from the `phoneme_id_map`, and
the app ships `espeak-ng-data` (about 7 MB, shared across languages) because the voice uses
`phoneme_type: espeak` with voice `te`.

Android's built-in `TextToSpeech` stays as an opportunistic fallback only, never the only
path. Its Telugu voice data is a user-initiated download and **that download needs network**,
which contradicts the offline requirement outright. The app may use it when
`isLanguageAvailable` reports a usable Telugu voice and `Voice.isNetworkConnectionRequired()`
is false, and otherwise falls through to the bundled ONNX.

*20 Sep 2026, `0019`:* the prerequisites above are done and measured (the download reads
`custom_metadata_map {}` through onnxruntime; `tools/stamp_piper_voice.py` stamps it; the
shipped espeak-ng-data is trimmed to 1.07 MB with byte-identical output). For ENGLISH the
built-in engine is the only path, deliberately, with the offline check above applied to the
voice it picks. Hindi TTS is now sourced; see the register below.

## NON-COMMERCIAL DEPENDENCY REGISTER

**One entry, added 20 September 2026, the day the voice was added.** Everything else shipped
is Apache-2.0, CC-BY-4.0, MIT or public domain.

| Dependency | Licence, verbatim from its MODEL_CARD | Added | Used for | Record |
| --- | --- | --- | --- | --- |
| `rhasspy/piper-voices` `hi/hi_IN/pratham/medium` (`hi_IN-pratham-medium.onnx`, 63,516,050 B, sha256 `169964b0871667f6793416d4b35e97357a68ba1ad01df8580c28048989ee7693`) | `* License: http://creativecommons.org/licenses/by-nc-sa/4.0/` (stated under "Dataset"; dataset given as `https://github.com/AI4Bharat/indicnlp_corpus`, trained by `https://github.com/PravalX`) | 20 Sep 2026 | Hindi spoken output. Not on the demo path; the demo is Telugu | `0019` |

What an audit would need to know: the NC-SA term is on the training data, the model card
carries it forward, and there is no other Hindi Piper voice with a readable permissive licence
(`rohan` is the IITM PDF that returns empty, `priyamvada` is the same NC-SA). Going commercial
means replacing this voice or dropping Hindi speech to on-screen text.

Evaluated and rejected on other grounds, recorded here so a future audit does not have to
rediscover them:

| Dependency | Licence | Why not used |
| --- | --- | --- |
| `willwade/mms-tts-multilingual-models-onnx` `tel/` | CC-BY-NC-4.0 | Would have been the only NC item. Piper is CC-BY-4.0, 79% smaller and higher sample rate, so NC was not needed |
| `facebook/mms-tts-tel` | CC-BY-NC-4.0 | Upstream of the above, PyTorch only |
| piper `hi_IN-priyamvada` | CC-BY-NC-SA-4.0 | Same licence, size and trainer as `pratham`, which is the one used; a listening preference, not a licence difference |

**Rule for anything added later:** a dependency whose licence is non-commercial, unclear, or
a link to an unreachable document goes in this table the day it is added. If it cannot be
recorded here with a verbatim licence, it does not ship.
