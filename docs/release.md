# FFacio Android Release Notes

## Android 0.8.1-runtime-demo-parity-ios

- Replaces the CameraX YUV_420_888 plane conversion with the exact Fotoapparat 2.7.0 NV21 camera path used by the Runtime Demo.
- Uses the Demo front-camera EXIF orientation and mirror mapping, 1280×720 enrollment request, 640×480 balanced authentication request, and CenterCrop preview coordinates.
- Reproduces the Demo registration check order and square capture ROI; the only multi-face difference is the requested largest-face selection.
- Stores the highest-quality original NV21 frame during the 1200 ms stable interval, then reconverts, redetects, rechecks, and extracts the single final template once, like the Demo capture flow.
- Keeps authentication at one stable frame as requested while matching Demo liveness, quality, minimum-area, similarity, and uncertain-gap behavior.
- Adds explicit frame/template ownership and zeroing across replacement, cancellation, stale result, persistence failure, and screen shutdown paths.
- Refreshes the camera and operation UI with a Face ID-style tracking ring, glass status card, progress treatment, capsules, and iOS-like status colors.
- Bumps the biometric policy and user schema to 7 so every previous face enrollment is discarded and re-enrolled through the corrected camera path.

## Android 0.6.2-runtime-demo-aligned-final

- Removes the active head-turn liveness challenge, five-pose enrollment, pose hold gates, sample-cohesion checks, representative-sample selection, and supporting-sample authentication.
- Uses the Runtime Demo registration policy with a single 1200 ms stable capture and stores the highest-quality Runtime template from that interval.
- Selects only the largest detected face when multiple people are visible.
- Aligns Runtime Demo defaults for liveness, quality, pose, luminance, eye, occlusion, mouth, size, thresholds, frame interval, and result hold; authentication stabilization is intentionally one frame.
- Requests eye and mouth attributes for both authentication and enrollment like the Runtime Demo, while applying those pass/fail gates only during enrollment.
- Introduces user-store policy/schema version 5. On first launch, all previous enrolled users and encrypted user templates are deleted; non-biometric relay settings are preserved.

## Android 0.6.0-runtime

- Adds a Runtime diagnostics card to the admin advanced settings: Runtime package install state and version, Binder connection phase, initialization result, disconnect reason, automatic reconnect attempt count, and a manual reconnect button.
- Measures real per-frame Runtime call timings (YUV conversion, combined detect+attributes, template extraction) and shows them in the diagnostics card. Detection and requested attributes are one AIDL call, so attribute-only time is never fabricated.
- Exposes the Runtime liveness check level (passed verbatim to the engine contract) as an admin setting instead of a hardcoded `0`.
- Adds a face-occlusion check toggle with Runtime Demo semantics: disabling it removes `check_face_occlusion` from the actual Runtime detect request and from authentication/enrollment gating, instead of just hiding the result.
- Centralizes detect-option construction in a tested `runtimeDetectionOptions()` helper and clamps unknown liveness levels to the documented range.
- Fixes a stale unit test that still asserted the pre-0.5.x match threshold.

## Android 0.5.1-runtime

- Moves all Runtime template comparisons off the Compose/UI thread with one in-flight decision, an 8-second timeout, stale-result invalidation, and a 10-second stall fallback that invalidates the decision token and reconnects the Runtime Binder.
- Drops camera analysis before Runtime work while a decision is pending and prevents the camera watchdog from treating comparison latency as a feed stall.
- Fixes YUV plane reads for non-zero buffer positions and validates chroma-aligned crops, strides, template sizes, NV21 length, and native orientation codes.
- Loads encrypted users once at startup instead of reloading them on every Runtime connection-state transition.
- Upgrades encrypted user records to schema v3 and fails closed on malformed or mixed-size Runtime samples instead of silently filtering them.
- Wipes enrollment/deleted template arrays, clears transient NV21 buffers, and hardens Runtime-client temporary-file cleanup.
- Extends matching diagnostics with successful/failed comparison counts and separates total Runtime comparison failure from an ordinary non-match.
- Makes APK static verification scan every `classes*.dex` entry and adds a cross-platform source audit script.
- Rechecks admin-session expiry at action execution time, requires a valid HTTPS relay URL and token, disables cleartext traffic, and releases a failed Runtime binding so reconnection can proceed.

## Android 0.5.0-runtime

- Replaces the in-app OpenCV/ONNX/ArcFace/SFace/MiniFASNet engine with the separately installed FFacio Runtime Binder service.
- Uses Runtime YUV conversion, full face attributes, 68-point landmarks, liveness, template extraction, and template comparison.
- Stores Runtime byte templates and rejects legacy embedding records until users are re-enrolled.
- Removes bundled model assets and model-fetch/build verification scripts.
- Adds Runtime connection recovery, initialization-specific errors, and same-certificate release verification for the app/Runtime pair.
- Keeps FFacio enrollment, authentication, Head Admin, encrypted storage, relay, camera watchdog, and privacy behavior.

## Android 0.4.5

- Removes the admin guidance card from the real-use operation screen.
- Updates the operation-screen subtitle to `Door Access System By sampple-korea`.

## Android 0.4.4

- Makes the face guide ring track the detected face position and size instead of staying fixed in the center.
- Redesigns the guide ring to cover less of the face while keeping turn arrows visible as edge badges.
- Adds smoother status colors: blue while checking, green on approval, red on rejection or failed face checks.
- Updates left/right liveness prompts to ask for a slight head turn.
- Adds a cancel button on the Head Admin face-auth screen.

## Android 0.4.3

- Treats entry into the admin screen as the Head Admin-approved admin session.
- Runs normal admin-screen actions immediately after entry, including enrollment, user deletion, reset, relay changes, relay test, and passive liveness mode changes.
- Keeps only Head Admin assignment and removal behind Android screen-lock verification inside the admin screen.
- Makes the Head Admin face-auth screen visually distinct with a dedicated `Head Admin 인증 중` state and badge over the camera.

## Android 0.4.2

- Adds the Head Admin permission model for real door-terminal administration.
- Admin screen entry, new user enrollment, user deletion, relay/token changes, and other normal admin actions are approved with the Head Admin user's face once a Head Admin is configured.
- Multiple Head Admin users can be configured; any compatible Head Admin face can approve normal admin actions.
- Head Admin assignment and removal remain protected by Android screen-lock verification.
- Adds Head Admin badges and set/change/clear controls to the protected user-management list.
- Restricts Head Admin face approval to compatible Head Admin templates only, so a similar non-admin user cannot accidentally satisfy an admin prompt.
- Gates relay disable, relay health test, and optional passive liveness mode changes through the same Head Admin face approval path.
- Shows operation-screen guidance when registered users exist but no Head Admin is configured yet.
- Keeps initial setup and recovery paths on Android screen-lock when no compatible Head Admin exists.

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
