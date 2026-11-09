# Picture models in `src/main/assets`

Both run on the phone with LiteRT; no picture leaves the phone.

| File | What | Source | Licence |
|---|---|---|---|
| `food_classifier.tflite` | EfficientNet-Lite0 (int8), ImageNet classes; the app only uses about 20 fruit and vegetable classes (`read/Pictures.kt`, `Produce`) | Google MediaPipe, `storage.googleapis.com/mediapipe-models/image_classifier/efficientnet_lite0/int8/latest/efficientnet_lite0.tflite` | Apache 2.0 |
| `food_embedder.tflite` | MobileNet-V3 small image embedder (1024 numbers per picture), for comparing with pictures you named | Google MediaPipe, `storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/latest/mobilenet_v3_small.tflite` | Apache 2.0 |

Input: 224 × 224 RGB, the middle square of the camera frame; classifier takes 0–255 bytes, embedder 0–1 floats.

## Head start for fruit and vegetables: `produce_head_start.bin`

A small layer (55 kinds plus "no food") on top of the embedder's fingerprints, made with
`tools/produce-head-start/produce_head_start.py` (rebuilt in Round 15, Nov 2026; the first version knew 38 kinds).
Trained on about 8,000 photos: crops and labelled photos from [Open Images](https://storage.googleapis.com/openimages/web/index.html)
(CC BY 2.0 by their authors), photos from Wikimedia Commons for kinds Open Images has few of (public domain,
CC0, CC BY or CC BY-SA; `commons_photos.py` lists each photo's source and licence), and 600 Open Images photos
without food as "no food". Only the trained numbers are in the app; no photos.

New in Round 15: spinach, leek, lettuce, celery, brussels sprouts, kale, beetroot, sweet potato, fennel, parsnip,
turnip, avocado, ginger, coriander, oyster mushroom, shiitake, pak choi. Too few photos (left out): celeriac,
witlof, kohlrabi, radicchio, shallot.

Test on photos held back from training (3 rounds of 20%): at the chosen bar (0.35) it makes a suggestion for 73%
of food photos; for 85% of those the right kind is among its up to 3 suggestions (71% as the first one). It makes
a suggestion for 9% of photos without food. Weakest: onions, garlic, ginger, avocado (under 30% found); best:
broccoli, oyster mushroom, parsnip, pomegranate, pumpkin (over 85%). Photos you name on your phone
(`PictureMemory`) are checked first, so a kind it misses is learned after naming it once.
