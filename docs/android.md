# FFacio Android

Android build target for the same offline face access goal.

## Stack

- Kotlin + Jetpack Compose for the mobile UI.
- CameraX for preview and frame analysis.
- OpenCV Android AAR for YuNet face detection and SFace embeddings.
- Android Keystore AES-GCM for stored face templates and HTTP relay token.
- The APK bundles an Android-only offline model set generated from `resources/models/`: OpenCV YuNet, OpenCV SFace, and MiniFASNet-V2. It does not need a model download on first launch, and it intentionally omits desktop-only InsightFace assets.
- Sensitive screens run with Android `FLAG_SECURE`, so camera preview, recognized names, and relay settings are blocked from screenshots, screen recording, and recent-app thumbnails on compliant devices.

## Build

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup_android_deps.ps1
$env:FFACIO_ANDROID_KEYSTORE = "C:\path\to\release.jks"
$env:FFACIO_ANDROID_KEYSTORE_PASSWORD = "<store-password>"
$env:FFACIO_ANDROID_KEY_ALIAS = "<key-alias>"
$env:FFACIO_ANDROID_KEY_PASSWORD = "<key-password>"
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build_android.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify_android_static.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify_android_emulator.ps1
```

For disposable local sideload testing only, `scripts\build_android.ps1 -AllowGeneratedSigningKey` can create `release\ffacio-local-release.jks`. Do not treat that generated key as reproducible production signing provenance.

Output:

- `release\FFacio-Android-release.apk`
- `release\FFacio-Android-debug.apk`
- `release\android-release-manifest.json`
- `release\android-emulator-verification.json`
- `release\android-gradle-verification.log`

## Current Caveats

- Release APK signing requires `FFACIO_ANDROID_KEYSTORE` and related signing environment variables. The private signing key is intentionally not stored in git.
- Android uses OpenCV YuNet/SFace plus MiniFASNet-V2. The larger desktop InsightFace `buffalo_l` bundle is not packaged into the APK.
- `scripts\build_android.ps1` runs unit tests, release lint, debug/release assembly, static APK verification, and emulator launch/model-readiness smoke. The emulator check is still not a substitute for real-device enrollment/auth/liveness testing.
- RGB-camera liveness now combines passive MiniFASNet anti-spoofing with the active pose challenge. It helps against many static photo and simple screen attacks, but it is not equivalent to hardware depth/IR Face ID.
- Real device camera/liveness testing is still required on actual phones.
