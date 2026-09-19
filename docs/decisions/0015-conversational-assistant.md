# 0015. IN2FIT is a conversational assistant, not a logging form

Date: 20 September 2026. Status: accepted, ruled by Vedant.

**THIS RECORD SUPERSEDES `docs/spec.md` WHERE THEY CONFLICT.** The spec is the original Swasth
document, written before anything had run. It is still the authority for everything not listed
here, and its section numbers are still what the code cites. Read it, then read this, then `0014`.

Precedence, when two documents disagree: **the code, then this record, then `0014`, then the
spec.**

## The change

Speech routes to one of four intents, not to one logging pipeline:

| Intent | Example |
| --- | --- |
| LOG | "I had two rotis and a katori of dal" |
| ANSWER | "how much protein was in my lunch?" |
| SUGGEST | "I'm having rice and palak paneer, what should I add?" |
| RECOMMEND | "I have anaemia, what should I eat for iron?" |

The last two are first-class requests. They are not a follow-up to logging a meal, and they do not
require a lab report to have landed first.

## The safety line, restated narrowly

The old line was drawn around a fixed catalogue of sentences. The new one is drawn around numbers
and diagnosis instead, and it is narrower:

**The model MAY** explain, guide, suggest swaps, answer nutrition questions and encourage. That is
the product.

**The model MAY NOT** state a number it was not given, name a condition the user has not declared,
diagnose, or prescribe.

- A lab value reads "your last report shows iron below the range printed on it". Never "you have
  anaemia".
- Serious matters get a doctor referral **alongside** help, not instead of it.
- Numbers still come from the database. `NumericGuard` stays in force, unchanged.

## SPEC SECTIONS THIS AFFECTS

Cited as the code cites them; the code's own wording is quoted where the conflict is sharp.

| Section | As the codebase uses it | Status now |
| --- | --- | --- |
| **15.1** | "escalate, do not handle": a value far outside range produces a referral and NO suggestion | **SUPERSEDED.** Referral now comes alongside help |
| **11.5** | the model extracts and explains, never computes a number | **HOLDS**, and is the load-bearing rule |
| **11.6** | the two prompt paths stay separate | **AMENDED.** See the third path below |
| **8.1** | the orchestrator routes and no pipeline talks to another | **HOLDS**, but it routes by intent now |
| **4.3** | suggestions constrained to a bundled ingredient list | **WIDENED** by the knowledge-facts file |
| **13.4** | quantity is never silently guessed | **HOLDS**, and calibration serves it |
| **5.x** | the four demo beats | **RE-FRAMED.** Beats are a demo script, not the intent model |
| **10.2** | language is a parameter, never detected | **HOLDS.** Code-mixed audio, one selected language |
| **10.5** | the 3.5 s round-trip budget | already superseded by `0014` |

## FIVE CONTRACT CONFLICTS, AND WHAT HAPPENS TO EACH

`0001` froze these files. Three of the five need that freeze broken deliberately.

### 1. `Severity.ESCALATE` forbids exactly what the new rule requires

`domain/RulesEngine.kt` says, verbatim:

> `[ESCALATE]` exists to satisfy spec 15.1's "escalate, do not handle": a value far outside its
> range produces "this is worth showing to a doctor" and NO dietary suggestion at all. An
> implementation that emits swaps alongside an ESCALATE severity has broken the safety contract.

The new rule is the opposite: a referral **alongside** help. Two tests encode the old behaviour
and will fail — `a value far outside its range escalates and emits no dietary suggestion` and `an
escalation never carries suggestions even when other rules would have fired`.

**Resolution:** ESCALATE stops meaning "suppress all help" and starts meaning "a referral is
mandatory in this response". The referral becomes a required component rather than an exclusive
one. The two tests are rewritten to assert the referral is PRESENT, which is the property that
actually protects the user; deleting them without a replacement is not acceptable.

### 2. `UserIntent` is shaped like the demo, not like the product

The seven variants are named for beats: `LogMealByVoice`, `QueryHistoryByVoice`, `ScanLabReport`,
`AdviseOnMeal`, `CorrectValue`, `ScanPackagedLabel`, `CheckExerciseForm`.

**Resolution:** restructured around LOG / ANSWER / SUGGEST / RECOMMEND, with the capture-shaped
ones (`ScanLabReport`, `ScanPackagedLabel`, `CheckExerciseForm`, `CorrectValue`) kept as they are,
because they are input modes rather than conversational intents. Done with `Orchestrator`, after
`ModelArbiter`.

### 3. `LlmEngine` says two paths and nothing else

> Two paths, and nothing else. ... A third path cannot be added without changing this interface,
> which goes to the integrator.

ANSWER and RECOMMEND are neither. They are not extraction, and they are not phrasing an
already-decided result: the answer is composed from retrieved knowledge rows.

**Resolution:** it goes to the integrator, and the integrator is me, so this is recorded as a
deliberate amendment rather than a drift. A third path is added **with the same defences**: its
inputs are rows from the knowledge-facts file and figures from the database, and every number in
its output must appear in that permitted set or `NumericGuard` fails the response. The narrow
native surface in `0010` does not change — three JNI functions, no general completion endpoint.

### 4. "No nutrient preference means no suggestions at all"

`0006` records this, and `RulesEngineTest` asserts `with no rule fired there are no suggestions at
all`. It was right when suggestions were a side effect of a lab value. It is wrong when SUGGEST
and RECOMMEND are things a person asks for directly.

**Resolution:** the rule survives for UNPROMPTED suggestion — the app still does not volunteer
advice it has no basis for. It does not apply to an explicit request. A RECOMMEND with no lab
data answers from declared conditions and the knowledge file, and says what it is working from.

### 5. Per-household dish variants have nowhere to live

`unit_conversions` and `context_foods` are the only override tables, and both are keyed for other
purposes. There is no table for "this household's sambar".

**Resolution:** a new override table, owned by whoever builds the recipe-variant slice. It follows
the same rule as the other two: **user overrides only, never shipped defaults**, so a bundled data
refresh cannot destroy a correction and a correction outlives a refresh (`0001`).

**Utensil calibration is NOT a conflict.** `unit_conversions` already exists for exactly this, as
user-overrides-only, and `resolveUnit` already returns `isDefaultConversion` so a calibrated
portion can outrank a shipped default and carry a better confidence reason. It fits the existing
design without an amendment.

## What does not change

- `NumericGuard`, in force on every generated sentence.
- Three-state `NutrientValue`. Absent is still not zero.
- Computed confidence, worst ceiling wins, no percentage anywhere.
- The permission whitelist and the offline guarantee.
- No-data items still refuse by name and never resolve to a substitute.
- The knowledge-facts file follows the food database's discipline: **every claim traces to a row**,
  and a claim with no row does not ship. It gets a `DataSource` constant, which `0001` anticipated
  as "a new constant here plus new rows, not a change to any interface".

## What this record does not settle

The knowledge-facts schema, the calibration flow's UI, the dish-variant table's columns and the
localisation strategy belong to the sessions building them. This record fixes the intent model and
the safety line so those five can start without each inventing a different one.
