# IN2FIT: Telugu reviewer packet

**If you only do one part, do Part 1.** Those nine lines decide whether the app speaks Telugu at all.

Generated 20 September 2026 from the app's English string table. 176 lines to check, 0 to write. One packet, one trip: everything the team needs from you is in this file.

Reviewer's name: ______________________

**Frozen at 22:00 on 2026-09-20. This is the packet that ships; keys landing after this line are English in the demo build and wait for the next packet.**

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


## PART 2. THE SCREENS THE DEMO SHOWS, AND THE NEW LINES THAT ARRIVED TODAY

Written by a machine as a starting point so the app has no holes; every one needs your eye. Correct or confirm each. These are the words on screen during the demo.

### The three tabs at the bottom of the screen

One word each.

10. `tab_talk`

   English: Talk

   Telugu, as written, unreviewed: మాట్లాడండి

   Correct Telugu (leave blank if the line above is right):


11. `tab_scan`

   English: Scan report

   Telugu, as written, unreviewed: రిపోర్టు స్కాన్

   Correct Telugu (leave blank if the line above is right):


12. `tab_about`

   English: About

   Telugu, as written, unreviewed: గురించి

   Correct Telugu (leave blank if the line above is right):


### The Talk screen, where the person speaks or types

The main screen. The person says or types what they ate, or asks a question.

13. `talk_hint`

   English: Say what you ate, or ask a question.

   Telugu, as written, unreviewed: మీరు ఏమి తిన్నారో చెప్పండి, లేదా ఒక ప్రశ్న అడగండి.

   Correct Telugu (leave blank if the line above is right):


14. `talk_elapsed_seconds`

   English: %1$d s

   Telugu, as written, unreviewed: %1$d సె

   Correct Telugu (leave blank if the line above is right):


15. `mic_speak`

   English: Speak

   Telugu, as written, unreviewed: మాట్లాడండి

   Correct Telugu (leave blank if the line above is right):


16. `mic_listening`

   English: Listening…

   Telugu, as written, unreviewed: వింటున్నాను…

   Correct Telugu (leave blank if the line above is right):


17. `mic_hold_to_speak`

   English: Hold to speak

   Telugu, as written, unreviewed: నొక్కి పట్టుకుని మాట్లాడండి

   Correct Telugu (leave blank if the line above is right):


18. `mic_hold_hint`

   English: Hold the button while you speak, then let go.

   Telugu, as written, unreviewed: మాట్లాడేటప్పుడు బటన్‌ను నొక్కి పట్టుకోండి, తరువాత వదిలేయండి.

   Correct Telugu (leave blank if the line above is right):


19. `mic_permission_needed`

   English: Microphone permission was not granted. You can type instead.

   Telugu, as written, unreviewed: మైక్రోఫోన్ అనుమతి ఇవ్వలేదు. బదులుగా టైప్ చేయవచ్చు.

   Correct Telugu (leave blank if the line above is right):


20. `type_hint`

   English: Type what you ate, or a question

   Telugu, as written, unreviewed: మీరు ఏమి తిన్నారో, లేదా ఒక ప్రశ్న టైప్ చేయండి

   Correct Telugu (leave blank if the line above is right):


21. `send`

   English: Send

   Telugu, as written, unreviewed: పంపండి

   Correct Telugu (leave blank if the line above is right):


22. `said_by_you`

   English: You

   Telugu, as written, unreviewed: మీరు

   Correct Telugu (leave blank if the line above is right):


23. `advise_again`

   English: Advise again on my last meal

   Telugu, as written, unreviewed: నా చివరి భోజనంపై మళ్ళీ సలహా ఇవ్వండి

   Correct Telugu (leave blank if the line above is right):


### Progress lines while the app works

Shown one after another while the app is busy, instead of a spinner, so a ten-second wait reads as work. Each is a short phrase in the present tense: what it is doing now.

24. `stage_recording`

   English: Listening

   Telugu, as written, unreviewed: వింటున్నాను

   Correct Telugu (leave blank if the line above is right):


25. `stage_transcribing`

   English: Writing down what you said

   Telugu, as written, unreviewed: మీరు చెప్పింది రాస్తున్నాను

   Correct Telugu (leave blank if the line above is right):


26. `stage_classifying`

   English: Working out what you meant

   Telugu, as written, unreviewed: మీ ఉద్దేశం తెలుసుకుంటున్నాను

   Correct Telugu (leave blank if the line above is right):


27. `stage_extracting`

   English: Picking out the foods

   Telugu, as written, unreviewed: ఆహారాలను గుర్తిస్తున్నాను

   Correct Telugu (leave blank if the line above is right):


28. `stage_matching_foods`

   English: Matching the foods

   Telugu, as written, unreviewed: ఆహారాలను సరిపోలుస్తున్నాను

   Correct Telugu (leave blank if the line above is right):


29. `stage_computing`

   English: Adding up the figures

   Telugu, as written, unreviewed: అంకెలను కూడుతున్నాను

   Correct Telugu (leave blank if the line above is right):


30. `stage_saving`

   English: Saving

   Telugu, as written, unreviewed: సేవ్ చేస్తున్నాను

   Correct Telugu (leave blank if the line above is right):


31. `stage_evaluating_rules`

   English: Checking your records

   Telugu, as written, unreviewed: మీ రికార్డులు చూస్తున్నాను

   Correct Telugu (leave blank if the line above is right):


32. `stage_retrieving_facts`

   English: Looking up facts

   Telugu, as written, unreviewed: వివరాలు చూస్తున్నాను

   Correct Telugu (leave blank if the line above is right):


33. `stage_phrasing`

   English: Writing the reply

   Telugu, as written, unreviewed: జవాబు రాస్తున్నాను

   Correct Telugu (leave blank if the line above is right):


34. `stage_speaking`

   English: Speaking

   Telugu, as written, unreviewed: చెబుతున్నాను

   Correct Telugu (leave blank if the line above is right):


35. `stage_capturing`

   English: Capturing

   Telugu, as written, unreviewed: ఫోటో తీస్తున్నాను

   Correct Telugu (leave blank if the line above is right):


36. `stage_reading_text`

   English: Reading the text

   Telugu, as written, unreviewed: అక్షరాలు చదువుతున్నాను

   Correct Telugu (leave blank if the line above is right):


### A plate, after the app has understood it

The foods as the person said them, with the figures. 'Logged' means written into their food history; the other line means the plate was only asked about.

37. `meal_logged`

   English: Logged

   Telugu, as written, unreviewed: నమోదు చేశాను

   Correct Telugu (leave blank if the line above is right):


38. `meal_hypothetical`

   English: A plate you asked about. Not logged.

   Telugu, as written, unreviewed: మీరు అడిగిన ప్లేటు. నమోదు చేయలేదు.

   Correct Telugu (leave blank if the line above is right):


39. `meal_item_with_quantity`

   English: %1$s: %2$s %3$s

   Telugu, as written, unreviewed: %1$s: %2$s %3$s

   Correct Telugu (leave blank if the line above is right):


40. `meal_item_no_quantity`

   English: %1$s: quantity not stated

   Telugu, as written, unreviewed: %1$s: పరిమాణం చెప్పలేదు

   Correct Telugu (leave blank if the line above is right):


