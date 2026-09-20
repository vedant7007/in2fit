"""Renders the lab-report fixture sheets for Beat 3 and their sidecars.

Output: app/src/androidTest/assets/lab-reports/rendered-NN-*.png and *.expected.txt (Arjun's
sidecar format, `name|value|unit|low|high|printed row`). Every image here is RENDERED; a
photograph of the printed sheet is taken with the demo phone and named photo-NN-*.jpg. The
README beside them states the threshold. Deterministic: the same fonts and seed give the same
bytes, so a regenerated fixture is a no-op diff.
"""
import os, random
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "androidTest", "assets", "lab-reports")
FONTS = "C:/Windows/Fonts/"
W, H = 1700, 2200  # A4 at ~150 dpi

# The demo's two rows first, then the rows the corpus knows. Printed range: the SHEET's.
ROWS = [
    ("Fasting Glucose", "142", "mg/dL", 70, 100, "H"),
    ("HbA1c", "6.4", "%", 4.0, 5.6, "H"),
    ("Haemoglobin", "13.8", "g/dL", 13.0, 17.0, ""),
    ("Total Cholesterol", "192", "mg/dL", None, 200, ""),
    ("Triglycerides", "148", "mg/dL", None, 150, ""),
    ("Vitamin B12", "250", "pg/mL", 200, 900, ""),
    ("Creatinine", "0.9", "mg/dL", 0.7, 1.3, ""),
    ("TSH", "2.1", "uIU/mL", 0.4, 4.2, ""),
]

def fmt(v):
    return "" if v is None else (str(int(v)) if float(v).is_integer() else str(v))

def range_text(lo, hi, style):
    if lo is None and hi is not None:
        return "< " + fmt(hi) if style != "hyphen" else "<" + fmt(hi)
    if style == "spaced": return f"{fmt(lo)} - {fmt(hi)}"
    if style == "hyphen": return f"{fmt(lo)}-{fmt(hi)}"
    if style == "bracket": return f"({fmt(lo)} - {fmt(hi)})"
    if style == "to": return f"{fmt(lo)} to {fmt(hi)}"
    raise ValueError(style)

def font(name, size):
    return ImageFont.truetype(FONTS + name, size)

def header(d, f_body, f_bold, title="SRI VENKATA DIAGNOSTICS, HYDERABAD"):
    d.text((90, 70), title, font=f_bold, fill="black")
    d.text((90, 130), "NABL accredited laboratory   |   Ph: 040-2345 6789", font=f_body, fill="black")
    d.line((90, 190, W - 90, 190), fill="black", width=3)
    d.text((90, 220), "Name: Test Patient          Age: 22 Years          Sex: Male", font=f_body, fill="black")
    d.text((90, 275), "Collected: 24/09/2026 08:10          Reported on: 24/09/2026 18:30", font=f_body, fill="black")
    d.text((90, 330), "Ref. by: Dr. A. Sharma MD          Sample: Fasting venous blood", font=f_body, fill="black")
    d.line((90, 390, W - 90, 390), fill="black", width=2)

def footer(d, f_body):
    d.line((90, H - 220, W - 90, H - 220), fill="black", width=2)
    d.text((90, H - 190), "End of report.  Values outside the reference range are flagged H or L.", font=f_body, fill="black")
    d.text((90, H - 130), "Dr. A. Sharma MD (Pathology)                                        Page 1 of 1", font=f_body, fill="black")

