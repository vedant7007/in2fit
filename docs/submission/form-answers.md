# Submission form: proposed answers

Drafted 20 September 2026 by Nila, from the repository, not from the deck, so Vedant edits at
midnight rather than composes. Every line marked **[confirm]** is his to check or decide; nothing
below invents a team detail, a date, or a claim the repository does not support. Character counts
were measured by script and are given beside each text so the right one can be pasted.

## Idea title

    IN2FIT

If the box wants a phrase, **[confirm]**:

    IN2FIT: an offline health assistant that hears your meal, reads your lab report, and joins them

(95 characters)

## Description

**Short, for a box that accepts about 60:**

    An offline Android assistant: speak your meal, scan your lab report, get advice that cites its data.

(100 characters; without the last clause, "An offline Android assistant: speak your meal, scan your lab report.", it is 68)

**Long, for a box that accepts about 300:**

    IN2FIT is an Android health assistant that runs entirely on the phone: you say what you ate, in Hindi, Telugu or English; on-device speech recognition and a local language model extract the foods; public-domain USDA data supplies the numbers; a rules engine decides what to say. Point the camera at a printed lab report and later advice changes because of it. The demo build has no internet permission.

(402 characters. If the box is a hard 300: the first sentence alone is 278; dropping ", in Hindi, Telugu or English" from it gives 249.)

Sources: `HANDOVER.md` §1, `docs/decisions/0001`, `0002`, `0004`, `0015`, `0018`.

## Prototype URL

We are a mobile app with no hosted build, so the strongest link is the repository, and the answer
should say what a reader finds there.

    https://github.com/vedant7007/in2fit

Created 20 September 2026 at 19:41 IST, PRIVATE, default branch `master`, pushed at `83e7375`
(141 commits, every author and committer Vedant Manmath Idlgave, no attribution anywhere in
the history). **It stays private until Vedant answers the six-voices question in
`docs/submission/publish-checklist.md` §7a; it goes public before submission and not before.**
A private link in the form is a dead link to a judge, so the form is filled only after that.

Proposed text beside the link, if the form allows one:

    This is a mobile app, not a web page, so the link is the repository. A reader will find: screenshots of every demo beat (docs/screenshots), 33 decision records that state each choice with its evidence and its known limits (docs/decisions), the run of show and an audit of our own submission deck against the code (docs/demo), and a status file in which every number traces to a build log or a device report. The repository has a README that points to each of these.

(465 characters.) The README exists as of this commit; it was written for exactly this click.

Not proposed: a recorded video link. Spec 7.4 asks for one as the on-stage fallback; it does
not exist yet **[confirm whether one will be recorded before the form closes]**.

## The two proficiency dropdowns

Currently reading "No experience". **[confirm]: these are Vedant's to answer honestly about
himself; no level is proposed here.** What the repository shows is only that the project was
built from its first commit at 02:28 on 19 September 2026 to its 115th on 20 September, in
Kotlin, C++ and Python, and that is not a proficiency level.

## Prior builds

