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
| 10 | Arjun's storage, (a) | install `da67d04`+ over the existing database: the app opens (Room v2 -> v3 migration ran), the diary still shows the 06:43 chapatiis, the rig's profile row still reads 19 / 62 / 172 | the migration on populated data, on the phone | LANDED AND UNPROVEN (Arjun, 08:01) |
| 11 | Arjun's storage, (b) | Talk: the picker reads Hindi on first open after install; choose Telugu, kill the process, reopen: Telugu | the language now lives in the profile row | LANDED AND UNPROVEN (Arjun, 08:01) |
| 12 | Arjun's plate, (c) | Beat 1 typed in English: the plate rows show "dal · 1 katori · taken as 180 g" and the rotis without "taken as" | `MealResolved.items` filled by the orchestrator (Rao, 21 Sep) reaching Ira's rows | LANDED AND UNPROVEN |
| 13 | Arjun's screens, (d) | Home/Diary on the migrated database: the chapatiis as a meal named "Chapati / roti", 232.3 kcal, the day's total equal to what ANSWER speaks for today | the derivable rows on real data | LANDED AND UNPROVEN (Arjun, 10:55) |
| 14 | Arjun's screens, (e) | delete that meal from Diary: gone from Diary, Today and the next ANSWER | `deleteMealAndAdvice` | LANDED AND UNPROVEN (Arjun, 10:55) |
| 15 | Arjun's screens, (f) | Search "dal": rows with "1 katori" and grams, protein per portion | the search query | LANDED AND UNPROVEN (Arjun, 10:55) |
| 16 | The meal's source | after a spoken LOG and a typed LOG, `meals.source` reads SPOKEN then TYPED and `language_tag` the picker's tag (`run-as` + sqlite, or Arjun's Diary row) | `CurrentTurn` feeding the store's lambdas (Rao, 21 Sep) | LANDED AND UNPROVEN |
| 17 | Ira's v2 shell, (d) | install the demo build over the existing data: the app opens in the DARK v2 shell (five-place bar, lime microphone), the status icons light, Today shows the old Talk in cream inside it | the shell, the window theme, the bundled Instrument faces | LANDED AND UNPROVEN (Ira, 11:15) |
| 18 | Ira's voice sheet, listening, (e) | hold the microphone on any tab: the sheet opens ON THE PRESS with the pulse, the thirteen bars move with the voice, "LISTENING · हिन्दी" | the held FAB feeds `speak()`; the bars are real level samples | LANDED AND UNPROVEN (Ira, 11:15) |
| 19 | Ira's voice sheet, the plate, (e) | say Beat 1, release: the quoted transcript, the stage rows ticking with the counter, then the plate: "taken as 180 g" under the dal ONLY, the rotis as said, per-item kcal and protein on the right, "Added to today" | the sheet follows `TalkViewModel.State`; the rows on the real path (item 12 seen through v2) | LANDED AND UNPROVEN (Ira, 11:15) |
| 20 | Ira's voice sheet, a question, (f) | hold and ask Beat 4's question: the sheet closes by itself and Coach opens with the answer bubble | a turn that is not a plate lands in Coach | LANDED AND UNPROVEN (Ira, 11:15) |
| 21 | Ira's Coach, typed, (g) | Coach: type a question, Send: the stage rows appear where the design has typing dots, then the answer | the Coach input feeds `type()` | LANDED AND UNPROVEN (Ira, 11:15) |
| 22 | Ira's old-shell switch, (h) | Coach title long-press: the old three-tab shell in cream; old About, "New screens": back to v2 | nothing the phone could do yesterday is lost | LANDED AND UNPROVEN (Ira, 11:15) |
| 23 | Ira's hostile checks, (i)-(j) | animator scale 0: the sheet appears without the rise, no pulse; 2× font: tabs whole, stage rows wrap; then `dumpsys gfxinfo` for the worst frame with the sheet up | reduce motion end to end; the frame cost of the blur behind the sheet | LANDED AND UNPROVEN (Ira, 11:15) |
| 24 | Arjun's storage, v4 | install over the v3 database: the app opens (3 -> 4 ran: `water`, `weights`, `reminders`), Diary and Today still show the meals | the second migration on populated data, on the phone | LANDED AND UNPROVEN (Arjun, 11:30) |
| 25 | Arjun's water and weight | from whichever v2 screen Ira binds first, or `run-as` + sqlite: `TodayViewModel.logWater(250)` twice -> `waterMl` 500 on Today and Diary, null before the first; `ProfileViewModel.recordWeight(61.5)` -> a `weights` row and `profile.weight_kg` 61.5 | the v4 write paths | LANDED AND UNPROVEN (Arjun, 11:30) |
| 26 | Arjun's item delete | on a two-item meal, `DiaryViewModel.deleteItem(<dal's id>)`: the meal keeps the roti, its band is the roti's, the day's total drops by the dal's figures, the stored advice for it is gone; delete the last item: the meal is gone | `deleteItemAndRederive` | LANDED AND UNPROVEN (Arjun, 11:30) |
| 27 | Arjun's marker explanations | Reports after the scripted or a real report: under Haemoglobin, HbA1c, Vitamin D, Ferritin, TSH, glucose or B12 the "What this measures" text with its MedlinePlus source; under a marker not in `knowledge/markers.csv`, nothing | the shipped, cited content reaching a row; the alias matching on printed names | LANDED AND UNPROVEN (Arjun, 11:30) |
| 28 | Arjun's export | You: "share with your doctor" (`ProfileViewModel.export` + `shareText`): the system chooser opens with two CSV texts, one row per item per nutrient with its state, an UNKNOWN with no amount; `adb shell dumpsys package io.github.vedant7007.katori | grep permission` still lists only CAMERA and RECORD_AUDIO | text through ACTION_SEND, no file, no provider, no permission | LANDED AND UNPROVEN (Arjun, 11:50) |

Struck lines stay in the table with the date and the log path, so the next person sees what was
run and when.
