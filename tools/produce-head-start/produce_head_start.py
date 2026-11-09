"""
Makes feature/camera/src/main/assets/produce_head_start.bin: a small layer on top of the phone's picture
fingerprints (food_embedder.tflite, MobileNet-V3 small) that says which fruit or vegetable is in view.
Rebuilt in Round 15 (the first version's script was lost with GitHub); see feature/camera/MODELS.md.

Photos, none of them go into the app (only the trained numbers):
- Open Images (storage.googleapis.com/openimages), images CC BY 2.0 by their authors: box crops of each kind
  where there are boxes, else whole photos with a human-checked label; validation photos without food as "no food".
- Wikimedia Commons (commons_photos.py) for kinds Open Images has too few photos of; licences in commons/sources.csv.

    python -m venv venv
    venv\\Scripts\\pip install ai-edge-litert numpy pillow
    venv\\Scripts\\python commons_photos.py <work>
    venv\\Scripts\\python produce_head_start.py <work> <repo>

<work> holds the Open Images lists: oidv7-class-descriptions.csv, oidv7-val-annotations-human-imagelabels.csv,
and train-boxes-veg.csv / train-labels-veg.csv (oidv6-train-annotations-bbox.csv and
oidv7-train-annotations-human-imagelabels.csv filtered to the kinds below with filter_labels.py), plus the
downloaded photos and fingerprints.npz, so a second run only trains again.
"""
import csv, io, json, os, random, struct, sys, urllib.request
from concurrent.futures import ThreadPoolExecutor

import numpy as np
from PIL import Image

# Open Images name -> the app's search word (English; search also knows the Dutch NEVO names).
OPEN_IMAGES = {
    "Apple": "apple", "Artichoke": "artichoke", "Banana": "banana", "Bell pepper": "sweet pepper",
    "Blueberry": "blueberries", "Broccoli": "broccoli", "Cabbage": "cabbage", "Cantaloupe": "melon", "Melon": "melon",
    "Carrot": "carrot", "Cauliflower": "cauliflower", "Chili pepper": "chili pepper", "Common fig": "figs",
    "Corn": "sweetcorn", "Sweet corn": "sweetcorn", "Cucumber": "cucumber", "Eggplant": "aubergine",
    "Garden Asparagus": "asparagus", "Garlic": "garlic", "Grape": "grapes", "Grapefruit": "grapefruit",
    "Green bean": "beans french", "Kiwifruit": "kiwi", "Mango": "mango", "Mushroom": "mushroom", "Onion": "onions",
    "Pea": "peas", "Peach": "peach", "Pear": "pear", "Pineapple": "pineapple", "Pomegranate": "pomegranate",
    "Potato": "potatoes", "Pumpkin": "pumpkin", "Radish": "radish", "Raspberry": "raspberries",
    "Scallion": "onion welsh", "Strawberry": "strawberries", "Tomato": "tomato", "Watermelon": "watermelon",
    "Zucchini": "courgette",
    # Round 15
    "Spinach": "spinach", "Lettuce": "lettuce", "Beetroot": "beetroot", "Avocado": "avocado", "Coriander": "coriander",
    "Sweet potato": "sweet potato",
}
PER_KIND = 150          # photos per kind at most
MIN_KIND = 40           # kinds with fewer photos are left out (too few to learn from)
NO_FOOD = 600           # photos without food
MIN_SURE = 0.35         # HeadStart.MIN_SURE in the app
SIZE = 224
random.seed(15)


def read_csv(path):
    with open(path, encoding="utf8") as f:
        yield from csv.reader(f)


