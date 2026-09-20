# String conventions

For anyone writing a screen. Written 20 September 2026 by Nila, who owns `res/` and the string
table; the rules are `docs/decisions/0017`, this is how to follow them without reading it.

## The one sentence that shapes what you build

**Anything you add after the freeze at 22:00 IST on 20 September is English-only in the demo
build by default.** The Telugu reviewer packet ships once, tonight. A key that is not in it has
no Telugu, and the phone falls back to English for that line while every line around it is
Telugu. So keep new copy minimal, and reuse an existing key wherever one says what you mean.
`res/values/strings.xml` is the list; read it before writing a new one.

## No literal user-facing text in a composable

`StringResourcesTest` fails the build on a literal passed to `Text(...)` or to a `text`,
`contentDescription`, `label`, `placeholder`, `title`, `headline` or `supportingText`
parameter anywhere under `ui/`. Use `stringResource(R.string.key)`; with slots,
`stringResource(R.string.key, a, b)`. Test-only or log-only strings are not user-facing and
are not caught, and should not be in `ui/` anyway.

## Where a new key goes

`res/values/strings.xml`, the DEFAULT table, in English, in the group it belongs to, under
that group's comment. That file is the only place a key is born. **Grep it for the name first:**
two definitions of one key is a duplicate-resource error for everyone at the next merge, and
it happened once on 20 September. Never add a key to
`values-te/` or `values-hi/` yourself: those files are written by `tools/import_review_queue.py`
from a reviewer's reply and nothing else, and a missing key there falls back to English at
runtime, visibly, which is the honest state.

## Key names

`<place>_<meaning>`, lowercase, underscores. The prefix is the place the string is shown, and
it is what the reviewer packet uses to tell the reviewer where they will meet it:

| prefix | where it is shown |
| --- | --- |
| `safety_` | the non-dismissible line on every advice screen |
| `trigger_` | the health sentences the rules engine emits |
| `nutrient_`, `life_context_` | words dropped into those sentences |
| `confidence_band_`, `confidence_reason_` | the label beside every figure and its tap-to-explain |
| `language_` | the language picker |
| `about_` | the About screen (data sources and licences) |
| `context_` | lines written for the model, not shown |
| `tts_lead_in_` | spoken while the app works |
| `status_`, `state_`, `pipeline_` | the temporary build-status screen |

A NEW prefix means a new place. Tell Nila the prefix and one line saying where it is shown;
it goes into `PLACES` in `tools/make_review_queue.py` so the reviewer is told. Until then the
packet lists it under "UNPLACED: ask Vedant where this appears", which is a stop.

Keys derived from an enum use the constant lowercased: `confidence_reason_exact_food_match`,
`trigger_lab_above_range`, `nutrient_vitamin_b12`. A test asserts one string per
`ConfidenceReason`; follow the same shape for any enum whose constants are shown.

## `translatable="false"`

Only for things that are reproduced, not translated: the product name, the language endonyms
(`తెలుగు`, `हिन्दी`), and legal notices (`licence_notice_*`), which must appear verbatim. A
label AROUND a notice is an ordinary key. Everything else is translatable, including
format-only strings like `status_line` (`%1$s: %2$s`), because word order is the translator's.

## Slots, numbers, health

- Positional slots only: `%1$s`, `%2$s`, or `%1$d` for a plain count. Never bare `%s` or
  `%d`; the app formats figures and dates itself and passes strings, so a translation can
  reorder freely.
- **No string carries a number the app did not supply.** Not "2", not "1 katori", not
  "100 g". A test renders every health template and fails on a digit the evidence did not
  supply; the same rule applies to every string by intent.
- Health text (spec 15.2): say what the data says, offer what someone might consider, hand
  the decision back. Never name a condition the person did not declare, never prescribe. If
  you find yourself writing a sentence about a person's health, it belongs in the rules
  engine's templates, not in a screen.

## Comments in the table

Each group has a comment saying where it is shown and what the slots hold. Keep that habit
for a new group: the reviewer packet's notes are written from those comments.

## The About screen, ready to build

The screen exists (`AboutScreen.kt`, keys `about_*`, Arjun, 20 Sep) and already lists most of
this. What it still owes, from `0002` and `0005`:

1. The USDA attribution, licence and disclosure. These are rows in the food database's `meta`
   table (`attribution`, `licence`, `disclosure`, `sr_legacy_release`, `foundation_release`),
   read them from there rather than duplicating them; they are the same text 0002 requires and
   they travel with the data they describe.
2. The app's own licence: Apache-2.0, copyright Vedant Manmath Idlgave.
3. If the Piper voice stays: the espeak-ng notice, GPL-3.0-or-later, with a pointer to the
   exact source built (pending the TTS probe, `0005`).
4. The Telugu Piper voice: CC-BY-4.0 attribution to `rhasspy/piper-voices`.
5. The IITM notice, only if a voice under that licence ships.
6. Any ASR model attribution `0021` names (the English ASR model is NVIDIA's, CC-BY-4.0).

Of these the screen shows 2, 3 (without the source pointer yet), 4 and 6 in `about_components`,
and 5 has its key (`licence_notice_iitm_tts`) ready to bind. Missing is 1: the USDA line names
the source but not the citation `0002` asks for nor the disclosure ("values are based on foods
sampled in the United States ... estimates, not measurements of your own food"); both are rows
in the database's `meta` table, read them from there.
