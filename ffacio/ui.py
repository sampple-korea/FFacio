from __future__ import annotations

import sys
import os
from collections import deque
from dataclasses import replace
from time import monotonic

import cv2
import numpy as np
from PySide6.QtCore import QObject, QThread, QTimer, Qt, Signal, Slot
from PySide6.QtGui import QColor, QFont, QImage, QPainter, QPen, QPixmap
from PySide6.QtWidgets import (
    QApplication,
    QCheckBox,
    QComboBox,
    QFrame,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QSlider,
    QStackedWidget,
    QVBoxLayout,
    QWidget,
)

from .camera import list_camera_devices, open_camera, open_windows_camera_settings
from .door import DoorDecision, create_door_controller
from .engine import FaceEngine, FaceObservation, MatchResult, average_embeddings, create_engine, stable_match_decision
from .liveness import LivenessChallenge
from .models import ModelVerificationError, verify_models
from .paths import APP_VERSION, user_data_dir
from .store import Store, UserTemplate, now_iso, purge_local_data


STYLE = """
QMainWindow { background: #f5f5f7; }
QLabel { color: #1d1d1f; }
QPushButton {
    background: #ffffff;
    color: #1d1d1f;
    border: 1px solid #d2d2d7;
    border-radius: 10px;
    padding: 10px 14px;
    font-weight: 600;
}
QPushButton:hover { background: #f0f0f2; }
QPushButton:pressed { background: #e8e8ed; }
QPushButton#primary {
    background: #0071e3;
    color: white;
    border: 1px solid #0071e3;
}
QPushButton#danger { color: #b42318; }
QFrame#panel {
    background: #ffffff;
    border: 1px solid #e2e2e7;
    border-radius: 16px;
}
QLineEdit {
    background: #ffffff;
    border: 1px solid #d2d2d7;
    border-radius: 10px;
    padding: 11px 12px;
    font-size: 15px;
}
QListWidget {
    background: #ffffff;
    border: 1px solid #e2e2e7;
    border-radius: 12px;
    padding: 6px;
}
QSlider::groove:horizontal {
    height: 6px;
    background: #d2d2d7;
    border-radius: 3px;
}
QSlider::handle:horizontal {
    background: #0071e3;
    width: 18px;
    height: 18px;
    margin: -7px 0;
    border-radius: 9px;
}
"""


class CameraView(QLabel):
    def __init__(self) -> None:
        super().__init__()
        self.setMinimumSize(760, 500)
        self.setAlignment(Qt.AlignCenter)
        self.setStyleSheet("background:#111114; border-radius:18px; color:#f5f5f7;")
        self.setText("Camera")
        self._status_color = QColor("#86868b")

    def set_frame(self, frame: np.ndarray, bbox: tuple[int, int, int, int] | None, accepted: bool = False) -> None:
        self._status_color = QColor("#32d74b" if accepted else "#0a84ff")
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        h, w, ch = rgb.shape
        image = QImage(rgb.data, w, h, ch * w, QImage.Format_RGB888).copy()
        pixmap = QPixmap.fromImage(image).scaled(self.size(), Qt.KeepAspectRatio, Qt.SmoothTransformation)
        self.setPixmap(pixmap)

    def paintEvent(self, event) -> None:  # type: ignore[override]
        super().paintEvent(event)
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)
        painter.setPen(QPen(self._status_color, 3))
        margin = 54
        side = min(self.width(), self.height()) - margin * 2
        x = (self.width() - side) // 2
        y = (self.height() - side) // 2
        painter.drawEllipse(x, y, side, side)


class RuntimeWorker(QObject):
    finished = Signal(object)

    def run(self) -> None:
        result: dict[str, object] = {"engine": None, "engine_error": None, "model_status": "모델 확인 전"}
        try:
            bundle = verify_models()
            result["model_status"] = f"모델 검증 완료 · {bundle.version}"
            prefer_insightface = os.environ.get("FFACIO_FORCE_OPENCV") != "1"
            result["engine"] = create_engine(prefer_insightface=prefer_insightface)
        except ModelVerificationError as exc:
            result["model_status"] = "모델 검증 실패"
            result["engine_error"] = str(exc)
        except Exception as exc:
            result["engine_error"] = repr(exc)
        self.finished.emit(result)


class DoorWorker(QObject):
    finished = Signal(bool, str)

    def __init__(self, action) -> None:
        super().__init__()
        self.action = action

    def run(self) -> None:
        try:
            ok, message = self.action()
        except Exception as exc:
            ok, message = False, repr(exc)
        self.finished.emit(bool(ok), str(message))


