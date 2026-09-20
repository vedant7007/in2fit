# 0028. A digit run glued to a letter is a word, not a figure: B12, and the one rule for it

Date: 20 September 2026. Status: accepted. Closes Nila's finding of 03:42 (COORDINATION.md) on
the template digit test; consistent with `DefaultNumericGuard`'s existing precedent.

## The finding

The health sentences are rendered from the string table and a test asserts that every digit in
a rendered sentence came from the evidence, so a translation cannot smuggle a number in. The
test's regex was `\d+(?:\.\d+)?`, which reads the 12 in "vitamin B12" as a cited figure. It did
not fail the build only because the MEAL_COMPOSITION sample used CARBOHYDRATE, so "vitamin B12"
was never rendered; with VITAMIN_B12 in the sample the English defaults would fail today, and
the Telugu line "విటమిన్ B12" (item 16 on the reviewer's sheet) would fail the same check when a
locale is put through it.

Two candidate fixes were named: exempt letter-adjacent digits, or exclude nutrient words from the
digit scan.

## The ruling

**A digit run glued to a letter is a word, not a figure.** One rule, stated once, applied in
every place a rendered health sentence is scanned for figures:

- `DefaultNumericGuard` already has it: a value that appears in the input only attached to
  letters ("B12") does not licence a bare number in the output, and "b12" out for "b12" in is
  fine. The guard's tokeniser is the definition; nothing else re-derives it.
- The template digit test in `RulesEngineTest` now uses the same boundary, `(?<![\p{L}\d])\d+(?:\.\d+)?(?![\p{L}\d])`:
  a digit run that touches a letter on either side is not a cited figure. And its
  MEAL_COMPOSITION sample now uses VITAMIN_B12, so the case is exercised on every run rather
  than avoided by the fixture.
- The reviewer's sheet says it in one line: **"B12" stays as the Latin token in every language.**
  A translator writes the nutrient name around it; the digits inside it are part of the name.

Excluding nutrient words from the scan was rejected: it would need a list of every word that
may carry a digit, per language, and a list is a thing that goes stale. A boundary rule needs
no list and matches what the guard already does.

## What it does not cover

A sentence that writes a figure glued to a unit ("12g") is a figure with a unit, and the guard
reads it as one (unit-aware since `0463557`); the template test is stricter and would flag it,
which is correct: the templates put a space between a number and its unit, and a locale that
does not is a reviewer's finding, not a false positive.
