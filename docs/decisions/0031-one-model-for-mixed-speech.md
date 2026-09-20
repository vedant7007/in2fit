# 0031. One model for code-mixed speech: what exists, measured, and the decision it leaves Vedant

Date: 20 September 2026. Status: **DECIDED by Vedant, 20 September 2026: Option A.** The evidence
below was gathered before the decision and is unchanged by it. Nothing here ran on a phone; every
clip is synthetic and every number is a desktop number on a shared laptop.

## The decision, and the gap it knowingly leaves

**Option A. IndicConformer, one checkpoint per the language the user selects in their profile.**
The output language is the profile setting; the checkpoint is selected from it; one code path
(`DefaultAsrEngine.listen(language)` -> `AsrModels.handleFor(language)`).

Vedant's reasons, in order:

1. B puts Telugu in Malayalam script on 5 of 6 mixed clips and Hindi in Urdu on 2 of 3, and sits
   12-27 WER points behind on the same 16 clips. Six days out, with nothing running end to end,
   we do not ship a 365 MB engine nobody has heard on a real Telugu voice.
2. The finding that decides it: a Telugu voice saying English words the Telugu way transcribes
   fine through the te model, and the spliced foreign-accent clips overstate the problem. So the
   code-mixing failure we actually have is in the MATCHER: 0 of 25 transliterated food words
   resolving is a matcher bug, and Priya is fixing it.
3. A does not literally meet "one model for any mix", and nobody is pretending it does.

**KNOWN GAP, IN PLAIN WORDS.** With A, an utterance is decoded by the model for the language the
user selected. Words from another language come out transliterated into the selected language's
script (Hindi दाल inside a Telugu sentence becomes దాల్; English "milk" becomes మిల్క్). Food names
survived that on every synthetic clip, and the matcher is being taught those renderings. What A
cannot do: produce Devanagari or Latin inside a Telugu transcript, or recognise a sentence spoken
mostly in a language other than the one selected. A Telugu-profile user who speaks a whole
sentence in Hindi gets a Telugu-script transliteration of Hindi, not Hindi. That is the gap, and
it was accepted knowingly, not buried.

**RECORDED DIRECTION IF THE GAP BITES: Option B**, Meta Omnilingual ASR CTC-300M (Apache-2.0,
365 MB, one output space, English in Latin) plus a deterministic Indic-script normaliser to the
selected output language. Its evidence is in §3 below and the harness already runs it
(`tools/asr_eval.py wer --engine omnilingual`).

**REOPEN CONDITION, the only one:** the real speaker recordings show A failing on natural
code-mixed speech. Not synthetic clips, not spliced voices, not this record's tables: recorded
people. That is the test and it is the only test.

**The profile field** (decided by Vedant, 20 Sep): `ProfileEntity.speech_language_tag: String?`,
nullable, no default, values exactly `te`, `hi` or `en-IN`; the user picks at onboarding; never
detected. Rao's `DefaultOrchestrator.speechLanguage()` resolves it to `SpeechLanguage` and the
checkpoint follows.

## Demo language: the mitigation for the gap, decided 20 September

The gap above bites hardest in exactly one place: on stage. **The demo is performed by Vedant, and
Vedant does not speak Telugu.** With the profile set to `te`, his own spoken sentence would go
through the Telugu checkpoint and come back as a Telugu-script transliteration of Hindi or English,
which is the failure this record just described. Telugu had quietly become the assumed demo
language because it is the language the TTS voice, the reviewed strings and the first smoke test
were in. Nobody on stage speaks it.

Decided:

1. **The demo runs in the language the presenter actually speaks.** Hindi or Indian English until
   Vedant says otherwise. Code-mixing stays the headline claim and still holds: "do roti aur thoda
   dal" is code-mixed, it is what he says naturally, and it goes through the right checkpoint.
2. **Telugu stays in the demo as the language picker and the reviewed interface strings.** That
   shows the localisation without betting the live recognition on a language nobody present speaks.
3. **The demo utterance set is the best-measured thing in the repo**:
   `data-authoring/demo-utterance-set.csv`, ten sentences across the four beats and the four
   intents, each in Hinglish and in Indian English, with `spoken` (what the presenter reads) and
   `reference` (what the checkpoint emits) kept apart. Not a corpus: the sentences we will say.

**First measurement**, synthetic (Piper `hi_IN-pratham` for Hindi, a Windows English voice for
English; `tools/asr_eval.py synth-csv` then `wer`; `logs/asr-eval-demo-synthetic.log`):

