# The data gap list: what the v2 design shows, against what the system produces

Written 21 September 2026 by Ira, before any code, from the design at
the v2 design file (`IN2FIT App v2.dc.html`, 28 screens, its script's demo data
model, `assets/in2fit-mark.png`, `assets/in2fit-lockup.png`) read against the code on master
(`data/local/entity/Entities.kt`, `data/local/dao/Daos.kt`, `domain/Orchestrator.kt`,
`domain/OrchestratorSeams.kt`, `data/food/FoodLookup.kt`).

Every distinct piece of information the design displays is one row. Three verdicts:

- **PRODUCED** — the system gives it today; the field or event is named.
- **DERIVABLE** — the ingredients exist; what has to be combined, and by whom, is named.
- **MISSING** — nothing produces it; what would have to be built, and who owns it, is named.
  Some MISSING rows are also **FORBIDDEN** by a standing ruling; those are marked and will not
  be built by anyone.

The standing rule for the build (Vedant, 21 Sep): a MISSING piece is left out of the screen
entirely, never filled with something plausible. This list is also the brief for Priya, Rao and
Arjun: the DERIVABLE rows are queries and fields; the MISSING rows are product decisions.

## What the system holds today, in one paragraph

`profile` (age, weight, height, sex, `goal` as a kind — MAINTAIN / LOSE_WEIGHT / GAIN_WEIGHT /
BUILD_MUSCLE — never a number, life context, diet type; **no name, no speech language**),
`conditions` (declared, with a source), `meals` (logged-at, raw transcript, band, language) with
`meal_items` (spoken name, quantity, unit, **grams**, food id, band, reasons) and per-item
`meal_item_nutrients`, `lab_values` (test, value, unit, printed low/high, report date) with
`history(testName)`, `suggestions` (the stored advice per meal, trigger + phrasing), bundled
`unit_conversions` (katori 150 g, plate, glass, spoon) and the override tables nothing writes.
Queries that exist: `recentMeals(limit)`, `mealsInRange(from, to)`, `nutrientTotalsForRange`,
`nutrientTotalsForMeal`, `itemsFor(mealId)`, `history(testName)`, `observeAll()` on labs,
`deleteItem` (an item, never a meal). Engines: `FoodLookup.candidates / nutrientsFor /
resolveUnit`, the rules engine's trigger sentence per evaluation, the four spoken intents.
Tables with nothing behind them: `activity`, `exercise_sessions` (sensors were dropped).
Nothing anywhere: targets, water, streaks, reminders, notifications, a chat history, an
account, a network.

---

## Onboarding (design: Welcome, Phone, Verify, Language, About you, Conditions, Add report, Scanning, Mic, Ready)

