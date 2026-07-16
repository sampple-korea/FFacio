package com.ffacio.mobile

import com.kbyai.facesdk.FaceSDK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeDiagnosticsTest {

    @Test
    fun sanitizeRuntimeLivenessLevelClampsUnknownValues() {
        assertEquals(0, sanitizeRuntimeLivenessLevel(0))
        assertEquals(1, sanitizeRuntimeLivenessLevel(1))
        assertEquals(0, sanitizeRuntimeLivenessLevel(-1))
        assertEquals(0, sanitizeRuntimeLivenessLevel(2))
        assertEquals(0, sanitizeRuntimeLivenessLevel(Int.MAX_VALUE))
    }

    @Test
    fun detectionOptionsExcludeDisabledChecksFromRuntimeRequest() {
        val options = runtimeDetectionOptions(
            passiveLivenessEnabled = false,
            livenessLevel = 1,
            occlusionCheckEnabled = false,
            enrollmentMode = false
        )
        assertFalse(options.check_liveness)
        assertFalse(options.check_face_occlusion)
        assertFalse(options.estimate_age_gender)
        assertTrue(options.check_eye_closeness)
        assertTrue(options.check_mouth_opened)
        assertEquals(1, options.check_liveness_level)
    }

    @Test
    fun detectionOptionsEnableRequestedChecks() {
        val options = runtimeDetectionOptions(
            passiveLivenessEnabled = true,
            livenessLevel = 7,
            occlusionCheckEnabled = true,
            enrollmentMode = true
        )
        assertTrue(options.check_liveness)
        assertTrue(options.check_face_occlusion)
        assertTrue(options.estimate_age_gender)
        assertEquals(0, options.check_liveness_level)
    }

    @Test
    fun connectionStateLabelDistinguishesPhases() {
        assertEquals("준비됨", runtimeConnectionStateLabel(connected = true, connecting = false, ready = true))
        assertEquals("연결 중", runtimeConnectionStateLabel(connected = false, connecting = true, ready = false))
        assertEquals("Binder 연결됨 · 초기화 확인 중", runtimeConnectionStateLabel(connected = true, connecting = false, ready = false))
        assertEquals("연결 안 됨", runtimeConnectionStateLabel(connected = false, connecting = false, ready = false))
    }

    @Test
    fun disconnectReasonLabelsCoverAllReasons() {
        val labels = FaceSDK.DisconnectReason.values().map { runtimeDisconnectReasonLabel(it) }
        assertEquals(labels.size, labels.toSet().size)
        assertTrue(labels.all { it.isNotBlank() })
    }

    @Test
    fun initializationLabelOnlyTreatsZeroAsSuccess() {
        assertEquals("초기화 전", runtimeInitializationLabel(Int.MIN_VALUE))
        assertEquals("성공(0)", runtimeInitializationLabel(FaceSDK.SDK_SUCCESS))
        assertEquals("실패(-3)", runtimeInitializationLabel(FaceSDK.SDK_LICENSE_EXPIRED))
        assertEquals("실패(42)", runtimeInitializationLabel(42))
    }

    @Test
    fun timingSummaryHandlesMissingAndPresentMeasurements() {
        assertEquals("아직 측정된 프레임이 없습니다", runtimeTimingSummary(null))
        val summary = runtimeTimingSummary(RuntimeCallTimings(convertMillis = 4, detectMillis = 120, templateMillis = 35))
        assertTrue(summary.contains("YUV 변환 4ms"))
        assertTrue(summary.contains("검출+속성 120ms"))
        assertTrue(summary.contains("템플릿 35ms"))
        assertTrue(summary.contains("합계 159ms"))
    }

    @Test
    fun runtimePackageLabelDistinguishesInstallState() {
        assertEquals("미설치", runtimePackageLabel(RuntimePackageStatus(false, "", 0L)))
        assertEquals("2.0.1 (3)", runtimePackageLabel(RuntimePackageStatus(true, "2.0.1", 3L)))
    }
}
