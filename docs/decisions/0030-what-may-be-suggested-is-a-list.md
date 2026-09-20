# 0030. What may be suggested is an authored list per context; everything else is an ingredient

Date: 20 September 2026, evening. Status: accepted. Ruled by Vedant: "Raw grains and pulses
are ingredients, not candidates. That is the top of your list until it is done." Implements
spec 4.3, which was unauthored until now.

## Why now

The recommendations were unshippable. With every row of the database a candidate, the rules
engine's ranked list for "I have anaemia, what should I eat for iron" topped out at cumin,
turmeric and bay leaf (per 100 g), then, after the per-serving ruling (`02417a9`), at raw
cowpea, raw urad and raw masoor, because a raw pulse's "serving" is a 200 g cup. Beat 4 ("fibre
higher, carbohydrate lower") tied every zero-carbohydrate item at zero and listed raw chicken
breast, brewed coffee, raw carp and water alphabetically under a correct trigger sentence.
"You should drink more water" as our answer to an iron question ends the pitch more
efficiently than a crash would.

## The rule

`data-authoring/candidates.csv` lists every food and recipe in the database exactly once (the
importer refuses a missing or duplicated key), with the life contexts it is AVAILABLE in, the
name a suggestion shows, and where the class's usual unit is wrong for a suggestion, what one
helping weighs. **A key with no context is an ingredient: the engine never ranks it and the
model never names it.** Raw grains and pulses never have a context. A spice, fat, sweet or
packaged item has one only with its own serving stated (roasted peanuts, 30 g, a handful; not
cumin). Raw meats have none: the dish is the candidate. Fried snacks and sweets are logged when
eaten and never suggested as an addition.

Availability is the file; merit is the engine's ranking. The contexts follow the spec 4.2
table: the hostel student has a canteen and no fridge, the field worker a tiffin box that must
hold unrefrigerated, the PG a basic kitchen and no time, the desk professional and the
homemaker a full kitchen and a moderate budget. 105 of 157 keys may be suggested somewhere:
58 for the hostel, 54 for the field, 91 for the PG, 105 at home.

`CandidateCatalogue.load(foods, allowed)` in `data/food` reads the table into the engine's
`CandidateFood`s, with the caller's diet filter applied to a food and to every ingredient of a
recipe, as `RoomUserContextSource` does today. Its one line to change is its `candidates()`
body. The demo run (`DemoSentencesTest`, `docs/demo/…`) already uses the catalogue, so the
beat 3 and beat 4 rows there are what the engine gives with the list.

## What the list gives, measured (`DemoSentencesTest`, hostel context)

Beat 3, "I have anaemia, what should I eat for iron": cooked chana dal, palakura pappu,
thotakura pappu, chana masala, dal tadka. (The first run of the list put white bread on top: a
"katori" of bread is five slices, and the US record is iron-enriched flour. A slice is the
helping now, and the enrichment is in the shipped disclosure beside milk's vitamin D.)

Beat 4, "fibre higher, carbohydrate lower", was the engine's: under the additive scoring every
real food scored below zero, the carbohydrate term being ten times the fibre term per serving,
and the only candidates not below zero were the ones with nothing in them. **Ruled (Vedant,
evening): make the terms comparable; do not filter by sign.** The integrator's `fddedf3` scales
each term as a fraction of a day's reference amount, which is the terms made comparable, and
with the list beat 4 now reads cooked moong dal, cooked chana dal, cooked toor dal. The same
commit also filters scores at zero and below, which the ruling forbids: "positive scores only"
under the additive scoring would have left beat 4 EMPTY, and under any scoring it turns "ordered
by the preference" into "deleted by the preference". `RankingDefectsTest`, three tests, is the
guard: an empty cup does not lead; coconut, best on fibre and middling on carbohydrate, ranks
above the egg; and the list is never emptied. Two are red on master until the filter goes. Water, black tea and black coffee are off the list entirely: they
have nothing of the eight nutrients, so any "lower" preference reads them as the best food
there is, and "you should drink more water" is the failure that ends the pitch.

## What this does not settle

The list is one hand's reading of the spec table; a Hyderabad hostel mess menu would correct
it, and every row carries the reason it can be corrected against. The side-by-side of spec 4.4
(the same meal, two contexts, two suggestions) is now possible and unmeasured.
