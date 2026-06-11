from __future__ import annotations

import os
import sys
from pathlib import Path


APP_NAME = "FFacio"
APP_VERSION = "0.3.4"


def is_frozen() -> bool:
    return bool(getattr(sys, "frozen", False))


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def bundle_root() -> Path:
    if is_frozen():
        return Path(getattr(sys, "_MEIPASS", Path(sys.executable).resolve().parent))
    return repo_root()


def resource_dir() -> Path:
    root = bundle_root()
    candidate = root / "resources"
    return candidate if candidate.exists() else root


def user_data_dir() -> Path:
    base = os.environ.get("LOCALAPPDATA")
    if base:
        path = Path(base) / APP_NAME
    else:
        path = Path.home() / f".{APP_NAME.lower()}"
    path.mkdir(parents=True, exist_ok=True)
    return path


def logs_dir() -> Path:
    path = user_data_dir() / "logs"
    path.mkdir(parents=True, exist_ok=True)
    return path


def models_dir() -> Path:
    return resource_dir() / "models"


def store_path() -> Path:
    return user_data_dir() / "ffacio_store.json"


def legacy_store_path() -> Path:
    return repo_root() / "data" / "ffacio_store.json"


ROOT = repo_root()
DATA_DIR = user_data_dir()
MODEL_DIR = models_dir()
STORE_PATH = store_path()
