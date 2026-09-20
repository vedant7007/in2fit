# The week, on one page

A live document. Nila updates it each evening from `COORDINATION.md`; anyone may correct a row
in their own name. Written Sunday 20 September 2026 at 20:20. Two deadlines:

- **Tuesday 22 September: submission.** Every field of the form, the deck PDF, the repository
  public, the prototype URL live. **[confirm the hour the form closes]**
- **Saturday 26 to Sunday 27 September: the Hyderabad City Battle, a 30-hour build event on
  site**, not a demo day. The loaner iQOO is handed to selected teams on the day the event
  starts, on site; early collection is impossible. The demo runs on it, at the end of those
  thirty hours. Winning Hyderabad leads to a **Grand Finale in Bengaluru, 9 to 11 October**;
  the 27th is not the finish.

Rows are things that, if they slip, cost a beat or a claim. Wishes are not on this page.
Owners are as the log shows them; **[unowned]** means nobody has said "mine".

## The reality about the phone, and hour one of thirty

**The demo runs on the loaner iQOO** (ruled by Vedant, 20 Sep 20:25). **The iQOO cannot be
collected early**: it is handed to selected teams only on the day the event starts, on site.
Every measurement in this repository is on Vedant's realme RMX3780; nobody will have held the
iQOO before hour one. So the whole "handset arrival" list is an **HOUR-ONE LIST, run on site
with the venue network available, before airplane mode goes on for the demo**, in this order.
Meera's steps are right; only the timing moved. Thirty hours, not twenty tense minutes at a
table, is the good news.

| # | in hour one, on site, on the venue network | who | evidence it worked |
| --- | --- | --- | --- |
| 0 | **First five minutes, no cable, on whatever network the hall has (Meera, 20:39)**: Settings → (System →) Languages & input → Text-to-speech output → Speech Services by Google → gear → Install voice data → English (India) and Hindi (India). If it cannot be installed there and then, the TTS decision is made in those five minutes, not on stage: the bundled English voice Vedant rejected is the insurance path (`0019` addendum 10). | whoever holds the phone; Meera's step | both voices listed as installed |
| 1 | **The vivo/iQOO ROM's "Install via USB"** and "USB debugging (Security settings)" switched on. Some of those ROMs want a vivo account and a network for this: the venue network is there, and this is why it is first. Developer options via Build number ×7, USB debugging on, USB mode "File transfer", the RSA prompt accepted. | whoever holds the cable | `adb devices` shows `device`, not `unauthorized` |
| 2 | Free space: at least 2 GB (`adb shell df /sdcard`). | same | the number, written down |
| 3 | `adb install -r app-demo-debug.apk` (the `assembleDemoDebug` build), opened once. | same | install "Success"; the app opens |
| 4 | **The models: `powershell -File tools\stage-models.ps1`.** Chunked push with per-chunk retry and an on-device `sha256sum`, written from `0012`; 1.6 GB; at the realme's measured 6.3 MB/s about 4.3 minutes of pure transfer if nothing drops. Rao verifies the script's three tunables in his next realme cable session, before the day. | same; Rao (the verification) | the script's `OK ... sha256` line per file and its total seconds |
| 5 | **The pre-flight card**: long-press the IN2FIT title on About; the last card must read "Not in this build. The demo build carries no scripted feed." | same | read off the device |
| 6 | Voice data checked once more if step 0 was interrupted; otherwise nothing. | same | step 0's evidence |
| 7 | **Airplane mode ON, Bluetooth icon absent. Then the probes**: `TtsVoiceProbeTest`'s "-- ENGLISH_INDIA" block must show an `en-in-x-*-local` line with `network=false` and a written WAV with an RTF, and the `hi-IN` voice likewise; `OrchestratorDeviceTest` once for the four beats' timings on THIS phone. | Rao (or whoever holds the cable, from the run of show) | the probe reports; every timing on the run of show re-read against them |
| 8 | **A full rehearsal on the iQOO**, the run of show end to end, the device checklist aloud. | Vedant (P), S | timed per beat, written beside the run of show's realme figures |

