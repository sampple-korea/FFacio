from __future__ import annotations

from dataclasses import dataclass

import cv2
import numpy as np

from .models import SFACE_MODEL, YUNET_MODEL, ModelBundle, ModelVerificationError, ensure_models, verify_models
from .store import Settings, UserTemplate


@dataclass
class FaceObservation:
    face: np.ndarray
    bbox: tuple[int, int, int, int]
    confidence: float
    embedding: np.ndarray | None
    quality: float
    message: str
    pose: str = "unknown"


@dataclass
class MatchResult:
    state: str
    user: UserTemplate | None
    score: float
    second_score: float
    message: str


class FaceEngine:
    name = "base"
    engine_id = "base"
    model_version = "unknown"

    def observe(self, frame: np.ndarray, settings: Settings) -> FaceObservation:
        raise NotImplementedError

    def compatible_users(self, users: list[UserTemplate]) -> list[UserTemplate]:
        return [user for user in users if (user.engine_id or user.model) == self.engine_id]

    def match(self, embedding: np.ndarray, users: list[UserTemplate], settings: Settings) -> MatchResult:
        users = self.compatible_users(users)
        if not users:
            return MatchResult("empty", None, 0.0, 0.0, "현재 엔진과 호환되는 등록자가 없습니다")
        scores = []
        for user in users:
            ref = normalize(np.array(user.embedding, dtype=np.float32))
            if ref.shape != embedding.shape:
                continue
            scores.append((float(np.dot(embedding, ref)), user))
        if not scores:
            return MatchResult("empty", None, 0.0, 0.0, "현재 엔진과 호환되는 등록자가 없습니다")
        scores.sort(key=lambda item: item[0], reverse=True)
        best_score, best_user = scores[0]
        second_score = scores[1][0] if len(scores) > 1 else 0.0
        if best_score < settings.threshold:
            return MatchResult("unknown", None, best_score, second_score, "알 수 없는 얼굴")
        if len(scores) > 1 and (best_score - second_score) < settings.ambiguous_margin:
            return MatchResult("ambiguous", None, best_score, second_score, "인증 보류")
        return MatchResult("accepted", best_user, best_score, second_score, f"{best_user.name} 인증")


class OpenCVSFaceEngine(FaceEngine):
    name = "OpenCV YuNet + SFace"
    engine_id = "opencv-sface"

    def __init__(self) -> None:
        bundle = verify_models()
        self.model_version = bundle.version
        paths = ensure_models()
        self.detector = cv2.FaceDetectorYN.create(
            str(paths[YUNET_MODEL]), "", (320, 320), 0.85, 0.3, 5000
        )
        self.recognizer = cv2.FaceRecognizerSF.create(str(paths[SFACE_MODEL]), "")

    def observe(self, frame: np.ndarray, settings: Settings) -> FaceObservation:
        height, width = frame.shape[:2]
        self.detector.setInputSize((width, height))
        _ok, faces = self.detector.detect(frame)
        if faces is None or len(faces) == 0:
            return FaceObservation(np.array([]), (0, 0, 0, 0), 0.0, None, 0.0, "카메라를 바라봐 주세요")
        if len(faces) > 1:
            face = largest_face(faces)
            return self._make_observation(frame, face, settings, "한 명만 화면에 들어오게 해주세요")
        return self._make_observation(frame, faces[0], settings, "")

    def _make_observation(
        self, frame: np.ndarray, face: np.ndarray, settings: Settings, forced_message: str
    ) -> FaceObservation:
        x, y, w, h = [int(v) for v in face[:4]]
        conf = float(face[-1])
        quality, message = score_quality(frame, (x, y, w, h), conf, settings)
        if forced_message:
            quality = min(quality, 0.3)
            message = forced_message
        embedding = None
        if quality >= 0.58:
            aligned = self.recognizer.alignCrop(frame, face)
            raw = self.recognizer.feature(aligned)
            embedding = normalize(raw.flatten().astype(np.float32))
        return FaceObservation(face, (x, y, w, h), conf, embedding, quality, message, estimate_pose_from_yunet(face))


class InsightFaceEngine(FaceEngine):
    name = "InsightFace"
    engine_id = "insightface-buffalo-l"

    def __init__(self) -> None:
        from insightface.app import face_analysis

        bundle: ModelBundle = verify_models()
        self.model_version = bundle.version
        bundle.require_insightface_model_dir()

        def offline_ensure_available(sub_dir: str, name: str, root: str = "~/.insightface") -> str:
            model_dir = bundle.insightface_root / sub_dir / name
            if not model_dir.exists():
                raise ModelVerificationError(f"Offline InsightFace model is missing: {model_dir}")
            return str(model_dir)

        face_analysis.ensure_available = offline_ensure_available
        self.app = face_analysis.FaceAnalysis(
            name="buffalo_l",
            root=str(bundle.insightface_root),
            allowed_modules=["detection", "recognition"],
            providers=["CPUExecutionProvider"],
        )
        self.app.prepare(ctx_id=-1, det_size=(640, 640))

    def observe(self, frame: np.ndarray, settings: Settings) -> FaceObservation:
        faces = self.app.get(frame)
        if not faces:
            return FaceObservation(np.array([]), (0, 0, 0, 0), 0.0, None, 0.0, "카메라를 바라봐 주세요")
        faces.sort(key=lambda f: (f.bbox[2] - f.bbox[0]) * (f.bbox[3] - f.bbox[1]), reverse=True)
        face = faces[0]
        x1, y1, x2, y2 = [int(v) for v in face.bbox]
        conf = float(getattr(face, "det_score", 0.99))
        quality, message = score_quality(frame, (x1, y1, x2 - x1, y2 - y1), conf, settings)
        embedding = normalize(np.array(face.normed_embedding, dtype=np.float32)) if quality >= 0.58 else None
        pose = estimate_pose_from_keypoints(getattr(face, "kps", None))
        return FaceObservation(np.array([]), (x1, y1, x2 - x1, y2 - y1), conf, embedding, quality, message, pose)


