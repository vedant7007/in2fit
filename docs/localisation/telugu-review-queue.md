# IN2FIT: Telugu reviewer packet

**If you only do one part, do Part 1.** Those nine lines decide whether the app speaks Telugu at all.

Generated 20 September 2026 from the app's English string table. 66 lines to check, 0 to write. One packet, one trip: everything the team needs from you is in this file.

Reviewer's name: ______________________

## What the app is

IN2FIT is a phone app for people in India who may not read English nutrition labels. A person says what they ate, in Telugu, Hindi or English; the app finds each food in its database and shows the nutrition, with a label saying how far to trust each figure. It can also read a printed lab report with the camera. It never diagnoses and never prescribes.

## What we need from you

Most lines below already have Telugu on them, WRITTEN BY A MACHINE. Nobody on the team can read it. Under each one is a line `Correct Telugu:`. If the machine's line is right, leave that blank. If it is wrong, write the right line there. A line with no Telugu yet has a `Telugu:` line to write on.

1. Write it the way you would say it to a family member. Short: it goes on a phone screen.
2. Keep the meaning exactly. Add no reassurance, no advice and no number. The English was written so that the app never says a person has a condition and never tells them what to take; the translation must not either.
3. Anything like `%1$s` or `%2$s` is a slot the app fills in with a name or a number. Keep it in the Telugu sentence, wherever Telugu needs it.
4. If an English line is unclear or makes no sense to you, write that instead of guessing. A note beats a wrong string.
5. Put your name at the top. Because you checked it, it counts as reviewed.
6. Replying in a chat instead of in this file is fine: send your name, then one line per item starting with its number here, like `7. ...`, or `7. ok` for a line that is right. The numbers are how your words reach the right place, so keep them.

Send it back to Vedant. Your text goes into the app unchanged, and you will get a list back showing each item next to what landed, so you can check nothing slipped.

## PART 1. THE NINE SENTENCES THAT DECIDE WHETHER TELUGU SHIPS

The safety line and the eight health sentences. If these nine are not confirmed by a fluent speaker before the demo build is made, the app ships without Telugu. If you only have ten minutes, do these nine and stop.

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


## PART 2. NEW LINES THAT ARRIVED TODAY

Written by a machine as a starting point so the app has no holes; every one needs your eye. Correct or confirm each.

### The About screen

Where the app says where its numbers come from and which open-source parts it contains.

10. `about_licences_title`

   English: Data sources and open-source licences

   Telugu, as written, unreviewed: డేటా మూలాలు మరియు ఓపెన్ సోర్స్ లైసెన్సులు

   Correct Telugu (leave blank if the line above is right):


### Lines the app writes for its own language model, not for the screen

Not shown on a screen. When the person asks a question, the app writes their own meals, lab values and diet into a few lines like these and gives them to its language model, in the person's language, before it answers; the answer may repeat them back. Plain and literal, no advice in them: every slot is a name, a number or a date the app fills in.

11. `context_figure`

   English: %1$s: %2$s %3$s

   Note: %1$s a nutrient word, %2$s a number, %3$s its unit (g, mg, kcal). A line like 'iron: 4 mg'. Keep it that short.

   Telugu, as written, unreviewed: %1$s: %2$s %3$s

   Correct Telugu (leave blank if the line above is right):


12. `context_figure_partial`

   English: %1$s: at least %2$s %3$s (no value for %4$s)

   Note: Same, when some foods in the meal had no value: %4$s is the names of those foods.

   Telugu, as written, unreviewed: %1$s: కనీసం %2$s %3$s (%4$sకు విలువ లేదు)

   Correct Telugu (leave blank if the line above is right):


13. `context_figure_none`

   English: %1$s: not known

   Note: %1$s a nutrient word. The app has no value for it.

   Telugu, as written, unreviewed: %1$s: తెలియదు

   Correct Telugu (leave blank if the line above is right):


14. `context_meal`

   English: %1$s: %2$s. %3$s

   Note: %1$s the time of the meal, %2$s the foods, %3$s the figures. Just the slots and the punctuation between them.

   Telugu, as written, unreviewed: %1$s: %2$s. %3$s

   Correct Telugu (leave blank if the line above is right):


15. `context_period`

   English: %1$s: %2$s

   Note: %1$s a period (one of the two lines below), %2$s the figures for it.

   Telugu, as written, unreviewed: %1$s: %2$s

   Correct Telugu (leave blank if the line above is right):


