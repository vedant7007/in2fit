# 0023. Packaged-label fields are read under a basis the panel proves, and a percent is never a cell

Date: 20 September 2026. Status: **DROPPED by Vedant, 20 September 14:30.** Packaged-label
OCR is not in the demo and there are no days for it. The code stays landed, unbound and
untested on hardware; the probe asks to Rao are withdrawn; nothing below is to be extended.
Owner: Arjun (`ml/vision/`). Number claimed in `COORDINATION.md` at 03:29 before this file was
written, per the process Vedant approved.

**READ THIS FIRST, as for `0018`: the parser is tuned to imagined output.** There are ZERO real
OCR observations of a nutrition panel in this project. Every panel and row the extractor was
written and measured against was authored by the person who wrote the extractor. Nobody should
trust it until `NutritionLabelOcrProbeTest` has run over a rendered panel and then over
photographs of real packs, and its WRONG VALUE and WRONG BASIS lines have been read off the
device.

## What landed

| File | What it is |
| --- | --- |
| `NutritionLabelExtractor.kt` | `RecognisedText` → `NutritionLabel(basis, rows, nutrients, ingredients)`. Layout and regex only. No model, no reference values, no assumed units. |
| `TextLayout.kt` | The row grouping from `0018`, shared by both extractors now. `LabReportExtractor` unchanged in behaviour; its 8 tests still pass. |
| `NutritionLabelExtractorTest` | 22 JVM tests: 21 whole-panel layouts and a 51-row corpus (43 must read, 8 must be dropped) with a printed table. |
| `NutritionLabelOcrProbeTest` | Instrumented: a rendered FSSAI panel, then staged pack photographs with a person-written sidecar. Compiled, never run. |

Same engine as `0018` (`MlKitOcrEngine`, one `TextBlock` per ML Kit line), same method
(corpus, then probe with rows the corpus was not written against, then fix structurally).

## The column rule, decided before the parser was written

A panel states values per 100 g AND per serving, usually side by side. Taking the wrong column
is a silent factor of two that looks entirely reasonable: the biryani-to-bay-leaf shape.

**A value is reported only under a basis PROVEN by a header token on the panel** (`Per 100 g`,
`Per serve (30 g)`, `Per 30 g`, `%RDA`), or, on a small pack with no header, by the same token
printed on every nutrient row (`Energy 520 kcal per 100 g`, `Protein 7.2 g/100g`). **Per-100
wins when both are printed.** Cells are assigned to header columns by x-position when every
column and every cell has its own ML Kit box, and by order when they do not, in which case the
plain cells must fill the plain columns exactly. A row whose cells cannot be placed is dropped.
A panel that proves no basis yields no nutrient values at all: `Net Wt: 100 g` is a weight, not
a basis, and one unlabelled column of numbers is not per 100 g because the pack weighs 100 g.

**A percent is never a cell.** `7.8%` is marked as one, so it cannot be mistaken for a value;
it is ignored wherever it is printed, in its own column or inline after a value.

**A printed row name matches the lexicon exactly** (`0022`): `saturated fat` never becomes
`FAT`, `of which sugars` never becomes `CARBOHYDRATE`, and a row the lexicon does not name is
dropped, however plausible its number. The domain knows eight nutrients; the rest of the
lexicon (sugars, saturates, trans fat, cholesterol, calcium, salt...) survives in `rows` as
printed and reaches no total.

**Units convert by factor, never by assumption.** `Sodium 0.65 g` becomes 650 mg by arithmetic;
`Sodium 650` with no unit read stays a row and is not a nutrient, although it is mg on every
pack. kJ is never converted to kcal; a panel printing only kJ has no energy. `<0.5 g` is not a
value. A comma decimal (`7,2`) is refused, not read as 7.

## Found by probing, fixed structurally

The corpus passed at 100% before the probe, as `0018`'s did. Twenty panels it was not written
against found:

| Probe | First version read | Structural fix |
| --- | --- | --- |
| `%RDA` printed LEFT of `Per 100 g` | basis **per serving** (the percent token, patched earlier to absorb "per serve", swallowed "Per 100 g"); rows dropped by the count rule so no wrong number escaped, but a one-cell row would have been reported per serving | the percent token may absorb "per serve" and nothing else: a percent RDA is per serving by definition |
| a row without a `%RDA` cell under a three-column header (the most common FSSAI layout) | dropped by the strict count | percent cells are not cells; plain cells must fill plain columns |
| a bare number cell `3.1` | lost its box because the cell regex swallowed the trailing space and no longer matched its own line; the row fell back to the order rule and was dropped | the space moved inside the optional unit group |
| `Energy (kcal/100g)`, `Total Fat, g`, `Sodium mg` | unit not read, nutrient dropped | a unit at the end of a name is read from brackets, after a comma, or trailing, and stripped before the lexicon lookup |
| header split by ML Kit into `Per 100` and `g` | no header, silence | basis tokens are found on the joined row, not per line |
| `Energy: 520 kcal/100 g` on a small pack | no header, silence | `/100 g` proves the basis as "per 100 g" does; every nutrient row must prove the same one |

Measured, read from the JUnit XML in my worktree, every file written by my own run at 04:10:

    NutritionLabelExtractorTest   tests="22" failures="0" errors="0"
    corpus                        read 43/43  missed 0  wrong value 0  wrong basis 0  junk kept 0
    LabReportExtractorTest        tests="8"  failures="0" errors="0"   (after the TextLayout move)
    whole JVM suite               tests=274 failures=0 errors=0

Circular, as `0018`'s figure is. A regression guard, not coverage.

## Ceilings, stated in the source and guarded by tests

- A one-line panel (`Typical values per 100 g: Energy 520 kcal, Protein 7.2 g`) reads as no
  rows. Silence.
- A mirrored panel (values printed left of names) reads as no rows. Silence.
- Two nutrients on one printed line read the first and lose the second, and the first is right
  only because per-100 is the first column. On a serve-first panel this is a wrong value; that
  combination has not been seen on a pack and the corpus row says so.
- A one-cell row under a multi-column header read as single lines (`Added Sugars (g) 0`) is
  dropped; with per-cell boxes it is placed by x. The probe will say which ML Kit returns.
- A panel printing both `Per 100 g` and `Per 100 ml` (powder and prepared) reports the first
  and says so in `basis`.

## What is not built, deliberately

- **Flagging** hidden sugars, palm oil and sodium (spec 12.2). The ingredient list is returned
  as printed; the rules live in `domain/`, which is Rao's, and they are rules about a person's
  declared goals, not about text.
- **Barcode lookup** (spec 12.2). There is no local product database to match against, and a
  barcode with nothing behind it is a number with no meaning.
- **A `NutrientValue` for every row.** The domain has eight nutrients; inventing enum values
  for sugars or saturates is Rao's call, in `Nutrients.kt`.

## What Rao is asked for

1. `NutritionLabelOcrProbeTest`, test a, in the same window as the lab probes. Then real packs
   staged in `/sdcard/Android/media/<pkg>/packs/` for test b — biscuits, a namkeen, a drink,
   a milk powder: four different panel formats matter more than four of one.
2. The hidden-sugar and palm-oil flags, if the demo wants them, as rules over
   `NutritionLabel.ingredients`.
