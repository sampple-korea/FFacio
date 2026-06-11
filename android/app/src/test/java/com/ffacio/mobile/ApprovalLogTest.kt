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
    fun relayFailureIsNotRenderedAsSuccessfulApproval() {
        assertEquals(true, approvalResultSucceeded("승인"))
        assertEquals(true, approvalResultSucceeded("문 열림 요청 완료"))
        assertEquals(false, approvalResultSucceeded("문 제어 실패"))
    }

    @Test
    fun relayPendingIsNotRenderedAsFinalSuccess() {
        assertEquals("…", accessFeedbackSymbol(AccessFeedbackKind.DoorPending))
        assertEquals("문 열림 요청 중", accessFeedbackTitle(AccessFeedbackKind.DoorPending))
        assertEquals("✓", accessFeedbackSymbol(AccessFeedbackKind.DoorSucceeded))
        assertEquals("문 열림 완료", accessFeedbackTitle(AccessFeedbackKind.DoorSucceeded))
    }

    @Test
    fun publicOperationFeedbackDoesNotExposeUserNameOrTime() {
        val feedback = AccessFeedback(AccessFeedbackKind.AuthOnly, "민수")
        val summary = approvalPublicSummary(ApprovalLogEntry("09:31:15", "민수", "승인"))

        assertEquals("얼굴 인증이 완료되었습니다", accessFeedbackPublicMessage(feedback))
        assertEquals("최근 출입 이벤트 · 승인", summary)
        assertEquals(false, accessFeedbackPublicMessage(feedback).contains("민수"))
        assertEquals(false, summary.contains("민수"))
        assertEquals(false, summary.contains("09:31:15"))
    }
}