16. `context_period_today`

   English: Today so far

   Telugu, as written, unreviewed: ఈరోజు ఇప్పటివరకు

   Correct Telugu (leave blank if the line above is right):


17. `context_period_last_seven_days`

   English: The last seven days

   Telugu, as written, unreviewed: గత ఏడు రోజులు

   Correct Telugu (leave blank if the line above is right):


18. `context_lab`

   English: %1$s: %2$s %3$s (report dated %4$s)

   Note: %1$s the test's name as printed, %2$s the value, %3$s its unit, %4$s the report's date.

   Telugu, as written, unreviewed: %1$s: %2$s %3$s (%4$s తేదీ రిపోర్టు)

   Correct Telugu (leave blank if the line above is right):


19. `context_lab_with_range`

   English: %1$s: %2$s %3$s, printed range %4$s to %5$s (report dated %6$s)

   Note: Same, plus %4$s and %5$s the low and high limits printed on the report, and %6$s the date.

   Telugu, as written, unreviewed: %1$s: %2$s %3$s, రిపోర్టులో ముద్రించిన పరిధి %4$s నుండి %5$s (%6$s తేదీ రిపోర్టు)

   Correct Telugu (leave blank if the line above is right):


20. `context_never_suggest_vegetarian`

   English: meat, fish or eggs (vegetarian)

   Note: Completes 'never suggest ...'. The word in brackets is the diet as the person named it.

   Telugu, as written, unreviewed: మాంసం, చేపలు లేదా గుడ్లు (శాకాహారి)

   Correct Telugu (leave blank if the line above is right):


21. `context_never_suggest_vegan`

   English: meat, fish, eggs, milk or any dairy (vegan)

   Telugu, as written, unreviewed: మాంసం, చేపలు, గుడ్లు, పాలు లేదా ఏ పాల ఉత్పత్తులు (వీగన్)

   Correct Telugu (leave blank if the line above is right):


22. `context_never_suggest_eggetarian`

   English: meat or fish (eggetarian)

   Telugu, as written, unreviewed: మాంసం లేదా చేపలు (గుడ్లు తినే శాకాహారి)

   Correct Telugu (leave blank if the line above is right):


23. `context_never_suggest_jain`

   English: meat, fish, eggs, onion, garlic or root vegetables (Jain)

   Telugu, as written, unreviewed: మాంసం, చేపలు, గుడ్లు, ఉల్లిపాయ, వెల్లుల్లి లేదా దుంప కూరగాయలు (జైన్)

   Correct Telugu (leave blank if the line above is right):


### Spoken while the app works

The app says one of these aloud while it is thinking, so a ten-second wait sounds like work and not like silence. One or two seconds long when spoken. No health content.

24. `tts_lead_in_log`

   English: Noting that down.

   Telugu, as written, unreviewed: నోట్ చేసుకుంటున్నాను.

   Correct Telugu (leave blank if the line above is right):


25. `tts_lead_in_answer`

   English: Let me check your records.

   Telugu, as written, unreviewed: మీ రికార్డులు చూస్తాను.

   Correct Telugu (leave blank if the line above is right):


26. `tts_lead_in_suggest`

   English: Let me think about what fits.

   Telugu, as written, unreviewed: ఏది సరిపోతుందో ఆలోచిస్తాను.

   Correct Telugu (leave blank if the line above is right):


27. `tts_lead_in_recommend`

   English: Let me see what suits you.

   Telugu, as written, unreviewed: మీకు ఏది సరిపడుతుందో చూస్తాను.

   Correct Telugu (leave blank if the line above is right):


## PART 3. THE REST OF THE SCREEN TEXT

Labels, explanations and the words dropped into sentences. Same rules.

### Nutrient words

Single words dropped into the sentences above and shown next to figures, so they should read naturally mid-sentence.

28. `nutrient_energy`

   English: energy

   Telugu, as written, unreviewed: శక్తి

   Correct Telugu (leave blank if the line above is right):


29. `nutrient_protein`

   English: protein

   Telugu, as written, unreviewed: ప్రోటీన్

   Correct Telugu (leave blank if the line above is right):


30. `nutrient_carbohydrate`

   English: carbohydrate

   Telugu, as written, unreviewed: కార్బోహైడ్రేట్

   Correct Telugu (leave blank if the line above is right):


