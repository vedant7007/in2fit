"""Takes a fluent speaker's reply and writes it into values-<tag>/strings.xml, then prints back
what landed, in sheet order, so the speaker can check the mapping without reading XML.

    python tools/import_review_queue.py te --reply reply.txt --reviewer "Name"
    python tools/import_review_queue.py te --sheet filled-sheet.md
    python tools/import_review_queue.py te --sheet reply.md --reviewer "Name" --unreviewed

Two input shapes, because people reply the way they reply:
  --reply    a text file holding a chat reply: one line per item, starting with the item's
             number in the committed sheet, e.g. "7. ...". Continuation lines without a number
             join the item above.
  --sheet    a file in the sheet's own shape: "N. `key`" headers with `Telugu:` lines filled in.
             The committed sheet edited in place, or a copy, or a reply that kept the headers.
             Answers are matched BY KEY, and each item's number is cross-checked against the
             committed sheet: a number that names a different key there means the reply was
             written against another version of the sheet, and the whole import is refused.

A name is REQUIRED: who wrote the lines, or who handed them in. Two stamps, and the difference
is the whole point:
  written by <name>, <date>                 a fluent speaker wrote or checked these lines
  REVIEW: received via <name>, <date>, author not confirmed     (--unreviewed)
The second is for lines whose author cannot be confirmed yet: they land in the XML so the
check file can be produced and the phone shows them, but every entry carries the REVIEW
marker, StringResourcesTest lists them as awaiting review, and the queue keeps them until a
fluent speaker confirms each one through a check sheet. Never stamp "written by" on lines the
named person cannot read.

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

ITEM = re.compile(r"^\s*(\d+)\. `([a-z0-9_]+)`\s*$")
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
    # The check is echoed to the console, and a Windows console defaults to a code page that
    # cannot carry Telugu; without this the print crashes AFTER the files are written.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("tag", choices=sorted(LANGUAGES))
    ap.add_argument("--sheet", type=Path, help="the review sheet; filled in, or the one a --reply refers to")
    ap.add_argument("--reply", type=Path, help="a chat reply: numbered lines")
    ap.add_argument("--reviewer", help="the fluent speaker's name; read from the sheet if absent")
    ap.add_argument("--unreviewed", action="store_true",
                    help="the author cannot be confirmed: stamp REVIEW instead of 'written by'")
    ap.add_argument("--origin", choices=["person", "machine"], default="person",
                    help="with --unreviewed: 'machine' says so in the stamp, because the XML must "
                         "say what 0017 says and a model's lines are never softened into a person's")
    a = ap.parse_args(argv)

    language = LANGUAGES[a.tag]
    canonical = DOCS / f"{language.lower()}-review-queue.md"
    if not canonical.is_file():
        print(f"no sheet at {canonical}; run make_review_queue.py {a.tag} first")
        return 2
    items, _ = parse_sheet(canonical)          # numbering, order and English come from here
    source = a.sheet or canonical
    filled, sheet_reviewer = parse_sheet(source) if not a.reply else ([], "")
    reviewer = (a.reviewer or sheet_reviewer or "").strip()
    if not reviewer:
        print("REFUSED: no name. Put it on the sheet's \"Reviewer's name:\" line or pass --reviewer: "
              "who wrote these lines, or with --unreviewed, who handed them in.")
        return 2

    answers = {}
    if a.reply:
        by_number = parse_reply(a.reply)
        numbers = {n for n, *_ in items}
        stray = sorted(set(by_number) - numbers)
        if stray:
            print(f"REFUSED: reply numbers {stray} are not on the sheet {canonical.name}. Wrong sheet, or a "
                  "line that starts with a number. Nothing written.")
            return 2
        for n, key, *_ in items:
            if n in by_number:
                answers[key] = by_number[n]
    else:
        number_of = {key: n for n, key, *_ in items}
        wrong = [(n, key, number_of.get(key)) for n, key, *_ in filled if number_of.get(key) != n]
        if wrong:
            print("REFUSED: these items are numbered differently on the committed sheet, so the reply was "
                  "written against another version of it. Nothing written.")
            for n, key, want in wrong:
                print(f"  {n}. `{key}` is item {want} on {canonical.name}" if want
                      else f"  {n}. `{key}` is not on {canonical.name}")
            return 2
        for n, key, en, current, answer in filled:
            if answer:
                answers[key] = answer
            elif current is not None:
                answers[key] = unescape(current)  # blank under "Correct ...:" confirms the existing line

    default = read_entries(RES / "values" / "strings.xml")
    target = RES / f"values-{a.tag}" / "strings.xml"
    existing = read_entries(target)
    english = {key: en for _, key, en, _, _ in items}

    problems, written = [], []
    if a.unreviewed and a.origin == "machine":
        stamp = f"REVIEW: machine-generated, received via {reviewer}, {date.today():%d %b %Y}, unreviewed"
    elif a.unreviewed:
        stamp = f"REVIEW: received via {reviewer}, {date.today():%d %b %Y}, author not confirmed"
    else:
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
    check = [f"# IN2FIT: what landed in the app, {language}", "",
             f"**Tell me any number where the {language} is under the wrong English.**", "",
             f"Each number is the item on the sheet you had, with the English it belongs to and the "
             f"{language} now in the app under that English. Nobody else on the team can read the "
             f"{language}, so a line that landed under the wrong key is invisible unless you say so. "
             f"The health sentences deserve the closest read: they are the ones a person acts on.", "",
             (f"Machine-generated lines received via {reviewer}; every line below is marked for review "
              f"until a fluent speaker confirms it." if a.unreviewed and a.origin == "machine" else
              f"Received via {reviewer}, author not yet confirmed; every line below is marked for review "
              f"until a fluent speaker confirms it." if a.unreviewed else f"Reviewer: {reviewer}.")
             + f" Generated {date.today():%d %B %Y}.", ""]
    from make_review_queue import place_of
    last = None
    for n, key, en, _, _ in items:
        _, section, _ = place_of(key)
        if section != last:
            check += [f"## {section}", ""]
            last = section
        status = unescape(landed[key][0]) if key in landed else "(nothing yet)"
        check += [f"{n}. `{key}`", "", f"   English: {en}", "", f"   {language}: {status}", "", ""]
    # Same text under two keys: the importer checks slots, not bodies, so this is for the reviewer.
    by_text = {}
    for n, key, *_ in items:
        if key in landed:
            by_text.setdefault(landed[key][0], []).append(f"{n}. `{key}`")
    dups = [v for v in by_text.values() if len(v) > 1]
    if dups:
        check += ["## Same text under more than one key: confirm this is deliberate", ""]
        for group in dups:
            check += ["- " + " and ".join(group)]
        check.append("")
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
