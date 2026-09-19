# IN2FIT: Telugu strings for review

Generated 20 September 2026 from the app's English string table. 10 strings to write, 48 to check.

Reviewer's name: ______________________

## What the app is

IN2FIT is a phone app for people in India who may not read English nutrition labels. A person says what they ate, in Telugu, Hindi or English; the app finds each food in its database and shows the nutrition, with a label saying how far to trust each figure. It can also read a printed lab report with the camera. It never diagnoses and never prescribes.

## What we need from you

For each numbered item, write the Telugu on the line that says `Telugu:`.

1. Write it the way you would say it to a family member. Short: it goes on a phone screen.
2. Keep the meaning exactly. Add no reassurance, no advice and no number. The English was written so that the app never says a person has a condition and never tells them what to take; the translation must not either.
3. Anything like `%1$s` or `%2$s` is a slot the app fills in with a name or a number. Keep it in the Telugu sentence, wherever Telugu needs it.
4. If an English line is unclear or makes no sense to you, write that instead of guessing. A note beats a wrong string.
5. Put your name at the top. Because you wrote it, it counts as reviewed.
6. Replying in a chat instead of in this file is fine: send your name, then one line per item starting with its number here, like `7. ...`. The numbers are how your words reach the right place, so keep them.

Send it back to Vedant. Your text goes into the app unchanged, and you will get a list back showing each item next to what landed, so you can check nothing slipped.

## Telugu already written but not yet reviewed: please check

### Advice screens

A line at the bottom of every screen that gives advice. Always visible, cannot be closed.

1. `safety_not_medical_advice`

   English: This is information, not medical advice.

   Telugu, as written, unreviewed: ఇది సమాచారం మాత్రమే, వైద్య సలహా కాదు.

   Correct Telugu (leave blank if the line above is right):


### The health sentences

One of these is shown when something in the person's data changes what the app suggests: a lab report value, a condition they told the app about, what dominates a meal, their living situation, or a pattern over days. THESE MATTER MOST. Each must say only what the data says, never that the person has an illness, never what to take. The slots (%1$s and so on) are filled by the app with names and numbers; what each slot holds is in the note.

2. `trigger_escalate_above_range`

   English: Your report from %1$s shows %2$s at %3$s %4$s, well outside the %5$s printed on it. This is worth showing to a doctor.

   Note: %1$s the report's date, %2$s the test's name as printed, %3$s the value, %4$s its unit, %5$s the upper limit printed on the report. Shown when the value is far above it.

   Telugu, as written, unreviewed: %1$s తేదీ నాటి మీ రిపోర్టులో %2$s విలువ %3$s %4$sగా ఉంది. రిపోర్టులో ముద్రించిన %5$s పరిధికి ఇది చాలా దూరంగా ఉంది. దీన్ని డాక్టర్‌కు చూపించడం మంచిది.

   Correct Telugu (leave blank if the line above is right):


3. `trigger_escalate_below_range`

   English: Your report from %1$s shows %2$s at %3$s %4$s, well outside the %5$s printed on it. This is worth showing to a doctor.

   Note: Same slots. Shown when the value is far below the lower limit printed on the report.

   Telugu, as written, unreviewed: %1$s తేదీ నాటి మీ రిపోర్టులో %2$s విలువ %3$s %4$sగా ఉంది. రిపోర్టులో ముద్రించిన %5$s పరిధికి ఇది చాలా దూరంగా ఉంది. దీన్ని డాక్టర్‌కు చూపించడం మంచిది.

   Correct Telugu (leave blank if the line above is right):


4. `trigger_lab_above_range`

   English: Your report from %1$s shows %2$s at %3$s %4$s, above the %5$s printed on it, so suggestions are ranked differently now.

   Note: Same slots as above; %5$s is the upper limit printed on the report.

   Telugu, as written, unreviewed: %1$s తేదీ నాటి మీ రిపోర్టులో %2$s విలువ %3$s %4$sగా ఉంది, ఇది రిపోర్టులో ముద్రించిన %5$s కంటే ఎక్కువగా ఉంది. అందువల్ల సూచనల క్రమం ఇప్పుడు మారింది.

   Correct Telugu (leave blank if the line above is right):


5. `trigger_lab_below_range`

   English: Your report from %1$s shows %2$s at %3$s %4$s, below the %5$s printed on it, so suggestions are ranked differently now.

   Note: Same slots; %5$s is the lower limit printed on the report.

   Telugu, as written, unreviewed: %1$s తేదీ నాటి మీ రిపోర్టులో %2$s విలువ %3$s %4$sగా ఉంది, ఇది రిపోర్టులో ముద్రించిన %5$s కంటే తక్కువగా ఉంది. అందువల్ల సూచనల క్రమం ఇప్పుడు మారింది.

   Correct Telugu (leave blank if the line above is right):