31. `nutrient_fat`

   English: fat

   Telugu, as written, unreviewed: కొవ్వు

   Correct Telugu (leave blank if the line above is right):


32. `nutrient_fibre`

   English: fibre

   Telugu, as written, unreviewed: పీచు

   Correct Telugu (leave blank if the line above is right):


33. `nutrient_iron`

   English: iron

   Telugu, as written, unreviewed: ఇనుము

   Correct Telugu (leave blank if the line above is right):


34. `nutrient_vitamin_b12`

   English: vitamin B12

   Telugu, as written, unreviewed: విటమిన్ B12

   Correct Telugu (leave blank if the line above is right):


35. `nutrient_sodium`

   English: sodium

   Telugu, as written, unreviewed: సోడియం

   Correct Telugu (leave blank if the line above is right):


### Living-situation phrases

Dropped into the sentence 'Suggestions are limited to what is realistic for ...' in place of the slot, so each phrase should complete that sentence.

36. `life_context_hostel_student`

   English: hostel and canteen food

   Telugu, as written, unreviewed: హాస్టల్ మరియు క్యాంటీన్ ఆహారం

   Correct Telugu (leave blank if the line above is right):


37. `life_context_pg_own_cooking`

   English: cooking for yourself with limited time

   Telugu, as written, unreviewed: తక్కువ సమయంలో మీ కోసం మీరు వండుకోవడం

   Correct Telugu (leave blank if the line above is right):


38. `life_context_field_or_manual_worker`

   English: long physical shifts and eating out

   Telugu, as written, unreviewed: ఎక్కువసేపు శారీరక పని చేయడం మరియు బయట తినడం

   Correct Telugu (leave blank if the line above is right):


39. `life_context_desk_professional`

   English: a desk day with a full kitchen

   Telugu, as written, unreviewed: డెస్క్ వద్ద పని చేసే రోజు మరియు పూర్తి వంటగది

   Correct Telugu (leave blank if the line above is right):


40. `life_context_homemaker`

   English: cooking for the household

   Telugu, as written, unreviewed: ఇంటివారి కోసం వంట చేయడం

   Correct Telugu (leave blank if the line above is right):


### Language choice

The heading of the screen where the person picks Telugu, Hindi or English.

41. `language_picker_title`

   English: Language

   Telugu, as written, unreviewed: భాష

   Correct Telugu (leave blank if the line above is right):


### The confidence label

A one-word label next to every nutrition figure saying how far to trust it. Good means the food and the amount were both clear; Approximate means something was assumed, such as a standard bowl size; Rough means the figure could be far off.

42. `confidence_band_good`

   English: Good

   Telugu, as written, unreviewed: మంచిది

   Correct Telugu (leave blank if the line above is right):


43. `confidence_band_approximate`

   English: Approximate

   Telugu, as written, unreviewed: సుమారు

   Correct Telugu (leave blank if the line above is right):


44. `confidence_band_rough`

   English: Rough

   Telugu, as written, unreviewed: అంచనా

   Correct Telugu (leave blank if the line above is right):


### Why the label says what it says

Shown when the person taps the confidence label. One sentence explaining it.

45. `confidence_reason_exact_food_match`

   English: Matched exactly to a food in the database.

   Telugu, as written, unreviewed: డేటాబేస్‌లోని ఆహారంతో ఖచ్చితంగా సరిపోలింది.

   Correct Telugu (leave blank if the line above is right):


46. `confidence_reason_fuzzy_food_match`

   English: Matched to the closest name in the database; check it is the food you meant.

   Telugu, as written, unreviewed: డేటాబేస్‌లోని దగ్గరగా సరిపోలే పేరుతో సరిపోలింది; మీరు ఉద్దేశించిన ఆహారమేనా చూసుకోండి.

   Correct Telugu (leave blank if the line above is right):


47. `confidence_reason_category_level_match`

   English: Matched only to a food category, not a specific food.

   Telugu, as written, unreviewed: నిర్దిష్ట ఆహారంతో కాకుండా, ఆహార వర్గంతో మాత్రమే సరిపోలింది.

   Correct Telugu (leave blank if the line above is right):