After 8 (step 0 first, because it needs no cable and its failure changes the plan), the iQOO is the demo phone in every sense, and the rest of the thirty hours is the
build event. **If step 1 fails and the venue network cannot satisfy the ROM**, that is the one
failure the plan has no answer for on the iQOO; the organisers are asked in hour one, not at
hour twenty-nine.

**The realme** is the **rehearsal device and the fallback-video device** (Vedant, 20:45):
every rehearsal this week is on it, the fallback video is recorded on it, it is charged the
night before and it is in the bag on the 26th, staged with the same build (spec 7.4), so the
run of show can be rehearsed on site while the iQOO is being staged. Whether it may stand in
front of the judges if hour one fails on the iQOO is Vedant's to say; the plan does not assume
it, and the 20:25 line "the realme comes with him as the backup" is superseded only as far as
he says so.

## Tuesday 22 September: submission

Anything not closed by **Monday night** is a risk and is named as one at the bottom of this
section.

| must be true | by | owner | state Sunday 20:20 |
| --- | --- | --- | --- |
| **BLOCKING: the rules on pre-existing code.** The event is 30 hours of building on site and we arrive with a working app. Most hackathons permit prior work when disclosed, and the form's disclosure checkbox is probably exactly that; "probably" is not a rule. **Action: read the rules, or email the organisers, tonight.** The answer decides what the form says and what the 30 hours are for. | tonight | Vedant | unread; `docs/submission/form-answers.md` lists what the repository discloses |
| The clinical-judgement referral test green on `master` (the safety path; red since 17:48) | Monday morning | Rao, copy Priya | **RED**; stop-the-line since 19:45; the repository does not go public while it is red unless Vedant publishes with a README line naming it |
| Vedant has read the README as a stranger and said "public" out loud | Monday | Vedant | reading now; README ready; repository private at `8b9b4ba` |
| `gh repo edit vedant7007/in2fit --visibility public` | on his word | Nila | not before |
| The corrected deck PDF on this laptop, re-read page by page against the repository, third pass appended to the audit | Monday | Vedant (the file), Nila (the read) | the PDF has not landed; Downloads still holds the 14:07 original |
| Deck page 8 numbers re-read against the log the morning of submission (test count, string count, corpus counts move by the hour) | Tuesday morning | Nila | numbers move daily; 363 tests / 171 strings / 93-626-52 at 19:17 |
| Team slide: the three `[Name]` placeholders filled | Monday | Vedant | placeholders in the 14:07 deck |
| The form: every field from `docs/submission/form-answers.md`, the `[confirm]` lines decided (proficiency levels, "prior builds" meaning, standout limit, checkbox wording, the video) | Monday night | Vedant | drafted; URL filled; repository private |
| Prototype URL live: the public repository, README first | Tuesday, before the form | Nila on Vedant's word | see row 3 |
| The Telugu packet frozen and sent | tonight 22:00 | Nila (freeze), Vedant (send) | 171 lines, 0 missing; freeze holds; the reviewer is not found yet |
| IITM licence PDF diffed against the fetched text, recorded in `0005` | Monday | Vedant (the PDF), Nila (the diff) | PDF not landed; nothing ruled from the text |

**Risks if not closed Monday night:** the red safety test (a public repository with a red safety
test, or a README apologising for one); the deck re-read (submitting a deck nobody checked
against the code since 14:07); the team slide; the form's `[confirm]` lines answered at midnight.

## Saturday 26 to Sunday 27 September: the build event, and the demo at the end of it

