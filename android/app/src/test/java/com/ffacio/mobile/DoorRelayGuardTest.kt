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

    @Test
    fun relayHealthCheckUsesSafeWellKnownEndpointInsteadOfOpenPath() {
        val healthUrl = doorRelayHealthCheckUrl("https://door.local:8443/relay/open?device=front")

        assertTrue(healthUrl.startsWith("https://door.local:8443/relay/"))
        assertTrue(healthUrl.endsWith("/.well-known/ffacio-door-relay"))
        assertFalse(healthUrl.contains("/open"))
        assertFalse(healthUrl.contains("device=front"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun relayHealthCheckRejectsPlainHttp() {
        doorRelayHealthCheckUrl("http://door.local/open")
    }

    @Test
    fun relayTestButtonRequiresMutableCompleteConfigAndNoRequestInFlight() {
        assertTrue(canTestDoorRelayConfig("https://door.local/open", "token", inFlight = false, canMutate = true))
        assertFalse(canTestDoorRelayConfig("", "token", inFlight = false, canMutate = true))
        assertFalse(canTestDoorRelayConfig("http://door.local/open", "token", inFlight = false, canMutate = true))
        assertFalse(canTestDoorRelayConfig("not a url", "token", inFlight = false, canMutate = true))
        assertFalse(canTestDoorRelayConfig("https://door.local/open", "", inFlight = false, canMutate = true))
        assertFalse(canTestDoorRelayConfig("https://door.local/open", "token", inFlight = true, canMutate = true))
        assertFalse(canTestDoorRelayConfig("https://door.local/open", "token", inFlight = false, canMutate = false))
    }

    @Test
    fun relayHealthFailuresAreOperatorActionableWithoutSecrets() {
        assertTrue(doorRelayHealthHttpFailureMessage(401).contains("토큰"))
        assertTrue(doorRelayHealthHttpFailureMessage(404).contains("주소"))
        assertTrue(doorRelayHealthHttpFailureMessage(503).contains("장치"))
        assertFalse(doorRelayHealthHttpFailureMessage(401).contains("secret-token"))

        assertTrue(doorRelayHealthNetworkFailureMessage(java.net.UnknownHostException()).contains("주소"))
        assertTrue(doorRelayHealthNetworkFailureMessage(java.net.SocketTimeoutException()).contains("시간"))
    }
}
