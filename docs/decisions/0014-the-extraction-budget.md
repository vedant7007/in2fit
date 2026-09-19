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

## So the lever is the ANSWER, not the prompt

Untried, and the obvious next thing: the model currently emits
`"quantity":null,"unit":null,"method":null` on every item even when nothing was said. Three items
of that is a large share of a 55-token answer. `ExtractionJson` already accepts an absent
optional field as null, and there is a passing test for it, so the schema does not need these
keys at all.

That is a change to what the model is asked to emit, so it is measured against the correctness
cases before it is believed, exactly as the prompt change was.

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
