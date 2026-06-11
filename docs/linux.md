# FFacio Linux builds

Linux artifacts are built on Linux, not cross-compiled from Windows.

## Local Linux build

```bash
bash scripts/build_linux.sh
```

Output:

- `release/FFacio-linux-<arch>-<version>.tar.gz`
- `release/linux-<arch>-manifest.json`

The build script:

- creates `.venv-linux`
- installs Python dependencies
- downloads pinned model files with SHA-256 verification
- runs an OpenCV model smoke test
- builds `dist/FFacio` with PyInstaller
- writes a release manifest with size, SHA-256, architecture, and git commit

## GitHub Actions

The workflow `.github/workflows/linux-release.yml` builds:

- x64 on `ubuntu-24.04`
- ARM64 on `ubuntu-24.04-arm`

Run it manually from GitHub Actions or by pushing a version tag. Download the workflow artifacts, then attach the tarballs and manifests to the GitHub Release.

## Caveats

- Real camera access must be tested on the target Linux device.
- ARM64 support depends on Python wheels existing for OpenCV, ONNX Runtime, InsightFace dependencies, and PySide6 on that runner.
- Real door hardware remains behind the same relay abstraction and must be bench-tested before controlling a lock.
