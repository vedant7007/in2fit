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


## Third list: food and unit names in Telugu and Hindi script

These are what the app expects the speech recogniser to write when a person says an English
food word the Indian way ("paneer", "bread", "one glass"), plus the ordinary Telugu and Hindi
words for the same things. Every one was GENERATED, most from the recogniser's own output on a
synthetic voice (`logs/asr-codemix-renderings.log`), by someone who does not read either script.
A wrong one here logs the wrong food, so please check each: does it say what the third column
says, and would a person actually write or say it that way? Cross out any that are wrong or
nonsense; the ones marked "as the model garbled it" are deliberate copies of a machine's
mistakes and only need a yes if a real speaker could plausibly produce them too.

| # | Written | Script | Meant as | Points to | Origin | OK? |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `మిల్క్` | te | milk (English word) | milk | model log, said | |
| 2 | `మిలిచ` | te | milk, as the model garbled it | milk | model log, emitted | |
| 3 | `ఎగ్` | te | egg | egg | model log, said | |
| 4 | `ఎది` | te | egg, as the model garbled it | egg | model log, emitted | |
| 5 | `బాయిల్డ్ ఎగ్` | te | boiled egg | egg | model log, said | |
| 6 | `బాయిల్డే ఎగ` | te | boiled egg, as the model garbled it | egg | model log, emitted | |
| 7 | `బ్రెడ్` | te | bread | white bread | model log | |
| 8 | `బ్రెడ్డు` | te | bread, with a final u | white bread | generated | |
| 9 | `బ్రౌన్ బ్రెడ్` | te | brown bread | whole-wheat bread | generated | |
| 10 | `రైస్` | te | rice | cooked rice | model log | |
| 11 | `చికెన్` | te | chicken | chicken | model log | |
| 12 | `కర్డ్` | te | curd | curd | model log, said | |
| 13 | `కడ్డ` | te | curd, as the model garbled it | curd | model log, emitted | |
| 14 | `కర్డు` | te | curd, with a final u | curd | generated | |
| 15 | `కాఫీ` | te | coffee | coffee with milk and sugar | model log | |
| 16 | `కాపీ` | te | coffee, p spelling | coffee with milk and sugar | generated | |
| 17 | `బ్లాక్ కాఫీ` | te | black coffee | black coffee | generated | |
| 18 | `టీ` | te | tea | chai | model log, said | |
| 19 | `తీ` | te | tea, as the model garbled it | chai | model log, emitted | |
| 20 | `చాయ్` | te | chai | chai | generated | |
| 21 | `చాయ` | te | chai, without the final virama | chai | generated | |
| 22 | `బ్లాక్ టీ` | te | black tea | black tea | generated | |
| 23 | `జ్యూస్` | te | juice | juice (assumed orange) | model log | |
| 24 | `జూస్` | te | juice, short spelling | juice (assumed orange) | generated | |
| 25 | `ఆరెంజ్ జ్యూస్` | te | orange juice | orange juice | generated | |
| 26 | `మామిడి జ్యూస్` | te | mango juice | mango juice | generated | |
| 27 | `బిస్కెట్` | te | biscuit | Marie biscuit | model log, said | |
| 28 | `బిస్కెడి` | te | biscuit, as the model garbled it | Marie biscuit | model log, emitted | |
| 29 | `బిస్కట్` | te | biscuit, short spelling | Marie biscuit | generated | |
| 30 | `బనానా` | te | banana (English word) | ripe banana | model log | |
| 31 | `అరటిపండు` | te | ripe banana (Telugu word) | ripe banana | generated | |
| 32 | `అరటి పండు` | te | ripe banana, two words | ripe banana | generated | |
| 33 | `ఆపిల్` | te | apple | apple | model log, said | |
| 34 | `ఆపిలి` | te | apple, as the model garbled it | apple | model log, emitted | |
| 35 | `యాపిల్` | te | apple, ya spelling | apple | generated | |
| 36 | `చపాతీ` | te | chapati | chapati | model log | |
| 37 | `రోటీ` | te | roti | chapati | model log | |
| 38 | `దాల్` | te | dal (Hindi word) | dal tadka | model log | |
| 39 | `దాలు` | te | dal, with a final u | dal tadka | generated | |
| 40 | `పనీర్` | te | paneer | REFUSED by name: no honest record | model log, said | |
| 41 | `పనీరు` | te | paneer, as the model rendered it | REFUSED by name | model log, emitted | |
| 42 | `బటర్` | te | butter | butter | model log, said | |
| 43 | `బటరు` | te | butter, as the model rendered it | butter | model log, emitted | |
| 44 | `చీజ్` | te | cheese | processed cheese | model log, said | |
| 45 | `చీరి` | te | cheese, as the model garbled it | processed cheese | model log, emitted | |
| 46 | `చీజు` | te | cheese, with a final u | processed cheese | generated | |
| 47 | `షుగర్` | te | sugar | sugar | model log, said | |
| 48 | `షుగరు` | te | sugar, as the model rendered it | sugar | model log, emitted | |
| 49 | `ఆయిల్` | te | oil | groundnut oil (the default oil) | model log, said | |
| 50 | `ఆయలు` | te | oil, as the model garbled it | groundnut oil | model log, emitted | |
| 51 | `వాటర్` | te | water | water | model log, said | |
| 52 | `వాటరు` | te | water, as the model rendered it | water | model log, emitted | |
| 53 | `నీళ్లు` | te | water (Telugu word) | water | generated | |
| 54 | `నీళ్ళు` | te | water, alternate spelling | water | generated | |
| 55 | `ఫ్రైడ్ రైస్` | te | fried rice | REFUSED by name: no recipe yet | model log, said | |
| 56 | `ఫైడ్ రైస్` | te | fried rice, as the model garbled it | REFUSED by name | model log, emitted | |
| 57 | `గ్లాస్` | te | glass (unit) | a glass, 200 g | model log | |
| 58 | `గ్లాసు` | te | glass, with a final u | a glass, 200 g | generated | |
| 59 | `కప్` | te | cup (unit) | a cup | generated | |
| 60 | `కప్పు` | te | cup, Telugu form | a cup | generated | |
| 61 | `కప్స్` | te | cups | a cup | model log, said | |
| 62 | `కత్స్` | te | cups, as the model garbled it | a cup | model log, emitted | |
| 63 | `కటోరీ` | te | katori (unit) | a katori | generated | |
| 64 | `గిన్నె` | te | small bowl (Telugu word) | a katori | generated | |
| 65 | `బౌల్` | te | bowl | a bowl | generated | |
| 66 | `ప్లేట్` | te | plate | a plate | generated | |
| 67 | `ప్లేటు` | te | plate, with a final u | a plate | generated | |
| 68 | `స్పూన్` | te | spoon | a serving spoon | generated | |
| 69 | `స్పూను` | te | spoon, with a final u | a serving spoon | generated | |
| 70 | `చెంచా` | te | spoon (Telugu word) | a serving spoon | generated | |
| 71 | `టేబుల్ స్పూన్` | te | tablespoon | a tablespoon | generated | |
| 72 | `టీ స్పూన్` | te | teaspoon | a teaspoon | generated | |
| 73 | `టీస్పూన్` | te | teaspoon, one word | a teaspoon | generated | |
| 74 | `స్లైస్` | te | slice (unit) | one slice of bread, 29 g | generated | |
| 75 | `ముక్క` | te | piece (Telugu word) | one biscuit | generated | |
| 76 | `టుకడా` | te | piece (Hindi word in Telugu script) | one biscuit | generated | |
| 77 | `मिल्क` | hi | milk (English word) | milk | generated | |
| 78 | `उबला अंडा` | hi | boiled egg | egg | generated | |
| 79 | `एग` | hi | egg (English word) | egg | generated | |
| 80 | `राइस` | hi | rice (English word) | cooked rice | generated | |
| 81 | `चिकन` | hi | chicken | chicken | generated | |
| 82 | `बटर` | hi | butter (English word) | butter | generated | |
| 83 | `शुगर` | hi | sugar (English word) | sugar | generated | |
| 84 | `तेल` | hi | oil | groundnut oil | generated | |
| 85 | `ऑयल` | hi | oil (English word) | groundnut oil | generated | |
| 86 | `ब्रेड` | hi | bread | white bread | generated | |
| 87 | `डबल रोटी` | hi | bread | white bread | generated | |
| 88 | `ब्राउन ब्रेड` | hi | brown bread | whole-wheat bread | generated | |
| 89 | `चीज़` | hi | cheese | processed cheese | generated | |
| 90 | `चीज` | hi | cheese, without nukta | processed cheese | generated | |
| 91 | `बिस्कुट` | hi | biscuit | Marie biscuit | generated | |
| 92 | `बिस्किट` | hi | biscuit, alternate spelling | Marie biscuit | generated | |
| 93 | `केला` | hi | banana | ripe banana | generated | |
| 94 | `सेब` | hi | apple | apple | generated | |
| 95 | `एप्पल` | hi | apple (English word) | apple | generated | |
| 96 | `संतरे का जूस` | hi | orange juice | orange juice | generated | |
| 97 | `जूस` | hi | juice | juice (assumed orange) | generated | |
| 98 | `आम का जूस` | hi | mango juice | mango juice | generated | |
| 99 | `पानी` | hi | water | water | generated | |
| 100 | `काली चाय` | hi | black tea | black tea | generated | |
| 101 | `काली कॉफी` | hi | black coffee | black coffee | generated | |
| 102 | `चाय` | hi | tea | chai | generated | |
| 103 | `टी` | hi | tea (English word) | chai | generated | |
| 104 | `कॉफी` | hi | coffee | coffee with milk and sugar | generated | |
| 105 | `कॉफ़ी` | hi | coffee, with nukta | coffee with milk and sugar | generated | |
| 106 | `चपाती` | hi | chapati | chapati | generated | |
| 107 | `पनीर` | hi | paneer | REFUSED by name | generated | |
| 108 | `फ्राइड राइस` | hi | fried rice | REFUSED by name | generated | |
| 109 | `गिलास` | hi | glass (unit) | a glass | generated | |
| 110 | `ग्लास` | hi | glass (English word) | a glass | generated | |
| 111 | `कप` | hi | cup | a cup | generated | |
| 112 | `कटोरी` | hi | katori | a katori | generated | |
| 113 | `कटोरा` | hi | bowl | a bowl | generated | |
| 114 | `प्लेट` | hi | plate | a plate | generated | |
| 115 | `चम्मच` | hi | spoon | a serving spoon | generated | |
| 116 | `बड़ा चम्मच` | hi | tablespoon | a tablespoon | generated | |
| 117 | `छोटा चम्मच` | hi | teaspoon | a teaspoon | generated | |
| 118 | `स्लाइस` | hi | slice (unit) | one slice of bread | generated | |
| 119 | `टुकड़ा` | hi | piece | one biscuit | generated | |

Missing common forms (write them in):

______________________________________________________________________________

Send it back to Vedant. The lists live in `LogPrefilter.kt`, `SafetyLine.kt` and the `data-authoring` CSVs, and tests
keep this sheet in step with the log-word list, so a word you flag is changed in one place.
