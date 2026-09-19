"""Takes a fluent speaker's reply and writes it into values-<tag>/strings.xml, then prints back
what landed, in sheet order, so the speaker can check the mapping without reading XML.

    python tools/import_review_queue.py te --reply reply.txt --reviewer "Name"
    python tools/import_review_queue.py te --sheet filled-sheet.md

Two input shapes, because people reply the way they reply:
  --reply    a text file holding a chat reply: one line per item, starting with the item's
             number in the sheet, e.g. "7. ...". Continuation lines without a number join the
             item above. The sheet the numbers refer to is --sheet (default: the committed one).
  --sheet    the sheet itself with the `Telugu:` lines filled in. The reviewer's name is read
             from the "Reviewer's name:" line unless --reviewer is given.

A reviewer's name is REQUIRED. It is the evidence that a fluent speaker wrote the line, and it
goes into the XML as a comment above each entry. Without it the rule in values-te/strings.xml
("no Telugu ships unreviewed") has nothing to point at, so the script refuses.

THE CHECK. A mis-ordered paste puts the wrong sentence under the wrong key and nothing about the
XML would show it. So after writing, the script re-reads the XML FROM DISK and writes
docs/localisation/<language>-review-check.md: every item in sheet order, number, English, and
what is now in the app. Send that back to the reviewer; only they can read it.

Refused per item, never silently fixed: a slot mismatch (the English has %1$s and the reply does
not, or the reply has a slot the English lacks, or a bare % that would crash formatting). Those
are listed at the end and left out of the XML.

Stdlib only.
"""
import argparse
import re
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
DOCS = ROOT / "docs" / "localisation"
LANGUAGES = {"te": "Telugu", "hi": "Hindi"}

ITEM = re.compile(r"^(\d+)\. `([a-z0-9_]+)`\s*$")
ANSWER = re.compile(r"^\s*(?:Correct )?(?P<lang>Telugu|Hindi)(?: \([^)]*\))?:\s*(?P<text>.*)$")
CURRENT = re.compile(r"^\s*(?:Telugu|Hindi), as written, unreviewed:\s*(?P<text>.*)$")
ENGLISH = re.compile(r"^\s*English:\s*(?P<text>.*)$")
NUMBERED = re.compile(r"^\s*(\d+)\s*[.):\-]\s+(.*\S)\s*$")
SLOT = re.compile(r"%\d+\$s")


# --- the sheet ------------------------------------------------------------------------------

def parse_sheet(path: Path):
    """[(number, key, english, current_or_None, answer)] in sheet order, plus the reviewer line."""
    items, reviewer = [], ""
    cur = None
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.rstrip()
        if line.startswith("Reviewer's name:"):
            reviewer = line.split(":", 1)[1].replace("_", "").strip()
            continue
        m = ITEM.match(line)
        if m:
            cur = {"n": int(m.group(1)), "key": m.group(2), "en": "", "current": None, "answer": [], "in_answer": False}
            items.append(cur)
            continue
        if cur is None:
            continue
        if line.startswith("#"):
            cur = None
            continue
        e = ENGLISH.match(line)
        if e and not cur["in_answer"]:
            cur["en"] = e.group("text")
            continue
        c = CURRENT.match(line)
        if c:
            cur["current"] = c.group("text")
            continue
        a = ANSWER.match(line)
        if a:
            cur["in_answer"] = True
            if a.group("text").strip():
                cur["answer"].append(a.group("text").strip())
            continue
        if cur["in_answer"] and line.strip():
            cur["answer"].append(line.strip())
    return [(i["n"], i["key"], i["en"], i["current"], " ".join(i["answer"])) for i in items], reviewer


def parse_reply(path: Path) -> dict:
    """number -> text, from a chat reply. Continuation lines join the item above."""
    out, last = {}, None
    for raw in path.read_text(encoding="utf-8").splitlines():
        m = NUMBERED.match(raw)
        if m:
            last = int(m.group(1))
            out[last] = m.group(2)
        elif last is not None and raw.strip():
            out[last] += " " + raw.strip()
    return out


# --- the string tables ----------------------------------------------------------------------

def read_entries(path: Path) -> dict:
    """key -> (raw text as in the file, comment immediately above or ''), in file order."""
    if not path.is_file():
        return {}
    # From <resources> on: the header comment above it is not an entry's provenance.
    src = path.read_text(encoding="utf-8").split("<resources>", 1)[-1]
    out, pending = {}, ""
    for m in re.finditer(r"<!--(.*?)-->|<string\s+([^>]*)>(.*?)</string>", src, re.S):
        if m.group(1) is not None:
            pending = m.group(1).strip()
            continue
        name = re.search(r'name="([^"]+)"', m.group(2)).group(1)
        out[name] = (m.group(3), pending)
        pending = ""
    return out


def header_of(path: Path) -> str:
    """Everything before <resources>, kept verbatim: the rules live there."""
    src = path.read_text(encoding="utf-8")
    return src[: src.index("<resources>")]


