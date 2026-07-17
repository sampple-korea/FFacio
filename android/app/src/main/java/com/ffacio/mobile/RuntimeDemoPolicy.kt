package com.ffacio.mobile

import com.kbyai.facesdk.FaceBox
import com.kbyai.facesdk.FaceDetectionParam
import kotlin.math.abs
import kotlin.math.max

/**
 * Single source of truth for the Runtime Demo-compatible camera and face policy.
 *
 * Keep these values aligned with Runtime Demo DemoSettings/RegistrationPipeline/CameraActivityKt.
 * FFacio intentionally differs only in two requested places:
 * 1) the largest face is selected when several faces are visible;
 * 2) authentication accepts one stable frame.
 */
internal const val ANALYSIS_INTERVAL_MS = 180L
internal const val DEMO_CAPTURE_FRAME_WIDTH = 1280
internal const val DEMO_CAPTURE_FRAME_HEIGHT = 720
internal const val DEMO_IDENTIFICATION_FRAME_WIDTH = 640
internal const val DEMO_IDENTIFICATION_FRAME_HEIGHT = 480
internal const val ANTISPOOF_THRESHOLD = 0.70f
internal const val RUNTIME_QUALITY_THRESHOLD = 0.50f
internal const val RUNTIME_EYE_CLOSED_THRESHOLD = 0.80f
internal const val RUNTIME_OCCLUSION_THRESHOLD = 0.50f
internal const val RUNTIME_MOUTH_OPEN_THRESHOLD = 0.50f
internal const val RUNTIME_MAX_YAW = 10.0f
internal const val RUNTIME_MAX_PITCH = 10.0f
internal const val RUNTIME_MAX_ROLL = 10.0f
internal const val RUNTIME_LUMINANCE_MIN = 0.0f
internal const val RUNTIME_LUMINANCE_MAX = 255.0f
internal const val RUNTIME_MIN_FACE_SIZE = 80
internal const val RUNTIME_MAX_FACE_SIZE = 1200
internal const val RUNTIME_MIN_FACE_AREA_RATIO = 0.03
internal const val ENROLL_AUTO_CAPTURE_STABLE_MS = 1200L
internal const val AUTH_STABLE_FRAMES = 1
internal const val RUNTIME_LANDMARK_VALUE_COUNT = 136

/**
 * Selects a safe fallback when the exact Runtime Demo preview size is unavailable.
 * Aspect ratio dominates area so a rotated 720x1280 stream cannot beat a landscape
 * 16:9 stream merely because both contain the same number of pixels.
 */
internal fun runtimeDemoResolutionCost(
    width: Int,
    height: Int,
    targetWidth: Int,
    targetHeight: Int
): Double {
    if (width <= 0 || height <= 0 || targetWidth <= 0 || targetHeight <= 0) {
        return Double.POSITIVE_INFINITY
    }
    val aspect = width.toDouble() / height.toDouble()
    val targetAspect = targetWidth.toDouble() / targetHeight.toDouble()
    val area = width.toDouble() * height.toDouble()
    val targetArea = targetWidth.toDouble() * targetHeight.toDouble()
    val aspectError = abs(aspect - targetAspect)
    val areaError = abs(area - targetArea) / targetArea
    return aspectError * 8.0 + areaError
}

