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
    "runtimeDecisionInFlight",
    "runtimeDecisionGeneration",
    "withTimeout(RUNTIME_DECISION_TIMEOUT_MS)",
    "RUNTIME_DECISION_STALL_RECOVERY_MS",
    "runtimeDecisionToken",
    "USER_STORE_SCHEMA_VERSION = 5",
    "USER_STORE_POLICY_VERSION = 5",
    "resetLegacyUserStoreForSingleTemplatePolicy",
    ".putBoolean(PASSIVE_LIVENESS_ENABLED_KEY, true)",
    ".putInt(RUNTIME_LIVENESS_LEVEL_KEY, 0)",
    ".putBoolean(OCCLUSION_CHECK_ENABLED_KEY, false)",
    "users.indices.filter { index -> users[index].isCompatible() }",
    "largestRuntimeFace",
    "findBestEnrollmentDuplicate",
    "ownedTemplates.forEach { it.fill(0) }",
    "EnrollmentStabilityTracker",
    "AUTH_STABLE_FRAMES = 1",
    "check_eye_closeness = true",
    "check_mouth_opened = true",
    "estimate_age_gender = false",
    "isAdminSessionActive",
    "copyForRuntimeDecision",
    "SystemClock.elapsedRealtime()",
    "CoroutineStart.UNDISPATCHED",
    "persistenceSnapshot.wipeTemplates()",
    "array.optJSONObject(i) ?: continue",
    "users.forEach { it.wipe() }",
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
    for marker in OBSOLETE_ENROLLMENT_MARKERS:
        if marker in main_text:
            fail(f"retired enrollment/authentication logic remains: {marker}")

    if "implementation project(\":runtime-client\")" not in read(APP_GRADLE):
        fail("app does not depend on :runtime-client")
    if 'include ":app", ":runtime-client"' not in read(SETTINGS):
        fail(":runtime-client is not included in settings.gradle")

    manifest_text = read(MANIFEST)
    for marker in (
        'android.permission.CAMERA',
        'io.ffacio.sdk.permission.BIND_RUNTIME',
        'com.kbyai.faceattribute',
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
    if test_count < 79:
        fail(f"regression test inventory shrank unexpectedly: {test_count} < 79")

    digest = hashlib.sha256(MAIN.read_bytes()).hexdigest()
    print(f"Source audit passed: {len(source_files)} Kotlin/Java/Gradle files, MainActivity SHA-256 {digest}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
