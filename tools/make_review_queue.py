"""Writes the translation review queue for one language as a self-contained Markdown file.

    python tools/make_review_queue.py te      -> docs/localisation/telugu-review-queue.md
    python tools/make_review_queue.py hi      -> docs/localisation/hindi-review-queue.md

The sheet is for a fluent speaker who does not have the repo: every key still missing from
values-<tag>/strings.xml, with its English, where it appears in the app, and a blank line to
write on. Entries already present but marked REVIEW are listed too, with the current text, so the
reviewer can check them. Regenerate after the returned sheet has been pasted into the XML; the
count goes down. docs/decisions/0017.

Stdlib only. The XML is read with the same rule StringResourcesTest uses: a <string> preceded by
a comment containing REVIEW is unreviewed.
"""
import re
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
LANGUAGES = {"te": "Telugu", "hi": "Hindi"}

# Where each group of keys appears, in words a reviewer can picture. Order is PACKET order,
# ruled by Vedant on 20 Sep 2026: the packet ships once, and a reviewer with ten minutes reads
# the top and stops, so what the build depends on comes first. A key whose prefix is not here
# lands in a final "unplaced" section, so a new group of strings cannot be handed out without
# someone saying where it is shown.
PLACES = [
    ("safety_", "Advice screens",
     "A line at the bottom of every screen that gives advice. Always visible, cannot be closed."),
    ("trigger_", "The health sentences",
     "One of these is shown when something in the person's data changes what the app suggests: "
     "a lab report value, a condition they told the app about, what dominates a meal, their "
     "living situation, or a pattern over days. THESE MATTER MOST. Each must say only what the "
     "data says, never that the person has an illness, never what to take. The slots (%1$s and "
     "so on) are filled by the app with names and numbers; what each slot holds is in the note."),
    ("tab_", "The three tabs at the bottom of the screen", "One word each."),
    ("talk_", "The Talk screen, where the person speaks or types",
     "The main screen. The person says or types what they ate, or asks a question."),
    ("mic_", "The Talk screen, where the person speaks or types", ""),
    ("type_", "The Talk screen, where the person speaks or types", ""),
    ("send", "The Talk screen, where the person speaks or types", ""),
    ("said_by_", "The Talk screen, where the person speaks or types", ""),
    ("advise_", "The Talk screen, where the person speaks or types", ""),
    ("stage_", "Progress lines while the app works",
     "Shown one after another while the app is busy, instead of a spinner, so a ten-second "
     "wait reads as work. Each is a short phrase in the present tense: what it is doing now."),
    ("meal_", "A plate, after the app has understood it",
     "The foods as the person said them, with the figures. 'Logged' means written into their "
     "food history; the other line means the plate was only asked about."),
    ("figure_", "A plate, after the app has understood it", ""),
    ("advice_", "The reply",
     "What the app says back: a sentence from its rules, then foods a person could consider."),
    ("answer_", "The reply", ""),
    ("ask_intent", "When the app is not sure what the person meant",
     "It asks rather than guesses: was that a meal eaten, a question, a plate about to be "
     "eaten, or a request for what to eat. The four short lines are the four answers."),
    ("intent_", "When the app is not sure what the person meant", ""),
    ("confirm_", "When the plate could not be understood",
     "Nothing was saved. The app says so and asks the person to say it another way."),
    ("unavailable_", "When something cannot be done",
     "One sentence for each thing that can stop the app. Each states a limitation of the app "
     "or the phone, never a number, never a guess."),
    ("not_built", "When a part of the app does not exist yet",
     "Shown instead of a made-up result. %1$s is the name of the missing part."),
    ("scan_", "The Scan screen, for a printed lab report",
     "The camera, then each value read off the report beside where it came from, with the "
     "range printed on the report if there was one. The person ticks what to save."),
    ("camera_", "The Scan screen, for a printed lab report", ""),
    ("about_", "The About screen",
     "Where the app says it runs offline, where its numbers come from and which open-source "
     "parts it contains. The legal notices themselves are not translated."),
    ("context_", "Lines the app writes for its own language model, not for the screen",
     "Not shown on a screen. When the person asks a question, the app writes their own meals, "
     "lab values and diet into a few lines like these and gives them to its language model, in "
     "the person's language, before it answers; the answer may repeat them back. Plain and "
     "literal, no advice in them: every slot is a name, a number or a date the app fills in."),
    ("tts_lead_in_", "Spoken while the app works",
     "The app says one of these aloud while it is thinking, so a ten-second wait sounds like "
     "work and not like silence. One or two seconds long when spoken. No health content."),
    ("nutrient_", "Nutrient words",
     "Single words dropped into the sentences above and shown next to figures, so they should "
     "read naturally mid-sentence."),
    ("life_context_", "Living-situation phrases",
     "Dropped into the sentence 'Suggestions are limited to what is realistic for ...' in place "
     "of the slot, so each phrase should complete that sentence."),
    ("language_picker", "Language choice",
     "The heading of the screen where the person picks Telugu, Hindi or English."),
    ("confidence_band_", "The confidence label",
     "A one-word label next to every nutrition figure saying how far to trust it. "
     "Good means the food and the amount were both clear; Approximate means something was "
     "assumed, such as a standard bowl size; Rough means the figure could be far off."),
    ("confidence_reason_", "Why the label says what it says",
     "Shown when the person taps the confidence label. One sentence explaining it."),
    ("preflight_", "The setup screen, behind a long-press, never seen by accident",
     "A checklist screen for the team before a demo: every model present or absent, its size, "
     "whether it loads; permissions; the interface and speech languages. Not for the person "
     "using the app."),
    ("demo_", "The scripted-feed banner",
     "A banner the team switches on to feed the app a scripted sentence during a rehearsal, "
     "and its switch. Not for the person using the app."),
    ("status_", "TEMPORARY: the build-status screen",
     "A developer screen listing what is built. It will be replaced before the demo. Lowest "
     "priority: do these last, or skip them."),
    ("state_", "TEMPORARY: the build-status screen", ""),
    ("pipeline_", "TEMPORARY: the build-status screen", ""),
]