| shown | verdict | detail |
| --- | --- | --- |
| Welcome: rotating "You say … / 412 kcal · 18 g protein" | **MISSING** | The four phrases carry figures nobody computed for this user. 0029: demo figures are computed, not remembered. Either the app computes them through the real resolver at first launch (Rao; ~1 s per sentence, no model needed for LOG figures) or the numbers are cut and the phrases stand alone. I cut the numbers. |
| "Get started" / "I already have an account" | **MISSING, FORBIDDEN** | No accounts. The demo build has no INTERNET permission (0004); "account" implies a server. Cut. |
| Phone number, OTP, "Resend in 0:24", "keep your log across devices" | **MISSING, FORBIDDEN** | SMS and a server. Cut entirely. First run begins at Language. |
| Language: English / Telugu / Hindi / **Mixed** | PRODUCED for three; **MISSING, CONTRADICTED** for Mixed | The speech language is chosen (`SpeechLanguageRef`, TalkViewModel state, default `en-IN`). It is **not persisted**: `ProfileEntity` has no `speech_language_tag` (Jacob's 05:40 ask to Rao, not landed). "Mixed" contradicts 0022 (one IndicConformer checkpoint per chosen language). Ask Rao: the field on the profile, Room v3; cut "Mixed". |
| "Speech and replies" under Telugu/Hindi | **MISSING, CONTRADICTED** | Replies are English whatever is spoken (0019 addendum 7). The note must read "speech" only. Copy, Nila. |
| About you: age, weight, height | PRODUCED | `ProfileEntity.age_years / weight_kg / height_cm`; **no write path from a screen exists** — `ProfileDao.upsert` exists, no `UserIntent` covers it. Ask Rao/Arjun: a `ProfileStore` seam or a `UserIntent.UpdateProfile`. |
| About you: activity "3 days/wk" | **MISSING** | No field. Product decision. |
| Goal chips: "Reverse prediabetes", "Lose fat", "Build muscle" | PRODUCED as a kind, **MISSING, FORBIDDEN** as worded | `ProfileEntity.goal` holds MAINTAIN / LOSE_WEIGHT / GAIN_WEIGHT / BUILD_MUSCLE. "Reverse prediabetes" names a condition as a goal (spec 15.1: never diagnose). The chips become the four kinds, in plain words. |
| Conditions chips (Prediabetes, Type 2 diabetes, High BP, Thyroid, PCOS, Vitamin D deficiency, High cholesterol, None) | PRODUCED (declared conditions) | `ConditionEntity.name`, source DECLARED. Free text today; the chip list needs a vocabulary Priya agrees with the routing lists (`SafetyLine` keys on condition words). |
| Add report / Scanning: "Finding the results table", "Reading **14** markers", "Matching to your profile" | PRODUCED (the flow), DERIVABLE (the count) | `Stage.CAPTURING / READING_TEXT`, `LabReport.fields.size` for the count; "matching to your profile" is the regeneration `LabReportSaved.regeneratedMealId`. |
| "Photo or PDF" | **MISSING** (PDF) | The camera path only. Cut "PDF". |
| Mic permission | PRODUCED | Runtime permission, as today. |
| Ready: "Daily budget 2100 kcal", "Protein target 95 g" | **MISSING** | No target mechanism (Priya, 20 Sep). Cut both rows. |
| Ready: "Watching: HbA1c 6.4 · Vit D" | DERIVABLE | Lab values outside their printed range: `LabValueDao.observeAll()` + the below/above comparison the Scan screen already draws. Worded as "below the printed range", never "low". |
| Ready: "Voice: English" | PRODUCED | The chosen speech language. |

## Home ("Today")

| shown | verdict | detail |
| --- | --- | --- |
| Date | PRODUCED | The clock. |
| "Morning, **Aarav**" | **MISSING** | No name on the profile. Ask Rao: `ProfileEntity.name` (Room migration) — or the greeting drops the name. I drop it until the field exists. |
| Notification bell with a dot | **MISSING** | No notifications (see Nudges). Cut. |
| kcal ring: "1180 kcal in" | DERIVABLE | Today's energy total: `MealDao.nutrientTotalsForRange(startOfToday, now)` is exactly what `RoomUserContextSource` builds for `PeriodTotals(TODAY)`. Needs a read outside a turn: Arjun, a `TodayViewModel` on the DAO, or Rao exposing `UserContextSource.current()` to a screen. |
| The ring as a fraction of a goal, "remaining kcal left today" | **MISSING** | No calorie target. The ring becomes a plain number; "left" is cut. |
| "dinner still to log" | **MISSING** | No meal-slot concept (see Diary). Cut. |
| Macro bars "57 / 95 g" | DERIVABLE (numerator), **MISSING** (denominator) | Today's protein / carbohydrate / fat from the same totals; the targets do not exist. Shown as numbers, no bars. |
| Water "1.6 / 3.0 L", glasses, "+" | **MISSING** | No water table, no write. Product decision; cut. |
| Last meal card: name, "1.5 katori · 210 g · 318 kcal", time | DERIVABLE | `recentMeals(1)` → `itemsFor` (spoken names, quantity, unit, grams) + `nutrientTotalsForMeal` (energy) + `logged_at_epoch_ms`. A query, Arjun's ViewModel. |
| "Add manually" | DERIVABLE (the search), **MISSING** (the write) | See Search. |
| "Today's diary" | DERIVABLE | See Diary. |
| Nudge card: "You're 38 g short on protein…" | **MISSING** | Needs a target and a nudge rule. The one produced text of this shape is the **stored advice on the last meal** (`SuggestionDao.forMeal` / `AdviceStore.latest`: the trigger sentence and the guarded phrasing). The card shows that, labelled as advice on the last meal. |
| "Reading your report · HbA1c 6.4" | DERIVABLE | Latest out-of-range lab values, as on Ready. |
| "Ask about this" | PRODUCED | Opens the voice sheet with the ANSWER intent. |

## Diary

| shown | verdict | detail |
| --- | --- | --- |
| "1180 kcal logged · 4 entries" | DERIVABLE | Today's totals and `mealsInRange` count. |
| Week strip: ✓ per day logged | DERIVABLE | `mealsInRange` per day for seven days: logged or not. No streak arithmetic is shown or implied. |
| "Trends" | see Trends | |
| Entry: slot "Breakfast / Snack / Lunch" | **MISSING** | No slot on a meal. A rule by hour would be an invented label; Priya to rule if a slot is wanted. Until then the time alone. |
| Entry: time | PRODUCED | `logged_at_epoch_ms`. |
| Entry: name ("Roti, dal and curd") | DERIVABLE | Items' display names joined (`food_id` → display name via the catalogue, or `spoken_name`). |
| Entry: portions "1.5 katori · 210 g" | PRODUCED per item | `MealItemEntity.quantity / unit / grams`. |
| Entry: "3 items · voice logged" | DERIVABLE (count), **MISSING** (voice vs typed) | `MealEntity` has `raw_transcript` and `language_tag` but no source flag; a `Speak` and a `Type` turn write the same row. Ask Rao: `MealEntity.source`. Until then "3 items". |
| Entry: kcal | DERIVABLE | `nutrientTotalsForMeal(mealId)`, energy row. |
| Entry flag ("Second sugared drink before noon") | DERIVABLE, narrower | The stored advice's trigger sentence for that meal (`suggestions`), when one fired. Never a rule the engine did not run. |
| "Log dinner" | **MISSING** (the slot) | "Log a meal". |

## Trends ("This week")

| shown | verdict | detail |
| --- | --- | --- |
| Calories per day, seven bars | DERIVABLE | `nutrientTotalsForRange` per day. |
| Bars coloured against a goal; "Avg calories 1,963, 137 under goal" | DERIVABLE (the average), **MISSING** (goal, colour) | Average of the seven; no goal, so one colour and no "under". |
| "Protein hit 3/7 days at target" | **MISSING** | No target. Cut. |
| "Logged meals 26, 22 by voice" | DERIVABLE (count), **MISSING** (by voice) | As Diary. |
| "Weight −0.6 kg since 1 Sep" | **MISSING** | `weight_kg` is one number with no history. Product decision; cut. |
| "What the coach noticed" paragraph (gym days, late dinners) | **MISSING** | A weekly summary generated by the model over the week, with no guard for it, mentioning activity nobody logs. Cut. If wanted: Priya (prompt + guards), Rao (the turn), and it is post-battle. |

## Coach

| shown | verdict | detail |
| --- | --- | --- |
| The thread: earlier messages | **MISSING** | Turns are not stored; `TalkViewModel` keeps them in memory for the session. A `conversations` table is Rao's if wanted; for the demo the session thread is enough and is what today's Talk shows. |
| "Knows your Sept blood report · HbA1c 6.4 · Vit D low" | DERIVABLE | Latest report date and out-of-range values; "below the printed range", never "low". |
| The coach's opening line ("You're on track — protein is the only thing lagging") | **MISSING** | A target claim. Cut. |
| An answer to a question | PRODUCED | `OrchestratorEvent.OwnFigures` then `Answered` (ANSWER, RECOMMEND, SUGGEST). |
| Typing dots | **CONTRADICTED** | 0026: no spinner without a name and a number; the stage list with its counter is the thinking state. |
| Suggestion chips ("Plan dinner under 500 kcal", "after today's gym") | PRODUCED as prompts, **MISSING** as promises | The chip sends a sentence to the ANSWER/RECOMMEND path — fine. Their wording promises a budget and a gym log the system does not have; the chips become the demo-set questions (`demo-utterance-set.csv` rows 6–9). |
| The reply text with figures ("508 kcal left", "478 kcal, 21 g protein") | **MISSING** as shown | Arithmetic over targets. The real reply is the guarded model sentence over the person's own lines. |

## Search / Food detail / Add manually

| shown | verdict | detail |
| --- | --- | --- |
| Search results: name, "1 katori · 180 g", kcal | PRODUCED / DERIVABLE | `FoodLookup.candidates(FoodQuery, limit)` gives matches; `resolveUnit(unit, foodClass)` the grams of a household unit; `nutrientsFor(code, grams)` the kcal. Arjun: a search ViewModel over the injected `FoodLookup` (AppModule provides it). |
| "You log these often" | DERIVABLE | Most frequent `food_id` in `meal_items`. A query. |
| "Nothing matches that yet — say it out loud instead" | PRODUCED | The no-match path. |
| Food detail: "IFCT 2017 food table · cooked" | **FORBIDDEN** | Hard rule 14: no IFCT anywhere. The source is `DataSource.displayName` (USDA SR Legacy / Foundation / the authored recipe). |
| Portion stepper and presets (½, ¾, 1, 1½, 2 katori; grams) | DERIVABLE | `resolveUnit` × quantity; the katori for the food's class from `unit_conversions`. |
| Energy / protein / carbs / fat per portion | PRODUCED | `nutrientsFor(code, grams)`. |
| "Glycaemic load: Low" | **MISSING** | Not one of the eight nutrients; no GI data source. Cut. |
| Per-food advice ("Good pick for you — slow carbs…") | **MISSING** as written; DERIVABLE narrowly | `KnowledgeFacts.find(query, 2)` returns cited rows for a food's tags (Priya's file); that is a quoted fact, not advice. Advice about a plate comes only from a SUGGEST turn. |
| "Add to diary" | **MISSING** | The only write is the LOG turn. Ask Rao: a `UserIntent.LogItems(items)` that skips extraction, or the screen sends `Type("I had <quantity> <unit> of <name>")` which is honest but slow. Until then the search opens the voice sheet with the food name typed. |

