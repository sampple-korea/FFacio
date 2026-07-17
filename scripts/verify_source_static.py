#!/usr/bin/env python3
"""Fail-closed source audit for the FFacio Runtime client tree."""
from __future__ import annotations

import hashlib
import re
import sys
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "android/app/src/main/java/com/ffacio/mobile/MainActivity.kt"
POLICY = ROOT / "android/app/src/main/java/com/ffacio/mobile/RuntimeDemoPolicy.kt"
FOTOAPPARAT = ROOT / "android/app/libs/fotoapparat-2.7.0.aar"
MANIFEST = ROOT / "android/app/src/main/AndroidManifest.xml"
APP_GRADLE = ROOT / "android/app/build.gradle"
SETTINGS = ROOT / "android/settings.gradle"
CLIENT = ROOT / "android/runtime-client/src/main/java/io/ffacio/sdk/FFacioRuntimeClient.java"
FACE_SDK = ROOT / "android/runtime-client/src/main/java/com/kbyai/facesdk/FaceSDK.java"

FORBIDDEN_SOURCE_MARKERS = (
    "ai.onnxruntime",
    "org.opencv",
    "OrtSession",
    "OpenCVLoader",
    "MiniFASNet",
    "w600k_r50.onnx",
    "face_detection_yunet",
    "face_recognition_sface",
)
REQUIRED_MAIN_MARKERS = (
    "FaceSDK.faceDetection",
    "FaceSDK.templateExtraction",
    "FaceSDK.similarityCalculation",
    "FaceSDK.yuv2Bitmap",
    "Fotoapparat.with(context)",
    ".previewScaleType(ScaleType.CenterCrop)",
    "analyzeRuntimeFrame",
    "RuntimeFrameSnapshot",
    "frame.image.clone()",
    "runtimeDecisionInFlight",
    "runtimeDecisionGeneration",
    "withTimeout(RUNTIME_DECISION_TIMEOUT_MS)",
    "RUNTIME_DECISION_STALL_RECOVERY_MS",
    "runtimeDecisionToken",
    "USER_STORE_SCHEMA_VERSION = 8",
    "USER_STORE_POLICY_VERSION = 8",
    'FACE_ENGINE_ID = "ffacio.runtime.demo.camera.v4"',
    "resetLegacyUserStoreForSingleTemplatePolicy",
    ".putBoolean(PASSIVE_LIVENESS_ENABLED_KEY, true)",
    ".putInt(RUNTIME_LIVENESS_LEVEL_KEY, 0)",
    ".putBoolean(OCCLUSION_CHECK_ENABLED_KEY, false)",
    "users.indices.filter { index -> users[index].isCompatible() }",
    "largestRuntimeFace",
    "findBestEnrollmentDuplicate",
    "duplicateCheckComplete",
    "registeredNameExists",
    "userNameValid",
    "Duplicate user name",
    "authenticationComparisonComplete",
    "runtimeSimilarityScoreValid",
    "canIssueSmartThingsUnlock",
    "canAuthorizeAdminActionWithHeadAdminFace(action, users, passiveLivenessEnabled)",
    "TrackedFaceBoxOverlay",
    "faceRectInPreview",
    "ITSOKEY_RUNTIME_SENTINEL",
    "ITSOKEY_RPC_TIMEOUT_MS",
    "ItsokeyRuntimeClient",
    "checkItsokeyDoorAccess",
    "unlockItsokeyDoor",
    "client.getDevice",
    "client.openDevice",
    "disableSmartThingsDoorPersisted",
    "clearPersistedSmartThingsCredentials",
    "ownedTemplates.forEach { it.fill(0) }",
    "EnrollmentStabilityTracker",
    "RuntimeEnrollmentCapture",
    "finalizeEnrollment",
    "frameTimestampMillis",
    "enrollmentCapture.wipe()",
    "isAdminSessionActive",
    "copyForRuntimeDecision",
    "SystemClock.elapsedRealtime()",
    "CoroutineStart.UNDISPATCHED",
    "persistenceSnapshot.wipeTemplates()",
    "cipher.updateAAD(associatedData)",
    "plainText.fill(0)",
    "require(user.isCompatible())",
    "array.optJSONObject(i) ?: continue",
    "users.forEach { it.wipe() }",
)

