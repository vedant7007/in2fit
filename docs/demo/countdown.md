# The week, on one page

A live document. Nila updates it each evening from `COORDINATION.md`; anyone may correct a row
in their own name. Written Sunday 20 September 2026 at 20:20. Two deadlines:

- **Tuesday 22 September: submission.** Every field of the form, the deck PDF, the repository
  public, the prototype URL live. **[confirm the hour the form closes]**
- **Saturday 26 September: the battle.** Four beats, live, on a phone, in a hall.

Rows are things that, if they slip, cost a beat or a claim. Wishes are not on this page.
Owners are as the log shows them; **[unowned]** means nobody has said "mine".

## The risk above every other row

**The battle phone.** The deck says "the loaner iQOO". Every measurement in this repository is
on a realme RMX3780 (MediaTek, 8 cores, 7.6 GB). Nobody has held the iQOO. Meera asked Vedant
at 16:51 whether it can be in the team's hands before the 26th and called the answer
load-bearing: it decides the TTS ladder (platform voice first, or Piper), the voice-data
install, the model staging, the thermal behaviour, the launcher icon, the per-app language
menu path, and whether a single timing on the run of show applies. **If the answer is no, the
plan is: the realme is the demo phone, in the team's hands, staged and rehearsed; the iQOO, if
it is only handed over on the day, is not the phone the demo runs on.** That decision is
Vedant's, tonight, and it changes what Monday to Friday are for. **[Vedant, tonight]**

## Tuesday 22 September: submission

Anything not closed by **Monday night** is a risk and is named as one at the bottom of this
section.

| must be true | by | owner | state Sunday 20:20 |
| --- | --- | --- | --- |
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

## Saturday 26 September: the battle

| must be true | by | owner | state Sunday 20:20 |
| --- | --- | --- | --- |
| **The demo phone decided** (iQOO in hand, or the realme) | tonight | Vedant | see the risk above |
| The demo APK cut: `assembleDemoDebug`, permission check green, installed; **on the phone, long-press the IN2FIT title on About and read the pre-flight's last card: "Not in this build. The demo build carries no scripted feed."** If it shows a switch, the wrong APK is installed; reinstall | Friday, and again Saturday morning | Rao (install), whoever holds the phone (the card) | Arjun's two lines, 20:09, ruled into the checklist |
| Models staged on that phone, checksums verified: LLM, Hindi ASR + `tokens.txt`, the TTS voice; **models are wiped by an uninstall** (`0012`) | Friday | Rao | staged on the realme 19 Sep; not on any iQOO |
| Voice data installed: Speech Services by Google, Hindi (India) and English (India) voices, downloaded ON A NETWORK days before, then `TtsVoiceProbeTest` in airplane mode showing `hi-IN` and `en-IN` with `network=false` (Meera, 16:51) | Wednesday | whoever holds the phone; Meera confirms from the probe | not done on any phone |
| Piper or platform voice decided from that probe; if Piper, the espeak-ng source pointer recorded (`0005`) | Thursday | Meera, Vedant (the Hindi listener) | ladder in `0019`; English clips sent to Vedant 18:28, no answer yet |
| Profile pre-seeded: Hindi speech, English interface, bundled utensil defaults, a meal logged, the report scanned once as the fallback | Friday, and Saturday morning at the table | Rao (seed), presenter (the table) | `TalkViewModel` still sends `te`: Arjun, tonight |
| The wired speaker bought, plugged, `TtsVoiceProbeTest` "routed to" naming it, one turn heard from the back of a room; the wired headset or lapel mic bought and rehearsed once | Thursday | **[unowned: who buys them]**; Meera (the probe) | not bought |
| The printed lab report, two copies, ferritin and haemoglobin below their printed ranges, matching the scripted one the screenshots used | Wednesday | Vedant | not printed |
| Vedant's ten sentences recorded in the room and scored; the column (Hindi or English) chosen from the WER | Monday | Vedant (record), Jacob (score) | not recorded; synthetic Hindi 6.2% WER, English 19.1% |
| Push-to-talk on the Talk screen, or the decision to stay with open listening (Jacob's shape, Arjun's call) | Tuesday | Arjun | undecided |
| The Telugu packet back from a fluent speaker: Part 1 confirmed, or `values-te` out of the build (`0017`) and Beat 5 cut | Thursday | Vedant | reviewer not found |
| **The run of show rehearsed once end to end on the demo phone**, timed per beat, on a cool phone, in airplane mode, by the two people who will do it | Thursday, again Friday | Vedant (P), **[unowned: S]**, Rao (the phone) | never done; the budgets on the run of show are from a USB-powered, thermal-3 test run |
| The backup phone with the same build and the same staging | Friday | **[unowned]** | none named |
| The fallback video of the full sequence, recorded on the demo phone | Friday | **[unowned]**; see "no owner yet" | none |
| `0026`'s counter and stop control on the Talk screen (the playbook's "the counter is real time" line is untrue on the device without them) | Tuesday | Arjun | absent as of 17:10 |

## Day by day

**Sunday 20 (tonight).** Packet frozen at 22:00 and sent (Nila, Vedant). Vedant reads the
README and answers: public or not yet; the iQOO, yes or no; which Hindi voice he prefers from
the clips; the video, in or out. Arjun lands the `te` constant fix. Rao starts on the red test.

**Monday 21.** Rao: the safety test green, with the XML time in the log; the post-fix device run
that proves the first AdviseOnMeal is instant. Vedant: the corrected deck PDF onto this laptop;
the ten sentences recorded; the team slide; the form's confirm lines; the IITM PDF. Nila: the
deck re-read the hour the file lands; the diff; publish on the word. Jacob: WER on the
recordings, the column named. Arjun: push-to-talk decided; counter and stop control. Meera: the
voice-data step on the realme, and the iQOO if it exists.

**Tuesday 22.** Submission. Morning: Nila re-reads page 8 against that morning's test count,
string count and corpus; the URL opened in a private window and read as a judge. Vedant
submits. Afternoon: the first end-to-end rehearsal on the realme, whatever the iQOO's status,
so the wait at Beat 1 has been felt once by the person who will stand through it.

**Wednesday 23.** The report printed, two copies. Voice data installed on the demo phone, probe
run in airplane mode. Speaker and headset bought. If the iQOO is in hand: models staged, APK
installed, pre-flight card read, the four beats run on it once, every timing re-read.

**Thursday 24.** Full rehearsal on the demo phone: cool, airplane, the checklist run aloud by S,
each beat timed and written into the run of show beside the budget it replaces. Piper or
platform decided from the probe. The packet's answer in, or `values-te` stripped and Beat 5
cut. The playbook rehearsed: every branch twice.

**Friday 25.** Second rehearsal, on the phone as it will be carried, from a cold start of the
day: the checklist from row 1. The fallback video recorded on that phone in that state. The
backup phone staged. The bag packed against the checklist: two reports, speaker, headset,
cable, charger, both phones. Nothing new lands after Friday noon.

**Saturday 26.** The device checklist at the table, aloud. One spoken turn from the back of the
room before the judges arrive. The run of show, and nothing else.

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
| **The iQOO's arrival and staging**, if it comes | see the risk at the top | Vedant, Rao |

## How this page is kept

Each evening: every "state" column re-read against `COORDINATION.md` and the logs, the day's
row moved to done or to risk, tomorrow's row sharpened. A row nobody moved in two days is a
risk and is said so. This page never contains a number that is not in a log.
