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
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
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
private const val KEYSTORE_ALIAS = "ffacio_mobile_store_key"
private const val MATCH_THRESHOLD = 0.42
private const val ENROLL_SAMPLES = 5

class MainActivity : ComponentActivity() {
    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private val doorExecutor = Executors.newSingleThreadExecutor()
    private val processing = AtomicBoolean(false)
    private lateinit var prefs: SharedPreferences
    private var engine: MobileFaceEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val modelState = runCatching {
            if (!OpenCVLoader.initDebug()) error("OpenCV init failed")
            engine = MobileFaceEngine(
                copyAsset("models/opencv/face_detection_yunet_2023mar.onnx"),
                copyAsset("models/opencv/face_recognition_sface_2021dec.onnx")
            )
        }.exceptionOrNull()

        setContent {
            FFacioTheme {
                FFacioApp(
                    modelError = modelState,
                    prefs = prefs,
                    engineProvider = { engine },
                    analyzerExecutor = analyzerExecutor,
                    doorExecutor = doorExecutor,
                    processing = processing
                )
            }
        }
    }

    override fun onDestroy() {
        analyzerExecutor.shutdownNow()
        doorExecutor.shutdownNow()
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
    modelError: Throwable?,
    prefs: SharedPreferences,
    engineProvider: () -> MobileFaceEngine?,
    analyzerExecutor: ExecutorService,
    doorExecutor: ExecutorService,
    processing: AtomicBoolean
) {
    val context = LocalContext.current
    val users = remember { mutableStateListOf<UserTemplate>() }
    var status by remember { mutableStateOf("시스템 준비 중") }
    var detail by remember { mutableStateOf("모델과 카메라를 확인하고 있습니다") }
    var mode by remember { mutableStateOf(AppMode.Auth) }
    var name by remember { mutableStateOf("") }
    var doorUrl by remember { mutableStateOf(prefs.getString(DOOR_URL_KEY, "") ?: "") }
    var doorToken by remember { mutableStateOf(secureGetString(context, prefs, DOOR_TOKEN_KEY, "", failClosed = false)) }
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
                    enrollSamples.clear()
                    liveCandidate = -1
                    stableUser = -1
                    stableCount = 0
                    liveness.reset()
                    status = "ì–¼êµ´ì„ ì¤‘ì•™ì— ë§žì¶°ì£¼ì„¸ìš”"
                    detail = "ì—¬ëŸ¬ ê°ë„ì˜ ìƒ˜í”Œì„ ìžë™ìœ¼ë¡œ ìˆ˜ì§‘í•©ë‹ˆë‹¤"
                }
                AdminAction.DeleteUsers -> {
                    users.clear()
                    saveUsers(context, prefs, users)
                    liveCandidate = -1
                    stableUser = -1
                    stableCount = 0
                    liveness.reset()
                    confirmDelete = false
                    status = "ë“±ë¡ ì‚¬ìš©ìžë¥¼ ì‚­ì œí–ˆìŠµë‹ˆë‹¤"
                    detail = "ìƒˆ ì‚¬ìš©ìž ë“±ë¡ì„ ì‹œìž‘í•  ìˆ˜ ìžˆìŠµë‹ˆë‹¤"
                }
                AdminAction.ArmDoor -> {
                    doorArmed = true
                    prefs.edit()
                        .putString(DOOR_URL_KEY, doorUrl.trim())
                        .putBoolean(DOOR_ARMED_KEY, doorArmed)
                        .apply()
                    securePutString(context, prefs, DOOR_TOKEN_KEY, doorToken.trim())
                    status = "ë¦´ë ˆì´ ì—´ê¸°ê°€ í™œì„±í™”ë˜ì—ˆìŠµë‹ˆë‹¤"
                    detail = "ì¸ì¦ê³¼ ë¼ì´ë¸Œë‹ˆìŠ¤ê°€ ëª¨ë‘ í†µê³¼í•œ ê²½ìš°ì—ë§Œ ìš”ì²­í•©ë‹ˆë‹¤"
                }
            }
        } else {
            status = "ê´€ë¦¬ìž í™•ì¸ì´ ì·¨ì†Œë˜ì—ˆìŠµë‹ˆë‹¤"
            detail = "ë“±ë¡, ì‚­ì œ, ë¬¸ ì œì–´ í™œì„±í™”ì€ ê¸°ê¸° ìž ê¸ˆ í™•ì¸ì´ í•„ìš”í•©ë‹ˆë‹¤"
        }
    }

    LaunchedEffect(Unit) {
        users.replaceWith(loadUsers(context, prefs))
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

    fun persistDoor() {
        prefs.edit()
            .putString(DOOR_URL_KEY, doorUrl.trim())
            .putBoolean(DOOR_ARMED_KEY, doorArmed)
            .apply()
        securePutString(context, prefs, DOOR_TOKEN_KEY, doorToken.trim())
    }

    fun resetTransient() {
        liveCandidate = -1
        stableUser = -1
        stableCount = 0
        liveness.reset()
    }

    fun requestAdmin(action: AdminAction) {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure) {
            status = "ê¸°ê¸° ìž ê¸ˆì´ í•„ìš”í•©ë‹ˆë‹¤"
            detail = "ë“±ë¡, ì‚­ì œ, ë¦´ë ˆì´ í™œì„±í™”ì„ ìœ„í•´ Android í™”ë©´ ìž ê¸ˆì„ ë¨¼ì € ì„¤ì •í•˜ì„¸ìš”"
            return
        }
        val prompt = keyguard.createConfirmDeviceCredentialIntent(
            "FFacio ê´€ë¦¬ìž í™•ì¸",
            "ë¡œì»¬ ìƒì²´ í…œí”Œë¦¿ê³¼ ë¬¸ ì œì–´ ì„¤ì •ì„ ë³´í˜¸í•©ë‹ˆë‹¤"
        )
        if (prompt == null) {
            status = "ê´€ë¦¬ìž í™•ì¸ì„ ì‹œìž‘í•  ìˆ˜ ì—†ìŠµë‹ˆë‹¤"
            detail = "Android ë³´ì•ˆ ì„¤ì •ì„ í™•ì¸í•œ ë’¤ ë‹¤ì‹œ ì‹œë„í•˜ì„¸ìš”"
            return
        }
        pendingAdminAction = action
        adminLauncher.launch(prompt)
    }

    fun blockedReason(): String? = when {
        modelError != null -> "모델을 사용할 수 없습니다"
        !hasCameraPermission -> "카메라 권한이 필요합니다"
        !cameraAvailable -> "카메라를 사용할 수 없습니다"
        else -> null
    }

    fun openDoor(user: UserTemplate) {
        val now = System.currentTimeMillis()
        if (!doorArmed || now - lastOpenAt < 3500L) return
        if (doorUrl.trim().isEmpty()) {
            status = "문 제어가 설정되지 않았습니다"
            detail = "HTTP 릴레이 URL을 입력하거나 비활성 상태로 사용하세요"
            return
        }
        lastOpenAt = now
        persistDoor()
        status = "문 제어를 요청하는 중입니다"
        detail = "릴레이 응답을 기다리고 있습니다"
        doorExecutor.execute {
            val ok = postDoor(doorUrl.trim(), doorToken.trim(), user.name)
            ContextCompat.getMainExecutor(context).execute {
                status = if (ok) "문 열림 요청 완료" else "문 제어 실패"
                detail = if (ok) "릴레이가 요청을 수락했습니다" else "URL, 토큰, 네트워크를 확인해 주세요"
            }
        }
    }

    fun onObservation(obs: Observation) {
        if (modelError != null) return
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
                val cleanName = name.trim()
                if (cleanName.isNotEmpty()) {
                    users.add(UserTemplate(cleanName, average(enrollSamples)))
                    saveUsers(context, prefs, users)
                    mode = AppMode.Auth
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
                enabled = modelError == null && hasCameraPermission,
                cameraRetryNonce = cameraRetryNonce,
                stageMessage = blockedReason() ?: "카메라 준비 중",
                engineProvider = engineProvider,
                analyzerExecutor = analyzerExecutor,
                processing = processing,
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
                onName = { name = it },
                onDoorUrl = { doorUrl = it; persistDoor() },
                onDoorToken = { doorToken = it; persistDoor() },
                onDoorArmed = {
                    if (it && doorUrl.trim().isEmpty()) {
                        doorArmed = false
                        persistDoor()
                        status = "릴레이 URL이 필요합니다"
                        detail = "문 열림을 활성화하려면 HTTP 릴레이 URL을 먼저 입력하세요"
                    } else if (it) {
                        requestAdmin(AdminAction.ArmDoor)
                    } else {
                        doorArmed = false
                        persistDoor()
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
                        return@enroll
                        enrollSamples.clear()
                        resetTransient()
                        status = "얼굴을 중앙에 맞춰주세요"
                        detail = "여러 각도의 샘플을 자동으로 수집합니다"
                    }
                },
                onAuth = auth@{
                    blockedReason()?.let {
                        status = it
                        detail = "상태를 해결한 뒤 다시 시도해 주세요"
                        return@auth
                    }
                    mode = AppMode.Auth
                    enrollSamples.clear()
                    resetTransient()
                    status = if (users.isEmpty()) "먼저 얼굴을 등록하세요" else "인증 모드"
                    detail = if (users.isEmpty()) "등록 사용자 0명" else "카메라를 바라봐 주세요"
                },
                onDelete = {
                    confirmDelete = true
                },
                onRetry = {
                    cameraAvailable = true
                    cameraRetryNonce += 1
                    if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
                    status = "카메라를 다시 확인하는 중입니다"
                    detail = "잠시만 기다려 주세요"
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
                    return@TextButton
                    saveUsers(context, prefs, users)
                    resetTransient()
                    confirmDelete = false
                    status = "등록 사용자를 삭제했습니다"
                    detail = "새 사용자 등록을 시작할 수 있습니다"
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
        if (enabled) {
            providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                val provider = providerFuture.get()
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
                            analyzeProxy(proxy, engineProvider, processing, context, onObservation)
                        }
                    }
                provider.unbindAll()
                val selector = runCatching {
                    when {
                        provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                        provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                        else -> null
                    }
                }.getOrNull()
                if (selector == null) {
                    onCameraUnavailable()
                } else {
                    runCatching {
                        provider.bindToLifecycle(context as ComponentActivity, selector, preview, analysis)
                    }.onFailure {
                        onCameraUnavailable()
                    }
                }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
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
    onName: (String) -> Unit,
    onDoorUrl: (String) -> Unit,
    onDoorToken: (String) -> Unit,
    onDoorArmed: (Boolean) -> Unit,
    onEnroll: () -> Unit,
    onAuth: () -> Unit,
    onDelete: () -> Unit,
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
            if (blockedReason != null) {
                Surface(color = ComposeColor(0xFFFFF4E5), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(blockedReason, color = ComposeColor(0xFF8A4B00), fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("다시 확인") }
                            Button(onClick = onOpenSettings, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF1D1D1F))) { Text("앱 설정") }
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
            Button(onClick = onDelete, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFFFF3B30))) {
                Text("등록 사용자 삭제")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Switch(checked = doorArmed, onCheckedChange = onDoorArmed)
                Text("인증 성공 시 HTTP 릴레이 열기", color = ComposeColor(0xFF1D1D1F))
            }
            if (doorArmed && doorUrl.trim().isEmpty()) {
                Text("릴레이 URL을 입력해야 문 열림이 활성화됩니다", color = ComposeColor(0xFFFF3B30), fontSize = 13.sp)
            }
            OutlinedTextField(
                value = doorUrl,
                onValueChange = onDoorUrl,
                label = { Text("HTTP 릴레이 URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = doorToken,
                onValueChange = onDoorToken,
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
    context: Context,
    onObservation: (Observation) -> Unit
) {
    if (!processing.compareAndSet(false, true)) {
        proxy.close()
        return
    }
    try {
        val engine = engineProvider() ?: return
        val bitmap = imageProxyToBitmap(proxy)
        try {
            val obs = engine.observe(bitmap)
            ContextCompat.getMainExecutor(context).execute { onObservation(obs) }
        } finally {
            bitmap.recycle()
        }
    } catch (_: Exception) {
        ContextCompat.getMainExecutor(context).execute {
            onObservation(Observation.fail("프레임을 분석할 수 없습니다"))
        }
    } finally {
        proxy.close()
        processing.set(false)
    }
}

private fun imageProxyToBitmap(proxy: ImageProxy): Bitmap {
    val plane = proxy.planes.firstOrNull() ?: error("No image plane")
    val bitmap = Bitmap.createBitmap(proxy.width, proxy.height, Bitmap.Config.ARGB_8888)
    plane.buffer.rewind()
    bitmap.copyPixelsFromBuffer(plane.buffer)
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
            face = faces.row(0)
            val row = DoubleArray(face.total().toInt())
            face.get(0, 0, row)
            if ((row.getOrNull(2) ?: 0.0) < bgr.cols() * 0.16) return Observation.fail("조금 더 가까이 와 주세요")
            aligned = Mat()
            recognizer.alignCrop(bgr, face, aligned)
            recognizer.feature(aligned, feature)
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

    private fun estimatePose(face: DoubleArray): Int {
        if (face.size < 15) return 0
        val mid = (face[4] + face[6]) / 2.0
        val eyeDistance = max(1.0, abs(face[6] - face[4]))
        val yaw = (face[8] - mid) / eyeDistance
        return when {
            yaw < -0.08 -> -1
            yaw > 0.08 -> 1
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
private enum class AdminAction { StartEnroll, DeleteUsers, ArmDoor }
private data class Observation(val ok: Boolean, val message: String, val embedding: FloatArray, val pose: Int) {
    companion object {
        fun fail(message: String) = Observation(false, message, FloatArray(0), 0)
    }
}
private data class UserTemplate(val name: String, val embedding: FloatArray)
private data class Match(val index: Int, val score: Double)

private fun MutableList<UserTemplate>.replaceWith(items: List<UserTemplate>) {
    clear()
    addAll(items)
}

private fun loadUsers(context: Context, prefs: SharedPreferences): List<UserTemplate> = runCatching {
    val raw = secureGetString(context, prefs, USERS_KEY, prefs.getString(USERS_KEY, "[]") ?: "[]", failClosed = true)
    val array = JSONArray(raw)
    buildList {
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val values = item.getJSONArray("embedding")
            val embedding = FloatArray(values.length()) { values.getDouble(it).toFloat() }
            add(UserTemplate(item.getString("name"), embedding))
        }
    }
}.getOrElse { emptyList() }

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
    val encrypted = prefs.getString("${key}_enc", null) ?: return default
    return runCatching {
        val payload = Base64.decode(encrypted, Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, 12)
        val cipherText = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(context), GCMParameterSpec(128, iv))
        String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }.getOrElse {
        if (failClosed) throw IllegalStateException("Encrypted local store authentication failed", it)
        default
    }
}

private fun securePutString(context: Context, prefs: SharedPreferences, key: String, value: String) {
    val iv = ByteArray(12)
    SecureRandom().nextBytes(iv)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, keystoreKey(context), GCMParameterSpec(128, iv))
    val cipherText = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    val payload = iv + cipherText
    prefs.edit()
        .putString("${key}_enc", Base64.encodeToString(payload, Base64.NO_WRAP))
        .remove(key)
        .apply()
}

private fun keystoreKey(context: Context): SecretKey {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    keyStore.getKey(KEYSTORE_ALIAS, null)?.let { return it as SecretKey }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    val spec = KeyGenParameterSpec.Builder(
        KEYSTORE_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .setUserAuthenticationRequired(false)
        .build()
    generator.init(spec)
    return generator.generateKey()
}

private fun match(embedding: FloatArray, users: List<UserTemplate>): Match {
    var best = -1.0
    var bestIndex = -1
    users.forEachIndexed { index, user ->
        val score = cosine(embedding, user.embedding)
        if (score > best) {
            best = score
            bestIndex = index
        }
    }
    return Match(bestIndex, best)
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
