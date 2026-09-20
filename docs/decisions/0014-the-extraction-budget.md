# 0014. The extraction budget, measured, and why shortening the prompt did not help

Date: 19 September 2026. Status: accepted. Supersedes the 3.5 s figure in spec 10.5 for the
extraction stage.

## The number

**Extraction of one spoken meal takes about 10.7 s on the test device.** realme RMX3780,
MT6835, 2x Cortex-A76 + 6x Cortex-A55, `+dotprod+fp16` build, 8 threads, warm:

    prompt      221 tokens in  4,255 ms   (51.94 tok/s)
    generation   55 tokens in  6,074 ms   ( 9.06 tok/s)
    round trip              10,731 ms

ASR and TTS are not in that figure and are not written yet. Spec 10.5's 3.5 s was set before
anything had run; this replaces it for this stage.

## GENERATED TOKENS COST FIVE TIMES WHAT PROMPT TOKENS COST

This is the finding, and it is the opposite of the assumption that sent us looking.

    prompt processing    ~38-52 tok/s
    token generation      ~9-10 tok/s

The prompt is four times longer than the answer and takes less time than it. A prompt token is
roughly a fifth the cost of a generated token, so trimming the prompt is the weak lever and the
answer is the strong one.

## The experiment that proved it

The prompt was cut from 207 tokens to 162, keeping every instruction that defends a safety
property and dropping the opening sentence, the "Rules:" header, the worked example, the repeated
quantity rule and "No markdown". Both configurations were then run against the same five
extraction cases on the device.

| | prompt tokens | cases passed | round trip |
| --- | ---: | ---: | ---: |
| shortened | 162 | **3 of 5** | ~10.6 s |
| restored | 221 | **4 of 5** | 10.7 s |

**The shortening bought no time and cost a food.** Two reasons, both measured:

