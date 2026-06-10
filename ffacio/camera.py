from __future__ import annotations

import os
import platform
import subprocess
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
        self._graph = FilterGraph()
        self._graph.add_video_input_device(index)
        self._graph.add_sample_grabber(self._on_frame)
        self._graph.add_null_render()
        self._graph.prepare_preview_graph()
        self._graph.run()

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
    devices: list[CameraDevice] = []
    if platform.system() == "Windows":
        try:
            from pygrabber.dshow_graph import FilterGraph

            graph = FilterGraph()
            return [CameraDevice(index, name) for index, name in enumerate(graph.get_input_devices())]
        except Exception:
            pass
    for index in range(max_index):
        devices.append(CameraDevice(index, f"Camera {index}"))
    return devices


def open_camera(index: int) -> CameraOpenResult:
    if os.environ.get("FFACIO_SKIP_CAMERA") == "1":
        return CameraOpenResult(False, "카메라 smoke skip")

    attempts: list[str] = []
    for api, label in ((cv2.CAP_DSHOW, "DirectShow(OpenCV)"),):
        cap = OpenCVCapture(index, api, label)
        if cap.is_opened():
            ok, frame = cap.read()
            if ok and frame is not None:
                return CameraOpenResult(True, f"카메라 {index}: 연결됨 · {label}", cap)
            attempts.append(f"{label}: 프레임 읽기 실패")
            cap.release()
        else:
            attempts.append(f"{label}: 열기 실패")

    if platform.system() == "Windows":
        try:
            cap = PyGrabberCapture(index)
            ok, frame = cap.read()
            if ok and frame is not None:
                return CameraOpenResult(True, f"카메라 {index}: 연결됨 · {cap.label}", cap)
            cap.release()
            attempts.append("DirectShow(pygrabber): 프레임 읽기 실패")
        except Exception as exc:
            attempts.append(f"DirectShow(pygrabber): {format_camera_exception(exc)}")

    if os.environ.get("FFACIO_ENABLE_MSMF") == "1":
        cap = OpenCVCapture(index, cv2.CAP_MSMF, "Media Foundation(OpenCV)")
        if cap.is_opened():
            ok, frame = cap.read()
            if ok and frame is not None:
                return CameraOpenResult(True, f"카메라 {index}: 연결됨 · {cap.label}", cap)
            attempts.append("Media Foundation(OpenCV): 프레임 읽기 실패")
            cap.release()
        else:
            attempts.append("Media Foundation(OpenCV): 열기 실패")

    return CameraOpenResult(False, camera_failure_message(index, attempts))


def format_camera_exception(exc: Exception) -> str:
    text = str(exc)
    if "Access is denied" in text or "액세스가 거부" in text:
        return "Windows가 카메라 접근을 거부했습니다"
    return repr(exc)


def camera_failure_message(index: int, attempts: list[str]) -> str:
    devices = list_camera_devices()
    visible = ", ".join(f"{d.index}:{d.name}" for d in devices) or "감지된 장치 없음"
    joined = " / ".join(attempts)
    if any("거부" in item or "denied" in item.lower() for item in attempts):
        return (
            f"카메라 {index}: 권한 거부. Windows 설정 > 개인정보 및 보안 > 카메라에서 "
            f"데스크톱 앱 카메라 접근을 허용하세요. 장치: {visible}. 진단: {joined}"
        )
    return f"카메라 {index}: 연결 실패. 장치: {visible}. 진단: {joined}"


def open_windows_camera_settings() -> None:
    if platform.system() == "Windows":
        subprocess.Popen(["cmd", "/c", "start", "ms-settings:privacy-webcam"], shell=False)
