package com.ffacio.mobile

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
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
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
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
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.dnn.Dnn
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN
import org.opencv.objdetect.FaceRecognizerSF
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

private const val PREFS = "ffacio_store"
private const val USERS_KEY = "users"
private const val DOOR_URL_KEY = "door_url"
private const val DOOR_TOKEN_KEY = "door_token"
private const val DOOR_ENABLED_KEY = "door_enabled"
private const val DOOR_RELAY_HEALTH_PATH = "/.well-known/ffacio-door-relay"
private const val PASSIVE_LIVENESS_ENABLED_KEY = "passive_liveness_enabled"
internal const val FACE_ENGINE_ID = "insightface.arcface.w600k_r50"
internal const val FACE_EMBEDDING_SIZE = 512
private const val KEYSTORE_ALIAS = "ffacio_mobile_store_key_v3"
private const val LEGACY_KEYSTORE_ALIAS = "ffacio_mobile_store_key_v2"
private const val OLDER_KEYSTORE_ALIAS = "ffacio_mobile_store_key"
private const val SECURE_SUFFIX = "_enc_v3"
private const val LEGACY_SECURE_SUFFIX = "_enc_v2"
private const val OLDER_SECURE_SUFFIX = "_enc"
private const val STORE_PREFLIGHT_KEY = "__store_preflight"
private const val MATCH_THRESHOLD = 0.58
private const val MATCH_SAMPLE_THRESHOLD = 0.54
private const val MATCH_MARGIN = 0.08
private const val MATCH_MIN_SUPPORTING_SAMPLES = 2
private const val ENROLL_SAMPLES = 5
private const val ENROLL_POSE_HOLD_MS = 420L
private const val ENROLL_REPEAT_THRESHOLD = 0.985
private const val ENROLL_DUPLICATE_THRESHOLD = 0.68
private const val ENROLL_MIN_DISTINCT_POSES = 3
private const val ENROLL_TEMPLATE_MIN_SAMPLE_SCORE = 0.62
private const val ENROLL_TEMPLATE_AVG_SAMPLE_SCORE = 0.70
private const val ENROLL_TEMPLATE_MIN_PAIR_SCORE = 0.58
private const val ANALYSIS_INTERVAL_MS = 180L
private const val CAMERA_ANALYSIS_STALL_MS = 6500L
private const val CAMERA_WATCHDOG_RETRY_COOLDOWN_MS = 6000L
private const val CAMERA_WATCHDOG_MAX_REBIND_ATTEMPTS = 2
private const val CAMERA_ANALYZER_FATAL_STALL_MS = 20_000L
private const val ANTISPOOF_THRESHOLD = 0.55f
private const val AUTH_RESULT_HOLD_MS = 3500L
private const val APPROVAL_LOG_LIMIT = 8
private const val AUTH_DECISION_LOG_LIMIT = 8
private const val AUTH_DECISION_LOG_DEDUPE_MS = 2500L
private const val ADMIN_SESSION_TIMEOUT_MS = 120_000L
private const val ADMIN_FACE_AUTH_TIMEOUT_MS = 30_000L
private const val ENROLLMENT_IDLE_TIMEOUT_MS = 60_000L

class MainActivity : ComponentActivity() {
    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private val doorExecutor = Executors.newSingleThreadExecutor()
    private val modelExecutor = Executors.newSingleThreadExecutor()
    private val processing = AtomicBoolean(false)
    private val firstAnalyzedFrameLogged = AtomicBoolean(false)
    private val lastAnalysisAt = AtomicLong(0L)
    private val active = AtomicBoolean(true)
    private lateinit var prefs: SharedPreferences
    @Volatile
    private var engine: MobileFaceEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        var modelLoadState by mutableStateOf<ModelLoadState>(ModelLoadState.Loading)

        setContent {
            FFacioTheme {
                FFacioApp(
                    modelLoadState = modelLoadState,
                    prefs = prefs,
                    engineProvider = { engine },
                    analyzerExecutor = analyzerExecutor,
                    doorExecutor = doorExecutor,
                    processing = processing,
                    firstAnalyzedFrameLogged = firstAnalyzedFrameLogged,
                    lastAnalysisAt = lastAnalysisAt,
                    active = active
                )
            }
        }

