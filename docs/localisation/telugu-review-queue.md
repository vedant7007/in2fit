# IN2FIT: Telugu strings for review

Generated 20 September 2026 from the app's English string table. 27 strings to write, 0 to check.

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

Send the file back to Vedant. Your text is pasted into the app unchanged.

## Telugu not yet written

### Advice screens

A line at the bottom of every screen that gives advice. Always visible, cannot be closed.

1. `safety_not_medical_advice`

   English: This is information, not medical advice.

   Telugu:


### Language choice

The heading of the screen where the person picks Telugu, Hindi or English.

2. `language_picker_title`

   English: Language

   Telugu:


### The confidence label

A one-word label next to every nutrition figure saying how far to trust it. Good means the food and the amount were both clear; Approximate means something was assumed, such as a standard bowl size; Rough means the figure could be far off.

3. `confidence_band_good`

   English: Good

   Telugu:


4. `confidence_band_approximate`

   English: Approximate

   Telugu:


5. `confidence_band_rough`

   English: Rough

   Telugu:


### Why the label says what it says

Shown when the person taps the confidence label. One sentence explaining it.

6. `confidence_reason_exact_food_match`

   English: Matched exactly to a food in the database.

   Telugu:


7. `confidence_reason_fuzzy_food_match`

   English: Matched to the closest name in the database; check it is the food you meant.

   Telugu:


8. `confidence_reason_category_level_match`

   English: Matched only to a food category, not a specific food.

   Telugu:


9. `confidence_reason_quantity_stated`

   English: You gave the quantity in a unit we can convert.

   Telugu:


10. `confidence_reason_household_unit_default`

   English: A household measure was converted with a standard weight. Tap to correct the grams.

   Note: 'Household measure' means a katori, glass, spoon or plate rather than grams.

   Telugu:


11. `confidence_reason_quantity_inferred`

   English: You did not say how much, so this quantity is a guess. Tap to correct it.

   Telugu:


12. `confidence_reason_authored_reference_recipe`

   English: Based on a reference recipe. Edit it to match how you cook.

   Note: 'Reference recipe' is the app's own standard recipe for a dish such as sambar, which the person can edit to match their kitchen.

   Telugu:


13. `confidence_reason_user_edited_recipe`

   English: Based on the recipe as you edited it.

   Telugu:


14. `confidence_reason_weak_source_record`

   English: The source record for this food rests on few samples or on a label.

   Telugu:


15. `confidence_reason_substitute_food_record`

   English: The nearest record is a similar food, not this one.

   Telugu:


16. `confidence_reason_low_asr_confidence`

   English: Speech recognition was unsure of what it heard. Check the words.

   Note: 'Speech recognition' is the part that turns what the person said into words.

   Telugu:


17. `confidence_reason_uncorrected_camera_guess`

   English: A camera guess that has not been confirmed by you.

   Telugu:


### TEMPORARY: the build-status screen

A developer screen listing what is built. It will be replaced before the demo. Lowest priority: do these last, or skip them.

18. `status_screen_subtitle`

   English: Phase 1B scaffold. Contracts are defined; no pipeline is built yet.

   Telugu:


19. `status_line`

   English: %1$s: %2$s

   Note: A format only. %1$s is the name of a feature and %2$s is its state, so the Telugu is just those two slots in the right order with whatever goes between them.

   Telugu:


20. `state_not_implemented`

   English: Not implemented

   Telugu:


21. `pipeline_voice_logging`

   English: Voice logging (ASR, LLM extract, TTS)

   Telugu:


22. `pipeline_nutrition_lookup`

   English: Nutrition lookup

   Telugu:


23. `pipeline_timeline_query`

   English: Timeline and voice query

   Telugu:


24. `pipeline_lab_report_scan`

   English: Lab report scan

   Telugu:


25. `pipeline_adaptive_suggestions`

   English: Adaptive suggestions

   Telugu:


26. `pipeline_dish_first_guess`

   English: Camera dish first guess

   Telugu:


27. `pipeline_exercise_form`

   English: Exercise form check

   Telugu:
