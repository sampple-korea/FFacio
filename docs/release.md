# FFacio Release Notes

## Artifact

- Installer: `release/FFacio-Setup.exe`
- Manifest: `release/release-manifest.json`
- Install target: `%ProgramFiles%\FFacio`
- User data: `%LOCALAPPDATA%\FFacio`
- Logs/store backup: `%LOCALAPPDATA%\FFacio`

## Offline Behavior

The installer bundles:

- Python runtime
- PySide6 runtime and `qwindows.dll`
- OpenCV runtime
- ONNX Runtime CPU provider
- InsightFace package runtime
- OpenCV YuNet/SFace ONNX models
- InsightFace `buffalo_l` ONNX models
- Door relay protocol docs, ESP32 example sketch, and the optional local mock relay reference utility

The packaged app verifies `resources/models/models.manifest.json` and model SHA-256 hashes before loading. Missing or tampered models prevent authentication.

## Security Notes

- The installer is currently unsigned. Windows SmartScreen warnings are expected until a signing certificate and reputation are available.
- Original face images are not stored by default. The app stores face embeddings/templates and audit logs locally.
- Uninstall preserves `%LOCALAPPDATA%\FFacio` by default for reinstall continuity. Users can purge registered templates, settings, and logs from the Settings screen with `로컬 데이터 초기화`, or run `FFacio.exe --wipe-local-data --yes` as the same Windows user.
- RGB webcam liveness uses randomized gaze prompts plus a short stable-pose hold to reduce simple photo attacks, but high-quality replay/video attacks still require passive anti-spoofing, depth/IR hardware, or a second factor before real door control.
- Door control defaults to a mock event logger. The app can also call a local HTTP relay URL after accepted face+liveness authentication, but real opening requires the separate HTTP relay arming checkbox in Settings. Optional Bearer tokens are protected in the local store with DPAPI on Windows. Missing URL, HTTP errors, and non-2xx responses are logged as failures and do not count as successful opening. Real hardware should remain fail-closed and have independent emergency egress.
- The HTTP relay contract is documented in `docs/door-relay.md`. Use a separate test URL for connectivity checks; it must not unlock anything. A source-tree mock relay and an ESP32 example sketch are included for bench testing, but real lock wiring must be verified on hardware. Plain HTTP relay traffic should stay on localhost, a wired bridge, or an isolated/trusted network.

## Build

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build_release.ps1
```

This creates the installer artifact, runs non-admin static release verification, and writes `static_verified: true` plus `installer_verified: false` in the release manifest until the installed-artifact verifier runs successfully.

If Inno Setup is not installed:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build_release.ps1 -InstallInno
```

## Installer Verification

Non-admin release integrity verification can be rerun at any time:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify_release_static.ps1
```

This checks the installer hash/size against `release-manifest.json`, required PyInstaller runtime files, bundled model IDs, model sizes, model SHA-256 hashes, packaged smoke checks, and that packaged InsightFace startup does not create a user `~\.insightface` cache.

Run this from an elevated PowerShell session before publishing a release:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify_installer.ps1
```

Or combine build and verification:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build_release.ps1 -VerifyInstaller
```

The verifier silent-installs `FFacio-Setup.exe`, runs strict InsightFace/OpenCV/UI smoke checks from the installed location, uninstalls, and marks `installer_verified: true` in `release-manifest.json`. A clean offline VM with no Python and no `%USERPROFILE%\.insightface` cache is still the final manual acceptance gate.