## Meal detail / Portion editor

| shown | verdict | detail |
| --- | --- | --- |
| Header: slot, time, name | see Diary | |
| Totals: kcal, protein, carbs, fat | DERIVABLE | `nutrientTotalsForMeal`. |
| Per item: name, portion chip "1 katori · 180 g", kcal, protein | DERIVABLE for a logged meal | `itemsFor(mealId)` and `meal_item_nutrients` per item (a query: per-item energy and protein). **Not on the live `MealResolved` event**: it carries totals only; the per-item nutrients live on `ResolvedItem.nutrients`. Ask Rao: `MealResolved.items` with grams and per-item nutrients (this supersedes the grams-only ask of 23:34). |
| "Logged by voice at 1:42 pm" | **MISSING** (voice) | As Diary. |
| "Portions used your katori (180 ml)" | DERIVABLE, corrected | The bundled katori is 150 g for pulses (`household-units.csv`); "your katori" is the bundled default, said as such (0035: "taken as"). Never "learned". |
| "Confidence is high on roti and dal, medium on curd" | PRODUCED as bands | `MealItemEntity.confidence_band` per item: Good / Approximate / Rough, the app's own words, never "high / medium". |
| "Delete meal" | **MISSING** | `deleteItem` exists; no `deleteMeal`, no intent. Ask Rao: a DAO delete and `UserIntent.DeleteMeal(mealId)` (with the stored advice). |
| "Edit portions", the stepper, "Save portion", "remembers your katori" | **MISSING, DESCOPED** | `CorrectValue` returns NotImplemented; `CalibrateUnit` is post-battle (Rao 18:40). The chip is not a control this week. |

