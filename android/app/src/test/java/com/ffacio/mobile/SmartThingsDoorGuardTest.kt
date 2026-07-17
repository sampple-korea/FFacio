package com.ffacio.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartThingsDoorGuardTest {
    private val accessToken = "0123456789abcdef0123456789abcdef"
    private val deviceId = "01234567-89ab-cdef-0123-456789abcdef"

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
    fun smartThingsEndpointsAreFixedToOfficialApiHostAndDevicePath() {
        assertEquals(
            "https://api.smartthings.com/v1/devices/$deviceId/components/main/capabilities/lock/status",
            smartThingsStatusUrl(deviceId)
        )
        assertEquals(
            "https://api.smartthings.com/v1/devices/$deviceId/commands",
            smartThingsCommandsUrl(deviceId)
        )
    }

    @Test
    fun unlockPayloadUsesLockCapabilityWithoutPersonalData() {
        val payload = smartThingsUnlockPayloadJson()
        assertEquals(
            "{\"commands\":[{\"component\":\"main\",\"capability\":\"lock\",\"command\":\"unlock\",\"arguments\":[]}]}",
            payload
        )
        assertFalse(payload.contains("user"))
        assertFalse(payload.contains("token"))
    }

    @Test
    fun deviceIdValidationRejectsUrlsPathsAndWhitespace() {
        assertTrue(smartThingsDeviceIdValid(deviceId))
        assertFalse(smartThingsDeviceIdValid("https://api.smartthings.com/device"))
        assertFalse(smartThingsDeviceIdValid("device/child"))
        assertFalse(smartThingsDeviceIdValid("bad id with spaces"))
        assertFalse(smartThingsDeviceIdValid("short"))
    }

    @Test
    fun testButtonRequiresCompleteConfigAndIdleMutableState() {
        assertTrue(canTestSmartThingsDoorConfig(deviceId, accessToken, inFlight = false, canMutate = true))
        assertFalse(canTestSmartThingsDoorConfig("", accessToken, inFlight = false, canMutate = true))
        assertFalse(canTestSmartThingsDoorConfig(deviceId, "", inFlight = false, canMutate = true))
        assertFalse(canTestSmartThingsDoorConfig(deviceId, accessToken, inFlight = true, canMutate = true))
        assertFalse(canTestSmartThingsDoorConfig(deviceId, accessToken, inFlight = false, canMutate = false))
    }

    @Test
    fun smartThingsFailuresAreActionableAndDoNotExposeSecrets() {
        assertTrue(smartThingsHttpFailureMessage(401).contains("token"))
        assertTrue(smartThingsHttpFailureMessage(403).contains("권한"))
        assertTrue(smartThingsHttpFailureMessage(404).contains("기기"))
        assertTrue(smartThingsHttpFailureMessage(429).contains("한도"))
        assertTrue(smartThingsHttpFailureMessage(503).contains("서버"))
        assertFalse(smartThingsHttpFailureMessage(401).contains("secret-token"))
        assertTrue(smartThingsNetworkFailureMessage(java.net.UnknownHostException()).contains("주소"))
        assertTrue(smartThingsNetworkFailureMessage(java.net.SocketTimeoutException()).contains("시간"))
    }

    @Test
    fun unlockRequiresEverySecurityGate() {
        assertTrue(canIssueSmartThingsUnlock(true, true, true, "runtime_live", 0.70f, true))
        assertFalse(canIssueSmartThingsUnlock(false, true, true, "runtime_live", 0.95f, true))
        assertFalse(canIssueSmartThingsUnlock(true, false, true, "runtime_live", 0.95f, true))
        assertFalse(canIssueSmartThingsUnlock(true, true, false, "disabled", 1.0f, true))
        assertFalse(canIssueSmartThingsUnlock(true, true, true, "runtime_rejected", 0.95f, true))
        assertFalse(canIssueSmartThingsUnlock(true, true, true, "runtime_live", 0.69f, true))
        assertFalse(canIssueSmartThingsUnlock(true, true, true, "runtime_live", 0.95f, false))
    }
    @Test
    fun accessTokenValidationRejectsHeaderInjectionWhitespaceAndOversize() {
        assertTrue(smartThingsAccessTokenValid(accessToken))
        assertFalse(smartThingsAccessTokenValid("short"))
        assertFalse(smartThingsAccessTokenValid("0123456789abcdef\nInjected"))
        assertFalse(smartThingsAccessTokenValid("0123456789 abcdef"))
        assertFalse(smartThingsAccessTokenValid(" $accessToken"))
        assertFalse(smartThingsAccessTokenValid("$accessToken "))
        assertFalse(smartThingsAccessTokenValid("x".repeat(SMARTTHINGS_MAX_TOKEN_LENGTH + 1)))
    }

    @Test
    fun commandResponseRequiresEveryResultToBeAcceptedOrCompleted() {
        assertTrue(smartThingsCommandAccepted("{\"results\":[{\"status\":\"ACCEPTED\"}]}"))
        assertTrue(smartThingsCommandAccepted("{\"results\":[{\"status\":\"COMPLETED\"}]}"))
        assertTrue(smartThingsCommandAccepted("{\"results\":[{\"status\":\"ACCEPTED\"},{\"status\":\"COMPLETED\"}]}"))
        assertFalse(smartThingsCommandAccepted("{\"results\":[]}"))
        assertFalse(smartThingsCommandAccepted("{\"results\":[{\"status\":\"FAILED\"}]}"))
        assertFalse(smartThingsCommandAccepted("not-json"))
    }

    @Test
    fun lockStatusParserFailsClosedOnMalformedResponses() {
        assertEquals("locked", parseSmartThingsLockState("{\"lock\":{\"value\":\"locked\"}}"))
        assertEquals("unlocked", parseSmartThingsLockState("{\"lock\":{\"value\":\"unlocked\"}}"))
        assertEquals(null, parseSmartThingsLockState("{\"lock\":{}}"))
        assertEquals(null, parseSmartThingsLockState("not-json"))
    }
}
