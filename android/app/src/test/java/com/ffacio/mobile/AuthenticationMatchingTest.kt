package com.ffacio.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationMatchingTest {
    @Test
    fun mismatchedEmbeddingDimensionsFailClosed() {
        assertEquals(
            -1.0,
            cosine(FloatArray(512) { 1.0f }, FloatArray(128) { 1.0f }),
            0.0
        )
    }

    @Test
    fun currentEngineTemplateWithoutSamplesIsIncompatible() {
        val template = UserTemplate(
            name = "missing-samples",
            embedding = unitEmbedding(0),
            samples = emptyList(),
            engineId = FACE_ENGINE_ID,
            embeddingSize = FACE_EMBEDDING_SIZE
        )

        assertFalse(template.isCompatible())
    }

    @Test
    fun runnerUpAmbiguityUsesOtherUsersEnrollmentSamples() {
        val probe = unitEmbedding(0)
        val enrolled = UserTemplate(
            name = "enrolled",
            embedding = unitEmbedding(0),
            samples = listOf(unitEmbedding(0), mixedEmbedding(0, 1, 0.98f)),
            engineId = FACE_ENGINE_ID,
            embeddingSize = FACE_EMBEDDING_SIZE
        )
        val nearbySampleUser = UserTemplate(
            name = "nearby-sample",
            embedding = unitEmbedding(2),
            samples = listOf(mixedEmbedding(0, 2, 0.97f), unitEmbedding(2)),
            engineId = FACE_ENGINE_ID,
            embeddingSize = FACE_EMBEDDING_SIZE
        )

        val result = match(probe, listOf(enrolled, nearbySampleUser))

        assertEquals(0, result.index)
        assertTrue(result.secondScore > 0.90)
    }

    @Test
    fun weakScoreThatPreviouslyCouldPassIsRejected() {
        assertFalse(
            acceptsAuthenticationCandidate(
                score = 0.50,
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
                score = 0.72,
                secondScore = 0.40,
                supportCount = 1,
                availableSamples = 5
            )
        )
    }

    @Test
    fun strongSupportedCandidateIsAccepted() {
        assertTrue(
            acceptsAuthenticationCandidate(
                score = 0.72,
                secondScore = 0.40,
                supportCount = 2,
                availableSamples = 5
            )
        )
    }

    @Test
    fun ambiguousCandidateIsRejectedEvenWithHighScore() {
        assertFalse(
            acceptsAuthenticationCandidate(
                score = 0.72,
                secondScore = 0.67,
                supportCount = 3,
                availableSamples = 5
            )
        )
    }

    private fun unitEmbedding(index: Int): FloatArray =
        FloatArray(FACE_EMBEDDING_SIZE).also { it[index] = 1.0f }

    private fun mixedEmbedding(primary: Int, secondary: Int, primaryWeight: Float): FloatArray {
        val secondaryWeight = kotlin.math.sqrt(1.0f - primaryWeight * primaryWeight)
        return FloatArray(FACE_EMBEDDING_SIZE).also {
            it[primary] = primaryWeight
            it[secondary] = secondaryWeight
        }
    }
}