## Reports / Marker detail

| shown | verdict | detail |
| --- | --- | --- |
| A report card per scan: "Scanned 12 Sep" | DERIVABLE | `lab_values` grouped by `report_date`. |
| "Full blood panel", "Apollo Diagnostics" | **MISSING** | The extractor reads rows, not a title or a lab name. Cut. |
| "Active" badge | **MISSING** | No such state. Cut. |
| Marker: name, value, unit, printed range | PRODUCED | `LabValueEntity`. |
| Marker flag: "Prediabetic", "Low", "Slightly high", "Normal" | **FORBIDDEN** as worded; DERIVABLE as below / above / within | Spec 15.1 and 0018: the range is the report's; the app says "below the printed range", never a diagnosis or a degree. |
| "How this changes your day", three bullets | DERIVABLE, narrower | The trigger sentence of the last evaluation (`AdviceStore.latest(lastMealId).trigger`): one sentence, the engine's own. The three promises in the design are copy about rules that do not exist. |
| Marker detail: "6.4 % · prediabetic range" | **FORBIDDEN** | "above the printed range". |
| "Last four tests" bars | DERIVABLE | `LabValueDao.history(testName)`: every value for that test by report date. With one report scanned there is one bar, shown as one. |
| The explanation paragraph ("HbA1c is your average blood sugar…") | **MISSING** | No sourced text per marker exists; spec 15.1.5 says lab explanations cite the range on the report. If wanted: Priya's facts file gains a `marker` kind with citations, Nila reviews the language, and it is post-battle. Cut. |
| "Add another report · Photo or PDF" | PRODUCED (photo) | Cut "PDF". |

