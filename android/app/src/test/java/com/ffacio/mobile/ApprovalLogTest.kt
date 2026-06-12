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
                    secondScore = 0.20,
                    supportCount = index
                ),
                limit = 8
            )
        }

        assertEquals(8, logs.size)
        assertEquals("user10", logs.first().userName)
        assertEquals("user3", logs.last().userName)
    }

    @Test
    fun authDecisionSummaryIncludesScoresAndSupport() {
        val summary = authDecisionSummary(
            AuthDecisionLogEntry(
                time = "12:10:00",
                userName = "tester",
                result = "보류",
                reason = "ambiguous runner-up",
                score = 0.61234,
                secondScore = 0.59012,
                supportCount = 1
            )
        )

        assertEquals("ambiguous runner-up · score 0.612 · second 0.590 · support 1", summary)
    }

    @Test
    fun duplicateAuthDecisionIsThrottledByKeyAndTime() {
        val entry = AuthDecisionLogEntry(
            time = "12:10:00",
            userName = "tester",
            result = "보류",
            reason = "score below threshold",
            score = 0.512,
            secondScore = 0.233,
            supportCount = 1
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
            secondScore = 0.233,
            supportCount = 1
        )
        val jittered = first.copy(score = 0.519, secondScore = 0.241, supportCount = 2)

        assertEquals(authDecisionDedupeKey(first), authDecisionDedupeKey(jittered))
    }

    @Test
    fun authDecisionReasonNamesTheBlockingGate() {
        assertEquals(
            "score below threshold",
            authDecisionReason(Match(index = 0, score = 0.40, secondScore = 0.10, supportCount = 5), availableSamples = 5)
        )
        assertEquals(
            "ambiguous runner-up",
            authDecisionReason(Match(index = 0, score = 0.70, secondScore = 0.65, supportCount = 5), availableSamples = 5)
        )
        assertEquals(
            "not enough sample support",
            authDecisionReason(Match(index = 0, score = 0.70, secondScore = 0.10, supportCount = 1), availableSamples = 5)
        )
    }

    @Test
    fun relayFailureIsNotRenderedAsSuccessfulApproval() {
        assertEquals(true, approvalResultSucceeded("승인"))
        assertEquals(true, approvalResultSucceeded("문 열림 요청 완료"))
        assertEquals(false, approvalResultSucceeded("문 제어 실패"))
    }

    @Test
    fun relayPendingIsNotRenderedAsFinalSuccess() {
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
    fun doorRelayConfiguredRequiresUrlAndToken() {
        assertEquals(true, doorRelayConfigured("https://relay.example/open", "token"))
        assertEquals(false, doorRelayConfigured("", "token"))
        assertEquals(false, doorRelayConfigured("https://relay.example/open", ""))
    }

    @Test
    fun welcomeStatusUsesAcceptedUserName() {
        assertEquals("환영합니다, 민수님", welcomeStatus("민수"))
    }
}
