# 0020. Intent routing, the knowledge-facts file, and the ANSWER and RECOMMEND prompts

Date: 20 September 2026. Status: accepted for the code and the data. **No behaviour claim:
none of the three prompts has run on a phone.**

Implements the parts of `0015` that are prompts and data. The Orchestrator that routes on the
intent and the third `LlmEngine` path that runs these prompts are the integrator's and are not
in this record.

## What landed

| Thing | Where |
| --- | --- |
| Intent classifier prompt and parser | `ml/llm/ConversationPrompts.kt`, `Intent` |
| ANSWER and RECOMMEND prompts, their request shapes, the guard's permitted list | same file |
| The knowledge-facts file, 131 rows, every row cited | `app/src/main/assets/knowledge/facts.csv` |
| Loader, CSV reader, tag retrieval | `data/knowledge/KnowledgeFacts.kt` |
| Authored intent case set, labelled circular | `data-authoring/intent-test-set.csv` |
| LOG pre-filter, measured | `ml/llm/LogPrefilter.kt`, `LogPrefilterTest` |
| Reviewer's sheet for the log words | `data-authoring/log-words-review.md` |
| 43 JVM tests | `KnowledgeFactsTest` (17), `ConversationPromptsTest` (16), `LogPrefilterTest` (10) |

## The classifier

One prompt, four labels, one word out. The output is the cost on this device (`0014`:
generation is about five times the price of prompt processing), so the answer is a single word
and the stop is a newline. The labels are words rather than letters or digits because a 1.5B
model is more reliable at emitting a word it was shown than at mapping a category to a symbol,
and the difference is one or two tokens.

`Intent.parse` is lenient about case, punctuation and trailing words and strict about the label:
the first alphabetic word must be one of the four, allowing a prefix of at least three letters
so a label cut off by the token budget still parses. Anything else is null, and the caller asks
rather than guesses (spec 10.7).

**ANSWER IS DEFINED WIDER THAN THE BRIEF'S EXAMPLES, AND THAT IS THE DECISION, NOT A DRIFT.**
ANSWER is a question about their own diary **or a general nutrition question**. `0015` lists
"answer nutrition questions" among what the model may do, and "does tea reduce iron absorption"
has no plate and no history, so without this it has no route and would be forced into RECOMMEND,
which suggests foods, or LOG, which writes a meal. RECOMMEND is "what should *I* eat for a goal
or condition". Ruled by Vedant on 20 September; anyone narrowing ANSWER back to diary-only
questions is undoing a decision, and needs a new record saying where the general questions go.

**Nothing about the classifier's accuracy or latency is known.** The prompt is about 600
characters; a test caps it at 800 as a proxy for tokens, because a classifier prompt is the
kind that grows one helpful sentence at a time. The first number it gets is the authored case
set run on the phone by the integrator, which is when the examples in the label definitions
become the first thing to try cutting.

## The LOG pre-filter

LOG is the most common turn by a wide margin, and the classifier prompt costs roughly two
seconds of prompt processing on the test device before extraction's ten. `LogPrefilter` routes
an utterance to LOG **without a model call** when it is certain, and to the model otherwise. It
never returns anything but "certain" or "ask".

**The rule is asymmetric on purpose.** It needs positive evidence of a log (a past-tense eating
or drinking word: ate, had, drank, khaya, tinnanu, ...; or "lunch was ...") AND the absence of
every question or advice marker it knows (what, how, should, can, did, add, better, help,
kya, kitna, chahiye, emi, entha, tinali, ...; a question mark; "having", "about to", "ippudu").
A missed short-circuit costs two seconds; a wrong one writes a meal the person never ate into
their history. So the marker lists are over-inclusive and the log-word list is short.

**Measured on the authored case set** (`LogPrefilterTest`, 20 September):

    cases                     53
    LOG cases                 15
      short-circuited         13  (86.7 %)   <- model calls saved
      sent to the model        2             "a plate of vegetable biryani and some raita",
                                             "tea with two biscuits": no verb, no evidence
    non-LOG cases             38
      MISROUTED as LOG         0             <- the number that matters, asserted at zero

Two findings from the measurement, both fixed in the lists rather than the rule:

- **"do" is two in Hindi.** As a whole-word marker it sent every "maine do roti khaya" to the
  model. "do" and "is" ("is subah", this morning) are now markers only as the FIRST word, where
  they are English question openers.
