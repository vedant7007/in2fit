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

## The IIT Madras Indic TTS licence: one document, three voices (read from the committed text on 20 Sep; ruling at the end of this section)

*Added 20 Sep 2026, 04:05, Meera.*

`https://www.iitm.ac.in/donlab/indictts/downloads/license.pdf` is the licence the SPRINGLab
dataset cards point at ("subject to the original Indic TTS license terms"). It gates, together:

- `te_IN-maya-medium` and `hi_IN-rohan-medium` (Piper voices trained on Indic TTS data; excluded
  above because this document could not be read),
- `prasadvittaldev/pocket-tts-telugu-female-syspin` (its teacher was trained partly on
  `SPRINGLab/IndicTTS_Telugu`; `0019` addendum 2).

**Status of the text: not in this repository.** From the laptop this project runs on, the host
resolves (103.158.42.45), `http://` answers with a 302 to `https://`, and `https://` times out
on every route tried on 20 Sep: curl with four TLS settings, PowerShell's HTTP stack, and the
fetch service. Vedant reached it the same night from elsewhere. So the document exists and is
readable; this project has not yet read it.

**What the document is reported to say**, from Vedant's fetch, 20 Sep 2026. THIS IS A SUMMARY,
NOT THE TEXT, and `0005`'s rule stands until the text is here: royalty-free; derivative works may
be created and freely distributed; commercial use permitted; governed by Indian law; the TTS
Consortium and IIT Madras copyright notice must be retained; and one clause with teeth:
recipients of a derivative work must be told it contains open-source components and shall not
further sell, lease, sub-license, decompile or reverse-engineer it. If that clause binds, it is a
downstream restriction on whatever this app ships, not a formality, and it has to be read in
its own words before anything under it ships.

**The open question, recorded and not resolved:** whether model weights are a derivative work
of the audio they were trained on is unsettled law. The pocket-tts card claims MIT for its
weights while deferring to the corpora's terms; Piper's cards carry the dataset licence forward
as the voice's licence. Both positions are recorded here as claims. This project does not pick
one; it ships only what is clear under either reading.

**To close this:** the PDF committed under `docs/licences/` (Nila's directory; a few kilobytes)
or its text pasted into this record verbatim, then the three voices re-ruled here on the same
day. Until then: `maya` and `rohan` stay excluded, pocket-tts stays out of the register and off
the phone.

### The text is in the repository. Filed 20 Sep 2026 (Nila). NOT yet diffed against the PDF.

`docs/licences/iitm-tts-eula.txt`, 4,921 bytes, sha256
`3dcfe36df6afa2e73849491333f25b0017682bd76cc7169652ea25fda6510f0c`, byte-identical to the file
Vedant handed over, fetched from the URL above on 20 Sep 2026 from a machine that can reach it.
**Its provenance header says what it is: machine-converted from the PDF by a fetch service,
not read from the PDF by a person.** The PDF itself is being downloaded separately; when it
lands it is diffed against this text and any difference is recorded here BEFORE anyone rules
from this text. Nothing below is a ruling. It is what the converted text says, quoted where
the words matter, so the ruling can be made against words rather than a summary.

**What the text settles, and what it does not.** It settles the TERMS a licensee of the IITM
distribution takes. It says nothing about PROVENANCE: whether `maya`, `rohan` or pocket-tts
actually descend from IITM data, and whether weights are a derivative of audio, are the chain
questions above and this document does not touch them. Those remain Meera's to close.

**2.1, the grant.** "a perpetual, non-exclusive, worldwide, transferable, sub-licensable,
royalty-free license to a) make copies of the Licensed Software in source and object code and
data; b) modify copies of the Licensed Software and data to create derivative works thereof."
Then: "The Licensee will exclusively own all software, files, documentation, discoveries,
ideas, inventions, improvements, processes, materials and data ("Derivative Work")
acquired/prepared/generated/developed in any medium by Licensee using the Licensed Software",
with an irrevocable assignment to vest that ownership, and: "Notwithstanding anything to the
contrary, Licensee shall be allowed to freely distribute the Derivative Work."