| checkpoint | exact | WER | CER | what broke |
| --- | ---: | ---: | ---: | --- |
| hi on the Hinglish rows (before the row-3 edit) | 7 of the ten | 6.2 % | 1.8 % | एग→एक once, करूँ→करू, इडली और सांबर→इरली और सांबा |
| en on the Indian-English rows | 2 of the ten | 19.1 % | 13.0 % | **every Indic food word**: rotis→rotees (x3), dal→dell (x5), katori→catery, idlis→idies, sambar→sombre |

Every रोटी, दाल, चावल, दही, दूध from the hi checkpoint arrived in the Devanagari the alias table
already holds (`रोटी->chapati`, `दाल->toor_dal_tadka`, `चावल->rice_cooked`, `दही->curd`,
`दूध->milk_whole`). The en checkpoint has a 1,024-piece English vocabulary and no Indic food word
in it; "dell" for dal on five of five sentences is not an accent artefact. **On this evidence the
safer stage path is a Hindi profile and Hinglish speech, not an English profile and Indian
English.** The synthetic voices are not Vedant; his own recording of the ten sentences, named
`vedant_hi_01`..`vedant_hi_10` (or `_en_`), through
`tools/asr_eval.py manifest <folder> data-authoring/demo-utterance-set.csv` and `wer`, is the
number that decides the column, and it is the one number in this project that must be good on
the 26th.

**RULED by Vedant, 20 September: demo speech language `hi`, utterances Hinglish. PROVISIONAL on
Vedant's own recording of the ten sentences.** The Windows voice is not the presenter. If his
recording disagrees with it, the ruling flips to `en-IN` the same evening and Priya's Devanagari
work changes shape, which is why that recording is his highest-priority task tonight and why this
paragraph says provisional rather than settled. Provisional in a second way too: it assumes Vedant
does the speaking. If a teammate speaks Hindi or Telugu more fluently, the right presenter for the
voice beats may not be him, and that changes the profile default we ship. The harness scores a
second presenter's twenty files against the same references in one command (`manifest` on a folder
holding both speakers' files, `wer` prints a by-speaker table), so that comparison is an evening,
not an afternoon. Consequence recorded for Rao: staging
`asr/en/model.int8.onnx` on the phone is off the critical path; keep it as the fallback the flip
would need, not as an urgent item.

The demo set was re-measured after one edit: "boiled egg" said inside a Hindi sentence came out as
**एक**, the number one, twice (`logs/asr-hi-emitted-forms.log`), so row 3 now says उबला अंडा. With
that, the hi checkpoint gets **8 of the ten demo sentences** exact (2.5 % WER / 1.1 % CER on this
regeneration; Piper is not bit-stable between runs) and the en checkpoint **2 of the ten**
(19.1 % WER, unchanged).

**THE CAVEAT THAT TRAVELS WITH THAT NUMBER.** Changing row 3 was legitimate: a presenter chooses
their words, and choosing words the recogniser handles is preparation, not cheating. But the
number is now **the accuracy of ten sentences we selected, on synthetic audio, after tuning the
sentences to the model. It is not the accuracy of the system on arbitrary speech**, and in two
weeks somebody will quote it as if it were. It is reported, always, as "n of the ten demo
sentences", never as a WER figure standing alone, and `tools/asr_eval.py` prints that sentence
itself on any demo-set run so the bare figure cannot come out of the tool. Nila's deck audit has
the same line and rejects the figure if it appears on a slide as a general claim. The general
claim, when there is one, comes from the recorded speakers.

### What the hi checkpoint emits, exactly, for Priya's matcher and classifier

Measured through the same Piper voice (`logs/asr-hi-emitted-forms.log`), checked against the
database at `5144f8f` (870 aliases; Priya's Telugu renderings and the Devanagari units landed
that afternoon). An earlier line in this record said the unit table was English-only; that was
true of the database I first read and is not true now, and the claim is withdrawn.

- **Question markers arrive as Devanagari, every one exact**: क्या, कितना, कितनी, कैसा, कब, कौन
  सी, क्यों, कहाँ, चाहिए, बताओ, and the code-mixed verb ऐड करूँ. The roman forms (kya, kitna,
  kaisa) never reach the classifier from speech; they are the typed-input path only. A marker set
  for the spoken demo has to hold the Devanagari half, and that half is the one the demo depends on.
