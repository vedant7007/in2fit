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

### WHERE I AM GOING NEXT

`ModelArbiter`, per the handover's recommended order. It is a contract with nothing behind it and
both the memory story and the voice round trip rest on it. The ceiling it admits against will be
derived from the measured co-residency figures above rather than the provisional half-of-RAM
formula the probe currently prints.

I own `domain/`, the hardware probe and the phone. If your work needs a model loaded, it needs the
arbiter, so talk to me before writing your own loading path — `AsrEngine`'s contract already
forbids one.