# The packet's parts. A prefix's part is decided here; everything not named is Part 3.
# Part 4 is assembled from files under docs/localisation/packet-extra/ (other sessions' sheets),
# appended verbatim, lowest priority.
PARTS = [
    (1, "PART 1. THE NINE SENTENCES THAT DECIDE WHETHER TELUGU SHIPS",
     "The safety line and the eight health sentences. If these nine are not confirmed by a "
     "fluent speaker before the demo build is made, the app ships without Telugu. If you only "
     "have ten minutes, do these nine and stop.",
     ("safety_", "trigger_")),
    (2, "PART 2. THE SCREENS THE DEMO SHOWS, AND THE NEW LINES THAT ARRIVED TODAY",
     "Written by a machine as a starting point so the app has no holes; every one needs your "
     "eye. Correct or confirm each. These are the words on screen during the demo.",
     ("tab_", "talk_", "mic_", "type_", "send", "said_by_", "advise_", "stage_", "meal_",
      "figure_", "advice_", "answer_", "ask_intent", "intent_", "confirm_", "unavailable_",
      "not_built", "scan_", "camera_", "about_", "context_", "tts_lead_in_")),
    (3, "PART 3. THE REST OF THE SCREEN TEXT",
     "Labels, explanations and the words dropped into sentences. Same rules.",
     ()),
]
EXTRA_DIR = "docs/localisation/packet-extra"
# Sheets that live where their owners keep them, included live so they cannot drift from the
# copy in the packet. Each is a different kind of question from the strings above.
EXTRA_FILES = [
    "data-authoring/log-words-review.md",     # Priya: the log words and the medicine words
]

