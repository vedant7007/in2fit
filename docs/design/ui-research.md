# UI research for IN2FIT

Phase 1 of the design work. Written 20 September 2026 by Ira, the seventh session, who owns how
the app looks and moves and nothing else. Nothing in this file is built; Vedant reads it before a
line of Compose is written.

The bar set for it: every claim names an app, a pattern or a measurement. Where I measured
something myself it says so and says how. Where a number comes from outside, the source is in the
list at the end. Where I could not verify a thing I say I could not, rather than round it up.

What I read before writing: `HANDOVER.md`, `COORDINATION.md` end to end (including Arjun's 20:20
and 20:23 notes to me, and the rulings of 20:25–20:31), `STATUS.md`, `0026-the-spoken-turn-on-screen`,
`0027`, `0028`, `docs/localisation/string-conventions.md`, `docs/demo/run-of-show.md`,
`docs/demo/countdown.md`, spec §15, and every file under `ui/` plus `res/values/strings.xml`,
`themes.xml`, `colors.xml`. What I looked at: all fourteen screenshots in
`docs/screenshots/2026-09-20-shell/`, `App_logo.png` at the repo root, and the 14:07 deck PDF
(nine pages; the corrected deck is not on this laptop, per Nila 17:08).

---

## 0. The constraints that decide the design, before any taste does

These are the facts the screens have to be built around. Each one changes a design decision;
none of them is negotiable by me.

