# Katori status

Updated 19 Sep 2026, 08:25. Autonomous run while Vedant is at college.

---

## BLOCKED ON VEDANT

Answer from your phone, one line each. Nothing is waiting on these; I have moved on.

**Q1. Will Katori ever be commercial or ad-supported, even later?**
`YES` or `NO`.
Why it matters: the only ready-made Hindi text-to-speech models are either
non-commercial (CC-BY-NC) or under a custom IITM licence nobody has read. If NO, Hindi
TTS is solved today. If YES, the demo speaks Telugu and English only until we find or
train a Hindi voice.

**Q2. Is the live demo spoken in Telugu or Hindi?**
`TELUGU` or `HINDI` or `BOTH`.
Why it matters: Telugu TTS is licence-clean right now (CC-BY-4.0). Hindi is not. If the
demo is Telugu, Q1 stops being urgent.

**Q3. The Telugu and Hindi speech models the spec named do not exist. Proceed with the
IndicConformer replacement?**
`YES` or `PICK ANOTHER`.
Details below. The spec already named IndicConformer as the fallback, so this is the
planned path, but the only ready-made export is one person's repo.

---

## VERIFIED ON HARDWARE

**Nothing. All day. Not one line.** No phone has been connected, nothing has been
installed, no model has been loaded. Every claim below is compile-time or desktop-only.

Still unverified and unverifiable until you are back with the Realme 11: model load time,
inference speed, ASR accuracy, ASR/LLM/TTS co-residency, camera, OCR, any latency number.

---

## VERIFIED ON EMULATOR

Nothing yet. The emulator variant is not built.

---

## COMPILE AND UNIT TEST ONLY

- `assembleDemoDebug` green, APK 34.06 MB, arm64-v8a native libs only.
- `testDemoDebugUnitTest` green, 2 tests.
- Merged `demo` manifest: CAMERA and RECORD_AUDIO only, no INTERNET.
- 13 contract files compile.

---

## TODAY'S TWO REAL FINDINGS

**1. The Telugu and Hindi Whisper packs in spec 10.2 do not exist.**
`vasista22/whisper-telugu-*` and `whisper-hindi-*` are real and Apache-2.0, but they ship
PyTorch weights only. There is no sherpa-onnx INT8 conversion of them, by anyone. A sweep
of all 767 `csukuangfj` repos found no Indic ASR at all.

This does not change the four demo beats, so I did not stop. Spec 10.2 already named
IndicConformer as the alternative, and the ruling on the ASR interface already said the
model family is decided by measurement with the interface kept pluggable. That is exactly
what this is.

Replacement in use: `parismitaglobalsolutions/indicconformer-sherpa-onnx`, Apache-2.0,
NeMo CTC conformers, one model per language, about 197 MB each for te and hi, 175 MB for
en. Caveat worth knowing: 4 likes, no recorded downloads, one person's export. It needs a
smoke test on hardware before anyone trusts it. That is Q3.

**2. Two model licence problems, handled the same way as IFCT.**
- `csukuangfj/sherpa-onnx-whisper-tiny` has NO licence stated anywhere: no licence field,
  no tag, no LICENSE file, no README. Absence of a licence is absence of a grant, so it is
  NOT being used. English now comes from the same Apache-2.0 IndicConformer repo instead.
- MMS TTS for Telugu and Hindi is CC-BY-NC-4.0, non-commercial. Not downloaded. That is Q1.

---

## DECIDED WITHOUT ASKING

- Models whose licence does not permit redistribution are not downloaded at all, so they
  cannot drift into the build. `docs/decisions/0005-model-sourcing-and-licences.md`
- English ASR moved to the Apache-2.0 IndicConformer export, away from the unlicensed
  Whisper conversion. Same file, same interface. `0005`
- Telugu TTS is the Piper `te_IN-padmavathi` voice, CC-BY-4.0 via IndicVoices-R. `0005`
- SDK root, JDK 21, and how builds are run. `docs/decisions/0003-sdk-root-and-toolchain.md`
- AGP 9.4.1 and Gradle 9.6.0, and the transitive INTERNET permission removal.
  `docs/decisions/0004-agp-9-and-the-telemetry-permission.md`
- USDA import rules, all four enforced with post-import assertions.
  `docs/decisions/0002-usda-import-rules.md`

---

## IN PROGRESS

Working the vertical slice order. Priority A first, because it carries three of the four
beats and needs no phone: USDA import, then FoodLookup, then the rules engine.