- Sentences with a log word that are not about adding a meal ("I had my report checked",
  "delete the dal I had") had no marker. "report", "doctor", "checked", "remove", "delete",
  "change", "wrong" and their neighbours were added; rejecting costs nothing.

The set is circular and 86.7% is a regression guard, not accuracy. The roman-script Hindi and
Telugu entries were written by someone who does not speak either fluently: a wrong MARKER only
sends a log to the model, which is safe; a wrong LOG WORD could short-circuit a question, so
those are the words a fluent speaker reviews first. They are on their own sheet,
`data-authoring/log-words-review.md`, separate from Nila's string sheet because it is a
different kind of review (yes/no per word: does it mean *ate*, could it mean something else),
with the one question that matters asked outright. A test fails if a log word is in the code
and not on the sheet.

**Marker-versus-numeral collision is the general hazard of one Roman-script list serving three
languages**: every English marker is a candidate Hindi or Telugu word, and it will recur as the
lists grow. A test now fails if any single-word marker equals a log word or a word inside a
log phrase, which is the exact shape of the "do" bug and the only thing that stops it
recurring. (It found "was" shared between the phrases "was there" and "lunch was" on its first
run; phrases match as whole runs and cannot collide, so the test compares phrases as phrases.)

Vedant's recorded transcripts replace the set, and the first thing to read off them is the
misroute count. **They will be five or six speakers, not twenty, and the number they give is
reported as a small sample**, not as the figure that replaces this one for good.

## The knowledge-facts file

**The rule, unchanged from the brief: a fact without a real, citable source does not go in the
file.** The loader refuses a row with a blank source, so the rule holds where the file is read
and not only where it is written. Every row carries the citation as a reader would write it, the
URL, and the date it was read.

Sources on 20 September 2026, all read that day: the ICMR-NIN RDA 2020 brief note (Indian
requirements: energy, protein, iron, calcium, vitamin C, B12, the rest); the ICMR-NIN Dietary
Guidelines for Indians 2024 (guidance, the 'My Plate' quantities, salt, sugar, oil, cooking
methods, and its glycaemic-index annexure for rice, chapati, the dals, dosa, idli-sambar, lemon
rice, curd rice, biryani, pesarattu and vada-sambar); the WHO healthy-diet fact sheet; StatPearls
(dietary iron); the Linus Pauling Institute (iron, calcium, vitamin C, B12); the Harvard
Nutrition Source (iron, protein, glycaemic index, fibre, fats); Atkinson et al. 2008 (the
international GI tables); NHLBI's DASH page; and USDA FoodData Central by fdcId.

**Where a fact rounds a figure, the note carries the exact one.** "Cooked rice about 78" is in
the fact; "78.23 ± 4.24, ten participants" is in the note. The fact is what the model may say
and the note is what a reviewer checks.

### What is deliberately not in the file

- **No diagnostic thresholds.** No haemoglobin cut-off, no glucose range. The reference range
  comes from the person's own report, and a threshold row would let the model turn "below the
  range printed on it" into a named condition. A test asserts the absence.
- **No IFCT 2017, no INDB, in any form** (`0002`, HANDOVER §6 rule 14). The two ICMR-NIN
  documents are cited for requirements, guidance and the GI annexure, which is from Devindra et
  al. and not from the composition tables. The DGI's own nutrient tables cite IFCT 2017 and are
  not used. A test greps every row for the names.
- **No per-100 g figure for a nutrient the food database already tracks.** Those come from the
  database on the day, as `DisplayFigure`s; a row would be a second copy that could drift. Rows
  do carry per-100 g figures for nutrients the database does not have (calcium, vitamin C,
  potassium, folate, vitamin A) and for foods it does not have (guava, orange, lemon, papaya,
  capsicum). Those nine rows were read from the SR Legacy April 2018 release on disk in
  `data-sources/usda`, the same files and the same fdcIds the food database is built from, so
  a figure here and a figure there can never disagree about which record they mean.
- **No row that tells the person to take, stop or change a medicine or supplement.** One row
  quotes its source saying vegans "need supplemental vitamin B12"; the prompt forbids the model
  from turning that into an instruction, and the note says so.
