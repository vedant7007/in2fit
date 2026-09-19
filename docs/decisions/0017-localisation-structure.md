# 0017. Three languages, one string table per language, and no invented Telugu

Date: 20 September 2026. Status: accepted for the structure. No Telugu or Hindi string exists yet.

## What was decided

Spec 5.11 asks for Telugu, Hindi and English "across the whole app", and `0015` leaves the
localisation strategy to whoever builds it. This is that strategy, set up before any screen
exists so that no string is ever hardcoded and a fourth language is a file rather than a refactor.

It is Android's own resource mechanism and nothing on top of it:

| File | Holds |
| --- | --- |
| `res/values/strings.xml` | the DEFAULT table, complete, written in English as the code is |
| `res/values-te/strings.xml` | Telugu, the demo language. **Empty on this date.** |
| `res/values-hi/strings.xml` | Hindi. **Empty.** |
| `res/values-en/strings.xml` | English. **Empty by design**: the default already resolves in English. |
| `res/xml/locales_config.xml` | `te`, `hi`, `en`, declared to the system via `android:localeConfig` |
| `app/build.gradle.kts` | `localeFilters = en, te, hi`, so library translations for other locales are dropped from the APK |

A key missing from a locale file **falls back to the default at runtime, visibly, on the phone.**
That is the whole design. An untranslated string shows in English rather than as a placeholder
that looks translated, for the same reason a stub returns `NotImplemented` rather than a plausible
number (`0001`): a placeholder is how a not-done state quietly becomes a fake one.

## The rules, and what enforces each

1. **Nothing user-facing is a literal in a composable.** `StringResourcesTest` scans `ui/` and
   fails the build on a literal passed to `Text(...)` or to a `text`, `contentDescription`,
   `label`, `placeholder` or `title` parameter. Compose has no lint check for this; `HardcodedText`
   covers XML layouts only, which this app does not use. The test is the same shape as
   `NetworkIsolationTest`, and for the same reason: the compiler cannot express the rule.
2. **A key exists in the default table or it does not exist.** The test fails on a key present in
   a locale file but absent from `values/`, because such a key is a typo that would silently never
   be shown.
3. **No Telugu ships unreviewed.** Vedant does not read Telugu well enough to catch a wrong word,
   and a wrong word on a health screen is read on stage by a judge who does. A Telugu string is
   written or reviewed by a fluent speaker. An entry written but not yet reviewed carries a
   `REVIEW` comment above it, and the test **prints** the review queue and the missing keys per
   locale on every run, so the state of translation is read off a test report rather than
   remembered. It is printed, not asserted, because a missing translation is an honest fallback
   and not a defect.
4. **No machine translation, anywhere.** The Hindi and English files are left empty rather than
   filled by a model, and so is the Telugu file. Nothing in `values-te/` was written by anyone
   who does not write Telugu. The endonyms in the language picker (`తెలుగు`, `हिन्दी`) are marked
   `translatable="false"` because they are names rather than translations, and they are on the
   review list all the same.
5. **Every `ConfidenceReason` has a sentence.** `Confidence.kt` says a reason with no sentence in
   the UI string table is a bug (spec 15.1.1 requires the reason to be visible, not just the
   band). The twelve sentences now exist in the default table and the test asserts one per enum
   constant, so adding a reason without a sentence fails the build.

## What was moved

`MainActivity` had eleven literals: the product name, the subtitle, seven pipeline names, the
"Not implemented" state and the `name: state` format. All are keys now. The product name reads
**IN2FIT** on screen; the package, classes and `applicationId` keep `katori`, because the JNI
symbol names encode the package and that rename is deferred until after the hackathon.

## What this record does not cover, and who owns it

- **The rules engine's sentences.** Were English literals inside the pure engine, out of the
  string table's reach. **Resolved in `db099ca`**: the engine emits a `TriggerTemplate` and the
  evidence, `TriggerText` renders from `trigger_*` keys with positional slots, and a test renders
  every template to assert no digit appears that the evidence did not supply. The nutrient words
  and life-context phrases are keys too. One free-text field remains: `TimelinePattern.description`
  has no producer yet; when one exists it has to come from the table the same way.
- **The picker itself.** Spec 10.2: the user chooses the language; the app never detects it. The
  keys for the picker exist. The screen does not, and neither does the call that applies the
  choice. On API 33+ that is `LocaleManager.setApplicationLocales`; on 26–32 it needs either
  `AppCompatDelegate.setApplicationLocales` from `androidx.appcompat`, which is not a dependency
  today, or a `Configuration` override at `attachBaseContext`. Whoever builds the settings screen
  picks one and records it. The test device is API 35.
- **Spoken language.** The ASR language parameter and the TTS voice are the ASR and TTS slices'
  concern. This record is about text on screen.
- **Numerals and dates.** Spec 15.1.1 forbids invented precision, not localised digits. Whether
  figures render in Telugu numerals is a product question nobody has asked yet; the string table
  uses `%1$s`-style positional placeholders so that either answer is possible without a refactor.

## The demo-build rule for unconfirmed Telugu

Ruled by Vedant, 20 September 2026, as a RULE so that nobody decides it under pressure:

