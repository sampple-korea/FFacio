from __future__ import annotations

import random
from dataclasses import dataclass, field
from time import monotonic


PROMPTS = {
    "center": "정면을 보고 잠시 유지해 주세요",
    "left": "왼쪽을 보고 잠시 유지해 주세요",
    "right": "오른쪽을 보고 잠시 유지해 주세요",
}


@dataclass
class LivenessChallenge:
    steps: list[str] = field(default_factory=list)
    index: int = 0
    started_at: float = field(default_factory=monotonic)
    passed_until: float = 0.0
    timeout_seconds: float = 9.0
    grace_seconds: float = 3.5
    min_step_seconds: float = 0.18
    hold_seconds: float = 0.42
    last_step_at: float = 0.0
    candidate_pose: str = ""
    candidate_since: float = 0.0

    def reset(self, count: int = 3) -> None:
        first = ["center"]
        rest = random.sample(["left", "right"], k=2)
        self.steps = (first + rest)[: max(1, count)]
        self.index = 0
        self.started_at = monotonic()
        self.passed_until = 0.0
        self.last_step_at = 0.0
        self.candidate_pose = ""
        self.candidate_since = 0.0

    def prompt(self) -> str:
        if self.ready:
            return "라이브니스 확인됨"
        if not self.steps:
            self.reset()
        return PROMPTS.get(self.steps[self.index], "카메라를 바라봐 주세요")

    @property
    def ready(self) -> bool:
        return monotonic() < self.passed_until

    def update(self, pose: str, quality: float) -> bool:
        if not self.steps:
            self.reset()
        if monotonic() - self.started_at > self.timeout_seconds:
            self.reset(len(self.steps))
            return False
        if quality < 0.58:
            self.candidate_pose = ""
            self.candidate_since = 0.0
            return False
        expected = self.steps[self.index]
        now = monotonic()
        if pose != expected:
            self.candidate_pose = ""
            self.candidate_since = 0.0
            return self.ready
        if self.index > 0 and now - self.last_step_at < self.min_step_seconds:
            return False
        if self.hold_seconds <= 0:
            self.index += 1
            self.last_step_at = now
            if self.index >= len(self.steps):
                count = len(self.steps)
                self.reset(count)
                self.passed_until = monotonic() + self.grace_seconds
                return True
            return self.ready
        if self.candidate_pose != pose:
            self.candidate_pose = pose
            self.candidate_since = now
            return self.ready
        if now - self.candidate_since < self.hold_seconds:
            return self.ready
        self.index += 1
        self.last_step_at = now
        self.candidate_pose = ""
        self.candidate_since = 0.0
        if self.index >= len(self.steps):
            count = len(self.steps)
            self.reset(count)
            self.passed_until = monotonic() + self.grace_seconds
            return True
        return self.ready

    def progress(self) -> tuple[int, int]:
        return min(self.index, len(self.steps)), len(self.steps)
