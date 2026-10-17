# Picture models in `src/main/assets`

Both run on the phone with LiteRT; no picture leaves the phone.

| File | What | Source | Licence |
|---|---|---|---|
| `food_classifier.tflite` | EfficientNet-Lite0 (int8), ImageNet classes; the app only uses about 20 fruit and vegetable classes (`read/Pictures.kt`, `Produce`) | Google MediaPipe, `storage.googleapis.com/mediapipe-models/image_classifier/efficientnet_lite0/int8/latest/efficientnet_lite0.tflite` | Apache 2.0 |
| `food_embedder.tflite` | MobileNet-V3 small image embedder (1024 numbers per picture), for comparing with pictures you named | Google MediaPipe, `storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/latest/mobilenet_v3_small.tflite` | Apache 2.0 |

Input: 224 × 224 RGB, the middle square of the camera frame; classifier takes 0–255 bytes, embedder 0–1 floats.

## Head start for fruit and vegetables: `produce_head_start.bin` (160 KB)

A small layer (38 kinds plus "no food") on top of the embedder's fingerprints, made with `tools/produce_head_start.py`. It was trained on about 2,300 crops of photos from [Open Images](https://storage.googleapis.com/openimages/web/index.html), whose images are licensed CC BY 2.0 by their authors, plus about 400 Open Images photos without food and Guido's 13 scale photos as "no food". Only the trained numbers are in the app; no photos.

Test on photos held back from training (3 rounds): at the chosen bar (0.35), it makes a suggestion for 89% of food photos. For 87% of those, the right kind is among its up to 3 suggestions. It also makes a suggestion for 10% of photos without food. Photos you name on your phone (`PictureMemory`) are checked first.
