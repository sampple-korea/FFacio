from __future__ import annotations

import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def check_bearer(header: str | None, token: str) -> bool:
    if not token:
        return False
    return header == f"Bearer {token}"


def bounded_duration(payload: dict, default_ms: int = 1200) -> int:
    try:
        value = int(payload.get("duration_ms", default_ms))
    except (TypeError, ValueError):
        value = default_ms
    return max(200, min(5000, value))


class RelayState:
    def __init__(self) -> None:
        self.open_until = 0.0
        self.last_event: dict | None = None

    @property
    def is_open(self) -> bool:
        return time.monotonic() < self.open_until

    def open_for(self, duration_ms: int, payload: dict) -> None:
        self.open_until = time.monotonic() + duration_ms / 1000
        self.last_event = {"type": "open", "duration_ms": duration_ms, "payload": payload}

    def record_test(self, payload: dict) -> None:
        self.last_event = {"type": "test", "payload": payload}


def make_handler(token: str, state: RelayState):
    class MockDoorRelayHandler(BaseHTTPRequestHandler):
        server_version = "FFacioMockRelay/0.1"

        def do_GET(self) -> None:  # noqa: N802
            if self.path == "/open":
                self._json(405, {"ok": False, "error": "method_not_allowed"})
                return
            self._route({})

        def do_POST(self) -> None:  # noqa: N802
            length = int(self.headers.get("Content-Length", "0") or "0")
            body = self.rfile.read(length) if length else b"{}"
            try:
                payload = json.loads(body.decode("utf-8"))
            except json.JSONDecodeError:
                self._json(400, {"ok": False, "error": "invalid_json"})
                return
            self._route(payload)

        def _route(self, payload: dict) -> None:
            if not check_bearer(self.headers.get("Authorization"), token):
                self._json(401, {"ok": False, "error": "unauthorized"})
                return
            if self.path == "/test":
                state.record_test(payload)
                self._json(200, {"ok": True, "relay_open": state.is_open, "event": "test"})
                return
            if self.path == "/open":
                duration_ms = bounded_duration(payload)
                state.open_for(duration_ms, payload)
                self._json(200, {"ok": True, "relay_open": True, "duration_ms": duration_ms})
                return
            if self.path == "/state":
                self._json(200, {"ok": True, "relay_open": state.is_open, "last_event": state.last_event})
                return
            self._json(404, {"ok": False, "error": "not_found"})

        def _json(self, status: int, payload: dict) -> None:
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def log_message(self, fmt: str, *args) -> None:
            print(f"{self.address_string()} - {fmt % args}")

    return MockDoorRelayHandler


def main() -> None:
    parser = argparse.ArgumentParser(description="Safe local HTTP relay simulator for FFacio.")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--token", default="dev-secret")
    args = parser.parse_args()

    state = RelayState()
    server = ThreadingHTTPServer((args.host, args.port), make_handler(args.token, state))
    print(f"FFacio mock relay listening on http://{args.host}:{args.port}")
    print(f"Test URL: http://{args.host}:{args.port}/test")
    print(f"Open URL: http://{args.host}:{args.port}/open")
    if args.token:
        print("Bearer token required.")
    server.serve_forever()


if __name__ == "__main__":
    main()
