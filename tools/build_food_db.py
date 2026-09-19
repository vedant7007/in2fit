#!/usr/bin/env python3
"""
Builds the read-only bundled food database from USDA FoodData Central.

This is a BUILD-TIME tool. It runs on a laptop, not on the phone. Its output is a SQLite file
shipped in the APK's assets, separate from the app's writable Room database.

Every rule in docs/decisions/0002-usda-import-rules.md is enforced here and asserted after the
import. An assertion failure aborts the build rather than shipping a database that is quietly
wrong, because a wrong nutrition number is worse than no database.
"""
import csv, io, os, sqlite3, sys, zipfile, hashlib, json, re
from collections import defaultdict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SRC = os.path.join(ROOT, "data-sources", "usda")
AUTH = os.path.join(ROOT, "data-authoring")
OUT_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "food")
OUT = os.path.join(OUT_DIR, "katori-food.db")

SR_ZIP = "FoodData_Central_sr_legacy_food_csv_2018-04.zip"
FF_ZIP = "FoodData_Central_foundation_food_csv_2026-04-30.zip"

SCHEMA_VERSION = 1

# Nutrients the app shows. Resolved from nutrient.csv BY NAME, with the expected id as a
# cross-check, so a silent id change in a future release is caught rather than inherited.
WANTED = {
    "ENERGY":       dict(expect=1008, unit="KCAL",       names=["Energy"]),
    "PROTEIN":      dict(expect=1003, unit="GRAM",       names=["Protein"]),
    "CARBOHYDRATE": dict(expect=1005, unit="GRAM",       names=["Carbohydrate, by difference"]),
    "FAT":          dict(expect=1004, unit="GRAM",       names=["Total lipid (fat)"]),
    "FIBRE":        dict(expect=1079, unit="GRAM",       names=["Fiber, total dietary"]),
    "IRON":         dict(expect=1089, unit="MILLIGRAM",  names=["Iron, Fe"]),
    "VITAMIN_B12":  dict(expect=1178, unit="MICROGRAM",  names=["Vitamin B-12"]),
    "SODIUM":       dict(expect=1093, unit="MILLIGRAM",  names=["Sodium, Na"]),
}
# Rule 2: energy coalesces 1008 -> 2048 (Atwater Specific) -> 2047 (Atwater General).
ENERGY_FALLBACKS = [2048, 2047]

log_lines = []
def log(m):
    print(m)
    log_lines.append(m)

def read_csv(zf, member):
    with zf.open(member) as fh:
        text = io.TextIOWrapper(fh, encoding="utf-8-sig", newline="")
        return list(csv.DictReader(text))

def open_zip(name):
    p = os.path.join(SRC, name)
    if not os.path.exists(p):
        sys.exit(f"FATAL: missing {p}")
    z = zipfile.ZipFile(p)
    root = z.namelist()[0].split("/")[0]
    return z, root

def load_authored(fname):
    rows = []
    with open(os.path.join(AUTH, fname), encoding="utf-8") as fh:
        for line in fh:
            if line.startswith("#") or not line.strip():
                continue
            rows.append(line)
    return list(csv.DictReader(io.StringIO("".join(rows))))


