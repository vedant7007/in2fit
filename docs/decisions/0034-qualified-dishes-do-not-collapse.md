# 0034. A qualified dish name does not collapse onto the ingredient it contains

Date: 20 September 2026. Status: accepted for the data and the guard. Ruled by Vedant: the
"fried rice" finding of `0041` is a bug class, not an instance, and is swept rather than noted.

## The class

The matcher's containment stage finds an alias inside a longer utterance as a whole run of words
("two spoons of groundnut oil" finds groundnut oil; `0006`). A dish name that contains a base
ingredient's name is the reverse case: "butter chicken" contains "chicken", "jeera rice" contains
"jeera", and with no recipe for the dish the name resolves to the ingredient, silently dropping
the oil, the cream, the other ingredients and the dish's own figures. The demo plates are exactly
this shape.

## Swept, and counted

`data-authoring/qualified-dishes.csv` is 70 names a demo speaker plausibly says, authored by one
hand from the utterance set's dishes, the code-mix list and the common canteen and home plates.
`QualifiedDishesTest` resolves each through the shipped lookup.

    before   70 names,  38 wrong:  18 collapsed onto the base ingredient the name contains
                                    ("butter chicken" -> chicken breast, "egg bhurji" -> raw egg,
                                     "fried egg" -> raw egg, "aloo paratha" -> raw potato)
                                    15 onto another ingredient or the wrong dish
                                    ("jeera rice" -> cumin, "tomato rice" -> tomato,
                                     "mutton biryani" -> veg biryani, "chole bhature" -> chana masala,
                                     "bread butter" -> butter, "idli sambar" -> sambar)
                                     5 honest misses that should refuse by name
    after    70 names,   0 wrong, 0 collapsed

The authored utterance set is unchanged at 213 of 213, the code-mixed renderings at 25 of 25.

## Per item, the same principle as fried rice

**Twelve became authored recipes**, because they are frequent and their composition is simple
enough to state: fried egg, egg bhurji, bread and butter, cheese sandwich, egg sandwich, jeera
rice, ghee rice, tomato rice, coconut rice, cold coffee, banana milkshake, sweet lassi. Each is a
stated, editable composition in `recipes.csv` with its fat role declared, and each passed the
importer's oil-share, yield and moisture bands on the first build. They carry
`AUTHORED_REFERENCE_RECIPE` like every recipe.

**Twenty-five became named no-data items**, refused by name and never fuzzy, with a reason
that says what is missing: the pulaos, egg and mutton biryani, butter chicken, chilli chicken,
chicken 65, tikka and tandoori, chicken and fish fry, bread omelette, dal makhani, chole bhature,
rajma chawal, aloo paratha, gobi manchurian, rava and onion dosa, rasam rice, mango lassi,
fruit salad, veg sandwich. Two of them are two real dishes said as one item, "idli sambar" and
"vada sambar": the reason tells the person to say them separately, since both exist.

**Prawn fry was the sweep's own error**: the prawn recipe already claims the name, and the
importer's collision assertion refused the build until the row was corrected.

## What the existing guards caught while doing it

- A bare `sandwich` alias on the veg-sandwich refusal swallowed the cheese and egg sandwich
  recipes through containment; `RecipeLayerTest`'s "every recipe is reachable by its own name"
  failed. Bare `tikka` and `tandoori` had the same shape (tandoori roti would have been refused
  as tandoori chicken). All three bare forms are gone. A refusal alias must be as specific as
  the dish it refuses.

## What this does not claim

That the class is closed. The sweep is 70 names one person thought of; a dish nobody wrote down
can still collapse. The recorded speakers are the next sweep, and any new dish alias should be
checked against this file's question: what does the name CONTAIN, and what happens if the dish
is not in the tables.