REQUIRED_POLICY_MARKERS = (
    "ANALYSIS_INTERVAL_MS = 180L",
    "DEMO_CAPTURE_FRAME_WIDTH = 1280",
    "DEMO_CAPTURE_FRAME_HEIGHT = 720",
    "DEMO_IDENTIFICATION_FRAME_WIDTH = 640",
    "DEMO_IDENTIFICATION_FRAME_HEIGHT = 480",
    "ANTISPOOF_THRESHOLD = 0.70f",
    "runtimeUnitScoreValid",
    "RUNTIME_QUALITY_THRESHOLD = 0.50f",
    "RUNTIME_OCCLUSION_THRESHOLD = 0.50f",
    "RUNTIME_SEVERE_MAX_YAW = 35.0f",
    "RUNTIME_SEVERE_MAX_PITCH = 30.0f",
    "RUNTIME_SEVERE_MAX_ROLL = 30.0f",
    "severePoseGuidance",
    "RUNTIME_MIN_FACE_SIZE = 80",
    "RUNTIME_MAX_FACE_SIZE = 1200",
    "RUNTIME_MIN_FACE_AREA_RATIO = 0.03",
    "ENROLL_AUTO_CAPTURE_STABLE_MS = 1200L",
    "AUTH_STABLE_FRAMES = 1",
    "check_eye_closeness = true",
    "check_mouth_opened = true",
    "estimate_age_gender = false",
    "largestRuntimeFace",
    "evaluateRuntimeDemoFace",
    "runtimeNativeOrientation",
    "runtimeDemoResolutionCost",
)


OBSOLETE_ENROLLMENT_MARKERS = (
    "LivenessChallenge",
    "EnrollmentPoseHold",
    "enrollmentTargetPose",
    "enrollmentSampleDecision",
    "enrollmentTemplateQuality",
    "ENROLL_SAMPLES",
    "matchingSamples",
    "samples_b64",
    "supportCount",
    "availableSamples",
    "좌우 얼굴 돌리기",
    "다각도",
    "doorRelayConfigured",
    "doorRelayHealthCheckUrl",
    "doorRelayPayloadJson",
)

SECRET_PATTERNS = (
    re.compile(r"ghp_[A-Za-z0-9]{20,}"),
    re.compile(r"sk-(?:proj-)?[A-Za-z0-9_-]{20,}"),
    re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
)


EXCLUDED_DIR_NAMES = {".git", ".gradle", "build", ".idea", ".kotlin"}


def is_excluded(path: Path) -> bool:
    return any(part in EXCLUDED_DIR_NAMES for part in path.relative_to(ROOT).parts)


def fail(message: str) -> None:
    raise SystemExit(f"ERROR: {message}")


