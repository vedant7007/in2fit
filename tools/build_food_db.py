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
    recipes = load_authored("recipes.csv")
    recipe_ings_raw = load_authored("recipe-ingredients.csv")
    candidates = load_authored("candidates.csv")
    log(f"authored: {len(ingredients)} ingredients, {len(nodata)} no-data items, {len(units)} unit rows, {len(candidates)} candidate rows")

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
    -- Spec 4.3: what may be SUGGESTED, per life context. A key absent here is an ingredient.
    -- suggest_as and serving_g are NULL when the food's own display name and usual serving apply.
    CREATE TABLE candidates (
        key TEXT NOT NULL, context TEXT NOT NULL, suggest_as TEXT, serving_g REAL, note TEXT NOT NULL,
        PRIMARY KEY (key, context)
    );
    CREATE TABLE recipes (
        recipe_key TEXT PRIMARY KEY,
        display_name TEXT NOT NULL,
        servings REAL NOT NULL,
        serving_g REAL NOT NULL,
        yield_g REAL NOT NULL,
        water_change_g REAL NOT NULL,
        oil_method TEXT NOT NULL,
        absorbed_oil_g REAL NOT NULL,
        absorbed_basis TEXT NOT NULL,
        absorbed_reason TEXT NOT NULL,
        moisture_class TEXT NOT NULL,
        note TEXT
    );
    CREATE TABLE recipe_ingredients (
        recipe_key TEXT NOT NULL,
        food_key TEXT NOT NULL,
        grams REAL NOT NULL,
        role TEXT NOT NULL,
        note TEXT,
        PRIMARY KEY (recipe_key, food_key, role)
    );
    CREATE TABLE recipe_nutrients (
        recipe_key TEXT NOT NULL,
        nutrient TEXT NOT NULL,
        state TEXT NOT NULL,
        amount_per_100g REAL,
        unit TEXT NOT NULL,
        PRIMARY KEY (recipe_key, nutrient)
    );
    CREATE TABLE recipe_aliases (
        alias TEXT NOT NULL, alias_norm TEXT NOT NULL, script TEXT NOT NULL, recipe_key TEXT NOT NULL
    );
    CREATE INDEX idx_recipe_alias_norm ON recipe_aliases(alias_norm);
    CREATE TABLE usda_portions (
        food_key TEXT NOT NULL, label TEXT NOT NULL, grams REAL NOT NULL
    );
    """)

    def norm(s):
        s = s.strip().lower()
        s = re.sub(r"[^\wऀ-ॿఀ-౿ ]+", " ", s, flags=re.UNICODE)
        s = re.sub(r"\s+", " ", s).strip()
        # A word-final ు is ్: Telugu closes a borrowed consonant-final word with a short u the
        # speaker did not say (పనీర్ -> పనీరు). Mirrors FoodTextMatching.normalise; a JVM test
        # asserts the two agree on every alias in the shipped tables.
        return re.sub("ు(?= |$)", "్", s)

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

    # A unit row's `unit` may carry `|`-separated spoken forms (glass|గ్లాస్|गिलास); each becomes
    # its own row with the same grams, stored normalised, which is what resolveUnit looks up.
    for r in units:
        seen_units = set()
        for u in r["unit"].split("|"):
            u = norm(u)
            # Two spellings that normalise to one form (గ్లాస్, గ్లాసు) are one unit, by design.
            if not u or u in seen_units:
                continue
            seen_units.add(u)
            c.execute("INSERT INTO unit_conversions VALUES (?,?,?,?)",
                      (u, r["food_class"], float(r["grams"]), r["note"] or None))

    # ---- authored reference recipes ------------------------------------------------------
    # A reference recipe is a stated composition, shown to the user and editable. Every figure
    # derived from one is capped at Approximate. The assertions below exist because a recipe is
    # where a wrong number is most likely to look reasonable: a plausible total hides a wrong
    # composition, and nothing downstream would notice.
    by_recipe = defaultdict(list)
    for r in recipe_ings_raw:
        by_recipe[r["recipe_key"]].append(r)

    recipe_rows_parsed = sum(len(v) for v in by_recipe.values())
    recipe_rows_inserted = 0

    for r in recipes:
        key = r["recipe_key"]
        c.execute("INSERT INTO recipes VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", (
            key, r["display_name"], float(r["servings"]), float(r["serving_g"]),
            float(r["yield_g"]), float(r["water_change_g"]), r["oil_method"],
            float(r["absorbed_oil_g"]), r["absorbed_basis"], r["absorbed_reason"],
            r["moisture_class"], r["note"] or None))
        for i in by_recipe.get(key, []):
            c.execute("INSERT INTO recipe_ingredients VALUES (?,?,?,?,?)",
                      (key, i["ingredient_key"], float(i["grams"]), i["role"], i["note"] or None))
            recipe_rows_inserted += 1
        seen_r = set()
        for col in ("aliases_roman", "aliases_native"):
            for a in (r.get(col) or "").split("|"):
                a = a.strip()
                if not a:
                    continue
                n = norm(a)
                if not n or n in seen_r:
                    continue
                seen_r.add(n)
                c.execute("INSERT INTO recipe_aliases VALUES (?,?,?,?)",
                          (a, n, "NATIVE" if is_native(a) else "ROMAN", key))
        n = norm(r["display_name"])
        if n and n not in seen_r:
            c.execute("INSERT INTO recipe_aliases VALUES (?,?,?,?)", (r["display_name"], n, "ROMAN", key))

    # nutrition per 100 g of the finished dish
    ing_nutrients = {}
    for row in c.execute("SELECT food_key, nutrient, state, amount FROM food_nutrients").fetchall():
        ing_nutrients.setdefault(row[0], {})[row[1]] = (row[2], row[3])

    for r in recipes:
        key = r["recipe_key"]
        yield_g = float(r["yield_g"])
        for nk in WANTED:
            total = 0.0
            unknown = False
            for i in by_recipe.get(key, []):
                st_amt = ing_nutrients.get(i["ingredient_key"], {}).get(nk)
                if st_amt is None or st_amt[0] == "UNKNOWN":
                    # One unknown contributor makes the whole recipe figure unknown. Summing the
                    # rest and presenting it as a total would understate it silently.
                    unknown = True
                    break
                if st_amt[0] == "MEASURED":
                    total += (float(i["grams"]) / 100.0) * float(st_amt[1])
            if unknown:
                c.execute("INSERT INTO recipe_nutrients VALUES (?,?,?,?,?)",
                          (key, nk, "UNKNOWN", None, WANTED[nk]["unit"]))
            else:
                c.execute("INSERT INTO recipe_nutrients VALUES (?,?,?,?,?)",
                          (key, nk, "MEASURED", total / (yield_g / 100.0), WANTED[nk]["unit"]))

    log(f"recipes: {len(recipes)}  ingredient rows parsed {recipe_rows_parsed} inserted {recipe_rows_inserted}")

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
                       "Indian-grown produce and Indian preparation. US milk is vitamin-D fortified "
                       "and US bread flour is iron-enriched; Indian milk and bread are not, so those "
                       "two figures read high. They are estimates, not measurements of your own food."),
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

    # AMBIGUOUS ALIAS assertion.
    #
    # Added after the measured utterance set showed "kandi pappu" resolving to cooked toor dal
    # when it was authored as raw. The cause was the same alias written on two different foods:
    # the last one loaded silently won. Nothing in the app could have detected that, and the
    # figure it produced looked perfectly reasonable.
    rows = c.execute("""
        SELECT alias_norm, GROUP_CONCAT(DISTINCT food_key) AS keys, COUNT(DISTINCT food_key) AS n
        FROM food_aliases GROUP BY alias_norm HAVING n > 1
    """).fetchall()
    if rows:
        failures.append("AMBIGUOUS ALIAS: one spoken name maps to several foods: " +
                        "; ".join(f"'{r[0]}' -> {r[1]}" for r in rows))
    else:
        log("ALIAS ok: every spoken name maps to exactly one food")

    # An alias must not sit on both a real food and a no-data item.
    rows = c.execute("""
        SELECT a.alias_norm, a.food_key, n.item_key
        FROM food_aliases a JOIN no_data_aliases n ON n.alias_norm = a.alias_norm
    """).fetchall()
    if rows:
        failures.append(f"ALIAS COLLISION between a food and a no-data item: {rows}")
    else:
        log("ALIAS ok: no name is both a food and a no-data item")

    # RECIPE ASSERTIONS.
    #
    # A recipe is where a wrong number is most likely to look reasonable, which is the same shape
    # as a dish name collapsing onto one ingredient. These check composition, not plausibility of
    # the total, because a plausible total is exactly what hides a wrong composition.

    # 1. No ingredient row may be silently dropped.
    if recipe_rows_parsed != recipe_rows_inserted:
        failures.append(f"RECIPE: {recipe_rows_parsed - recipe_rows_inserted} ingredient rows were "
                        f"parsed but not inserted. A dropped ingredient leaves a total that still "
                        f"looks reasonable.")
    else:
        log(f"RECIPE ok: all {recipe_rows_inserted} ingredient rows inserted, none dropped")

    # 2. Ingredient weights plus the stated water change must equal the stated yield, exactly.
    rows = c.execute("""
        SELECT r.recipe_key, r.yield_g, r.water_change_g, SUM(i.grams) AS ing_sum
        FROM recipes r JOIN recipe_ingredients i ON i.recipe_key = r.recipe_key
        GROUP BY r.recipe_key
    """).fetchall()
    off = [(k, y, w, s_, s_ + w - y) for k, y, w, s_ in rows if abs(s_ + w - y) > 0.5]
    if off:
        failures.append("RECIPE: ingredients plus water change do not equal the stated yield: " +
                        "; ".join(f"{k} off by {d:+.0f} g" for k, _, _, _, d in off))
    else:
        log(f"RECIPE ok: all {len(rows)} recipes reconcile to their stated yield")

    # 3. Every ingredient must be a real food, never a no-data item and never a typo.
    rows = c.execute("""
        SELECT DISTINCT i.recipe_key, i.food_key FROM recipe_ingredients i
        WHERE i.food_key NOT IN (SELECT food_key FROM foods)
    """).fetchall()
    if rows:
        failures.append(f"RECIPE: ingredients that are not real foods: {rows}")
    else:
        log("RECIPE ok: every ingredient resolves to a real food")
    rows = c.execute("""
        SELECT DISTINCT i.recipe_key, i.food_key FROM recipe_ingredients i
        WHERE i.food_key IN (SELECT item_key FROM no_data_items)
    """).fetchall()
    if rows:
        failures.append(f"RECIPE: ingredients that are no-data items: {rows}")
    else:
        log("RECIPE ok: no ingredient is a no-data item")

    # 4. The absorbed-fat row must equal the figure the recipe documents.
    rows = c.execute("""
        SELECT r.recipe_key, r.absorbed_oil_g,
               COALESCE((SELECT SUM(grams) FROM recipe_ingredients i
                         WHERE i.recipe_key = r.recipe_key
                           AND i.role IN ('ABSORBED_FAT','TEMPER','SHALLOW_FRY_RETAINED')
                           AND i.food_key IN (SELECT food_key FROM foods WHERE food_class='FAT_OIL')), 0)
        FROM recipes r WHERE r.oil_method != 'NONE'
    """).fetchall()
    off = [(k, doc, actual) for k, doc, actual in rows if abs(doc - actual) > 0.5]
    if off:
        failures.append("RECIPE: documented absorbed oil does not match the fat rows: " +
                        "; ".join(f"{k} documents {d:.0f} g but the rows total {a:.0f} g" for k, d, a in off))
    else:
        log(f"RECIPE ok: absorbed oil matches its documented figure in all {len(rows)} recipes with fat")

    # 4b. An alias must not sit on both a food and a dish.
    #
    # "annam" was authored on both the cooked-rice ingredient and a steamed-rice recipe, so which
    # one a person got depended on lookup order. That is the same silent ambiguity the food alias
    # assertion catches, one table over.
    rows = c.execute("""
        SELECT a.alias_norm, a.food_key, ra.recipe_key
        FROM food_aliases a JOIN recipe_aliases ra ON ra.alias_norm = a.alias_norm
    """).fetchall()
    if rows:
        failures.append("RECIPE: a name means both a food and a dish: " +
                        "; ".join(f"'{a}' -> food {f} and dish {r}" for a, f, r in rows))
    else:
        log("RECIPE ok: no name means both a food and a dish")
    rows = c.execute("""
        SELECT ra.alias_norm, ra.recipe_key, n.item_key
        FROM recipe_aliases ra JOIN no_data_aliases n ON n.alias_norm = ra.alias_norm
    """).fetchall()
    if rows:
        failures.append(f"RECIPE: a dish name collides with a no-data item: {rows}")
    else:
        log("RECIPE ok: no dish name collides with a no-data item")

    # 5. The oil share of the finished dish must sit in a defensible band for its method.
    #
    # This replaced a cruder rule that said a deep-fried dish cannot gain water. That rule fired
    # on a correct change and was simply wrong: water_change covers the whole process from raw
    # ingredients to finished dish, and the hydration steps come first. Dal is soaked before it is
    # ground, and flour is hydrated into dough, so most fried items gain weight overall even
    # though the frying step alone drives water off.
    #
    # The band below is the guard that actually matters. A vada reading 745 kcal/100 g in the
    # rejected dataset was an oil share of roughly 50%, which no fried food has. Anything outside
    # these bands means the absorbed figure or the yield is wrong, whichever way the error runs.
    OIL_SHARE_BANDS = {"DEEP_FRY": (0.05, 0.25), "SHALLOW_FRY": (0.01, 0.15), "TEMPER": (0.0, 0.12)}
    rows = c.execute("SELECT recipe_key, oil_method, absorbed_oil_g, yield_g FROM recipes").fetchall()
    out_of_band = []
    for k, method, oil, y in rows:
        band = OIL_SHARE_BANDS.get(method)
        if not band or y <= 0:
            continue
        share = oil / y
        if not (band[0] <= share <= band[1]):
            out_of_band.append(f"{k} ({method}) is {share:.1%}, outside {band[0]:.0%}-{band[1]:.0%}")
    if out_of_band:
        failures.append("RECIPE: oil share outside a defensible band: " + "; ".join(out_of_band))
    else:
        log(f"RECIPE ok: oil share is within band for all {len(rows)} recipes")

    # 6. Mass sanity: a dish cannot lose almost all of its weight, and cannot end up weightless.
    rows = c.execute("""
        SELECT r.recipe_key, r.yield_g, SUM(i.grams) FROM recipes r
        JOIN recipe_ingredients i ON i.recipe_key = r.recipe_key GROUP BY r.recipe_key
    """).fetchall()
    silly = [(k, y, s_) for k, y, s_ in rows if y <= 0 or y < 0.2 * s_ or y > 4.0 * s_]
    if silly:
        failures.append(f"RECIPE: implausible yield against ingredient weight: {silly}")
    else:
        log("RECIPE ok: every yield is plausible against its ingredient weight")

    # 7. Implied moisture must be plausible for how the dish is cooked.
    #
    # THIS IS THE ASSERTION THAT MEASURES THE YIELD. Every other recipe check takes yield_g as
    # given: assertion 2 only proves the arithmetic is self-consistent, and a wrong water figure
    # passes it happily as long as the sum still matches. Nothing caught that until this.
    #
    # It works by deriving the finished dish's moisture-and-ash fraction from its own macros,
    # as 100 minus protein, carbohydrate and fat per 100 g, and comparing it to a band for the
    # cooking method stated in recipes.csv. A wrong yield shows up here immediately, because
    # dividing the same nutrients by a smaller mass makes a wet food look dry.
    #
    # WHAT IT FOUND. Idli was authored at a 300 g yield, which put a steamed rice-and-dal cake at
    # 45% moisture and 227 kcal/100 g, within reach of a dry griddle roti at 258. Steaming cannot
    # do that. The corrected yield of 539 g puts it at 68%, the moisture of boiled rice.
    #
    # WHAT IT IS NOT. The bands are wide on purpose and they are not an accuracy check. They
    # cannot separate a right figure from a nearly right one, and passing says only that the dish
    # is not impossible. Narrowing them without weighed plates to narrow them against would be
    # inventing precision.
    MOISTURE_BANDS = {
        "STEAMED":       (58, 80),   # idli: a steamed batter finishes near boiled rice
        "GRIDDLE_BREAD": (25, 55),   # chapati, dosa: a dry griddle drives water off
        "DEEP_FRIED":    (28, 60),   # frying replaces water with a little retained oil
        "SOFT_GRAIN":    (52, 80),   # rice dishes, upma, poha, pongal
        "MIXED_PLATE":   (50, 80),   # a dry item served with a wet one, e.g. masala dosa
        "GRAVY":         (58, 93),   # curries, pappu, sambar
        "DRY_FRY":       (58, 88),   # vepudu: a vegetable dish cooked down but not dried out
        "CHUTNEY":       (38, 88),   # ranges from coconut to a thin tomato pachadi
        "THIN_SOUP":     (80, 95),   # rasam
        "EGG":           (60, 85),
        "RAW_SALAD":     (78, 95),
    }
    rows = c.execute("SELECT recipe_key, display_name, moisture_class FROM recipes").fetchall()
    bad_class = [k for k, _, m in rows if m not in MOISTURE_BANDS]
    if bad_class:
        failures.append(f"RECIPE: unknown moisture_class on {bad_class}")
    off = []
    for k, name, mclass in rows:
        band = MOISTURE_BANDS.get(mclass)
        if not band:
            continue
        macros = 0.0
        unknown = False
        for nk in ("PROTEIN", "CARBOHYDRATE", "FAT"):
            row = c.execute(
                "SELECT state, amount_per_100g FROM recipe_nutrients "
                "WHERE recipe_key = ? AND nutrient = ?", (k, nk)).fetchone()
            if row is None or row[0] != "MEASURED":
                unknown = True
                break
            macros += row[1]
        if unknown:
            # Nothing to check against; the dish already reads Unknown for that nutrient.
            continue
        moisture = 100.0 - macros
        if not (band[0] <= moisture <= band[1]):
            off.append(f"{k} ({name}, {mclass}) implies {moisture:.0f}% moisture, "
                       f"outside {band[0]}-{band[1]}%")
    if off:
        failures.append("RECIPE: implied moisture is not possible for the stated cooking method. "
                        "The yield is the number to check first:\n  " + "\n  ".join(off))
    else:
        log(f"RECIPE ok: implied moisture is plausible for all {len(rows)} recipes")

    # ---- candidates (spec 4.3) -----------------------------------------------------------
    # Every food and recipe is listed exactly once, with or without contexts, so the sweep is
    # complete; a raw grain or pulse never has a context (Vedant, 20 Sep); a spice, fat, sweet
    # or packaged item has one only with its own serving stated; every context is a LifeContext.
    LIFE_CONTEXTS = {"HOSTEL_STUDENT", "PG_OWN_COOKING", "FIELD_OR_MANUAL_WORKER", "DESK_PROFESSIONAL", "HOMEMAKER"}
    NEVER = {"GRAIN_RAW", "PULSE_RAW"}
    ONLY_WITH_SERVING = {"SPICE", "FAT_OIL", "SWEET", "PACKAGED"}
    food_class_of = {k: cls for k, cls in c.execute("SELECT food_key, food_class FROM foods")}
    recipe_keys = {k for (k,) in c.execute("SELECT recipe_key FROM recipes")}
    seen = set()
    cand_fail = []
    for r in candidates:
        key = r["key"].strip()
        if key in seen:
            cand_fail.append(f"{key} listed twice")
        seen.add(key)
        if key not in food_class_of and key not in recipe_keys:
            cand_fail.append(f"{key} is neither a food nor a recipe")
            continue
        if not r["note"].strip():
            cand_fail.append(f"{key} has no reason")
        contexts = [x.strip() for x in r["contexts"].split("|") if x.strip()]
        bad = [x for x in contexts if x not in LIFE_CONTEXTS]
        if bad:
            cand_fail.append(f"{key}: unknown context {bad}")
        cls = food_class_of.get(key)
        serving = float(r["serving_g"]) if r["serving_g"].strip() else None
        if contexts and cls in NEVER:
            cand_fail.append(f"{key} is {cls}: a raw grain or pulse is an ingredient, never a candidate")
        if contexts and cls in ONLY_WITH_SERVING and serving is None:
            cand_fail.append(f"{key} is {cls} and states no serving: the class unit is a teaspoon, not a helping")
        if serving is not None and not (5.0 <= serving <= 500.0):
            cand_fail.append(f"{key}: serving {serving} g is not a helping")
        for ctx in contexts:
            c.execute("INSERT INTO candidates VALUES (?,?,?,?,?)",
                      (key, ctx, r["suggest_as"].strip() or None, serving, r["note"].strip()))
    unlisted = (set(food_class_of) | recipe_keys) - seen
    if unlisted:
        cand_fail.append(f"not in candidates.csv at all (list it, with or without a context): {sorted(unlisted)}")
    if cand_fail:
        failures.append("CANDIDATES: " + "; ".join(cand_fail))
    else:
        n_keys = c.execute("SELECT COUNT(DISTINCT key) FROM candidates").fetchone()[0]
        per_ctx = c.execute("SELECT context, COUNT(*) FROM candidates GROUP BY context ORDER BY context").fetchall()
        log(f"CANDIDATES ok: {n_keys} of {len(seen)} foods and recipes may be suggested; per context " +
            ", ".join(f"{ctx} {n}" for ctx, n in per_ctx))

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
