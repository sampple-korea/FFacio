from __future__ import annotations

import os
import platform
import subprocess
import time
from dataclasses import dataclass
from typing import Protocol

import cv2
import numpy as np


@dataclass(frozen=True)
class CameraDevice:
    index: int
    name: str


@dataclass(frozen=True)
class CameraOpenResult:
    ok: bool
    message: str
    capture: "CameraCapture | None" = None


class CameraCapture(Protocol):
    label: str

    def read(self) -> tuple[bool, np.ndarray | None]:
        ...

    def release(self) -> None:
        ...


class OpenCVCapture:
    def __init__(self, index: int, api: int, label: str) -> None:
        self.label = label
        self.capture = cv2.VideoCapture(index, api)
        if self.capture.isOpened():
            self.capture.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
            self.capture.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
            self.capture.set(cv2.CAP_PROP_FPS, 30)

    def is_opened(self) -> bool:
        return bool(self.capture and self.capture.isOpened())

    def isOpened(self) -> bool:
        return self.is_opened()

    def read(self) -> tuple[bool, np.ndarray | None]:
        return self.capture.read()

    def release(self) -> None:
        if self.capture:
            self.capture.release()


class PyGrabberCapture:
    label = "DirectShow(pygrabber)"

    def __init__(self, index: int) -> None:
        import comtypes
        from pygrabber.dshow_graph import FilterGraph

        comtypes.CoInitialize()
        self._comtypes = comtypes
        self._frame: np.ndarray | None = None
        try:
            self._graph = FilterGraph()
            self._graph.add_video_input_device(index)
            self._graph.add_sample_grabber(self._on_frame)
            self._graph.add_null_render()
            self._graph.prepare_preview_graph()
            self._graph.run()
        except Exception:
            comtypes.CoUninitialize()
            raise

    def isOpened(self) -> bool:
        return True

    def _on_frame(self, frame) -> None:
        arr = np.asarray(frame)
        if arr.ndim == 3 and arr.shape[2] == 3:
            arr = cv2.cvtColor(arr, cv2.COLOR_RGB2BGR)
        self._frame = arr.copy()

    def read(self) -> tuple[bool, np.ndarray | None]:
        self._graph.grab_frame()
        if self._frame is None:
            return False, None
        return True, self._frame.copy()

    def release(self) -> None:
        try:
            self._graph.stop()
        finally:
            self._comtypes.CoUninitialize()


def list_camera_devices(max_index: int = 8) -> list[CameraDevice]:
    if platform.system() == "Windows":
        try:
            from pygrabber.dshow_graph import FilterGraph

            graph = FilterGraph()
            return [CameraDevice(index, name) for index, name in enumerate(graph.get_input_devices())]
        except Exception:
            pass
    return [CameraDevice(index, f"Camera {index}") for index in range(max_index)]


def open_camera(index: int) -> CameraOpenResult:
    if os.environ.get("FFACIO_SKIP_CAMERA") == "1":
        return CameraOpenResult(False, "Camera smoke skip")

    attempts: list[str] = []
    opencv_attempts = [(cv2.CAP_DSHOW, "DirectShow(OpenCV)")]
    if platform.system() == "Windows":
        opencv_attempts.append((cv2.CAP_MSMF, "Media Foundation(OpenCV)"))
    opencv_attempts.append((cv2.CAP_ANY, "Auto(OpenCV)"))

    for api, label in opencv_attempts:
        cap = OpenCVCapture(index, api, label)
        if cap.is_opened():
            ok, frame = read_with_warmup(cap)
            if ok and frame is not None:
                return CameraOpenResult(True, f"Camera {index}: connected - {label}", cap)
            attempts.append(f"{label}: frame read failed")
            cap.release()
        else:
            attempts.append(f"{label}: open failed")

    if platform.system() == "Windows":
        try:
            cap = PyGrabberCapture(index)
            ok, frame = read_with_warmup(cap)
            if ok and frame is not None:
                return CameraOpenResult(True, f"Camera {index}: connected - {cap.label}", cap)
            cap.release()
            attempts.append("DirectShow(pygrabber): frame read failed")
        except Exception as exc:
            attempts.append(f"DirectShow(pygrabber): {format_camera_exception(exc)}")

    return CameraOpenResult(False, camera_failure_message(index, attempts))


def read_with_warmup(capture: CameraCapture, attempts: int = 12, delay_seconds: float = 0.08) -> tuple[bool, np.ndarray | None]:
    for _ in range(attempts):
        ok, frame = capture.read()
        if ok and frame is not None and getattr(frame, "size", 0):
            return True, frame
        time.sleep(delay_seconds)
    return False, None


def format_camera_exception(exc: Exception) -> str:
    text = str(exc)
    if "Access is denied" in text or "access is denied" in text.lower() or "액세스가 거부" in text:
        return "Windows denied camera access"
    return repr(exc)


def camera_failure_message(index: int, attempts: list[str]) -> str:
    devices = list_camera_devices()
    visible = ", ".join(f"{device.index}:{device.name}" for device in devices) or "no detected devices"
    joined = " / ".join(attempts)
    if any("denied" in item.lower() or "거부" in item for item in attempts):
        return (
            f"Camera {index}: permission denied. Open Windows Settings > Privacy & security > Camera, "
            f"then allow desktop apps to access the camera. Devices: {visible}. Diagnostics: {joined}"
        )
    return f"Camera {index}: connection failed. Devices: {visible}. Diagnostics: {joined}"


def open_windows_camera_settings() -> None:
    if platform.system() == "Windows":
        subprocess.Popen(["cmd", "/c", "start", "ms-settings:privacy-webcam"], shell=False)
