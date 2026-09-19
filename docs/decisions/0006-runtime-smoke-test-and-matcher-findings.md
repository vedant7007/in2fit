# 0006 - The LLM runtime works, and what measuring the matcher found

Status: accepted. Desktop and cross-compile verified; nothing on a phone.

## Why this was done before Hilt

The spec has now been wrong twice about what exists: the Indic Whisper packs in 10.2 and the
FastPitch ONNX in 10.3. Both were plausible from the documentation and neither survived contact.
The LLM runtime was the last large unverified assumption, and it is load bearing. Wiring
dependency injection around a runtime nobody had run would have been building on the same kind of
assumption that has already failed twice.

## Result: llama.cpp runs Qwen 2.5 1.5B Q4_K_M

Read out of `logs/llama-smoke.log`. Built from source, commit `b23701f`.

    where:  Linux VM, 2 CPU cores, 3.9 GB RAM, CPU only, no GPU and no NPU
    load:   10.08 s
    prompt: 22 tokens in 525.77 ms   (41.84 tok/s)
    eval:   23 tokens in 2324.65 ms  (9.89 tok/s)
    total:  12.75 s wall for load plus 40 tokens

The prompt was the app's actual extraction task, and the output was this, verbatim:

    Extract food items and quantities as JSON. Input: two rotis and a katori of dal. JSON: ```json
    {
      "rotis": 2,
      "dal": "a katori"
    }```

Worth noting for spec 11.6: the model produced valid, correctly structured JSON on the first
attempt with no fine-tuning and no retry, on the task it will actually do.

**WHAT THIS DOES NOT SAY.** It says nothing about phone speed, phone memory, thermal behaviour or
co-residency. Two desktop cores are not a phone. Nobody may quote 9.89 tok/s as a Katori number;
it is a "the runtime and this model file work together" number and nothing more.

## The Android native path compiles

Read out of `logs/llama-android-build.log`. Cross-compiled with NDK 28.2.13676358, CMake 3.22.1,
`ANDROID_ABI=arm64-v8a`, `ANDROID_PLATFORM=android-26`:

    libggml-base.so   8,757,040 bytes
    libggml-cpu.so    4,955,576 bytes
    libggml.so        1,812,136 bytes
    libllama.so      39,694,760 bytes

So the NDK toolchain, the ABI restriction and the minSdk target all work together. **These
libraries have never been loaded.** Compiling is not running, and the JNI bridge is not written.

LiteRT-LM remains the named alternative. Swapping it is an `LlmEngine` implementation change and
not a contract change, which is exactly why that interface has two methods and no third path.

## What measuring the matcher found

Spec 13.5's "handles anything from A to Z" was replaced by a measured match rate. The measurement
found five wrong answers on its first run, and every one was a real bug.

| Utterance | Resolved to | Why |
| --- | --- | --- |
| `biryani` | bay leaf | alias "biryani aaku" contained the query |
| `upma` | semolina | alias "upma rava" contained the query |
| `atta` | curry leaves, a no-data item | alias "kadi patta" contained "atta" |
| `avalu` | horse gram, a no-data item | alias "ulavalu" contained "avalu" |
| `kandi pappu` | cooked toor dal | the same alias was authored on two different foods |

Three of those are a whole dish collapsing onto one of its ingredients, which is the worst shape
of wrong answer here: the number that follows looks entirely reasonable.

### Four fixes

1. **Containment runs in one direction only, on word boundaries.** The alias must appear inside
   the utterance as a whole run of words. "two spoons of groundnut oil" still finds groundnut oil;
   "biryani" no longer finds "biryani aaku".
2. **The no-data list is never fuzzy-matched.** Refusing a food because its name merely resembles
   something we hold no data for is worse than missing it. The measurement caught "gajar", carrot,
   being refused as "gawar", cluster beans, one edit apart.
3. **Fuzzy tolerance tightened** from 2 to 1 in the 5-to-12 character band. It had pulled
   "bendakaya", okra, onto "dondakaya", ivy gourd.
4. **An ambiguous-alias assertion in the importer.** One spoken name may map to exactly one food,
   or the import fails. It immediately found three more that nobody had noticed: "rice", "chawal"
   and "chana" each sat on both the raw and the cooked record, and whichever loaded last won
   silently. Bare names now mean the cooked form, which is what a person logging a meal means.

### Measured, after the fixes

    utterances                 176
    ingredient utterances      144
      resolved correctly       143   (99.3 %)
      missed, asked the user     1
    no-data utterances          22
      refused correctly         22   (100.0 %)
    expected-miss utterances    10
      correctly not matched     10
    WRONG FOOD                   0   <- the number that matters

The match rate is not the number to watch. WRONG FOOD is. A miss is honest and the user is asked;
a wrong food silently puts a wrong number in a health app. The test asserts zero wrong foods and a
match-rate floor, so a regression in either fails the build.

**The utterance set is authored, not recorded.** It is what people plausibly say, written down. It
is not a substitute for the 100+ real spoken meal logs in spec 18.3, and it cannot show what the
recogniser will actually emit. It gets replaced by those transcripts when they exist.

## Corpus, now

81 foods, 513 aliases across roman, Telugu and Devanagari, 13 deliberate no-data items.

One find worth naming: **gongura is in USDA**, as "Roselle, raw", its botanical common name. An
earlier sweep had reported it absent from every available source. Same species, so no substitute
caveat. Thotakura, palakura, ash gourd, colocasia, bobbarlu, cowpea, goat, prawn and the whole
Telugu spice set are also real records rather than approximations.

Six more items joined the no-data list because no honest record exists: ivy gourd, snake gourd,
cluster beans, methi leaves, horse gram and wheat vermicelli. Ulavacharu cannot be computed
without horse gram, and that stays true until a licensed Indian source exists.