6. `trigger_declared_condition`

   English: You told us you are managing %1$s, so suggestions are ranked with that in mind.

   Note: %1$s is the condition in the person's own words, exactly as they told the app.

   Telugu, as written, unreviewed: మీరు %1$sను నిర్వహిస్తున్నట్లు చెప్పారు, కాబట్టి దాన్ని దృష్టిలో ఉంచుకుని సూచనల క్రమం రూపొందించబడింది.

   Correct Telugu (leave blank if the line above is right):


7. `trigger_meal_composition`

   English: Most of the %1$s in this meal comes from %2$s, about %3$s percent of it.

   Note: %1$s a nutrient word (from the list below), %2$s the name of a food or dish, %3$s a whole number, the percentage.

   Telugu, as written, unreviewed: ఈ భోజనంలోని %1$sలో ఎక్కువ భాగం %2$s నుంచి వస్తోంది, దాదాపు %3$s శాతం.

   Correct Telugu (leave blank if the line above is right):


8. `trigger_life_context`

   English: Suggestions are limited to what is realistic for %1$s.

   Note: %1$s is one of the living-situation phrases below.

   Telugu, as written, unreviewed: %1$sకు వాస్తవికంగా సాధ్యమయ్యే వాటికే సూచనలు పరిమితం చేయబడ్డాయి.

   Correct Telugu (leave blank if the line above is right):


9. `trigger_timeline`

   English: Over the last %1$s days, %2$s.

   Note: %1$s a number of days, %2$s a short description the app supplies.

   Telugu, as written, unreviewed: గత %1$s రోజుల్లో, %2$s.

   Correct Telugu (leave blank if the line above is right):


### Nutrient words

Single words dropped into the sentences above and shown next to figures, so they should read naturally mid-sentence.

10. `nutrient_energy`

   English: energy

   Telugu, as written, unreviewed: శక్తి

   Correct Telugu (leave blank if the line above is right):


11. `nutrient_protein`

   English: protein

   Telugu, as written, unreviewed: ప్రోటీన్

   Correct Telugu (leave blank if the line above is right):


12. `nutrient_carbohydrate`

   English: carbohydrate

   Telugu, as written, unreviewed: కార్బోహైడ్రేట్

   Correct Telugu (leave blank if the line above is right):


13. `nutrient_fat`

   English: fat

   Telugu, as written, unreviewed: కొవ్వు

   Correct Telugu (leave blank if the line above is right):


14. `nutrient_fibre`

   English: fibre

   Telugu, as written, unreviewed: పీచు

   Correct Telugu (leave blank if the line above is right):


15. `nutrient_iron`

   English: iron

   Telugu, as written, unreviewed: ఇనుము

   Correct Telugu (leave blank if the line above is right):


16. `nutrient_vitamin_b12`

   English: vitamin B12

   Telugu, as written, unreviewed: విటమిన్ B12

   Correct Telugu (leave blank if the line above is right):


17. `nutrient_sodium`

   English: sodium

   Telugu, as written, unreviewed: సోడియం

   Correct Telugu (leave blank if the line above is right):


### Living-situation phrases

Dropped into the sentence 'Suggestions are limited to what is realistic for ...' in place of the slot, so each phrase should complete that sentence.

18. `life_context_hostel_student`

   English: hostel and canteen food

   Telugu, as written, unreviewed: హాస్టల్ మరియు క్యాంటీన్ ఆహారం

   Correct Telugu (leave blank if the line above is right):


19. `life_context_pg_own_cooking`

   English: cooking for yourself with limited time

   Telugu, as written, unreviewed: తక్కువ సమయంలో మీ కోసం మీరు వండుకోవడం

   Correct Telugu (leave blank if the line above is right):


20. `life_context_field_or_manual_worker`

   English: long physical shifts and eating out

   Telugu, as written, unreviewed: ఎక్కువసేపు శారీరక పని చేయడం మరియు బయట తినడం

   Correct Telugu (leave blank if the line above is right):


21. `life_context_desk_professional`

   English: a desk day with a full kitchen

   Telugu, as written, unreviewed: డెస్క్ వద్ద పని చేసే రోజు మరియు పూర్తి వంటగది

   Correct Telugu (leave blank if the line above is right):


22. `life_context_homemaker`

   English: cooking for the household

   Telugu, as written, unreviewed: ఇంటివారి కోసం వంట చేయడం

   Correct Telugu (leave blank if the line above is right):


### Language choice