- **No composition figure for ragi, bajra or jaggery.** They stay no-data (`0002`). Two rows
  name ragi and millets qualitatively, from the DGI, and their notes say the app cannot put a
  number on them.

### Language

Nila's flag: text composed from this file is user-facing in the user's language. The decision
is **no language column**. Nobody on the team writes Telugu or Hindi and `0017` forbids
shipping either unreviewed, so the rows are English and the model renders them in the user's
language at generation time, which is already how the phrasing path works. The numeric guard is
script-aware, so the figures survive. If a reviewed Telugu set is ever wanted it is a second
file keyed by the same `id`, with an untranslated row falling back to this one, exactly as a
missing string-table key falls back to the default table. Nothing in the loader changes for
that.

### Retrieval

Deliberately dumb. A row is relevant when one of its tags appears in the request text as a
whole word, using `FoodTextMatching.containsAsWords`, the same one-directional word-bounded
containment the food matcher uses and for the same reason (`0006`: substring containment
produced biryani → bay leaf). Rows rank by how many tags hit, ties broken by file order, so the
author controls precedence with the order of the rows. Six rows by default, about 150 prompt
tokens. No embeddings, no fuzzy matching: a row retrieved for the wrong reason is a confident
sentence about the wrong thing, and a miss is only a shorter answer.

Checked by eye on ten requests before committing: "my haemoglobin is low what foods help"
retrieved nothing until the iron rows were tagged `haemoglobin`, and "I have diabetes what
should I eat for breakfast" ranked the GI *definitions* above the measured Indian dishes until
the definitions were moved to the end of their block. Both are file edits, not code.

## The ANSWER and RECOMMEND prompts

Both are built the way the phrasing prompt is built: everything numeric arrives as finished
strings, the facts go in as prose, and `permitted(request)` is defined beside the prompt so the
guard's list and the prompt's contents cannot drift. A `check` fails the call if the prompt
would show text the guard was not given.

Two additions to the permitted set, both recorded because they widen it:

- **The request text itself.** "How much protein in 2 rotis" carries a 2 the model must be
  allowed to echo. It is the person's number, not the model's.
- **The declared conditions and context, verbatim.** "type 2 diabetes" carries a 2 for the
  same reason. Declared conditions are also the *only* conditions the prompt shows, in the
  person's own words, so the model has nothing to diagnose from.

**The referral is not generated.** When the rules engine decides a referral is mandatory
(`ESCALATE` as `0015` redefines it), the caller appends the fixed referral line after the
model's text and sets `referralFollows`, and the prompt tells the model not to contradict a line
it cannot see. A generated referral could be softened or omitted on a bad sample; a fixed line
cannot. This is the same choice as the trigger sentence: the safety-critical words are
templated, the model puts prose around them.

**Constraints are never relaxed.** Diet type and allergies go in as "Never suggest" lines, and
the allowed food list is filtered by them upstream, so a slip in the prose has no food to land
on. The UI renders suggestions from the list, never from the prose (spec 4.3, unchanged).

SUGGEST has no prompt of its own here. "I'm having rice and sambar, what should I add" is the
existing phrasing path with the plate's figures, the rules engine's ranked candidates and the
retrieved rows; whether it needs its own wording is a question for after the first device run.

## What is testable without a phone, and is tested

The file, row by row: source present, URL, date, no IFCT, no diagnosis, no threshold, every row
reachable by its own tags. The parser: quoted commas, doubled quotes, CRLF, the refusals. The
retrieval: the brief's iron request, a dish on the plate, whole-word matching, punctuation. The
prompts: every figure, fact, condition, constraint and food appears verbatim; the permitted list
covers what is shown; the guard (unchanged, the integrator's) passes an echo and fails an
invention; the referral notice appears only when the caller will append the referral. The
parser for the classifier's one word. The case set's shape.

Run standalone against the Kotlin compiler in the Gradle cache, because the laptop's daemon is
the integrator's (`COORDINATION.md`): **43 tests, 0 failures.** Not yet run under Gradle; the
two new test files need the same input declarations the utterance set has, which is a build-file
change and is asked for in `COORDINATION.md`.

## What this does not claim

That the classifier routes correctly, at any rate. That the model uses the rows rather than its
own memory. That the answer fits any latency budget. That any of it survives Telugu. Every one
of those is a device measurement and the first run replaces this paragraph.