48. `confidence_reason_quantity_stated`

   English: You gave the quantity in a unit we can convert.

   Telugu, as written, unreviewed: మీరు మార్చగలిగే కొలతలో పరిమాణాన్ని చెప్పారు.

   Correct Telugu (leave blank if the line above is right):


49. `confidence_reason_household_unit_default`

   English: A household measure was converted with a standard weight. Tap to correct the grams.

   Note: 'Household measure' means a katori, glass, spoon or plate rather than grams.

   Telugu, as written, unreviewed: ఇంట్లో ఉపయోగించే కొలతను ప్రామాణిక బరువుతో మార్చాం. గ్రాములను సరిచేయడానికి నొక్కండి.

   Correct Telugu (leave blank if the line above is right):


50. `confidence_reason_quantity_inferred`

   English: You did not say how much, so this quantity is a guess. Tap to correct it.

   Telugu, as written, unreviewed: మీరు ఎంత పరిమాణం చెప్పలేదు, కాబట్టి ఈ పరిమాణం అంచనా మాత్రమే. దాన్ని సరిచేయడానికి నొక్కండి.

   Correct Telugu (leave blank if the line above is right):


51. `confidence_reason_authored_reference_recipe`

   English: Based on a reference recipe. Edit it to match how you cook.

   Note: 'Reference recipe' is the app's own standard recipe for a dish such as sambar, which the person can edit to match their kitchen.

   Telugu, as written, unreviewed: ఒక ప్రామాణిక వంటకం ఆధారంగా తీసుకున్నది. మీరు ఎలా వండుతారో దానికి అనుగుణంగా మార్చండి.

   Correct Telugu (leave blank if the line above is right):


52. `confidence_reason_user_edited_recipe`

   English: Based on the recipe as you edited it.

   Telugu, as written, unreviewed: మీరు సవరించిన వంటకం ఆధారంగా తీసుకున్నది.

   Correct Telugu (leave blank if the line above is right):


53. `confidence_reason_weak_source_record`

   English: The source record for this food rests on few samples or on a label.

   Telugu, as written, unreviewed: ఈ ఆహారానికి సంబంధించిన మూల రికార్డు కొన్ని నమూనాలు లేదా ఒక లేబుల్‌పై ఆధారపడి ఉంది.

   Correct Telugu (leave blank if the line above is right):


54. `confidence_reason_substitute_food_record`

   English: The nearest record is a similar food, not this one.

   Telugu, as written, unreviewed: దగ్గరగా సరిపోలిన రికార్డు ఇదే ఆహారం కాదు, ఇలాంటి ఆహారానికి సంబంధించినది.

   Correct Telugu (leave blank if the line above is right):


55. `confidence_reason_low_asr_confidence`

   English: Speech recognition was unsure of what it heard. Check the words.

   Note: 'Speech recognition' is the part that turns what the person said into words.

   Telugu, as written, unreviewed: మీరు చెప్పింది ఏమిటో వాయిస్ గుర్తింపు సరిగ్గా నిర్ధారించలేకపోయింది. పదాలను చూసుకోండి.

   Correct Telugu (leave blank if the line above is right):


56. `confidence_reason_uncorrected_camera_guess`

   English: A camera guess that has not been confirmed by you.

   Telugu, as written, unreviewed: కెమెరా ద్వారా గుర్తించిన అంచనా ఇంకా మీరు నిర్ధారించలేదు.

   Correct Telugu (leave blank if the line above is right):


### TEMPORARY: the build-status screen

A developer screen listing what is built. It will be replaced before the demo. Lowest priority: do these last, or skip them.

57. `status_screen_subtitle`

   English: Phase 1B scaffold. Contracts are defined; no pipeline is built yet.

   Telugu, as written, unreviewed: ఫేజ్ 1B ప్రాథమిక నిర్మాణం. అవసరమైన ఒప్పందాలు నిర్వచించబడ్డాయి; ఇంకా ఏ పైప్‌లైన్ నిర్మించబడలేదు.

   Correct Telugu (leave blank if the line above is right):


58. `status_line`

   English: %1$s: %2$s

   Note: A format only. %1$s is the name of a feature and %2$s is its state, so the Telugu is just those two slots in the right order with whatever goes between them.

   Telugu, as written, unreviewed: %1$s: %2$s

   Correct Telugu (leave blank if the line above is right):


59. `state_not_implemented`

   English: Not implemented

   Telugu, as written, unreviewed: ఇంకా అమలు చేయలేదు

   Correct Telugu (leave blank if the line above is right):


