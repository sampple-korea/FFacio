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
}
