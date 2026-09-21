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
`lastUpdateTime`. Then `powershell -File tools\preflight.ps1` and do not start item 1 on a WAIT;
every run of it writes `logs/preflight-<stamp>.txt`, and those numbers are wanted too.

Item 3 no longer needs a build: `adb shell settings put global katori_llama_threads 6` (or 4)
re-threads the live model before the next call, and the `katori-llama: generate: threads N …`
line says which count each number was made at; `settings delete global katori_llama_threads`
returns to 8.

## Block 0: the cold phone (runs first on the iQOO, before anything else is attempted on it)

Every number the team holds is the realme's. The demo handset is an iQOO collected on the day:
different chip, thermal curve, skin and housekeeping, and it starts with no profile, no diary
and no report. This block turns it into the demo's starting state and measures that state; the
rest of the queue runs against the realme until the iQOO exists.

`powershell -File tools\cold-phone.ps1` (add `-Report` for the Beat 3 fallback report; see the
card below for the three steps that need a thumb). It runs, with a check after each: the link
(USB debugging authorised, default server, arm64, API >= 29 for the transliteration), the
platform voice data (a card: needs a network, so before the radios go off), install of the demo
and test APKs (three tries), microphone and camera permissions (`pm grant`, and a card when the
skin refuses it as ColorOS does), the models (`stage-models.ps1`, then the five files checked
present and non-empty), THE SEED through the app's own stores (`DemoSeedTest`: the profile at
19 / 62 / 172 with speech Hindi, six meals on six days through the real resolver and
`RoomMealStore.save`, the report only with `-Report`), radios off, the app launched and its own
0032 warm-up reported in logcat (`katori-warmup: llm … asr … tts … total …`), and the pre-flight,
ending READY or WAIT. The last line is the total time.