| must be true | by | owner | state Sunday 20:20 |
| --- | --- | --- | --- |
| **Hour one on site, steps 0 to 8 above, in order, on the venue network, before airplane mode** | 26 Sep, hour one | whoever holds the cable; Rao; Vedant | the list exists; `tools/stage-models.ps1` exists and is unverified on a phone |
| **`tools/stage-models.ps1` verified by Rao on the realme**, its three tunables confirmed, so the person holding the cable on the 26th runs a script that has run before | Wednesday | Rao | written 20 Sep from `0012`; not yet run |
| **The realme: rehearsal device and fallback-video device**, staged, charged the night before, in the bag on the 26th | Friday | Rao (staging), Vedant (the bag) | staged as of 19 Sep |
| The demo APK cut on Friday: `assembleDemoDebug`, permission check green, the file copied to the laptop that goes on site (and to a second place: a USB stick), its sha256 written in the log | Friday | Rao | not cut |
| On the realme this week, the same steps 3 to 7 as a dry run of hour one, so that the only new thing on site is the phone | Wednesday | Rao; whoever will hold the cable on the 26th | not done |
| Piper or platform voice decided from that probe; if Piper, the espeak-ng source pointer recorded (`0005`) | Thursday | Meera, Vedant (the Hindi listener) | ladder in `0019`; English clips sent to Vedant 18:28, no answer yet |
| Profile pre-seeded: Hindi speech, English interface, bundled utensil defaults, a meal logged, the report scanned once as the fallback | Friday, and Saturday morning at the table | Rao (seed), presenter (the table) | `TalkViewModel` still sends `te`: Arjun, tonight |
| The wired speaker bought, plugged, `TtsVoiceProbeTest` "routed to" naming it, one turn heard from the back of a room; the wired headset or lapel mic bought and rehearsed once | Thursday | **[unowned: who buys them]**; Meera (the probe) | not bought |
| The printed lab report, two copies, ferritin and haemoglobin below their printed ranges, matching the scripted one the screenshots used | Wednesday | Vedant | not printed |
| Vedant's ten sentences recorded in the room and scored; the column (Hindi or English) chosen from the WER | Monday | Vedant (record), Jacob (score) | not recorded; synthetic Hindi 6.2% WER, English 19.1% |
| Push-to-talk on the Talk screen, or the decision to stay with open listening (Jacob's shape, Arjun's call) | Tuesday | Arjun | undecided |
| The Telugu packet back from a fluent speaker: Part 1 confirmed, or `values-te` out of the build (`0017`) and Beat 5 cut | Thursday | Vedant | reviewer not found |
| **Tuesday afternoon: the first end-to-end rehearsal on the realme, whatever the iQOO's status, with Vedant standing through Beat 1's wait himself.** Nine seconds in a log and nine seconds on a stage are different lengths; he finds that out on Tuesday, not Saturday. | Tuesday afternoon | Vedant (P), Rao (the phone) | ruled 20:30; not done |
| **"Dal · 1 katori · taken as 180 g" on the plate card, and an unstated amount never marked stated**: the model writes no quantity it was not given (Rao); a unitless quantity on an authored dish is an assumed serving with the band saying so (`weigh()`, Priya or Rao); the card line (Ira, `plate_unit_taken_as`). **If it is not on screen by Thursday, the sentence comes off slide 6** (Vedant, 21:12) and Beat 1's second passage is struck. | Thursday | Rao, Priya, Ira | resolver half DONE `425691d` (22:57): the band reads Rough and the assumed katori is on the item; the card line and the model change are open |
| **The run of show rehearsed end to end on BOTH phones**, timed per beat, cool, in airplane mode, by the two people who will do it | Thursday on the iQOO if it is in hand; Friday again | Vedant (P), **[unowned: S]**, Rao (the phones) | never done; the run of show's figures are from a USB-powered, thermal-3 test run on the realme |
| **The deck's final pass**: after Rao's safety fix is green and the first-tap AdviseOnMeal figure exists on the device, one edit by Vedant, one re-read by Nila against the repository, then frozen for submission | Monday night | Vedant (the pass), Rao (the two inputs), Nila (the re-read) | held; the corrected PDF is not on this laptop |
| The backup phone with the same build and the same staging | Friday | **[unowned]** | none named |
| The fallback video of the full sequence, recorded on the demo phone | Friday | **[unowned]**; see "no owner yet" | none |
| `0026`'s counter and stop control on the Talk screen (the playbook's "the counter is real time" line is untrue on the device without them) | Tuesday | Arjun | absent as of 17:10 |

