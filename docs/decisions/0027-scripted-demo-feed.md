# 0027. A scripted feed for building the screens, and how it is kept from becoming a fake

Date: 20 September 2026. Status: accepted, ruled by Vedant ("build every screen the judges will
see, complete, behind a debug flag that feeds the UI scripted orchestrator events"). Owner:
Arjun (`ui/`). Number claimed in `COORDINATION.md` at 16:41 before this file was written.

## The tension, stated

Hard rule 6 (HANDOVER §6): a stub returns `NotImplemented`, never a plausible value, in any
build variant, at any time; binding a stub is how a not-implemented state quietly becomes a fake
one. The rule exists because a plausible number in a screenshot is how stub data becomes demo
data.

Six days out, the app has screens for four beats and no phone in front of the person building
them. The screens cannot be designed against the empty state, and the standing rule from 16:41
says a number on an emulator screenshot is never a result. So the feed exists, and this record
says exactly how it is kept from crossing the line the rule draws.

## What it is

`ui/demo/ScriptedOrchestrator.kt`, a second `Orchestrator` that emits the contract's own events
on a timer, and `ui/demo/DemoFeed.kt`, the switch and the little state the script keeps.

**What is REAL inside it.** The rules engine is `DefaultRulesEngine`, unchanged. The trigger
sentence on screen is what the real engine says about the scripted plate and the scripted
report, rendered by the real `TriggerText`; beat 4's changed sentence and changed input digest
are the engine's own (`ScriptedOrchestratorTest` asserts both). The figure lines are rendered by
the real `ContextText`. The transcript is one of the presenter's own sentences from
`data-authoring/demo-utterance-set.csv` (ids 2, 7, 8, 9). The lab report's fields come out of
the real `LabReportExtractor` over scripted lines.

**What is SCRIPT.** The extraction (which foods the sentence names), the plate's per-item
figures (plausible per-100 g values, not database rows), the model's phrasing, and the report's
lines. The scripted phrasing obeys the numeric guard's rule by construction: the only number in
its answer is taken from the same totals the diary line shows.

## The five guards

1. **Never injected.** `AppModule` provides the real orchestrator and nothing else. The scripted
   one is constructed inside the two view-models and chosen only when `DemoFeed.enabled` is
   true, per turn.
2. **Off by default, process-wide.** `DemoFeed.enabled` starts false on every launch and is a
   `StateFlow` nothing persists.
3. **Reachable only by a long-press.** The switch lives on the pre-flight screen, which opens
   only from a long-press on the About title. Nothing on any screen says so.
4. **A banner on every tab while it is on.** `demo_banner`: "SCRIPTED DEMO FEED. Nothing on this
   screen is a real result." Red, above everything, on every screenshot taken with it on.
5. **The 16:41 rule.** UI screenshots come from the emulator; every number on a screenshot comes
   from the phone; never the reverse. A screenshot with the banner is a design artefact.

## What it produced today

`docs/screenshots/2026-09-20-shell/`: `beat1-turn-in-progress.png` (the 0026 stage list with
ticks and the counter, transcript pinned), `beat1-logged.png`, `beat2-answer.png` (the person's
own lines above the model's sentence, the shape of Vedant's figures-first ruling),
`beat3-fields.png` (the real extractor over the scripted report), `beat4-advice-after-report.png`
(the engine's own sentence naming the value, the printed bound and the date, and the candidates
re-ranked), `recommend.png`, `preflight.png`. All carry the banner except the pre-flight.

## What the feed cannot script, and who owns it

A script can only emit what the sealed interface has. Three events the 0026 screen needs are
not in the contract and are asked of Rao (16:41): `OwnFigures(lines)` early in ANSWER and
RECOMMEND, `IntentKnown(intent, leadIn)` after classification, and a way to stop speech. Until
they land, the answer card shows `Answered.figures` above the sentence, which arrives with it.

## Revisit

Delete the feed the day the phone runs all four beats end to end and the screenshots are
retaken from it. It is scaffolding for one week and the record says so.
