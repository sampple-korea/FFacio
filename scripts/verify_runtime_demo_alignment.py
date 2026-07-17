#!/usr/bin/env python3
"""Compare the FFacio camera/IPC policy against a checked-out FFacio Runtime Demo tree."""
from __future__ import annotations

import hashlib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def fail(message: str) -> None:
    raise SystemExit(f"ERROR: {message}")


def read(path: Path) -> str:
    if not path.is_file():
        fail(f"missing file: {path}")
    return path.read_text(encoding="utf-8")


def main() -> int:
    if len(sys.argv) != 2:
        fail("usage: verify_runtime_demo_alignment.py /path/to/FFacio-Runtime-main")
    runtime = Path(sys.argv[1]).resolve()
    if not runtime.is_dir():
        fail(f"Runtime source tree not found: {runtime}")

    pairs = (
        ("android/runtime-client/src/main/aidl/io/ffacio/ipc/IFFacioRuntime.aidl", "client/src/main/aidl/io/ffacio/ipc/IFFacioRuntime.aidl"),
        ("android/runtime-client/src/main/aidl/io/ffacio/ipc/FFacioFaceParcel.aidl", "client/src/main/aidl/io/ffacio/ipc/FFacioFaceParcel.aidl"),
        ("android/runtime-client/src/main/aidl/io/ffacio/ipc/FFacioOptionsParcel.aidl", "client/src/main/aidl/io/ffacio/ipc/FFacioOptionsParcel.aidl"),
        ("android/runtime-client/src/main/java/io/ffacio/ipc/FFacioFaceParcel.java", "client/src/main/java/io/ffacio/ipc/FFacioFaceParcel.java"),
        ("android/runtime-client/src/main/java/io/ffacio/ipc/FFacioOptionsParcel.java", "client/src/main/java/io/ffacio/ipc/FFacioOptionsParcel.java"),
        ("android/runtime-client/src/main/java/com/kbyai/facesdk/FaceBox.java", "client/src/main/java/com/kbyai/facesdk/FaceBox.java"),
        ("android/runtime-client/src/main/java/com/kbyai/facesdk/FaceDetectionParam.java", "client/src/main/java/com/kbyai/facesdk/FaceDetectionParam.java"),
    )
    for app_rel, runtime_rel in pairs:
        app_path = ROOT / app_rel
        runtime_path = runtime / runtime_rel
        if not app_path.is_file() or not runtime_path.is_file() or app_path.read_bytes() != runtime_path.read_bytes():
            fail(f"Runtime IPC contract differs: {app_rel} <-> {runtime_rel}")

    app_aar = ROOT / "android/app/libs/fotoapparat-2.7.0.aar"
    demo_aar = runtime / "demo/libs/fotoapparat-2.7.0.aar"
    if not app_aar.is_file() or not demo_aar.is_file() or app_aar.read_bytes() != demo_aar.read_bytes():
        fail("Fotoapparat AAR differs from Runtime Demo")

    settings = read(runtime / "demo/src/main/java/io/ffacio/demo/SettingsRepository.kt")
    expected_defaults = {
        "livenessThreshold": "0.7f",
        "identifyThreshold": "0.8f",
        "authenticationResultHoldMs": "3500L",
        "authenticationUncertainGap": "0.03f",
        "qualityThreshold": "0.5f",
        "luminanceMin": "0f",
        "luminanceMax": "255f",
        "maxYaw": "10f",
        "maxPitch": "10f",
        "maxRoll": "10f",
        "leftEyeClosedThreshold": "0.8f",
        "rightEyeClosedThreshold": "0.8f",
        "occlusionThreshold": "0.5f",
        "mouthOpenedThreshold": "0.5f",
        "minimumFaceSize": "80",
        "maximumFaceSize": "1200",
        "autoCaptureStableMs": "1200L",
        "analysisFrameIntervalMs": "180L",
    }
    for name, value in expected_defaults.items():
        if not re.search(rf"\b{re.escape(name)}\s*:\s*[^=]+\s*=\s*{re.escape(value)}\b", settings):
            fail(f"Runtime Demo default changed: {name}={value}")

    capture = read(runtime / "demo/src/main/java/io/ffacio/demo/CaptureActivityKt.kt")
    for marker in (
        ".previewScaleType(ScaleType.CenterCrop)",
        ".previewResolution { Resolution(PREVIEW_HEIGHT, PREVIEW_WIDTH) }",
        "FrameSnapshot(frame.image.clone(), frame.size.width, frame.size.height, frame.rotation",
        "CaptureView.getROIRect1(Size(converted.width, converted.height))",
        "settings.autoCaptureStableMs",
        "FaceSDK.templateExtraction(source, face)",
        "const val PREVIEW_WIDTH = 720",
        "const val PREVIEW_HEIGHT = 1280",
    ):
        if marker not in capture:
            fail(f"Runtime Demo capture flow changed: {marker}")

    policy = read(ROOT / "android/app/src/main/java/com/ffacio/mobile/RuntimeDemoPolicy.kt")
    main_source = read(ROOT / "android/app/src/main/java/com/ffacio/mobile/MainActivity.kt")
    for marker in (
        "ANALYSIS_INTERVAL_MS = 180L",
        "DEMO_CAPTURE_FRAME_WIDTH = 1280",
        "DEMO_CAPTURE_FRAME_HEIGHT = 720",
        "DEMO_IDENTIFICATION_FRAME_WIDTH = 640",
        "DEMO_IDENTIFICATION_FRAME_HEIGHT = 480",
        "ANTISPOOF_THRESHOLD = 0.70f",
        "RUNTIME_QUALITY_THRESHOLD = 0.50f",
        "ENROLL_AUTO_CAPTURE_STABLE_MS = 1200L",
        "AUTH_STABLE_FRAMES = 1",
        "runtimeNativeOrientation",
        "runtimeDemoResolutionCost",
    ):
        if marker not in policy:
            fail(f"app policy marker missing: {marker}")
    for marker in (
        "Fotoapparat.with(context)",
        "frame.image.clone()",
        "largestRuntimeFace",
        "finalizeEnrollment",
        "FaceSDK.yuv2Bitmap",
    ):
        if marker not in main_source:
            fail(f"app camera flow marker missing: {marker}")

    print(f"Runtime Demo alignment passed: {len(pairs)} byte-identical IPC files")
    print(f"Fotoapparat SHA-256: {hashlib.sha256(app_aar.read_bytes()).hexdigest()}")
    print(f"Runtime Demo defaults checked: {len(expected_defaults)}")
    print("Intentional differences: largest-face selection; authentication stability=1 frame")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
