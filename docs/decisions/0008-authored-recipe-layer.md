# 0008. The authored reference recipe layer

Date: 19 September 2026. Status: accepted.

## Context

A person logging a meal says "sambar", not "toor dal, drumstick, tamarind, oil". Until now the
matcher had no dish layer, so every composed dish was an honest MISS and the user was asked to
enter ingredients. That is safe but it is not usable, and it is the layer the demo needs.

The INDB recipe table was ruled out in `0005` on licence grounds, and estimated values were
ruled out outright. So the recipes here are AUTHORED: a stated composition, written down with
its reasoning, shown to the user, and editable by them. They are not measurements of anyone's
cooking and they never read better than Approximate.

## Decision

**50 dishes, 434 ingredient rows, in two committed CSVs.** `data-authoring/recipes.csv` and
`data-authoring/recipe-ingredients.csv`. Every amount a reviewer might argue with is a column in
the file, not a comment in code:

- `absorbed_oil_g` is what the dish RETAINS, never what the pan held.
- `absorbed_basis` states the arithmetic, e.g. "14% of the 203 g pre-fry dough".
- `absorbed_reason` states why that fraction, in words someone can disagree with.
- `water_change_g` is stated, not inferred, and covers the whole process: soaking and hydration
  first, evaporation later.

**The absorbed fraction is a percentage of the PRE-FRY weight, not of the finished dish.** The
first version used the finished weight, which is circular: the finished weight includes the oil
being measured.

**Bare dish names mean the dish.** "pappu", "poha", "chhole", "dal" resolve to the tempered dish,
not to the boiled pulse or the flattened rice, because that is what somebody logging a meal
means. The plain ingredient is still reachable under a name that says so: "plain boiled toor
dal", "kandi pappu", "flattened rice". The importer asserts no spoken name means both a food and
a dish, which is what forced this decision to be made explicitly rather than by whichever row
happened to load last.

**Dish and ingredient are matched together and the stronger match wins.** Searching recipes first
would let a fuzzy dish name beat an exact ingredient name; searching foods first would let "dal
tadka" collapse onto plain dal, which is the same shape as the `biryani` to bay leaf bug in
`0006`. On a tie the dish wins: a person who says a dish name has named the whole thing.

**Two paths to a dish's nutrition, deliberately.** `nutrientsFor` reads a per-100 g figure
computed at import, which is what the demo uses. `DefaultRecipeCalculator` does the same
arithmetic at runtime from the ingredient rows, which is what the user's edited version uses. A
test cross-checks one against the other, so a stale database or a wrong implementation fails the
build instead of producing a plausible number.

**One unknown ingredient makes the dish unknown, on the shipped path.** A single `NutrientValue`
cannot say "at least". The editable path returns a `NutrientTotal`, which can, so there it is
PARTIAL and names the ingredients responsible.

## Measuring it, rather than reviewing it

The alias bugs in `0006` were all found by running the whole set, never by reading the data.
Recipes have the same property and worse: a plausible total hides a wrong composition.

Twelve assertions now run at import and delete the database if any fails. Six were written for
this layer: every ingredient row inserted, sum plus water change equals the stated yield, every
ingredient is a real food and not a no-data item, no name means both a food and a dish, the oil
share sits in a band for its method, and the yield is not absurd against the ingredient weight.
Sixteen JVM tests then exercise all 50 dishes through the shipped database.

**What that missed, and the assertion that now catches it.** Every one of those checks takes
`yield_g` as given. Assertion 2 proves the arithmetic is self-consistent, so a wrong water figure
passes it happily as long as the sum still matches. Idli was authored at a 300 g yield, which put
a steamed rice-and-dal cake at 227 kcal per 100 g, within reach of a dry griddle roti at 258.
Steaming cannot do that, and no assertion said so.

Assertion 7 now derives each finished dish's moisture-and-ash fraction from its own macros, as
100 minus protein, carbohydrate and fat, and compares it to a band for the stated cooking method.
A wrong yield shows up immediately, because dividing the same nutrients by a smaller mass makes a
wet food look dry. Idli at the old yield implies 45% moisture against a 58-80% band for a steamed
dish; it fails, and the build deletes the database.

The corrected figure was derived from moisture, not from a calorie target: 172.4 g of solids at
68% moisture, the moisture of boiled rice, is a 539 g yield, which is ten idlis of about 54 g
rather than six of 50 g. The energy figure fell out of that.

**The bands are wide and they are not an accuracy check.** They cannot separate a right figure
from a nearly right one. Passing says only that the dish is not impossible. Narrowing them
without weighed plates to narrow them against would be inventing precision.

## The circularity, again

The dish utterances in `data-authoring/utterance-test-set.csv` were written by the same hand, on
the same day, as the recipes they point at. The resulting percentage is a regression guard and
nothing else. It is not real-world accuracy and it does not go on a slide. Same caveat as `0006`,
and it holds harder here.

## Consequences

- Every figure from this layer carries `AUTHORED_REFERENCE_RECIPE` and is capped at Approximate,
  however exact the name match. Knowing the word is not knowing the plate.
- A reviewer can challenge any absorbed fraction from the CSV alone, without reverse-engineering
  it from a total.
- Adding a dish means adding rows and rerunning the importer. If the composition is wrong in a
  way that shows, twelve assertions and sixteen tests say so before it ships.