41. `figure_with_band`

   English: %1$s (%2$s)

   Telugu, as written, unreviewed: %1$s (%2$s)

   Correct Telugu (leave blank if the line above is right):


### The reply

What the app says back: a sentence from its rules, then foods a person could consider.

42. `advice_title`

   English: Advice

   Telugu, as written, unreviewed: సలహా

   Correct Telugu (leave blank if the line above is right):


43. `advice_no_rule`

   English: Nothing in your records changes this.

   Telugu, as written, unreviewed: మీ రికార్డుల్లో దీన్ని మార్చేది ఏమీ లేదు.

   Correct Telugu (leave blank if the line above is right):


44. `advice_candidates_title`

   English: You could consider

   Telugu, as written, unreviewed: మీరు పరిశీలించవచ్చు

   Correct Telugu (leave blank if the line above is right):


45. `answer_title`

   English: Answer

   Telugu, as written, unreviewed: జవాబు

   Correct Telugu (leave blank if the line above is right):


46. `answer_refused`

   English: I can\'t put a number or a judgement on that. Here is what your diary shows.

   Telugu, as written, unreviewed: దానిపై నేను ఒక సంఖ్య గానీ తీర్పు గానీ చెప్పలేను. మీ డైరీ చూపిస్తున్నది ఇదిగో.

   Correct Telugu (leave blank if the line above is right):


47. `answer_from_diary`

   English: From your diary

   Telugu, as written, unreviewed: మీ డైరీ నుండి

   Correct Telugu (leave blank if the line above is right):


### When the app is not sure what the person meant

It asks rather than guesses: was that a meal eaten, a question, a plate about to be eaten, or a request for what to eat. The four short lines are the four answers.

48. `ask_intent_question`

   English: Was that a meal you ate, a question, a plate you are about to eat, or a request for what to eat?

   Telugu, as written, unreviewed: అది మీరు తిన్న భోజనమా, ఒక ప్రశ్నా, ఇప్పుడు తినబోయే ప్లేటా, లేదా ఏమి తినాలో అడుగుతున్నారా?

   Correct Telugu (leave blank if the line above is right):


49. `intent_log`

   English: I ate this

   Telugu, as written, unreviewed: నేను ఇది తిన్నాను

   Correct Telugu (leave blank if the line above is right):


50. `intent_answer`

   English: A question

   Telugu, as written, unreviewed: ఒక ప్రశ్న

   Correct Telugu (leave blank if the line above is right):


51. `intent_suggest`

   English: About to eat this

   Telugu, as written, unreviewed: ఇది తినబోతున్నాను

   Correct Telugu (leave blank if the line above is right):


52. `intent_recommend`

   English: What should I eat

   Telugu, as written, unreviewed: నేను ఏమి తినాలి

   Correct Telugu (leave blank if the line above is right):


53. `intent_heading_log`

   English: Logging

   Telugu, as written, unreviewed: నమోదు చేస్తున్నాను

   Correct Telugu (leave blank if the line above is right):


54. `intent_heading_answer`

   English: Answering

   Telugu, as written, unreviewed: జవాబు ఇస్తున్నాను

   Correct Telugu (leave blank if the line above is right):


55. `intent_heading_suggest`

   English: Suggesting

   Telugu, as written, unreviewed: సూచిస్తున్నాను

   Correct Telugu (leave blank if the line above is right):


56. `intent_heading_recommend`

   English: Recommending

   Telugu, as written, unreviewed: సిఫారసు చేస్తున్నాను

   Correct Telugu (leave blank if the line above is right):


### When the plate could not be understood

Nothing was saved. The app says so and asks the person to say it another way.

57. `confirm_title`

   English: Not logged yet

   Telugu, as written, unreviewed: ఇంకా నమోదు కాలేదు

   Correct Telugu (leave blank if the line above is right):


58. `confirm_hint`

   English: Say it differently, or type it.

   Telugu, as written, unreviewed: వేరే విధంగా చెప్పండి, లేదా టైప్ చేయండి.

   Correct Telugu (leave blank if the line above is right):


### When something cannot be done

One sentence for each thing that can stop the app. Each states a limitation of the app or the phone, never a number, never a guess.

59. `unavailable_model_not_loaded`

   English: The model is not loaded on this phone yet.

   Telugu, as written, unreviewed: ఈ ఫోన్‌లో మోడల్ ఇంకా లోడ్ కాలేదు.

   Correct Telugu (leave blank if the line above is right):


60. `unavailable_model_load_failed`

   English: The model could not start.

   Telugu, as written, unreviewed: మోడల్ ప్రారంభం కాలేదు.

   Correct Telugu (leave blank if the line above is right):


61. `unavailable_insufficient_memory`

   English: Not enough memory free to do this now.

   Telugu, as written, unreviewed: ఇప్పుడు దీనికి సరిపడా మెమరీ ఖాళీగా లేదు.

   Correct Telugu (leave blank if the line above is right):


62. `unavailable_no_match`

   English: I do not know a food in that.

   Telugu, as written, unreviewed: అందులో నాకు తెలిసిన ఆహారం ఏదీ లేదు.

   Correct Telugu (leave blank if the line above is right):


63. `unavailable_known_item_no_data`

   English: I know that food and hold no figures for it.

   Telugu, as written, unreviewed: ఆ ఆహారం నాకు తెలుసు, కానీ దానికి అంకెలు నా దగ్గర లేవు.

   Correct Telugu (leave blank if the line above is right):


64. `unavailable_below_confidence_threshold`

   English: I am not sure enough of what you said.

   Telugu, as written, unreviewed: మీరు చెప్పింది నాకు సరిగ్గా అర్థం కాలేదు.

   Correct Telugu (leave blank if the line above is right):


65. `unavailable_permission_denied`

   English: A permission was not granted.

   Telugu, as written, unreviewed: ఒక అనుమతి ఇవ్వలేదు.

   Correct Telugu (leave blank if the line above is right):


66. `unavailable_hardware_unsupported`

   English: This phone cannot run this.

   Telugu, as written, unreviewed: ఈ ఫోన్ దీన్ని నడపలేదు.

   Correct Telugu (leave blank if the line above is right):


67. `unavailable_input_not_usable`

   English: That could not be used. Please try again.

   Telugu, as written, unreviewed: అది ఉపయోగించలేకపోయాను. దయచేసి మళ్ళీ ప్రయత్నించండి.

   Correct Telugu (leave blank if the line above is right):


68. `unavailable_cancelled`

   English: Cancelled.

   Telugu, as written, unreviewed: రద్దు చేయబడింది.

   Correct Telugu (leave blank if the line above is right):


69. `unavailable_schema_validation_failed`

   English: The reply could not be read. Please try again.

   Telugu, as written, unreviewed: జవాబు చదవలేకపోయాను. దయచేసి మళ్ళీ ప్రయత్నించండి.

   Correct Telugu (leave blank if the line above is right):


70. `unavailable_internal_error`

   English: Something went wrong inside the app.

   Telugu, as written, unreviewed: యాప్ లోపల ఏదో తప్పు జరిగింది.

   Correct Telugu (leave blank if the line above is right):


### When a part of the app does not exist yet

Shown instead of a made-up result. %1$s is the name of the missing part.

71. `not_built`

   English: Not built yet: %1$s

   Telugu, as written, unreviewed: ఇంకా నిర్మించలేదు: %1$s

   Correct Telugu (leave blank if the line above is right):


