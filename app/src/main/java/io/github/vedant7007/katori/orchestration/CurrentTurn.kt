package io.github.vedant7007.katori.orchestration

/**
 * The turn in flight, for the things that write at the end of it and were not handed the
 * intent: `RoomMealStore` records `meals.source` ("SPOKEN"/"TYPED") and `meals.language_tag`
 * through two lambdas (Arjun, 21 Sep 10:55), and this is what those lambdas read. Set by
 * [DefaultOrchestrator] at the top of every turn. One turn runs at a time (the screen refuses a
 * second while busy), so a pair of volatile fields is the whole of it; the day this carries
 * more than two facts it becomes a parameter of `MealStore.save`.
 */
object CurrentTurn {
    @Volatile var source: String? = null
    @Volatile var languageTag: String? = null
}