- **Units**: कटोरी, चम्मच, बड़ा चम्मच, छोटा चम्मच, गिलास, प्लेट, कप, कटोरा arrive exactly and are in the
  unit table. Not in it: the English loans said in Hindi, पीस (piece) and बाउल (bowl). Minor.
- **Alias gaps, food exists** (demo-blocking on the hi path): इडली→idli; सांभर AND सांबर→sambar (the
  model emitted the भ form for a speaker saying the ब form); डोसा→plain_dosa (only दोसा exists);
  उपमा→upma; पोहा→poha_upma; ऑमलेट→omelette; छोले→chana_masala; बिरयानी→veg_biryani;
  दाल फ्राई→toor_dal_tadka.
- **Food gaps, nothing to alias to**: पनीर, पालक पनीर, पराठा, सब्ज़ी, राजमा, खिचड़ी. Data authoring,
  not matching.
- **Unstable renderings to know about**: ब्रेड→ब्रिड, पराठा→पराठक, बॉयल्ड एग→बॉयल एक, and the "egg"→एक
  case above. One voice, one rendering each; a recorded speaker will vary them.

## The crowded hall: where the recogniser and the endpointer break, measured

Every clip before this section was clean audio at the microphone. The battle is a hall: other
teams demonstrating, a PA, judges talking over each other, and a presenter holding a phone at
arm's length. Measured on the laptop, `tools/asr_eval.py robustness` on the ten hi demo rows
(`logs/asr-robustness-hall-hi.log`; run 1 and run 2 kept beside it): babble is twelve other
synthetic voices at once at a fixed conversational level, so the SNR is set by how loud the
presenter is at the microphone; distance is attenuation plus a textbook exponential reverb tail
(arm's length: -12 dB, direct-to-reverberant 0 dB, RT60 0.6 s; across the room: -20 dB, -5 dB,
0.8 s); a "burst" is one louder voice, 10 dB over the babble, for 2 s starting 300 ms after the
sentence ends: a laugh, a PA line. The endpointer is a faithful port of `EnergyEndpointer` with
2 s of hall before the sentence and 3 s after. **Synthetic hall, synthetic presenter. This says
where THIS recogniser and THIS endpointer break, not what the hall will be.** Between two runs
with different babble draws the noisy rows moved by one to three sentences; the shape did not.

Two columns matter. **"Cut right"** is the recogniser on the sentence at its true edges: the
floor of the model, and what push-to-talk would hand it. **"As heard"** is the recogniser on the
clip open listening would actually hand over: nothing when the VAD never started, the sentence
plus the hall it kept when it closed late, minus the tail it dropped when it closed early.

| condition | cut right, of ten | as heard, of ten | endpointer, open listening |
| --- | ---: | ---: | --- |
| clean, phone at the mouth | 8 | 9 | starts and ends every sentence |
| babble, voice 20 dB above it, at the mouth | 8 | 8 | clean |
| babble, +15 dB, at the mouth | 8 | 8 | clean |
| babble, +10 dB, at the mouth | 7 | 8 | clean; closes 0.6 s early on average |
| babble, +5 dB, at the mouth | 4 | **0** | **never starts on 6 of ten**; cuts the other four 1.4 s short |
| babble, 0 dB, at the mouth | 1 | **0** | **never starts, 10 of ten** |
| babble +20 dB **+ a burst after the sentence** | 8 | **0** | closes 2.2 s late on 8 of ten (the laugh goes to the recogniser), runs on past 3 s on 2 |
| babble +15 dB **+ a burst after the sentence** | 8 | **2** | closes 1.5 s late on 9 of ten, runs on 1 |
| arm's length, quiet room | 7 | 9 | clean |
| across the room, quiet room | 5 | 5 | clean |
| arm's length + babble +15 dB | 7 | 7 | clean |
| arm's length + babble +10 dB | 4 | 4 | one never starts; closes 0.7 s early |
| arm's length + babble +5 dB | 1 | 0 | never starts on 5; cuts the rest 1.8 s short |
| across the room + babble +10 dB | 2 | 3 | clean; closes 0.8 s early |