def read(path: Path) -> str:
    if not path.is_file():
        fail(f"missing required file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def scan_balanced(path: Path, text: str) -> None:
    stack: list[tuple[str, int]] = []
    pairs = {')': '(', ']': '[', '}': '{'}
    line = 1
    i = 0
    state = "code"
    while i < len(text):
        ch = text[i]
        nxt = text[i + 1] if i + 1 < len(text) else ""
        if ch == "\n":
            line += 1
        if state == "line_comment":
            if ch == "\n":
                state = "code"
        elif state == "block_comment":
            if ch == "*" and nxt == "/":
                state = "code"
                i += 1
        elif state == "string":
            if ch == "\\":
                i += 1
            elif ch == '"':
                state = "code"
        elif state == "char":
            if ch == "\\":
                i += 1
            elif ch == "'":
                state = "code"
        elif state == "triple":
            if text.startswith('"""', i):
                state = "code"
                i += 2
        else:
            if ch == "/" and nxt == "/":
                state = "line_comment"
                i += 1
            elif ch == "/" and nxt == "*":
                state = "block_comment"
                i += 1
            elif text.startswith('"""', i):
                state = "triple"
                i += 2
            elif ch == '"':
                state = "string"
            elif ch == "'":
                state = "char"
            elif ch in "([{":
                stack.append((ch, line))
            elif ch in ")]}":
                if not stack or stack[-1][0] != pairs[ch]:
                    fail(f"unbalanced {ch!r} in {path.relative_to(ROOT)} line {line}")
                stack.pop()
        i += 1
    if state in {"block_comment", "string", "char", "triple"}:
        fail(f"unterminated {state} in {path.relative_to(ROOT)}")
    if stack:
        symbol, symbol_line = stack[-1]
        fail(f"unclosed {symbol!r} in {path.relative_to(ROOT)} line {symbol_line}")


def main() -> int:
    for xml in sorted(ROOT.rglob("*.xml")):
        if not xml.is_file() or is_excluded(xml):
            continue
        try:
            ElementTree.parse(xml)
        except ElementTree.ParseError as exc:
            fail(f"invalid XML {xml.relative_to(ROOT)}: {exc}")

    source_files = [
        path
        for pattern in ("*.kt", "*.java", "*.gradle")
        for path in ROOT.rglob(pattern)
        if path.is_file() and not is_excluded(path)
    ]
    for path in sorted(set(source_files)):
        text = read(path)
        scan_balanced(path, text)
        for pattern in SECRET_PATTERNS:
            if pattern.search(text):
                fail(f"possible secret in {path.relative_to(ROOT)}")

    main_text = read(MAIN)
    for marker in FORBIDDEN_SOURCE_MARKERS:
        if marker in main_text:
            fail(f"legacy engine marker remains in MainActivity.kt: {marker}")
    for marker in REQUIRED_MAIN_MARKERS:
        if marker not in main_text:
            fail(f"required Runtime integration marker missing: {marker}")
    policy_text = read(POLICY)
    for marker in REQUIRED_POLICY_MARKERS:
        if marker not in policy_text:
            fail(f"required Runtime Demo policy marker missing: {marker}")
    for marker in OBSOLETE_ENROLLMENT_MARKERS:
        if marker in main_text:
            fail(f"retired enrollment/authentication logic remains: {marker}")

    camera_stage_start = main_text.index("private fun CameraStage(")
    camera_stage_end = main_text.index("private fun TrackedFaceBoxOverlay(", camera_stage_start)
    camera_stage_text = main_text[camera_stage_start:camera_stage_end]
    for duplicate_message in ('if (isEnrollmentMode) "얼굴 등록" else "얼굴 인증"', '"Head Admin 인증 중"'):
        if duplicate_message in camera_stage_text:
            fail(f"duplicate guidance remains inside enabled camera stage: {duplicate_message}")
    if camera_stage_text.count("Text(\n                stageMessage") != 1 or "if (!enabled)" not in camera_stage_text:
        fail("camera stage fallback message is not restricted to the disabled-camera state")

    app_gradle_text = read(APP_GRADLE)
    for marker in ('versionCode 39', 'versionName "0.9.2-itsokey-runtime"'):
        if marker not in app_gradle_text:
            fail(f"expected final app version marker missing: {marker}")
    for marker in ("androidx.camera", "ImageProxy", "imageProxyToNv21", "ProcessCameraProvider", "PreviewView"):
        if marker in main_text or marker in app_gradle_text:
            fail(f"CameraX path remains after Runtime Demo camera migration: {marker}")
    if not FOTOAPPARAT.is_file():
        fail("Runtime Demo Fotoapparat AAR is missing")
    expected_fotoapparat_sha256 = "a9ce65824a2ff6ee05450c1b28d11b0bb668e5345e0c60303b184f3cd8fbbdff"
    if hashlib.sha256(FOTOAPPARAT.read_bytes()).hexdigest() != expected_fotoapparat_sha256:
        fail("Fotoapparat AAR differs from the Runtime Demo copy")
    if 'implementation files("libs/fotoapparat-2.7.0.aar")' not in app_gradle_text:
        fail("app does not package the Runtime Demo Fotoapparat AAR")

    if "implementation project(\":runtime-client\")" not in read(APP_GRADLE):
        fail("app does not depend on :runtime-client")
    if 'include ":app", ":runtime-client"' not in read(SETTINGS):
        fail(":runtime-client is not included in settings.gradle")

    manifest_text = read(MANIFEST)
    for marker in (
        'android.permission.CAMERA',
        'io.ffacio.sdk.permission.BIND_RUNTIME',
        'com.kbyai.faceattribute',
        'io.ffacio.itsokeyruntime',
        'io.ffacio.itsokeyruntime.permission.BIND_RUNTIME',
        'android:allowBackup="false"',
        'android:usesCleartextTraffic="false"',
    ):
        if marker not in manifest_text:
            fail(f"manifest contract missing: {marker}")

    client_text = read(CLIENT)
    for marker in (
        'RUNTIME_PACKAGE = "com.kbyai.faceattribute"',
        'RUNTIME_SERVICE = "io.ffacio.runtime.FFacioRuntimeService"',
        "template sizes must match",
        "YUV input size does not match width and height",
        "deleteTemporaryFile",
        "if (!file.delete() && file.exists()) file.deleteOnExit();",
        "releaseDeadBinding();",
    ):
        if marker not in client_text:
            fail(f"Runtime client guard missing: {marker}")
    for marker in ("RandomAccessFile", "getFD().sync", "setLength(0"):
        if marker in client_text:
            fail(f"per-frame synchronous temporary-file wipe returned: {marker}")

    face_sdk_text = read(FACE_SDK)
    for marker in ("scheduleReconnect", "similarityCalculation", "yuv2Bitmap"):
        if marker not in face_sdk_text:
            fail(f"FaceSDK compatibility path missing: {marker}")

    model_files = [
        p for p in ROOT.rglob("*")
        if p.is_file() and not is_excluded(p) and p.suffix.lower() in {".onnx", ".tflite", ".pt", ".bin"}
    ]
    if model_files:
        fail("legacy/model binary found: " + str(model_files[0].relative_to(ROOT)))
    keystores = [
        p for p in ROOT.rglob("*")
        if p.is_file() and not is_excluded(p) and p.suffix.lower() in {".jks", ".keystore", ".p12", ".pfx"}
    ]
    if keystores:
        fail("signing key must not be packaged: " + str(keystores[0].relative_to(ROOT)))

    test_files = [p for p in ROOT.rglob("*Test.kt") if p.is_file() and not is_excluded(p)]
    test_count = sum(read(path).count("@Test") for path in test_files)
    if test_count < 102:
        fail(f"regression test inventory shrank unexpectedly: {test_count} < 102")

    digest = hashlib.sha256(MAIN.read_bytes()).hexdigest()
    print(f"Source audit passed: {len(source_files)} Kotlin/Java/Gradle files, MainActivity SHA-256 {digest}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