### The Scan screen, for a printed lab report

The camera, then each value read off the report beside where it came from, with the range printed on the report if there was one. The person ticks what to save.

72. `scan_title`

   English: Scan a lab report

   Telugu, as written, unreviewed: ల్యాబ్ రిపోర్టు స్కాన్ చేయండి

   Correct Telugu (leave blank if the line above is right):


73. `scan_capture`

   English: Capture

   Telugu, as written, unreviewed: ఫోటో తీయండి

   Correct Telugu (leave blank if the line above is right):


74. `scan_retake`

   English: Retake

   Telugu, as written, unreviewed: మళ్ళీ తీయండి

   Correct Telugu (leave blank if the line above is right):


75. `scan_reading`

   English: Reading the report…

   Telugu, as written, unreviewed: రిపోర్టు చదువుతున్నాను…

   Correct Telugu (leave blank if the line above is right):


76. `scan_no_fields`

   English: No test values could be read. Try a sharper, straighter photo.

   Telugu, as written, unreviewed: పరీక్ష విలువలు ఏవీ చదవలేకపోయాను. మరింత స్పష్టమైన, నిటారుగా ఉన్న ఫోటో ప్రయత్నించండి.

   Correct Telugu (leave blank if the line above is right):


77. `scan_field_line`

   English: %1$s: %2$s %3$s

   Telugu, as written, unreviewed: %1$s: %2$s %3$s

   Correct Telugu (leave blank if the line above is right):


78. `scan_range_both`

   English: printed range %1$s to %2$s

   Telugu, as written, unreviewed: ముద్రించిన పరిధి %1$s నుండి %2$s

   Correct Telugu (leave blank if the line above is right):


79. `scan_range_high_only`

   English: printed range up to %1$s

   Telugu, as written, unreviewed: ముద్రించిన పరిధి %1$s వరకు

   Correct Telugu (leave blank if the line above is right):


80. `scan_range_low_only`

   English: printed range from %1$s

   Telugu, as written, unreviewed: ముద్రించిన పరిధి %1$s నుండి

   Correct Telugu (leave blank if the line above is right):


81. `scan_range_none`

   English: no range printed, so nothing will be judged against it

   Telugu, as written, unreviewed: పరిధి ముద్రించలేదు, కాబట్టి దేనితోనూ పోల్చడం జరగదు

   Correct Telugu (leave blank if the line above is right):


82. `scan_below_range`

   English: below the printed range

   Telugu, as written, unreviewed: ముద్రించిన పరిధి కంటే తక్కువ

   Correct Telugu (leave blank if the line above is right):


83. `scan_above_range`

   English: above the printed range

   Telugu, as written, unreviewed: ముద్రించిన పరిధి కంటే ఎక్కువ

   Correct Telugu (leave blank if the line above is right):


84. `scan_report_date`

   English: Report date %1$s

   Telugu, as written, unreviewed: రిపోర్టు తేదీ %1$s

   Correct Telugu (leave blank if the line above is right):


85. `scan_report_date_unknown`

   English: Report date not read

   Telugu, as written, unreviewed: రిపోర్టు తేదీ చదవలేదు

   Correct Telugu (leave blank if the line above is right):


86. `scan_save`

   English: Save %1$d values

   Telugu, as written, unreviewed: %1$d విలువలను సేవ్ చేయండి

   Correct Telugu (leave blank if the line above is right):


87. `scan_saved`

   English: Saved %1$d values from this report.

   Telugu, as written, unreviewed: ఈ రిపోర్టు నుండి %1$d విలువలు సేవ్ చేశాను.

   Correct Telugu (leave blank if the line above is right):


88. `camera_permission_needed`

   English: Camera permission is needed to scan a report.

   Telugu, as written, unreviewed: రిపోర్టు స్కాన్ చేయడానికి కెమెరా అనుమతి అవసరం.

   Correct Telugu (leave blank if the line above is right):


### The About screen

Where the app says it runs offline, where its numbers come from and which open-source parts it contains. The legal notices themselves are not translated.

89. `about_title`

   English: About

   Telugu, as written, unreviewed: గురించి

   Correct Telugu (leave blank if the line above is right):


90. `about_licences_title`

   English: Licences and notices

   Telugu, as written, unreviewed: లైసెన్సులు మరియు నోటీసులు

   Correct Telugu (leave blank if the line above is right):


91. `about_offline`

   English: IN2FIT runs entirely on this phone. The demo build has no internet permission: nothing you say, scan or log leaves the device.

   Telugu, as written, unreviewed: IN2FIT పూర్తిగా ఈ ఫోన్‌లోనే నడుస్తుంది. డెమో బిల్డ్‌కు ఇంటర్నెట్ అనుమతి లేదు: మీరు చెప్పేది, స్కాన్ చేసేది లేదా నమోదు చేసేది ఏదీ ఫోన్ బయటకు వెళ్ళదు.

   Correct Telugu (leave blank if the line above is right):


92. `about_notices_pending`

   English: The verbatim copyright notice for the voice models is being prepared and will appear here.

   Telugu, as written, unreviewed: వాయిస్ మోడల్స్ కాపీరైట్ నోటీసు యథాతథంగా సిద్ధం చేస్తున్నాము; అది ఇక్కడ కనిపిస్తుంది.

   Correct Telugu (leave blank if the line above is right):


93. `about_usda_title`

   English: Nutrition data

   Telugu, as written, unreviewed: పోషక విలువల డేటా

   Correct Telugu (leave blank if the line above is right):


### Lines the app writes for its own language model, not for the screen

Not shown on a screen. When the person asks a question, the app writes their own meals, lab values and diet into a few lines like these and gives them to its language model, in the person's language, before it answers; the answer may repeat them back. Plain and literal, no advice in them: every slot is a name, a number or a date the app fills in.

94. `context_figure`

   English: %1$s: %2$s %3$s

   Note: %1$s a nutrient word, %2$s a number, %3$s its unit (g, mg, kcal). A line like 'iron: 4 mg'. Keep it that short.

   Telugu, as written, unreviewed: %1$s: %2$s %3$s

   Correct Telugu (leave blank if the line above is right):


95. `context_figure_partial`

   English: %1$s: at least %2$s %3$s (no value for %4$s)

   Note: Same, when some foods in the meal had no value: %4$s is the names of those foods.

   Telugu, as written, unreviewed: %1$s: కనీసం %2$s %3$s (%4$sకు విలువ లేదు)

   Correct Telugu (leave blank if the line above is right):


96. `context_figure_none`

   English: %1$s: not known

   Note: %1$s a nutrient word. The app has no value for it.

   Telugu, as written, unreviewed: %1$s: తెలియదు

   Correct Telugu (leave blank if the line above is right):


97. `context_meal`

   English: %1$s: %2$s. %3$s

   Note: %1$s the time of the meal, %2$s the foods, %3$s the figures. Just the slots and the punctuation between them.

   Telugu, as written, unreviewed: %1$s: %2$s. %3$s

   Correct Telugu (leave blank if the line above is right):


98. `context_period`

   English: %1$s: %2$s

   Note: %1$s a period (one of the two lines below), %2$s the figures for it.

   Telugu, as written, unreviewed: %1$s: %2$s

   Correct Telugu (leave blank if the line above is right):


