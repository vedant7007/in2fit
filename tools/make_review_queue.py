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
    ("language_picker", "Language choice",
     "The heading of the screen where the person picks Telugu, Hindi or English."),
    ("confidence_band_", "The confidence label",
     "A one-word label next to every nutrition figure saying how far to trust it. "
     "Good means the food and the amount were both clear; Approximate means something was "
     "assumed, such as a standard bowl size; Rough means the figure could be far off."),
    ("confidence_reason_", "Why the label says what it says",
     "Shown when the person taps the confidence label. One sentence explaining it."),
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
    "status_line":
        "A format only. %1$s is the name of a feature and %2$s is its state, so the Telugu is "
        "just those two slots in the right order with whatever goes between them.",
}


def read_table(path: Path) -> dict:
    """key -> (text, translatable, needs_review), in file order, comments respected."""
    if not path.is_file():
        return {}
    src = path.read_text(encoding="utf-8")
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
            "5. Put your name at the top. Because you wrote it, it counts as reviewed.", "",
            "Send the file back to Vedant. Your text is pasted into the app unchanged.", ""]

    def emit(section_keys, heading, show_current):
        nonlocal out
        if not section_keys:
            return
        out += [f"## {heading}", ""]
        n, last_section = 0, None
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
