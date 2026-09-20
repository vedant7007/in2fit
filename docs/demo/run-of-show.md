# IN2FIT run of show

Written 20 September 2026 (Nila). For Vedant, who presents; for Rao, who fills the timings from
the clean airplane-mode run; for Jacob, who measures word error rate on exactly these
sentences; for whoever is holding the second phone. Six days out. Rehearse from this file, not
from memory of it.

**Language.** Ruled today: Vedant presents, Vedant does not speak Telugu, so the demo runs in
Hindi (Hinglish, the way it is really said) or Indian English, with an English interface, and
the Telugu toggle as the localisation demonstration. The sentences below are the Hindi column of
`data-authoring/demo-utterance-set.csv`; the English column is the alternative, and **the column
that ships is the one Vedant's own recordings score better on** (`tools/asr_eval.py`, Jacob's
05:40 note). On synthetic voices Hindi scored 7/10 exact at 6.2% WER and English 2/10 at 19.1%
with every Indic food word broken, so this file is written in Hindi until his recordings say
otherwise. Speech language and interface locale are separate settings.

**What the screen shows.** The screen states below are `0026`'s. Checked against the shell on
20 September 17:10: every phrase quoted here is a key in the string table (none missing); the
level meter, the stage list and the intent heading are in `ui/` today; **the elapsed-seconds
counter and the stop control are not yet**, and are Arjun's tonight with his `talk_` keys.
Until they land, the presenter's "the counter is real time" line in the playbook is not true
on the device; say "the stage list" instead. Re-read this paragraph after the end-to-end run.

**Timings** marked *(measured)* are from the phone, with the log named. Everything else is a
budget, marked *(budget)*, and Rao replaces it with a row from the clean run. Do not read a
budget as a measurement.

---

## Before anyone walks to the table: the device state checklist

Rao's report of 20 September showed what a hot phone with Instagram in the foreground does: every
number doubled. This list exists so that condition cannot happen on the day. One person runs it,
aloud, ticking, at the table, before the first sentence. Ten minutes.

