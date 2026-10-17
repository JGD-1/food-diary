"""
Makes feature/camera/src/main/assets/produce_head_start.bin: the head start for recognising
fruit and vegetables (see ../MODELS.md). Run on a computer, not in the app:

    pip install ai-edge-litert pillow numpy
    python3 produce_head_start.py vectors.npz ../src/main/assets/produce_head_start.bin

vectors.npz holds picture fingerprints made with food_embedder.tflite (names, vec), from crops of
Open Images photos (CC BY 2.0, storage.googleapis.com/openimages) of each kind below, plus
pictures with no food under the name "(no food)". The photos themselves are not kept.
"""
import struct, sys
import numpy as np

# Open Images name -> what the app searches for (NEVO English names); "" = no food.
WORDS = {
    "Apple": "apple", "Artichoke": "artichoke", "Banana": "banana", "Bell pepper": "sweet pepper",
    "Blueberry": "blueberries", "Broccoli": "broccoli", "Cabbage": "cabbage", "Cantaloupe": "melon",
    "Carrot": "carrot", "Cauliflower": "cauliflower", "Chili pepper": "chili pepper", "Common fig": "figs",
    "Corn": "sweetcorn", "Cucumber": "cucumber", "Eggplant": "aubergine", "Garden Asparagus": "asparagus",
    "Garlic": "garlic", "Grape": "grapes", "Grapefruit": "grapefruit", "Green bean": "beans french",
    "Kiwifruit": "kiwi", "Mango": "mango", "Mushroom": "mushroom", "Onion": "onions", "Pea": "peas",
    "Peach": "peach", "Pear": "pear", "Pineapple": "pineapple", "Pomegranate": "pomegranate",
    "Potato": "potatoes", "Pumpkin": "pumpkin", "Radish": "radish", "Raspberry": "raspberries",
    "Scallion": "onion welsh", "Strawberry": "strawberries", "Tomato": "tomato", "Watermelon": "watermelon",
    "Zucchini": "courgette", "(no food)": "",
}
SCALE = 20.0


def train(X, y, kinds, lam=1e-3, steps=600, lr=0.5):
    W = np.zeros((X.shape[1], kinds), np.float32); b = np.zeros(kinds, np.float32); Y = np.eye(kinds)[y]
    for _ in range(steps):
        Z = X @ W + b; Z -= Z.max(1, keepdims=True); P = np.exp(Z); P /= P.sum(1, keepdims=True)
        G = (P - Y) / len(X); W -= lr * (X.T @ G + lam * W); b -= lr * G.sum(0)
    return W, b


def main(src, dst):
    d = np.load(src)
    names = [str(n) for n in d["names"]]
    kinds = sorted(set(names))
    y = np.array([kinds.index(n) for n in names])
    W, b = train(d["vec"].astype(np.float32) * SCALE, y, len(kinds))
    with open(dst, "wb") as f:  # read by HeadStart.decode (Java DataOutput format, big-endian)
        f.write(struct.pack(">iiif", 1, len(kinds), W.shape[0], SCALE))
        for k in kinds:
            word = WORDS[k].encode("utf-8"); f.write(struct.pack(">H", len(word)) + word)
        f.write(W.astype(">f4").tobytes()); f.write(b.astype(">f4").tobytes())
    print(len(kinds), "kinds,", len(names), "pictures ->", dst)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
