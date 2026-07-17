package com.ffacio.mobile

import com.kbyai.facesdk.FaceBox
import com.kbyai.facesdk.FaceDetectionParam
import kotlin.math.abs
import kotlin.math.max

/**
 * Single source of truth for the Runtime Demo-compatible camera and face policy.
 *
 * Keep these values aligned with Runtime Demo DemoSettings/RegistrationPipeline/CameraActivityKt.
 * FFacio keeps the Runtime Demo camera/options/template flow, while applying the product
 * requirements that are unsafe or impractical to copy literally: the largest face is selected,
 * authentication accepts one stable frame, unreliable eye/mouth attributes never block, and only
 * clearly severe head rotation is rejected.
 */
internal const val ANALYSIS_INTERVAL_MS = 180L
internal const val DEMO_CAPTURE_FRAME_WIDTH = 1280
internal const val DEMO_CAPTURE_FRAME_HEIGHT = 720
internal const val DEMO_IDENTIFICATION_FRAME_WIDTH = 640
internal const val DEMO_IDENTIFICATION_FRAME_HEIGHT = 480
internal const val ANTISPOOF_THRESHOLD = 0.70f
internal const val RUNTIME_QUALITY_THRESHOLD = 0.50f
internal const val RUNTIME_OCCLUSION_THRESHOLD = 0.50f
// The Demo exposes 10-degree pose settings, but those values caused false rejection on this
// camera/runtime combination. Pose is still enforced fail-closed at deliberately severe angles.
internal const val RUNTIME_SEVERE_MAX_YAW = 35.0f
internal const val RUNTIME_SEVERE_MAX_PITCH = 30.0f
internal const val RUNTIME_SEVERE_MAX_ROLL = 30.0f
internal const val RUNTIME_LUMINANCE_MIN = 0.0f
internal const val RUNTIME_LUMINANCE_MAX = 255.0f
internal const val RUNTIME_MIN_FACE_SIZE = 80
internal const val RUNTIME_MAX_FACE_SIZE = 1200
internal const val RUNTIME_MIN_FACE_AREA_RATIO = 0.03
internal const val ENROLL_AUTO_CAPTURE_STABLE_MS = 1200L
internal const val AUTH_STABLE_FRAMES = 1
internal const val RUNTIME_LANDMARK_VALUE_COUNT = 136

internal fun runtimeUnitScoreValid(value: Float): Boolean =
    value.isFinite() && value in 0.0f..1.0f

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
        if (!runtimeUnitScoreValid(face.face_quality) || face.face_quality < RUNTIME_QUALITY_THRESHOLD) {
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
        severePoseGuidance(face)?.let { guidance ->
            return RuntimeDemoFaceDecision(false, guidance)
        }
        // Runtime eye/mouth probabilities are retained for diagnostics but are intentionally not
        // registration gates. They produced repeated false "open your eyes" failures even when the
        // Demo camera feed itself was healthy.
        if (occlusionCheckEnabled &&
            (!runtimeUnitScoreValid(face.face_occlusion) || face.face_occlusion > RUNTIME_OCCLUSION_THRESHOLD)
        ) {
            return RuntimeDemoFaceDecision(false, "얼굴을 가리는 물체를 치워 주세요")
        }
        if (passiveLivenessEnabled &&
            (!runtimeUnitScoreValid(face.liveness) || face.liveness < ANTISPOOF_THRESHOLD)
        ) {
            return RuntimeDemoFaceDecision(
                accepted = false,
                message = "사진이나 다른 화면을 사용하지 말고 실제 얼굴을 카메라에 보여 주세요",
                liveScore = face.liveness.takeIf { it.isFinite() } ?: 0f,
                liveState = "runtime_rejected"
            )
        }
    } else {
        // Runtime Demo authentication gates plus FFacio severe-pose and optional-occlusion safety checks.
        if (passiveLivenessEnabled &&
            (!runtimeUnitScoreValid(face.liveness) || face.liveness < ANTISPOOF_THRESHOLD)
        ) {
            return RuntimeDemoFaceDecision(
                accepted = false,
                message = "사진이나 다른 화면을 사용하지 말고 실제 얼굴을 카메라에 보여 주세요",
                liveScore = face.liveness.takeIf { it.isFinite() } ?: 0f,
                liveState = "runtime_rejected"
            )
        }
        if (!runtimeUnitScoreValid(face.face_quality) || face.face_quality < RUNTIME_QUALITY_THRESHOLD) {
            return RuntimeDemoFaceDecision(false, "얼굴 품질이 낮습니다. 조명과 초점을 맞춰 주세요")
        }
        if (faceAreaRatio < RUNTIME_MIN_FACE_AREA_RATIO) {
            return RuntimeDemoFaceDecision(false, "조금 더 가까이 와 주세요")
        }
        severePoseGuidance(face)?.let { guidance ->
            return RuntimeDemoFaceDecision(false, guidance)
        }
        if (occlusionCheckEnabled &&
            (!runtimeUnitScoreValid(face.face_occlusion) || face.face_occlusion > RUNTIME_OCCLUSION_THRESHOLD)
        ) {
            return RuntimeDemoFaceDecision(false, "얼굴을 가리는 물체를 치워 주세요")
        }
    }
    return RuntimeDemoFaceDecision(
        accepted = true,
        message = "확인 중",
        liveScore = if (passiveLivenessEnabled) face.liveness else 1f,
        liveState = if (passiveLivenessEnabled) "runtime_live" else "disabled"
    )
}

internal fun severePoseGuidance(face: FaceBox): String? {
    if (!face.yaw.isFinite() || !face.pitch.isFinite() || !face.roll.isFinite()) {
        return "얼굴 방향을 안정적으로 확인하지 못했습니다"
    }
    if (abs(face.yaw) > RUNTIME_SEVERE_MAX_YAW) {
        return "고개가 너무 돌아가 있습니다. 얼굴을 정면으로 맞춰 주세요"
    }
    if (abs(face.pitch) > RUNTIME_SEVERE_MAX_PITCH) {
        return "고개가 너무 숙여지거나 들려 있습니다. 얼굴을 정면으로 맞춰 주세요"
    }
    if (abs(face.roll) > RUNTIME_SEVERE_MAX_ROLL) {
        return "고개가 너무 기울어져 있습니다. 얼굴을 수평으로 맞춰 주세요"
    }
    return null
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