def sheet(fname, style, glued, order, font_name, flags, two_column=False, title=None):
    img = Image.new("RGB", (W, H), "white")
    d = ImageDraw.Draw(img)
    f_body = font(font_name, 38); f_bold = font(font_name.replace(".ttf", "bd.ttf") if os.path.exists(FONTS + font_name.replace(".ttf", "bd.ttf")) else font_name, 42)
    header(d, f_body, f_bold, title or "SRI VENKATA DIAGNOSTICS, HYDERABAD")
    printed = []
    y0 = 450
    if two_column:
        # two tests per line: name value unit range | name value unit range
        colsL = [90, 470, 610, 700]; colsR = [900, 1280, 1420, 1510]
        for x, t in zip(colsL, ["Test", "Result", "Unit", "Range"]): d.text((x, y0), t, font=f_bold, fill="black")
        for x, t in zip(colsR, ["Test", "Result", "Unit", "Range"]): d.text((x, y0), t, font=f_bold, fill="black")
        pairs = [(ROWS[i], ROWS[i + 1] if i + 1 < len(ROWS) else None) for i in range(0, len(ROWS), 2)]
        for i, (a, b) in enumerate(pairs):
            y = y0 + 90 + i * 110
            for cols, r in ((colsL, a), (colsR, b)):
                if r is None: continue
                name, val, unit, lo, hi, flag = r
                rng = range_text(lo, hi, style)
                d.text((cols[0], y), name, font=f_body, fill="black")
                d.text((cols[1], y), val, font=f_body, fill="black")
                d.text((cols[2], y), unit, font=f_body, fill="black")
                d.text((cols[3], y), rng, font=f_body, fill="black")
                printed.append((name, val, unit, lo, hi, f"{name} {val} {unit} {rng}"))
    else:
        if order == "result-first":
            cols = [90, 760, 980, 1230]; heads = ["Test Name", "Result", "Unit", "Reference Range"]
        else:  # range-first: Reference column before Result
            cols = [90, 760, 1080, 1330]; heads = ["Test Name", "Reference Range", "Result", "Unit"]
        for x, t in zip(cols, heads): d.text((x, y0), t, font=f_bold, fill="black")
        d.line((90, y0 + 60, W - 90, y0 + 60), fill="black", width=2)
        for i, (name, val, unit, lo, hi, flag) in enumerate(ROWS):
            y = y0 + 110 + i * 120
            rng = range_text(lo, hi, style)
            shown_val = val + unit if glued else val
            shown_unit = "" if glued else unit
            flag_txt = (" " + flag) if (flags and flag) else ""
            d.text((cols[0], y), name if not (flags and name == "Fasting Glucose") else "Glucose (Fasting)", font=f_body, fill="black")
            if order == "result-first":
                d.text((cols[1], y), shown_val + flag_txt, font=f_body, fill="black")
                d.text((cols[2], y), shown_unit, font=f_body, fill="black")
                d.text((cols[3], y), rng, font=f_body, fill="black")
                row = " ".join(x for x in [name if not (flags and name == "Fasting Glucose") else "Glucose (Fasting)", shown_val + flag_txt, shown_unit, rng] if x)
            else:
                d.text((cols[1], y), rng, font=f_body, fill="black")
                d.text((cols[2], y), shown_val + flag_txt, font=f_body, fill="black")
                d.text((cols[3], y), shown_unit, font=f_body, fill="black")
                row = " ".join(x for x in [name, rng, shown_val + flag_txt, shown_unit] if x)
            printed.append((name if not (flags and name == "Fasting Glucose") else "Glucose (Fasting)", val, unit, lo, hi, row))
    footer(d, f_body)
    return img, printed

def sidecar(path, kind, printed, note):
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(f"# kind: {kind}\n# {note}\n# name|value|unit|low|high|printed row (the row exactly as printed, for the JVM extractor test)\n")
        for name, val, unit, lo, hi, row in printed:
            fh.write(f"{name}|{val}|{unit}|{fmt(lo)}|{fmt(hi)}|{row}\n")

def angled(img):
    """Perspective as a phone held about 20 degrees off square, plus a soft focus."""
    w, h = img.size
    # PIL perspective: coefficients from 4-point correspondence
    def coeffs(pa, pb):
        import numpy as np
        A = []
        for (x, y), (u, v) in zip(pa, pb):
            A.append([x, y, 1, 0, 0, 0, -u * x, -u * y]); A.append([0, 0, 0, x, y, 1, -v * x, -v * y])
        A = np.array(A, dtype=float); B = np.array([c for p in pb for c in p], dtype=float)
        return tuple(np.linalg.solve(A, B))
    src = [(0, 0), (w, 0), (w, h), (0, h)]
    dst = [(120, 60), (w - 40, 180), (w - 160, h - 40), (60, h - 200)]
    out = img.transform((w, h), Image.PERSPECTIVE, coeffs(dst, src), Image.BICUBIC, fillcolor=(235, 232, 226))
    return out.filter(ImageFilter.GaussianBlur(1.2))

