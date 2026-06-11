from __future__ import annotations

import json
import tempfile
import threading
import unittest
from http.client import HTTPConnection
from http.server import ThreadingHTTPServer
from pathlib import Path
from unittest.mock import Mock, patch

import numpy as np

from ffacio.antispoof import classify_antispoof_logits
from ffacio.door import DoorDecision, HttpDoorController, MockDoorController, create_door_controller
from ffacio.engine import FaceEngine, MatchResult, average_embeddings, normalize, score_quality, stable_match_decision
from ffacio.liveness import LivenessChallenge
from ffacio.models import verify_models
from ffacio.store import Settings, Store, UserTemplate, purge_local_data
from scripts.mock_door_relay import RelayState, bounded_duration, check_bearer, make_handler


class DummyEngine(FaceEngine):
    name = "Dummy"
    engine_id = "dummy-engine"
    model_version = "test"


class CoreTests(unittest.TestCase):
    def test_normalize_and_average_embeddings(self) -> None:
        zero = normalize(np.zeros(4, dtype=np.float32))
        self.assertTrue(np.all(zero == 0))
        unit = normalize(np.array([3, 4], dtype=np.float32))
        self.assertAlmostEqual(float(np.linalg.norm(unit)), 1.0)
        avg = average_embeddings([np.array([1, 0], dtype=np.float32), np.array([1, 0], dtype=np.float32)])
        self.assertAlmostEqual(float(np.linalg.norm(avg)), 1.0)

    def test_match_states_and_engine_compatibility(self) -> None:
        engine = DummyEngine()
        settings = Settings(threshold=0.8, ambiguous_margin=0.05)
        embedding = normalize(np.array([1, 0, 0], dtype=np.float32))
        self.assertEqual(engine.match(embedding, [], settings).state, "empty")

        wrong_engine_user = UserTemplate.create(
            "Wrong", "Other", embedding, 1, 1.0, engine_id="other-engine", model_version="x"
        )
        self.assertEqual(engine.match(embedding, [wrong_engine_user], settings).state, "empty")

        accepted_user = UserTemplate.create(
            "A", "Dummy", embedding, 1, 1.0, engine_id=engine.engine_id, model_version=engine.model_version
        )
        self.assertEqual(engine.match(embedding, [accepted_user], settings).state, "accepted")

        unknown = normalize(np.array([0, 1, 0], dtype=np.float32))
        self.assertEqual(engine.match(unknown, [accepted_user], settings).state, "unknown")

        user_b = UserTemplate.create(
            "B",
            "Dummy",
            normalize(np.array([0.99, 0.02, 0], dtype=np.float32)),
            1,
            1.0,
            engine_id=engine.engine_id,
            model_version=engine.model_version,
        )
        self.assertEqual(engine.match(embedding, [accepted_user, user_b], settings).state, "ambiguous")

    def test_stable_match_decision_requires_three_same_accepts(self) -> None:
        user = UserTemplate.create(
            "A",
            "Dummy",
            normalize(np.array([1, 0, 0], dtype=np.float32)),
            1,
            1.0,
            engine_id="dummy-engine",
            model_version="test",
        )
        accepted = MatchResult("accepted", user, 0.91, 0.0, "accepted")
        self.assertEqual(stable_match_decision([accepted]).state, "pending")
        self.assertEqual(stable_match_decision([accepted, accepted]).state, "pending")
        stable = stable_match_decision([accepted, accepted, accepted])
        self.assertEqual(stable.state, "accepted")
        self.assertEqual(stable.user.id, user.id)

        unknown = MatchResult("unknown", None, 0.2, 0.0, "unknown")
        self.assertEqual(stable_match_decision([accepted, unknown]).state, "unknown")
        self.assertEqual(stable_match_decision([accepted, unknown, accepted, accepted]).state, "pending")

    def test_liveness_challenge(self) -> None:
        challenge = LivenessChallenge(timeout_seconds=100, min_step_seconds=0, hold_seconds=0)
        challenge.steps = ["center", "left", "right"]
        challenge.index = 0
        self.assertFalse(challenge.update("left", 0.9))
        self.assertFalse(challenge.update("center", 0.4))
        self.assertFalse(challenge.update("unknown", 0.9))
        self.assertFalse(challenge.update("center", 0.9))
        self.assertFalse(challenge.update("left", 0.9))
        self.assertTrue(challenge.update("right", 0.9))
        self.assertTrue(challenge.ready)

        challenge.timeout_seconds = -1
        self.assertFalse(challenge.update("center", 0.9))

    def test_liveness_requires_time_between_steps(self) -> None:
        challenge = LivenessChallenge(timeout_seconds=100, min_step_seconds=60, hold_seconds=0)
        challenge.steps = ["center", "left"]
        challenge.index = 0
        self.assertFalse(challenge.update("center", 0.9))
        self.assertFalse(challenge.update("left", 0.9))

    def test_liveness_requires_stable_pose_hold(self) -> None:
        challenge = LivenessChallenge(timeout_seconds=100, min_step_seconds=0, hold_seconds=60)
        challenge.steps = ["center"]
        challenge.index = 0
        self.assertFalse(challenge.update("center", 0.9))
        self.assertFalse(challenge.update("center", 0.9))
        challenge.candidate_since -= 61
        self.assertTrue(challenge.update("center", 0.9))

        reset_check = LivenessChallenge(timeout_seconds=100, min_step_seconds=0, hold_seconds=60)
        reset_check.steps = ["left"]
        reset_check.index = 0
        self.assertFalse(reset_check.update("left", 0.9))
        self.assertFalse(reset_check.update("right", 0.9))
        self.assertEqual(reset_check.candidate_pose, "")

    def test_quality_penalizes_flat_or_overbright_face_crops(self) -> None:
        settings = Settings(detection_confidence=0.85)
        flat = np.full((240, 240, 3), 128, dtype=np.uint8)
        quality, message = score_quality(flat, (40, 40, 150, 150), 0.99, settings)
        self.assertLess(quality, 0.58)
        self.assertEqual(message, "얼굴 질감이 부족합니다")

        overbright = np.full((240, 240, 3), 255, dtype=np.uint8)
        quality, message = score_quality(overbright, (40, 40, 150, 150), 0.99, settings)
        self.assertLess(quality, 0.58)
        self.assertEqual(message, "빛 반사가 너무 강합니다")

    def test_passive_antispoof_classification(self) -> None:
        live = classify_antispoof_logits(np.array([0.1, 4.0, -0.2], dtype=np.float32), threshold=0.55)
        self.assertEqual(live.state, "live")
        self.assertGreater(live.live_score, 0.55)

        printed = classify_antispoof_logits(np.array([3.0, 0.1, 0.0], dtype=np.float32), threshold=0.70)
        self.assertEqual(printed.state, "print_attack")
        self.assertFalse(printed.is_live)

        replay = classify_antispoof_logits(np.array([0.1, 0.0, 3.0], dtype=np.float32), threshold=0.70)
        self.assertEqual(replay.state, "replay_attack")
        self.assertFalse(replay.is_live)

        low_confidence_live = classify_antispoof_logits(np.array([0.3, 0.2, 0.1], dtype=np.float32), threshold=0.70)
        self.assertFalse(low_confidence_live.is_live)

    def test_model_manifest_verification(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            model = root / "fake.onnx"
            model.write_bytes(b"model")
            import hashlib

            digest = hashlib.sha256(b"model").hexdigest()
            (root / "models.manifest.json").write_text(
                json.dumps(
                    {
                        "version": "test",
                        "files": [
                            {
                                "id": "fake",
                                "path": "fake.onnx",
                                "engine": "fake",
                                "size": 5,
                                "sha256": digest,
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            bundle = verify_models(root)
            self.assertEqual(bundle.path_for("fake"), model)
            model.write_bytes(b"tampered")
            with self.assertRaises(RuntimeError):
                verify_models(root)

            model.write_bytes(b"model")
            (root / "models.manifest.json").write_text(
                json.dumps(
                    {
                        "version": "test",
                        "offline_first": True,
                        "files": [
                            {
                                "id": "fake",
                                "path": "fake.onnx",
                                "engine": "fake",
                                "size": 5,
                                "sha256": digest,
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            with self.assertRaises(RuntimeError):
                verify_models(root)

    def test_store_atomic_save_and_recovery(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "store.json"
            store = Store()
            user = UserTemplate.create(
                "Secret",
                "Dummy",
                normalize(np.array([1, 2, 3], dtype=np.float32)),
                1,
                1.0,
                engine_id="dummy-engine",
                model_version="test",
            )
            store.users.append(user)
            store.log("hello", value=1)
            store.save(path)
            raw_file = path.read_text(encoding="utf-8")
            self.assertIn("embedding_ciphertext", raw_file)
            self.assertNotIn("0.267261", raw_file)
            loaded = Store.load(path)
            self.assertEqual(loaded.logs[-1]["event"], "hello")
            self.assertEqual(loaded.users[0].name, "Secret")

            path.write_text("{ broken", encoding="utf-8")
            recovered = Store.load(path)
            self.assertEqual(recovered.logs[-1]["event"], "hello")

            path.write_text("{ broken", encoding="utf-8")
            path.with_suffix(path.suffix + ".bak").write_text("{ also broken", encoding="utf-8")
            recreated = Store.load(path)
            self.assertEqual(recreated.logs[-1]["event"], "store_recreated_after_corruption")

    def test_purge_local_data_removes_store_family(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            path = root / "ffacio_store.json"
            path.write_text("{}", encoding="utf-8")
            path.with_suffix(path.suffix + ".bak").write_text("{}", encoding="utf-8")
            with patch("ffacio.store.user_data_dir", return_value=root), patch(
                "ffacio.store.logs_dir", return_value=root / "logs"
            ):
                (root / "logs").mkdir()
                (root / "logs" / "x.log").write_text("log", encoding="utf-8")
                deleted = purge_local_data(path)
            self.assertFalse(path.exists())
            self.assertFalse(path.with_suffix(path.suffix + ".bak").exists())
            self.assertFalse((root / "logs").exists())
            self.assertGreaterEqual(len(deleted), 2)

    def test_settings_are_sanitized_and_logs_are_capped(self) -> None:
        store = Store.from_dict(
            {
                "settings": {
                    "camera_index": -5,
                    "threshold": 0.1,
                    "ambiguous_margin": 0.001,
                    "enroll_samples": 1,
                    "detection_confidence": 0.1,
                    "open_duration_ms": 1,
                    "liveness_enabled": False,
                    "liveness_steps": 99,
                    "door_http_token": "relay-secret",
                    "door_http_armed": True,
                },
                "logs": [{"event": str(i)} for i in range(600)],
            }
        )
        self.assertEqual(store.settings.camera_index, 0)
        self.assertGreaterEqual(store.settings.threshold, 0.45)
        self.assertTrue(store.settings.liveness_enabled)
        self.assertFalse(store.settings.door_http_armed)
        store.settings.door_mode = "http"
        store.settings.door_http_armed = True
        store.settings.door_http_method = "GET"
        store.settings.normalize()
        self.assertEqual(store.settings.door_http_method, "POST")
        self.assertEqual(len(store.to_dict()["logs"]), 500)
        encoded = store.to_dict()["settings"]
        self.assertIn("door_http_token_ciphertext", encoded)
        self.assertNotIn("door_http_token", encoded)
        roundtrip = Store.from_dict({"settings": encoded})
        self.assertEqual(roundtrip.settings.door_http_token, "relay-secret")

    def test_door_controller_blocks_denied_decision(self) -> None:
        store = Store()
        store.save = lambda *args, **kwargs: None  # type: ignore[method-assign]
        door = MockDoorController(store)
        door.open(DoorDecision(False, None, None, 0.0, "test_denied"))
        self.assertEqual(store.logs[-1]["event"], "door_open_blocked")

    def test_http_door_controller_fail_closed_and_success_logging(self) -> None:
        store = Store()
        store.save = lambda *args, **kwargs: None  # type: ignore[method-assign]
        store.settings.door_mode = "http"
        door = create_door_controller(store)
        self.assertIsInstance(door, HttpDoorController)

        decision = DoorDecision(True, "u1", "A", 0.91, "face_match")
        door.open(decision)
        self.assertEqual(store.logs[-1]["event"], "door_open_blocked")
        self.assertEqual(store.logs[-1]["reason"], "http_door_not_armed")

        store.settings.door_http_armed = True
        door = create_door_controller(store)
        door.open(decision)
        self.assertEqual(store.logs[-1]["event"], "door_open_blocked")
        self.assertEqual(store.logs[-1]["reason"], "http_door_not_configured")

        store.settings.door_http_url = "http://127.0.0.1/open"
        store.settings.door_http_test_url = "http://127.0.0.1/test"
        store.settings.door_http_token = "relay-secret"
        door = create_door_controller(store)
        response = Mock()
        response.status = 204
        response.__enter__ = Mock(return_value=response)
        response.__exit__ = Mock(return_value=False)
        with patch("ffacio.door.urlopen", return_value=response) as opener:
            door.open(decision)
        self.assertEqual(store.logs[-1]["event"], "door_open_failed")
        self.assertFalse(opener.called)

        store.settings.door_http_token = ""
        door = create_door_controller(store)
        with patch("ffacio.door.urlopen", return_value=response) as opener:
            door.open(decision)
        self.assertEqual(store.logs[-1]["event"], "door_open_http")
        self.assertEqual(store.logs[-1]["status"], 204)
        self.assertTrue(opener.called)
        request = opener.call_args.args[0]
        self.assertIsNone(request.get_header("Authorization"))
        self.assertEqual(request.full_url, "http://127.0.0.1/open")

        with patch("ffacio.door.urlopen", return_value=response) as opener:
            ok, message = door.record_manual_test()
        self.assertTrue(ok)
        self.assertIn("204", message)
        self.assertEqual(store.logs[-1]["event"], "door_test_http_ok")
        request = opener.call_args.args[0]
        self.assertEqual(request.full_url, "http://127.0.0.1/test")

        store.settings.door_http_test_url = store.settings.door_http_url
        door = create_door_controller(store)
        ok, message = door.record_manual_test()
        self.assertFalse(ok)
        self.assertIn("달라야", message)
        self.assertEqual(store.logs[-1]["event"], "door_test_http_refused_open_url")
        store.settings.door_http_test_url = "http://127.0.0.1/test"
        door = create_door_controller(store)

        with patch("ffacio.door.urlopen", side_effect=TimeoutError("slow")):
            door.open(decision)
        self.assertEqual(store.logs[-1]["event"], "door_open_failed")

        store.settings.door_http_url = "not-a-url"
        door = create_door_controller(store)
        door.open(decision)
        self.assertEqual(store.logs[-1]["event"], "door_open_failed")

    def test_mock_relay_helpers(self) -> None:
        self.assertFalse(check_bearer(None, ""))
        self.assertTrue(check_bearer("Bearer abc", "abc"))
        self.assertFalse(check_bearer("Bearer wrong", "abc"))
        self.assertEqual(bounded_duration({"duration_ms": 1}), 200)
        self.assertEqual(bounded_duration({"duration_ms": 99999}), 5000)
        self.assertEqual(bounded_duration({"duration_ms": "1500"}), 1500)
        self.assertEqual(bounded_duration({"duration_ms": "bad"}), 1200)

    def test_mock_relay_http_routes(self) -> None:
        state = RelayState()
        server = ThreadingHTTPServer(("127.0.0.1", 0), make_handler("dev-secret", state))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        port = server.server_address[1]

        def request(method: str, path: str, body: dict | str | None = None, token: str | None = "dev-secret"):
            conn = HTTPConnection("127.0.0.1", port, timeout=3)
            headers = {}
            payload = None
            if token is not None:
                headers["Authorization"] = f"Bearer {token}"
            if body is not None:
                headers["Content-Type"] = "application/json"
                payload = body if isinstance(body, str) else json.dumps(body)
            conn.request(method, path, body=payload, headers=headers)
            response = conn.getresponse()
            data = response.read().decode("utf-8")
            conn.close()
            return response.status, data

        try:
            status, _ = request("POST", "/test", {"event": "test"}, token=None)
            self.assertEqual(status, 401)
            status, _ = request("GET", "/open")
            self.assertEqual(status, 405)
            status, _ = request("POST", "/open", "{bad")
            self.assertEqual(status, 400)
            status, _ = request("POST", "/missing", {})
            self.assertEqual(status, 404)
            status, body = request("POST", "/open", {"duration_ms": 250})
            self.assertEqual(status, 200)
            self.assertTrue(state.is_open)
            status, body = request("GET", "/state")
            self.assertEqual(status, 200)
            self.assertIn("relay_open", body)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=3)


if __name__ == "__main__":
    unittest.main()