99. `context_period_today`

   English: Today so far

   Telugu, as written, unreviewed: ఈరోజు ఇప్పటివరకు

   Correct Telugu (leave blank if the line above is right):


100. `context_period_last_seven_days`

   English: The last seven days

   Telugu, as written, unreviewed: గత ఏడు రోజులు

   Correct Telugu (leave blank if the line above is right):


101. `context_lab`

   English: %1$s: %2$s %3$s (report dated %4$s)

   Note: %1$s the test's name as printed, %2$s the value, %3$s its unit, %4$s the report's date.

   Telugu, as written, unreviewed: %1$s: %2$s %3$s (%4$s తేదీ రిపోర్టు)

   Correct Telugu (leave blank if the line above is right):


102. `context_lab_with_range`

   English: %1$s: %2$s %3$s, printed range %4$s to %5$s (report dated %6$s)

   Note: Same, plus %4$s and %5$s the low and high limits printed on the report, and %6$s the date.

   Telugu, as written, unreviewed: %1$s: %2$s %3$s, రిపోర్టులో ముద్రించిన పరిధి %4$s నుండి %5$s (%6$s తేదీ రిపోర్టు)

   Correct Telugu (leave blank if the line above is right):


103. `context_never_suggest_vegetarian`

   English: meat, fish or eggs (vegetarian)

   Note: Completes 'never suggest ...'. The word in brackets is the diet as the person named it.

   Telugu, as written, unreviewed: మాంసం, చేపలు లేదా గుడ్లు (శాకాహారి)

   Correct Telugu (leave blank if the line above is right):


104. `context_never_suggest_vegan`

   English: meat, fish, eggs, milk or any dairy (vegan)

   Telugu, as written, unreviewed: మాంసం, చేపలు, గుడ్లు, పాలు లేదా ఏ పాల ఉత్పత్తులు (వీగన్)

   Correct Telugu (leave blank if the line above is right):


105. `context_never_suggest_eggetarian`

   English: meat or fish (eggetarian)

   Telugu, as written, unreviewed: మాంసం లేదా చేపలు (గుడ్లు తినే శాకాహారి)

   Correct Telugu (leave blank if the line above is right):


106. `context_never_suggest_jain`

   English: meat, fish, eggs, onion, garlic or root vegetables (Jain)

   Telugu, as written, unreviewed: మాంసం, చేపలు, గుడ్లు, ఉల్లిపాయ, వెల్లుల్లి లేదా దుంప కూరగాయలు (జైన్)

   Correct Telugu (leave blank if the line above is right):


107. `context_referral`

   English: That is a question for a doctor, who can look at it with you.

   Telugu, as written, unreviewed: అది డాక్టర్‌ను అడగవలసిన ప్రశ్న; వారు దాన్ని మీతో కలిసి చూడగలరు.

   Correct Telugu (leave blank if the line above is right):


### Spoken while the app works

The app says one of these aloud while it is thinking, so a ten-second wait sounds like work and not like silence. One or two seconds long when spoken. No health content.

108. `tts_lead_in_log`

   English: Noting that down.

   Telugu, as written, unreviewed: నోట్ చేసుకుంటున్నాను.

   Correct Telugu (leave blank if the line above is right):


109. `tts_lead_in_answer`

   English: Let me check your records.

   Telugu, as written, unreviewed: మీ రికార్డులు చూస్తాను.

   Correct Telugu (leave blank if the line above is right):


110. `tts_lead_in_suggest`

   English: Let me think about what fits.

   Telugu, as written, unreviewed: ఏది సరిపోతుందో ఆలోచిస్తాను.

   Correct Telugu (leave blank if the line above is right):


111. `tts_lead_in_recommend`

   English: Let me see what suits you.

   Telugu, as written, unreviewed: మీకు ఏది సరిపడుతుందో చూస్తాను.

   Correct Telugu (leave blank if the line above is right):


## PART 3. THE REST OF THE SCREEN TEXT

Labels, explanations and the words dropped into sentences. Same rules.

### Nutrient words

Single words dropped into the sentences above and shown next to figures, so they should read naturally mid-sentence.

112. `nutrient_energy`

   English: energy

   Telugu, as written, unreviewed: శక్తి

   Correct Telugu (leave blank if the line above is right):


113. `nutrient_protein`

   English: protein

   Telugu, as written, unreviewed: ప్రోటీన్

   Correct Telugu (leave blank if the line above is right):


114. `nutrient_carbohydrate`

   English: carbohydrate

   Telugu, as written, unreviewed: కార్బోహైడ్రేట్

   Correct Telugu (leave blank if the line above is right):


115. `nutrient_fat`

   English: fat

   Telugu, as written, unreviewed: కొవ్వు

   Correct Telugu (leave blank if the line above is right):


116. `nutrient_fibre`

   English: fibre

   Telugu, as written, unreviewed: పీచు

   Correct Telugu (leave blank if the line above is right):


117. `nutrient_iron`

   English: iron

   Telugu, as written, unreviewed: ఇనుము

   Correct Telugu (leave blank if the line above is right):


118. `nutrient_vitamin_b12`

   English: vitamin B12

   Telugu, as written, unreviewed: విటమిన్ B12

   Correct Telugu (leave blank if the line above is right):


119. `nutrient_sodium`

   English: sodium

   Telugu, as written, unreviewed: సోడియం

   Correct Telugu (leave blank if the line above is right):


### Living-situation phrases

Dropped into the sentence 'Suggestions are limited to what is realistic for ...' in place of the slot, so each phrase should complete that sentence.

120. `life_context_hostel_student`

   English: hostel and canteen food

   Telugu, as written, unreviewed: హాస్టల్ మరియు క్యాంటీన్ ఆహారం

   Correct Telugu (leave blank if the line above is right):


121. `life_context_pg_own_cooking`

   English: cooking for yourself with limited time

   Telugu, as written, unreviewed: తక్కువ సమయంలో మీ కోసం మీరు వండుకోవడం

   Correct Telugu (leave blank if the line above is right):


122. `life_context_field_or_manual_worker`

   English: long physical shifts and eating out

   Telugu, as written, unreviewed: ఎక్కువసేపు శారీరక పని చేయడం మరియు బయట తినడం

   Correct Telugu (leave blank if the line above is right):


123. `life_context_desk_professional`

   English: a desk day with a full kitchen

   Telugu, as written, unreviewed: డెస్క్ వద్ద పని చేసే రోజు మరియు పూర్తి వంటగది

   Correct Telugu (leave blank if the line above is right):


124. `life_context_homemaker`

   English: cooking for the household

   Telugu, as written, unreviewed: ఇంటివారి కోసం వంట చేయడం

   Correct Telugu (leave blank if the line above is right):


### Language choice

The heading of the screen where the person picks Telugu, Hindi or English.

125. `language_picker_title`

   English: Language

   Telugu, as written, unreviewed: భాష

   Correct Telugu (leave blank if the line above is right):


### The confidence label

A one-word label next to every nutrition figure saying how far to trust it. Good means the food and the amount were both clear; Approximate means something was assumed, such as a standard bowl size; Rough means the figure could be far off.

126. `confidence_band_good`

   English: Good

   Telugu, as written, unreviewed: మంచిది

   Correct Telugu (leave blank if the line above is right):