def warm(img, seed=7):
    """2700 K cast, a shadow gradient across the page, grain, then JPEG loss."""
    import numpy as np
    rnd = np.random.default_rng(seed)
    a = np.asarray(img).astype(float)
    a[..., 0] *= 1.00; a[..., 1] *= 0.86; a[..., 2] *= 0.62          # warm cast, a tungsten bulb
    h, w = a.shape[:2]
    grad = np.linspace(1.0, 0.42, w)[None, :, None]                   # lamp on the left, the right half in shadow
    a = a * grad
    yy, xx = np.mgrid[0:h, 0:w]
    glare = 70 * np.exp(-(((xx - 0.3 * w) ** 2) / (2 * (0.18 * w) ** 2) + ((yy - 0.25 * h) ** 2) / (2 * (0.12 * h) ** 2)))
    a += glare[..., None]                                              # a lamp's hot spot on the paper
    a += rnd.normal(0, 14, a.shape)                                    # grain
    a = np.clip(a, 0, 255).astype("uint8")
    out = Image.fromarray(a).filter(ImageFilter.GaussianBlur(0.8))
    return out

def main():
    os.makedirs(OUT, exist_ok=True)
    specs = [
        ("rendered-01-single-column-spaced-range", dict(style="spaced", glued=False, order="result-first", font_name="arial.ttf", flags=False),
         "one column, spaced range, unit separate, sans-serif: the clean sheet, and the one to print and photograph"),
        ("rendered-02-two-column-sheet", dict(style="spaced", glued=False, order="result-first", font_name="arial.ttf", flags=False, two_column=True),
         "two tests per line: the known ceiling, the right column may be lost and must not be misread"),
        ("rendered-03-glued-units-hyphen-range", dict(style="hyphen", glued=True, order="result-first", font_name="cour.ttf", flags=False, title="CITY PATHOLOGY LAB"),
         "unit glued to the number and 70-100 with no spaces, monospace: the shape the hardware run returned"),
        ("rendered-04-range-before-result", dict(style="spaced", glued=False, order="range-first", font_name="calibri.ttf", flags=False, title="APOLLO CLINICAL LABORATORY"),
         "Reference column before Result: the known ceiling, the range may read as none and must not read as the value"),
        ("rendered-07-flags-brackets-serif", dict(style="bracket", glued=False, order="result-first", font_name="times.ttf", flags=True, title="LIFELINE DIAGNOSTIC CENTRE"),
         "H flags, (70 - 100) in brackets, Glucose (Fasting) with brackets in the name, serif"),
    ]
    made = []
    for name, kw, note in specs:
        img, printed = sheet(name, **kw)
        img.save(os.path.join(OUT, name + ".png"), optimize=True)
        sidecar(os.path.join(OUT, name + ".expected.txt"), "rendered", printed, note)
        made.append(name)
        if name == "rendered-01-single-column-spaced-range":
            angled(img).save(os.path.join(OUT, "rendered-05-angled.png"), optimize=True)
            sidecar(os.path.join(OUT, "rendered-05-angled.expected.txt"), "rendered", printed, "sheet 01 warped to about 20 degrees off square and softened: perspective, in the recogniser only")
            warm(img).save(os.path.join(OUT, "rendered-06-warm-light.jpg"), quality=72)
            sidecar(os.path.join(OUT, "rendered-06-warm-light.expected.txt"), "rendered", printed, "sheet 01 under a 2700 K cast, a shadow gradient, grain and JPEG loss: colour and noise, in the recogniser only")
            made += ["rendered-05-angled", "rendered-06-warm-light"]
    for m in sorted(made): print(m)

if __name__ == "__main__":
    main()
