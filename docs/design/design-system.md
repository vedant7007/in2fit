# The IN2FIT design system

What is in `ui/theme/` and `ui/components/`, what each piece is for, and when not to use it.
Written by Ira as the code was written, 20–21 September 2026, from the findings in
`ui-research.md`. Every value here is also the value in the code; if they disagree, the code is
wrong and this file says what it should be.

The rules it exists to keep, in one place:

- **Green on cream carries every word.** `#252F26` on `#ECEBE6`, 11.63:1. The accent `#E5522D`
  (3.15:1 on the ground) carries marks and one 28 sp number; never text under 24 sp, never a filled
  button with a small label. `accentInk` `#B83D1B` (4.73:1) exists for one small word at a time.
- **Light only** for the battle (ruled 20 Sep). Every token is named so a second value per token
  is a dark scheme later.
- **Nothing animates during inference** except the seconds counter, once a second. Motion is
  driven by real data (the voice's level) or absent.
- **No literal in a composable**; every string is a key. Components take strings; screens look them up.
- **No new state.** A component takes what an `Entry` already carries; a visual idea that needs a
  field is an ask to Arjun, not a field.

## Tokens (`ui/theme/Theme.kt`)

### Colour, `In2fitColors`

| token | value | for | not for |
| --- | --- | --- | --- |
| `ground` | `#ECEBE6` | the screen | a card |
| `ink` | `#252F26` | every word, every figure, the wordmark, filled controls | large areas other than the person's block and the mic |
| `inkSecondary` | `#55635A` (5.30:1) | labels, units, dates, the source line, done stages, unchosen tabs | body text |
| `raised` | `#F6F5F1` | a card that is a thing: a plate, the figures, a report field, advice | prose blocks (those sit on the ground) |
| `person` / `onPerson` | `#252F26` / `#ECEBE6` | the person's own words, the one dark block | anything the app says |
| `hairline` | `#D1D0C8` | rules between rows, the tab bar's top edge | boxes around things |
| `selected` | `#D3DAD3` | the chosen language chip; Material's selected containers | emphasis |
| `accent` | `#E5522D` | the level bar, the current-stage dot, the counter, the chosen tab's rule, a referral's left rule, a range dot | text under 24 sp; a filled button |
| `accentInk` | `#B83D1B` | one small word ("below" beside a lab value) | a sentence |
| `error` | `#B3261E` | the scripted-feed banner | anything a person sees in the demo build |

The Material `colorScheme` is mapped onto these (primary = ink, surface = ground, surfaceVariant =
raised, outlineVariant = hairline, secondaryContainer = selected) so a screen written against
`MaterialTheme.colorScheme` (the pre-flight) renders in the palette with no edit.

### Type, `In2fitText`

One family: **IBM Plex Sans Devanagari**, three static weights as assets (`res/font/plex_*.ttf`,
OFL). It carries Plex's Latin and its Devanagari in one file (x-height 516/520/522 in both,
measured), and its digits are tabular by default (every digit 600 units), so a column of figures
aligns with no feature switched on. Telugu falls back to the system's Noto Sans Telugu. Every
style has `includeFontPadding = false` and centred line height, so tall scripts get their room
from the line height, not the font's padding.

Five sizes and one number:

| style | size / line | weight | for | not for |
| --- | --- | --- | --- | --- |
| `hero` | 36 / 40 | SemiBold | one figure per card at most: energy on a plate | any word |
| `title` | 28 / 34 | SemiBold | a screen's title; the intent heading; the running counter (in accent) | a card label |
| `transcript` | 20 / 30 | Regular | the person's words, in their script; the empty-state invitation | the app's sentences |
| `figure` | 20 / 28 | SemiBold | a figure's value in a row, never wrapping | a number inside a sentence |
| `body` | 16 / 24 | Regular (Medium for the trigger sentence) | health text, answers, the current stage | labels |
| `bodySmall` | 14 / 20 | Regular | items, candidates, the lead-in, chips, tabs, done stages | the answer |
| `label` | 12 / 16 | Medium, `inkSecondary` | a label over a block, a unit, a date, the safety line, the offline mark | anything a judge must read from a metre away |
| `button` | 16 / 20 | Medium | a control's word | a heading |

