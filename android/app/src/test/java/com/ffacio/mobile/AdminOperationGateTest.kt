package com.ffacio.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminOperationGateTest {
    @Test
    fun cameraAnalysisIsBlockedDuringAdminPrompt() {
        assertFalse(
            shouldAnalyzeCameraFrame(
                baseReady = true,
                adminPromptInFlight = true,
                isOperationScreen = true,
                isAdminScreen = false,
                isEnrollmentMode = false,
                hasIdleReason = false
            )
        )
    }

    @Test
    fun operationCanAnalyzeWhenReadyAndNotIdle() {
        assertTrue(
            shouldAnalyzeCameraFrame(
                baseReady = true,
                adminPromptInFlight = false,
                isOperationScreen = true,
                isAdminScreen = false,
                isEnrollmentMode = false,
                hasIdleReason = false
            )
        )
    }

    @Test
    fun headAdminFaceAuthorizesGeneralAdminActions() {
        val headAdmin = compatibleUser("head", isHeadAdmin = true)

        assertTrue(canAuthorizeAdminActionWithHeadAdminFace(AdminAction.OpenAdmin, listOf(headAdmin)))
        assertTrue(canAuthorizeAdminActionWithHeadAdminFace(AdminAction.StartEnroll, listOf(headAdmin)))
        assertTrue(canAuthorizeAdminActionWithHeadAdminFace(AdminAction.DeleteUser, listOf(headAdmin)))
        assertTrue(canAuthorizeAdminActionWithHeadAdminFace(AdminAction.DeleteUsers, listOf(headAdmin)))
        assertTrue(canAuthorizeAdminActionWithHeadAdminFace(AdminAction.ResetStore, listOf(headAdmin)))
        assertTrue(canAuthorizeAdminActionWithHeadAdminFace(AdminAction.ArmDoor, listOf(headAdmin)))
        assertTrue(canAuthorizeAdminActionWithHeadAdminFace(AdminAction.DisarmDoor, listOf(headAdmin)))
        assertTrue(canAuthorizeAdminActionWithHeadAdminFace(AdminAction.UnlockSmartThingsToken, listOf(headAdmin)))
        assertTrue(canAuthorizeAdminActionWithHeadAdminFace(AdminAction.UnlockStore, listOf(headAdmin)))
        assertTrue(canAuthorizeAdminActionWithHeadAdminFace(AdminAction.TestSmartThingsDoor, listOf(headAdmin)))
        assertTrue(canAuthorizeAdminActionWithHeadAdminFace(AdminAction.SetPassiveLiveness, listOf(headAdmin)))
        assertFalse(canAuthorizeAdminActionWithHeadAdminFace(AdminAction.SetHeadAdmin, listOf(headAdmin)))
        assertFalse(canAuthorizeAdminActionWithHeadAdminFace(AdminAction.ClearHeadAdmin, listOf(headAdmin)))
        assertFalse(
            canAuthorizeAdminActionWithHeadAdminFace(
                AdminAction.OpenAdmin,
                listOf(headAdmin),
                passiveLivenessEnabled = false
            )
        )
    }

    @Test
    fun androidLockIsRequiredForHeadAdminSetClearAndInitialSetup() {
        val regular = compatibleUser("regular", isHeadAdmin = false)
        val headAdmin = compatibleUser("head", isHeadAdmin = true)

        assertTrue(requiresAndroidLockForAdminAction(AdminAction.OpenAdmin, emptyList()))
        assertTrue(requiresAndroidLockForAdminAction(AdminAction.OpenAdmin, listOf(regular)))
        assertTrue(requiresAndroidLockForAdminAction(AdminAction.StartEnroll, listOf(regular)))
        assertFalse(requiresAndroidLockForAdminAction(AdminAction.OpenAdmin, listOf(headAdmin)))
        assertFalse(requiresAndroidLockForAdminAction(AdminAction.StartEnroll, listOf(headAdmin)))
        assertFalse(requiresAndroidLockForAdminAction(AdminAction.ResetStore, listOf(headAdmin)))
        assertFalse(requiresAndroidLockForAdminAction(AdminAction.DisarmDoor, listOf(headAdmin)))
        assertFalse(requiresAndroidLockForAdminAction(AdminAction.TestSmartThingsDoor, listOf(headAdmin)))
        assertFalse(requiresAndroidLockForAdminAction(AdminAction.SetPassiveLiveness, listOf(headAdmin)))
        assertTrue(requiresAndroidLockForAdminAction(AdminAction.SetHeadAdmin, listOf(headAdmin)))
        assertTrue(requiresAndroidLockForAdminAction(AdminAction.ClearHeadAdmin, listOf(headAdmin)))
    }

    @Test
    fun adminSessionRunsGeneralActionsImmediatelyButKeepsHeadAdminChangesLocked() {
        assertTrue(shouldRunAdminActionImmediatelyInAdminSession(AdminAction.StartEnroll, isAdminScreen = true))
        assertTrue(shouldRunAdminActionImmediatelyInAdminSession(AdminAction.DeleteUser, isAdminScreen = true))
        assertTrue(shouldRunAdminActionImmediatelyInAdminSession(AdminAction.DeleteUsers, isAdminScreen = true))
        assertTrue(shouldRunAdminActionImmediatelyInAdminSession(AdminAction.ResetStore, isAdminScreen = true))
        assertTrue(shouldRunAdminActionImmediatelyInAdminSession(AdminAction.ArmDoor, isAdminScreen = true))
        assertTrue(shouldRunAdminActionImmediatelyInAdminSession(AdminAction.DisarmDoor, isAdminScreen = true))
        assertTrue(shouldRunAdminActionImmediatelyInAdminSession(AdminAction.TestSmartThingsDoor, isAdminScreen = true))
        assertTrue(shouldRunAdminActionImmediatelyInAdminSession(AdminAction.SetPassiveLiveness, isAdminScreen = true))
        assertFalse(shouldRunAdminActionImmediatelyInAdminSession(AdminAction.SetHeadAdmin, isAdminScreen = true))
        assertFalse(shouldRunAdminActionImmediatelyInAdminSession(AdminAction.ClearHeadAdmin, isAdminScreen = true))
        assertFalse(shouldRunAdminActionImmediatelyInAdminSession(AdminAction.OpenAdmin, isAdminScreen = false))
    }

    @Test
    fun incompatibleHeadAdminDoesNotAuthorizeAdminActions() {
        val legacyHeadAdmin = incompatibleUser("legacy", isHeadAdmin = true)

        assertFalse(hasHeadAdmin(listOf(legacyHeadAdmin)))
        assertTrue(requiresAndroidLockForAdminAction(AdminAction.OpenAdmin, listOf(legacyHeadAdmin)))
        assertFalse(canAuthorizeAdminActionWithHeadAdminFace(AdminAction.OpenAdmin, listOf(legacyHeadAdmin)))
    }

    @Test
    fun adminFaceDecisionApprovesOnlyCompatibleHeadAdmin() {
        val headAdmin = compatibleUser("head", isHeadAdmin = true)
        val regular = compatibleUser("regular", isHeadAdmin = false)
        val legacyHeadAdmin = incompatibleUser("legacy", isHeadAdmin = true)

        assertEquals(AdminAuthDecision.Expired, adminAuthDecision(null, headAdmin))
        assertEquals(AdminAuthDecision.Rejected, adminAuthDecision(AdminAction.OpenAdmin, null))
        assertEquals(AdminAuthDecision.Rejected, adminAuthDecision(AdminAction.OpenAdmin, regular))
        assertEquals(AdminAuthDecision.Rejected, adminAuthDecision(AdminAction.OpenAdmin, legacyHeadAdmin))
        assertEquals(AdminAuthDecision.Approved, adminAuthDecision(AdminAction.OpenAdmin, headAdmin))
    }

    @Test
    fun adminAuthMatchesOnlyCompatibleHeadAdminCandidates() {
        val regular = compatibleUser("regular", isHeadAdmin = false)
        val headAdmin = compatibleUser("head", isHeadAdmin = true)
        val legacyHeadAdmin = incompatibleUser("legacy", isHeadAdmin = true)
        val secondHeadAdmin = compatibleUser("second", isHeadAdmin = true)

        assertEquals(listOf(1, 3), adminAuthCandidateIndices(listOf(regular, headAdmin, legacyHeadAdmin, secondHeadAdmin)))
    }

    @Test
    fun faceGuideTargetTracksDetectedFaceWithFillCenterMapping() {
        val centered = faceGuideTarget(
            bounds = FaceBounds(left = 270.0f, top = 190.0f, width = 100.0f, height = 100.0f, frameWidth = 640.0f, frameHeight = 480.0f),
            containerWidth = 640.0f,
            containerHeight = 480.0f,
            fallbackSize = 260.0f
        )
        assertEquals(320.0f, centered.centerX, 0.01f)
        assertEquals(240.0f, centered.centerY, 0.01f)

        val left = faceGuideTarget(
            bounds = FaceBounds(left = 80.0f, top = 170.0f, width = 120.0f, height = 120.0f, frameWidth = 640.0f, frameHeight = 480.0f),
            containerWidth = 640.0f,
            containerHeight = 480.0f,
            fallbackSize = 260.0f
        )
        assertTrue(left.centerX < centered.centerX)

        val fallback = faceGuideTarget(null, containerWidth = 640.0f, containerHeight = 480.0f, fallbackSize = 260.0f)
        assertEquals(320.0f, fallback.centerX, 0.01f)
        assertEquals(240.0f, fallback.centerY, 0.01f)
        assertEquals(260.0f, fallback.sizePx, 0.01f)
    }

    @Test
    fun previewRectangleUsesCenterCropCoordinatesAndClipsToViewport() {
        val rect = faceRectInPreview(
            bounds = FaceBounds(80f, 120f, 160f, 200f, 640f, 480f),
            containerWidth = 1080f,
            containerHeight = 1080f
        )!!
        // 640x480 center-crops to a square at scale 2.25, with -180 horizontal offset.
        assertEquals(0f, rect.left, 0.01f)
        assertEquals(270f, rect.top, 0.01f)
        assertEquals(360f, rect.right, 0.01f)
        assertEquals(720f, rect.bottom, 0.01f)
        assertNull(faceRectInPreview(null, 1080f, 1080f))
    }

    @Test
    fun multipleHeadAdminsCanAuthorizeAndAreNormalizedOnLoad() {
        val headAdmin = compatibleUser("head", isHeadAdmin = true)
        val secondHeadAdmin = compatibleUser("second", isHeadAdmin = true)
        val legacyHeadAdmin = incompatibleUser("legacy", isHeadAdmin = true)
        val regular = compatibleUser("regular", isHeadAdmin = false)

        assertTrue(canAuthorizeAdminActionWithHeadAdminFace(AdminAction.OpenAdmin, listOf(headAdmin, secondHeadAdmin)))

        val normalized = normalizeHeadAdminUsers(listOf(legacyHeadAdmin, headAdmin, secondHeadAdmin, regular))
        assertFalse(normalized[0].isHeadAdmin)
        assertTrue(normalized[1].isHeadAdmin)
        assertTrue(normalized[2].isHeadAdmin)
        assertFalse(normalized[3].isHeadAdmin)
    }

    @Test
    fun adminCanUseCameraOnlyForEnrollment() {
        assertFalse(
            shouldUseCameraForScreen(
                baseReady = true,
                adminPromptInFlight = false,
                isOperationScreen = false,
                isAdminScreen = true,
                isEnrollmentMode = false
            )
        )
        assertTrue(
            shouldUseCameraForScreen(
                baseReady = true,
                adminPromptInFlight = false,
                isOperationScreen = false,
                isAdminScreen = true,
                isEnrollmentMode = true
            )
        )
    }

    @Test
    fun enrollmentCannotUseCameraFromOperationScreen() {
        assertFalse(
            shouldUseCameraForScreen(
                baseReady = true,
                adminPromptInFlight = false,
                isOperationScreen = true,
                isAdminScreen = false,
                isEnrollmentMode = true
            )
        )
    }

    @Test
    fun analysisIsBlockedWhenThereIsAnIdleReason() {
        assertFalse(
            shouldAnalyzeCameraFrame(
                baseReady = true,
                adminPromptInFlight = false,
                isOperationScreen = true,
                isAdminScreen = false,
                isEnrollmentMode = false,
                hasIdleReason = true
            )
        )
    }

    @Test
    fun previewCanStayBoundWhileAnalysisUseCaseIsPaused() {
        assertTrue(shouldBindCameraAnalysisUseCase(cameraEnabled = true, analysisEnabled = true))
        assertTrue(shouldBindCameraAnalysisUseCase(cameraEnabled = true, analysisEnabled = false))
        assertFalse(shouldBindCameraAnalysisUseCase(cameraEnabled = false, analysisEnabled = true))
    }

    @Test
    fun cameraAnalysisWatchdogRetriesOnlyAfterStallAndCooldown() {
        assertFalse(
            shouldRetryCameraAnalysis(
                analysisExpected = false,
                nowMillis = 10_000L,
                watchStartedAtMillis = 1_000L,
                lastAnalysisAtMillis = 0L,
                lastRetryAtMillis = 0L,
                stallMillis = 5_000L,
                retryCooldownMillis = 3_000L
            )
        )
        assertFalse(
            shouldRetryCameraAnalysis(
                analysisExpected = true,
                nowMillis = 5_900L,
                watchStartedAtMillis = 1_000L,
                lastAnalysisAtMillis = 0L,
                lastRetryAtMillis = 0L,
                stallMillis = 5_000L,
                retryCooldownMillis = 3_000L
            )
        )
        assertTrue(
            shouldRetryCameraAnalysis(
                analysisExpected = true,
                nowMillis = 6_000L,
                watchStartedAtMillis = 1_000L,
                lastAnalysisAtMillis = 0L,
                lastRetryAtMillis = 0L,
                stallMillis = 5_000L,
                retryCooldownMillis = 3_000L
            )
        )
        assertFalse(
            shouldRetryCameraAnalysis(
                analysisExpected = true,
                nowMillis = 6_000L,
                watchStartedAtMillis = 1_000L,
                lastAnalysisAtMillis = 5_500L,
                lastRetryAtMillis = 0L,
                stallMillis = 5_000L,
                retryCooldownMillis = 3_000L
            )
        )
        assertFalse(
            shouldRetryCameraAnalysis(
                analysisExpected = true,
                nowMillis = 6_000L,
                watchStartedAtMillis = 1_000L,
                lastAnalysisAtMillis = 0L,
                lastRetryAtMillis = 4_000L,
                stallMillis = 5_000L,
                retryCooldownMillis = 3_000L
            )
        )
    }

    @Test
    fun cameraAnalysisWatchdogRebindsFeedStallsAndEscalatesRepeatedRebinds() {
        assertEquals(
            CameraAnalysisWatchdogAction.RebindCamera,
            cameraAnalysisWatchdogAction(
                analysisExpected = true,
                nowMillis = 6_000L,
                watchStartedAtMillis = 1_000L,
                lastAnalysisAtMillis = 0L,
                lastRetryAtMillis = 0L,
                processingInFlight = false,
                rebindAttemptCount = 0,
                stallMillis = 5_000L,
                retryCooldownMillis = 3_000L,
                maxRebindAttempts = 2
            )
        )
        assertEquals(
            CameraAnalysisWatchdogAction.FailVisible,
            cameraAnalysisWatchdogAction(
                analysisExpected = true,
                nowMillis = 6_000L,
                watchStartedAtMillis = 1_000L,
                lastAnalysisAtMillis = 0L,
                lastRetryAtMillis = 0L,
                processingInFlight = false,
                rebindAttemptCount = 2,
                stallMillis = 5_000L,
                retryCooldownMillis = 3_000L,
                maxRebindAttempts = 2
            )
        )
        assertEquals(
            CameraAnalysisWatchdogAction.None,
            cameraAnalysisWatchdogAction(
                analysisExpected = true,
                nowMillis = 5_900L,
                watchStartedAtMillis = 1_000L,
                lastAnalysisAtMillis = 0L,
                lastRetryAtMillis = 0L,
                processingInFlight = false,
                stallMillis = 5_000L,
                retryCooldownMillis = 3_000L
            )
        )
    }

    @Test
    fun cameraAnalysisWatchdogAllowsSlowAnalyzerBeforeFatalWindow() {
        assertEquals(
            CameraAnalysisWatchdogAction.None,
            cameraAnalysisWatchdogAction(
                analysisExpected = true,
                nowMillis = 6_000L,
                watchStartedAtMillis = 1_000L,
                lastAnalysisAtMillis = 1_000L,
                lastRetryAtMillis = 0L,
                processingInFlight = true,
                processingInFlightStartedAtMillis = 1_000L,
                stallMillis = 5_000L,
                retryCooldownMillis = 3_000L,
                analyzerFatalStallMillis = 15_000L
            )
        )
        assertEquals(
            CameraAnalysisWatchdogAction.FailVisible,
            cameraAnalysisWatchdogAction(
                analysisExpected = true,
                nowMillis = 16_000L,
                watchStartedAtMillis = 1_000L,
                lastAnalysisAtMillis = 1_000L,
                lastRetryAtMillis = 0L,
                processingInFlight = true,
                processingInFlightStartedAtMillis = 1_000L,
                stallMillis = 5_000L,
                retryCooldownMillis = 3_000L,
                analyzerFatalStallMillis = 15_000L
            )
        )
    }

    @Test
    fun analyzerFatalStallCannotBeRetriedAsCameraRebind() {
        assertTrue(
            canRetryCamera(
                cameraAvailable = false,
                hasCameraPermission = true,
                noCameraHardware = false,
                analyzerFatalStall = false
            )
        )
        assertFalse(
            canRetryCamera(
                cameraAvailable = false,
                hasCameraPermission = true,
                noCameraHardware = false,
                analyzerFatalStall = true
            )
        )
    }

    @Test
    fun analyzerFatalStallKeepsCameraPreviewDisabledUntilRestart() {
        assertTrue(
            canPreviewCamera(
                modelError = null,
                storeError = null,
                hasCameraPermission = true,
                cameraAvailable = true,
                noCameraHardware = false,
                analyzerFatalStall = false
            )
        )
        assertFalse(
            canPreviewCamera(
                modelError = null,
                storeError = null,
                hasCameraPermission = true,
                cameraAvailable = true,
                noCameraHardware = false,
                analyzerFatalStall = true
            )
        )
    }

    @Test
    fun lifecyclePauseReturnsToOperationOnlyOutsideAdminPrompt() {
        assertFalse(shouldReturnToOperationOnLifecyclePause(adminPromptInFlight = true))
        assertTrue(shouldReturnToOperationOnLifecyclePause(adminPromptInFlight = false))
    }

    @Test
    fun doorTerminalImmersiveModeOnlyRunsOnOperationScreenWithoutAdminPrompt() {
        assertTrue(shouldUseDoorTerminalImmersive(isOperationScreen = true, adminPromptInFlight = false, touchExplorationEnabled = false))
        assertFalse(shouldUseDoorTerminalImmersive(isOperationScreen = true, adminPromptInFlight = true, touchExplorationEnabled = false))
        assertFalse(shouldUseDoorTerminalImmersive(isOperationScreen = false, adminPromptInFlight = false, touchExplorationEnabled = false))
        assertFalse(shouldUseDoorTerminalImmersive(isOperationScreen = true, adminPromptInFlight = false, touchExplorationEnabled = true))
    }

    @Test
    fun adminScreenAutoLocksOnlyAfterIdleAdminSessionExpires() {
        assertTrue(
            shouldAutoLockAdminScreen(
                nowMillis = 120_000L,
                expiresAtMillis = 120_000L,
                isAdminScreen = true,
                isEnrollmentMode = false,
                storageBusy = false,
                adminPromptInFlight = false
            )
        )
        assertFalse(
            shouldAutoLockAdminScreen(
                nowMillis = 120_000L,
                expiresAtMillis = 120_000L,
                isAdminScreen = true,
                isEnrollmentMode = true,
                storageBusy = false,
                adminPromptInFlight = false
            )
        )
        assertFalse(
            shouldAutoLockAdminScreen(
                nowMillis = 119_999L,
                expiresAtMillis = 120_000L,
                isAdminScreen = true,
                isEnrollmentMode = false,
                storageBusy = false,
                adminPromptInFlight = false
            )
        )
        assertFalse(
            shouldAutoLockAdminScreen(
                nowMillis = 120_000L,
                expiresAtMillis = 120_000L,
                isAdminScreen = true,
                isEnrollmentMode = false,
                storageBusy = true,
                adminPromptInFlight = false
            )
        )
        assertFalse(
            shouldAutoLockAdminScreen(
                nowMillis = 120_000L,
                expiresAtMillis = 120_000L,
                isAdminScreen = true,
                isEnrollmentMode = false,
                storageBusy = false,
                adminPromptInFlight = true
            )
        )
        assertFalse(
            shouldAutoLockAdminScreen(
                nowMillis = 120_000L,
                expiresAtMillis = 120_000L,
                isAdminScreen = false,
                isEnrollmentMode = false,
                storageBusy = false,
                adminPromptInFlight = false
            )
        )
        assertFalse(
            shouldAutoLockAdminScreen(
                nowMillis = 120_000L,
                expiresAtMillis = 0L,
                isAdminScreen = true,
                isEnrollmentMode = false,
                storageBusy = false,
                adminPromptInFlight = false
            )
        )
    }

    @Test
    fun adminAutoLockResetPlanClearsSensitiveAdminState() {
        val plan = adminAutoLockResetPlan()

        assertTrue(plan.returnToOperation)
        assertTrue(plan.exitEnrollment)
        assertTrue(plan.clearAdminSession)
        assertTrue(plan.clearEnrollmentSession)
        assertTrue(plan.clearAdminDialogs)
        assertTrue(plan.clearEnrollment)
        assertTrue(plan.clearAuthHold)
        assertTrue(plan.clearAccessFeedback)

        val reset = applyAdminAutoLockReset(AdminAutoLockState())

        assertTrue(reset.returnToOperation)
        assertTrue(reset.authMode)
        assertEquals(0L, reset.adminSessionExpiresAt)
        assertEquals(0L, reset.enrollmentExpiresAt)
        assertFalse(reset.confirmDelete)
        assertEquals(-1, reset.pendingDeleteUserIndex)
        assertEquals("", reset.enrollmentName)
        assertEquals(0L, reset.authResultHoldUntil)
        assertFalse(reset.hasAccessFeedback)
    }

    @Test
    fun enrollmentAutoLocksOnlyAfterIdleEnrollmentExpires() {
        assertTrue(
            shouldAutoLockEnrollment(
                nowMillis = 60_000L,
                expiresAtMillis = 60_000L,
                isAdminScreen = true,
                isEnrollmentMode = true,
                storageBusy = false,
                adminPromptInFlight = false
            )
        )
        assertFalse(
            shouldAutoLockEnrollment(
                nowMillis = 60_000L,
                expiresAtMillis = 60_000L,
                isAdminScreen = true,
                isEnrollmentMode = true,
                storageBusy = true,
                adminPromptInFlight = false
            )
        )
        assertFalse(
            shouldAutoLockEnrollment(
                nowMillis = 60_000L,
                expiresAtMillis = 60_000L,
                isAdminScreen = true,
                isEnrollmentMode = true,
                storageBusy = false,
                adminPromptInFlight = true
            )
        )
        assertFalse(
            shouldAutoLockEnrollment(
                nowMillis = 59_999L,
                expiresAtMillis = 60_000L,
                isAdminScreen = true,
                isEnrollmentMode = true,
                storageBusy = false,
                adminPromptInFlight = false
            )
        )
        assertFalse(
            shouldAutoLockEnrollment(
                nowMillis = 60_000L,
                expiresAtMillis = 60_000L,
                isAdminScreen = false,
                isEnrollmentMode = true,
                storageBusy = false,
                adminPromptInFlight = false
            )
        )
        assertFalse(
            shouldAutoLockEnrollment(
                nowMillis = 60_000L,
                expiresAtMillis = 60_000L,
                isAdminScreen = true,
                isEnrollmentMode = false,
                storageBusy = false,
                adminPromptInFlight = false
            )
        )
    }

    @Test
    fun removingRegisteredUserRemovesOnlySelectedIndex() {
        assertEquals(listOf("alice", "carol"), removeRegisteredUserAt(listOf("alice", "bob", "carol"), 1))
    }

    @Test
    fun removingRegisteredUserRejectsStaleIndex() {
        assertNull(removeRegisteredUserAt(listOf("alice"), 3))
    }

    private fun compatibleUser(name: String, isHeadAdmin: Boolean): UserTemplate {
        val template = ByteArray(32) { 1 }
        return UserTemplate(
            name = name,
            template = template,
            engineId = FACE_ENGINE_ID,
            templateSize = template.size,
            isHeadAdmin = isHeadAdmin
        )
    }

    private fun incompatibleUser(name: String, isHeadAdmin: Boolean): UserTemplate {
        return UserTemplate(
            name = name,
            template = ByteArray(0),
            engineId = "legacy.unknown",
            templateSize = 0,
            isHeadAdmin = isHeadAdmin
        )
    }
    @Test
    fun expiredAdminSessionCannotRunActionsImmediately() {
        assertTrue(isAdminSessionActive(isAdminScreen = true, expiresAtMillis = 2_000L, nowMillis = 1_999L))
        assertFalse(isAdminSessionActive(isAdminScreen = true, expiresAtMillis = 2_000L, nowMillis = 2_000L))
        assertFalse(isAdminSessionActive(isAdminScreen = false, expiresAtMillis = 2_000L, nowMillis = 1_000L))
        assertFalse(isAdminSessionActive(isAdminScreen = true, expiresAtMillis = 0L, nowMillis = 0L))
    }

}
