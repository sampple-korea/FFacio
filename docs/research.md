# FFacio Android Recognition Research

Updated: 2026-06-12

## Decision

FFacio Android now prioritizes InsightFace ArcFace `w600k_r50.onnx` through ONNX Runtime Android for recognition embeddings. OpenCV YuNet remains the detector, OpenCV SFace remains the face alignment/fallback path, and MiniFASNet-V2 remains the optional passive anti-spoofing model.

This keeps the app offline, avoids cloud subscriptions, and improves recognition strength over SFace-only embeddings.

## Compared Options

| Option | Result |
| --- | --- |
| InsightFace ArcFace ONNX | Best immediate fit. Strong recognition family, local ONNX inference, model already bundled in `resources/models`. |
| OpenCV SFace | Useful and lightweight, but now fallback only because a real-device false accept was reported with SFace-era matching. |
| KBY-AI Android SDK | Potentially strong commercial SDK with liveness/recognition, but Android use is license-tied and not the free/offline source-tree path for this project. |
| InspireFace Android SDK | Promising InsightFace-family SDK for edge/mobile, but native SDK integration is a larger migration than using the existing ONNX model through ORT. |
| MediaPipe Face Landmarker | Good for landmarks/pose/liveness assistance, but it is not a face identity embedding model by itself. |
| FaceNet/TFLite sample apps | Useful references, but ArcFace/InsightFace is the stronger current recognition direction for this project. |

## Current Hardening

- ArcFace embeddings are normalized before storage/matching.
- New users store the full enrollment sample set, not only one averaged centroid.
- Authentication requires centroid score, ambiguity margin, enrollment-sample support, active liveness, and stable multi-frame confirmation.
- Enrollment rejects near-duplicate samples, missing pose diversity, mixed/unstable template sets, and likely duplicate users.
- Legacy users remain loadable, but should be deleted and re-registered after 0.3.16 for the strongest false-accept guard.

## References

- InsightFace: https://github.com/deepinsight/insightface
- InsightFace buffalo_l models: https://github.com/deepinsight/insightface/releases
- ONNX Runtime Android: https://onnxruntime.ai/docs/install/
- OpenCV Zoo YuNet/SFace: https://github.com/opencv/opencv_zoo
- KBY-AI Android docs: https://docs.kby-ai.com/help/product/face-liveness-detection-sdk-face-recognition-sdk/standard-sdk-mobile/standard-sdk-android
- InspireFace Android SDK: https://github.com/HyperInspire/inspireface-android-sdk
- MediaPipe Face Landmarker: https://developers.google.com/edge/mediapipe/solutions/vision/face_landmarker
