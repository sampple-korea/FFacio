# FFacio Door Relay Protocol

FFacio can call a local HTTP relay only after face recognition, liveness, stable multi-frame acceptance, and threshold checks pass.
HTTP relay settings are not enough by themselves: the user must also enable the explicit "open on successful authentication" arming checkbox in Settings.

## Endpoints

- Open URL: receives real open requests.
- Test URL: receives safe connectivity checks and must not unlock anything.

Do not point the test URL at the open URL.

## Local Simulator

The source tree includes a safe relay simulator at `scripts/mock_door_relay.py` for desk testing before wiring hardware:

```powershell
.\.venv\Scripts\python.exe scripts\mock_door_relay.py --port 8765 --token dev-secret
```

The simulator defaults to token `dev-secret`, rejects unauthenticated requests, and only allows `/open` over `POST`. The installer also copies this simulator to `tools/mock_door_relay.py` as a reference utility. It requires Python if you run it directly, so it is not required on target PCs where the actual relay is an ESP32 or another standalone HTTP device.

Configure FFacio settings with:

- Open URL: `http://127.0.0.1:8765/open`
- Test URL: `http://127.0.0.1:8765/test`
- Token: `dev-secret`

The simulator only records state in memory; it does not control a real lock.

## ESP32 Example

An ESP32 HTTP relay sketch is included at `hardware/esp32_http_relay/esp32_http_relay.ino`. Replace Wi-Fi credentials, set a long random Bearer token, verify the relay polarity, and bench-test with the test endpoint before connecting a lock.

## Request

`POST` is recommended. `GET` is supported for simple relay devices, but the JSON body is only sent with `POST`.

Open request body:

```json
{
  "event": "open",
  "duration_ms": 1200,
  "user_id": "registered-user-id",
  "user_name": "Name",
  "score": 0.8342,
  "reason": "face_match"
}
```

Test request body:

```json
{
  "event": "test"
}
```

If a Bearer token is configured, FFacio sends:

```http
Authorization: Bearer <token>
```

The token is protected in the local store with Windows DPAPI.
For real hardware, avoid exposing the relay on a shared Wi-Fi/LAN. Plain HTTP Bearer tokens can be captured on an untrusted network; prefer a local wired bridge, isolated network, HTTPS-capable controller, or a relay that implements nonce/HMAC protection.

## Response

Return any `2xx` status for success. Any non-2xx status, timeout, missing URL, or network error is logged as failure and does not count as a door opening.

## Hardware Safety

The relay device should also enforce its own timeout, require the Bearer token, and default to locked on reboot, network loss, or application crash.
