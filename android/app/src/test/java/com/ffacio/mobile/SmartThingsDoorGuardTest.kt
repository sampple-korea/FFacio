package com.ffacio.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartThingsDoorGuardTest {
    private val runtimeSentinel = "itsokey-runtime-service"
    private val numericDeviceId = "12345"

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
    fun itsokeyDeviceIdAcceptsServerIdsAndRejectsInjectionOrPaths() {
        assertTrue(smartThingsDeviceIdValid(numericDeviceId))
        assertTrue(smartThingsDeviceIdValid("device-01:main"))
        assertFalse(smartThingsDeviceIdValid(""))
        assertFalse(smartThingsDeviceIdValid("https://v2.api.itsokey.kr/device"))
        assertFalse(smartThingsDeviceIdValid("device/child"))
        assertFalse(smartThingsDeviceIdValid("bad id with spaces"))
        assertFalse(smartThingsDeviceIdValid("123\nInjected"))
        assertFalse(smartThingsDeviceIdValid("x".repeat(129)))
    }

    @Test
    fun runtimeSentinelReplacesCredentialsInsideFFacio() {
        assertTrue(smartThingsAccessTokenValid(runtimeSentinel))
        assertFalse(smartThingsAccessTokenValid(""))
        assertFalse(smartThingsAccessTokenValid("real-access-token"))
        assertTrue(smartThingsDoorConfigured(numericDeviceId, runtimeSentinel))
        assertFalse(smartThingsDoorConfigured("", runtimeSentinel))
        assertFalse(smartThingsDoorConfigured(numericDeviceId, "real-access-token"))
    }

    @Test
    fun testButtonRequiresSelectedDeviceIdleStateAndMutableUi() {
        assertTrue(canTestSmartThingsDoorConfig(numericDeviceId, runtimeSentinel, inFlight = false, canMutate = true))
        assertFalse(canTestSmartThingsDoorConfig("", runtimeSentinel, inFlight = false, canMutate = true))
        assertFalse(canTestSmartThingsDoorConfig(numericDeviceId, "", inFlight = false, canMutate = true))
        assertFalse(canTestSmartThingsDoorConfig(numericDeviceId, runtimeSentinel, inFlight = true, canMutate = true))
        assertFalse(canTestSmartThingsDoorConfig(numericDeviceId, runtimeSentinel, inFlight = false, canMutate = false))
    }

    @Test
    fun unlockRequiresEveryFaceAndRuntimeSecurityGate() {
        assertTrue(canIssueSmartThingsUnlock(true, true, true, "runtime_live", 0.70f, true))
        assertFalse(canIssueSmartThingsUnlock(false, true, true, "runtime_live", 0.95f, true))
        assertFalse(canIssueSmartThingsUnlock(true, false, true, "runtime_live", 0.95f, true))
        assertFalse(canIssueSmartThingsUnlock(true, true, false, "runtime_live", 1.0f, true))
        assertFalse(canIssueSmartThingsUnlock(true, true, true, "runtime_rejected", 0.95f, true))
        assertFalse(canIssueSmartThingsUnlock(true, true, true, "runtime_live", 0.69f, true))
        assertFalse(canIssueSmartThingsUnlock(true, true, true, "runtime_live", 0.95f, false))
    }

    @Test
    fun itsokeyNumericAndStructuredServerIdsRemainSupported() {
        assertTrue(smartThingsDeviceIdValid("0"))
        assertTrue(smartThingsDeviceIdValid("0000123"))
        assertTrue(smartThingsDeviceIdValid("door.alpha_01:main"))
    }

    @Test
    fun runtimeSentinelRequiresAnExactMatch() {
        assertTrue(smartThingsAccessTokenValid(runtimeSentinel))
        assertFalse(smartThingsAccessTokenValid(" $runtimeSentinel"))
        assertFalse(smartThingsAccessTokenValid("$runtimeSentinel "))
        assertFalse(smartThingsAccessTokenValid(runtimeSentinel.uppercase()))
    }

    @Test
    fun deviceIdRejectsTraversalQueryAndFragmentCharacters() {
        assertFalse(smartThingsDeviceIdValid("../1"))
        assertFalse(smartThingsDeviceIdValid("1?open=true"))
        assertFalse(smartThingsDeviceIdValid("1#fragment"))
        assertFalse(smartThingsDeviceIdValid("1%2F2"))
    }

    @Test
    fun testButtonNeverAcceptsAnInvalidDeviceEvenWithRuntimeConnected() {
        assertFalse(canTestSmartThingsDoorConfig("../1", runtimeSentinel, inFlight = false, canMutate = true))
        assertFalse(canTestSmartThingsDoorConfig("1?open=true", runtimeSentinel, inFlight = false, canMutate = true))
        assertFalse(canTestSmartThingsDoorConfig("1#fragment", runtimeSentinel, inFlight = false, canMutate = true))
    }

    @Test
    fun unlockLivenessThresholdIsInclusiveAndRejectsNonFiniteScores() {
        assertTrue(canIssueSmartThingsUnlock(true, true, true, "runtime_live", 0.70f, true))
        assertFalse(canIssueSmartThingsUnlock(true, true, true, "runtime_live", 0.6999f, true))
        assertFalse(canIssueSmartThingsUnlock(true, true, true, "runtime_live", Float.NaN, true))
        assertFalse(canIssueSmartThingsUnlock(true, true, true, "runtime_live", Float.POSITIVE_INFINITY, true))
    }

}
