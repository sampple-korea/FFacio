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
    fun lifecyclePauseReturnsToOperationOnlyOutsideAdminPrompt() {
        assertFalse(shouldReturnToOperationOnLifecyclePause(adminPromptInFlight = true))
        assertTrue(shouldReturnToOperationOnLifecyclePause(adminPromptInFlight = false))
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