**Where it breaks, in three sentences.** At the mouth, open listening holds the clean score in
steady babble down to a voice 10 dB above the room (8 of ten as heard) and collapses to 0 of ten
at +5 dB: the energy VAD needs speech three times its adaptive floor, the floor has become the
babble, and it either never starts or cuts the sentence a second short. **A single burst after
the sentence collapses it in babble the recogniser is comfortable in: +20 dB, 8 of ten cut
right, 0 of ten as heard**, because the VAD waits for 700 ms of quiet and a laugh 300 ms after the
last word is not quiet, so the laugh is transcribed with the sentence. **Distance costs more than
moderate babble**: arm's length in a quiet room costs one to two sentences, and a voice at 60 cm
is roughly 20 dB quieter at the microphone than at 5 cm, so the same hall is "+20 dB, 8 of ten"
with the phone at the mouth and "0 dB, nothing heard" at arm's length. Microphone distance is
the variable, and it is the one we control.

**What we do about it, all cheap, all decided before the day, not on it:**

1. **Push-to-talk for the voice beats, not open listening.** It is the only mitigation on this
   list that removes the burst failure, which is the failure a hall actually produces: nobody
   controls when the next table laughs. Press, speak, release; the clip between press and release
   goes to `AsrEngine.transcribe()`, which already exists and is the path `AsrDeviceTest` and the
   harness use; the "cut right" column is then the column that applies. It changes the Talk
   screen's gesture (Arjun) and adds a small capture class in `ml/asr` (a `Flow` of levels while
   held, one `transcribe` on release, minimum hold 300 ms refused as INPUT_NOT_USABLE like a cough
   is today); it does not touch the frozen `AsrEngine` interface and the screen's event handling
   is unchanged.
2. **The phone is held at the mouth when speaking, and the run of show says so.** Speak, then
   show; never both at arm's length. If the screen must be seen while speaking, a second person
   mirrors it. This is what turns "0 dB" into "+20 dB" and it costs a sentence in the script.
3. **A wired headset or lapel microphone in the bag.** Microphone at the mouth whatever the hand
   does; a cable, so it costs nothing on the airplane-mode claim, unlike anything Bluetooth.
   `AndroidAudioSource` records from `VOICE_RECOGNITION`, which follows a connected wired
   microphone; the ten sentences must be run once on the phone through it before the day, because
   a headset capsule and its gain are a different signal from the handset's array.

**The thumb question, measured** (escalated by Vedant via Arjun): with push-to-talk the sentence
ends where the thumb lifts, so how late or early may it lift? `tools/asr_eval.py tail` cuts each of
the ten hi demo clips N ms after (negative: inside) the last word, clean and in +15 dB babble
(`logs/asr-tail-after-last-word.log`), exact of the ten:

| cut relative to the last word | clean | +15 dB babble |
| --- | ---: | ---: |
| 300 ms inside the word | 0 | 0 |
| 200 ms inside | 6 | 5 |
| 100 ms inside | 8 | 7 |
| on the last syllable (0 ms) | 8 | 8 |
| 150 / 300 / 600 / 1,000 ms after | 8 / 8 / 8 / 8 | 8 / 8 / 8 / 8 |

A late thumb costs nothing up to a second; an early one costs the sentence by 300 ms inside the
word. So `PushToTalk` records a **300 ms release tail** after the thumb lifts (free by the table,
and it turns a thumb that lifted on the last word into one that lifted after it), and the
presenter's instruction is **"finish the word, then let go"**, not "wait a beat": the beat is not
needed and the tail covers the times he is early anyway.

**RULED by Vedant, 20 September: push-to-talk ships for the voice beats**, on the burst rows: 8 of
ten to 0 of ten on one laugh, in babble the recogniser is otherwise comfortable in, is not a risk
to mitigate but a thing that will happen in a room full of teams demonstrating and judges
reacting. Order of the mitigations as above: 1, then 2 in the script, then 3 tested once and in
the bag. The headset does not touch the burst failure, only the distance one, so it is insurance
and push-to-talk is the fix. `ml/asr/PushToTalk.kt` implements it against the existing
`AsrEngine.transcribe()` with the same events as open listening, so the Talk screen changes its
gesture and nothing else; the frozen `AsrEngine` interface is untouched.

## "I didn't catch that": the path where a wrong-language sentence is not logged

**The finding first, plainly: garbage was already not being saved.** `LookupMealResolver` refuses
on the first item the lookup cannot match, and `DefaultOrchestrator.plate()` turns that into
`NeedsConfirmation(NO_MATCH, parsed)` and completes without writing. The system was correct. What
is wrong is the MESSAGE: NO_MATCH renders as "I do not know that food", which tells a judge the
database is missing an item when the truth is that nothing on the plate was speech the app
understood. A correct system with a wrong message is a different defect class from a broken one,
and the change below must not be written up as fixing a bug that logged wrong meals, because no
such bug existed.