**[confirm the field's meaning.]** If it asks whether this idea was built before: no. The
repository begins on 19 September 2026 with the project scaffold; the specification it
implements (`docs/spec.md`) was written for this hackathon, under an earlier working name that
was changed twice for name collisions (`docs/decisions/0001`, `0015`). No earlier version was
shipped, published or entered elsewhere.

If it asks what has been built so far: the four demo beats in the app's shell, as screenshots
from an emulator (`docs/screenshots/`); the language model, speech recognition, the camera
reader and the food database running on a handset, each with a device report behind it
(`STATUS.md`, `docs/decisions/0011` to `0026`); 315 automated tests, nine of which are red on
purpose until a named fix lands (`docs/demo/deck-audit-2026-09-20.md`).

## The standout text

This is the answer that matters. The honest one is in our own audit: we measured, we found our
own claims wrong, and we corrected them before anyone asked. Two lengths; the field's limit is
**[confirm]**.

**Full (1,167 characters):**

    Every number in this project traces to a file a machine wrote. When we audited our own submission deck against the repository this afternoon, seven claims did not survive: a model-load time no log supported, a latency figure that credited the model with work the database does afterwards, a test count that was green on the slide and red on purpose in the code, and "48 strings translated into Telugu" that were 136 strings drafted by a machine and marked unreviewed. We corrected all seven, and the corrections were then checked again and corrected again. The app is built the same way. The language model may explain and phrase; it may never state a number it was not handed, and a test renders every health sentence and fails on any digit the evidence did not supply. The demo build carries no internet permission, and the build fails if any dependency adds one back. India's own food tables could not be licensed for a shipped product, so we read the licence, took the block seriously, and rebuilt on public-domain USDA data with recipes we authored and assert at import. What a judge sees on stage is what the logs say. If it is not in a log, we do not claim it.

**Short (643 characters):**

    Every number here traces to a file a machine wrote. When we audited our own deck against the repository, seven claims failed: a load time no log supported, a latency figure crediting the model with the database's work, a test count green on the slide and red on purpose in the code, and "48 strings translated" that were 136 machine drafts marked unreviewed. We corrected all seven before anyone asked. The app is built the same way: the model never states a number it was not handed, the demo build has no internet permission and the build fails if a dependency adds one, and every food figure comes from public-domain data looked up by code.

Sources: `docs/demo/deck-audit-2026-09-20.md`; `RulesEngineTest` (the digit test, `db099ca`);
`app/build.gradle.kts` (`verifyDemoDebugPermissions`); `docs/decisions/0002`, `0008`.

## The disclosure checkbox

**[confirm the checkbox's exact wording]**; it decides which of the following it covers. What
the repository discloses, all from `docs/decisions/0005` and its register:

Third-party models and data shipped in the app, with licences:

| component | licence |
| --- | --- |
| Qwen 2.5 1.5B Instruct (Alibaba Cloud), through llama.cpp (MIT) | Apache-2.0 |
| IndicConformer Telugu and Hindi speech recognition (AI4Bharat) | MIT |
| English speech recognition: NVIDIA NeMo FastConformer | CC-BY-4.0, attribution owed |
| sherpa-onnx, the speech runtime (k2-fsa) | Apache-2.0 |
| Piper Telugu voice `te_IN-padmavathi` (rhasspy/piper-voices) | CC-BY-4.0, attribution owed |
| Piper Hindi voice `hi_IN-pratham` | **CC-BY-NC-SA-4.0, non-commercial**; in the register; not on the demo path |
| espeak-ng, the phonemiser, code and data | **GPL-3.0-or-later, copyleft**; duties attach on distributing the APK; repository licensed Apache-2.0 to be compatible |
| Google ML Kit text recognition | Google's terms; on-device; no network permission |
| USDA FoodData Central, SR Legacy April 2018 and Foundation Foods | public domain, CC0 |
| The app's own code | Apache-2.0, copyright Vedant Manmath Idlgave |

Excluded on licence grounds and NOT in the app: IFCT 2017, INDB, the `ifct2017` package, and
any voice whose licence could not be read verbatim (`0005`).

If the checkbox covers machine-generated content: every Telugu interface string in the app was
drafted by a machine, is marked so in the file, and is under review by a fluent speaker
(`docs/decisions/0017`). The English strings are the source language, not a translation.

The non-commercial register exists because the project is ruled a non-commercial student
project (`0005`); if the form asks about commercial intent, that is the ruling to state.

## Not answered here, because the repository cannot

- Team member names and roles: the deck's page 9 still carried `[Name]` placeholders at 14:07.
- The date the app was first run on a phone for the form's purposes: 19 September 2026 is when
  the first device report was written (`logs/hw-report-run2.txt`), **[confirm]** if the form
  asks.
- Anything about the loaner iQOO device: every measurement so far is on a realme RMX3780.
