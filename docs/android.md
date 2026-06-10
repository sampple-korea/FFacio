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

- `release\FFacio-Android-release.apk`
- `release\FFacio-Android-debug.apk`
- `release\android-release-manifest.json`

## Current Caveats

- The release APK is signed with a generated local sideload key unless `FFACIO_ANDROID_KEYSTORE` and related signing environment variables are provided. Use your own keystore for Play/production distribution.
- It includes the shared InsightFace `buffalo_l` bundle for parity, but the first mobile implementation runs OpenCV YuNet/SFace because the Python InsightFace package is not directly portable to Android.
- RGB-camera liveness remains an active pose challenge. It helps against static photos and simple screens, but it is not equivalent to hardware depth/IR Face ID.
- Real device camera/liveness testing is still required on actual phones.