## Day by day

**Sunday 20 (tonight).** Packet frozen at 22:00 and sent (Nila, Vedant). **Vedant reads the
rules on pre-existing code or emails the organisers.** He reads the README and answers: public
or not yet; which Hindi voice he prefers from the clips; the video, in or out. Arjun lands the
`te` constant fix. Rao starts on the red test. Beat 1 is the two-food sentence from now on
(ruled 20:30).

**Monday 21.** Rao: the safety test green, with the XML time in the log; the post-fix device run
that proves the first AdviseOnMeal is instant. Vedant: the corrected deck PDF onto this laptop;
the ten sentences recorded; the team slide; the form's confirm lines; the IITM PDF. Nila: the
deck re-read the hour the file lands; the diff; publish on the word. Jacob: WER on the
recordings, the column named. Arjun: push-to-talk decided; counter and stop control. Meera: the
voice-data step on the realme, and the iQOO if it exists.

**Tuesday 22.** Submission. Morning: Nila re-reads page 8 against that morning's test count,
string count and corpus; the URL opened in a private window and read as a judge. Vedant
submits. **Afternoon, a row in its own right: the first end-to-end rehearsal on the realme,
whatever the iQOO's status, Vedant standing through Beat 1's wait himself.**

**Wednesday 23.** The report printed, two copies. Speaker and headset bought. **The dry run of
hour one on the realme**, by the person who will hold the cable on the 26th: `stage-models.ps1`
end to end with Rao watching and correcting its tunables, the voice data installed on a
network, the probes in airplane mode. Whatever that dry run finds is fixed by Friday, because
on the 26th there is no laptop time to fix a script.

**Thursday 24.** Full rehearsal on the realme: cool, airplane, the checklist run aloud by S,
each beat timed and written into the run of show beside the budget it replaces. Piper or
platform decided from the probe. The packet's answer in, or `values-te` stripped and Beat 5
cut. The playbook rehearsed: every branch twice.

**Friday 25.** Second rehearsal on the realme from a cold start of the day: the checklist from
row 0. The fallback video recorded on the realme in that state. The demo APK cut, hashed,
copied twice. The bag packed against the checklist: two reports, speaker, headset, two cables,
charger, the realme, the laptop with `data-sources/models` and the APK on it, a USB stick with
both. Nothing new lands after Friday noon.

**Saturday 26, hour one.** The nine steps above, step 0 first, on site, on the venue network, in order. Then
the thirty hours are what the rules say they are for. **The demo**, at the end: the device
checklist aloud, one spoken turn from the back of the room, the run of show, and nothing else.

**October 9 to 11, Bengaluru**, if Hyderabad is won. Nobody treats the 27th as the finish.

## On watch: the deck's one conditional claim