class VisionWorker(QObject):
    frame_ready = Signal(object, object)
    camera_status = Signal(bool, str)
    fatal_error = Signal(str)
    stopped = Signal()

    def __init__(self, engine: FaceEngine, settings) -> None:
        super().__init__()
        self.engine = engine
        self.settings = settings
        self.capture = None
        self.timer: QTimer | None = None
        self.running = False
        self.processing = False
        self.awaiting_ui = False
        self.last_camera_ok: bool | None = None

    @Slot()
    def start(self) -> None:
        self.running = True
        self.open_camera()
        self.timer = QTimer()
        self.timer.timeout.connect(self.process_frame)
        self.timer.start(45)

    @Slot(object)
    def update_settings(self, settings) -> None:
        self.settings = settings

    @Slot(int)
    def set_camera_index(self, index: int) -> None:
        self.settings.camera_index = max(0, int(index))
        self.open_camera()

    @Slot()
    def mark_frame_consumed(self) -> None:
        self.awaiting_ui = False

    def open_camera(self) -> None:
        if self.capture:
            self.capture.release()
        result = open_camera(self.settings.camera_index)
        self.capture = result.capture
        self.last_camera_ok = result.ok
        self.camera_status.emit(result.ok, result.message)
        return
        if os.environ.get("FFACIO_SKIP_CAMERA") == "1":
            self.last_camera_ok = False
            self.camera_status.emit(False, "카메라 smoke skip")
            return
        if self.capture:
            self.capture.release()
        self.capture = cv2.VideoCapture(self.settings.camera_index, cv2.CAP_DSHOW)
        if not self.capture.isOpened():
            self.capture.release()
            self.capture = cv2.VideoCapture(self.settings.camera_index)
        ok = self.capture.isOpened()
        self.last_camera_ok = ok
        message = f"카메라 {self.settings.camera_index}: 연결됨" if ok else f"카메라 {self.settings.camera_index}: 연결 실패"
        self.camera_status.emit(ok, message)

    def process_frame(self) -> None:
        if not self.running or self.processing or self.awaiting_ui:
            return
        if self.capture is None or not self.capture.isOpened():
            if self.last_camera_ok is not False:
                self.camera_status.emit(False, f"카메라 {self.settings.camera_index}: 연결 실패")
                self.last_camera_ok = False
            return
        self.processing = True
        try:
            ok, frame = self.capture.read()
            if not ok:
                if self.last_camera_ok is not False:
                    self.last_camera_ok = False
                    self.camera_status.emit(False, "카메라 프레임을 읽을 수 없습니다")
                return
            if self.last_camera_ok is not True:
                self.camera_status.emit(True, f"카메라 {self.settings.camera_index}: 연결됨")
                self.last_camera_ok = True
            frame = cv2.flip(frame, 1)
            obs = self.engine.observe(frame, self.settings)
            self.awaiting_ui = True
            self.frame_ready.emit(frame, obs)
        except Exception as exc:
            self.fatal_error.emit(repr(exc))
            self.stop()
        finally:
            self.processing = False

    @Slot()
    def stop(self) -> None:
        self.running = False
        if self.timer:
            self.timer.stop()
            self.timer.deleteLater()
            self.timer = None
        if self.capture:
            self.capture.release()
            self.capture = None
        self.stopped.emit()
        QThread.currentThread().quit()


