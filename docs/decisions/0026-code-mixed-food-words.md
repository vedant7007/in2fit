# 0026. English food words said the Telugu way, and what the matcher does with them

Date: 20 September 2026. Status: accepted for the data and the rule. The number is a regression
guard on one synthetic voice, not accuracy.

## The finding this answers

Jacob measured (`logs/asr-codemix-renderings.log`) that the te ASR model renders English food
words a Telugu speaker mixes in as Telugu-script phonetics, and that **0 of 25 of those
renderings resolved** against the shipped alias tables. Vedant ruled the ASR model is not
changing; whichever model is chosen, an English word said the Indian way arrives in an Indic
script. So this is a matcher problem, and the matcher is where it is fixed.

## Measured, before and after, on Jacob's exact list

`data-authoring/codemix-renderings.csv` is his log copied exactly: the model's rendering, the
form the speaker meant, and what each must resolve to. `CodeMixRenderingsTest` scores it through
the shipped lookup, foods through `resolve`, the two unit words through `resolveUnit`.

    before   emitted renderings resolve    1 of 25   (the one is fried rice, an honest miss;
                                                      0 of 25 on Jacob's exact-alias criterion)
    after    emitted renderings resolve   25 of 25
             said forms resolve           25 of 25
             WRONG FOOD                    0
    the authored utterance set            213 of 213, 0 wrong, unchanged

## What changed

1. **One normalisation rule, on both sides of the alias table.** Telugu closes a borrowed
   consonant-final word with a short u the speaker did not say: the model wrote పనీర్ as పనీరు,
   బటర్ as బటరు, వాటర్ as వాటరు. A word-final ు is now treated as ్ in `FoodTextMatching.normalise`
   and in the importer's `norm()`, and a test asserts the two agree on every alias in every
   shipped table, so a later rule cannot be added on one side. Native words that end in ు (పప్పు,
   పాలు) normalise the same way on both sides and are unaffected; the importer's ambiguous-alias
   assertion would catch a collision, and it found none.

2. **The renderings as aliases of the foods that exist.** Both the said form and the model's
   emitted form, Telugu and Devanagari, on milk, egg, rice, chicken, curd, butter, sugar, the
   default oil, chapati (రోటీ, చపాతీ) and dal (దాల్).

3. **The foods that did not exist**, from the SR Legacy files on disk by fdcId: white and
   whole-wheat bread, processed cheese (the Indian cube or slice; USDA American process, high in
   sodium as the product is), Marie biscuit (USDA carries the record), ripe banana (only the green
   plantain existed), apple, orange juice, mango nectar, tap water, and brewed black tea and
   coffee as ingredients. **Chai and coffee with milk are authored recipes**: a bare "tea" or
   "coffee" in India is with milk and sugar, and mapping it to brewed tea at 2 kcal would
   under-count every cup by about 70 kcal. Each is 100 g brewed, 100 g whole milk, 10 g sugar,
   THIN_SOUP moisture class; the importer's bands accept both.

4. **Bare "juice" is a category, not a food.** `juice_generic` resolves to the orange record at
   ROUGH via `CATEGORY_LEVEL_MATCH`, with a display name that says the assumption ("Juice
   (assumed orange)") so the person can correct it to mango or apple. It shares the orange
   fdcId on purpose.

5. **Paneer is a named no-data item**, not a food. It is not in USDA; the recipe layer keeps every
   ingredient nutrient and paneer sheds its lactose in the whey, so composing it from milk would
   overstate carbohydrate about ten-fold; and every USDA stand-in (queso fresco, mozzarella) is
   wrong on sodium, a tracked nutrient. That is the same reasoning as ragi, bajra and jaggery
   (`0002`): a wrong number on a tracked nutrient is worse than a refusal by name. The
   `data-authoring/README.md` plan to author it from milk plus acid needs a whey-loss mechanism
   the recipe layer does not have; recorded, not built.

6. **Fried rice is a named no-data item**, and this one the list found. "ఫైడ్ రైస్" resolved to
   plain cooked rice through containment (the word రైస్ inside it), dropping the oil and the
   vegetables; roman "fried rice" does the same. Until a recipe is authored it is refused by
   name, never fuzzy. The general shape, a qualified dish collapsing onto its base ingredient
   through containment, is the reverse of the `0006` finding and is worth a sweep of the recipe
   list for other missing dishes.

7. **Units take spoken forms.** A `household-units.csv` row's `unit` may carry `|`-separated
   forms; the importer writes one normalised row per form. Glass, cup, katori, bowl, plate and
   the spoons carry their Telugu and Hindi forms, including the model's garbled "కత్స్" for cups;
   slice (29 g, USDA's portion for the bread record) and piece for a biscuit (5.6 g, USDA's 28 g
   for five) are new.

## What is deliberately not hand-written

Every Telugu-script and Devanagari string added here was generated by someone who does not read
either script, either copied from the model's log or a common spelling. **All 119 of them are on
`data-authoring/log-words-review.md`**, third section, each with what it is meant to say and
what it points to, for the same reviewer on the same trip as the log words and the SafetyLine
patterns. The rows that copy the model's garbled output are marked as such: they need a yes only
if a real speaker could plausibly produce them too. The `ingredients.csv` notes carry the same
flag.

## What this does not claim

That a recorded speaker's rendering will match this one. The list is one synthetic Piper voice,
one rendering per word, and Jacob's own log says a real speaker will vary the vowel signs. The
recorded transcripts replace this file when they exist; they will be five or six speakers, a
small sample, and the number they give is reported as one. 25 of 25 is a regression guard.