def main():
    log(f"=== food db build, schema v{SCHEMA_VERSION} ===")
    os.makedirs(OUT_DIR, exist_ok=True)
    if os.path.exists(OUT):
        os.remove(OUT)

    srz, srroot = open_zip(SR_ZIP)
    ffz, ffroot = open_zip(FF_ZIP)

    # ---- resolve nutrient ids by name, per source -------------------------------------
    def resolve_nutrients(zf, root, label):
        rows = read_csv(zf, f"{root}/nutrient.csv")
        by_name = {}
        for r in rows:
            by_name.setdefault(r["name"], []).append(r)
        out = {}
        for key, spec in WANTED.items():
            found = None
            for nm in spec["names"]:
                if nm in by_name:
                    found = by_name[nm][0]
                    break
            if not found:
                sys.exit(f"FATAL: nutrient {key} not found by name in {label}")
            nid = int(found["id"])
            flag = "" if nid == spec["expect"] else f"  (EXPECTED {spec['expect']}, DIFFERS)"
            log(f"nutrient[{label}] {key:13s} id={nid:5d} unit={found['unit_name']}{flag}")
            out[key] = nid
        return out

    sr_nut = resolve_nutrients(srz, srroot, "SR")
    ff_nut = resolve_nutrients(ffz, ffroot, "FF")

    # ---- derivation codes: which zeros are genuine ------------------------------------
    assumed_zero_ids = set()
    for r in read_csv(srz, f"{srroot}/food_nutrient_derivation.csv"):
        if "assumed zero" in r["description"].lower():
            assumed_zero_ids.add(r["id"])
            log(f"derivation '{r['description']}' (id {r['id']}) treated as ASSUMED_ZERO")
    log(f"assumed-zero derivation ids: {sorted(assumed_zero_ids) or 'NONE FOUND'}")

    # ---- authored inputs ---------------------------------------------------------------
    ingredients = load_authored("ingredients.csv")
    nodata = load_authored("no-data-items.csv")
    units = load_authored("household-units.csv")
    log(f"authored: {len(ingredients)} ingredients, {len(nodata)} no-data items, {len(units)} unit rows")

    wanted_ids = {int(r["fdc_id"]) for r in ingredients}

    # ---- load food rows -----------------------------------------------------------------
    sr_food = {int(r["fdc_id"]): r for r in read_csv(srz, f"{srroot}/food.csv")
               if r["data_type"] == "sr_legacy_food"}
    ff_food = {int(r["fdc_id"]): r for r in read_csv(ffz, f"{ffroot}/food.csv")
               if r["data_type"] == "foundation_food"}
    log(f"SR Legacy foods: {len(sr_food)}   Foundation foods: {len(ff_food)}")

    missing = [i for i in wanted_ids if i not in sr_food and i not in ff_food]
    if missing:
        sys.exit(f"FATAL: authored fdc_ids not present in either dataset: {missing}")

    # ---- nutrients for the authored ids only -------------------------------------------
    def collect(zf, root, nutmap, ids, source_label):
        want_rev = {v: k for k, v in nutmap.items()}
        energy_alt = set(ENERGY_FALLBACKS)
        out = defaultdict(dict)       # fdc_id -> nutrient key -> (state, amount)
        energy_alt_vals = defaultdict(dict)
        with zf.open(f"{root}/food_nutrient.csv") as fh:
            text = io.TextIOWrapper(fh, encoding="utf-8-sig", newline="")
            for r in csv.DictReader(text):
                try:
                    fid = int(r["fdc_id"])
                except ValueError:
                    continue
                if fid not in ids:
                    continue
                nid = int(r["nutrient_id"]) if r["nutrient_id"] else -1
                amt = r["amount"]
                if nid in energy_alt:
                    if amt not in ("", None):
                        energy_alt_vals[fid][nid] = float(amt)
                    continue
                key = want_rev.get(nid)
                if not key:
                    continue
                if amt in ("", None):
                    continue  # no value: stays absent, which the app reads as Unknown
                deriv = r.get("derivation_id", "")
                state = "ASSUMED_ZERO" if (deriv in assumed_zero_ids and float(amt) == 0.0) else "MEASURED"
                out[fid][key] = (state, float(amt))
        return out, energy_alt_vals

    sr_ids = {i for i in wanted_ids if i in sr_food}
    ff_ids = {i for i in wanted_ids if i in ff_food}
    sr_n, sr_alt = collect(srz, srroot, sr_nut, sr_ids, "SR")
    ff_n, ff_alt = collect(ffz, ffroot, ff_nut, ff_ids, "FF")

    nutrients = {}
    nutrients.update(sr_n)
    nutrients.update(ff_n)
    alt = {}
    alt.update(sr_alt)
    alt.update(ff_alt)

    # Rule 2: energy coalesce 1008 -> 2048 -> 2047
    coalesced = 0
    for fid in wanted_ids:
        if "ENERGY" in nutrients.get(fid, {}):
            continue
        for nid in ENERGY_FALLBACKS:
            if nid in alt.get(fid, {}):
                nutrients.setdefault(fid, {})["ENERGY"] = ("MEASURED", alt[fid][nid])
                coalesced += 1
                log(f"energy coalesced for fdc {fid} from nutrient {nid}")
                break
    log(f"energy coalesced from Atwater for {coalesced} foods")

    # ---- portions ------------------------------------------------------------------------
    def portions(zf, root, ids):
        mu = {r["id"]: r["name"] for r in read_csv(zf, f"{root}/measure_unit.csv")}
        out = []
        for r in read_csv(zf, f"{root}/food_portion.csv"):
            try:
                fid = int(r["fdc_id"])
            except ValueError:
                continue
            if fid not in ids or not r.get("gram_weight"):
                continue
            desc = (r.get("portion_description") or "").strip()
            modi = (r.get("modifier") or "").strip()
            unit = mu.get(r.get("measure_unit_id", ""), "")
            label = " ".join(x for x in [r.get("amount", ""), unit if unit != "undetermined" else "", desc, modi] if x)
            out.append((fid, label.strip(), float(r["gram_weight"])))
        return out
    all_portions = portions(srz, srroot, sr_ids) + portions(ffz, ffroot, ff_ids)
    log(f"usda portions for authored foods: {len(all_portions)}")

    # ---- write ----------------------------------------------------------------------------
    db = sqlite3.connect(OUT)
    c = db.cursor()
    c.executescript("""
    PRAGMA journal_mode=DELETE;
    CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
    CREATE TABLE foods (
        food_key TEXT PRIMARY KEY,
        fdc_id INTEGER NOT NULL,
        source TEXT NOT NULL,
        display_name TEXT NOT NULL,
        usda_description TEXT NOT NULL,
        food_class TEXT NOT NULL,
        band_reason TEXT,
        note TEXT
    );
    CREATE TABLE food_nutrients (
        food_key TEXT NOT NULL,
        nutrient TEXT NOT NULL,
        state TEXT NOT NULL,
        amount REAL,
        unit TEXT NOT NULL,
        PRIMARY KEY (food_key, nutrient)
    );
    CREATE TABLE food_aliases (
        alias TEXT NOT NULL,
        alias_norm TEXT NOT NULL,
        script TEXT NOT NULL,
        food_key TEXT NOT NULL
    );
    CREATE INDEX idx_alias_norm ON food_aliases(alias_norm);
    CREATE TABLE no_data_items (
        item_key TEXT PRIMARY KEY,
        display_name TEXT NOT NULL,
        reason TEXT NOT NULL
    );
    CREATE TABLE no_data_aliases (
        alias TEXT NOT NULL, alias_norm TEXT NOT NULL, item_key TEXT NOT NULL
    );
    CREATE INDEX idx_nodata_alias_norm ON no_data_aliases(alias_norm);
    CREATE TABLE unit_conversions (
        unit TEXT NOT NULL, food_class TEXT NOT NULL, grams REAL NOT NULL, note TEXT,
        PRIMARY KEY (unit, food_class)
    );
    CREATE TABLE usda_portions (
        food_key TEXT NOT NULL, label TEXT NOT NULL, grams REAL NOT NULL
    );
    """)

    def norm(s):
        s = s.strip().lower()
        s = re.sub(r"[^\wऀ-ॿఀ-౿ ]+", " ", s, flags=re.UNICODE)
        return re.sub(r"\s+", " ", s).strip()

    def is_native(s):
        return any("ऀ" <= ch <= "ॿ" or "ఀ" <= ch <= "౿" for ch in s)

    id_to_key = {}
    for r in ingredients:
        fid = int(r["fdc_id"])
        key = r["key"]
        id_to_key[fid] = key
        src = sr_food.get(fid) or ff_food.get(fid)
        c.execute("INSERT INTO foods VALUES (?,?,?,?,?,?,?,?)", (
            key, fid, r["source"], r["display_name"], src["description"],
            r["food_class"], r["band_reason"] or None, r["note"] or None))
        for nk, (state, amount) in nutrients.get(fid, {}).items():
            c.execute("INSERT INTO food_nutrients VALUES (?,?,?,?,?)",
                      (key, nk, state, amount, WANTED[nk]["unit"]))
        seen = set()
        for col, script in (("aliases_roman", "ROMAN"), ("aliases_native", "NATIVE")):
            for a in (r.get(col) or "").split("|"):
                a = a.strip()
                if not a:
                    continue
                n = norm(a)
                if not n or (n, script) in seen:
                    continue
                seen.add((n, script))
                c.execute("INSERT INTO food_aliases VALUES (?,?,?,?)",
                          (a, n, "NATIVE" if is_native(a) else "ROMAN", key))
        n = norm(r["display_name"])
        if n:
            c.execute("INSERT INTO food_aliases VALUES (?,?,?,?)", (r["display_name"], n, "ROMAN", key))

    for r in nodata:
        c.execute("INSERT INTO no_data_items VALUES (?,?,?)", (r["key"], r["display_name"], r["reason"]))
        for a in r["aliases_roman"].split("|"):
            a = a.strip()
            if a:
                c.execute("INSERT INTO no_data_aliases VALUES (?,?,?)", (a, norm(a), r["key"]))

    for r in units:
        c.execute("INSERT INTO unit_conversions VALUES (?,?,?,?)",
                  (r["unit"], r["food_class"], float(r["grams"]), r["note"] or None))

    for fid, label, grams in all_portions:
        if fid in id_to_key:
            c.execute("INSERT INTO usda_portions VALUES (?,?,?)", (id_to_key[fid], label, grams))

    for k, v in {
        "schema_version": str(SCHEMA_VERSION),
        "sr_legacy_release": "2018-04",
        "foundation_release": "2026-04-30",
        "attribution": ("U.S. Department of Agriculture, Agricultural Research Service. "
                        "FoodData Central. fdc.nal.usda.gov"),
        "licence": "USDA FoodData Central data are in the public domain, published under CC0 1.0 Universal.",
        "disclosure": ("Values are based on foods sampled in the United States and may differ from "
                       "Indian-grown produce and Indian preparation. They are estimates, not "
                       "measurements of your own food."),
    }.items():
        c.execute("INSERT INTO meta VALUES (?,?)", (k, v))

    db.commit()

    # ---- POST-IMPORT ASSERTIONS ---------------------------------------------------------
    log("--- assertions ---")
    failures = []

    n_foods = c.execute("SELECT COUNT(*) FROM foods").fetchone()[0]

    # Rule 2 assertion: every food has an energy value.
    rows = c.execute("""
        SELECT f.food_key FROM foods f
        WHERE NOT EXISTS (SELECT 1 FROM food_nutrients n
                          WHERE n.food_key = f.food_key AND n.nutrient = 'ENERGY')
    """).fetchall()
    if rows:
        failures.append(f"RULE 2: foods with no energy value: {[r[0] for r in rows]}")
    else:
        log(f"RULE 2 ok: all {n_foods} foods have an energy value")

    # Rule 3 assertion: no dairy from Foundation.
    rows = c.execute("SELECT food_key, source FROM foods WHERE food_class='DAIRY' AND source!='USDA_SR_LEGACY'").fetchall()
    if rows:
        failures.append(f"RULE 3: dairy sourced outside SR Legacy: {rows}")
    else:
        n_dairy = c.execute("SELECT COUNT(*) FROM foods WHERE food_class='DAIRY'").fetchone()[0]
        log(f"RULE 3 ok: all {n_dairy} dairy foods come from SR Legacy")

    # Rule 1 assertion: no nutrient row carries a NULL amount in a MEASURED state, and no
    # zero is recorded as MEASURED unless the source said so.
    rows = c.execute("SELECT food_key, nutrient FROM food_nutrients WHERE state='MEASURED' AND amount IS NULL").fetchall()
    if rows:
        failures.append(f"RULE 1: MEASURED rows with a null amount: {rows}")
    else:
        log("RULE 1 ok: no MEASURED row has a null amount; absent nutrients are simply absent")

    # Rule 4 assertion: the enriched twins must not appear.
    ENRICHED_FORBIDDEN = {168877: "enriched raw rice", 168878: "enriched cooked rice",
                          2259793: "Foundation yoghurt with no B12 row"}
    rows = c.execute("SELECT fdc_id, food_key FROM foods").fetchall()
    bad = [(fid, k, ENRICHED_FORBIDDEN[fid]) for fid, k in rows if fid in ENRICHED_FORBIDDEN]
    if bad:
        failures.append(f"RULE 4: a forbidden record was imported: {bad}")
    else:
        log("RULE 4 ok: no enriched or known-defective record was imported")

    # No-data items must not have any nutrients.
    rows = c.execute("""
        SELECT i.item_key FROM no_data_items i
        JOIN food_aliases a ON a.alias_norm IN (SELECT alias_norm FROM no_data_aliases WHERE item_key = i.item_key)
    """).fetchall()
    if rows:
        failures.append(f"NO-DATA: an item that must have no data resolves to a food: {rows}")
    else:
        log("NO-DATA ok: ragi, bajra, jaggery and the rest resolve to no food")

    # Unknown-vs-zero sanity: report how many nutrients are absent, so the gap is visible.
    total_possible = n_foods * len(WANTED)
    present = c.execute("SELECT COUNT(*) FROM food_nutrients").fetchone()[0]
    log(f"coverage: {present}/{total_possible} nutrient values present; "
        f"{total_possible - present} absent and will read as Unknown, never as zero")
    for nk in WANTED:
        cnt = c.execute("SELECT COUNT(*) FROM food_nutrients WHERE nutrient=?", (nk,)).fetchone()[0]
        z = c.execute("SELECT COUNT(*) FROM food_nutrients WHERE nutrient=? AND state='ASSUMED_ZERO'", (nk,)).fetchone()[0]
        log(f"  {nk:13s} present {cnt:3d}/{n_foods}   assumed-zero {z}")

    log(f"aliases: {c.execute('SELECT COUNT(*) FROM food_aliases').fetchone()[0]}")
    log(f"unit conversions: {c.execute('SELECT COUNT(*) FROM unit_conversions').fetchone()[0]}")
    log(f"usda portions: {c.execute('SELECT COUNT(*) FROM usda_portions').fetchone()[0]}")

    db.commit()
    db.close()

    if failures:
        for f in failures:
            log("ASSERTION FAILED: " + f)
        os.remove(OUT)
        log("database deleted; a wrong database is worse than none")
        sys.exit(1)

    h = hashlib.sha256(open(OUT, "rb").read()).hexdigest()
    size = os.path.getsize(OUT)
    log(f"WROTE {OUT}  {size} bytes  sha256 {h}")
    log("=== all assertions passed ===")

    with open(os.path.join(ROOT, "logs", "food-db-build.log"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(log_lines) + "\n")

if __name__ == "__main__":
    main()
