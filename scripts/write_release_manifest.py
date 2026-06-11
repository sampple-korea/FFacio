from __future__ import annotations

import hashlib
import json
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
RELEASE = ROOT / "release"
SETUP = RELEASE / "FFacio-Setup.exe"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def app_version() -> str:
    source = (ROOT / "ffacio" / "paths.py").read_text(encoding="utf-8")
    match = re.search(r'^APP_VERSION\s*=\s*"([^"]+)"', source, re.MULTILINE)
    if not match:
        raise RuntimeError("Could not read APP_VERSION from ffacio/paths.py")
    return match.group(1)


def main() -> None:
    if not SETUP.exists():
        raise SystemExit(f"Missing installer: {SETUP}")
    commit = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=ROOT,
        capture_output=True,
        text=True,
        check=False,
    ).stdout.strip()
    manifest = {
        "name": "FFacio",
        "version": app_version(),
        "artifact": str(SETUP.name),
        "size": SETUP.stat().st_size,
        "sha256": sha256_file(SETUP),
        "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
        "git_commit": commit or None,
        "signed": False,
        "static_verified": False,
        "static_verified_at": None,
        "installer_verified": False,
        "installer_verified_at": None,
        "notes": "Unsigned installer. SmartScreen warning is expected until code signing/reputation is available.",
    }
    output = RELEASE / "release-manifest.json"
    output.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
