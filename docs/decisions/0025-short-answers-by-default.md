# 0025. One-sentence answers are the default for a spoken turn

Date: 20 September 2026. Status: accepted, ruled by Vedant. Supersedes section 3 of `0024`,
which prepared the variant and did not apply it.

## The evidence

The first measured conversational turn, from the integrator's device run
(`logs/hw-report-conversational.txt`, 8 threads, warm):

    ANSWER      591 prompt tokens / 17.3 s  +  103 generated / 13.3 s  =  30.7 s
    RECOMMEND   779 prompt tokens / 17.0 s  +   86 generated / 11.6 s  =  28.8 s

Three times extraction. Generation runs at about 9 tok/s, and on a spoken turn TTS cannot start a
word until generation ends: the 13 s of generation is 13 s of silence after 17 s of prompt.

`AnswerLength.SHORT` caps generation at 48 tokens for ANSWER and 56 for RECOMMEND, about 5 to 6 s.
The authored quality set (`0024`) scores its one-sentence answers full marks on every row, by the
same scorer that scores the long ones, so by the project's own definition of a good answer the
cost of the shorter form is zero. It was the largest latency lever anyone held, and it was sitting
behind a test that asserted the old default.

## The ruling

- **`SHORT` is the default** for `ConversationPrompts.answer` and `recommend`, and the engine's
  budgets `ANSWER_MAX_TOKENS` / `RECOMMEND_MAX_TOKENS` are SHORT's, so the default prompt and the
  default budget move together and the integrator's engine picks both up unchanged.
- **`STANDARD` stays reachable** for a text path, where the person reads rather than waits, by
  passing it explicitly with its own budgets (120 / 160).
- The tests that asserted the old default now assert the new one, and one asserts that both
  lengths keep every figure, fact, constraint, referral notice and safety rule: the length line
  is the only thing that changes.

## What this does not settle

The other half of the turn. The prompt is now the larger cost (17 s for 591 to 779 tokens), and
it is the person's context lines plus six rows plus the rules. That is a separate lever, with
its own trade-off against the "never context-free" rule in `0020`, and it is not touched here.

Nothing about what the model writes in one sentence on the phone. The short answers scored are
authored; the device run of the quality set against this default is the measurement, both
thread counts, prompt and generation reported separately as `0014` does.

## Addendum, 20 September, evening: RECOMMEND has its own budget; the prompt side is cut

The first end-to-end run on the handset (Rao, 17:25) answered "I have anaemia, what should I eat
for iron" at SHORT with the bare words "Spices, cumin seed", 6 tokens: a broken answer, not a
short one. Ruled by Vedant (via Rao, 18:40): SHORT is not reverted; RECOMMEND gets its own room.
`RECOMMEND_MAX_TOKENS` is 88 (about 9 s at 9 tok/s) and SHORT's RECOMMEND rule reads "Two short
sentences: name one food from the list, then say why it helps, from the facts." ANSWER stays at
48 and one sentence.

The other half, "not touched here" above, is touched now: the integrator's trim of the request
left 394 (RECOMMEND) and 457 (ANSWER) prompt tokens against a 250 budget, with the system block
as the remainder. Both system blocks are about half their length, one clause per rule; the guards
behind the model (numeric, condition, `SafetyLine`) hold what the longer wording only asked for.
Unmeasured on the phone until the demo-condition run; the number to report is prompt tokens and
generation tokens separately, as `0014` does.

## Addendum, 22 September: RECOMMEND asks for a food, not a reason; the reason is quoted by code

Three e2e runs on the phone (21 Sep, 00:33 the last) ended RECOMMEND and SUGGEST with
`phrased = null`. Not the budget: the RECOMMEND turn generated 30 tokens in 3.2 s and the
orchestrator's `textOrNull()` dropped a guard's refusal, detail and all. On the JVM with the
same request (`RecommendSilenceTest`: the hostel list, the two iron rows, the haemoglobin
trigger), every sentence that says WHY in the model's own words is refused by the ClaimGuard,
"a good source of iron", "help raise haemoglobin", "vitamin C helps you absorb it", and only a
bare food name, or a name followed by a row quoted verbatim, passes. The SHORT rule widened on
20 Sep, "name one food, then say why it helps", asked for exactly what the guard refuses, and
the guard is right: a paraphrased claim is what it exists to refuse.

So: the model names the food and how to have it, one sentence of at most twelve words, no
reason and no figures, and the REASON IS APPENDED BY CODE, verbatim from the first row the
request carried (the row the engine used). `RECOMMEND_MAX_TOKENS` is 40, a runaway cap of about
4 s at 9 tok/s; a twelve-word sentence is under 25. Nothing the model can write in that shape
is a claim, so nothing is refused. The SUGGEST silence is the integrator's `phrase` path (its
prompt, the numeric guard, a 60-token cap the run hit exactly); the same missing detail hides
which guard, and the same fix applies: carry the refusal on the Advice event so the log names it.
Unmeasured on the phone until the integrator's next run; a JVM fix does not count here.
