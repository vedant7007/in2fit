package io.github.vedant7007.katori.ml.llm

import io.github.vedant7007.katori.data.food.FoodTextMatching

/**
 * The two deterministic halves of the `0015` safety line that a prompt cannot guarantee.
 *
 * The line: the model MAY explain, guide, suggest and encourage; it MAY NOT diagnose, prescribe,
 * or pass a clinical verdict, and a serious matter gets a doctor referral ALONGSIDE help, never
 * instead of it. The prompt states that. This file is what holds when the model ignores it.
 *
 * TWO CHECKS, ONE ON EACH SIDE OF THE MODEL.
 *
 * 1. [invitesClinicalJudgement] reads the QUESTION. "Do I have diabetes?", "how much iron tablet
 *    should I take?", "my haemoglobin is 7, is that dangerous?" each ask for something the app
 *    must not give. The orchestrator's referral is decided by the rules engine from a scanned
 *    lab value; a question like these, spoken with no report on file, would get no referral at
 *    all. This makes the referral a property of the question as well, and it is decided before
 *    the model runs, by a rule, so no sample can drop it.
 *
 * 2. [prescribesOrJudges] reads the RESPONSE. The numeric guard catches an invented number and
 *    the engine's condition check catches an undeclared diagnosis; neither catches "take two
 *    tablets a day" when the 2 was permitted, or "a reading of 7 is dangerous" when the 7 was
 *    the person's own. This is the third structural check on a response, the same shape as the
 *    other two: a short deliberate list, not a medical vocabulary.
 *
 * THE ASYMMETRY, again. A question flagged here that did not need it costs one extra sentence
 * telling the person a doctor can judge; a question missed costs a diagnosis wearing the app's
 * voice. So the question markers are over-inclusive. The response list is tighter, because a
 * false rejection there loses the whole answer: it names the phrases that are a prescription or
 * a verdict in any reading, and leaves "your doctor can tell you" alone, which is the sentence we
 * want.
 *
 * NOT MEASURED against a model. `data-authoring/safety-adversarial-set.csv` is the authored
 * set; `SafetyLineTest` proves the detector on it and proves the guards refuse the authored
 * bad answers and pass the authored good ones. What the model actually says to these questions
 * is a device measurement, and the first run of it is the first evidence for the Q&A answer.
 */
object SafetyLine {

    /**
     * True when the utterance asks for a diagnosis, a dose, a medication change, a danger or
     * risk verdict, or a verdict on a lab value the person states themselves.
     */
    fun invitesClinicalJudgement(utterance: String): Boolean {
        val text = FoodTextMatching.normalise(utterance)
        if (text.isEmpty()) return false
        val words = text.split(' ')
        if (words.any { it in CLINICAL_WORDS }) return true
        if (CLINICAL_PHRASES.any { FoodTextMatching.containsAsWords(text, it) }) return true
        // A stated lab value: a number next to a lab unit or a lab test name. The person is
        // asking about a reading, and the app only ever says what the printed range says.
        val bareNumber = words.any { w -> w.any { it.isDigit() } && w.all { it.isDigit() || it == '.' } }
        if (bareNumber && LAB_TERMS.any { FoodTextMatching.containsAsWords(text, it) }) return true
        return false
    }

    /**
     * The first phrase in [response] that prescribes, changes a medication, or passes a clinical
     * verdict; null when the response is clean. Case-insensitive, matched on whole words.
     */
    fun prescribesOrJudges(response: String): String? {
        val lower = response.lowercase()
        return RESPONSE_PATTERNS.firstNotNullOfOrNull { it.find(lower)?.value }
    }

    /**
     * Single words that, anywhere in a question, make it a clinical one. English, roman Hindi,
     * roman Telugu. CONDITION NAMES ARE DELIBERATELY ABSENT: "I have anaemia, what should I eat
     * for iron" is a food request from a person who told us their condition, and it gets help,
     * not a referral line on every turn. What makes a question clinical is the JUDGEMENT it asks
     * for, and symptoms and events a food app has no business reading.
     */
    internal val CLINICAL_WORDS: Set<String> = setOf(
        // diagnosis and verdicts
        "diagnose", "diagnosis", "diagnosed", "dangerous", "danger", "serious", "risk", "risky", "symptom", "symptoms",
        "cured", "cure", "reversed", "reverse", "safe", "unsafe", "abnormal", "wrong",
        // medicines and doses
        "tablet", "tablets", "pill", "pills", "capsule", "capsules", "dose", "dosage", "medication", "medications",
        "medicine", "medicines", "supplement", "supplements", "injection", "insulin", "metformin", "prescription", "prescribe",
        // events and symptoms, which are outside a food diary's competence
        "attack", "stroke", "cancer", "kidney", "liver", "pregnant", "pregnancy", "fever", "dizzy", "dizziness",
        "faint", "fainting", "pain", "chest", "vomiting", "bleeding",
        // roman Hindi, then Devanagari as the hi recogniser writes it; Vedant verifies
        "dawai", "dawa", "goli", "goliyan", "bimari", "bimar", "khatra", "khatarnak", "ilaj",
        "दवाई", "दवा", "गोली", "गोलियाँ", "बीमारी", "बीमार", "खतरा", "खतरनाक", "इलाज", "डॉक्टर",
        // roman Telugu
        "mandu", "mandulu", "matra", "jabbu", "rogam", "pramadam", "pramadakaram", "vaidyam",
    )