| fact | where it comes from | what it does to the design |
| --- | --- | --- |
| Beat 1: **9.2 s to the first figure, 19.0 s to the spoken sentence** (two-food sentence, ruled Beat 1 at 20:28). Three foods was 28.7 / 39.3 s. | run of show; Nila 20:28 | A ten-second wait with a named stage and a counter, twice per turn. The counter is the design's honesty device (0026). |
| Beat 2: **own figures on screen at 0.55 s**, the model's sentence at 9.2 s. Beat 4: **0.47 s**, no model call. | run of show (measured 20 Sep 19:41) | The figures card is the answer; the model's sentence is a caption that arrives later. The layout must be complete at 0.55 s and not rearrange itself at 9 s. |
| **No token streaming.** The numeric guard and `SafetyLine` run over the whole generated text, and a refused answer is replaced by a fixed line. | `LlamaCppLlmEngine`, 0024 | The answer lands whole, once. No typewriter effect; no partial sentence may ever be shown. |
| **Whole-utterance ASR.** `AsrEvent.Level` while speaking, `SpeechEnded` at the endpoint, then one `Result`. No partial transcripts. | `AsrEngine.kt`, 0021 | There is no "transcript still being written" state to design. The honest listening feedback is the level meter, then a flat line, then the words all at once. |
| **Hold-to-speak** is ruled and ahead of the splash: press sends `Speak`, release sends `EndSpeech`; under 300 ms is `INPUT_NOT_USABLE`. | Arjun 20:15, Jacob 19:30 | The mic is a held control, not a tapped one. The gesture India already knows is WhatsApp's (§2). |
| **Output is English on screen and aloud whatever is spoken**; the interface is English for the demo; Telugu is a toggle only if Part 1 of the packet is confirmed. | 0019 addendum 7, Nila 16:44 | Devanagari appears in one place on the demo screen: the transcript of the presenter's Hindi. Telugu appears in the chip endonym. Both must render well; neither is the body text. |
| **Every string is a key; keys added after tonight 22:00 are English-only in the demo build.** No literal in a composable. | 0017, string-conventions | New copy is expensive and must be minimal. The design should need at most a handful of new keys, all English. |
| **No INTERNET permission; a test fails the build if a dependency adds one.** A downloadable font is a network call. | 0004, Arjun 20:23 | A typeface ships in `res/font/` as an asset, or the system face is used. |
| **The demo phone is the loaner iQOO, unknown until it arrives; the realme RMX3780 is the backup**: 6.72-inch IPS LCD, 2400×1080, 120 Hz, 550 nits (source list). | Nila 20:28, Meera 20:25 | Design for a bright hall on an LCD: high contrast, no reliance on true black, no OLED-only tricks. Both panels are 120 Hz class. |
| The **emulator** used for screenshots is `Medium_Phone`, 1080×2400 at 420 dpi = **411×914 dp**, the same dp geometry as the realme. | Arjun 15:11; my arithmetic | Layout decisions made on the emulator transfer to the realme at the same dp; the iQOO is a question until Rao has it. |
| The brand: **`#252F26` on `#ECEBE6`, accent `#E5522D`**. Logo is a PNG (1536×1024), no SVG, no trace; the launcher icon is closed (Nila's bowl in `#252F26`). | Arjun 20:15, 20:20, 20:23 | See §6 and §8 for what the contrast arithmetic does to the orange. |
| The 14:07 deck is set in **IBM Plex Sans** (body) with a geometric display face for headings, on near-black with cream and orange; the corrected deck moves to the green/cream palette. | the PDF on disk | If the app and the deck are to look like one product, the app's typeface is the deck's body face (§6). |
| The split with Arjun: I own `ui/theme/`, `ui/components/`, the splash, and then the four screen composables one at a time with a logged handover; he owns every `State`, `Entry`, event, `Sentences.kt`, `MainActivity.kt`'s routing, and `PreflightScreen.kt` ("leave it plain"). | Arjun 20:20, 20:23 | Every visual idea below that needs a field is written as an ask, not a design. The pre-flight screen gets the theme through `MaterialTheme` and nothing else unless Vedant rules otherwise. |

One arithmetic fact that outranks taste, computed with the WCAG 2 relative-luminance formula:

| pair | contrast | passes |
| --- | ---: | --- |
| `#252F26` on `#ECEBE6` | **11.63 : 1** | AAA at every size |
| `#ECEBE6` on `#252F26` | 11.63 : 1 | AAA |
| `#E5522D` on `#ECEBE6` | **3.15 : 1** | large text only (≥ 24 sp regular or ≥ 18.7 sp bold); fails AA for body |
| white on `#E5522D` | 3.77 : 1 | large text only; **a filled orange button with a 14 sp label fails AA** |
| `#252F26` on `#E5522D` | 3.69 : 1 | large text only |
| Compose M3 default `#6750A4` on its surface | 6.28 : 1 | what the shell has today |

So the accent cannot carry body-size text, in either direction. It carries marks: the live
level, the running figure, a range mark, the one moving number. Where it carries text, the text
is 24 sp or larger. That is a good rule anyway (one accent, sparingly), and here it is forced.

---

## 1. What actually ships well on Android in 2026

### 1.1 Material 3 is the baseline, and the shell is its factory setting

Material 3's shipped numbers, which every Compose app inherits unless it overrides them
(`TypeScaleTokens.kt` in androidx, quoted verbatim; the component sizes from the M3 spec):

- Type: bodyLarge 16/24 regular, tracking 0.5; bodyMedium 14/20, tracking 0.2; bodySmall 12/16,
  tracking 0.4; titleLarge 22/28 regular; titleMedium 16/24 medium; labelLarge 14/20 medium;
  labelMedium 12/16 medium, tracking 0.5; labelSmall 11/16 medium, tracking 0.5; headlineSmall
  24/32; headlineMedium 28/36; displaySmall 36/44. Fifteen roles.
- Layout: 4 dp grid, 16 dp screen margin, 48 dp minimum touch target, 40 dp Button, 32 dp
  FilterChip, 56/72/88 dp list rows for one/two/three lines, 80 dp NavigationBar, 12 dp corner
  on a Card, 16 dp card padding.
- Colour: baseline light scheme primary `#6750A4`, surface `#FEF7FF` (the pink-white every
  screenshot has), surfaceVariant `#E7E0EC` (the grey-lavender of every card), secondaryContainer
  `#E8DEF8` (the selected chip), error `#B3261E` (the demo banner).

Every one of the fourteen screenshots is exactly this. `MainActivity.kt:46` is `MaterialTheme {}`
with no colour scheme, no type and no shapes; `themes.xml` is one empty style. The shell is not
badly designed; it is undesigned, and M3's defaults are what "undesigned" looks like on Android
in 2026. A judge has seen this exact purple, this exact pill and this exact card on a hundred
student apps. That is the gap.

Where following M3 helps: the sizes above are correct and I will keep most of them (48 dp targets,
the 4 dp grid, 16 dp margins, the label/body/title roles at their sizes). Where following it makes
everything look like everything: the colour scheme (fixed by the brand), the filled `Card` on
`surfaceVariant` for every block (§7), the `NavigationBar` pill, the `FilterChip`, the
indeterminate `LinearProgressIndicator`. Those are the tells and they all go.

### 1.2 Material 3 Expressive: what to take, what to leave

M3 Expressive (announced May 2025, in Android 16, in Compose Material 3 1.4) replaced
duration-plus-easing with springs as the primary model, split into **spatial** (position, size,
shape; may overshoot) and **effects** (colour, alpha; never overshoot). The token values, read
from androidx source:

| scheme | spatial default | spatial fast | spatial slow | effects default | effects fast | effects slow |
| --- | --- | --- | --- | --- | --- | --- |
| Expressive | stiffness 380, damping 0.8 | 800 / 0.6 | 200 / 0.8 | 1600 / 1.0 | 3800 / 1.0 | 800 / 1.0 |
| Standard | 700 / 0.9 | 1400 / 0.9 | 300 / 0.9 | 1600 / 1.0 | 3800 / 1.0 | 800 / 1.0 |

Take: the spatial/effects split (a fade must never wobble; the most common Expressive mistake per
the Compose motion reference is animating alpha with a spatial spec); the Standard spatial
numbers (700/0.9) for anything that moves; the effects numbers (1600/1.0) for anything that fades.
Leave: the Expressive spatial springs (0.6 damping bounces; health text must not bounce), the
shape-morphing `LoadingIndicator` and the wavy progress indicator (both animate continuously,
which §5 rules out during inference), and the emphasized type scale (a second weight axis the
app does not need). M3E's own guidance puts `LoadingIndicator` at waits under about five seconds;
our waits are ten and twenty.

### 1.3 Apps people use, and what they actually do

**Linear** (2024 redesign, their own write-up): hierarchy from **background tints, not borders or
shadows**; text and neutral icons made darker in light mode for contrast; chroma taken out of
the neutrals so the UI reads "neutral and timeless"; Inter Display for headings and Inter for
everything else; spacing 8/12/24; card radius 12, button radius 6. The principle they wrote down:
"reduce visual noise, maintain visual alignment, and increase the hierarchy and density". That is
the closest published articulation of what this app needs: tint, alignment, one type family,
density where the content is dense.

**Gemini Live** (February 2026 redesign): the assistant collapsed into a floating pill; the
waveform sits in the background of the pill and the transcript above it; mute and end are the
only controls; the pill collapses to a movable circle when you use the phone. The lesson is not
the waveform, it is the reduction: two controls, transcript on top, the animation behind the text.

**ChatGPT voice**: launched with a full-screen blue orb (September 2024); by 2025 the
conversation "stays in the familiar interface" and the orb is an opt-in "Separate mode". OpenAI
moved voice *into* the transcript view. A voice-first screen is a transcript with a microphone,
not a screensaver with a microphone.

**Cronometer** (the dense-numbers benchmark of the category): a vertical list, a colour-coded bar
per nutrient whose colour has a fixed meaning (grey under the minimum, green at target, red over
the maximum), reviewers calling it "functional rather than beautiful". Dense works when every
row has the same shape and the colour means one thing.

**MyFitnessPal** (August 2025 diary redesign): the feedback recorded on their own forum was
"overwhelmingly negative": more steps to log, busier home, moved buttons. The category's most
used app made its log slower by adding to it. Restraint is the finding.

### 1.4 A list of numbers

What the good ones do with a column of figures, and what the shell does instead:

- The name is left, the number is right, in **tabular figures** so the decimal points and units
  form a column (Cronometer, Apple Health's data lists, every bank app). The shell writes nine
  lines of `name: number unit (band)` as prose in 14 sp, so nothing aligns and the eye has to read
  every line to find protein.
- The number is one step larger than its label, the unit one step smaller and lighter, never the
  other way round (Apple Health: the value in a heavier, larger face, the unit small beside it).
- A qualifier that applies to every row is said once at the top, and only the exceptions are
  marked per row. The shell says "(Approximate)" nine times in one card.
- A number never wraps. The 14:07 deck's own page 4 wraps "142" as "14 / 2" in the lab column;
  it is the exact failure a figure component exists to prevent.

---

## 2. Voice-first interfaces: what a screen does while it listens, thinks, and mishears

### 2.1 Listening

**WhatsApp's voice note** is the voice gesture most of India performs daily: hold the mic to
record, slide left to cancel, slide up to lock (added April 2018, still the shape); a timer on the
left, a waveform, release to send. Our push-to-talk (Arjun 20:15) is this gesture minus lock and
cancel: hold to speak, release to end, a 300 ms floor. The design should use the vocabulary the
thumb already has: the control is at the bottom, it is large, it responds to pressure the instant
the finger lands, and the meter is the proof the mic is live (0026 step 1).

**Siri on iOS 18.1+** glows the screen edge while listening, from the side the button was pressed
on or from the bottom when summoned by voice. The lesson is placement, not the rainbow: the
listening state is drawn where the person's hand is.

**Gemini Live** and **ChatGPT** both animate a level-driven shape (waveform, orb) while listening.
The only motion on any of these screens that is not decorative is the one driven by the
microphone's actual level. It is the one animation this app should have while recording, and it
costs nothing during inference because the model is not running while the person speaks.

The design consequence: the mic control itself is the meter. While held, its fill rises with the
RMS the orchestrator already emits (`AudioLevel`), in the accent colour; on release it freezes
(0026 step 2, "the meter freezes into a flat line the instant speech ends"). One element, one
piece of real data, drawn in the draw phase from a state read the composition never sees (§5.4).

### 2.2 Thinking

The field does not have our problem. Gemini answers in one to two seconds; ChatGPT streams
tokens; Siri hands off to a screen. Their "thinking" states are spinners that last under a
second, and they are useless as references for a ten-to-twenty-second wait on a CPU that the
model is using. What is relevant is the research on long waits: Myers (1985) found people prefer
a progress indicator for any wait and tolerate long ones better with it; Bill Chung's 2018 mobile
study found skeleton screens perceived as shorter than spinners, and slow left-to-right motion
perceived as shorter than fast or pulsing motion; Viget's 2017 study found the opposite for
skeletons on the web. None tested past a few seconds. What survives all three: **a named stage
and a moving number beat a spinner**, which is what 0026 already rules, and fast or pulsing
motion makes a wait feel longer, which is why the shell's indeterminate bar (a 4 dp track
sweeping continuously) is the wrong instrument.

The one thing the field does that we should copy is where the answer's place is reserved.
Perplexity puts sources first and the answer streams into a slot beneath; the slot exists before
the text does. Our equivalent is stronger: the figures card (0.55 s) *is* the answer, and the
model's sentence gets a reserved slot under it with the stage name and the counter, so the
layout does not jump when the sentence lands at 9 s. The wait reads as arrival because the thing
that arrived first was the important one.

### 2.3 A transcript that is still wrong

Apple dictation and Google's Recorder show partial words as they arrive and revise them; Otter
marks low-confidence words. We cannot: the recogniser returns one whole utterance (§0). So the
honest design is different from the field's, and better for a demo: the transcript lands whole,
set large, in the person's own script, in quotation marks, and it is never edited (0026: "the
transcript is never edited by us"). The correction path is *say it again*, and the wrong
transcript stays on screen above the new one, because it is the person's evidence of what the
phone heard (the run of show's own line: "it shows me exactly what it heard rather than hiding
it").

One thing the contract could carry and does not: `AsrEvent.Result` has an `AsrConfidence`
(`<unk>` maps to LOW) and `OrchestratorEvent.Transcribed(text)` drops it. A LOW transcript could
be marked (a hairline under the quote, no words) so the presenter sees the doubt before the
extraction runs. That is Rao's contract and Arjun's `Entry`; it is an ask, not a plan.

### 2.4 Mishearing

The good apps do not apologise and do not auto-correct: Gemini Live shows the transcript as heard
above the waveform, and none of the assistants edits one silently. Ours already asks rather than guesses (`NeedsIntent`, `NeedsConfirmation`, the zero-resolve
path). The design job is to make the ask card look like a question the person answers with one
thumb, not like an error: the four intent buttons are the answer, the hint under the mic ("Hold
the button while you speak") teaches the gesture after a too-short hold.

---

## 3. Food logging and health apps, and why they feel like data entry

They feel like data entry because the log is the unit of work and the number is the reward. In
MyFitnessPal the path is search, pick a serving, pick a meal, save, and the diary is the
receipt. Cronometer keeps the receipt and makes it beautiful to a person who wants 84 nutrients;
to anyone else it is a spreadsheet. Ate removed the numbers entirely ("snap a photo of your meal
and you are done"; reflection prompts instead of calories) and its reviewers call it the humane
option for people who left the counters. HealthifyMe's Snap (2023) recognises Indian dishes
from a photo at a claimed 75 % and answers with calories first.

What the good ones do differently, concretely:

1. **The entry is the person's own act, kept visible.** Ate keeps the photo; we keep the sentence.
   "Two rotis and a little dal", in quotes, in their script, is the log. The figures hang off it.
2. **One number, then the story.** The 14:07 deck already drew the shape this app promised
   (page 3): `412 kcal` large; `14 g protein · 62 g carbs · 9 g fat` on one line; `Roti 2 × 40 g ·
   Dal 1 katori, your size` as the items; `Approximate · tap to correct` as one pill. Four lines.
   The shell renders the same meal as twelve lines of `key: value` prose. The deck is the promise
   the judges have already read; the app should keep it, with the database's real figures.
3. **The assumption is on the card, not in a settings page.** "1 katori, your size" is the deck's
   line; the ruling (Rao 18:40) is that the profile is pre-seeded with the bundled utensil
   defaults and the figure carries `HOUSEHOLD_UNIT_DEFAULT`. So the card should say what a
   katori was taken to be, in grams, next to the item, because that assumption is the honest
   thing about the number and it is Indian in a way no other app's log is.
4. **The band is one mark, once.** Spec 15.3: "confidence shown inline with the figure". Inline
   is a mark beside the number and a tap that lists the `confidence_reason_*` sentences, which
   already exist as keys. Not a bracketed word repeated on every line.

What a logged meal looks like when it is a pleasure: the sentence, the number, the story, the
assumption, the band. Then the advice under it as a separate block in a different tone, because
advice and record are different kinds of statement.

---

## 4. Indian mobile expectations

I will not put a number on "density tolerance"; I found no measurement I would defend. What I can
point to, verifiable by anyone in the hall with a phone: PhonePe's and Paytm's home screens are
grids of small-labelled icons, Zomato's list rows carry five or six pieces of information each, and
all three are denser than Google's own equivalents on the same phone. The audience in a Hyderabad
hall is used to dense screens and will not reward whitespace for its own sake. Density where
the content is dense (the plate, the report), air where it is not (the mic, the transcript).

What I can defend with a mechanism:

- **Mixed script needs vertical room and gets it for free only if we ask.** Devanagari and Telugu
  carry marks above and below the base line; Android's `includeFontPadding` exists because tall
  scripts were being clipped, and Compose's fix (padding only on the first and last line, line
  height from the tallest glyph) applies only when `includeFontPadding = false` is set. The
  transcript style, which is the one place Devanagari appears in the demo, should be set with
  that off, `LineHeightStyle(Alignment.Center, Trim.None)`, and a line height of 1.5×, and then
  rendered on the emulator with the demo's Hindi sentence before it is called done.
- **Hindi is not shorter.** One localisation vendor reports Hindi contracting 10–15 % by character
  count against English; Reverie's guidance for Indian apps says Hindi words are often longer than
  their English counterparts and warns about button widths. Both can be true: fewer characters,
  wider glyphs. Our own table is the test: the four
  intent answers ("I ate this", "A question", "About to eat this", "What should I eat") already
  wrap to two rows in English on a 411 dp screen (`needs-intent.png`). In Telugu or Hindi they
  will need a column, not a row. A chip row that assumes English widths is a layout that will
  break the day the toggle is used on stage.
- **One-handed on 6.72 inches.** The realme's screen is 914 dp tall; roughly the top third is
  out of reach of the thumb that holds the phone. The mic, the intent answers, the retake and
  save buttons belong in the bottom 400 dp. The shell's mic is already at the bottom; the four
  intent buttons and the lab-report ticks are mid-screen and the language chips are at the very
  top, where nothing that changes during a demo should be.
- **The hall is bright and the LCD is 550 nits.** Every text colour must clear 4.5 : 1 against
  the cream; the green does at 11.6, and a secondary text tone must be chosen by arithmetic
  (`#5C6A5E` on `#ECEBE6` is 4.78 : 1 and passes; `#6B7A6D` at 3.80 does not). No text on the
  orange, no orange text under 24 sp.
- **The system font is not Roboto on either demo phone.** The emulator renders Roboto; realme UI
  and Funtouch OS each map `sans-serif` to their own face. Every screenshot taken so far shows a
  typeface the judges will not see, and the two phones would not even show the same one. This is
  the practical case for bundling (§6), beyond matching the deck.

---

## 5. Motion

### 5.1 What 120 Hz buys, and where it is wasted

At 120 Hz a frame is 8.33 ms instead of 16.67. What that buys is visible only where something
tracks the finger or moves fast: scroll, drag, a spring settling. On a static screen it buys
nothing, and Android 15 (the realme) knows it: with adaptive refresh rate the panel runs lower on
static content and boosts on touch. So a screen that is static except for one number changing
once a second costs the display and the CPU almost nothing, and a screen with a continuously
animating bar keeps the panel at 120 Hz and the RenderThread busy for the whole turn.

That is the whole argument about animating during inference. The demo build pins 8 threads for
the model (Rao 16:50). Every frame the UI draws during a turn is time on the same cores. The
shell today runs M3's indeterminate `LinearProgressIndicator` for every non-recording stage
(`TalkScreen.kt:103`), which is a continuous 120 Hz animation running for the exact ten seconds
the model needs the CPU most. It is the first thing to remove and the one measurement worth
asking Rao for (§8.5).

### 5.2 Springs versus durations

Springs for anything that moves under the finger or settles after a gesture (the mic's press,
the intent card arriving); they are interruptible and they have no "duration" to get wrong.
Durations for anything that fades: an effects spring at 1600 / 1.0 is a ~150 ms fade and is what
M3 uses for colour and alpha. The published rules I would hold the app to, from Emil Kowalski's
"Great animations" (the current reference among people who do this well): UI animations "usually
shorter than 300 ms"; ease-out, never ease-in, on anything entering; only transform and opacity,
because "if our animations won't run at 60 frames per second, everything else we've talked about
becomes useless"; and "never animate keyboard-initiated actions" (Raycast has none "and it feels
right"). Add the M3 split: spatial springs for position and size, effects springs for colour and
alpha, never the other way.

### 5.3 What makes motion feel expensive rather than busy

Expensive: one choreography per event, driven by something real. The mic fills with the voice's
level. The stage list advances by one tick. The figures card fades in once. The model's sentence
fades into the slot that was waiting for it. Busy: anything that moves while nothing has
happened. The shell's sweeping bar; a pulsing dot; a shimmer; a counting-up number (a number that
counts up to 15.9 is a fake number for 400 ms, and this project does not show fake numbers for
any length of time).

Absent, by rule: the figures (they appear, they do not animate), the health text (it lands,
whole, once), the safety line (it never moves), the transcript (never), anything on the pre-flight
screen, and everything during `Stage.SPEAKING` except the button label change, because the voice
is the motion then.

### 5.4 How it is built so it does not compete with the model

From the Compose performance guidance, the three rules that matter here:

- **Defer frequently changing reads to draw.** The level meter reads `level` inside `drawBehind`
  or a `graphicsLayer {}` lambda, so a 30-per-second RMS update touches the draw phase only and
  never recomposes the screen. Today `TalkViewModel.State` is one object carrying `level`,
  `elapsedSeconds` and the entries; every `AudioLevel` event copies it and recomposes
  `TalkScreen`. That is Arjun's state and a one-line ask: `level` as its own `State`/`StateFlow`.
- **The counter is its own composable.** One Text reading `elapsedSeconds`, so the 1 Hz tick
  recomposes one node.
- **`LazyColumn` items get keys.** `items(state.entries)` today has none; a new entry at the end
  is cheap, but a `MealLogged` that replaces an item in place re-runs everything after it.

And the measurement that says whether it worked: `dumpsys gfxinfo <pkg> framestats` over one LOG
turn, janky-frame percentage and 90th/99th percentile frame time, taken on the phone by Rao
before and after the Talk screen lands. Without that number "60–120 fps" is a wish.

---

## 6. Typography for an app whose job is to show numbers in sentences

### 6.1 The rules

1. **Tabular lining figures everywhere a number appears.** `FontFeatureSettings = "tnum"` on the
   figure styles. IBM Plex Sans has `tnum` (it is what Carbon uses for numeric columns); so does
   Roboto. Without it, `15.9` and `459.8` do not align and a column of figures ripples.
2. **A number never wraps.** The number is one `Text` with `softWrap = false`; the name and the
   unit wrap around it. The deck's "14 / 2" is the example to pin above the desk.
3. **In a sentence, the number is the size of the words.** "Your lunch had 15.9 g of protein" is
   set in one style; inflating a mid-sentence figure is a magazine trick that reads as shouting.
4. **In a figure row, the number is one step up and the unit one step down.** Value 20 sp
   semibold, name 14 sp, unit 12 sp; the value and unit are read as one token because the
   `ContextText.num()` rule already caps decimals at one and formats whole numbers without a
   point, so the token is short.
5. **One hero number per card.** Energy on a plate card, the nutrient asked about on an answer
   card. 36 sp semibold, tabular. Nothing else on the screen is that size.
6. **The band is a mark, once per card**, with the exceptions spelled out per row (a `Rough` row
   among `Approximate` ones is named; nine identical brackets are not).

### 6.2 Which typeface

Three honest options, with what each costs:

| option | what it gives | what it costs |
| --- | --- | --- |
| **A. System face** (`sans-serif`) | Zero bytes; Telugu and Devanagari fall back to the phone's Noto. | Roboto on the emulator, one OEM face on the realme, another on the iQOO; not the deck's face; nothing the screenshots show is what the judges see. |
| **B. IBM Plex Sans + IBM Plex Sans Devanagari** as assets, Telugu falling back to the system's Noto Sans Telugu | The deck's body face, so app and deck are one product; `tnum`; OFL, bundleable; Plex's Devanagari was drawn to sit with its Latin; three static weights (Regular, Medium, SemiBold) of both families is about 1 MB against a 67 MB APK. | No Plex Telugu exists (Plex covers Arabic, Devanagari, Hebrew, Thai, CJK and Latin scripts; Telugu is not in the set), so the Telugu chip and a Telugu toggle render in Noto beside Plex. For the demo that is one word on one chip. |
| **C. Hind + Hind Guntur** (Indian Type Foundry, OFL, five weights each) | One family drawn for UI across Latin, Devanagari and Telugu, so all three scripts match. | Not the deck's face; Hind's Latin is narrower and plainer than Plex; the family has not been updated in years. |

I recommend **B**, and I will render B and C side by side on the emulator with the demo's Hindi
transcript and the Telugu chip before the type file is committed, and put both screenshots in
`docs/screenshots/` so the choice is made from a rendering. A Google Fonts *downloadable* font is
a network call and is out (Arjun 20:23); assets only.

### 6.3 Line height and script

Latin body at 16/24 is fine. The transcript, which carries Devanagari on the day, is set at
20/30 with `includeFontPadding = false` and centre alignment, and checked on the emulator with
"मैंने दो रोटी और थोड़ी दाल खाई". The chip endonyms (`తెలుగు`, `हिन्दी`) at 14 sp need the same check;
Telugu's `ల`/`గ` loops descend further than Latin and will clip in a 32 dp chip with the default
padding if the fallback face's metrics are taller than Plex's.

---

## 7. The screenshots, one by one

Measurements below are from the PNGs (1080×2400, the emulator's 420 dpi, so 2.625 px per dp) and
from the source that drew them. I was asked to be specific and unkind. Arjun built this in two
days and it works; none of what follows is about that.

### `talk-empty.png` (the first screen a judge sees)

- The whole middle of the screen, roughly 560 dp of 914, is empty pink-white (`#FEF7FF`). The
  product's one sentence, "Say what you ate, or ask a question.", is 16 sp regular at the top
  left under the chips, in the same style as everything else. Nothing on this screen says what
  the product is, and nothing says it is offline, which is the pitch's first line.
- The language chips are the first thing on the screen, 32 dp tall, at the top, where a thumb
  cannot reach and where nothing should change during a demo. The selected one is lavender
  `#E8DEF8`.
- The primary control of a voice-first app is a 40 dp filled purple `Button` labelled "Speak"
  in 14 sp, the same component as "Send" and "Retake". It is the smallest kind of primary
  action Material has, and it is visually equal to the typed fallback below it.
- The typed fallback (an `OutlinedTextField` plus an outlined "Send") takes 56 dp of permanent
  space directly under the mic on every state of the screen. It exists for a denied microphone
  (rule from the contract) and for the emulator. On the demo screen it is a keyboard in a voice
  demo.
- The bottom `NavigationBar` has `icon = {}`: an empty lavender selection pill floats above the
  word "Talk", the ghost of an icon that was never drawn. Three text-only tabs on an 80 dp bar.
- The system dialog in the shot ("System UI isn't responding") is the emulator at 2 GB (Arjun
  18:17 recorded the 4 GB fix). Not a design fault; the screenshot should be retaken.

### `talk-typed-no-model.png`

- Two cards, same grey-lavender fill, same 12 dp corner, same 12 dp padding (`EntryCard`,
  `TalkScreen.kt:156`): the person's words and the failure look identical. Nothing
  distinguishes "you said" from "the app says", which is the one distinction a conversation
  screen exists to draw.
- "The model could not start." is 16 sp; the path under it is 12 sp and 200 characters long.
  Honest, correct, and the right thing on a diagnostics screen; on the Talk screen the detail
  belongs behind a tap (0026: "never the `detail`").
- 620 dp of empty space between the failure and the mic.

### `beat1-turn-in-progress.png`

- The stage list is bottom-left, ticks drawn as the text glyph "✓" in 12 sp medium, the current
  stage in 14 sp medium, 2 dp between rows, no indent alignment between tick and text, and an
  indeterminate `LinearProgressIndicator` sweeping under it. The list is the most important
  thing on the screen for ten seconds and it is set in the smallest text on the screen.
- The counter reads "0 s" in 14 sp. On the phone it will read "9 s" and the presenter reads it
  aloud (the playbook says so). It should be the largest number on the screen while it runs, in
  tabular figures, and it should not move horizontally as digits change.
- The transcript is in a card at the top with "You" as a 12 sp label; 700 dp of empty pink
  between it and the stages. 0026 wants the transcript pinned above the progress area; here
  they are at opposite ends of the screen.
- The mic is greyed to a disabled `Button`; the demo banner overlaps the status bar clock in
  this build (fixed in the later shots).

### `beat1-logged.png`

- The plate is twelve lines of 14 sp `key: value` prose in one card: three items, then nine
  nutrients, every nutrient lower-case, every one ending "(Approximate)". Energy, the number the
  deck leads with, is line four in the same size as sodium.
- The "Logged" label is 12 sp medium in the same grey as the card; a person cannot tell from
  the card whether the meal was written or is being asked about (`meal_hypothetical` uses the
  same slot and style).
- The advice card: "Advice" 12 sp label, the engine's trigger sentence in 16 sp semibold, the
  model's sentence 16 sp regular, the safety line 11 sp medium. Four sizes, four weights, one
  card, no rule about which is which.
- The safety line appears on this screen twice on `beat4` and three times across the session;
  spec 15.3 says one persistent line per advice screen.
- Two buttons at the bottom, filled purple "Speak" and outlined "Advise again on my last meal"
  at equal weight, the second wrapping to two lines.

### `beat2-turn-in-progress.png`

- The current turn's transcript ("How much protein was in my lunch?") is not on screen while
  the stage list runs; the list is scrolled to the previous turn's advice. Whether the script's
  event order or the auto-scroll is at fault, the layout permits it and 0026 step 3 does not.
- "Looking up facts 0 s" with a sweeping bar again, during the one stage that is a model call.

### `beat2-answer.png` (the shape of Vedant's figures-first ruling, and the most important one)

- Five cards in a column, all the same grey, all the same corner and padding: the previous
  advice, "You", "Answering / Let me check your records.", "From your diary", "Answer". The
  ruling says the diary line is the answer and the sentence is the caption; the screen gives
  them identical cards of identical weight, with the diary line as a 16 sp run-on
  ("Today so far: energy: 459.8 kcal; protein: 15.9 g; carbohydrate: 61.7 g; …") that the eye
  has to scan for "protein".
- "15.9 g" appears twice, once buried in the run-on and once in the model's sentence; neither
  is set as a figure.
- The heading "Answering" (`titleMedium`, 16 sp) is the same size as body text. It is the one
  word that tells the judge what the app decided; it is invisible.

### `beat3-fields.png`

- Each field is a 48 dp purple `Checkbox` and three lines: the parsed line in 16 sp, the range
  in 12 sp, and the source row in 11 sp medium, which is the same text as line one in a smaller
  bolder face ("Haemoglobin 9.8 g/dL 13.0 - 17.0" under "Haemoglobin: 9.8 g/dL / printed range
  13 to 17"). It reads as a rendering bug.
- Nothing marks a value as outside its printed range. Haemoglobin 9.8 against 13–17 and
  ferritin 8.2 against 15–150 are the whole point of beats 3 and 4, and the screen leaves the
  comparison to the reader. The engine already makes and speaks exactly this comparison
  ("below the 15 printed on it"), so marking it is presentation of data on screen, not a
  judgement (checked against spec 15.1: the range is the report's own).
- The title "Scan a lab report" is 22 sp; "Report date 2026-09-12" is 14 sp medium in ISO
  format on a screen for a person.
- 800 dp of empty space between the fifth field and the buttons.

### `beat4-advice-after-report.png` and `recommend.png`

- Two advice cards stacked, both grey, both headed "Advice" in 12 sp: the old sentence and the
  new one. The judge is meant to see that the advice *changed*; the screen shows two similar
  cards and asks them to diff.
- The candidates ("You could consider / Palakura pappu / Roasted peanuts / …") are five 14 sp
  lines with no separation from the paragraph above; a list rendered as prose lines.
- The trigger sentence is 16 sp semibold and three lines long; the model's sentence 16 sp
  regular and four lines; the same weight of attention on both, when the trigger is the proof
  and the phrasing is the decoration.

### `needs-intent.png` and `needs-confirmation.png`

- The four intent buttons wrap into two rows of two, in 14 sp, in `OutlinedButton`s of
  different widths ("I ate this" 190 px, "What should I eat" 340 px); the row is ragged on the
  right. In Hindi they will not fit the row at all (§4).
- "Was that a meal you ate, a question, a plate you are about to eat, or a request for what to
  eat?" is a 16 sp three-line question with the answer buttons below; the question restates the
  four buttons. The buttons are the question.
- `needs-confirmation`: "Not logged yet" 12 sp label, "I do not know a food in that." 16 sp,
  "quinoa: 1 bowl" 14 sp, "Say it differently, or type it." 12 sp. Four sizes in five lines. The
  honest content is right; the hierarchy says nothing.

### `scan.png`

- 22 sp title, a black rectangle (the emulator's camera), a full-width purple "Capture". No
  framing guide, no instruction ("flat, whole page in frame, light from the side", which is the
  playbook's own advice), no indication of what the app will read. The capture button is the
  same component as "Speak".

### `about.png`

- "IN2FIT" is 28 sp Roboto text on the screen that exists to show the brand; the wordmark exists
  as a file and is not used.
- A nine-line legal string at 12 sp with hard line breaks, then "This is information, not
  medical advice." at 11 sp. The screen is 55 % empty below it. The USDA lines from the `meta`
  table (landed later) will make it longer, not better, unless it becomes a list with a rule
  between entries.

### `preflight.png`

- Ten `Row(label, value)` lines where the value starts at a different x on every row ("App",
  "Device", "Locale", "Microphone permission" are different widths), and wrapped values hang at
  that ragged x. A two-column table with a fixed label column is the fix, and it is a
  diagnostics screen: Arjun's ruling is "leave it plain", so this is noted and not taken. It gets
  the theme's face and colours through `MaterialTheme` and nothing else.
- "MISSING" in 14 sp regular is the most important word on the screen on the morning of the
  demo and it is the same weight as the path above it.

### Across all fourteen

- One card component, one fill, one corner, one padding, for eleven different kinds of content
  (a transcript, a heading, a figure list, advice, an answer, a question, a refusal, a failure, a
  not-built line, a diagnostics row, a model row). A layout that looks identical with different
  content in it is the definition in the brief of what not to ship.
- Six type sizes in use (11, 12, 14, 16, 22, 28) with no rule for which means what; labels are
  sometimes 12 medium and sometimes 11 medium; body is sometimes 16 and sometimes 14.
- The purple. `#6750A4` on `#FEF7FF` on every screen, the M3 baseline, in an app whose brand is
  dark green on cream and whose deck is dark green on cream.

---

## 8. What this means for IN2FIT

This section is direction, not the system; the system is Phase 2 and it is written down as it
is built.

### 8.1 The palette as tokens, with the arithmetic done

| token | value | contrast | use |
| --- | --- | --- | --- |
| `ground` | `#ECEBE6` | — | the screen |
| `ink` | `#252F26` | 11.63 on ground | all body text, all figures, headings, the wordmark |
| `ink.secondary` | about `#5C6A5E` (final value in Phase 2, must clear 4.5) | 4.78 on ground | labels, units, the source line, dates |
| `surface.raised` | about `#F6F5F1` | 1.08 on ground | cards that must read as "a thing": the plate, the figures, the report fields. Lighter than the ground, as the deck's cards are; no shadow |
| `surface.person` | `#252F26` | text `#ECEBE6` on it, 11.63 | the person's own words, the one dark block on the screen (the deck's page 3 draws it this way) |
| `hairline` | about `#D6D5CE` | — | rules between rows; the only separator the app uses |
| `accent` | `#E5522D` | 3.15 on ground | marks only: the level fill, the range mark, the live stage, the running counter at ≥ 24 sp, the running figure. Never body text, never a filled button with a 14 sp label |

Light only for the battle. The shell is light-only today (`MaterialTheme {}` has no dark
branch), the hall is bright, and a dark scheme that nobody has photographed is a risk with no
reward this week. The tokens are named so a dark scheme is a second value per token later.

### 8.2 The type scale, five sizes and one number

12 / 14 / 16 / 20 / 28, plus one numeric display size, 36, used only for a hero figure. Label
12 medium, body 14 and 16 regular, transcript and figure values 20, screen title and intent
heading 28 semibold. Everything in the app is one of these six. Spacing 4 / 8 / 12 / 16 / 24 /
32; nothing is 10 or 6 or 2. Corners: 8 dp on small controls, 16 dp on cards, a full pill on the
mic. No elevation anywhere; hierarchy from tint and a hairline.

### 8.3 The components this app needs, mapped to the events that already exist

| component | renders | the idea in it |
| --- | --- | --- |
| **Said** | `Entry.Said` | The person's words, 20 sp, in quotes, in their script, on the one dark block; the retake lives beside it |
| **Intent heading** | `Entry.Heading` | 28 sp, the four words; the lead-in in 14 sp secondary under it |
| **Figure row** | one `NutritionFigure` | name left in 14, value 20 semibold tabular right, unit 12 secondary; the band as a mark; tap lists `confidence_reason_*` |
| **Plate card** | `Entry.Plate` | the deck's page 3: hero energy, one macro line, items as said with the unit assumption in grams, band once |
| **Figures card** | `Entry.Figures` | the `OwnFigures` lines as figure rows, the nutrient that was asked about first and largest; a reserved slot beneath for the sentence, carrying the stage and the counter until it lands |
| **Advice card** | `Entry.Advice` | the trigger sentence as the head in 16 semibold on a raised surface, the phrasing in 16 regular, the referral set apart with a hairline and the accent mark, the candidates as a list with hairlines |
| **Answer card** | `Entry.Answer` | the sentence in 16; a refused answer is the fixed line over the person's figure rows |
| **Ask card** | `Entry.AskIntent`, `Entry.Confirm` | the question is the buttons: four full-width rows with hairlines, 48 dp each, in the bottom half of the screen |
| **Stage indicator** | `State.stages`, `elapsedSeconds` | the stage list with a real tick glyph aligned in a 24 dp column, the current stage in 16, the counter in 28 tabular accent; no bar |
| **Mic** | `State.stage`, `level`, `busy` | a 72 dp pill at the bottom centre; held, it fills with the level in accent from the bottom; released, it freezes; "Stop" during `SPEAKING`; the hold hint under it |
| **Report field** | `LabField` | name, value 20 tabular, unit; under it the printed range drawn as a hairline with the value as a dot on it, and the word "below" or "above" in accent when the value is outside |
| **Safety line** | — | once per screen, pinned above the mic, 12 secondary, never inside a card |
| **Offline mark** | — | one static line in the screen header: the demo build's no-INTERNET fact, in words, the deck's "OFFLINE" strip made true on the phone |
| **Splash** | warm-up | the PNG wordmark scaled (Arjun's suggestion, taken), `scaleX` 0.72→1.0 ease-out 1.2 s, tagline alpha, contract and fade from 3.5 s, loop; never gates, ends at the next expand |

### 8.4 The ideas specific to this product

The brief asked for at least one thing a generic app would not have. Five, in the order I would
build them; the first three are the ones the judges will see in every beat.

1. **The figures arrive; the sentence catches up.** The figures card is the hero of an answer,
   complete at 0.55 s, with the asked-for nutrient as the one large number; under it a reserved
   slot with the stage name and a 28 sp tabular counter in the accent. When the sentence lands
   it fades into the slot (effects spring, ~150 ms) and the counter stops. Nothing moves except
   the counter, once a second, for the whole wait. The wait reads as arrival because what
   arrived first was the answer.
2. **The mic is the meter.** Hold-to-speak, the WhatsApp gesture, and while it is held the
   button fills with the voice from the bottom in the accent, read from `AudioLevel` in the draw
   phase. On release it freezes (0026 step 2). The only organic motion on the screen is the
   person's own voice.
3. **The printed range, drawn.** Every lab field shows its value as a dot on a hairline between
   the printed low and high; outside the line, the dot and the word "below"/"above" are in the
   accent. A judge sees in one second what beat 4 is about to change, and the app still says
   nothing the report did not print.
4. **The unit assumption on the card.** "dal · 1 katori · taken as 150 g" on the plate, because
   the household unit is the Indian fact about the number and its default is the honest
   caveat. This is the line the deck wrote as "1 katori, your size".
5. **The offline mark.** One static line at the top of the Talk screen stating the build has no
   internet permission. It is the deck's first claim, it is compile-time true, and it is on the
   screen a judge reads over a shoulder.

### 8.5 Asks, so the design is discovered on paper (Arjun 20:23)

To **Arjun** (state and entries), none of which changes what a screen does:

1. `Entry.Plate.figures` and `Entry.Figures.lines` are rendered strings. A figure row needs the
   parts: the nutrient word, the formatted number, the unit, the completeness and the band. Ask:
   carry the `NutritionFigure` (or a small `FigureParts(name, number, unit, band, partial)`)
   beside the line, formatted by the same `ContextText.num()` so the screen never says a number
   a second way. If `ContextText` needs a `figureParts()` that is Rao's file and I will ask him
   with your yes.
2. `level` as its own `State`/`StateFlow`, so the meter reads it in the draw phase and a 30 Hz
   RMS does not recompose `TalkScreen` (§5.4).
3. `elapsedSeconds` likewise, or I isolate the counter in one composable; either works.
4. The auto-scroll: the transcript must stay on screen for the whole turn (0026 step 3). Whether
   that is "scroll to the current `Said`" instead of "scroll to the last entry", or a pinned
   header, is yours; I will render whichever you choose.
5. `items(state.entries)` with a stable key per entry.
6. `Entry.Said` could carry the ASR confidence if Rao adds it to `Transcribed`; a LOW transcript
   gets a mark. Low priority; say no and nothing changes.

To **Rao**, only via Arjun's yes: `ContextText.figureParts()` (item 1); `AsrConfidence` on
`Transcribed` (item 6). And one measurement when the Talk screen lands: `dumpsys gfxinfo`
framestats over one LOG turn on the phone, before and after, so "animation does not compete
with the model" is a row and not a sentence.

To **Nila**, keys, all English-only by the freeze rule and all short: `scan_below_range`,
`scan_above_range` ("below the printed range" / "above the printed range"), `talk_offline_mark`
(the no-internet line), `plate_unit_taken_as` ("taken as %1$s g"), and a `contentDescription`
key for the wordmark. Five keys, prefixes already placed. Two files in `res/font/` (Plex Sans,
Plex Sans Devanagari, three weights each) and their OFL texts in `docs/licences/`, with a row in
`0005`; both are yours to accept.

To **Vedant**, decisions only he makes:

1. Typeface: **B** (IBM Plex Sans + Plex Sans Devanagari, Telugu on the system's Noto) is my
   recommendation because it is the deck's face; I will show B and C rendered before committing.
2. The accent rule (§0): orange never carries text under 24 sp. It follows from the contrast
   arithmetic; say if the brand needs an orange button and I will find a darker orange that
   passes and show it beside the brand one.
3. Light-only for the battle.
4. The pre-flight screen: the brief says restyle it; Arjun's split says leave it plain. My
   proposal: it takes the theme and nothing else this week.
5. Which of the five ideas in §8.4 to cut if Thursday comes early. My order is the order above.

### 8.6 What I will measure, and what I will not claim

On the emulator: every screen, every state, both flavours, before and after; the Hindi transcript
and the Telugu chip rendered in both typeface options. On the phone, through Rao: the
framestats row, and one screenshot of the Talk screen on the realme so we know what its ROM does
to the face. I will not quote a frame rate, a latency or a memory figure from the emulator, and
no number on any screenshot I take is a result.

---

## Sources

Repository: `COORDINATION.md` (Arjun 15:11, 16:41, 17:34, 18:17, 20:09, 20:15, 20:20, 20:23;
Rao 16:50, 17:55, 18:40; Meera 16:51, 17:24, 20:25; Nila 16:44, 20:28), `docs/decisions/0026`,
`0027`, `0028`, `docs/demo/run-of-show.md`, `docs/demo/countdown.md`, `docs/spec.md` §15,
`docs/localisation/string-conventions.md`, `ui/TalkScreen.kt`, `ui/TalkViewModel.kt`,
`ui/ScanScreen.kt`, `ui/AboutScreen.kt`, `ui/PreflightScreen.kt`, `ui/MainActivity.kt`,
`domain/OrchestratorSeams.kt` (`ContextText`), `ml/asr/AsrEngine.kt`, `res/values/strings.xml`,
`res/values/themes.xml`, `res/values/colors.xml`. Contrast ratios computed by me with the WCAG 2
relative-luminance formula.

Outside:

- Material 3 motion tokens, verbatim from androidx: [ExpressiveMotionTokens.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ExpressiveMotionTokens.kt), [StandardMotionTokens.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/StandardMotionTokens.kt); type scale from [TypeScaleTokens.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/TypeScaleTokens.kt); [Material 3 motion overview](https://m3.material.io/styles/motion/overview/how-it-works); the spatial-vs-effects rule from [a Compose motion reference](https://github.com/aldefy/compose-skill/blob/master/skills/compose-expert/references/material3-motion.md).
- M3 Expressive loading indicators: [9to5Google, May 2025](https://9to5google.com/2025/05/16/material-3-expressive-loading-indicator/); [Joe Birch on `LoadingIndicator`](https://joebirch.co/android/material-3-expressive-for-compose-loading-indicator/).
- Compose performance: [Jetpack Compose performance best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices); font padding and tall scripts: [Fixing font padding in Compose Text](https://aster.cloud/2022/06/19/fixing-font-padding-in-compose-text/) (mirror of the Android Developers post).
- Android 15 adaptive refresh rate: [AOSP, Adaptive refresh rate](https://source.android.com/docs/core/graphics/arr); [Android Authority](https://www.androidauthority.com/android-15-adaptive-refresh-rate-3498029/).
- Splash screen API dimensions and limits: [Android Developers, Splash screens](https://developer.android.com/develop/ui/views/launch/splash-screen).
- Linear's redesign: [How we redesigned the Linear UI (part II)](https://linear.app/now/how-we-redesigned-the-linear-ui); token summary via [DesignMD](https://designmd.cc/benchmarks/linear).
- Gemini Live pill redesign: [9to5Google, February 2026](https://9to5google.com/2026/02/28/gemini-live-floating-redesign/).
- ChatGPT voice: [TechCrunch, September 2024](https://techcrunch.com/2024/09/24/openai-rolls-out-advanced-voice-mode-with-more-voices-and-a-new-look); the move into the chat view: [TechBuzz](https://www.techbuzz.ai/articles/chatgpt-voice-gets-major-ux-upgrade-with-unified-interface).
- Siri's edge glow: [SlashGear](https://www.slashgear.com/1865686/iphone-glowing-around-edges-reason/); [Pocket-lint](https://www.pocket-lint.com/how-to-get-new-siri-look-glowing-border/).
- WhatsApp hold-to-record, slide to cancel, slide up to lock: [Android Police, April 2018](https://www.androidpolice.com/2018/04/05/whatsapp-2-18-102-brings-voice-note-recording-lock-option/); [Nerdschalk](https://nerdschalk.com/what-is-locked-recording-on-whatsapp-and-how-to-get-and-use-it/).
- Percent-done indicators: Myers, B. A., [The importance of percent-done progress indicators for computer-human interfaces](https://dl.acm.org/doi/10.1145/317456.317459), CHI 1985.
- Skeleton screens and perceived duration: [Bill Chung, UX Collective](https://uxdesign.cc/what-you-should-know-about-skeleton-screens-a820c45a571a); [Viget, A bone to pick with skeleton screens](https://www.viget.com/articles/a-bone-to-pick-with-skeleton-screens); [NN/g, Skeleton screens 101](https://www.nngroup.com/articles/skeleton-screens/).
- Animation rules: [Emil Kowalski, Great animations](https://emilkowal.ski/ui/great-animations).
- Food apps: [Cronometer nutrient targets](https://support.cronometer.com/hc/en-us/articles/360060170532-Nutrient-Targets); [Cronometer review](https://repreturn.com/cronometer-review/); [MyFitnessPal 2025 UI update and user response](https://www.bentobunny.app/guides/myfitnesspal-new-ui-update); [MyFitnessPal, the Today tab](https://support.myfitnesspal.com/hc/en-us/articles/39985611667341-Introducing-the-brand-new-Today-tab); [Ate / AteMate](https://www.atemate.com/blog/best-food-journaling-app); [HealthifyMe Snap, TechCrunch](https://techcrunch.com/2023/09/21/khosla-backed-healthifyme-introduces-ai-powered-image-recognition-for-indian-food); [HealthifyMe Snap accuracy claim](https://robots.net/news/new-feature-introduces-ai-powered-image-recognition-for-indian-food-tracking/).
- Typefaces: [IBM Plex on GitHub](https://github.com/IBM/plex) (script coverage); [IBM Plex Sans Devanagari, Google Fonts](https://fonts.google.com/specimen/IBM+Plex+Sans+Devanagari); [Carbon typography](https://carbondesignsystem.com/elements/typography/overview/) (Plex, `tnum`); [Hind Guntur, Google Fonts](https://fonts.google.com/specimen/Hind%2BGuntur); [OpenType `tnum`](https://www.preusstype.com/techdata/otf_tnum.php).
- Localisation of Hindi strings: [Reverie, mobile app localisation](https://reverieinc.com/blog/best-practices-for-mobile-app-localization/); [SimpleLocalize, text expansion](https://simplelocalize.io/blog/posts/text-expansion-ui-localization/).
- The realme RMX3780 display: [Amazon listing, realme 11 5G](https://www.amazon.com/realme-RMX3780-Display-Unlocked-Carriers/dp/B0CGF21RCY) (6.72-inch 120 Hz LCD); [chooseyourmobile](https://www.chooseyourmobile.com/realme-11-5g/) (2400×1080, 550 nits).

---

## Corrections, appended and dated (the rule: never overwrite)

**20 September, 21:58, Ira.**

- §0, the row "the realme RMX3780 is the backup": Nila 20:58 corrected the role. Vedant's 20:45
  facts make the realme the rehearsal and fallback-video device, staged with the same build and in
  the bag; whether it may stand in front of the judges is unruled. The display facts in the row
  (6.72-inch IPS LCD, 120 Hz, 550 nits) are unchanged and still the only panel anyone has measured on.
- §6.1 rule 1 and §6.2 option B said Plex "has `tnum`". Measured with fontTools on 20 September:
  neither IBM Plex Sans nor IBM Plex Sans Devanagari lists a `tnum` feature; their digits are
  **tabular by default** (every digit 600 units per em), so the alignment holds with no feature
  set. Hind and Hind Guntur have proportional digits (323 to 544 units) and no `tnum` to switch on,
  which is the measured reason they lose for a numbers app before any rendering. Also measured:
  Plex Sans Devanagari carries Plex's Latin at the same x-height (516/520/522), so one family of
  three files covers Latin and Devanagari; the separate Plex Sans Latin files are not bundled.
- §8.6 asked for a private adb server on the emulator. Withdrawn: on 20 September at 21:39 a
  private server took the phone from the default one on a USB re-enumeration (COORDINATION 21:45).
  The emulator is attached to the default server only inside a stated window, after the phone,
  with `-s` on every command.
