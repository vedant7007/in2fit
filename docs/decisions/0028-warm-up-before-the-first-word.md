# 0028. Warm-up: the first of everything is slow, and the first of everything is what the judges see

Date: 20 September 2026. Status: SPEC for Rao (engine side) and Arjun (when it runs relative to
the first screen), with the desktop evidence behind it. The device numbers it needs are named
and not yet measured.

## The finding

Two measurements, two engines, same shape:

- **TTS** (Rao, on the phone): the platform engine's first call runs at real-time factor
  1.87-3.60; every call after it at 0.04-0.70. A first sentence that takes two to three times as
  long to synthesise as it takes to say.
- **ASR** (Jacob, desktop, `logs/asr-cold-vs-warm.log`): opening a 197 MB int8 IndicConformer
  checkpoint in sherpa-onnx took 0.7-3.6 s, and in the two blocks the host was quiet enough to
  read, the first decode after the load ran 1.4x a subsequent one (hi: 157 ms then ~114; te: 198
  then ~150). The load is the cold cost; the first inference pays a smaller second one. The laptop
  was shared with other sessions' builds and had 1.5 GB free, so the ratio is the finding and the
  milliseconds are not. On the phone the `0013` probe measured the LLM load alone at 2.6-7 s
  depending on page cache.

So a cold app answers its first spoken sentence after a model load, a first-pass decode, an LLM
load, and a first TTS call, in series. Every one of those is a one-off, and in a demo the one-off
is the whole show: the judge sees the first sentence, not the tenth.

## The spec

**At app start, before the presenter touches anything: every model the voice round trip needs is
loaded and has run one throwaway inference.** Concretely, for the demo profile (`hi`):

| model | warm-up call | what it does | who |
| --- | --- | --- | --- |
| ASR, `asr.indicconformer-hi` | `AsrEngine.prepare(HINDI)` | admits the model through the arbiter and decodes 1 s of digital silence inside the lease, then leaves it resident and unpinned. Implemented in `DefaultAsrEngine` (this commit); the silence decodes to a phantom piece and is discarded | Jacob, done |
| LLM, `llm.qwen2.5-1.5b-q4km` | one short generation with the real system prompt | the KV cache and the mmap'd weights get their first touch off the critical path; `0012` recorded the FUSE page-fault cost of a cold first pass | Rao |
| TTS, Piper `hi` voice | `TtsEngine.prepare(HINDI)`, one short utterance synthesised and discarded (not played) | the platform engine's 1.87-3.60 RTF first call, and Piper's espeak initialisation, happen before the first real sentence | Meera / Rao |

Order: LLM first (largest, slowest), then ASR, then TTS, all through the arbiter so the
co-residency row (`0013`) is the one that gets measured, not three separate ones. Warm-up runs on
a background dispatcher; the first screen renders immediately with a "getting ready" state that
the orchestrator's `Progress` events already model, and the microphone button is enabled when
`prepare` has returned for all three. **A tap before that is refused with the same state, never
queued**: a queued tap that fires the moment warm-up ends is the cold path with extra steps.

What warm-up must NOT do: hold a lease across the wait (the `ModelArbiter` rule: a lease never
spans waiting for a person), so each `prepare` takes and releases its own lease and the models
stay resident and evictable. If memory pressure evicts one between warm-up and the first tap, the
first tap pays the load again; that is the honest cost of the contract, and the co-residency
headroom (`0013`: 2.3 GB with the LLM resident, before the sherpa and Piper state) says it should
not happen on the test device.

## The run-of-show line, and where this spec actually bites

`docs/demo/run-of-show.md` already says it, checklist row 9: **"App WARM, not cold. Open IN2FIT and
run one full Beat 1 sentence to the answer, then leave it open on the Talk tab,"** with the phone's
own numbers beside it (cold model load 5.4-8.5 s, warm under a second). So the run of show is
"already open and warmed", and today the warm-up is a person running a sentence by hand. Fine for
the walk to the table.

Where it is not fine is failure-playbook row 202: a model fails to load on stage, the presenter
says "Ten seconds", and the second person **closes the app and reopens it in front of the
judges**. That reopen is a cold launch, and "ten seconds" is a guess until the launch-to-microphone
time is measured. This spec turns that reopen into: app opens, "getting ready" state shows, all
three models load and warm in the background, microphone enables. The number to write into row
202 in place of "ten seconds" is the one `AsrDeviceTest.b` and the LLM/TTS equivalents produce.
Manual warm-up before the table stays in the checklist; automatic warm-up is what makes the
reopen survivable.

## What has to be measured on the phone, and by whom

`AsrDeviceTest.b` now prints, per language: COLD first transcribe (load + first decode),
`prepare()` time, and a WARM transcribe of the same clip. Those three numbers are the ASR row of
this table, and they do not exist yet. The LLM's cold load is already in `0012`; its first-token
latency after warm-up is not. The TTS pair Rao already has. Together they give the one figure the
run of show needs: seconds from app launch to microphone enabled, on the test device, with the
three models resident.

## Why not lazy-load on the first tap with a spinner

Because `0014` measured the whole extraction turn at 10.7 s and the UI is already carrying that
with visible progress. Adding a model load and a first-pass penalty in front of it on the FIRST
turn, the one the judges score, is spending the demo's patience on work that could have been
done while the presenter was still talking about airplane mode.
