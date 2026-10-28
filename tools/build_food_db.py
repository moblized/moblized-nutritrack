#!/usr/bin/env python3
"""Build NutriTrack's bundled offline food database from USDA FoodData Central.

Downloads the Foundation Foods, SR Legacy and FNDDS (survey) CSV releases,
keeps the nutrients the app tracks, normalises units and writes a compact
SQLite database with full-text search that ships inside the APK.

Usage:
    python3 tools/build_food_db.py --out app/src/main/assets/usda.db [--cache tools/cache]
    python3 tools/build_food_db.py --out test.db --local-dir path/with/extracted/csvs   # testing
"""
import argparse
import csv
import io
import json
import math
import re
import os
import sqlite3
import sys
import time
import urllib.request
import zipfile

BASE = "https://fdc.nal.usda.gov/fdc-datasets/"

# (key, zip file, data_type kept from food.csv, source code stored in the db)
DATASETS = [
    ("sr_legacy", "FoodData_Central_sr_legacy_food_csv_2018-04.zip", "sr_legacy_food", 0),
    ("foundation", "FoodData_Central_foundation_food_csv_2026-04-30.zip", "foundation_food", 1),
    ("fndds", "FoodData_Central_survey_food_csv_2024-10-31.zip", "survey_fndds_food", 2),
]

# FDC nutrient id -> unit the app expects.
TRACKED = {
    1008: "KCAL", 1018: "G", 1057: "MG", 1051: "G",
    1005: "G", 1079: "G", 2000: "G", 1235: "G", 1009: "G",
    1004: "G", 1258: "G", 1292: "G", 1293: "G", 1257: "G", 1253: "MG",
    1404: "G", 1278: "G", 1272: "G", 1280: "G", 1269: "G",
    1003: "G",
    1210: "G", 1211: "G", 1212: "G", 1213: "G", 1214: "G", 1215: "G", 1216: "G",
    1217: "G", 1218: "G", 1219: "G", 1220: "G", 1221: "G", 1222: "G", 1223: "G",
    1224: "G", 1225: "G", 1226: "G", 1227: "G",
    1106: "UG", 1107: "UG", 1123: "UG", 1122: "UG", 1162: "MG", 1114: "UG",
    1109: "MG", 1185: "UG", 1165: "MG", 1166: "MG", 1167: "MG", 1170: "MG",
    1175: "MG", 1176: "UG", 1190: "UG", 1178: "UG", 1180: "MG",
    1087: "MG", 1098: "MG", 1089: "MG", 1090: "MG", 1101: "MG", 1091: "MG",
    1092: "MG", 1103: "UG", 1093: "MG", 1095: "MG", 1100: "UG", 1099: "UG",
}

# When the canonical id is missing, use the first alternative that exists.
FALLBACKS = {
    1008: [2048, 2047],   # energy: Atwater specific / general factors (Foundation foods)
    1005: [1050],         # carbohydrate by summation
    2000: [1063],         # total sugars
    1404: [1270],         # ALA from total 18:3
    1269: [1316],         # linoleic acid n-6 c,c
    1190: [1177],         # folate DFE from total folate
}
# Alternatives whose unit cannot be converted automatically: id -> (factor, resulting unit)
SPECIAL = {1110: (1 / 40.0, "UG")}  # vitamin D IU -> ug
FALLBACKS.setdefault(1114, []).append(1110)

UNIT_SCALE = {"G": 1.0, "MG": 1e-3, "UG": 1e-6}

# Foods USDA lacks (erythritol, food coloring, ...), entered from their nutrition labels.
EXTRA_FOODS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "extra_foods.json")
EXTRA_SOURCE = 3
# Label field -> FDC nutrient id; units match TRACKED (g, mg, ug, kcal).
LABEL_NUTRIENTS = {
    "kcal": 1008, "fat": 1004, "sat_fat": 1258, "trans_fat": 1257, "cholesterol": 1253, "sodium": 1093,
    "carbs": 1005, "fiber": 1079, "sugars": 2000, "added_sugars": 1235, "protein": 1003, "alcohol": 1018,
    "vit_d": 1114, "calcium": 1087, "iron": 1089, "potassium": 1092, "vit_a": 1106, "riboflavin": 1166,
    "vit_b12": 1178, "phosphorus": 1091, "magnesium": 1090, "zinc": 1095, "selenium": 1103,
}


