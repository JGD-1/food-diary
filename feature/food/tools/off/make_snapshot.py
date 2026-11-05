"""Makes the app's bundled Open Food Facts list (Round 9) from the full Open Food Facts CSV export.

Keeps products sold in the Netherlands or Belgium that have a name, a barcode and calories, and writes
one line per product, sorted by barcode, gzipped:

    barcode|name|brand|kcal|protein|carbs|fat|fibre|sugar|salt|serving_g|package_g|drink

Numbers are per 100 g (or 100 ml for drinks), with a dot; an empty fibre, sugar, salt, serving or pack
means "not known". Same rules as OpenFoodFacts.toFood in the app, so a bundled product and one looked up
online give the same food.

Usage: python3 make_snapshot.py <products.csv.gz or -> <out.tsv.gz>
Data: Open Food Facts, openfoodfacts.org, Open Database License (ODbL); contents under the DbCL.
"""

import csv
import datetime
import gzip
import io
import sys

COUNTRIES = {"en:netherlands", "en:belgium"}
NEEDED = ["code", "product_name", "brands", "countries_tags", "energy-kcal_100g", "energy_100g"]


def number(row, key, top=None):
    """The column as a number, or None when it is empty, not a number, negative or above [top]."""
    text = (row.get(key) or "").strip().replace(",", ".")
    if not text:
        return None
    try:
        value = float(text)
    except ValueError:
        return None
    if value != value or value < 0 or (top is not None and value > top):
        return None
    return value


def text(value):
    return " ".join((value or "").replace("|", "/").split())


def fmt(value):
    if value is None:
        return ""
    return f"{value:.2f}".rstrip("0").rstrip(".")


def amount(row, key, unit_key):
    """A serving or pack size in g (ml counted as g), like OpenFoodFacts.amount in the app."""
    unit = (row.get(unit_key) or "").strip().lower()
    if unit and unit not in ("g", "ml"):
        return None
    value = number(row, key, top=20000)
    return value if value else None


def to_line(row):
    code = (row.get("code") or "").strip()
    if not (8 <= len(code) <= 14 and code.isdigit()):
        return None
    # Shop barcodes (weighed meat, starting with 2) are never looked up in Open Food Facts (decisions.md).
    if len(code) == 13 and code.startswith("2"):
        return None
    countries = set((row.get("countries_tags") or "").split(","))
    if not countries & COUNTRIES:
        return None
    name = text(row.get("product_name"))
    if not name:
        return None
    kcal = number(row, "energy-kcal_100g", top=950)
    if kcal is None:
        kj = number(row, "energy_100g", top=4000)
        kcal = kj / 4.184 if kj is not None else None
    if kcal is None:
        return None
    grams = [number(row, k, top=100) for k in ("proteins_100g", "carbohydrates_100g", "fat_100g")]
    fibre = number(row, "fiber_100g", top=100)
    # Typing slips (kJ typed as kcal, a lost comma): drop products whose calories are far from what their
    # protein, carbs, fat and fibre add up to, like the app's "please check" hint on label scans.
    if all(g is not None for g in grams):
        expected = 4 * grams[0] + 4 * grams[1] + 9 * grams[2] + 2 * (fibre or 0.0)
        if abs(kcal - expected) > max(100.0, 0.5 * expected):
            return None
    sugar = number(row, "sugars_100g", top=100)
    salt = number(row, "salt_100g", top=100)
    if salt is None:
        sodium = number(row, "sodium_100g", top=40)
        salt = sodium * 2.5 if sodium is not None else None
    categories = set((row.get("categories_tags") or "").split(","))
    drink = "en:beverages" in categories and not categories & {"en:dairies", "en:milks"}
    brand = text((row.get("brands") or "").split(",")[0])
    return code, "|".join([
        code, name, brand, fmt(kcal), *(fmt(g or 0.0) for g in grams), fmt(fibre), fmt(sugar), fmt(salt),
        fmt(amount(row, "serving_quantity", "serving_quantity_unit")),
        fmt(amount(row, "product_quantity", "product_quantity_unit")),
        "1" if drink else "0",
    ])


def make(source, out):
    csv.field_size_limit(sys.maxsize)
    reader = csv.DictReader(source, delimiter="\t", quoting=csv.QUOTE_NONE)
    missing = [k for k in NEEDED if k not in (reader.fieldnames or [])]
    if missing:
        raise SystemExit(f"Open Food Facts export lacks columns: {missing}")
    lines = {}
    seen = 0
    for row in reader:
        seen += 1
        made = to_line(row)
        if made:
            lines[made[0]] = made[1]
    today = datetime.date.today().isoformat()
    out.write(f"# Product data from Open Food Facts (openfoodfacts.org), ODbL, export of {today}; NL/BE products with calories\n")
    out.write("barcode|name|brand|kcal|protein|carbs|fat|fibre|sugar|salt|serving_g|package_g|drink\n")
    for code in sorted(lines):
        out.write(lines[code] + "\n")
    print(f"Read {seen} products, kept {len(lines)}", file=sys.stderr)
    return len(lines)


def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    raw = sys.stdin.buffer if sys.argv[1] == "-" else open(sys.argv[1], "rb")
    if sys.argv[1].endswith(".gz"):
        raw = gzip.open(raw)
    source = io.TextIOWrapper(raw, encoding="utf-8", errors="replace", newline="")
    # mtime=0 keeps the file the same when the data is the same, so no needless commits.
    with gzip.GzipFile(sys.argv[2], "wb", mtime=0) as gz, io.TextIOWrapper(gz, encoding="utf-8", newline="\n") as out:
        kept = make(source, out)
    if kept < 1000:
        raise SystemExit(f"Only {kept} products kept; the export looks wrong, not saving it")


if __name__ == "__main__":
    main()
