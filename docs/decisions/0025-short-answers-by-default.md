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
