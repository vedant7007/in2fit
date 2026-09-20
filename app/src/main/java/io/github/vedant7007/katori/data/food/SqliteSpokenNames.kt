package io.github.vedant7007.katori.data.food

import io.github.vedant7007.katori.domain.SpokenNames

/**
 * [SpokenNames] over the bundled database: a food's first roman alias, in the order the
 * authoring file lists them (rowid order in `food_aliases`), which is the name the author
 * would say; a recipe's own display name, which is already a dish name ("Dal tadka"); the
 * USDA description only when there is nothing else. Loaded once; a few hundred rows.
 */
class SqliteSpokenNames(db: FoodDbSource) : SpokenNames {

    private val firstAlias: Map<String, String> = db.query(
        "SELECT food_key, alias FROM food_aliases WHERE script = 'ROMAN' ORDER BY rowid"
    ).fold(LinkedHashMap()) { acc, row -> acc.putIfAbsent(row.str("food_key"), row.str("alias")); acc }

    private val recipeNames: Map<String, String> =
        db.query("SELECT recipe_key, display_name FROM recipes").associate { it.str("recipe_key") to it.str("display_name") }

    override fun of(foodCode: String?, displayName: String): String =
        foodCode?.let { recipeNames[it] ?: firstAlias[it] } ?: displayName
}