| # | check | why |
| --- | --- | --- |
| 0 | **The right APK.** The stage build is `assembleDemoDebug`; since `1eee48b` the demo and full APKs differ in CODE, not only manifest. On the phone: long-press the IN2FIT title on About and read the pre-flight's last card. It must say **"Not in this build. The demo build carries no scripted feed."** If it shows a switch, the wrong APK is installed: stop and reinstall. (Arjun, 20:09, escalated by Vedant to sit beside airplane mode.) | The cheapest proof there is that the right APK is on the phone, read off the device, not off a build log. |
| 1 | **Airplane mode on, and the Bluetooth icon ABSENT from the status bar.** Wi-Fi and Bluetooth off too (airplane mode leaves them as they were). | Beat 0 is the proof; it must already be true before the phone is held up. A Bluetooth icon beside the airplane one undoes the sentence. |
| 2 | **Every other app closed.** Recents swiped clear. Instagram, browser, camera, everything. | The report that doubled every number had Instagram in the foreground. |
| 3 | **Phone cool.** Off charge for 15 minutes, out of a pocket, not in the sun, back of the case not warm to the hand. | A warm chip throttles; the 2.4x build gain is lost first. |
| 4 | **Battery above 50%, not charging during the demo.** | Charging warms the phone; low battery throttles. |
| 5 | **Screen stays awake.** Developer options: stay awake while charging is NOT enough, because it is not charging; set screen timeout to 10 minutes or the maximum. | This phone freezes app processes when the screen goes off (`OplusHansManager`, `0012`); a frozen probe read 1.08 tok/s. |
| 6 | **Do Not Disturb OFF.** (Corrected 20 Sep, Meera: some DND modes mute the media stream, and the voice is the media stream.) Airplane mode already blocks calls and messages, and row 2 leaves nothing running to notify. | A notification over the transcript is the kind of thing judges remember; a silent voice is worse. |
| 7 | **Brightness full; media volume 15 of 15, READ off the probe, not trusted from the volume key.** `TtsVoiceProbeTest` prints "stream 3 at N of MAX"; N must be the max. From a laptop: `adb shell cmd media_session volume --stream 3 --set 15`. | The lead-in phrase and the spoken answer must be heard from the back of the room. |
| 7a | **The wired speaker plugged in: USB-C or 3.5 mm. NEVER Bluetooth.** Bluetooth is a radio and shows an icon beside the airplane one. Run `TtsVoiceProbeTest`: its "routed to" line must name the wired device and the tone must come from it. Unplug, run again: "BUILTIN_SPEAKER". (Meera, 17:24.) | The hall eats sound; the screen carries the answer regardless (`0026`), but the voice is the demo's second channel. |
| 7b | **One spoken turn heard from the back of the room before the judges arrive.** | The only test of the speaker that counts. |
| 7c | **A wired headset or lapel microphone in the bag**, rehearsed once through a full turn (Jacob, 19:05). Wired: a cable costs nothing on the airplane-mode claim; anything Bluetooth is a radio. | In a loud hall the microphone must be at the mouth whatever the hand is doing. |
| 7d | **Push-to-talk, ruled by Vedant (Jacob, 20:05) and shipped in `ml/asr` (`7a6e49d`); Arjun's Talk screen carries the hold.** The presenter's rule, in Jacob's words, the same line as "phone at the mouth": **FINISH THE WORD, THEN LET GO.** Measured (`logs/asr-tail-after-last-word.log` in Jacob's worktree, ten demo sentences, clean and +15 dB babble): a thumb that lifts on the last syllable or up to a second after it costs nothing, 8/8; 100 ms inside the last word 8/7; 200 ms inside 6/5; 300 ms inside 0/0. The code records a 300 ms tail after release, so an early thumb on the last word is covered; a thumb that lifts mid-word is not. Under 300 ms held is a tap, not a sentence. | Jacob's tail run, 20:50; the rehearsal proves P's thumb. |
| 8 | **Storage: at least 2 GB free.** | The phone was 98–100% full on 19 September; the app copies assets on first run and writes its log. |
| 9 | **App WARM, not cold.** Open IN2FIT and run one full Beat 1 sentence to the answer, then leave it open on the Talk tab. | Cold model load reads 5.4–8.5 s on this phone; warm is under a second (`logs/hw-report-*.txt`). Spec 7.4: warm every model before the demo begins. |
| 10 | **Speech language = Hindi; interface = English.** Two settings, two places. SPEECH: the profile's speech language, which the Talk screen must send to the recogniser; on 20 September `TalkViewModel.kt:75` still sends `te` as a constant, and that is Arjun's one-line change TONIGHT, because on the day it would break the demo silently (a Hindi sentence through the Telugu checkpoint comes back transliterated). INTERFACE: the phone's per-app language, Settings > System > Languages & input > App languages > IN2FIT (Android 13+; the app declares `localeConfig`, so IN2FIT is listed). On this realme the menu may sit under "Additional settings"; Rao confirms the path once, and it goes here. English for the demo, Telugu only in Beat 5. | See "Language" above. |
| 11 | **Backup phone** with the same build, same checklist, in the second person's hand. | Spec 7.4. |
| 12 | **The recorded video** of the same sequence, on a laptop, ready to play. | Spec 7.4. If the live device fails twice, the video is the demo. |

## Before the day: the pre-demo checklist

