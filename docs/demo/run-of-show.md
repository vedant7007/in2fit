# IN2FIT run of show

Rewritten end to end at 00:09 on 21 September 2026 (Nila), against master `16baf50`, after the
night's rulings. For Vedant, who presents; for S, who stands beside him; for Rao, who replaces
every *(budget)* with a row from a run on the phone; for Jacob, whose recogniser numbers are the
ones the speaking rests on. Rehearse from this file, not from memory of it. Where this file says
the app does something, the commit is named; where it does not yet, that is said in the same
sentence, and the list at the end is the honest one.

## The rulings this file obeys

- **The demo is Hindi, in Vedant's own voice.** Marathi-accented Hindi; that is the voice the
  numbers were measured on (foods heard 16 of 16 in hi against 7 of 16 in en; `0031`, the ruling
  paragraph). **There is no English path in this file.** If anyone proposes an English beat, 0031
  is the answer. The interface stays English; the app's spoken answer is English in this build;
  neither is the presenter speaking English.
- **Beat 1's sentence is frozen**: row 1 of `data-authoring/demo-utterance-set.csv`, exact on
  Vedant's voice through the hi checkpoint (`logs/asr-eval-vedant-demo.log`, Jacob's worktree).
  Nobody rephrases it or makes it more impressive (Jacob, accepted by Vedant, 20 Sep 22:00).
- **Everywhere else he may talk instead of reciting.** Foods heard held flat at 16 of 16 from 0
  to 14 inserted words (Jacob, 22:40); the recogniser did not lose a food to extra words on any
  clip that fit. **The budget is seconds, not words**: the hold cap is 30 s
  (`PushToTalk.MAX_HOLD_MS`, no real sentence reaches it), and the model's wait grows with the
  words, so a sentence stays under about fifteen seconds for the wait's sake (`0014`), not the
  recogniser's. Nothing in this file assumes a twenty-second cap.
- **PRESS · PAUSE · SPEAK · FINISH THE WORD · LET GO.** The pause first because there is no
  pre-roll: the microphone opens at the press, and a word spoken as the thumb lands is not
  recorded (hi_06 lost "मैंने पिछले" exactly that way, on his own recorder). Finish the word because
  a cut inside the last word costs the sentence (200 ms inside, 6 and 5 of ten; 300 ms, 0 of ten)
  and a late lift costs nothing. **Read the box under Beat 1 before rehearsing it: on tonight's
  master the release does not end the recording; the endpointer does.**
- `mic_hold_hint` is English-only in the demo build; its Telugu line went with the old sentence.
- The app does not stop and ask "how much dal?" this week. An unstated amount is an assumed
  serving at the band that says so (`425691d`, `0035`); the "taken as" caption is Ira's card and
  is not on a screen yet (on watch in `countdown.md`; the cut-off is the evening of the 25th).

**Timings** marked *(measured)* are from the phone, with the log named. Everything else is a
*(budget)*, and Rao replaces it with a row from a run on the phone. A budget is not a measurement.

---

## Reboot, wait, check, then demo (Rao, 21 Sep; the procedure for the 26th)

A warm ANSWER took 66 s on 21 Sep on a phone seven minutes past a reboot with its indexer
(`android.process.acore`) on two cores; the same call is 10 s on a quiet phone. The speed of the
demo depends on what Android is doing in the background, and in a hall nobody knows that by
looking. So the state is measured, not assumed, in this order, at the table, with the laptop:

1. **Reboot the phone.** Every 0 %-CPU model stall so far (three) followed an idle spell and
   cleared with a reboot. Airplane mode survives the reboot; check it anyway.
2. **Wait ten minutes.** The post-boot indexing ran at 140-230 % CPU for four minutes on 21 Sep
   and the load average was still above 20 at nine minutes. Do nothing on the phone meanwhile.
3. **Open IN2FIT and run one Beat 1 sentence** (row 9 of the pre-demo checklist): the model is
   then resident and warm.
