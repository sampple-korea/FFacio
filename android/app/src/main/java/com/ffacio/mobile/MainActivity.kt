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
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kbyai.facesdk.FaceBox
import com.kbyai.facesdk.FaceSDK
import io.ffacio.sdk.FFacioRuntimeClient
import io.fotoapparat.Fotoapparat
import io.fotoapparat.parameter.Resolution
import io.fotoapparat.parameter.ScaleType
import io.fotoapparat.preview.Frame
import io.fotoapparat.selector.back
import io.fotoapparat.selector.front
import io.fotoapparat.view.CameraView
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.max
import kotlin.math.min

private const val PREFS = "ffacio_store"
private const val USERS_KEY = "users"
private const val USER_STORE_POLICY_KEY = "user_store_policy_version"
internal const val USER_STORE_POLICY_VERSION = 8
private const val SMARTTHINGS_DEVICE_ID_KEY = "smartthings_device_id"
private const val SMARTTHINGS_ACCESS_TOKEN_KEY = "smartthings_access_token"
private const val SMARTTHINGS_ENABLED_KEY = "smartthings_enabled"
private const val SMARTTHINGS_CONFIG_VERSION_KEY = "smartthings_config_version"
private const val SMARTTHINGS_CONFIG_VERSION = 2
private const val LEGACY_DOOR_URL_KEY = "door_url"
private const val LEGACY_DOOR_TOKEN_KEY = "door_token"
private const val LEGACY_DOOR_ENABLED_KEY = "door_enabled"
private const val SMARTTHINGS_API_BASE = "https://api.smartthings.com/v1"
private const val SMARTTHINGS_COMPONENT = "main"
private const val SMARTTHINGS_LOCK_CAPABILITY = "lock"
private const val SMARTTHINGS_MAX_RESPONSE_BYTES = 64 * 1024
internal const val SMARTTHINGS_MAX_TOKEN_LENGTH = 4096
private const val PASSIVE_LIVENESS_ENABLED_KEY = "passive_liveness_enabled"
private const val RUNTIME_LIVENESS_LEVEL_KEY = "runtime_liveness_level"
private const val OCCLUSION_CHECK_ENABLED_KEY = "occlusion_check_enabled"
internal const val FACE_ENGINE_ID = "ffacio.runtime.demo.camera.v4"
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
private const val MATCH_MARGIN = 0.03
private const val ENROLL_DUPLICATE_THRESHOLD = 0.80
private const val CAMERA_ANALYSIS_STALL_MS = 6500L
private const val CAMERA_WATCHDOG_RETRY_COOLDOWN_MS = 6000L
private const val CAMERA_WATCHDOG_MAX_REBIND_ATTEMPTS = 2
private const val CAMERA_ANALYZER_FATAL_STALL_MS = 20_000L
private const val RUNTIME_DECISION_TIMEOUT_MS = 8_000L
private const val RUNTIME_DECISION_STALL_RECOVERY_MS = 10_000L
private const val AUTH_RESULT_HOLD_MS = 3500L
private const val APPROVAL_LOG_LIMIT = 8
private const val AUTH_DECISION_LOG_LIMIT = 8
private const val AUTH_DECISION_LOG_DEDUPE_MS = 2500L
private const val ADMIN_SESSION_TIMEOUT_MS = 120_000L
private const val ADMIN_FACE_AUTH_TIMEOUT_MS = 30_000L
private const val ENROLLMENT_IDLE_TIMEOUT_MS = 60_000L
private const val USER_STORE_SCHEMA_VERSION = 8
internal const val MAX_USER_NAME_LENGTH = 40

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
    var smartThingsDeviceId by remember { mutableStateOf(prefs.getString(SMARTTHINGS_DEVICE_ID_KEY, "") ?: "") }
    var smartThingsAccessToken by remember { mutableStateOf("") }
    var doorConfigError by remember { mutableStateOf<Throwable?>(null) }
    var doorArmed by remember { mutableStateOf(prefs.getBoolean(SMARTTHINGS_ENABLED_KEY, false)) }
    var doorTestInFlight by remember { mutableStateOf(false) }
    var doorTestRequestId by remember { mutableLongStateOf(0L) }
    val startsWithLegacyUserStore = remember {
        needsUserStorePolicyReset(prefs.getInt(USER_STORE_POLICY_KEY, 0))
    }
    var passiveLivenessEnabled by remember {
        mutableStateOf(if (startsWithLegacyUserStore) true else prefs.getBoolean(PASSIVE_LIVENESS_ENABLED_KEY, true))
    }
    var pendingPassiveLivenessEnabled by remember { mutableStateOf<Boolean?>(null) }
    var runtimeLivenessLevel by remember {
        mutableIntStateOf(if (startsWithLegacyUserStore) 0 else sanitizeRuntimeLivenessLevel(prefs.getInt(RUNTIME_LIVENESS_LEVEL_KEY, 0)))
    }
    var pendingRuntimeLivenessLevel by remember { mutableStateOf<Int?>(null) }
    var occlusionCheckEnabled by remember {
        mutableStateOf(if (startsWithLegacyUserStore) false else prefs.getBoolean(OCCLUSION_CHECK_ENABLED_KEY, false))
    }
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
    val approvalLogs = remember { mutableStateListOf<ApprovalLogEntry>() }
    val authDecisionLogs = remember { mutableStateListOf<AuthDecisionLogEntry>() }
    var lastAuthDecisionLogKey by remember { mutableStateOf("") }
    var lastAuthDecisionLogAt by remember { mutableLongStateOf(0L) }
    var accessFeedback by remember { mutableStateOf<AccessFeedback?>(null) }
    var guideState by remember { mutableStateOf(FaceGuideState.Searching) }
    var faceBounds by remember { mutableStateOf<FaceBounds?>(null) }
    val enrollmentStability = remember { EnrollmentStabilityTracker(ENROLL_AUTO_CAPTURE_STABLE_MS) }
    var enrollmentHoldProgress by remember { mutableStateOf(0.0f) }
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

    DisposableEffect(Unit) {
        onDispose {
            runtimeDecisionGeneration.incrementAndGet()
            runtimeDecisionToken.incrementAndGet()
            runtimeDecisionStartedAt.set(0L)
            runtimeDecisionInFlight.set(false)
            enrollmentStability.reset()
            users.forEach { it.wipe() }
            users.clear()
        }
    }

    fun invalidateSmartThingsTest() {
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
        val remaining = authResultHoldUntil - SystemClock.elapsedRealtime()
        if (remaining > 0L) {
            delay(remaining)
            if (SystemClock.elapsedRealtime() >= authResultHoldUntil) {
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
                    pendingDeleteUserIndex = pendingDeleteUserIndex
                ))
                invalidateSmartThingsTest()
                appScreen = AppScreen.Operation
                mode = AppMode.Auth
                adminSessionExpiresAt = 0L
                enrollmentExpiresAt = 0L
                confirmDelete = reset.confirmDelete
                pendingDeleteUserIndex = reset.pendingDeleteUserIndex
                enrollmentName = reset.enrollmentName
                authResultHoldUntil = 0L
                accessFeedback = null
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
                    pendingDeleteUserIndex = pendingDeleteUserIndex
                ))
                invalidateSmartThingsTest()
                appScreen = AppScreen.Operation
                mode = AppMode.Auth
                adminSessionExpiresAt = 0L
                enrollmentExpiresAt = 0L
                confirmDelete = reset.confirmDelete
                pendingDeleteUserIndex = reset.pendingDeleteUserIndex
                enrollmentName = reset.enrollmentName
                authResultHoldUntil = 0L
                accessFeedback = null
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
                invalidateSmartThingsTest()
                appScreen = AppScreen.Operation
                mode = AppMode.Auth
                adminSessionExpiresAt = 0L
                enrollmentExpiresAt = 0L
                authResultHoldUntil = 0L
                pendingDeleteUserIndex = -1
                pendingHeadAdminUserIndex = -1
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
    var testSmartThingsDoorAccess: () -> Unit = {}

    fun completeAdminAction(action: AdminAction) {
        adminSessionExpiresAt = SystemClock.elapsedRealtime() + ADMIN_SESSION_TIMEOUT_MS
        when (action) {
                AdminAction.OpenAdmin -> {
                    appScreen = AppScreen.Admin
                    authResultHoldUntil = 0L
                    status = "관리자 설정"
                    detail = "등록 사용자, SmartThings 도어락, 보안 옵션을 관리할 수 있습니다"
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
                            authResultHoldUntil = 0L
                            guideState = FaceGuideState.Center
                            status = "얼굴을 중앙에 맞춰주세요"
                            detail = "FFacio가 안정적인 얼굴 템플릿을 수집하고 있습니다"
                        }.onFailure {
                            storeError = it
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
                    val persistenceSnapshot = nextUsers.map { it.copyForRuntimeDecision() }
                    appScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        val deleted = try {
                            withContext(Dispatchers.IO) {
                                runCatching { saveUsers(context, prefs, persistenceSnapshot) }
                            }
                        } finally {
                            persistenceSnapshot.wipeTemplates()
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
                    val persistenceSnapshot = nextUsers.map { it.copyForRuntimeDecision() }
                    appScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        val saved = try {
                            withContext(Dispatchers.IO) { runCatching { saveUsers(context, prefs, persistenceSnapshot) } }
                        } finally {
                            persistenceSnapshot.wipeTemplates()
                        }
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
                    val persistenceSnapshot = nextUsers.map { it.copyForRuntimeDecision() }
                    appScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        val saved = try {
                            withContext(Dispatchers.IO) { runCatching { saveUsers(context, prefs, persistenceSnapshot) } }
                        } finally {
                            persistenceSnapshot.wipeTemplates()
                        }
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
                            users.forEach { it.wipe() }
                            users.replaceWith(loaded.users)
                            mode = AppMode.Auth
                            enrollmentExpiresAt = 0L
                            enrollmentName = ""
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
                    detail = "암호화 키와 저장된 SmartThings token을 함께 폐기합니다"
                    appScope.launch {
                        val reset = withContext(Dispatchers.IO) {
                            runCatching {
                                val removed = prefs.edit()
                                    .remove(USERS_KEY)
                                    .remove("$USERS_KEY$SECURE_SUFFIX")
                                    .remove("$USERS_KEY$LEGACY_SECURE_SUFFIX")
                                    .remove("$USERS_KEY$OLDER_SECURE_SUFFIX")
                                    .putInt(USER_STORE_POLICY_KEY, USER_STORE_POLICY_VERSION)
                                    .remove(SMARTTHINGS_DEVICE_ID_KEY)
                                    .remove(SMARTTHINGS_ACCESS_TOKEN_KEY)
                                    .remove(SMARTTHINGS_ENABLED_KEY)
                                    .remove("$SMARTTHINGS_ACCESS_TOKEN_KEY$SECURE_SUFFIX")
                                    .remove("$SMARTTHINGS_ACCESS_TOKEN_KEY$LEGACY_SECURE_SUFFIX")
                                    .remove("$SMARTTHINGS_ACCESS_TOKEN_KEY$OLDER_SECURE_SUFFIX")
                                    .remove(LEGACY_DOOR_URL_KEY)
                                    .remove(LEGACY_DOOR_TOKEN_KEY)
                                    .remove(LEGACY_DOOR_ENABLED_KEY)
                                    .remove("$LEGACY_DOOR_TOKEN_KEY$SECURE_SUFFIX")
                                    .remove("$LEGACY_DOOR_TOKEN_KEY$LEGACY_SECURE_SUFFIX")
                                    .remove("$LEGACY_DOOR_TOKEN_KEY$OLDER_SECURE_SUFFIX")
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
                        confirmDelete = false
                        smartThingsDeviceId = ""
                        smartThingsAccessToken = ""
                        doorConfigError = null
                        doorArmed = false
                        invalidateSmartThingsTest()
                        storeError = null
                        appScreen = AppScreen.Admin
                        status = "로컬 템플릿 저장소 초기화 완료"
                        detail = "기기 안의 얼굴 템플릿을 지웠습니다. 새 사용자를 등록하세요"
                    }
                }
                AdminAction.ArmDoor -> {
                    val deviceId = smartThingsDeviceId.trim()
                    val accessToken = smartThingsAccessToken.trim()
                    storageBusy = true
                    status = "SmartThings 기기 접근과 잠금 상태를 확인하는 중입니다"
                    detail = "검증이 끝나면 access token을 Android Keystore로 암호화해 저장합니다"
                    appScope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            runCatching {
                                if (!passiveLivenessEnabled) error("문 열림에는 Runtime 라이브니스가 반드시 필요합니다")
                                if (deviceId.isEmpty()) error("SmartThings Device ID가 필요합니다")
                                if (accessToken.isEmpty()) error("SmartThings access token이 필요합니다")
                                if (!smartThingsDoorConfigured(deviceId, accessToken)) error("SmartThings Device ID와 access token 형식을 확인해 주세요")
                                val accessCheck = checkSmartThingsDoorAccess(deviceId, accessToken)
                                if (!accessCheck.accepted) error(accessCheck.message)
                                securePutString(context, prefs, SMARTTHINGS_ACCESS_TOKEN_KEY, accessToken)
                                if (!prefs.edit()
                                        .putString(SMARTTHINGS_DEVICE_ID_KEY, deviceId)
                                        .putBoolean(SMARTTHINGS_ENABLED_KEY, true)
                                        .commit()
                                ) {
                                    val rolledBack = clearPersistedSmartThingsCredentials(prefs)
                                    if (!rolledBack) {
                                        error("SmartThings 설정 저장과 보안 롤백이 모두 실패했습니다")
                                    }
                                    error("SmartThings Device ID와 활성화 상태를 저장하지 못했습니다")
                                }
                                accessCheck.message
                            }
                        }
                        if (!active.get()) return@launch
                        storageBusy = false
                        if (saved.isSuccess) {
                            appScreen = AppScreen.Admin
                            smartThingsDeviceId = deviceId
                            smartThingsAccessToken = accessToken
                            doorConfigError = null
                            doorArmed = true
                            status = "SmartThings 도어락 활성화 완료"
                            detail = "${saved.getOrThrow()} · 얼굴 인증·Runtime 라이브니스·전체 템플릿 비교를 통과한 뒤 unlock 명령을 보냅니다"
                        } else {
                            doorArmed = false
                            val rollbackSaved = disableSmartThingsDoorPersisted(prefs)
                            doorConfigError = saved.exceptionOrNull()
                            status = "SmartThings 설정을 저장할 수 없습니다"
                            detail = if (rollbackSaved) {
                                saved.exceptionOrNull()?.message ?: "암호화된 SmartThings access token 저장에 실패했습니다"
                            } else {
                                "SmartThings 설정 저장과 비활성화 상태 저장이 모두 실패했습니다. 앱을 재시작해 설정 상태를 확인해 주세요"
                            }
                        }
                    }
                }
                AdminAction.DisarmDoor -> {
                    appScreen = AppScreen.Admin
                    if (disableSmartThingsDoorPersisted(prefs)) {
                        doorArmed = false
                        invalidateSmartThingsTest()
                        doorConfigError = null
                        status = "SmartThings 도어락 비활성화 완료"
                        detail = "얼굴 인증은 계속 진행하지만 SmartThings unlock 명령은 보내지 않습니다"
                    } else {
                        doorConfigError = IllegalStateException("SmartThings 비활성화 상태를 저장하지 못했습니다")
                        status = "SmartThings 도어락을 끌 수 없습니다"
                        detail = "저장소 업데이트에 실패해 문 열림 설정을 유지했습니다"
                    }
                }
                AdminAction.UnlockSmartThingsToken -> {
                    storageBusy = true
                    status = "SmartThings token을 여는 중입니다"
                    detail = "기기 인증으로 암호화된 토큰을 확인합니다"
                    appScope.launch {
                        val loaded = withContext(Dispatchers.IO) {
                            runCatching { secureGetString(context, prefs, SMARTTHINGS_ACCESS_TOKEN_KEY, "", failClosed = true) }
                        }
                        if (!active.get()) return@launch
                        storageBusy = false
                        if (loaded.isSuccess) {
                            appScreen = AppScreen.Admin
                            smartThingsAccessToken = loaded.getOrDefault("")
                            doorArmed = prefs.getBoolean(SMARTTHINGS_ENABLED_KEY, false) &&
                                passiveLivenessEnabled &&
                                smartThingsDoorConfigured(smartThingsDeviceId, smartThingsAccessToken)
                            if (!doorArmed && prefs.getBoolean(SMARTTHINGS_ENABLED_KEY, false)) {
                                disableSmartThingsDoorPersisted(prefs)
                            }
                            doorConfigError = null
                            status = "SmartThings token 잠금 해제 완료"
                            detail = if (doorArmed) "인증 성공 시 SmartThings lock capability의 unlock 명령을 보냅니다" else "필요하면 SmartThings 도어락 스위치를 다시 활성화하세요"
                        } else {
                            doorArmed = false
                            disableSmartThingsDoorPersisted(prefs)
                            doorConfigError = loaded.exceptionOrNull()
                            status = "SmartThings token을 열 수 없습니다"
                            detail = "토큰을 다시 입력하고 기기 인증 후 활성화하세요"
                        }
                    }
                }
                AdminAction.TestSmartThingsDoor -> {
                    appScreen = AppScreen.Admin
                    testSmartThingsDoorAccess()
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
                        val saved = prefs.edit()
                            .putBoolean(PASSIVE_LIVENESS_ENABLED_KEY, false)
                            .putBoolean(SMARTTHINGS_ENABLED_KEY, false)
                            .commit()
                        passiveLivenessEnabled = false
                        doorArmed = false
                        status = if (saved) "Runtime 라이브니스 꺼짐" else "실제 얼굴 체크 설정을 저장할 수 없습니다"
                        detail = if (saved) {
                            "보안을 위해 SmartThings 문 열림도 함께 비활성화했습니다"
                        } else {
                            "Runtime 라이브니스 설정을 저장하지 못했습니다. 앱을 재시작해 상태를 확인해 주세요"
                        }
                        return
                    }
                    val livenessEditor = prefs.edit()
                        .putBoolean(PASSIVE_LIVENESS_ENABLED_KEY, nextEnabled)
                    if (!nextEnabled) {
                        livenessEditor.putBoolean(SMARTTHINGS_ENABLED_KEY, false)
                    }
                    if (!livenessEditor.commit()) {
                        status = "실제 얼굴 체크 설정을 저장할 수 없습니다"
                        detail = "저장소 업데이트에 실패했습니다. 다시 시도해 주세요"
                        return
                    }
                    passiveLivenessEnabled = nextEnabled
                    if (!nextEnabled) doorArmed = false
                    appScreen = AppScreen.Admin
                    status = if (nextEnabled) "Runtime 라이브니스 켜짐" else "Runtime 라이브니스 꺼짐"
                    detail = if (nextEnabled) {
                        "Runtime 라이브니스와 품질·유사도 기준을 사용합니다"
                    } else {
                        "라이브니스가 꺼져 SmartThings 문 열림도 함께 비활성화했습니다"
                    }
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
        invalidateSmartThingsTest()
        appScreen = AppScreen.Operation
        mode = AppMode.Auth
        adminSessionExpiresAt = 0L
        enrollmentExpiresAt = 0L
        authResultHoldUntil = 0L
        pendingDeleteUserIndex = -1
        pendingHeadAdminUserIndex = -1
        pendingPassiveLivenessEnabled = null
        pendingRuntimeLivenessLevel = null
        pendingOcclusionCheckEnabled = null
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
                    invalidateSmartThingsTest()
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
                    authResultHoldUntil = 0L
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            resetLegacyUserStoreForSingleTemplatePolicy(prefs).fold(
                onSuccess = { loadUsers(context, prefs) },
                onFailure = { StoreLoadResult(emptyList(), it) }
            )
        }
        if (startsWithLegacyUserStore && loaded.error == null) {
            passiveLivenessEnabled = true
            runtimeLivenessLevel = 0
            occlusionCheckEnabled = false
        }
        val tokenLoad = withContext(Dispatchers.IO) {
            runCatching {
                migrateSmartThingsConfiguration(prefs)
                secureGetString(context, prefs, SMARTTHINGS_ACCESS_TOKEN_KEY, "", failClosed = true)
            }
        }
        tokenLoad.onSuccess {
            doorConfigError = null
            smartThingsAccessToken = it
            doorArmed = prefs.getBoolean(SMARTTHINGS_ENABLED_KEY, false) && passiveLivenessEnabled && smartThingsDoorConfigured(smartThingsDeviceId, it)
        }.onFailure {
            doorConfigError = it
            doorArmed = false
            disableSmartThingsDoorPersisted(prefs)
        }
        storeError = loaded.error
        users.replaceWith(loaded.users)
        initialStoreLoaded = true
    }

    LaunchedEffect(modelLoadState, initialStoreLoaded, storeError) {
        runtimeDecisionGeneration.incrementAndGet()
        if (!initialStoreLoaded) {
            status = "로컬 저장소를 확인하고 있습니다"
            detail = "암호화된 사용자와 SmartThings 설정을 불러오는 중입니다"
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

    testSmartThingsDoorAccess = {
        if (canTestSmartThingsDoorConfig(smartThingsDeviceId, smartThingsAccessToken, doorTestInFlight, canMutate = !storageBusy)) {
            val deviceId = smartThingsDeviceId.trim()
            val accessToken = smartThingsAccessToken.trim()
            val requestId = doorTestRequestId + 1L
            doorTestRequestId = requestId
            doorTestInFlight = true
            status = "SmartThings 기기 접근과 잠금 상태를 확인하는 중입니다"
            detail = "이 테스트는 unlock 명령을 보내지 않습니다"
            appScope.launch {
                val checked = withContext(Dispatchers.IO) {
                    runCatching {
                        if (deviceId.isEmpty()) error("SmartThings Device ID가 필요합니다")
                        if (accessToken.isEmpty()) error("SmartThings access token이 필요합니다")
                        val result = checkSmartThingsDoorAccess(deviceId, accessToken)
                        if (!result.accepted) error(result.message)
                        result.message
                    }
                }
                if (!active.get() || requestId != doorTestRequestId || appScreen != AppScreen.Admin) return@launch
                doorTestInFlight = false
                checked.onSuccess { message ->
                    status = "SmartThings 연결 정상"
                    detail = message
                }.onFailure {
                    status = "SmartThings 연결 확인 실패"
                    detail = it.message ?: "Device ID, token 읽기 권한, lock capability를 확인하세요"
                }
            }
        }
    }

    fun resetEnrollmentCapture() {
        enrollmentStability.reset()
        enrollmentHoldProgress = 0.0f
    }

    fun resetTransient(invalidatePendingDecision: Boolean = true) {
        if (invalidatePendingDecision) runtimeDecisionGeneration.incrementAndGet()
        resetEnrollmentCapture()
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
            secondScore = match?.secondScore ?: -1.0
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

    fun persistUsersAsync(
        nextUsers: List<UserTemplate>,
        ownedTemplates: List<ByteArray> = emptyList(),
        onSaved: () -> Unit
    ) {
        storageBusy = true
        status = "얼굴 템플릿 저장 중"
        detail = "암호화된 로컬 저장소에 저장하고 있습니다"
        val persistenceSnapshot = nextUsers.map { it.copyForRuntimeDecision() }
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var adoptedOwnedTemplates = false
            try {
                val saved = withContext(Dispatchers.IO) {
                    runCatching { saveUsers(context, prefs, persistenceSnapshot) }
                }
                if (!active.get()) return@launch
                storageBusy = false
                saved.onSuccess {
                    storeError = null
                    onSaved()
                    adoptedOwnedTemplates = true
                }.onFailure {
                    storeError = it
                    mode = AppMode.Auth
                    enrollmentExpiresAt = 0L
                    enrollmentName = ""
                    resetTransient()
                    status = "로컬 생체 저장소를 사용할 수 없습니다"
                    detail = "암호화된 얼굴 템플릿 저장에 실패해 인증을 차단했습니다"
                }
            } finally {
                persistenceSnapshot.wipeTemplates()
                if (!adoptedOwnedTemplates) ownedTemplates.forEach { it.fill(0) }
            }
        }
    }

    fun requestAdmin(action: AdminAction) {
        if (canAuthorizeAdminActionWithHeadAdminFace(action, users, passiveLivenessEnabled)) {
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
        val headAdminChange = requiresAndroidLockForAdminAction(action, users)
        val livenessFallback = hasHeadAdmin(users) && !passiveLivenessEnabled
        val prompt = keyguard.createConfirmDeviceCredentialIntent(
            when {
                headAdminChange -> "FFacio Head Admin 설정"
                livenessFallback -> "FFacio 관리자 확인"
                else -> "FFacio 초기 관리자 확인"
            },
            when {
                headAdminChange -> "Head Admin 등록/해제를 보호합니다"
                livenessFallback -> "Runtime 라이브니스가 꺼져 Android 화면 잠금으로 관리자 작업을 보호합니다"
                else -> "Head Admin이 없을 때만 초기 설정에 사용합니다"
            }
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
            cameraAnalysisWatchStartedAt = SystemClock.elapsedRealtime()
        }
        while (cameraLifecycleActive && cameraCanAnalyze()) {
            delay(1000L)
            val now = SystemClock.elapsedRealtime()
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
        val deviceId = smartThingsDeviceId.trim()
        val accessToken = smartThingsAccessToken.trim()
        if (!smartThingsDoorConfigured(deviceId, accessToken)) {
            doorArmed = false
            disableSmartThingsDoorPersisted(prefs)
            accessFeedback = AccessFeedback(AccessFeedbackKind.AuthOnly, user.name)
            status = welcomeStatus(user.name)
            detail = "SmartThings Device ID와 access token을 확인해 주세요"
            return
        }
        if (!passiveLivenessEnabled) {
            doorArmed = false
            disableSmartThingsDoorPersisted(prefs)
            accessFeedback = AccessFeedback(AccessFeedbackKind.AuthOnly, user.name)
            status = welcomeStatus(user.name)
            detail = "Runtime 라이브니스가 꺼져 SmartThings unlock을 보내지 않았습니다"
            return
        }
        if (!processDoorRequestGate.tryStart(doorArmed = doorArmed, nowMillis = SystemClock.elapsedRealtime())) return
        accessFeedback = AccessFeedback(AccessFeedbackKind.DoorPending, user.name)
        status = welcomeStatus(user.name)
        detail = "인증 승인 · SmartThings unlock 명령을 전송하고 있습니다"
        try {
            doorExecutor.execute {
                val result = unlockSmartThingsDoor(deviceId, accessToken)
                processDoorRequestGate.finish()
                ContextCompat.getMainExecutor(context).execute {
                    if (!active.get()) return@execute
                    authResultHoldUntil = SystemClock.elapsedRealtime() + AUTH_RESULT_HOLD_MS
                    guideState = if (result.accepted) FaceGuideState.Approved else FaceGuideState.Searching
                    accessFeedback = AccessFeedback(
                        if (result.accepted) AccessFeedbackKind.DoorSucceeded else AccessFeedbackKind.DoorFailed,
                        user.name
                    )
                    recordApproval(user.name, if (result.accepted) "SmartThings unlock 수락" else "SmartThings unlock 실패")
                    status = if (result.accepted) "SmartThings unlock 명령 수락" else "SmartThings 문 제어 실패"
                    detail = result.message
                }
            }
        } catch (_: Throwable) {
            processDoorRequestGate.finish()
            accessFeedback = AccessFeedback(AccessFeedbackKind.DoorFailed, user.name)
            recordApproval(user.name, "SmartThings unlock 실패")
            status = "SmartThings 문 제어 실패"
            detail = "unlock 요청 작업을 시작할 수 없습니다"
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
        if (!authenticationComparisonComplete(match)) {
            resetTransient()
            guideState = FaceGuideState.Rejected
            status = "Runtime 비교 오류"
            detail = "모든 등록 사용자와의 Runtime 비교가 완료되지 않아 보안상 인증을 중단했습니다"
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
                secondScore = match.secondScore
            )
        ) {
            recordAuthDecision(matchedUser.name, "보류", authDecisionReason(match), match)
            resetTransient()
            guideState = FaceGuideState.Rejected
            status = "인증 보류"
            detail = "등록된 얼굴과 충분히 일치하지 않습니다. 정면과 거리를 맞춰 다시 시도해 주세요"
            return
        }
        // Runtime demo authentication policy: passive liveness + quality + similarity.
        // No head-turn challenge; one accepted frame is sufficient.
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
        recordAuthDecision(user.name, "승인", "score/liveness 통과", match)
        val doorConfigured = smartThingsDoorConfigured(smartThingsDeviceId, smartThingsAccessToken)
        if (doorArmed && !doorConfigured) {
            doorArmed = false
            disableSmartThingsDoorPersisted(prefs)
        }
        val shouldOpenDoor = canIssueSmartThingsUnlock(
            doorArmed = doorArmed,
            configured = doorConfigured,
            passiveLivenessEnabled = passiveLivenessEnabled,
            liveState = obs.liveState,
            liveScore = obs.liveScore,
            comparisonComplete = authenticationComparisonComplete(match)
        )
        authResultHoldUntil = SystemClock.elapsedRealtime() + AUTH_RESULT_HOLD_MS
        guideState = if (shouldOpenDoor) FaceGuideState.Center else FaceGuideState.Approved
        accessFeedback = if (shouldOpenDoor) {
            AccessFeedback(AccessFeedbackKind.DoorPending, user.name)
        } else {
            AccessFeedback(AccessFeedbackKind.AuthOnly, user.name)
        }
        if (!shouldOpenDoor) recordApproval(user.name, "승인")
        status = welcomeStatus(user.name)
        detail = when {
            shouldOpenDoor -> "인증 승인 · SmartThings 결과를 기다리고 있습니다"
            !doorConfigured -> "SmartThings Device ID와 access token 설정 후 문 제어를 사용할 수 있습니다"
            !passiveLivenessEnabled || obs.liveState != "runtime_live" -> "라이브니스 확인이 없어 문 열림은 실행하지 않았습니다"
            else -> "인증 승인 · 최근 승인 로그에 기록했습니다"
        }
        resetTransient()
        if (shouldOpenDoor) openDoor(user)
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
        faceBounds = obs.faceBounds ?: if (!obs.ok) null else faceBounds
        if (mode == AppMode.Auth && SystemClock.elapsedRealtime() < authResultHoldUntil) return
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
            val cleanName = normalizeUserName(enrollmentName.ifBlank { name })
            if (!userNameValid(cleanName)) {
                resetEnrollmentCapture()
                guideState = FaceGuideState.Rejected
                status = "등록 이름을 확인해 주세요"
                detail = "이름은 1~${MAX_USER_NAME_LENGTH}자이며 제어 문자를 포함할 수 없습니다"
                return
            }
            if (registeredNameExists(cleanName, users)) {
                resetEnrollmentCapture()
                guideState = FaceGuideState.Rejected
                status = "이미 사용 중인 이름입니다"
                detail = "기존 사용자를 삭제하거나 다른 이름을 입력해 주세요"
                return
            }
            guideState = FaceGuideState.Center
            val enrollmentNow = obs.frameTimestampMillis.takeIf { it > 0L } ?: SystemClock.elapsedRealtime()
            val ready = enrollmentStability.update(obs, enrollmentNow)
            enrollmentHoldProgress = enrollmentStability.progress(enrollmentNow)
            if (!ready) {
                status = "얼굴을 안정적으로 확인 중입니다"
                detail = "정면을 바라보고 잠시 유지해 주세요 · ${(enrollmentHoldProgress * 100).toInt()}% · " +
                    runtimeObservationSummary(obs)
                return
            }
            val enrollmentCapture = enrollmentStability.takeCapture() ?: obs.enrollmentCapture?.copyOwned()
            if (enrollmentCapture == null) {
                resetEnrollmentCapture()
                guideState = FaceGuideState.Rejected
                status = "등록 프레임을 다시 확인해 주세요"
                detail = "최종 등록용 카메라 프레임을 보관하지 못했습니다"
                return
            }
            enrollmentHoldProgress = 0.0f
            val userSnapshot = users.map { it.copyForRuntimeDecision() }
            status = "Runtime 등록 최종 확인 중"
            detail = "데모와 같이 최적 프레임을 다시 검출하고 템플릿을 추출하고 있습니다"
            val started = runRuntimeDecision(
                computation = {
                    var finalization: RuntimeEnrollmentFinalization? = null
                    try {
                        val runtimeEngine = engineProvider()
                            ?: return@runRuntimeDecision EnrollmentRuntimeDecision.Rejected(
                                EnrollmentFailure("FFacio Runtime 연결이 필요합니다", "Runtime 얼굴 엔진을 사용할 수 없습니다")
                            )
                        finalization = runtimeEngine.finalizeEnrollment(
                            capture = enrollmentCapture,
                            passiveLivenessEnabled = passiveLivenessEnabled,
                            livenessLevel = runtimeLivenessLevel,
                            occlusionCheckEnabled = occlusionCheckEnabled
                        )
                        if (!finalization.accepted || !isUsableRuntimeTemplate(finalization.template)) {
                            return@runRuntimeDecision EnrollmentRuntimeDecision.Rejected(
                                EnrollmentFailure(finalization.message, "최종 등록 조건을 다시 확인해 주세요")
                            )
                        }
                        val duplicateSearch = findBestEnrollmentDuplicate(finalization.template, userSnapshot)
                        EnrollmentRuntimeDecision.Ready(
                            name = cleanName,
                            template = finalization.template.copyOf(),
                            duplicateName = duplicateSearch.userName,
                            duplicateScore = duplicateSearch.score,
                            failedComparisons = duplicateSearch.failedComparisons,
                            duplicateCheckComplete = duplicateSearch.comparisonComplete
                        )
                    } finally {
                        finalization?.wipe()
                        enrollmentCapture.wipe()
                        userSnapshot.wipeTemplates()
                    }
                },
                discardResult = { decision -> decision.wipe() },
                applyResult = { result ->
                    result.onFailure { error ->
                        resetEnrollmentCapture()
                        status = "Runtime 비교 오류"
                        detail = runtimeCallFailureMessage(error)
                    }.onSuccess { decision ->
                        when (decision) {
                            is EnrollmentRuntimeDecision.Rejected -> {
                                resetEnrollmentCapture()
                                status = decision.decision.status
                                detail = decision.decision.detail
                            }
                            is EnrollmentRuntimeDecision.Ready -> {
                                when {
                                    !decision.duplicateCheckComplete -> {
                                        decision.template.fill(0)
                                        resetEnrollmentCapture()
                                        guideState = FaceGuideState.Rejected
                                        status = "중복 얼굴 검사를 완료하지 못했습니다"
                                        detail = "기존 사용자 ${decision.failedComparisons}명과의 Runtime 비교가 실패했습니다. 연결을 확인한 뒤 다시 등록해 주세요"
                                    }
                                    decision.duplicateName != null -> {
                                        decision.template.fill(0)
                                        resetEnrollmentCapture()
                                        guideState = FaceGuideState.Rejected
                                        status = "중복 얼굴 등록 차단"
                                        detail = "이미 등록된 ${decision.duplicateName} 사용자와 유사도 %.3f로 확인되어 저장하지 않았습니다".format(
                                            Locale.US, decision.duplicateScore
                                        )
                                    }
                                    registeredNameExists(decision.name, users) -> {
                                        decision.template.fill(0)
                                        resetEnrollmentCapture()
                                        guideState = FaceGuideState.Rejected
                                        status = "이미 사용 중인 이름입니다"
                                        detail = "등록 처리 중 같은 이름의 사용자가 추가되어 저장을 중단했습니다"
                                    }
                                    else -> {
                                        val nextUsers = users.toList() + UserTemplate(
                                            name = decision.name,
                                            template = decision.template,
                                            engineId = FACE_ENGINE_ID,
                                            templateSize = decision.template.size
                                        )
                                        persistUsersAsync(nextUsers, ownedTemplates = listOf(decision.template)) {
                                            users.replaceWith(nextUsers)
                                            mode = AppMode.Auth
                                            enrollmentExpiresAt = 0L
                                            enrollmentName = ""
                                            resetEnrollmentCapture()
                                            resetTransient()
                                            status = "얼굴 등록이 완료되었습니다"
                                            detail = "${decision.name} · 단일 Runtime 템플릿 등록 · 등록 사용자 ${users.size}명 · 중복 검사 통과"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            )
            if (!started) {
                enrollmentCapture.wipe()
                userSnapshot.wipeTemplates()
                resetEnrollmentCapture()
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
        val eligibleCandidateIndices = if (mode == AppMode.AdminAuth) {
            adminAuthCandidateIndices(users)
        } else {
            users.indices.filter { index -> users[index].isCompatible() }
        }
        val candidateIndices = eligibleCandidateIndices
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
                            invalidateSmartThingsTest()
                            appScreen = AppScreen.Operation
                            mode = AppMode.Auth
                            adminSessionExpiresAt = 0L
                            enrollmentExpiresAt = 0L
                            clearAuthResultHold()
                            resetTransient()
                            status = if (users.isEmpty()) "등록된 사용자가 없습니다" else "출입 인증 대기 중"
                            detail = if (users.isEmpty()) "관리자 인증 후 첫 사용자를 등록하세요" else "카메라를 정면으로 바라봐 주세요"
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
                stageMessage = blockedReason() ?: idleReason() ?: status,
                guideState = guideState,
                faceBounds = faceBounds,
                isEnrollmentMode = mode == AppMode.Enroll,
                isAdminAuthMode = mode == AppMode.AdminAuth,
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
                    smartThingsDeviceId = smartThingsDeviceId,
                    smartThingsAccessToken = smartThingsAccessToken,
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
                    canResetStore = storeError != null,
                    canUnlockSmartThingsToken = doorConfigError != null,
                    canRetryBlocked = blockedReason() != null && !modelLoading && modelError == null && !storageBusy,
                    canMutate = !storageBusy,
                    onName = { if (mode != AppMode.Enroll && it.length <= MAX_USER_NAME_LENGTH) name = it },
                    onSmartThingsDeviceId = {
                        if (it.length <= 128) {
                            invalidateSmartThingsTest()
                            smartThingsDeviceId = it
                        }
                    },
                    onSmartThingsAccessToken = {
                        if (it.length <= SMARTTHINGS_MAX_TOKEN_LENGTH) {
                            invalidateSmartThingsTest()
                            smartThingsAccessToken = it
                        }
                    },
                    onDoorArmed = onDoorArmed@{
                        if (doorTestInFlight) {
                            status = "SmartThings 확인 중입니다"
                            detail = "연결 확인이 끝난 뒤 SmartThings 설정을 변경하세요"
                            return@onDoorArmed
                        }
                        if (it && !passiveLivenessEnabled) {
                            doorArmed = false
                            status = "Runtime 라이브니스가 필요합니다"
                            detail = "문 열림 기능은 실제 얼굴 확인이 켜진 상태에서만 활성화할 수 있습니다"
                        } else if (it && smartThingsDeviceId.trim().isEmpty()) {
                            doorArmed = false
                            status = "SmartThings Device ID가 필요합니다"
                            detail = "문 열림을 활성화하려면 SmartThings Device ID를 먼저 입력하세요"
                        } else if (it && smartThingsAccessToken.trim().isEmpty()) {
                            doorArmed = false
                            status = "SmartThings access token이 필요합니다"
                            detail = "문 열림을 활성화하려면 Bearer 토큰을 입력하세요"
                        } else if (it && !smartThingsDoorConfigured(smartThingsDeviceId, smartThingsAccessToken)) {
                            doorArmed = false
                            status = "올바른 SmartThings 설정이 필요합니다"
                            detail = "SmartThings Device ID 형식과 access token을 확인하세요"
                        } else if (it) {
                            performAdminAction(AdminAction.ArmDoor)
                        } else {
                            performAdminAction(AdminAction.DisarmDoor)
                        }
                    },
                    onTestSmartThingsDoor = {
                        performAdminAction(AdminAction.TestSmartThingsDoor)
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
                        val cleanName = normalizeUserName(name)
                        when {
                            !userNameValid(cleanName) -> {
                                status = "등록 이름을 확인하세요"
                                detail = "이름은 1~${MAX_USER_NAME_LENGTH}자이며 제어 문자를 포함할 수 없습니다"
                            }
                            registeredNameExists(cleanName, users) -> {
                                status = "이미 사용 중인 이름입니다"
                                detail = "등록 사용자 이름은 대소문자와 연속 공백을 구분하지 않고 고유해야 합니다"
                            }
                            else -> {
                                name = cleanName
                                performAdminAction(AdminAction.StartEnroll)
                            }
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
                    onUnlockSmartThingsToken = {
                        performAdminAction(AdminAction.UnlockSmartThingsToken)
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
                            performAdminAction(AdminAction.UnlockSmartThingsToken)
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
    val cameraView = remember {
        CameraView(context).apply {
            setBackgroundColor(Color.BLACK)
            contentDescription = "FFacio 얼굴 인식 카메라"
        }
    }
    DisposableEffect(enabled, cameraRetryNonce, isEnrollmentMode) {
        val sessionActive = AtomicBoolean(true)
        var camera: Fotoapparat? = null
        if (enabled) {
            val packageManager = context.packageManager
            val hasAnyCamera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
            if (!hasAnyCamera) {
                onNoCameraHardware()
            } else {
                val frontFacing = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
                camera = runCatching {
                    Fotoapparat.with(context)
                        .into(cameraView)
                        .lensPosition(if (frontFacing) front() else back())
                        .previewScaleType(ScaleType.CenterCrop)
                        .previewResolution {
                            val targetWidth = if (isEnrollmentMode) {
                                DEMO_CAPTURE_FRAME_WIDTH
                            } else {
                                DEMO_IDENTIFICATION_FRAME_WIDTH
                            }
                            val targetHeight = if (isEnrollmentMode) {
                                DEMO_CAPTURE_FRAME_HEIGHT
                            } else {
                                DEMO_IDENTIFICATION_FRAME_HEIGHT
                            }
                            // Prefer the Demo's exact stream, but do not fail on a camera that omits it.
                            firstOrNull { it.width == targetWidth && it.height == targetHeight }
                                ?: minByOrNull { resolution ->
                                    runtimeDemoResolutionCost(
                                        width = resolution.width,
                                        height = resolution.height,
                                        targetWidth = targetWidth,
                                        targetHeight = targetHeight
                                    )
                                }
                                ?: Resolution(targetWidth, targetHeight)
                        }
                        .frameProcessor { frame ->
                            if (!sessionActive.get() || !currentAnalysisEnabled) return@frameProcessor
                            analyzeRuntimeFrame(
                                frame = frame,
                                engineProvider = engineProvider,
                                analyzerExecutor = analyzerExecutor,
                                processing = processing,
                                decisionInFlight = decisionInFlight,
                                firstAnalyzedFrameLogged = firstAnalyzedFrameLogged,
                                lastAnalysisAt = lastAnalysisAt,
                                active = active,
                                sessionActive = sessionActive,
                                context = context,
                                frontFacing = frontFacing,
                                passiveLivenessEnabled = currentPassiveLivenessEnabled,
                                runtimeLivenessLevel = currentRuntimeLivenessLevel,
                                occlusionCheckEnabled = currentOcclusionCheckEnabled,
                                enrollmentMode = currentEnrollmentMode,
                                onObservation = onObservation
                            )
                        }
                        .cameraErrorCallback { error ->
                            Log.e("FFacio", "Runtime Demo camera pipeline error: ${error.javaClass.simpleName}: ${error.message.orEmpty()}")
                            ContextCompat.getMainExecutor(context).execute {
                                if (sessionActive.get() && active.get()) onCameraUnavailable()
                            }
                        }
                        .build()
                        .also { it.start() }
                }.onFailure { error ->
                    Log.e("FFacio", "Could not start Runtime Demo camera pipeline", error)
                    ContextCompat.getMainExecutor(context).execute {
                        if (sessionActive.get() && active.get()) onCameraUnavailable()
                    }
                }.getOrNull()
            }
        }
        onDispose {
            sessionActive.set(false)
            lastAnalysisAt.set(0L)
            runCatching { camera?.stop() }
            camera = null
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(34.dp))
            .background(ComposeColor.Black)
            .border(1.dp, ComposeColor.White.copy(alpha = 0.16f), RoundedCornerShape(34.dp))
    ) {
        val enrollmentProgress = enrollmentHoldProgress.takeIf { enabled && isEnrollmentMode }
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
            AndroidView(factory = { cameraView }, modifier = Modifier.fillMaxSize())
            TrackedFaceBoxOverlay(
                bounds = faceBounds,
                state = guideState,
                containerWidth = containerWidthPx,
                containerHeight = containerHeightPx,
                modifier = Modifier.fillMaxSize()
            )
            FaceGuideOverlay(
                state = guideState,
                enrollmentProgress = enrollmentProgress,
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
            Surface(
                color = ComposeColor.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
            ) {
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
private fun TrackedFaceBoxOverlay(
    bounds: FaceBounds?,
    state: FaceGuideState,
    containerWidth: Float,
    containerHeight: Float,
    modifier: Modifier = Modifier
) {
    val target = faceRectInPreview(bounds, containerWidth, containerHeight)
    val targetLeft = target?.left ?: 0.0f
    val targetTop = target?.top ?: 0.0f
    val targetRight = target?.right ?: 0.0f
    val targetBottom = target?.bottom ?: 0.0f
    val left by animateFloatAsState(
        targetValue = targetLeft,
        animationSpec = tween(durationMillis = 90),
        label = "tracked-face-left"
    )
    val top by animateFloatAsState(
        targetValue = targetTop,
        animationSpec = tween(durationMillis = 90),
        label = "tracked-face-top"
    )
    val right by animateFloatAsState(
        targetValue = targetRight,
        animationSpec = tween(durationMillis = 90),
        label = "tracked-face-right"
    )
    val bottom by animateFloatAsState(
        targetValue = targetBottom,
        animationSpec = tween(durationMillis = 90),
        label = "tracked-face-bottom"
    )
    val targetColor = when (state) {
        FaceGuideState.Approved -> ComposeColor(0xFF30D158)
        FaceGuideState.Rejected -> ComposeColor(0xFFFF453A)
        else -> ComposeColor(0xFF64D2FF)
    }
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 120),
        label = "tracked-face-color"
    )
    if (target != null) {
        Canvas(modifier) {
            val width = (right - left).coerceAtLeast(0.0f)
            val height = (bottom - top).coerceAtLeast(0.0f)
            if (width <= 1.0f || height <= 1.0f) return@Canvas
            val strokeWidth = 3.dp.toPx()
            val radius = min(width, height) * 0.12f
            drawRoundRect(
                color = color.copy(alpha = 0.08f),
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(radius, radius)
            )
            drawRoundRect(
                color = color.copy(alpha = 0.95f),
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = strokeWidth)
            )
        }
    }
}

@Composable
private fun FaceGuideOverlay(
    state: FaceGuideState,
    enrollmentProgress: Float? = null,
    isAdminAuthMode: Boolean = false,
    ringSize: Dp = 260.dp,
    modifier: Modifier = Modifier
) {
    val targetRingColor = when {
        state == FaceGuideState.Approved -> ComposeColor(0xFF30D158)
        state == FaceGuideState.Rejected -> ComposeColor(0xFFFF453A)
        enrollmentProgress != null -> ComposeColor(0xFF30D158)
        isAdminAuthMode -> ComposeColor(0xFF0A84FF)
        state == FaceGuideState.Center -> ComposeColor.White
        else -> ComposeColor(0x99FFFFFF)
    }
    val ringColor by animateColorAsState(
        targetValue = targetRingColor,
        animationSpec = tween(durationMillis = 220),
        label = "face-guide-color"
    )
    val progress by animateFloatAsState(
        targetValue = enrollmentProgress ?: when (state) {
            FaceGuideState.Searching -> 0.16f
            FaceGuideState.Center -> 0.34f
            FaceGuideState.Rejected -> 0.66f
            FaceGuideState.Approved -> 1.0f
        },
        animationSpec = tween(durationMillis = 260),
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
            drawArc(
                color = ringColor.copy(alpha = 0.94f),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = activeStroke
            )
        }
        Box(
            modifier = Modifier
                .size(ringSize - 28.dp)
                .clip(CircleShape)
                .border(1.dp, ComposeColor.White.copy(alpha = 0.18f), CircleShape)
                .background(ComposeColor.Transparent)
        )
        when (state) {
            FaceGuideState.Approved -> GuideBadge("✓", ComposeColor(0xFF30D158), Modifier.align(Alignment.BottomCenter))
            FaceGuideState.Rejected -> GuideBadge("!", ComposeColor(0xFFFF453A), Modifier.align(Alignment.TopCenter))
            FaceGuideState.Center -> Text("•", color = ComposeColor.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
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
    smartThingsDeviceId: String,
    smartThingsAccessToken: String,
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
    canResetStore: Boolean,
    canUnlockSmartThingsToken: Boolean,
    canRetryBlocked: Boolean,
    canMutate: Boolean,
    onName: (String) -> Unit,
    onSmartThingsDeviceId: (String) -> Unit,
    onSmartThingsAccessToken: (String) -> Unit,
    onDoorArmed: (Boolean) -> Unit,
    onTestSmartThingsDoor: () -> Unit,
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
    onUnlockSmartThingsToken: () -> Unit,
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
                Text("단일 Runtime 템플릿 등록 중", color = ComposeColor(0xFF1D1D1F), fontWeight = FontWeight.SemiBold)
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
            if (blockedReason != null || canUnlockSmartThingsToken) {
                Surface(color = ComposeColor(0xFFFFF4E5), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(blockedReason ?: "SmartThings token을 다시 확인하세요", color = ComposeColor(0xFF8A4B00), fontWeight = FontWeight.Bold)
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
                        if (canUnlockSmartThingsToken) {
                            Button(
                                onClick = onUnlockSmartThingsToken,
                                enabled = canMutate,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF0071E3))
                            ) { Text("SmartThings token 다시 열기") }
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
                    Text("라이브니스가 꺼진 동안 얼굴 인증은 가능하지만 SmartThings 문 열림은 보안상 비활성화됩니다", color = ComposeColor(0xFFD70015), fontSize = 13.sp)
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
                    Text("인증 성공 시 SmartThings 도어락 해제", color = ComposeColor(0xFF1D1D1F))
                }
            if (doorArmed && smartThingsDeviceId.trim().isEmpty()) {
                Text("SmartThings Device ID를 입력해야 문 열림이 활성화됩니다", color = ComposeColor(0xFFFF3B30), fontSize = 13.sp)
            }
            OutlinedTextField(
                value = smartThingsDeviceId,
                onValueChange = onSmartThingsDeviceId,
                enabled = canMutate && !doorArmed && !doorTestInFlight,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                label = { Text("SmartThings Device ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = smartThingsAccessToken,
                onValueChange = onSmartThingsAccessToken,
                enabled = canMutate && !doorArmed && !doorTestInFlight,
                label = { Text("SmartThings access token") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = onTestSmartThingsDoor,
                enabled = canTestSmartThingsDoorConfig(smartThingsDeviceId, smartThingsAccessToken, doorTestInFlight, canMutate),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF1D1D1F))
            ) {
                Text(if (doorTestInFlight) "SmartThings 확인 중" else "SmartThings 기기·상태 확인")
            }
            Text(
                "테스트는 unlock 명령을 보내지 않고, 지정한 기기의 lock capability 상태만 조회합니다. 토큰이 만료되면 새 토큰으로 갱신해야 합니다.",
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

private data class RuntimeFrameSnapshot(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val capturedAtMillis: Long,
)

/**
 * Runtime Demo-compatible frame handoff.
 *
 * Fotoapparat already supplies tightly packed NV21. We intentionally do not rebuild chroma
 * planes, crop, or reinterpret row/pixel strides here: doing so was the source of incorrect
 * eye, pose and landmark values in the previous CameraX pipeline.
 */
private fun analyzeRuntimeFrame(
    frame: Frame,
    engineProvider: () -> MobileFaceEngine?,
    analyzerExecutor: ExecutorService,
    processing: AtomicBoolean,
    decisionInFlight: AtomicBoolean,
    firstAnalyzedFrameLogged: AtomicBoolean,
    lastAnalysisAt: AtomicLong,
    active: AtomicBoolean,
    sessionActive: AtomicBoolean,
    context: Context,
    frontFacing: Boolean,
    passiveLivenessEnabled: Boolean,
    runtimeLivenessLevel: Int,
    occlusionCheckEnabled: Boolean,
    enrollmentMode: Boolean,
    onObservation: (Observation) -> Unit
) {
    if (!active.get() || !sessionActive.get() || decisionInFlight.get()) return
    val now = SystemClock.elapsedRealtime()
    val previous = lastAnalysisAt.get()
    if (now - previous < ANALYSIS_INTERVAL_MS || !lastAnalysisAt.compareAndSet(previous, now)) return
    if (!processing.compareAndSet(false, true)) return

    val width = frame.size.width
    val height = frame.size.height
    val frameBytes = frame.image.clone()
    val expected = width.toLong() * height.toLong() * 3L / 2L
    if (width <= 0 || height <= 0 || (width and 1) != 0 || (height and 1) != 0 ||
        expected > Int.MAX_VALUE || frameBytes.size != expected.toInt()
    ) {
        frameBytes.fill(0)
        processing.set(false)
        ContextCompat.getMainExecutor(context).execute {
            if (active.get() && sessionActive.get()) {
                onObservation(Observation.fail("카메라 프레임 형식이 Runtime Demo와 일치하지 않습니다"))
            }
        }
        return
    }

    val snapshot = RuntimeFrameSnapshot(
        bytes = frameBytes,
        width = width,
        height = height,
        rotationDegrees = frame.rotation,
        capturedAtMillis = now,
    )
    try {
        analyzerExecutor.execute {
            try {
                if (!active.get() || !sessionActive.get()) return@execute
                val engine = engineProvider() ?: return@execute
                if (firstAnalyzedFrameLogged.compareAndSet(false, true)) {
                    Log.i(
                        "FFacio",
                        "Runtime Demo camera frame received: ${snapshot.width}x${snapshot.height}, rotation=${snapshot.rotationDegrees}"
                    )
                }
                val observation = engine.observe(
                    frame = snapshot,
                    frontFacing = frontFacing,
                    passiveLivenessEnabled = passiveLivenessEnabled,
                    enrollmentMode = enrollmentMode,
                    livenessLevel = runtimeLivenessLevel,
                    occlusionCheckEnabled = occlusionCheckEnabled
                )
                ContextCompat.getMainExecutor(context).execute {
                    try {
                        if (active.get() && sessionActive.get()) onObservation(observation)
                    } finally {
                        observation.template.fill(0)
                        observation.enrollmentCapture?.wipe()
                    }
                }
            } catch (error: Throwable) {
                Log.e("FFacio", "Runtime Demo camera frame analysis failed", error)
                ContextCompat.getMainExecutor(context).execute {
                    if (active.get() && sessionActive.get()) {
                        onObservation(Observation.fail(runtimeCallFailureMessage(error)))
                    }
                }
            } finally {
                snapshot.bytes.fill(0)
                processing.set(false)
            }
        }
    } catch (error: Throwable) {
        snapshot.bytes.fill(0)
        processing.set(false)
        Log.e("FFacio", "Runtime camera analysis executor rejected a frame", error)
        ContextCompat.getMainExecutor(context).execute {
            if (active.get() && sessionActive.get()) {
                onObservation(Observation.fail("카메라 분석 작업을 시작할 수 없습니다"))
            }
        }
    }
}

private class MobileFaceEngine {
    fun hasPassiveLiveness(): Boolean = true

    fun observe(
        frame: RuntimeFrameSnapshot,
        frontFacing: Boolean,
        passiveLivenessEnabled: Boolean,
        enrollmentMode: Boolean,
        livenessLevel: Int = 0,
        occlusionCheckEnabled: Boolean = false
    ): Observation {
        if (!FaceSDK.isReady()) return Observation.fail("FFacio Runtime 연결이 준비되지 않았습니다")
        var bitmap: Bitmap? = null
        try {
            val convertStartedAt = SystemClock.elapsedRealtime()
            bitmap = FaceSDK.yuv2Bitmap(
                frame.bytes,
                frame.width,
                frame.height,
                runtimeNativeOrientation(frame.rotationDegrees, frontFacing)
            ) ?: return Observation.fail("Runtime이 카메라 이미지를 변환하지 못했습니다")
            val convertMillis = SystemClock.elapsedRealtime() - convertStartedAt

            // Exact Runtime Demo option set: liveness + eye + optional occlusion + mouth, no age/gender.
            val options = runtimeDetectionOptions(
                passiveLivenessEnabled = passiveLivenessEnabled,
                livenessLevel = livenessLevel,
                occlusionCheckEnabled = occlusionCheckEnabled
            )
            val detectStartedAt = SystemClock.elapsedRealtime()
            val faces = FaceSDK.faceDetection(bitmap, options).orEmpty()
            val detectMillis = SystemClock.elapsedRealtime() - detectStartedAt
            if (faces.isEmpty()) return Observation.fail("얼굴을 찾을 수 없습니다")

            // User-requested exception to Demo single-face rejection: process only the largest face.
            val face = largestRuntimeFace(faces)
                ?: return Observation.fail("얼굴을 찾을 수 없습니다")
            val bounds = faceBoundsFromRuntime(face, bitmap.width, bitmap.height)
                ?: return Observation.fail("Runtime 얼굴 좌표가 올바르지 않습니다")
            val policyDecision = evaluateRuntimeDemoFace(
                face = face,
                frameWidth = bitmap.width,
                frameHeight = bitmap.height,
                enrollmentMode = enrollmentMode,
                passiveLivenessEnabled = passiveLivenessEnabled,
                occlusionCheckEnabled = occlusionCheckEnabled
            )
            if (!policyDecision.accepted) {
                return Observation.fail(
                    message = policyDecision.message,
                    liveScore = policyDecision.liveScore,
                    liveState = policyDecision.liveState,
                    faceBounds = bounds
                )
            }

            if (enrollmentMode) {
                // Runtime Demo capture parity: keep the best original NV21 frame for 1.2 seconds,
                // then convert, detect, inspect and extract once more during finalization.
                return Observation(
                    ok = true,
                    message = "확인 중",
                    template = ByteArray(0),
                    liveScore = if (passiveLivenessEnabled) face.liveness else 1f,
                    liveState = if (passiveLivenessEnabled) "runtime_live" else "disabled",
                    faceBounds = bounds,
                    quality = face.face_quality,
                    luminance = face.face_luminance,
                    yaw = face.yaw,
                    pitch = face.pitch,
                    roll = face.roll,
                    frameTimestampMillis = frame.capturedAtMillis,
                    enrollmentCapture = RuntimeEnrollmentCapture(
                        bytes = frame.bytes.copyOf(),
                        width = frame.width,
                        height = frame.height,
                        rotationDegrees = frame.rotationDegrees,
                        frontFacing = frontFacing,
                        quality = face.face_quality,
                        capturedAtMillis = frame.capturedAtMillis
                    ),
                    timings = RuntimeCallTimings(convertMillis, detectMillis, 0L)
                )
            }

            val templateStartedAt = SystemClock.elapsedRealtime()
            val template = FaceSDK.templateExtraction(bitmap, face)
            val templateMillis = SystemClock.elapsedRealtime() - templateStartedAt
            if (!isUsableRuntimeTemplate(template)) {
                template?.fill(0)
                return Observation.fail("Runtime 얼굴 템플릿을 추출할 수 없습니다", faceBounds = bounds)
            }
            return Observation(
                ok = true,
                message = "확인 중",
                template = template,
                liveScore = if (passiveLivenessEnabled) face.liveness else 1f,
                liveState = if (passiveLivenessEnabled) "runtime_live" else "disabled",
                faceBounds = bounds,
                quality = face.face_quality,
                luminance = face.face_luminance,
                yaw = face.yaw,
                pitch = face.pitch,
                roll = face.roll,
                frameTimestampMillis = frame.capturedAtMillis,
                timings = RuntimeCallTimings(convertMillis, detectMillis, templateMillis)
            )
        } catch (error: Throwable) {
            Log.e("FFacio", "FFacio Runtime observation failed", error)
            return Observation.fail(runtimeCallFailureMessage(error))
        } finally {
            bitmap?.recycle()
        }
    }

    fun finalizeEnrollment(
        capture: RuntimeEnrollmentCapture,
        passiveLivenessEnabled: Boolean,
        livenessLevel: Int,
        occlusionCheckEnabled: Boolean
    ): RuntimeEnrollmentFinalization {
        if (!FaceSDK.isReady()) {
            return RuntimeEnrollmentFinalization.rejected("FFacio Runtime 연결이 준비되지 않았습니다")
        }
        var bitmap: Bitmap? = null
        return try {
            bitmap = FaceSDK.yuv2Bitmap(
                capture.bytes,
                capture.width,
                capture.height,
                runtimeNativeOrientation(capture.rotationDegrees, capture.frontFacing)
            ) ?: return RuntimeEnrollmentFinalization.rejected("Runtime이 등록 이미지를 변환하지 못했습니다")
            val faces = FaceSDK.faceDetection(
                bitmap,
                runtimeDetectionOptions(
                    passiveLivenessEnabled = passiveLivenessEnabled,
                    livenessLevel = livenessLevel,
                    occlusionCheckEnabled = occlusionCheckEnabled
                )
            ).orEmpty()
            val face = largestRuntimeFace(faces)
                ?: return RuntimeEnrollmentFinalization.rejected("얼굴을 찾을 수 없습니다")
            val decision = evaluateRuntimeDemoFace(
                face = face,
                frameWidth = bitmap.width,
                frameHeight = bitmap.height,
                enrollmentMode = true,
                passiveLivenessEnabled = passiveLivenessEnabled,
                occlusionCheckEnabled = occlusionCheckEnabled
            )
            if (!decision.accepted) {
                return RuntimeEnrollmentFinalization.rejected(decision.message)
            }
            val template = FaceSDK.templateExtraction(bitmap, face)
            if (!isUsableRuntimeTemplate(template)) {
                template?.fill(0)
                RuntimeEnrollmentFinalization.rejected("Runtime 얼굴 템플릿을 추출할 수 없습니다")
            } else {
                RuntimeEnrollmentFinalization(
                    accepted = true,
                    message = "등록 준비 완료",
                    template = template,
                    quality = face.face_quality
                )
            }
        } catch (error: Throwable) {
            Log.e("FFacio", "Runtime Demo enrollment finalization failed", error)
            RuntimeEnrollmentFinalization.rejected(runtimeCallFailureMessage(error))
        } finally {
            bitmap?.recycle()
        }
    }

    fun close() = Unit
}

internal data class RuntimeEnrollmentCapture(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val frontFacing: Boolean,
    val quality: Float,
    val capturedAtMillis: Long
) {
    fun copyOwned(): RuntimeEnrollmentCapture = copy(bytes = bytes.copyOf())
    fun wipe() = bytes.fill(0)
}

internal data class RuntimeEnrollmentFinalization(
    val accepted: Boolean,
    val message: String,
    val template: ByteArray,
    val quality: Float = 0.0f
) {
    fun wipe() = template.fill(0)

    companion object {
        fun rejected(message: String) = RuntimeEnrollmentFinalization(
            accepted = false,
            message = message,
            template = ByteArray(0)
        )
    }
}

internal class EnrollmentStabilityTracker(private val stableMillis: Long) {
    private var validSince = 0L
    private var bestQuality = Float.NEGATIVE_INFINITY
    private var bestCapture: RuntimeEnrollmentCapture? = null

    fun update(observation: Observation, now: Long): Boolean {
        val capture = observation.enrollmentCapture
        if (!observation.ok || capture == null || capture.bytes.isEmpty()) {
            reset()
            return false
        }
        if (validSince == 0L || now < validSince) {
            reset()
            validSince = now
        }
        if (observation.quality.isFinite() && observation.quality >= bestQuality) {
            bestCapture?.wipe()
            bestCapture = capture.copyOwned()
            bestQuality = observation.quality
        }
        return now - validSince >= stableMillis
    }

    fun progress(now: Long): Float {
        if (validSince == 0L || now < validSince) return 0.0f
        return ((now - validSince).toFloat() / stableMillis.coerceAtLeast(1L).toFloat()).coerceIn(0.0f, 1.0f)
    }

    fun takeCapture(): RuntimeEnrollmentCapture? {
        val result = bestCapture
        bestCapture = null
        validSince = 0L
        bestQuality = Float.NEGATIVE_INFINITY
        return result
    }

    fun reset() {
        bestCapture?.wipe()
        bestCapture = null
        validSince = 0L
        bestQuality = Float.NEGATIVE_INFINITY
    }
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

internal fun runtimeSimilarityScoreValid(value: Double): Boolean =
    value.isFinite() && value in 0.0..1.0

private fun runtimeObservationSummary(obs: Observation): String =
    "품질 %.2f · 밝기 %.0f · 자세 %.0f/%.0f/%.0f".format(
        Locale.US,
        obs.quality,
        obs.luminance,
        obs.yaw,
        obs.pitch,
        obs.roll
    )

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
    UnlockSmartThingsToken,
    TestSmartThingsDoor,
    SetPassiveLiveness,
    SetRuntimeLivenessLevel,
    SetOcclusionCheck,
    SetHeadAdmin,
    ClearHeadAdmin
}
internal enum class AdminAuthDecision { Approved, Rejected, Expired }
private enum class FaceGuideState { Searching, Center, Approved, Rejected }
internal enum class AccessFeedbackKind { AuthOnly, DoorPending, DoorSucceeded, DoorFailed }
private sealed class ModelLoadState {
    data object Loading : ModelLoadState()
    data object Ready : ModelLoadState()
    data class Failed(val error: Throwable) : ModelLoadState()
}

internal data class AccessFeedback(val kind: AccessFeedbackKind, val userName: String)

internal data class Observation(
    val ok: Boolean,
    val message: String,
    val template: ByteArray,
    val liveScore: Float = 0.0f,
    val liveState: String = "unknown",
    val faceBounds: FaceBounds? = null,
    val quality: Float = 0.0f,
    val luminance: Float = 0.0f,
    val yaw: Float = 0.0f,
    val pitch: Float = 0.0f,
    val roll: Float = 0.0f,
    val frameTimestampMillis: Long = 0L,
    val enrollmentCapture: RuntimeEnrollmentCapture? = null,
    val timings: RuntimeCallTimings? = null
) {
    companion object {
        fun fail(message: String, liveScore: Float = 0.0f, liveState: String = "unknown", faceBounds: FaceBounds? = null) =
            Observation(false, message, ByteArray(0), liveScore, liveState, faceBounds)
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
internal data class PreviewFaceRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = (right - left).coerceAtLeast(0.0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.0f)
}
internal data class UserTemplate(
    val name: String,
    val template: ByteArray,
    val engineId: String = FACE_ENGINE_ID,
    val templateSize: Int = template.size,
    val isHeadAdmin: Boolean = false
) {
    fun isCompatible(): Boolean =
        engineId == FACE_ENGINE_ID && isUsableRuntimeTemplate(template) && templateSize == template.size
}
internal data class Match(
    val index: Int,
    val score: Double,
    val secondScore: Double,
    val successfulComparisons: Int = 0,
    val failedComparisons: Int = 0
)

private sealed interface EnrollmentRuntimeDecision {
    data class Rejected(val decision: EnrollmentFailure) : EnrollmentRuntimeDecision
    data class Ready(
        val name: String,
        val template: ByteArray,
        val duplicateName: String? = null,
        val duplicateScore: Double = -1.0,
        val failedComparisons: Int = 0,
        val duplicateCheckComplete: Boolean = true
    ) : EnrollmentRuntimeDecision
}

private data class AuthenticationRuntimeDecision(
    val candidateIndices: List<Int>,
    val match: Match
)

private fun EnrollmentRuntimeDecision.wipe() {
    if (this is EnrollmentRuntimeDecision.Ready) template.fill(0)
}

internal data class EnrollmentFailure(val status: String, val detail: String)
internal data class ApprovalLogEntry(val time: String, val userName: String, val result: String)
internal data class AuthDecisionLogEntry(
    val time: String,
    val userName: String,
    val result: String,
    val reason: String,
    val score: Double,
    val secondScore: Double
)
private data class StoreLoadResult(val users: List<UserTemplate>, val error: Throwable?)

private fun MutableList<UserTemplate>.replaceWith(items: List<UserTemplate>) {
    clear()
    addAll(items)
}

private fun UserTemplate.wipe() {
    template.fill(0)
}


internal fun normalizeUserName(raw: String): String =
    raw.trim().replace(Regex("\\s+"), " ")

internal fun userNameValid(raw: String): Boolean {
    val normalized = normalizeUserName(raw)
    return normalized.length in 1..MAX_USER_NAME_LENGTH && normalized.none { it.isISOControl() }
}

internal fun registeredNameExists(name: String, users: List<UserTemplate>): Boolean {
    val key = normalizeUserName(name).lowercase(Locale.ROOT)
    return key.isNotEmpty() && users.any { normalizeUserName(it.name).lowercase(Locale.ROOT) == key }
}

internal fun UserTemplate.copyForRuntimeDecision(): UserTemplate = copy(
    template = template.copyOf()
)

internal fun List<UserTemplate>.wipeTemplates() {
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

internal fun canAuthorizeAdminActionWithHeadAdminFace(
    action: AdminAction,
    users: List<UserTemplate>,
    passiveLivenessEnabled: Boolean = true
): Boolean = passiveLivenessEnabled &&
    hasHeadAdmin(users) &&
    !requiresAndroidLockForAdminAction(action, users)

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

internal fun faceRectInPreview(
    bounds: FaceBounds?,
    containerWidth: Float,
    containerHeight: Float
): PreviewFaceRect? {
    if (bounds == null || containerWidth <= 0.0f || containerHeight <= 0.0f ||
        bounds.frameWidth <= 0.0f || bounds.frameHeight <= 0.0f ||
        bounds.width <= 0.0f || bounds.height <= 0.0f
    ) return null
    val scale = max(containerWidth / bounds.frameWidth, containerHeight / bounds.frameHeight)
    val drawnWidth = bounds.frameWidth * scale
    val drawnHeight = bounds.frameHeight * scale
    val offsetX = (containerWidth - drawnWidth) / 2.0f
    val offsetY = (containerHeight - drawnHeight) / 2.0f
    val left = bounds.left * scale + offsetX
    val top = bounds.top * scale + offsetY
    val right = (bounds.left + bounds.width) * scale + offsetX
    val bottom = (bounds.top + bounds.height) * scale + offsetY
    val clippedLeft = left.coerceIn(0.0f, containerWidth)
    val clippedTop = top.coerceIn(0.0f, containerHeight)
    val clippedRight = right.coerceIn(0.0f, containerWidth)
    val clippedBottom = bottom.coerceIn(0.0f, containerHeight)
    if (clippedRight <= clippedLeft || clippedBottom <= clippedTop) return null
    return PreviewFaceRect(clippedLeft, clippedTop, clippedRight, clippedBottom)
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

internal fun canTestSmartThingsDoorConfig(
    deviceId: String,
    accessToken: String,
    inFlight: Boolean,
    canMutate: Boolean
): Boolean =
    canMutate &&
        !inFlight &&
        smartThingsDoorConfigured(deviceId, accessToken)

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
    val clearAccessFeedback: Boolean = true
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
    val authResultHoldUntil: Long = 1L,
    val hasAccessFeedback: Boolean = true
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
    authResultHoldUntil = if (plan.clearAuthHold) 0L else state.authResultHoldUntil,
    hasAccessFeedback = if (plan.clearAccessFeedback) false else state.hasAccessFeedback
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

internal fun needsUserStorePolicyReset(
    storedVersion: Int,
    currentVersion: Int = USER_STORE_POLICY_VERSION
): Boolean = storedVersion != currentVersion

private fun resetLegacyUserStoreForSingleTemplatePolicy(prefs: SharedPreferences): Result<Unit> = runCatching {
    val storedVersion = prefs.getInt(USER_STORE_POLICY_KEY, 0)
    if (!needsUserStorePolicyReset(storedVersion)) return@runCatching
    val saved = prefs.edit()
        .remove(USERS_KEY)
        .remove("$USERS_KEY$SECURE_SUFFIX")
        .remove("$USERS_KEY$LEGACY_SECURE_SUFFIX")
        .remove("$USERS_KEY$OLDER_SECURE_SUFFIX")
        .putBoolean(PASSIVE_LIVENESS_ENABLED_KEY, true)
        .putInt(RUNTIME_LIVENESS_LEVEL_KEY, 0)
        .putBoolean(OCCLUSION_CHECK_ENABLED_KEY, false)
        .putInt(USER_STORE_POLICY_KEY, USER_STORE_POLICY_VERSION)
        .commit()
    check(saved) { "Single-template user store reset could not be saved" }
}

private fun loadUsers(context: Context, prefs: SharedPreferences): StoreLoadResult {
    val loaded = mutableListOf<UserTemplate>()
    val loadedNames = mutableSetOf<String>()
    return runCatching {
        val raw = secureGetString(context, prefs, USERS_KEY, "[]", failClosed = true)
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = normalizeUserName(item.optString("name"))
            if (!userNameValid(name)) continue
            val schemaVersion = item.optInt("schema_version", 0)
            val storedEngineId = item.optString("engine_id", "legacy.unknown")
            val template = decodeTemplate(item.optString("template_b64", ""))
            val declaredSize = item.optInt("template_size", -1)
            val compatible = schemaVersion == USER_STORE_SCHEMA_VERSION &&
                storedEngineId == FACE_ENGINE_ID &&
                isUsableRuntimeTemplate(template) &&
                declaredSize == template.size
            val normalizedNameKey = name.lowercase(Locale.ROOT)
            if (compatible && normalizedNameKey !in loadedNames) {
                loadedNames += normalizedNameKey
                loaded += UserTemplate(
                    name = name,
                    template = template,
                    engineId = FACE_ENGINE_ID,
                    templateSize = template.size,
                    isHeadAdmin = item.optBoolean("head_admin", false)
                )
            } else {
                template.fill(0)
            }
        }
        StoreLoadResult(normalizeHeadAdminUsers(loaded), null)
    }.getOrElse { error ->
        loaded.wipeTemplates()
        StoreLoadResult(emptyList(), error)
    }
}

private fun saveUsers(context: Context, prefs: SharedPreferences, users: List<UserTemplate>) {
    val array = JSONArray()
    val savedNames = mutableSetOf<String>()
    users.forEach { user ->
        val cleanName = normalizeUserName(user.name)
        require(userNameValid(cleanName)) { "User name is invalid" }
        require(savedNames.add(cleanName.lowercase(Locale.ROOT))) { "Duplicate user name" }
        require(user.isCompatible()) { "Runtime user template is incomplete or inconsistent" }
        val item = JSONObject()
        item.put("schema_version", USER_STORE_SCHEMA_VERSION)
        item.put("name", cleanName)
        item.put("engine_id", FACE_ENGINE_ID)
        item.put("template_size", user.templateSize)
        item.put("head_admin", user.isHeadAdmin)
        item.put("template_b64", Base64.encodeToString(user.template, Base64.NO_WRAP))
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
            try {
                decryptPayload(
                    context = context,
                    payload = payload,
                    alias = KEYSTORE_ALIAS,
                    authRequired = false,
                    associatedData = key.toByteArray(Charsets.UTF_8)
                )
            } finally {
                payload.fill(0)
            }
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
            try {
                decryptPayload(context, payload, legacyAlias, authRequired = true)
            } finally {
                payload.fill(0)
            }
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
    val associatedData = key.toByteArray(Charsets.UTF_8)
    val plainText = value.toByteArray(Charsets.UTF_8)
    val iv = cipher.iv ?: error("Android Keystore did not provide an AES-GCM IV")
    val cipherText = try {
        cipher.updateAAD(associatedData)
        cipher.doFinal(plainText)
    } finally {
        associatedData.fill(0)
        plainText.fill(0)
    }
    val payload = iv + cipherText
    try {
        prefs.edit()
            .putString("$key$SECURE_SUFFIX", Base64.encodeToString(payload, Base64.NO_WRAP))
            .remove("$key$LEGACY_SECURE_SUFFIX")
            .remove("$key$OLDER_SECURE_SUFFIX")
            .remove(key)
            .commit()
            .also { if (!it) error("Encrypted local store could not be saved") }
    } finally {
        cipherText.fill(0)
        payload.fill(0)
    }
}

private fun decryptPayload(
    context: Context,
    payload: ByteArray,
    alias: String,
    authRequired: Boolean,
    associatedData: ByteArray? = null
): String {
    require(payload.size > 28) { "Encrypted local store payload is truncated" }
    val iv = payload.copyOfRange(0, 12)
    val cipherText = payload.copyOfRange(12, payload.size)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    return try {
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(context, alias, authRequired), GCMParameterSpec(128, iv))
        if (associatedData != null) cipher.updateAAD(associatedData)
        val plainText = cipher.doFinal(cipherText)
        try {
            String(plainText, Charsets.UTF_8)
        } finally {
            plainText.fill(0)
        }
    } finally {
        associatedData?.fill(0)
        iv.fill(0)
        cipherText.fill(0)
    }
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
        require(runtimeSimilarityScoreValid(it)) { "Runtime returned a similarity score outside 0.0..1.0" }
    }
}

internal fun match(
    template: ByteArray,
    users: List<UserTemplate>,
    comparator: (ByteArray, ByteArray) -> Double = ::runtimeSimilarity
): Match {
    if (!isUsableRuntimeTemplate(template)) return Match(-1, -1.0, -1.0)
    var bestScore = -1.0
    var second = -1.0
    var bestIndex = -1
    var successfulComparisons = 0
    var failedComparisons = 0
    users.forEachIndexed { index, user ->
        if (!user.isCompatible() || user.templateSize != template.size) {
            failedComparisons += 1
            return@forEachIndexed
        }
        val score = runCatching { comparator(template, user.template) }
            .onFailure { failedComparisons += 1 }
            .getOrNull()
        if (score == null || !runtimeSimilarityScoreValid(score)) {
            if (score != null) failedComparisons += 1
            return@forEachIndexed
        }
        successfulComparisons += 1
        if (score > bestScore) {
            second = bestScore
            bestScore = score
            bestIndex = index
        } else if (score > second) {
            second = score
        }
    }
    return Match(
        index = bestIndex,
        score = bestScore,
        secondScore = second,
        successfulComparisons = successfulComparisons,
        failedComparisons = failedComparisons
    )
}

internal fun acceptsAuthenticationCandidate(
    score: Double,
    secondScore: Double,
    threshold: Double = MATCH_THRESHOLD,
    margin: Double = MATCH_MARGIN
): Boolean {
    if (!runtimeSimilarityScoreValid(score) || score < threshold) return false
    if (secondScore != -1.0 && !runtimeSimilarityScoreValid(secondScore)) return false
    if (secondScore > 0.0 && score - secondScore < margin) return false
    return true
}

internal fun authenticationComparisonComplete(match: Match): Boolean =
    match.successfulComparisons > 0 && match.failedComparisons == 0

internal fun canIssueSmartThingsUnlock(
    doorArmed: Boolean,
    configured: Boolean,
    passiveLivenessEnabled: Boolean,
    liveState: String,
    liveScore: Float,
    comparisonComplete: Boolean
): Boolean = doorArmed && configured && passiveLivenessEnabled &&
    liveState == "runtime_live" && runtimeUnitScoreValid(liveScore) && liveScore >= ANTISPOOF_THRESHOLD &&
    comparisonComplete

internal data class EnrollmentDuplicateSearchResult(
    val userName: String?,
    val score: Double,
    val failedComparisons: Int,
    val successfulComparisons: Int,
    val eligibleComparisons: Int,
) {
    val comparisonComplete: Boolean get() = successfulComparisons == eligibleComparisons
}

internal fun findBestEnrollmentDuplicate(
    template: ByteArray,
    users: List<UserTemplate>,
    comparator: (ByteArray, ByteArray) -> Double = ::runtimeSimilarity,
    threshold: Double = ENROLL_DUPLICATE_THRESHOLD
): EnrollmentDuplicateSearchResult {
    var bestName: String? = null
    var bestScore = -1.0
    var failedComparisons = 0
    var successfulComparisons = 0
    var eligibleComparisons = 0
    users.forEach { user ->
        eligibleComparisons += 1
        if (!user.isCompatible() || user.templateSize != template.size) {
            failedComparisons += 1
            return@forEach
        }
        var score: Double? = null
        repeat(2) {
            if (score != null) return@repeat
            runCatching { enrollmentDuplicateScore(template, user, comparator) }
                .onSuccess { score = it }
        }
        if (score == null) {
            failedComparisons += 1
            return@forEach
        }
        successfulComparisons += 1
        val completedScore = score ?: return@forEach
        if (completedScore > bestScore) {
            bestScore = completedScore
            bestName = user.name
        }
    }
    return EnrollmentDuplicateSearchResult(
        userName = bestName?.takeIf { bestScore >= threshold },
        score = bestScore,
        failedComparisons = failedComparisons,
        successfulComparisons = successfulComparisons,
        eligibleComparisons = eligibleComparisons,
    )
}

internal fun enrollmentDuplicateScore(
    template: ByteArray,
    user: UserTemplate,
    comparator: (ByteArray, ByteArray) -> Double = ::runtimeSimilarity
): Double {
    if (!user.isCompatible() || user.templateSize != template.size) return -1.0
    return comparator(template, user.template).also {
        require(runtimeSimilarityScoreValid(it)) { "Runtime returned a similarity score outside 0.0..1.0" }
    }
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

internal fun authDecisionReason(match: Match): String = when {
    match.score < MATCH_THRESHOLD -> "score below threshold"
    match.secondScore > 0.0 && match.score - match.secondScore < MATCH_MARGIN -> "ambiguous runner-up"
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
    "${entry.reason} · score ${formatScore(entry.score)} · second ${formatScore(entry.secondScore)}"

private fun formatScore(value: Double): String =
    if (value < 0.0) "-" else String.format(Locale.US, "%.3f", value)

internal fun approvalResultSucceeded(result: String): Boolean =
    !result.contains("실패")

internal fun approvalPublicSummary(entry: ApprovalLogEntry): String =
    "최근 출입 이벤트 · ${entry.result}"

private fun formatClock(timeMillis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timeMillis))

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
    AccessFeedbackKind.DoorPending -> "인증 승인 · SmartThings unlock 결과를 기다리고 있습니다"
    AccessFeedbackKind.DoorSucceeded -> "SmartThings가 unlock 명령을 수락했습니다"
    AccessFeedbackKind.DoorFailed -> "얼굴 인증은 통과했지만 SmartThings 요청이 실패했습니다"
}

internal fun accessFeedbackPublicMessage(feedback: AccessFeedback): String = when (feedback.kind) {
    AccessFeedbackKind.AuthOnly -> "얼굴 인증이 완료되었습니다"
    AccessFeedbackKind.DoorPending -> "인증 승인 · SmartThings unlock 결과를 기다리고 있습니다"
    AccessFeedbackKind.DoorSucceeded -> "SmartThings가 unlock 명령을 수락했습니다"
    AccessFeedbackKind.DoorFailed -> "얼굴 인증은 통과했지만 SmartThings 요청이 실패했습니다"
}

internal fun welcomeStatus(userName: String): String = "환영합니다, ${userName}님"

private fun disableSmartThingsDoorPersisted(prefs: SharedPreferences): Boolean =
    prefs.edit().putBoolean(SMARTTHINGS_ENABLED_KEY, false).commit()

private fun clearPersistedSmartThingsCredentials(prefs: SharedPreferences): Boolean =
    prefs.edit()
        .remove(SMARTTHINGS_DEVICE_ID_KEY)
        .remove(SMARTTHINGS_ACCESS_TOKEN_KEY)
        .remove("$SMARTTHINGS_ACCESS_TOKEN_KEY$SECURE_SUFFIX")
        .remove("$SMARTTHINGS_ACCESS_TOKEN_KEY$LEGACY_SECURE_SUFFIX")
        .remove("$SMARTTHINGS_ACCESS_TOKEN_KEY$OLDER_SECURE_SUFFIX")
        .putBoolean(SMARTTHINGS_ENABLED_KEY, false)
        .commit()

private fun migrateSmartThingsConfiguration(prefs: SharedPreferences) {
    if (prefs.getInt(SMARTTHINGS_CONFIG_VERSION_KEY, 0) == SMARTTHINGS_CONFIG_VERSION) return
    val migrated = prefs.edit()
        .remove(SMARTTHINGS_DEVICE_ID_KEY)
        .remove(SMARTTHINGS_ACCESS_TOKEN_KEY)
        .remove("$SMARTTHINGS_ACCESS_TOKEN_KEY$SECURE_SUFFIX")
        .remove("$SMARTTHINGS_ACCESS_TOKEN_KEY$LEGACY_SECURE_SUFFIX")
        .remove("$SMARTTHINGS_ACCESS_TOKEN_KEY$OLDER_SECURE_SUFFIX")
        .remove(LEGACY_DOOR_URL_KEY)
        .remove(LEGACY_DOOR_TOKEN_KEY)
        .remove(LEGACY_DOOR_ENABLED_KEY)
        .remove("$LEGACY_DOOR_TOKEN_KEY$SECURE_SUFFIX")
        .remove("$LEGACY_DOOR_TOKEN_KEY$LEGACY_SECURE_SUFFIX")
        .remove("$LEGACY_DOOR_TOKEN_KEY$OLDER_SECURE_SUFFIX")
        .putBoolean(SMARTTHINGS_ENABLED_KEY, false)
        .putInt(SMARTTHINGS_CONFIG_VERSION_KEY, SMARTTHINGS_CONFIG_VERSION)
        .commit()
    check(migrated) { "SmartThings 설정 마이그레이션을 저장하지 못했습니다" }
}

internal fun smartThingsDeviceIdValid(deviceId: String): Boolean {
    val value = deviceId.trim()
    return value.length in 8..128 && value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]*"))
}

internal fun smartThingsAccessTokenValid(accessToken: String): Boolean {
    val value = accessToken.trim()
    return value == accessToken &&
        value.length in 16..SMARTTHINGS_MAX_TOKEN_LENGTH &&
        value.none { it.isWhitespace() || it.isISOControl() }
}

internal fun smartThingsDoorConfigured(deviceId: String, accessToken: String): Boolean =
    smartThingsDeviceIdValid(deviceId) && smartThingsAccessTokenValid(accessToken)

internal fun smartThingsStatusUrl(deviceId: String): String {
    require(smartThingsDeviceIdValid(deviceId)) { "SmartThings Device ID 형식이 올바르지 않습니다" }
    return "$SMARTTHINGS_API_BASE/devices/${deviceId.trim()}/components/$SMARTTHINGS_COMPONENT/capabilities/$SMARTTHINGS_LOCK_CAPABILITY/status"
}

internal fun smartThingsCommandsUrl(deviceId: String): String {
    require(smartThingsDeviceIdValid(deviceId)) { "SmartThings Device ID 형식이 올바르지 않습니다" }
    return "$SMARTTHINGS_API_BASE/devices/${deviceId.trim()}/commands"
}

internal fun smartThingsUnlockPayloadJson(): String =
    """{"commands":[{"component":"main","capability":"lock","command":"unlock","arguments":[]}]}"""

internal data class SmartThingsDoorResult(val accepted: Boolean, val message: String)

internal fun smartThingsHttpFailureMessage(responseCode: Int): String = when (responseCode) {
    400 -> "SmartThings 요청 형식이 올바르지 않습니다"
    401 -> "SmartThings access token이 만료되었거나 올바르지 않습니다"
    403 -> "token에 이 기기 또는 잠금 제어 권한이 없습니다"
    404 -> "SmartThings 기기나 lock capability를 찾을 수 없습니다"
    409, 422 -> "이 기기는 현재 unlock 명령을 지원하거나 실행할 수 없습니다"
    429 -> "SmartThings 요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요"
    in 500..599 -> "SmartThings 서버에서 오류 응답을 보냈습니다"
    else -> "SmartThings가 요청을 수락하지 않았습니다 (HTTP $responseCode)"
}

internal fun smartThingsNetworkFailureMessage(error: Throwable): String = when (error) {
    is java.net.UnknownHostException -> "SmartThings 서버 주소를 찾을 수 없습니다"
    is java.net.SocketTimeoutException -> "SmartThings 응답 시간이 초과되었습니다"
    is javax.net.ssl.SSLException -> "SmartThings HTTPS 인증서를 확인할 수 없습니다"
    else -> "SmartThings에 연결할 수 없습니다"
}

internal fun parseSmartThingsLockState(body: String): String? = runCatching {
    JSONObject(body).optJSONObject("lock")?.optString("value")?.takeIf { it.isNotBlank() }
}.getOrNull()

internal fun smartThingsCommandAccepted(body: String): Boolean = runCatching {
    val results = JSONObject(body).optJSONArray("results") ?: return@runCatching false
    if (results.length() == 0) return@runCatching false
    (0 until results.length()).all { index ->
        val status = results.optJSONObject(index)?.optString("status").orEmpty().uppercase(Locale.US)
        status == "ACCEPTED" || status == "COMPLETED"
    }
}.getOrDefault(false)

private fun readHttpBodyLimited(connection: HttpURLConnection, responseCode: Int): String {
    val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
    if (stream == null) return ""
    return stream.use { input ->
        val buffer = ByteArray(4096)
        val output = java.io.ByteArrayOutputStream()
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > SMARTTHINGS_MAX_RESPONSE_BYTES) {
                throw IllegalStateException("SmartThings 응답이 허용 크기를 초과했습니다")
            }
            output.write(buffer, 0, count)
        }
        output.toString(Charsets.UTF_8.name())
    }
}

private fun checkSmartThingsDoorAccess(deviceId: String, token: String): SmartThingsDoorResult = runCatching {
    require(smartThingsDoorConfigured(deviceId, token)) { "SmartThings 설정이 완전하지 않습니다" }
    val conn = (URL(smartThingsStatusUrl(deviceId)).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        instanceFollowRedirects = false
        connectTimeout = 5000
        readTimeout = 5000
        doOutput = false
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Authorization", "Bearer ${token.trim()}")
    }
    try {
        val responseCode = conn.responseCode
        val body = readHttpBodyLimited(conn, responseCode)
        if (responseCode == 200) {
            val state = parseSmartThingsLockState(body)
                ?: return@runCatching SmartThingsDoorResult(false, "lock capability 응답에서 잠금 상태를 읽지 못했습니다")
            SmartThingsDoorResult(true, "SmartThings 연결 정상 · 현재 잠금 상태: $state")
        } else {
            SmartThingsDoorResult(false, smartThingsHttpFailureMessage(responseCode))
        }
    } finally {
        conn.disconnect()
    }
}.getOrElse { SmartThingsDoorResult(false, smartThingsNetworkFailureMessage(it)) }

private fun unlockSmartThingsDoor(deviceId: String, token: String): SmartThingsDoorResult = runCatching {
    require(smartThingsDoorConfigured(deviceId, token)) { "SmartThings 설정이 완전하지 않습니다" }
    val conn = (URL(smartThingsCommandsUrl(deviceId)).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        instanceFollowRedirects = false
        connectTimeout = 5000
        readTimeout = 5000
        doOutput = true
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Content-Type", "application/json;charset=utf-8")
        setRequestProperty("Authorization", "Bearer ${token.trim()}")
    }
    try {
        val body = smartThingsUnlockPayloadJson().toByteArray(Charsets.UTF_8)
        conn.outputStream.use { it.write(body) }
        val responseCode = conn.responseCode
        val responseBody = readHttpBodyLimited(conn, responseCode)
        if (responseCode in 200..299 && smartThingsCommandAccepted(responseBody)) {
            SmartThingsDoorResult(true, "SmartThings가 unlock 명령을 수락했습니다. 실제 잠금 상태 반영은 기기에서 이어집니다")
        } else if (responseCode in 200..299) {
            SmartThingsDoorResult(false, "SmartThings 응답에 수락된 unlock 결과가 없습니다")
        } else {
            SmartThingsDoorResult(false, smartThingsHttpFailureMessage(responseCode))
        }
    } finally {
        conn.disconnect()
    }
}.getOrElse { SmartThingsDoorResult(false, smartThingsNetworkFailureMessage(it)) }
