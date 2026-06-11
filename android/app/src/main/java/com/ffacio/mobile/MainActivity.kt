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
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import android.util.Size as AndroidSize
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN
import org.opencv.objdetect.FaceRecognizerSF
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

private const val PREFS = "ffacio_store"
private const val USERS_KEY = "users"
private const val DOOR_URL_KEY = "door_url"
private const val DOOR_TOKEN_KEY = "door_token"
private const val DOOR_ARMED_KEY = "door_armed"
private const val KEYSTORE_ALIAS = "ffacio_mobile_store_key_v2"
private const val LEGACY_KEYSTORE_ALIAS = "ffacio_mobile_store_key"
private const val SECURE_SUFFIX = "_enc_v2"
private const val LEGACY_SECURE_SUFFIX = "_enc"
private const val MATCH_THRESHOLD = 0.42
private const val MATCH_MARGIN = 0.04
private const val ENROLL_SAMPLES = 5
private const val ANALYSIS_INTERVAL_MS = 180L

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
                createdEngine = MobileFaceEngine(
                    copyAsset("models/opencv/face_detection_yunet_2023mar.onnx"),
                    copyAsset("models/opencv/face_recognition_sface_2021dec.onnx")
                )
                if (!active.get()) return@execute
                engine = createdEngine
            }.exceptionOrNull()
            if (!active.get()) return@execute
            ContextCompat.getMainExecutor(this).execute {
                if (!active.get()) return@execute
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
        engine = null
        analyzerExecutor.shutdownNow()
        doorExecutor.shutdownNow()
        modelExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun copyAsset(assetPath: String): File {
        val out = File(filesDir, assetPath.replace("/", "_"))
        val expectedLength = assets.openFd(assetPath).use { it.length }
        if (out.exists() && out.length() == expectedLength) return out
        val tmp = File(out.parentFile, "${out.name}.tmp")
        assets.open(assetPath).use { input ->
            FileOutputStream(tmp).use { output ->
                input.copyTo(output, 1024 * 1024)
            }
        }
        if (tmp.length() != expectedLength) {
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
    val users = remember { mutableStateListOf<UserTemplate>() }
    val modelLoading = modelLoadState is ModelLoadState.Loading
    val modelError = (modelLoadState as? ModelLoadState.Failed)?.error
    var storeError by remember { mutableStateOf<Throwable?>(null) }
    var status by remember { mutableStateOf("시스템 준비 중") }
    var detail by remember { mutableStateOf("모델과 카메라를 확인하고 있습니다") }
    var mode by remember { mutableStateOf(AppMode.Auth) }
    var name by remember { mutableStateOf("") }
    var enrollmentName by remember { mutableStateOf("") }
    var doorUrl by remember { mutableStateOf(prefs.getString(DOOR_URL_KEY, "") ?: "") }
    var doorToken by remember { mutableStateOf("") }
    var doorConfigError by remember { mutableStateOf<Throwable?>(null) }
    var doorArmed by remember { mutableStateOf(prefs.getBoolean(DOOR_ARMED_KEY, false)) }
    var cameraAvailable by remember { mutableStateOf(true) }
    var cameraRetryNonce by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingAdminAction by remember { mutableStateOf<AdminAction?>(null) }
    val enrollSamples = remember { mutableStateListOf<FloatArray>() }
    val liveness = remember { LivenessChallenge() }
    var liveCandidate by remember { mutableIntStateOf(-1) }
    var stableUser by remember { mutableIntStateOf(-1) }
    var stableCount by remember { mutableIntStateOf(0) }
    var lastOpenAt by remember { mutableLongStateOf(0L) }
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
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
        if (it.resultCode == Activity.RESULT_OK && action != null) {
            when (action) {
                AdminAction.StartEnroll -> {
                    mode = AppMode.Enroll
                    enrollmentName = name.trim()
                    enrollSamples.clear()
                    liveCandidate = -1
                    stableUser = -1
                    stableCount = 0
                    liveness.reset()
                    status = "얼굴을 중앙에 맞춰주세요"
                    detail = "FFacio가 안정적인 얼굴 템플릿을 수집하고 있습니다"
                }
                AdminAction.DeleteUsers -> {
                    val deleted = runCatching { saveUsers(context, prefs, emptyList()) }
                        .onFailure {
                            storeError = it
                            status = "로컬 생체 저장소를 사용할 수 없습니다"
                            detail = "암호화된 얼굴 템플릿 저장에 실패해 인증을 차단했습니다"
                        }
                        .isSuccess
                    if (deleted) {
                        storeError = null
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
                AdminAction.UnlockStore -> {
                    val loaded = loadUsers(context, prefs)
                    storeError = loaded.error
                    if (loaded.error == null) {
                        users.replaceWith(loaded.users)
                        status = if (users.isEmpty()) "첫 사용자를 등록하세요" else "로컬 템플릿 잠금 해제 완료"
                        detail = if (users.isEmpty()) "기기에 저장된 얼굴 템플릿이 없습니다" else "등록 사용자 ${users.size}명"
                    } else {
                        status = "로컬 템플릿을 열 수 없습니다"
                        detail = "기기 인증 후에도 템플릿을 확인하지 못했습니다. 필요하면 초기화하세요"
                    }
                }
                AdminAction.ResetStore -> {
                    val reset = prefs.edit()
                        .remove(USERS_KEY)
                        .remove("$USERS_KEY$SECURE_SUFFIX")
                        .remove("$USERS_KEY$LEGACY_SECURE_SUFFIX")
                        .commit()
                    if (!reset) {
                        storeError = IllegalStateException("Local template reset could not be saved")
                        status = "로컬 템플릿 초기화 실패"
                        detail = "기기 저장소 상태를 확인한 뒤 다시 시도하세요"
                        return@rememberLauncherForActivityResult
                    }
                    users.clear()
                    storeError = null
                    liveCandidate = -1
                    stableUser = -1
                    stableCount = 0
                    liveness.reset()
                    confirmDelete = false
                    status = "로컬 템플릿 저장소 초기화 완료"
                    detail = "기기 안의 얼굴 템플릿을 지웠습니다. 새 사용자를 등록하세요"
                }
                AdminAction.ArmDoor -> {
                    val saved = runCatching {
                        val relayUrl = doorUrl.trim()
                        val relayToken = doorToken.trim()
                        if (relayUrl.isEmpty()) error("릴레이 URL이 필요합니다")
                        if (relayToken.isEmpty()) error("릴레이 토큰이 필요합니다")
                        if (URL(relayUrl).protocol.lowercase() != "https") error("HTTPS 릴레이 URL만 사용할 수 있습니다")
                        if (!prefs.edit().putString(DOOR_URL_KEY, relayUrl).commit()) error("릴레이 URL을 저장하지 못했습니다")
                        securePutString(context, prefs, DOOR_TOKEN_KEY, relayToken)
                        if (!prefs.edit().putBoolean(DOOR_ARMED_KEY, true).commit()) error("릴레이 활성화 상태를 저장하지 못했습니다")
                    }.onSuccess {
                        doorConfigError = null
                        doorArmed = true
                        status = "문 열림 릴레이 활성화 완료"
                        detail = "얼굴 인증과 라이브니스 확인을 모두 통과한 뒤에만 요청합니다"
                    }.onFailure {
                        doorArmed = false
                        prefs.edit().putBoolean(DOOR_ARMED_KEY, false).commit()
                        doorConfigError = it
                        status = "릴레이 설정을 저장할 수 없습니다"
                        detail = it.message ?: "암호화된 릴레이 토큰 저장에 실패했습니다"
                    }.isSuccess
                    if (!saved) {
                        liveCandidate = -1
                        stableUser = -1
                        stableCount = 0
                        liveness.reset()
                    }
                }
                AdminAction.UnlockDoor -> {
                    val loaded = runCatching { secureGetString(context, prefs, DOOR_TOKEN_KEY, "", failClosed = true) }
                    loaded.onSuccess {
                        doorToken = it
                        doorConfigError = null
                        status = "릴레이 토큰 잠금 해제 완료"
                        detail = if (doorArmed) "인증 성공 시 저장된 HTTPS 릴레이로 요청합니다" else "필요하면 문 열림 스위치를 다시 활성화하세요"
                    }.onFailure {
                        doorArmed = false
                        prefs.edit().putBoolean(DOOR_ARMED_KEY, false).commit()
                        doorConfigError = it
                        status = "릴레이 토큰을 열 수 없습니다"
                        detail = "토큰을 다시 입력하고 기기 인증 후 활성화하세요"
                    }
                }
            }
        } else {
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
                    }
                }
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
            if (doorArmed && it.isBlank()) {
                doorArmed = false
                prefs.edit().putBoolean(DOOR_ARMED_KEY, false).commit()
            }
        }.onFailure {
            doorConfigError = it
            doorArmed = false
            prefs.edit().putBoolean(DOOR_ARMED_KEY, false).commit()
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

    fun persistDoorDisabled() {
        runCatching {
            if (!prefs.edit().putBoolean(DOOR_ARMED_KEY, false).commit()) error("릴레이 비활성화 상태를 저장하지 못했습니다")
        }.onSuccess {
            doorConfigError = null
        }.onFailure {
            doorArmed = false
            status = "릴레이 설정을 저장할 수 없습니다"
            detail = it.message ?: "암호화된 릴레이 토큰 저장에 실패했습니다"
        }
    }

    fun resetTransient() {
        liveCandidate = -1
        stableUser = -1
        stableCount = 0
        liveness.reset()
    }

    fun writeUsersOrBlock(nextUsers: List<UserTemplate>): Boolean {
        return runCatching {
            saveUsers(context, prefs, nextUsers)
        }.onSuccess {
            storeError = null
        }.onFailure {
            storeError = it
            resetTransient()
            status = "로컬 생체 저장소를 사용할 수 없습니다"
            detail = "암호화된 얼굴 템플릿 저장에 실패해 인증을 차단했습니다"
        }.isSuccess
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
        adminLauncher.launch(prompt)
    }

    fun blockedReason(): String? = when {
        modelLoading -> "오프라인 모델을 불러오는 중입니다"
        modelError != null -> "모델을 사용할 수 없습니다"
        storeError != null -> "로컬 생체 저장소가 잠겨 있습니다"
        !hasCameraPermission -> "카메라 권한이 필요합니다"
        !cameraAvailable -> "카메라를 사용할 수 없습니다"
        else -> null
    }

    fun openDoor(user: UserTemplate) {
        val now = System.currentTimeMillis()
        if (!doorArmed || now - lastOpenAt < 3500L) return
        val relayUrl = doorUrl.trim()
        val relayToken = doorToken.trim()
        if (relayUrl.isEmpty() || relayToken.isEmpty()) {
            doorArmed = false
            prefs.edit().putBoolean(DOOR_ARMED_KEY, false).commit()
            status = "문 제어가 설정되지 않았습니다"
            detail = "릴레이 URL과 토큰을 저장한 뒤 다시 활성화하세요"
            return
        }
        lastOpenAt = now
        status = "문 제어를 요청하는 중입니다"
        detail = "릴레이 응답을 기다리고 있습니다"
        if (doorExecutor.isShutdown) return
        doorExecutor.execute {
            val ok = postDoor(relayUrl, relayToken, user.name)
            ContextCompat.getMainExecutor(context).execute {
                if (!active.get()) return@execute
                status = if (ok) "문 열림 요청 완료" else "문 제어 실패"
                detail = if (ok) "릴레이가 요청을 수락했습니다" else "URL, 토큰, 네트워크를 확인해 주세요"
            }
        }
    }

    fun onObservation(obs: Observation) {
        if (modelLoading || modelError != null || storeError != null) return
        if (!obs.ok) {
            resetTransient()
            status = obs.message
            detail = "얼굴을 화면 중앙에 맞춰 주세요"
            return
        }
        if (mode == AppMode.Enroll) {
            enrollSamples.add(obs.embedding)
            status = "샘플 수집 중"
            detail = "${enrollSamples.size}/$ENROLL_SAMPLES · ${poseLabel(obs.pose)}"
            if (enrollSamples.size >= ENROLL_SAMPLES) {
                val cleanName = enrollmentName.ifBlank { name.trim() }
                if (cleanName.isNotEmpty()) {
                    val nextUsers = users.toList() + UserTemplate(cleanName, average(enrollSamples))
                    if (!writeUsersOrBlock(nextUsers)) return
                    users.replaceWith(nextUsers)
                    mode = AppMode.Auth
                    enrollmentName = ""
                    enrollSamples.clear()
                    resetTransient()
                    status = "얼굴 등록이 완료되었습니다"
                    detail = "$cleanName · 등록 사용자 ${users.size}명"
                }
            }
            return
        }
        if (users.isEmpty()) {
            resetTransient()
            status = "등록된 사용자가 없습니다"
            detail = "아래에서 첫 사용자를 등록해 주세요"
            return
        }
        val match = match(obs.embedding, users)
        if (match.index < 0 || match.score < MATCH_THRESHOLD) {
            resetTransient()
            status = "인식하지 못했습니다"
            detail = "조명과 거리를 맞춘 뒤 다시 시도해 주세요"
            return
        }
        if (match.secondScore > 0.0 && match.score - match.secondScore < MATCH_MARGIN) {
            resetTransient()
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
            status = liveness.prompt()
            detail = "${users[match.index].name} 후보의 실제 얼굴 여부를 확인합니다"
            return
        }
        if (stableUser == match.index) stableCount += 1 else {
            stableUser = match.index
            stableCount = 1
        }
        if (stableCount < 3) {
            status = "안정적으로 확인 중입니다"
            detail = "잠시 그대로 유지해 주세요"
            return
        }
        val user = users[match.index]
        status = "환영합니다, ${user.name}님"
        detail = "인증이 안정적으로 확인되었습니다"
        resetTransient()
        openDoor(user)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FFacio", fontWeight = FontWeight.Bold)
                        Text("Offline Face Access", fontSize = 12.sp, color = ComposeColor(0xFF6E6E73))
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
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CameraStage(
                enabled = !modelLoading && modelError == null && storeError == null && hasCameraPermission && cameraAvailable,
                cameraRetryNonce = cameraRetryNonce,
                stageMessage = blockedReason() ?: "카메라 준비 중",
                engineProvider = engineProvider,
                analyzerExecutor = analyzerExecutor,
                processing = processing,
                firstAnalyzedFrameLogged = firstAnalyzedFrameLogged,
                lastAnalysisAt = lastAnalysisAt,
                active = active,
                onObservation = ::onObservation,
                onCameraUnavailable = {
                    cameraAvailable = false
                    resetTransient()
                    status = "카메라를 사용할 수 없습니다"
                    detail = "다른 앱이 카메라를 사용 중인지 확인한 뒤 다시 시도해 주세요"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 260.dp, max = 430.dp)
            )
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
                userCount = users.size,
                enrollCount = enrollSamples.size,
                canResetStore = storeError != null,
                canUnlockDoor = doorConfigError != null,
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
                        doorArmed = false
                        persistDoorDisabled()
                    }
                },
                onEnroll = enroll@{
                    blockedReason()?.let {
                        status = it
                        detail = "상태를 해결한 뒤 다시 시도해 주세요"
                        return@enroll
                    }
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
                    enrollmentName = ""
                    enrollSamples.clear()
                    resetTransient()
                    status = if (users.isEmpty()) "먼저 얼굴을 등록하세요" else "인증 모드"
                    detail = if (users.isEmpty()) "등록 사용자 0명" else "카메라를 바라봐 주세요"
                },
                onDelete = {
                    confirmDelete = true
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
                onRetry = {
                    cameraAvailable = true
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
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("등록 사용자를 삭제할까요?") },
            text = { Text("이 기기에 저장된 얼굴 템플릿이 삭제됩니다. 이 작업은 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    requestAdmin(AdminAction.DeleteUsers)
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("취소") }
            }
        )
    }
}

@Composable
private fun CameraStage(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    cameraRetryNonce: Int,
    stageMessage: String,
    engineProvider: () -> MobileFaceEngine?,
    analyzerExecutor: ExecutorService,
    processing: AtomicBoolean,
    firstAnalyzedFrameLogged: AtomicBoolean,
    lastAnalysisAt: AtomicLong,
    active: AtomicBoolean,
    onObservation: (Observation) -> Unit,
    onCameraUnavailable: () -> Unit
) {
    val context = LocalContext.current
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
        var boundPreview: Preview? = null
        var boundAnalysis: ImageAnalysis? = null
        var disposed = false
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
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(AndroidSize(640, 480))
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(analyzerExecutor) { proxy ->
                                analyzeProxy(proxy, engineProvider, processing, firstAnalyzedFrameLogged, lastAnalysisAt, active, context, onObservation)
                            }
                        }
                    val selector = when {
                        provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                        provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                        else -> null
                    }
                    if (selector == null) {
                        onCameraUnavailable()
                    } else {
                        if (disposed) return@addListener
                        provider.bindToLifecycle(context as ComponentActivity, selector, preview, analysis)
                        boundProvider = provider
                        boundPreview = preview
                        boundAnalysis = analysis
                    }
                }.onFailure {
                    onCameraUnavailable()
                }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose {
            disposed = true
            boundAnalysis?.clearAnalyzer()
            val provider = boundProvider
            val preview = boundPreview
            val analysis = boundAnalysis
            if (provider != null && preview != null && analysis != null) {
                runCatching { provider.unbind(preview, analysis) }
            } else {
                providerFuture?.addListener({
                    runCatching {
                        boundAnalysis?.clearAnalyzer()
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
        if (!enabled) {
            Text(
                stageMessage,
                color = ComposeColor.White,
                modifier = Modifier.align(Alignment.Center)
            )
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
    userCount: Int,
    enrollCount: Int,
    canResetStore: Boolean,
    canUnlockDoor: Boolean,
    onName: (String) -> Unit,
    onDoorUrl: (String) -> Unit,
    onDoorToken: (String) -> Unit,
    onDoorArmed: (Boolean) -> Unit,
    onEnroll: () -> Unit,
    onAuth: () -> Unit,
    onDelete: () -> Unit,
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
            if (mode == AppMode.Enroll) {
                Text("등록 진행 $enrollCount/$ENROLL_SAMPLES", color = ComposeColor(0xFF1D1D1F), fontWeight = FontWeight.SemiBold)
            }
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                label = { Text("등록 이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (blockedReason != null || canUnlockDoor) {
                Surface(color = ComposeColor(0xFFFFF4E5), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(blockedReason ?: "릴레이 토큰을 다시 확인하세요", color = ComposeColor(0xFF8A4B00), fontWeight = FontWeight.Bold)
                        if (blockedReason != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("다시 확인") }
                                Button(onClick = onOpenSettings, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF1D1D1F))) { Text("앱 설정") }
                            }
                        }
                        if (canResetStore) {
                            Button(
                                onClick = onUnlockStore,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF0071E3))
                            ) { Text("로컬 템플릿 다시 열기") }
                            Button(
                                onClick = onResetStore,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFFFF3B30))
                            ) { Text("로컬 템플릿 초기화") }
                        }
                        if (canUnlockDoor) {
                            Button(
                                onClick = onUnlockDoor,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF0071E3))
                            ) { Text("릴레이 토큰 다시 열기") }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onEnroll, enabled = blockedReason == null, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF0071E3))) {
                    Text("등록")
                }
                Button(onClick = onAuth, enabled = blockedReason == null, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF1D1D1F))) {
                    Text("인증")
                }
            }
            Button(onClick = onDelete, enabled = userCount > 0, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFFFF3B30))) {
                Text("등록 사용자 삭제")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Switch(checked = doorArmed, onCheckedChange = onDoorArmed)
                Text("인증 성공 시 HTTPS 릴레이 열기", color = ComposeColor(0xFF1D1D1F))
            }
            if (doorArmed && doorUrl.trim().isEmpty()) {
                Text("릴레이 URL을 입력해야 문 열림이 활성화됩니다", color = ComposeColor(0xFFFF3B30), fontSize = 13.sp)
            }
            OutlinedTextField(
                value = doorUrl,
                onValueChange = onDoorUrl,
                enabled = !doorArmed,
                label = { Text("HTTPS 릴레이 URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = doorToken,
                onValueChange = onDoorToken,
                enabled = !doorArmed,
                label = { Text("Bearer 토큰") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
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
        val bitmap = imageProxyToBitmap(proxy)
        try {
            if (firstAnalyzedFrameLogged.compareAndSet(false, true)) {
                Log.i("FFacio", "Camera analysis frame received")
            }
            val obs = engine.observe(bitmap)
            ContextCompat.getMainExecutor(context).execute {
                if (active.get()) onObservation(obs)
            }
        } finally {
            bitmap.recycle()
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

private fun imageProxyToBitmap(proxy: ImageProxy): Bitmap {
    if (proxy.format != PixelFormat.RGBA_8888) {
        error("Unexpected image format: ${proxy.format}")
    }
    val plane = proxy.planes.firstOrNull() ?: error("No image plane")
    if (plane.pixelStride != 4) error("Unexpected RGBA pixel stride: ${plane.pixelStride}")
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val buffer = plane.buffer.duplicate()
    val pixels = IntArray(proxy.width * proxy.height)
    for (y in 0 until proxy.height) {
        val rowStart = y * rowStride
        for (x in 0 until proxy.width) {
            val offset = rowStart + x * pixelStride
            if (offset + 3 >= buffer.limit()) error("RGBA buffer too small for ${proxy.width}x${proxy.height}")
            val r = buffer.get(offset).toInt() and 0xFF
            val g = buffer.get(offset + 1).toInt() and 0xFF
            val b = buffer.get(offset + 2).toInt() and 0xFF
            val a = buffer.get(offset + 3).toInt() and 0xFF
            pixels[y * proxy.width + x] = Color.argb(a, r, g, b)
        }
    }
    val bitmap = Bitmap.createBitmap(proxy.width, proxy.height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, proxy.width, 0, 0, proxy.width, proxy.height)
    val matrix = Matrix().apply {
        postRotate(proxy.imageInfo.rotationDegrees.toFloat())
        postScale(-1f, 1f)
    }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}

private class MobileFaceEngine(detectorModel: File, recognizerModel: File) {
    private val detector = FaceDetectorYN.create(detectorModel.absolutePath, "", Size(320.0, 320.0), 0.82f, 0.3f, 5000)
    private val recognizer = FaceRecognizerSF.create(recognizerModel.absolutePath, "")
    private var currentSize = Size(0.0, 0.0)

    fun observe(bitmap: Bitmap): Observation {
        val rgba = Mat()
        val bgr = Mat()
        val faces = Mat()
        val feature = Mat()
        var face: Mat? = null
        var aligned: Mat? = null
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            if (currentSize.width != bgr.cols().toDouble() || currentSize.height != bgr.rows().toDouble()) {
                currentSize = Size(bgr.cols().toDouble(), bgr.rows().toDouble())
                detector.inputSize = currentSize
            }
            detector.detect(bgr, faces)
            if (faces.empty()) return Observation.fail("얼굴을 찾을 수 없습니다")
            if (faces.rows() > 1) return Observation.fail("한 명만 카메라 앞에 서 주세요")
            val faceRow = faces.row(0)
            face = Mat()
            try {
                faceRow.convertTo(face, CvType.CV_32F)
            } finally {
                faceRow.release()
            }
            val row = FloatArray(face.total().toInt())
            face.get(0, 0, row)
            if ((row.getOrNull(2) ?: 0.0f) < bgr.cols().toFloat() * 0.16f) return Observation.fail("조금 더 가까이 와 주세요")
            aligned = Mat()
            runCatching {
                recognizer.alignCrop(bgr, face, aligned)
                if (aligned.empty()) error("SFace alignCrop returned an empty aligned face")
                recognizer.feature(aligned, feature)
                if (feature.empty()) error("SFace feature returned an empty embedding")
            }.getOrElse {
                Log.e("FFacio", "SFace alignment or feature extraction failed", it)
                return Observation.fail("얼굴 특징을 추출할 수 없습니다. 정면을 유지하고 다시 시도해 주세요")
            }
            return Observation(true, "확인 중", matToFloatArray(feature), estimatePose(row))
        } finally {
            aligned?.release()
            face?.release()
            feature.release()
            faces.release()
            bgr.release()
            rgba.release()
        }
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
        val asFloat = Mat()
        try {
            if (mat.type() != CvType.CV_32F) mat.convertTo(asFloat, CvType.CV_32F) else mat.copyTo(asFloat)
            Core.normalize(asFloat, asFloat)
            val data = FloatArray(asFloat.total().toInt())
            asFloat.get(0, 0, data)
            return data
        } finally {
            asFloat.release()
        }
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

    fun prompt(): String = if (index >= targets.size) "라이브니스 확인 완료" else "${poseLabel(targets[index])}을 보고 잠시 유지해 주세요"
}

private enum class AppMode { Auth, Enroll }
private enum class AdminAction { StartEnroll, DeleteUsers, UnlockStore, ResetStore, ArmDoor, UnlockDoor }
private sealed class ModelLoadState {
    data object Loading : ModelLoadState()
    data object Ready : ModelLoadState()
    data class Failed(val error: Throwable) : ModelLoadState()
}
private data class Observation(val ok: Boolean, val message: String, val embedding: FloatArray, val pose: Int) {
    companion object {
        fun fail(message: String) = Observation(false, message, FloatArray(0), 0)
    }
}
private data class UserTemplate(val name: String, val embedding: FloatArray)
private data class Match(val index: Int, val score: Double, val secondScore: Double)
private data class StoreLoadResult(val users: List<UserTemplate>, val error: Throwable?)

private fun MutableList<UserTemplate>.replaceWith(items: List<UserTemplate>) {
    clear()
    addAll(items)
}

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
    val encrypted = prefs.getString("$key$SECURE_SUFFIX", null)
    if (encrypted == null) return default
    return runCatching {
        val payload = Base64.decode(encrypted, Base64.NO_WRAP)
        decryptPayload(context, payload, KEYSTORE_ALIAS, authRequired = true)
    }.getOrElse {
        if (failClosed) throw IllegalStateException("Encrypted local store authentication failed", it)
        default
    }
}

private fun securePutString(context: Context, prefs: SharedPreferences, key: String, value: String) {
    val iv = ByteArray(12)
    SecureRandom().nextBytes(iv)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, keystoreKey(context, KEYSTORE_ALIAS, authRequired = true), GCMParameterSpec(128, iv))
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

private fun postDoor(url: String, token: String, user: String): Boolean = runCatching {
    val endpoint = URL(url)
    if (token.isNotBlank() && endpoint.protocol.lowercase() != "https") return@runCatching false
    val conn = (endpoint.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
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