The heading of the screen where the person picks Telugu, Hindi or English.

23. `language_picker_title`

   English: Language

   Telugu, as written, unreviewed: భాష

   Correct Telugu (leave blank if the line above is right):


### The confidence label

A one-word label next to every nutrition figure saying how far to trust it. Good means the food and the amount were both clear; Approximate means something was assumed, such as a standard bowl size; Rough means the figure could be far off.

24. `confidence_band_good`

   English: Good

   Telugu, as written, unreviewed: మంచిది

   Correct Telugu (leave blank if the line above is right):


25. `confidence_band_approximate`

   English: Approximate

   Telugu, as written, unreviewed: సుమారు

   Correct Telugu (leave blank if the line above is right):


26. `confidence_band_rough`

   English: Rough

   Telugu, as written, unreviewed: అంచనా

   Correct Telugu (leave blank if the line above is right):


### Why the label says what it says

Shown when the person taps the confidence label. One sentence explaining it.

27. `confidence_reason_exact_food_match`

   English: Matched exactly to a food in the database.

   Telugu, as written, unreviewed: డేటాబేస్‌లోని ఆహారంతో ఖచ్చితంగా సరిపోలింది.

   Correct Telugu (leave blank if the line above is right):


28. `confidence_reason_fuzzy_food_match`

   English: Matched to the closest name in the database; check it is the food you meant.

   Telugu, as written, unreviewed: డేటాబేస్‌లోని దగ్గరగా సరిపోలే పేరుతో సరిపోలింది; మీరు ఉద్దేశించిన ఆహారమేనా చూసుకోండి.

   Correct Telugu (leave blank if the line above is right):


29. `confidence_reason_category_level_match`

   English: Matched only to a food category, not a specific food.

   Telugu, as written, unreviewed: నిర్దిష్ట ఆహారంతో కాకుండా, ఆహార వర్గంతో మాత్రమే సరిపోలింది.

   Correct Telugu (leave blank if the line above is right):


30. `confidence_reason_quantity_stated`

   English: You gave the quantity in a unit we can convert.

   Telugu, as written, unreviewed: మీరు మార్చగలిగే కొలతలో పరిమాణాన్ని చెప్పారు.

   Correct Telugu (leave blank if the line above is right):


31. `confidence_reason_household_unit_default`

   English: A household measure was converted with a standard weight. Tap to correct the grams.

   Note: 'Household measure' means a katori, glass, spoon or plate rather than grams.

   Telugu, as written, unreviewed: ఇంట్లో ఉపయోగించే కొలతను ప్రామాణిక బరువుతో మార్చాం. గ్రాములను సరిచేయడానికి నొక్కండి.

   Correct Telugu (leave blank if the line above is right):


32. `confidence_reason_quantity_inferred`

   English: You did not say how much, so this quantity is a guess. Tap to correct it.

   Telugu, as written, unreviewed: మీరు ఎంత పరిమాణం చెప్పలేదు, కాబట్టి ఈ పరిమాణం అంచనా మాత్రమే. దాన్ని సరిచేయడానికి నొక్కండి.

   Correct Telugu (leave blank if the line above is right):


33. `confidence_reason_authored_reference_recipe`

   English: Based on a reference recipe. Edit it to match how you cook.

   Note: 'Reference recipe' is the app's own standard recipe for a dish such as sambar, which the person can edit to match their kitchen.

   Telugu, as written, unreviewed: ఒక ప్రామాణిక వంటకం ఆధారంగా తీసుకున్నది. మీరు ఎలా వండుతారో దానికి అనుగుణంగా మార్చండి.

   Correct Telugu (leave blank if the line above is right):


34. `confidence_reason_user_edited_recipe`

   English: Based on the recipe as you edited it.

   Telugu, as written, unreviewed: మీరు సవరించిన వంటకం ఆధారంగా తీసుకున్నది.

   Correct Telugu (leave blank if the line above is right):


35. `confidence_reason_weak_source_record`

   English: The source record for this food rests on few samples or on a label.

   Telugu, as written, unreviewed: ఈ ఆహారానికి సంబంధించిన మూల రికార్డు కొన్ని నమూనాలు లేదా ఒక లేబుల్‌పై ఆధారపడి ఉంది.

   Correct Telugu (leave blank if the line above is right):


36. `confidence_reason_substitute_food_record`

   English: The nearest record is a similar food, not this one.

   Telugu, as written, unreviewed: దగ్గరగా సరిపోలిన రికార్డు ఇదే ఆహారం కాదు, ఇలాంటి ఆహారానికి సంబంధించినది.

   Correct Telugu (leave blank if the line above is right):


