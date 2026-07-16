package com.ffacio.mobile

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import android.util.Size as AndroidSize
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.common.util.concurrent.ListenableFuture
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kbyai.facesdk.FaceBox
import com.kbyai.facesdk.FaceDetectionParam
import com.kbyai.facesdk.FaceSDK
import io.ffacio.sdk.FFacioRuntimeClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private const val PREFS = "ffacio_store"
private const val USERS_KEY = "users"
private const val DOOR_URL_KEY = "door_url"
private const val DOOR_TOKEN_KEY = "door_token"
private const val DOOR_ENABLED_KEY = "door_enabled"
private const val DOOR_RELAY_HEALTH_PATH = "/.well-known/ffacio-door-relay"
private const val PASSIVE_LIVENESS_ENABLED_KEY = "passive_liveness_enabled"
private const val RUNTIME_LIVENESS_LEVEL_KEY = "runtime_liveness_level"
private const val OCCLUSION_CHECK_ENABLED_KEY = "occlusion_check_enabled"
internal const val FACE_ENGINE_ID = "ffacio.runtime.template.v1"
private const val MIN_RUNTIME_TEMPLATE_BYTES = 16
private const val MAX_RUNTIME_TEMPLATE_BYTES = 64 * 1024
private const val KEYSTORE_ALIAS = "ffacio_mobile_store_key_v3"
private const val LEGACY_KEYSTORE_ALIAS = "ffacio_mobile_store_key_v2"
private const val OLDER_KEYSTORE_ALIAS = "ffacio_mobile_store_key"
private const val SECURE_SUFFIX = "_enc_v3"
private const val LEGACY_SECURE_SUFFIX = "_enc_v2"
private const val OLDER_SECURE_SUFFIX = "_enc"
private const val STORE_PREFLIGHT_KEY = "__store_preflight"
private const val MATCH_THRESHOLD = 0.80
private const val MATCH_SAMPLE_THRESHOLD = 0.75
private const val MATCH_MARGIN = 0.03
private const val MATCH_MIN_SUPPORTING_SAMPLES = 2
private const val ENROLL_SAMPLES = 5
private const val ENROLL_POSE_HOLD_MS = 420L
private const val ENROLL_REPEAT_THRESHOLD = 0.995
private const val ENROLL_DUPLICATE_THRESHOLD = 0.88
private const val ENROLL_MIN_DISTINCT_POSES = 3
private const val ENROLL_TEMPLATE_MIN_SAMPLE_SCORE = 0.62
private const val ENROLL_TEMPLATE_AVG_SAMPLE_SCORE = 0.75
private const val ENROLL_TEMPLATE_MIN_PAIR_SCORE = 0.62
private const val ANALYSIS_INTERVAL_MS = 180L
private const val CAMERA_ANALYSIS_STALL_MS = 6500L
private const val CAMERA_WATCHDOG_RETRY_COOLDOWN_MS = 6000L
private const val CAMERA_WATCHDOG_MAX_REBIND_ATTEMPTS = 2
private const val CAMERA_ANALYZER_FATAL_STALL_MS = 20_000L
private const val RUNTIME_DECISION_TIMEOUT_MS = 8_000L
private const val RUNTIME_DECISION_STALL_RECOVERY_MS = 10_000L
private const val ANTISPOOF_THRESHOLD = 0.70f
private const val RUNTIME_QUALITY_THRESHOLD = 0.50f
private const val RUNTIME_EYE_CLOSED_THRESHOLD = 0.80f
private const val RUNTIME_OCCLUSION_THRESHOLD = 0.50f
private const val RUNTIME_MOUTH_OPEN_THRESHOLD = 0.50f
private const val RUNTIME_MAX_PITCH = 20.0f
private const val RUNTIME_MAX_ROLL = 20.0f
private const val RUNTIME_LANDMARK_VALUE_COUNT = 136
private const val AUTH_RESULT_HOLD_MS = 3500L
private const val APPROVAL_LOG_LIMIT = 8
private const val AUTH_DECISION_LOG_LIMIT = 8
private const val AUTH_DECISION_LOG_DEDUPE_MS = 2500L
private const val ADMIN_SESSION_TIMEOUT_MS = 120_000L
private const val ADMIN_FACE_AUTH_TIMEOUT_MS = 30_000L
private const val ENROLLMENT_IDLE_TIMEOUT_MS = 60_000L
private const val USER_STORE_SCHEMA_VERSION = 3

class MainActivity : ComponentActivity() {
    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private val doorExecutor = Executors.newSingleThreadExecutor()
    private val processing = AtomicBoolean(false)
    private val firstAnalyzedFrameLogged = AtomicBoolean(false)
    private val lastAnalysisAt = AtomicLong(0L)
    private val active = AtomicBoolean(true)
    private lateinit var prefs: SharedPreferences
    private var modelLoadState by mutableStateOf<ModelLoadState>(ModelLoadState.Loading)
    @Volatile private var engine: MobileFaceEngine? = null
    private var runtimeStateListener: FaceSDK.ConnectionStateListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        setContent {
            FFacioTheme {
                FFacioApp(
                    modelLoadState = modelLoadState,
                    prefs = prefs,
                    engineProvider = { engine },
                    retryRuntime = { connectRuntime(forceReconnect = true) },
                    analyzerExecutor = analyzerExecutor,
                    doorExecutor = doorExecutor,
                    processing = processing,
                    firstAnalyzedFrameLogged = firstAnalyzedFrameLogged,
                    lastAnalysisAt = lastAnalysisAt,
                    active = active
                )
            }
        }