## Nudges (notifications)

| shown | verdict | detail |
| --- | --- | --- |
| Every row (protein short, sugared coffee, recheck due, 12-day streak, late dinners) | **MISSING** | No scheduler, no nudge rules, no streaks, no recheck dates, no meal timing rules. The screen is cut. |

## You (profile) and its lists

| shown | verdict | detail |
| --- | --- | --- |
| Name, avatar initial | **MISSING** | No name field. |
| "32 · 74 kg · Hyderabad" | PRODUCED (age, weight), **MISSING** (city) | |
| Goals list: calories, protein, carbs, fat, water, weekly weight change | **MISSING** | The entire list. The `goal` kind (maintain / lose / gain / muscle) is the one row that exists. |
| Reminders list | **MISSING** | Cut. |
| Language list: English on, Telugu ready, Hindi ready | PRODUCED (speech language, unpersisted), DERIVABLE (which checkpoints are staged: the pre-flight already reads the models folder) | "Mixed speech: On" cut (0022). The interface language is the system's per-app locale: a button to `Settings.ACTION_APP_LOCALE_SETTINGS` (Nila 17:08). |
| Portion reference: katori 180 ml "learned from 34 corrections", roti 45 g, glass, mutthi, spoon | PRODUCED (bundled defaults), **MISSING** (learning) | `unit_conversions` defaults; nothing writes overrides. Shown as the bundled defaults, no "learned". |
| Privacy: "On-device voice: On / Off" toggle, "Cloud" | **CONTRADICTED** | There is no cloud mode; the build has no INTERNET permission. A toggle would imply the opposite. It becomes a static line: the offline mark, worded as a fact. |
| "Share with Dr. Meera Rao · Monthly" | **MISSING, FORBIDDEN** | Network. Cut. |
| "Export my data · CSV" | **MISSING** | Possible on-device (a file); nobody owns it; post-battle. |
| "Delete everything" | **MISSING** | No wipe; Rao's (`deleteDatabase` + the models are not the user's). Post-battle. |
| Help rows | PRODUCED as copy | Static keys; "Contact support · Chat" cut (network). |
| Version | PRODUCED | `BuildConfig.VERSION_NAME` (the pre-flight shows it). |
| Edit details: name, age, weight, height, doctor | PRODUCED (age, weight, height), **MISSING** (name, doctor; and the write path) | |
| "Sign out" | **MISSING, FORBIDDEN** | No accounts. Cut. |

