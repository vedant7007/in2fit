# IN2FIT: Telugu strings for review

Generated 20 September 2026 from the app's English string table. 48 strings to write, 0 to check.

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

## Telugu not yet written

### Advice screens

A line at the bottom of every screen that gives advice. Always visible, cannot be closed.

1. `safety_not_medical_advice`

   English: This is information, not medical advice.

   Telugu:


### The health sentences

One of these is shown when something in the person's data changes what the app suggests: a lab report value, a condition they told the app about, what dominates a meal, their living situation, or a pattern over days. THESE MATTER MOST. Each must say only what the data says, never that the person has an illness, never what to take. The slots (%1$s and so on) are filled by the app with names and numbers; what each slot holds is in the note.

2. `trigger_escalate_above_range`

   English: Your report from %1$s shows %2$s at %3$s %4$s, well outside the %5$s printed on it. This is worth showing to a doctor.

   Note: %1$s the report's date, %2$s the test's name as printed, %3$s the value, %4$s its unit, %5$s the upper limit printed on the report. Shown when the value is far above it.

   Telugu:


3. `trigger_escalate_below_range`

   English: Your report from %1$s shows %2$s at %3$s %4$s, well outside the %5$s printed on it. This is worth showing to a doctor.

   Note: Same slots. Shown when the value is far below the lower limit printed on the report.

   Telugu:


4. `trigger_lab_above_range`

   English: Your report from %1$s shows %2$s at %3$s %4$s, above the %5$s printed on it, so suggestions are ranked differently now.

   Note: Same slots as above; %5$s is the upper limit printed on the report.

   Telugu:


5. `trigger_lab_below_range`

   English: Your report from %1$s shows %2$s at %3$s %4$s, below the %5$s printed on it, so suggestions are ranked differently now.

   Note: Same slots; %5$s is the lower limit printed on the report.

   Telugu:


6. `trigger_declared_condition`

   English: You told us you are managing %1$s, so suggestions are ranked with that in mind.

   Note: %1$s is the condition in the person's own words, exactly as they told the app.

   Telugu:


7. `trigger_meal_composition`

   English: Most of the %1$s in this meal comes from %2$s, about %3$s percent of it.

   Note: %1$s a nutrient word (from the list below), %2$s the name of a food or dish, %3$s a whole number, the percentage.

   Telugu:


8. `trigger_life_context`

   English: Suggestions are limited to what is realistic for %1$s.

   Note: %1$s is one of the living-situation phrases below.

   Telugu:


9. `trigger_timeline`

   English: Over the last %1$s days, %2$s.

   Note: %1$s a number of days, %2$s a short description the app supplies.

   Telugu:


### Nutrient words

Single words dropped into the sentences above and shown next to figures, so they should read naturally mid-sentence.

10. `nutrient_energy`

   English: energy

   Telugu:


11. `nutrient_protein`

   English: protein

   Telugu:


12. `nutrient_carbohydrate`

   English: carbohydrate

   Telugu:


13. `nutrient_fat`

   English: fat

   Telugu:


14. `nutrient_fibre`

   English: fibre

   Telugu:


15. `nutrient_iron`

   English: iron

   Telugu:


16. `nutrient_vitamin_b12`

   English: vitamin B12

   Telugu:


17. `nutrient_sodium`

   English: sodium

   Telugu:


### Living-situation phrases

Dropped into the sentence 'Suggestions are limited to what is realistic for ...' in place of the slot, so each phrase should complete that sentence.

18. `life_context_hostel_student`

   English: hostel and canteen food

   Telugu:


19. `life_context_pg_own_cooking`

   English: cooking for yourself with limited time

   Telugu:


20. `life_context_field_or_manual_worker`

   English: long physical shifts and eating out

   Telugu:


21. `life_context_desk_professional`

   English: a desk day with a full kitchen

   Telugu:


22. `life_context_homemaker`

   English: cooking for the household

   Telugu:


### Language choice

The heading of the screen where the person picks Telugu, Hindi or English.

23. `language_picker_title`

   English: Language

   Telugu:


### The confidence label

A one-word label next to every nutrition figure saying how far to trust it. Good means the food and the amount were both clear; Approximate means something was assumed, such as a standard bowl size; Rough means the figure could be far off.

24. `confidence_band_good`

   English: Good

   Telugu:


25. `confidence_band_approximate`

   English: Approximate

   Telugu:


26. `confidence_band_rough`

   English: Rough

   Telugu:


### Why the label says what it says

Shown when the person taps the confidence label. One sentence explaining it.

27. `confidence_reason_exact_food_match`

   English: Matched exactly to a food in the database.

   Telugu:


28. `confidence_reason_fuzzy_food_match`

   English: Matched to the closest name in the database; check it is the food you meant.

   Telugu:


29. `confidence_reason_category_level_match`

   English: Matched only to a food category, not a specific food.

   Telugu:


30. `confidence_reason_quantity_stated`

   English: You gave the quantity in a unit we can convert.

   Telugu:


31. `confidence_reason_household_unit_default`

   English: A household measure was converted with a standard weight. Tap to correct the grams.

   Note: 'Household measure' means a katori, glass, spoon or plate rather than grams.

   Telugu:


32. `confidence_reason_quantity_inferred`

   English: You did not say how much, so this quantity is a guess. Tap to correct it.

   Telugu:


33. `confidence_reason_authored_reference_recipe`

   English: Based on a reference recipe. Edit it to match how you cook.

   Note: 'Reference recipe' is the app's own standard recipe for a dish such as sambar, which the person can edit to match their kitchen.

   Telugu:


34. `confidence_reason_user_edited_recipe`

   English: Based on the recipe as you edited it.

   Telugu:


35. `confidence_reason_weak_source_record`

   English: The source record for this food rests on few samples or on a label.

   Telugu:


36. `confidence_reason_substitute_food_record`

   English: The nearest record is a similar food, not this one.

   Telugu:


37. `confidence_reason_low_asr_confidence`

   English: Speech recognition was unsure of what it heard. Check the words.

   Note: 'Speech recognition' is the part that turns what the person said into words.

   Telugu:


38. `confidence_reason_uncorrected_camera_guess`

   English: A camera guess that has not been confirmed by you.

   Telugu:


### TEMPORARY: the build-status screen

A developer screen listing what is built. It will be replaced before the demo. Lowest priority: do these last, or skip them.

39. `status_screen_subtitle`

   English: Phase 1B scaffold. Contracts are defined; no pipeline is built yet.

   Telugu:


40. `status_line`

   English: %1$s: %2$s

   Note: A format only. %1$s is the name of a feature and %2$s is its state, so the Telugu is just those two slots in the right order with whatever goes between them.

   Telugu:


41. `state_not_implemented`

   English: Not implemented

   Telugu:


42. `pipeline_voice_logging`

   English: Voice logging (ASR, LLM extract, TTS)

   Telugu:


43. `pipeline_nutrition_lookup`

   English: Nutrition lookup

   Telugu:


44. `pipeline_timeline_query`

   English: Timeline and voice query

   Telugu:


45. `pipeline_lab_report_scan`

   English: Lab report scan

   Telugu:


46. `pipeline_adaptive_suggestions`

   English: Adaptive suggestions

   Telugu:


47. `pipeline_dish_first_guess`

   English: Camera dish first guess

   Telugu:


48. `pipeline_exercise_form`

   English: Exercise form check

   Telugu:
