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
import android.view.WindowManager
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
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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
private const val PASSIVE_LIVENESS_ENABLED_KEY = "passive_liveness_enabled"
private const val KEYSTORE_ALIAS = "ffacio_mobile_store_key_v3"
private const val LEGACY_KEYSTORE_ALIAS = "ffacio_mobile_store_key_v2"
private const val OLDER_KEYSTORE_ALIAS = "ffacio_mobile_store_key"
private const val SECURE_SUFFIX = "_enc_v3"
private const val LEGACY_SECURE_SUFFIX = "_enc_v2"
private const val OLDER_SECURE_SUFFIX = "_enc"
private const val STORE_PREFLIGHT_KEY = "__store_preflight"
private const val MATCH_THRESHOLD = 0.42
private const val MATCH_MARGIN = 0.04
private const val ENROLL_SAMPLES = 5
private const val ENROLL_REPEAT_THRESHOLD = 0.985
private const val ENROLL_DUPLICATE_THRESHOLD = MATCH_THRESHOLD
private const val ENROLL_MIN_DISTINCT_POSES = 2
private const val ANALYSIS_INTERVAL_MS = 180L
private const val ANTISPOOF_THRESHOLD = 0.55f
private const val AUTH_RESULT_HOLD_MS = 3500L
private const val APPROVAL_LOG_LIMIT = 8
private const val ADMIN_SESSION_TIMEOUT_MS = 120_000L
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
    var passiveLivenessEnabled by remember { mutableStateOf(false) }
    var cameraAvailable by remember { mutableStateOf(true) }
    var noCameraHardware by remember { mutableStateOf(false) }
    var cameraRetryNonce by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingDeleteUserIndex by remember { mutableIntStateOf(-1) }
    var pendingAdminAction by remember { mutableStateOf<AdminAction?>(null) }
    val enrollSamples = remember { mutableStateListOf<FloatArray>() }
    val enrollPoses = remember { mutableStateListOf<Int>() }
    val approvalLogs = remember { mutableStateListOf<ApprovalLogEntry>() }
    var accessFeedback by remember { mutableStateOf<AccessFeedback?>(null) }
    val liveness = remember { LivenessChallenge() }
    var guideState by remember { mutableStateOf(FaceGuideState.Searching) }
    var liveCandidate by remember { mutableIntStateOf(-1) }
    var stableUser by remember { mutableIntStateOf(-1) }
    var stableCount by remember { mutableIntStateOf(0) }
    var lastOpenAt by remember { mutableLongStateOf(0L) }
    var authResultHoldUntil by remember { mutableLongStateOf(0L) }
    var adminSessionExpiresAt by remember { mutableLongStateOf(0L) }
    var enrollmentExpiresAt by remember { mutableLongStateOf(0L) }
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val currentAdminPromptInFlight by rememberUpdatedState(adminPromptInFlight)
    LaunchedEffect(Unit) {
        prefs.edit().remove(PASSIVE_LIVENESS_ENABLED_KEY).apply()
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
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCameraPermission = it
        if (!it) {
            status = "카메라 권한이 필요합니다"
            detail = "앱 설정에서 카메라 권한을 허용해 주세요"
        }
    }

    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val action = pendingAdminAction
        pendingAdminAction = null
        adminPromptInFlight = false
        if (it.resultCode == Activity.RESULT_OK && action != null) {
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
                        return@rememberLauncherForActivityResult
                    }
                    val deleteName = users[deleteIndex].name
                    val nextUsers = removeRegisteredUserAt(users.toList(), deleteIndex)
                        ?: run {
                            status = "삭제할 사용자를 찾을 수 없습니다"
                            detail = "등록 사용자 목록을 다시 확인해 주세요"
                            return@rememberLauncherForActivityResult
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
                        users.clear()
                        liveCandidate = -1
                        stableUser = -1
                        stableCount = 0
                        liveness.reset()
                        confirmDelete = false
                        doorUrl = ""
                        doorToken = ""
                        doorConfigError = null
                        doorArmed = false
                        if (reset.isFailure) {
                            storeError = IllegalStateException("Local template reset could not be saved", reset.exceptionOrNull())
                            status = "로컬 템플릿 초기화 실패"
                            detail = "현재 세션의 인증과 문 제어 상태는 비웠습니다. 기기 저장소 상태를 확인한 뒤 다시 시도하세요"
                            return@launch
                        }
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
                                if (!prefs.edit().putString(DOOR_URL_KEY, relayUrl).commit()) error("릴레이 URL을 저장하지 못했습니다")
                                securePutString(context, prefs, DOOR_TOKEN_KEY, relayToken)
                                if (!prefs.edit().putBoolean(DOOR_ENABLED_KEY, true).commit()) error("릴레이 활성화 상태를 저장하지 못했습니다")
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
                            prefs.edit().putBoolean(DOOR_ENABLED_KEY, false).apply()
                            doorConfigError = saved.exceptionOrNull()
                            status = "릴레이 설정을 저장할 수 없습니다"
                            detail = saved.exceptionOrNull()?.message ?: "암호화된 릴레이 토큰 저장에 실패했습니다"
                            liveCandidate = -1
                            stableUser = -1
                            stableCount = 0
                            liveness.reset()
                        }
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
            }
        } else {
            adminPromptInFlight = false
            appScreen = AppScreen.Operation
            mode = AppMode.Auth
            adminSessionExpiresAt = 0L
            enrollmentExpiresAt = 0L
            authResultHoldUntil = 0L
            liveCandidate = -1
            stableUser = -1
            stableCount = 0
            pendingDeleteUserIndex = -1
            liveness.reset()
            status = "기기 인증이 취소되었습니다"
            detail = "등록, 삭제, 문 열림 활성화에는 기기 잠금 해제가 필요합니다"
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (granted != hasCameraPermission) {
                    hasCameraPermission = granted
                    if (granted) {
                        cameraAvailable = true
                        cameraRetryNonce += 1
                        status = if (users.isEmpty()) "첫 사용자를 등록하세요" else "인증 준비 완료"
                        detail = if (users.isEmpty()) "카메라 권한이 허용되었습니다. 얼굴 등록을 시작하세요" else "카메라를 바라봐 주세요"
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
                if (shouldReturnToOperationOnLifecyclePause(currentAdminPromptInFlight)) {
                    appScreen = AppScreen.Operation
                    mode = AppMode.Auth
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

    fun disableDoorControl() {
        doorArmed = false
        prefs.edit().putBoolean(DOOR_ENABLED_KEY, false).apply()
        doorConfigError = null
    }

    fun resetTransient() {
        liveCandidate = -1
        stableUser = -1
        stableCount = 0
        liveness.reset()
    }

    fun clearAuthResultHold() {
        authResultHoldUntil = 0L
        accessFeedback = null
    }

    fun recordApproval(userName: String, result: String) {
        addApprovalLog(approvalLogs, ApprovalLogEntry(formatClock(System.currentTimeMillis()), userName, result))
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
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure) {
            status = "기기 잠금 설정이 필요합니다"
            detail = "등록, 삭제, 문 열림 활성화 전에 Android 화면 잠금을 설정하세요"
            return
        }
        val prompt = keyguard.createConfirmDeviceCredentialIntent(
            "FFacio 관리자 확인",
            "로컬 생체 템플릿과 문 제어 설정을 보호합니다"
        )
        if (prompt == null) {
            status = "기기 인증을 사용할 수 없습니다"
            detail = "Android 보안 설정을 확인한 뒤 다시 시도하세요"
            return
        }
        pendingAdminAction = action
        adminPromptInFlight = true
        authResultHoldUntil = 0L
        liveCandidate = -1
        stableUser = -1
        stableCount = 0
        liveness.reset()
        adminLauncher.launch(prompt)
    }

    fun blockedReason(): String? = when {
        modelLoading -> "오프라인 모델을 불러오는 중입니다"
        modelError != null -> "모델을 사용할 수 없습니다"
        storageBusy -> "로컬 저장소를 업데이트하는 중입니다"
        storeError != null -> "로컬 생체 저장소가 잠겨 있습니다"
        !hasCameraPermission -> "카메라 권한이 필요합니다"
        noCameraHardware -> "이 기기에는 사용할 수 있는 카메라가 없습니다"
        !cameraAvailable -> "카메라를 사용할 수 없습니다"
        else -> null
    }

    fun idleReason(): String? = when {
        mode == AppMode.Auth && users.isEmpty() -> "첫 사용자를 등록하세요"
        else -> null
    }

    fun cameraCanPreview(): Boolean = !modelLoading &&
        modelError == null &&
        storeError == null &&
        hasCameraPermission &&
        cameraAvailable &&
        !noCameraHardware

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
        hasIdleReason = idleReason() != null
    )

    fun openDoor(user: UserTemplate) {
        val now = System.currentTimeMillis()
        if (adminPromptInFlight || appScreen != AppScreen.Operation) return
        if (!doorArmed || now - lastOpenAt < 3500L) return
        val relayUrl = doorUrl.trim()
        val relayToken = doorToken.trim()
        if (relayUrl.isEmpty() || relayToken.isEmpty()) {
            doorArmed = false
            prefs.edit().putBoolean(DOOR_ENABLED_KEY, false).apply()
            status = "문 제어가 설정되지 않았습니다"
            detail = "릴레이 URL과 토큰을 저장한 뒤 다시 활성화하세요"
            return
        }
        lastOpenAt = now
        accessFeedback = AccessFeedback(AccessFeedbackKind.DoorPending, user.name)
        status = "인증 완료 · 문 열림 요청"
        detail = "${user.name}님 승인 · 릴레이 응답을 기다리고 있습니다"
        if (doorExecutor.isShutdown) return
        doorExecutor.execute {
            val ok = postDoor(relayUrl, relayToken, user.name)
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
    }

    fun onObservation(obs: Observation) {
        if (modelLoading || modelError != null || storeError != null || storageBusy) return
        if (adminPromptInFlight) return
        if (mode == AppMode.Enroll && appScreen != AppScreen.Admin) return
        if (mode == AppMode.Auth && appScreen != AppScreen.Operation) return
        if (mode == AppMode.Auth && System.currentTimeMillis() < authResultHoldUntil) return
        if (!obs.ok) {
            resetTransient()
            guideState = FaceGuideState.Searching
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
            guideState = FaceGuideState.Center
            val duplicateSample = duplicateUserForEnrollment(obs.embedding, users)
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
            val sampleDecision = enrollmentSampleDecision(obs.embedding, obs.pose, enrollSamples, enrollPoses)
            if (!sampleDecision.accepted) {
                status = sampleDecision.status
                detail = sampleDecision.detail
                return
            }
            enrollSamples.add(obs.embedding)
            enrollPoses.add(obs.pose)
            enrollmentExpiresAt = SystemClock.elapsedRealtime() + ENROLLMENT_IDLE_TIMEOUT_MS
            status = "샘플 수집 중"
            detail = "${enrollSamples.size}/$ENROLL_SAMPLES · 실제 얼굴 확인 완료 · ${poseLabel(obs.pose)}"
            if (enrollSamples.size >= ENROLL_SAMPLES) {
                val cleanName = enrollmentName.ifBlank { name.trim() }
                if (cleanName.isNotEmpty()) {
                    val averaged = average(enrollSamples)
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
                    val nextUsers = users.toList() + UserTemplate(cleanName, averaged)
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
        val match = match(obs.embedding, users)
        if (match.index < 0 || match.score < MATCH_THRESHOLD) {
            resetTransient()
            guideState = FaceGuideState.Searching
            status = "인식하지 못했습니다"
            detail = "조명과 거리를 맞춘 뒤 다시 시도해 주세요"
            return
        }
        if (match.secondScore > 0.0 && match.score - match.secondScore < MATCH_MARGIN) {
            resetTransient()
            guideState = FaceGuideState.Center
            status = "인증 보류"
            detail = "두 등록 사용자와 너무 비슷합니다. 다시 정면을 유지해 주세요"
            return
        }
        if (liveCandidate != match.index) {
            liveCandidate = match.index
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
            detail = "${users[match.index].name} 후보의 실제 얼굴 여부를 확인합니다"
            return
        }
        if (stableUser == match.index) stableCount += 1 else {
            stableUser = match.index
            stableCount = 1
        }
        if (stableCount < 3) {
            guideState = FaceGuideState.Center
            status = "안정적으로 확인 중입니다"
            detail = "잠시 그대로 유지해 주세요"
            return
        }
        val user = users[match.index]
        val shouldOpenDoor = doorArmed
        authResultHoldUntil = System.currentTimeMillis() + AUTH_RESULT_HOLD_MS
        guideState = if (shouldOpenDoor) FaceGuideState.Center else FaceGuideState.Approved
        accessFeedback = if (shouldOpenDoor) {
            AccessFeedback(AccessFeedbackKind.DoorPending, user.name)
        } else {
            AccessFeedback(AccessFeedbackKind.AuthOnly, user.name)
        }
        if (!shouldOpenDoor) recordApproval(user.name, "승인")
        status = "인증 완료"
        detail = if (shouldOpenDoor) "${user.name}님 승인 · 릴레이 결과를 기다리고 있습니다" else "${user.name}님 승인 · 최근 승인 로그에 기록했습니다"
        resetTransient()
        openDoor(user)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FFacio", fontWeight = FontWeight.Bold)
                        Text(if (appScreen == AppScreen.Operation) "Door Access" else "관리자 설정", fontSize = 12.sp, color = ComposeColor(0xFF6E6E73))
                    }
                },
                actions = {
                    if (appScreen == AppScreen.Operation) {
                        TextButton(onClick = { requestAdmin(AdminAction.OpenAdmin) }) { Text("관리") }
                    } else {
                        TextButton(onClick = {
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
                engineProvider = engineProvider,
                analyzerExecutor = analyzerExecutor,
                processing = processing,
                firstAnalyzedFrameLogged = firstAnalyzedFrameLogged,
                lastAnalysisAt = lastAnalysisAt,
                passiveLivenessEnabled = passiveLivenessEnabled,
                active = active,
                onObservation = ::onObservation,
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
                    doorArmed = doorArmed,
                    accessFeedback = accessFeedback,
                    approvalLogs = approvalLogs,
                    blockedReason = blockedReason(),
                    canRetryCamera = !cameraAvailable && hasCameraPermission && !noCameraHardware,
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
                passiveLivenessEnabled = passiveLivenessEnabled,
                accessFeedback = accessFeedback,
                approvalLogs = approvalLogs,
                users = users,
                userCount = users.size,
                enrollCount = enrollSamples.size,
                canResetStore = storeError != null,
                canUnlockDoor = doorConfigError != null,
                canRetryBlocked = blockedReason() != null && !modelLoading && modelError == null && !storageBusy,
                canMutate = !storageBusy,
                onName = { if (mode != AppMode.Enroll) name = it },
                onDoorUrl = { doorUrl = it },
                onDoorToken = { doorToken = it },
                onDoorArmed = {
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
                        requestAdmin(AdminAction.ArmDoor)
                    } else {
                        disableDoorControl()
                    }
                },
                onPassiveLivenessEnabled = passiveToggle@{
                    if (it && engineProvider()?.hasPassiveLiveness() != true) {
                        passiveLivenessEnabled = false
                        prefs.edit().remove(PASSIVE_LIVENESS_ENABLED_KEY).apply()
                        resetTransient()
                        status = "좌우 얼굴 돌리기 모드"
                        detail = "선택형 사진/화면 차단 모델을 사용할 수 없어 기본 챌린지로 계속합니다"
                        return@passiveToggle
                    }
                    passiveLivenessEnabled = it
                    prefs.edit().remove(PASSIVE_LIVENESS_ENABLED_KEY).apply()
                    resetTransient()
                    status = if (it) "사진/화면 차단 모델 켜짐" else "좌우 얼굴 돌리기 모드"
                    detail = if (it) "선택 강화 모델을 함께 사용합니다" else "기본 실제 얼굴 확인은 좌우 얼굴 돌리기 챌린지로 진행합니다"
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
                        requestAdmin(AdminAction.StartEnroll)
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
                onUnlockStore = {
                    requestAdmin(AdminAction.UnlockStore)
                },
                onResetStore = {
                    requestAdmin(AdminAction.ResetStore)
                },
                onUnlockDoor = {
                    requestAdmin(AdminAction.UnlockDoor)
                },
                onRetry = retry@{
                    if (modelError != null || noCameraHardware) {
                        status = blockedReason() ?: status
                        detail = if (modelError != null) "앱을 다시 설치해 주세요" else "전면 또는 후면 카메라가 있는 기기에서 실행해 주세요"
                        return@retry
                    }
                    cameraAvailable = true
                    noCameraHardware = false
                    cameraRetryNonce += 1
                    if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
                    if (storeError != null) {
                        requestAdmin(AdminAction.UnlockStore)
                    } else if (doorConfigError != null) {
                        requestAdmin(AdminAction.UnlockDoor)
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
            text = { Text("이 기기에 저장된 얼굴 템플릿이 삭제됩니다. 이 작업은 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(
                    enabled = !storageBusy,
                    onClick = { requestAdmin(AdminAction.DeleteUsers) }
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
            text = { Text("이 사용자의 로컬 얼굴 템플릿을 삭제합니다. 확인 후 Android 화면잠금 인증이 필요합니다.") },
            confirmButton = {
                TextButton(
                    enabled = !storageBusy,
                    onClick = { requestAdmin(AdminAction.DeleteUser) }
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
    engineProvider: () -> MobileFaceEngine?,
    analyzerExecutor: ExecutorService,
    processing: AtomicBoolean,
    firstAnalyzedFrameLogged: AtomicBoolean,
    lastAnalysisAt: AtomicLong,
    passiveLivenessEnabled: Boolean,
    active: AtomicBoolean,
    onObservation: (Observation) -> Unit,
    onCameraUnavailable: () -> Unit,
    onNoCameraHardware: () -> Unit
) {
    val context = LocalContext.current
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
                    val analysis = if (enabled) {
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
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(260.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(ComposeColor.Transparent, ComposeColor(0x330A84FF))
                    )
                )
        )
        FaceGuideOverlay(
            state = guideState,
            modifier = Modifier.align(Alignment.Center)
        )
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
private fun FaceGuideOverlay(state: FaceGuideState, modifier: Modifier = Modifier) {
    val ringColor = when (state) {
        FaceGuideState.Approved -> ComposeColor(0xFF30D158)
        FaceGuideState.TurnLeft, FaceGuideState.TurnRight -> ComposeColor(0xFF0071E3)
        FaceGuideState.Center -> ComposeColor(0xFFFFFFFF)
        FaceGuideState.Searching -> ComposeColor(0x99FFFFFF)
    }
    val scale by animateFloatAsState(
        targetValue = if (state == FaceGuideState.Approved) 1.08f else 1.0f,
        animationSpec = tween(durationMillis = 240),
        label = "face-guide-scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (state == FaceGuideState.Searching) 0.62f else 0.94f,
        animationSpec = tween(durationMillis = 260),
        label = "face-guide-alpha"
    )
    val symbolOffset by animateFloatAsState(
        targetValue = when (state) {
            FaceGuideState.TurnLeft -> -26.0f
            FaceGuideState.TurnRight -> 26.0f
            else -> 0.0f
        },
        animationSpec = tween(durationMillis = 280),
        label = "face-guide-symbol-offset"
    )
    Box(
        modifier = modifier
            .size(260.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .border(4.dp, ringColor.copy(alpha = 0.72f), CircleShape)
                .background(ringColor.copy(alpha = 0.10f))
        )
        Box(
            modifier = Modifier
                .size(232.dp)
                .clip(CircleShape)
                .border(1.dp, ComposeColor.White.copy(alpha = 0.28f), CircleShape)
                .background(ComposeColor.Transparent)
        )
        when (state) {
            FaceGuideState.Approved -> Text("✓", color = ComposeColor.White, fontSize = 76.sp, fontWeight = FontWeight.Bold)
            FaceGuideState.TurnLeft -> Text("‹", color = ComposeColor.White, fontSize = 78.sp, fontWeight = FontWeight.Bold, modifier = Modifier.graphicsLayer { translationX = symbolOffset })
            FaceGuideState.TurnRight -> Text("›", color = ComposeColor.White, fontSize = 78.sp, fontWeight = FontWeight.Bold, modifier = Modifier.graphicsLayer { translationX = symbolOffset })
            FaceGuideState.Center -> Text("•", color = ComposeColor.White, fontSize = 54.sp, fontWeight = FontWeight.Bold)
            FaceGuideState.Searching -> Text("", color = ComposeColor.White)
        }
    }
}

@Composable
private fun OperationPanel(
    modifier: Modifier = Modifier,
    status: String,
    detail: String,
    userCount: Int,
    doorArmed: Boolean,
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
            Surface(
                color = ComposeColor(0xFFF5F5F7),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("안내", color = ComposeColor(0xFF1D1D1F), fontWeight = FontWeight.SemiBold)
                    Text(
                        if (userCount == 0) {
                            "관리 버튼을 눌러 화면잠금 인증 후 첫 사용자를 등록하세요."
                        } else {
                            "카메라를 바라보고 화면 안내에 따라 정면, 왼쪽, 오른쪽을 천천히 보여 주세요."
                        },
                        color = ComposeColor(0xFF6E6E73),
                        fontSize = 14.sp
                    )
                    Text(
                        if (doorArmed) "문 열림 릴레이 활성화됨" else "문 열림 릴레이 비활성화",
                        color = if (doorArmed) ComposeColor(0xFF248A3D) else ComposeColor(0xFF6E6E73),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
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
                            Text(accessFeedbackTitle(feedback.kind), color = ComposeColor(0xFF1D1D1F), fontWeight = FontWeight.Bold)
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
                Text("최근 승인 ${approvalLogs.first().time} · ${approvalLogs.first().userName} · ${approvalLogs.first().result}", color = ComposeColor(0xFF6E6E73), fontSize = 13.sp)
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
    passiveLivenessEnabled: Boolean,
    accessFeedback: AccessFeedback?,
    approvalLogs: List<ApprovalLogEntry>,
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
    onPassiveLivenessEnabled: (Boolean) -> Unit,
    onEnroll: () -> Unit,
    onAuth: () -> Unit,
    onDelete: () -> Unit,
    onDeleteUser: (Int) -> Unit,
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
                            Text(accessFeedbackTitle(feedback.kind), color = ComposeColor(0xFF1D1D1F), fontWeight = FontWeight.Bold)
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
                        users.forEachIndexed { index, user ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(user.name, color = ComposeColor(0xFF1D1D1F), fontWeight = FontWeight.SemiBold)
                                    Text("로컬 얼굴 템플릿", color = ComposeColor(0xFF6E6E73), fontSize = 12.sp)
                                }
                                TextButton(enabled = canMutate, onClick = { onDeleteUser(index) }) {
                                    Text("삭제", color = ComposeColor(0xFFFF3B30))
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
                    Switch(checked = doorArmed, onCheckedChange = onDoorArmed, enabled = canMutate)
                    Text("인증 성공 시 HTTPS 릴레이 열기", color = ComposeColor(0xFF1D1D1F))
                }
            if (doorArmed && doorUrl.trim().isEmpty()) {
                Text("릴레이 URL을 입력해야 문 열림이 활성화됩니다", color = ComposeColor(0xFFFF3B30), fontSize = 13.sp)
            }
            OutlinedTextField(
                value = doorUrl,
                onValueChange = onDoorUrl,
                enabled = canMutate && !doorArmed,
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
                enabled = canMutate && !doorArmed,
                label = { Text("Bearer 토큰") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
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

private class MobileFaceEngine(detectorModel: File, recognizerModel: File, antiSpoofModel: File?) {
    private val detector = FaceDetectorYN.create(detectorModel.absolutePath, "", Size(320.0, 320.0), 0.82f, 0.3f, 5000)
    private val recognizer = FaceRecognizerSF.create(recognizerModel.absolutePath, "")
    private val antiSpoof = antiSpoofModel?.let { model ->
        runCatching { Dnn.readNetFromONNX(model.absolutePath) }
            .onFailure { Log.w("FFacio", "Optional passive PAD model failed to load", it) }
            .getOrNull()
    }
    private var currentSize = Size(0.0, 0.0)
    private val bgr = Mat()
    private val faces = Mat()
    private val feature = Mat()
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
            if ((row.getOrNull(2) ?: 0.0f) < bgr.cols().toFloat() * 0.16f) return Observation.fail("조금 더 가까이 와 주세요")
            val passiveLiveness = if (passiveLivenessEnabled) {
                predictPassiveLiveness(bgr, row)
            } else {
                PassiveLiveness(1.0f, "disabled")
            }
            if (!passiveLiveness.isLive) {
                return Observation.fail(passiveLiveness.message(), passiveLiveness.liveScore, passiveLiveness.state)
            }
            runCatching {
                recognizer.alignCrop(bgr, face, aligned)
                if (aligned.empty()) error("SFace alignCrop returned an empty aligned face")
                recognizer.feature(aligned, feature)
                if (feature.empty()) error("SFace feature returned an empty embedding")
            }.getOrElse {
                Log.e("FFacio", "SFace alignment or feature extraction failed", it)
                return Observation.fail("얼굴 특징을 추출할 수 없습니다. 정면을 유지하고 다시 시도해 주세요")
            }
            return Observation(true, "확인 중", matToFloatArray(feature), estimatePose(row), passiveLiveness.liveScore, passiveLiveness.state)
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
            yaw < -0.08f -> -1
            yaw > 0.08f -> 1
            else -> 0
        }
    }

    private fun matToFloatArray(mat: Mat): FloatArray {
        if (mat.type() != CvType.CV_32F) mat.convertTo(mat, CvType.CV_32F)
        Core.normalize(mat, mat)
        val data = FloatArray(mat.total().toInt())
        mat.get(0, 0, data)
        return data
    }

    fun close() {
        bgr.release()
        faces.release()
        feature.release()
        face.release()
        aligned.release()
        resizedAntiSpoof.release()
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

    fun prompt(): String = if (index >= targets.size) "라이브니스 확인 완료" else "${poseLabel(targets[index])}을 보고 잠시 유지해 주세요"
}

private enum class AppMode { Auth, Enroll }
private enum class AppScreen { Operation, Admin }
private enum class AdminAction { OpenAdmin, StartEnroll, DeleteUser, DeleteUsers, UnlockStore, ResetStore, ArmDoor, UnlockDoor }
private enum class FaceGuideState { Searching, Center, TurnLeft, TurnRight, Approved }
internal enum class AccessFeedbackKind { AuthOnly, DoorPending, DoorSucceeded, DoorFailed }
private sealed class ModelLoadState {
    data object Loading : ModelLoadState()
    data object Ready : ModelLoadState()
    data class Failed(val error: Throwable) : ModelLoadState()
}

private data class AccessFeedback(val kind: AccessFeedbackKind, val userName: String)

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
    val liveState: String = "unknown"
) {
    companion object {
        fun fail(message: String, liveScore: Float = 0.0f, liveState: String = "unknown") =
            Observation(false, message, FloatArray(0), 0, liveScore, liveState)
    }
}
private data class UserTemplate(val name: String, val embedding: FloatArray)
private data class Match(val index: Int, val score: Double, val secondScore: Double)
internal data class EnrollmentSampleDecision(val accepted: Boolean, val status: String, val detail: String)
internal data class ApprovalLogEntry(val time: String, val userName: String, val result: String)
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

internal fun shouldReturnToOperationOnLifecyclePause(adminPromptInFlight: Boolean): Boolean =
    !adminPromptInFlight

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
    StoreLoadResult(buildList {
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val values = item.getJSONArray("embedding")
            val embedding = FloatArray(values.length()) { values.getDouble(it).toFloat() }
            add(UserTemplate(item.getString("name"), embedding))
        }
    }, null)
}.getOrElse { StoreLoadResult(emptyList(), it) }

private fun saveUsers(context: Context, prefs: SharedPreferences, users: List<UserTemplate>) {
    val array = JSONArray()
    users.forEach { user ->
        val item = JSONObject()
        item.put("name", user.name)
        val values = JSONArray()
        user.embedding.forEach { values.put(it) }
        item.put("embedding", values)
        array.put(item)
    }
    securePutString(context, prefs, USERS_KEY, array.toString())
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

private fun match(embedding: FloatArray, users: List<UserTemplate>): Match {
    var best = -1.0
    var second = -1.0
    var bestIndex = -1
    users.forEachIndexed { index, user ->
        val score = cosine(embedding, user.embedding)
        if (score > best) {
            second = best
            best = score
            bestIndex = index
        } else if (score > second) {
            second = score
        }
    }
    return Match(bestIndex, best, second)
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
    if (samples.any { cosine(embedding, it) > ENROLL_REPEAT_THRESHOLD }) {
        return EnrollmentSampleDecision(false, "고개를 좌우로 천천히 돌려 주세요", "${samples.size}/$ENROLL_SAMPLES · 이미 수집한 각도와 너무 비슷합니다")
    }
    if (samples.size >= ENROLL_SAMPLES - 1) {
        val distinctPoses = (poses + pose).toSet().size
        if (distinctPoses < ENROLL_MIN_DISTINCT_POSES) {
            return EnrollmentSampleDecision(false, "고개를 살짝 돌려 주세요", "${samples.size}/$ENROLL_SAMPLES · 정면과 좌/우 중 최소 두 각도가 필요합니다")
        }
    }
    return EnrollmentSampleDecision(true, "", "")
}

private fun duplicateUserForEnrollment(embedding: FloatArray, users: List<UserTemplate>): UserTemplate? {
    val duplicate = match(embedding, users)
    return if (duplicate.index >= 0 && duplicate.score >= ENROLL_DUPLICATE_THRESHOLD) users[duplicate.index] else null
}

private fun average(samples: List<FloatArray>): FloatArray {
    val out = FloatArray(samples.first().size)
    samples.forEach { sample -> sample.indices.forEach { out[it] += sample[it] } }
    out.indices.forEach { out[it] /= samples.size }
    return out
}

private fun cosine(a: FloatArray, b: FloatArray): Double {
    var dot = 0.0
    var na = 0.0
    var nb = 0.0
    val size = min(a.size, b.size)
    for (i in 0 until size) {
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

internal fun accessFeedbackTitle(kind: AccessFeedbackKind): String = when (kind) {
    AccessFeedbackKind.AuthOnly -> "인증 승인"
    AccessFeedbackKind.DoorPending -> "문 열림 요청 중"
    AccessFeedbackKind.DoorSucceeded -> "문 열림 완료"
    AccessFeedbackKind.DoorFailed -> "문 제어 실패"
}

private fun accessFeedbackMessage(feedback: AccessFeedback): String = when (feedback.kind) {
    AccessFeedbackKind.AuthOnly -> "${feedback.userName}님 얼굴 인증이 완료되었습니다"
    AccessFeedbackKind.DoorPending -> "${feedback.userName}님 승인 · 릴레이 응답을 기다리고 있습니다"
    AccessFeedbackKind.DoorSucceeded -> "릴레이가 문 열림 요청을 수락했습니다"
    AccessFeedbackKind.DoorFailed -> "얼굴 인증은 통과했지만 릴레이 요청이 실패했습니다"
}

private fun formatClock(timeMillis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timeMillis))

private fun postDoor(url: String, token: String, user: String): Boolean = runCatching {
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
        val body = JSONObject().put("user", user).toString().toByteArray(Charsets.UTF_8)
        conn.outputStream.use { it.write(body) }
        conn.responseCode in 200..299
    } finally {
        conn.disconnect()
    }
}.getOrDefault(false)