**2.2, the clause the summary above called "teeth".** In its own words: "The Licensee agrees
not to remove any copyright, trademark or patent notices that appear in the Licensed
Software. The Licensee shall ensure that the third party to whom the Derivative Work is sold
is made aware that the Derivative Work has few open source component along with Licensee's
IP. Such third party shall not be allowed to further sell, lease, license, sub-license,
decompile, disassemble or reverse engineer any portion of the Licensed Software or the
Derivative Work." Read as written: the second and third sentences bind a third party "to whom
the Derivative Work is sold". That is a flow-down obligation on RESALE, not a bar on the
licensee shipping. It is not the downstream restriction on this app that the summary above
feared; it is a term this project would have to pass on if it ever SOLD a derivative.

**What lands on this project if an IITM-derived voice ships**, per the text: keep every
copyright, trademark and patent notice intact (2.2, first sentence), and, under the
capitalised line before section 6, "REDISTRIBUTORS MUST RETAIN THE FOLLOWING COPYRIGHT
NOTICE", reproduce this verbatim on redistribution:

    "COPYRIGHT 2016 TTS Consortium, TDIL, Meity represented by Hema A Murthy & S
    Umesh, DEPARTMENT OF Computer Science and Engineering and Electrical
    Engineering, IIT Madras. ALL RIGHTS RESERVED"

That notice needs a home in the app. The string key `licence_notice_iitm_tts` now holds it
verbatim, `translatable="false"`, bound to nothing until a voice under this licence ships; the
screen it goes on is raised with Arjun. Also in the text: 2.3 and 2.4 disclaim warranty and
consequential damages; 3 makes the agreement effective on download and terminable by the
licensee with destruction of copies; 4 is Indian law; 5 encourages, "though not a license
condition", bug reports and a reference to IITM in publications; 6 says contributions do not
fall under this agreement.

**Corrections to the lines above, once the PDF diff confirms the text:** `maya` and `rohan`
were excluded "because this document could not be read", and `rohan` was listed in the
rejected table as "the IITM PDF that returns empty". The text is readable now; whether the
voices are IITM-derived, and whether weights are a derivative, are the questions that remain.
Re-ruling those three is Meera's, on the same day the diff is recorded.

### Ruling from the committed text, 20 Sep 2026, 15:05 (Meera). Provisional on the PDF diff.

Read in full from `docs/licences/iitm-tts-eula.txt` at sha256 `3dcfe36d…10c`, not from any
summary. The file is machine-converted and not yet diffed against the PDF; nothing below rests
on a phrase that looks like a conversion artefact ("has few open source component"), and if the
diff changes a word that matters, this ruling is re-made the same day.

**1. The terms, as I read them.**

- *The grant covers the audio, not only the code.* §1 defines one term, "Licensed Data and
  Software", to include "all data files (voice files, lab files, pronunciation dictionaries)",
  and 2.1(b) licenses the licensee to "modify copies of the Licensed Software and data to
  create derivative works thereof". The document then uses "Licensed Software" loosely where §1
  says "Licensed Data and Software"; I read the single defined term as governing throughout.
- *"Derivative Work" is defined by the licence itself, and the definition reaches a trained
  model on its face.* 2.1: "all software, files, documentation, discoveries, ideas, inventions,
  improvements, processes, materials and data ("Derivative Work")
  acquired/prepared/generated/developed in any medium by Licensee using the Licensed Software."
  Model weights are data generated using the licensed data. So the question this record left
  open earlier, whether weights are a derivative of training audio, does not need copyright
  law's answer HERE: for material under this licence the licensor has defined the term and
  granted the right, and the licensee "will exclusively own" the result, with an irrevocable
  assignment to vest that ownership, and "Notwithstanding anything to the contrary, Licensee
  shall be allowed to freely distribute the Derivative Work." The general legal question stays
  unsettled; it is simply not load-bearing for this licence.
- *Nothing in the text is non-commercial.* Perpetual, non-exclusive, worldwide, transferable,
  sub-licensable, royalty-free; no field-of-use term anywhere. This licence does NOT go in the
  non-commercial register.
