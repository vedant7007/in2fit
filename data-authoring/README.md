# Authored reference recipes

Not started. Waiting on the Phase 2 schema review, then the USDA ingredient base.

## What goes here

One row per authored recipe, with the reasoning for every amount, in a committed data
file rather than in code comments. A reviewer must be able to see where each figure came
from without reading Kotlin.

## Rules for authoring

1. Ingredient amounts are computed against USDA FoodData Central values only. Public
   domain, CC0. No IFCT, no INDB, no ifct2017 package, in any form, including as a
   reference to check against.

2. ABSORBED OIL, NOT OIL USED. A fried item records the fat the dish retains, not the
   volume of the frying bath. Counting the whole bath is how an existing public recipe
   database ended up reporting 745 kcal per 100 g for a vada. Repeating that would be
   our error rather than an inherited one, and it lands on exactly the food the demo
   audience eats.

3. Every recipe carries the Approximate confidence band and a visible "reference recipe,
   edit to match how you cook it" affordance. It is a stated composition, not a
   measurement of this user's cooking.

4. Scope: 50 to 80 dishes. Telangana, Andhra, and pan-Indian staples. Demo dishes first.

5. Each recipe cites its per-amount reasoning by `authoringNoteId`, which the app stores
   alongside the recipe so the reasoning is reachable from the figure.

## Items routed here from the USDA gap tiering (see decision 0002)

- PANEER. No USDA record, and queso fresco carries 626 mg sodium per 100 g against
  paneer's zero, which is wrong in a way that matters for a user with declared blood
  pressure. Author it from whole milk plus an acid, with the whey loss stated.

- SOOJI and POHA. Derived from the USDA semolina and unenriched rice records
  respectively. Record the derivation here, band Approximate, disclose in the UI.

- NOT AUTHORABLE, no-data until NIN responds or a licensed source appears: ragi, bajra,
  jaggery. These are ingredient composition questions, not recipe questions, so the recipe
  layer cannot answer them. Authoring a value for them would be inventing a measurement.
