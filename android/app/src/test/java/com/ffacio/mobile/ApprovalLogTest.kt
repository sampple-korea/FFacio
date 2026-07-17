package com.ffacio.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class ApprovalLogTest {
    @Test
    fun newestApprovalLogIsFirstAndLimitIsEnforced() {
        val logs = mutableListOf<ApprovalLogEntry>()

        for (index in 1..10) {
            addApprovalLog(
                logs,
                ApprovalLogEntry("12:00:${index.toString().padStart(2, '0')}", "user$index", "승인"),
                limit = 8
            )
        }

        assertEquals(8, logs.size)
        assertEquals("user10", logs.first().userName)
        assertEquals("user3", logs.last().userName)
    }

    @Test
    fun newestAuthDecisionLogIsFirstAndLimitIsEnforced() {
        val logs = mutableListOf<AuthDecisionLogEntry>()

        for (index in 1..10) {
            addAuthDecisionLog(
                logs,
                AuthDecisionLogEntry(
                    time = "12:01:${index.toString().padStart(2, '0')}",
                    userName = "user$index",
                    result = if (index == 10) "승인" else "보류",
                    reason = "score below threshold",
                    score = 0.40 + index / 100.0,
                    secondScore = 0.20
                ),
                limit = 8
            )
        }

        assertEquals(8, logs.size)
        assertEquals("user10", logs.first().userName)
        assertEquals("user3", logs.last().userName)
    }

    @Test
    fun authDecisionSummaryIncludesScores() {
        val summary = authDecisionSummary(
            AuthDecisionLogEntry(
                time = "12:10:00",
                userName = "tester",
                result = "보류",
                reason = "ambiguous runner-up",
                score = 0.61234,
                secondScore = 0.59012
            )
        )

        assertEquals("ambiguous runner-up · score 0.612 · second 0.590", summary)
    }

    @Test
    fun duplicateAuthDecisionIsThrottledByKeyAndTime() {
        val entry = AuthDecisionLogEntry(
            time = "12:10:00",
            userName = "tester",
            result = "보류",
            reason = "score below threshold",
            score = 0.512,
            secondScore = 0.233
        )
        val key = authDecisionDedupeKey(entry)

        assertEquals(false, shouldRecordAuthDecisionLog(key, nowMillis = 2_000L, lastKey = key, lastAtMillis = 1_000L))
        assertEquals(true, shouldRecordAuthDecisionLog(key, nowMillis = 4_000L, lastKey = key, lastAtMillis = 1_000L))
        assertEquals(true, shouldRecordAuthDecisionLog("$key|changed", nowMillis = 2_000L, lastKey = key, lastAtMillis = 1_000L))
    }

    @Test
    fun authDecisionDedupeIgnoresScoreJitterForSameUserAndReason() {
        val first = AuthDecisionLogEntry(
            time = "12:10:00",
            userName = "tester",
            result = "보류",
            reason = "score below threshold",
            score = 0.512,
            secondScore = 0.233
        )
        val jittered = first.copy(score = 0.519, secondScore = 0.241)

        assertEquals(authDecisionDedupeKey(first), authDecisionDedupeKey(jittered))
    }

    @Test
    fun authDecisionReasonNamesTheBlockingGate() {
        assertEquals(
            "score below threshold",
            authDecisionReason(Match(index = 0, score = 0.40, secondScore = 0.10))
        )
        assertEquals(
            "ambiguous runner-up",
            authDecisionReason(Match(index = 0, score = 0.85, secondScore = 0.84))
        )
        assertEquals(
            "candidate accepted",
            authDecisionReason(Match(index = 0, score = 0.85, secondScore = 0.10))
        )
    }

    @Test
    fun smartThingsFailureIsNotRenderedAsSuccessfulApproval() {
        assertEquals(true, approvalResultSucceeded("승인"))
        assertEquals(true, approvalResultSucceeded("문 열림 요청 완료"))
        assertEquals(false, approvalResultSucceeded("문 제어 실패"))
    }

    @Test
    fun smartThingsPendingIsNotRenderedAsFinalSuccess() {
        assertEquals("…", accessFeedbackSymbol(AccessFeedbackKind.DoorPending))
        assertEquals("환영합니다, 민수님", accessFeedbackTitle(AccessFeedback(AccessFeedbackKind.DoorPending, "민수")))
        assertEquals("✓", accessFeedbackSymbol(AccessFeedbackKind.DoorSucceeded))
        assertEquals("환영합니다, 민수님", accessFeedbackTitle(AccessFeedback(AccessFeedbackKind.DoorSucceeded, "민수")))
    }

    @Test
    fun publicOperationFeedbackWelcomesAcceptedUserButHidesApprovalTime() {
        val feedback = AccessFeedback(AccessFeedbackKind.AuthOnly, "민수")
        val summary = approvalPublicSummary(ApprovalLogEntry("09:31:15", "민수", "승인"))

        assertEquals("환영합니다, 민수님", accessFeedbackTitle(feedback))
        assertEquals("얼굴 인증이 완료되었습니다", accessFeedbackPublicMessage(feedback))
        assertEquals("최근 출입 이벤트 · 승인", summary)
        assertEquals(false, summary.contains("민수"))
        assertEquals(false, summary.contains("09:31:15"))
    }

    @Test
    fun itsokeyConfigurationRequiresValidDeviceIdAndRuntimeService() {
        val deviceId = "01234567-89ab-cdef-0123-456789abcdef"
        val runtimeService = "itsokey-runtime-service"
        assertEquals(true, smartThingsDoorConfigured(deviceId, runtimeService))
        assertEquals(false, smartThingsDoorConfigured("https://relay.example/open", runtimeService))
        assertEquals(false, smartThingsDoorConfigured("not a device id", runtimeService))
        assertEquals(false, smartThingsDoorConfigured("", runtimeService))
        assertEquals(false, smartThingsDoorConfigured(deviceId, ""))
        assertEquals(false, smartThingsDoorConfigured(deviceId, "0123456789abcdef0123456789abcdef"))
    }

    @Test
    fun welcomeStatusUsesAcceptedUserName() {
        assertEquals("환영합니다, 민수님", welcomeStatus("민수"))
    }
}