internal fun runtimeNativeOrientation(rotationDegrees: Int, frontFacing: Boolean): Int {
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

internal fun sanitizeRuntimeLivenessLevel(value: Int): Int = if (value == 1) 1 else 0

internal fun runtimeDetectionOptions(
    passiveLivenessEnabled: Boolean,
    livenessLevel: Int,
    occlusionCheckEnabled: Boolean
): FaceDetectionParam = FaceDetectionParam().apply {
    check_liveness = passiveLivenessEnabled
    check_liveness_level = sanitizeRuntimeLivenessLevel(livenessLevel)
    check_eye_closeness = true
    check_face_occlusion = occlusionCheckEnabled
    check_mouth_opened = true
    estimate_age_gender = false
}

internal fun largestRuntimeFace(faces: List<FaceBox>): FaceBox? = faces.maxByOrNull { face ->
    val width = (face.x2 - face.x1).coerceAtLeast(0)
    val height = (face.y2 - face.y1).coerceAtLeast(0)
    width.toLong() * height.toLong()
}

internal data class RuntimeDemoFaceDecision(
    val accepted: Boolean,
    val message: String,
    val liveScore: Float = 0.0f,
    val liveState: String = "unknown"
)

/** Pure Runtime Demo gate shared by live code and local unit tests. */
internal fun evaluateRuntimeDemoFace(
    face: FaceBox,
    frameWidth: Int,
    frameHeight: Int,
    enrollmentMode: Boolean,
    passiveLivenessEnabled: Boolean,
    occlusionCheckEnabled: Boolean
): RuntimeDemoFaceDecision {
    val faceWidth = (face.x2 - face.x1).coerceAtLeast(0)
    val faceHeight = (face.y2 - face.y1).coerceAtLeast(0)
    val faceSize = max(faceWidth, faceHeight)
    val frameArea = (frameWidth.toDouble() * frameHeight.toDouble()).coerceAtLeast(1.0)
    val faceAreaRatio = faceWidth.toDouble() * faceHeight.toDouble() / frameArea

    if (enrollmentMode) {
        // RegistrationPipeline order from the Runtime Demo.
        if (face.x2 <= face.x1 || face.y2 <= face.y1) {
            return RuntimeDemoFaceDecision(false, "Runtime 얼굴 좌표가 올바르지 않습니다")
        }
        if (face.landmarks_68?.size != RUNTIME_LANDMARK_VALUE_COUNT) {
            return RuntimeDemoFaceDecision(false, "얼굴 특징점을 안정적으로 찾지 못했습니다")
        }
        if (faceSize < RUNTIME_MIN_FACE_SIZE) {
            return RuntimeDemoFaceDecision(false, "조금 더 가까이 와 주세요")
        }
        if (faceSize > RUNTIME_MAX_FACE_SIZE) {
            return RuntimeDemoFaceDecision(false, "조금 뒤로 이동해 주세요")
        }
        runtimeDemoCenterGuidance(face, frameWidth, frameHeight)?.let { guidance ->
            return RuntimeDemoFaceDecision(false, guidance)
        }
        if (!face.face_quality.isFinite() || face.face_quality < RUNTIME_QUALITY_THRESHOLD) {
            return RuntimeDemoFaceDecision(false, "얼굴이 흐리거나 조건이 좋지 않습니다. 흔들림을 줄이고 선명하게 촬영하세요")
        }
        if (!face.face_luminance.isFinite() ||
            face.face_luminance !in RUNTIME_LUMINANCE_MIN..RUNTIME_LUMINANCE_MAX
        ) {
            return RuntimeDemoFaceDecision(
                false,
                if (face.face_luminance.isFinite() && face.face_luminance < RUNTIME_LUMINANCE_MIN) {
                    "얼굴이 어둡습니다. 조명을 밝히고 얼굴에 빛이 고르게 닿게 하세요"
                } else {
                    "얼굴이 지나치게 밝거나 역광입니다. 강한 빛을 피하고 위치를 바꿔 주세요"
                }
            )
        }
        if (!face.yaw.isFinite() || kotlin.math.abs(face.yaw) > RUNTIME_MAX_YAW) {
            return RuntimeDemoFaceDecision(
                false,
                if (face.yaw.isFinite() && face.yaw > 0f) {
                    "얼굴을 왼쪽으로 돌려 정면을 맞춰 주세요"
                } else {
                    "얼굴을 오른쪽으로 돌려 정면을 맞춰 주세요"
                }
            )
        }
        if (!face.pitch.isFinite() || kotlin.math.abs(face.pitch) > RUNTIME_MAX_PITCH) {
            return RuntimeDemoFaceDecision(
                false,
                if (face.pitch.isFinite() && face.pitch > 0f) "고개를 조금 들어 주세요" else "고개를 조금 내려 주세요"
            )
        }
        if (!face.roll.isFinite() || kotlin.math.abs(face.roll) > RUNTIME_MAX_ROLL) {
            return RuntimeDemoFaceDecision(false, "고개 기울기를 바로잡아 수평을 맞춰 주세요")
        }
        if (!face.left_eye_closed.isFinite() || face.left_eye_closed > RUNTIME_EYE_CLOSED_THRESHOLD) {
            return RuntimeDemoFaceDecision(false, "왼쪽 눈을 떠 주세요")
        }
        if (!face.right_eye_closed.isFinite() || face.right_eye_closed > RUNTIME_EYE_CLOSED_THRESHOLD) {
            return RuntimeDemoFaceDecision(false, "오른쪽 눈을 떠 주세요")
        }
        if (occlusionCheckEnabled &&
            (!face.face_occlusion.isFinite() || face.face_occlusion > RUNTIME_OCCLUSION_THRESHOLD)
        ) {
            return RuntimeDemoFaceDecision(false, "얼굴을 가리는 물체를 치워 주세요")
        }
        if (!face.mouth_opened.isFinite() || face.mouth_opened > RUNTIME_MOUTH_OPEN_THRESHOLD) {
            return RuntimeDemoFaceDecision(false, "입을 다물어 주세요")
        }
        if (passiveLivenessEnabled &&
            (!face.liveness.isFinite() || face.liveness < ANTISPOOF_THRESHOLD)
        ) {
            return RuntimeDemoFaceDecision(
                accepted = false,
                message = "사진이나 다른 화면을 사용하지 말고 실제 얼굴을 카메라에 보여 주세요",
                liveScore = face.liveness.takeIf { it.isFinite() } ?: 0f,
                liveState = "runtime_rejected"
            )
        }
    } else {
        // CameraActivityKt authentication policy: liveness, quality and area only.
        if (passiveLivenessEnabled &&
            (!face.liveness.isFinite() || face.liveness < ANTISPOOF_THRESHOLD)
        ) {
            return RuntimeDemoFaceDecision(
                accepted = false,
                message = "사진이나 다른 화면을 사용하지 말고 실제 얼굴을 카메라에 보여 주세요",
                liveScore = face.liveness.takeIf { it.isFinite() } ?: 0f,
                liveState = "runtime_rejected"
            )
        }
        if (!face.face_quality.isFinite() || face.face_quality < RUNTIME_QUALITY_THRESHOLD) {
            return RuntimeDemoFaceDecision(false, "얼굴 품질이 낮습니다. 조명과 초점을 맞춰 주세요")
        }
        if (faceAreaRatio < RUNTIME_MIN_FACE_AREA_RATIO) {
            return RuntimeDemoFaceDecision(false, "조금 더 가까이 와 주세요")
        }
    }
    return RuntimeDemoFaceDecision(
        accepted = true,
        message = "확인 중",
        liveScore = if (passiveLivenessEnabled) face.liveness else 1f,
        liveState = if (passiveLivenessEnabled) "runtime_live" else "disabled"
    )
}

internal fun runtimeDemoCenterGuidance(face: FaceBox, frameWidth: Int, frameHeight: Int): String? {
    if (frameWidth <= 0 || frameHeight <= 0) return "얼굴을 안내 영역 중앙에 맞춰 주세요"
    // CaptureActivityKt passes CaptureView.getROIRect1(): a centered square whose
    // width is two thirds of the oriented frame width.
    val targetWidth = frameWidth * 2.0f / 3.0f
    val targetHeight = targetWidth
    val left = (frameWidth - targetWidth) / 2.0f
    val top = (frameHeight - targetHeight) / 2.0f
    val right = (frameWidth + targetWidth) / 2.0f
    val bottom = (frameHeight + targetHeight) / 2.0f
    val centerX = (face.x1 + face.x2) / 2.0f
    val centerY = (face.y1 + face.y2) / 2.0f
    val horizontal = when {
        centerX < left -> "오른쪽"
        centerX > right -> "왼쪽"
        else -> null
    }
    val vertical = when {
        centerY < top -> "아래쪽"
        centerY > bottom -> "위쪽"
        else -> null
    }
    val direction = listOfNotNull(horizontal, vertical).joinToString(" · ")
    return direction.takeIf { it.isNotEmpty() }?.let {
        "얼굴을 $it 방향으로 이동해 안내 영역 중앙에 맞춰 주세요"
    }
}

internal fun isRuntimeFaceCentered(face: FaceBox, frameWidth: Int, frameHeight: Int): Boolean =
    runtimeDemoCenterGuidance(face, frameWidth, frameHeight) == null