Agreed threshold (Jacob, for Priya's matcher and Rao's orchestrator; the code is theirs):

- **0 of N extracted items resolve, N >= 1: end the turn with "I didn't catch that, say it
  again."** The resolver evaluates every item rather than stopping at the first miss, and when
  none resolves returns `INPUT_NOT_USABLE` (already in the vocabulary: "the input itself was not
  usable", the same reason the ASR uses for non-speech). `plate()` treats it like the other ask
  reasons: `NeedsConfirmation(INPUT_NOT_USABLE, parsed)`, `Completed`, nothing saved. The UI's
  sentence for INPUT_NOT_USABLE is the retry line.
- **1 or more of N resolve but not all: today's behaviour**, `NeedsConfirmation(NO_MATCH, ...)`
  naming the item, nothing saved. A single fuzzy hit does not rescue a plate.
- **No ASR-side signal is added**, because none exists: a wrong-language sentence decodes at full
  piece density with no `<unk>`, and a language-identification check is forbidden by the
  contract. The zero-match result is the cheapest honest signal and it is downstream of the ASR.
- The remaining risk is a WRONG match, a fuzzy hit on the wrong food from a transliterated
  syllable, which no retry path catches. That is the matcher's WRONG FOOD metric and its
  tolerances, and it is why "dell" for dal is worth Priya's look before the demo.

## The requirement

Vedant, 20 Sep, overriding the per-language default in `AsrEngine`: a code-mixed utterance is NOT
routed to one language's model. Input in any mix of Telugu, Hindi and English goes to a model that
handles all of them. Only the OUTPUT language follows the user's selection.

The brief before building anything: does what `0005` chose already do this, and if not, what does.

## 1. What the IndicConformer export actually is

`parismitaglobalsolutions/indicconformer-sherpa-onnx`, from its own README ("How these were made"):

> Source: AI4Bharat's `indicconformer_stt_<lang>_hybrid_ctc_rnnt_large` checkpoints (Conformer-Large,
> hybrid CTC+RNNT, 120M params, with an aggregate tokenizer shared across all 22 languages). [...]
> Each language's vocabulary mask is baked into the graph, so any consumer can do plain greedy
> argmax decoding safely. [...] the language mask confirmed exactly `-inf` outside its slice.

Confirmed in the file: the te export's last op is `Add(LogSoftmax, mask)` with a `(5633,)` constant,
257 zeros and 5,376 `-inf`. The shared `tokens.txt` is 22 slices of 256 pieces plus `<blk>` at 5632;
a script census of each slice gives one script per slice (te = `[5120, 5376)`, hi = `[1536, 1792)`,
read from the two models' own masks, not from alphabetical order, which is wrong: `kok` sorts
before `gu` in this vocabulary).

So the deployed files are **22 per-language checkpoints**, each masked to its own 256-piece slice.
The README's own licence table also corrects `0005`: the 22 Indic models are **MIT (AI4Bharat)**,
the English one is **CC-BY-4.0 (NVIDIA)**, and the repository's `apache-2.0` tag is the
repackaging, not the weights. The `en` model is `sherpa-onnx-nemo-fast-conformer-ctc-en-24500`
re-quantised (`0021`).

**Can the mask be removed to get a multilingual model?** No. The te export with its mask zeroed
(all 5,633 pieces allowed) and with a te+hi dual mask produced output **byte-identical to the
masked export on all eleven clips, including pure Hindi speech** (`logs/asr-mixed-te-variants.log`).
The per-language head never picks a non-Telugu piece; the specialisation is in the weights, and
the mask is belt and braces.

## 2. AI4Bharat's genuinely multilingual checkpoint, and what it does with mixed speech

`ai4bharat/indic-conformer-600m-multilingual`: **MIT** (card), gated behind a click-through that
Vedant's account has already passed (the token in this environment downloads it), 435,717
downloads. One encoder, one CTC head over the same 5,633-piece aggregate vocabulary, and
`language_masks.json`. Its own inference code, `model_onnx.py` line 73:

    logprobs = torch.from_numpy(logprobs[:, :, self.language_masks[lang]]).log_softmax(dim=-1)

The head is **sliced to the chosen language before argmax**. The authors' model is multilingual
in the encoder and monolingual in the output, by design, exactly like the exports.

