# 0022. One model for code-mixed speech: what exists, measured, and the decision it leaves Vedant

Date: 20 September 2026. Status: EVIDENCE FOR A PRODUCT DECISION, not a decision. Nothing here
ran on a phone; every clip is synthetic and every number is a desktop number on a shared laptop.

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

## What is NOT decided here

The routing. `DefaultAsrEngine` still selects a model by `SpeechLanguage` as the contract says. It
is not changed until Vedant chooses between: IndicConformer per language plus matcher coverage of
transliterated English and Hindi food words; Omnilingual plus script normalisation to the selected
language; or something this record did not find. The evidence above is what that choice is made
with.