The Material `Typography` roles are mapped onto these (bodyLarge = body, labelMedium = label, …)
so unrestyled screens inherit the face and sizes. There is no 11 sp and no 22 sp anywhere.

### Spacing, `Space`

4 / 8 / 12 / 16 / 24 / 32 (`xs s m l xl xxl`). Screen gutter 16; inside a card 16; between rows 8;
between a label and its block 4. Nothing is 2, 6 or 10.

### Shape

8 dp on a small control, 16 dp on a card, a full pill on the mic. No elevation anywhere:
hierarchy comes from the tint (raised on ground, person on ground) and a hairline.

## Components (`ui/components/`)

### Chrome.kt

- **`Hairline`** — the one separator. Not for boxing things.
- **`Label`** — 12 sp secondary label over a block.
- **`ScreenTitle`** — 28 sp.
- **`Raised`** — the card that is a thing (plate, figures, report field, advice). 16 dp corner, 16 dp
  padding, no shadow. Not for prose.
- **`SafetyLine`** — spec 15.3's line, once per screen, pinned above the controls. Never inside a
  card, never repeated per card.
- **`OfflineMark`** — the static no-INTERNET line at the top of Talk (`talk_offline_mark`). It never
  changes; it is compile-time true of the demo build.
- **`BottomTabs`** — three words, the chosen one in ink with a 24×2 dp accent rule under it; 56 dp
  rows, a third of the width each. Replaces the Material `NavigationBar` and its pill around an
  icon that was never drawn.
- **`PrimaryButton`**, **`SecondaryButton`** — the one filled control on a screen (56 dp pill, ink,
  cream word) and the hairline pill beside it. Never in the accent. The mic is not one of these.

### Turn.kt

- **`SaidBlock`** — the person's words, verbatim, 20/30 on the dark block, "You" as a faint label.
  `doubtful` draws an accent rule under the words; nothing sets it until the ASR confidence reaches
  the entry (asked). Never used for anything the app says.
- **`IntentHeading`** — 0026 step 5: "Logging / Answering / Suggesting / Recommending" at 28 sp, the
  lead-in phrase under it in 14 sp secondary as it is spoken.
- **`StageIndicator`** — 0026 step 6 with no bar: done stages ticked (a drawn two-stroke tick in a
  24 dp column, 14 sp secondary), the current stage with a 10 dp accent dot at 16 sp, and the
  seconds at 28 sp tabular accent, right-aligned in an 88 dp box so digits never move the line.
  `elapsed` is a lambda read here and nowhere else: the 1 Hz tick recomposes this composable
  only. Not to be shown without a current stage; not a spinner.

### Figures.kt

- **`FigureLine`** — name, value (with its unit, one token), band. `fromRendered(line, band)`
  splits the contract's rendered line at the first ": ", exactly as `context_figure` writes it
  (`ponytail:` a locale that reorders that format needs the parts field, asked 21:01). Shaped to
  take the parts the hour they are carried on the entry.
- **`FigureRow`** — name left in 14 sp, value right in 20 sp semibold, `softWrap = false` on the
  value: a number never wraps (the deck's "14 / 2"). `exception` names the band only when it
  differs from the card's.
- **`contextFigures(line)`** / **`ContextLines`** — the store's own lines (`OwnFigures`,
  `Answered.figures`): a period line ("Today so far: energy: …; protein: …") becomes a heading over
  figure rows; a lab or meal line, whose shape is a sentence, is shown as the sentence. All or
  nothing per line, so a sentence never becomes a wrong row (`ContextFiguresTest`). `ponytail:`
  shape-sniffing a rendered string; the parts field replaces it.
- **`FigureList`** — rows with hairlines; the band said once at the top right when every row
  shares it; an optional `hero` figure at 36 sp above the rows.
- **`PlateCard`** — the deck's promise: the energy as the hero, the items as said on one 14 sp
  line, the other figures as rows, the band once, the "Logged" / "not logged" line as the label.
  Finds the energy row by comparing the nutrient word to `nutrient_energy` from the same table,
  so it is locale-safe.

