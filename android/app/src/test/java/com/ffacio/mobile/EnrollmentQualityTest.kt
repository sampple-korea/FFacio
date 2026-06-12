package com.ffacio.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentQualityTest {
    @Test
    fun repeatedSampleIsRejected() {
        val sample = floatArrayOf(1.0f, 0.0f, 0.0f)
        val result = enrollmentSampleDecision(
            embedding = floatArrayOf(1.0f, 0.0f, 0.0f),
            pose = 0,
            samples = listOf(sample),
            poses = listOf(0)
        )

        assertFalse(result.accepted)
    }

    @Test
    fun repeatedEarlierSampleIsRejected() {
        val result = enrollmentSampleDecision(
            embedding = floatArrayOf(1.0f, 0.0f, 0.0f),
            pose = 0,
            samples = listOf(
                floatArrayOf(1.0f, 0.0f, 0.0f),
                floatArrayOf(0.0f, 1.0f, 0.0f)
            ),
            poses = listOf(0, 1)
        )

        assertFalse(result.accepted)
    }

    @Test
    fun finalSampleNeedsPoseDiversity() {
        val samples = listOf(
            floatArrayOf(1.0f, 0.0f, 0.0f),
            floatArrayOf(0.96f, 0.28f, 0.0f),
            floatArrayOf(0.92f, 0.39f, 0.0f),
            floatArrayOf(0.87f, 0.49f, 0.0f)
        )
        val result = enrollmentSampleDecision(
            embedding = floatArrayOf(0.40f, 0.0f, 0.916f),
            pose = 0,
            samples = samples,
            poses = listOf(0, 0, 0, 0)
        )

        assertFalse(result.accepted)
    }

    @Test
    fun diverseFinalSampleIsAccepted() {
        val samples = listOf(
            floatArrayOf(1.0f, 0.0f, 0.0f),
            floatArrayOf(0.96f, 0.28f, 0.0f),
            floatArrayOf(0.92f, 0.39f, 0.0f),
            floatArrayOf(0.87f, 0.49f, 0.0f)
        )
        val result = enrollmentSampleDecision(
            embedding = floatArrayOf(0.40f, 0.0f, 0.916f),
            pose = 1,
            samples = samples,
            poses = listOf(0, 0, 0, 0)
        )

        assertTrue(result.accepted)
    }

    @Test
    fun cohesiveTemplateQualityIsAccepted() {
        val result = enrollmentTemplateQuality(
            centroid = floatArrayOf(1.0f, 0.0f, 0.0f),
            samples = listOf(
                floatArrayOf(1.0f, 0.0f, 0.0f),
                floatArrayOf(0.92f, 0.20f, 0.0f),
                floatArrayOf(0.88f, -0.22f, 0.0f),
                floatArrayOf(0.86f, 0.12f, 0.0f),
                floatArrayOf(0.90f, -0.18f, 0.0f)
            )
        )

        assertTrue(result.accepted)
    }

    @Test
    fun contaminatedTemplateQualityIsRejected() {
        val result = enrollmentTemplateQuality(
            centroid = floatArrayOf(1.0f, 0.0f, 0.0f),
            samples = listOf(
                floatArrayOf(1.0f, 0.0f, 0.0f),
                floatArrayOf(0.92f, 0.20f, 0.0f),
                floatArrayOf(0.88f, -0.22f, 0.0f),
                floatArrayOf(0.0f, 1.0f, 0.0f),
                floatArrayOf(0.90f, -0.18f, 0.0f)
            )
        )

        assertFalse(result.accepted)
    }

    @Test
    fun splitIdentityTemplateQualityIsRejectedByPairwiseCohesion() {
        val result = enrollmentTemplateQuality(
            centroid = floatArrayOf(0.86f, 0.51f, 0.0f),
            samples = listOf(
                floatArrayOf(1.0f, 0.0f, 0.0f),
                floatArrayOf(0.98f, 0.18f, 0.0f),
                floatArrayOf(0.96f, -0.22f, 0.0f),
                floatArrayOf(0.55f, 0.835f, 0.0f),
                floatArrayOf(0.50f, 0.866f, 0.0f)
            )
        )

        assertFalse(result.accepted)
    }
}