        engine = MobileFaceEngine()
        val listener = FaceSDK.ConnectionStateListener { snapshot ->
            ContextCompat.getMainExecutor(this).execute {
                if (!active.get()) return@execute
                modelLoadState = when {
                    snapshot.ready -> ModelLoadState.Ready
                    snapshot.connecting -> ModelLoadState.Loading
                    snapshot.error != null -> ModelLoadState.Failed(snapshot.error)
                    snapshot.initializationResult != Int.MIN_VALUE && snapshot.initializationResult != FaceSDK.SDK_SUCCESS ->
                        ModelLoadState.Failed(IllegalStateException(runtimeInitializationMessage(snapshot.initializationResult)))
                    snapshot.connected -> ModelLoadState.Loading
                    else -> ModelLoadState.Loading
                }
            }
        }
        runtimeStateListener = listener
        FaceSDK.addConnectionStateListener(listener, true)
        connectRuntime(forceReconnect = false)
    }

    override fun onStart() {
        super.onStart()
        if (!FaceSDK.isReady() && !FaceSDK.isConnecting()) {
            connectRuntime(forceReconnect = false)
        }
    }

    private fun connectRuntime(forceReconnect: Boolean) {
        if (!active.get()) return
        val installed = runCatching {
            packageManager.getPackageInfo(FFacioRuntimeClient.RUNTIME_PACKAGE, 0)
        }.isSuccess
        if (!installed) {
            modelLoadState = ModelLoadState.Failed(IllegalStateException("FFacio Runtime 앱이 설치되어 있지 않습니다"))
            return
        }
        modelLoadState = ModelLoadState.Loading
        if (forceReconnect) {
            val started = runCatching { FaceSDK.reconnect(this) }.getOrElse { error ->
                modelLoadState = ModelLoadState.Failed(error)
                false
            }
            if (!started) {
                modelLoadState = ModelLoadState.Failed(IllegalStateException("FFacio Runtime 서비스에 연결할 수 없습니다"))
            }
            return
        }
        FaceSDK.initialize(this) { result ->
            ContextCompat.getMainExecutor(this).execute {
                if (!active.get()) return@execute
                modelLoadState = if (result == FaceSDK.SDK_SUCCESS) {
                    Log.i("FFacio", "FFacio Runtime ready")
                    ModelLoadState.Ready
                } else {
                    val error = IllegalStateException(runtimeInitializationMessage(result))
                    Log.e("FFacio", "FFacio Runtime initialization failed: $result", error)
                    ModelLoadState.Failed(error)
                }
            }
        }
    }

    override fun onDestroy() {
        active.set(false)
        runtimeStateListener?.let { FaceSDK.removeConnectionStateListener(it) }
        runtimeStateListener = null
        engine?.close()
        engine = null
        FaceSDK.disconnect()
        analyzerExecutor.shutdownNow()
        doorExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun runtimeInitializationMessage(code: Int): String = when (code) {
        FaceSDK.SDK_LICENSE_KEY_ERROR -> "FFacio Runtime 라이선스 키가 올바르지 않습니다"
        FaceSDK.SDK_LICENSE_APPID_ERROR -> "FFacio Runtime App ID가 이 앱과 맞지 않습니다"
        FaceSDK.SDK_LICENSE_EXPIRED -> "FFacio Runtime 라이선스가 만료되었습니다"
        FaceSDK.SDK_NO_ACTIVATED -> "FFacio Runtime이 활성화되지 않았습니다"
        FaceSDK.SDK_INIT_ERROR -> "FFacio Runtime 엔진 초기화에 실패했습니다"
        else -> "FFacio Runtime 초기화 오류($code)"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FFacioApp(
    modelLoadState: ModelLoadState,
    prefs: SharedPreferences,
    engineProvider: () -> MobileFaceEngine?,
    retryRuntime: () -> Unit,
    analyzerExecutor: ExecutorService,
    doorExecutor: ExecutorService,
    processing: AtomicBoolean,
    firstAnalyzedFrameLogged: AtomicBoolean,
    lastAnalysisAt: AtomicLong,
    active: AtomicBoolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appScope = rememberCoroutineScope()
    var cameraLifecycleActive by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val users = remember { mutableStateListOf<UserTemplate>() }
    val modelLoading = modelLoadState is ModelLoadState.Loading
    val modelError = (modelLoadState as? ModelLoadState.Failed)?.error
    var storeError by remember { mutableStateOf<Throwable?>(null) }
    var initialStoreLoaded by remember { mutableStateOf(false) }
    var storageBusy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("시스템 준비 중") }
    var detail by remember { mutableStateOf("FFacio Runtime 연결과 카메라를 확인하고 있습니다") }
    var mode by remember { mutableStateOf(AppMode.Auth) }
    var appScreen by remember { mutableStateOf(AppScreen.Operation) }
    var adminPromptInFlight by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var enrollmentName by remember { mutableStateOf("") }
    var doorUrl by remember { mutableStateOf(prefs.getString(DOOR_URL_KEY, "") ?: "") }
    var doorToken by remember { mutableStateOf("") }
    var doorConfigError by remember { mutableStateOf<Throwable?>(null) }
    var doorArmed by remember { mutableStateOf(prefs.getBoolean(DOOR_ENABLED_KEY, false)) }
    var doorTestInFlight by remember { mutableStateOf(false) }
    var doorTestRequestId by remember { mutableLongStateOf(0L) }
    var passiveLivenessEnabled by remember { mutableStateOf(prefs.getBoolean(PASSIVE_LIVENESS_ENABLED_KEY, false)) }
    var pendingPassiveLivenessEnabled by remember { mutableStateOf<Boolean?>(null) }
    var runtimeLivenessLevel by remember { mutableIntStateOf(sanitizeRuntimeLivenessLevel(prefs.getInt(RUNTIME_LIVENESS_LEVEL_KEY, 0))) }
    var pendingRuntimeLivenessLevel by remember { mutableStateOf<Int?>(null) }
    var occlusionCheckEnabled by remember { mutableStateOf(prefs.getBoolean(OCCLUSION_CHECK_ENABLED_KEY, true)) }
    var pendingOcclusionCheckEnabled by remember { mutableStateOf<Boolean?>(null) }
    var runtimeSnapshot by remember { mutableStateOf(FaceSDK.getConnectionSnapshot()) }
    var runtimePackageStatus by remember { mutableStateOf(queryRuntimePackageStatus(context)) }
    var lastRuntimeTimings by remember { mutableStateOf<RuntimeCallTimings?>(null) }
    var cameraAvailable by remember { mutableStateOf(true) }
    var noCameraHardware by remember { mutableStateOf(false) }
    var analyzerFatalStall by remember { mutableStateOf(false) }
    var cameraRetryNonce by remember { mutableIntStateOf(0) }
    var cameraAnalysisWatchStartedAt by remember { mutableLongStateOf(0L) }
    var lastCameraWatchdogRetryAt by remember { mutableLongStateOf(0L) }
    var cameraWatchdogRebindAttempts by remember { mutableIntStateOf(0) }
    var analyzerProcessingWatchStartedAt by remember { mutableLongStateOf(0L) }
    var touchExplorationEnabled by remember { mutableStateOf(isTouchExplorationEnabled(context)) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingDeleteUserIndex by remember { mutableIntStateOf(-1) }
    var pendingHeadAdminUserIndex by remember { mutableIntStateOf(-1) }
    var pendingAdminAction by remember { mutableStateOf<AdminAction?>(null) }
    var adminFaceAuthExpiresAt by remember { mutableLongStateOf(0L) }
    val enrollSamples = remember { mutableStateListOf<ByteArray>() }
    val enrollPoses = remember { mutableStateListOf<Int>() }
    val approvalLogs = remember { mutableStateListOf<ApprovalLogEntry>() }
    val authDecisionLogs = remember { mutableStateListOf<AuthDecisionLogEntry>() }
    var lastAuthDecisionLogKey by remember { mutableStateOf("") }
    var lastAuthDecisionLogAt by remember { mutableLongStateOf(0L) }
    var accessFeedback by remember { mutableStateOf<AccessFeedback?>(null) }
    val liveness = remember { LivenessChallenge() }
    var guideState by remember { mutableStateOf(FaceGuideState.Searching) }
    var faceBounds by remember { mutableStateOf<FaceBounds?>(null) }
    val enrollmentHold = remember { EnrollmentPoseHold() }
    var enrollmentHoldProgress by remember { mutableStateOf(0.0f) }
    var liveCandidate by remember { mutableIntStateOf(-1) }
    var stableUser by remember { mutableIntStateOf(-1) }
    var stableCount by remember { mutableIntStateOf(0) }
    var authResultHoldUntil by remember { mutableLongStateOf(0L) }
    val runtimeDecisionInFlight = remember { AtomicBoolean(false) }
    val runtimeDecisionGeneration = remember { AtomicLong(0L) }
    val runtimeDecisionToken = remember { AtomicLong(0L) }
    val runtimeDecisionStartedAt = remember { AtomicLong(0L) }
    var adminSessionExpiresAt by remember { mutableLongStateOf(0L) }
    var enrollmentExpiresAt by remember { mutableLongStateOf(0L) }
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val currentAppScreen by rememberUpdatedState(appScreen)
    val currentAdminPromptInFlight by rememberUpdatedState(adminPromptInFlight)

    fun invalidateDoorRelayTest() {
        doorTestInFlight = false
        doorTestRequestId += 1L
    }

    DisposableEffect(Unit) {
        val snapshotListener = FaceSDK.ConnectionStateListener { snapshot ->
            ContextCompat.getMainExecutor(context).execute {
                if (!active.get()) return@execute
                runtimeSnapshot = snapshot
                runtimePackageStatus = queryRuntimePackageStatus(context)
            }
        }
        FaceSDK.addConnectionStateListener(snapshotListener, true)
        onDispose { FaceSDK.removeConnectionStateListener(snapshotListener) }
    }

    DisposableEffect(appScreen, adminPromptInFlight, touchExplorationEnabled) {
        val activity = context as? Activity
        val window = activity?.window
        if (window != null) {
            applyDoorTerminalSystemUi(
                window = window,
                immersive = shouldUseDoorTerminalImmersive(
                    isOperationScreen = appScreen == AppScreen.Operation,
                    adminPromptInFlight = adminPromptInFlight,
                    touchExplorationEnabled = touchExplorationEnabled
                )
            )
        }
        onDispose {
            if (window != null) {
                applyDoorTerminalSystemUi(window = window, immersive = false)
            }
        }
    }
    LaunchedEffect(authResultHoldUntil) {
        val remaining = authResultHoldUntil - System.currentTimeMillis()
        if (remaining > 0L) {
            delay(remaining)
            if (System.currentTimeMillis() >= authResultHoldUntil) {
                authResultHoldUntil = 0L
                accessFeedback = null
                if (mode == AppMode.Auth) {
                    guideState = FaceGuideState.Searching
                }
            }
        }
    }
    LaunchedEffect(appScreen, mode, storageBusy, adminPromptInFlight, adminSessionExpiresAt) {
        if (appScreen == AppScreen.Admin && mode != AppMode.Enroll && !storageBusy && !adminPromptInFlight && adminSessionExpiresAt > 0L) {
            val remaining = adminSessionExpiresAt - SystemClock.elapsedRealtime()
            if (remaining > 0L) delay(remaining)
            if (shouldAutoLockAdminScreen(
                    nowMillis = SystemClock.elapsedRealtime(),
                    expiresAtMillis = adminSessionExpiresAt,
                    isAdminScreen = appScreen == AppScreen.Admin,
                    isEnrollmentMode = mode == AppMode.Enroll,
                    storageBusy = storageBusy,
                    adminPromptInFlight = adminPromptInFlight
                )
            ) {
                val reset = applyAdminAutoLockReset(AdminAutoLockState(
                    enrollmentName = enrollmentName,
                    enrollSampleCount = enrollSamples.size,
                    enrollPoseCount = enrollPoses.size,
                    pendingDeleteUserIndex = pendingDeleteUserIndex,
                    liveCandidate = liveCandidate,
                    stableUser = stableUser,
                    stableCount = stableCount
                ))
                invalidateDoorRelayTest()
                appScreen = AppScreen.Operation
                mode = AppMode.Auth
                adminSessionExpiresAt = 0L
                enrollmentExpiresAt = 0L
                confirmDelete = reset.confirmDelete
                pendingDeleteUserIndex = reset.pendingDeleteUserIndex
                enrollmentName = reset.enrollmentName
                enrollSamples.clearSecurely()
                enrollPoses.clear()
                authResultHoldUntil = 0L
                accessFeedback = null
                liveCandidate = reset.liveCandidate
                stableUser = reset.stableUser
                stableCount = reset.stableCount
                liveness.reset()
                status = "관리자 화면 잠금"
                detail = "보안을 위해 운영 화면으로 돌아왔습니다"
            }
        }
    }
    LaunchedEffect(appScreen, mode, storageBusy, adminPromptInFlight, enrollmentExpiresAt) {
        if (appScreen == AppScreen.Admin && mode == AppMode.Enroll && !storageBusy && !adminPromptInFlight && enrollmentExpiresAt > 0L) {
            val remaining = enrollmentExpiresAt - SystemClock.elapsedRealtime()
            if (remaining > 0L) delay(remaining)
            if (shouldAutoLockEnrollment(
                    nowMillis = SystemClock.elapsedRealtime(),
                    expiresAtMillis = enrollmentExpiresAt,
                    isAdminScreen = appScreen == AppScreen.Admin,
                    isEnrollmentMode = mode == AppMode.Enroll,
                    storageBusy = storageBusy,
                    adminPromptInFlight = adminPromptInFlight
                )
            ) {
                val reset = applyAdminAutoLockReset(AdminAutoLockState(
                    enrollmentName = enrollmentName,
                    enrollSampleCount = enrollSamples.size,
                    enrollPoseCount = enrollPoses.size,
                    pendingDeleteUserIndex = pendingDeleteUserIndex,
                    liveCandidate = liveCandidate,
                    stableUser = stableUser,
                    stableCount = stableCount
                ))
                invalidateDoorRelayTest()
                appScreen = AppScreen.Operation
                mode = AppMode.Auth
                adminSessionExpiresAt = 0L
                enrollmentExpiresAt = 0L
                confirmDelete = reset.confirmDelete
                pendingDeleteUserIndex = reset.pendingDeleteUserIndex
                enrollmentName = reset.enrollmentName
                enrollSamples.clearSecurely()
                enrollPoses.clear()
                authResultHoldUntil = 0L
                accessFeedback = null
                liveCandidate = reset.liveCandidate
                stableUser = reset.stableUser
                stableCount = reset.stableCount
                liveness.reset()
                status = "등록 세션 만료"
                detail = "보안을 위해 등록을 취소하고 운영 화면으로 돌아왔습니다"
            }
        }
    }
    LaunchedEffect(mode, adminFaceAuthExpiresAt, pendingAdminAction) {
        if (mode == AppMode.AdminAuth && pendingAdminAction != null && adminFaceAuthExpiresAt > 0L) {
            val remaining = adminFaceAuthExpiresAt - SystemClock.elapsedRealtime()
            if (remaining > 0L) delay(remaining)
            if (mode == AppMode.AdminAuth && pendingAdminAction != null && SystemClock.elapsedRealtime() >= adminFaceAuthExpiresAt) {
                pendingAdminAction = null
                adminFaceAuthExpiresAt = 0L
                adminPromptInFlight = false
                invalidateDoorRelayTest()
                appScreen = AppScreen.Operation
                mode = AppMode.Auth
                adminSessionExpiresAt = 0L
                enrollmentExpiresAt = 0L
                authResultHoldUntil = 0L
                liveCandidate = -1
                stableUser = -1
                stableCount = 0
                pendingDeleteUserIndex = -1
                pendingHeadAdminUserIndex = -1
                liveness.reset()
                status = "Head Admin 확인 시간이 초과되었습니다"
                detail = "관리 버튼을 다시 눌러 Head Admin 얼굴 인증을 시작해 주세요"
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCameraPermission = it
        if (!it) {
            status = "카메라 권한이 필요합니다"
            detail = "앱 설정에서 카메라 권한을 허용해 주세요"
        }
    }
    var testDoorRelayHealth: () -> Unit = {}

    fun completeAdminAction(action: AdminAction) {
        adminSessionExpiresAt = SystemClock.elapsedRealtime() + ADMIN_SESSION_TIMEOUT_MS
            when (action) {
                AdminAction.OpenAdmin -> {
                    appScreen = AppScreen.Admin
                    authResultHoldUntil = 0L
                    status = "관리자 설정"
                    detail = "등록 사용자, 문 열림 릴레이, 보안 옵션을 관리할 수 있습니다"
                }
                AdminAction.StartEnroll -> {
                    storageBusy = true
                    status = "로컬 저장소를 확인하는 중입니다"
                    detail = "얼굴 템플릿을 안전하게 저장할 수 있는지 먼저 확인합니다"
                    appScope.launch {
                        val checked = withContext(Dispatchers.IO) {
                            runCatching { preflightSecureStore(context, prefs) }
                        }
                        if (!active.get()) return@launch
                        storageBusy = false
                        checked.onSuccess {
                            storeError = null
                            appScreen = AppScreen.Admin
                            mode = AppMode.Enroll
                            enrollmentExpiresAt = SystemClock.elapsedRealtime() + ENROLLMENT_IDLE_TIMEOUT_MS
                            enrollmentName = name.trim()
                            enrollSamples.clearSecurely()
                            enrollPoses.clear()
                            authResultHoldUntil = 0L
                            guideState = FaceGuideState.Center
                            liveCandidate = -1
                            stableUser = -1
                            stableCount = 0
                            liveness.reset()
                            status = "얼굴을 중앙에 맞춰주세요"
                            detail = "FFacio가 안정적인 얼굴 템플릿을 수집하고 있습니다"
                        }.onFailure {
                            storeError = it
                            liveCandidate = -1
                            stableUser = -1
                            stableCount = 0
                            liveness.reset()
                            status = "로컬 생체 저장소를 사용할 수 없습니다"
                            detail = "얼굴 등록 전에 암호화 저장소 확인에 실패했습니다"
                        }
                    }
                }
                AdminAction.DeleteUser -> {
                    val deleteIndex = pendingDeleteUserIndex
                    pendingDeleteUserIndex = -1
                    if (deleteIndex !in users.indices) {
                        status = "삭제할 사용자를 찾을 수 없습니다"
                        detail = "등록 사용자 목록을 다시 확인해 주세요"
                        return
                    }
                    val deleteName = users[deleteIndex].name
                    val nextUsers = removeRegisteredUserAt(users.toList(), deleteIndex)
                        ?: run {
                            status = "삭제할 사용자를 찾을 수 없습니다"
                            detail = "등록 사용자 목록을 다시 확인해 주세요"
                            return
                        }
                    storageBusy = true
                    status = "등록 사용자 삭제 중"
                    detail = "$deleteName 템플릿을 로컬 저장소에서 제거하고 있습니다"
                    appScope.launch {
                        val deleted = withContext(Dispatchers.IO) {
                            runCatching { saveUsers(context, prefs, nextUsers) }
                        }
                        if (!active.get()) return@launch
                        storageBusy = false
                        deleted.onFailure {
                            storeError = it
                            status = "등록 사용자를 삭제할 수 없습니다"
                            detail = "암호화된 얼굴 템플릿 저장소 업데이트에 실패했습니다"
                        }.onSuccess {
                            storeError = null
                            appScreen = AppScreen.Admin
                            users.getOrNull(deleteIndex)?.wipe()
                            users.replaceWith(nextUsers)
                            approvalLogs.removeAll { it.userName == deleteName }
                            authDecisionLogs.removeAll { it.userName == deleteName }
                            liveCandidate = -1
                            stableUser = -1
                            stableCount = 0
                            liveness.reset()
                            status = "등록 사용자 삭제 완료"
                            detail = "$deleteName 사용자를 삭제했습니다"
                        }
                    }
                }
                AdminAction.DeleteUsers -> {
                    storageBusy = true
                    confirmDelete = false
                    status = "로컬 템플릿을 삭제하는 중입니다"
                    detail = "암호화된 저장소를 업데이트하고 있습니다"
                    appScope.launch {
                        val deleted = withContext(Dispatchers.IO) {
                            runCatching { saveUsers(context, prefs, emptyList()) }
                        }
                        if (!active.get()) return@launch
                        storageBusy = false
                        deleted.onFailure {
                            storeError = it
                            status = "로컬 생체 저장소를 사용할 수 없습니다"
                            detail = "암호화된 얼굴 템플릿 저장에 실패해 인증을 차단했습니다"
                        }.onSuccess {
                            storeError = null
                            appScreen = AppScreen.Admin
                            mode = AppMode.Auth
                            enrollmentExpiresAt = 0L
                            users.forEach { it.wipe() }
                            users.clear()
                            approvalLogs.clear()
                            authDecisionLogs.clear()
                            lastAuthDecisionLogKey = ""
                            lastAuthDecisionLogAt = 0L
                            liveCandidate = -1
                            stableUser = -1
                            stableCount = 0
                            liveness.reset()
                            confirmDelete = false
                            status = "등록 사용자를 삭제했습니다"
                            detail = "새 사용자 등록을 시작할 수 있습니다"
                        }
                    }
                }
                AdminAction.SetHeadAdmin -> {
                    val targetIndex = pendingHeadAdminUserIndex
                    pendingHeadAdminUserIndex = -1
                    if (targetIndex !in users.indices) {
                        status = "Head Admin을 설정할 수 없습니다"
                        detail = "등록 사용자 목록을 다시 확인해 주세요"
                        return
                    }
                    if (!users[targetIndex].isCompatible()) {
                        status = "Head Admin을 설정할 수 없습니다"
                        detail = "이 사용자는 FFacio Runtime 템플릿과 호환되지 않습니다. 다시 등록해 주세요"
                        return
                    }
                    val targetName = users[targetIndex].name
                    val nextUsers = users.mapIndexed { index, user -> if (index == targetIndex) user.copy(isHeadAdmin = true) else user }
                    storageBusy = true
                    status = "Head Admin 설정 중"
                    detail = "$targetName 사용자에게 관리자 권한을 저장하고 있습니다"
                    appScope.launch {
                        val saved = withContext(Dispatchers.IO) { runCatching { saveUsers(context, prefs, nextUsers) } }
                        if (!active.get()) return@launch
                        storageBusy = false
                        saved.onSuccess {
                            storeError = null
                            appScreen = AppScreen.Admin
                            users.replaceWith(nextUsers)
                            status = "Head Admin 설정 완료"
                            detail = "$targetName 사용자의 얼굴로도 관리자 진입과 등록 작업을 승인할 수 있습니다"
                        }.onFailure {
                            storeError = it
                            status = "Head Admin을 저장할 수 없습니다"
                            detail = "암호화된 얼굴 템플릿 저장소 업데이트에 실패했습니다"
                        }
                    }
                }
                AdminAction.ClearHeadAdmin -> {
                    val targetIndex = pendingHeadAdminUserIndex
                    pendingHeadAdminUserIndex = -1
                    if (targetIndex !in users.indices || !users[targetIndex].isHeadAdmin) {
                        status = "해제할 Head Admin을 찾을 수 없습니다"
                        detail = "등록 사용자 목록을 다시 확인해 주세요"
                        return
                    }
                    val targetName = users[targetIndex].name
                    val nextUsers = users.mapIndexed { index, user -> if (index == targetIndex) user.copy(isHeadAdmin = false) else user }
                    storageBusy = true
                    status = "Head Admin 해제 중"
                    detail = "$targetName 사용자의 관리자 권한을 해제하고 있습니다"
                    appScope.launch {
                        val saved = withContext(Dispatchers.IO) { runCatching { saveUsers(context, prefs, nextUsers) } }
                        if (!active.get()) return@launch
                        storageBusy = false
                        saved.onSuccess {
                            storeError = null
                            appScreen = AppScreen.Admin
                            users.replaceWith(nextUsers)
                            status = "Head Admin 해제 완료"
                            detail = if (hasHeadAdmin(nextUsers)) {
                                "남은 Head Admin 얼굴로 계속 관리자 작업을 승인할 수 있습니다"
                            } else {
                                "새 Head Admin을 설정하기 전까지 초기 설정은 Android 화면잠금으로 진행합니다"
                            }
                        }.onFailure {
                            storeError = it
                            status = "Head Admin을 해제할 수 없습니다"
                            detail = "암호화된 얼굴 템플릿 저장소 업데이트에 실패했습니다"
                        }
                    }
                }
                AdminAction.UnlockStore -> {
                    storageBusy = true
                    status = "로컬 템플릿을 여는 중입니다"
                    detail = "Android Keystore 인증을 확인하고 있습니다"
                    appScope.launch {
                        val loaded = withContext(Dispatchers.IO) { loadUsers(context, prefs) }
                        if (!active.get()) return@launch
                        storageBusy = false
                        storeError = loaded.error
                        if (loaded.error == null) {
                            appScreen = AppScreen.Admin
                            users.replaceWith(loaded.users)
                            mode = AppMode.Auth
                            enrollmentExpiresAt = 0L
                            enrollmentName = ""
                            enrollSamples.clearSecurely()
                            enrollPoses.clear()
                            liveCandidate = -1
                            stableUser = -1
                            stableCount = 0
                            liveness.reset()
                            status = if (users.isEmpty()) "첫 사용자를 등록하세요" else "로컬 템플릿 잠금 해제 완료"
                            detail = if (users.isEmpty()) "기기에 저장된 얼굴 템플릿이 없습니다" else "등록 사용자 ${users.size}명"
                        } else {
                            status = "로컬 템플릿을 열 수 없습니다"
                            detail = "기기 인증 후에도 템플릿을 확인하지 못했습니다. 필요하면 초기화하세요"
                        }
                    }
                }
                AdminAction.ResetStore -> {
                    storageBusy = true
                    status = "로컬 템플릿 저장소를 초기화하는 중입니다"
                    detail = "암호화 키와 저장된 릴레이 토큰을 함께 폐기합니다"
                    appScope.launch {
                        val reset = withContext(Dispatchers.IO) {
                            runCatching {
                                val removed = prefs.edit()
                                    .remove(USERS_KEY)
                                    .remove("$USERS_KEY$SECURE_SUFFIX")
                                    .remove("$USERS_KEY$LEGACY_SECURE_SUFFIX")
                                    .remove("$USERS_KEY$OLDER_SECURE_SUFFIX")
                                    .remove(DOOR_URL_KEY)
                                    .remove(DOOR_TOKEN_KEY)
                                    .remove(DOOR_ENABLED_KEY)
                                    .remove("$DOOR_TOKEN_KEY$SECURE_SUFFIX")
                                    .remove("$DOOR_TOKEN_KEY$LEGACY_SECURE_SUFFIX")
                                    .remove("$DOOR_TOKEN_KEY$OLDER_SECURE_SUFFIX")
                                    .remove("$STORE_PREFLIGHT_KEY$SECURE_SUFFIX")
                                    .remove("$STORE_PREFLIGHT_KEY$LEGACY_SECURE_SUFFIX")
                                    .remove("$STORE_PREFLIGHT_KEY$OLDER_SECURE_SUFFIX")
                                    .commit()
                                if (!removed) error("Local template reset could not be saved")
                                deleteKeystoreAlias(KEYSTORE_ALIAS)
                                deleteKeystoreAlias(LEGACY_KEYSTORE_ALIAS)
                                deleteKeystoreAlias(OLDER_KEYSTORE_ALIAS)
                            }
                        }
                        if (!active.get()) return@launch
                        storageBusy = false
                        if (reset.isFailure) {
                            storeError = IllegalStateException("Local template reset could not be saved", reset.exceptionOrNull())
                            status = "로컬 템플릿 초기화 실패"
                            detail = "기기 저장소 상태를 확인한 뒤 다시 시도하세요"
                            return@launch
                        }
                        users.forEach { it.wipe() }
                        users.clear()
                        approvalLogs.clear()
                        authDecisionLogs.clear()
                        lastAuthDecisionLogKey = ""
                        lastAuthDecisionLogAt = 0L
                        liveCandidate = -1
                        stableUser = -1
                        stableCount = 0
                        liveness.reset()
                        confirmDelete = false
                        doorUrl = ""
                        doorToken = ""
                        doorConfigError = null
                        doorArmed = false
                        invalidateDoorRelayTest()
                        storeError = null
                        appScreen = AppScreen.Admin
                        status = "로컬 템플릿 저장소 초기화 완료"
                        detail = "기기 안의 얼굴 템플릿을 지웠습니다. 새 사용자를 등록하세요"
                    }
                }
                AdminAction.ArmDoor -> {
                    val relayUrl = doorUrl.trim()
                    val relayToken = doorToken.trim()
                    storageBusy = true
                    status = "릴레이 설정을 저장하는 중입니다"
                    detail = "토큰을 Android Keystore로 암호화하고 있습니다"
                    appScope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            runCatching {
                                if (relayUrl.isEmpty()) error("릴레이 URL이 필요합니다")
                                if (relayToken.isEmpty()) error("릴레이 토큰이 필요합니다")
                                if (!doorRelayConfigured(relayUrl, relayToken)) error("올바른 HTTPS 릴레이 URL과 토큰이 필요합니다")
                                securePutString(context, prefs, DOOR_TOKEN_KEY, relayToken)
                                if (!prefs.edit()
                                        .putString(DOOR_URL_KEY, relayUrl)
                                        .putBoolean(DOOR_ENABLED_KEY, true)
                                        .commit()
                                ) {
                                    error("릴레이 URL과 활성화 상태를 저장하지 못했습니다")
                                }
                            }
                        }
                        if (!active.get()) return@launch
                        storageBusy = false
                        if (saved.isSuccess) {
                            appScreen = AppScreen.Admin
                            doorConfigError = null
                            doorArmed = true
                            status = "문 열림 릴레이 활성화 완료"
                            detail = "얼굴 인증과 라이브니스 확인을 모두 통과한 뒤에만 요청합니다"
                        } else {
                            doorArmed = false
                            val rollbackSaved = prefs.edit().putBoolean(DOOR_ENABLED_KEY, false).commit()
                            doorConfigError = saved.exceptionOrNull()
                            status = "릴레이 설정을 저장할 수 없습니다"
                            detail = if (rollbackSaved) {
                                saved.exceptionOrNull()?.message ?: "암호화된 릴레이 토큰 저장에 실패했습니다"
                            } else {
                                "릴레이 저장에 실패했고 비활성화 상태 저장도 확인하지 못했습니다. 앱을 재시작해 설정 상태를 확인해 주세요"
                            }
                            liveCandidate = -1
                            stableUser = -1
                            stableCount = 0
                            liveness.reset()
                        }
                    }
                }
                AdminAction.DisarmDoor -> {
                    appScreen = AppScreen.Admin
                    if (prefs.edit().putBoolean(DOOR_ENABLED_KEY, false).commit()) {
                        doorArmed = false
                        invalidateDoorRelayTest()
                        doorConfigError = null
                        status = "문 열림 릴레이 비활성화 완료"
                        detail = "얼굴 인증은 계속 진행되지만 릴레이 요청은 보내지 않습니다"
                    } else {
                        doorConfigError = IllegalStateException("릴레이 비활성화 상태를 저장하지 못했습니다")
                        status = "릴레이를 끌 수 없습니다"
                        detail = "저장소 업데이트에 실패해 문 열림 설정을 유지했습니다"
                    }
                }
                AdminAction.UnlockDoor -> {
                    storageBusy = true
                    status = "릴레이 토큰을 여는 중입니다"
                    detail = "기기 인증으로 암호화된 토큰을 확인합니다"
                    appScope.launch {
                        val loaded = withContext(Dispatchers.IO) {
                            runCatching { secureGetString(context, prefs, DOOR_TOKEN_KEY, "", failClosed = true) }
                        }
                        if (!active.get()) return@launch
                        storageBusy = false
                        if (loaded.isSuccess) {
                            appScreen = AppScreen.Admin
                            doorToken = loaded.getOrDefault("")
                            doorArmed = prefs.getBoolean(DOOR_ENABLED_KEY, false) && doorToken.isNotBlank()
                            doorConfigError = null
                            status = "릴레이 토큰 잠금 해제 완료"
                            detail = if (doorArmed) "인증 성공 시 저장된 HTTPS 릴레이로 요청합니다" else "필요하면 문 열림 스위치를 다시 활성화하세요"
                        } else {
                            doorArmed = false
                            prefs.edit().putBoolean(DOOR_ENABLED_KEY, false).apply()
                            doorConfigError = loaded.exceptionOrNull()
                            status = "릴레이 토큰을 열 수 없습니다"
                            detail = "토큰을 다시 입력하고 기기 인증 후 활성화하세요"
                        }
                    }
                }
                AdminAction.TestDoorRelay -> {
                    appScreen = AppScreen.Admin
                    testDoorRelayHealth()
                }
                AdminAction.SetPassiveLiveness -> {
                    val nextEnabled = pendingPassiveLivenessEnabled
                    pendingPassiveLivenessEnabled = null
                    if (nextEnabled == null) {
                        status = "실제 얼굴 체크 설정을 바꿀 수 없습니다"
                        detail = "설정 값을 다시 선택해 주세요"
                        return
                    }
                    if (nextEnabled && engineProvider()?.hasPassiveLiveness() != true) {
                        val saved = prefs.edit().putBoolean(PASSIVE_LIVENESS_ENABLED_KEY, false).commit()
                        passiveLivenessEnabled = false
                        liveCandidate = -1
                        stableUser = -1
                        stableCount = 0
                        liveness.reset()
                        status = if (saved) "좌우 얼굴 돌리기 모드" else "실제 얼굴 체크 설정을 저장할 수 없습니다"
                        detail = if (saved) {
                            "Runtime 라이브니스 값을 사용할 수 없어 기본 챌린지로 계속합니다"
                        } else {
                            "Runtime 라이브니스 설정을 저장하지 못했습니다. 앱을 재시작해 상태를 확인해 주세요"
                        }
                        return
                    }
                    if (!prefs.edit().putBoolean(PASSIVE_LIVENESS_ENABLED_KEY, nextEnabled).commit()) {
                        status = "실제 얼굴 체크 설정을 저장할 수 없습니다"
                        detail = "저장소 업데이트에 실패했습니다. 다시 시도해 주세요"
                        return
                    }
                    passiveLivenessEnabled = nextEnabled
                    liveCandidate = -1
                    stableUser = -1
                    stableCount = 0
                    liveness.reset()
                    appScreen = AppScreen.Admin
                    status = if (nextEnabled) "Runtime 라이브니스 켜짐" else "좌우 얼굴 돌리기 모드"
                    detail = if (nextEnabled) "Runtime 라이브니스 검사와 좌우 얼굴 돌리기를 함께 사용합니다" else "기본 실제 얼굴 확인은 좌우 얼굴 돌리기 챌린지로 진행합니다"
                }
                AdminAction.SetRuntimeLivenessLevel -> {
                    val nextLevel = pendingRuntimeLivenessLevel
                    pendingRuntimeLivenessLevel = null
                    if (nextLevel == null) {
                        status = "라이브니스 레벨을 바꿀 수 없습니다"
                        detail = "설정 값을 다시 선택해 주세요"
                        return
                    }
                    val sanitized = sanitizeRuntimeLivenessLevel(nextLevel)
                    if (!prefs.edit().putInt(RUNTIME_LIVENESS_LEVEL_KEY, sanitized).commit()) {
                        status = "라이브니스 레벨을 저장할 수 없습니다"
                        detail = "저장소 업데이트에 실패했습니다. 다시 시도해 주세요"
                        return
                    }
                    runtimeLivenessLevel = sanitized
                    liveCandidate = -1
                    stableUser = -1
                    stableCount = 0
                    liveness.reset()
                    appScreen = AppScreen.Admin
                    status = "라이브니스 검사 레벨 $sanitized"
                    detail = "다음 카메라 분석부터 Runtime 검출 요청에 그대로 전달됩니다"
                }
                AdminAction.SetOcclusionCheck -> {
                    val nextEnabled = pendingOcclusionCheckEnabled
                    pendingOcclusionCheckEnabled = null
                    if (nextEnabled == null) {
                        status = "얼굴 가림 검사 설정을 바꿀 수 없습니다"
                        detail = "설정 값을 다시 선택해 주세요"
                        return
                    }
                    if (!prefs.edit().putBoolean(OCCLUSION_CHECK_ENABLED_KEY, nextEnabled).commit()) {
                        status = "얼굴 가림 검사 설정을 저장할 수 없습니다"
                        detail = "저장소 업데이트에 실패했습니다. 다시 시도해 주세요"
                        return
                    }
                    occlusionCheckEnabled = nextEnabled
                    liveCandidate = -1
                    stableUser = -1
                    stableCount = 0
                    liveness.reset()
                    appScreen = AppScreen.Admin
                    status = if (nextEnabled) "얼굴 가림 검사 켜짐" else "얼굴 가림 검사 꺼짐"
                    detail = if (nextEnabled) {
                        "Runtime 검출 요청에 가림 검사를 포함하고 인증·등록 통과 조건에 사용합니다"
                    } else {
                        "다음 요청부터 Runtime 검출 옵션에서 가림 검사를 제외하고 통과 조건에도 쓰지 않습니다"
                    }
                }
            }
    }

    fun cancelAdminAction(message: String = "Head Admin 확인이 취소되었습니다", detailMessage: String = "관리 작업에는 Head Admin 얼굴 인증이 필요합니다") {
        adminPromptInFlight = false
        pendingAdminAction = null
        adminFaceAuthExpiresAt = 0L
        invalidateDoorRelayTest()
        appScreen = AppScreen.Operation
        mode = AppMode.Auth
        adminSessionExpiresAt = 0L
        enrollmentExpiresAt = 0L
        authResultHoldUntil = 0L
        liveCandidate = -1
        stableUser = -1
        stableCount = 0
        pendingDeleteUserIndex = -1
        pendingHeadAdminUserIndex = -1
        pendingPassiveLivenessEnabled = null
        pendingRuntimeLivenessLevel = null
        pendingOcclusionCheckEnabled = null
        liveness.reset()
        status = message
        detail = detailMessage
    }

    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val action = pendingAdminAction
        pendingAdminAction = null
        adminPromptInFlight = false
        if (it.resultCode == Activity.RESULT_OK && action != null) {
            completeAdminAction(action)
        } else {
            cancelAdminAction(
                message = "기기 인증이 취소되었습니다",
                detailMessage = "Head Admin 등록/해제에는 Android 화면잠금 인증이 필요합니다"
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameraLifecycleActive = true
                val resumedTouchExplorationEnabled = isTouchExplorationEnabled(context)
                touchExplorationEnabled = resumedTouchExplorationEnabled
                (context as? Activity)?.window?.let { window ->
                    applyDoorTerminalSystemUi(
                        window = window,
                        immersive = shouldUseDoorTerminalImmersive(
                            isOperationScreen = currentAppScreen == AppScreen.Operation,
                            adminPromptInFlight = currentAdminPromptInFlight,
                            touchExplorationEnabled = resumedTouchExplorationEnabled
                        )
                    )
                }
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (granted != hasCameraPermission) {
                    hasCameraPermission = granted
                    if (granted) {
                        if (analyzerFatalStall) {
                            cameraAvailable = false
                            status = "카메라 분석이 멈췄습니다"
                            detail = "Runtime 앱 설치·서명·연결 상태를 확인한 뒤 다시 시도해 주세요"
                        } else {
                            cameraAvailable = true
                            cameraRetryNonce += 1
                            status = if (users.isEmpty()) "첫 사용자를 등록하세요" else "인증 준비 완료"
                            detail = if (users.isEmpty()) "카메라 권한이 허용되었습니다. 얼굴 등록을 시작하세요" else "카메라를 바라봐 주세요"
                        }
                    } else {
                        liveCandidate = -1
                        stableUser = -1
                        stableCount = 0
                        liveness.reset()
                        status = "카메라 권한이 필요합니다"
                        detail = "앱 설정에서 카메라 권한을 허용해 주세요"
                    }
                }
            } else if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                cameraLifecycleActive = false
                cameraAnalysisWatchStartedAt = 0L
                analyzerProcessingWatchStartedAt = 0L
                cameraWatchdogRebindAttempts = 0
                if (shouldReturnToOperationOnLifecyclePause(currentAdminPromptInFlight)) {
                    invalidateDoorRelayTest()
                    appScreen = AppScreen.Operation
                    mode = AppMode.Auth
                    pendingAdminAction = null
                    pendingHeadAdminUserIndex = -1
                    pendingDeleteUserIndex = -1
                    pendingPassiveLivenessEnabled = null
                    adminFaceAuthExpiresAt = 0L
                    adminSessionExpiresAt = 0L
                    enrollmentExpiresAt = 0L
                    enrollmentName = ""
                    enrollSamples.clearSecurely()
                    enrollPoses.clear()
                    authResultHoldUntil = 0L
                }
                liveCandidate = -1
                stableUser = -1
                stableCount = 0
                liveness.reset()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { loadUsers(context, prefs) }
        val tokenLoad = withContext(Dispatchers.IO) {
            runCatching { secureGetString(context, prefs, DOOR_TOKEN_KEY, "", failClosed = true) }
        }
        tokenLoad.onSuccess {
            doorConfigError = null
            doorToken = it
            doorArmed = prefs.getBoolean(DOOR_ENABLED_KEY, false) && it.isNotBlank()
        }.onFailure {
            doorConfigError = it
            doorArmed = false
            prefs.edit().putBoolean(DOOR_ENABLED_KEY, false).apply()
        }
        storeError = loaded.error
        users.replaceWith(loaded.users)
        initialStoreLoaded = true
    }

    LaunchedEffect(modelLoadState, initialStoreLoaded, storeError) {
        runtimeDecisionGeneration.incrementAndGet()
        if (!initialStoreLoaded) {
            status = "로컬 저장소를 확인하고 있습니다"
            detail = "암호화된 사용자와 릴레이 설정을 불러오는 중입니다"
            return@LaunchedEffect
        }
        if (storeError != null) {
            status = "로컬 생체 저장소가 잠겨 있습니다"
            detail = "기기 인증으로 다시 열어 보거나, 필요할 때만 템플릿을 초기화하세요"
            return@LaunchedEffect
        }
        if (modelLoading) {
            status = "FFacio Runtime에 연결하고 있습니다"
            detail = "별도 Runtime 앱의 얼굴 엔진과 Binder 세션을 초기화하는 중입니다"
            return@LaunchedEffect
        }
        when {
            modelError != null -> {
                status = "FFacio Runtime을 사용할 수 없습니다"
                detail = modelError.message ?: "Runtime 설치·서명·서비스 상태를 확인해 주세요"
            }
            users.isEmpty() -> {
                status = "첫 사용자를 등록하세요"
                detail = "원본 얼굴 이미지는 저장하지 않고 템플릿만 이 기기에 보관합니다"
            }
            else -> {
                status = "인증 준비 완료"
                detail = "등록 사용자 ${users.size}명"
            }
        }
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    testDoorRelayHealth = {
        if (canTestDoorRelayConfig(doorUrl, doorToken, doorTestInFlight, canMutate = !storageBusy)) {
            val relayUrl = doorUrl.trim()
            val relayToken = doorToken.trim()
            val requestId = doorTestRequestId + 1L
            doorTestRequestId = requestId
            doorTestInFlight = true
            status = "릴레이 연결을 확인하는 중입니다"
            detail = "이 테스트는 문을 열지 않습니다"
            appScope.launch {
                val checked = withContext(Dispatchers.IO) {
                    runCatching {
                        if (relayUrl.isEmpty()) error("릴레이 URL이 필요합니다")
                        if (relayToken.isEmpty()) error("릴레이 토큰이 필요합니다")
                        val healthUrl = doorRelayHealthCheckUrl(relayUrl)
                        val result = checkDoorRelayHealthUrl(healthUrl, relayToken)
                        if (!result.accepted) error(result.message)
                    }
                }
                if (!active.get() || requestId != doorTestRequestId || appScreen != AppScreen.Admin) return@launch
                doorTestInFlight = false
                checked.onSuccess {
                    status = "릴레이 연결 정상"
                    detail = "릴레이가 안전 테스트 요청을 수락했습니다. 실제 문 열림은 얼굴 인증 후에만 실행됩니다"
                }.onFailure {
                    status = "릴레이 연결 테스트 실패"
                    detail = it.message ?: "릴레이 주소, 토큰, 장치 상태를 확인하세요"
                }
            }
        }
    }

    fun resetTransient(invalidatePendingDecision: Boolean = true) {
        if (invalidatePendingDecision) runtimeDecisionGeneration.incrementAndGet()
        liveCandidate = -1
        stableUser = -1
        stableCount = 0
        liveness.reset()
        enrollmentHold.reset()
        enrollmentHoldProgress = 0.0f
    }

    fun <T> runRuntimeDecision(
        computation: () -> T,
        discardResult: (T) -> Unit = {},
        applyResult: (Result<T>) -> Unit
    ): Boolean {
        if (!runtimeDecisionInFlight.compareAndSet(false, true)) return false
        val generation = runtimeDecisionGeneration.get()
        val token = runtimeDecisionToken.incrementAndGet()
        runtimeDecisionStartedAt.set(SystemClock.elapsedRealtime())
        appScope.launch {
            val result: Result<T>
            try {
                result = withContext(Dispatchers.IO) {
                    try {
                        Result.success(withTimeout(RUNTIME_DECISION_TIMEOUT_MS) { computation() })
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }
                }
            } finally {
                if (runtimeDecisionToken.get() == token) {
                    runtimeDecisionStartedAt.set(0L)
                    runtimeDecisionInFlight.set(false)
                }
            }
            if (!active.get() || generation != runtimeDecisionGeneration.get() || token != runtimeDecisionToken.get()) {
                result.getOrNull()?.let(discardResult)
                return@launch
            }
            applyResult(result)
        }
        return true
    }

    fun clearAuthResultHold() {
        authResultHoldUntil = 0L
        accessFeedback = null
    }

    fun recordApproval(userName: String, result: String) {
        addApprovalLog(approvalLogs, ApprovalLogEntry(formatClock(System.currentTimeMillis()), userName, result))
    }

    fun recordAuthDecision(userName: String, result: String, reason: String, match: Match?) {
        val now = System.currentTimeMillis()
        val entry = AuthDecisionLogEntry(
            time = formatClock(now),
            userName = userName,
            result = result,
            reason = reason,
            score = match?.score ?: -1.0,
            secondScore = match?.secondScore ?: -1.0,
            supportCount = match?.supportCount ?: 0
        )
        val key = authDecisionDedupeKey(entry)
        if (!shouldRecordAuthDecisionLog(key, now, lastAuthDecisionLogKey, lastAuthDecisionLogAt)) return
        lastAuthDecisionLogKey = key
        lastAuthDecisionLogAt = now
        addAuthDecisionLog(
            authDecisionLogs,
            entry
        )
    }

    fun persistUsersAsync(nextUsers: List<UserTemplate>, onSaved: () -> Unit) {
        storageBusy = true
        status = "얼굴 템플릿 저장 중"
        detail = "암호화된 로컬 저장소에 저장하고 있습니다"
        appScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { saveUsers(context, prefs, nextUsers) }
            }
            if (!active.get()) return@launch
            storageBusy = false
            saved.onSuccess {
                storeError = null
                onSaved()
            }.onFailure {
                storeError = it
                mode = AppMode.Auth
                enrollmentExpiresAt = 0L
                enrollmentName = ""
                enrollSamples.clearSecurely()
                enrollPoses.clear()
                resetTransient()
                status = "로컬 생체 저장소를 사용할 수 없습니다"
                detail = "암호화된 얼굴 템플릿 저장에 실패해 인증을 차단했습니다"
            }
        }
    }

    fun requestAdmin(action: AdminAction) {
        if (canAuthorizeAdminActionWithHeadAdminFace(action, users)) {
            pendingAdminAction = action
            adminPromptInFlight = false
            adminFaceAuthExpiresAt = SystemClock.elapsedRealtime() + ADMIN_FACE_AUTH_TIMEOUT_MS
            appScreen = AppScreen.Operation
            mode = AppMode.AdminAuth
            enrollmentExpiresAt = 0L
            authResultHoldUntil = 0L
            accessFeedback = null
            resetTransient()
            guideState = FaceGuideState.Searching
            status = "Head Admin 인증 중"
            detail = "관리 화면을 열려면 설정된 Head Admin 중 한 명이 카메라를 바라봐 주세요"
            return
        }
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure) {
            (context as? Activity)?.window?.let { applyDoorTerminalSystemUi(window = it, immersive = false) }
            status = "기기 잠금 설정이 필요합니다"
            detail = "Head Admin 등록/해제와 초기 설정에는 Android 화면 잠금이 필요합니다"
            return
        }
        val prompt = keyguard.createConfirmDeviceCredentialIntent(
            if (requiresAndroidLockForAdminAction(action, users)) "FFacio Head Admin 설정" else "FFacio 초기 관리자 확인",
            if (requiresAndroidLockForAdminAction(action, users)) "Head Admin 등록/해제를 보호합니다" else "Head Admin이 없을 때만 초기 설정에 사용합니다"
        )
        if (prompt == null) {
            (context as? Activity)?.window?.let { applyDoorTerminalSystemUi(window = it, immersive = false) }
            status = "기기 인증을 사용할 수 없습니다"
            detail = "Android 보안 설정을 확인한 뒤 다시 시도하세요"
            return
        }
        pendingAdminAction = action
        adminFaceAuthExpiresAt = 0L
        adminPromptInFlight = true
        authResultHoldUntil = 0L
        liveCandidate = -1
        stableUser = -1
        stableCount = 0
        liveness.reset()
        (context as? Activity)?.window?.let { applyDoorTerminalSystemUi(window = it, immersive = false) }
        adminLauncher.launch(prompt)
    }

    fun performAdminAction(action: AdminAction) {
        val sessionActive = isAdminSessionActive(
            isAdminScreen = appScreen == AppScreen.Admin,
            expiresAtMillis = adminSessionExpiresAt,
            nowMillis = SystemClock.elapsedRealtime()
        )
        if (sessionActive && shouldRunAdminActionImmediatelyInAdminSession(action, isAdminScreen = true)) {
            completeAdminAction(action)
        } else {
            requestAdmin(action)
        }
    }

    fun blockedReason(): String? = when {
        !initialStoreLoaded -> "로컬 저장소를 확인하고 있습니다"
        modelLoading -> "FFacio Runtime 연결 중입니다"
        modelError != null -> "FFacio Runtime을 사용할 수 없습니다"
        storageBusy -> "로컬 저장소를 업데이트하는 중입니다"
        storeError != null -> "로컬 생체 저장소가 잠겨 있습니다"
        !hasCameraPermission -> "카메라 권한이 필요합니다"
        noCameraHardware -> "이 기기에는 사용할 수 있는 카메라가 없습니다"
        analyzerFatalStall -> "카메라 분석이 멈췄습니다"
        !cameraAvailable -> "카메라를 사용할 수 없습니다"
        else -> null
    }

    fun idleReason(): String? = when {
        mode == AppMode.Auth && users.isEmpty() -> "첫 사용자를 등록하세요"
        else -> null
    }

    fun cameraCanPreview(): Boolean = initialStoreLoaded && !modelLoading &&
        canPreviewCamera(
            modelError = modelError,
            storeError = storeError,
            hasCameraPermission = hasCameraPermission,
            cameraAvailable = cameraAvailable,
            noCameraHardware = noCameraHardware,
            analyzerFatalStall = analyzerFatalStall
        )

    fun cameraCanUseForCurrentScreen(): Boolean = shouldUseCameraForScreen(
        baseReady = cameraCanPreview(),
        adminPromptInFlight = adminPromptInFlight,
        isOperationScreen = appScreen == AppScreen.Operation,
        isAdminScreen = appScreen == AppScreen.Admin,
        isEnrollmentMode = mode == AppMode.Enroll
    )

    fun cameraCanAnalyze(): Boolean = shouldAnalyzeCameraFrame(
        baseReady = cameraCanPreview(),
        adminPromptInFlight = adminPromptInFlight,
        isOperationScreen = appScreen == AppScreen.Operation,
        isAdminScreen = appScreen == AppScreen.Admin,
        isEnrollmentMode = mode == AppMode.Enroll,
        hasIdleReason = idleReason() != null || blockedReason() != null
    )

    LaunchedEffect(cameraLifecycleActive, cameraCanAnalyze(), cameraRetryNonce, appScreen, mode, storageBusy) {
        if (!cameraLifecycleActive || !cameraCanAnalyze()) {
            cameraAnalysisWatchStartedAt = 0L
            analyzerProcessingWatchStartedAt = 0L
            cameraWatchdogRebindAttempts = 0
            return@LaunchedEffect
        }
        if (cameraAnalysisWatchStartedAt <= 0L) {
            cameraAnalysisWatchStartedAt = System.currentTimeMillis()
        }
        while (cameraLifecycleActive && cameraCanAnalyze()) {
            delay(1000L)
            val now = System.currentTimeMillis()
            if (runtimeDecisionInFlight.get()) {
                val decisionNow = SystemClock.elapsedRealtime()
                val decisionStartedAt = runtimeDecisionStartedAt.get()
                if (decisionStartedAt > 0L && decisionNow - decisionStartedAt >= RUNTIME_DECISION_STALL_RECOVERY_MS) {
                    runtimeDecisionToken.incrementAndGet()
                    runtimeDecisionGeneration.incrementAndGet()
                    runtimeDecisionStartedAt.set(0L)
                    runtimeDecisionInFlight.set(false)
                    cameraAnalysisWatchStartedAt = now
                    lastAnalysisAt.set(now)
                    analyzerProcessingWatchStartedAt = 0L
                    resetTransient(invalidatePendingDecision = false)
                    status = "Runtime 비교가 응답하지 않습니다"
                    detail = "지연된 결과를 폐기하고 Runtime Binder 연결을 다시 초기화합니다"
                    retryRuntime()
                    continue
                }
                cameraAnalysisWatchStartedAt = now
                lastAnalysisAt.set(now)
                analyzerProcessingWatchStartedAt = 0L
                continue
            }
            val processingNow = processing.get()
            if (processingNow && analyzerProcessingWatchStartedAt <= 0L) {
                analyzerProcessingWatchStartedAt = now
            } else if (!processingNow && analyzerProcessingWatchStartedAt > 0L) {
                analyzerProcessingWatchStartedAt = 0L
            }
            when (cameraAnalysisWatchdogAction(
                    analysisExpected = true,
                    nowMillis = now,
                    watchStartedAtMillis = cameraAnalysisWatchStartedAt,
                    lastAnalysisAtMillis = lastAnalysisAt.get(),
                    lastRetryAtMillis = lastCameraWatchdogRetryAt,
                    processingInFlight = processingNow,
                    processingInFlightStartedAtMillis = analyzerProcessingWatchStartedAt,
                    rebindAttemptCount = cameraWatchdogRebindAttempts
                )
            ) {
                CameraAnalysisWatchdogAction.None -> Unit
                CameraAnalysisWatchdogAction.FailVisible -> {
                    lastCameraWatchdogRetryAt = now
                    cameraAnalysisWatchStartedAt = now
                    analyzerFatalStall = true
                    cameraAvailable = false
                    resetTransient()
                    status = "카메라 분석이 멈췄습니다"
                    detail = if (processingNow) {
                        "분석 작업이 오래 응답하지 않습니다. Runtime 앱 설치·서명·연결 상태를 확인한 뒤 다시 시도해 주세요"
                    } else {
                        "프레임 수신이 반복해서 멈췄습니다. Runtime 앱 설치·서명·연결 상태를 확인한 뒤 다시 시도해 주세요"
                    }
                    break
                }
                CameraAnalysisWatchdogAction.RebindCamera -> {
                    lastCameraWatchdogRetryAt = now
                    cameraAnalysisWatchStartedAt = now
                    cameraWatchdogRebindAttempts += 1
                    analyzerProcessingWatchStartedAt = 0L
                    firstAnalyzedFrameLogged.set(false)
                    lastAnalysisAt.set(0L)
                    cameraAvailable = true
                    cameraRetryNonce += 1
                    resetTransient()
                    status = "카메라 분석을 다시 연결하는 중입니다"
                    detail = "프레임 수신이 지연되어 카메라를 자동으로 복구합니다"
                    break
                }
            }
        }
    }

    fun openDoor(user: UserTemplate) {
        if (adminPromptInFlight || appScreen != AppScreen.Operation) return
        if (doorExecutor.isShutdown) return
        val relayUrl = doorUrl.trim()
        val relayToken = doorToken.trim()
        if (relayUrl.isEmpty() || relayToken.isEmpty()) {
            doorArmed = false
            prefs.edit().putBoolean(DOOR_ENABLED_KEY, false).apply()
            accessFeedback = AccessFeedback(AccessFeedbackKind.AuthOnly, user.name)
            status = welcomeStatus(user.name)
            detail = "문 제어는 릴레이 URL과 토큰 설정 후 사용할 수 있습니다"
            return
        }
        if (!processDoorRequestGate.tryStart(doorArmed = doorArmed, nowMillis = SystemClock.elapsedRealtime())) return
        accessFeedback = AccessFeedback(AccessFeedbackKind.DoorPending, user.name)
        status = welcomeStatus(user.name)
        detail = "인증 승인 · 릴레이 응답을 기다리고 있습니다"
        try {
            doorExecutor.execute {
                val ok = postDoor(relayUrl, relayToken)
                processDoorRequestGate.finish()
                ContextCompat.getMainExecutor(context).execute {
                    if (!active.get()) return@execute
                    authResultHoldUntil = System.currentTimeMillis() + AUTH_RESULT_HOLD_MS
                    guideState = if (ok) FaceGuideState.Approved else FaceGuideState.Searching
                    accessFeedback = AccessFeedback(if (ok) AccessFeedbackKind.DoorSucceeded else AccessFeedbackKind.DoorFailed, user.name)
                    recordApproval(user.name, if (ok) "문 열림 요청 완료" else "문 제어 실패")
                    status = if (ok) "문 열림 요청 완료" else "문 제어 실패"
                    detail = if (ok) "릴레이가 요청을 수락했습니다" else "URL, 토큰, 네트워크를 확인해 주세요"
                }
            }
        } catch (_: Throwable) {
            processDoorRequestGate.finish()
            accessFeedback = AccessFeedback(AccessFeedbackKind.DoorFailed, user.name)
            recordApproval(user.name, "문 제어 실패")
            status = "문 제어 실패"
            detail = "릴레이 요청을 시작할 수 없습니다"
        }
    }

    fun applyAuthenticationDecision(obs: Observation, decision: AuthenticationRuntimeDecision) {
        if (!FaceSDK.isReady()) {
            resetTransient()
            guideState = FaceGuideState.Rejected
            status = "FFacio Runtime 연결이 끊겼습니다"
            detail = "Runtime이 다시 연결된 뒤 카메라를 다시 바라봐 주세요"
            return
        }
        val match = decision.match
        if (match.successfulComparisons == 0 && match.failedComparisons > 0) {
            resetTransient()
            guideState = FaceGuideState.Rejected
            status = "Runtime 비교 오류"
            detail = "FFacio Runtime 연결이 불안정합니다. 잠시 후 다시 시도하거나 Runtime 연결을 재시도해 주세요"
            return
        }
        if (match.index !in decision.candidateIndices.indices) {
            resetTransient()
            guideState = FaceGuideState.Searching
            status = "인식하지 못했습니다"
            detail = "조명과 거리를 맞춘 뒤 다시 시도해 주세요"
            return
        }
        val matchedUserIndex = decision.candidateIndices[match.index]
        val matchedUser = users.getOrNull(matchedUserIndex)
        if (matchedUser == null || !matchedUser.isCompatible()) {
            resetTransient()
            guideState = FaceGuideState.Searching
            status = "사용자 정보가 변경되었습니다"
            detail = "카메라를 다시 바라봐 주세요"
            return
        }
        if (!acceptsAuthenticationCandidate(
                score = match.score,
                secondScore = match.secondScore,
                supportCount = match.supportCount,
                availableSamples = matchedUser.matchSampleCount()
            )
        ) {
            recordAuthDecision(matchedUser.name, "보류", authDecisionReason(match, matchedUser.matchSampleCount()), match)
            resetTransient()
            guideState = FaceGuideState.Rejected
            status = "인증 보류"
            detail = "등록된 얼굴과 충분히 일치하지 않습니다. 정면과 거리를 맞춰 다시 시도해 주세요"
            return
        }
        if (liveCandidate != matchedUserIndex) {
            liveCandidate = matchedUserIndex
            stableUser = -1
            stableCount = 0
            liveness.reset()
        }
        if (!liveness.update(obs.pose)) {
            stableUser = -1
            stableCount = 0
            guideState = when (liveness.currentTarget()) {
                -1 -> FaceGuideState.TurnLeft
                1 -> FaceGuideState.TurnRight
                else -> FaceGuideState.Center
            }
            status = liveness.prompt()
            detail = "등록된 얼굴 후보의 실제 얼굴 여부를 확인합니다"
            return
        }
        if (stableUser == matchedUserIndex) stableCount += 1 else {
            stableUser = matchedUserIndex
            stableCount = 1
        }
        if (stableCount < 3) {
            guideState = FaceGuideState.Center
            status = "안정적으로 확인 중입니다"
            detail = "잠시 그대로 유지해 주세요 · " + runtimeObservationSummary(obs, includeDemographics = false)
            return
        }
        val user = users.getOrNull(matchedUserIndex) ?: return
        if (mode == AppMode.AdminAuth) {
            val action = pendingAdminAction
            when (adminAuthDecision(action, user)) {
                AdminAuthDecision.Expired -> {
                    cancelAdminAction(
                        message = "관리자 요청이 만료되었습니다",
                        detailMessage = "관리 버튼을 다시 눌러 주세요"
                    )
                    return
                }
                AdminAuthDecision.Rejected -> {
                    recordAuthDecision(user.name, "보류", "not head admin", match)
                    resetTransient()
                    guideState = FaceGuideState.Rejected
                    status = "Head Admin 얼굴이 아닙니다"
                    detail = "설정된 Head Admin 중 한 명이 카메라를 바라봐 주세요"
                    return
                }
                AdminAuthDecision.Approved -> Unit
            }
            val approvedAction = action ?: return
            pendingAdminAction = null
            adminFaceAuthExpiresAt = 0L
            mode = AppMode.Auth
            recordAuthDecision(user.name, "승인", "head admin face approved", match)
            recordApproval(user.name, "관리 승인")
            accessFeedback = AccessFeedback(AccessFeedbackKind.AuthOnly, user.name)
            status = "Head Admin 확인 완료"
            detail = "관리 작업을 여는 중입니다"
            resetTransient()
            completeAdminAction(approvedAction)
            return
        }
        recordAuthDecision(user.name, "승인", "score/support/liveness/stability 통과", match)
        val doorConfigured = doorRelayConfigured(doorUrl, doorToken)
        if (doorArmed && !doorConfigured) {
            doorArmed = false
            prefs.edit().putBoolean(DOOR_ENABLED_KEY, false).apply()
        }
        val shouldOpenDoor = doorArmed && doorConfigured
        authResultHoldUntil = System.currentTimeMillis() + AUTH_RESULT_HOLD_MS
        guideState = if (shouldOpenDoor) FaceGuideState.Center else FaceGuideState.Approved
        accessFeedback = if (shouldOpenDoor) {
            AccessFeedback(AccessFeedbackKind.DoorPending, user.name)
        } else {
            AccessFeedback(AccessFeedbackKind.AuthOnly, user.name)
        }
        if (!shouldOpenDoor) recordApproval(user.name, "승인")
        status = welcomeStatus(user.name)
        detail = when {
            shouldOpenDoor -> "인증 승인 · 릴레이 결과를 기다리고 있습니다"
            !doorConfigured -> "문 제어는 릴레이 URL과 토큰 설정 후 사용할 수 있습니다"
            else -> "인증 승인 · 최근 승인 로그에 기록했습니다"
        }
        resetTransient()
        openDoor(user)
    }

    fun onObservation(obs: Observation) {
        if (analyzerFatalStall || !cameraAvailable) return
        obs.timings?.let { lastRuntimeTimings = it }
        if (cameraWatchdogRebindAttempts != 0) cameraWatchdogRebindAttempts = 0
        if (analyzerProcessingWatchStartedAt != 0L) analyzerProcessingWatchStartedAt = 0L
        if (modelLoading || modelError != null || storeError != null || storageBusy) return
        if (adminPromptInFlight) return
        if (mode == AppMode.Enroll && appScreen != AppScreen.Admin) return
        if (mode == AppMode.Auth && appScreen != AppScreen.Operation) return
        if (mode == AppMode.Auth && System.currentTimeMillis() < authResultHoldUntil) return
        faceBounds = obs.faceBounds ?: if (!obs.ok) null else faceBounds
        if (runtimeDecisionInFlight.get()) return
        if (!obs.ok) {
            resetTransient()
            guideState = if (obs.faceBounds == null) FaceGuideState.Searching else FaceGuideState.Rejected
            status = obs.message
            detail = if (obs.liveScore > 0.0f) {
                "실제 얼굴 여부를 확인하고 있습니다"
            } else if (obs.message.contains("Runtime")) {
                "Runtime 앱 설치·서명·연결 상태를 확인한 뒤 다시 시도해 주세요"
            } else {
                "얼굴을 화면 중앙에 맞춰 주세요"
            }
            return
        }
        if (mode == AppMode.Enroll) {
            val targetPose = enrollmentTargetPose(enrollSamples.size, enrollPoses)
            guideState = faceGuideStateForPose(targetPose ?: obs.pose)
            if (!enrollmentHold.update(targetPose ?: obs.pose, obs.pose)) {
                enrollmentHoldProgress = enrollmentHold.progress()
                status = enrollmentTargetStatus(targetPose ?: obs.pose)
                detail = "${enrollSamples.size}/$ENROLL_SAMPLES · ${enrollmentTargetInstruction(targetPose)}"
                return
            }
            enrollmentHoldProgress = 1.0f
            val enrollmentTemplate = obs.template.copyOf()
            val sampleSnapshot = enrollSamples.map { it.copyOf() }
            val poseSnapshot = enrollPoses.toList()
            val userSnapshot = users.map { it.copyForRuntimeDecision() }
            val cleanName = enrollmentName.ifBlank { name.trim() }
            enrollmentHold.reset()
            enrollmentHoldProgress = 0.0f
            status = "Runtime 등록 품질 확인 중"
            detail = "수집한 얼굴 특징의 중복과 일관성을 확인하고 있습니다"
            val started = runRuntimeDecision(
                computation = {
                    try {
                        val sampleDecision = enrollmentSampleDecision(
                        enrollmentTemplate,
                        obs.pose,
                        sampleSnapshot,
                        poseSnapshot
                    )
                    if (!sampleDecision.accepted) {
                        enrollmentTemplate.fill(0)
                        sampleSnapshot.wipeCopies()
                        return@runRuntimeDecision EnrollmentRuntimeDecision.Rejected(sampleDecision)
                    }
                    duplicateUserForEnrollment(enrollmentTemplate, userSnapshot)?.let { duplicate ->
                        enrollmentTemplate.fill(0)
                        sampleSnapshot.wipeCopies()
                        return@runRuntimeDecision EnrollmentRuntimeDecision.Duplicate(duplicate.name)
                    }
                    if (sampleSnapshot.size + 1 < ENROLL_SAMPLES) {
                        sampleSnapshot.wipeCopies()
                        return@runRuntimeDecision EnrollmentRuntimeDecision.SampleAccepted(enrollmentTemplate, obs.pose)
                    }
                    val allSamples = sampleSnapshot + enrollmentTemplate
                    val allPoses = poseSnapshot + obs.pose
                    if (cleanName.isBlank()) {
                        allSamples.wipeCopies()
                        return@runRuntimeDecision EnrollmentRuntimeDecision.Rejected(
                            EnrollmentSampleDecision(false, "이름을 입력해 주세요", "사용자 이름을 입력한 뒤 다시 등록해 주세요")
                        )
                    }
                    val representative = selectRepresentativeTemplate(allSamples)
                    val templateQuality = enrollmentTemplateQuality(representative, allSamples, allPoses)
                    if (!templateQuality.accepted) {
                        representative.fill(0)
                        allSamples.wipeCopies()
                        return@runRuntimeDecision EnrollmentRuntimeDecision.TemplateRejected(templateQuality)
                    }
                    duplicateUserForEnrollment(representative, userSnapshot)?.let { duplicate ->
                        representative.fill(0)
                        allSamples.wipeCopies()
                        return@runRuntimeDecision EnrollmentRuntimeDecision.Duplicate(duplicate.name)
                    }
                    val auxiliary = allSamples
                        .filterNot { it.contentEquals(representative) }
                        .map { it.copyOf() }
                    val ready = EnrollmentRuntimeDecision.Ready(cleanName, representative.copyOf(), auxiliary)
                        representative.fill(0)
                        allSamples.wipeCopies()
                        ready
                    } finally {
                        userSnapshot.wipeTemplates()
                    }
                },
                discardResult = { decision -> decision.wipe() },
                applyResult = { result ->
                    result.onFailure { error ->
                        enrollmentTemplate.fill(0)
                        sampleSnapshot.wipeCopies()
                        status = "Runtime 비교 오류"
                        detail = runtimeCallFailureMessage(error)
                    }.onSuccess { decision ->
                        when (decision) {
                            is EnrollmentRuntimeDecision.Rejected -> {
                                status = decision.decision.status
                                detail = decision.decision.detail
                            }
                            is EnrollmentRuntimeDecision.Duplicate -> {
                                mode = AppMode.Auth
                                enrollmentExpiresAt = 0L
                                enrollmentName = ""
                                enrollSamples.clearSecurely()
                                enrollPoses.clear()
                                resetTransient()
                                status = "이미 등록된 얼굴입니다"
                                detail = "${decision.userName} 사용자와 너무 비슷합니다. 기존 사용자로 인증하거나 다른 사람을 등록해 주세요"
                            }
                            is EnrollmentRuntimeDecision.SampleAccepted -> {
                                enrollSamples.add(decision.template)
                                enrollPoses.add(decision.pose)
                                enrollmentExpiresAt = SystemClock.elapsedRealtime() + ENROLLMENT_IDLE_TIMEOUT_MS
                                val nextTargetPose = enrollmentTargetPose(enrollSamples.size, enrollPoses)
                                guideState = faceGuideStateForPose(nextTargetPose ?: obs.pose)
                                status = "샘플 수집 중"
                                detail = enrollmentProgressDetail(enrollSamples.size, obs.pose, nextTargetPose) + " · " + runtimeObservationSummary(obs, includeDemographics = true)
                            }
                            is EnrollmentRuntimeDecision.TemplateRejected -> {
                                enrollSamples.clearSecurely()
                                enrollPoses.clear()
                                enrollmentExpiresAt = SystemClock.elapsedRealtime() + ENROLLMENT_IDLE_TIMEOUT_MS
                                resetTransient()
                                status = decision.decision.status
                                detail = decision.decision.detail
                            }
                            is EnrollmentRuntimeDecision.Ready -> {
                                val nextUsers = users.toList() + UserTemplate(
                                    name = decision.name,
                                    template = decision.template,
                                    samples = decision.samples,
                                    engineId = FACE_ENGINE_ID,
                                    templateSize = decision.template.size
                                )
                                persistUsersAsync(nextUsers) {
                                    users.replaceWith(nextUsers)
                                    mode = AppMode.Auth
                                    enrollmentExpiresAt = 0L
                                    enrollmentName = ""
                                    enrollSamples.clearSecurely()
                                    enrollPoses.clear()
                                    resetTransient()
                                    status = "얼굴 등록이 완료되었습니다"
                                    detail = "${decision.name} · 등록 사용자 ${users.size}명"
                                }
                            }
                        }
                    }
                }
            )
            if (!started) {
                enrollmentTemplate.fill(0)
                sampleSnapshot.wipeCopies()
                userSnapshot.wipeTemplates()
            }
            return
        }
        if (users.isEmpty()) {
            resetTransient()
            guideState = FaceGuideState.Searching
            status = "등록된 사용자가 없습니다"
            detail = "아래에서 첫 사용자를 등록해 주세요"
            return
        }
        if (users.none { it.isCompatible() }) {
            resetTransient()
            guideState = FaceGuideState.Searching
            status = "사용자 재등록이 필요합니다"
            detail = "이전 자체 엔진 템플릿은 FFacio Runtime 형식과 호환되지 않습니다. 관리자 화면에서 삭제 후 다시 등록해 주세요"
            return
        }
        val eligibleCandidateIndices = if (mode == AppMode.AdminAuth) adminAuthCandidateIndices(users) else users.indices.toList()
        val candidateIndices = if (liveCandidate in eligibleCandidateIndices) listOf(liveCandidate) else eligibleCandidateIndices
        if (candidateIndices.isEmpty()) {
            resetTransient()
            guideState = FaceGuideState.Searching
            status = if (mode == AppMode.AdminAuth) "Head Admin 재설정이 필요합니다" else "사용자 재등록이 필요합니다"
            detail = if (mode == AppMode.AdminAuth) "호환되는 Head Admin이 없습니다. Android 화면잠금으로 다시 설정해 주세요" else "이전 자체 엔진 템플릿은 FFacio Runtime 형식과 호환되지 않습니다"
            return
        }
        val authTemplate = obs.template.copyOf()
        val candidateUsers = candidateIndices.map { users[it].copyForRuntimeDecision() }
        status = "얼굴 일치 여부 확인 중"
        detail = "FFacio Runtime에서 등록 템플릿과 비교하고 있습니다"
        val started = runRuntimeDecision(
            computation = {
                try {
                    AuthenticationRuntimeDecision(candidateIndices, match(authTemplate, candidateUsers))
                } finally {
                    authTemplate.fill(0)
                    candidateUsers.wipeTemplates()
                }
            },
            applyResult = { result ->
                result.onFailure { error ->
                    resetTransient()
                    guideState = FaceGuideState.Rejected
                    status = "Runtime 비교 오류"
                    detail = runtimeCallFailureMessage(error)
                }.onSuccess { decision ->
                    applyAuthenticationDecision(obs, decision)
                }
            }
        )
        if (!started) {
            authTemplate.fill(0)
            candidateUsers.wipeTemplates()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FFacio", fontWeight = FontWeight.Bold)
                        Text(if (appScreen == AppScreen.Operation) "Door Access System By sampple-korea" else "관리자 설정", fontSize = 12.sp, color = ComposeColor(0xFF6E6E73))
                    }
                },
                actions = {
                    if (appScreen == AppScreen.Operation) {
                        TextButton(onClick = { requestAdmin(AdminAction.OpenAdmin) }) { Text("관리") }
                    } else {
                        TextButton(onClick = {
                            invalidateDoorRelayTest()
                            appScreen = AppScreen.Operation
                            mode = AppMode.Auth
                            adminSessionExpiresAt = 0L
                            enrollmentExpiresAt = 0L
                            clearAuthResultHold()
                            resetTransient()
                            status = if (users.isEmpty()) "등록된 사용자가 없습니다" else "출입 인증 대기 중"
                            detail = if (users.isEmpty()) "관리자 인증 후 첫 사용자를 등록하세요" else "카메라를 바라보고 안내에 따라 얼굴을 돌려 주세요"
                        }) { Text("운영 화면") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ComposeColor(0xFFF5F5F7))
            )
        },
        containerColor = ComposeColor(0xFFF5F5F7)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CameraStage(
                enabled = cameraCanUseForCurrentScreen(),
                analysisEnabled = cameraCanAnalyze(),
                cameraRetryNonce = cameraRetryNonce,
                stageMessage = blockedReason() ?: idleReason() ?: "카메라 준비 중",
                guideState = guideState,
                faceBounds = faceBounds,
                isEnrollmentMode = mode == AppMode.Enroll,
                isAdminAuthMode = mode == AppMode.AdminAuth,
                enrollmentPoses = enrollPoses.toList(),
                enrollmentCount = enrollSamples.size,
                enrollmentHoldProgress = enrollmentHoldProgress,
                engineProvider = engineProvider,
                analyzerExecutor = analyzerExecutor,
                processing = processing,
                decisionInFlight = runtimeDecisionInFlight,
                firstAnalyzedFrameLogged = firstAnalyzedFrameLogged,
                lastAnalysisAt = lastAnalysisAt,
                passiveLivenessEnabled = passiveLivenessEnabled,
                runtimeLivenessLevel = runtimeLivenessLevel,
                occlusionCheckEnabled = occlusionCheckEnabled,
                active = active,
                onObservation = ::onObservation,
                onCancelAdminAuth = {
                    cancelAdminAction(
                        message = "Head Admin 인증이 취소되었습니다",
                        detailMessage = "관리 버튼을 눌러 다시 시작할 수 있습니다"
                    )
                },
                onCameraUnavailable = {
                    cameraAvailable = false
                    resetTransient()
                    status = "카메라를 사용할 수 없습니다"
                    detail = "다른 앱이 카메라를 사용 중인지 확인한 뒤 다시 시도해 주세요"
                },
                onNoCameraHardware = {
                    noCameraHardware = true
                    cameraAvailable = false
                    resetTransient()
                    status = "사용 가능한 카메라가 없습니다"
                    detail = "전면 또는 후면 카메라가 있는 기기에서 실행해 주세요"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 260.dp, max = 430.dp)
            )
            if (appScreen == AppScreen.Operation) {
                OperationPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    status = status,
                    detail = detail,
                    userCount = users.size,
                    accessFeedback = accessFeedback,
                    approvalLogs = approvalLogs,
                    blockedReason = blockedReason(),
                    canRetryCamera = canRetryCamera(cameraAvailable, hasCameraPermission, noCameraHardware, analyzerFatalStall),
                    onRetryCamera = {
                        cameraAvailable = true
                        cameraRetryNonce += 1
                        resetTransient()
                        status = "카메라를 다시 연결하는 중입니다"
                        detail = "잠시만 기다려 주세요"
                    }
                )
            } else {
                ControlPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                status = status,
                detail = detail,
                mode = mode,
                blockedReason = blockedReason(),
                name = name,
                doorUrl = doorUrl,
                doorToken = doorToken,
                doorArmed = doorArmed,
                doorTestInFlight = doorTestInFlight,
                passiveLivenessEnabled = passiveLivenessEnabled,
                runtimeLivenessLevel = runtimeLivenessLevel,
                occlusionCheckEnabled = occlusionCheckEnabled,
                runtimeStateLabel = runtimeConnectionStateLabel(runtimeSnapshot.connected, runtimeSnapshot.connecting, runtimeSnapshot.ready),
                runtimeDisconnectLabel = runtimeDisconnectReasonLabel(runtimeSnapshot.disconnectReason),
                runtimeInitLabel = runtimeInitializationLabel(runtimeSnapshot.initializationResult),
                runtimeReconnectAttempt = runtimeSnapshot.reconnectAttempt,
                runtimePackageLabel = runtimePackageLabel(runtimePackageStatus),
                runtimeTimingSummary = runtimeTimingSummary(lastRuntimeTimings),
                accessFeedback = accessFeedback,
                approvalLogs = approvalLogs,
                authDecisionLogs = authDecisionLogs,
                users = users,
                userCount = users.size,
                enrollCount = enrollSamples.size,
                canResetStore = storeError != null,
                canUnlockDoor = doorConfigError != null,
                canRetryBlocked = blockedReason() != null && !modelLoading && modelError == null && !storageBusy,
                canMutate = !storageBusy,
                onName = { if (mode != AppMode.Enroll) name = it },
                onDoorUrl = {
                    invalidateDoorRelayTest()
                    doorUrl = it
                },
                onDoorToken = {
                    invalidateDoorRelayTest()
                    doorToken = it
                },
                onDoorArmed = onDoorArmed@{
                    if (doorTestInFlight) {
                        status = "릴레이 확인 중입니다"
                        detail = "연결 테스트가 끝난 뒤 문 열림 릴레이를 변경하세요"
                        return@onDoorArmed
                    }
                    if (it && doorUrl.trim().isEmpty()) {
                        doorArmed = false
                        status = "릴레이 URL이 필요합니다"
                        detail = "문 열림을 활성화하려면 HTTPS 릴레이 URL을 먼저 입력하세요"
                    } else if (it && doorToken.trim().isEmpty()) {
                        doorArmed = false
                        status = "릴레이 토큰이 필요합니다"
                        detail = "문 열림을 활성화하려면 Bearer 토큰을 입력하세요"
                    } else if (it && !doorRelayConfigured(doorUrl, doorToken)) {
                        doorArmed = false
                        status = "올바른 HTTPS 릴레이가 필요합니다"
                        detail = "host가 포함된 HTTPS URL과 Bearer 토큰을 확인하세요"
                    } else if (it) {
                        performAdminAction(AdminAction.ArmDoor)
                    } else {
                        performAdminAction(AdminAction.DisarmDoor)
                    }
                },
                onTestDoorRelay = {
                    performAdminAction(AdminAction.TestDoorRelay)
                },
                onPassiveLivenessEnabled = passiveToggle@{
                    pendingPassiveLivenessEnabled = it
                    performAdminAction(AdminAction.SetPassiveLiveness)
                },
                onRuntimeLivenessLevel = { level ->
                    pendingRuntimeLivenessLevel = level
                    performAdminAction(AdminAction.SetRuntimeLivenessLevel)
                },
                onOcclusionCheckEnabled = { enabled ->
                    pendingOcclusionCheckEnabled = enabled
                    performAdminAction(AdminAction.SetOcclusionCheck)
                },
                onReconnectRuntime = {
                    status = "FFacio Runtime을 다시 연결하는 중입니다"
                    detail = "Binder 세션을 해제하고 다시 바인딩합니다"
                    retryRuntime()
                },
                onEnroll = enroll@{
                    blockedReason()?.let {
                        status = it
                        detail = "상태를 해결한 뒤 다시 시도해 주세요"
                        return@enroll
                    }
                    clearAuthResultHold()
                    guideState = FaceGuideState.Center
                    if (name.trim().isEmpty()) {
                        status = "이름을 입력하세요"
                        detail = "등록할 사용자의 이름이 필요합니다"
                    } else {
                        performAdminAction(AdminAction.StartEnroll)
                    }
                },
                onAuth = auth@{
                    blockedReason()?.let {
                        status = it
                        detail = "상태를 해결한 뒤 다시 시도해 주세요"
                        return@auth
                    }
                    mode = AppMode.Auth
                    enrollmentExpiresAt = 0L
                    enrollmentName = ""
                    enrollSamples.clearSecurely()
                    enrollPoses.clear()
                    clearAuthResultHold()
                    guideState = FaceGuideState.Searching
                    resetTransient()
                    status = if (users.isEmpty()) "먼저 얼굴을 등록하세요" else "인증 모드"
                    detail = if (users.isEmpty()) "등록 사용자 0명" else "카메라를 바라봐 주세요"
                },
                onDelete = {
                    confirmDelete = true
                },
                onDeleteUser = { index ->
                    pendingDeleteUserIndex = index
                },
                onSetHeadAdmin = { index ->
                    pendingHeadAdminUserIndex = index
                    requestAdmin(AdminAction.SetHeadAdmin)
                },
                onClearHeadAdmin = { index ->
                    pendingHeadAdminUserIndex = index
                    requestAdmin(AdminAction.ClearHeadAdmin)
                },
                onUnlockStore = {
                    performAdminAction(AdminAction.UnlockStore)
                },
                onResetStore = {
                    performAdminAction(AdminAction.ResetStore)
                },
                onUnlockDoor = {
                    performAdminAction(AdminAction.UnlockDoor)
                },
                onRetry = retry@{
                    if (modelError != null) {
                        status = "FFacio Runtime을 다시 연결하는 중입니다"
                        detail = "Runtime 설치·서명·서비스 상태를 다시 확인합니다"
                        retryRuntime()
                        return@retry
                    }
                    if (noCameraHardware) {
                        status = blockedReason() ?: status
                        detail = "전면 또는 후면 카메라가 있는 기기에서 실행해 주세요"
                        return@retry
                    }
                    if (analyzerFatalStall) {
                        analyzerFatalStall = false
                        cameraAvailable = true
                        firstAnalyzedFrameLogged.set(false)
                        lastAnalysisAt.set(0L)
                        cameraRetryNonce += 1
                        status = "Runtime과 카메라를 복구하는 중입니다"
                        detail = "Binder 연결과 카메라 분석 파이프라인을 다시 시작합니다"
                        retryRuntime()
                        return@retry
                    }
                    cameraAvailable = true
                    noCameraHardware = false
                    cameraRetryNonce += 1
                    if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
                    if (storeError != null) {
                        performAdminAction(AdminAction.UnlockStore)
                    } else if (doorConfigError != null) {
                        performAdminAction(AdminAction.UnlockDoor)
                    } else {
                        status = "카메라를 다시 확인하는 중입니다"
                        detail = "잠시만 기다려 주세요"
                    }
                },
                onOpenSettings = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    })
                }
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!storageBusy) confirmDelete = false },
            title = { Text("등록 사용자를 삭제할까요?") },
            text = {
                Text("이 기기에 저장된 얼굴 템플릿이 삭제됩니다. 작업은 되돌릴 수 없습니다.")
            },
            confirmButton = {
                TextButton(
                    enabled = !storageBusy,
                    onClick = { performAdminAction(AdminAction.DeleteUsers) }
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(enabled = !storageBusy, onClick = { confirmDelete = false }) { Text("취소") }
            }
        )
    }
    users.getOrNull(pendingDeleteUserIndex)?.let { deleteUser ->
        AlertDialog(
            onDismissRequest = { if (!storageBusy) pendingDeleteUserIndex = -1 },
            title = { Text("${deleteUser.name} 사용자를 삭제할까요?") },
            text = { Text("이 사용자의 로컬 얼굴 템플릿을 삭제합니다.") },
            confirmButton = {
                TextButton(
                    enabled = !storageBusy,
                    onClick = { performAdminAction(AdminAction.DeleteUser) }
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(enabled = !storageBusy, onClick = { pendingDeleteUserIndex = -1 }) { Text("취소") }
            }
        )
    }
}

