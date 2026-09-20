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

# Where each group of keys appears, in words a reviewer can picture. Order is priority order.
# A key whose prefix is not here lands in a final "unplaced" section, so a new group of strings
# cannot be handed out without someone saying where it is shown.
PLACES = [
    ("safety_", "Advice screens",
     "A line at the bottom of every screen that gives advice. Always visible, cannot be closed."),
    ("trigger_", "The health sentences",
     "One of these is shown when something in the person's data changes what the app suggests: "
     "a lab report value, a condition they told the app about, what dominates a meal, their "
     "living situation, or a pattern over days. THESE MATTER MOST. Each must say only what the "
     "data says, never that the person has an illness, never what to take. The slots (%1$s and "
     "so on) are filled by the app with names and numbers; what each slot holds is in the note."),
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
    ("about_", "The About screen",
     "Where the app says where its numbers come from and which open-source parts it contains."),
    ("context_", "Lines the app writes for its own language model, not for the screen",
     "Not shown on a screen. When the person asks a question, the app writes their own meals, "
     "lab values and diet into a few lines like these and gives them to its language model, in "
     "the person's language, before it answers; the answer may repeat them back. Plain and "
     "literal, no advice in them: every slot is a name, a number or a date the app fills in."),
    ("status_", "TEMPORARY: the build-status screen",
     "A developer screen listing what is built. It will be replaced before the demo. Lowest "
     "priority: do these last, or skip them."),
    ("state_", "TEMPORARY: the build-status screen", ""),
    ("pipeline_", "TEMPORARY: the build-status screen", ""),
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


def by_priority(keys):
    """PLACES order first, then the order the keys have in the string table."""
    return sorted(keys, key=lambda k: place_of(k)[0])


def main(tag: str) -> int:
    language = LANGUAGES[tag]
    default = read_table(RES / "values" / "strings.xml")
    local = read_table(RES / f"values-{tag}" / "strings.xml")
    keys = [k for k, (_, translatable, _) in default.items() if translatable]
    missing = by_priority(k for k in keys if k not in local)
    unreviewed = by_priority(k for k in keys if k in local and local[k][2])

    out = [f"# IN2FIT: {language} strings for review", ""]
    out += [f"Generated {date.today():%d %B %Y} from the app's English string table. "
            f"{len(missing)} strings to write, {len(unreviewed)} to check.", ""]
    out += ["Reviewer's name: ______________________", ""]
    out += ["## What the app is", "",
            "IN2FIT is a phone app for people in India who may not read English nutrition labels. "
            "A person says what they ate, in Telugu, Hindi or English; the app finds each food in "
            "its database and shows the nutrition, with a label saying how far to trust each "
            "figure. It can also read a printed lab report with the camera. It never diagnoses "
            "and never prescribes.", ""]
    out += ["## What we need from you", "",
            f"For each numbered item, write the {language} on the line that says `{language}:`.", "",
            "1. Write it the way you would say it to a family member. Short: it goes on a phone screen.",
            "2. Keep the meaning exactly. Add no reassurance, no advice and no number. The English was "
            "written so that the app never says a person has a condition and never tells them what to "
            "take; the translation must not either.",
            "3. Anything like `%1$s` or `%2$s` is a slot the app fills in with a name or a number. Keep "
            f"it in the {language} sentence, wherever {language} needs it.",
            "4. If an English line is unclear or makes no sense to you, write that instead of guessing. "
            "A note beats a wrong string.",
            "5. Put your name at the top. Because you wrote it, it counts as reviewed.",
            "6. Replying in a chat instead of in this file is fine: send your name, then one line per "
            "item starting with its number here, like `7. ...`. The numbers are how your words reach "
            "the right place, so keep them.", "",
            "Send it back to Vedant. Your text goes into the app unchanged, and you will get a list "
            "back showing each item next to what landed, so you can check nothing slipped.", ""]

    n = 0  # continuous across sections: a numbered chat reply must be unambiguous

    def emit(section_keys, heading, show_current):
        nonlocal out, n
        if not section_keys:
            return
        out += [f"## {heading}", ""]
        last_section = None
        for k in section_keys:
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
            if show_current:
                out += [f"   {language}, as written, unreviewed: {local[k][0]}", "",
                        f"   Correct {language} (leave blank if the line above is right):", ""]
            else:
                out += [f"   {language}:", ""]
            out += [""]

    emit(unreviewed, f"{language} already written but not yet reviewed: please check", True)
    emit(missing, f"{language} not yet written", False)

    dest = ROOT / "docs" / "localisation" / f"{language.lower()}-review-queue.md"
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text("\n".join(out).rstrip() + "\n", encoding="utf-8", newline="\n")
    print(f"{dest.relative_to(ROOT)}: {len(missing)} to write, {len(unreviewed)} to check")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "te"))
