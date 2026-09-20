package io.github.vedant7007.katori.ml.llm

import io.github.vedant7007.katori.data.food.FoodTextMatching
import io.github.vedant7007.katori.data.knowledge.KnowledgeFacts

/**
 * What a question asks for, in figures: which nutrients it names in everyday words, and whether
 * it asks for the whole picture.
 *
 * FOUND ON THE PHONE (21 Sep, typed): "I said that I ate two chapatiis, can you tell me the
 * nutritional information of it" was routed ANSWER, correctly, and refused. Two layers: the LOG
 * had missed "chapatiis" (the matcher), so the diary was empty; and even with the chapatis
 * logged, the ANSWER path gave the model the meal line WITHOUT its figures, because the question
 * names no nutrient by the word the rules engine uses ("energy", "carbohydrate"), and a question
 * that names none was taken to be about WHAT was eaten, not how much. "Nutritional information",
 * "calories", "carbs", "what did that give me" are all how-much questions. A model given a meal
 * with no figures and asked for figures writes them from memory, and the numeric guard refuses
 * that, as it must. The guard was not over-firing; the request was under-supplied.
 *
 * This reads the question the way a person means it. Nutrient names are the enum's NAMES as
 * strings, keyed the way [KnowledgeFacts.nutrientTerms] is, so this package does not import
 * `domain`. The orchestrator turns them into `Nutrient`s.
 */
object NutrientWords {

    /** The nutrients the question names, in the everyday words for each: "calories" is energy, "carbs" is carbohydrate. */
    fun named(question: String): Set<String> {
        val q = FoodTextMatching.normalise(question)
        if (q.isEmpty()) return emptySet()
        return NUTRIENTS.filterTo(linkedSetOf()) { n ->
            (KnowledgeFacts.nutrientTerms(n) + EXTRA_TERMS[n].orEmpty()).any { FoodTextMatching.containsAsWords(q, FoodTextMatching.normalise(it)) }
        }
    }

    /**
     * True when the question asks for the figures as a whole rather than one nutrient: "the
     * nutritional information", "what did that give me", "how much was in that", "the numbers".
     * A question about WHAT was eaten ("what did I eat on Tuesday") is not one of these.
     */
    fun asksForAll(question: String): Boolean {
        val q = FoodTextMatching.normalise(question)
        if (q.isEmpty()) return false
        val words = q.split(' ')
        return words.any { it in ALL_WORDS } || ALL_PHRASES.any { FoodTextMatching.containsAsWords(q, it) }
    }

    /** The enum's names, as strings; `Nutrient.entries` in the orchestrator. */
    internal val NUTRIENTS = listOf("ENERGY", "PROTEIN", "CARBOHYDRATE", "FAT", "FIBRE", "IRON", "VITAMIN_B12", "SODIUM")

    /** Everyday words beyond the knowledge file's tags: what people type, including the misspellings a phone keyboard leaves. */
    internal val EXTRA_TERMS: Map<String, List<String>> = mapOf(
        "ENERGY" to listOf("calorie", "kcal", "cal", "cals", "kilocalories", "calories"),
        "PROTEIN" to listOf("proteins", "protien", "protein"),
        "CARBOHYDRATE" to listOf("carb", "carbs", "carbohydrates", "carbohydrate"),
        "FAT" to listOf("fats", "fatty"),
        "FIBRE" to listOf("fibre", "fiber"),
        "IRON" to listOf("iron"),
        "VITAMIN_B12" to listOf("b12", "b 12", "vitamin b12"),
        "SODIUM" to listOf("sodium", "salt", "namak", "uppu"),
    )

    internal val ALL_WORDS: Set<String> = setOf(
        "nutrition", "nutritional", "nutrients", "nutrient", "nutritious", "macros", "breakdown", "figures", "numbers", "values", "information", "info", "stats",
    )

    internal val ALL_PHRASES: List<String> = listOf(
        "what did that give", "what does that give", "what did it give", "what does it give", "what is in that", "what was in that", "whats in that",
        "what is in it", "what was in it", "how much was in", "how much is in", "how much did that", "how much does that", "how much did it", "how much does it",
        "what did i get", "what do i get", "tell me about that", "tell me about it", "details of", "kitna hai", "kitna tha", "kya hai isme", "isme kya hai",
    )
}