- *2.2 is a flow-down on sale, not a bar on shipping.* First sentence binds us: no removal of
  copyright, trademark or patent notices. Second and third sentences bind "the third party to
  whom the Derivative Work is sold": that party must be told the work has open-source
  components and "shall not be allowed to further sell, lease, license, sub-license,
  decompile, disassemble or reverse engineer" it. On its words it triggers on a SALE. IN2FIT is
  not sold; if it ever is, the buyer is told and bound, and that sits under the commercial
  re-audit this record already requires. One consequence worth naming: a sold derivative cannot
  be passed on by its buyer under an open-source licence, because the buyer may not
  sub-license. Not our case today.
- *What lands on us the day anything under this licence ships:* the notice quoted above,
  verbatim, on redistribution (`licence_notice_iitm_tts`, home pending with Nila and Arjun),
  and every existing notice left intact. 2.3 and 2.4 disclaim warranty and consequential
  damages; 3 makes it effective on download and terminable by us with destruction of copies;
  4 is Indian law; 5's bug reports and publication reference are "not a license condition".

**2. Provenance is a separate gate, and it stays closed. Say it plainly so the two are never
conflated:** the licence tells us what a licensee of IITM's distribution may do. It says
nothing about whether `maya`, `rohan` or pocket-tts were in fact built from that
distribution. That is a chain-of-custody question, and the evidence for it is:

| voice | provenance evidence today | what the licence text clears | what still gates it |
| --- | --- | --- | --- |
| `hi_IN-rohan-medium` | the trainer's MODEL_CARD: "URL: https://www.iitm.ac.in/donlab/indictts · Database Name: Hindi Mono Male · License: [this PDF]" | the terms, if the claim is true | a second source that the "Hindi Mono Male" database exists in the IITM distribution under this licence, the way padmavathi's card was checked against its dataset's own record; the IITM database page is reachable from India and not from here |
| `te_IN-maya-medium` | the trainer's MODEL_CARD: "URL: https://www.iitm.ac.in/donlab/indictts · License: [this PDF]"; fine-tuned from the English `lessac` voice | the same | the same second source, plus the `lessac` base voice's own licence, unread |
| pocket-tts Telugu (`prasadvittaldev/…-syspin`) | the card names TWO corpora: `SPRINGLab/IndicTTS_Telugu` (IITM) and `arpit-tiwari/syspin-telugu-tts` (SYSPIN, IISc); the student was distilled on the SYSPIN speaker | the IITM half only | **the SYSPIN half is still unlicensed**: the re-upload carries no licence and SYSPIN's own terms are unread. IITM being permissive does not clear this voice. Independently, `0019` addendum 2: no working Android path, and desktop RTF 0.8-0.9 |

Padmavathi was accepted on its card PLUS its dataset's own licence record. The same standard
applies here: card plus a second source. Until the second source is read, `maya` and `rohan`
move from "excluded: licence unreadable" to "licence read and permissive; provenance
unconfirmed", and neither ships. pocket-tts stays out on the SYSPIN gate alone, whatever the
IITM text says.

**3. What changes today.** Nothing that ships. Piper `padmavathi`/`venkatesh` (CC-BY-4.0) and
the platform voices remain the candidates; the register still holds one entry, `pratham`. The
"EXCLUDED: its licence is a PDF … that returns an empty reply" line on `maya` above and the
`rohan` row in the rejected table are superseded by this section and left in place as the
record of why the exclusion was made when it was made.

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

## COPYLEFT, recorded the day it was added

Not non-commercial and not unclear, but it makes the sentence at the top of the register
("everything else shipped is Apache-2.0, CC-BY-4.0, MIT or public domain") false from 20
September 2026, so it is recorded here rather than left for an audit to find.