127. `confidence_band_approximate`

   English: Approximate

   Telugu, as written, unreviewed: సుమారు

   Correct Telugu (leave blank if the line above is right):


128. `confidence_band_rough`

   English: Rough

   Telugu, as written, unreviewed: అంచనా

   Correct Telugu (leave blank if the line above is right):


### Why the label says what it says

Shown when the person taps the confidence label. One sentence explaining it.

129. `confidence_reason_exact_food_match`

   English: Matched exactly to a food in the database.

   Telugu, as written, unreviewed: డేటాబేస్‌లోని ఆహారంతో ఖచ్చితంగా సరిపోలింది.

   Correct Telugu (leave blank if the line above is right):


130. `confidence_reason_fuzzy_food_match`

   English: Matched to the closest name in the database; check it is the food you meant.

   Telugu, as written, unreviewed: డేటాబేస్‌లోని దగ్గరగా సరిపోలే పేరుతో సరిపోలింది; మీరు ఉద్దేశించిన ఆహారమేనా చూసుకోండి.

   Correct Telugu (leave blank if the line above is right):


131. `confidence_reason_category_level_match`

   English: Matched only to a food category, not a specific food.

   Telugu, as written, unreviewed: నిర్దిష్ట ఆహారంతో కాకుండా, ఆహార వర్గంతో మాత్రమే సరిపోలింది.

   Correct Telugu (leave blank if the line above is right):


132. `confidence_reason_quantity_stated`

   English: You gave the quantity in a unit we can convert.

   Telugu, as written, unreviewed: మీరు మార్చగలిగే కొలతలో పరిమాణాన్ని చెప్పారు.

   Correct Telugu (leave blank if the line above is right):


133. `confidence_reason_household_unit_default`

   English: A household measure was converted with a standard weight. Tap to correct the grams.

   Note: 'Household measure' means a katori, glass, spoon or plate rather than grams.

   Telugu, as written, unreviewed: ఇంట్లో ఉపయోగించే కొలతను ప్రామాణిక బరువుతో మార్చాం. గ్రాములను సరిచేయడానికి నొక్కండి.

   Correct Telugu (leave blank if the line above is right):


134. `confidence_reason_quantity_inferred`

   English: You did not say how much, so this quantity is a guess. Tap to correct it.

   Telugu, as written, unreviewed: మీరు ఎంత పరిమాణం చెప్పలేదు, కాబట్టి ఈ పరిమాణం అంచనా మాత్రమే. దాన్ని సరిచేయడానికి నొక్కండి.

   Correct Telugu (leave blank if the line above is right):


135. `confidence_reason_authored_reference_recipe`

   English: Based on a reference recipe. Edit it to match how you cook.

   Note: 'Reference recipe' is the app's own standard recipe for a dish such as sambar, which the person can edit to match their kitchen.

   Telugu, as written, unreviewed: ఒక ప్రామాణిక వంటకం ఆధారంగా తీసుకున్నది. మీరు ఎలా వండుతారో దానికి అనుగుణంగా మార్చండి.

   Correct Telugu (leave blank if the line above is right):


136. `confidence_reason_user_edited_recipe`

   English: Based on the recipe as you edited it.

   Telugu, as written, unreviewed: మీరు సవరించిన వంటకం ఆధారంగా తీసుకున్నది.

   Correct Telugu (leave blank if the line above is right):


137. `confidence_reason_weak_source_record`

   English: The source record for this food rests on few samples or on a label.

   Telugu, as written, unreviewed: ఈ ఆహారానికి సంబంధించిన మూల రికార్డు కొన్ని నమూనాలు లేదా ఒక లేబుల్‌పై ఆధారపడి ఉంది.

   Correct Telugu (leave blank if the line above is right):


138. `confidence_reason_substitute_food_record`

   English: The nearest record is a similar food, not this one.

   Telugu, as written, unreviewed: దగ్గరగా సరిపోలిన రికార్డు ఇదే ఆహారం కాదు, ఇలాంటి ఆహారానికి సంబంధించినది.

   Correct Telugu (leave blank if the line above is right):


139. `confidence_reason_low_asr_confidence`

   English: Speech recognition was unsure of what it heard. Check the words.

   Note: 'Speech recognition' is the part that turns what the person said into words.

   Telugu, as written, unreviewed: మీరు చెప్పింది ఏమిటో వాయిస్ గుర్తింపు సరిగ్గా నిర్ధారించలేకపోయింది. పదాలను చూసుకోండి.

   Correct Telugu (leave blank if the line above is right):


140. `confidence_reason_uncorrected_camera_guess`

   English: A camera guess that has not been confirmed by you.

   Telugu, as written, unreviewed: కెమెరా ద్వారా గుర్తించిన అంచనా ఇంకా మీరు నిర్ధారించలేదు.

   Correct Telugu (leave blank if the line above is right):


### The setup screen, behind a long-press, never seen by accident

A checklist screen for the team before a demo: every model present or absent, its size, whether it loads; permissions; the interface and speech languages. Not for the person using the app.

141. `preflight_title`

   English: Pre-flight check

   Telugu, as written, unreviewed: ప్రీ-ఫ్లైట్ తనిఖీ

   Correct Telugu (leave blank if the line above is right):


142. `preflight_intro`

   English: What this phone has and what it is missing, read now. Nothing here is estimated.

   Telugu, as written, unreviewed: ఈ ఫోన్‌లో ఏముందో, ఏది లేదో, ఇప్పుడు చదివినది. ఇక్కడ ఏదీ అంచనా కాదు.

   Correct Telugu (leave blank if the line above is right):


143. `preflight_app`

   English: App

   Telugu, as written, unreviewed: యాప్

   Correct Telugu (leave blank if the line above is right):


144. `preflight_device`

   English: Device

   Telugu, as written, unreviewed: పరికరం

   Correct Telugu (leave blank if the line above is right):


145. `preflight_locale`

   English: Locale

   Telugu, as written, unreviewed: భాషా సెట్టింగ్

   Correct Telugu (leave blank if the line above is right):


146. `preflight_mic`

   English: Microphone permission

   Telugu, as written, unreviewed: మైక్రోఫోన్ అనుమతి

   Correct Telugu (leave blank if the line above is right):


147. `preflight_camera`

   English: Camera permission

   Telugu, as written, unreviewed: కెమెరా అనుమతి

   Correct Telugu (leave blank if the line above is right):


148. `preflight_models_dir`

   English: Models folder

   Telugu, as written, unreviewed: మోడల్స్ ఫోల్డర్

   Correct Telugu (leave blank if the line above is right):


149. `preflight_free_space`

   English: Free space

   Telugu, as written, unreviewed: ఖాళీ స్థలం

   Correct Telugu (leave blank if the line above is right):


150. `preflight_voices`

   English: Voices available

   Telugu, as written, unreviewed: అందుబాటులో ఉన్న వాయిస్‌లు

   Correct Telugu (leave blank if the line above is right):


151. `preflight_residency`

   English: Model memory

   Telugu, as written, unreviewed: మోడల్ మెమరీ

   Correct Telugu (leave blank if the line above is right):


152. `preflight_models_title`

   English: Models

   Telugu, as written, unreviewed: మోడల్స్

   Correct Telugu (leave blank if the line above is right):