1. Dropping "No markdown" made the model wrap its answer in a ``` fence and pretty-print the JSON
   inside it. That is roughly fifty extra GENERATED tokens, at 9 tok/s, which is about five
   seconds, against the roughly one second saved on the prompt. The cheaper prompt produced a
   more expensive answer.
2. It dropped items. "I ate idli and sambar" came back as idli alone, a case that passes with the
   longer prompt. A missing food is the failure this package exists to prevent.

Two lines were kept from the experiment because they fix failures it exposed: "Every food they
named gets an entry" and "Never repeat a field".

## The lever is the ANSWER, not the prompt. That was tried too, and it also failed

The model emits `"quantity":null,"unit":null,"method":null` on every item even when nothing was
said. Across three items that is a large share of a 55-token answer, and `ExtractionJson` accepts
an absent optional field as null with a passing test, so the schema does not need those keys.

So the prompt was changed to show `{"items":[{"name":"..."}]}` and to say leave the field out.

**It is unstable and it is reverted.** The five correctness cases passed. The very next test then
refused the SAME transcript with `'quantity' is not a number`, at a different thread count,
because the model had emitted the quantity as a string.

Valid at eight threads and invalid at four is not a working prompt. It is a prompt sitting close
enough to a decision boundary that floating-point accumulation order pushes it over, which is the
same effect `0011` records for the toolchain. Showing the model the full shape, nulls included,
keeps it on that shape.

**This is the second latency idea in a row that measurement killed**, and both died the same way:
the saving was real and something else got worse. The remaining honest levers are the product
decisions, not another prompt edit.

Anyone retrying it measures across BOTH thread counts, and treats a pass on one as meaningless.

## The conversational turn, measured 20 September, and the ruling that followed

Every number above is extraction's: 221 prompt tokens, 55 generated. The first ANSWER and
RECOMMEND turns were timed on 20 September with the request built exactly as the orchestrator
builds it (the person's period totals, two meals, a lab line, the engine's sentence, six
knowledge rows): **688 prompt tokens for ANSWER, 779 for RECOMMEND**. Same device, `+dotprod+fp16`
build, from `HardwareProbeTest.b3_conversationalTurns`; "cold" is the first inference after a
fresh load in a fresh process, page cache uncontrolled.

    idle phone, screen awake, 04:15          4 thr   ANSWER cold 32.5 s (prompt 24.1 s @28.5 tok/s, gen 43 tok 8.1 s)   warm 22.8 s (17.8 + 4.8)
                                             8 thr   ANSWER cold 31.4 s (24.8 + 6.4)                                    warm 30.7 s (17.3 + 13.3, 103 tok)
                                                     RECOMMEND warm 28.8 s (17.0 + 11.6, 86 tok)
    hot, in use, thermal SEVERE, 14:48       4 thr   ANSWER cold 51.3 s   warm 133 s (screen off; prompt at 5.6 tok/s)
                                             8 thr   ANSWER cold 40.1 s   warm 32.5 s (23.4 + 9.0);  RECOMMEND cold 52.0 s, warm 45.5 s

**A spoken ANSWER never came back under 20 s.** The prompt is 17-33 s of it on its own; generation
is 5-14 s. The floor with every tuning applied (short answer, prefix cache, 8 threads) is about
15 s warm on an idle phone, and the demo phone will be neither warm-idle nor ours alone.

### An error, on the record, in Vedant's words

"I reverted prompt trimming after Priya measured generation at roughly 5x prompt cost per token.
Your numbers show why that was wrong: the prompt is 16x longer, so it dominates regardless. The
specific revert, putting 'No markdown' back after its removal made the model fence its output,
was correct. The conclusion I drew from it, that the prompt was the weak lever, was not."

The 5x per-token ratio in this record is still true. It is the wrong quantity to reason from
when the prompt is 16x the length of the answer: per-token cost times token count is what the
person waits for, and on the conversational shape that product is 24 s of prompt against 6 s
of answer. Nobody should re-derive "trim the answer, not the prompt" from the ratio above.

### The ruling: architecture, not tuning

1. **Precompute RECOMMEND.** Advice is generated when the meal is logged and regenerated when a
   lab report is saved; the save is the trigger, and the scan's own pause hides the
   regeneration. Beat 4 becomes instant. Invalidation is by `RuleEvaluation.inputDigest`.
2. **Stop sending the model a choice.** Six rows, two meals, a lab line and totals is the model
   deciding which applies, and the design claim is that it does not decide. Code selects the
   one or two rows the fired rules point at, and only the figures those rules used go in.
   Target under 250 prompt tokens: about 7 s of prompt instead of 24, and free accuracy.
3. Then the tail: the short answer as default (`0025`), KV-cache prefix reuse for the constant
   system block, **8 threads pinned for the demo build**.

**And the turn shape that makes it a product regardless:** for "how much protein today" the
number is in the database. The person's own figures go on screen first, straight from the store
(`OrchestratorEvent.OwnFigures`), with the lead-in speaking; the model's sentence arrives later
and is spoken. The answer is on screen in under a second; a 12-second model is phrasing.

### Flagged, not chased

**Thread count changed the model's output.** At 4 threads the same ANSWER request produced a
computed "14.6" on every pass and the guard refused it; at 8 threads it quoted the period total
correctly, and it was faster. `0011` already records that a compiler flag moved an argmax; this
is the same class of finding one level down. It is why 8 threads is pinned, and it is not being
investigated this week.

**Every number above was taken with airplane mode off** (adb over TCP), and the afternoon's on a
phone with Instagram in the foreground and thermal at SEVERE. The demo condition (USB, airplane
on, nothing else running, screen held, ambient) has never been measured; that run's number is
the one that goes in the deck, after 1 and 2 land.

## What the UI has to do about it

Ten seconds of silence reads as a hang. Ten seconds with continuous visible feedback reads as
work. The round trip carries a visible state from the moment speech ends: a recording meter while
they talk, an acknowledgement at endpoint, then a progress state through extraction. A longer
round trip with visible progress is a better demo than a shorter silent one, and far better than
a fast wrong number.

## Still open

The 1.5B model drops a food on "I drank 200 ml of milk and ate one boiled egg", in BOTH prompt
configurations. That is a model-capability finding, not a prompt-length one, and it is the kind of
thing the measured case set exists to keep visible. It is recorded, not fixed.