    /** Phrases that make a question clinical even when no single word above appears. */
    internal val CLINICAL_PHRASES: List<String> = listOf(
        "do i have", "am i", "is my", "are my", "is it serious", "is that bad", "is it bad", "is that ok", "is that okay",
        "is it ok", "is it okay", "should i stop", "should i take", "can i stop", "can i take", "how much should i take",
        "what is wrong", "whats wrong", "something wrong", "at risk", "under control", "gone away", "go away",
        "get better", "getting worse", "what dose", "how many tablets", "instead of",
        "hai kya", "kya hai", "kya mujhe", "mujhe kya hua", "है क्या", "क्या है", "क्या मुझे", "मुझे क्या हुआ",
        "unda", "vachinda", "tagginda", "perigindha",
    )

    /** A lab test name next to a bare number in the question is a reading the person is asking about. */
    internal val LAB_TERMS: List<String> = listOf(
        "haemoglobin", "hemoglobin", "hb", "glucose", "fasting", "hba1c", "a1c", "bp", "blood pressure", "ldl", "hdl",
        "triglycerides", "creatinine", "tsh", "b12", "vitamin d", "ferritin", "reading", "report", "level", "levels",
    )

    /**
     * Response phrases that are a prescription or a verdict in any reading. Each is anchored on
     * a verb or a verdict, not on a noun, so "your doctor can tell you whether a supplement
     * applies" and "the tablets your doctor gave you" pass, and "take two tablets" does not.
     */
    internal val RESPONSE_PATTERNS: List<Regex> = listOf(
        // a dose
        Regex("""\btake \S+ (mg|mcg|iu|tablets?|capsules?|pills?|supplements?)\b"""),
        Regex("""\btake (a|an|one|two|three|your|the|some) (\S+ )?(tablets?|capsules?|pills?|supplements?|medicines?|medications?)\b"""),
        // Anchored on a dose FORM, never on "a day": "19 mg of iron a day" is an allowance and
        // the sentence we want; "60 mg tablets" is a dose. The set caught the first version.
        Regex("""\b\d+ ?(mg|mcg|iu|ml) (of \S+ )?(tablets?|capsules?|pills?|supplements?|injections?)\b"""),
        // a medication change
        Regex("""\b(stop|start|skip|reduce|increase|double|halve|change) (taking )?(your |the |all )?(\S+ )?(tablets?|capsules?|pills?|supplements?|medicines?|medications?|dose|dosage|insulin|metformin)\b"""),
        Regex("""\b(you can|you may|it is safe to|it's safe to|feel free to) (stop|skip|reduce|drop) (your |the )?(tablets?|pills?|medicines?|medications?|dose|insulin|metformin)\b"""),
        // a verdict on the person
        Regex("""\byou (have|are|do not have|don't have|are not|aren't) (a |an |not )?(diabetes|diabetic|anaemia|anemia|anaemic|anemic|hypertension|hypertensive|deficiency|deficient|disease|disorder|infection|at risk|fine|safe|cured|healthy|normal)\b"""),
        Regex("""\b(is|isn't|is not|are|aren't|are not|looks|sounds|seems) (very |quite |not |now )?(dangerous|life.threatening|cured|reversed|abnormal|under control)\b"""),
        Regex("""\bnothing to worry\b"""),
        // A diagnosis read off a figure. Meera's desktop run of the SHORT answer prompt (20 Sep,
        // `logs/meera-hindi-reply.log`) said "which indicates anaemia" to a person who declared
        // nothing; the engine's condition check catches the undeclared name, and this catches the
        // same verdict on a condition the person DID declare ("your figure indicates anaemia").
        Regex("""\b(indicates|indicating|suggests|suggesting|points to|consistent with|a sign of|confirms) (a |an |mild |severe |possible )?(diabetes|anaemia|anemia|hypertension|deficiency|disease|disorder|infection)\b"""),
        // the same verdicts and doses in roman Hindi and Telugu, the forms a code-mixed reply would take
        Regex("""\b(aapko|tumhe|tumko|aapki|tumhari|meeku|neeku|miku) (\S+ )?(sugar|diabetes|anaemia|anemia|bp|thyroid|cancer|bimari|jabbu) (hai|hain|undi|unnadi|undhi)\b"""),
        Regex("""\b(goli|goliyan|dawai|dawa|tablets?|mandu|mandulu|matra) (roz |daily |rozana |rojoo )?(lo|lena|le lo|lijiye|veskondi|veyandi|thesukondi|teesukondi)\b"""),
        // a referral withheld
        Regex("""\bno need (to|for) (see|visit|consult|check with|a|the) (a |the |your )?(doctor|physician|clinic|hospital)\b"""),
        Regex("""\b(instead of|rather than|without) (seeing |visiting |consulting |asking )?(a |the |your )?(doctor|physician)\b"""),
    )
}