**Size.** CTC path (encoder + head + preprocessor, no RNNT): **2,496,903,241 bytes fp32.**
2,427 MB of that is MatMul and conv weight tensors; MatMul-only int8 as the export repo does it
would give roughly **680 MB** (an estimate from tensor sizes, not a file; nobody has published one).
Against `0013`'s 2,303 MB of headroom with the LLM resident, that is a third of it for ASR alone,
before sherpa-onnx's own state.

**Measured on the mixed clips** (fp32, ONNX Runtime 1.23.1, 4 threads, the authors' own
preprocessor; `logs/asr-multilingual-600m.log`). Decoded three ways: masked to one language as the
authors do; allowed te+hi together; allowed all 22.

| clip | te mask | hi mask | te+hi allowed | all 22 allowed |
| --- | --- | --- | --- | --- |
| Hindi: मैंने दो रोटी और दाल खाई | మేనే ద రోటీ ఔ దాలఖాయి | **exact** | मैंने दो रोटी औरఔ దाल खఖाయి | मैंने دوो রरोਟੀी اورऔ दाल खھाయि |
| Telugu frame, Hindi words: నేను ఉదయం दो रोटी और दाल తిన్నాను | నేను ఉదయం దో రోటీ ఆర్ దాల్ తిన్నాను | नेन उदय दो दो रोटी औरर दााल तिनाान | నేను उदయం दोో रोटी औरర్ दాల్ तిన్నాను | 13 scripts |
| Hindi frame, English words: मैंने one glass of milk पिया | మని వన్ గ్లాస్ ఆఫ్ మల్క్ పయా | मैने वन ग्लास ऑफ मिल्क प | मैని వन ग్లాस ऑफ్ మिल्क పయా | 15 scripts |
| Telugu: నేను రెండు రొట్టెలు మరియు పప్పు తిన్నాను | **exact** | नेन रेडो रोटेल मरी पप तिनाान | నేను రెండు రొట్టెలు मరియు पప్పు తిన్నాను | 7 scripts |

Three things the table shows:

- Masked to one language it is as good as the per-language exports on these clips, and **more
  robust to foreign-accented English inside an Indic frame**: `वन ग्लास ऑफ मिल्क` where the 120M
  hi export produced `नहीं नहीं ओन क्वास सब में ओंकाय दियािया`. One synthetic data point, but a real one.
- Allowed two languages, it **switches script inside words**: `औरఔ దाल`, `दोో रोटी औरర్ दాల्`. It does
  pick the Hindi slice for रोटी inside a Telugu frame, so the encoder hears the switch; but the
  neighbouring pieces flip too, and a word split across two scripts matches nothing in any alias
  table.
- Allowed all 22, it is script salad from 7 to 15 languages per utterance.

**Conclusion for questions 1 and 2:** no IndicConformer, exported or original, produces a mixed
Telugu/Hindi/English transcript. The aggregate head is 22 decoders side by side, and argmax hops
between them per piece. A language has to be chosen per utterance. Desktop fp32 cost per clip was
2.2-7.5 s against 0.1-1.1 s for the 120M int8 exports, on a laptop that was paging.

## 3. What else does it, from the cards and the same clips

Every licence below is read from the model card or LICENSE file, verbatim class, today.

