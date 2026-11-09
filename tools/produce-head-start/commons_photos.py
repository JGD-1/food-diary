"""
Downloads openly licensed photos of vegetables from Wikimedia Commons for produce_head_start.py (Round 15),
for kinds Open Images has too few photos of. Only photos whose licence allows reuse (public domain, CC0,
CC BY, CC BY-SA) are kept; their source and licence are listed in <work>/commons/sources.csv. The photos
themselves never go into the app, only the numbers trained from them.

    venv\\Scripts\\python commons_photos.py <work folder>

Slow on purpose (about one request a second), as Wikimedia asks of automatic clients.
"""
import csv, json, os, sys, time, urllib.parse, urllib.request

UA = {"User-Agent": "food-diary-training/1.0 (personal non-commercial project; produce recogniser)"}
PER_KIND = 150
OK_LICENCES = ("public domain", "pd", "cc0", "cc by", "cc-by", "cc by-sa", "cc-by-sa")

# The app's search word -> Commons categories to look in (the first ones are the best).
CATEGORIES = {
    "spinach": ["Spinach", "Spinacia oleracea"],
    "leek": ["Leeks", "Allium porrum", "Allium ampeloprasum var. porrum", "Leek (vegetable)", "Leeks in markets"],
    "lettuce": ["Lettuce", "Iceberg lettuce", "Romaine lettuce", "Butterhead lettuce"],
    "celery": ["Celery", "Celery stalks"],
    "brussels sprouts": ["Brussels sprouts as food", "Brussels sprouts", "Brassica oleracea var. gemmifera"],
    "kale": ["Brassica oleracea var. sabellica", "Curly kale", "Kale"],
    "beetroot": ["Beetroot", "Beetroots", "Beets (food)"],
    "sweet potato": ["Sweet potatoes", "Sweet potato"],
    "fennel": ["Florence fennel", "Fennel bulbs", "Foeniculum vulgare var. azoricum"],
    "parsnip": ["Parsnips", "Pastinaca sativa"],
    "turnip": ["Turnips", "Brassica rapa subsp. rapa"],
    "avocado": ["Avocado as food", "Avocado", "Avocados"],
    "ginger": ["Ginger", "Ginger root"],
    "coriander": ["Coriander leaves", "Coriander (herb)", "Cilantro"],
    "oyster mushroom": ["Oyster mushrooms", "Pleurotus ostreatus (food)", "Pleurotus ostreatus"],
    "shiitake": ["Shiitake", "Lentinula edodes"],
    "chicory": ["Belgian endive", "Witloof", "Chicory (vegetable)"],
    "radicchio": ["Radicchio"],
    "onion welsh": ["Scallions", "Spring onions"],
    "cauliflower": ["Cauliflowers", "Cauliflower"],
    # Kinds the first head start knew that Open Images has few photos of.
    "aubergine": ["Brinjal (food)", "Eggplants", "Aubergines"],
    "beans french": ["Green beans"],
    "blueberries": ["Blueberries"],
    "kiwi": ["Kiwifruit", "Green kiwifruit"],
    "peas": ["Green peas", "Peas (food)", "Peas in pods"],
    "raspberries": ["Raspberries"],
}


def api(params):
    time.sleep(1.0)
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode({**params, "format": "json"})
    for attempt in range(5):
        try:
            return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60))
        except urllib.error.HTTPError as e:
            if e.code != 429:
                raise
            time.sleep(10 * (attempt + 1))
    raise RuntimeError("Commons keeps saying too many requests")


def fetch(url):
    """One photo (a standard 500 px thumbnail), waiting longer each time Commons says "too many"."""
    for attempt in range(6):
        time.sleep(1.5)
        try:
            return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60).read()
        except urllib.error.HTTPError as e:
            if e.code != 429:
                return None
            time.sleep(15 * (attempt + 1))
        except Exception:
            return None
    return None


def photos_in(category, wanted):
    found, cont = [], {}
    while len(found) < wanted:
        d = api({"action": "query", "generator": "categorymembers", "gcmtitle": "Category:" + category,
                 "gcmtype": "file", "gcmlimit": "50", "prop": "imageinfo", "iiprop": "url|extmetadata|mime",
                 "iiurlwidth": "500", **cont})
        for page in d.get("query", {}).get("pages", {}).values():
            info = (page.get("imageinfo") or [{}])[0]
            meta = info.get("extmetadata", {})
            licence = meta.get("LicenseShortName", {}).get("value", "")
            if info.get("mime") != "image/jpeg" or not licence.lower().startswith(OK_LICENCES):
                continue
            found.append((page["title"], info.get("thumburl"), licence, info.get("descriptionurl")))
        if "continue" not in d:
            break
        cont = d["continue"]
    return found


def main(work):
    folder = os.path.join(work, "commons")
    os.makedirs(folder, exist_ok=True)
    listing = os.path.join(folder, "sources.csv")
    done = set()
    if os.path.exists(listing):
        with open(listing, encoding="utf8") as f:
            done = {row[1] for row in csv.reader(f)}
    with open(listing, "a", encoding="utf8", newline="") as out:
        writer = csv.writer(out)
        for word, cats in CATEGORIES.items():
            have = sum(1 for _ in os.scandir(os.path.join(folder, word))) if os.path.isdir(os.path.join(folder, word)) else 0
            if have >= PER_KIND:
                continue
            os.makedirs(os.path.join(folder, word), exist_ok=True)
            seen = set()
            for cat in cats:
                if have >= PER_KIND:
                    break
                try:
                    items = photos_in(cat, PER_KIND * 2)
                except Exception as e:
                    print(word, cat, "skipped:", e, flush=True)
                    continue
                for title, thumb, licence, page in items:
                    if have >= PER_KIND or title in done or title in seen or not thumb:
                        continue
                    seen.add(title)
                    path = os.path.join(folder, word, "%03d.jpg" % have)
                    data = fetch(thumb)
                    if data is None:
                        print("skip", title, flush=True)
                        continue
                    with open(path, "wb") as f:
                        f.write(data)
                    writer.writerow([word, title, licence, page])
                    out.flush()
                    have += 1
            print(word, have, "photos", flush=True)


if __name__ == "__main__":
    main(sys.argv[1])
