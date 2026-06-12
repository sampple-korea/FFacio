package com.ffacio.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoorRelayGuardTest {
    @Test
    fun doorRequestStartsOnlyWhenArmedIdleAndCooldownExpired() {
        val gate = DoorRequestGate()

        assertFalse(gate.tryStart(doorArmed = false, nowMillis = 10_000L))
        assertTrue(gate.tryStart(doorArmed = true, nowMillis = 10_000L))
        assertFalse(gate.tryStart(doorArmed = true, nowMillis = 10_100L))

        gate.finish()

        assertFalse(gate.tryStart(doorArmed = true, nowMillis = 13_000L))
        assertTrue(gate.tryStart(doorArmed = true, nowMillis = 13_500L))
    }

    @Test
    fun doorRelayPayloadDoesNotExposeUserName() {
        val payload = doorRelayPayloadJson()

        assertFalse(payload.contains("\"user\""))
        assertFalse(payload.contains("민수"))
        assertTrue(payload.contains("\"event\""))
        assertTrue(payload.contains("\"source\""))
    }
}