153. `preflight_present`

   English: Present, %1$s

   Telugu, as written, unreviewed: ఉంది, %1$s

   Correct Telugu (leave blank if the line above is right):


154. `preflight_absent`

   English: MISSING

   Telugu, as written, unreviewed: లేదు

   Correct Telugu (leave blank if the line above is right):


155. `preflight_load`

   English: Load

   Telugu, as written, unreviewed: లోడ్ చేయి

   Correct Telugu (leave blank if the line above is right):


156. `preflight_loading`

   English: Loading…

   Telugu, as written, unreviewed: లోడ్ అవుతోంది…

   Correct Telugu (leave blank if the line above is right):


157. `preflight_refresh`

   English: Refresh

   Telugu, as written, unreviewed: రిఫ్రెష్

   Correct Telugu (leave blank if the line above is right):


158. `preflight_yes`

   English: granted

   Telugu, as written, unreviewed: ఇవ్వబడింది

   Correct Telugu (leave blank if the line above is right):


159. `preflight_no`

   English: NOT granted

   Telugu, as written, unreviewed: ఇవ్వలేదు

   Correct Telugu (leave blank if the line above is right):


### The scripted-feed banner

A banner the team switches on to feed the app a scripted sentence during a rehearsal, and its switch. Not for the person using the app.

160. `demo_switch_title`

   English: Scripted demo feed

   Telugu, as written, unreviewed: స్క్రిప్ట్ చేసిన డెమో ఫీడ్

   Correct Telugu (leave blank if the line above is right):


161. `demo_switch_hint`

   English: Feeds the screens scripted events instead of the real pipeline. For building and photographing screens only. Every figure it shows is script.

   Telugu, as written, unreviewed: నిజమైన పైప్‌లైన్‌కు బదులుగా స్క్రీన్‌లకు స్క్రిప్ట్ చేసిన ఈవెంట్‌లను ఇస్తుంది. స్క్రీన్‌లను నిర్మించడానికి, ఫోటో తీయడానికి మాత్రమే. ఇది చూపించే ప్రతి అంకె స్క్రిప్ట్ మాత్రమే.

   Correct Telugu (leave blank if the line above is right):


162. `demo_banner`

   English: SCRIPTED DEMO FEED. Nothing on this screen is a real result.

   Telugu, as written, unreviewed: స్క్రిప్ట్ చేసిన డెమో ఫీడ్. ఈ స్క్రీన్‌లో ఏదీ నిజమైన ఫలితం కాదు.

   Correct Telugu (leave blank if the line above is right):


163. `demo_absent`

   English: Not in this build. The demo build carries no scripted feed.

   Telugu, as written, unreviewed: ఈ బిల్డ్‌లో లేదు. డెమో బిల్డ్‌లో స్క్రిప్ట్ చేసిన ఫీడ్ లేదు.

   Correct Telugu (leave blank if the line above is right):


### TEMPORARY: the build-status screen

A developer screen listing what is built. It will be replaced before the demo. Lowest priority: do these last, or skip them.

164. `status_screen_subtitle`

   English: Phase 1B scaffold. Contracts are defined; no pipeline is built yet.

   Telugu, as written, unreviewed: ఫేజ్ 1B ప్రాథమిక నిర్మాణం. అవసరమైన ఒప్పందాలు నిర్వచించబడ్డాయి; ఇంకా ఏ పైప్‌లైన్ నిర్మించబడలేదు.

   Correct Telugu (leave blank if the line above is right):


165. `status_line`

   English: %1$s: %2$s

   Note: A format only. %1$s is the name of a feature and %2$s is its state, so the Telugu is just those two slots in the right order with whatever goes between them.

   Telugu, as written, unreviewed: %1$s: %2$s

   Correct Telugu (leave blank if the line above is right):


166. `state_not_implemented`

   English: Not implemented

   Telugu, as written, unreviewed: ఇంకా అమలు చేయలేదు

   Correct Telugu (leave blank if the line above is right):


167. `pipeline_voice_logging`

   English: Voice logging (ASR, LLM extract, TTS)

   Telugu, as written, unreviewed: వాయిస్ లాగింగ్ (ASR, LLM ఎక్స్‌ట్రాక్ట్, TTS)

   Correct Telugu (leave blank if the line above is right):


168. `pipeline_nutrition_lookup`

   English: Nutrition lookup

   Telugu, as written, unreviewed: పోషక విలువల శోధన

   Correct Telugu (leave blank if the line above is right):


169. `pipeline_timeline_query`

   English: Timeline and voice query

   Telugu, as written, unreviewed: టైమ్‌లైన్ మరియు వాయిస్ ప్రశ్న

   Correct Telugu (leave blank if the line above is right):


170. `pipeline_lab_report_scan`

   English: Lab report scan

   Telugu, as written, unreviewed: ల్యాబ్ రిపోర్ట్ స్కాన్

   Correct Telugu (leave blank if the line above is right):


171. `pipeline_adaptive_suggestions`

   English: Adaptive suggestions

   Telugu, as written, unreviewed: అనుకూల సూచనలు

   Correct Telugu (leave blank if the line above is right):


172. `pipeline_dish_first_guess`

   English: Camera dish first guess

   Telugu, as written, unreviewed: కెమెరాతో వంటకం మొదటి అంచనా

   Correct Telugu (leave blank if the line above is right):


173. `pipeline_exercise_form`

   English: Exercise form check

   Telugu, as written, unreviewed: వ్యాయామం చేసే విధానం తనిఖీ

   Correct Telugu (leave blank if the line above is right):


### UNPLACED: ask Vedant where this appears

174. `speak_in_label`

   English: Speak in

   Telugu, as written, unreviewed: ఈ భాషలో మాట్లాడండి

   Correct Telugu (leave blank if the line above is right):


175. `plate_unit_taken_as`

   English: taken as %1$s g

   Telugu, as written, unreviewed: %1$s గ్రాములుగా తీసుకున్నాం

   Correct Telugu (leave blank if the line above is right):


176. `stop_speaking`

   English: Stop

   Telugu, as written, unreviewed: ఆపు

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

English words on the same list, for completeness: `ate`, `had`, `drank`, `eaten`, `finished`, `used`, `log`, `record`,
and the phrases `breakfast was`, `lunch was`, `dinner was`, `snack was`, `tiffin was`.

Devanagari, as the Hindi recogniser writes them (Vedant reads Devanagari; these are for him):
ate/drank `खाया`, `खाई`, `खाए`, `खायी`, `खाये`, `पिया`, `पी`. A number or a household unit beside a
food, with nothing that looks like a question, also counts as a meal: `एक दो तीन चार पाँच पांच छह
सात आठ नौ दस आधा आधी कटोरी कटोरा प्लेट गिलास ग्लास कप चम्मच एमएल ग्राम`. Hindi question and
advice words the app treats as "not a meal": `क्या कितना कितनी कितने कैसे कैसा कौन कौनसा कौनसी कब
कहाँ कहां क्यों क्यूं चाहिए चाहिये करूँ करूं करू सकता सकती सकते हूँ हूं रहा रही रहे अभी बताओ
बताइए बताइये अच्छा बेहतर सही ठीक सुझाव ऐड जोड़ बदल हटा डिलीट`, and the phrases `खा रहा`, `खा रही`,
`खा रहे`, `के लिए`, `खाना चाहिए`.

