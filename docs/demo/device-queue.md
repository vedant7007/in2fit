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

## The abandon rule: when the iQOO stops being the demo phone

Block 0 assumes the iQOO eventually cooperates. It may not, and on the morning someone has ten
seconds to decide. Vedant's realme is set up, measured, seeded and warm, and every number the
team holds came off it. So the rule is written here, before the day, not invented at the table.
`T` is the team's slot; Vedant writes the clock time beside it the evening before.

| Trigger | What happens |
|---------|--------------|
| **Install refused three times** (the script's third try, after the "Install via USB" card) | Stop work on the iQOO. Demo on the realme. |
| **The app killed twice while idle** after the skin settings (battery optimisation off, autostart on, background allowed) | Stop. Demo on the realme. A phone that kills the warm model between the table and the first word is not a demo phone. |
| **`lowmemorykiller` names the app once more** after the voice is dropped from the warm-up (PSS over the ceiling) | Stop. Demo on the realme. |
| **No platform voice data by T-60** (no usable network at the venue) | NOT an abandon trigger by itself: the bundled Piper voice is the insurance (0019 addendum 10) and the pre-flight does not check the voice. Say it once to the presenter so the English answer's voice is not a surprise. |
| **Block 0 not READY by T-45** | Stop trying regardless. The realme needs: reboot, ten quiet minutes, one Beat 1, the pre-flight (about fifteen minutes), then one rehearsal beat (five). Forty-five is that with margin for a cable. |

At any trigger, the same three moves: (1) stop touching the iQOO and say "realme" out loud so
nobody keeps trying; (2) the realme's own checklist from "Reboot, wait, check, then demo" in the
run of show; (3) the iQOO stays ON THE TABLE, screen up, so the offer to run on the loaner is
visible rather than hidden, and P says one sentence to the judges, the one Nila writes, and
nothing else about phones. The sentence must be true on the day it is said: if the install
never succeeded, it does not claim the build is on the loaner.

What is lost by demoing on the realme: nothing technical. Every number, every screenshot and
every rehearsal is the realme's; the iQOO would only ever have been faster and unmeasured.
Rao agrees with the default. WHAT IS NOT KNOWN: whether the hackathon's rules require the
loaner to be used. Nothing in the repo says so (spec 7.4 says "have a backup phone with the
same build installed", and the loaner is named only as "most likely an iQOO"); if the rules do
require it, this table is a fallback for a demo that would otherwise not happen, not a choice,
and Vedant needs that answer from the organisers before the day. Both branches are in the run
of show (Nila), and P rehearses the sentence for each.

Vedant: run `tools\cold-phone.ps1` the moment the iQOO is in your hand on the 26th, not on demo
morning, and do the voice-data card on a network before the radios go off.

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

## The realme queue, walked as three sessions

Walked on 21 Sep as if run at a table with fifteen minutes and a flaky cable (Vedant). One
install per session; the fifteen-minute set is Session A and it is in the order that survives
the link dying after any row. Rows others wrote are kept verbatim; merged rows say which one
runs them. The status column is still each owner's.

### Session A: the fifteen-minute set (one install, this order, stop where the link stops)

| # | What | How | Proves | Status |
|---|------|-----|--------|--------|
| 3a | The pre-flight's numbers | `tools/preflight.ps1` before every item above, its log kept | what READY looks like on this phone | LANDED AND UNPROVEN (21 Sep; dry-run on the emulator only) |
| 5 | Task 2, Beat 1 end to end through the microphone — FIRST, before anything else: the demo's first beat, and it proves the transliteration, push-to-talk and extraction in one go | `measure_case.ps1` with `vedant_hi_01.wav`, three runs: plate and spoken times, the plate's items | the demo's first beat, with the microphone in the path | UNPROVEN |
| 4 | Task 2, transliteration on the phone — ONLY IF 5 FAILS, to bisect the recogniser from the extraction | `OrchestratorDeviceTest.d_hindiLogRows` (now with `IcuRomaniser`): rows 1, 2, 5 and the Hinglish row | the frozen Beat 1 sentence extracts on the device through the platform's ICU (the JVM proved icu4j) | LANDED AND UNPROVEN, 21 Sep |
| 6 | The transliteration's nine words on the device — one minute; runs after 5 because 5 is the thing | `DevanagariDeviceTest` (androidTest, ml/asr): `IcuRomaniser` over the nine words and the four demo rows, asserting the JVM's strings (android.icu vs icu4j 75.1) | the phone's ICU writes what the JVM's did | LANDED AND UNPROVEN, 21 Sep |
| 1 | Task 1, the 68 s ANSWER, through the UI — two warm runs, not four, in the short window | hi_07 through the laptop's speakers, `measure_case.ps1` shape (thumb = DOWN/UP in one device shell), four warm runs after one cold; `logcat` now carries one `katori-llama: generate: wall … prompt … gen …` line per model call; sample `top -p <pid>` and `dumpsys thermalservice` every 2 s alongside | whether the 68 s is real and repeatable, and whether it is the model call (the log line) or around it | UNPROVEN; the log line landed 21 Sep |
| 3 | Task 1, 8 / 6 / 4 threads, one session — ONLY IF 1 REPRODUCES | if item 1 reproduces: the same utterance at `katori_llama_threads` 8, 6, 4 (the global setting, no rebuild, no reload), two runs each, `top` and thermal sampled | the 8-thread barrier hypothesis, and the thread count that leaves the phone a core | LANDED AND UNPROVEN (the knob, 21 Sep) |
| 2 | Task 1, the same utterance through the rig, same session — ONLY IF 1 REPRODUCES | `OrchestratorDeviceTest.c_oneHindiAnswer` (typed, no UI) twice | fast in the rig + slow in the UI = the UI or its threads (Ira's too) | UNPROVEN |
| 5a | Beat 3 by PDF, DEMO-CRITICAL (Arjun's PDF input; Vedant 13:05: Beat 3 may run this way on the day; both photograph thresholds failed and an angled shot reads nothing; a page rendered from a PDF has no angle) | FIRST the script: 36's line `ARJUN-CHECK 5a pdf PASS render=Ok pages=1 1700x2405 ocr=Ok fields=[Haemoglobin=9.8g/dL [13.0-17.0], …] date=…` (a PDF the script draws, rendered by the platform, read by ML Kit ON THIS PHONE, extracted; on the emulator 21 Sep 14:33 it read exactly). THEN BY EYE, this one must be seen: Scan → "Open a PDF report" → Downloads → `in2fit-lab-report.pdf` (put there by `cold-phone.ps1 -Report`; on the realme, `adb push` any lab-report PDF to /sdcard/Download first) → the read values against their printed ranges → Save → Reports shows them; a password-protected PDF says so and saves nothing | Beat 3 by PDF, end to end through the picker, the reader and the store | LANDED AND UNPROVEN (Arjun, 12:25; the script line ran green on the emulator 14:33, NOT the phone) |
| 36 | ARJUN'S ITEMS AS ONE SCRIPTED RUN — ONE RUN COVERS 10, 11, 13-15, 24-28 AND 5a's script half: those rows stay below for the record and are not run by hand | `powershell -File tools\arjun-checks.ps1` (default adb server only, refuses a second one; stops the app; COPIES THE DATABASE FIRST to `logs/katori-user-<stamp>.db` so a refused migration is reproducible and nothing logged is lost; runs `ArjunDeviceChecksTest` against the phone's OWN database through the app's migrations; prints nine `ARJUN-CHECK <item> PASS|FAIL …` lines with the numbers it saw, then `VERDICT: n PASS, m FAIL`; UTF-8 log `logs/arjun-checks-<stamp>.txt`; `-Only a`…`-Only i` reruns one). Every row it writes it deletes; the diary is read, never left changed. RAN GREEN ON THE EMULATOR 14:33 (9 PASS, 66 s wall, log `logs/arjun-checks-20260921-1432.txt`): the harness works; the phone is the proof. THE FIRST LINE IS VEDANT'S HIGH ITEM: a FAIL on `10/24 migration`, or the run stopping before any line with a Room `IllegalStateException`, means the app would not open on this database: STOP, keep the copied .db, post the stack | rows 10, 11, 13-15, 24-28 and 5a without a human having to notice anything | LANDED AND UNPROVEN (Arjun, 12:25; harness shaken down on the emulator 14:33) |
| 37 | Arjun's warm-up gate — MERGED WITH 43: one cold launch proves both gates, BY EYE (30 s) | cold launch; within the first seconds hold the microphone: NOTHING starts, the button is greyed and reads "Getting ready…" (Ira's v2 bar, 13:20); wait for `logcat -s katori-warmup` to print `total N ms`; hold again: the turn starts. PASS = both halves seen; with the scripted feed on, the gate never applies | `TalkViewModel.speak` refuses before `WarmUp.ready` (0032, the screen's half) | LANDED AND UNPROVEN (Arjun, 12:42) |

### Session B: the record (the pass, the recogniser, the one-hour hazard)

| # | What | How | Proves | Status |
|---|------|-----|--------|--------|
| 7 | The measurement pass, with the microphone | `tools/measurement-pass.ps1` after items 1-5, for the record; the pass itself types | the deck's numbers on the landed code | UNPROVEN |
| 9 | Jacob's row on the recorded set | stage the `vedant` clips, run `AsrDeviceTest.b` twice | cold/warm on Vedant's own voice | UNPROVEN |
| 8 | Duplicate transcription on a long hold — the hour, not more | `cap_case.ps1` twice more (37 s hold, hi_06 at 2 s), with the per-second level log | reproduces or goes on the hazard list | one hour, not more (Vedant) |
| 16 | The meal's source — by hand only if 36's line for it is missing | after a spoken LOG and a typed LOG, `meals.source` reads SPOKEN then TYPED and `language_tag` the picker's tag (`run-as` + sqlite, or Arjun's Diary row) | `CurrentTurn` feeding the store's lambdas (Rao, 21 Sep) | LANDED AND UNPROVEN |

### Session C: Ira's screens on the realme's own data (one install, grouped by screen)

| # | What | How | Proves | Status |
|---|------|-----|--------|--------|
| 38 | Ira's v2 shell, (d) | install the demo build over the existing data: the app opens in the DARK v2 shell (five-place bar, lime microphone), the status icons light, Today shows the old Talk in cream inside it | the shell, the window theme, the bundled Instrument faces. ROWS 38-47 DECIDE THE 26TH: v2 is demoed only if every one of them passes on the realme; otherwise the old shell (row 61) is the demo | LANDED AND UNPROVEN (Ira, 11:15) |
| 39 | Ira's voice sheet, listening, (e) | hold the microphone on any tab: the sheet opens ON THE PRESS reading "HOLD TO SPEAK" with the five-word hint under it; from the first frame with signal the label turns to "LISTENING · हिन्दी" and the thirteen bars move with the voice | the held FAB feeds `speak()`; the bars are real level samples | LANDED AND UNPROVEN (Ira, 11:15) |
| 40 | Ira's voice sheet, the plate, (e) | say Beat 1, release: the quoted transcript, the stage rows ticking with the counter, then the plate: "taken as 180 g" under the dal ONLY, the rotis as said, per-item kcal and protein on the right, "Saved" | the sheet follows `TalkViewModel.State`; the rows on the real path (item 12 seen through v2) | LANDED AND UNPROVEN (Ira, 11:15) |
| 41 | Ira's voice sheet, a question, (f) | hold and ask Beat 4's question: the sheet closes by itself and Coach opens with the answer bubble | a turn that is not a plate lands in Coach | LANDED AND UNPROVEN (Ira, 11:15) |
| 42 | Ira's Coach, typed, (g) | Coach: type a question, Send: the stage rows appear where the design has typing dots, then the answer | the Coach input feeds `type()` | LANDED AND UNPROVEN (Ira, 11:15) |
| 44 | Ira's hostile checks, (i)-(j) | animator scale 0: the sheet appears without the rise, no pulse; 2× font: tabs whole, stage rows wrap; then `dumpsys gfxinfo` for the worst frame with the sheet up | reduce motion end to end; the frame cost of the blur behind the sheet | LANDED AND UNPROVEN (Ira, 11:15) |
| 45 | Ira's Discard — same delete as 14 and 26; one run | after a spoken LOG: Discard on the sheet; Diary and the next ANSWER no longer carry the meal | the sheet's Discard on `deleteMealAndAdvice` | LANDED AND UNPROVEN (Ira, 11:55) |
| 57 | Ira's Welcome and first run | clear the app's data, launch: the lime splash until the warm-up line, then Welcome; "Get started"; the six steps with real answers (Hindi, 19 / 62 / 172 / 3, Maintain, Prediabetes, Skip, Allow → the system prompt, Start logging); kill and reopen: Today, no Welcome | first run writes the profile row, the condition and the language; the flag holds | LANDED AND UNPROVEN (Ira, 13:20) |
| 58 | Ira's sign-in link | clear data, launch, "I already have an account": the one sentence, Continue, Today; You shows an empty profile card | amendment 1 | LANDED AND UNPROVEN (Ira, 13:20) |
| 46 | Ira's Diary | Diary on the realme: the day's meals as cards, the energy under the title equal to Today's ring, ticks on the days with meals; tap the chapati meal: the four totals equal the card, "taken as" only under the dal | Diary and the meal on real rows | LANDED AND UNPROVEN (Ira, 13:20) |
| 48 | Ira's Today | Today on the realme's own database: the ring's number equals what ANSWER speaks for today, the macros match Diary's day, the last meal is the last meal, no ring fill and no "/ target" anywhere (Priya's rule is not in) | Today on real rows; the empty states where the data is not there | LANDED AND UNPROVEN (Ira, 12:25) |
| 49 | Ira's water — same water as 25; one run | "+" on the water card twice: 0.5 appears; kill and reopen: still 0.5 | `logWater` through the v2 card | LANDED AND UNPROVEN (Ira, 12:25) |
| 50 | Ira's nudge card | after Beat 1 with a rule fired: the nudge card carries the engine's sentence, "Ask about this" opens Coach; Coach's line under the title names the report by date and each value outside its printed range | the stored advice and the attributed context line | LANDED AND UNPROVEN (Ira, 12:25) |
| 59 | Ira's Trends | Diary → Trends: seven bars, no bar on an empty day, Avg over the logged days only, Protein hit and Weight empty | Trends on real rows | LANDED AND UNPROVEN (Ira, 13:20) |
| 60 | Ira's Search and the food page | Today → "Add manually": "You log these often" from the realme's diary; type "dal": the matches with the bundled unit and grams and the energy for that portion; open one: 0.5 / 1 / 1.5 change the figures through the lookup (65.2 → 97.9 kcal on the emulator's row), "Add to diary" dim | Search on the real catalogue; a portion is a new lookup, never arithmetic on the screen | LANDED AND UNPROVEN (Ira, 13:25) |
| 47 | Ira's scan in the v2 dress | Reports → "Add another report" (and first run's report step): the camera in the card, Capture on the realme's printed report: "Reading the text" as a row, then the values with their printed ranges and tick circles, "Save N values"; Reports shows the new card; "Open a PDF report" opens the picker | the v2 scan on `ScanViewModel`, camera and PDF paths | LANDED AND UNPROVEN (Ira, 13:35) |
| 51 | Ira's Reports | Reports (You → Reports until the v2 You screen lands) on the realme's own scanned report: every saved value on a card under its printed date, each flag matching the printed range the row shows, no word but "above / below / within the printed range" | Reports on real extractor rows; the attributed wording | LANDED AND UNPROVEN (Ira, 11:55) |
| 52 | Ira's marker screen | tap a value: the marker screen, that report's bar in the warm colour when out of range, the safety line | `labHistory` on the device | LANDED AND UNPROVEN (Ira, 11:55) |
| 53 | Ira's You | You on the realme: the rig's row as "19 · 62 kg", Language reads the picker's choice; Edit details: change the weight, Save: You shows it | You and the profile's write path on the device | LANDED AND UNPROVEN (Ira, 12:50) |
| 54 | Ira's cream switch | You → Appearance → Cream: every screen cream, status icons dark; kill and reopen: still cream; back to Dark | `ThemePreference` through `LocalScheme`, the bars' icon colour at run time | LANDED AND UNPROVEN (Ira, 12:50) |
| 55 | Ira's language page | You → Language → Telugu: the sheet's label reads "LISTENING · తెలుగు" on the next hold | the language's one home (the profile row) read by both screens | LANDED AND UNPROVEN (Ira, 12:50) |
| 56 | Ira's share sheet — same export as 28; one run | You → Privacy → "Share with your doctor": the chooser opens with the CSV text, no permission prompt | the export through the v2 page | LANDED AND UNPROVEN (Ira, 12:50) |
| 61 | Ira's old-shell switch, (h) | Coach title long-press: the old three-tab shell in cream; old About, "New screens": back to v2 | nothing the phone could do yesterday is lost | LANDED AND UNPROVEN (Ira, 11:15) |

### Covered by a row above (kept for the record, not run by hand)

| # | What | How | Proves | Status |
|---|------|-----|--------|--------|
| 10 | Arjun's storage, (a) — covered by 36 | its line in 36's output: `ARJUN-CHECK 10/24 migration PASS user_version=4 tables=… meals=N labs=N profile=19/62.0/172.0 lang=…`; the app's own open after the run is the same migration | Room v2 → v3 → v4 on real rows, on the actual device | LANDED AND UNPROVEN (Arjun, 08:01; scripted 12:25) |
| 11 | Arjun's storage, (b) — covered by 36 | its line in 36's output: `ARJUN-CHECK 11 language home PASS row before=… set te, fresh open reads=te; restored …` (the picker's own write path, then a fresh open of the database, which is what a kill and reopen amount to; the row is put back). By eye only if there is time: Talk's picker reads हिन्दी on first open | one home for the setting (0037) | LANDED AND UNPROVEN (Arjun, 08:01; scripted 14:30) |
| 12 | Arjun's plate, (c) — covered by Ira's 19, not by 36 (the rows are seen through the sheet) | Beat 1 typed in English: the plate rows show "dal · 1 katori · taken as 180 g" and the rotis without "taken as" | `MealResolved.items` filled by the orchestrator (Rao, 11:20) reaching Ira's rows | LANDED AND UNPROVEN |
| 13 | Arjun's screens, (d) — covered by 36 | its line in 36's output: `ARJUN-CHECK 13 diary = answer PASS meals today=N (…) screen energy=X spoken energy=X …` (Diary's day figures compared, as a set, with the context source's TODAY figures, the lines ANSWER reads out) | one derivation for the card and the voice | LANDED AND UNPROVEN (Arjun, 10:55; scripted 12:25) |
| 14 | Arjun's screens, (e) — covered by 36 | its line in 36's output: `ARJUN-CHECK 14/26 delete PASS meal N: 2 items -> band after item delete=GOOD remaining=[roti] gone=true meals before/after=X/X` (a two-item meal the script writes, then removes) | `deleteItemAndRederive`, `deleteMealAndAdvice` | LANDED AND UNPROVEN (Arjun, 10:55; scripted 12:25) |
| 15 | Arjun's screens, (f) — covered by 36 | its line in 36's output: `ARJUN-CHECK 15 search PASS matches=N first=Dal tadka … unit=katori grams=180.0 …` (the match's portion through the plate's own resolver, as `SearchViewModel.portion` does it) | the search query and the household word | LANDED AND UNPROVEN (Arjun, 10:55; scripted 14:14) |
| 24 | Arjun's storage, v4 — covered by 36 | its line in 36's output: `ARJUN-CHECK 10/24 migration PASS …` (the same line as 10: `user_version=4`, the `water`, `weights`, `reminders` tables present) | the second migration on populated data, on the phone | LANDED AND UNPROVEN (Arjun, 11:30; scripted 12:25) |
| 25 | Arjun's water and weight — covered by 36 | its line in 36's output: `ARJUN-CHECK 25 water/weight PASS water before=… after=+500 restored=…; weight row=61.5 profile=61.5 (profile restored …)` (writes, reads back, restores) | the v4 write paths | LANDED AND UNPROVEN (Arjun, 11:30; scripted 12:25) |
| 26 | Arjun's item delete — covered by 36 | its line in 36's output: `ARJUN-CHECK 14/26 delete PASS …` (the same line as 14: the band after the item delete is the roti's, the meal goes with its last item) | `deleteItemAndRederive` | LANDED AND UNPROVEN (Arjun, 11:30; scripted 12:25) |
| 27 | Arjun's marker explanations — covered by 36 | its line in 36's output: `ARJUN-CHECK 27 markers PASS rows=7 hb=Haemoglobin long-hba1c=HbA1c platelets=null` (the shipped file loaded from the APK, the printed-name matching). By eye only if there is time: Reports → a haemoglobin row shows "What this measures" with its MedlinePlus source | the shipped, cited content reaching a row (0038) | LANDED AND UNPROVEN (Arjun, 11:30; scripted 12:25) |
| 28 | Arjun's export — covered by 36 | its line in 36's output: `ARJUN-CHECK 28 export PASS meal lines=N (items=N nutrient rows=N) lab lines=N (rows=N)`. By eye only if there is time: You → "share with your doctor" opens the system chooser; `dumpsys package io.github.vedant7007.katori | grep permission` still lists only CAMERA and RECORD_AUDIO | text through ACTION_SEND, no file, no provider, no permission | LANDED AND UNPROVEN (Arjun, 11:50; scripted 12:25) |
| 43 | Ira's warm-up gate on the v2 bar — covered by 37 | a hold within the first second after launch: nothing starts and the button is dim; a hold after the warm-up line starts the sheet | `state.ready` on the v2 microphone | LANDED AND UNPROVEN (Ira, 13:20) |

Struck lines stay in the table with the date and the log path, so the next person sees what was
run and when.