@Composable
private fun CameraStage(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    analysisEnabled: Boolean,
    cameraRetryNonce: Int,
    stageMessage: String,
    guideState: FaceGuideState,
    faceBounds: FaceBounds?,
    isEnrollmentMode: Boolean,
    isAdminAuthMode: Boolean,
    enrollmentPoses: List<Int>,
    enrollmentCount: Int,
    enrollmentHoldProgress: Float,
    engineProvider: () -> MobileFaceEngine?,
    analyzerExecutor: ExecutorService,
    processing: AtomicBoolean,
    decisionInFlight: AtomicBoolean,
    firstAnalyzedFrameLogged: AtomicBoolean,
    lastAnalysisAt: AtomicLong,
    passiveLivenessEnabled: Boolean,
    runtimeLivenessLevel: Int,
    occlusionCheckEnabled: Boolean,
    active: AtomicBoolean,
    onObservation: (Observation) -> Unit,
    onCancelAdminAuth: () -> Unit,
    onCameraUnavailable: () -> Unit,
    onNoCameraHardware: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val currentAnalysisEnabled by rememberUpdatedState(analysisEnabled)
    val currentPassiveLivenessEnabled by rememberUpdatedState(passiveLivenessEnabled)
    val currentRuntimeLivenessLevel by rememberUpdatedState(runtimeLivenessLevel)
    val currentOcclusionCheckEnabled by rememberUpdatedState(occlusionCheckEnabled)
    val currentEnrollmentMode by rememberUpdatedState(isEnrollmentMode)
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(Color.BLACK)
        }
    }
    DisposableEffect(enabled, cameraRetryNonce) {
        var providerFuture: ListenableFuture<ProcessCameraProvider>? = null
        var boundProvider: ProcessCameraProvider? = null
        var boundUseCases: Array<UseCase> = emptyArray()
        var disposed = false
        val mirrorFrames = AtomicBoolean(true)
        if (enabled) {
            providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                runCatching {
                    if (!active.get() || disposed) return@addListener
                    val provider = providerFuture.get()
                    if (disposed) return@addListener
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = if (shouldBindCameraAnalysisUseCase(enabled, analysisEnabled)) {
                        ImageAnalysis.Builder()
                            .setTargetResolution(AndroidSize(640, 480))
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(analyzerExecutor) { proxy ->
                                    if (!currentAnalysisEnabled) {
                                        proxy.close()
                                        return@setAnalyzer
                                    }
                                    analyzeProxy(
                                        proxy = proxy,
                                        engineProvider = engineProvider,
                                        processing = processing,
                                        decisionInFlight = decisionInFlight,
                                        firstAnalyzedFrameLogged = firstAnalyzedFrameLogged,
                                        lastAnalysisAt = lastAnalysisAt,
                                        active = active,
                                        context = context,
                                        frontFacing = mirrorFrames.get(),
                                        passiveLivenessEnabled = currentPassiveLivenessEnabled,
                                        runtimeLivenessLevel = currentRuntimeLivenessLevel,
                                        occlusionCheckEnabled = currentOcclusionCheckEnabled,
                                        enrollmentMode = currentEnrollmentMode,
                                        onObservation = onObservation
                                    )
                                }
                            }
                    } else {
                        null
                    }
                    val selector = when {
                        provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                        provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                        else -> null
                    }
                    if (selector == null) {
                        onNoCameraHardware()
                    } else {
                        mirrorFrames.set(selector == CameraSelector.DEFAULT_FRONT_CAMERA)
                        if (disposed) return@addListener
                        val useCases = if (analysis != null) arrayOf<UseCase>(preview, analysis) else arrayOf<UseCase>(preview)
                        provider.bindToLifecycle(context as ComponentActivity, selector, *useCases)
                        boundProvider = provider
                        boundUseCases = useCases
                    }
                }.onFailure {
                    onCameraUnavailable()
                }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose {
            disposed = true
            boundUseCases.filterIsInstance<ImageAnalysis>().forEach { it.clearAnalyzer() }
            val provider = boundProvider
            val useCases = boundUseCases
            if (provider != null && useCases.isNotEmpty()) {
                runCatching { provider.unbind(*useCases) }
            } else {
                providerFuture?.addListener({
                    runCatching {
                        boundUseCases.filterIsInstance<ImageAnalysis>().forEach { it.clearAnalyzer() }
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(ComposeColor.Black)
    ) {
        val enrollmentGuide = if (enabled && isEnrollmentMode) {
            enrollmentGuideState(enrollmentPoses, enrollmentCount, enrollmentHoldProgress)
        } else {
            null
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val fallbackRingSize = responsiveFaceGuideSize(maxWidth, maxHeight)
            val containerWidthPx = with(density) { maxWidth.toPx() }
            val containerHeightPx = with(density) { maxHeight.toPx() }
            val fallbackRingSizePx = with(density) { fallbackRingSize.toPx() }
            val guideTarget = faceGuideTarget(faceBounds, containerWidthPx, containerHeightPx, fallbackRingSizePx)
            val animatedGuideX by animateFloatAsState(
                targetValue = guideTarget.centerX,
                animationSpec = tween(durationMillis = 180),
                label = "face-guide-x"
            )
            val animatedGuideY by animateFloatAsState(
                targetValue = guideTarget.centerY,
                animationSpec = tween(durationMillis = 180),
                label = "face-guide-y"
            )
            val animatedGuideSizePx by animateFloatAsState(
                targetValue = guideTarget.sizePx,
                animationSpec = tween(durationMillis = 220),
                label = "face-guide-size"
            )
            val ringSize = with(density) { animatedGuideSizePx.toDp() }
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            FaceGuideOverlay(
                state = guideState,
                enrollmentGuide = enrollmentGuide,
                isAdminAuthMode = isAdminAuthMode,
                ringSize = ringSize,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        translationX = animatedGuideX - (containerWidthPx / 2.0f)
                        translationY = animatedGuideY - (containerHeightPx / 2.0f)
                    }
            )
        }
        if (enabled && isAdminAuthMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = ComposeColor(0xFF0A84FF).copy(alpha = 0.92f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        "Head Admin 인증 중",
                        color = ComposeColor.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
                TextButton(onClick = onCancelAdminAuth) {
                    Text("취소", color = ComposeColor.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (!enabled) {
            Text(
                stageMessage,
                color = ComposeColor.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        }
    }
}

@Composable
private fun FaceGuideOverlay(
    state: FaceGuideState,
    enrollmentGuide: EnrollmentGuideState? = null,
    isAdminAuthMode: Boolean = false,
    ringSize: Dp = 260.dp,
    modifier: Modifier = Modifier
) {
    val targetRingColor = when {
        state == FaceGuideState.Approved -> ComposeColor(0xFF30D158)
        state == FaceGuideState.Rejected -> ComposeColor(0xFFFF453A)
        state == FaceGuideState.TurnLeft || state == FaceGuideState.TurnRight -> ComposeColor(0xFF0A84FF)
        isAdminAuthMode -> ComposeColor(0xFF0A84FF)
        state == FaceGuideState.Center -> ComposeColor(0xFFFFFFFF)
        else -> ComposeColor(0x99FFFFFF)
    }
    val ringColor by animateColorAsState(
        targetValue = if (enrollmentGuide != null) ComposeColor(0xFF30D158) else targetRingColor,
        animationSpec = tween(durationMillis = 220),
        label = "face-guide-color"
    )
    val progress by animateFloatAsState(
        targetValue = enrollmentGuide?.progress ?: if (state == FaceGuideState.Approved) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 280),
        label = "face-guide-progress"
    )
    val scale by animateFloatAsState(
        targetValue = when (state) {
            FaceGuideState.Approved -> 1.05f
            FaceGuideState.Rejected -> 0.98f
            else -> 1.0f
        },
        animationSpec = tween(durationMillis = 240),
        label = "face-guide-scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (state == FaceGuideState.Searching) 0.58f else 0.96f,
        animationSpec = tween(durationMillis = 260),
        label = "face-guide-alpha"
    )
    val symbolOffset by animateFloatAsState(
        targetValue = when {
            enrollmentGuide?.nextPose == -1 -> -26.0f
            enrollmentGuide?.nextPose == 1 -> 26.0f
            state == FaceGuideState.TurnLeft -> -26.0f
            state == FaceGuideState.TurnRight -> 26.0f
            else -> 0.0f
        },
        animationSpec = tween(durationMillis = 280),
        label = "face-guide-symbol-offset"
    )
    val symbolState = if (enrollmentGuide == null) state else FaceGuideState.Searching
    Box(
        modifier = modifier
            .size(ringSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
        },
        contentAlignment = Alignment.Center
    ) {
        if (enrollmentGuide != null) {
            Canvas(modifier = Modifier.fillMaxSize().padding(5.dp)) {
                val outerStroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                val poseStroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = ComposeColor.White.copy(alpha = 0.24f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = outerStroke
                )
                drawArc(
                    color = ComposeColor(0xFF30D158).copy(alpha = 0.92f),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = outerStroke
                )
                drawArc(
                    color = ComposeColor(0xFF0071E3).copy(alpha = 0.86f),
                    startAngle = -90f,
                    sweepAngle = 360f * enrollmentGuide.holdProgress,
                    useCenter = false,
                    style = poseStroke
                )
                drawArc(
                    color = enrollmentPoseColor(enrollmentGuide.collected.contains(0)),
                    startAngle = -106f,
                    sweepAngle = 32f,
                    useCenter = false,
                    style = poseStroke
                )
                drawArc(
                    color = enrollmentPoseColor(enrollmentGuide.collected.contains(-1)),
                    startAngle = 158f,
                    sweepAngle = 32f,
                    useCenter = false,
                    style = poseStroke
                )
                drawArc(
                    color = enrollmentPoseColor(enrollmentGuide.collected.contains(1)),
                    startAngle = -10f,
                    sweepAngle = 32f,
                    useCenter = false,
                    style = poseStroke
                )
            }
        } else {
            Canvas(modifier = Modifier.fillMaxSize().padding(5.dp)) {
                val trackStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                val activeStroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = ComposeColor.White.copy(alpha = 0.20f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = trackStroke
                )
                val sweep = when (state) {
                    FaceGuideState.Searching -> 58f
                    FaceGuideState.Center -> 118f
                    FaceGuideState.TurnLeft, FaceGuideState.TurnRight -> 178f
                    FaceGuideState.Rejected -> 240f
                    FaceGuideState.Approved -> 360f
                }
                drawArc(
                    color = ringColor.copy(alpha = 0.94f),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = activeStroke
                )
                if (state != FaceGuideState.Searching && state != FaceGuideState.Approved) {
                    drawArc(
                        color = ringColor.copy(alpha = 0.58f),
                        startAngle = 120f,
                        sweepAngle = 58f,
                        useCenter = false,
                        style = activeStroke
                    )
                    drawArc(
                        color = ringColor.copy(alpha = 0.42f),
                        startAngle = 250f,
                        sweepAngle = 42f,
                        useCenter = false,
                        style = activeStroke
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(ringSize - 28.dp)
                .clip(CircleShape)
                .border(1.dp, ComposeColor.White.copy(alpha = 0.18f), CircleShape)
                .background(ComposeColor.Transparent)
        )
        when (symbolState) {
            FaceGuideState.Approved -> GuideBadge("✓", ComposeColor(0xFF30D158), Modifier.align(Alignment.BottomCenter))
            FaceGuideState.Rejected -> GuideBadge("!", ComposeColor(0xFFFF453A), Modifier.align(Alignment.TopCenter))
            FaceGuideState.TurnLeft -> GuideBadge("‹", ComposeColor(0xFF0A84FF), Modifier.align(Alignment.CenterStart).graphicsLayer { translationX = symbolOffset })
            FaceGuideState.TurnRight -> GuideBadge("›", ComposeColor(0xFF0A84FF), Modifier.align(Alignment.CenterEnd).graphicsLayer { translationX = symbolOffset })
            FaceGuideState.Center -> if (enrollmentGuide != null) Text("•", color = ComposeColor.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            FaceGuideState.Searching -> Text("", color = ComposeColor.White)
        }
    }
}

@Composable
private fun GuideBadge(symbol: String, color: ComposeColor, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.92f))
            .border(1.dp, ComposeColor.White.copy(alpha = 0.34f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = ComposeColor.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EnrollmentPoseRibbon(
    guide: EnrollmentGuideState,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ComposeColor.Black.copy(alpha = 0.58f),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth(0.92f)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                EnrollmentPoseDot("정면", guide.collected.contains(0), guide.nextPose == 0, Modifier.weight(1f))
                EnrollmentPoseDot("왼쪽", guide.collected.contains(-1), guide.nextPose == -1, Modifier.weight(1f))
                EnrollmentPoseDot("오른쪽", guide.collected.contains(1), guide.nextPose == 1, Modifier.weight(1f))
            }
            LinearProgressIndicator(
                progress = { guide.progress },
                modifier = Modifier.fillMaxWidth(),
                color = ComposeColor(0xFF30D158),
                trackColor = ComposeColor.White.copy(alpha = 0.22f)
            )
            Text(
                "${guide.instruction} · ${guide.count}/$ENROLL_SAMPLES",
                color = ComposeColor.White.copy(alpha = 0.92f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EnrollmentPoseDot(label: String, collected: Boolean, active: Boolean, modifier: Modifier = Modifier) {
    val fill by animateColorAsState(
        targetValue = if (collected) ComposeColor(0xFF30D158) else ComposeColor.White.copy(alpha = 0.18f),
        animationSpec = tween(durationMillis = 220),
        label = "enrollment-pose-fill"
    )
    val scale by animateFloatAsState(
        targetValue = if (active && !collected) 1.16f else 1.0f,
        animationSpec = tween(durationMillis = 260),
        label = "enrollment-pose-active-scale"
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(fill)
                .border(1.dp, ComposeColor.White.copy(alpha = 0.44f), CircleShape)
        )
        Text(label, color = if (active || collected) ComposeColor.White else ComposeColor.White.copy(alpha = 0.68f), fontSize = 11.sp)
    }
}

private fun enrollmentPoseColor(collected: Boolean): ComposeColor =
    if (collected) ComposeColor(0xFF30D158).copy(alpha = 0.96f) else ComposeColor.White.copy(alpha = 0.42f)

private fun responsiveFaceGuideSize(maxWidth: Dp, maxHeight: Dp): Dp {
    val shortSide = min(maxWidth.value, maxHeight.value)
    return (shortSide * 0.72f).coerceIn(210f, 300f).dp
}

@Composable
private fun OperationPanel(
    modifier: Modifier = Modifier,
    status: String,
    detail: String,
    userCount: Int,
    accessFeedback: AccessFeedback?,
    approvalLogs: List<ApprovalLogEntry>,
    blockedReason: String?,
    canRetryCamera: Boolean,
    onRetryCamera: () -> Unit
) {
    Surface(
        color = ComposeColor.White,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(if (blockedReason == null) ComposeColor(0xFF30D158) else ComposeColor(0xFFFF9F0A)))
                Text("출입 인증 대기", color = ComposeColor(0xFF6E6E73), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("등록 ${userCount}명", color = ComposeColor(0xFF6E6E73), fontSize = 13.sp)
            }
            AnimatedContent(status, label = "operation-status") {
                Text(it, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            Text(detail, color = ComposeColor(0xFF6E6E73), fontSize = 16.sp)
            if (canRetryCamera) {
                Button(onClick = onRetryCamera, modifier = Modifier.fillMaxWidth()) {
                    Text("카메라 다시 연결")
                }
            }
            accessFeedback?.let { feedback ->
                Surface(
                    color = accessFeedbackBackground(feedback.kind),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(accessFeedbackAccent(feedback.kind)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(accessFeedbackSymbol(feedback.kind), color = ComposeColor.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(accessFeedbackTitle(feedback), color = ComposeColor(0xFF1D1D1F), fontWeight = FontWeight.Bold)
                            Text(
                                accessFeedbackPublicMessage(feedback),
                                color = accessFeedbackText(feedback.kind),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            if (approvalLogs.isNotEmpty()) {
                Text(approvalPublicSummary(approvalLogs.first()), color = ComposeColor(0xFF6E6E73), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ControlPanel(
    modifier: Modifier = Modifier,
    status: String,
    detail: String,
    mode: AppMode,
    blockedReason: String?,
    name: String,
    doorUrl: String,
    doorToken: String,
    doorArmed: Boolean,
    doorTestInFlight: Boolean,
    passiveLivenessEnabled: Boolean,
    runtimeLivenessLevel: Int,
    occlusionCheckEnabled: Boolean,
    runtimeStateLabel: String,
    runtimeDisconnectLabel: String,
    runtimeInitLabel: String,
    runtimeReconnectAttempt: Int,
    runtimePackageLabel: String,
    runtimeTimingSummary: String,
    accessFeedback: AccessFeedback?,
    approvalLogs: List<ApprovalLogEntry>,
    authDecisionLogs: List<AuthDecisionLogEntry>,
    users: List<UserTemplate>,
    userCount: Int,
    enrollCount: Int,
    canResetStore: Boolean,
    canUnlockDoor: Boolean,
    canRetryBlocked: Boolean,
    canMutate: Boolean,
    onName: (String) -> Unit,
    onDoorUrl: (String) -> Unit,
    onDoorToken: (String) -> Unit,
    onDoorArmed: (Boolean) -> Unit,
    onTestDoorRelay: () -> Unit,
    onPassiveLivenessEnabled: (Boolean) -> Unit,
    onRuntimeLivenessLevel: (Int) -> Unit,
    onOcclusionCheckEnabled: (Boolean) -> Unit,
    onReconnectRuntime: () -> Unit,
    onEnroll: () -> Unit,
    onAuth: () -> Unit,
    onDelete: () -> Unit,
    onDeleteUser: (Int) -> Unit,
    onSetHeadAdmin: (Int) -> Unit,
    onClearHeadAdmin: (Int) -> Unit,
    onUnlockStore: () -> Unit,
    onResetStore: () -> Unit,
    onUnlockDoor: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val accent by animateColorAsState(
        if (mode == AppMode.Enroll) ComposeColor(0xFF30D158) else ComposeColor(0xFF0071E3),
        label = "accent"
    )
    var advancedExpanded by remember { mutableStateOf(false) }
    Surface(
        color = ComposeColor.White,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(accent))
                Text("로컬 생체 인증", color = ComposeColor(0xFF6E6E73), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("사용자 ${userCount}명", color = ComposeColor(0xFF6E6E73), fontSize = 13.sp)
            }
            AnimatedContent(status, label = "status") {
                Text(it, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text(detail, color = ComposeColor(0xFF6E6E73), fontSize = 15.sp)
            accessFeedback?.let { feedback ->
                var approvalTarget by remember { mutableStateOf(0.92f) }
                LaunchedEffect(feedback) {
                    approvalTarget = 1.0f
                }
                val approvalScale by animateFloatAsState(
                    targetValue = approvalTarget,
                    animationSpec = tween(durationMillis = 260),
                    label = "approval-card-scale"
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = approvalScale
                            scaleY = approvalScale
                    },
                    color = accessFeedbackBackground(feedback.kind),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(accessFeedbackAccent(feedback.kind)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(accessFeedbackSymbol(feedback.kind), color = ComposeColor.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(accessFeedbackTitle(feedback), color = ComposeColor(0xFF1D1D1F), fontWeight = FontWeight.Bold)
                            Text(
                                accessFeedbackMessage(feedback),
                                color = accessFeedbackText(feedback.kind),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            if (approvalLogs.isNotEmpty()) {
                Surface(color = ComposeColor(0xFFF5F5F7), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("최근 승인 로그", color = ComposeColor(0xFF1D1D1F), fontWeight = FontWeight.SemiBold)
                        approvalLogs.take(3).forEach { entry ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(entry.time, color = ComposeColor(0xFF6E6E73), fontSize = 12.sp)
                                Text(entry.userName, color = ComposeColor(0xFF1D1D1F), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(entry.result, color = if (approvalResultSucceeded(entry.result)) ComposeColor(0xFF248A3D) else ComposeColor(0xFFD70015), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            if (authDecisionLogs.isNotEmpty()) {
                Surface(color = ComposeColor(0xFFF5F5F7), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("인증 결정 로그", color = ComposeColor(0xFF1D1D1F), fontWeight = FontWeight.SemiBold)
                        authDecisionLogs.take(4).forEach { entry ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Text(entry.time, color = ComposeColor(0xFF6E6E73), fontSize = 12.sp)
                                    Text(entry.userName, color = ComposeColor(0xFF1D1D1F), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text(entry.result, color = if (entry.result == "승인") ComposeColor(0xFF248A3D) else ComposeColor(0xFFD70015), fontSize = 12.sp)
                                }
                                Text(authDecisionSummary(entry), color = ComposeColor(0xFF6E6E73), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            if (mode == AppMode.Enroll) {
                Text("등록 진행 $enrollCount/$ENROLL_SAMPLES", color = ComposeColor(0xFF1D1D1F), fontWeight = FontWeight.SemiBold)
            }
            if (mode == AppMode.Enroll) {
                LinearProgressIndicator(
                    progress = { (enrollCount.toFloat() / ENROLL_SAMPLES.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = ComposeColor(0xFF30D158),
                    trackColor = ComposeColor(0xFFE5E5EA)
                )
            }
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                label = { Text("등록 이름") },
                enabled = canMutate && mode != AppMode.Enroll,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (blockedReason != null || canUnlockDoor) {
                Surface(color = ComposeColor(0xFFFFF4E5), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(blockedReason ?: "릴레이 토큰을 다시 확인하세요", color = ComposeColor(0xFF8A4B00), fontWeight = FontWeight.Bold)
                        if (canRetryBlocked) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("다시 확인") }
                                Button(onClick = onOpenSettings, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF1D1D1F))) { Text("앱 설정") }
                            }
                        } else if (blockedReason != null) {
                            Text("현재 상태는 앱 설정 또는 재설치가 필요합니다", color = ComposeColor(0xFF8A4B00), fontSize = 13.sp)
                        }
                        if (canResetStore) {
                            Button(
                                onClick = onUnlockStore,
                                enabled = canMutate,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF0071E3))
                            ) { Text("로컬 템플릿 다시 열기") }
                            Button(
                                onClick = onResetStore,
                                enabled = canMutate,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFFFF3B30))
                            ) { Text("로컬 템플릿 초기화") }
                        }
                        if (canUnlockDoor) {
                            Button(
                                onClick = onUnlockDoor,
                                enabled = canMutate,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF0071E3))
                            ) { Text("릴레이 토큰 다시 열기") }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onEnroll, enabled = canMutate && blockedReason == null && mode != AppMode.Enroll, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF0071E3))) {
                    Text("등록")
                }
                Button(onClick = onAuth, enabled = canMutate && blockedReason == null && mode != AppMode.Auth, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF1D1D1F))) {
                    Text("인증")
                }
            }
            Surface(color = ComposeColor(0xFFF5F5F7), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("등록 사용자 관리", color = ComposeColor(0xFF1D1D1F), fontWeight = FontWeight.SemiBold)
                    if (users.isEmpty()) {
                        Text("등록된 사용자가 없습니다", color = ComposeColor(0xFF6E6E73), fontSize = 13.sp)
                    } else {
                        val headAdminConfigured = users.any { it.isCompatible() && it.isHeadAdmin }
                        if (!headAdminConfigured) {
                            Surface(color = ComposeColor(0xFFFFF4E5), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "Head Admin을 1명 이상 설정하세요. 이후 관리자 화면 진입은 Head Admin 얼굴로 진행됩니다.",
                                    color = ComposeColor(0xFF8A4B00),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        users.forEachIndexed { index, user ->
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(user.name, color = ComposeColor(0xFF1D1D1F), fontWeight = FontWeight.SemiBold)
                                        if (user.isHeadAdmin) {
                                            Surface(color = ComposeColor(0xFF0071E3), shape = RoundedCornerShape(999.dp)) {
                                                Text(
                                                    "Head Admin",
                                                    color = ComposeColor.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        if (user.isHeadAdmin) "관리 작업 얼굴 인증 권한 보유" else "로컬 얼굴 템플릿",
                                        color = if (user.isHeadAdmin) ComposeColor(0xFF248A3D) else ComposeColor(0xFF6E6E73),
                                        fontSize = 12.sp
                                    )
                                }
                                TextButton(enabled = canMutate, onClick = { onDeleteUser(index) }) {
                                    Text("삭제", color = ComposeColor(0xFFFF3B30))
                                }
                            }
                            OutlinedButton(
                                onClick = { if (user.isHeadAdmin) onClearHeadAdmin(index) else onSetHeadAdmin(index) },
                                enabled = canMutate,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (user.isHeadAdmin) "Head Admin 해제" else "Head Admin 추가")
                            }
                            }
                        }
                    }
                }
            }
            Button(onClick = onDelete, enabled = canMutate && userCount > 0, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFFFF3B30))) {
                Text("모든 등록 사용자 삭제")
            }
            TextButton(onClick = { advancedExpanded = !advancedExpanded }, modifier = Modifier.fillMaxWidth()) {
                Text(if (advancedExpanded || doorArmed) "고급 설정 접기" else "고급 설정")
            }
            if (advancedExpanded || doorArmed) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Switch(
                        checked = passiveLivenessEnabled,
                        onCheckedChange = {
                            advancedExpanded = true
                            onPassiveLivenessEnabled(it)
                        },
                        enabled = canMutate
                    )
                    Text("FFacio Runtime 라이브니스", color = ComposeColor(0xFF1D1D1F))
                }
                if (!passiveLivenessEnabled) {
                    Text("기본 모드: 좌우 얼굴 돌리기 챌린지로 실제 얼굴을 확인합니다", color = ComposeColor(0xFF6E6E73), fontSize = 13.sp)
                }
                if (passiveLivenessEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("라이브니스 검사 레벨", color = ComposeColor(0xFF1D1D1F), modifier = Modifier.weight(1f))
                        listOf(0, 1).forEach { level ->
                            val selected = runtimeLivenessLevel == level
                            if (selected) {
                                Button(onClick = {}, enabled = false, colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF0071E3), disabledContainerColor = ComposeColor(0xFF0071E3), disabledContentColor = ComposeColor.White)) { Text("$level") }
                            } else {
                                OutlinedButton(onClick = { onRuntimeLivenessLevel(level) }, enabled = canMutate) { Text("$level") }
                            }
                        }
                    }
                    Text("레벨 정수는 Runtime 엔진 계약에 그대로 전달됩니다", color = ComposeColor(0xFF6E6E73), fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Switch(
                        checked = occlusionCheckEnabled,
                        onCheckedChange = onOcclusionCheckEnabled,
                        enabled = canMutate
                    )
                    Text("얼굴 가림 검사", color = ComposeColor(0xFF1D1D1F))
                }
                Text(
                    if (occlusionCheckEnabled) {
                        "마스크 등 가림이 감지되면 인증과 등록을 차단합니다"
                    } else {
                        "Runtime 검출 요청에서 가림 검사를 제외합니다"
                    },
                    color = ComposeColor(0xFF6E6E73),
                    fontSize = 13.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Switch(checked = doorArmed, onCheckedChange = onDoorArmed, enabled = canMutate && !doorTestInFlight)
                    Text("인증 성공 시 HTTPS 릴레이 열기", color = ComposeColor(0xFF1D1D1F))
                }
            if (doorArmed && doorUrl.trim().isEmpty()) {
                Text("릴레이 URL을 입력해야 문 열림이 활성화됩니다", color = ComposeColor(0xFFFF3B30), fontSize = 13.sp)
            }
            OutlinedTextField(
                value = doorUrl,
                onValueChange = onDoorUrl,
                enabled = canMutate && !doorArmed && !doorTestInFlight,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                label = { Text("HTTPS 릴레이 URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = doorToken,
                onValueChange = onDoorToken,
                enabled = canMutate && !doorArmed && !doorTestInFlight,
                label = { Text("Bearer 토큰") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = onTestDoorRelay,
                enabled = canTestDoorRelayConfig(doorUrl, doorToken, doorTestInFlight, canMutate),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF1D1D1F))
            ) {
                Text(if (doorTestInFlight) "릴레이 확인 중" else "릴레이 연결 테스트")
            }
            Text(
                "테스트는 문을 열지 않습니다. 같은 릴레이 경로의 안전 확인 주소만 호출합니다.",
                color = ComposeColor(0xFF6E6E73),
                fontSize = 13.sp
            )
            Surface(color = ComposeColor(0xFFF5F5F7), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Runtime 진단", color = ComposeColor(0xFF1D1D1F), fontWeight = FontWeight.SemiBold)
                    RuntimeDiagnosticRow("Runtime 앱", runtimePackageLabel)
                    RuntimeDiagnosticRow("Binder 상태", runtimeStateLabel)
                    RuntimeDiagnosticRow("초기화", runtimeInitLabel)
                    RuntimeDiagnosticRow("끊김 사유", runtimeDisconnectLabel)
                    RuntimeDiagnosticRow("자동 재연결", "${runtimeReconnectAttempt}회")
                    RuntimeDiagnosticRow("프레임 계측", runtimeTimingSummary)
                    Button(
                        onClick = onReconnectRuntime,
                        enabled = canMutate,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF1D1D1F))
                    ) { Text("Runtime 재연결") }
                    Text(
                        "진단 정보에는 얼굴 이미지와 템플릿이 포함되지 않습니다",
                        color = ComposeColor(0xFF6E6E73),
                        fontSize = 12.sp
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun RuntimeDiagnosticRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(label, color = ComposeColor(0xFF6E6E73), fontSize = 13.sp, modifier = Modifier.width(84.dp))
        Text(value, color = ComposeColor(0xFF1D1D1F), fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FFacioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = ComposeColor(0xFF0071E3),
            background = ComposeColor(0xFFF5F5F7),
            surface = ComposeColor.White,
            onSurface = ComposeColor(0xFF1D1D1F)
        ),
        content = content
    )
}

private fun analyzeProxy(
    proxy: ImageProxy,
    engineProvider: () -> MobileFaceEngine?,
    processing: AtomicBoolean,
    decisionInFlight: AtomicBoolean,
    firstAnalyzedFrameLogged: AtomicBoolean,
    lastAnalysisAt: AtomicLong,
    active: AtomicBoolean,
    context: Context,
    frontFacing: Boolean,
    passiveLivenessEnabled: Boolean,
    runtimeLivenessLevel: Int,
    occlusionCheckEnabled: Boolean,
    enrollmentMode: Boolean,
    onObservation: (Observation) -> Unit
) {
    if (decisionInFlight.get()) {
        proxy.close()
        return
    }
    val now = System.currentTimeMillis()
    val previous = lastAnalysisAt.get()
    if (now - previous < ANALYSIS_INTERVAL_MS || !lastAnalysisAt.compareAndSet(previous, now)) {
        proxy.close()
        return
    }
    if (!processing.compareAndSet(false, true)) {
        proxy.close()
        return
    }
    if (!active.get()) {
        proxy.close()
        processing.set(false)
        return
    }
    try {
        val engine = engineProvider() ?: return
        if (firstAnalyzedFrameLogged.compareAndSet(false, true)) {
            Log.i("FFacio", "Runtime camera analysis frame received")
        }
        val obs = engine.observe(
            proxy = proxy,
            frontFacing = frontFacing,
            passiveLivenessEnabled = passiveLivenessEnabled,
            enrollmentMode = enrollmentMode,
            livenessLevel = runtimeLivenessLevel,
            occlusionCheckEnabled = occlusionCheckEnabled
        )
        ContextCompat.getMainExecutor(context).execute {
            try {
                if (active.get()) onObservation(obs)
            } finally {
                obs.template.fill(0)
            }
        }
    } catch (error: Exception) {
        Log.e("FFacio", "Runtime camera frame analysis failed", error)
        ContextCompat.getMainExecutor(context).execute {
            if (active.get()) onObservation(Observation.fail(runtimeCallFailureMessage(error)))
        }
    } finally {
        proxy.close()
        processing.set(false)
    }
}

/** CameraX YUV_420_888 planes -> tightly packed NV21 for Runtime yuvToBitmap(). */
private fun imageProxyToNv21(proxy: ImageProxy): ByteArray {
    val crop = proxy.cropRect
    val width = crop.width()
    val height = crop.height()
    require(width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0) { "Invalid camera crop" }
    require(crop.left % 2 == 0 && crop.top % 2 == 0) { "YUV crop origin must be chroma-aligned" }
    require(proxy.planes.size >= 3) { "Expected YUV_420_888 planes" }
    val output = ByteArray(width * height + width * height / 2)

    fun copyPlane(planeIndex: Int, channelOffset: Int, outputStride: Int, planeWidth: Int, planeHeight: Int) {
        val plane = proxy.planes[planeIndex]
        val buffer = plane.buffer.duplicate()
        val bufferStart = buffer.position()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val shift = if (planeIndex == 0) 0 else 1
        val cropLeft = crop.left shr shift
        val cropTop = crop.top shr shift
        var outputOffset = channelOffset
        for (row in 0 until planeHeight) {
            val rowStart = (cropTop + row) * rowStride + cropLeft * pixelStride
            for (column in 0 until planeWidth) {
                val index = bufferStart + rowStart + column * pixelStride
                require(index in bufferStart until buffer.limit()) { "Camera plane buffer is smaller than declared strides" }
                output[outputOffset] = buffer.get(index)
                outputOffset += outputStride
            }
        }
    }

    copyPlane(0, 0, 1, width, height)
    val chromaWidth = width / 2
    val chromaHeight = height / 2
    val ySize = width * height
    // NV21 is VU interleaved. CameraX planes are Y, U, V.
    copyPlane(2, ySize, 2, chromaWidth, chromaHeight)
    copyPlane(1, ySize + 1, 2, chromaWidth, chromaHeight)
    return output
}

private fun runtimeNativeOrientation(rotationDegrees: Int, frontFacing: Boolean): Int {
    val normalized = ((rotationDegrees % 360) + 360) % 360
    return if (frontFacing) {
        when (normalized) {
            0 -> 2
            90 -> 7
            180 -> 4
            270 -> 5
            else -> 7
        }
    } else {
        when (normalized) {
            0 -> 1
            90 -> 6
            180 -> 3
            270 -> 8
            else -> 6
        }
    }
}

private class MobileFaceEngine {
    fun hasPassiveLiveness(): Boolean = true

    fun observe(
        proxy: ImageProxy,
        frontFacing: Boolean,
        passiveLivenessEnabled: Boolean,
        enrollmentMode: Boolean,
        livenessLevel: Int = 0,
        occlusionCheckEnabled: Boolean = true
    ): Observation {
        if (!FaceSDK.isReady()) return Observation.fail("FFacio Runtime 연결이 준비되지 않았습니다")
        var bitmap: Bitmap? = null
        try {
            val convertStartedAt = SystemClock.elapsedRealtime()
            val nv21 = imageProxyToNv21(proxy)
            bitmap = try {
                FaceSDK.yuv2Bitmap(
                    nv21,
                    proxy.cropRect.width(),
                    proxy.cropRect.height(),
                    runtimeNativeOrientation(proxy.imageInfo.rotationDegrees, frontFacing)
                )
            } finally {
                nv21.fill(0)
            } ?: return Observation.fail("Runtime이 카메라 이미지를 변환하지 못했습니다")
            val convertMillis = SystemClock.elapsedRealtime() - convertStartedAt

            val options = runtimeDetectionOptions(
                passiveLivenessEnabled = passiveLivenessEnabled,
                livenessLevel = livenessLevel,
                occlusionCheckEnabled = occlusionCheckEnabled,
                enrollmentMode = enrollmentMode
            )
            val detectStartedAt = SystemClock.elapsedRealtime()
            val faces = FaceSDK.faceDetection(bitmap, options).orEmpty()
            val detectMillis = SystemClock.elapsedRealtime() - detectStartedAt
            if (faces.isEmpty()) return Observation.fail("얼굴을 찾을 수 없습니다")
            if (faces.size > 1) return Observation.fail("한 명만 카메라 앞에 서 주세요")
            val face = faces.single()
            val bounds = faceBoundsFromRuntime(face, bitmap.width, bitmap.height)
                ?: return Observation.fail("Runtime 얼굴 좌표가 올바르지 않습니다")
            val faceWidth = face.x2 - face.x1
            if (faceWidth < bitmap.width * 0.16f) {
                return Observation.fail("조금 더 가까이 와 주세요", faceBounds = bounds)
            }
            if (!face.face_quality.isFinite() || face.face_quality < RUNTIME_QUALITY_THRESHOLD) {
                return Observation.fail("얼굴 품질이 낮습니다. 조명과 초점을 맞춰 주세요", faceBounds = bounds)
            }
            if (!face.face_luminance.isFinite()) {
                return Observation.fail("얼굴 밝기 값을 확인할 수 없습니다", faceBounds = bounds)
            }
            if (!face.yaw.isFinite() || !face.pitch.isFinite() || !face.roll.isFinite()) {
                return Observation.fail("얼굴 자세 값을 확인할 수 없습니다", faceBounds = bounds)
            }
            val maximumPitch = if (enrollmentMode) 10.0f else RUNTIME_MAX_PITCH
            val maximumRoll = if (enrollmentMode) 10.0f else RUNTIME_MAX_ROLL
            if (kotlin.math.abs(face.pitch) > maximumPitch || kotlin.math.abs(face.roll) > maximumRoll) {
                return Observation.fail("고개를 세우고 카메라를 정면으로 바라봐 주세요", faceBounds = bounds)
            }
            if (enrollmentMode && face.landmarks_68?.size != RUNTIME_LANDMARK_VALUE_COUNT) {
                return Observation.fail("얼굴 특징점을 안정적으로 찾지 못했습니다", faceBounds = bounds)
            }
            if (passiveLivenessEnabled) {
                if (!face.liveness.isFinite() || face.liveness < ANTISPOOF_THRESHOLD) {
                    return Observation.fail(
                        "사진이나 화면으로 보이는 얼굴입니다. 실제 얼굴을 보여주세요",
                        liveScore = face.liveness.takeIf { it.isFinite() } ?: 0f,
                        liveState = "runtime_rejected",
                        faceBounds = bounds
                    )
                }
            }
            if (!face.left_eye_closed.isFinite() || !face.right_eye_closed.isFinite()) {
                return Observation.fail("눈 상태 값을 확인할 수 없습니다", faceBounds = bounds)
            }
            if (face.left_eye_closed > RUNTIME_EYE_CLOSED_THRESHOLD || face.right_eye_closed > RUNTIME_EYE_CLOSED_THRESHOLD) {
                return Observation.fail("눈을 뜨고 카메라를 바라봐 주세요", faceBounds = bounds)
            }
            if (occlusionCheckEnabled) {
                if (!face.face_occlusion.isFinite()) {
                    return Observation.fail("얼굴 가림 상태 값을 확인할 수 없습니다", faceBounds = bounds)
                }
                if (face.face_occlusion > RUNTIME_OCCLUSION_THRESHOLD) {
                    return Observation.fail("얼굴을 가리는 물체를 치워 주세요", faceBounds = bounds)
                }
            }
            if (!face.mouth_opened.isFinite()) {
                return Observation.fail("입 벌림 상태 값을 확인할 수 없습니다", faceBounds = bounds)
            }
            if (face.mouth_opened > RUNTIME_MOUTH_OPEN_THRESHOLD) {
                return Observation.fail("입을 다문 상태로 유지해 주세요", faceBounds = bounds)
            }

            val templateStartedAt = SystemClock.elapsedRealtime()
            val template = FaceSDK.templateExtraction(bitmap, face)
            val templateMillis = SystemClock.elapsedRealtime() - templateStartedAt
            if (!isUsableRuntimeTemplate(template)) {
                template?.fill(0)
                return Observation.fail("Runtime 얼굴 템플릿을 추출할 수 없습니다", faceBounds = bounds)
            }
            val pose = when {
                face.yaw < -10f -> -1
                face.yaw > 10f -> 1
                else -> 0
            }
            return Observation(
                ok = true,
                message = "확인 중",
                template = template,
                pose = pose,
                liveScore = if (passiveLivenessEnabled) face.liveness else 1f,
                liveState = if (passiveLivenessEnabled) "runtime_live" else "disabled",
                faceBounds = bounds,
                quality = face.face_quality,
                luminance = face.face_luminance,
                yaw = face.yaw,
                pitch = face.pitch,
                roll = face.roll,
                age = face.age.takeIf { enrollmentMode && it in 1..120 },
                genderCode = face.gender.takeIf { enrollmentMode },
                timings = RuntimeCallTimings(convertMillis, detectMillis, templateMillis)
            )
        } catch (error: Throwable) {
            Log.e("FFacio", "FFacio Runtime observation failed", error)
            return Observation.fail(runtimeCallFailureMessage(error))
        } finally {
            bitmap?.recycle()
        }
    }

    fun close() = Unit
}

private fun faceBoundsFromRuntime(face: FaceBox, frameWidth: Int, frameHeight: Int): FaceBounds? {
    if (frameWidth <= 0 || frameHeight <= 0 || face.x2 <= face.x1 || face.y2 <= face.y1) return null
    val left = face.x1.coerceIn(0, frameWidth).toFloat()
    val top = face.y1.coerceIn(0, frameHeight).toFloat()
    val right = face.x2.coerceIn(0, frameWidth).toFloat()
    val bottom = face.y2.coerceIn(0, frameHeight).toFloat()
    if (right <= left || bottom <= top) return null
    return FaceBounds(left, top, right - left, bottom - top, frameWidth.toFloat(), frameHeight.toFloat())
}

private fun isUsableRuntimeTemplate(value: ByteArray?): Boolean =
    value != null && value.size in MIN_RUNTIME_TEMPLATE_BYTES..MAX_RUNTIME_TEMPLATE_BYTES

private fun runtimeObservationSummary(obs: Observation, includeDemographics: Boolean): String {
    val base = "품질 %.2f · 밝기 %.0f · 자세 %.0f/%.0f/%.0f".format(
        Locale.US,
        obs.quality,
        obs.luminance,
        obs.yaw,
        obs.pitch,
        obs.roll
    )
    if (!includeDemographics) return base
    val estimatedAge = obs.age?.let { "추정 나이 ${it}" }
    val gender = obs.genderCode?.let { "성별 코드 ${it}" }
    return listOfNotNull(base, estimatedAge, gender).joinToString(" · ")
}

private fun runtimeCallFailureMessage(error: Throwable): String {
    val chain = generateSequence(error) { it.cause }.toList()
    val message = chain.joinToString(" ") { it.message.orEmpty() }
    return when {
        chain.any { it is SecurityException } -> "FFacio와 Runtime을 같은 서명키로 설치해야 합니다"
        message.contains("not connected", ignoreCase = true) -> "FFacio Runtime 연결이 끊겼습니다. 자동 재연결 중입니다"
        message.contains("service is unavailable", ignoreCase = true) -> "FFacio Runtime 앱을 먼저 설치해 주세요"
        message.contains("initialization", ignoreCase = true) -> "FFacio Runtime 초기화에 실패했습니다"
        else -> "FFacio Runtime 분석 오류가 발생했습니다"
    }
}

private class LivenessChallenge {
    private val targets = IntArray(3)
    private var index = 0
    private var holdStartedAt = 0L

    init {
        reset()
    }

    fun reset() {
        targets[0] = 0
        targets[1] = if (Random.nextBoolean()) -1 else 1
        targets[2] = -targets[1]
        index = 0
        holdStartedAt = 0L
    }

    fun update(pose: Int): Boolean {
        if (index >= targets.size) return true
        if (pose != targets[index]) {
            holdStartedAt = 0L
            return false
        }
        val now = System.currentTimeMillis()
        if (holdStartedAt == 0L) {
            holdStartedAt = now
            return false
        }
        if (now - holdStartedAt >= 380L) {
            index += 1
            holdStartedAt = 0L
        }
        return index >= targets.size
    }

    fun currentTarget(): Int = if (index >= targets.size) 0 else targets[index]

    fun prompt(): String = if (index >= targets.size) "라이브니스 확인 완료" else "${poseLabel(targets[index])}으로 살짝 돌려 잠시 유지해 주세요"
}

internal class EnrollmentPoseHold(private val holdMillis: Long = ENROLL_POSE_HOLD_MS) {
    private var targetPose: Int? = null
    private var holdStartedAt = 0L
    private var lastProgress = 0.0f

    fun reset() {
        targetPose = null
        holdStartedAt = 0L
        lastProgress = 0.0f
    }

    fun update(target: Int, pose: Int, now: Long = System.currentTimeMillis()): Boolean {
        if (pose != target) {
            reset()
            return false
        }
        if (targetPose != target || holdStartedAt == 0L) {
            targetPose = target
            holdStartedAt = now
            lastProgress = 0.0f
            return false
        }
        lastProgress = ((now - holdStartedAt).toFloat() / holdMillis.toFloat()).coerceIn(0.0f, 1.0f)
        return now - holdStartedAt >= holdMillis
    }

    fun progress(): Float = lastProgress
}

private enum class AppMode { Auth, Enroll, AdminAuth }
private enum class AppScreen { Operation, Admin }
internal enum class AdminAction {
    OpenAdmin,
    StartEnroll,
    DeleteUser,
    DeleteUsers,
    UnlockStore,
    ResetStore,
    ArmDoor,
    DisarmDoor,
    UnlockDoor,
    TestDoorRelay,
    SetPassiveLiveness,
    SetRuntimeLivenessLevel,
    SetOcclusionCheck,
    SetHeadAdmin,
    ClearHeadAdmin
}
internal enum class AdminAuthDecision { Approved, Rejected, Expired }
private enum class FaceGuideState { Searching, Center, TurnLeft, TurnRight, Approved, Rejected }
internal enum class AccessFeedbackKind { AuthOnly, DoorPending, DoorSucceeded, DoorFailed }
private sealed class ModelLoadState {
    data object Loading : ModelLoadState()
    data object Ready : ModelLoadState()
    data class Failed(val error: Throwable) : ModelLoadState()
}

internal data class AccessFeedback(val kind: AccessFeedbackKind, val userName: String)

private data class Observation(
    val ok: Boolean,
    val message: String,
    val template: ByteArray,
    val pose: Int,
    val liveScore: Float = 0.0f,
    val liveState: String = "unknown",
    val faceBounds: FaceBounds? = null,
    val quality: Float = 0.0f,
    val luminance: Float = 0.0f,
    val yaw: Float = 0.0f,
    val pitch: Float = 0.0f,
    val roll: Float = 0.0f,
    val age: Int? = null,
    val genderCode: Int? = null,
    val timings: RuntimeCallTimings? = null
) {
    companion object {
        fun fail(message: String, liveScore: Float = 0.0f, liveState: String = "unknown", faceBounds: FaceBounds? = null) =
            Observation(false, message, ByteArray(0), 0, liveScore, liveState, faceBounds)
    }
}

/** Runtime AIDL은 검출과 요청 속성을 하나의 detect 호출로 반환하므로 속성만의 시간은 따로 표시하지 않습니다. */
internal data class RuntimeCallTimings(
    val convertMillis: Long,
    val detectMillis: Long,
    val templateMillis: Long
) {
    fun totalMillis(): Long = convertMillis + detectMillis + templateMillis
}

internal fun sanitizeRuntimeLivenessLevel(value: Int): Int = if (value == 1) 1 else 0

internal fun runtimeDetectionOptions(
    passiveLivenessEnabled: Boolean,
    livenessLevel: Int,
    occlusionCheckEnabled: Boolean,
    enrollmentMode: Boolean
): FaceDetectionParam = FaceDetectionParam().apply {
    check_liveness = passiveLivenessEnabled
    check_liveness_level = sanitizeRuntimeLivenessLevel(livenessLevel)
    check_eye_closeness = true
    check_face_occlusion = occlusionCheckEnabled
    check_mouth_opened = true
    estimate_age_gender = enrollmentMode
}

internal fun runtimeConnectionStateLabel(connected: Boolean, connecting: Boolean, ready: Boolean): String = when {
    ready -> "준비됨"
    connecting -> "연결 중"
    connected -> "Binder 연결됨 · 초기화 확인 중"
    else -> "연결 안 됨"
}

internal fun runtimeDisconnectReasonLabel(reason: FaceSDK.DisconnectReason): String = when (reason) {
    FaceSDK.DisconnectReason.NONE -> "정상"
    FaceSDK.DisconnectReason.MANUAL -> "수동 해제"
    FaceSDK.DisconnectReason.SERVICE_DISCONNECTED -> "서비스 연결 끊김"
    FaceSDK.DisconnectReason.BINDER_DIED -> "Runtime 프로세스 종료"
    FaceSDK.DisconnectReason.ERROR -> "연결 오류"
}

internal fun runtimeInitializationLabel(initializationResult: Int): String = when (initializationResult) {
    Int.MIN_VALUE -> "초기화 전"
    FaceSDK.SDK_SUCCESS -> "성공(0)"
    else -> "실패($initializationResult)"
}

internal fun runtimeTimingSummary(timings: RuntimeCallTimings?): String =
    if (timings == null) {
        "아직 측정된 프레임이 없습니다"
    } else {
        "YUV 변환 ${timings.convertMillis}ms · 검출+속성 ${timings.detectMillis}ms · 템플릿 ${timings.templateMillis}ms · 합계 ${timings.totalMillis()}ms"
    }

internal data class RuntimePackageStatus(val installed: Boolean, val versionName: String, val versionCode: Long)

internal fun runtimePackageLabel(status: RuntimePackageStatus): String =
    if (!status.installed) "미설치" else "${status.versionName} (${status.versionCode})"

private fun queryRuntimePackageStatus(context: Context): RuntimePackageStatus = runCatching {
    val info = context.packageManager.getPackageInfo(FFacioRuntimeClient.RUNTIME_PACKAGE, 0)
    RuntimePackageStatus(
        installed = true,
        versionName = info.versionName ?: "?",
        versionCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info)
    )
}.getOrElse { RuntimePackageStatus(false, "", 0L) }

internal data class FaceBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val frameWidth: Float,
    val frameHeight: Float
)

internal data class FaceGuideTarget(val centerX: Float, val centerY: Float, val sizePx: Float)
internal data class UserTemplate(
    val name: String,
    val template: ByteArray,
    val samples: List<ByteArray> = emptyList(),
    val engineId: String = FACE_ENGINE_ID,
    val templateSize: Int = template.size,
    val isHeadAdmin: Boolean = false
) {
    fun matchingSamples(): List<ByteArray> = buildList {
        if (isUsableRuntimeTemplate(template) && template.size == templateSize) add(template)
        samples.filterTo(this) { isUsableRuntimeTemplate(it) && it.size == templateSize }
    }
    fun matchSampleCount(): Int = matchingSamples().size
    fun isCompatible(): Boolean {
        if (engineId != FACE_ENGINE_ID || !isUsableRuntimeTemplate(template) || templateSize != template.size) return false
        if (samples.any { !isUsableRuntimeTemplate(it) || it.size != templateSize }) return false
        return matchingSamples().isNotEmpty()
    }
}
internal data class Match(
    val index: Int,
    val score: Double,
    val secondScore: Double,
    val supportCount: Int,
    val successfulComparisons: Int = 0,
    val failedComparisons: Int = 0
)

private sealed interface EnrollmentRuntimeDecision {
    data class Rejected(val decision: EnrollmentSampleDecision) : EnrollmentRuntimeDecision
    data class Duplicate(val userName: String) : EnrollmentRuntimeDecision
    data class SampleAccepted(val template: ByteArray, val pose: Int) : EnrollmentRuntimeDecision
    data class TemplateRejected(val decision: EnrollmentTemplateQualityDecision) : EnrollmentRuntimeDecision
    data class Ready(val name: String, val template: ByteArray, val samples: List<ByteArray>) : EnrollmentRuntimeDecision
}

private data class AuthenticationRuntimeDecision(
    val candidateIndices: List<Int>,
    val match: Match
)

private fun EnrollmentRuntimeDecision.wipe() {
    when (this) {
        is EnrollmentRuntimeDecision.SampleAccepted -> template.fill(0)
        is EnrollmentRuntimeDecision.Ready -> {
            template.fill(0)
            samples.wipeCopies()
        }
        else -> Unit
    }
}

internal data class EnrollmentGuideState(
    val count: Int,
    val progress: Float,
    val holdProgress: Float,
    val collected: Set<Int>,
    val nextPose: Int?,
    val instruction: String,
    val complete: Boolean
)
internal data class EnrollmentSampleDecision(val accepted: Boolean, val status: String, val detail: String)
internal data class EnrollmentTemplateQualityDecision(val accepted: Boolean, val status: String, val detail: String)
internal data class ApprovalLogEntry(val time: String, val userName: String, val result: String)
internal data class AuthDecisionLogEntry(
    val time: String,
    val userName: String,
    val result: String,
    val reason: String,
    val score: Double,
    val secondScore: Double,
    val supportCount: Int
)
private data class StoreLoadResult(val users: List<UserTemplate>, val error: Throwable?)

private fun MutableList<UserTemplate>.replaceWith(items: List<UserTemplate>) {
    clear()
    addAll(items)
}

private fun MutableList<ByteArray>.clearSecurely() {
    forEach { it.fill(0) }
    clear()
}

private fun List<ByteArray>.wipeCopies() {
    forEach { it.fill(0) }
}

private fun UserTemplate.wipe() {
    template.fill(0)
    samples.wipeCopies()
}

private fun UserTemplate.copyForRuntimeDecision(): UserTemplate = copy(
    template = template.copyOf(),
    samples = samples.map { it.copyOf() }
)

private fun List<UserTemplate>.wipeTemplates() {
    forEach { it.wipe() }
}

internal fun shouldUseCameraForScreen(
    baseReady: Boolean,
    adminPromptInFlight: Boolean,
    isOperationScreen: Boolean,
    isAdminScreen: Boolean,
    isEnrollmentMode: Boolean
): Boolean = baseReady &&
    !adminPromptInFlight &&
    ((isOperationScreen && !isEnrollmentMode) || (isAdminScreen && isEnrollmentMode))

internal fun shouldAnalyzeCameraFrame(
    baseReady: Boolean,
    adminPromptInFlight: Boolean,
    isOperationScreen: Boolean,
    isAdminScreen: Boolean,
    isEnrollmentMode: Boolean,
    hasIdleReason: Boolean
): Boolean = shouldUseCameraForScreen(
    baseReady = baseReady,
    adminPromptInFlight = adminPromptInFlight,
    isOperationScreen = isOperationScreen,
    isAdminScreen = isAdminScreen,
    isEnrollmentMode = isEnrollmentMode
) && !hasIdleReason

internal fun shouldBindCameraAnalysisUseCase(
    cameraEnabled: Boolean,
    analysisEnabled: Boolean
): Boolean = cameraEnabled

internal fun hasHeadAdmin(users: List<UserTemplate>): Boolean =
    users.any { it.isCompatible() && it.isHeadAdmin }

internal fun requiresAndroidLockForAdminAction(action: AdminAction, users: List<UserTemplate>): Boolean = when (action) {
    AdminAction.SetHeadAdmin, AdminAction.ClearHeadAdmin -> true
    else -> !hasHeadAdmin(users)
}

internal fun canAuthorizeAdminActionWithHeadAdminFace(action: AdminAction, users: List<UserTemplate>): Boolean =
    hasHeadAdmin(users) && !requiresAndroidLockForAdminAction(action, users)

internal fun shouldRunAdminActionImmediatelyInAdminSession(action: AdminAction, isAdminScreen: Boolean): Boolean =
    isAdminScreen && action != AdminAction.SetHeadAdmin && action != AdminAction.ClearHeadAdmin

internal fun isAdminSessionActive(
    isAdminScreen: Boolean,
    expiresAtMillis: Long,
    nowMillis: Long
): Boolean = isAdminScreen && expiresAtMillis > 0L && nowMillis < expiresAtMillis

internal fun faceBoundsFromDetection(row: FloatArray, frameWidth: Int, frameHeight: Int): FaceBounds? {
    val left = row.getOrNull(0) ?: return null
    val top = row.getOrNull(1) ?: return null
    val width = row.getOrNull(2) ?: return null
    val height = row.getOrNull(3) ?: return null
    if (frameWidth <= 0 || frameHeight <= 0 || width <= 0.0f || height <= 0.0f) return null
    return FaceBounds(
        left = left.coerceIn(0.0f, frameWidth.toFloat()),
        top = top.coerceIn(0.0f, frameHeight.toFloat()),
        width = width.coerceAtMost(frameWidth.toFloat()),
        height = height.coerceAtMost(frameHeight.toFloat()),
        frameWidth = frameWidth.toFloat(),
        frameHeight = frameHeight.toFloat()
    )
}

internal fun faceGuideTarget(
    bounds: FaceBounds?,
    containerWidth: Float,
    containerHeight: Float,
    fallbackSize: Float
): FaceGuideTarget {
    val fallback = FaceGuideTarget(containerWidth / 2.0f, containerHeight / 2.0f, fallbackSize)
    if (bounds == null || containerWidth <= 0.0f || containerHeight <= 0.0f || bounds.frameWidth <= 0.0f || bounds.frameHeight <= 0.0f) {
        return fallback
    }
    val scale = max(containerWidth / bounds.frameWidth, containerHeight / bounds.frameHeight)
    val drawnWidth = bounds.frameWidth * scale
    val drawnHeight = bounds.frameHeight * scale
    val offsetX = (containerWidth - drawnWidth) / 2.0f
    val offsetY = (containerHeight - drawnHeight) / 2.0f
    val rawSize = max(bounds.width, bounds.height) * scale * 1.62f
    val maxSize = min(containerWidth, containerHeight) * 0.86f
    val minSize = fallbackSize * 0.60f
    val targetSize = rawSize.coerceIn(minSize, maxSize)
    val half = targetSize / 2.0f
    val centerX = ((bounds.left + bounds.width / 2.0f) * scale + offsetX).coerceIn(half, containerWidth - half)
    val centerY = ((bounds.top + bounds.height / 2.0f) * scale + offsetY).coerceIn(half, containerHeight - half)
    return FaceGuideTarget(centerX, centerY, targetSize)
}

internal fun adminAuthCandidateIndices(users: List<UserTemplate>): List<Int> =
    users.indices.filter { index -> users[index].isHeadAdmin && users[index].isCompatible() }

internal fun adminAuthDecision(action: AdminAction?, matchedUser: UserTemplate?): AdminAuthDecision = when {
    action == null -> AdminAuthDecision.Expired
    matchedUser?.isHeadAdmin == true && matchedUser.isCompatible() -> AdminAuthDecision.Approved
    else -> AdminAuthDecision.Rejected
}

internal fun normalizeHeadAdminUsers(users: List<UserTemplate>): List<UserTemplate> {
    return users.map { user ->
        val keepHeadAdmin = user.isHeadAdmin && user.isCompatible()
        if (user.isHeadAdmin == keepHeadAdmin) user else user.copy(isHeadAdmin = keepHeadAdmin)
    }
}

internal fun canRetryCamera(
    cameraAvailable: Boolean,
    hasCameraPermission: Boolean,
    noCameraHardware: Boolean,
    analyzerFatalStall: Boolean
): Boolean =
    !cameraAvailable && hasCameraPermission && !noCameraHardware && !analyzerFatalStall

internal fun canPreviewCamera(
    modelError: Throwable?,
    storeError: Throwable?,
    hasCameraPermission: Boolean,
    cameraAvailable: Boolean,
    noCameraHardware: Boolean,
    analyzerFatalStall: Boolean
): Boolean =
    modelError == null &&
        storeError == null &&
        hasCameraPermission &&
        cameraAvailable &&
        !noCameraHardware &&
        !analyzerFatalStall

internal fun shouldRetryCameraAnalysis(
    analysisExpected: Boolean,
    nowMillis: Long,
    watchStartedAtMillis: Long,
    lastAnalysisAtMillis: Long,
    lastRetryAtMillis: Long,
    stallMillis: Long = CAMERA_ANALYSIS_STALL_MS,
    retryCooldownMillis: Long = CAMERA_WATCHDOG_RETRY_COOLDOWN_MS
): Boolean {
    if (!analysisExpected || watchStartedAtMillis <= 0L) return false
    if (lastRetryAtMillis > 0L && nowMillis - lastRetryAtMillis < retryCooldownMillis) return false
    val lastActivityAt = max(watchStartedAtMillis, lastAnalysisAtMillis)
    return nowMillis - lastActivityAt >= stallMillis
}

internal enum class CameraAnalysisWatchdogAction { None, RebindCamera, FailVisible }

internal fun cameraAnalysisWatchdogAction(
    analysisExpected: Boolean,
    nowMillis: Long,
    watchStartedAtMillis: Long,
    lastAnalysisAtMillis: Long,
    lastRetryAtMillis: Long,
    processingInFlight: Boolean,
    processingInFlightStartedAtMillis: Long = 0L,
    rebindAttemptCount: Int = 0,
    stallMillis: Long = CAMERA_ANALYSIS_STALL_MS,
    retryCooldownMillis: Long = CAMERA_WATCHDOG_RETRY_COOLDOWN_MS,
    maxRebindAttempts: Int = CAMERA_WATCHDOG_MAX_REBIND_ATTEMPTS,
    analyzerFatalStallMillis: Long = CAMERA_ANALYZER_FATAL_STALL_MS
): CameraAnalysisWatchdogAction {
    if (!shouldRetryCameraAnalysis(
            analysisExpected = analysisExpected,
            nowMillis = nowMillis,
            watchStartedAtMillis = watchStartedAtMillis,
            lastAnalysisAtMillis = lastAnalysisAtMillis,
            lastRetryAtMillis = lastRetryAtMillis,
            stallMillis = stallMillis,
            retryCooldownMillis = retryCooldownMillis
        )
    ) {
        return CameraAnalysisWatchdogAction.None
    }
    if (processingInFlight) {
        if (processingInFlightStartedAtMillis <= 0L) return CameraAnalysisWatchdogAction.None
        return if (nowMillis - processingInFlightStartedAtMillis >= analyzerFatalStallMillis) {
            CameraAnalysisWatchdogAction.FailVisible
        } else {
            CameraAnalysisWatchdogAction.None
        }
    }
    return if (rebindAttemptCount >= maxRebindAttempts) {
        CameraAnalysisWatchdogAction.FailVisible
    } else {
        CameraAnalysisWatchdogAction.RebindCamera
    }
}

internal fun shouldReturnToOperationOnLifecyclePause(adminPromptInFlight: Boolean): Boolean =
    !adminPromptInFlight

internal fun shouldUseDoorTerminalImmersive(
    isOperationScreen: Boolean,
    adminPromptInFlight: Boolean,
    touchExplorationEnabled: Boolean
): Boolean = isOperationScreen && !adminPromptInFlight && !touchExplorationEnabled

internal class DoorRequestGate {
    private var requestInFlight = false
    private var lastOpenAtMillis = 0L

    @Synchronized
    fun tryStart(
        doorArmed: Boolean,
        nowMillis: Long,
        cooldownMillis: Long = 3500L
    ): Boolean {
        if (!doorArmed || requestInFlight || nowMillis - lastOpenAtMillis < cooldownMillis) return false
        requestInFlight = true
        lastOpenAtMillis = nowMillis
        return true
    }

    @Synchronized
    fun finish() {
        requestInFlight = false
    }
}

private val processDoorRequestGate = DoorRequestGate()

internal fun canTestDoorRelayConfig(
    relayUrl: String,
    relayToken: String,
    inFlight: Boolean,
    canMutate: Boolean
): Boolean =
    canMutate &&
        !inFlight &&
        relayToken.trim().isNotEmpty() &&
        runCatching { doorRelayHealthCheckUrl(relayUrl) }.isSuccess

private fun applyDoorTerminalSystemUi(window: Window, immersive: Boolean) {
    WindowCompat.setDecorFitsSystemWindows(window, !immersive)
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    if (immersive) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        Log.i("FFacio", "Door terminal immersive enabled")
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
        Log.i("FFacio", "Door terminal immersive disabled")
    }
}

private fun isTouchExplorationEnabled(context: Context): Boolean =
    (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)
        ?.isTouchExplorationEnabled == true

internal fun shouldAutoLockAdminScreen(
    nowMillis: Long,
    expiresAtMillis: Long,
    isAdminScreen: Boolean,
    isEnrollmentMode: Boolean,
    storageBusy: Boolean,
    adminPromptInFlight: Boolean
): Boolean = isAdminScreen &&
    !isEnrollmentMode &&
    !storageBusy &&
    !adminPromptInFlight &&
    expiresAtMillis > 0L &&
    nowMillis >= expiresAtMillis

internal data class AdminAutoLockResetPlan(
    val returnToOperation: Boolean = true,
    val exitEnrollment: Boolean = true,
    val clearAdminSession: Boolean = true,
    val clearEnrollmentSession: Boolean = true,
    val clearAdminDialogs: Boolean = true,
    val clearEnrollment: Boolean = true,
    val clearAuthHold: Boolean = true,
    val clearAccessFeedback: Boolean = true,
    val resetTransientRecognition: Boolean = true
)

internal fun adminAutoLockResetPlan(): AdminAutoLockResetPlan = AdminAutoLockResetPlan()

internal data class AdminAutoLockState(
    val returnToOperation: Boolean = false,
    val authMode: Boolean = false,
    val adminSessionExpiresAt: Long = 1L,
    val enrollmentExpiresAt: Long = 1L,
    val confirmDelete: Boolean = true,
    val pendingDeleteUserIndex: Int = 0,
    val enrollmentName: String = "pending",
    val enrollSampleCount: Int = 1,
    val enrollPoseCount: Int = 1,
    val authResultHoldUntil: Long = 1L,
    val hasAccessFeedback: Boolean = true,
    val liveCandidate: Int = 1,
    val stableUser: Int = 1,
    val stableCount: Int = 1
)

internal fun applyAdminAutoLockReset(
    state: AdminAutoLockState,
    plan: AdminAutoLockResetPlan = adminAutoLockResetPlan()
): AdminAutoLockState = state.copy(
    returnToOperation = if (plan.returnToOperation) true else state.returnToOperation,
    authMode = if (plan.exitEnrollment) true else state.authMode,
    adminSessionExpiresAt = if (plan.clearAdminSession) 0L else state.adminSessionExpiresAt,
    enrollmentExpiresAt = if (plan.clearEnrollmentSession) 0L else state.enrollmentExpiresAt,
    confirmDelete = if (plan.clearAdminDialogs) false else state.confirmDelete,
    pendingDeleteUserIndex = if (plan.clearAdminDialogs) -1 else state.pendingDeleteUserIndex,
    enrollmentName = if (plan.clearEnrollment) "" else state.enrollmentName,
    enrollSampleCount = if (plan.clearEnrollment) 0 else state.enrollSampleCount,
    enrollPoseCount = if (plan.clearEnrollment) 0 else state.enrollPoseCount,
    authResultHoldUntil = if (plan.clearAuthHold) 0L else state.authResultHoldUntil,
    hasAccessFeedback = if (plan.clearAccessFeedback) false else state.hasAccessFeedback,
    liveCandidate = if (plan.resetTransientRecognition) -1 else state.liveCandidate,
    stableUser = if (plan.resetTransientRecognition) -1 else state.stableUser,
    stableCount = if (plan.resetTransientRecognition) 0 else state.stableCount
)

internal fun shouldAutoLockEnrollment(
    nowMillis: Long,
    expiresAtMillis: Long,
    isAdminScreen: Boolean,
    isEnrollmentMode: Boolean,
    storageBusy: Boolean,
    adminPromptInFlight: Boolean
): Boolean = isAdminScreen &&
    isEnrollmentMode &&
    !storageBusy &&
    !adminPromptInFlight &&
    expiresAtMillis > 0L &&
    nowMillis >= expiresAtMillis

internal fun <T> removeRegisteredUserAt(users: List<T>, index: Int): List<T>? =
    if (index in users.indices) users.filterIndexed { itemIndex, _ -> itemIndex != index } else null

private fun loadUsers(context: Context, prefs: SharedPreferences): StoreLoadResult = runCatching {
    val raw = secureGetString(context, prefs, USERS_KEY, "[]", failClosed = true)
    val array = JSONArray(raw)
    StoreLoadResult(normalizeHeadAdminUsers(buildList {
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val name = item.optString("name").trim()
            if (name.isEmpty()) continue
            val schemaVersion = item.optInt("schema_version", 0)
            val storedEngineId = item.optString("engine_id", "legacy.unknown")
            val template = decodeTemplate(item.optString("template_b64", ""))
            val encodedSamples = item.optJSONArray("samples_b64")
            val decodedSamples = buildList {
                if (encodedSamples != null) {
                    for (sampleIndex in 0 until encodedSamples.length()) {
                        add(decodeTemplate(encodedSamples.optString(sampleIndex, "")))
                    }
                }
            }
            val declaredSize = item.optInt("template_size", template.size)
            val compatible = schemaVersion in 2..USER_STORE_SCHEMA_VERSION &&
                storedEngineId == FACE_ENGINE_ID &&
                isUsableRuntimeTemplate(template) &&
                declaredSize == template.size &&
                decodedSamples.all { isUsableRuntimeTemplate(it) && it.size == template.size }
            if (compatible) {
                add(
                    UserTemplate(
                        name = name,
                        template = template,
                        samples = decodedSamples,
                        engineId = FACE_ENGINE_ID,
                        templateSize = template.size,
                        isHeadAdmin = item.optBoolean("head_admin", false)
                    )
                )
            } else {
                template.fill(0)
                decodedSamples.wipeCopies()
                add(
                    UserTemplate(
                        name = name,
                        template = ByteArray(0),
                        samples = emptyList(),
                        engineId = if (storedEngineId == FACE_ENGINE_ID) "$FACE_ENGINE_ID.incompatible" else storedEngineId,
                        templateSize = 0,
                        isHeadAdmin = false
                    )
                )
            }
        }
    }), null)
}.getOrElse { StoreLoadResult(emptyList(), it) }

private fun saveUsers(context: Context, prefs: SharedPreferences, users: List<UserTemplate>) {
    val array = JSONArray()
    users.forEach { user ->
        val cleanName = user.name.trim()
        require(cleanName.isNotEmpty()) { "User name is empty" }
        val compatible = user.isCompatible()
        require(user.engineId != FACE_ENGINE_ID || compatible) { "Runtime user template is incomplete or inconsistent" }
        val item = JSONObject()
        item.put("schema_version", USER_STORE_SCHEMA_VERSION)
        item.put("name", cleanName)
        item.put("engine_id", user.engineId)
        item.put("template_size", if (compatible) user.templateSize else 0)
        item.put("head_admin", compatible && user.isHeadAdmin)
        item.put(
            "template_b64",
            if (compatible) Base64.encodeToString(user.template, Base64.NO_WRAP) else ""
        )
        val samples = JSONArray()
        if (compatible) {
            user.samples.forEach { sample ->
                require(isUsableRuntimeTemplate(sample) && sample.size == user.templateSize) {
                    "Runtime user sample is incomplete or inconsistent"
                }
                samples.put(Base64.encodeToString(sample, Base64.NO_WRAP))
            }
        }
        item.put("samples_b64", samples)
        array.put(item)
    }
    securePutString(context, prefs, USERS_KEY, array.toString())
}

private fun decodeTemplate(encoded: String): ByteArray = runCatching {
    if (encoded.isBlank()) ByteArray(0) else Base64.decode(encoded, Base64.NO_WRAP)
}.getOrDefault(ByteArray(0))

private fun secureGetString(context: Context, prefs: SharedPreferences, key: String, default: String, failClosed: Boolean): String {
    prefs.getString("$key$SECURE_SUFFIX", null)?.let { encrypted ->
        return runCatching {
            val payload = Base64.decode(encrypted, Base64.NO_WRAP)
            decryptPayload(context, payload, KEYSTORE_ALIAS, authRequired = false)
        }.getOrElse {
            if (failClosed) throw IllegalStateException("Encrypted local store authentication failed", it)
            default
        }
    }
    for (suffix in listOf(LEGACY_SECURE_SUFFIX, OLDER_SECURE_SUFFIX)) {
        val legacy = prefs.getString("$key$suffix", null) ?: continue
        val legacyAlias = if (suffix == OLDER_SECURE_SUFFIX) OLDER_KEYSTORE_ALIAS else LEGACY_KEYSTORE_ALIAS
        val migrated = runCatching {
            val payload = Base64.decode(legacy, Base64.NO_WRAP)
            decryptPayload(context, payload, legacyAlias, authRequired = true)
        }
        if (migrated.isSuccess) {
            val value = migrated.getOrThrow()
            securePutString(context, prefs, key, value)
            prefs.edit().remove("$key$suffix").remove(key).commit()
            return value
        }
        if (failClosed) throw IllegalStateException("Legacy encrypted local store migration failed", migrated.exceptionOrNull())
    }
    prefs.getString(key, null)?.let { plaintext ->
        securePutString(context, prefs, key, plaintext)
        prefs.edit().remove(key).commit()
        return plaintext
    }
    return default
}

private fun securePutString(context: Context, prefs: SharedPreferences, key: String, value: String) {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, keystoreKey(context, KEYSTORE_ALIAS, authRequired = false))
    val iv = cipher.iv ?: error("Android Keystore did not provide an AES-GCM IV")
    val cipherText = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    val payload = iv + cipherText
    prefs.edit()
        .putString("$key$SECURE_SUFFIX", Base64.encodeToString(payload, Base64.NO_WRAP))
        .remove("$key$LEGACY_SECURE_SUFFIX")
        .remove("$key$OLDER_SECURE_SUFFIX")
        .remove(key)
        .commit()
        .also { if (!it) error("Encrypted local store could not be saved") }
}

private fun decryptPayload(context: Context, payload: ByteArray, alias: String, authRequired: Boolean): String {
    require(payload.size > 28) { "Encrypted local store payload is truncated" }
    val iv = payload.copyOfRange(0, 12)
    val cipherText = payload.copyOfRange(12, payload.size)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, keystoreKey(context, alias, authRequired), GCMParameterSpec(128, iv))
    return String(cipher.doFinal(cipherText), Charsets.UTF_8)
}

private fun preflightSecureStore(context: Context, prefs: SharedPreferences) {
    val probe = "ok-${System.currentTimeMillis()}"
    securePutString(context, prefs, STORE_PREFLIGHT_KEY, probe)
    val roundTrip = secureGetString(context, prefs, STORE_PREFLIGHT_KEY, "", failClosed = true)
    if (roundTrip != probe) error("Encrypted local store preflight mismatch")
    prefs.edit().remove("$STORE_PREFLIGHT_KEY$SECURE_SUFFIX").commit()
}

private fun keystoreKey(context: Context, alias: String, authRequired: Boolean): SecretKey {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    keyStore.getKey(alias, null)?.let { return it as SecretKey }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    val builder = KeyGenParameterSpec.Builder(
        alias,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .setUserAuthenticationRequired(authRequired)
    if (authRequired) {
        builder.setUserAuthenticationValidityDurationSeconds(120)
    }
    val spec = builder.build()
    generator.init(spec)
    return generator.generateKey()
}

private fun deleteKeystoreAlias(alias: String) {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    if (keyStore.containsAlias(alias)) {
        keyStore.deleteEntry(alias)
    }
}

private fun runtimeSimilarity(first: ByteArray, second: ByteArray): Double {
    require(isUsableRuntimeTemplate(first) && isUsableRuntimeTemplate(second)) { "Invalid Runtime template" }
    require(first.size == second.size) { "Runtime template sizes do not match" }
    return FaceSDK.similarityCalculation(first, second).toDouble().also {
        require(it.isFinite()) { "Runtime returned a non-finite similarity score" }
    }
}

internal fun match(
    template: ByteArray,
    users: List<UserTemplate>,
    comparator: (ByteArray, ByteArray) -> Double = ::runtimeSimilarity
): Match {
    if (!isUsableRuntimeTemplate(template)) return Match(-1, -1.0, -1.0, 0)
    var bestScore = -1.0
    var second = -1.0
    var bestSupportCount = 0
    var bestIndex = -1
    var successfulComparisons = 0
    var failedComparisons = 0
    users.forEachIndexed { index, user ->
        if (!user.isCompatible() || user.templateSize != template.size) return@forEachIndexed
        val scores = buildList {
            user.matchingSamples().forEach { stored ->
                val score = runCatching { comparator(template, stored) }
                    .onFailure { failedComparisons += 1 }
                    .getOrNull()
                if (score != null && score.isFinite()) {
                    successfulComparisons += 1
                    add(score)
                } else if (score != null) {
                    failedComparisons += 1
                }
            }
        }
        val rankScore = scores.maxOrNull() ?: return@forEachIndexed
        val supportCount = scores.count { it >= MATCH_SAMPLE_THRESHOLD }
        if (rankScore > bestScore) {
            second = bestScore
            bestScore = rankScore
            bestSupportCount = supportCount
            bestIndex = index
        } else if (rankScore > second) {
            second = rankScore
        }
    }
    return Match(
        index = bestIndex,
        score = bestScore,
        secondScore = second,
        supportCount = bestSupportCount,
        successfulComparisons = successfulComparisons,
        failedComparisons = failedComparisons
    )
}

internal fun acceptsAuthenticationCandidate(
    score: Double,
    secondScore: Double,
    supportCount: Int,
    availableSamples: Int,
    threshold: Double = MATCH_THRESHOLD,
    margin: Double = MATCH_MARGIN,
    minSupportingSamples: Int = MATCH_MIN_SUPPORTING_SAMPLES
): Boolean {
    if (score < threshold) return false
    if (secondScore > 0.0 && score - secondScore < margin) return false
    val requiredSupport = min(minSupportingSamples, max(1, availableSamples))
    return supportCount >= requiredSupport
}

internal fun enrollmentSampleDecision(
    template: ByteArray,
    pose: Int,
    samples: List<ByteArray>,
    poses: List<Int>,
    comparator: (ByteArray, ByteArray) -> Double = ::runtimeSimilarity
): EnrollmentSampleDecision {
    if (!isUsableRuntimeTemplate(template)) {
        return EnrollmentSampleDecision(false, "얼굴 특징을 다시 추출해 주세요", "Runtime 템플릿이 비어 있거나 손상되었습니다")
    }
    if (samples.any { !isUsableRuntimeTemplate(it) || it.size != template.size }) {
        return EnrollmentSampleDecision(false, "얼굴 특징을 다시 추출해 주세요", "등록 샘플 형식이 일치하지 않습니다")
    }
    val targetPose = enrollmentTargetPose(samples.size, poses)
    if (targetPose != null && pose != targetPose) {
        return EnrollmentSampleDecision(false, enrollmentTargetStatus(targetPose), "${samples.size}/$ENROLL_SAMPLES · ${enrollmentTargetInstruction(targetPose)}")
    }
    val repeated = samples.any { stored ->
        runCatching { comparator(template, stored) }.getOrElse {
            return EnrollmentSampleDecision(false, "Runtime 비교를 다시 시도해 주세요", runtimeCallFailureMessage(it))
        } > ENROLL_REPEAT_THRESHOLD
    }
    if (repeated) {
        return EnrollmentSampleDecision(false, "고개를 좌우로 천천히 돌려 주세요", "${samples.size}/$ENROLL_SAMPLES · 이미 수집한 각도와 너무 비슷합니다")
    }
    if (samples.size >= ENROLL_SAMPLES - 1) {
        val distinctPoses = (poses + pose).toSet().size
        if (distinctPoses < ENROLL_MIN_DISTINCT_POSES) {
            return EnrollmentSampleDecision(false, "고개를 살짝 돌려 주세요", "${samples.size}/$ENROLL_SAMPLES · 정면, 왼쪽, 오른쪽 각도가 모두 필요합니다")
        }
    }
    return EnrollmentSampleDecision(true, "", "")
}

internal fun enrollmentTemplateQuality(
    representative: ByteArray,
    samples: List<ByteArray>,
    poses: List<Int> = emptyList(),
    minSampleScore: Double = ENROLL_TEMPLATE_MIN_SAMPLE_SCORE,
    averageSampleScore: Double = ENROLL_TEMPLATE_AVG_SAMPLE_SCORE,
    minPairScore: Double = ENROLL_TEMPLATE_MIN_PAIR_SCORE,
    comparator: (ByteArray, ByteArray) -> Double = ::runtimeSimilarity
): EnrollmentTemplateQualityDecision {
    if (!isUsableRuntimeTemplate(representative) || samples.isEmpty()) {
        return EnrollmentTemplateQualityDecision(false, "등록 품질이 낮습니다", "얼굴 샘플을 다시 수집해 주세요")
    }
    if (samples.any { !isUsableRuntimeTemplate(it) || it.size != representative.size }) {
        return EnrollmentTemplateQualityDecision(false, "등록 품질이 낮습니다", "Runtime 템플릿 형식이 일치하지 않습니다. 다시 등록해 주세요")
    }
    if (poses.isNotEmpty() && !enrollmentPoseCoverageAccepted(poses)) {
        return EnrollmentTemplateQualityDecision(false, "등록 품질이 낮습니다", "정면, 왼쪽, 오른쪽을 모두 다시 수집해 주세요")
    }
    return runCatching {
        val scores = samples.map { comparator(representative, it) }
        val weakest = scores.minOrNull() ?: 0.0
        val averageScore = scores.average()
        var weakestPair = 1.0
        for (left in samples.indices) {
            for (right in left + 1 until samples.size) {
                weakestPair = min(weakestPair, comparator(samples[left], samples[right]))
            }
        }
        if (weakest >= minSampleScore && averageScore >= averageSampleScore && weakestPair >= minPairScore) {
            EnrollmentTemplateQualityDecision(true, "", "")
        } else {
            EnrollmentTemplateQualityDecision(false, "등록 품질이 낮습니다", "한 사람만 카메라 앞에서 조명과 거리를 맞춘 뒤 처음부터 다시 등록해 주세요")
        }
    }.getOrElse {
        EnrollmentTemplateQualityDecision(false, "Runtime 비교 오류", runtimeCallFailureMessage(it))
    }
}

private fun duplicateUserForEnrollment(
    template: ByteArray,
    users: List<UserTemplate>,
    comparator: (ByteArray, ByteArray) -> Double = ::runtimeSimilarity
): UserTemplate? = users.firstOrNull { user ->
    user.isCompatible() && enrollmentDuplicateScore(template, user, comparator) >= ENROLL_DUPLICATE_THRESHOLD
}

internal fun enrollmentDuplicateScore(
    template: ByteArray,
    user: UserTemplate,
    comparator: (ByteArray, ByteArray) -> Double = ::runtimeSimilarity
): Double {
    if (!user.isCompatible() || user.templateSize != template.size) return -1.0
    return user.matchingSamples().maxOfOrNull { stored ->
        comparator(template, stored).also { require(it.isFinite()) { "Runtime returned a non-finite similarity score" } }
    } ?: -1.0
}

private fun selectRepresentativeTemplate(
    samples: List<ByteArray>,
    comparator: (ByteArray, ByteArray) -> Double = ::runtimeSimilarity
): ByteArray {
    require(samples.isNotEmpty()) { "No Runtime templates to select" }
    if (samples.size == 1) return samples.first().copyOf()
    val best = samples.maxByOrNull { candidate ->
        samples.filterNot { it === candidate }.map { other ->
            comparator(candidate, other).also { require(it.isFinite()) { "Runtime returned a non-finite similarity score" } }
        }.average()
    } ?: samples.first()
    return best.copyOf()
}

private fun poseLabel(pose: Int): String = when {
    pose < 0 -> "왼쪽"
    pose > 0 -> "오른쪽"
    else -> "정면"
}

internal fun enrollmentTargetPose(sampleCount: Int, poses: List<Int>): Int? {
    val guidedSequence = intArrayOf(0, -1, 1, -1, 1)
    return guidedSequence.getOrNull(sampleCount)
}

internal fun enrollmentGuideState(poses: List<Int>, count: Int, holdProgress: Float = 0.0f): EnrollmentGuideState {
    val collected = poses.toSet()
    val nextPose = enrollmentTargetPose(count, poses)
    val complete = count >= ENROLL_SAMPLES
    return EnrollmentGuideState(
        count = count,
        progress = (count.toFloat() / ENROLL_SAMPLES.toFloat()).coerceIn(0f, 1f),
        holdProgress = holdProgress.coerceIn(0f, 1f),
        collected = collected,
        nextPose = nextPose,
        instruction = if (complete) "등록 완료" else enrollmentTargetInstruction(nextPose),
        complete = complete
    )
}

private fun enrollmentTargetInstruction(targetPose: Int?): String = when (targetPose) {
    -1 -> "왼쪽으로 살짝 돌려 잠시 유지"
    1 -> "오른쪽으로 살짝 돌려 잠시 유지"
    0 -> "정면을 바라보고 잠시 유지"
    else -> "얼굴을 천천히 좌우로 움직여 주세요"
}

private fun enrollmentTargetStatus(targetPose: Int): String = when (targetPose) {
    -1 -> "왼쪽으로 살짝 돌려 주세요"
    1 -> "오른쪽으로 살짝 돌려 주세요"
    else -> "정면을 바라봐 주세요"
}

private fun faceGuideStateForPose(pose: Int?): FaceGuideState = when (pose) {
    -1 -> FaceGuideState.TurnLeft
    1 -> FaceGuideState.TurnRight
    0 -> FaceGuideState.Center
    else -> FaceGuideState.Center
}

private fun enrollmentProgressDetail(count: Int, capturedPose: Int, nextPose: Int?): String {
    val nextInstruction = enrollmentTargetInstruction(nextPose)
    return "$count/$ENROLL_SAMPLES · ${poseLabel(capturedPose)} 수집 완료 · $nextInstruction"
}

internal fun enrollmentPoseCoverageAccepted(poses: List<Int>): Boolean {
    val collected = poses.toSet()
    return poses.size >= ENROLL_SAMPLES &&
        collected.contains(0) &&
        collected.contains(-1) &&
        collected.contains(1) &&
        poses.count { it == -1 } >= 2 &&
        poses.count { it == 1 } >= 2
}

internal fun addApprovalLog(
    logs: MutableList<ApprovalLogEntry>,
    entry: ApprovalLogEntry,
    limit: Int = APPROVAL_LOG_LIMIT
) {
    logs.add(0, entry)
    while (logs.size > limit) {
        logs.removeAt(logs.lastIndex)
    }
}

internal fun addAuthDecisionLog(
    logs: MutableList<AuthDecisionLogEntry>,
    entry: AuthDecisionLogEntry,
    limit: Int = AUTH_DECISION_LOG_LIMIT
) {
    logs.add(0, entry)
    while (logs.size > limit) {
        logs.removeAt(logs.lastIndex)
    }
}

internal fun authDecisionReason(match: Match, availableSamples: Int): String = when {
    match.score < MATCH_THRESHOLD -> "score below threshold"
    match.secondScore > 0.0 && match.score - match.secondScore < MATCH_MARGIN -> "ambiguous runner-up"
    match.supportCount < min(MATCH_MIN_SUPPORTING_SAMPLES, max(1, availableSamples)) -> "not enough sample support"
    else -> "candidate accepted"
}

internal fun authDecisionDedupeKey(entry: AuthDecisionLogEntry): String =
    listOf(
        entry.userName,
        entry.result,
        entry.reason
    ).joinToString("|")

internal fun shouldRecordAuthDecisionLog(
    key: String,
    nowMillis: Long,
    lastKey: String,
    lastAtMillis: Long,
    dedupeMillis: Long = AUTH_DECISION_LOG_DEDUPE_MS
): Boolean =
    key != lastKey || lastAtMillis <= 0L || nowMillis - lastAtMillis >= dedupeMillis

internal fun authDecisionSummary(entry: AuthDecisionLogEntry): String =
    "${entry.reason} · score ${formatScore(entry.score)} · second ${formatScore(entry.secondScore)} · support ${entry.supportCount}"

private fun formatScore(value: Double): String =
    if (value < 0.0) "-" else String.format(Locale.US, "%.3f", value)

internal fun approvalResultSucceeded(result: String): Boolean =
    !result.contains("실패")

private fun accessFeedbackBackground(kind: AccessFeedbackKind): ComposeColor = when (kind) {
    AccessFeedbackKind.AuthOnly, AccessFeedbackKind.DoorSucceeded -> ComposeColor(0xFFE9FBEF)
    AccessFeedbackKind.DoorPending -> ComposeColor(0xFFEAF2FF)
    AccessFeedbackKind.DoorFailed -> ComposeColor(0xFFFFE8E6)
}

private fun accessFeedbackAccent(kind: AccessFeedbackKind): ComposeColor = when (kind) {
    AccessFeedbackKind.AuthOnly, AccessFeedbackKind.DoorSucceeded -> ComposeColor(0xFF30D158)
    AccessFeedbackKind.DoorPending -> ComposeColor(0xFF0071E3)
    AccessFeedbackKind.DoorFailed -> ComposeColor(0xFFFF3B30)
}

private fun accessFeedbackText(kind: AccessFeedbackKind): ComposeColor = when (kind) {
    AccessFeedbackKind.AuthOnly, AccessFeedbackKind.DoorSucceeded -> ComposeColor(0xFF248A3D)
    AccessFeedbackKind.DoorPending -> ComposeColor(0xFF0057D9)
    AccessFeedbackKind.DoorFailed -> ComposeColor(0xFFD70015)
}

internal fun accessFeedbackSymbol(kind: AccessFeedbackKind): String = when (kind) {
    AccessFeedbackKind.AuthOnly, AccessFeedbackKind.DoorSucceeded -> "✓"
    AccessFeedbackKind.DoorPending -> "…"
    AccessFeedbackKind.DoorFailed -> "!"
}

internal fun accessFeedbackTitle(feedback: AccessFeedback): String = when (feedback.kind) {
    AccessFeedbackKind.AuthOnly -> welcomeStatus(feedback.userName)
    AccessFeedbackKind.DoorPending -> welcomeStatus(feedback.userName)
    AccessFeedbackKind.DoorSucceeded -> welcomeStatus(feedback.userName)
    AccessFeedbackKind.DoorFailed -> "문 제어 실패"
}

private fun accessFeedbackMessage(feedback: AccessFeedback): String = when (feedback.kind) {
    AccessFeedbackKind.AuthOnly -> "얼굴 인증이 완료되었습니다"
    AccessFeedbackKind.DoorPending -> "인증 승인 · 릴레이 응답을 기다리고 있습니다"
    AccessFeedbackKind.DoorSucceeded -> "릴레이가 문 열림 요청을 수락했습니다"
    AccessFeedbackKind.DoorFailed -> "얼굴 인증은 통과했지만 릴레이 요청이 실패했습니다"
}

internal fun accessFeedbackPublicMessage(feedback: AccessFeedback): String = when (feedback.kind) {
    AccessFeedbackKind.AuthOnly -> "얼굴 인증이 완료되었습니다"
    AccessFeedbackKind.DoorPending -> "인증 승인 · 릴레이 응답을 기다리고 있습니다"
    AccessFeedbackKind.DoorSucceeded -> "릴레이가 문 열림 요청을 수락했습니다"
    AccessFeedbackKind.DoorFailed -> "얼굴 인증은 통과했지만 릴레이 요청이 실패했습니다"
}

internal fun welcomeStatus(userName: String): String = "환영합니다, ${userName}님"

internal fun doorRelayConfigured(doorUrl: String, doorToken: String): Boolean =
    doorToken.trim().isNotEmpty() && runCatching {
        val endpoint = URL(doorUrl.trim())
        endpoint.protocol.lowercase(Locale.US) == "https" && endpoint.host.isNotBlank()
    }.getOrDefault(false)

internal fun approvalPublicSummary(entry: ApprovalLogEntry): String =
    "최근 출입 이벤트 · ${entry.result}"

private fun formatClock(timeMillis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timeMillis))

internal fun doorRelayPayloadJson(): String =
    """{"event":"accepted","source":"ffacio-android"}"""

internal fun doorRelayHealthCheckUrl(relayUrl: String): String {
    val endpoint = URL(relayUrl.trim())
    require(endpoint.protocol.lowercase(Locale.US) == "https") { "HTTPS 릴레이 URL만 사용할 수 있습니다" }
    require(endpoint.host.isNotBlank()) { "릴레이 host가 필요합니다" }
    val openPath = endpoint.path.orEmpty()
    val parentPath = openPath.substringBeforeLast("/", missingDelimiterValue = "")
    val healthPath = if (parentPath.isBlank()) {
        DOOR_RELAY_HEALTH_PATH
    } else {
        "$parentPath$DOOR_RELAY_HEALTH_PATH"
    }
    return URL(endpoint.protocol, endpoint.host, endpoint.port, healthPath).toString()
}

internal data class DoorRelayHealthResult(val accepted: Boolean, val message: String)

internal fun doorRelayHealthHttpFailureMessage(responseCode: Int): String = when (responseCode) {
    401, 403 -> "릴레이 토큰이 거부되었습니다"
    404 -> "릴레이 안전 확인 주소를 찾을 수 없습니다"
    in 500..599 -> "릴레이 장치가 오류 응답을 보냈습니다"
    else -> "릴레이가 테스트 요청을 수락하지 않았습니다"
}

internal fun doorRelayHealthNetworkFailureMessage(error: Throwable): String = when (error) {
    is java.net.UnknownHostException -> "릴레이 주소를 찾을 수 없습니다"
    is java.net.SocketTimeoutException -> "릴레이 응답 시간이 초과되었습니다"
    is javax.net.ssl.SSLException -> "HTTPS 인증서를 확인할 수 없습니다"
    else -> "릴레이에 연결할 수 없습니다"
}

private fun checkDoorRelayHealthUrl(healthUrl: String, token: String): DoorRelayHealthResult = runCatching {
    val endpoint = URL(healthUrl)
    val conn = (endpoint.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        instanceFollowRedirects = false
        connectTimeout = 1800
        readTimeout = 1800
        doOutput = false
        setRequestProperty("Accept", "application/json")
        if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
    }
    try {
        val responseCode = conn.responseCode
        if (responseCode in 200..299) {
            DoorRelayHealthResult(true, "릴레이 연결 정상")
        } else {
            DoorRelayHealthResult(false, doorRelayHealthHttpFailureMessage(responseCode))
        }
    } finally {
        conn.disconnect()
    }
}.getOrElse { DoorRelayHealthResult(false, doorRelayHealthNetworkFailureMessage(it)) }

private fun postDoor(url: String, token: String): Boolean = runCatching {
    val endpoint = URL(url)
    if (endpoint.protocol.lowercase(Locale.US) != "https" || endpoint.host.isBlank()) return@runCatching false
    val conn = (endpoint.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        instanceFollowRedirects = false
        connectTimeout = 1800
        readTimeout = 1800
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
    }
    try {
        val body = doorRelayPayloadJson().toByteArray(Charsets.UTF_8)
        conn.outputStream.use { it.write(body) }
        conn.responseCode in 200..299
    } finally {
        conn.disconnect()
    }
}.getOrDefault(false)
