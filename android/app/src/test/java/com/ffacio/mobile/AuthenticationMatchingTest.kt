package com.ffacio.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun oneRuntimeTemplateIsEnoughForCompatibility() {
        val template = runtimeTemplate(10)
        val user = UserTemplate(
            name = "primary-only",
            template = template,
            engineId = FACE_ENGINE_ID,
            templateSize = template.size
        )

        assertTrue(user.isCompatible())
    }

    @Test
    fun runnerUpAmbiguityComparesOneRepresentativeTemplatePerUser() {
        val probe = runtimeTemplate(10)
        val enrolled = user("enrolled", 10)
        val nearby = user("nearby", 14)

        val result = match(probe, listOf(enrolled, nearby), ::fakeRuntimeSimilarity)

        assertEquals(0, result.index)
        assertTrue(result.score > 0.99)
        assertTrue(result.secondScore > 0.95)
        assertEquals(2, result.successfulComparisons)
    }

    @Test
    fun enrollmentDuplicateScoreUsesOnlyRepresentativeTemplate() {
        val probe = runtimeTemplate(10)
        val enrolled = user("same", 12)

        assertTrue(enrollmentDuplicateScore(probe, enrolled, ::fakeRuntimeSimilarity) > 0.95)
    }

    @Test
    fun enrollmentDuplicateSearchFindsHighestScoreInsteadOfFirstMatch() {
        val probe = runtimeTemplate(10)
        val result = findBestEnrollmentDuplicate(
            probe,
            listOf(user("first", 18), user("closest", 11)),
            ::fakeRuntimeSimilarity,
            threshold = 0.80
        )

        assertEquals("closest", result.userName)
        assertTrue(result.score > 0.98)
        assertEquals(0, result.failedComparisons)
    }

    @Test
    fun enrollmentComparisonRetriesOnceAndCompletesWhenRuntimeRecovers() {
        val probe = runtimeTemplate(10)
        var calls = 0
        val result = findBestEnrollmentDuplicate(
            probe,
            listOf(user("broken", 10), user("valid", 12)),
            comparator = { first, second ->
                calls += 1
                if (calls == 1) error("temporary binder failure")
                fakeRuntimeSimilarity(first, second)
            },
            threshold = 0.80
        )

        assertEquals("broken", result.userName)
        assertEquals(0, result.failedComparisons)
        assertTrue(result.comparisonComplete)
    }

    @Test
    fun enrollmentFailsClosedWhenExistingTemplateCannotBeCompared() {
        val probe = runtimeTemplate(10)
        val wrongSize = UserTemplate(
            name = "wrong-size",
            template = ByteArray(64) { 1 },
            engineId = FACE_ENGINE_ID,
            templateSize = 64
        )
        val result = findBestEnrollmentDuplicate(probe, listOf(wrongSize), ::fakeRuntimeSimilarity)
        assertFalse(result.comparisonComplete)
        assertEquals(1, result.failedComparisons)
    }

    @Test
    fun enrollmentFailsClosedAfterBothDuplicateComparisonAttemptsFail() {
        val result = findBestEnrollmentDuplicate(
            runtimeTemplate(10),
            listOf(user("same", 10)),
            comparator = { _, _ -> error("binder unavailable") }
        )
        assertFalse(result.comparisonComplete)
        assertEquals(1, result.failedComparisons)
    }

    @Test
    fun noEnrollmentDuplicateAboveThresholdStillReturnsComparisonDiagnostics() {
        val probe = runtimeTemplate(10)
        val result = findBestEnrollmentDuplicate(
            probe,
            listOf(user("different", 70)),
            ::fakeRuntimeSimilarity,
            threshold = 0.80
        )

        assertEquals(null, result.userName)
        assertTrue(result.score < 0.80)
        assertEquals(0, result.failedComparisons)
    }

    @Test
    fun authenticationFailsClosedWhenAnyEligibleComparisonFails() {
        assertTrue(authenticationComparisonComplete(Match(0, 0.9, 0.4, successfulComparisons = 2, failedComparisons = 0)))
        assertFalse(authenticationComparisonComplete(Match(0, 0.9, 0.4, successfulComparisons = 1, failedComparisons = 1)))
        assertFalse(authenticationComparisonComplete(Match(-1, -1.0, -1.0, successfulComparisons = 0, failedComparisons = 0)))
    }

    @Test
    fun weakScoreIsRejected() {
        assertFalse(acceptsAuthenticationCandidate(score = 0.79, secondScore = 0.0))
    }

    @Test
    fun oneStrongFrameIsAccepted() {
        assertTrue(acceptsAuthenticationCandidate(score = 0.91, secondScore = 0.50))
    }

    @Test
    fun ambiguousCandidateIsRejectedEvenWithHighScore() {
        assertFalse(acceptsAuthenticationCandidate(score = 0.91, secondScore = 0.89))
    }

    @Test
    fun runtimeComparisonFailuresAreCountedAndDoNotBecomeMatches() {
        val probe = runtimeTemplate(10)
        val result = match(probe, listOf(user("broken", 10))) { _, _ ->
            error("binder failed")
        }

        assertEquals(-1, result.index)
        assertEquals(0, result.successfulComparisons)
        assertEquals(1, result.failedComparisons)
    }

    @Test
    fun oneUsersFailureDoesNotBlockAnotherValidComparison() {
        val probe = runtimeTemplate(10)
        var calls = 0
        val result = match(probe, listOf(user("broken", 10), user("valid", 12))) { first, second ->
            calls += 1
            if (calls == 1) error("temporary binder failure")
            fakeRuntimeSimilarity(first, second)
        }

        assertEquals(1, result.index)
        assertEquals(1, result.successfulComparisons)
        assertEquals(1, result.failedComparisons)
    }

    @Test
    fun authenticationCountsTemplateSizeMismatchAsFailure() {
        val result = match(
            runtimeTemplate(10),
            listOf(UserTemplate("wrong-size", ByteArray(64), FACE_ENGINE_ID, 64)),
            ::fakeRuntimeSimilarity
        )
        assertEquals(0, result.successfulComparisons)
        assertEquals(1, result.failedComparisons)
        assertFalse(authenticationComparisonComplete(result))
    }

    @Test
    fun exactThresholdAndMarginAreAccepted() {
        assertTrue(acceptsAuthenticationCandidate(score = 0.80, secondScore = 0.77))
    }

    @Test
    fun nonFiniteComparisonIsCountedAsFailure() {
        val result = match(runtimeTemplate(10), listOf(user("nan", 10))) { _, _ -> Double.NaN }
        assertEquals(-1, result.index)
        assertEquals(0, result.successfulComparisons)
        assertEquals(1, result.failedComparisons)
    }

    private fun user(name: String, code: Int): UserTemplate {
        val template = runtimeTemplate(code)
        return UserTemplate(
            name = name,
            template = template,
            engineId = FACE_ENGINE_ID,
            templateSize = template.size
        )
    }

    private fun runtimeTemplate(code: Int): ByteArray = ByteArray(32).also { it[0] = code.toByte() }

    private fun fakeRuntimeSimilarity(first: ByteArray, second: ByteArray): Double =
        1.0 - abs((first[0].toInt() and 0xff) - (second[0].toInt() and 0xff)) / 100.0
    @Test
    fun userNamesAreNormalizedValidatedAndUnique() {
        val existing = listOf(user("홍  길동", 10))
        assertEquals("홍 길동", normalizeUserName("  홍   길동  "))
        assertTrue(userNameValid("홍 길동"))
        assertFalse(userNameValid(""))
        assertFalse(userNameValid("bad\u0000name"))
        assertFalse(userNameValid("가".repeat(MAX_USER_NAME_LENGTH + 1)))
        assertTrue(registeredNameExists(" 홍 길동 ", existing))
        assertFalse(registeredNameExists("다른 사람", existing))
    }


    @Test
    fun comparisonScoresOutsideUnitRangeFailClosed() {
        val probe = runtimeTemplate(10)
        val users = listOf(user("candidate", 10))

        val aboveOne = match(probe, users) { _, _ -> 1.01 }
        assertEquals(0, aboveOne.successfulComparisons)
        assertEquals(1, aboveOne.failedComparisons)
        assertFalse(authenticationComparisonComplete(aboveOne))
        assertFalse(acceptsAuthenticationCandidate(1.01, -1.0))

        val belowZero = match(probe, users) { _, _ -> -0.01 }
        assertEquals(0, belowZero.successfulComparisons)
        assertEquals(1, belowZero.failedComparisons)
        assertFalse(authenticationComparisonComplete(belowZero))
        assertFalse(acceptsAuthenticationCandidate(-0.01, -1.0))
    }

    @Test
    fun duplicateComparisonOutsideUnitRangeIsRetriedThenBlocksSave() {
        val probe = runtimeTemplate(10)
        var calls = 0
        val result = findBestEnrollmentDuplicate(
            template = probe,
            users = listOf(user("candidate", 10)),
            comparator = { _, _ -> calls += 1; 1.2 }
        )
        assertEquals(2, calls)
        assertFalse(result.comparisonComplete)
        assertEquals(1, result.failedComparisons)
    }

}