def create_engine(prefer_insightface: bool = True) -> FaceEngine:
    if prefer_insightface:
        try:
            return InsightFaceEngine()
        except Exception as exc:
            fallback = OpenCVSFaceEngine()
            setattr(fallback, "fallback_reason", repr(exc))
            return fallback
    return OpenCVSFaceEngine()


def largest_face(faces: np.ndarray) -> np.ndarray:
    areas = faces[:, 2] * faces[:, 3]
    return faces[int(np.argmax(areas))]


def normalize(vector: np.ndarray) -> np.ndarray:
    vector = vector.astype(np.float32)
    norm = np.linalg.norm(vector)
    if norm == 0:
        return vector
    return vector / norm


def estimate_pose_from_yunet(face: np.ndarray) -> str:
    if len(face) < 14:
        return "unknown"
    points = face[4:14].reshape(5, 2)
    return estimate_pose_from_keypoints(points)


def estimate_pose_from_keypoints(points) -> str:
    if points is None:
        return "unknown"
    pts = np.array(points, dtype=np.float32)
    if pts.shape[0] < 3:
        return "unknown"
    left_eye, right_eye, nose = pts[0], pts[1], pts[2]
    eye_center = (left_eye + right_eye) / 2
    eye_dist = float(np.linalg.norm(left_eye - right_eye))
    if eye_dist < 1:
        return "unknown"
    nose_offset = float((nose[0] - eye_center[0]) / eye_dist)
    if nose_offset > 0.18:
        return "left"
    if nose_offset < -0.18:
        return "right"
    return "center"


def average_embeddings(embeddings: list[np.ndarray]) -> np.ndarray:
    return normalize(np.mean(np.vstack(embeddings), axis=0))


def stable_match_decision(recent_matches) -> MatchResult:
    recent = list(recent_matches)
    if not recent:
        return MatchResult("empty", None, 0.0, 0.0, "")
    tail = recent[-3:]
    if len(tail) == 3 and all(m.state == "accepted" and m.user for m in tail):
        user_ids = [m.user.id for m in tail if m.user]
        if len(set(user_ids)) == 1:
            score = float(np.mean([m.score for m in tail]))
            user = tail[-1].user
            return MatchResult("accepted", user, score, 0.0, "인증")
    latest = recent[-1]
    if latest.state == "accepted":
        return MatchResult("pending", latest.user, latest.score, latest.second_score, "안정적으로 확인 중입니다")
    return latest


def score_quality(
    frame: np.ndarray, bbox: tuple[int, int, int, int], confidence: float, settings: Settings
) -> tuple[float, str]:
    x, y, w, h = bbox
    height, width = frame.shape[:2]
    area_ratio = (w * h) / max(1, width * height)
    center_x = x + w / 2
    center_y = y + h / 2
    off_x = abs(center_x - width / 2) / max(1, width / 2)
    off_y = abs(center_y - height / 2) / max(1, height / 2)
    crop = frame[max(0, y) : min(height, y + h), max(0, x) : min(width, x + w)]
    if crop.size == 0:
        return 0.0, "얼굴을 화면 안에 맞춰주세요"
    gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
    brightness = float(np.mean(gray))
    contrast = float(np.std(gray))
    blur = float(cv2.Laplacian(gray, cv2.CV_64F).var())
    saturated_ratio = float(np.mean(np.max(crop, axis=2) > 245))

    penalties = 0.0
    message = "좋습니다"
    if confidence < settings.detection_confidence:
        penalties += 0.35
        message = "얼굴을 더 선명하게 보여주세요"
    if area_ratio < 0.045:
        penalties += 0.28
        message = "조금 가까이 와 주세요"
    elif area_ratio > 0.42:
        penalties += 0.22
        message = "조금 뒤로 이동해 주세요"
    if off_x > 0.32 or off_y > 0.32:
        penalties += 0.22
        message = "중앙에 맞춰주세요"
    if brightness < 55:
        penalties += 0.24
        message = "조명을 조금 밝게 해주세요"
    elif brightness > 215:
        penalties += 0.18
        message = "빛이 너무 강합니다"
    if blur < 45:
        penalties += 0.24
        message = "잠시 멈춰주세요"
    if contrast < 18:
        penalties += 0.18
        message = "얼굴 질감이 부족합니다"
    if saturated_ratio > 0.18:
        penalties += 0.16
        message = "빛 반사가 너무 강합니다"

    quality = max(0.0, min(1.0, confidence - penalties))
    return quality, message
