# IN2FIT: a short word list for a Telugu and Hindi speaker to check

Different from the strings sheet: nothing to write, only yes-or-no answers about single words.
Ten minutes. Send it with `docs/localisation/telugu-review-queue.md`, or on its own.

Reviewer's name: ______________________

## What this is for

When a person speaks to the app, one rule decides whether they are *telling the app what they
ate* (which the app writes into their food history) or *asking it something* (which it must
never write into their history). The rule is written in Roman letters, and Telugu, Hindi and
English words share those letters. We already found one collision by measuring: English "do"
(as in "do I need...") is Hindi "do" (two), and every "maine do roti khaya" was being treated
as a question. There may be others, and they are the kind of thing only a speaker sees.

The rule is deliberately lopsided. Treating a meal as a question costs the person two seconds.
Treating a question as a meal writes food they never ate into their health history. So:

## The words that matter most: "I ate" / "I drank"

The app treats a sentence containing one of these as **certainly a meal**, if nothing else in it
looks like a question. **A wrong word here is the dangerous kind.** For each word, please answer:

- Does it mean *ate* or *drank*, in the past, the way a person would say what they had?
- Could the same spelling be a different word in Telugu, Hindi or Telugu-English mixing, that
  someone might say when asking a question rather than reporting a meal?
- Is there a common everyday spelling or form we have missed?

| # | Word | Meant as | Past-tense ate/drank? (yes/no) | Could mean something else? | Missing form? |
| --- | --- | --- | --- | --- | --- |
| 1 | `khaya` | Hindi, ate | | | |
| 2 | `khayi` | Hindi, ate (fem.) | | | |
| 3 | `khaye` | Hindi, ate (pl.) | | | |
| 4 | `khaaya` | Hindi, ate, long-vowel spelling | | | |
| 5 | `khai` | Hindi, ate, short spelling | | | |
| 6 | `piya` | Hindi, drank | | | |
| 7 | `pi` | Hindi, drank, short spelling | | | |
| 8 | `peeya` | Hindi, drank, long-vowel spelling | | | |
| 9 | `tinnanu` | Telugu, I ate | | | |
| 10 | `tinnaanu` | Telugu, I ate, long-vowel spelling | | | |
| 11 | `thinnanu` | Telugu, I ate, th- spelling | | | |
| 12 | `tinna` | Telugu, ate, colloquial | | | |
| 13 | `thinna` | Telugu, ate, th- spelling | | | |
| 14 | `tinnam` | Telugu, we ate | | | |
| 15 | `tinnaam` | Telugu, we ate, long-vowel spelling | | | |
| 16 | `tagaanu` | Telugu, I drank | | | |
| 17 | `taganu` | Telugu, I drank, short spelling | | | |
| 18 | `thaganu` | Telugu, I drank, th- spelling | | | |

English words on the same list, for completeness: `ate`, `had`, `drank`, `eaten`, `finished`,
and the phrases `breakfast was`, `lunch was`, `dinner was`, `snack was`, `tiffin was`.

## The words that make the app ask instead

A sentence containing any of these is sent to the app's slower, careful path. **A wrong word
here only costs two seconds**, so guessing is safe, but a common *meal* word on this list would
slow every meal down. Please only flag one if it is a word people use when *reporting a meal*.

Hindi: `kya kitna kitni kitne kaise kaun kaunsa kaunsi kab kahan kyun kyon chahiye karun karoon
karu sakta sakti sakte hoon raha rahi rahe abhi batao bataye bataiye accha achha behtar sahi
theek`; phrases `kha raha`, `kha rahi`, `kha rahe`, `hai to`, `hai toh`, `ke liye`.

Telugu: `emi em enti entha enni ela ekkada eppudu evaru endhuku enduku tinali tinaali thinali
cheyali cheyyali kavali kavaali cheppu cheppandi ippudu tintunna tintunnanu tintunnam manchidi
manchida bagunda avuna kada`; phrase `kosam`.

English (listed so you can spot a Telugu or Hindi word hiding in it, like "do"): `what whats
which how why when where who whether should shall can could would will may might must does did
are am any anything enough add suggest recommend recommendation advice advise avoid better best
worse help helps need needs want wants prefer instead swap replace option options question tell
show check compare versus vs ok okay fine good healthy report doctor checked reading level
medicine tablet tablets remove delete undo cancel correct change edit wrong mistake having eating
making cooking planning going now tonight later`. And `do` and `is`, only at the start of a
sentence.

Any English word above that is ALSO an everyday Telugu or Hindi word people would say while
reporting a meal: write it here.

______________________________________________________________________________

## Second list: words about medicine, illness and diagnosis

Separate rule, same kind of question. When a person's sentence contains one of these, the app
adds a line saying a doctor should judge it; and when the app's own reply contains one of the
phrases, the reply is thrown away. A wrong word here costs one extra sentence or one lost reply,
never a wrong meal, so this list matters less than the first. Still: is any of these a common
everyday word that means something else?

Hindi: `dawai dawa goli goliyan bimari bimar khatra khatarnak ilaj`; phrases `hai kya`, `kya hai`,
`kya mujhe`, `mujhe kya hua`. Replies thrown away if they contain: `aapko ... hai` / `tumhe ...
hai` with an illness word, and `goli lo`, `dawai lo`, `goli roz lo`, `dawai lena`.

Telugu: `mandu mandulu matra jabbu rogam pramadam pramadakaram vaidyam`; phrases `unda`,
`vachinda`, `tagginda`, `perigindha`. Replies thrown away if they contain: `meeku ... undi` /
`neeku ... undi` with an illness word, and `mandu veskondi`, `matra veyandi`, `tablets
thesukondi`.

Any of these wrong, or a common form missing: write it here.

______________________________________________________________________________

Send it back to Vedant. The lists live in `LogPrefilter.kt` and `SafetyLine.kt`, and a test
keeps this sheet and the log-word list in step, so a word you flag is changed in one place.
