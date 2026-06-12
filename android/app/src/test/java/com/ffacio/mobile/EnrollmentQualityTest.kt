package com.ffacio.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentQualityTest {
    @Test
    fun enrollmentTargetPoseGuidesFiveStepPoseSequence() {
        assertEquals(0, enrollmentTargetPose(sampleCount = 0, poses = emptyList()))
        assertEquals(-1, enrollmentTargetPose(sampleCount = 1, poses = listOf(0)))
        assertEquals(1, enrollmentTargetPose(sampleCount = 2, poses = listOf(0, -1)))
        assertEquals(-1, enrollmentTargetPose(sampleCount = 3, poses = listOf(0, -1, 1)))
        assertEquals(1, enrollmentTargetPose(sampleCount = 4, poses = listOf(0, -1, 1, -1)))
        assertEquals(null, enrollmentTargetPose(sampleCount = 5, poses = listOf(0, -1, 1, -1, 1)))
    }

    @Test
    fun wrongTargetPoseIsRejectedDuringGuidedEnrollment() {
        val result = enrollmentSampleDecision(
            embedding = floatArrayOf(0.0f, 1.0f, 0.0f),
            pose = 1,
            samples = listOf(floatArrayOf(1.0f, 0.0f, 0.0f)),
            poses = listOf(0)
        )

        assertFalse(result.accepted)
        assertEquals("왼쪽으로 살짝 돌려 주세요", result.status)
    }

    @Test
    fun repeatedSampleIsRejected() {
        val sample = floatArrayOf(1.0f, 0.0f, 0.0f)
        val result = enrollmentSampleDecision(
            embedding = floatArrayOf(1.0f, 0.0f, 0.0f),
            pose = -1,
            samples = listOf(
                sample,
                floatArrayOf(0.0f, 1.0f, 0.0f),
                floatArrayOf(0.0f, 0.0f, 1.0f)
            ),
            poses = listOf(0, -1, 1)
        )

        assertFalse(result.accepted)
        assertEquals("고개를 좌우로 천천히 돌려 주세요", result.status)
    }

    @Test
    fun repeatedEarlierSampleIsRejected() {
        val result = enrollmentSampleDecision(
            embedding = floatArrayOf(1.0f, 0.0f, 0.0f),
            pose = -1,
            samples = listOf(
                floatArrayOf(1.0f, 0.0f, 0.0f),
                floatArrayOf(0.0f, 1.0f, 0.0f),
                floatArrayOf(0.0f, 0.0f, 1.0f)
            ),
            poses = listOf(0, -1, 1)
        )

        assertFalse(result.accepted)
        assertEquals("고개를 좌우로 천천히 돌려 주세요", result.status)
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
            poses = listOf(0, -1, 1, -1)
        )

        assertTrue(result.accepted)
    }

    @Test
    fun enrollmentPoseHoldRequiresStablePoseDuration() {
        val hold = EnrollmentPoseHold(holdMillis = 400L)

        assertFalse(hold.update(target = -1, pose = -1, now = 1_000L))
        assertFalse(hold.update(target = -1, pose = -1, now = 1_250L))
        assertTrue(hold.progress() in 0.5f..0.7f)
        assertTrue(hold.update(target = -1, pose = -1, now = 1_420L))
    }

    @Test
    fun enrollmentPoseHoldResetsWhenPoseChanges() {
        val hold = EnrollmentPoseHold(holdMillis = 400L)

        assertFalse(hold.update(target = -1, pose = -1, now = 1_000L))
        assertFalse(hold.update(target = -1, pose = 1, now = 1_300L))
        assertEquals(0.0f, hold.progress(), 0.0f)
    }

    @Test
    fun finalTemplateQualityRequiresPoseCoverageWhenProvided() {
        val result = enrollmentTemplateQuality(
            centroid = floatArrayOf(1.0f, 0.0f, 0.0f),
            samples = listOf(
                floatArrayOf(1.0f, 0.0f, 0.0f),
                floatArrayOf(0.92f, 0.20f, 0.0f),
                floatArrayOf(0.88f, -0.22f, 0.0f),
                floatArrayOf(0.86f, 0.12f, 0.0f),
                floatArrayOf(0.90f, -0.18f, 0.0f)
            ),
            poses = listOf(0, 0, 0, -1, 1)
        )

        assertFalse(result.accepted)
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
