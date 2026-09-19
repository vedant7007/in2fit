# Katori status

Updated 19 Sep 2026, 09:50.

---

## BLOCKED ON VEDANT

**Nothing right now.** Your three answers unblocked everything.

One recurring cost you should know about: **laptop control times out after 30 minutes idle**,
and it is what runs builds and downloads. It bit once this morning and cost about an hour. If
you see me report it again, re-granting from your phone is all it takes.

---

## VERIFIED ON HARDWARE

**Nothing. All day. Not one line.** No phone connected, nothing installed, no model loaded.

Still unverified: model load, inference speed, ASR accuracy, ASR/LLM/TTS co-residency, camera,
OCR, every latency number. All of it waits for the Realme 11.

---

## VERIFIED ON EMULATOR

Nothing. The emulator variant is not built yet.

---

## COMPILE AND UNIT TEST ONLY

Everything below is from a redirected log, read off disk.

- `assembleDemoDebug` green, APK 34.17 MB, arm64-v8a only
- `testDemoDebugUnitTest` green: **47 tests, 0 failures**
  - NetworkIsolationTest 2
  - FoodTextMatchingTest 11
  - SqliteFoodLookupTest 18, against the real shipped database
  - RulesEngineTest 16
- Merged demo manifest permissions are **exactly** CAMERA, RECORD_AUDIO, and the platform's own
  DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. Now enforced as a whitelist, not a ban on INTERNET.

### Food layer, done

Bundled read-only SQLite built from USDA. All four import rules enforced **and asserted after
import**, so a violation aborts the build instead of shipping:

- energy coalesces 1008, then 2048, then 2047. It fired for real on the jowar record
- every food has an energy value, asserted
- dairy comes only from SR Legacy, asserted
- no enriched or known-defective record imported, asserted
- absent nutrients stay absent. 2 of 272 values are genuinely unknown and read as Unknown,
  never as zero. 21 are assumed-zero, which is different and comes from the source saying so

34 ingredients, 230 aliases across roman, Telugu and Devanagari, 21 household unit conversions.

**Ragi, bajra, jaggery, curry leaves and asafoetida** resolve to a named refusal with the reason,
never to a substitute. There is a test that fails if any of them ever resolves to a food.

### Rules engine, done

Pure. No clock, no randomness, no I/O, no model. The required test passes: identical input at two
different times gives an identical digest **and** an identical evaluation.

Eight safety-reviewed sentences, and a test that fails if any of them ever contains "diabetes",
"anaemia", "hypertension", "deficiency" or six other words.

### Models, downloaded and checksummed

1669.6 MB total, against the ~2 GB budget. Disk C: 215 GB free.
Nothing has been loaded or run. Sizes match what the repositories publish, byte for byte.

---

## DECIDED WITHOUT ASKING

- **No nutrient preference means no suggestions at all.** Found by a test asserting beat 4's own
  claim. With nothing to rank by, every candidate scored zero and the list fell back to
  alphabetical order, which happened to match the ranked order, making "the advice changed"
  silently false. An arbitrary order presented as advice is the same failure as a made-up number.
  `docs/decisions/0006`
- **Lab markers respond to the direction that matters clinically.** A glucose above its range and
  one below it are not mirror images, and ranking them as though they were would be worse than
  doing nothing. A marker with no reviewed policy produces no preferences at all.
- **Permission check is now a whitelist.** A denylist only finds what it already knows to look
  for. `docs/decisions/0004`
- Model sourcing, the licences, and the non-commercial register. `docs/decisions/0005`
- USDA import rules and the gap tiering. `docs/decisions/0002`

---

## NON-COMMERCIAL REGISTER: EMPTY

You allowed non-commercial licences and, as it turned out, none was needed. Everything shipped is
Apache-2.0, CC-BY-4.0, MIT or public domain. Telugu TTS came in at CC-BY-4.0, so the allowance was
not spent. Full table in `docs/decisions/0005`.

---

## NEXT

KSP, Room compiler and Hilt, timeboxed. Then the LLM layer with NumericGuard, then the UI.