def escape(text: str) -> str:
    """Android string-resource escaping. Slots (%1$s) pass through untouched."""
    t = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    t = t.replace("'", "\\'").replace('"', '\\"')
    if t[:1] in ("@", "?"):
        t = "\\" + t
    return t


def unescape(text: str) -> str:
    t = text.replace("\\'", "'").replace('\\"', '"')
    if t[:2] in ("\\@", "\\?"):
        t = t[1:]
    return t.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")


def slot_problem(english: str, answer: str):
    want, got = set(SLOT.findall(english)), set(SLOT.findall(answer))
    if want != got:
        return f"slots differ: English has {sorted(want) or 'none'}, reply has {sorted(got) or 'none'}"
    bare = re.sub(r"%\d+\$s|%%", "", answer).count("%")
    if bare:
        return "a bare % that is not a slot; write %% for a percent sign"
    return None


# --- main -----------------------------------------------------------------------------------

def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("tag", choices=sorted(LANGUAGES))
    ap.add_argument("--sheet", type=Path, help="the review sheet; filled in, or the one a --reply refers to")
    ap.add_argument("--reply", type=Path, help="a chat reply: numbered lines")
    ap.add_argument("--reviewer", help="the fluent speaker's name; read from the sheet if absent")
    a = ap.parse_args(argv)

    language = LANGUAGES[a.tag]
    sheet = a.sheet or DOCS / f"{language.lower()}-review-queue.md"
    if not sheet.is_file():
        print(f"no sheet at {sheet}; run make_review_queue.py {a.tag} first")
        return 2
    items, sheet_reviewer = parse_sheet(sheet)
    reviewer = (a.reviewer or sheet_reviewer or "").strip()
    if not reviewer:
        print("REFUSED: no reviewer name. Put it on the sheet's \"Reviewer's name:\" line or pass --reviewer. "
              "It is the evidence that a fluent speaker wrote these lines.")
        return 2

    answers = {}
    if a.reply:
        by_number = parse_reply(a.reply)
        numbers = {n for n, *_ in items}
        stray = sorted(set(by_number) - numbers)
        if stray:
            print(f"REFUSED: reply numbers {stray} are not on the sheet {sheet.name}. Wrong sheet, or a "
                  "line that starts with a number. Nothing written.")
            return 2
        for n, key, *_ in items:
            if n in by_number:
                answers[key] = by_number[n]
    else:
        for n, key, en, current, answer in items:
            if answer:
                answers[key] = answer
            elif current is not None:
                answers[key] = unescape(current)  # blank under "Correct ...:" confirms the existing line

    default = read_entries(RES / "values" / "strings.xml")
    target = RES / f"values-{a.tag}" / "strings.xml"
    existing = read_entries(target)
    english = {key: en for _, key, en, _, _ in items}

    problems, written = [], []
    stamp = f"written by {reviewer}, {date.today():%d %b %Y}"
    for key, text in answers.items():
        if key not in default:
            problems.append((key, "not a key in the default table"))
            continue
        p = slot_problem(unescape(default[key][0]), text)
        if p:
            problems.append((key, p))
            continue
        existing[key] = (escape(text), stamp)
        written.append(key)

    # Default-table order, header verbatim, one provenance comment per entry.
    lines = [header_of(target).rstrip("\n"), "<resources>"]
    for key in default:
        if key in existing:
            raw, comment = existing[key]
            if comment:
                lines.append(f"    <!-- {comment} -->")
            lines.append(f'    <string name="{key}">{raw}</string>')
    lines += ["</resources>", ""]
    target.write_text("\n".join(lines), encoding="utf-8", newline="\n")

    # THE CHECK: read back from disk, in sheet order, for the reviewer's eyes.
    landed = read_entries(target)
    # The question travels with the file, so it does not depend on anyone remembering to ask it.
    check = [f"# IN2FIT: what landed in the app, {language}", "",
             f"**Tell me any number where the {language} is under the wrong English.**", "",
             f"Each number is the item on the sheet you had, with the English it belongs to and the "
             f"{language} now in the app under that English. Nobody else on the team can read the "
             f"{language}, so a line that landed under the wrong key is invisible unless you say so.", "",
             f"Reviewer: {reviewer}. Generated {date.today():%d %B %Y}.", ""]
    for n, key, en, _, _ in items:
        status = unescape(landed[key][0]) if key in landed else "(nothing yet)"
        check += [f"{n}. `{key}`", "", f"   English: {en}", "", f"   {language}: {status}", "", ""]
    if problems:
        check += ["## Not taken, needs a second look", ""]
        for key, why in problems:
            check += [f"- `{key}`: {why}"]
        check.append("")
    out = DOCS / f"{language.lower()}-review-check.md"
    out.write_text("\n".join(check).rstrip() + "\n", encoding="utf-8", newline="\n")

    print("\n".join(check))
    print(f"--- {len(written)} written to {target.relative_to(ROOT)}, {len(problems)} refused; "
          f"check sheet at {out.relative_to(ROOT)}. Re-run make_review_queue.py {a.tag} to refresh the queue.")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
