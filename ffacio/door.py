from __future__ import annotations

import json
from dataclasses import dataclass, replace
from urllib.error import URLError
from urllib.request import Request, urlopen

from .store import Settings, Store


@dataclass
class DoorDecision:
    allowed: bool
    user_id: str | None
    user_name: str | None
    score: float
    reason: str


class MockDoorController:
    def __init__(self, store: Store, settings: Settings | None = None) -> None:
        self.store = store
        self.settings = replace(settings or store.settings)

    def open(self, decision: DoorDecision) -> tuple[bool, str]:
        if not decision.allowed:
            self.store.log("door_open_blocked", reason=decision.reason)
            self.store.save()
            return False, f"문 열림 차단: {decision.reason}"
        self.store.log(
            "door_open_mock",
            user_id=decision.user_id,
            user_name=decision.user_name,
            score=round(decision.score, 4),
            reason=decision.reason,
        )
        self.store.save()
        return True, "모의 문 열림이 기록되었습니다."

    def record_manual_test(self) -> tuple[bool, str]:
        self.store.log("door_test_mock", reason="manual_test")
        self.store.save()
        return True, "모의 문 테스트가 기록되었습니다."


class HttpDoorController:
    def __init__(self, store: Store, settings: Settings | None = None) -> None:
        self.store = store
        self.settings = replace(settings or store.settings)

    def open(self, decision: DoorDecision) -> tuple[bool, str]:
        if not decision.allowed:
            self.store.log("door_open_blocked", reason=decision.reason)
            self.store.save()
            return False, f"문 열림 차단: {decision.reason}"
        url = self.settings.door_http_url
        if not self.settings.door_http_armed:
            self.store.log("door_open_blocked", reason="http_door_not_armed")
            self.store.save()
            return False, "HTTP 문 열림이 활성화되어 있지 않습니다."
        if not url:
            self.store.log("door_open_blocked", reason="http_door_not_configured")
            self.store.save()
            return False, "HTTP 문 열림 URL이 설정되지 않았습니다."
        payload = {
            "event": "open",
            "duration_ms": self.settings.open_duration_ms,
            "user_id": decision.user_id,
            "user_name": decision.user_name,
            "score": round(decision.score, 4),
            "reason": decision.reason,
        }
        ok, status, error = self._send(url, payload)
        if ok:
            self.store.log(
                "door_open_http",
                user_id=decision.user_id,
                user_name=decision.user_name,
                score=round(decision.score, 4),
                status=status,
            )
            message = f"HTTP 문 열림 성공: 상태 {status}."
        elif status is not None:
            self.store.log("door_open_failed", reason="http_status", status=status)
            message = f"HTTP 문 열림 실패: 상태 {status}."
        else:
            self.store.log("door_open_failed", reason="http_error", error=error)
            message = f"HTTP 문 열림 실패: {error}"
        self.store.save()
        return ok, message

    def _send(self, url: str, payload: dict) -> tuple[bool, int | None, str | None]:
        data_payload = json.dumps(payload).encode("utf-8")
        method = self.settings.door_http_method
        data = data_payload if method == "POST" else None
        headers = {"Content-Type": "application/json"}
        if self.settings.door_http_token:
            headers["Authorization"] = f"Bearer {self.settings.door_http_token}"
        try:
            request = Request(url, data=data, method=method, headers=headers)
            with urlopen(request, timeout=self.settings.door_http_timeout_ms / 1000) as response:
                status = int(getattr(response, "status", 0))
                return 200 <= status < 300, status, None
        except (OSError, URLError, TimeoutError, ValueError) as exc:
            return False, None, repr(exc)

    def record_manual_test(self) -> tuple[bool, str]:
        url = self.settings.door_http_test_url
        if not url:
            self.store.log("door_test_http_unconfigured")
            self.store.save()
            return False, "HTTP 테스트 URL이 설정되지 않았습니다."
        if self.settings.door_http_url and url == self.settings.door_http_url:
            self.store.log("door_test_http_refused_open_url")
            self.store.save()
            return False, "HTTP 테스트 URL은 실제 문 열림 URL과 달라야 합니다."
        else:
            ok, status, error = self._send(url, {"event": "test"})
            if ok:
                self.store.log("door_test_http_ok", status=status)
                self.store.save()
                return True, f"HTTP 릴레이 테스트 성공: 상태 {status}."
            elif status is not None:
                self.store.log("door_test_http_failed", reason="http_status", status=status)
                self.store.save()
                return False, f"HTTP 릴레이 테스트 실패: 상태 {status}."
            else:
                self.store.log("door_test_http_failed", reason="http_error", error=error)
                self.store.save()
                return False, f"HTTP 릴레이 테스트 실패: {error}"


def create_door_controller(store: Store, settings: Settings | None = None):
    snapshot = replace(settings or store.settings)
    if snapshot.door_mode == "http":
        return HttpDoorController(store, snapshot)
    return MockDoorController(store, snapshot)
