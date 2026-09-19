# 0018. Lab report fields are read by layout and regex, and a range counts only when it is printed after the value

Date: 20 September 2026. Status: accepted for the code; NOT for any behaviour on a photographed
report. Owner: Arjun (`ml/vision/`).

## What landed

Three files in `ml/vision/`, on top of the frozen `VisionEngines.kt` contracts, none of which
changed:

| File | What it is |
| --- | --- |
| `MlKitOcrEngine.kt` | `OcrEngine` over ML Kit Text Recognition, the exact call sequence the hardware probe verified without INTERNET (`0004`). One `TextBlock` per ML Kit *line*, not per block, because blocks follow columns and destroy rows. |
| `FrameStore.kt` | Where an `ImageRef` comes from. The camera layer holds a bitmap plus its CameraX rotation and gets a ref; the engine takes it, once. |
| `LabReportExtractor.kt` | `RecognisedText` → `LabReport(fields, reportDate)`. Pure. No model, no table of tests, no reference ranges of its own. |

Tests: `LabReportExtractorTest` (8 JVM tests, a 63-row corpus with a printed table) and
`LabReportOcrProbeTest` (instrumented, rendered page through the real recogniser, for Rao).

## The rule this record exists to state

**A reference range is taken from the report, and only from a range printed AFTER the value on
the same row.** Two ranges after the value read as none. A range-shaped thing before the value is
not a range. A row whose range cannot be read keeps its value with null bounds, so it can be
shown and stored and no rule fires on it (`Evidence.LabValueOutsideRange` cannot be built).

That sentence was not the first version. The first version took the first range on the row and
the first free-standing number as the value. It passed its own 44-row corpus at 100%. Then it was
probed with rows it had not been written against, and three of twenty-five were wrong:

| Row | First version read | Why |
| --- | --- | --- |
| `Vitamin D 25 Hydroxy 18.5 30-100` | value **25.0** | the first free-standing number was in the name |
| `Haemoglobin 9,8 g/dL 13-17` | value **9.0** | a comma decimal was truncated, not refused |
| `Vitamin D (25-0H) 18.5 ng/mL 30 - 100` | range **none**, name `Vitamin D (    H)` | OCR's `0` for `O` made `25-0` a range, two ranges read as ambiguous, and the blanked span leaked into the name |

Each of the first two is a WRONG VALUE: a number that is not on the report, carrying a range that
is, which is precisely the failure this pipeline exists to prevent. The corpus that passed at
100% could not have found them because the same hand wrote both. The probe rows are now corpus
rows, so the fix is guarded, and the corpus is 63 rows: 51 must read, 12 must be dropped.

The fix is structural rather than a patch per row: the value is the first free-standing number
followed by a known unit, and the range is the one range after it; with no unit read, the row is
kept only if it has exactly one range, and the value is the last free-standing number before that
range. A comma decimal is refused whole.

## What is measured, and what is not

Measured, read from the JUnit XML in `app/build/test-results/testDemoDebugUnitTest/`:

    LabReportExtractorTest   tests="8" failures="0" errors="0"
    corpus                   read 51/51  missed 0  wrong value 0  wrong range 0  junk kept 0
    whole JVM suite          tests=140 failures=0 errors=0

**This is a parser measurement over authored rows. It is circular in the same way
`MatchRateTest` is** (`0006`): the rows are what a printed Indian report looks like after ML Kit
has read it *as far as anyone on this project knows*, plus the single real observation there is,
`9.8g/dL` with the unit glued to the number. Do not quote 51/51 as accuracy.

NOT measured, and not claimed:

- **Whether ML Kit's line segmentation of a real table matches the row model.** The row model is
  "lines whose vertical centres fall within half a typical line height are one row, read left to
  right". `LabReportOcrProbeTest` renders a nine-row four-column page and pushes it through the
  real recogniser on the device. It has been compiled (`compileDemoDebugAndroidTestKotlin`,
  exit 0) and never run. Rao runs it; its report is `katori-ocr-report.txt` beside the hardware
  report, logcat tag `IN2FIT-OCR`.
- **Anything about a photograph.** A rendered page is square, sharp and evenly lit. A phone photo
  of a real report is the next probe, and it is the one that matters.
- **The rotation convention.** `FrameStore` carries CameraX's `rotationDegrees` through to ML Kit
  unchanged, on the reading that both mean "clockwise rotation that makes the image upright".
  Test `b_rotatedCaptureReadsTheSame` fails loudly if that reading is wrong.

## Ceilings, stated in the source

- A report that prints the range BEFORE the result reads as no range, and the name keeps the
  range text. Silence, not a guess.
- A two-column report (two tests per printed row) yields the left test with no range and loses
  the right one.
- A stray digit between a unit-less value and its range is taken as the value.
- Row grouping is axis-aligned; a page skewed more than a few degrees drifts rows together.
  Upgrade path is deskewing from ML Kit's corner points.
- The report date is day-first, as every Indian lab prints; the UI confirms it.

## What was deliberately not built

- **`DishClassifier`.** No model exists in this repository or in `data-sources/models` for it,
  and no dependency is declared for one. Spec 12.4 names MobileNet or EfficientNet-Lite over an
  Indian food image set; no such weights are sourced, and sourcing them is a `0005`-class licence
  decision, not a coding task. ML Kit's generic image labeller was considered and rejected: its
  labels ("Food", "Rice", "Dish") cannot become a `dishCode` in the food database without a
  mapping that would be a guess presented as a first guess. Nothing binds the contract, so the UI
  shows the honest state. Per hard rule 6, no stub was written either.
- **`PoseEngine`.** The brief orders it last and after beat 3 is tested on hardware, which has
  not happened. No MediaPipe dependency is declared.
- **A per-test lookup table** (name → canonical test, expected unit). It would make names
  prettier and would also be the first place an app-carried range could creep in. Downstream
  matches on substrings of the printed name (`RuleTemplates.forLabValue`), which needs no table.

## What Rao is asked for

1. Run `LabReportOcrProbeTest` with `am instrument` and paste the `read/missed/wrong` line and
   any `UNEXPECTED` rows into `COORDINATION.md`. Both tests: upright and rotated.
2. Provide `OcrEngine` in `AppModule` as `MlKitOcrEngine(FrameStore())`, with the `FrameStore`
   a singleton the camera layer can inject. `di/` is Rao's. The engine is real and
   hardware-verified on its one call, which is the bar `AppModule`'s own comment sets.
3. Decide the `LabField` → `LabValue` mapping when a row's unit is null. `LabValue.unit` is
   non-null; the row is still comparable to its own printed range. Empty string, or ask the
   person: the orchestrator's call.