### Cards.kt

- **`Prose`** — a label and text on the ground, for what the app says. Not for a plate.
- **`Referral`** — the fixed doctor line with a 3 dp accent rule at its left; the words in ink.
- **`AdviceCard`** — raised: the engine's trigger sentence first, in medium (it is the proof); the
  model's phrasing in regular; the referral; the candidates as hairlined rows.
- **`AnswerCard`** — prose: the sentence, or the refusal line over the person's figure rows.
- **`AskCard`** — the question is the buttons: 48 dp full-width rows with hairlines on a raised
  surface; the question text small above them. Never a row of pills of different widths.
- **`ConfirmCard`**, **`FailedCard`** — one sentence each; the failure detail appears in label
  style on a tap, not by default (0026: never the detail).

### Report.kt

- **`ReportField`** — one lab value on a raised card: the test name, the value at 20 sp with its
  unit at 12, and the printed range drawn as a hairline between the printed low and high with
  the value as a dot on it. Outside the line the dot sits past the end in the accent and the word
  "below" / "above" (`scan_below_range`, `scan_above_range`) stands beside the value in
  `accentInk`. No range printed, no line, the sentence instead. The source row under it in the
  label style, as read. The whole card toggles the tick.

### Splash.kt

- **`Splash(ready, onFinished)`** — the wordmark PNG breathes open (`scaleX` 0.72 → 1.0, ease-out,
  1.2 s), the tagline fades in as its own element, from 3.5 s both contract and fade, and it
  loops; every property is a `graphicsLayer` transform. It never gates the app, ends at the next
  expand once `ready()` is true, and has no minimum duration. `Shell()` passes `ready = { true }`
  until the 0028 warm-up signal reaches the screen, so today it is one breath and gone.

### Mic.kt

- **`MicButton`** — 72 dp pill, full width, ink with cream label. Held: `onPress` starts the turn,
  `tryAwaitRelease` then `onRelease` (a no-op until `UserIntent.EndSpeech` lands). While `active`,
  a 6 dp accent bar along the pill's bottom edge grows with `level()`, read in `drawBehind` so a
  30 Hz RMS never recomposes anything; it freezes at the endpoint because the level stops
  changing. The bar never sits under the label, so the label stays 11.6:1. With `stop` set, the
  pill is a tap that stops speech only (0026 step 8). Disabled: hairline fill, secondary label.
  Haptic on press. Not for anything but the microphone.

## How the Talk screen is assembled (`ui/TalkScreen.kt`)

Top: `OfflineMark`. Middle: one `LazyColumn` of entries rendered by `EntryView` (one `when` over
`Entry`), with the `StageIndicator` as the last item while a turn runs, so the transcript sits
just above it (0026 step 3) and the answer lands where the indicator was. Bottom, in the thumb's
third: `SafetyLine` once, "Advise again" as a text button when there is a last meal, the
`MicButton`, the language chips with `talk_type_instead`, and the typed fallback only when opened
or when the microphone was denied.

Three reads of the one `State`: the layout (a copy with `level` and `elapsedSeconds` zeroed,
`distinctUntilChanged`), the level as a `State<Float>` read only in the mic's draw lambda, the
seconds as a `State<Int>` read only in the stage indicator. So a level or a tick never recomposes
the list.

## Motion (`ui/theme/Motion.kt`)

The inventory first, decided on paper: every moment state changes on screen across the four
beats, and whether motion clarifies what just happened or decorates it.