**If the health sentences (`trigger_*`, items 1 to 9 on the sheet) are not confirmed by a fluent
speaker before the demo build is cut, `values-te` comes out of that build.** The mechanism is one
line in `app/build.gradle.kts`: `localeFilters += listOf("en", "te", "hi")` becomes
`listOf("en", "hi")`, which drops the Telugu table from the APK; every screen falls back to
the default table. The rule, the line and the reason are here so the cut is a lookup.

That is the contingency, not the plan. The health sentences are what beat 4 displays, and
losing Telugu guts the demo's strongest moment. The plan is a ten-minute read by a fluent
speaker, which Vedant is chasing.

**A middle option, recorded and NOT built:** the health templates and the UI strings are
separate keys. If the review comes back partial, a build could keep the confirmed strings and
fall back to English only for the unconfirmed health text, by stripping the `REVIEW`-marked
`trigger_*` entries from `values-te` at build time (a small Gradle step reading the same marker
`StringResourcesTest` and the importer read). Worse-looking than all-or-nothing, better than
shipping unreviewed health text and better than losing the language. It is not built because
it should not need to be; it is written down so that if it is needed it is a known shape.

## Evidence

`logs/nila-scope-build.log`: `testDemoDebugUnitTest`, `assembleDemoDebug` and
`assembleDemoDebugAndroidTest` all succeed with the four string tables, the locale config and the
locale filter in place. Test counts are in the JUnit XML under
`app/build/test-results/testDemoDebugUnitTest/`. The coverage report the test prints on that run:

    values-te: 0/27 present, 0 awaiting review, 27 missing
    values-hi: 0/27 present, 0 awaiting review, 27 missing
    values-en: 0/27 present, 0 awaiting review, 27 missing

Twenty-seven is the number of translatable keys on this date, and every one of them is missing in
every language. That is the true state, and it is the number a fluent speaker starts from.

## The review sheet

A reviewer does not need the repo. `python tools/make_review_queue.py te` writes
`docs/localisation/telugu-review-queue.md`: every key still missing from `values-te/`, with its
English, a sentence saying where it appears in the app, a note where the English uses a term of
art, and a blank line to write on. Entries present but marked `REVIEW` are listed with their
current text for checking. Keys are ordered by where they matter, so the temporary build-status
screen comes last and is marked as skippable. A key with a prefix the script does not know lands
in an "UNPLACED" section, so a new group of strings cannot be handed out without someone saying
where it is shown. The sheet comes back, the text is pasted into `values-te/strings.xml` unchanged
under the reviewer's name, the script is re-run and the count drops. `hi` works the same way.

After `db099ca` the sheet has 48 keys, and the eight health sentences are items 2 to 9, right
after the safety line, with a note per item saying what each slot holds.

## The way back, and the check

The reply will come as a WhatsApp message or a photo, because that is how people reply, and
Vedant cannot read what he would be pasting into XML. So the XML is never edited by hand:
`python tools/import_review_queue.py te --reply reply.txt --reviewer "Name"` takes a chat reply
(one line per item, starting with the item's number on the sheet; the sheet tells the reviewer
to number them) and `--sheet filled.md` takes the sheet itself with the lines filled in. Either
way the script writes `values-te/strings.xml` in default-table order, with a
`written by <name>, <date>` comment above each entry, escapes for Android, and refuses per item
anything whose `%N$s` slots do not match the English or that carries a bare `%`.

A name is required; without it the script refuses. Two stamps, and the difference is the
whole point: `written by <name>` means a fluent speaker wrote or checked the line;
`REVIEW: received via <name>, author not confirmed` (`--unreviewed`) means the lines arrived
through someone who cannot verify them. **The first Telugu reply, 20 September, is the second
case**: 48 lines came back under Vedant's name, and Vedant does not read Telugu well enough to
have written or checked them. They were imported unreviewed, every entry carries the marker,
the test reports "48 awaiting review", the queue keeps them, and the check file went back with
the question at the top. Nothing is stamped as reviewed until a fluent speaker confirms it
through a check sheet, one entry at a time; a blank answer under a `REVIEW`-marked entry is
that confirmation and drops the marker. A number that is not on the sheet, or an item whose
number names a different key on the committed sheet, refuses the whole reply, because it means
the wrong sheet. Identical text under two keys is listed for the reviewer to confirm, since the
importer checks slots, not bodies (the two escalate sentences are identical in English too).

**The check, which is the point.** A mis-ordered paste puts the wrong sentence under the wrong
key, and nothing about the XML would show it to anyone who cannot read Telugu. After writing,
the script re-reads the XML from disk and writes `docs/localisation/telugu-review-check.md`:
every item in sheet order, its number, the English, and what is now in the app, headed by the one
question it exists to ask: "tell me any number where the Telugu is under the wrong English." The
question is in the file so it does not depend on anyone remembering to ask it. That file goes
back to the reviewer, who is the only person who can see a wrong mapping. Exercised on a
temporary copy of `res/` with stand-in text before any of it touched the repo: reply and sheet
paths, a deliberate mis-order showing up under the number given, slot mismatch refused, missing
name refused, stray number refused, apostrophe escaping round-tripped, `REVIEW` confirmation.
