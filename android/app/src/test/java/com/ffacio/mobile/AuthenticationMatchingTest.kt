package com.ffacio.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AuthenticationMatchingTest {
    @Test
    fun emptyRuntimeTemplateIsIncompatible() {
        val user = UserTemplate(
            name = "empty",
            template = ByteArray(0),
            engineId = FACE_ENGINE_ID,
            templateSize = 0
        )

        assertFalse(user.isCompatible())
    }

    @Test
    fun runtimePrimaryTemplateIsCompatibleWithoutImprovementSamples() {
        val template = runtimeTemplate(10)
        val user = UserTemplate(
            name = "primary-only",
            template = template,
            samples = emptyList(),
            engineId = FACE_ENGINE_ID,
            templateSize = template.size
        )

        assertTrue(user.isCompatible())
        assertEquals(1, user.matchSampleCount())
    }

    @Test
    fun runnerUpAmbiguityUsesEveryUsersRuntimeSamples() {
        val probe = runtimeTemplate(10)
        val enrolled = user("enrolled", 10, 12)
        val nearbySampleUser = user("nearby", 60, 14)

        val result = match(probe, listOf(enrolled, nearbySampleUser), ::fakeRuntimeSimilarity)

        assertEquals(0, result.index)
        assertTrue(result.score > 0.99)
        assertTrue(result.secondScore > 0.95)
    }

    @Test
    fun enrollmentDuplicateScoreUsesStoredSampleMaximum() {
        val probe = runtimeTemplate(10)
        val user = user("sample-match", 80, 12)

        assertTrue(enrollmentDuplicateScore(probe, user, ::fakeRuntimeSimilarity) > 0.95)
    }

    @Test
    fun weakScoreIsRejected() {
        assertFalse(
            acceptsAuthenticationCandidate(
                score = 0.79,
                secondScore = 0.0,
                supportCount = 5,
                availableSamples = 5
            )
        )
    }

    @Test
    fun multiSampleTemplateRequiresMoreThanOneSupportingSample() {
        assertFalse(
            acceptsAuthenticationCandidate(
                score = 0.91,
                secondScore = 0.50,
                supportCount = 1,
                availableSamples = 5
            )
        )
    }

    @Test
    fun strongSupportedCandidateIsAccepted() {
        assertTrue(
            acceptsAuthenticationCandidate(
                score = 0.91,
                secondScore = 0.50,
                supportCount = 2,
                availableSamples = 5
            )
        )
    }

    @Test
    fun ambiguousCandidateIsRejectedEvenWithHighScore() {
        assertFalse(
            acceptsAuthenticationCandidate(
                score = 0.91,
                secondScore = 0.89,
                supportCount = 3,
                availableSamples = 5
            )
        )
    }

    @Test
    fun runtimeComparisonFailuresAreCountedAndDoNotBecomeMatches() {
        val probe = runtimeTemplate(10)
        val result = match(probe, listOf(user("broken", 10, 12))) { _, _ ->
            error("binder failed")
        }

        assertEquals(-1, result.index)
        assertEquals(0, result.successfulComparisons)
        assertEquals(2, result.failedComparisons)
    }

    @Test
    fun partialRuntimeComparisonFailureCanStillUseEnoughValidSamples() {
        val probe = runtimeTemplate(10)
        val enrolled = user("partial", 10, 12)
        var calls = 0
        val result = match(probe, listOf(enrolled)) { first, second ->
            calls += 1
            if (calls == 1) error("temporary binder failure")
            fakeRuntimeSimilarity(first, second)
        }

        assertEquals(0, result.index)
        assertEquals(1, result.successfulComparisons)
        assertEquals(1, result.failedComparisons)
    }

    private fun user(name: String, primary: Int, sample: Int): UserTemplate {
        val template = runtimeTemplate(primary)
        return UserTemplate(
            name = name,
            template = template,
            samples = listOf(runtimeTemplate(sample)),
            engineId = FACE_ENGINE_ID,
            templateSize = template.size
        )
    }

    private fun runtimeTemplate(code: Int): ByteArray = ByteArray(32).also { it[0] = code.toByte() }

    private fun fakeRuntimeSimilarity(first: ByteArray, second: ByteArray): Double =
        1.0 - abs((first[0].toInt() and 0xff) - (second[0].toInt() and 0xff)) / 100.0
}