def add_extra_foods(db, path=EXTRA_FOODS):
    """Inserts the label-based foods into `foods`; returns how many were added."""
    with open(path, encoding="utf-8") as fh:
        extras = json.load(fh)["foods"]
    for f in extras:
        grams = float(f["serving"][1])
        per100 = {}
        for key, value in f["per_serving"].items():
            nid = LABEL_NUTRIENTS[key]
            if nid not in TRACKED:
                raise SystemExit(f"{f['description']}: {key} is not a tracked nutrient")
            per100[nid] = sig(value * 100.0 / grams)
        enc = ";".join(f"{k}:{v:g}" for k, v in sorted(per100.items()))
        portions = [[label, round(float(g), 2)] for label, g in f["portions"]]
        db.execute("INSERT INTO foods VALUES (?,?,?,?,?,?,?,?)", (
            int(f["id"]), f["description"], f["category"], EXTRA_SOURCE, per100.get(1008), len(per100), enc,
            json.dumps(portions, separators=(",", ":")),
        ))
    return len(extras)


# Everyday single foods from the USDA survey database (FNDDS), grouped by food group.
GROUPS = {
    "veg": ["Broccoli", "Cabbage", "Carrots", "Corn", "Lettuce and lettuce salads", "Onions",
            "Other dark green vegetables", "Other red and orange vegetables", "Other starchy vegetables",
            "Other vegetables and combinations", "Spinach", "String beans", "Tomatoes",
            "White potatoes, baked or boiled"],
    "fruit": ["Apples", "Bananas", "Blueberries and other berries", "Citrus fruits", "Dried fruits", "Grapes",
              "Mango and papaya", "Melons", "Other fruits and fruit salads", "Peaches and nectarines", "Pears",
              "Pineapple", "Strawberries"],
    "dairy": ["Cheese", "Cottage/ricotta cheese", "Milk, lowfat", "Milk, nonfat", "Milk, reduced fat", "Milk, whole",
              "Yogurt, Greek", "Yogurt, regular", "Plant-based milk", "Plant-based yogurt"],
    "protein": ["Beef, excludes ground", "Ground beef", "Chicken, whole pieces", "Turkey, duck, other poultry", "Pork",
                "Fish", "Shellfish", "Eggs and omelets", "Soy and meat-alternative products"],
    "legumes": ["Nuts and seeds", "Beans, peas, legumes"],
    "grains": ["Oatmeal", "Grits and other cooked cereals", "Pasta, noodles, cooked grains", "Rice",
               "Ready-to-eat cereal, lower sugar (=<21.2g/100g)", "Tortillas", "Yeast breads"],
}
GROUP_OF = {cat: g for g, cats in GROUPS.items() for cat in cats}

EXCLUDE = re.compile(
    r"\b(fried|breaded|battered|coated|fritter|tempura|sauce|gravy|creamed|candied|glazed|au gratin|"
    r"scalloped|casserole|omelet|souffle|with cheese|with butter|with margarine|with oil|fat added|"
    r"from fast food|from restaurant|sandwich|salad dressing|dip|chips|pie|pudding|syrup|sweetened|"
    r"chocolate|frosted|honey|sugar|jelly|jam|baby|infant|toddler|formula|liver|organ|giblets|chitterlings|"
    r"tripe|pickled|smoked|jerky|sausage|bacon|ham|salami|bologna|frankfurter|hot dog|ns as to|nfs|"
    r"evaporated|condensed|spread|lard|imitation|reduced calorie|light)\b",
    re.I)

def portion_score(label):
    l = label.lower()
    if re.search(r"\b(lb|pound|package|recipe|yield|can|jar|bottle|box|loaf|bag|quart|gallon|pint|guideline)\b", l):
        return -1
    if re.search(r"\b(medium|large|small|fruit|egg|slice|breast|thigh|drumstick|fillet|steak|chop|patty|container|"
                 r"stalk|spear|ear|whole|piece)\b", l):
        sc = 3
    elif re.search(r"\b(cup|oz)\b", l):
        sc = 2
    elif l == "typical serving":
        sc = 1.5
    else:
        sc = 1
    if re.search(r"\bmedium\b", l): sc += 0.5
    if re.search(r"\bfl oz\b|tbsp|tsp|pureed|mashed", l): sc -= 1
    if not re.match(r"\s*\d", l) and l != "typical serving": sc -= 1.5
    return sc