def pick(work):
    names = {r[0]: r[1] for r in read_csv(os.path.join(work, "oidv7-class-descriptions.csv"))}
    wanted = {i: OPEN_IMAGES[n] for i, n in names.items() if n in OPEN_IMAGES}
    # Box rows: ImageID,Source,LabelName,Confidence,XMin,XMax,YMin,YMax,... A crop shows the food itself.
    boxes, labels = {}, {}
    for r in read_csv(os.path.join(work, "train-boxes-veg.csv")):
        if r[2] in wanted:
            x0, x1, y0, y1 = map(float, r[4:8])
            if (x1 - x0) * (y1 - y0) >= 0.04:  # big enough to see
                boxes.setdefault(wanted[r[2]], []).append((r[0], (x0, x1, y0, y1)))
    for r in read_csv(os.path.join(work, "train-labels-veg.csv")):
        if r[2] in wanted:
            labels.setdefault(wanted[r[2]], []).append((r[0], None))
    items = {}
    for word in sorted(set(wanted.values())):
        b = boxes.get(word, [])
        random.shuffle(b)
        one_per_photo = list({i: (i, box) for i, box in b}.values())  # varied photos, not 20 crops of one tray
        seen = {i for i, _ in one_per_photo}
        l = [x for x in labels.get(word, []) if x[0] not in seen]
        random.shuffle(l)
        items[word] = (one_per_photo + l)[:PER_KIND]
    # Commons photos (local files) fill up kinds Open Images has few of.
    commons = os.path.join(work, "commons")
    if os.path.isdir(commons):
        for word in sorted(os.listdir(commons)):
            folder = os.path.join(commons, word)
            if not os.path.isdir(folder):
                continue
            have = items.setdefault(word, [])
            local = ["file:" + os.path.join(folder, f) for f in sorted(os.listdir(folder)) if f.endswith(".jpg")]
            have += [(f, None) for f in local[: max(0, PER_KIND - len(have))]]
    # No food: validation photos whose labels name none of the kinds and no food at all.
    foodish = {i for i, n in names.items() if n in OPEN_IMAGES or n.lower() in {
        "food", "fruit", "vegetable", "produce", "dish", "cuisine", "ingredient", "natural foods", "local food",
        "whole food", "vegan nutrition", "superfood", "recipe", "meal", "plant", "leaf vegetable", "salad",
        "root vegetable", "cruciferous vegetables", "herb", "flower", "tree", "leaf", "grass"}}
    per_image = {}
    for r in read_csv(os.path.join(work, "oidv7-val-annotations-human-imagelabels.csv")):
        if len(r) > 3 and r[3] == "1":
            per_image.setdefault(r[0], set()).add(r[2])
    clean = sorted(i for i, ls in per_image.items() if not ls & foodish)
    random.shuffle(clean)
    items[""] = [("val/" + i, None) for i in clean[:NO_FOOD]]
    return items


def photo_path(work, image):
    if image.startswith("file:"):
        return image[5:]
    return os.path.join(work, "photos", image.replace("/", "_") + ".jpg")


def url(image):
    if image.startswith("val/"):
        return "https://open-images-dataset.s3.amazonaws.com/validation/%s.jpg" % image[4:]
    return "https://open-images-dataset.s3.amazonaws.com/train/%s.jpg" % image


def download(work, items):
    os.makedirs(os.path.join(work, "photos"), exist_ok=True)
    todo = sorted({i for lst in items.values() for i, _ in lst if not i.startswith("file:")})

    def one(image):
        path = photo_path(work, image)
        if os.path.exists(path):
            return
        try:
            data = urllib.request.urlopen(url(image), timeout=60).read()
            im = Image.open(io.BytesIO(data)).convert("RGB")
            im.thumbnail((800, 800))  # the model only sees 224 x 224
            im.save(path, quality=90)
        except Exception as e:
            print("skip", image, e, flush=True)

    with ThreadPoolExecutor(16) as pool:
        for n, _ in enumerate(pool.map(one, todo)):
            if n % 500 == 0:
                print("photos", n, "of", len(todo), flush=True)


def square(im, box):
    """The middle square (as the phone does), or the square around a box with a little room."""
    w, h = im.size
    if box is None:
        side = min(w, h)
        cx, cy = w / 2, h / 2
    else:
        x0, x1, y0, y1 = box[0] * w, box[1] * w, box[2] * h, box[3] * h
        side = min(max(x1 - x0, y1 - y0) * 1.15, min(w, h))
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    left = min(max(cx - side / 2, 0), w - side)
    top = min(max(cy - side / 2, 0), h - side)
    return im.crop((int(left), int(top), int(left + side), int(top + side))).resize((SIZE, SIZE), Image.BILINEAR)


def fingerprints(work, repo, items):
    from ai_edge_litert.interpreter import Interpreter
    model = Interpreter(model_path=os.path.join(repo, "feature/camera/src/main/assets/food_embedder.tflite"))
    model.allocate_tensors()
    inp, out = model.get_input_details()[0], model.get_output_details()[0]
    X, y, words = [], [], sorted(items)
    for k, word in enumerate(words):
        for image, box in items[word]:
            path = photo_path(work, image)
            try:
                pixels = np.asarray(square(Image.open(path).convert("RGB"), box), dtype=np.float32) / 255.0
            except Exception:
                continue
            model.set_tensor(inp["index"], pixels[None])
            model.invoke()
            X.append(model.get_tensor(out["index"])[0].copy())
            y.append(k)
        print(word or "(no food)", sum(1 for t in y if t == k), flush=True)
    return np.array(X), np.array(y), words


