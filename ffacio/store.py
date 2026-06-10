from __future__ import annotations

import json
import os
import shutil
import threading
import uuid
from base64 import b64decode, b64encode
from ctypes import POINTER, Structure, byref, c_char, windll
from ctypes.wintypes import DWORD
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any

import numpy as np

from .paths import APP_VERSION, STORE_PATH, logs_dir, user_data_dir


SCHEMA_VERSION = 2


class DataBlob(Structure):
    _fields_ = [("cbData", DWORD), ("pbData", POINTER(c_char))]


def _dpapi_available() -> bool:
    return os.name == "nt"


def _blob_from_bytes(data: bytes) -> DataBlob:
    buffer = (c_char * len(data)).from_buffer_copy(data)
    return DataBlob(len(data), buffer)


def _bytes_from_blob(blob: DataBlob) -> bytes:
    output = bytes(blob.pbData[: blob.cbData])
    windll.kernel32.LocalFree(blob.pbData)
    return output


def protect_bytes(data: bytes) -> str:
    if not _dpapi_available():
        return b64encode(data).decode("ascii")
    blob_in = _blob_from_bytes(data)
    blob_out = DataBlob()
    if not windll.crypt32.CryptProtectData(byref(blob_in), None, None, None, None, 0, byref(blob_out)):
        raise OSError("CryptProtectData failed")
    return b64encode(_bytes_from_blob(blob_out)).decode("ascii")


def unprotect_bytes(payload: str, protected: bool) -> bytes:
    encrypted = b64decode(payload.encode("ascii"))
    if not protected:
        return encrypted
    blob_in = _blob_from_bytes(encrypted)
    blob_out = DataBlob()
    if not windll.crypt32.CryptUnprotectData(byref(blob_in), None, None, None, None, 0, byref(blob_out)):
        raise OSError("CryptUnprotectData failed")
    return _bytes_from_blob(blob_out)


def now_iso() -> str:
    return datetime.now().isoformat(timespec="seconds")


@dataclass
class Settings:
    camera_index: int = 0
    threshold: float = 0.50
    ambiguous_margin: float = 0.05
    enroll_samples: int = 8
    detection_confidence: float = 0.85
    open_duration_ms: int = 1200
    liveness_enabled: bool = True
    liveness_steps: int = 3
    door_mode: str = "mock"
    door_http_url: str = ""
    door_http_test_url: str = ""
    door_http_method: str = "POST"
    door_http_timeout_ms: int = 1500
    door_http_token: str = ""
    door_http_armed: bool = False

    def normalize(self) -> None:
        self.camera_index = max(0, int(self.camera_index))
        self.threshold = max(0.45, min(0.70, float(self.threshold)))
        self.ambiguous_margin = max(0.03, min(0.15, float(self.ambiguous_margin)))
        self.enroll_samples = max(5, min(20, int(self.enroll_samples)))
        self.detection_confidence = max(0.70, min(0.98, float(self.detection_confidence)))
        self.open_duration_ms = max(200, min(5000, int(self.open_duration_ms)))
        self.liveness_enabled = True
        self.liveness_steps = max(1, min(3, int(self.liveness_steps)))
        self.door_mode = self.door_mode if self.door_mode in {"mock", "http"} else "mock"
        self.door_http_url = str(self.door_http_url).strip()
        self.door_http_test_url = str(self.door_http_test_url).strip()
        self.door_http_method = str(self.door_http_method).upper()
        self.door_http_token = str(self.door_http_token).strip()
        self.door_http_armed = bool(self.door_http_armed) and self.door_mode == "http"
        if self.door_http_method not in {"GET", "POST"}:
            self.door_http_method = "POST"
        if self.door_http_armed:
            self.door_http_method = "POST"
        self.door_http_timeout_ms = max(300, min(5000, int(self.door_http_timeout_ms)))


@dataclass
class UserTemplate:
    id: str
    name: str
    created_at: str
    model: str
    embedding: list[float]
    samples: int
    quality: float
    engine_id: str | None = None
    model_version: str | None = None
    last_seen_at: str | None = None
    active: bool = True

    @classmethod
    def create(
        cls,
        name: str,
        model: str,
        embedding: np.ndarray,
        samples: int,
        quality: float,
        engine_id: str | None = None,
        model_version: str | None = None,
    ) -> "UserTemplate":
        return cls(
            id=str(uuid.uuid4()),
            name=name.strip(),
            created_at=now_iso(),
            model=model,
            engine_id=engine_id or model,
            model_version=model_version,
            embedding=embedding.astype(float).tolist(),
            samples=samples,
            quality=round(float(quality), 3),
        )


def user_from_dict(raw: dict[str, Any]) -> UserTemplate:
    item = dict(raw)
    protection = item.pop("embedding_protection", None)
    ciphertext = item.pop("embedding_ciphertext", None)
    if protection == "dpapi" and ciphertext:
        item["embedding"] = json.loads(unprotect_bytes(ciphertext, protected=True).decode("utf-8"))
    elif protection == "base64" and ciphertext:
        item["embedding"] = json.loads(unprotect_bytes(ciphertext, protected=False).decode("utf-8"))
    item.pop("embedding_ciphertext", None)
    item.pop("embedding_protection", None)
    return UserTemplate(**item)


