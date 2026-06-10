from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from ffacio.camera import list_camera_devices, open_camera


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify a real Windows camera without FFACIO_SKIP_CAMERA.")
    parser.add_argument("--index", type=int, default=0)
    parser.add_argument("--json-out", default="")
    args = parser.parse_args()

    devices = list_camera_devices()
    result = open_camera(args.index)
    frame_ok = False
    shape = None
    if result.capture:
        try:
            frame_ok, frame = result.capture.read()
            shape = list(frame.shape) if frame_ok and frame is not None else None
        finally:
            result.capture.release()

    payload = {
        "camera_index": args.index,
        "devices": [device.__dict__ for device in devices],
        "open_ok": result.ok,
        "message": result.message,
        "frame_ok": frame_ok,
        "frame_shape": shape,
    }
    text = json.dumps(payload, ensure_ascii=False, indent=2)
    print(text)
    if args.json_out:
        Path(args.json_out).write_text(text, encoding="utf-8")
    return 0 if result.ok and frame_ok else 2


if __name__ == "__main__":
    raise SystemExit(main())
