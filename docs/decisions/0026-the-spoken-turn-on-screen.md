# 0026. What the person sees and hears from the end of their sentence to the end of ours

Date: 20 September 2026. Status: a specification for the screen, handed to Arjun; the
timings in it are the measured ones where they exist and are marked where they do not.
Written by Meera (spoken output). Nothing here changes a contract.

## Why this exists

`0014`'s rule: ten seconds of silence reads as a hang, ten seconds with continuous visible
feedback reads as work. That rule was written for the extraction stage. A conversational turn
(`0015`) is longer and has more stages, and it now has a voice at both ends: a spoken lead-in
the moment the intent is known (`ml/tts/SpokenLeadIn.kt`, `0019` addendum 3) and the answer
spoken at the end. This record says what the screen does in between, so the lead-in is not a
two-second voice followed by a blank wait.

The orchestrator already emits the stages; the screen renders them. Every event below exists
in `domain/Orchestrator.kt` today: `Progress(Stage.*)`, `NeedsIntent`, `Advice`, `Answered`,
`Failed`, `NotImplemented`, `Completed`.

## The numbers this is built on

| segment | measured | where |
| --- | --- | --- |
| extraction, LOG, prompt 221 tokens + 55 generated | 10.7 s | `0014`, on the phone |
| generation rate | 9-10 tok/s; prompt processing 38-52 tok/s | `0014` |
| a conversational ANSWER turn | **not yet measured**; Rao's batch today carries the first one | `logs/hw-report-conversational.txt` when it lands |
| the same with `AnswerLength.SHORT` (at most twenty words) | not measured; Priya is making it the default for spoken turns, which by the generation rate alone caps generation near seven seconds | `0024` |
| the lead-in phrase | 1-2 s of audio, by design | `0019` addendum 3 |
| spoken answer | the answer's length at the voice's rate; the voice's own synthesis time on the phone is unmeasured | `0019` |

Ask before tuning any duration below: Rao, the turn timing with and without the lead-in wired,
same prompt, so its cost to generation is a row and not a guess.

## The turn, stage by stage

Reading order is the person's: what they hear, then what they see. The screen never goes
still and never shows a spinner that means nothing; every state names what is happening.

| # | orchestrator event | they hear | they see | notes |
| --- | --- | --- | --- | --- |
| 1 | `Progress(RECORDING)` | nothing | a live level meter moving with their voice; their language's name; "listening" | the meter is the proof the mic is live (`0014`, spec 10.5) |
| 2 | `Progress(TRANSCRIBING)` | nothing | the meter freezes into a flat line the instant speech ends: acknowledgement at endpoint, before any text | this is the moment `AsrEvent.SpeechEnded` fires; do not wait for the transcript to change the screen |
| 3 | transcript arrives | nothing | their words, verbatim, in their script, above the progress area; they stay for the whole turn | never paraphrased; a wrong transcript is theirs to see and correct |
| 4 | `Progress(CLASSIFYING)` | nothing | the transcript, and a one-word state ("thinking") | short; the classifier is one model call |
| 4a | `NeedsIntent` | nothing | the transcript and the question "did you mean to log this, or ask about it?" with the intent choices; the turn ends here until they answer | no lead-in has been spoken yet, so nothing is talking over the question |
| 5 | intent known | **the lead-in**, one to two seconds, in their language, from the string table, per intent | the intent named as a heading ("Logging", "Answering", "Suggesting", "Recommending"); the transcript beneath it; the progress area starts a stage list | the lead-in is the first sound of ours; it starts here and not later |
| 6 | `Progress(EXTRACTING)` / `MATCHING_FOODS` / `COMPUTING` / `SAVING` (LOG) or `EVALUATING_RULES` / `RETRIEVING_FACTS` / `PHRASING` (ANSWER, SUGGEST, RECOMMEND) | the lead-in finishing, then silence | a stage list with the current stage marked and completed ones ticked: "extracting foods", "matching foods", "computing", "saving" or "checking your records", "finding facts", "writing"; an elapsed-time counter in seconds beside the current stage | the counter is the honesty device: a number that keeps moving is what makes a long wait read as work, and it is also what the presenter reads out if asked |
| 7 | `Advice` / `Answered` | nothing yet | the answer text, in full, in their language; the stage list collapses; the referral sentence, when there is one, set apart beneath the answer | text lands before speech starts, always: the words are on screen before they are heard |
| 8 | `Progress(SPEAKING)` | **the answer**, and the referral after it | the answer text with the currently spoken sentence emphasised if the engine reports sentence boundaries, otherwise the whole text; a stop control | a tap on stop calls `TtsEngine.stop()`; the text stays |
| 9 | `Completed` | nothing | the answer stays; the input returns to ready | |
| any | `Failed(reason)` | nothing, and any lead-in already playing is cancelled by `withSpokenLeadIn` | the transcript stays; the reason's sentence from the string table (`UnavailableReason` to text is the UI's mapping); never the `detail` | a failed turn is a sentence, not an empty screen |
| any | `NotImplemented(component)` | nothing | the explicit not-implemented state `0001` requires | never a placeholder answer |

## Rules the spec keeps

- **Text before speech, every time.** Step 7 precedes step 8 without exception, so a person who
  cannot hear the phone, or a judge reading over a shoulder, is never behind the voice.
- **Nothing spoken is not on screen.** The lead-in phrase is shown as it is spoken (a small
  line under the intent heading), because a voice saying something the screen does not show is
  the kind of thing a demo audience notices.
- **No spinner without a name and a number.** Every waiting state has the stage's name and the
  seconds counter. A spinner alone is forbidden by `0014`'s reasoning.
- **The transcript is never edited by us.** It is the person's evidence of what the phone heard.
- **The stop control stops speech only.** It does not cancel the turn; the answer is already on
  screen.
- **Language on screen follows the picker; the voice follows availability.** A language whose
  voice is absent on this phone (`RoutingTtsEngine` answers MODEL_NOT_LOADED) shows every state
  above with no sound and no error: silence is the degraded mode `0001` chose, and the screen
  does not apologise for it. The diagnostics screen can show the `detail`; the turn does not.

## Durations to tune, and with what

| duration | starting value | tune with |
| --- | --- | --- |
| how long "thinking" shows before a lead-in feels late | whatever the classifier takes; the lead-in starts the instant the intent is known | Rao's turn timing, the classify segment |
| whether a lead-in plays on a turn that will be short | always, today (`TtsFlags.SPOKEN_LEAD_IN`) | the SHORT-answer turn timing; if a whole turn is under about three seconds the lead-in is noise and the flag or a per-intent choice turns it off |
| the counter's threshold for appearing | from the first stage after the intent | none; it always shows |
| the gap between the lead-in ending and the answer starting | whatever generation takes; the screen carries it | the turn timing, with and without the lead-in |

## Not in this record

The screen's visual design, which is Arjun's; the strings, which are keys in Nila's table and
the reviewer's Telugu; and any figure about how long an ANSWER turn takes on the phone, which
does not exist yet and is not invented here.