def user_to_dict(user: UserTemplate) -> dict[str, Any]:
    item = user.__dict__.copy()
    payload = json.dumps(user.embedding, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    if _dpapi_available():
        item["embedding_ciphertext"] = protect_bytes(payload)
        item["embedding_protection"] = "dpapi"
    else:
        item["embedding_ciphertext"] = protect_bytes(payload)
        item["embedding_protection"] = "base64"
    item["embedding"] = []
    return item


def settings_from_dict(raw: dict[str, Any]) -> Settings:
    item = dict(raw)
    protection = item.pop("door_http_token_protection", None)
    ciphertext = item.pop("door_http_token_ciphertext", None)
    if protection == "dpapi" and ciphertext:
        item["door_http_token"] = unprotect_bytes(ciphertext, protected=True).decode("utf-8")
    elif protection == "base64" and ciphertext:
        item["door_http_token"] = unprotect_bytes(ciphertext, protected=False).decode("utf-8")
    settings = Settings(**item)
    settings.normalize()
    return settings


def settings_to_dict(settings: Settings) -> dict[str, Any]:
    settings.normalize()
    item = settings.__dict__.copy()
    token = item.pop("door_http_token", "")
    if token:
        payload = token.encode("utf-8")
        if _dpapi_available():
            item["door_http_token_ciphertext"] = protect_bytes(payload)
            item["door_http_token_protection"] = "dpapi"
        else:
            item["door_http_token_ciphertext"] = protect_bytes(payload)
            item["door_http_token_protection"] = "base64"
    return item


@dataclass
class Store:
    settings: Settings = field(default_factory=Settings)
    users: list[UserTemplate] = field(default_factory=list)
    logs: list[dict[str, Any]] = field(default_factory=list)
    schema_version: int = SCHEMA_VERSION
    app_version: str = APP_VERSION
    created_at: str = field(default_factory=now_iso)
    last_migrated_at: str | None = None
    _lock: threading.RLock = field(default_factory=threading.RLock, init=False, repr=False)

    @classmethod
    def load(cls, path: Path = STORE_PATH) -> "Store":
        user_data_dir().mkdir(parents=True, exist_ok=True)
        if not path.exists():
            store = cls()
            store.save(path)
            return store
        try:
            return cls.from_dict(json.loads(path.read_text(encoding="utf-8")))
        except Exception:
            backup = path.with_suffix(path.suffix + ".bak")
            if backup.exists():
                try:
                    return cls.from_dict(json.loads(backup.read_text(encoding="utf-8")))
                except Exception:
                    corrupt_backup = backup.with_suffix(backup.suffix + f".corrupt-{datetime.now().strftime('%Y%m%d%H%M%S')}")
                    shutil.move(str(backup), str(corrupt_backup))
            quarantined = path.with_suffix(path.suffix + f".corrupt-{datetime.now().strftime('%Y%m%d%H%M%S')}")
            shutil.move(str(path), str(quarantined))
            store = cls()
            store.log("store_recreated_after_corruption", quarantined=str(quarantined))
            store.save(path)
            return store

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> "Store":
        settings = settings_from_dict(raw.get("settings", {}))
        users = [user_from_dict(item) for item in raw.get("users", [])]
        store = cls(
            settings=settings,
            users=users,
            logs=raw.get("logs", []),
            schema_version=int(raw.get("schema_version", 1)),
            app_version=raw.get("app_version", APP_VERSION),
            created_at=raw.get("created_at", now_iso()),
            last_migrated_at=raw.get("last_migrated_at"),
        )
        if store.schema_version < SCHEMA_VERSION:
            store.schema_version = SCHEMA_VERSION
            store.last_migrated_at = now_iso()
            for user in store.users:
                if user.engine_id is None:
                    user.engine_id = user.model
        return store

    def to_dict(self) -> dict[str, Any]:
        self.settings.normalize()
        return {
            "schema_version": self.schema_version,
            "app_version": APP_VERSION,
            "created_at": self.created_at,
            "last_migrated_at": self.last_migrated_at,
            "settings": settings_to_dict(self.settings),
            "users": [user_to_dict(u) for u in self.users],
            "logs": self.logs[-500:],
        }

    def save(self, path: Path = STORE_PATH) -> None:
        with self._lock:
            path.parent.mkdir(parents=True, exist_ok=True)
            payload = json.dumps(self.to_dict(), ensure_ascii=False, indent=2)
            tmp = path.with_suffix(path.suffix + ".tmp")
            backup = path.with_suffix(path.suffix + ".bak")
            with tmp.open("w", encoding="utf-8") as handle:
                handle.write(payload)
                handle.flush()
                os.fsync(handle.fileno())
            if backup.exists():
                backup.unlink()
            os.replace(tmp, path)
            shutil.copy2(path, backup)

    def log(self, event: str, **data: Any) -> None:
        with self._lock:
            self.logs.append({"time": now_iso(), "event": event, **data})
            self.logs = self.logs[-500:]

    def active_users(self, engine_id: str | None = None) -> list[UserTemplate]:
        users = [u for u in self.users if u.active]
        if engine_id is not None:
            users = [u for u in users if (u.engine_id or u.model) == engine_id]
        return users

    def delete_user(self, user_id: str) -> None:
        with self._lock:
            self.users = [u for u in self.users if u.id != user_id]


def purge_local_data(path: Path = STORE_PATH) -> list[Path]:
    deleted: list[Path] = []
    data_dir = user_data_dir().resolve()
    target = path.resolve()
    if target.parent != data_dir:
        raise ValueError(f"Refusing to purge store outside FFacio user data dir: {target}")
    for candidate in data_dir.glob("ffacio_store.json*"):
        if candidate.is_file():
            candidate.unlink()
            deleted.append(candidate)
    log_root = logs_dir().resolve()
    if log_root.parent == data_dir and log_root.exists():
        shutil.rmtree(log_root)
        deleted.append(log_root)
    return deleted