## The voice sheet (Listening / Analysing / Result / Portion)

| shown | verdict | detail |
| --- | --- | --- |
| Level bars while listening | PRODUCED | `AudioLevel(rms)`. |
| "Listening · English" | PRODUCED | Stage + chosen language. |
| Transcript appearing word by word | **MISSING** | Whole-utterance ASR (0021): the transcript lands whole at `Transcribed`. The sheet shows the meter, then the sentence at once. |
| Stages: "Heard you / Matched foods / Weighing portions / Totalling nutrition" | PRODUCED | `Stage.*`, the 0026 list. |
| Stage details: "3 items from the IFCT food table", "Using your katori · 180 ml", "412 kcal · 18 g protein" | **FORBIDDEN / MISSING** | IFCT (rule 14); the katori and the totals are not known until `MealResolved`. Stage rows carry the stage name and the counter (0026), no invented detail. |
| Result: total kcal, protein, carbs, fat | PRODUCED | `MealResolved.figures`. |
| Result: per item name, portion chip, kcal, protein | **MISSING on the event** | `MealResolved` carries `ParsedMeal` (name, quantity, unit) and totals; grams and per-item nutrients stay on `ResolvedItem`. Ask Rao (supersedes 23:34): `MealResolved.items: List<ResolvedItem>` or the parts. Arjun: `Entry.Plate` carries them. Until then: items as said, totals, and the "taken as" caption when the grams land. |
| "Portions use your katori (180 ml) from earlier logs" | DERIVABLE, corrected | The bundled default, per 0035's wording. |
| "Discard / Add to today" (review before saving) | **MISSING as behaviour** | LOG writes at `SAVING` before the plate is shown; the design confirms first. The non-writing path exists (SUGGEST, `hypothetical = true`). Ask Rao whether LOG can be split into resolve → confirm → save without a second model call; if not, the sheet shows "Logged" as today, and "Discard" is `DeleteMeal` (above). |
| Portion editor | **DESCOPED** | As Meal detail. |

## Global: the shell, the tabs, the theme