| # | item | owner | status 20 Sep |
| --- | --- | --- | --- |
| 1 | Models staged on the phone: the LLM, the Hindi ASR model and its `tokens.txt`, the TTS voice, checksums verified. | Rao | staged 19 Sep (`COORDINATION.md`); re-verify after every reinstall, uninstalling wipes them (`0012`) |
| 1a | **The platform voice data, DAYS BEFORE, ON A NETWORK** (Meera, 16:51): Settings → System → Languages & input → Text-to-speech output (some skins: Accessibility → Text-to-speech output), or `adb shell am start -a com.android.settings.TTS_SETTINGS`; preferred engine Speech Services by Google → gear → Install voice data → Hindi (India) and English (India), download both. Then airplane mode ON and `TtsVoiceProbeTest`: the report must show a `hi-IN` and an `en-IN` voice with `network=false` and a WAV with an RTF. Only then does the platform voice exist on that phone; otherwise the bundled Piper voice speaks. On the realme now, on the iQOO the hour it arrives. | whoever holds the phone; Meera reads the probe | not done on any phone |
| 1b | **Which phone.** The demo runs on the loaner iQOO, handed over on site on the day the event starts and staged in hour one of thirty (`docs/demo/countdown.md`, the hour-one list: USB toggle, install, `tools/stage-models.ps1`, pre-flight card, voice data on the venue network, then airplane mode, the probes and a full rehearsal). Every rehearsal this week is on the realme, which is the rehearsal and fallback-video device, not the demo device. Row 1a's voice-data step happens on the realme this week and on the iQOO in hour one. | Vedant, whoever holds the cable | ruled 20:25 and 20:45 |
| 2 | The demo build installed: `demo` flavour, permission check green in the build log. | Rao | every assemble asserts it |
| 3 | Profile: name, Hindi speech, English interface, a declared context if beat 5 is attempted. | Arjun / Rao | no in-app picker yet; the interface language is the system's per-app setting (path in row 10 of the device checklist). One line gives the app a button that opens that page directly: `startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:" + packageName)))`, API 33+, and the phone is 35. That is the cheapest toggle for Beat 5. |
| 4 | **A meal already logged today**, the Beat 1 meal, so Beat 4's "Advise again on my last meal" has a meal. | presenter, at the table (row 9 above does it) | |
| 5 | **A week of history**, so a "last Tuesday" question has an answer. | Rao (seed) | spec 7.4; Beat 2 below uses "my lunch" so this is a nice-to-have |
| 6 | **The printed lab report**, on paper, with FERRITIN (and haemoglobin) BELOW the range printed on the same sheet, in someone's bag. Plus a second copy. The scripted report used for the screenshots reads ferritin 8.2 ng/mL against a printed 15 and haemoglobin below its range too. | Vedant | the trigger fires only on a value outside the range printed beside it (`0018`); with two values out of range the engine leads with the alphabetically first test name, so ferritin, and Vedant ruled the presenter says what the app says (Arjun, 18:46) |
| 7 | **The same report already scanned and saved** in the app, as the fallback if the camera will not read it on the day. | presenter, at the table | Beat 4 then still fires |
| 8 | The ten sentences recorded in Vedant's voice, in the room, and scored. | Vedant, Jacob | not yet; this decides Hindi vs English |
| 9 | Telugu Part 1 confirmed by a fluent speaker, if the toggle is to be shown. | Vedant | packet frozen 22:00 20 Sep; unconfirmed = do not show the toggle |
| 10 | Video recorded, backup phone built, charger and cable in the bag. | Vedant | |

---

## The run of show

Two people. **P** holds the phone and speaks; **S** stands beside with the second phone, the
printed report, and the checklist. The judge sees the phone screen; nothing else is narrated
until the end (spec 7.4: show it working, explain afterwards).

Total machine time across the four beats *(budget)*: about 45–60 s. Wall clock with speaking and
paper handling: 2–3 minutes. Measure per beat; a single total is not a target.

### Beat 0. The radios are off. *(0 s)*

**Screen:** the phone's quick-settings shade, airplane icon lit; then the IN2FIT Talk tab.
**P holds the phone up, shade down, and says:**

> "Airplane mode. It stays on. Everything you are about to see runs on this phone."

**P closes the shade. The Talk tab is showing, empty, "Say what you ate, or ask a question."**

### Beat 1. Speak a meal. *(measured 20 Sep 19:41–19:44, thermal 3, USB-powered: this sentence 9.2 s to the first figure, 19.0 s to the spoken sentence; the ten LOG rows ran 8.3–28.7 s / 15.2–39.3 s)*

**Ruled by Vedant, 20 Sep 20:30: Beat 1 is the TWO-FOOD sentence, row 1 of the utterance set.**
The three-food spec sentence measured 28.7 s to its first figure and 39.3 s to the spoken
sentence, the slowest of the ten; thirty-nine seconds is not a pause, it is the end of the demo.
The sentence changed, not the system.

