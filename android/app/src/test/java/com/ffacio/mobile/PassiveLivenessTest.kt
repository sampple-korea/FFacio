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
}
