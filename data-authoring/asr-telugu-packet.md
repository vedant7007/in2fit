# Telugu, in your own voice: six recordings, two minutes

Same as last night, different language. Read the **Latin line** aloud if you don't read Telugu
script; the Telugu line is there for anyone who does, the English line tells you what you are
saying. Say it the way you'd say it, not the way it is spelled.

## Recording

- Same phone, same recorder app, same folder as last night (`Music/Recordings/Standard Recordings`).
- A hand's width from your mouth, quiet room.
- **Press · pause · speak · finish the word · stop.** The pause after pressing is the important one:
  last night the recorder cut the first word of two sentences because you started as you pressed.
- One file per sentence. Don't re-record a stumble.
- File names, exactly (extension is whatever the recorder writes):

## The sentences

**vedant_te_01** ← record this one first
నేను రెండు రొట్టెలు మరియు పప్పు తిన్నాను
*Nenu rendu rottelu mariyu pappu tinnaanu*
(I ate two rotis and pappu.)

**vedant_te_02**
ఉదయం మూడు ఇడ్లీ, సాంబార్, కొబ్బరి పచ్చడి తిన్నాను
*Udayam moodu idli, sambar, kobbari pachchadi tinnaanu*
(In the morning I ate three idli, sambar and coconut chutney.)

**vedant_te_03**
రాత్రి two rotis, dal fry, one glass milk తీసుకున్నాను
*Raatri two rotis, dal fry, one glass milk teesukunnaanu*
(At night I had two rotis, dal fry and a glass of milk. The English words stay English, said your way.)

**vedant_te_04**
నాకు షుగర్ ఉంది, రాత్రి ఏం తినాలి?
*Naaku sugar undi, raatri em tinaali?*
(I have sugar, what should I eat at night?)

**vedant_te_05**
నేను ఒకటి... కాదు, రెండు దోసెలు తిన్నాను, పచ్చడితో
*Nenu okati... kaadu, rendu doselu tinnaanu, pachchadi-to*
(I ate one... no, two dosas, with chutney. The pause and the correction are meant.)

**vedant_te_06** — optional; only if you have another thirty seconds
ఒక ప్లేట్ అన్నం, పప్పు, ఒక కప్పు పెరుగు, ఒక ఉడికించిన గుడ్డు తిన్నాను
*Oka plate annam, pappu, oka kappu perugu, oka udikinchina guddu tinnaanu*
(I ate a plate of rice, pappu, a cup of curd and one boiled egg. The first five sentences have no
rice, curd or egg in them, and the demo does; this one carries them.)

Then send the files back the same way, and one line: whether you can judge if spoken Telugu
sounds right, or only speak it.

---

## What happens to them, written before any of them exists

Jacob runs, the moment they land:

    python tools/asr_eval.py manifest <folder> data-authoring/telugu-packet-set.csv
    python tools/asr_eval.py wer <folder>/manifest.csv
    python tools/asr_eval.py wer --engine omnilingual <folder>/manifest.csv     (comparison only)

through the `te` IndicConformer checkpoint, the same way the twenty Hindi and English files were
scored, and posts the table verbatim.

**The threshold, fixed now (docs/decisions/0031, "Telugu on the presenter's voice"):**

1. **Food words heard, as spoken: 10 of 10 on the five required sentences** (రొట్టెలు, పప్పు; ఇడ్లీ,
   సాంబార్, కొబ్బరి పచ్చడి; రోటీస్, దాల్, మిల్క్; దోసెలు, పచ్చడితో). One lost food is the failure a
   demo cannot carry, and 16 of 16 is what Hindi scored on this voice; Telugu has to match that bar,
   not approach it. Row 6, if recorded, adds four more and is reported, not required.
2. **Reference words all present (exact, or extra words only) on at least 3 of the 5**, the bar Hindi
   met (5 of 10).

Both met: the Telugu path is **demo-capable on this voice**, and becomes a second option for a NEW
ruling with its own numbers. It does not reverse anything: Hindi, Marathi-accented, in Vedant's
own voice stands. (1) met and (2) not: it hears the foods and garbles the frame; not yet. (1) not
met: not demo-capable for Telugu on this voice, and the question closes on a measurement.

**What this does and does not measure.** It measures whether the recogniser can hear his Telugu.
It does not measure whether the Telugu the app speaks back is correct; that is Nila's frozen review
packet, and neither answers the other. A good number here is not a licence to ship unreviewed
Telugu wording.

Five sentences, one speaker, a non-native one (native Marathi; Telugu by his own account, which is
unverified until his answer above). Small sample; reported as one.