**P brings the phone to the mouth (Jacob, 19:05: speak first, THEN show the screen; never both at
arm's length), HOLDS the microphone button (row 7d) and says, exactly, then finishes the last
word and lets go:**

> **मैंने दो रोटी और थोड़ी दाल खाई**
> *(English column: "I had two rotis and a little dal.")*

**What the judge sees, in order** (`0026`): the level meter moving while P speaks; the meter
freezing into a flat line the instant P lets go (a 300 ms tail, then nothing); the transcript appearing verbatim under "You";
"Working out what you meant"; the lead-in phrase spoken ("Noting that down."); then the stage
list, "Picking out the foods", "Matching the foods", "Adding up the figures", "Saving", with the
seconds counter beside the live stage; then the plate: each food as said, the figures, the
confidence band beside each, "Logged".

**The wait:** measured end to end on 20 September (`e2e-demo-condition-20sep.txt`, Rao's
worktree, row #1 of the ten): 9.2 s to the first figure, 19.0 s to the spoken sentence, on a
phone at thermal status 3. Speech recognition is not in these figures. Nobody has measured it
cool yet; Tuesday's rehearsal is where Vedant stands through it himself. The screen carries it: the stage list and the
counter are what P looks at, not the judge. **P says nothing during the wait unless asked**; if
asked, P reads the stage aloud: "It's matching the foods against the database now."

**When the plate lands, P says:**

**"थोड़ी दाल" states no amount, and what the app does with that is hard problem 01 on the deck
("when someone says 'some rice', the app asks what some means instead of inventing a figure").
One of two things happens on stage; we choose which, and the presenter has a line for each.
Vedant's preference: show the asking.**

**Branch A: it asks.** *(Preferred. NOT what the app does on 20 September: no path asks for a
quantity, there is no asking card and no key for one. It needs two changes by Tuesday's
rehearsal: the extraction leaves "थोड़ी / a little / some" as no quantity instead of the model's
"1" (Priya, the prompt), and the Talk screen asks "How much dal?" with the household choices
when a quantity is missing (Rao, the orchestrator event; Arjun, the card). Rao and Arjun say by
Tuesday whether it exists; if it does, this is the branch.)*

Screen: the rotis resolved with their figure; the dal named, with a question under it, "How
much dal?", and the choices: a katori, a bowl, a spoon, grams. P taps "a katori".

> "It stopped and asked. I said 'a little dal', and the app does not know what a little is, so it
> will not invent it: it asks me, once, and remembers the answer. That is the safety rule in
> three seconds: it never states a number it was not given, not even about my own plate."

**Branch B: it resolves silently at one serving.** *(What the log shows today,
`e2e-demo-condition-20sep.txt` row #1, `dal=1.0 none`: the model wrote quantity 1 for "a
little"; the dish path then took 1 with no unit as one piece of an authored dish and marked it
QUANTITY_STATED, so the plate reads one serving, 467.2 kcal, and the band beside it does not say
Rough. A number the model invented was treated as a number the person said. If this is the
branch, the asking exists only on a slide, and the line below must not claim the app says
"approximate" beside the dal unless the band on the phone actually reads that.)*

> "Two rotis and a little dal. The numbers come from the USDA tables, looked up by code, not
> written by the model. The word beside each figure is how far to trust it; the dal is a standard
> serving because I did not say how much, and that figure is the one a person corrects."

*(Tuesday's rehearsal decides which branch is real; the other is struck.)*

*(Ruled 18:40, Rao: the demo profile is PRE-SEEDED with the bundled utensil defaults from
`household-units.csv`, katori 150 g for cooked pulses, grains and dairy, 100 g for a sabzi, plate
200 g of rice, glass 200 ml, a spoon of oil 10 g, one roti 45 g from its recipe. "One katori,
your size" is on screen as a figure carrying `HOUSEHOLD_UNIT_DEFAULT`. Calibrating a utensil is
descoped for the battle; P does not offer to.)*

### Beat 2. Ask about your own history. *(measured 20 Sep 19:41: own figures on screen at 0.55 s, the spoken one-sentence answer at 9.2 s; 10.3–10.7 s in the ten-sentence sequence, warmer)*

**P taps the microphone and says, exactly:**

> **मेरे lunch में कितना protein था?**
> *(English column: "How much protein was in my lunch?")*

**Screen:** meter, transcript, "Working out what you meant", the lead-in ("Let me check your
records."), then "Checking your records", "Looking up facts", "Writing the reply" with the
counter; then the answer, one sentence, on screen BEFORE it is spoken; then spoken. **The answer
is in English, on screen and aloud, whatever language P spoke in** (Meera, 18:07, traced end to
end; `values-hi` is empty by design and the model replies in English regardless). P does not
claim otherwise; if asked, "it answers in English in this build."

**The wait:** the person's own figures are on screen at 0.55 s, before the model starts; the
one-sentence answer arrives and is spoken at 9.2 s (380-token prompt, 11 generated tokens;
`e2e-demo-condition-20sep.txt`). The old 30.7 s (`0025`) is history. What the judge looks at
for those nine seconds is their own diary, which is the point.

**When the answer lands, P says:**

> "That is my own record answering, not an article. The protein figure in that sentence is the one
> the database produced; the model is not allowed to state a number it was not handed, and there
> is a test for that."

*(The test is a JVM test. Whether the guard holds on the phone in the demo's own ANSWER turns is
Rao's ten-sentence device run, read by a person; until then this line claims the test, not the
device. Audit, 18:45.)*

*Alternate, only if a week of history was seeded:* **मैंने पिछले मंगलवार को क्या खाया?** ("What did I
eat last Tuesday?")

### Beat 3. Scan a lab report. *(budget 15 s; OCR itself unmeasured on this report)*

**S hands P the printed report. P switches to the Scan tab and says:**

> "A blood report, on paper. Nothing about it leaves the phone; there is no permission it could
> leave by."

**P holds the report flat under the table light, fills the frame, taps Capture.**

**Screen:** "Capturing", "Reading the text" with the counter; then each value read off the sheet
beside the row it came from, with "printed range … to …" under each value that had one, the report
date, and "Save N values". **P ticks ferritin and haemoglobin and taps Save. The save takes about
9 s** *(measured: 9.3 s, `lab report saved: 1 values, regenerated meal 1`)*, because it
regenerates the last meal's advice with a model call so that Beat 4 is instant; the stage list
carries it. P uses those seconds to put the paper down.

**P says:**

> "Ferritin, and the range the sheet itself prints beside it. The app does not carry ranges of
> its own; it reads the lab's. It will not say what that means for my health. It will change
> what it suggests."

*Optional, if time allows and the turn is under 20 s:* **मेरा iron कम है, मुझे क्या खाना चाहिए?**
("I have anaemia, what should I eat for iron?") shows a RECOMMEND answering from declared
conditions and the knowledge file, with the referral sentence set apart beneath it.

### Beat 4. The same meal, different advice. *(measured 20 Sep 19:41: 0.47 s to the advice after a report, no model call; the first tap before any report is instant only after `ea75984`, not yet measured on the device)*

**P returns to the Talk tab and taps "Advise again on my last meal".**

**Screen:** "Checking your records" with the counter, then the advice: the trigger sentence
first, verbatim from the rules engine, then the candidates:

> "Your report from 2026-09-12 shows Ferritin at 8.2 ng/mL, below the 15 printed on it, so
> suggestions are ranked differently now."

*(The date, value and bound are whatever the printed report carries; these are the scripted
report's. With two values below range the engine leads with the alphabetically first test name:
ferritin before haemoglobin. That rule is arbitrary and is flagged to Rao; until it changes, the
presenter says what the app says.)*

**P says:**

> "Same meal. Same numbers. Different advice, because the phone read a piece of paper a minute
> ago. That sentence is a template the rules engine chose, not prose the model wrote; the only
> figures in it are the ones printed on the report. Nothing here diagnoses. It says what the
> report says and hands the decision back."

**That is the demo.** If the room is with it, stop here.

### Beat 5, only if Part 1 of the Telugu packet is confirmed. The language toggle. *(budget 10 s)*

**P opens the language setting (the in-app button if Arjun adds the one-liner above; otherwise
S has Settings > App languages > IN2FIT open on the second phone), chooses తెలుగు, and returns
to the Talk tab.**

> "The same screen, in Telugu. Every line of this interface is a key in one table; adding a
> language is one file, reviewed by a speaker, not a rebuild."

**If Part 1 is NOT confirmed by the day, this beat is cut, and Vedant knows why: the wording on a
health screen is shown to Telugu-speaking judges only after a Telugu speaker has read it.** The
picker still exists and P can say so (see the playbook). No apology.

### The close, spoken after the phone is down

> "Three things most health apps keep in three places, one model that sees all of them, and none
> of it ever left the phone. Every figure you saw came from public-domain data looked up by code.
> The model reads and it phrases; it never counts."

---

## The failure playbook

Every hackathon demo breaks once. The recovery is one sentence and one action, and neither is an
apology. Rehearse each branch twice.

| failure | P says | P does |
| --- | --- | --- |
| **The recogniser mishears** (the transcript on screen is not what P said) | "It heard me wrong, and it shows me exactly what it heard rather than hiding it. Once more." | Taps the mic and says the SAME sentence, a little slower, facing the phone. If nothing in the sentence resolved, the app has already asked again on its own (`0026`, Rao's zero-resolve path); P just answers it. |
| **The model is slow** (the counter is climbing past 15 s) | "The foods and the figures are already on screen from the database. What you are watching now is the model writing one sentence; the counter is real time, not a spinner." | Nothing. Does not tap. Reads the current stage aloud if the silence is long. |
| **The camera will not read the report** ("No test values could be read") | "Glare. Let me flatten it." | Retake once, report flat, light from the side, whole page in frame. If the second retake fails: "I scanned this same sheet this morning; it is already in my records," and goes straight to Beat 4, which works from the saved scan (pre-demo checklist row 7). |
| **A model fails to load** ("The model could not start" / "not loaded on this phone yet") | "It said so rather than pretending. Ten seconds." | Closes the app from Recents, reopens it, waits for the Talk tab, tries once. If it fails a second time, S hands over the backup phone, already warm, and P repeats the beat from the top of it. |
| **The phone froze while the screen was off** (the app is blank or stale after the screen came back) | "The phone put it to sleep. One moment." | Wakes the screen, swipes the app away, reopens. Then checks the screen-timeout row of the checklist was actually done. |
| **The judges ask for a language nobody on the team speaks** (Telugu, on stage) | *If Part 1 confirmed:* "The interface is in Telugu; here." *(toggle)* "The recogniser has a Telugu model too. I will not read Telugu at you badly, so that part I demonstrate on screen, not aloud." *If not confirmed:* "There is a Telugu model and a Telugu string table, and Telugu speakers are reviewing every health sentence in it this week. I will not show wording on a health screen before a speaker has read it." | Shows the picker exists (system Settings > Apps > IN2FIT > Language, or the in-app picker when it lands). Does not switch to unreviewed Telugu. |
| **It heard nothing** (no transcript, or "That could not be used") in a loud room | "Loud room. Once more, closer." | Brings the phone to the mouth, or plugs the wired headset in, and repeats the sentence. |
| **It kept listening after P let go** (the meter does not freeze) | "One moment." | With push-to-talk shipped this should not happen: release ends capture after a 300 ms tail. If the meter runs on anyway, P taps the button once (a tap under 300 ms is not a sentence, and release is a no-op outside a hold), then repeats the sentence on a fresh hold, finishing the word before letting go. |
| **A notification or call lands on screen** | Nothing. | Swipes it away. |
| **The answer contains a number that looks wrong** | "That figure came from the database row for that food; if it is off, the row is off, and the row is public and correctable. The model did not make it up: it cannot." | Taps the confidence label to show the reason. Moves on. |
| **The whole device is dead** | "We recorded this run last night, on this phone, in airplane mode." | S plays the video. P narrates it with the same lines above. |

**The rule under every branch:** say what the app is doing, in one sentence, and act. The app
never shows a fake result, so there is never a moment where P has to explain away a wrong
number; every failure state on screen is a sentence the app chose to show. Read it out.

---

## What this file waits on

| gap | owner |
| --- | --- |
| The clean airplane-mode run: a number for every *(budget)* above, per beat, warm, nothing else running. | Rao |
| Vedant's ten recordings scored; the column chosen. | Vedant, Jacob |
| `TalkViewModel` sending the profile's speech language instead of `te`. TONIGHT. | Arjun |
| The interface toggle: the system per-app setting is the decision; a one-line button that opens it is the nice-to-have. Rao confirms the realme menu path. | Arjun, Rao |
| Telugu Part 1 confirmed, or Beat 5 cut. | Vedant |
| The seeded week of history, if the "last Tuesday" alternate is wanted. | Rao |
| The trigger sentence's choice between two out-of-range values (alphabetical today; furthest outside its range is the principled rule). Known and arbitrary until changed. | Rao |
| Push-to-talk on the Talk screen (the engine side is shipped; the screen's hold gesture is Arjun's). | Arjun |
