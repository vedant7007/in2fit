# 0002 - USDA FoodData Central import rules

Status: accepted. Implemented by `tools/build_food_db.py`, which enforces and asserts all four
rules below at import and deletes the database if any fails (`logs/food-db-build.log`, lines
`RULE 1 ok` to `RULE 4 ok`). The "not yet implemented" line that stood here until 20 September
2026 was stale from the day the importer landed.

The ingredient base is USDA FoodData Central. Public domain, CC0 1.0. Attribution is
requested rather than required, and we give it anyway with the snapshot release date,
because an app that reports iron content should say where the number came from.

    U.S. Department of Agriculture, Agricultural Research Service.
    FoodData Central. fdc.nal.usda.gov

IFCT 2017, INDB and the ifct2017 package are not used in any form, including as a
reference to check values against.

## What gets bundled

| Data type | Decision | Reason |
| --- | --- | --- |
| SR Legacy, 7,793 foods, April 2018 | PRIMARY | Complete macros, iron on 99% of foods, B12 on 91% |
| Foundation Foods, 469 foods, April 2026 | Selective overlay only | Small, and structurally incomplete: B12 on 20%, fibre on 51% |
| Branded Foods, 433k | EXCLUDED | 2.9 GB, US and New Zealand markets only, no Indian products, third-party marks |
| FNDDS Survey | Not used | Not an ingredient-level source |

Excluding Branded keeps the licence story pure federal work with no third-party
trademarks in the bundle.

## Enforced import rules

These are rules the importer applies and asserts, not judgement calls made per food.

1. NO COALESCE TO ZERO, ANYWHERE. A missing nutrient row imports as UNKNOWN, never as 0.
   USDA states plainly that a missing value "does not indicate that the value is zero".
   Enforced by the type: see NutrientValue.

2. ENERGY COALESCES 1008, THEN 2048, THEN 2047. SR Legacy carries kcal under nutrient
   1008 on 100% of foods; Foundation carries it under 1008 on only 28.8%, with the rest
   under Atwater Specific (2048) and Atwater General (2047). Joining on 1008 alone
   silently zeroes 71% of Foundation Foods.
   ASSERTION AFTER IMPORT: every imported food has an energy value. The import fails if
   any does not.

3. DAIRY AND ANYTHING B12-RELEVANT COMES FROM SR LEGACY, NEVER FOUNDATION. Foundation's
   plain whole-milk yoghurt record has no B12 row at all and reports iron as 0.0, while
   the SR milk record carries B12 at 0.45 ug. For a user base where B12 deficiency is
   common among vegetarians, sourcing dairy from Foundation would quietly zero out one of
   the few B12 sources in a vegetarian Indian diet.
   ASSERTION AFTER IMPORT: no food in the DAIRY class resolves to a Foundation record.

4. PREFER UNENRICHED AND UNFORTIFIED VARIANTS, DELIBERATELY. US fortification is a
   regulatory fact that does not apply in India, and taking FDC defaults attributes it to
   Indian food. The chosen records are listed below and are part of this rule, not a
   suggestion.

   | Food | fdcId | Note |
   | --- | --- | --- |
   | Rice, white, long-grain, regular, raw, UNENRICHED | 169756 | Not 168877, which is enriched, Fe 4.31 |
   | Rice, white, long-grain, regular, UNENRICHED, cooked | 169757 | Not 168878 |
   | Wheat flour, whole-grain | 168893 | |
   | Pigeon peas (red gram), raw / cooked | 172436 / 172437 | toor dal |
   | Chickpeas (bengal gram), raw / cooked | 173756 / 173757 | chana dal; besan 174288 |
   | Mungo beans, raw / cooked | 174259 / 172427 | urad dal, filed under "mungo" |
   | Mung beans, raw / cooked | 174256 / 174257 | moong dal |
   | Lentils, pink or red, raw | 174284 | masoor dal |
   | Oil, peanut | 171410 | SR, not the Foundation record 1750348, which has no energy or fat |
   | Oil, sunflower | 171025 | SR, not Foundation 1750349, same defect |
   | Oil, mustard | 172337 | |
   | Butter, clarified (ghee) | 171314 | See disclosure below |
   | Milk, whole | 171265 | Vitamin-D fortified in the US; disclosed |
   | Yogurt, plain | 170886 | SR, not Foundation 2259793 which has no B12 row |
   | Nuts, coconut meat, raw / dried unsweetened | 170169 / 170170 | Take the unsweetened dried record |
   | Okra, raw / cooked | 169260 / 169261 | |
   | Eggplant, raw / cooked | 169228 / 169229 | brinjal |
   | Balsam-pear (bitter gourd), raw / cooked | 168393 / 168394 | |
   | Gourd, white-flowered (calabash), raw / cooked | 169232 / 169233 | bottle gourd |
   | Gourd, dishcloth (towelgourd), raw / cooked | 168414 / 168415 | ridge gourd; different Luffa species, disclosed |
   | Drumstick pods, raw / cooked | 170483 / 170484 | 1 analytical sample, disclosed |
   | Drumstick leaves, raw / cooked | 168416 / 168417 | 3 samples |
   | Coriander (cilantro) leaves, raw | 169997 | |
   | Tamarinds, raw | 167763 | Whole fruit, not concentrated pulp; disclosed |
   | Spices, mustard seed, ground | 170929 | |
   | Spices, cumin seed | 170923 | Fe 66.36 mg/100g, 48 samples; see spice rule |
   | Spices, turmeric, ground | 172231 | Fe 55 mg/100g, 3 samples; see spice rule |
   | Sorghum, whole grain, white | 2710842 | jowar; species confirmed Sorghum bicolor |

