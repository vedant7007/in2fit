package io.github.vedant7007.katori.ml.llm

import io.github.vedant7007.katori.data.knowledge.Csv
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * THE RULE, AS A TEST (Vedant, 20 Sep): any change that makes LOG easier to reach must be
 * tested against a health question containing a number. LOG is the only intent that writes to
 * the person's record, so it is the only one where a false positive is destructive rather than
 * annoying.
 *
 * WHERE IT CAME FROM. A bare digit became log evidence in the pre-filter (a sensible rule), and
 * "my haemoglobin is 7, is that dangerous" has a digit and no marker, so the router decided
 * LOG and the turn wrote a MEAL into the diary from a health question. It showed up as a
 * missing Answered event and was worse than that. The domain decides, not the grammar: a
 * stated reading is never a meal.
 *
 * Two sources, so the test grows with the data: every clinical row of Priya's adversarial set,
 * and a fixed list of the shapes a person actually says with a number in them. Anyone adding
 * evidence to [LogPrefilter] or [IntentRouter] runs into this before it runs into a diary.
 */
class LogNeverFromAHealthQuestionTest {

    private val healthQuestionsWithANumber = listOf(
        "my haemoglobin is 7, is that dangerous",
        "my sugar was 140 this morning",
        "haemoglobin 9.8",
        "BP 150 over 90 is that ok",
        "hba1c 7.2 what should I eat",
        "I weigh 82 kg is that fine",
        "my cholesterol is 240",
        "fasting glucose 126 today",
        "iron 9.5 on my report",
        "mera sugar 180 hai",
    )

    private fun clinicalRows(): List<String> {
        val dir = System.getProperty("katori.projectDir") ?: error("katori.projectDir not set")
        val f = File(dir, "data-authoring/safety-adversarial-set.csv")
        return Csv.parse(f.readText()).drop(1).filter { it.getOrNull(3) == "yes" }.map { it[0] }
    }

    @Test fun `no health question with a number is a certain log`() {
        val offenders = (healthQuestionsWithANumber + clinicalRows()).filter { LogPrefilter.isCertainLog(it) }
        assertTrue("these would write a meal into the diary from a health question: $offenders", offenders.isEmpty())
    }

    @Test fun `the router never decides LOG for a health question with a number`() {
        val offenders = (healthQuestionsWithANumber + clinicalRows()).filter {
            val d = IntentRouter.decide(it)
            d is IntentRouter.Decision.Decided && d.intent == Intent.LOG
        }
        assertTrue("the router decided LOG for: $offenders", offenders.isEmpty())
    }

    @Test fun `and a LOG verdict from the model on any of them is refused`() {
        val accepted = (healthQuestionsWithANumber + clinicalRows()).filter { IntentRouter.accept(Intent.LOG, it) == Intent.LOG }
        assertTrue("a model saying LOG would have been believed for: $accepted", accepted.isEmpty())
    }
}