| risk | the facts | the check | if it fails |
| --- | --- | --- | --- |
| **The "taken as" caption on the plate.** The deck's phone mockups on the product and moment slides show a plate with "one katori, taken as 180 g" beside the dal; hard problem 01 says the assumed amount "travels beside" the total. **At `67a6dec` the app does not render it.** `TalkScreen.ItemLine` shows "dal: 1 katori" and the band word; `plate_unit_taken_as` is in the string table and no screen references it. The resolver half is right since `425691d` (`0035`): the assumed quantity and unit are on the item, QUANTITY_INFERRED, band Rough. The caption is Ira's card. | Nila watches, does not chase: `git grep plate_unit_taken_as origin/master -- '*.kt'` at every update of this page, and the moment Ira lands the plate UI, one line to Vedant: rendered, yes or no. | **Not rendered by the evening of Thursday 25 September: Vedant cuts the caption from the mockups** rather than show a screen that does not exist (ruled 23:17, 20 Sep). |
| **Beat 3's photograph: the deck's claim 3.** The parse MATCHES (the values land beside their printed ranges, the trigger fires); the PHOTOGRAPH is NOT MEASURED: ML Kit has one observation ever, on a single haemoglobin row, and that step is Beat 3 of the live demo. Priya is building the fixture set. | Nila watches, does not chase: a photograph result in a log (Priya's fixtures through ML Kit on the phone, Rao's cable) is the evidence; the moment one exists, one line to Vedant. | **No photograph result by the evening of Thursday 25 September: Vedant softens the moment slide before the battle** (ruled 00:13, 21 Sep). |

## Decided NOT to do, so nobody rediscovers it on Friday night

| not doing | ruled | where |
| --- | --- | --- |
| Utensil calibration (`CalibrateUnit`): the profile is pre-seeded with the bundled defaults; the presenter does not offer to calibrate | Rao, 18:40, Vedant's ruling | `COORDINATION.md`, run of show Beat 1 |
| KV-cache prefix reuse: four seconds against a 688-token prompt, native work with real risk; after the battle | Rao, 18:40 | `COORDINATION.md` |
| Hindi (or Telugu) OUTPUT: the answer is English on screen and aloud whatever language is spoken; `values-hi` is empty by design | Meera, 18:07; audit | `0019` addendum 7, deck audit |
| Packaged-label OCR (`0023`): dropped, its probe asks withdrawn | Vedant | `0023`, `c2eb7cc` |
| Telugu as the demo speech language: the presenter does not speak it; Hindi or Indian English, English interface, Telugu as the toggle | Vedant | `0022`, `0017`, run of show |
| A second ASR engine, or one model for mixed speech: one IndicConformer checkpoint per selected language; Whisper measured and unusable for Telugu | Vedant via `0022` | `0022-one-model-for-mixed-speech`, `0005` |
| The dish classifier before the probes: kept, ordered after Rao's beat-3 probe and the photographed-report probe; Arjun builds nothing for it until then | Vedant | `0022-dish-classifier-sourcing` |
| Activity tracking from sensors, exercise form checking: never built; off the deck | audit | deck audit |
| The splash screen before all four beats run on the handset: decided, not built until then, costs nothing if it never happens | Vedant, via Arjun 20:09 | `COORDINATION.md` |
| Renaming the package, classes or `master`; rewriting any history | Vedant | `0015`, HANDOVER |

## No owner yet

| thing | why it matters | proposed |
| --- | --- | --- |
| **The fallback video** of the full sequence (spec 7.4) and the storytelling video Vedant mentioned early on (spec 7.5). `form-answers.md` marks a video `[confirm]`. | If the live device dies twice the video IS the demo; without one there is no third try. And it cannot be recorded until the beats run end to end on a phone. | Vedant decides tonight: in scope with Friday against it, or on the not-doing list. Not to stay unsaid. |
| **The second person at the table (S)**: holds the backup phone, the report, the checklist, mirrors the screen | The run of show is written for two people | Vedant names them |
| **The wired speaker and the wired headset**: who buys, by Wednesday | Meera's probe needs the hardware to name | Vedant names a buyer, or does it |
| **The backup phone** | spec 7.4 | which phone, who stages it |
| **The person who holds the cable in hour one** and runs steps 0 to 8; they dry-run it on the realme on Wednesday | hour one is theirs | Vedant names them |

## How this page is kept

Each evening: every "state" column re-read against `COORDINATION.md` and the logs, the day's
row moved to done or to risk, tomorrow's row sharpened. A row nobody moved in two days is a
risk and is said so. This page never contains a number that is not in a log.