def choose_portion(portions, kcal100):
    best = None
    for label, grams in portions:
        if not (15 <= grams <= 400):
            continue
        sc = portion_score(label)
        if sc < 0:
            continue
        kcal = (kcal100 or 0) * grams / 100
        if kcal > 280:
            sc -= 1.5 * math.log(kcal / 280)
        if best is None or sc > best[0]:
            best = (sc, label, grams)
    return (best[1], best[2]) if best else None


def suggestion(description, category, source, kcal, portions):
    """(portion label, grams, group) when this food belongs in the "What to eat" pool, else None."""
    if source != 2 or category not in GROUP_OF or EXCLUDE.search(description):
        return None
    p = choose_portion(portions, kcal)
    return (p[0], p[1], GROUP_OF[category]) if p else None


def log(*a):
    print(*a, file=sys.stderr, flush=True)


def download(url, dest):
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        log(f"cached  {os.path.basename(dest)}")
        return
    tmp = dest + ".part"
    for attempt in range(5):
        try:
            log(f"download {url}")
            req = urllib.request.Request(url, headers={
                "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) NutriTrack-build/1.0",
            })
            with urllib.request.urlopen(req, timeout=300) as r, open(tmp, "wb") as f:
                while True:
                    chunk = r.read(1 << 20)
                    if not chunk:
                        break
                    f.write(chunk)
            os.replace(tmp, dest)
            return
        except Exception as e:  # noqa: BLE001
            log(f"  attempt {attempt + 1} failed: {e}")
            time.sleep(5 * (attempt + 1))
    raise SystemExit(f"could not download {url}")


class Source:
    """Reads named CSV files from a zip archive or a directory (searched recursively)."""

    def __init__(self, path):
        self.path = path
        self.zip = zipfile.ZipFile(path) if path.endswith(".zip") else None

    def _find(self, name):
        if self.zip:
            for n in self.zip.namelist():
                if os.path.basename(n) == name:
                    return n
            return None
        for root, _, files in os.walk(self.path):
            if name in files:
                return os.path.join(root, name)
        return None

    def rows(self, name):
        found = self._find(name)
        if not found:
            return
        if self.zip:
            fh = io.TextIOWrapper(self.zip.open(found), encoding="utf-8-sig", newline="")
        else:
            fh = open(found, encoding="utf-8-sig", newline="")
        with fh:
            yield from csv.DictReader(fh)


def diagnose(src, kept):
    """Print what a dataset looks like when no nutrient values could be matched."""
    names = src.zip.namelist() if src.zip else []
    log(f"  DIAG files: {names[:60]}")
    log(f"  DIAG kept ids sample: {sorted(kept)[:5]}")
    for name in ("food_nutrient.csv", "food.csv", "nutrient.csv"):
        log(f"  DIAG {name}:")
        for i, r in enumerate(src.rows(name) or []):
            log(f"    {dict(r)}")
            if i >= 2:
                break
    ids = {}
    for i, r in enumerate(src.rows("food_nutrient.csv") or []):
        ids[r.get("nutrient_id")] = ids.get(r.get("nutrient_id"), 0) + 1
        if i > 200000:
            break
    log(f"  DIAG nutrient_id counts: {sorted(ids.items(), key=lambda kv: -kv[1])[:40]}")


def num(v):
    try:
        x = float(v)
    except (TypeError, ValueError):
        return None
    return x if x == x else None


def fmt_amount(a):
    x = num(a)
    if x is None:
        return ""
    if abs(x - round(x)) < 1e-9:
        return str(int(round(x)))
    return f"{x:g}"


def portion_label(row, units):
    desc = (row.get("portion_description") or "").strip()
    if desc and desc.lower() != "quantity not specified":
        return desc
    if desc.lower() == "quantity not specified":
        return "typical serving"
    unit = units.get((row.get("measure_unit_id") or "").strip(), "")
    if unit.lower() == "undetermined":
        unit = ""
    mod = (row.get("modifier") or "").strip()
    parts = [fmt_amount(row.get("amount")), unit, mod]
    label = " ".join(p for p in parts if p).strip()
    return label


def sig(x):
    return float(f"{x:.4g}")


