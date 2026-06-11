#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

ARCH="$(uname -m)"
ARTIFACT="${1:-}"
MANIFEST="${2:-}"

if [[ -z "$ARTIFACT" ]]; then
  ARTIFACT="$(ls -t release/FFacio-linux-"${ARCH}"-*.tar.gz 2>/dev/null | head -n 1 || true)"
fi
if [[ -z "$MANIFEST" ]]; then
  MANIFEST="release/linux-${ARCH}-manifest.json"
fi
if [[ -z "$ARTIFACT" || ! -f "$ARTIFACT" ]]; then
  echo "Linux artifact missing: ${ARTIFACT:-<auto>}" >&2
  exit 1
fi
if [[ ! -f "$MANIFEST" ]]; then
  echo "Linux manifest missing: $MANIFEST" >&2
  exit 1
fi

python - "$ARTIFACT" "$MANIFEST" <<'PY'
import hashlib
import json
import subprocess
import sys
from pathlib import Path

artifact = Path(sys.argv[1])
manifest_path = Path(sys.argv[2])
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

if manifest.get("artifact") != artifact.name:
    raise SystemExit(f"Manifest artifact {manifest.get('artifact')!r} does not match {artifact.name!r}")
if int(manifest.get("size", -1)) != artifact.stat().st_size:
    raise SystemExit("Manifest size does not match artifact size")
actual = sha256_file(artifact)
if str(manifest.get("sha256", "")).lower() != actual:
    raise SystemExit(f"Manifest SHA-256 does not match artifact SHA-256: {actual}")

commit = subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True, text=True, check=False).stdout.strip()
if commit and manifest.get("git_commit") and manifest["git_commit"] != commit:
    raise SystemExit(f"Manifest git_commit {manifest['git_commit']} does not match current HEAD {commit}")
PY

TMP="$(mktemp -d)"
cleanup() {
  rm -rf "$TMP"
}
trap cleanup EXIT

tar -xzf "$ARTIFACT" -C "$TMP"
APP_DIR="$TMP/FFacio"
EXE="$APP_DIR/FFacio"
MODEL_ROOT="$APP_DIR/resources/models"
MODEL_MANIFEST="$MODEL_ROOT/models.manifest.json"

if [[ ! -x "$EXE" ]]; then
  echo "Packaged executable missing or not executable: $EXE" >&2
  exit 1
fi
if [[ ! -f "$MODEL_MANIFEST" ]]; then
  echo "Packaged model manifest missing: $MODEL_MANIFEST" >&2
  exit 1
fi

python - "$MODEL_ROOT" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

root = Path(sys.argv[1])
manifest = json.loads((root / "models.manifest.json").read_text(encoding="utf-8"))
required = {
    "opencv.yunet",
    "opencv.sface",
    "antispoof.minifasnet_v2",
    "insightface.detector",
    "insightface.recognition",
}
seen = set()

def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

for item in manifest.get("files", []):
    seen.add(item["id"])
    path = root / item["path"]
    if not path.exists():
        raise SystemExit(f"Packaged model missing: {item['path']}")
    if path.stat().st_size != int(item["size"]):
        raise SystemExit(f"Packaged model size mismatch: {item['path']}")
    actual = sha256_file(path)
    if actual != str(item["sha256"]).lower():
        raise SystemExit(f"Packaged model SHA-256 mismatch: {item['path']}")

missing = sorted(required - seen)
if missing:
    raise SystemExit(f"Packaged model manifest missing required ids: {', '.join(missing)}")
PY

QT_QPA_PLATFORM=offscreen \
FFACIO_SKIP_CAMERA=1 \
FFACIO_FORCE_OPENCV=1 \
"$EXE" --smoke-test --strict-opencv --ui-smoke --runtime-smoke --offscreen

echo "Linux static verification passed: $ARTIFACT"