def train(X, y, kinds, scale, steps=2000, l2=1e-3, lr=0.5):
    Xn = X / np.linalg.norm(X, axis=1, keepdims=True) * scale
    W = np.zeros((X.shape[1], kinds), np.float32)
    b = np.zeros(kinds, np.float32)
    Y = np.eye(kinds, dtype=np.float32)[y]
    weight = (1.0 / np.bincount(y, minlength=kinds))[y]  # every kind counts the same
    weight = (weight / weight.sum())[:, None]
    for _ in range(steps):
        z = Xn @ W + b
        z -= z.max(axis=1, keepdims=True)
        p = np.exp(z)
        p /= p.sum(axis=1, keepdims=True)
        g = (p - Y) * weight
        W -= lr * (Xn.T @ g + l2 * W)
        b -= lr * g.sum(axis=0)
    return W, b


def predict(X, W, b, scale):
    z = (X / np.linalg.norm(X, axis=1, keepdims=True) * scale) @ W + b
    z -= z.max(axis=1, keepdims=True)
    p = np.exp(z)
    return p / p.sum(axis=1, keepdims=True)


def test(X, y, words, scale, rounds=3):
    """Held-back photos, used as the app does (HeadStart.kinds): a suggestion only when sure enough, up to 3."""
    none = words.index("")
    shown = right = first = food = shown_no_food = no_food = 0
    per_kind = {}
    for r in range(rounds):
        idx = np.random.RandomState(r).permutation(len(y))
        cut = len(y) // 5
        hold, keep = idx[:cut], idx[cut:]
        W, b = train(X[keep], y[keep], len(words), scale)
        for row, truth in zip(predict(X[hold], W, b, scale), y[hold]):
            ranked = [k for k in np.argsort(-row) if k != none]
            says = row[none] < 0.5 and row[ranked[0]] >= MIN_SURE
            if truth == none:
                no_food += 1
                shown_no_food += says
                continue
            food += 1
            hit = says and truth in [k for k in ranked[:3] if row[k] >= 0.05]
            shown += says
            right += hit
            first += says and ranked[0] == truth
            stats = per_kind.setdefault(words[truth], [0, 0])
            stats[0] += 1
            stats[1] += hit
    return {
        "suggests_for_food": round(shown / food, 3),
        "right_among_up_to_3": round(right / max(shown, 1), 3),
        "right_first": round(first / max(shown, 1), 3),
        "suggests_for_no_food": round(shown_no_food / max(no_food, 1), 3),
        "found_per_kind": {w: round(h / n, 2) for w, (n, h) in sorted(per_kind.items())},
    }


def write(path, words, W, b, scale):
    """HeadStart.decode's format: version, kinds, dim, scale, words, weights [dim][kinds], bias (big-endian)."""
    with open(path, "wb") as f:
        f.write(struct.pack(">iiif", 1, len(words), W.shape[0], scale))
        for w in words:
            data = w.encode("utf8")
            f.write(struct.pack(">H", len(data)) + data)
        f.write(W.astype(">f4").tobytes())
        f.write(b.astype(">f4").tobytes())


def main(work, repo):
    cache = os.path.join(work, "fingerprints.npz")
    if os.path.exists(cache):
        d = np.load(cache, allow_pickle=True)
        X, y, words = d["X"], d["y"], list(d["words"])
    else:
        items = pick(work)
        print({w: len(v) for w, v in items.items()}, flush=True)
        download(work, items)
        X, y, words = fingerprints(work, repo, items)
        np.savez(cache, X=X, y=y, words=np.array(words, dtype=object))
    counts = np.bincount(y, minlength=len(words))
    left_out = [w for w, c in zip(words, counts) if w and c < MIN_KIND]
    keep = [w for w, c in zip(words, counts) if w == "" or c >= MIN_KIND]
    remap = {words.index(w): k for k, w in enumerate(keep)}
    mask = np.array([k in remap for k in y])
    X, y, words = X[mask], np.array([remap[k] for k in y[mask]]), keep
    scale = 10.0
    result = test(X, y, words, scale)
    W, b = train(X, y, len(words), scale)
    write(os.path.join(repo, "feature/camera/src/main/assets/produce_head_start.bin"), words, W, b, scale)
    result.update({"kinds": len(words) - 1, "photos": int(len(y)), "left_out": left_out, "words": [w for w in words if w]})
    print(json.dumps(result, indent=1))
    with open(os.path.join(work, "result.json"), "w") as f:
        json.dump(result, f, indent=1)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
