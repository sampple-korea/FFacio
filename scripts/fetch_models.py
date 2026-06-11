from __future__ import annotations

import hashlib
import json
import shutil
import sys
import urllib.request
import zipfile
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODEL_ROOT = ROOT / "resources" / "models"


@dataclass(frozen=True)
class ModelSource:
    id: str
    url: str
    path: str
    sha256: str
    size: int
    license: str


MODELS = [
    ModelSource(
        "opencv.yunet",
        "https://github.com/opencv/opencv_zoo/raw/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx",
        "opencv/face_detection_yunet_2023mar.onnx",
        "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4",
        232589,
        "MIT/OpenCV Zoo model license",
    ),
    ModelSource(
        "opencv.sface",
        "https://github.com/opencv/opencv_zoo/raw/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx",
        "opencv/face_recognition_sface_2021dec.onnx",
        "0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79",
        38696353,
        "Apache-2.0/OpenCV Zoo model license",
    ),
    ModelSource(
        "antispoof.minifasnet_v2",
        "https://huggingface.co/garciafido/minifasnet-v2-anti-spoofing-onnx/resolve/main/minifasnet_v2.onnx",
        "antispoof/minifasnet_v2.onnx",
        "d7b3cd9ba8a7ceb13baa8c4720902e27ca3112eff52f926c08804af6b6eecc7b",
        1744116,
        "Apache-2.0/MiniFASNet-V2 anti-spoofing ONNX model",
    ),
]

INSIGHTFACE_ZIP = "https://github.com/deepinsight/insightface/releases/download/v0.7/buffalo_l.zip"
INSIGHTFACE_MODELS = {
    "det_10g.onnx": ("insightface.detector", 16923827, "5838f7fe053675b1c7a08b633df49e7af5495cee0493c7dcf6697200b85b5b91"),
    "w600k_r50.onnx": ("insightface.recognition", 174383860, "4c06341c33c2ca1f86781dab0e829f88ad5b64be9fba56e56bc9ebdefc619e43"),
    "1k3d68.onnx": ("insightface.landmark3d", 143607619, "df5c06b8a0c12e422b2ed8947b8869faa4105387f199c477af038aa01f9a45cc"),
    "2d106det.onnx": ("insightface.landmark2d", 5030888, "f001b856447c413801ef5c42091ed0cd516fcd21f2d6b79635b1e733a7109dbf"),
    "genderage.onnx": ("insightface.genderage", 1322532, "4fde69b1c810857b88c64a335084f1c3fe8f01246c9a191b48c7bb756d6652fb"),
}


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download(url: str, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    with urllib.request.urlopen(url, timeout=120) as response, tmp.open("wb") as handle:
        shutil.copyfileobj(response, handle)
    tmp.replace(path)


def ensure_file(source: ModelSource) -> None:
    target = MODEL_ROOT / source.path
    if not target.exists() or target.stat().st_size != source.size or sha256_file(target) != source.sha256:
        download(source.url, target)
    verify(target, source.size, source.sha256)


def verify(path: Path, size: int, sha256: str) -> None:
    if path.stat().st_size != size:
        raise SystemExit(f"Size mismatch for {path}: {path.stat().st_size} != {size}")
    actual = sha256_file(path)
    if actual != sha256:
        raise SystemExit(f"SHA-256 mismatch for {path}: {actual} != {sha256}")


def ensure_insightface() -> None:
    zip_path = MODEL_ROOT / "_downloads" / "buffalo_l.zip"
    need_zip = False
    for filename, (_model_id, size, sha) in INSIGHTFACE_MODELS.items():
        target = MODEL_ROOT / "insightface" / "models" / "buffalo_l" / filename
        if not target.exists() or target.stat().st_size != size or sha256_file(target) != sha:
            need_zip = True
            break
    if need_zip:
        download(INSIGHTFACE_ZIP, zip_path)
        with zipfile.ZipFile(zip_path) as archive:
            for filename in INSIGHTFACE_MODELS:
                member = f"buffalo_l/{filename}"
                target = MODEL_ROOT / "insightface" / "models" / "buffalo_l" / filename
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(member) as source, target.open("wb") as output:
                    shutil.copyfileobj(source, output)
    for filename, (_model_id, size, sha) in INSIGHTFACE_MODELS.items():
        verify(MODEL_ROOT / "insightface" / "models" / "buffalo_l" / filename, size, sha)


def write_manifest() -> None:
    files = [
        {
            "id": source.id,
            "path": source.path,
            "engine": (
                "opencv-yunet"
                if "yunet" in source.id
                else "opencv-sface"
                if "sface" in source.id
                else "antispoof-minifasnet-v2"
            ),
            "size": source.size,
            "sha256": source.sha256,
            "license": source.license,
        }
        for source in MODELS
    ]
    for filename, (model_id, size, sha) in INSIGHTFACE_MODELS.items():
        files.append(
            {
                "id": model_id,
                "path": f"insightface/models/buffalo_l/{filename}",
                "engine": "insightface-buffalo-l",
                "size": size,
                "sha256": sha,
                "license": "InsightFace pretrained model license",
            }
        )
    manifest = {
        "version": "2026.06.10",
        "generated_by": "scripts/fetch_models.py",
        "offline_first": True,
        "files": files,
    }
    (MODEL_ROOT / "models.manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")


def main() -> int:
    for model in MODELS:
        ensure_file(model)
    ensure_insightface()
    write_manifest()
    print(f"Prepared model bundle at {MODEL_ROOT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
