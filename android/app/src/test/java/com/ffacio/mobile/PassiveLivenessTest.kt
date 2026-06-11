package com.ffacio.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassiveLivenessTest {
    @Test
    fun classIndexZeroIsLiveFace() {
        val result = classifyPassiveLiveness(floatArrayOf(4.0f, 0.1f, -0.2f), threshold = 0.55f)

        assertEquals("live", result.state)
        assertTrue(result.isLive)
        assertTrue(result.liveScore > 0.55f)
    }

    @Test
    fun classIndexOneIsPrintAttack() {
        val result = classifyPassiveLiveness(floatArrayOf(0.1f, 3.0f, 0.0f), threshold = 0.55f)

        assertEquals("print_attack", result.state)
        assertFalse(result.isLive)
    }

    @Test
    fun classIndexTwoIsReplayAttack() {
        val result = classifyPassiveLiveness(floatArrayOf(0.1f, 0.0f, 3.0f), threshold = 0.55f)

        assertEquals("replay_attack", result.state)
        assertFalse(result.isLive)
    }

    @Test
    fun invalidOutputFailsClosed() {
        val result = classifyPassiveLiveness(floatArrayOf(1.0f, 2.0f), threshold = 0.55f)

        assertEquals("invalid_output", result.state)
        assertFalse(result.isLive)
    }

    @Test
    fun probabilityOutputIsNotSoftmaxedAgain() {
        val result = classifyPassiveLiveness(floatArrayOf(0.70f, 0.20f, 0.10f), threshold = 0.55f)

        assertEquals("live", result.state)
        assertTrue(result.isLive)
        assertTrue(result.liveScore >= 0.69f)
    }

    @Test
    fun probabilityOutputPrintAttackFailsClosed() {
        val result = classifyPassiveLiveness(floatArrayOf(0.10f, 0.75f, 0.15f), threshold = 0.55f)

        assertEquals("print_attack", result.state)
        assertFalse(result.isLive)
    }
}
