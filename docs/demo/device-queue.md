# The device queue

One list. Everything that is waiting on the realme, in the order it runs when the phone comes
back. Rao owns it (Vedant, 21 Sep: "one list, not three"). Anything landed while the phone is
away is LANDED AND UNPROVEN until its line here is struck through with a date and a log path.

Conditions before item 1, every time: default adb server only (`adb devices` lists the phone,
no second server on another port: 5bd5458), airplane mode on and Wi-Fi off, the phone rebooted
if it has been asleep on the desk (three 0 %-CPU model stalls so far, each after an idle spell,
each cleared by a reboot), nothing else running, then ten minutes of idle after the boot (the
post-boot indexing, `android.process.acore`, ran at 140-230 % CPU for four minutes on 21 Sep).
Build once: `assembleDemoDebug` + `assembleDemoDebugAndroidTest`, install both, note the
`lastUpdateTime`.

| # | What | How | Proves | Status |
|---|------|-----|--------|--------|
| 1 | Task 1, the 68 s ANSWER, through the UI | hi_07 through the laptop's speakers, `measure_case.ps1` shape (thumb = DOWN/UP in one device shell), four warm runs after one cold; `logcat` now carries one `katori-llama: generate: wall … prompt … gen …` line per model call; sample `top -p <pid>` and `dumpsys thermalservice` every 2 s alongside | whether the 68 s is real and repeatable, and whether it is the model call (the log line) or around it | UNPROVEN; the log line landed 21 Sep |
| 2 | Task 1, the same utterance through the rig, same session | `OrchestratorDeviceTest.c_oneHindiAnswer` (typed, no UI) twice | fast in the rig + slow in the UI = the UI or its threads (Ira's too) | UNPROVEN |
| 3 | Task 1, six threads | one UI run with `THREADS` at 6 if item 1 reproduces and `top` shows another process on two cores | the 8-thread barrier hypothesis | UNPROVEN, only if 1 reproduces |
| 4 | Task 2, transliteration on the phone | `OrchestratorDeviceTest.d_hindiLogRows` (now with `IcuRomaniser`): rows 1, 2, 5 and the Hinglish row | the frozen Beat 1 sentence extracts on the device through the platform's ICU (the JVM proved icu4j) | LANDED AND UNPROVEN, 21 Sep |
| 5 | Task 2, Beat 1 end to end through the microphone | `measure_case.ps1` with `vedant_hi_01.wav`, three runs: plate and spoken times, the plate's items | the demo's first beat, with the microphone in the path | UNPROVEN |
| 6 | The transliteration's nine words on the device | `DevanagariDeviceTest` (androidTest, ml/asr): `IcuRomaniser` over the nine words and the four demo rows, asserting the JVM's strings (android.icu vs icu4j 75.1) | the phone's ICU writes what the JVM's did | LANDED AND UNPROVEN, 21 Sep |
| 7 | The measurement pass, with the microphone | `tools/measurement-pass.ps1` after items 1-5, for the record; the pass itself types | the deck's numbers on the landed code | UNPROVEN |
| 8 | Duplicate transcription on a long hold | `cap_case.ps1` twice more (37 s hold, hi_06 at 2 s), with the per-second level log | reproduces or goes on the hazard list | one hour, not more (Vedant) |
| 9 | Jacob's row on the recorded set | stage the `vedant` clips, run `AsrDeviceTest.b` twice | cold/warm on Vedant's own voice | UNPROVEN |

Struck lines stay in the table with the date and the log path, so the next person sees what was
run and when.