| candidate | licence | on-disk | language handling | measured here |
| --- | --- | ---: | --- | --- |
| IndicConformer per-language exports (te, hi) | MIT (AI4Bharat) | 197 MB each, int8 | one per utterance, baked | te 8.3 % WER on synthetic Telugu; hi exact on 2 of 3 synthetic Hindi; mixed-in Indic words come out transliterated in the frame script with food names intact; foreign-accented English inside a frame is garbage, Indian-pronounced English is fine (`0021`, `logs/asr-mixed-per-language.log`) |
| IndicConformer 600M multilingual | MIT | 2,497 MB fp32; ~680 MB int8 (estimate) | one per utterance, masked at decode | above |
| NVIDIA `stt_en_fastconformer_hybrid_large_pc` (the `en` slot) | CC-BY-4.0 | 175 MB int8 | English only | mangles every Indic food name (`0021`) |
| `openai/whisper-small`, the repo's `whisper-multilingual-swift` export | Apache-2.0 | 375 MB int8 | language token per 30 s window, or auto-detect | **unusable for te/hi**: forced te gave empty output on 7 of 13 clips and Devanagari fragments on the rest; forced hi turned मैंने दो रोटी और दाल खाई into `मैंने ो ो`; forced en **translates** Hindi speech ("I ate two rotis and dal.") and **hallucinates** ("I ate the rice in two hotels" for the Telugu rotis-and-pappu clip): a WRONG FOOD by construction. 2-30 s per clip, autoregressive (`logs/asr-candidates-omnilingual-whisper.log`) |
| `openai/whisper-large-v3-turbo`, the repo's `whisper-multilingual-apex` export | MIT | 1,036 MB int8 | as above | **empty on 11 of 13 clips forced to Telugu**; hallucinates and loops when forced to English; 8-67 s per clip. Below |
| Oriserve `Whisper-Hindi2Hinglish-{Swift,Apex}` | Apache-2.0 | 150 MB / 1.0 GB int8 | Hindi + English, **romanised** output | not tested: no Telugu. Noted because romanised output would hit the 383 roman aliases directly, if a Telugu equivalent existed. None does |
| Meta Omnilingual ASR CTC-300M, sherpa-onnx export | **Apache-2.0** (Meta LICENSE file verbatim) | **365 MB int8** | **one model, no language parameter, one output space over 1,600 languages** | below |
| Meta MMS `mms-1b-all` | CC-BY-NC-4.0 | 1 B params | per-language adapter | excluded: non-commercial and per-language |
| sherpa-onnx's own `sherpa-onnx-whisper-*` exports | none stated | | | excluded, as `0005` did; the repo above re-exported from the licensed OpenAI checkpoints instead |

### Omnilingual ASR CTC-300M, the one candidate with a single output space

Same clips, sherpa-onnx `from_omnilingual_asr_ctc`, int8, 4 threads, 540-8,000 ms per clip on the
loaded laptop.

| clip | output |
| --- | --- |
| Telugu: నేను రెండు రొట్టెలు మరియు పప్పు తిన్నాను | నేను రెండు రొట్టలు మరియు పప్పు తిన్నాను (one vowel sign) |
| Telugu: ఉదయం మూడు ఇడ్లీ సాంబార్ తిన్నాను | ఉదయం మూడు ఇడిలీ సాంభారతిన్నాను |
| Hindi: मैंने दो रोटी और दाल खाई | मैंने दो रोटी और दाल खाए |
| Hindi: सुबह तीन इडली और सांबर खाया | **صبح تین इڈلی اور سامبر ہایا** (Urdu script) |
| English: I ate two rotis and dal for lunch | i ate two rotees and dell for lunch |
| Telugu frame, Hindi words | **നേന് ഉദയം ദോ റൂട്ടി ഒർദാൽ തിന്നാനു** (all Malayalam script, phonetically right) |
| Hindi frame, Telugu words | മേനേ സുബ രണ്ടു ഇഡ്ലി സാമ്ബാര ഖയ (Malayalam) |
| Telugu frame, English words | nേnു **two rotees** andell tിനnan |
| Hindi frame, English words | many **one glass of milk** tia |
| English frame, Telugu words | for lun്ചh i പപ്പു മരിയു അന്ന |
| Telugu voice saying English words the Telugu way | ടു റോടീസ് അണ്ഡ പപ്പു തിന്നാനു (Malayalam) |

It genuinely code-switches: English words come out in Latin inside an Indic sentence, which no
IndicConformer can do and which the 383 roman aliases would match directly. **But with no language
parameter it chooses the Indic script acoustically**, and on these clips Telugu speech landed in
Malayalam script five times out of six when anything else was in the utterance, and Hindi in Urdu
script twice out of three. The sounds are right; the script is wrong. The Brahmic scripts share
one Unicode layout, so a deterministic Malayalam-to-Telugu or Urdu-to-Devanagari normalisation to
the user's OUTPUT language is a table, not a model; that is an untested mechanism, stated here as a
possibility and not a plan. Its Telugu and Hindi accuracy on real speakers is unmeasured; Meta's
CER claims are for the 7B LLM variant, not this one.

**Both engines, same sixteen clips, same script** (`tools/asr_eval.py wer`, with and without
`--engine omnilingual`; `logs/asr-eval-indicconformer-synthetic.log`,
`logs/asr-eval-omnilingual-synthetic.log`). WER here is script-strict: a phonetically right
Malayalam rendering of Telugu counts as fully wrong, which is exactly what the alias table would
see today without a normaliser. All rows SYNTHETIC.