| Dependency | Licence, verbatim from upstream | Added | Ships as | Record |
| --- | --- | --- | --- | --- |
| espeak-ng (the phonemiser Piper voices use through sherpa-onnx) | `COPYING`: "GNU GENERAL PUBLIC LICENSE / Version 3, 29 June 2007"; `README.md`: "eSpeak NG Text-to-Speech is released under the GPL version 3 or later license" (both read from `github.com/espeak-ng/espeak-ng`, master, 20 Sep 2026; no licence file travels in the data tarball or in the AAR) | 20 Sep 2026 | its data, 244 files / 1,067,073 B, as `assets/espeak-ng-data`; its code, inside `libsherpa-onnx-jni.so` in the sherpa-onnx static AAR | `0019` |

**What copyleft obliges, concretely.** This is a different kind of obligation from the
non-commercial register above: it does not restrict use, it attaches DUTIES TO DISTRIBUTION,
and Vedant's non-commercial ruling does not lift them. Handing the APK to a judge, a friend or
a store is "conveying" under GPL-3 §6, and espeak-ng's code is inside `libsherpa-onnx-jni.so`
in that APK. Recorded here as the duties a reader of this file would otherwise have to
reconstruct from the licence text; this is a reading of GPL-3 §§4–6, not legal advice, and the
first three are the ones that would be checked.

1. **A licence on this repository. DONE, 20 Sep 2026: Apache-2.0, ruled by Vedant.** Until
   then there was none, and an unlicensed repository is all-rights-reserved by default, which
   is not compatible with conveying a GPL-3 combined work. `LICENSE` at the root is the
   canonical text from `apache.org/licenses/LICENSE-2.0.txt`, 11,358 bytes, sha256
   `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`, copyright holder
   Vedant Manmath Idlgave. His reasoning, for the record: it matches the licences of the major
   dependencies (Qwen, IndicConformer, Google `food_V1`); it leaves the commercial question
   open; and Apache-2.0 is GPL-3-compatible in the direction that matters. If espeak-ng ships,
   the combined APK is conveyed under GPL-3 terms with the corresponding-source pointer, while
   the project's own code stays Apache-2.0. That is the standard resolution and it does not
   require GPL-licensing his future work.
2. **Corresponding source for what is actually shipped.** The binary in the APK is whatever
   sherpa-onnx 1.13.8 compiled, from its own pinned espeak-ng fork (via piper-phonemize), not
   upstream master. The duty is to be able to point at that exact source: the sherpa-onnx
   v1.13.8 tag and the espeak-ng commit its build pins. Recording that pointer in `0019` or
   here, once read from sherpa-onnx's build files, discharges it; "it is on GitHub somewhere"
   does not.
3. **Notice to the person receiving the app.** The APK must carry the GPL-3 licence text and a
   notice that it includes espeak-ng under GPL-3.0-or-later, with where to get the source
   (§4, §5(a), §6). In practice: an "open-source licences" entry reachable from the About or
   disclosure screen `0002` already calls for. Not built; the string keys do not exist yet.
4. **No further restriction** on what the recipient may do with the espeak-ng part (§10). A
   hackathon "no redistribution" line, or an app-store EULA, must not contradict this.
5. **The Piper voices are separate.** CC-BY-4.0 (Telugu) needs attribution in the same notice;
   CC-BY-NC-SA-4.0 (Hindi) is the register entry above and is a use restriction, not copyleft
   on the app.

Going commercial or closing the source does not change these duties; it makes 1 impossible,
which is why the note above says that path means replacing the phonemiser and therefore Piper.

**When the duties attach, and when they do not.** They attach on CONVEYING a copy: giving the
APK to someone, publishing it, putting it in a store. Demoing on a device the team holds is
not conveying, so **none of this blocks the event.** It attaches on publication. Both halves
of that matter: nobody should panic at the wrong moment, and nobody should relax at the wrong
one. Handing a judge an APK file is publication; showing them the phone is not.

**The question is SEPARABLE and may resolve itself.** espeak-ng exists in this app for one
reason: Piper's phonemiser. If Rao's `TtsVoiceProbeTest` finds that the phone carries an
offline Google Telugu voice, Piper drops out, espeak-ng drops out with it, and so do items 2
to 4 above. Ruled by Vedant: the corresponding-source work (item 2) is NOT done until that
probe reports. The list stays; the work waits on the measurement.