**Words that say a food was NOT eaten.** A sentence with one of these is never written into the
diary, whatever else it contains ("no rice today", "didn't eat lunch"); the one exception is a
correction of a count, "two rotis no, three rotis". The same question as above, the other way
round: does each of these mean *not* / *did not eat*, and could it mean something else?

| # | Word | Meant as | Means not / did not eat? (yes/no) | Could mean something else? | Missing form? |
| --- | --- | --- | --- | --- | --- |
| N1 | `nahi` | Hindi, not / no | | | |
| N2 | `nahin` | Hindi, not, long spelling | | | |
| N3 | `nai` | Hindi, not, short spelling (also "new"?) | | | |
| N4 | `nahee` | Hindi, not, long-vowel spelling | | | |
| N5 | `mat` | Hindi, don't | | | |
| N6 | `bina` | Hindi, without | | | |
| N7 | `ledu` | Telugu, is not / did not | | | |
| N8 | `ledhu` | Telugu, is not, dh- spelling | | | |
| N9 | `kadu` | Telugu, not (that) | | | |
| N10 | `kaadu` | Telugu, not, long-vowel spelling | | | |
| N11 | `tinaledu` | Telugu, did not eat | | | |
| N12 | `thinaledu` | Telugu, did not eat, th- spelling | | | |

English on the same list: `no`, `not`, `never`, `nothing`, `none`, `without`, `skip`, `skipped`,
`skipping`, and the stems of "didn't", "don't", "doesn't", "hadn't", "haven't", "wasn't", "couldn't"
(`didn` `didnt` `don` `dont` `doesn` `doesnt` `hadn` `hadnt` `haven` `havent` `wasn` `wasnt` `couldn` `couldnt`).
Devanagari, for Vedant: `नहीं`, `नही`, `बिना`, `छोड़`, `छोड़ा`, `छोड़ी`. Telugu script, generated, unreviewed:
`లేదు`, `కాదు`, `తినలేదు`.

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


#### Third list: food and unit names in Telugu and Hindi script

These are what the app expects the speech recogniser to write when a person says an English
food word the Indian way ("paneer", "bread", "one glass"), plus the ordinary Telugu and Hindi
words for the same things. Every one was GENERATED, most from the recogniser's own output on a
synthetic voice (`logs/asr-codemix-renderings.log`), by someone who does not read either script.
A wrong one here logs the wrong food, so please check each: does it say what the third column
says, and would a person actually write or say it that way? Cross out any that are wrong or
nonsense.

**A garbled row needs a yes only if a real speaker could plausibly produce it.** The rows marked
"as the model garbled it" are deliberate copies of a machine's mistakes, kept so that the app
copes with them. You are not obliged to fix nonsense: a no is a complete answer, and you do not
need to write what it should have been.

