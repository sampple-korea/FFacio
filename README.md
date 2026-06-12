# FFacio Android

FFacio is an offline Android face-authentication app for a door-terminal style setup.
It uses Kotlin, Jetpack Compose, CameraX, OpenCV, ONNX Runtime Android, and bundled local models. No cloud face API or first-run model download is required.

## Current Recognition Stack

- Face detection: OpenCV YuNet
- Face alignment/fallback: OpenCV SFace
- Primary embeddings: InsightFace ArcFace `w600k_r50.onnx` through ONNX Runtime Android
- Optional passive PAD: MiniFASNet-V2
- Default liveness: active center/left/right face-turn challenge
- Storage: encrypted Android local biometric templates; original face images are not stored

For door use, authentication is intentionally conservative. A candidate must pass a stricter centroid score, ambiguity margin, supporting enrollment-sample checks, active liveness, and stable multi-frame confirmation.

## Build

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup_android_deps.ps1
$env:FFACIO_ANDROID_KEYSTORE = "C:\path\to\release.jks"
$env:FFACIO_ANDROID_KEYSTORE_PASSWORD = "<store-password>"
$env:FFACIO_ANDROID_KEY_ALIAS = "<key-alias>"
$env:FFACIO_ANDROID_KEY_PASSWORD = "<key-password>"
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build_android.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify_android_static.ps1
```

For disposable sideload testing only:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build_android.ps1 -AllowGeneratedSigningKey
```

Artifacts are written to `release/`:

- `FFacio-Android-release.apk`
- `FFacio-Android-debug.apk`
- `android-release-manifest.json`
- `android-emulator-verification.json`
- `android-gradle-verification.log`

## Models

Android model assets are generated from `resources/models/` during Gradle builds:

- `opencv/face_detection_yunet_2023mar.onnx`
- `opencv/face_recognition_sface_2021dec.onnx`
- `insightface/models/buffalo_l/w600k_r50.onnx`
- `antispoof/minifasnet_v2.onnx`

Run `scripts/fetch_models.py` if the local model cache needs to be rebuilt.

## Door Relay

Door opening remains behind an explicit relay configuration and arming flow. See `docs/door-relay.md` and `hardware/esp32_http_relay/` for the local HTTP relay reference.

Unknown faces, ambiguous matches, liveness failures, storage failures, missing models, camera failures, and relay errors are fail-closed.

## Notes

- Users enrolled before Android 0.3.16 are intentionally not matched against the new ArcFace embedding space. Delete and re-register them so the app stores ArcFace templates with the full enrollment sample set.
- RGB-camera liveness is not equivalent to IR/depth Face ID. For high-risk physical locks, use isolated relay networking and consider a second factor or depth/IR hardware.
- InsightFace pretrained model licensing must be checked before any use beyond personal testing/research.
