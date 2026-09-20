# 0035. An unstated amount is never QUANTITY_STATED; it is an assumed serving and the band says so

Date: 20 September 2026, night. Status: accepted, ruled by Vedant. Demo-blocking: Beat 1.

## The defect

Nila found it. "थोड़ी दाल", a little dal, is the opening sentence on stage. The model writes
`1.0` for it, and `LookupMealResolver.weigh` took a unitless quantity on an authored dish as one
stated piece of the dal recipe: QUANTITY_STATED, the recipe's 180 g, full confidence. So Beat 1
presented an invented amount as the person's own, while slide 6 claimed the app never does.

## The ruling, in two halves

**This half (the resolver):** a number with no unit is a stated amount only when the dish is
COUNTED: a roti, a vada, an idli, a dosa, an omelette, an egg, a piece of chicken. "Two rotis"
says how much. For anything SERVED, a gravy, a rice dish, a drink, a chutney, a plain food in a
katori, "one dal" says nothing about how much: the unit is ours, the item carries
QUANTITY_INFERRED and HOUSEHOLD_UNIT_DEFAULT, the band is Rough, and the assumed quantity and
unit are written back onto the parsed item so the plate shows them. Counted or served is read
off the recipe's moisture class (`ReferenceRecipe.moistureClass`, GRIDDLE_BREAD, DEEP_FRIED,
STEAMED, EGG and MIXED_PLATE are counted), and the household word a served dish is shown in is
the one a person would say: a katori of dal, a plate of biryani, a cup of chai, a spoon of
chutney, a glass of chaas. The grams are the recipe's serving in every case; the word is what
the card shows.

**The other half (the model, the integrator's):** it must not write a quantity it was not given.
This half holds whichever way the model behaves: `1.0` and `null` read the same on the plate.

**Not this week:** the conversational ask ("how much dal?"). Instead the plate shows what was
assumed, "dal · 1 katori · taken as 180 g", visible and correctable; Ira is building that card,
and the band this half emits is what makes the card honest.

## Measured

`UnstatedQuantityTest`, on the shipped database: "दाल" written as `1.0` with no unit is
`toor_dal_tadka`, 180 g, shown as 1 katori, QUANTITY_INFERRED and HOUSEHOLD_UNIT_DEFAULT, Rough,
and never QUANTITY_STATED; a bare "dal" reads the same; "roti" × 2 is 90 g, 2 pieces, stated;
three idlis and one boiled egg are counted; "ek katori dal", the unit said, is Approximate and
not Rough; "rice" × 2 is two assumed katoris, Rough; sambar, biryani, chai and chutney are shown
as katori, plate, cup and spoon. `DemoSentencesTest` models the model's `1.0` on Beat 1 in both
languages and asserts the plate reads an assumed katori at Rough; its table column now reads as
the card will: food, amount said or assumed, "taken as" grams, band.
