package com.ffacio.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
            template = template(20),
            pose = 1,
            samples = listOf(template(10)),
            poses = listOf(0),
            comparator = ::similarity
        )

        assertFalse(result.accepted)
        assertEquals("왼쪽으로 살짝 돌려 주세요", result.status)
    }

    @Test
    fun repeatedRuntimeTemplateIsRejected() {
        val result = enrollmentSampleDecision(
            template = template(10),
            pose = -1,
            samples = listOf(template(10)),
            poses = listOf(0),
            comparator = ::similarity
        )

        assertFalse(result.accepted)
        assertEquals("고개를 좌우로 천천히 돌려 주세요", result.status)
    }

    @Test
    fun finalSampleNeedsPoseDiversity() {
        val samples = listOf(template(10), template(14), template(18), template(22))
        val result = enrollmentSampleDecision(
            template = template(26),
            pose = 0,
            samples = samples,
            poses = listOf(0, 0, 0, 0),
            comparator = ::similarity
        )

        assertFalse(result.accepted)
    }

    @Test
    fun diverseFinalSampleIsAccepted() {
        val samples = listOf(template(10), template(14), template(18), template(22))
        val result = enrollmentSampleDecision(
            template = template(26),
            pose = 1,
            samples = samples,
            poses = listOf(0, -1, 1, -1),
            comparator = ::similarity
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
        val samples = listOf(template(10), template(14), template(18), template(22), template(26))
        val result = enrollmentTemplateQuality(
            representative = template(18),
            samples = samples,
            poses = listOf(0, 0, 0, -1, 1),
            comparator = ::similarity
        )

        assertFalse(result.accepted)
    }

    @Test
    fun cohesiveRuntimeTemplateSetIsAccepted() {
        val samples = listOf(template(10), template(14), template(18), template(22), template(26))
        val result = enrollmentTemplateQuality(
            representative = template(18),
            samples = samples,
            poses = listOf(0, -1, 1, -1, 1),
            comparator = ::similarity
        )

        assertTrue(result.accepted)
    }

    @Test
    fun contaminatedRuntimeTemplateSetIsRejected() {
        val samples = listOf(template(10), template(14), template(18), template(90), template(22))
        val result = enrollmentTemplateQuality(
            representative = template(18),
            samples = samples,
            poses = listOf(0, -1, 1, -1, 1),
            comparator = ::similarity
        )

        assertFalse(result.accepted)
    }

    private fun template(code: Int): ByteArray = ByteArray(32).also { it[0] = code.toByte() }

    private fun similarity(first: ByteArray, second: ByteArray): Double =
        1.0 - abs((first[0].toInt() and 0xff) - (second[0].toInt() and 0xff)) / 100.0
}