        modelExecutor.execute {
            var createdEngine: MobileFaceEngine? = null
            val error = runCatching {
                if (!OpenCVLoader.initDebug()) error("OpenCV init failed")
                val antiSpoofModel = runCatching {
                    copyAsset("models/antispoof/minifasnet_v2.onnx")
                }.onFailure {
                    Log.w("FFacio", "Optional passive PAD model is unavailable; active liveness remains available", it)
                }.getOrNull()
                createdEngine = MobileFaceEngine(
                    copyAsset("models/opencv/face_detection_yunet_2023mar.onnx"),
                    copyAsset("models/opencv/face_recognition_sface_2021dec.onnx"),
                    copyAsset("models/insightface/models/buffalo_l/w600k_r50.onnx"),
                    antiSpoofModel
                )
            }.exceptionOrNull()
            if (!active.get()) {
                createdEngine?.close()
                return@execute
            }
            ContextCompat.getMainExecutor(this).execute {
                if (!active.get()) {
                    createdEngine?.close()
                    return@execute
                }
                if (error == null) engine = createdEngine
                if (error == null) {
                    Log.i("FFacio", "Offline models ready")
                } else {
                    Log.e("FFacio", "Offline model initialization failed", error)
                }
                modelLoadState = if (error == null) ModelLoadState.Ready else ModelLoadState.Failed(error)
            }
        }
    }

    override fun onDestroy() {
        active.set(false)
        val engineToClose = engine
        engine = null
        if (engineToClose != null) {
            runCatching {
                analyzerExecutor.execute {
                    engineToClose.close()
                }
            }.onFailure {
                engineToClose.close()
            }
        }
        analyzerExecutor.shutdown()
        if (!analyzerExecutor.awaitTermination(1500, TimeUnit.MILLISECONDS)) {
            analyzerExecutor.shutdownNow()
        }
        doorExecutor.shutdownNow()
        modelExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun copyAsset(assetPath: String): File {
        val out = File(filesDir, assetPath.replace("/", "_"))
        val modelEntry = modelManifestEntry(assetPath)
        val expectedLength = modelEntry.getLong("size")
        val expectedSha = modelEntry.getString("sha256").lowercase().takeIf { it.isNotBlank() }
            ?: error("Bundled model manifest entry is missing sha256: $assetPath")
        if (out.exists() && out.length() == expectedLength && sha256(out) == expectedSha) {
            return out
        }
        val tmp = File(out.parentFile, "${out.name}.tmp")
        assets.open(assetPath).use { input ->
            FileOutputStream(tmp).use { output ->
                input.copyTo(output, 1024 * 1024)
            }
        }
        if (tmp.length() != expectedLength || sha256(tmp) != expectedSha) {
            tmp.delete()
            error("Bundled model copy failed: $assetPath")
        }
        if (out.exists()) out.delete()
        if (!tmp.renameTo(out)) {
            tmp.delete()
            error("Bundled model install failed: $assetPath")
        }
        return out
    }

    private fun modelManifestEntry(assetPath: String): JSONObject {
        val modelPath = assetPath.removePrefix("models/")
        val raw = assets.open("models/models.manifest.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val files = JSONObject(raw).getJSONArray("files")
        for (i in 0 until files.length()) {
            val item = files.getJSONObject(i)
            if (item.optString("path") == modelPath) return item
        }
        error("Bundled model manifest is missing required asset: $assetPath")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FFacioApp(
    modelLoadState: ModelLoadState,
    prefs: SharedPreferences,
    engineProvider: () -> MobileFaceEngine?,
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
    var storageBusy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("시스템 준비 중") }
    var detail by remember { mutableStateOf("모델과 카메라를 확인하고 있습니다") }
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
    val enrollSamples = remember { mutableStateListOf<FloatArray>() }
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
                enrollSamples.clear()
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
                enrollSamples.clear()
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
                            enrollSamples.clear()
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
                        detail = "이 사용자는 현재 얼굴 인식 모델과 호환되지 않습니다. 다시 등록해 주세요"
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
                            enrollSamples.clear()
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
                                if (URL(relayUrl).protocol.lowercase() != "https") error("HTTPS 릴레이 URL만 사용할 수 있습니다")
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
                            "선택형 사진/화면 차단 모델을 사용할 수 없어 기본 챌린지로 계속합니다"
                        } else {
                            "선택형 모델을 사용할 수 없고 설정 저장에도 실패했습니다. 앱을 재시작해 상태를 확인해 주세요"
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
                    status = if (nextEnabled) "사진/화면 차단 모델 켜짐" else "좌우 얼굴 돌리기 모드"
                    detail = if (nextEnabled) "선택 강화 모델을 함께 사용합니다" else "기본 실제 얼굴 확인은 좌우 얼굴 돌리기 챌린지로 진행합니다"
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
                            detail = "앱을 완전히 종료한 뒤 다시 실행해 주세요"
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
                    enrollSamples.clear()
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

    LaunchedEffect(modelLoadState) {
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
        if (storeError != null) {
            status = "로컬 생체 저장소가 잠겨 있습니다"
            detail = "기기 인증으로 다시 열어 보거나, 필요할 때만 템플릿을 초기화하세요"
            return@LaunchedEffect
        }
        if (modelLoading) {
            status = "오프라인 모델을 준비하고 있습니다"
            detail = "기기에 포함된 얼굴 검출/인식 모델을 불러오는 중입니다"
            return@LaunchedEffect
        }
        when {
            modelError != null -> {
                status = "모델을 검증할 수 없습니다"
                detail = "앱을 다시 설치해 주세요"
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

    fun resetTransient() {
        liveCandidate = -1
        stableUser = -1
        stableCount = 0
        liveness.reset()
        enrollmentHold.reset()
        enrollmentHoldProgress = 0.0f
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
                enrollSamples.clear()
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
        if (shouldRunAdminActionImmediatelyInAdminSession(action, appScreen == AppScreen.Admin)) {
            completeAdminAction(action)
        } else {
            requestAdmin(action)
        }
    }

    fun blockedReason(): String? = when {
        modelLoading -> "오프라인 모델을 불러오는 중입니다"
        modelError != null -> "모델을 사용할 수 없습니다"
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

    fun cameraCanPreview(): Boolean = !modelLoading &&
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
                        "분석 작업이 오래 응답하지 않습니다. 앱을 완전히 종료한 뒤 다시 실행해 주세요"
                    } else {
                        "프레임 수신이 반복해서 멈췄습니다. 앱을 완전히 종료한 뒤 다시 실행해 주세요"
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

    fun onObservation(obs: Observation) {
        if (analyzerFatalStall || !cameraAvailable) return
        if (cameraWatchdogRebindAttempts != 0) cameraWatchdogRebindAttempts = 0
        if (analyzerProcessingWatchStartedAt != 0L) analyzerProcessingWatchStartedAt = 0L
        if (modelLoading || modelError != null || storeError != null || storageBusy) return
        if (adminPromptInFlight) return
        if (mode == AppMode.Enroll && appScreen != AppScreen.Admin) return
        if (mode == AppMode.Auth && appScreen != AppScreen.Operation) return
        if (mode == AppMode.Auth && System.currentTimeMillis() < authResultHoldUntil) return
        faceBounds = obs.faceBounds ?: if (!obs.ok) null else faceBounds
        if (!obs.ok) {
            resetTransient()
            guideState = if (obs.faceBounds == null) FaceGuideState.Searching else FaceGuideState.Rejected
            status = obs.message
            detail = if (obs.liveScore > 0.0f) {
                "실제 얼굴 여부를 확인하고 있습니다"
            } else if (obs.message.contains("모델")) {
                "앱을 완전히 종료한 뒤 다시 실행해 주세요"
            } else {
                "얼굴을 화면 중앙에 맞춰 주세요"
            }
            return
        }
        if (mode == AppMode.Enroll) {
            val targetPose = enrollmentTargetPose(enrollSamples.size, enrollPoses)
            guideState = faceGuideStateForPose(targetPose ?: obs.pose)
            val enrollmentEmbedding = normalizedEmbedding(obs.embedding)
            val sampleDecision = enrollmentSampleDecision(enrollmentEmbedding, obs.pose, enrollSamples, enrollPoses)
            if (!sampleDecision.accepted) {
                enrollmentHold.reset()
                enrollmentHoldProgress = 0.0f
                status = sampleDecision.status
                detail = sampleDecision.detail
                return
            }
            if (!enrollmentHold.update(targetPose ?: obs.pose, obs.pose)) {
                enrollmentHoldProgress = enrollmentHold.progress()
                status = enrollmentTargetStatus(targetPose ?: obs.pose)
                detail = "${enrollSamples.size}/$ENROLL_SAMPLES · ${enrollmentTargetInstruction(targetPose)}"
                return
            }
            enrollmentHoldProgress = 1.0f
            val duplicateSample = duplicateUserForEnrollment(enrollmentEmbedding, users)
            if (duplicateSample != null) {
                mode = AppMode.Auth
                enrollmentExpiresAt = 0L
                enrollmentName = ""
                enrollSamples.clear()
                enrollPoses.clear()
                resetTransient()
                status = "이미 등록된 얼굴입니다"
                detail = "${duplicateSample.name} 사용자와 너무 비슷합니다. 기존 사용자로 인증하거나 다른 사람을 등록해 주세요"
                return
            }
            enrollSamples.add(enrollmentEmbedding)
            enrollPoses.add(obs.pose)
            enrollmentHold.reset()
            enrollmentHoldProgress = 0.0f
            enrollmentExpiresAt = SystemClock.elapsedRealtime() + ENROLLMENT_IDLE_TIMEOUT_MS
            val nextTargetPose = enrollmentTargetPose(enrollSamples.size, enrollPoses)
            guideState = faceGuideStateForPose(nextTargetPose ?: obs.pose)
            status = "샘플 수집 중"
            detail = enrollmentProgressDetail(enrollSamples.size, obs.pose, nextTargetPose)
            if (enrollSamples.size >= ENROLL_SAMPLES) {
                val cleanName = enrollmentName.ifBlank { name.trim() }
                if (cleanName.isNotEmpty()) {
                    val averaged = average(enrollSamples)
                    val templateQuality = enrollmentTemplateQuality(averaged, enrollSamples, enrollPoses)
                    if (!templateQuality.accepted) {
                        enrollSamples.clear()
                        enrollPoses.clear()
                        enrollmentExpiresAt = SystemClock.elapsedRealtime() + ENROLLMENT_IDLE_TIMEOUT_MS
                        resetTransient()
                        status = templateQuality.status
                        detail = templateQuality.detail
                        return
                    }
                    val duplicate = duplicateUserForEnrollment(averaged, users)
                    if (duplicate != null) {
                        mode = AppMode.Auth
                        enrollmentExpiresAt = 0L
                        enrollmentName = ""
                        enrollSamples.clear()
                        enrollPoses.clear()
                        resetTransient()
                        status = "이미 등록된 얼굴입니다"
                        detail = "${duplicate.name} 사용자와 너무 비슷합니다"
                        return
                    }
                    val nextUsers = users.toList() + UserTemplate(
                        name = cleanName,
                        embedding = averaged,
                        samples = enrollSamples.map { it.copyOf() },
                        engineId = FACE_ENGINE_ID,
                        embeddingSize = FACE_EMBEDDING_SIZE
                    )
                    persistUsersAsync(nextUsers) {
                        users.replaceWith(nextUsers)
                        mode = AppMode.Auth
                        enrollmentExpiresAt = 0L
                        enrollmentName = ""
                        enrollSamples.clear()
                        enrollPoses.clear()
                        resetTransient()
                        status = "얼굴 등록이 완료되었습니다"
                        detail = "$cleanName · 등록 사용자 ${users.size}명"
                    }
                }
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
            detail = "이전 얼굴 템플릿은 새 ArcFace 모델과 호환되지 않습니다. 관리자 화면에서 삭제 후 다시 등록해 주세요"
            return
        }
        val authEmbedding = normalizedEmbedding(obs.embedding)
        val candidateIndices = if (mode == AppMode.AdminAuth) adminAuthCandidateIndices(users) else users.indices.toList()
        if (candidateIndices.isEmpty()) {
            resetTransient()
            guideState = FaceGuideState.Searching
            status = if (mode == AppMode.AdminAuth) "Head Admin 재설정이 필요합니다" else "사용자 재등록이 필요합니다"
            detail = if (mode == AppMode.AdminAuth) "호환되는 Head Admin이 없습니다. Android 화면잠금으로 다시 설정해 주세요" else "이전 얼굴 템플릿은 새 ArcFace 모델과 호환되지 않습니다"
            return
        }
        val candidateUsers = candidateIndices.map { users[it] }
        val match = match(authEmbedding, candidateUsers)
        if (match.index < 0) {
            resetTransient()
            guideState = FaceGuideState.Searching
            status = "인식하지 못했습니다"
            detail = "조명과 거리를 맞춘 뒤 다시 시도해 주세요"
            return
        }
        val matchedUserIndex = candidateIndices[match.index]
        val matchedUser = users[matchedUserIndex]
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
            detail = "잠시 그대로 유지해 주세요"
            return
        }
        val user = users[matchedUserIndex]
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
                firstAnalyzedFrameLogged = firstAnalyzedFrameLogged,
                lastAnalysisAt = lastAnalysisAt,
                passiveLivenessEnabled = passiveLivenessEnabled,
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
                    } else if (it && runCatching { URL(doorUrl.trim()).protocol.lowercase() == "https" }.getOrDefault(false).not()) {
                        doorArmed = false
                        status = "HTTPS 릴레이가 필요합니다"
                        detail = "Bearer 토큰을 사용하는 Android 문 제어는 HTTPS URL만 허용합니다"
                    } else if (it && doorToken.trim().isEmpty()) {
                        doorArmed = false
                    status = "릴레이 토큰이 필요합니다"
                    detail = "문 열림을 활성화하려면 Bearer 토큰을 입력하세요"
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
                    enrollSamples.clear()
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
                    if (modelError != null || noCameraHardware) {
                        status = blockedReason() ?: status
                        detail = if (modelError != null) "앱을 다시 설치해 주세요" else "전면 또는 후면 카메라가 있는 기기에서 실행해 주세요"
                        return@retry
                    }
                    if (analyzerFatalStall) {
                        status = "카메라 분석이 멈췄습니다"
                        detail = "안전하게 복구하려면 앱을 완전히 종료한 뒤 다시 실행해 주세요"
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
    firstAnalyzedFrameLogged: AtomicBoolean,
    lastAnalysisAt: AtomicLong,
    passiveLivenessEnabled: Boolean,
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
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(analyzerExecutor) { proxy ->
                                    if (!currentAnalysisEnabled) {
                                        proxy.close()
                                        return@setAnalyzer
                                    }
                                    analyzeProxy(proxy, engineProvider, processing, firstAnalyzedFrameLogged, lastAnalysisAt, active, context, mirrorFrames.get(), currentPassiveLivenessEnabled, onObservation)
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
                    Text("사진/화면 차단 모델", color = ComposeColor(0xFF1D1D1F))
                }
                if (!passiveLivenessEnabled) {
                    Text("기본 모드: 좌우 얼굴 돌리기 챌린지로 실제 얼굴을 확인합니다", color = ComposeColor(0xFF6E6E73), fontSize = 13.sp)
                }
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
            }
        }
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
    firstAnalyzedFrameLogged: AtomicBoolean,
    lastAnalysisAt: AtomicLong,
    active: AtomicBoolean,
    context: Context,
    mirrorHorizontal: Boolean,
    passiveLivenessEnabled: Boolean,
    onObservation: (Observation) -> Unit
) {
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
        val rgba = imageProxyToRgbaMat(proxy, mirrorHorizontal)
        try {
            if (firstAnalyzedFrameLogged.compareAndSet(false, true)) {
                Log.i("FFacio", "Camera analysis frame received")
            }
            val obs = engine.observe(rgba, passiveLivenessEnabled)
            ContextCompat.getMainExecutor(context).execute {
                if (active.get()) onObservation(obs)
            }
        } finally {
            rgba.release()
        }
    } catch (error: Exception) {
        Log.e("FFacio", "Camera frame analysis failed", error)
        ContextCompat.getMainExecutor(context).execute {
            if (active.get()) onObservation(Observation.fail("프레임 분석 오류가 발생했습니다"))
        }
    } finally {
        proxy.close()
        processing.set(false)
    }
}

private fun imageProxyToRgbaMat(proxy: ImageProxy, mirrorHorizontal: Boolean): Mat {
    if (proxy.format != PixelFormat.RGBA_8888) {
        error("Unexpected image format: ${proxy.format}")
    }
    val plane = proxy.planes.firstOrNull() ?: error("No image plane")
    if (plane.pixelStride != 4) error("Unexpected RGBA pixel stride: ${plane.pixelStride}")
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val buffer = plane.buffer.duplicate()
    val expectedBytes = proxy.width * proxy.height * pixelStride
    val packed = ByteArray(expectedBytes)
    if (rowStride == proxy.width * pixelStride && buffer.limit() >= expectedBytes) {
        buffer.position(0)
        buffer.limit(expectedBytes)
        buffer.get(packed)
    } else {
        val rowBytes = proxy.width * pixelStride
        for (y in 0 until proxy.height) {
            val src = y * rowStride
            val dst = y * rowBytes
            if (src + rowBytes > buffer.limit()) error("RGBA buffer too small for ${proxy.width}x${proxy.height}")
            buffer.position(src)
            buffer.get(packed, dst, rowBytes)
        }
    }
    val raw = Mat(proxy.height, proxy.width, CvType.CV_8UC4)
    val rotated = Mat()
    val output = Mat()
    try {
        raw.put(0, 0, packed)
        when (proxy.imageInfo.rotationDegrees) {
            90 -> Core.rotate(raw, rotated, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(raw, rotated, Core.ROTATE_180)
            270 -> Core.rotate(raw, rotated, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> raw.copyTo(rotated)
        }
        if (mirrorHorizontal) {
            Core.flip(rotated, output, 1)
        } else {
            rotated.copyTo(output)
        }
        return output
    } catch (error: Throwable) {
        output.release()
        throw error
    } finally {
        rotated.release()
        raw.release()
    }
}

private class MobileFaceEngine(detectorModel: File, recognizerModel: File, arcFaceModel: File, antiSpoofModel: File?) {
    private val detector = FaceDetectorYN.create(detectorModel.absolutePath, "", Size(320.0, 320.0), 0.82f, 0.3f, 5000)
    private val recognizer = FaceRecognizerSF.create(recognizerModel.absolutePath, "")
    private val arcFace = ArcFaceRecognizer(arcFaceModel)
    private val antiSpoof = antiSpoofModel?.let { model ->
        runCatching { Dnn.readNetFromONNX(model.absolutePath) }
            .onFailure { Log.w("FFacio", "Optional passive PAD model failed to load", it) }
            .getOrNull()
    }
    private var currentSize = Size(0.0, 0.0)
    private val bgr = Mat()
    private val faces = Mat()
    private val face = Mat()
    private val aligned = Mat()
    private val resizedAntiSpoof = Mat()

    fun hasPassiveLiveness(): Boolean = antiSpoof != null

    fun observe(rgba: Mat, passiveLivenessEnabled: Boolean): Observation {
        try {
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            if (currentSize.width != bgr.cols().toDouble() || currentSize.height != bgr.rows().toDouble()) {
                currentSize = Size(bgr.cols().toDouble(), bgr.rows().toDouble())
                detector.inputSize = currentSize
            }
            detector.detect(bgr, faces)
            if (faces.empty()) return Observation.fail("얼굴을 찾을 수 없습니다")
            if (faces.rows() > 1) return Observation.fail("한 명만 카메라 앞에 서 주세요")
            val faceRow = faces.row(0)
            try {
                faceRow.convertTo(face, CvType.CV_32F)
            } finally {
                faceRow.release()
            }
            val row = FloatArray(face.total().toInt())
            face.get(0, 0, row)
            val bounds = faceBoundsFromDetection(row, bgr.cols(), bgr.rows())
            if ((row.getOrNull(2) ?: 0.0f) < bgr.cols().toFloat() * 0.16f) return Observation.fail("조금 더 가까이 와 주세요", faceBounds = bounds)
            val passiveLiveness = if (passiveLivenessEnabled) {
                predictPassiveLiveness(bgr, row)
            } else {
                PassiveLiveness(1.0f, "disabled")
            }
            if (!passiveLiveness.isLive) {
                return Observation.fail(passiveLiveness.message(), passiveLiveness.liveScore, passiveLiveness.state, bounds)
            }
            runCatching {
                recognizer.alignCrop(bgr, face, aligned)
                if (aligned.empty()) error("SFace alignCrop returned an empty aligned face")
            }.getOrElse {
                Log.e("FFacio", "SFace alignment or feature extraction failed", it)
                return Observation.fail("얼굴 특징을 추출할 수 없습니다. 정면을 유지하고 다시 시도해 주세요", faceBounds = bounds)
            }
            val embedding = runCatching {
                arcFace.feature(aligned)
            }.getOrElse {
                Log.e("FFacio", "Face embedding extraction failed", it)
                return Observation.fail("얼굴 특징을 추출할 수 없습니다. 정면을 유지하고 다시 시도해 주세요", faceBounds = bounds)
            }
            if (embedding.size != FACE_EMBEDDING_SIZE) {
                return Observation.fail("얼굴 인식 모델 출력이 일치하지 않습니다. 앱을 다시 설치해 주세요", faceBounds = bounds)
            }
            return Observation(true, "확인 중", embedding, estimatePose(row), passiveLiveness.liveScore, passiveLiveness.state, bounds)
        } catch (error: Exception) {
            Log.e("FFacio", "Face observation failed", error)
            return Observation.fail("프레임을 분석할 수 없습니다. 카메라와 조명을 확인해 주세요")
        }
    }

    private fun predictPassiveLiveness(bgr: Mat, face: FloatArray): PassiveLiveness {
        val net = antiSpoof ?: return PassiveLiveness(0.0f, "model_unavailable")
        val crop = expandedFaceCrop(bgr, face)
        var blob: Mat? = null
        var logits: Mat? = null
        try {
            if (crop.empty()) return PassiveLiveness(0.0f, "invalid_crop")
            Imgproc.resize(crop, resizedAntiSpoof, Size(80.0, 80.0))
            blob = Dnn.blobFromImage(
                resizedAntiSpoof,
                1.0 / 255.0,
                Size(80.0, 80.0),
                Scalar(0.0, 0.0, 0.0),
                false,
                false
            )
            net.setInput(blob)
            logits = net.forward()
            val values = FloatArray(logits.total().toInt())
            logits.get(0, 0, values)
            return classifyPassiveLiveness(values)
        } catch (error: Exception) {
            Log.e("FFacio", "Passive liveness check failed", error)
            return PassiveLiveness(0.0f, "model_error")
        } finally {
            logits?.release()
            blob?.release()
            crop.release()
        }
    }

    private fun expandedFaceCrop(bgr: Mat, face: FloatArray): Mat {
        if (face.size < 4) return Mat()
        val x = face[0].toInt()
        val y = face[1].toInt()
        val w = max(1, face[2].toInt())
        val h = max(1, face[3].toInt())
        val expandedW = (w * 2.7f).toInt()
        val expandedH = (h * 2.7f).toInt()
        val cx = x + w / 2
        val cy = y + h / 2
        val left = max(0, cx - expandedW / 2)
        val top = max(0, cy - expandedH / 2)
        val right = min(bgr.cols(), cx + expandedW / 2)
        val bottom = min(bgr.rows(), cy + expandedH / 2)
        if (right <= left || bottom <= top) return Mat()
        return bgr.submat(top, bottom, left, right)
    }

    private fun estimatePose(face: FloatArray): Int {
        if (face.size < 15) return 0
        val mid = (face[4] + face[6]) / 2.0f
        val eyeDistance = max(1.0f, abs(face[6] - face[4]))
        val yaw = (face[8] - mid) / eyeDistance
        return when {
            yaw < -0.16f -> -1
            yaw > 0.16f -> 1
            else -> 0
        }
    }

    fun close() {
        arcFace.close()
        bgr.release()
        faces.release()
        face.release()
        aligned.release()
        resizedAntiSpoof.release()
    }
}

private class ArcFaceRecognizer(model: File) {
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = environment.createSession(model.absolutePath, OrtSession.SessionOptions())
    private val inputName: String = session.inputNames.first()
    private val inputShape = longArrayOf(1L, 3L, 112L, 112L)
    private val inputBuffer = FloatArray(3 * 112 * 112)
    private val pixelBuffer = ByteArray(112 * 112 * 3)
    private val resized = Mat()

    fun feature(alignedBgr: Mat): FloatArray {
        val input = arcFaceInput(alignedBgr)
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), inputShape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val output = result[0].value
                val embedding = when (output) {
                    is Array<*> -> (output.firstOrNull() as? FloatArray)?.copyOf()
                    is FloatArray -> output.copyOf()
                    else -> null
                } ?: error("Unexpected ArcFace output type: ${output::class.java.name}")
                return normalizedEmbedding(embedding)
            }
        }
    }

    private fun arcFaceInput(alignedBgr: Mat): FloatArray {
        val source = if (alignedBgr.cols() == 112 && alignedBgr.rows() == 112) {
            alignedBgr
        } else {
            Imgproc.resize(alignedBgr, resized, Size(112.0, 112.0))
            resized
        }
        if (source.channels() != 3) error("ArcFace expects a 3-channel aligned face")
        source.get(0, 0, pixelBuffer)
        val plane = 112 * 112
        for (i in 0 until plane) {
            val b = pixelBuffer[i * 3].toInt() and 0xff
            val g = pixelBuffer[i * 3 + 1].toInt() and 0xff
            val r = pixelBuffer[i * 3 + 2].toInt() and 0xff
            inputBuffer[i] = (r - 127.5f) / 127.5f
            inputBuffer[plane + i] = (g - 127.5f) / 127.5f
            inputBuffer[plane * 2 + i] = (b - 127.5f) / 127.5f
        }
        return inputBuffer
    }

    fun close() {
        resized.release()
        session.close()
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

internal data class PassiveLiveness(val liveScore: Float, val state: String) {
    val isLive: Boolean
        get() = state == "live" || state == "disabled"

    fun message(): String = when (state) {
        "model_error", "invalid_output", "model_unavailable" -> "사진/화면 차단 모델을 사용할 수 없습니다. 관리자 화면에서 해당 옵션을 끄고 기본 좌우 얼굴 돌리기 모드로 전환해 주세요"
        "invalid_crop" -> "얼굴을 화면 안에 맞춰 주세요"
        "disabled" -> "실제 얼굴 체크가 꺼져 있습니다"
        else -> "사진이나 화면으로 보이는 얼굴입니다. 실제 얼굴을 보여주세요"
    }
}

internal fun passiveLivenessProbabilities(values: FloatArray): FloatArray {
    if (values.size < 3) return FloatArray(0)
    val live = values[0]
    val printed = values[1]
    val replay = values[2]
    val probabilitySum = live + printed + replay
    if (
        live >= 0.0f && live <= 1.0f &&
        printed >= 0.0f && printed <= 1.0f &&
        replay >= 0.0f && replay <= 1.0f &&
        probabilitySum >= 0.98f && probabilitySum <= 1.02f
    ) {
        val safeSum = max(1.0e-8f, probabilitySum)
        return floatArrayOf(live / safeSum, printed / safeSum, replay / safeSum)
    }
    val maxLogit = max(values[0], max(values[1], values[2]))
    val liveExp = exp((values[0] - maxLogit).toDouble())
    val printExp = exp((values[1] - maxLogit).toDouble())
    val replayExp = exp((values[2] - maxLogit).toDouble())
    val sum = max(1e-8, liveExp + printExp + replayExp)
    return floatArrayOf((liveExp / sum).toFloat(), (printExp / sum).toFloat(), (replayExp / sum).toFloat())
}

internal fun classifyPassiveLiveness(values: FloatArray, threshold: Float = ANTISPOOF_THRESHOLD): PassiveLiveness {
    if (values.size < 3) return PassiveLiveness(0.0f, "invalid_output")
    // The bundled MiniFASNet-V2 ONNX model returns [live, print-attack, replay-attack].
    val probs = passiveLivenessProbabilities(values)
    if (probs.size < 3) return PassiveLiveness(0.0f, "invalid_output")
    val live = probs[0]
    val printed = probs[1]
    val replay = probs[2]
    val state = when {
        live >= threshold && live >= printed && live >= replay -> "live"
        replay >= printed -> "replay_attack"
        else -> "print_attack"
    }
    return PassiveLiveness(live, state)
}
private data class Observation(
    val ok: Boolean,
    val message: String,
    val embedding: FloatArray,
    val pose: Int,
    val liveScore: Float = 0.0f,
    val liveState: String = "unknown",
    val faceBounds: FaceBounds? = null
) {
    companion object {
        fun fail(message: String, liveScore: Float = 0.0f, liveState: String = "unknown", faceBounds: FaceBounds? = null) =
            Observation(false, message, FloatArray(0), 0, liveScore, liveState, faceBounds)
    }
}

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
    val embedding: FloatArray,
    val samples: List<FloatArray> = emptyList(),
    val engineId: String = FACE_ENGINE_ID,
    val embeddingSize: Int = embedding.size,
    val isHeadAdmin: Boolean = false
) {
    fun matchingSamples(): List<FloatArray> = samples
    fun matchSampleCount(): Int = matchingSamples().size
    fun isCompatible(): Boolean =
        engineId == FACE_ENGINE_ID &&
            embeddingSize == FACE_EMBEDDING_SIZE &&
            embedding.size == FACE_EMBEDDING_SIZE &&
            samples.isNotEmpty() &&
            matchingSamples().all { it.size == FACE_EMBEDDING_SIZE }
}
internal data class Match(val index: Int, val score: Double, val secondScore: Double, val supportCount: Int)
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
            val embedding = normalizedEmbedding(parseEmbeddingArray(item.getJSONArray("embedding")))
            val engineId = item.optString("engine_id", "legacy.unknown")
            val embeddingSize = item.optInt("embedding_size", embedding.size)
            val samples = if (item.has("samples")) {
                parseEmbeddingSamples(item.getJSONArray("samples"), embedding.size)
            } else {
                emptyList()
            }
            add(UserTemplate(item.getString("name"), embedding, samples, engineId, embeddingSize, item.optBoolean("head_admin", false)))
        }
    }), null)
}.getOrElse { StoreLoadResult(emptyList(), it) }

