# 0037. A setting has one home

Date: 21 September 2026, morning. Status: accepted (Arjun's call under Vedant's "one home
only"; Vedant did not overrule). Affects the speech language, and every setting after it.

## The defect

On 21 September the language the person speaks to the app in had two homes: Jacob's ruling
(05:40) put `ProfileEntity.speech_language_tag` on the profile row, and the Talk screen kept
it in `SharedPreferences` ("talk"/"language", `5cd3fdc`) so a restart would come back in Hindi.
Two homes for one setting is how a demo switches to English at the worst moment: whichever
home the reader consulted last wins, and nobody can say which that was.

## The ruling

1. A fact ABOUT THE PERSON lives on the profile row and nowhere else: the language they speak,
   their name, weight, height, age, sex, activity, goal, life context, diet. `ProfileStore` is
   its one write path; first run and settings write through it; the row has no second copy
   anywhere, including a preference file.
2. A default is supplied by the ONE reader that owns it, never stored as if the person chose
   it: `ProfileStore.speechLanguage` reads Hindi for a null tag (Vedant, 21 Sep), and the row
   stays null until the person picks, so "never chosen" and "chose Hindi" remain different
   facts.
3. An APPEARANCE preference (the dark/cream scheme, a collapsed card) is not a fact about the
   person; a preference file is its one home. The rule forbids two homes, not preference files.
4. `SharedPreferences` "talk"/"language" is gone (`da67d04`). A device that had it comes back in
   Hindi, the default, which is what the demo needs.

## Measured

`TalkViewModelTest`: the language is Hindi with no row and with a row that has not chosen;
`setLanguage("te")` reaches the row; a new view-model over the same row reads Telugu.
`ProfileStoreTest`: the first save creates the row, a later save keeps what it does not touch,
a blank name is no name. On the device: queue items 11 and 36 (`b_item11`).