class FFacioWindow(QMainWindow):
    vision_settings_changed = Signal(object)
    vision_camera_changed = Signal(int)
    vision_frame_consumed = Signal()
    vision_stop_requested = Signal()

    def __init__(self, auto_start: bool = True) -> None:
        super().__init__()
        self.store = Store.load()
        self.engine: FaceEngine | None = None
        self.engine_error: str | None = None
        self.model_status = "모델 확인 전"
        self.camera_error: str | None = None
        self.camera_ready = False
        self.door = create_door_controller(self.store)
        self.timer = QTimer(self)
        self.timer.timeout.connect(self.tick)
        self.frame_count = 0
        self.recent_matches: deque[MatchResult] = deque(maxlen=6)
        self.liveness = LivenessChallenge()
        self.liveness.reset(self.store.settings.liveness_steps)
        self.liveness_candidate_user_id: str | None = None
        self.last_open_at = 0.0
        self.enrolling = False
        self.enroll_embeddings: list[np.ndarray] = []
        self.enroll_qualities: list[float] = []
        self.runtime_ready = False
        self.runtime_initializing = auto_start
        self.runtime_error_logged = False
        self.runtime_thread: QThread | None = None
        self.runtime_worker: RuntimeWorker | None = None
        self.vision_thread: QThread | None = None
        self.vision_worker: VisionWorker | None = None
        self.door_threads: list[QThread] = []
        self.manual_camera_retry = False
        self.purging_local_data = False

        self.setWindowTitle("FFacio")
        self.resize(1180, 760)
        self.setStyleSheet(STYLE)
        self.build_ui()
        if auto_start:
            QTimer.singleShot(0, self.start_runtime_initialization)
            self.timer.start(120)

    def start_runtime_initialization(self) -> None:
        self.runtime_thread = QThread(self)
        self.runtime_worker = RuntimeWorker()
        self.runtime_worker.moveToThread(self.runtime_thread)
        self.runtime_thread.started.connect(self.runtime_worker.run)
        self.runtime_worker.finished.connect(self.apply_runtime_result)
        self.runtime_worker.finished.connect(self.runtime_thread.quit)
        self.runtime_worker.finished.connect(self.runtime_worker.deleteLater)
        self.runtime_thread.finished.connect(self.clear_runtime_thread)
        self.runtime_thread.finished.connect(self.runtime_thread.deleteLater)
        self.runtime_thread.start()

    def clear_runtime_thread(self) -> None:
        self.runtime_thread = None
        self.runtime_worker = None

    def run_door_task(self, action, callback=None) -> None:
        thread = QThread(self)
        worker = DoorWorker(action)
        worker.moveToThread(thread)
        thread.started.connect(worker.run)
        worker.finished.connect(thread.quit)
        worker.finished.connect(worker.deleteLater)
        if callback:
            worker.finished.connect(callback)
        thread.finished.connect(thread.deleteLater)
        thread.finished.connect(lambda t=thread: self.door_threads.remove(t) if t in self.door_threads else None)
        self.door_threads.append(thread)
        thread.start()

    def apply_runtime_result(self, result: dict[str, object]) -> None:
        self.model_status = str(result.get("model_status") or "모델 확인 전")
        self.engine = result.get("engine")  # type: ignore[assignment]
        self.engine_error = result.get("engine_error")  # type: ignore[assignment]
        if self.engine:
            fallback_reason = getattr(self.engine, "fallback_reason", None)
            if fallback_reason:
                self.store.log("engine_fallback", reason=fallback_reason, engine=self.engine.engine_id)
            self.store.log("engine_ready", engine=self.engine.engine_id, model_version=self.engine.model_version)
        elif self.model_status == "모델 검증 실패":
            self.store.log("model_verification_failed", error=self.engine_error)
        else:
            self.store.log("engine_init_failed", error=self.engine_error)
        if self.engine:
            self.start_vision_worker()
        self.runtime_ready = True
        self.runtime_initializing = False
        if hasattr(self, "startup_status"):
            self.startup_status.setText(self.startup_summary())
        if hasattr(self, "begin_btn"):
            self.begin_btn.setEnabled(self.engine is not None and self.camera_error is None)
        self.update_control_states()
        if hasattr(self, "nav_subtitle"):
            self.nav_subtitle.setText(self.engine.name if self.engine else "엔진을 사용할 수 없음")
        self.store.save()

    def settings_snapshot(self):
        return replace(self.store.settings)

    def door_controller_snapshot(self):
        return create_door_controller(self.store, self.settings_snapshot())

    def start_vision_worker(self) -> None:
        if self.engine is None:
            return
        self.stop_vision_worker()
        self.camera_ready = False
        self.camera_error = "카메라 확인 중"
        self.vision_thread = QThread(self)
        self.vision_worker = VisionWorker(self.engine, self.settings_snapshot())
        self.vision_worker.moveToThread(self.vision_thread)
        self.vision_thread.started.connect(self.vision_worker.start)
        self.vision_worker.frame_ready.connect(self.on_vision_frame)
        self.vision_worker.camera_status.connect(self.on_camera_status)
        self.vision_worker.fatal_error.connect(self.on_vision_error)
        self.vision_worker.stopped.connect(self.vision_thread.quit)
        self.vision_worker.stopped.connect(self.vision_worker.deleteLater)
        self.vision_thread.finished.connect(self.clear_vision_thread)
        self.vision_thread.finished.connect(self.vision_thread.deleteLater)
        self.vision_settings_changed.connect(self.vision_worker.update_settings)
        self.vision_camera_changed.connect(self.vision_worker.set_camera_index)
        self.vision_frame_consumed.connect(self.vision_worker.mark_frame_consumed)
        self.vision_stop_requested.connect(self.vision_worker.stop, Qt.QueuedConnection)
        self.vision_thread.start()

    def clear_vision_thread(self) -> None:
        self.vision_thread = None
        self.vision_worker = None

    def stop_vision_worker(self, wait_ms: int = 6500) -> bool:
        if not self.vision_thread:
            return True
        try:
            if self.vision_thread.isRunning():
                self.vision_stop_requested.emit()
                if self.vision_thread.wait(wait_ms):
                    return True
                return False
        except RuntimeError:
            return True
        return True

    def push_worker_settings(self) -> None:
        if self.vision_worker:
            self.vision_settings_changed.emit(self.settings_snapshot())

    def on_camera_status(self, ok: bool, message: str) -> None:
        self.camera_ready = ok
        self.camera_error = None if ok else message
        if hasattr(self, "camera_status"):
            self.camera_status.setText(message)
        if hasattr(self, "startup_status"):
            self.startup_status.setText(self.startup_summary())
        if hasattr(self, "begin_btn"):
            self.begin_btn.setEnabled(self.engine is not None and self.camera_error is None)
        if not ok:
            self.store.log("camera_unavailable", camera_index=self.store.settings.camera_index)
        if self.manual_camera_retry:
            self.manual_camera_retry = False
            if ok:
                QMessageBox.information(self, "FFacio", "카메라가 연결되었습니다.")
            else:
                QMessageBox.warning(self, "FFacio", message)
        self.update_control_states()

    def on_vision_error(self, error: str) -> None:
        self.engine = None
        self.engine_error = error
        self.auth_status.setText("런타임 오류로 인증을 중지했습니다")
        self.auth_detail.setText("설정을 확인한 뒤 앱을 다시 시작해 주세요")
        self.enroll_status.setText("런타임 오류로 등록을 중지했습니다")
        if not self.runtime_error_logged:
            self.runtime_error_logged = True
            self.store.log("runtime_fail_closed", error=error)
            self.store.save()
        self.update_control_states()

    def update_control_states(self) -> None:
        if hasattr(self, "start_enroll_btn"):
            self.start_enroll_btn.setEnabled(self.engine is not None and self.camera_ready)
        if hasattr(self, "door_arm_check"):
            http_mode = self.store.settings.door_mode == "http"
            self.door_arm_check.setEnabled(http_mode)

    def build_ui(self) -> None:
        root = QWidget()
        layout = QHBoxLayout(root)
        layout.setContentsMargins(22, 22, 22, 22)
        layout.setSpacing(18)

        nav = QFrame()
        nav.setFixedWidth(190)
        nav_layout = QVBoxLayout(nav)
        nav_layout.setContentsMargins(4, 4, 4, 4)
        nav_layout.setSpacing(10)
        title = QLabel("FFacio")
        title.setFont(QFont("Segoe UI", 24, QFont.Bold))
        self.nav_subtitle = QLabel(self.engine.name if self.engine else "시작 중")
        self.nav_subtitle.setStyleSheet("color:#6e6e73;")
        nav_layout.addWidget(title)
        nav_layout.addWidget(self.nav_subtitle)
        nav_layout.addSpacing(18)

        self.stack = QStackedWidget()
        for label, page in [
            ("시작", self.startup_page()),
            ("인증", self.auth_page()),
            ("등록", self.enroll_page()),
            ("사용자", self.users_page()),
            ("설정", self.settings_page()),
        ]:
            btn = QPushButton(label)
            btn.clicked.connect(lambda _checked=False, p=page: self.stack.setCurrentWidget(p))
            nav_layout.addWidget(btn)
            self.stack.addWidget(page)
        nav_layout.addStretch()

        layout.addWidget(nav)
        layout.addWidget(self.stack, 1)
        self.setCentralWidget(root)

    def startup_page(self) -> QWidget:
        page = QWidget()
        layout = QVBoxLayout(page)
        header = QLabel("System Ready")
        header.setFont(QFont("Segoe UI", 32, QFont.Bold))
        self.startup_status = QLabel(self.startup_summary())
        self.startup_status.setWordWrap(True)
        self.startup_status.setStyleSheet("color:#6e6e73; font-size:16px;")
        notice = QLabel(
            "Face templates and logs stay on this PC. Original face images are not stored by default."
        )
        notice.setWordWrap(True)
        notice.setStyleSheet("color:#6e6e73; font-size:15px;")
        begin_text = "Start enrollment" if not self.store.active_users() else "Go to authentication"
        begin = QPushButton(begin_text)
        self.begin_btn = begin
        begin.setObjectName("primary")
        begin.setEnabled(self.engine is not None and self.camera_error is None and not self.runtime_initializing)
        begin.clicked.connect(lambda: self.stack.setCurrentIndex(2 if not self.store.active_users() else 1))
        camera_btn = QPushButton("Reconnect camera")
        camera_btn.clicked.connect(self.retry_camera)
        camera_settings_btn = QPushButton("Open Windows camera privacy")
        camera_settings_btn.clicked.connect(open_windows_camera_settings)
        layout.addWidget(header)
        layout.addWidget(self.startup_status)
        layout.addSpacing(8)
        layout.addWidget(notice)
        layout.addStretch()
        layout.addWidget(camera_btn)
        layout.addWidget(camera_settings_btn)
        layout.addWidget(begin)
        return page

    def startup_summary(self) -> str:
        if self.runtime_initializing:
            return "모델과 엔진을 확인하는 중입니다.\n잠시만 기다려 주세요."
        engine = self.engine.name if self.engine else "엔진을 시작할 수 없습니다. 앱을 다시 설치해 주세요."
        camera = "Camera ready" if not self.camera_error else self.camera_error
        model_status = self.model_status
        if self.engine is None and self.engine_error:
            model_status = "모델을 검증할 수 없습니다. 앱을 다시 설치해 주세요."
        users = len(self.store.active_users(self.engine.engine_id if self.engine else None))
        return (
            f"앱 버전 {APP_VERSION}\n"
            f"{model_status}\n"
            f"엔진: {engine}\n"
            f"{camera}\n"
            f"호환 등록자: {users}명"
        )

    def auth_page(self) -> QWidget:
        page = QWidget()
        layout = QVBoxLayout(page)
        self.auth_camera = CameraView()
        self.auth_status = QLabel("카메라를 바라봐 주세요")
        self.auth_status.setFont(QFont("Segoe UI", 28, QFont.Bold))
        self.auth_detail = QLabel("등록 사용자 0명")
        self.auth_detail.setStyleSheet("color:#6e6e73; font-size:15px;")
        retry_camera = QPushButton("카메라 다시 연결")
        retry_camera.clicked.connect(self.retry_camera)
        layout.addWidget(self.auth_camera, 1)
        layout.addWidget(self.auth_status)
        layout.addWidget(self.auth_detail)
        layout.addWidget(retry_camera)
        return page

    def enroll_page(self) -> QWidget:
        page = QWidget()
        layout = QVBoxLayout(page)
        self.enroll_camera = CameraView()
        form = QFrame()
        form.setObjectName("panel")
        row = QHBoxLayout(form)
        self.name_input = QLineEdit()
        self.name_input.setPlaceholderText("이름")
        self.start_enroll_btn = QPushButton("등록 시작")
        self.start_enroll_btn.setObjectName("primary")
        self.start_enroll_btn.clicked.connect(self.start_enroll)
        self.cancel_enroll_btn = QPushButton("초기화")
        self.cancel_enroll_btn.clicked.connect(self.reset_enroll)
        row.addWidget(self.name_input, 1)
        row.addWidget(self.start_enroll_btn)
        row.addWidget(self.cancel_enroll_btn)
        self.enroll_status = QLabel("등록할 이름을 입력하세요")
        self.enroll_status.setFont(QFont("Segoe UI", 22, QFont.Bold))
        self.enroll_detail = QLabel("")
        self.enroll_detail.setStyleSheet("color:#6e6e73; font-size:15px;")
        layout.addWidget(self.enroll_camera, 1)
        layout.addWidget(form)
        layout.addWidget(self.enroll_status)
        layout.addWidget(self.enroll_detail)
        return page

    def users_page(self) -> QWidget:
        page = QWidget()
        layout = QVBoxLayout(page)
        header = QLabel("등록 사용자")
        header.setFont(QFont("Segoe UI", 28, QFont.Bold))
        self.user_list = QListWidget()
        delete_btn = QPushButton("선택 삭제")
        delete_btn.setObjectName("danger")
        delete_btn.clicked.connect(self.delete_selected_user)
        layout.addWidget(header)
        layout.addWidget(self.user_list, 1)
        layout.addWidget(delete_btn)
        self.refresh_users()
        return page

    def settings_page(self) -> QWidget:
        page = QWidget()
        layout = QVBoxLayout(page)
        header = QLabel("Settings")
        header.setFont(QFont("Segoe UI", 28, QFont.Bold))
        self.threshold_label = QLabel()
        self.threshold_slider = QSlider(Qt.Horizontal)
        self.threshold_slider.setRange(45, 70)
        self.threshold_slider.setValue(int(self.store.settings.threshold * 100))
        self.threshold_slider.valueChanged.connect(self.update_threshold)
        self.liveness_check = QCheckBox("Liveness challenge")
        self.liveness_check.setChecked(self.store.settings.liveness_enabled)
        self.liveness_check.setEnabled(False)
        self.liveness_check.setToolTip("Liveness is always enabled for access-control safety.")
        self.liveness_check.stateChanged.connect(self.update_liveness)

        self.camera_combo = QComboBox()
        self.refresh_camera_combo()
        self.camera_combo.currentIndexChanged.connect(self.update_camera_index)
        retry_camera = QPushButton("Reconnect camera")
        retry_camera.clicked.connect(self.retry_camera)
        camera_settings_btn = QPushButton("Open Windows camera privacy")
        camera_settings_btn.clicked.connect(open_windows_camera_settings)
        self.camera_status = QLabel("")
        self.camera_status.setWordWrap(True)
        self.camera_status.setStyleSheet("color:#6e6e73; font-size:14px;")

        self.door_mode_combo = QComboBox()
        self.door_mode_combo.addItem("Mock log only", "mock")
        self.door_mode_combo.addItem("HTTP relay", "http")
        self.door_mode_combo.setCurrentIndex(1 if self.store.settings.door_mode == "http" else 0)
        self.door_mode_combo.currentIndexChanged.connect(self.update_door_settings)
        self.door_arm_check = QCheckBox("Open HTTP relay after accepted authentication")
        self.door_arm_check.setChecked(self.store.settings.door_http_armed)
        self.door_arm_check.setEnabled(self.store.settings.door_mode == "http")
        self.door_arm_check.setToolTip("Enable real door control only with separate hardware safety checks.")
        self.door_arm_check.stateChanged.connect(self.update_door_settings)
        self.door_url_input = QLineEdit()
        self.door_url_input.setPlaceholderText("HTTP relay URL")
        self.door_url_input.setText(self.store.settings.door_http_url)
        self.door_url_input.editingFinished.connect(self.update_door_settings)
        self.door_test_url_input = QLineEdit()
        self.door_test_url_input.setPlaceholderText("HTTP test URL (required, no open)")
        self.door_test_url_input.setText(self.store.settings.door_http_test_url)
        self.door_test_url_input.editingFinished.connect(self.update_door_settings)
        self.door_token_input = QLineEdit()
        self.door_token_input.setPlaceholderText("Bearer token (optional)")
        self.door_token_input.setEchoMode(QLineEdit.Password)
        self.door_token_input.setText(self.store.settings.door_http_token)
        self.door_token_input.editingFinished.connect(self.update_door_settings)
        self.door_method_combo = QComboBox()
        self.door_method_combo.addItem("POST", "POST")
        self.door_method_combo.addItem("GET", "GET")
        self.door_method_combo.setCurrentIndex(1 if self.store.settings.door_http_method == "GET" else 0)
        self.door_method_combo.currentIndexChanged.connect(self.update_door_settings)
        test_btn = QPushButton("Check door settings")
        test_btn.clicked.connect(self.test_door)
        reset_data_btn = QPushButton("Reset local data")
        reset_data_btn.setObjectName("danger")
        reset_data_btn.clicked.connect(self.reset_local_data)
        self.log_list = QListWidget()

        layout.addWidget(header)
        layout.addWidget(self.threshold_label)
        layout.addWidget(self.threshold_slider)
        layout.addWidget(self.liveness_check)
        layout.addWidget(QLabel("Camera"))
        layout.addWidget(self.camera_combo)
        layout.addWidget(retry_camera)
        layout.addWidget(camera_settings_btn)
        layout.addWidget(self.camera_status)
        layout.addWidget(QLabel("Door control"))
        layout.addWidget(self.door_mode_combo)
        layout.addWidget(self.door_arm_check)
        layout.addWidget(self.door_url_input)
        layout.addWidget(self.door_test_url_input)
        layout.addWidget(self.door_token_input)
        layout.addWidget(self.door_method_combo)
        layout.addWidget(test_btn)
        layout.addWidget(reset_data_btn)
        layout.addWidget(QLabel("Recent logs"))
        layout.addWidget(self.log_list, 1)
        self.threshold_label.setText(f"Recognition threshold {self.store.settings.threshold:.2f}")
        self.refresh_logs()
        return page

    def refresh_camera_combo(self) -> None:
        current = self.store.settings.camera_index
        self.camera_combo.blockSignals(True)
        self.camera_combo.clear()
        devices = list_camera_devices()
        if devices:
            for device in devices:
                self.camera_combo.addItem(f"{device.index}: {device.name}", device.index)
        else:
            for index in range(6):
                self.camera_combo.addItem(f"Camera {index}", index)
        match = self.camera_combo.findData(current)
        self.camera_combo.setCurrentIndex(match if match >= 0 else 0)
        self.camera_combo.blockSignals(False)

    def tick(self) -> None:
        try:
            self._tick()
        except Exception as exc:
            self.engine = None
            self.engine_error = repr(exc)
            self.auth_status.setText("런타임 오류로 인증을 중지했습니다")
            self.auth_detail.setText("설정을 확인한 뒤 앱을 다시 시작해 주세요")
            self.enroll_status.setText("런타임 오류로 등록을 중지했습니다")
            if not self.runtime_error_logged:
                self.runtime_error_logged = True
                self.store.log("runtime_fail_closed", error=self.engine_error)
                self.store.save()

    def _tick(self) -> None:
        if self.runtime_initializing:
            self.auth_status.setText("모델과 엔진을 확인하는 중입니다")
            self.auth_detail.setText("초기화가 끝나면 인증을 시작합니다")
            self.enroll_status.setText("모델과 엔진을 확인하는 중입니다")
            return
        if self.engine is None:
            self.auth_status.setText("모델 또는 엔진을 사용할 수 없습니다")
            self.auth_detail.setText("앱을 다시 설치한 뒤 실행해 주세요")
            self.enroll_status.setText("모델 또는 엔진을 사용할 수 없습니다")
            return
        if not self.camera_ready:
            self.auth_status.setText("카메라를 사용할 수 없습니다")
            self.auth_detail.setText(self.camera_error or "설정에서 카메라를 다시 연결하세요")
            self.enroll_status.setText("카메라를 사용할 수 없습니다")
            return

    def on_vision_frame(self, frame: np.ndarray, obs: FaceObservation) -> None:
        try:
            if self.engine is None:
                return
            self.frame_count += 1
            bbox = obs.bbox if obs.confidence > 0 else None
            accepted = False

            if self.stack.currentIndex() == 2:
                self.handle_enroll(obs)
            elif obs.embedding is not None and self.frame_count % 4 == 0:
                users = self.store.active_users(self.engine.engine_id)
                match = self.engine.match(obs.embedding, users, self.store.settings)
                if match.state != "accepted" or not match.user:
                    self.liveness_candidate_user_id = None
                    self.liveness.reset(self.store.settings.liveness_steps)
                    self.recent_matches.append(match)
                    self.render_auth(match)
                else:
                    if self.liveness_candidate_user_id != match.user.id:
                        self.liveness_candidate_user_id = match.user.id
                        self.liveness.reset(self.store.settings.liveness_steps)
                        self.recent_matches.clear()
                    if self.store.settings.liveness_enabled and not self.liveness.update(obs.pose, obs.quality):
                        self.recent_matches.clear()
                        done, total = self.liveness.progress()
                        self.auth_status.setText(self.liveness.prompt())
                        self.auth_detail.setText(f"Liveness {done}/{total} - quality {obs.quality:.2f}")
                    else:
                        self.recent_matches.append(match)
                        decision = self.stable_decision()
                        accepted = decision.state == "accepted"
                        self.render_auth(decision)
            else:
                if obs.embedding is None or obs.confidence <= 0:
                    self.recent_matches.clear()
                    self.liveness_candidate_user_id = None
                compatible = len(self.store.active_users(self.engine.engine_id))
                self.auth_status.setText(obs.message)
                self.auth_detail.setText(f"품질 {obs.quality:.2f} · 호환 등록자 {compatible}명")

            if self.stack.currentIndex() == 2:
                self.enroll_camera.set_frame(frame, bbox, False)
            else:
                self.auth_camera.set_frame(frame, bbox, accepted)
        finally:
            self.vision_frame_consumed.emit()

    def render_auth(self, match: MatchResult) -> None:
        if match.state == "accepted" and match.user:
            self.auth_status.setText(f"환영합니다, {match.user.name}님")
            self.auth_detail.setText("인증이 안정적으로 확인되었습니다")
            if monotonic() - self.last_open_at < 3.0:
                return
            self.last_open_at = monotonic()
            self.store.log("auth_success", user_id=match.user.id, user_name=match.user.name, score=round(match.score, 4))
            match.user.last_seen_at = now_iso()
            decision = DoorDecision(True, match.user.id, match.user.name, match.score, "face_match")
            door = self.door_controller_snapshot()
            self.auth_detail.setText("문 제어를 요청하는 중입니다")
            self.run_door_task(lambda decision=decision, door=door: door.open(decision), self.after_door_action)
            self.refresh_users()
            self.refresh_logs()
            self.recent_matches.clear()
        elif match.state == "unknown":
            self.auth_status.setText("인식하지 못했습니다")
            self.auth_detail.setText("조명을 확인하고 얼굴을 정면에 맞춰 다시 시도해 주세요")
            self.store.log("auth_unknown", score=round(match.score, 4))
        elif match.state == "ambiguous":
            self.auth_status.setText("다시 확인 중입니다")
            self.auth_detail.setText("한 명만 카메라 앞에 서서 잠시 유지해 주세요")
            self.store.log("auth_ambiguous", score=round(match.score, 4), second_score=round(match.second_score, 4))
        else:
            self.auth_status.setText(match.message)
            self.auth_detail.setText(f"호환 등록자 {len(self.store.active_users(self.engine.engine_id if self.engine else None))}명")

    def stable_decision(self) -> MatchResult:
        return stable_match_decision(self.recent_matches)

    def start_enroll(self) -> None:
        if self.engine is None:
            self.enroll_status.setText("모델 또는 엔진을 사용할 수 없습니다")
            return
        if not self.camera_ready:
            self.enroll_status.setText("카메라를 사용할 수 없어 등록을 시작할 수 없습니다")
            return
        if not self.name_input.text().strip():
            self.enroll_status.setText("이름을 입력하세요")
            return
        self.enrolling = True
        self.enroll_embeddings.clear()
        self.enroll_qualities.clear()
        self.enroll_status.setText("얼굴을 중앙에 맞춰주세요")

    def reset_enroll(self) -> None:
        self.enrolling = False
        self.enroll_embeddings.clear()
        self.enroll_qualities.clear()
        self.enroll_status.setText("등록할 이름을 입력하세요")
        self.enroll_detail.setText("")

    def handle_enroll(self, obs) -> None:
        if not self.enrolling:
            return
        target = self.store.settings.enroll_samples
        if obs.embedding is None:
            self.enroll_status.setText(obs.message)
            self.enroll_detail.setText(f"{len(self.enroll_embeddings)}/{target} · 품질 {obs.quality:.2f}")
            return
        if self.enroll_embeddings and float(np.dot(obs.embedding, self.enroll_embeddings[-1])) > 0.985:
            self.enroll_status.setText("고개를 아주 조금 움직여주세요")
            self.enroll_detail.setText(f"{len(self.enroll_embeddings)}/{target} · 품질 {obs.quality:.2f}")
            return
        self.enroll_embeddings.append(obs.embedding)
        self.enroll_qualities.append(obs.quality)
        self.enroll_status.setText("좋습니다")
        self.enroll_detail.setText(f"{len(self.enroll_embeddings)}/{target} · 품질 {obs.quality:.2f}")
        if len(self.enroll_embeddings) >= target:
            self.finish_enroll()

    def finish_enroll(self) -> None:
        if self.engine is None:
            return
        embedding = average_embeddings(self.enroll_embeddings)
        duplicate = self.engine.match(embedding, self.store.active_users(self.engine.engine_id), self.store.settings)
        if duplicate.state == "accepted" and duplicate.user:
            self.store.log("enroll_duplicate", name=self.name_input.text().strip(), duplicate_user=duplicate.user.name)
            self.store.save()
            self.enroll_status.setText(f"이미 등록된 얼굴입니다: {duplicate.user.name}")
            self.enrolling = False
            self.refresh_logs()
            return
        user = UserTemplate.create(
            self.name_input.text().strip(),
            self.engine.name,
            embedding,
            len(self.enroll_embeddings),
            float(np.mean(self.enroll_qualities)),
            engine_id=self.engine.engine_id,
            model_version=self.engine.model_version,
        )
        self.store.users.append(user)
        self.store.log("enroll_success", user_id=user.id, user_name=user.name, quality=user.quality)
        self.store.save()
        self.enroll_status.setText("얼굴 등록이 완료되었습니다")
        self.enroll_detail.setText(f"{user.name} · 품질 {user.quality:.2f}")
        self.enrolling = False
        self.refresh_users()
        self.refresh_logs()

    def refresh_users(self) -> None:
        self.user_list.clear()
        for user in self.store.users:
            item = QListWidgetItem(
                f"{user.name} · {user.engine_id or user.model} · 품질 {user.quality:.2f} · {user.created_at}"
            )
            item.setData(Qt.UserRole, user.id)
            self.user_list.addItem(item)

    def delete_selected_user(self) -> None:
        item = self.user_list.currentItem()
        if not item:
            QMessageBox.information(self, "FFacio", "삭제할 사용자를 먼저 선택하세요.")
            return
        user_id = item.data(Qt.UserRole)
        answer = QMessageBox.question(
            self,
            "FFacio",
            "선택한 얼굴 템플릿을 삭제할까요? 이 작업은 되돌릴 수 없습니다.",
            QMessageBox.Yes | QMessageBox.No,
            QMessageBox.No,
        )
        if answer != QMessageBox.Yes:
            return
        self.store.delete_user(user_id)
        self.store.log("user_deleted", user_id=user_id)
        self.store.save()
        self.refresh_users()
        self.refresh_logs()

    def update_threshold(self, value: int) -> None:
        self.store.settings.threshold = max(0.45, value / 100)
        self.threshold_label.setText(f"인증 임계값 {self.store.settings.threshold:.2f}")
        self.push_worker_settings()
        self.store.save()

    def update_liveness(self, *_args) -> None:
        self.store.settings.liveness_enabled = True
        self.liveness_check.setChecked(True)
        self.store.log("setting_changed", key="liveness_enabled", value=self.store.settings.liveness_enabled)
        self.push_worker_settings()
        self.store.save()

    def update_camera_index(self) -> None:
        self.store.settings.camera_index = int(self.camera_combo.currentData())
        self.store.log("setting_changed", key="camera_index", value=self.store.settings.camera_index)
        self.camera_ready = False
        self.camera_error = "카메라 다시 연결 중"
        if hasattr(self, "camera_status"):
            self.camera_status.setText(f"카메라 {self.store.settings.camera_index}: 연결 중")
        if self.vision_worker:
            self.vision_camera_changed.emit(self.store.settings.camera_index)
        elif self.engine:
            self.start_vision_worker()
        if hasattr(self, "startup_status"):
            self.startup_status.setText(self.startup_summary())
        if hasattr(self, "begin_btn"):
            self.begin_btn.setEnabled(self.engine is not None and self.camera_error is None)
        self.store.save()

    def update_door_settings(self) -> None:
        previous_armed = self.store.settings.door_http_armed
        self.store.settings.door_mode = str(self.door_mode_combo.currentData())
        self.store.settings.door_http_url = self.door_url_input.text().strip()
        self.store.settings.door_http_test_url = self.door_test_url_input.text().strip()
        self.store.settings.door_http_token = self.door_token_input.text().strip()
        self.store.settings.door_http_method = str(self.door_method_combo.currentData())
        requested_armed = bool(self.door_arm_check.isChecked())
        if self.store.settings.door_mode != "http":
            requested_armed = False
        if requested_armed and not self.store.settings.door_http_url:
            QMessageBox.warning(self, "FFacio", "실제 열림을 활성화하려면 HTTP 릴레이 URL을 먼저 입력하세요.")
            requested_armed = False
            self.door_arm_check.blockSignals(True)
            self.door_arm_check.setChecked(False)
            self.door_arm_check.blockSignals(False)
        if requested_armed and not previous_armed:
            answer = QMessageBox.warning(
                self,
                "FFacio",
                "RGB 웹캠 기반 라이브니스는 사진 공격을 줄이지만 고급 화면/영상 재생 공격을 완전히 막지는 못합니다. 실제 릴레이 열림을 활성화할까요?",
                QMessageBox.Yes | QMessageBox.No,
                QMessageBox.No,
            )
            requested_armed = answer == QMessageBox.Yes
            if not requested_armed:
                self.door_arm_check.blockSignals(True)
                self.door_arm_check.setChecked(False)
                self.door_arm_check.blockSignals(False)
        self.store.settings.door_http_armed = requested_armed
        self.store.settings.normalize()
        if hasattr(self, "door_method_combo"):
            target_method_index = self.door_method_combo.findData(self.store.settings.door_http_method)
            if target_method_index >= 0 and self.door_method_combo.currentIndex() != target_method_index:
                self.door_method_combo.blockSignals(True)
                self.door_method_combo.setCurrentIndex(target_method_index)
                self.door_method_combo.blockSignals(False)
        if hasattr(self, "door_arm_check") and self.door_arm_check.isChecked() != self.store.settings.door_http_armed:
            self.door_arm_check.blockSignals(True)
            self.door_arm_check.setChecked(self.store.settings.door_http_armed)
            self.door_arm_check.blockSignals(False)
        self.door = create_door_controller(self.store)
        self.update_control_states()
        self.push_worker_settings()
        self.store.log("setting_changed", key="door_mode", value=self.store.settings.door_mode)
        self.store.save()

    def retry_camera(self) -> None:
        self.manual_camera_retry = True
        self.camera_ready = False
        self.camera_error = "카메라 다시 연결 중"
        if hasattr(self, "camera_status"):
            self.camera_status.setText(f"카메라 {self.store.settings.camera_index}: 연결 중")
        if self.vision_worker:
            self.vision_camera_changed.emit(self.store.settings.camera_index)
        elif self.engine:
            self.start_vision_worker()
        if hasattr(self, "startup_status"):
            self.startup_status.setText(self.startup_summary())
        if hasattr(self, "begin_btn"):
            self.begin_btn.setEnabled(self.engine is not None and self.camera_error is None)
        self.update_control_states()

    def test_door(self) -> None:
        door = self.door_controller_snapshot()
        self.run_door_task(lambda door=door: door.record_manual_test(), self.after_door_test)

    def after_door_action(self, ok: bool, message: str) -> None:
        self.auth_detail.setText(message)
        self.refresh_logs()

    def after_door_test(self, ok: bool, message: str) -> None:
        self.refresh_logs()
        if ok:
            QMessageBox.information(self, "FFacio", message)
        else:
            QMessageBox.warning(self, "FFacio", message)

    def reset_local_data(self) -> None:
        answer = QMessageBox.warning(
            self,
            "FFacio",
            "등록된 얼굴 템플릿, 설정, 로그를 이 PC에서 삭제할까요? 이 작업은 되돌릴 수 없습니다.",
            QMessageBox.Yes | QMessageBox.No,
            QMessageBox.No,
        )
        if answer != QMessageBox.Yes:
            return
        self.timer.stop()
        if not self.stop_vision_worker():
            self.timer.start(120)
            QMessageBox.information(self, "FFacio", "카메라 처리가 끝난 뒤 다시 시도해 주세요.")
            return
        if not self.wait_for_door_threads():
            self.timer.start(120)
            QMessageBox.information(self, "FFacio", "문 제어 요청이 끝난 뒤 다시 시도해 주세요.")
            return
        self.purging_local_data = True
        purge_local_data()
        QMessageBox.information(self, "FFacio", "로컬 데이터가 삭제되었습니다. 앱을 다시 시작하면 첫 실행 상태로 열립니다.")
        QApplication.quit()

    def refresh_logs(self) -> None:
        self.log_list.clear()
        for log in reversed(self.store.logs[-80:]):
            self.log_list.addItem(f"{log.get('time')} · {log.get('event')} · {log.get('user_name', '')}")

    def closeEvent(self, event) -> None:  # type: ignore[override]
        if self.runtime_initializing:
            QMessageBox.information(self, "FFacio", "초기화가 끝난 뒤 종료할 수 있습니다.")
            event.ignore()
            return
        self.timer.stop()
        if not self.stop_vision_worker():
            QMessageBox.information(self, "FFacio", "카메라 처리가 끝난 뒤 종료할 수 있습니다.")
            self.timer.start(120)
            event.ignore()
            return
        if not self.wait_for_door_threads():
            QMessageBox.information(self, "FFacio", "문 제어 요청이 끝난 뒤 종료할 수 있습니다.")
            self.timer.start(30)
            event.ignore()
            return
        try:
            if self.runtime_thread and self.runtime_thread.isRunning():
                self.runtime_thread.quit()
                if not self.runtime_thread.wait(12000):
                    QMessageBox.information(self, "FFacio", "초기화가 끝난 뒤 종료할 수 있습니다.")
                    self.timer.start(120)
                    event.ignore()
                    return
        except RuntimeError:
            self.runtime_thread = None
        if not self.purging_local_data:
            self.store.save()
        event.accept()

    def wait_for_door_threads(self, wait_ms: int = 6500) -> bool:
        for thread in list(self.door_threads):
            if thread.isRunning():
                thread.quit()
                if not thread.wait(wait_ms):
                    return False
        return True


def main() -> None:
    app = QApplication(sys.argv)
    app.setApplicationName("FFacio")
    window = FFacioWindow()
    window.show()
    sys.exit(app.exec())