| moment | verdict | what it does |
| --- | --- | --- |
| mic pressed | clarifies "held" | scale 1 → 0.97 on the spatial spring, back on release; haptic; the level bar (real data) |
| level bar while recording | real data | `drawBehind`, read in the draw phase, frozen at the endpoint |
| stage advancing, tick and dot | during inference | **no motion**; the list changes with no layout animation |
| counter ticking | a number | none, one `Text`, once a second |
| diary figures at 0.5 s | numbers | **none**; the number never waits a frame |
| plate card resolving | numbers | **none** |
| transcript, intent heading, ask, confirm, failed, advice, answer | words arriving | `arrive()`: alpha 0 → 1 and 8 dp of rise on the effects spring, once per item, `graphicsLayer` only |
| the sentence landing in the slot | words arriving after inference | the same entrance; the stage indicator leaves as the answer arrives |
| tab change, chip, "Type instead", Stop label | repeated actions | instant (Kowalski's rule: never animate the repeated thing) |
| splash → Talk | the one handoff | 250 ms veil fade; the breathing is `graphicsLayer` scale and alpha |
| reduce motion on | — | `LocalReduceMotion`: every entrance and the press off, the splash at its end state and gone as soon as the app is ready |

Tokens: `Motion.spatial` = spring 700 / 0.9 (Material Standard), `Motion.effects` = spring
1600 / 1.0. Both interruptible; nothing blocks input; nothing delays a figure.

### Measured on the emulator, 21 September, feed on (`logs/gfx-*.txt` in the ira tree)

`Medium_Phone`, API 37, x86_64 host running arm64 under translation, `swiftshader_indirect`
software GPU. `dumpsys gfxinfo <pkg> reset` before each beat, the summary and `framestats` after.

| window | frames drawn | jank by the 16.7 ms bar | 50th / 90th percentile | worst frame, where |
| --- | ---: | ---: | --- | --- |
| Talk idle, 10 s | **0** | — | — | nothing drawn: a static screen costs nothing |
| Beat 1, LOG turn, 22 s | 39 | 35 (90 %) | 105 / 250 ms | 688 ms at t+1.7 s, 645 of it in the renderer; worst UI-thread frame 229 ms at t+2.6 s: transcript + heading + stage list arriving |
| Beat 2, ANSWER turn, 22 s | 40 | 39 (98 %) | 73 / 150 ms | 403 ms at t+3.1 s, 336 of it UI thread (206 draw-record): the eight diary rows composing at once |
| Beat 3, Scan with the report, 15 s | 35 | 35 | 109 / 150 ms | the camera preview and the five report cards |
| Beat 3, save, 8 s | 12 | 12 | 200 / 350 ms | the saved line and the cards re-laid |
| Beat 4, advise again, 12 s | 39 | 20 (51 %) | 38 / 133 ms | the advice card arriving |
| cold start with the splash, 40 s | 23 | 14 | 65 / 500 ms | the first frame of the app |
| cold start, reduce motion on | **4** | 4 | — | the splash at its end state, straight to Talk |

What these numbers do say: the UI draws only on events. A turn of 22 s draws about 40 frames,
which is the level bar's twelve updates while recording, the stage changes, the entrances, and
the counter once a second; between events nothing is drawn. The worst frames are the arrivals
of many text nodes at once (the plate's nine rows, the diary's eight), which the figures-first
rule requires to appear together; the counter's tick is a single frame of 13–55 ms UI-thread
time here.

What they do not say: anything about the 8.3 ms or 16.7 ms budget on a phone. Every frame is
"janky" by that bar because the software renderer alone takes about 40 ms a frame and the UI
thread is arm64 code under translation; the medians are the emulator's cost, not the design's.
Whether a dropped frame coincides with inference cannot be measured here at all: the feed runs no
model. Both belong to the device pass (`dumpsys gfxinfo framestats` on the realme over one real
LOG turn, before `68d8a34` and at the current commit), asked of Rao.

One consequence worth carrying to the device pass: the plate card is nine `FigureRow`s of two
to three `Text` nodes each. If the device's worst frame is the plate's arrival, the row collapses
to one `Text` with an inline span; not before a number says so.

## Not yet in the system

The katori's gram assumption (ruled e; `plate_unit_taken_as` exists, the grams do not reach the
entry). Priya's rule for it, ruled 20 Sep: "taken as" is shown only when the amount was ASSUMED
(`HOUSEHOLD_UNIT_DEFAULT`); "200 ml of milk" is the person's own number and the card shows it as
they said it, with the conversion behind the band's detail, never beside the item, `Said.doubtful` (waits on the ASR confidence), the figure row's separate unit size and
the tap-to-explain of confidence reasons (both wait on the parts), the splash's real `ready`
(waits on 0028's warm-up reaching the screen). Each is listed so nobody builds it twice.