# A word of context where the English uses a term the reviewer may not have met.
NOTES = {
    "confidence_reason_household_unit_default":
        "'Household measure' means a katori, glass, spoon or plate rather than grams.",
    "confidence_reason_authored_reference_recipe":
        "'Reference recipe' is the app's own standard recipe for a dish such as sambar, which "
        "the person can edit to match their kitchen.",
    "confidence_reason_low_asr_confidence":
        "'Speech recognition' is the part that turns what the person said into words.",
    "trigger_escalate_above_range":
        "%1$s the report's date, %2$s the test's name as printed, %3$s the value, %4$s its unit, "
        "%5$s the upper limit printed on the report. Shown when the value is far above it.",
    "trigger_escalate_below_range":
        "Same slots. Shown when the value is far below the lower limit printed on the report.",
    "trigger_lab_above_range":
        "Same slots as above; %5$s is the upper limit printed on the report.",
    "trigger_lab_below_range":
        "Same slots; %5$s is the lower limit printed on the report.",
    "trigger_declared_condition":
        "%1$s is the condition in the person's own words, exactly as they told the app.",
    "trigger_meal_composition":
        "%1$s a nutrient word (from the list below), %2$s the name of a food or dish, %3$s a "
        "whole number, the percentage.",
    "trigger_life_context":
        "%1$s is one of the living-situation phrases below.",
    "trigger_timeline":
        "%1$s a number of days, %2$s a short description the app supplies.",
    "context_figure":
        "%1$s a nutrient word, %2$s a number, %3$s its unit (g, mg, kcal). A line like "
        "'iron: 4 mg'. Keep it that short.",
    "context_figure_partial":
        "Same, when some foods in the meal had no value: %4$s is the names of those foods.",
    "context_figure_none":
        "%1$s a nutrient word. The app has no value for it.",
    "context_meal":
        "%1$s the time of the meal, %2$s the foods, %3$s the figures. Just the slots and the "
        "punctuation between them.",
    "context_period":
        "%1$s a period (one of the two lines below), %2$s the figures for it.",
    "context_lab":
        "%1$s the test's name as printed, %2$s the value, %3$s its unit, %4$s the report's date.",
    "context_lab_with_range":
        "Same, plus %4$s and %5$s the low and high limits printed on the report, and %6$s the "
        "date.",
    "context_never_suggest_vegetarian":
        "Completes 'never suggest ...'. The word in brackets is the diet as the person named it.",
    "status_line":
        "A format only. %1$s is the name of a feature and %2$s is its state, so the Telugu is "
        "just those two slots in the right order with whatever goes between them.",
}


def read_table(path: Path) -> dict:
    """key -> (text, translatable, needs_review), in file order, comments respected."""
    if not path.is_file():
        return {}
    # From <resources> on: the header comment above it is the rules, and mentions REVIEW.
    src = path.read_text(encoding="utf-8").split("<resources>", 1)[-1]
    out, pending = {}, False
    for m in re.finditer(r"<!--(.*?)-->|<string\s+([^>]*)>(.*?)</string>", src, re.S):
        if m.group(1) is not None:
            pending = pending or "REVIEW" in m.group(1)
            continue
        attrs, text = m.group(2), m.group(3)
        name = re.search(r'name="([^"]+)"', attrs).group(1)
        translatable = 'translatable="false"' not in attrs
        out[name] = (text.strip(), translatable, pending)
        pending = False
    return out


def place_of(key: str):
    for i, (prefix, section, where) in enumerate(PLACES):
        if key.startswith(prefix):
            return i, section, where
    return len(PLACES), "UNPLACED: ask Vedant where this appears", ""


def part_of(key: str):
    for number, title, blurb, prefixes in PARTS:
        if any(key.startswith(pre) for pre in prefixes):
            return number, title, blurb
    return PARTS[-1][0], PARTS[-1][1], PARTS[-1][2]