| frame language | clips | IndicConformer (frame-language model) WER / CER | Omnilingual (one model) WER / CER |
| --- | ---: | --- | --- |
| te | 6 | 29.7 % / 16.9 % | 56.8 % / 38.8 % |
| hi | 5 | 48.3 % / 43.1 % | 65.5 % / 57.8 % |
| en | 5 | 30.2 % / 24.0 % | 41.9 % / 33.1 % |

Raw, Omnilingual is 12-27 WER points behind on every frame language, almost all of it the script
choice rather than the sounds. Whether a normaliser closes that gap is the measurement that has not
been made, and the mixed clips inflate both columns: the Telugu-only rows in `0021` were 8.3 % WER.

### Whisper large-v3-turbo

Encoder 674,716,477 B, decoder 361,152,887 B, int8. Same clips, four language settings, 4 threads;
8-67 s per clip on the loaded laptop (`logs/asr-candidates-whisper-turbo.log`).

- Forced Telugu: **empty output on 11 of 13 clips.** The two non-empty ones were the clips whose
  content was English.
- Forced Hindi: fragments. मैंने दो रोटी और दाल खाई came back as `मैंने ो ो`; सुबह तीन इडली और
  सांबर खाया as `सह ीन ल`.
- Forced English: translation ("I had two roti and dal to eat." for the Hindi sentence),
  romanisation ("Subah teen idli or sambar khaaya."), a repetition loop ("I ate the morning. I ate
  the morning. I ate the morning..."), and invented content: **"I have two people in the world."**
  for నేను రెండు రొట్టెలు మరియు పప్పు తిన్నాను, and "3. The 3rd is the 4th." for the idli-sambar clip.
- Auto-detect: Telugu detected as Tamil three times, output in Tamil script (`உதையம் மூடு இடிலி ச`);
  Hindi once as Urdu.

So the larger Whisper is **worse on Telugu than the small one**, not better, and forced-language
decoding invents food-shaped sentences on both sizes. That failure mode is the one this product
cannot carry: a hallucinated transcript reads as a confident meal. Whisper is out at both sizes on
this evidence, and its size and autoregressive cost never had to be weighed.

## 4. The trade-off, honestly

The requirement asks for one model that handles any mix. Three facts constrain the answer:

1. **No model in this family emits two Indic scripts in one utterance sensibly.** Both
   IndicConformer forms need a language chosen per utterance. Choosing it by the user's OUTPUT
   language, as the contract already does, gives a transcript in that script with mixed-in words
   transliterated. On these clips every food name survived that transliteration (`రోటీ`, `దాల్`,
   `ఇడ్లీ`, `సాంబర్`), and none of those renderings is in the alias table today (`0 of 25`
   common English food words, `logs/asr-codemix-renderings.log`). So with IndicConformer, "handles
   the mix" is a matcher-coverage job, not an ASR job.
2. **Only Omnilingual gives one output space**, and it pays for that with an unchosen script. It
   is 365 MB against 197 MB for one IndicConformer or 570 MB for all three, Apache-2.0, and the
   one candidate whose behaviour matches the requirement's shape: input in any mix, output
   normalised afterwards to the selected language. It has not been heard by anyone on real Telugu.
3. **Bigger buys robustness, not mixing.** The 600M IndicConformer was better on accented English
   inside an Indic frame than the 120M exports, at 3.4x the size and no change to the
   single-script output. Whisper was unusable at both sizes, and turbo was the worse of the two on
   Telugu.

What is NOT known and would decide it: word error rate and extraction accuracy of (a) the te
IndicConformer export and (b) Omnilingual CTC-300M on Vedant's recorded speakers, five or six of
them, through `tools/asr_eval.py`; and the resident cost and decode time of each on the phone
through `AsrDeviceTest`. The desktop harness runs both engines today (`--engine omnilingual`);
the device test and the loader know only IndicConformer, and stay that way until the decision is
made, on instruction.

## What this record decided, and what it left

Decided above: Option A, with the gap and the reopen condition stated. `DefaultAsrEngine` selects
a model by `SpeechLanguage` as the contract always said; the language comes from the profile
setting through the orchestrator's `SpeechLanguageRef`. Left open, and owned elsewhere: the
persisted profile language field (there is none in `ProfileEntity` as of this date) and the UI
that sets it at onboarding; the matcher coverage of transliterated food words; and the recorded
speakers, without whom every number above stays synthetic.