## Coverage gaps, tiered

### Negligible quantity: mark no-data and move on
Curry leaves, asafoetida, ajwain, amchur. Their contribution to a meal's macros is
immaterial, and the three-state value reports them honestly rather than dropping them
silently. The UI lists them as present in the dish with no nutrition data.

### Derivable with explicit disclosure
- Sooji / semolina: derived from the USDA semolina record.
- Poha / flattened rice: derived from the unenriched rice record.
Both record their derivation in the authoring data file, band APPROXIMATE, and say so in
the UI.

### Genuinely blocking: no-data until NIN responds or a licensed source appears
Ragi (finger millet), bajra (pearl millet), jaggery. DO NOT SUBSTITUTE.

The generic FDC "Millet" records (169702, 2512379) tag NCBI taxon 4479, which is Poaceae,
the entire grass family. FDC's own metadata does not identify the species. Ragi carries
roughly 300 to 350 mg calcium per 100 g against single digits for proso millet, and
calcium from ragi is exactly the kind of number people track deliberately. Mapping ragi
onto that record would not be a rough substitute, it would be a wrong number.

Jaggery has no record. Brown sugar (168833) loses the mineral content that is the reason
people distinguish jaggery from sugar, so it is not a substitute either.

### Route to the recipe layer instead
Paneer. There is no paneer record, and queso fresco (2647442) carries 626 mg sodium per
100 g against paneer's zero, which makes it wrong in a way that matters for a user with
declared blood pressure. Paneer is authorable from milk plus acid in the recipe layer.

### Use with disclosure
Ghee, fdcId 171314. Fifteen of its eighteen nutrient values are back-calculated from a
retail label rather than analysed, and it carries no magnesium, zinc or B12. It is near
pure fat, so ENERGY is sound, which is what ghee contributes to a meal. Band APPROXIMATE
with WEAK_SOURCE_RECORD, and disclose.

## Spice-derived iron

Spice iron values are extreme: turmeric 55 mg/100 g from 3 samples, cumin 66.36 mg/100 g
from 48 samples. Plausibly real, since ground spices are mineral-dense and pick up iron
during milling, but historically also suspected of reflecting soil and equipment
contamination. Indian cooking uses spices at many times the per-dish rate these values
were compiled to serve, so meal iron totals will run high and the error concentrates in
daily dishes.

Rules:
1. Tag spice-derived iron separately in the pipeline so a total's composition is visible.
2. Carry sample size on these records, so a 3-sample figure can be treated differently
   from a 48-sample one.
3. If a meal's iron total is majority spice-derived, the UI says so rather than reporting
   a confident number.

## Disclosure screen

An "About these numbers" screen states: values come from USDA FoodData Central with the
bundled release date and the suggested citation; they are based on foods sampled in the
United States and may differ from Indian-grown produce and Indian preparation; they are
estimates rather than measurements of the user's own food. Item-level markers appear on
ghee, the drumstick and gourd records, ridge gourd, and tamarind.

Do not imply USDA endorses the app. Their documentation states that reference to a product
or method by name "does not imply recommendation, endorsement, or approval by, or an
association with, the U.S. Department of Agriculture".

## Addendum, 20 September 2026: the sweep, and four corrections to rule 4's table

Rule 4 was applied by hand when the table above was written, and it missed four records. The
demo-sentence run found the first: a "katori" of white bread topped the iron list for anaemia,
and the iron was US enrichment. Ruled by Vedant: go through every shipped record for a value
that depends on US fortification, enrichment or processing that Indian production does not
have; correct it, disclose it beside the figure, or mark it no-data; report the count.

The sweep is `data-authoring/us-record-sweep.csv`: **93 records read, 5 acted on, 88 kept**,
one row per record with the finding and the action, and the importer refuses a shipped record
that is not in it, so a record added later is swept or the build fails. The corrections, dated,
replacing the rows above:

| Food | Was | Now | Why |
| --- | --- | --- | --- |
| Yogurt, plain | 170886, low fat | **171284, whole milk** | 170886 is low-fat yogurt with added milk solids (protein 5.25, fat 1.55 per 100 g); home-set curd is 3.47 and 3.25. The demo's katori of curd carried 2.7 g of protein it never had. |
| Milk, whole | 171265, with added vitamin D | **172217, without added A and D** | Same eight figures; the record no longer needs a disclosure. 3.25% fat is toned milk. |
| Bread, white | 174924, iron shipped | 174924, **iron NOT shipped**, disclosed | Enriched flour: 3.61 mg against 1.17 in unenriched flour. No unenriched white bread record exists. Iron reads Unknown for it and for the three sandwich recipes made from it: a named floor, never a number. |
| Milk, buttermilk, whole | 172225 | **the `chaas` recipe**: curd 60 g, water 139 g, salt 1 g per glass | US cultured buttermilk is undiluted fermented milk at 62 kcal per 100 g; chaas is a third of that. |
| Cheese, processed | 171290 | 171290, **disclosed** | US process American cheese; the Indian cube is the same kind of product. |

Confirmed on the same sweep as deliberate unenriched or unsweetened choices: rice raw and
cooked, poha, rice flour, sooji, atta, dried coconut, dry-roasted unsalted peanuts, plain
chicken breast, raw orange juice. Everything else among the eight shipped nutrients is raw
produce, meat, pulses, spices or fat with nothing added.

`foods.disclosure` carries the sentence for the screen; `FoodMatch.disclosure` carries it to
the resolver. The shipped `meta.disclosure` says what the sweep did in one sentence.
