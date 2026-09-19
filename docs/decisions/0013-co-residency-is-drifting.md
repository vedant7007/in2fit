# 0013. Co-residency fits, and the margin is moving the wrong way

Date: 19 September 2026. Status: OPEN OBSERVATION, deliberately not explained.

## The measurements

Same device, same three model files, same probe, one build change between them.

| Build | LLM only | + ASR | + TTS | peak | headroom |
| --- | ---: | ---: | ---: | ---: | ---: |
| baseline ARMv8.0 | 1,209.0 MB | 1,469.2 MB | 1,456.7 MB | 1,452.2 MB | 2,357.5 MB |
| baseline ARMv8.0, later run | 1,287.0 MB | 1,451.5 MB | 1,482.5 MB | 1,482.5 MB | 2,327.2 MB |
| `+dotprod+fp16` | 1,986.4 MB | 2,209.1 MB | 2,229.9 MB | **2,229.9 MB** | **1,579.8 MB** |
| `+dotprod+fp16`, via `DefaultModelArbiter`, LLM only, 20 Sep | 1,887.6 MB | — | — | 1,887.6 MB | 2,303.0 MB against the CALIBRATED ceiling |

Ceiling in the first three rows is the provisional one the probe printed: half of the device's
7,619.4 MB, so 3,809.7 MB. The fourth row is against the ceiling the arbiter calibrates for
itself (`DefaultModelArbiter`, `0c20de3`): **4,190.7 MB on this device**. That figure and the
LLM-only peak are the first two rows in the on-device measurement log,
`katori-memory-measurements.tsv`, which is append-only and is where every subsequent row goes.

**What that calibration did and did not validate, precisely.** The ceiling is
`min(total − 3·threshold − baseline, 0.55·total)`. On this phone the derived term is about
6,206 MB and the cap is 4,190.7 MB, so **the cap was the binding term and the cap is what the
device exercised.** The derived term — the low-memory-threshold arithmetic that is the actual
measured part of the formula — produced a number, but that number was never the ceiling and
nothing depended on it. It is unexercised on this hardware. A device with a larger threshold
relative to its RAM would be the first to exercise it, and until one does, "the formula was
validated" is not a sentence this record supports.

The fourth row is not directly comparable with the third: it is one model through the arbiter's
loader, not three through raw ONNX sessions. The ASR and TTS families have no runtime bound yet,
so the arbiter cannot measure the three together until sherpa-onnx lands. When it can, that row
goes here and the comparison with 2,229.9 MB is the one to watch.

**It still fits.** `FITS true` on every run, and 1,579.8 MB of headroom is not tight.

## What is not known

Peak PSS rose by about 750 MB across a change that only altered which instructions the CPU
executes. The model file is the same 1,065.6 MB, the context is the same 2,048 tokens, and the
three files loaded are byte-identical to the previous run.

**No explanation is recorded here, because none has been established.** Plausible-sounding
candidates were available and are deliberately omitted: this project has already spent a run
chasing a storage theory that the warm-versus-cold measurement disproved, and a guess written into
a decision record is read as a finding by the next person.

What is known is the trajectory, and the trajectory is the reason this file exists.

## Why it matters later rather than now

The co-residency figure is a FLOOR and the probe says so. It loads the raw ONNX sessions and
nothing else. When the ASR slice lands, sherpa-onnx adds a feature extractor and decoder state on
top of that session, and Piper adds a phonemiser. Those are not in any number above.

So the margin has to absorb two things that have not been measured yet, and it just lost roughly a
third of itself to a change nobody expected to cost memory at all.

## What to do about it

Nothing yet. Specifically:

- Do not raise the provisional ceiling to make the number look better. It is half of device RAM
  because the system, the launcher and whatever the user has open need the other half, and an app
  that takes more is the one the low-memory killer reaches for first.
- Do not design around 1,579.8 MB of headroom as though it were stable. It has moved once.
- DO re-measure co-residency after every change that touches the native build, the context size,
  or the set of resident models, and add the row to the table above. The point of this file is the
  trend, and a table with one more row is worth more than any theory about why.

If the margin ever stops fitting, that is a tier-degradation finding and the answer is the one
`0001` already wrote down: drop spoken confirmation to on-screen confirmation on that tier. It is
not a reason to split the lease, and not a reason to raise the ceiling.