| shown | verdict | detail |
| --- | --- | --- |
| Five tabs: Today, Diary, mic, Coach, You | DERIVABLE | Navigation is Arjun's `Shell()` routing; the layout mine. "Coach" is the Talk screen's ANSWER path; the mic opens the voice sheet from any tab. |
| Dark palette (#0E1312 ground, lime #C8F169, teal #6FB79A, orange #E58C5A) | **CONFLICTS WITH A RULING** | Vedant ruled the brand green-on-cream with the orange accent, light-only for the battle (20 Sep). The new brief asks for a working light and dark switch. I build the design's dark as the dark scheme and the brand's cream as the light scheme; **which is the default on stage is Vedant's call**. |
| Instrument Sans + Instrument Serif via Google Fonts | **FORBIDDEN as loaded**; DERIVABLE as assets | A downloadable font is a network call. Both are OFL and can ship in `res/font/`; neither has Devanagari or Telugu, so Plex Sans Devanagari stays the text face and Instrument Serif is bundled for the display numerals and headings only if Vedant wants the serif. Its digits must be checked for tabular widths before any figure is set in it. |
| The lockup in lime on dark | DERIVABLE | Recolour the wordmark drawable per scheme (cream on green for light, lime on dark for dark). |
| Status bar "9:41" mock | — | The system's. |

---

## The counts

Counted from the tables above by a script, not by hand: **110 rows**. By the verdict a row leads
with: PRODUCED **20**, DERIVABLE **28**, MISSING **58** (15 rows carry two verdicts, e.g. a
numerator that exists over a target that does not, and are counted by the first). Of the
MISSING rows, **14 are FORBIDDEN or CONTRADICTED** by a standing ruling (accounts, OTP, cloud
mode, doctor sharing, support chat, sign-out, IFCT, the condition-naming flags, "Mixed" speech,
"speech and replies", the typing spinner, the reply arithmetic) and **2 are DESCOPED** by an
earlier ruling (portion editing and calibration; the sensor tables are empty by ruling and are
not on any screen).

## What is asked of whom (the brief)

**Rao** (contract and store): (1) `MealResolved.items` with grams and per-item nutrients —
supersedes the 23:34 grams ask, same line. (2) `ProfileEntity.name` and
`ProfileEntity.speech_language_tag` (Jacob's 05:40), Room v3. (3) `MealEntity.source`
(SPOKEN / TYPED). (4) `deleteMeal(mealId)` and `UserIntent.DeleteMeal`. (5) A profile write:
`UserIntent.UpdateProfile` or a `ProfileStore` seam. (6) A ruling on LOG as resolve → confirm →
save. (7) A read of today's and the week's totals outside a turn, or Arjun reads the DAO.

**Arjun** (state and ViewModels): `TodayViewModel` (today's totals, last meal, stored advice on
it, out-of-range labs), `DiaryViewModel` (meals per day, per-meal totals, per-item lines),
`TrendsViewModel` (seven daily totals, counts), `ReportsViewModel` (labs grouped by date,
`history(testName)`), `SearchViewModel` (`FoodLookup.candidates`, `nutrientsFor`,
`resolveUnit`), `ProfileViewModel` (profile fields, conditions, the language, the bundled units).
`Entry.Plate` carries Rao's items. The voice sheet's state is `TalkViewModel` unchanged.

**Priya**: the conditions vocabulary for the chips, the goal kinds in plain words, and whether
a meal slot by hour is a rule she will stand behind.

**Nila**: about forty English keys for the new screens (all after the freeze, all English-only),
the OFL rows for any bundled Instrument face, and the wording of every flag that the design
wrote as a diagnosis.

**Vedant**: the default scheme on stage (the design's dark or the ruled cream); whether
Instrument Serif joins Plex; and the order in which the MISSING product decisions (name, water,
targets, slots, delete, export) are taken up after the 25th.

## What the build will therefore show, and leave empty

Talk / voice sheet: everything PRODUCED today, the per-item rows the hour Rao's event lands.
Reports: values, units, printed ranges, below/above/within, the history per test, the last
trigger sentence. Home: the date, today's energy and macros as numbers (no ring fraction, no
"left"), the last meal, the stored advice on it, the out-of-range labs. Diary: meals by day
with times, items, portions and kcal; the week strip as logged/not. Trends: seven daily totals
and the counts. You: age, weight, height, goal kind, conditions, speech language, the bundled
units, the offline fact, version, help. First run: language → about you → conditions → report
(optional) → mic → ready. Empty, deliberately: the greeting's name, the ring's goal, water,
nudges, streaks, reminders, per-food advice, marker explanations, the weekly summary, accounts.

---

## Overrides, ruled 21 September, so nobody re-adds them from the design

Vedant's rulings on the fourteen, and two amendments. Each is one line and one reason. The
visual treatment of every overridden element stays exactly as drawn; only what it says or
where it gets its words changes.

### The network seven (the demo build has no INTERNET permission, 0004; the whitelist is three)

| as drawn | built as | reason |
| --- | --- | --- |
| Phone number → OTP → account | The welcome page pixel for pixel; "Sign in" shows one honest sentence (a prototype, no account is created, nothing leaves the phone) and goes straight in; the profile-details step happens locally in the OTP screen's slot | no SMS, no server; amendment 1 |
| "I already have an account" | The same one-sentence notice, then in | no accounts |
| Sign out | Removed; the row's slot is empty | no accounts |
| Cloud sync / "On-device mode" toggle | Removed, no disabled switch; the same slot carries the offline statement the product makes | a toggle implies a cloud mode that does not exist |
| Share with Dr. … · Monthly | An Android share sheet handing over a file the app generated on the device | no upload; the permission set must stay the three (`verifyDemoDebugPermissions`) |
| Contact support · Chat | Cut; the row becomes a Help page built from content shipped in the app | no network |
| "Keep your log across devices" | Cut | no server |

### The clinical seven (spec 15.1; 0018; rule 14)

| as drawn | built as | reason |
| --- | --- | --- |
| Flags "Prediabetic", "Low", "Slightly high", "Normal" | Same chip, colour, position; the words are the report's: "above the range printed on your report", "below the printed range", "within the printed range" | the app never originates a judgement; attribution is what makes it safe, not a disclaimer |
| "6.4 % · prediabetic range" | "6.4 % · above the printed range" | as above |
| A report's comments or impression | Shown in quotation marks as "Your report says: <exact words>" **only once `LabReport.comments` exists** (Priya; today the extractor reads value rows and the date only) | the report speaking, the app repeating |
| "IFCT 2017 food table" | `DataSource.displayName` (USDA SR Legacy / Foundation / the authored recipe) | not licensed to name IFCT; hard rule 14 |
| "Mixed" speech | Removed from the language list | one checkpoint per chosen language, 0022 |
| "Speech and replies" | "Speech" | replies are English whatever is spoken, 0019 addendum 7 |
| The coach's typing dots | The stage list with its name and counter | no spinner without a name and a number, 0026 |

### Out, with no replacement (ruled 21 September, midday)

No placeholder and no dimmed control stands where these were drawn; the slot closes up.

| as drawn | built as | reason |
| --- | --- | --- |
| "Reverse prediabetes" as a goal chip (first run, About you) | Not drawn; the goals are the domain's four (Maintain, Lose weight, Gain weight, Build muscle) | no source for the target it implies, and a clinical promise the app cannot make (spec 15.1) |
| Sign out (You) | Not drawn | no accounts exist; the local first run replaced sign-in (amendment 1) |
| "Glycaemic load · Low" (the food page) | Not drawn | nothing in the database produces it |
| The row notes under You's lists ("Raised because your protein ran short…", "Capped for an HbA1c of 6.4", "Learned from 34 corrections") | Not drawn; a row is its label and its value | no source: each note is a claim the system does not make. The one note that stays is "Bundled default" under a household unit, which is an attribution the store requires (0035), not a design note |
| "Add to today" (the voice sheet) | "Saved" | the pipeline saves at MealResolved and that is ruled right; the pill says what happened, Discard beside it deletes it. No confirm step is asked of the contract |
| The microphone as a tap | Press and hold, release to end (0031) | ruled on measurement: the energy endpointer a tap needs scored 8/10 in a quiet room and 0/10 in babble; the hold survives a 1.5 s pause and a late sentence (Rao, 21 Sep). The sheet's label carries it: "Hold to speak" until MicrophoneLive, "Listening · <language>" after, the five-word hint under it |
| The lab's name on a report card ("Apollo Diagnostics") | Absent until the extractor reads it off the sheet; then the sheet's own text, as the report wording is | attributed or absent; no invented field (asked of Priya, 21 Sep) |

### Also ruled

- Every digit in IBM Plex Sans whatever face the design sets it in; the label may be Instrument
  Serif, the number is Plex at the same optical size (tabular digits do not jitter on a projector).
- The safety wording appears wherever a reading or a suggestion appears, in the design's style;
  it is the attribution, not the disclaimer, that makes a reading safe.
- Targets are computed by the rules engine from the profile, never by the model; the shape is
  in COORDINATION (21 Sep) and the home components are built against it and render empty until
  it lands.
- System notifications need `POST_NOTIFICATIONS`; nudges are an in-app list unless Vedant adds
  the permission with a written reason.
