# Picture models in `src/main/assets`

Both run on the phone with LiteRT; no picture leaves the phone.

| File | What | Source | Licence |
|---|---|---|---|
| `food_classifier.tflite` | EfficientNet-Lite0 (int8), ImageNet classes; the app only uses about 20 fruit and vegetable classes (`read/Pictures.kt`, `Produce`) | Google MediaPipe, `storage.googleapis.com/mediapipe-models/image_classifier/efficientnet_lite0/int8/latest/efficientnet_lite0.tflite` | Apache 2.0 |
| `food_embedder.tflite` | MobileNet-V3 small image embedder (1024 numbers per picture), for comparing with pictures you named | Google MediaPipe, `storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/latest/mobilenet_v3_small.tflite` | Apache 2.0 |

Input: 224 × 224 RGB, the middle square of the camera frame; classifier takes 0–255 bytes, embedder 0–1 floats.
