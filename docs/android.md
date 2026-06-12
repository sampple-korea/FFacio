# FFacio Android

Android build target for the same offline face access goal.

## Stack

- Kotlin + Jetpack Compose for the mobile UI.
- CameraX for preview and frame analysis.
- OpenCV Android AAR for YuNet face detection and SFace embeddings.
- Android Keystore AES-GCM for stored face templates and HTTP relay token.
- The APK bundles an Android-only offline model set generated from `resources/models/`: OpenCV YuNet, OpenCV SFace alignment fallback, InsightFace ArcFace `w600k_r50`, and MiniFASNet-V2. It does not need a model download on first launch. ArcFace is the primary recognition embedding model; SFace remains available for face alignment and fallback embeddings. Release APKs include MiniFASNet-V2 for the optional passive PAD switch, but runtime load failure degrades to active face-turn mode instead of blocking the whole app.
- Sensitive screens run with Android `FLAG_SECURE`, so camera preview, recognized names, and relay settings are blocked from screenshots, screen recording, and recent-app thumbnails on compliant devices.
- While the app is active it keeps the display awake for door-terminal operation. The operation view also hides system bars with transient swipe reveal to reduce accidental navigation on a mounted terminal; this immersive mode is disabled when touch exploration accessibility is active. The admin view restores normal system UI and still auto-locks back to the operation view after the admin session timeout.
- The default screen is an operation view for door use: camera guidance, current status, recent approvals, relay failures, and camera retry only. Registration, user management, relay settings, and destructive actions live in the admin view. After a Head Admin is configured, normal admin actions are approved by the Head Admin user's face; Head Admin assignment/removal and initial no-Head-Admin setup use Android screen-lock verification.
- The admin view automatically returns to the operation view after the admin session timeout, with a separate idle timeout that cancels stalled enrollment. Secure prompts and storage operations are not interrupted.

## Build

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup_android_deps.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\create_android_release_keystore.ps1
$env:FFACIO_ANDROID_KEYSTORE = "C:\path\to\release.jks"
$env:FFACIO_ANDROID_KEYSTORE_PASSWORD = "<store-password>"
$env:FFACIO_ANDROID_KEY_ALIAS = "<key-alias>"
$env:FFACIO_ANDROID_KEY_PASSWORD = "<key-password>"
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build_android.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify_android_static.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify_android_emulator.ps1
```

For disposable local sideload testing only, `scripts\build_android.ps1 -AllowGeneratedSigningKey` can create `release\ffacio-local-release.jks`. Do not treat that generated key as reproducible production signing provenance.

For production-like door terminals, create and back up one persistent keystore outside the repo with `scripts\create_android_release_keystore.ps1`, then reuse it for every release. Replacing the keystore changes the APK signing lineage and can prevent in-place upgrades on installed devices.

Output:

- `release\FFacio-Android-release.apk`
- `release\FFacio-Android-debug.apk`
- `release\android-release-manifest.json`
- `release\android-emulator-verification.json`
- `release\android-gradle-verification.log`

## Current Caveats

- Release APK signing requires `FFACIO_ANDROID_KEYSTORE` and related signing environment variables. The private signing key is intentionally not stored in git.
- `android-release-manifest.json` records whether the APK used production signing or the disposable local sideload key.
- Android uses OpenCV YuNet for detection, OpenCV SFace for face alignment/fallback, InsightFace ArcFace `w600k_r50` for primary recognition embeddings, and MiniFASNet-V2 for optional passive PAD. Only the required InsightFace recognition model is packaged, not the full `buffalo_l` bundle.
- Android packages 64-bit native libraries only (`arm64-v8a` for real devices and `x86_64` for emulator verification) to keep the ONNX Runtime APK footprint lower.
- `scripts\build_android.ps1` runs unit tests, release lint, debug/release assembly, static APK verification, and emulator launch/model-readiness smoke. The emulator check is still not a substitute for real-device enrollment/auth/liveness testing.
- RGB-camera liveness defaults to the active left/right pose challenge. The optional passive MiniFASNet anti-spoofing switch can add another check against many static photo and simple screen attacks, but it is not equivalent to hardware depth/IR Face ID.
- Basic real-face verification uses the active left/right face-turn challenge by default. Advanced settings include an optional Head-Admin-approved `사진/화면 차단 모델` switch for passive PAD on top of that challenge; release builds package that model, and if it cannot be loaded at runtime the app continues in active-challenge mode.
- Enrollment rejects near-duplicate samples, requires center/left/right pose diversity with a five-step center/left/right/left/right sequence, checks final template cohesion before saving, and blocks faces that already match an enrolled user or any of their stored enrollment samples.
- During enrollment, the camera preview uses the face guide ring as the main progress surface: it shows sample progress, pose coverage, and hold progress while the user slowly turns their face for a stronger template.
- Android authentication is intentionally conservative for door use: a candidate must clear a stricter centroid score, ambiguity margin, and supporting enrollment-sample check. Users enrolled before Android 0.3.16 are intentionally not matched against the new ArcFace embedding space; delete and re-register them to store ArcFace templates with the full sample set.
- During enrollment save, the camera pipeline remains bound while heavy frame analysis pauses until encrypted template storage finishes, avoiding the freeze-like preview teardown seen on some devices.
- When analysis frames stop arriving while camera analysis is expected, Android automatically rebinds the camera pipeline for silent CameraX feed stalls, then fails visibly after repeated unsuccessful recovery attempts. If the analyzer itself stays stuck inside a frame for an extended window, the app asks for a full restart instead of racing native face-engine cleanup.
- The admin view supports selecting and deleting individual registered users, plus a separate all-users reset path for destructive maintenance.
- The protected user list shows the Head Admin badge and lets an Android screen-lock-verified operator set or clear Head Admin users. Multiple Head Admin users are supported. Once at least one compatible Head Admin exists, user deletion, all-user deletion, relay/token changes, relay tests, and full local-store reset are approved by a Head Admin face.
- The screen-lock-protected admin view includes an authentication decision log with score, runner-up, support count, and reason fields so real-device false-accept/false-reject reports can be investigated without exposing biometric scores on the public operation screen.
- Individual user deletion requires a name-specific confirmation dialog and then Head Admin face approval when a compatible Head Admin exists. Relay activation is stored as an admin setting and remains enabled across normal app lifecycle changes until an admin disables it or the encrypted relay token cannot be opened.
- Door relay requests are single-flight with a short cooldown, so repeated accepted frames while one relay request is pending do not send additional open requests.
- Android relay requests do not include the recognized user's name in the outbound JSON payload; detailed identity remains local to the protected approval log.
- Android includes a relay connection test in the locked admin view. It never calls the configured open URL; it only sends an HTTPS `GET` to `.well-known/ffacio-door-relay` under the same relay parent path and treats non-2xx responses as failure.
- Real device camera/liveness testing is still required on actual phones.
- Full Android lock-task kiosk enforcement still requires device-owner / managed-device setup outside the app. The app's immersive operation view is an ergonomic guard, not a replacement for MDM/device-owner lock task mode.