4. **Run the check:** `powershell -File tools\preflight.ps1` on the laptop with the phone on the
   default adb server. It prints every value with its threshold beside it (load average,
   runnable count, thermal status, CPU temperature, any process above 30 % CPU by name, minutes
   since boot, airplane mode, Wi-Fi, the app's PSS with the model resident, the adb ownership) and
   ends with one line: **READY**, or **WAIT: <reasons>**. The thresholds are first guesses marked
   as such in the script; every run writes `logs/preflight-<stamp>.txt`, so by the 26th READY is
   a measured shape on this phone, not a hope.
5. **WAIT means wait**, then run it again. A named process above 30 % is stopped (Instagram
   restarts itself after every reboot on this phone: force-stop it). A thermal status above 0
   means the phone sits for five minutes. A load average above 4 with nothing named means the
   indexer is still working: wait.
6. **READY, then unplug and hand the phone to P.** Nothing else is opened on it afterwards.

## If it is slow on stage anyway

The cause may not be found by the 26th, so this is planned for, not hoped against. WHAT THE APP
DOES TODAY when an answer takes sixty seconds: nothing times out and nothing gives up, at any
layer (no timeout in the view-model, the orchestrator, the engine or the arbiter). The person
sees the stage list with the live stage and a running seconds counter ("Writing the reply · 41 s"),
the hold button greyed, and, for a question (Beat 2) or a request, THEIR OWN FIGURES ALREADY ON
SCREEN under "From your diary" before the model is even asked, because the figures are emitted
first (0014). The spoken part arrives when it arrives. For Beat 1 the plate cannot appear before
the model has picked out the foods, so a slow extraction shows only the counter under "Picking
out the foods". There is no cancel while the model works; the only way out of a stalled turn is
to kill the app (the playbook's restart branch), and a stall looks exactly like a slow turn until
the counter passes a minute.

WHAT P SAYS: the figures are the demo. At Beat 2, P reads the "From your diary" card aloud while
the counter runs ("It already has my week: 6.1 mg of iron; it is writing the sentence now"). At
Beat 1, P names the stage aloud and waits; at 60 s on the counter P uses the restart branch and
the pre-saved diary (row 7), not a second attempt. Nothing in the app hides the wait, and nothing
should: a spinner that hid the counter would turn an honest 40 s into a mystery.

## Before anyone walks to the table: the device state checklist

Rao's report of 20 September showed what a hot phone with Instagram in the foreground does: every
number doubled. This list exists so that condition cannot happen on the day. S runs it aloud,
ticking, at the table, before the first sentence. Ten minutes.

| # | check | why |
| --- | --- | --- |
| 0 | **The right APK.** The stage build is `assembleDemoDebug`; since `1eee48b` the demo and full APKs differ in CODE, not only manifest. On the phone: long-press the IN2FIT title on About and read the pre-flight's last card. It must say **"Not in this build. The demo build carries no scripted feed."** If it shows a switch, the wrong APK is installed: stop and reinstall. | The cheapest proof there is that the right APK is on the phone, read off the device. |
| 1 | **Airplane mode on, and the Bluetooth icon ABSENT from the status bar.** Wi-Fi and Bluetooth off too (airplane mode leaves them as they were). | Beat 0 is the proof; a Bluetooth icon beside the airplane one undoes the sentence. |
| 2 | **Every other app closed.** Recents swiped clear. | The report that doubled every number had Instagram in the foreground. |
| 3 | **Phone cool.** Off charge for 15 minutes, out of a pocket, back of the case not warm to the hand. | A warm chip throttles; every figure in this file was measured at thermal status 3 and is the slow case. |
| 4 | **Battery above 50%, not charging during the demo.** | Charging warms the phone; low battery throttles. |
| 5 | **Screen stays awake.** Screen timeout to the maximum. | This phone freezes app processes when the screen goes off (`0012`); a frozen probe read 1.08 tok/s. |
| 6 | **Do Not Disturb OFF.** Some DND modes mute the media stream, and the voice is the media stream (Meera). | A silent voice is worse than a notification. |
| 7 | **Brightness full; media volume 15 of 15, READ off the probe.** `TtsVoiceProbeTest` prints "stream 3 at N of MAX"; N must be the max. From a laptop: `adb shell cmd media_session volume --stream 3 --set 15`. | The lead-in phrase and the spoken answer must be heard from the back of the room. |
| 7a | **The wired speaker plugged in: USB-C or 3.5 mm. NEVER Bluetooth.** `TtsVoiceProbeTest`'s "routed to" line must name the wired device. | Bluetooth is a radio with an icon beside the airplane one. |
| 7b | **One spoken turn heard from the back of the room before the judges arrive.** | The only test of the speaker that counts. |
| 7c | **A wired headset or lapel microphone in the bag**, rehearsed once through a full turn. | In a loud hall the microphone must be at the mouth whatever the hand is doing. |
| 7d | **How the microphone ends a sentence on THIS build, read before rehearsing.** `PushToTalk` is shipped in `ml/asr` (`7a6e49d`, 30 s cap, 300 ms tail) and tested, **and nothing calls it on master `16baf50`**: `DefaultOrchestrator` collects `asr.listen(lang)`, `UserIntent` has no `EndSpeech`, and the Talk screen's release is a no-op (`TalkScreen.kt`, "until then the endpointer ends it"). So tonight: the press opens the microphone, the release does nothing, and the recording ends when the energy endpointer hears **700 ms of quiet** (`EnergyEndpointer.hangoverMs`). Rao's two lines (`AppModule`, `DefaultOrchestrator.spoken()`) and Arjun's `EndSpeech` make the release real; until they land, the presenter's five words gain a sixth: **STAY QUIET until the meter freezes.** | The crowded-hall measurement (`0031`): open listening in babble went from 8 of ten to 0 of ten the moment one laugh landed 300 ms after the sentence. That is the failure the hold exists for, and it is not wired tonight. |
| 8 | **Storage: at least 2 GB free.** | The app copies assets on first run and writes its log. |
| 9 | **App WARM, not cold.** Open IN2FIT and run one full Beat 1 sentence to the answer, then leave it on the Talk tab. This also gives Beat 4 its "last meal". | Cold model load reads 5.4–8.5 s on this phone; warm is under a second (`logs/hw-report-*.txt`). |
| 10 | **Speech language: tap हिन्दी on the Talk screen. Interface: English.** The Talk screen carries three chips under "Speak in": తెలుగు, हिन्दी, English (`TalkScreen.kt:155`). **The default is English (`TalkViewModel`, `language = "en-IN"`), and the choice lives in the view-model, so any restart of the app puts it back to English.** A Hindi sentence through the en checkpoint comes back as "dell" for dal, five of five. The interface language is the phone's per-app setting (Settings > System > Languages & input > App languages > IN2FIT), English for the demo. | Row 10 is the one that fails silently. It is repeated in the playbook under every branch that restarts the app. |
| 11 | **The realme in S's hand**, same build, same checklist, rehearsed. It is the rehearsal and fallback-video device (Vedant, 20:45); whether it stands in front of the judges if the iQOO fails is Vedant's call on the day, not the plan's assumption. | Spec 7.4. |
| 12 | **The recorded video** of the same sequence, on a laptop, ready to play. | If the live device fails twice, the video is the demo. |

## Before the day: the pre-demo checklist

| # | item | owner | status 21 Sep 00:09 |
| --- | --- | --- | --- |
| 1 | Models staged on the phone: the LLM, the Hindi ASR model and its `tokens.txt`, the TTS voices, checksums verified. On the iQOO this is hour one on site, `tools/stage-models.ps1` (`countdown.md`, steps 0 to 8). | Rao; whoever holds the cable | staged on the realme 19 Sep; the script is not yet run on a phone |
| 1a | **The platform voice data, on a network, before airplane mode**: Speech Services by Google → Install voice data → Hindi (India) and English (India); then `TtsVoiceProbeTest` in airplane mode showing an `en-in-x-*-local` and a `hi-IN` voice with `network=false`. The English answer is spoken by the platform voice; the bundled voice Vedant rejected is the insurance (`0019` addendum 10). | whoever holds the phone; Meera reads the probe | not done on any phone |
| 2 | The demo build installed: `demo` flavour, permission check green. | Rao | every assemble asserts it |
| 3 | Profile: name, a declared context if Beat 5 is attempted. Speech language is the chip (row 10 above); interface language is the system setting. **There is no in-app button that opens the language page** (`ACTION_APP_LOCALE_SETTINGS` is not in the code). | Arjun / Rao | as of `16baf50` |
| 4 | **A meal already logged today**, so Beat 4's "Advise again on my last meal" has a meal. | P, at the table (row 9 does it) | |
| 5 | **A week of history**, only if the "last Tuesday" alternate is wanted. | Rao (seed) | not seeded; the alternate is off unless it is |
| 6 | **The printed lab report**, on paper, with FERRITIN (and haemoglobin) BELOW the range printed on the same sheet. Plus a second copy. | Vedant | the trigger fires only on a value outside the range printed beside it (`0018`); with two values out of range the engine leads with the alphabetically first test name (ferritin), and the presenter says what the app says |
| 7 | **The same report already scanned and saved** in the app, the fallback if the camera will not read it on the day. | P, at the table | Beat 4 then still fires |
| 8 | The ten sentences recorded in Vedant's voice and scored. | Vedant, Jacob | DONE 20 Sep; the Hindi ruling rests on it |
| 9 | Telugu Part 1 confirmed by a fluent speaker, if the toggle is to be shown. | Vedant | packet frozen 22:00 20 Sep at `1a16705`; unconfirmed = Beat 5 is cut |
| 10 | Video recorded on the realme, charger and two cables in the bag. | Vedant | |

---

## The run of show

Two people. **P** (Vedant) holds the phone and speaks; **S** stands beside with the realme, the
printed report and the checklist. The judge sees the phone screen; nothing else is narrated
until the end (spec 7.4: show it working, explain afterwards).

Machine time across the four beats, from the measured rows below: about 19 + 11 + 9 + 0.5 s,
**plus the OCR, which nobody has measured**, plus the speech capture, which is not in any of
those figures. Wall clock with speaking and paper handling: about three minutes. Measure per
beat; a single total is not a target.

### Beat 0. The radios are off. *(0 s)*

**Screen:** the quick-settings shade, airplane icon lit; then the IN2FIT Talk tab.
**P holds the phone up, shade down, and says:**

> "Airplane mode. It stays on. Everything you are about to see runs on this phone."

**P closes the shade. The Talk tab shows "Say what you ate, or ask a question.", the "Offline by
build. No internet permission." line, and the हिन्दी chip already chosen (row 10).**

### Beat 1. Speak a meal. *(measured 20 Sep 19:41, thermal 3, USB-powered, `e2e-demo-condition-20sep.txt` row #1: 9.2 s to the first figure, 19.0 s to the spoken sentence; speech capture not included)*

**P brings the phone to the mouth (speak first, THEN show the screen; never both at arm's
length). PRESS · PAUSE · SPEAK · FINISH THE WORD · LET GO · and on this build, STAY QUIET until
the meter freezes. P presses "Speak", takes a breath, says exactly:**

> **मैंने दो रोटी और थोड़ी दाल खाई**
> *(FROZEN, measured 20 Sep, do not edit. Row 1 of `demo-utterance-set.csv`.)*

**What the judge sees, in order** (`0026`): the button reads "Listening…" and its bottom edge
moves with P's voice; the edge freezes when the endpointer hears quiet (on this build) or when P
lets go (once the release is wired); the transcript verbatim under "You"; "Working out what you
meant"; the lead-in spoken aloud, "Noting that down."; then the stage list, "Picking out the
foods", "Matching the foods", "Adding up the figures", "Saving", with the seconds beside the live
stage (`Turn.kt`, `talk_elapsed_seconds`); then the plate: each food as said, its figure, the
band beside it; "Logged".

**The wait:** 9.2 s to the first figure and 19.0 s to the spoken sentence on a throttled phone;
nobody has measured it cool; Tuesday's rehearsal is where Vedant stands through it himself. **P
says nothing during the wait unless asked**; if asked, P reads the stage aloud: "It is matching
the foods against the database now."

**What the plate shows for "थोड़ी दाल" on this build** (`425691d`, `0035`): "dal: 1 katori" with
the band Rough, because the amount was never stated and the app never marks an unstated amount
as stated. The grams behind it are the dal recipe's serving, 180 g. **The "taken as 180 g" line
is not on a screen yet** (Ira's card; `plate_unit_taken_as` has no caller). P says the number the
card shows, and the same number every rehearsal.

**When the plate lands, P says:**

> "Two rotis and a little dal. The numbers come from the USDA tables, looked up by code, not
> written by the model. The word beside each figure is how far to trust it: the dal says Rough,
> because I never said how much, and that figure is the one a person corrects."

**Only if the card reads "taken as … g" by the day, P points at the dal line and adds:**

> "And it tells you what it guessed: one katori, taken as a hundred and eighty grams. I never said
> how much, so it does not pretend I did. That line is mine to correct, and the app never hides
> the number it assumed."

*(Not on screen by the evening of the 25th: this passage goes, and the caption comes off the
deck's mockups; Vedant's rule, 20 Sep 23:17.)*

### Beat 2. Ask about your own history. *(measured 20 Sep 19:44, row #7 of the ten, throttled: own figures on screen at 0.53 s, the spoken answer at 10.7 s. The four-turn test's iron question read 0.55 s and 9.2 s.)*

**Phone to the mouth, the five words, and P asks, in his own words or these:**

> **मेरे lunch में कितना protein था?**
> *(row 7; "How much protein was in my lunch?")*

**Screen:** "Listening…", the transcript, "Working out what you meant", the lead-in aloud ("Let me
check your records."), then the person's own figures on screen at half a second, before the model
starts; "Checking your records", "Looking up facts", "Writing the reply" with the seconds; then the
answer, one sentence, on screen BEFORE it is spoken; then spoken. **The answer is in English, on
screen and aloud, whatever language P spoke in** (`values-hi` is empty by design; the model
replies in English). P does not claim otherwise; if asked: "It answers in English in this build."

**When the answer lands, P says:**

> "That is my own record answering, not an article. Those figures were on screen before the
> model started, straight from my diary; the model is not allowed to state a number it was not
> handed, and there is a test for that."

*(The test is a JVM test. Whether the guard holds on the phone in the demo's own ANSWER turns is
Rao's device run, read by a person; until then this line claims the test, not the device.)*

### Beat 3. Scan a lab report. *(budget 15 s; the photograph is UNMEASURED: ML Kit has one observation ever, one haemoglobin row; the save after it is measured at 9.3 s)*

**S hands P the printed report. P switches to the Scan tab and says:**

> "A blood report, on paper. Nothing about it leaves the phone; there is no permission it could
> leave by."

**P holds the report flat under the table light, fills the frame, taps "Capture".**

**Screen:** "Capturing", "Reading the text" with the seconds; then each value read off the sheet
beside the row it came from, "printed range … to …" under each value that had one, the report
date, and "Save N values". **P ticks ferritin and haemoglobin and taps Save. The save takes
about 9 s** *(measured 9.3 s, `lab report saved: 1 values, regenerated meal 1`)*, because it
regenerates the last meal's advice with a model call so that Beat 4 is instant. P uses those
seconds to put the paper down.

**P says:**

> "Ferritin, and the range the sheet itself prints beside it. The app carries no ranges of its
> own; it reads the lab's. It will not say what that means for my health. It will change what it
> suggests."

*Optional, only if the room is with it:* P asks, in his own words or these, **मेरा iron कम है, मुझे
क्या खाना चाहिए?** (row 9; measured throttled: own figures 0.66 s, spoken 11.5 s) and a RECOMMEND
answers from declared conditions and the knowledge file, the referral sentence set apart beneath.

### Beat 4. The same meal, different advice. *(measured 20 Sep 19:41: 0.47 s to the advice after a report, no model call)*

**P returns to the Talk tab and taps "Advise again on my last meal".**

**Screen:** "Checking your records", then the advice: the trigger sentence first, verbatim from
the rules engine, then the candidates:

> "Your report from 2026-09-12 shows Ferritin at 8.2 ng/mL, below the 15 printed on it, so
> suggestions are ranked differently now."

*(The date, value and bound are whatever the printed report carries. With two values below range
the engine leads with the alphabetically first test name; the presenter says what the app says.
A value "well outside" its range takes the escalation template instead, which ends "This is worth
showing to a doctor."; P reads whichever is on screen.)*

**P says:**

> "Same meal. Same numbers. Different advice, because the phone read a piece of paper a minute
> ago. That sentence is a template the rules engine chose, not prose the model wrote; the only
> figures in it are the ones printed on the report. Nothing here diagnoses. It says what the
> report says and hands the decision back."

**That is the demo.** If the room is with it, stop here.

### Beat 5, only if Part 1 of the Telugu packet is confirmed. The language toggle. *(budget 10 s)*

**S has Settings > App languages > IN2FIT open on the realme, or P opens it on the phone (there
is no in-app button). P chooses తెలుగు and returns to the Talk tab.**

> "The same screen, in Telugu. Every line of this interface is a key in one table; adding a
> language is one file, reviewed by a speaker, not a rebuild."

**Part 1 not confirmed by the day: this beat is cut, and Vedant knows why**: wording on a health
screen is shown to Telugu-speaking judges only after a Telugu speaker has read it. The picker
still exists and P can say so (playbook). No apology. As of tonight it is not confirmed.

### The close, spoken after the phone is down

> "Three things most health apps keep in three places, one model that sees all of them, and none
> of it ever left the phone. Every figure you saw came from public-domain data looked up by code.
> The model reads and it phrases; it never counts."

---

## The failure playbook

Every hackathon demo breaks once. The recovery is one sentence and one action, and neither is an
apology. Rehearse each branch twice. **Every branch that restarts the app ends with row 10: tap
हिन्दी again.**

| failure | P says | P does |
| --- | --- | --- |
| **The recogniser mishears** (the transcript is not what P said) | "It heard me wrong, and it shows me exactly what it heard rather than hiding it. Once more." | The same sentence, a little slower, phone at the mouth, the five words. If nothing resolved, the app shows "Not logged yet. Say it differently, or type it." (`NeedsConfirmation`); P says it again or types it ("Type instead" is on the screen). |
| **The model is slow** (the seconds beside the stage pass 15) | "The foods and the figures are already on screen from the database. What you are watching is the model writing one sentence; those seconds are real time, not a spinner." | Nothing. Does not tap. Reads the current stage aloud if the silence is long. |
| **It kept listening after P finished** (the edge keeps moving) | "It is waiting for the room to go quiet. I'll help it." | On this build the endpointer needs 700 ms of quiet: P stops speaking, brings the phone to the mouth and cups a hand over the microphone for a second. Once the release is wired this branch does not exist: letting go ends it. |
| **It heard nothing** ("That could not be used") in a loud room | "Loud room. Once more, closer." | Phone to the mouth, or the wired headset in, and the sentence again with the five words. |
| **The camera will not read the report** ("No test values could be read. Try a sharper, straighter photo.") | "Glare. Let me flatten it." | Retake once, report flat, light from the side, whole page in frame. Second failure: "I scanned this same sheet this morning; it is already in my records," and straight to Beat 4, which works from the saved scan (pre-demo row 7). |
| **A model fails to load** ("The model could not start" / "The model is not loaded on this phone yet.") | "It said so rather than pretending. Ten seconds." | Closes the app from Recents, reopens it, waits for the Talk tab, **taps हिन्दी**, tries once. Second failure: S hands over the realme, already warm, हिन्दी chosen, and P repeats the beat. |
| **The phone froze while the screen was off** (the app blank or stale) | "The phone put it to sleep. One moment." | Wakes the screen, swipes the app away, reopens, **taps हिन्दी**. Then checks the screen-timeout row was actually done. |
| **The judges ask for Telugu** | *If Part 1 confirmed:* "The interface is in Telugu; here." *(toggle)* "The recogniser has a Telugu model too. I will not read Telugu at you badly, so that part I demonstrate on screen, not aloud." *If not confirmed:* "There is a Telugu model and a Telugu string table, and Telugu speakers are reviewing every health sentence in it this week. I will not show wording on a health screen before a speaker has read it." | Shows the picker exists (Settings > Apps > IN2FIT > Language). Does not switch to unreviewed Telugu. |
| **A notification or call lands on screen** | Nothing. | Swipes it away. |
| **The answer contains a number that looks wrong** | "That figure came from the database row for that food; if it is off, the row is off, and the row is public and correctable. The model did not make it up: it cannot." | Moves on. (The band is a word beside the figure, not a control; there is nothing to tap.) |
| **The whole device is dead** | "We recorded this run, on a phone, in airplane mode." | S plays the video. P narrates it with the same lines above. |

**The rule under every branch:** say what the app is doing, in one sentence, and act. The app
never shows a fake result, so there is never a moment where P has to explain away a wrong
number; every failure state on screen is a sentence the app chose to show. Read it out.

---

## Read as a hostile judge, 21 Sep 00:09, against master `16baf50`

Where this file promises something the code does not do, or leans on a number nobody measured.
Each row is checked against the code, not against anyone's note.

| # | the promise | the code or the log | what it costs on the day |
| --- | --- | --- | --- |
| 1 | "Let go" ends the recording (the five words, row 7d, the meter freezing at release) | `PushToTalk` exists with tests and no caller; `DefaultOrchestrator` collects `asr.listen`; no `EndSpeech`; `TalkScreen` release is `{}`. The energy endpointer ends the capture after 700 ms of quiet. | In a loud hall, the one measured failure (`0031`: 8 of ten to 0 of ten when a laugh lands after the sentence). The hold gesture on the screen is cosmetic tonight. Rao's two lines and Arjun's `EndSpeech` are the fix; not chased here, reported. |
| 2 | The presenter speaks Hindi and the right checkpoint hears it | The Talk screen's chip sets it; the default is `en-IN`; the choice is view-model state and resets on every restart. | A silent wrong-checkpoint turn after any playbook restart. Row 10 repeated under every restart branch. A persisted or Hindi default is one line in `TalkViewModel`. |
| 3 | The photograph is read and the values land beside their printed ranges | Not measured: ML Kit has one observation ever, on a single haemoglobin row (Vedant, 21 Sep). Priya is building the fixture set. The 9.3 s after it is measured; the seconds before it are not. | Beat 3 is the live step with no number. On watch in `countdown.md`: no photograph result by the evening of the 25th, Vedant softens the moment slide. |
| 4 | "dal · 1 katori · taken as 180 g" on the plate | Resolver right since `425691d`; no screen renders the caption (`plate_unit_taken_as` has no caller). | The second Beat-1 passage is conditional and says so; the deck's mockups show the caption. On watch; cut-off the evening of the 25th. |
| 5 | The band is a control ("tap to correct", "taps the label to show the reason") | No clickable on the band or the item line in `TalkScreen`; `UserIntent.CorrectValue` exists in the contract and nothing on the Talk screen sends it. | Removed from the playbook. P never says "tap to correct" on stage unless a screen does it by then. |
| 6 | Beat 1 and Beat 2's figures are what the judge will see | Measured on the realme at thermal status 3 with transcripts injected (`Transcribed` at 4 ms); speech capture and transcription are not in any figure; nothing measured cool; nothing measured on an iQOO. | Every number here is the slow case for the model and no case at all for the microphone. Tuesday's rehearsal on the realme adds the capture; hour one on site adds the phone. |
| 7 | The model never states a number it was not handed | A JVM test (`NumericGuard`), and Rao's ten-sentence device run read by a person for the ANSWER turns. | Beat 2's line says "there is a test for that", which is true; it does not say "on this phone". |
| 8 | Beat 4's trigger sentence leads with ferritin | Alphabetical first among the out-of-range tests; "well outside" takes the escalation template instead. Neither threshold is in this file. | P reads whichever sentence is on screen. The threshold for "well outside" is Rao's to state; until then the printed report's values decide which template fires, and the rehearsal shows which. |
| 9 | Beat 5, the Telugu toggle | Part 1 unconfirmed; no in-app language button; the picker is the system page. | Cut as of tonight. The playbook line stands. |
| 10 | The seeded week of history behind "last Tuesday" | Not seeded. | The alternate is off; Beat 2 uses "my lunch", which needs only Beat 1's meal. |
| 11 | The hint under the microphone says the five words | The button reads "Speak" / "Listening…" (`mic_speak`, `mic_listening`); `mic_hold_to_speak` and `mic_hold_hint` have no caller. | Nothing on the day; the words are the presenter's, not the screen's. Arjun's when the hold is real. |
| 12 | The platform voice speaks the English answer | Not installed on any phone yet (pre-demo row 1a); without it the bundled voice Vedant rejected speaks. | Hour one on site, step 0 of the countdown's list. The one failure the plan cannot work around in airplane mode. |

Nothing in the four beats claims the personal katori, the conversational ask, a cold-phone
timing, or an English utterance. Those were the four claims that moved tonight; none of them is
in this file.

---

## What this file waits on

| gap | owner |
| --- | --- |
| The release wired to the recording: Rao's two lines, Arjun's `EndSpeech`. Until then, row 7d's sixth word. | Rao, Arjun |
| A Hindi default, or a persisted choice, for the speech-language chip. One line. | Arjun |
| A photograph measured: Priya's fixture set through ML Kit on the phone. | Priya, Rao (the phone) |
| The "taken as" caption on the plate. | Ira |
| The realme rehearsal on Tuesday afternoon, every beat timed with speech capture included; Vedant standing through Beat 1's wait. | Vedant, Rao |
| A warm, cool-phone row for each beat, from a run on the phone, replacing the throttled figures. | Rao |
| The escalation threshold ("well outside") and which template the printed report fires. | Rao |
| Telugu Part 1 confirmed, or Beat 5 cut. | Vedant |
| The platform voice data on the demo phone, hour one. | whoever holds the phone |
