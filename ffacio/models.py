from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .paths import models_dir


YUNET_MODEL = "face_detection_yunet_2023mar.onnx"
SFACE_MODEL = "face_recognition_sface_2021dec.onnx"
MANIFEST_NAME = "models.manifest.json"
REQUIRED_MODEL_IDS = frozenset(
    {
        "opencv.yunet",
        "opencv.sface",
        "insightface.detector",
        "insightface.recognition",
        "insightface.landmark3d",
        "insightface.landmark2d",
        "insightface.genderage",
    }
)


class ModelVerificationError(RuntimeError):
    pass


@dataclass(frozen=True)
class ModelFile:
    id: str
    path: Path
    sha256: str
    size: int
    engine: str


@dataclass(frozen=True)
class ModelBundle:
    root: Path
    version: str
    files: dict[str, ModelFile]

    def path_for(self, file_id: str) -> Path:
        try:
            return self.files[file_id].path
        except KeyError as exc:
            raise ModelVerificationError(f"Missing model id in manifest: {file_id}") from exc

    @property
    def insightface_root(self) -> Path:
        return self.root / "insightface"

    @property
    def insightface_model_dir(self) -> Path:
        return self.insightface_root / "models" / "buffalo_l"

    def require_insightface_model_dir(self) -> Path:
        model_dir = self.insightface_model_dir
        if not model_dir.exists():
            raise ModelVerificationError(f"Bundled InsightFace model directory missing: {model_dir}")
        return model_dir


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_manifest(root: Path | None = None) -> dict[str, Any]:
    model_root = root or models_dir()
    manifest_path = model_root / MANIFEST_NAME
    if not manifest_path.exists():
        raise ModelVerificationError(
            f"Model manifest not found: {manifest_path}. Run scripts/prepare_models.ps1 before building."
        )
    return json.loads(manifest_path.read_text(encoding="utf-8"))


def verify_models(root: Path | None = None) -> ModelBundle:
    model_root = root or models_dir()
    manifest = load_manifest(model_root)
    files: dict[str, ModelFile] = {}
    for item in manifest.get("files", []):
        rel_path = Path(item["path"])
        path = model_root / rel_path
        if not path.exists():
            raise ModelVerificationError(f"Bundled model missing: {rel_path}")
        expected_size = int(item["size"])
        actual_size = path.stat().st_size
        if actual_size != expected_size:
            raise ModelVerificationError(
                f"Model size mismatch for {rel_path}: expected {expected_size}, got {actual_size}"
            )
        expected_hash = str(item["sha256"]).lower()
        actual_hash = sha256_file(path)
        if actual_hash != expected_hash:
            raise ModelVerificationError(f"Model hash mismatch for {rel_path}")
        files[item["id"]] = ModelFile(
            id=item["id"],
            path=path,
            sha256=actual_hash,
            size=actual_size,
            engine=item.get("engine", "unknown"),
        )
    if manifest.get("offline_first") is True:
        missing = sorted(REQUIRED_MODEL_IDS.difference(files))
        if missing:
            raise ModelVerificationError(f"Model manifest missing required ids: {', '.join(missing)}")
    return ModelBundle(root=model_root, version=str(manifest.get("version", "unknown")), files=files)


def ensure_models() -> dict[str, Path]:
    bundle = verify_models()
    return {
        YUNET_MODEL: bundle.path_for("opencv.yunet"),
        SFACE_MODEL: bundle.path_for("opencv.sface"),
    }
