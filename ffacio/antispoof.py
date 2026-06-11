from __future__ import annotations

from dataclasses import dataclass

import cv2
import numpy as np


ANTISPOOF_INPUT_SIZE = (80, 80)
ANTISPOOF_MESSAGE = "사진이나 화면으로 보이는 얼굴입니다. 실제 얼굴을 카메라에 보여주세요"


@dataclass(frozen=True)
class AntiSpoofResult:
    live_score: float
    print_score: float
    replay_score: float
    state: str

    @property
    def is_live(self) -> bool:
        return self.state == "live"


def softmax(values: np.ndarray) -> np.ndarray:
    logits = values.astype(np.float32).reshape(-1)
    shifted = logits - float(np.max(logits))
    exp = np.exp(shifted)
    return exp / max(float(np.sum(exp)), 1e-8)


def classify_antispoof_logits(logits: np.ndarray, threshold: float) -> AntiSpoofResult:
    probs = softmax(logits)
    live = float(probs[0]) if probs.size > 0 else 0.0
    printed = float(probs[1]) if probs.size > 1 else 0.0
    replay = float(probs[2]) if probs.size > 2 else 0.0
    if live >= threshold and live >= printed and live >= replay:
        state = "live"
    elif replay >= printed:
        state = "replay_attack"
    else:
        state = "print_attack"
    return AntiSpoofResult(live, printed, replay, state)


def expanded_face_crop(frame: np.ndarray, bbox: tuple[int, int, int, int], scale: float = 1.25) -> np.ndarray:
    x, y, w, h = bbox
    height, width = frame.shape[:2]
    side_w = int(w * scale)
    side_h = int(h * scale)
    cx = x + w // 2
    cy = y + h // 2
    left = max(0, cx - side_w // 2)
    top = max(0, cy - side_h // 2)
    right = min(width, cx + side_w // 2)
    bottom = min(height, cy + side_h // 2)
    if right <= left or bottom <= top:
        return np.array([], dtype=frame.dtype)
    return frame[top:bottom, left:right]


class MiniFASNetAntiSpoof:
    model_id = "antispoof.minifasnet_v2"

    def __init__(self, model_path: str) -> None:
        self.net = cv2.dnn.readNetFromONNX(model_path)

    def predict(self, frame_bgr: np.ndarray, bbox: tuple[int, int, int, int], threshold: float) -> AntiSpoofResult:
        crop = expanded_face_crop(frame_bgr, bbox)
        if crop.size == 0:
            return AntiSpoofResult(0.0, 0.0, 0.0, "invalid_crop")
        resized = cv2.resize(crop, ANTISPOOF_INPUT_SIZE, interpolation=cv2.INTER_LINEAR)
        blob = ((resized.astype(np.float32) - 127.5) / 128.0).transpose(2, 0, 1)[np.newaxis, :]
        self.net.setInput(blob)
        logits = self.net.forward()
        return classify_antispoof_logits(logits, threshold)