37. `confidence_reason_low_asr_confidence`

   English: Speech recognition was unsure of what it heard. Check the words.

   Note: 'Speech recognition' is the part that turns what the person said into words.

   Telugu, as written, unreviewed: మీరు చెప్పింది ఏమిటో వాయిస్ గుర్తింపు సరిగ్గా నిర్ధారించలేకపోయింది. పదాలను చూసుకోండి.

   Correct Telugu (leave blank if the line above is right):


38. `confidence_reason_uncorrected_camera_guess`

   English: A camera guess that has not been confirmed by you.

   Telugu, as written, unreviewed: కెమెరా ద్వారా గుర్తించిన అంచనా ఇంకా మీరు నిర్ధారించలేదు.

   Correct Telugu (leave blank if the line above is right):


### TEMPORARY: the build-status screen

A developer screen listing what is built. It will be replaced before the demo. Lowest priority: do these last, or skip them.

39. `status_screen_subtitle`

   English: Phase 1B scaffold. Contracts are defined; no pipeline is built yet.

   Telugu, as written, unreviewed: ఫేజ్ 1B ప్రాథమిక నిర్మాణం. అవసరమైన ఒప్పందాలు నిర్వచించబడ్డాయి; ఇంకా ఏ పైప్‌లైన్ నిర్మించబడలేదు.

   Correct Telugu (leave blank if the line above is right):


40. `status_line`

   English: %1$s: %2$s

   Note: A format only. %1$s is the name of a feature and %2$s is its state, so the Telugu is just those two slots in the right order with whatever goes between them.

   Telugu, as written, unreviewed: %1$s: %2$s

   Correct Telugu (leave blank if the line above is right):


41. `state_not_implemented`

   English: Not implemented

   Telugu, as written, unreviewed: ఇంకా అమలు చేయలేదు

   Correct Telugu (leave blank if the line above is right):


42. `pipeline_voice_logging`

   English: Voice logging (ASR, LLM extract, TTS)

   Telugu, as written, unreviewed: వాయిస్ లాగింగ్ (ASR, LLM ఎక్స్‌ట్రాక్ట్, TTS)

   Correct Telugu (leave blank if the line above is right):


43. `pipeline_nutrition_lookup`

   English: Nutrition lookup

   Telugu, as written, unreviewed: పోషక విలువల శోధన

   Correct Telugu (leave blank if the line above is right):


44. `pipeline_timeline_query`

   English: Timeline and voice query

   Telugu, as written, unreviewed: టైమ్‌లైన్ మరియు వాయిస్ ప్రశ్న

   Correct Telugu (leave blank if the line above is right):


45. `pipeline_lab_report_scan`

   English: Lab report scan

   Telugu, as written, unreviewed: ల్యాబ్ రిపోర్ట్ స్కాన్

   Correct Telugu (leave blank if the line above is right):


46. `pipeline_adaptive_suggestions`

   English: Adaptive suggestions

   Telugu, as written, unreviewed: అనుకూల సూచనలు

   Correct Telugu (leave blank if the line above is right):


47. `pipeline_dish_first_guess`

   English: Camera dish first guess

   Telugu, as written, unreviewed: కెమెరాతో వంటకం మొదటి అంచనా

   Correct Telugu (leave blank if the line above is right):


48. `pipeline_exercise_form`

   English: Exercise form check

   Telugu, as written, unreviewed: వ్యాయామం చేసే విధానం తనిఖీ

   Correct Telugu (leave blank if the line above is right):


## Telugu not yet written

### UNPLACED: ask Vedant where this appears

49. `context_figure`

   English: %1$s: %2$s %3$s

   Telugu:


50. `context_figure_partial`

   English: %1$s: at least %2$s %3$s (no value for %4$s)

   Telugu:


51. `context_figure_none`

   English: %1$s: not known

   Telugu:


52. `context_meal`

   English: %1$s: %2$s. %3$s

   Telugu:


53. `context_lab`

   English: %1$s: %2$s %3$s (report dated %4$s)

   Telugu:


54. `context_lab_with_range`

   English: %1$s: %2$s %3$s, printed range %4$s to %5$s (report dated %6$s)

   Telugu:


55. `context_never_suggest_vegetarian`

   English: meat, fish or eggs (vegetarian)

   Telugu:


56. `context_never_suggest_vegan`

   English: meat, fish, eggs, milk or any dairy (vegan)

   Telugu:


57. `context_never_suggest_eggetarian`

   English: meat or fish (eggetarian)

   Telugu:


58. `context_never_suggest_jain`

   English: meat, fish, eggs, onion, garlic or root vegetables (Jain)

   Telugu:
