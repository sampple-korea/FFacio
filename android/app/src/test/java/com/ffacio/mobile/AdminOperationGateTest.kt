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
        assertFalse(shouldBindCameraAnalysisUseCase(cameraEnabled = true, analysisEnabled = false))
        assertFalse(shouldBindCameraAnalysisUseCase(cameraEnabled = false, analysisEnabled = true))
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
        assertTrue(plan.resetTransientRecognition)

        val reset = applyAdminAutoLockReset(AdminAutoLockState())

        assertTrue(reset.returnToOperation)
        assertTrue(reset.authMode)
        assertEquals(0L, reset.adminSessionExpiresAt)
        assertEquals(0L, reset.enrollmentExpiresAt)
        assertFalse(reset.confirmDelete)
        assertEquals(-1, reset.pendingDeleteUserIndex)
        assertEquals("", reset.enrollmentName)
        assertEquals(0, reset.enrollSampleCount)
        assertEquals(0, reset.enrollPoseCount)
        assertEquals(0L, reset.authResultHoldUntil)
        assertFalse(reset.hasAccessFeedback)
        assertEquals(-1, reset.liveCandidate)
        assertEquals(-1, reset.stableUser)
        assertEquals(0, reset.stableCount)
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
}