| 0.# | What | Status |
|-----|------|--------|
| 0.1 | `cold-phone.ps1` on the iQOO, end to end, once, and the TOTAL line written here | UNPROVEN. Dry run on the emulator 21 Sep 12:17: steps 0-6 ran (install 23 s, seed 13 s, radios 3 s) and step 7 could not, the emulator kills the model at 2.3 GB; the models step is unmeasured on any phone (`stage-models.ps1` has never run on one; 1.6 GB at the realme's 6.3 MB/s is about 4.5 min). Estimate for a cooperative phone: 6-8 min of script plus 3-6 min of thumbs (voice data on a network, permission taps), so 10-15 min: before the judges if the phone is in hand 30 min early, in front of them if not |
| 0.2 | The first sixty seconds as the judges see them: open the app on the seeded phone, Home/Diary/Talk each once; nothing empty where the seed says there is data | UNPROVEN |
| 0.3 | The iQOO's own pre-flight numbers, three runs ten minutes apart: what READY looks like on THIS phone | UNPROVEN |
| 0.4 | The thread count on the iQOO: hi_07 through the microphone at `katori_llama_threads` 8, 6, 4, two each, in one session (about 6 min); the count with the best warm figure is the day's | UNPROVEN |
| 0.5 | The thermal shape: the four beats in order, twice, thermal status read before each; if status reaches 2 before Beat 3, the LOG beats move apart or the phone rests between rehearsals | UNPROVEN |
| 0.6 | The memory ceiling: `preflight` prints the app's PSS; `HardwareProbeTest` prints the arbiter's ceiling; if the ceiling is under ~3.5 GB the co-residency of LLM + hi + voice is the thing to run first, and the sign it broke is a `lowmemorykiller` line naming the app (the emulator's failure), not a slow turn | UNPROVEN |
| 0.7 | Jacob's row on the iQOO's microphone: `AsrDeviceTest.b` twice (cold, warm), and Beat 1 through the microphone three times | UNPROVEN |

The card for the thumb, in order, printed by the script at the moment each is needed:

1. USB debugging: on the phone, "Allow USB debugging?" -> tick "Always allow" -> Allow. On a
   vivo/iQOO skin also Developer options -> "Install via USB" ON and "USB debugging (Security
   settings)" ON (it may ask for a vivo sign-in; do it on the network, before airplane mode).
2. Voice data: Settings -> System -> Languages & input -> Text-to-speech output -> Speech
   Services by Google -> gear -> Install voice data -> Hindi (India) and English (India). Both
   must say Installed. Then Enter on the laptop.
3. Permissions, only if the script says the skin refused `pm grant`: in IN2FIT press and hold
   the microphone once -> Allow -> let go; Scan report tab -> Allow. The script continues by
   itself the moment both are granted.

## What might be different on the iQOO, and what we do about each

| Difference | What we measure first | The action |
|------------|-----------------------|------------|
| Thread count (a different core layout; the realme's 8 threads may be wrong here) | 0.4: hi_07 at 8 / 6 / 4 threads, two runs each, in one session; the `generate: threads N` line names each number | The best warm figure's count goes into the run of show as `adb shell settings put global katori_llama_threads N` on the day's checklist; if 8 wins, nothing changes |
| Thermals (throttles sooner or later; the realme reached status 3 on the third LOG) | 0.5: the beats in order twice with `dumpsys thermalservice` read before each | If status reaches 2 before Beat 3: rest the phone five minutes between rehearsals, and do not rehearse Beat 1 more than once in the last fifteen minutes; if it reaches 3 on the first pass, Beat 1 goes last in rehearsal and the phone sits face-up off the table |
| Memory ceiling (the realme's is 4.39 GB; the warm-up now loads the LLM at launch) | 0.6: the arbiter's ceiling and the PSS after warm-up; the sign of trouble is a `lowmemorykiller` line naming the app | Under ~3.5 GB: warm only the LLM and the hi recogniser (drop the voice from the warm-up), and if it still dies, the launch warm-up is turned off for the day and row 9 goes back to a person running a sentence |
| The skin's opinion of background apps (ColorOS's athena killed a pass at 2 GB; Funtouch/OriginOS has its own) | 0.1 and 0.3: whether the app survives ten idle minutes with the model resident (pre-flight's PSS line, three runs) | On a fresh iQOO before anything else: battery optimisation OFF for IN2FIT, "allow background activity", autostart ON if the skin has it, and every pre-installed social app force-stopped (Instagram restarted itself after every reboot on the realme); if the app is still killed while idle, the presenter reopens it two minutes before the table and the warm-up covers the reopen |
| The microphone and the speaker (every ASR number is one microphone's; the platform voice is a different install) | 0.7: `AsrDeviceTest.b` cold and warm, and Beat 1 through the microphone three times; `TtsVoiceProbeTest` for the voices | Trusted across devices: the recogniser's accuracy on the clips (the model is the model) and the endpointer-free push-to-talk logic. NOT trusted: the cold/warm milliseconds, the first-word clipping (`AudioRecord` start latency is per device), the gain (a quieter mic changes the voiced-span onset: watch the `SpeechStarted` timing and the `not speech` refusals), and the platform voice's availability. If Beat 1 through the microphone fails twice where the typed row passes, the microphone is the difference and the hold gets a full second before the first word |
| The Android version (the transliteration needs API 29; the realme is 15) | Step 0 of the script prints the API and says so | Below 29 the model reads Devanagari again and Beat 1 in Hindi is back to unproven: the Hinglish typed row is the fallback, and Nila is told the day before |
| Storage and the models (1.6 GB over a cable that may drop) | 0.1's models step, timed | `stage-models.ps1` is chunked and resumable by design and unproven on any phone; if it dies twice, the LLM goes over first alone and the rest after, and the time is written down |

Nothing above makes the realme's numbers the iQOO's. Anything the deck claims says which
handset produced it; Nila has the list of what does not (COORDINATION, 21 Sep).

## The realme queue

| # | What | How | Proves | Status |
|---|------|-----|--------|--------|
| 1 | Task 1, the 68 s ANSWER, through the UI | hi_07 through the laptop's speakers, `measure_case.ps1` shape (thumb = DOWN/UP in one device shell), four warm runs after one cold; `logcat` now carries one `katori-llama: generate: wall … prompt … gen …` line per model call; sample `top -p <pid>` and `dumpsys thermalservice` every 2 s alongside | whether the 68 s is real and repeatable, and whether it is the model call (the log line) or around it | UNPROVEN; the log line landed 21 Sep |
| 2 | Task 1, the same utterance through the rig, same session | `OrchestratorDeviceTest.c_oneHindiAnswer` (typed, no UI) twice | fast in the rig + slow in the UI = the UI or its threads (Ira's too) | UNPROVEN |
| 3 | Task 1, 8 / 6 / 4 threads, one session | if item 1 reproduces: the same utterance at `katori_llama_threads` 8, 6, 4 (the global setting, no rebuild, no reload), two runs each, `top` and thermal sampled | the 8-thread barrier hypothesis, and the thread count that leaves the phone a core | LANDED AND UNPROVEN (the knob, 21 Sep) |
| 3a | The pre-flight's numbers | `tools/preflight.ps1` before every item above, its log kept | what READY looks like on this phone | LANDED AND UNPROVEN (21 Sep; dry-run on the emulator only) |
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
| 29 | Ira's Reports | Reports (You → Reports until the v2 You screen lands) on the realme's own scanned report: every saved value on a card under its printed date, each flag matching the printed range the row shows, no word but "above / below / within the printed range" | Reports on real extractor rows; the attributed wording | LANDED AND UNPROVEN (Ira, 11:55) |
| 30 | Ira's marker screen | tap a value: the marker screen, that report's bar in the warm colour when out of range, the safety line | `labHistory` on the device | LANDED AND UNPROVEN (Ira, 11:55) |
| 31 | Ira's Discard | after a spoken LOG: Discard on the sheet; Diary and the next ANSWER no longer carry the meal | the sheet's Discard on `deleteMealAndAdvice` | LANDED AND UNPROVEN (Ira, 11:55) |
| 32 | Ira's Today | Today on the realme's own database: the ring's number equals what ANSWER speaks for today, the macros match Diary's day, the last meal is the last meal, no ring fill and no "/ target" anywhere (Priya's rule is not in) | Today on real rows; the empty states where the data is not there | LANDED AND UNPROVEN (Ira, 12:25) |
| 33 | Ira's water | "+" on the water card twice: 0.5 appears; kill and reopen: still 0.5 | `logWater` through the v2 card | LANDED AND UNPROVEN (Ira, 12:25) |
| 34 | Ira's nudge card | after Beat 1 with a rule fired: the nudge card carries the engine's sentence, "Ask about this" opens Coach; Coach's line under the title names the report by date and each value outside its printed range | the stored advice and the attributed context line | LANDED AND UNPROVEN (Ira, 12:25) |
| 35 | Arjun's PDF input | Scan (old shell) or wherever Ira places `PdfPickButton`: "Open a PDF report", pick any lab-report PDF on the phone: the same results list as a photograph, pages merged, then Save; a password-protected PDF says so and saves nothing | `PdfPages` (platform `PdfRenderer`) into the same recogniser and extractor; no permission, the whitelist unchanged | LANDED AND UNPROVEN (Arjun, 12:25) |
| 36 | ARJUN'S ITEMS AS ONE SCRIPTED RUN | `powershell -File toolsrjun-checks.ps1` (default adb server, stops the app, runs `ArjunDeviceChecksTest` against the phone's OWN database through the app's migrations, prints every `ARJUN-CHECK <item> PASS/FAIL …` line, log in `logs/arjun-checks-*.txt`). Covers 10/24 (real DB at version 4 with its rows; the FIRST v3 shape from `da67d04` is migrated too), 11 (the language read from the row), 13 (Diary's day figures equal the context source's TODAY figures, i.e. what ANSWER speaks), 14/26 (a two-item meal it writes: item delete re-derives the band, meal delete leaves nothing), 15 (search: match, katori grams, protein), 25 (water +500 then restored; a weight row then the profile, restored), 27 (markers from the APK), 28 (export line counts), 35 (a PDF it draws, rendered, read by ML Kit ON THE DEVICE, Haemoglobin 9.8 [13-17] extracted). Runs first, before the by-eye items; a FAIL line names the item | every item of mine that a human would otherwise have to notice, in one command | LANDED AND UNPROVEN (Arjun, 12:25) |
| 37 | Arjun's warm-up gate | cold launch, hold the microphone within the first seconds: nothing starts and the label reads "Getting ready…" (once Ira binds `state.ready`); after the `katori-warmup: … total N ms` line, a hold starts a turn; with the scripted feed on, the gate never applies | `TalkViewModel.speak` refuses before `WarmUp.ready` (0032, the screen's half) | LANDED AND UNPROVEN (Arjun, 12:42) |
| 38 | Ira's You | You on the realme: the rig's row as "19 · 62 kg", Language reads the picker's choice; Edit details: change the weight, Save: You shows it | You and the profile's write path on the device | LANDED AND UNPROVEN (Ira, 12:50) |
| 39 | Ira's cream switch | You → Appearance → Cream: every screen cream, status icons dark; kill and reopen: still cream; back to Dark | `ThemePreference` through `LocalScheme`, the bars' icon colour at run time | LANDED AND UNPROVEN (Ira, 12:50) |
| 40 | Ira's language page | You → Language → Telugu: the sheet's label reads "LISTENING · తెలుగు" on the next hold | the language's one home (the profile row) read by both screens | LANDED AND UNPROVEN (Ira, 12:50) |
| 41 | Ira's share sheet | You → Privacy → "Share with your doctor": the chooser opens with the CSV text, no permission prompt | the export through the v2 page | LANDED AND UNPROVEN (Ira, 12:50) |
| 42 | Ira's Welcome and first run | clear the app's data, launch: the lime splash until the warm-up line, then Welcome; "Get started"; the six steps with real answers (Hindi, 19 / 62 / 172 / 3, Maintain, Prediabetes, Skip, Allow → the system prompt, Start logging); kill and reopen: Today, no Welcome | first run writes the profile row, the condition and the language; the flag holds | LANDED AND UNPROVEN (Ira, 13:20) |
| 43 | Ira's sign-in link | clear data, launch, "I already have an account": the one sentence, Continue, Today; You shows an empty profile card | amendment 1 | LANDED AND UNPROVEN (Ira, 13:20) |
| 44 | Ira's warm-up gate on the v2 bar | a hold within the first second after launch: nothing starts and the button is dim; a hold after the warm-up line starts the sheet | `state.ready` on the v2 microphone | LANDED AND UNPROVEN (Ira, 13:20) |
| 45 | Ira's Diary | Diary on the realme: the day's meals as cards, the energy under the title equal to Today's ring, ticks on the days with meals; tap the chapati meal: the four totals equal the card, "taken as" only under the dal | Diary and the meal on real rows | LANDED AND UNPROVEN (Ira, 13:20) |
| 46 | Ira's Trends | Diary → Trends: seven bars, no bar on an empty day, Avg over the logged days only, Protein hit and Weight empty | Trends on real rows | LANDED AND UNPROVEN (Ira, 13:20) |
| 47 | Ira's Search and the food page | Today → "Add manually": "You log these often" from the realme's diary; type "dal": the matches with the bundled unit and grams and the energy for that portion; open one: 0.5 / 1 / 1.5 change the figures through the lookup (65.2 → 97.9 kcal on the emulator's row), "Add to diary" dim | Search on the real catalogue; a portion is a new lookup, never arithmetic on the screen | LANDED AND UNPROVEN (Ira, 13:25) |

Struck lines stay in the table with the date and the log path, so the next person sees what was
run and when.
