package com.ffacio.mobile

import com.kbyai.facesdk.FaceBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeDemoAlignmentTest {
    @Test
    fun frontAndBackOrientationCodesMatchRuntimeDemoExactly() {
        assertEquals(2, runtimeNativeOrientation(0, frontFacing = true))
        assertEquals(7, runtimeNativeOrientation(90, frontFacing = true))
        assertEquals(4, runtimeNativeOrientation(180, frontFacing = true))
        assertEquals(5, runtimeNativeOrientation(270, frontFacing = true))

        assertEquals(1, runtimeNativeOrientation(0, frontFacing = false))
        assertEquals(6, runtimeNativeOrientation(90, frontFacing = false))
        assertEquals(3, runtimeNativeOrientation(180, frontFacing = false))
        assertEquals(8, runtimeNativeOrientation(270, frontFacing = false))
    }

    @Test
    fun neutralFaceAtDemoThresholdsCanEnroll() {
        val decision = evaluateRuntimeDemoFace(
            face = validFace().apply {
                face_quality = 0.5f
                face_luminance = 0f
                yaw = 10f
                pitch = -10f
                roll = 10f
                left_eye_closed = 0.8f
                right_eye_closed = 0.8f
                mouth_opened = 0.5f
                liveness = 0.7f
            },
            frameWidth = 640,
            frameHeight = 480,
            enrollmentMode = true,
            passiveLivenessEnabled = true,
            occlusionCheckEnabled = false
        )

        assertTrue(decision.accepted)
        assertEquals("runtime_live", decision.liveState)
    }

    @Test
    fun enrollmentUsesDemoPoseAndEyeThresholds() {
        val turned = evaluateRuntimeDemoFace(
            face = validFace().apply { yaw = 10.01f },
            frameWidth = 640,
            frameHeight = 480,
            enrollmentMode = true,
            passiveLivenessEnabled = true,
            occlusionCheckEnabled = false
        )
        assertFalse(turned.accepted)
        assertEquals("얼굴을 왼쪽으로 돌려 정면을 맞춰 주세요", turned.message)

        val closedEye = evaluateRuntimeDemoFace(
            face = validFace().apply { left_eye_closed = 0.81f },
            frameWidth = 640,
            frameHeight = 480,
            enrollmentMode = true,
            passiveLivenessEnabled = true,
            occlusionCheckEnabled = false
        )
        assertFalse(closedEye.accepted)
        assertEquals("왼쪽 눈을 떠 주세요", closedEye.message)
    }

    @Test
    fun authenticationMatchesDemoAndDoesNotGateOnPoseEyesOrMouth() {
        val decision = evaluateRuntimeDemoFace(
            face = validFace().apply {
                yaw = 80f
                pitch = -70f
                roll = 50f
                left_eye_closed = 1f
                right_eye_closed = 1f
                mouth_opened = 1f
            },
            frameWidth = 640,
            frameHeight = 480,
            enrollmentMode = false,
            passiveLivenessEnabled = true,
            occlusionCheckEnabled = true
        )

        assertTrue(decision.accepted)
    }

    @Test
    fun authenticationUsesDemoLivenessQualityAndAreaOnly() {
        val spoof = evaluateRuntimeDemoFace(
            validFace().apply { liveness = 0.69f }, 640, 480,
            enrollmentMode = false, passiveLivenessEnabled = true, occlusionCheckEnabled = false
        )
        assertFalse(spoof.accepted)
        assertEquals("runtime_rejected", spoof.liveState)

        val lowQuality = evaluateRuntimeDemoFace(
            validFace().apply { face_quality = 0.49f }, 640, 480,
            enrollmentMode = false, passiveLivenessEnabled = true, occlusionCheckEnabled = false
        )
        assertFalse(lowQuality.accepted)
        assertTrue(lowQuality.message.contains("품질"))

        val tooSmall = evaluateRuntimeDemoFace(
            validFace().apply { x1 = 280; y1 = 195; x2 = 360; y2 = 275 }, 640, 480,
            enrollmentMode = false, passiveLivenessEnabled = true, occlusionCheckEnabled = false
        )
        assertFalse(tooSmall.accepted)
        assertEquals("조금 더 가까이 와 주세요", tooSmall.message)
    }

    @Test
    fun enrollmentCenterGuideMatchesDemoSquareRoi() {
        val centered = validFace().apply { x1 = 220; y1 = 540; x2 = 500; y2 = 820 }
        assertTrue(isRuntimeFaceCentered(centered, frameWidth = 720, frameHeight = 1280))

        val tooHigh = validFace().apply { x1 = 220; y1 = 0; x2 = 500; y2 = 200 }
        assertFalse(isRuntimeFaceCentered(tooHigh, frameWidth = 720, frameHeight = 1280))
        assertEquals(
            "얼굴을 아래쪽 방향으로 이동해 안내 영역 중앙에 맞춰 주세요",
            runtimeDemoCenterGuidance(tooHigh, 720, 1280)
        )
    }

    @Test
    fun resolutionFallbackPrefersDemoAspectRatioOverRotatedEqualAreaStream() {
        val correctOrientation = runtimeDemoResolutionCost(1280, 720, 1280, 720)
        val rotatedEqualArea = runtimeDemoResolutionCost(720, 1280, 1280, 720)
        assertEquals(0.0, correctOrientation, 0.0)
        assertTrue(correctOrientation < rotatedEqualArea)
    }

    @Test
    fun resolutionFallbackRejectsInvalidDimensions() {
        assertTrue(runtimeDemoResolutionCost(0, 720, 1280, 720).isInfinite())
        assertTrue(runtimeDemoResolutionCost(1280, -1, 1280, 720).isInfinite())
    }

    @Test
    fun detectionRequestMatchesRuntimeDemoAttributeSet() {
        val options = runtimeDetectionOptions(
            passiveLivenessEnabled = true,
            livenessLevel = 0,
            occlusionCheckEnabled = false
        )
        assertTrue(options.check_liveness)
        assertEquals(0, options.check_liveness_level)
        assertTrue(options.check_eye_closeness)
        assertFalse(options.check_face_occlusion)
        assertTrue(options.check_mouth_opened)
        assertFalse(options.estimate_age_gender)
    }

    private fun validFace() = FaceBox().apply {
        x1 = 220
        y1 = 140
        x2 = 420
        y2 = 340
        landmarks_68 = FloatArray(136)
        face_quality = 0.9f
        face_luminance = 128f
        yaw = 0f
        pitch = 0f
        roll = 0f
        left_eye_closed = 0.1f
        right_eye_closed = 0.1f
        face_occlusion = 0.1f
        mouth_opened = 0.1f
        liveness = 0.95f
    }
}
