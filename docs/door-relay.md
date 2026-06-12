# FFacio Android Door Relay Protocol

FFacio Android can call a local relay only after face recognition, liveness, stable multi-frame acceptance, and threshold checks pass.
Relay settings are not enough by themselves: an admin must also enable the explicit open-on-success arming control.

Unknown faces, ambiguous matches, liveness failures, camera/model/storage errors, and relay errors are fail-closed.

## Health Check

Android derives a safe non-opening health-check endpoint from the configured HTTPS open URL.

Example:

- Open URL: `https://door.example/relay/open`
- Health check: `https://door.example/relay/.well-known/ffacio-door-relay`

The app sends:

```http
GET <open-url-parent>/.well-known/ffacio-door-relay
Authorization: Bearer <token>
```

Return a `2xx` status only when the relay is reachable, authenticated, and safe to use. This endpoint must never energize the lock.

## Open Request

The app may call the configured open URL with `POST` or `GET`, depending on relay settings. The privacy-preserving Android payload does not include the recognized user name:

```json
{
  "event": "accepted",
  "source": "ffacio-android"
}
```

If a Bearer token is configured, the app sends:

```http
Authorization: Bearer <token>
```

The token is protected with Android Keystore AES-GCM in the local app store.

## ESP32 Reference

An ESP32 HTTP relay sketch is included at `hardware/esp32_http_relay/esp32_http_relay.ino`.
Replace Wi-Fi credentials, set a long random Bearer token, verify relay polarity, and bench-test the non-opening health endpoint before connecting a lock.

Android requires an HTTPS URL when token-backed relay control is used, so put simple HTTP relay hardware behind a trusted HTTPS bridge or use HTTPS-capable relay hardware.

## Hardware Safety

The relay device should enforce its own timeout, require authentication, and default to locked on reboot, network loss, application crash, or malformed requests.
