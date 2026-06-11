#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

ARCH="$(uname -m)"
VERSION="$(python - <<'PY'
from ffacio.paths import APP_VERSION
print(APP_VERSION)
PY
)"

python -m venv .venv-linux
source .venv-linux/bin/activate
python -m pip install --upgrade pip
python -m pip install pyinstaller==6.20.0 pyinstaller-hooks-contrib==2026.6
python -m pip install -r requirements.txt

python scripts/fetch_models.py
python app.py --smoke-test --strict-opencv
pyinstaller --clean --noconfirm FFacio.linux.spec

mkdir -p release
TAR_NAME="FFacio-linux-${ARCH}-${VERSION}.tar.gz"
tar -C dist -czf "release/${TAR_NAME}" FFacio

python - <<PY
import hashlib, json, subprocess
from datetime import datetime, timezone
from pathlib import Path

artifact = Path("release") / "${TAR_NAME}"
digest = hashlib.sha256()
with artifact.open("rb") as handle:
    for chunk in iter(lambda: handle.read(1024 * 1024), b""):
        digest.update(chunk)
commit = subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True, text=True, check=False).stdout.strip()
manifest = {
    "name": "FFacio Linux",
    "version": "${VERSION}",
    "artifact": artifact.name,
    "arch": "${ARCH}",
    "size": artifact.stat().st_size,
    "sha256": digest.hexdigest(),
    "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
    "git_commit": commit or None,
    "signed": False,
    "notes": "Linux tarball built by PyInstaller on the target Linux architecture. Real camera/PAD/door hardware must be tested on the target device.",
}
Path(f"release/linux-${ARCH}-manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
print(json.dumps(manifest, indent=2))
PY

bash scripts/verify_linux_static.sh "release/${TAR_NAME}" "release/linux-${ARCH}-manifest.json"