def build(sources, out_path):
    foods = {}       # fdc_id -> dict
    nutrients = {}   # fdc_id -> {nutrient_id: amount}
    portions = {}    # fdc_id -> [(label, grams)]
    unit_of = {}     # nutrient id -> unit name
    by_number = {}   # legacy nutrient number (e.g. 208) -> FDC nutrient id (e.g. 1008)

    for key, src, data_type, code in sources:
        log(f"reading {key}")
        for r in src.rows("nutrient.csv"):
            nid = int(num(r.get("id")) or 0)
            if nid:
                unit_of[nid] = (r.get("unit_name") or "").strip().upper()
                nbr = num(r.get("nutrient_nbr"))
                if nbr:
                    prev = by_number.get(int(nbr))
                    if prev is None or (prev not in TRACKED and nid in TRACKED):
                        by_number[int(nbr)] = nid

        cats = {}
        for r in src.rows("food_category.csv"):
            cats[(r.get("id") or "").strip()] = (r.get("description") or "").strip()
        wweia = {}
        for r in src.rows("wweia_food_category.csv"):
            wweia[(r.get("wweia_food_category") or "").strip()] = (
                r.get("wweia_food_category_description") or "").strip()

        kept = set()
        for r in src.rows("food.csv"):
            if (r.get("data_type") or "").strip() != data_type:
                continue
            fid = int(num(r.get("fdc_id")) or 0)
            desc = (r.get("description") or "").strip()
            if not fid or not desc:
                continue
            cat_id = (r.get("food_category_id") or "").strip()
            category = (wweia.get(cat_id) if code == 2 else None) or cats.get(cat_id) or wweia.get(cat_id) or ""
            foods[fid] = {"description": desc, "category": category, "source": code}
            kept.add(fid)
        log(f"  {len(kept)} foods")

        wanted = set(TRACKED)
        for alts in FALLBACKS.values():
            wanted.update(alts)
        n_rows = 0
        for r in src.rows("food_nutrient.csv"):
            fid = int(num(r.get("fdc_id")) or 0)
            if fid not in kept:
                continue
            nid = int(num(r.get("nutrient_id")) or 0)
            if 0 < nid < 1000:
                # FNDDS lists legacy nutrient numbers (208 = energy) instead of FDC ids.
                nid = by_number.get(nid, nid)
            if nid not in wanted:
                continue
            amt = num(r.get("amount"))
            if amt is None or amt < 0:
                continue
            nutrients.setdefault(fid, {})[nid] = amt
            n_rows += 1
        log(f"  {n_rows} nutrient values")
        if n_rows == 0:
            diagnose(src, kept)

        units = {}
        for r in src.rows("measure_unit.csv"):
            units[(r.get("id") or "").strip()] = (r.get("name") or "").strip()
        for r in src.rows("food_portion.csv"):
            fid = int(num(r.get("fdc_id")) or 0)
            if fid not in kept:
                continue
            grams = num(r.get("gram_weight"))
            if not grams or grams <= 0:
                continue
            label = portion_label(r, units)
            if not label or label.lower().startswith("guideline amount"):
                continue
            portions.setdefault(fid, []).append((num(r.get("seq_num")) or 0, label, grams))

    # Resolve fallbacks and units.
    def to_expected(nid, value, from_unit):
        want = TRACKED[nid]
        if nid in SPECIAL:
            return value
        if from_unit == want or not from_unit:
            return value
        if from_unit in UNIT_SCALE and want in UNIT_SCALE:
            return value * UNIT_SCALE[from_unit] / UNIT_SCALE[want]
        if from_unit == "KJ" and want == "KCAL":
            return value / 4.184
        return None

    unit_warnings = set()
    for nid, want in TRACKED.items():
        have = unit_of.get(nid)
        if have and have != want:
            unit_warnings.add(f"{nid}: {have} -> {want}")
    if unit_warnings:
        log("unit conversions:", ", ".join(sorted(unit_warnings)))

    if os.path.exists(out_path):
        os.remove(out_path)
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    db = sqlite3.connect(out_path)
    db.execute("PRAGMA page_size = 4096")
    db.execute("PRAGMA journal_mode = DELETE")
    db.execute("""CREATE TABLE foods (
        id INTEGER PRIMARY KEY,
        description TEXT NOT NULL,
        category TEXT NOT NULL,
        source INTEGER NOT NULL,
        kcal REAL,
        n_count INTEGER NOT NULL,
        nutrients TEXT NOT NULL,
        portions TEXT NOT NULL)""")
    db.execute("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
    db.execute("CREATE TABLE suggest (id INTEGER PRIMARY KEY, label TEXT NOT NULL, grams REAL NOT NULL, grp TEXT NOT NULL)")

    written = 0
    with_energy = 0
    pooled = 0
    for fid, f in foods.items():
        raw = nutrients.get(fid, {})
        out = {}
        for nid in TRACKED:
            val = None
            if nid in raw:
                val = to_expected(nid, raw[nid], unit_of.get(nid, TRACKED[nid]))
            if val is None:
                for alt in FALLBACKS.get(nid, []):
                    if alt in raw:
                        if alt in SPECIAL:
                            factor, _ = SPECIAL[alt]
                            val = raw[alt] * factor
                        else:
                            alt_unit = unit_of.get(alt, TRACKED[nid])
                            val = to_expected(nid, raw[alt], alt_unit)
                        if val is not None:
                            break
            if val is not None:
                out[nid] = sig(val)
        if not out:
            continue
        kcal = out.get(1008)
        if kcal is None and all(k in out for k in (1003, 1004, 1005)):
            kcal = sig(out[1003] * 4 + out[1005] * 4 + out[1004] * 9)
            out[1008] = kcal
        if kcal is not None:
            with_energy += 1

        seen = set()
        plist = []
        for _, label, grams in sorted(portions.get(fid, []), key=lambda p: p[0]):
            k = label.lower()
            if k in seen:
                continue
            seen.add(k)
            plist.append([label, round(grams, 2)])
            if len(plist) >= 12:
                break
        enc = ";".join(f"{k}:{v:g}" for k, v in sorted(out.items()))
        db.execute("INSERT INTO foods VALUES (?,?,?,?,?,?,?,?)", (
            fid, f["description"], f["category"], f["source"], kcal, len(out), enc,
            json.dumps(plist, separators=(",", ":")),
        ))
        written += 1
        sug = suggestion(f["description"], f["category"], f["source"], kcal, plist)
        if sug:
            db.execute("INSERT INTO suggest VALUES (?,?,?,?)", (fid, sug[0], sug[1], sug[2]))
            pooled += 1

    extra = add_extra_foods(db)
    written += extra
    with_energy += extra
    log(f"added {extra} foods from nutrition labels")

    db.execute('CREATE VIRTUAL TABLE food_fts USING fts4(content="foods", description)')
    db.execute("INSERT INTO food_fts(food_fts) VALUES('rebuild')")
    db.execute("INSERT INTO meta VALUES ('built', ?)", (time.strftime("%Y-%m-%d"),))
    db.execute("INSERT INTO meta VALUES ('foods', ?)", (str(written),))
    db.execute("INSERT INTO meta VALUES ('suggest', ?)", (str(pooled),))
    db.execute("PRAGMA user_version = 1")
    db.commit()
    db.execute("VACUUM")
    db.close()

    log(f"suggestion pool: {pooled} foods")
    log(f"wrote {written} foods ({with_energy} with energy) -> {out_path} "
        f"({os.path.getsize(out_path) / 1e6:.1f} MB)")
    return written, with_energy, pooled


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--cache", default=os.path.join(os.path.dirname(__file__), "cache"))
    ap.add_argument("--local-dir", help="directory with one sub-folder per dataset key (testing)")
    ap.add_argument("--min-foods", type=int, default=10000)
    args = ap.parse_args()

    sources = []
    for key, zip_name, data_type, code in DATASETS:
        if args.local_dir:
            path = os.path.join(args.local_dir, key)
            if not os.path.isdir(path):
                log(f"skip {key} (no local data)")
                continue
        else:
            os.makedirs(args.cache, exist_ok=True)
            path = os.path.join(args.cache, zip_name)
            download(BASE + zip_name, path)
        sources.append((key, Source(path), data_type, code))

    written, with_energy, pooled = build(sources, args.out)
    if not args.local_dir and pooled < 400:
        raise SystemExit(f"only {pooled} foods in the suggestion pool, expected at least 400")
    if written < args.min_foods:
        raise SystemExit(f"only {written} foods written, expected at least {args.min_foods}")
    if with_energy < written * 0.9:
        raise SystemExit(f"energy missing for too many foods ({with_energy}/{written})")


if __name__ == "__main__":
    main()
