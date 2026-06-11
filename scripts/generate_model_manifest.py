from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


MODEL_FILES = [
    ("opencv.yunet", "opencv/face_detection_yunet_2023mar.onnx", "opencv-yunet", "MIT/OpenCV Zoo model license"),
    ("opencv.sface", "opencv/face_recognition_sface_2021dec.onnx", "opencv-sface", "Apache-2.0/OpenCV Zoo model license"),
    (
        "antispoof.minifasnet_v2",
        "antispoof/minifasnet_v2.onnx",
        "antispoof-minifasnet-v2",
        "Apache-2.0/MiniFASNet-V2 anti-spoofing ONNX model",
    ),
    ("insightface.detector", "insightface/models/buffalo_l/det_10g.onnx", "insightface-buffalo-l", "InsightFace pretrained model license"),
    ("insightface.recognition", "insightface/models/buffalo_l/w600k_r50.onnx", "insightface-buffalo-l", "InsightFace pretrained model license"),
    ("insightface.landmark3d", "insightface/models/buffalo_l/1k3d68.onnx", "insightface-buffalo-l", "InsightFace pretrained model license"),
    ("insightface.landmark2d", "insightface/models/buffalo_l/2d106det.onnx", "insightface-buffalo-l", "InsightFace pretrained model license"),
    ("insightface.genderage", "insightface/models/buffalo_l/genderage.onnx", "insightface-buffalo-l", "InsightFace pretrained model license"),
]


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True, type=Path)
    parser.add_argument("--version", default="2026.06.10")
    args = parser.parse_args()

    files = []
    for file_id, rel, engine, license_note in MODEL_FILES:
        path = args.root / rel
        if not path.exists():
            raise SystemExit(f"Missing model file: {path}")
        files.append(
            {
                "id": file_id,
                "path": rel.replace("\\", "/"),
                "engine": engine,
                "size": path.stat().st_size,
                "sha256": sha256_file(path),
                "license": license_note,
            }
        )

    manifest = {
        "version": args.version,
        "generated_by": "scripts/generate_model_manifest.py",
        "offline_first": True,
        "files": files,
    }
    output = args.root / "models.manifest.json"
    output.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