private fun saveUsers(context: Context, prefs: SharedPreferences, users: List<UserTemplate>) {
    val array = JSONArray()
    users.forEach { user ->
        val item = JSONObject()
        item.put("name", user.name)
        item.put("engine_id", user.engineId)
        item.put("embedding_size", user.embeddingSize)
        item.put("head_admin", user.isHeadAdmin)
        val values = JSONArray()
        user.embedding.forEach { values.put(it) }
        item.put("embedding", values)
        val samples = JSONArray()
        user.matchingSamples().forEach { sample ->
            val sampleValues = JSONArray()
            normalizedEmbedding(sample).forEach { sampleValues.put(it) }
            samples.put(sampleValues)
        }
        item.put("samples", samples)
        array.put(item)
    }
    securePutString(context, prefs, USERS_KEY, array.toString())
}

private fun parseEmbeddingArray(values: JSONArray): FloatArray =
    FloatArray(values.length()) { values.getDouble(it).toFloat() }

private fun parseEmbeddingSamples(samples: JSONArray, embeddingSize: Int): List<FloatArray> = buildList {
    for (i in 0 until samples.length()) {
        val sample = normalizedEmbedding(parseEmbeddingArray(samples.getJSONArray(i)))
        if (sample.size == embeddingSize) add(sample)
    }
}

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