| # | Written | Script | Meant as | Points to | Origin | OK? |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `మిల్క్` | te | milk (English word) | milk | model log, said | |
| 2 | `మిలిచ` | te | milk, as the model garbled it | milk | model log, emitted | |
| 3 | `ఎగ్` | te | egg | egg | model log, said | |
| 4 | `ఎది` | te | egg, as the model garbled it | egg | model log, emitted | |
| 5 | `బాయిల్డ్ ఎగ్` | te | boiled egg | egg | model log, said | |
| 6 | `బాయిల్డే ఎగ` | te | boiled egg, as the model garbled it | egg | model log, emitted | |
| 7 | `బ్రెడ్` | te | bread | white bread | model log | |
| 8 | `బ్రెడ్డు` | te | bread, with a final u | white bread | generated | |
| 9 | `బ్రౌన్ బ్రెడ్` | te | brown bread | whole-wheat bread | generated | |
| 10 | `రైస్` | te | rice | cooked rice | model log | |
| 11 | `చికెన్` | te | chicken | chicken | model log | |
| 12 | `కర్డ్` | te | curd | curd | model log, said | |
| 13 | `కడ్డ` | te | curd, as the model garbled it | curd | model log, emitted | |
| 14 | `కర్డు` | te | curd, with a final u | curd | generated | |
| 15 | `కాఫీ` | te | coffee | coffee with milk and sugar | model log | |
| 16 | `కాపీ` | te | coffee, p spelling | coffee with milk and sugar | generated | |
| 17 | `బ్లాక్ కాఫీ` | te | black coffee | black coffee | generated | |
| 18 | `టీ` | te | tea | chai | model log, said | |
| 19 | `తీ` | te | tea, as the model garbled it | chai | model log, emitted | |
| 20 | `చాయ్` | te | chai | chai | generated | |
| 21 | `చాయ` | te | chai, without the final virama | chai | generated | |
| 22 | `బ్లాక్ టీ` | te | black tea | black tea | generated | |
| 23 | `జ్యూస్` | te | juice | juice (assumed orange) | model log | |
| 24 | `జూస్` | te | juice, short spelling | juice (assumed orange) | generated | |
| 25 | `ఆరెంజ్ జ్యూస్` | te | orange juice | orange juice | generated | |
| 26 | `మామిడి జ్యూస్` | te | mango juice | mango juice | generated | |
| 27 | `బిస్కెట్` | te | biscuit | Marie biscuit | model log, said | |
| 28 | `బిస్కెడి` | te | biscuit, as the model garbled it | Marie biscuit | model log, emitted | |
| 29 | `బిస్కట్` | te | biscuit, short spelling | Marie biscuit | generated | |
| 30 | `బనానా` | te | banana (English word) | ripe banana | model log | |
| 31 | `అరటిపండు` | te | ripe banana (Telugu word) | ripe banana | generated | |
| 32 | `అరటి పండు` | te | ripe banana, two words | ripe banana | generated | |
| 33 | `ఆపిల్` | te | apple | apple | model log, said | |
| 34 | `ఆపిలి` | te | apple, as the model garbled it | apple | model log, emitted | |
| 35 | `యాపిల్` | te | apple, ya spelling | apple | generated | |
| 36 | `చపాతీ` | te | chapati | chapati | model log | |
| 37 | `రోటీ` | te | roti | chapati | model log | |
| 38 | `దాల్` | te | dal (Hindi word) | dal tadka | model log | |
| 39 | `దాలు` | te | dal, with a final u | dal tadka | generated | |
| 40 | `పనీర్` | te | paneer | REFUSED by name: no honest record | model log, said | |
| 41 | `పనీరు` | te | paneer, as the model rendered it | REFUSED by name | model log, emitted | |
| 42 | `బటర్` | te | butter | butter | model log, said | |
| 43 | `బటరు` | te | butter, as the model rendered it | butter | model log, emitted | |
| 44 | `చీజ్` | te | cheese | processed cheese | model log, said | |
| 45 | `చీరి` | te | cheese, as the model garbled it | processed cheese | model log, emitted | |
| 46 | `చీజు` | te | cheese, with a final u | processed cheese | generated | |
| 47 | `షుగర్` | te | sugar | sugar | model log, said | |
| 48 | `షుగరు` | te | sugar, as the model rendered it | sugar | model log, emitted | |
| 49 | `ఆయిల్` | te | oil | groundnut oil (the default oil) | model log, said | |
| 50 | `ఆయలు` | te | oil, as the model garbled it | groundnut oil | model log, emitted | |
| 51 | `వాటర్` | te | water | water | model log, said | |
| 52 | `వాటరు` | te | water, as the model rendered it | water | model log, emitted | |
| 53 | `నీళ్లు` | te | water (Telugu word) | water | generated | |
| 54 | `నీళ్ళు` | te | water, alternate spelling | water | generated | |
| 55 | `ఫ్రైడ్ రైస్` | te | fried rice | REFUSED by name: no recipe yet | model log, said | |
| 56 | `ఫైడ్ రైస్` | te | fried rice, as the model garbled it | REFUSED by name | model log, emitted | |
| 57 | `గ్లాస్` | te | glass (unit) | a glass, 200 g | model log | |
| 58 | `గ్లాసు` | te | glass, with a final u | a glass, 200 g | generated | |
| 59 | `కప్` | te | cup (unit) | a cup | generated | |
| 60 | `కప్పు` | te | cup, Telugu form | a cup | generated | |
| 61 | `కప్స్` | te | cups | a cup | model log, said | |
| 62 | `కత్స్` | te | cups, as the model garbled it | a cup | model log, emitted | |
| 63 | `కటోరీ` | te | katori (unit) | a katori | generated | |
| 64 | `గిన్నె` | te | small bowl (Telugu word) | a katori | generated | |
| 65 | `బౌల్` | te | bowl | a bowl | generated | |
| 66 | `ప్లేట్` | te | plate | a plate | generated | |
| 67 | `ప్లేటు` | te | plate, with a final u | a plate | generated | |
| 68 | `స్పూన్` | te | spoon | a serving spoon | generated | |
| 69 | `స్పూను` | te | spoon, with a final u | a serving spoon | generated | |
| 70 | `చెంచా` | te | spoon (Telugu word) | a serving spoon | generated | |
| 71 | `టేబుల్ స్పూన్` | te | tablespoon | a tablespoon | generated | |
| 72 | `టీ స్పూన్` | te | teaspoon | a teaspoon | generated | |
| 73 | `టీస్పూన్` | te | teaspoon, one word | a teaspoon | generated | |
| 74 | `స్లైస్` | te | slice (unit) | one slice of bread, 29 g | generated | |
| 75 | `ముక్క` | te | piece (Telugu word) | one biscuit | generated | |
| 76 | `టుకడా` | te | piece (Hindi word in Telugu script) | one biscuit | generated | |
| 77 | `मिल्क` | hi | milk (English word) | milk | generated | |
| 78 | `उबला अंडा` | hi | boiled egg | egg | generated | |
| 79 | `एग` | hi | egg (English word) | egg | generated | |
| 80 | `राइस` | hi | rice (English word) | cooked rice | generated | |
| 81 | `चिकन` | hi | chicken | chicken | generated | |
| 82 | `बटर` | hi | butter (English word) | butter | generated | |
| 83 | `शुगर` | hi | sugar (English word) | sugar | generated | |
| 84 | `तेल` | hi | oil | groundnut oil | generated | |
| 85 | `ऑयल` | hi | oil (English word) | groundnut oil | generated | |
| 86 | `ब्रेड` | hi | bread | white bread | generated | |
| 87 | `डबल रोटी` | hi | bread | white bread | generated | |
| 88 | `ब्राउन ब्रेड` | hi | brown bread | whole-wheat bread | generated | |
| 89 | `चीज़` | hi | cheese | processed cheese | generated | |
| 90 | `चीज` | hi | cheese, without nukta | processed cheese | generated | |
| 91 | `बिस्कुट` | hi | biscuit | Marie biscuit | generated | |
| 92 | `बिस्किट` | hi | biscuit, alternate spelling | Marie biscuit | generated | |
| 93 | `केला` | hi | banana | ripe banana | generated | |
| 94 | `सेब` | hi | apple | apple | generated | |
| 95 | `एप्पल` | hi | apple (English word) | apple | generated | |
| 96 | `संतरे का जूस` | hi | orange juice | orange juice | generated | |
| 97 | `जूस` | hi | juice | juice (assumed orange) | generated | |
| 98 | `आम का जूस` | hi | mango juice | mango juice | generated | |
| 99 | `पानी` | hi | water | water | generated | |
| 100 | `काली चाय` | hi | black tea | black tea | generated | |
| 101 | `काली कॉफी` | hi | black coffee | black coffee | generated | |
| 102 | `चाय` | hi | tea | chai | generated | |
| 103 | `टी` | hi | tea (English word) | chai | generated | |
| 104 | `कॉफी` | hi | coffee | coffee with milk and sugar | generated | |
| 105 | `कॉफ़ी` | hi | coffee, with nukta | coffee with milk and sugar | generated | |
| 106 | `चपाती` | hi | chapati | chapati | generated | |
| 107 | `पनीर` | hi | paneer | REFUSED by name | generated | |
| 108 | `फ्राइड राइस` | hi | fried rice | REFUSED by name | generated | |
| 109 | `गिलास` | hi | glass (unit) | a glass | generated | |
| 110 | `ग्लास` | hi | glass (English word) | a glass | generated | |
| 111 | `कप` | hi | cup | a cup | generated | |
| 112 | `कटोरी` | hi | katori | a katori | generated | |
| 113 | `कटोरा` | hi | bowl | a bowl | generated | |
| 114 | `प्लेट` | hi | plate | a plate | generated | |
| 115 | `चम्मच` | hi | spoon | a serving spoon | generated | |
| 116 | `बड़ा चम्मच` | hi | tablespoon | a tablespoon | generated | |
| 117 | `छोटा चम्मच` | hi | teaspoon | a teaspoon | generated | |
| 118 | `स्लाइस` | hi | slice (unit) | one slice of bread | generated | |
| 119 | `टुकड़ा` | hi | piece | one biscuit | generated | |

Missing common forms (write them in):

______________________________________________________________________________

Send it back to Vedant. The lists live in `LogPrefilter.kt`, `SafetyLine.kt` and the `data-authoring` CSVs, and tests
keep this sheet in step with the log-word list, so a word you flag is changed in one place.

---

### Two things to listen to, and one sentence to write (from Meera, the voice)

This is about the app's VOICE, not its text. Vedant will send you a few short audio files with
this packet; they are named like `te-padmavathi-default-A.wav`. Each says the same two Telugu
words (ఇడ్లీ సాంబార్) in a different synthetic voice, and the files ending in `-B` say the same
five food names. The set is:

- `te-padmavathi-default-A.wav` and `te-venkatesh-default-A.wav`: two voices of the same kind
- `te-padmavathi-noise0.5_length1.15-A.wav`: the first voice, slowed a little and smoothed
- `te-mms-facebook-default-A.wav`: a third voice
- `te-pocket-tts-syspin_female-int4-OURS-A.wav`: a fourth voice, newer
- `platform-te-...-A.wav`, if present: the phone's own built-in Telugu voice

1. **Rank them.** Which sounds most like a person speaking Telugu, and which least? A rough order
   is enough: best to worst, by file name. If the phone's own built-in Telugu voice is among
   them, rank it too and say so.

2. **Write one sentence.** One ordinary Telugu sentence a person would actually say about a meal
   they ate, the kind of thing someone says to family. It becomes the test sentence every voice
   is judged on next; two food names is not a fair test of a voice, and nobody on the team can
   write that sentence.

   Sentence: ______________________________________________________________

---
