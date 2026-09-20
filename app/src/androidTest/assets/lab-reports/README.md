# Lab-report fixtures for Beat 3, and the threshold that makes Beat 3 safe to perform live

Beat 3 is the camera pointed at a printed report in a room with bad lighting. The recogniser
(ML Kit) runs only on a device; the extractor (`LabReportExtractor`) runs anywhere. Before this
set, the photograph step had ONE observation ever, a single haemoglobin row. This directory is
the fixture set and `LabReportOcrTest` (device-only, in this source set) runs the whole path,
recognition then extraction, over every file here and writes a table of what it found against
what is printed on each sheet.

## The threshold, written before the run

A number the app shows that is not on the paper is an invented number; a row it does not read
is silence. So the gate is asymmetric:

1. **On every fixture, rendered or photographed: WRONG VALUE = 0 and WRONG RANGE = 0.** One
   misread number on any sheet is a stop, whatever else was read.
2. **On the rendered sheets: MISSED = 0 and NO RANGE = 0**, except on the two sheets built to
   show a known ceiling (the two-column sheet, whose right column may be lost; the
   range-before-result sheet, whose ranges read as none), where a miss is allowed and a wrong
   is not. A range read as NONE is silence (no rule fires on that value, `0018`); a range read
   as something else is a wrong number. The two are scored apart.
3. **On the photographs of the demo's own printed sheet, at least three (square-on, at an
   angle of about 20 degrees, under warm indoor light): both demo rows, fasting glucose and
   HbA1c, read with the right value AND the right printed range on all three, and the report
   date read on the square shot.**

Beat 3 is SAFE TO PERFORM LIVE when 1, 2 and 3 all hold. If 1 and 2 hold and only the angled
photograph misses a demo row, Beat 3 is safe SQUARE-ON ONLY: S holds the sheet flat and the run
of show says so. If any photograph reads a wrong number, or misses both demo rows, Beat 3 is
NOT safe: the presenter opens the pre-saved report (the run of show's fallback, item 7) and the
camera is not pointed at anything live. OCR time per sheet is reported, not gated; the beat's
budget is 15 s and the recogniser was 1 to 2 s on the rendered page (`0018`).

## What is here, and what each one proves

| File | Kind | What varies | Proves |
| --- | --- | --- | --- |
| `rendered-01-single-column-spaced-range.png` | RENDERED | one column, `70 - 100`, unit separate, sans-serif | the extractor on a clean sheet |
| `rendered-02-two-column-sheet.png` | RENDERED | two tests per line, left and right | the known ceiling: the right column may be lost, never misread |
| `rendered-03-glued-units-hyphen-range.png` | RENDERED | `142mg/dL`, `70-100`, monospace (a dot-matrix print) | the shape the hardware run actually returned |
| `rendered-04-range-before-result.png` | RENDERED | Reference column BEFORE Result | the known ceiling: the range reads as none, never as the value |
| `rendered-05-angled.png` | RENDERED, then warped | sheet 01 seen at about 20 degrees, slightly soft | perspective, in the recogniser only |
| `rendered-06-warm-light.jpg` | RENDERED, then lit | sheet 01 under a tungsten cast with the right half in shadow, a lamp's hot spot, grain and JPEG loss | colour and noise, in the recogniser only |
| `rendered-07-flags-brackets-serif.png` | RENDERED | `H` flags, `(70 - 100)` brackets, `Glucose (Fasting)`, serif | punctuation and a number in a name |
| `photo-*.jpg` | PHOTOGRAPH | the demo's printed sheet, from the demo phone | **the demo** |

**A rendered image proves the extractor. Only a photograph proves the demo.** Nobody on this
laptop has a camera or the sheet, so this directory ships with rendered images only and ZERO
photographs, and the test says so in its table. To add the photographs: print
`rendered-01-single-column-spaced-range.png` on A4, then with the demo phone take at least
`photo-01-square.jpg`, `photo-02-angled.jpg`, `photo-03-warm-light.jpg` of that sheet in the
demo room's light, copy `rendered-01-single-column-spaced-range.expected.txt` beside each as
`photo-0N-….expected.txt`, and rerun. The threshold's item 3 is scored on those.

## Sidecars

Beside `x.png`, `x.expected.txt`: one printed test per line, `name|value|unit|low|high|printed
row`, in the format `PhotographedReportProbeTest` already reads (it ignores the sixth field);
the sixth field is the row exactly as printed, which the JVM test `LabReportFixturesTest` pushes
through the extractor alone, so the extractor half is proven on every fixture without a phone.
`# kind: rendered|photograph` names the kind.

## Running it

    adb shell am instrument -w -e class io.github.vedant7007.katori.ml.vision.LabReportOcrTest \
        io.github.vedant7007.katori.test/androidx.test.runner.AndroidJUnitRunner

Fixtures are bundled in the test APK; nothing is staged. The table lands in
`/sdcard/Android/media/<pkg>/katori-ocr-report.txt` and in logcat under `IN2FIT-OCR`.