60. `pipeline_voice_logging`

   English: Voice logging (ASR, LLM extract, TTS)

   Telugu, as written, unreviewed: వాయిస్ లాగింగ్ (ASR, LLM ఎక్స్‌ట్రాక్ట్, TTS)

   Correct Telugu (leave blank if the line above is right):


61. `pipeline_nutrition_lookup`

   English: Nutrition lookup

   Telugu, as written, unreviewed: పోషక విలువల శోధన

   Correct Telugu (leave blank if the line above is right):


62. `pipeline_timeline_query`

   English: Timeline and voice query

   Telugu, as written, unreviewed: టైమ్‌లైన్ మరియు వాయిస్ ప్రశ్న

   Correct Telugu (leave blank if the line above is right):


63. `pipeline_lab_report_scan`

   English: Lab report scan

   Telugu, as written, unreviewed: ల్యాబ్ రిపోర్ట్ స్కాన్

   Correct Telugu (leave blank if the line above is right):


64. `pipeline_adaptive_suggestions`

   English: Adaptive suggestions

   Telugu, as written, unreviewed: అనుకూల సూచనలు

   Correct Telugu (leave blank if the line above is right):


65. `pipeline_dish_first_guess`

   English: Camera dish first guess

   Telugu, as written, unreviewed: కెమెరాతో వంటకం మొదటి అంచనా

   Correct Telugu (leave blank if the line above is right):


66. `pipeline_exercise_form`

   English: Exercise form check

   Telugu, as written, unreviewed: వ్యాయామం చేసే విధానం తనిఖీ

   Correct Telugu (leave blank if the line above is right):


## PART 4. LOWER PRIORITY: OTHER QUESTIONS FROM THE TEAM

Each section below was written by another member of the team and is a different kind of question. Skipping these costs us nothing you have not already given us above; do them if you have time.

### IN2FIT: a short word list for a Telugu and Hindi speaker to check

Different from the strings sheet: nothing to write, only yes-or-no answers about single words.
Ten minutes. Send it with `docs/localisation/telugu-review-queue.md`, or on its own.

Reviewer's name: ______________________

#### What this is for

When a person speaks to the app, one rule decides whether they are *telling the app what they
ate* (which the app writes into their food history) or *asking it something* (which it must
never write into their history). The rule is written in Roman letters, and Telugu, Hindi and
English words share those letters. We already found one collision by measuring: English "do"
(as in "do I need...") is Hindi "do" (two), and every "maine do roti khaya" was being treated
as a question. There may be others, and they are the kind of thing only a speaker sees.

The rule is deliberately lopsided. Treating a meal as a question costs the person two seconds.
Treating a question as a meal writes food they never ate into their health history. So:

#### The words that matter most: "I ate" / "I drank"

The app treats a sentence containing one of these as **certainly a meal**, if nothing else in it
looks like a question. **A wrong word here is the dangerous kind.** For each word, please answer:

- Does it mean *ate* or *drank*, in the past, the way a person would say what they had?
- Could the same spelling be a different word in Telugu, Hindi or Telugu-English mixing, that
  someone might say when asking a question rather than reporting a meal?
- Is there a common everyday spelling or form we have missed?

| # | Word | Meant as | Past-tense ate/drank? (yes/no) | Could mean something else? | Missing form? |
| --- | --- | --- | --- | --- | --- |
| 1 | `khaya` | Hindi, ate | | | |
| 2 | `khayi` | Hindi, ate (fem.) | | | |
| 3 | `khaye` | Hindi, ate (pl.) | | | |
| 4 | `khaaya` | Hindi, ate, long-vowel spelling | | | |
| 5 | `khai` | Hindi, ate, short spelling | | | |
| 6 | `piya` | Hindi, drank | | | |
| 7 | `pi` | Hindi, drank, short spelling | | | |
| 8 | `peeya` | Hindi, drank, long-vowel spelling | | | |
| 9 | `tinnanu` | Telugu, I ate | | | |
| 10 | `tinnaanu` | Telugu, I ate, long-vowel spelling | | | |
| 11 | `thinnanu` | Telugu, I ate, th- spelling | | | |
| 12 | `tinna` | Telugu, ate, colloquial | | | |
| 13 | `thinna` | Telugu, ate, th- spelling | | | |
| 14 | `tinnam` | Telugu, we ate | | | |
| 15 | `tinnaam` | Telugu, we ate, long-vowel spelling | | | |
| 16 | `tagaanu` | Telugu, I drank | | | |
| 17 | `taganu` | Telugu, I drank, short spelling | | | |
| 18 | `thaganu` | Telugu, I drank, th- spelling | | | |