def main(tag: str) -> int:
    language = LANGUAGES[tag]
    default = read_table(RES / "values" / "strings.xml")
    local = read_table(RES / f"values-{tag}" / "strings.xml")
    keys = [k for k, (_, translatable, _) in default.items() if translatable]
    missing = [k for k in keys if k not in local]
    unreviewed = [k for k in keys if k in local and local[k][2]]
    # ONE list in packet order: parts first, then places, then the table's own order.
    todo = sorted(missing + unreviewed, key=lambda k: (part_of(k)[0], place_of(k)[0]))

    out = [f"# IN2FIT: {language} reviewer packet", ""]
    out += ["**If you only do one part, do Part 1.** Those nine lines decide whether the app "
            f"speaks {language} at all.", ""]
    out += [f"Generated {date.today():%d %B %Y} from the app's English string table. "
            f"{len(unreviewed)} lines to check, {len(missing)} to write. One packet, one trip: "
            "everything the team needs from you is in this file.", ""]
    out += ["Reviewer's name: ______________________", ""]
    out += ["## What the app is", "",
            "IN2FIT is a phone app for people in India who may not read English nutrition labels. "
            "A person says what they ate, in Telugu, Hindi or English; the app finds each food in "
            "its database and shows the nutrition, with a label saying how far to trust each "
            "figure. It can also read a printed lab report with the camera. It never diagnoses "
            "and never prescribes.", ""]
    out += ["## What we need from you", "",
            f"Most lines below already have {language} on them, WRITTEN BY A MACHINE. Nobody on the "
            "team can read it. Under each one is a line `Correct " + language + ":`. If the machine's "
            "line is right, leave that blank. If it is wrong, write the right line there. A line "
            f"with no {language} yet has a `{language}:` line to write on.", "",
            "1. Write it the way you would say it to a family member. Short: it goes on a phone screen.",
            "2. Keep the meaning exactly. Add no reassurance, no advice and no number. The English was "
            "written so that the app never says a person has a condition and never tells them what to "
            "take; the translation must not either.",
            "3. Anything like `%1$s` or `%2$s` is a slot the app fills in with a name or a number. Keep "
            f"it in the {language} sentence, wherever {language} needs it.",
            "4. If an English line is unclear or makes no sense to you, write that instead of guessing. "
            "A note beats a wrong string.",
            "5. Put your name at the top. Because you checked it, it counts as reviewed.",
            "6. Replying in a chat instead of in this file is fine: send your name, then one line per "
            "item starting with its number here, like `7. ...`, or `7. ok` for a line that is right. "
            "The numbers are how your words reach the right place, so keep them.", "",
            "Send it back to Vedant. Your text goes into the app unchanged, and you will get a list "
            "back showing each item next to what landed, so you can check nothing slipped.", ""]

    n, last_part, last_section = 0, None, None
    for k in todo:
        part, title, blurb = part_of(k)
        if part != last_part:
            out += [f"## {title}", "", blurb, ""]
            last_part, last_section = part, None
        _, section, where = place_of(k)
        if section != last_section:
            out += [f"### {section}", ""]
            if where:
                out += [where, ""]
            last_section = section
        n += 1
        out += [f"{n}. `{k}`", "", f"   English: {default[k][0]}", ""]
        if k in NOTES:
            out += [f"   Note: {NOTES[k]}", ""]
        if k in local:
            out += [f"   {language}, as written, unreviewed: {local[k][0]}", "",
                    f"   Correct {language} (leave blank if the line above is right):", ""]
        else:
            out += [f"   {language}:", ""]
        out += [""]

    # Part 4: other sessions' sheets, appended verbatim, lowest priority.
    extra_dir = ROOT / EXTRA_DIR
    extras = [ROOT / f for f in EXTRA_FILES if (ROOT / f).is_file()]
    extras += sorted(extra_dir.glob("*.md")) if extra_dir.is_dir() else []
    if extras:
        out += ["## PART 4. LOWER PRIORITY: OTHER QUESTIONS FROM THE TEAM", "",
                "Each section below was written by another member of the team and is a different "
                "kind of question. Skipping these costs us nothing you have not already given us "
                "above; do them if you have time.", ""]
        for f in extras:
            body = f.read_text(encoding="utf-8").strip()
            # Demote the file's own headings one level so the packet keeps its shape.
            body = "\n".join(("##" + line) if line.startswith("#") else line for line in body.splitlines())
            out += [body, "", "---", ""]

    dest = ROOT / "docs" / "localisation" / f"{language.lower()}-review-queue.md"
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text("\n".join(out).rstrip() + "\n", encoding="utf-8", newline="\n")
    print(f"{dest.relative_to(ROOT)}: {len(missing)} to write, {len(unreviewed)} to check, "
          f"{len(extras)} extra section(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "te"))
