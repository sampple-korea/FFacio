# FFacio Android

Android build target for the same offline face access goal.

## Stack

- Kotlin + Jetpack Compose for the mobile UI.
- CameraX for preview and frame analysis.
- OpenCV Android AAR for YuNet face detection and SFace embeddings.
- Android Keystore AES-GCM for stored face templates and HTTP relay token.
- The APK bundles `resources/models/` so it does not need a model download on first launch.

## Build

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup_android_deps.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build_android.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify_android_static.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify_android_emulator.ps1
```

Output:

- `release\FFacio-Android-debug.apk`
- `release\android-release-manifest.json`

## Current Caveats

- The produced APK is debug-key signed for sideload testing, not release-signed for Play distribution.
- It includes the shared InsightFace `buffalo_l` bundle for parity, but the first mobile implementation runs OpenCV YuNet/SFace because the Python InsightFace package is not directly portable to Android.
- RGB-camera liveness remains an active pose challenge. It helps against static photos and simple screens, but it is not equivalent to hardware depth/IR Face ID.
- Real device camera/liveness testing is still required on actual phones.