English words on the same list, for completeness: `ate`, `had`, `drank`, `eaten`, `finished`,
and the phrases `breakfast was`, `lunch was`, `dinner was`, `snack was`, `tiffin was`.

#### The words that make the app ask instead

A sentence containing any of these is sent to the app's slower, careful path. **A wrong word
here only costs two seconds**, so guessing is safe, but a common *meal* word on this list would
slow every meal down. Please only flag one if it is a word people use when *reporting a meal*.

Hindi: `kya kitna kitni kitne kaise kaun kaunsa kaunsi kab kahan kyun kyon chahiye karun karoon
karu sakta sakti sakte hoon raha rahi rahe abhi batao bataye bataiye accha achha behtar sahi
theek`; phrases `kha raha`, `kha rahi`, `kha rahe`, `hai to`, `hai toh`, `ke liye`.

Telugu: `emi em enti entha enni ela ekkada eppudu evaru endhuku enduku tinali tinaali thinali
cheyali cheyyali kavali kavaali cheppu cheppandi ippudu tintunna tintunnanu tintunnam manchidi
manchida bagunda avuna kada`; phrase `kosam`.

English (listed so you can spot a Telugu or Hindi word hiding in it, like "do"): `what whats
which how why when where who whether should shall can could would will may might must does did
are am any anything enough add suggest recommend recommendation advice advise avoid better best
worse help helps need needs want wants prefer instead swap replace option options question tell
show check compare versus vs ok okay fine good healthy report doctor checked reading level
medicine tablet tablets remove delete undo cancel correct change edit wrong mistake having eating
making cooking planning going now tonight later`. And `do` and `is`, only at the start of a
sentence.

Any English word above that is ALSO an everyday Telugu or Hindi word people would say while
reporting a meal: write it here.

______________________________________________________________________________

#### Second list: words about medicine, illness and diagnosis

Separate rule, same kind of question. When a person's sentence contains one of these, the app
adds a line saying a doctor should judge it; and when the app's own reply contains one of the
phrases, the reply is thrown away. A wrong word here costs one extra sentence or one lost reply,
never a wrong meal, so this list matters less than the first. Still: is any of these a common
everyday word that means something else?

Hindi: `dawai dawa goli goliyan bimari bimar khatra khatarnak ilaj`; phrases `hai kya`, `kya hai`,
`kya mujhe`, `mujhe kya hua`. Replies thrown away if they contain: `aapko ... hai` / `tumhe ...
hai` with an illness word, and `goli lo`, `dawai lo`, `goli roz lo`, `dawai lena`.

Telugu: `mandu mandulu matra jabbu rogam pramadam pramadakaram vaidyam`; phrases `unda`,
`vachinda`, `tagginda`, `perigindha`. Replies thrown away if they contain: `meeku ... undi` /
`neeku ... undi` with an illness word, and `mandu veskondi`, `matra veyandi`, `tablets
thesukondi`.

Any of these wrong, or a common form missing: write it here.

______________________________________________________________________________

Send it back to Vedant. The lists live in `LogPrefilter.kt` and `SafetyLine.kt`, and a test
keeps this sheet and the log-word list in step, so a word you flag is changed in one place.

---

### Two things to listen to, and one sentence to write (from Meera, the voice)

This is about the app's VOICE, not its text. Vedant will send you a few short audio files with
this packet; they are named like `te-padmavathi-default-A.wav`. Each is the same two Telugu words
said by a different synthetic voice.

1. **Rank them.** Which sounds most like a person speaking Telugu, and which least? A rough order
   is enough: best to worst, by file name. If the phone's own built-in Telugu voice is among
   them, rank it too and say so.

2. **Write one sentence.** One ordinary Telugu sentence a person would actually say about a meal
   they ate, the kind of thing someone says to family. It becomes the test sentence every voice
   is judged on next; two food names is not a fair test of a voice, and nobody on the team can
   write that sentence.

   Sentence: ______________________________________________________________

---