internal fun match(embedding: FloatArray, users: List<UserTemplate>): Match {
    var bestRank = -1.0
    var bestCentroidScore = -1.0
    var second = -1.0
    var bestSupportCount = 0
    var bestIndex = -1
    users.forEachIndexed { index, user ->
        if (!user.isCompatible()) return@forEachIndexed
        val centroidScore = cosine(embedding, user.embedding)
        val sampleScores = user.matchingSamples().map { cosine(embedding, it) }
        val sampleMaxScore = sampleScores.maxOrNull() ?: -1.0
        val rankScore = max(centroidScore, sampleMaxScore)
        val supportCount = sampleScores.count { it >= MATCH_SAMPLE_THRESHOLD }
        if (rankScore > bestRank) {
            second = bestRank
            bestRank = rankScore
            bestCentroidScore = centroidScore
            bestSupportCount = supportCount
            bestIndex = index
        } else if (rankScore > second) {
            second = rankScore
        }
    }
    return Match(bestIndex, bestCentroidScore, second, bestSupportCount)
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
    embedding: FloatArray,
    pose: Int,
    samples: List<FloatArray>,
    poses: List<Int>
): EnrollmentSampleDecision {
    if (samples.isNotEmpty() && samples.last().size != embedding.size) {
        return EnrollmentSampleDecision(false, "얼굴 특징을 다시 추출해 주세요", "등록 샘플 형식이 일치하지 않습니다")
    }
    val targetPose = enrollmentTargetPose(samples.size, poses)
    if (targetPose != null && pose != targetPose) {
        return EnrollmentSampleDecision(false, enrollmentTargetStatus(targetPose), "${samples.size}/$ENROLL_SAMPLES · ${enrollmentTargetInstruction(targetPose)}")
    }
    if (samples.any { cosine(embedding, it) > ENROLL_REPEAT_THRESHOLD }) {
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
    centroid: FloatArray,
    samples: List<FloatArray>,
    poses: List<Int> = emptyList(),
    minSampleScore: Double = ENROLL_TEMPLATE_MIN_SAMPLE_SCORE,
    averageSampleScore: Double = ENROLL_TEMPLATE_AVG_SAMPLE_SCORE,
    minPairScore: Double = ENROLL_TEMPLATE_MIN_PAIR_SCORE
): EnrollmentTemplateQualityDecision {
    if (samples.isEmpty()) {
        return EnrollmentTemplateQualityDecision(false, "등록 품질이 낮습니다", "얼굴 샘플을 다시 수집해 주세요")
    }
    if (samples.any { it.size != centroid.size }) {
        return EnrollmentTemplateQualityDecision(false, "등록 품질이 낮습니다", "얼굴 특징 형식이 일치하지 않습니다. 다시 등록해 주세요")
    }
    if (poses.isNotEmpty() && !enrollmentPoseCoverageAccepted(poses)) {
        return EnrollmentTemplateQualityDecision(false, "등록 품질이 낮습니다", "정면, 왼쪽, 오른쪽을 모두 다시 수집해 주세요")
    }
    val scores = samples.map { cosine(centroid, it) }
    val weakest = scores.minOrNull() ?: 0.0
    val averageScore = scores.average()
    var weakestPair = 1.0
    for (left in samples.indices) {
        for (right in left + 1 until samples.size) {
            weakestPair = min(weakestPair, cosine(samples[left], samples[right]))
        }
    }
    return if (weakest >= minSampleScore && averageScore >= averageSampleScore && weakestPair >= minPairScore) {
        EnrollmentTemplateQualityDecision(true, "", "")
    } else {
        EnrollmentTemplateQualityDecision(
            false,
            "등록 품질이 낮습니다",
            "한 사람만 카메라 앞에서 조명과 거리를 맞춘 뒤 처음부터 다시 등록해 주세요"
        )
    }
}

private fun duplicateUserForEnrollment(embedding: FloatArray, users: List<UserTemplate>): UserTemplate? {
    return users.firstOrNull { user ->
        user.isCompatible() && enrollmentDuplicateScore(embedding, user) >= ENROLL_DUPLICATE_THRESHOLD
    }
}

internal fun enrollmentDuplicateScore(embedding: FloatArray, user: UserTemplate): Double {
    val candidates = listOf(user.embedding) + user.matchingSamples()
    return candidates.maxOfOrNull { cosine(embedding, it) } ?: -1.0
}

private fun average(samples: List<FloatArray>): FloatArray {
    val out = FloatArray(samples.first().size)
    samples.forEach { sample ->
        val normalized = normalizedEmbedding(sample)
        normalized.indices.forEach { out[it] += normalized[it] }
    }
    out.indices.forEach { out[it] /= samples.size }
    return normalizedEmbedding(out)
}

private fun normalizedEmbedding(embedding: FloatArray): FloatArray {
    var norm = 0.0
    embedding.forEach { value -> norm += value * value }
    val scale = sqrt(norm)
    if (scale <= 1e-8) return embedding.copyOf()
    return FloatArray(embedding.size) { index -> (embedding[index] / scale).toFloat() }
}

internal fun cosine(a: FloatArray, b: FloatArray): Double {
    if (a.isEmpty() || b.isEmpty() || a.size != b.size) return -1.0
    var dot = 0.0
    var na = 0.0
    var nb = 0.0
    for (i in a.indices) {
        dot += a[i] * b[i]
        na += a[i] * a[i]
        nb += b[i] * b[i]
    }
    return dot / (sqrt(na) * sqrt(nb) + 1e-8)
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
    doorUrl.trim().isNotEmpty() && doorToken.trim().isNotEmpty()

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
    if (endpoint.protocol.lowercase() != "https") return@runCatching false
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
