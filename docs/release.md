# FFacio Android Release Notes

## Android 0.4.1

- Shows a clear welcome message with the accepted user's name after successful face authentication.
- Keeps door relay setup problems as secondary guidance instead of replacing the successful authentication state.
- Treats an armed-but-unconfigured relay as authentication-only, so a missing relay URL/token does not make a valid face approval look like a failure.

## Android 0.4.0

- Makes enrollment guidance more Face ID-like by turning the camera guide ring into the primary registration progress surface.
- Requires a guided five-step center/left/right/left/right enrollment sequence with a short hold gate before each sample is saved, reducing one-frame pose mistakes and motion-blur samples.
- Adds progress and hold arcs plus center/left/right collection markers around the face guide ring while removing central registration symbols so the user's face is less obstructed.
- Keeps CameraX bound while encrypted enrollment storage is busy, so final save no longer tears down the camera pipeline and looks less like a freeze.
- Uses stored enrollment sample maximums for duplicate-registration checks, not only the averaged face template.
- Updates enrollment messages to tell the user the next required pose directly.

## Android 0.3.17

- Adds an admin-only authentication decision log for real-device calibration and false-accept/false-reject investigation.
- The log records the candidate user, decision result, blocking reason, primary score, runner-up score, and supporting sample count.
- Deduplicates repeated rejection decisions so one sustained false reject does not flood the admin log.
- Adds a Face ID-style enrollment pose ribbon over the camera preview to show center/left/right collection progress while registering a user.
- Keeps the public operation screen privacy-preserving; detailed scores are shown only in the screen-lock-protected admin view.

## Android 0.3.16

- Switches Android recognition to InsightFace ArcFace `w600k_r50` through ONNX Runtime Android, with OpenCV SFace retained for alignment/fallback.
- Tightens Android face authentication for door-terminal use after a real-device false-accept report.
- Raises the default recognition score threshold, widens the ambiguity margin, and requires supporting enrollment samples for newly registered users.
- Adds final enrollment-template cohesion checks so mixed/unstable sample sets are rejected before they can become stored biometric templates.
- Stores the full enrollment sample set for new users. Legacy single-centroid users remain visible for deletion, but must be re-registered because old embeddings are not compared against the new ArcFace space.
- Removes desktop Windows/Linux app artifacts from the current repository tree so the project is Android-focused.
- Adds unit coverage for weak-score, insufficient-sample-support, strong-candidate, ambiguous-candidate, and contaminated-template decisions.

## Android 0.3.15

- Adds an Android camera-analysis watchdog for real-device enrollment/auth stability.
- If analysis frames stop arriving while the app expects face analysis, the app automatically rebinds the CameraX pipeline for camera-feed stalls and then fails visibly after repeated unsuccessful recovery attempts.
- If the analyzer thread itself remains stuck inside a frame for an extended window, the app fails visibly and asks for a full restart instead of racing native face-engine cleanup.
- Adds unit coverage for the watchdog stall/cooldown decision, repeated camera-feed stalls, and sustained analyzer hangs.

## Android 0.3.14

- Adds an Android admin relay connection test for real door-terminal setup.
- The test is fail-closed and never posts to the configured open URL. It sends an HTTPS `GET` only to `.well-known/ffacio-door-relay` under the same relay parent path and requires a successful response.
- Adds unit coverage that the health-check URL strips the open endpoint/query, preserves path-based relay routing, rejects plain HTTP, and disables repeated/incomplete test requests.
- Keeps camera preview responsive during enrollment storage by pausing heavy frame analysis while local template storage is busy.
- Updates the ESP32 relay reference with Android-compatible non-opening health-check endpoints; Android deployments still need HTTPS relay access.

## Android 0.3.13

- Hardens door relay execution with a process-wide single-flight gate and cooldown so an accepted face cannot trigger duplicate relay POSTs across rapid UI recomposition or Activity recreation.
- Removes recognized user names from outbound Android relay JSON. Identity details stay local to the protected approval log; relay receivers get only the accepted event source.
- Adds unit coverage for the relay gate and privacy-preserving relay payload.
- Signing note: production signing still requires `FFACIO_ANDROID_KEYSTORE_*` environment variables. Builds made with `-AllowGeneratedSigningKey` are for sideload testing and are not production upgrade-compatible.

## Android 0.3.12

- Adds operation-view immersive terminal mode with Android system bars hidden by default and transient swipe reveal.
- Restores normal system UI before Android screen-lock admin prompts, when entering the admin view, and when touch exploration accessibility is active.
- Extends Android emulator release smoke verification to confirm the operation immersive request is emitted during launch.

## Android 0.3.11

- Keeps the Android app display awake while active so a mounted door terminal does not sleep during operation.
- Keeps `FLAG_SECURE` and admin session timeout behavior in place while the display stays awake.
- Reduces public operation-screen biometric context exposure: approved user names and approval timestamps are no longer shown on the operation screen, while the detailed approval log remains in the screen-lock-protected admin view.
- Adds Android production signing setup guidance and manifest fields that distinguish a persistent production keystore from a disposable local sideload key.
