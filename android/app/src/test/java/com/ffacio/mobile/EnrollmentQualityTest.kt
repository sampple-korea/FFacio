package com.ffacio.mobile

import com.kbyai.facesdk.FaceBox
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentQualityTest {
    @Test
    fun largestFaceIsSelectedWhenSeveralFacesAreVisible() {
        val small = face(10, 10, 110, 110)
        val largest = face(20, 20, 320, 320)
        val medium = face(30, 30, 230, 230)

        assertSame(largest, largestRuntimeFace(listOf(small, largest, medium)))
    }

    @Test
    fun firstFaceWinsWhenAreasAreEqual() {
        val first = face(0, 0, 100, 100)
        val second = face(50, 50, 150, 150)

        assertSame(first, largestRuntimeFace(listOf(first, second)))
    }

    @Test
    fun faceCenterMustRemainInsideRuntimeDemoGuide() {
        assertTrue(isRuntimeFaceCentered(face(400, 200, 600, 500), frameWidth = 1000, frameHeight = 700))
        assertFalse(isRuntimeFaceCentered(face(0, 0, 100, 100), frameWidth = 1000, frameHeight = 700))
    }

    @Test
    fun singleTemplateEnrollmentNeedsOnlyStableNeutralCapture() {
        val tracker = EnrollmentStabilityTracker(stableMillis = 1200L)
        val first = observation(code = 10, quality = 0.60f)
        val sharper = observation(code = 20, quality = 0.90f)

        assertFalse(tracker.update(first, now = 1_000L))
        assertFalse(tracker.update(sharper, now = 2_199L))
        assertTrue(tracker.update(sharper, now = 2_200L))
        assertArrayEquals(sharper.enrollmentCapture!!.bytes, tracker.takeCapture()!!.bytes)
    }

    @Test
    fun invalidFrameResetsSingleTemplateStabilityWindow() {
        val tracker = EnrollmentStabilityTracker(stableMillis = 1200L)

        assertFalse(tracker.update(observation(10, 0.8f), now = 1_000L))
        assertFalse(tracker.update(Observation.fail("no face"), now = 2_100L))
        assertFalse(tracker.update(observation(20, 0.9f), now = 2_200L))
        assertEquals(0.0f, tracker.progress(now = 2_200L), 0.0f)
    }

    @Test
    fun monotonicClockRegressionRestartsEnrollmentWindow() {
        val tracker = EnrollmentStabilityTracker(stableMillis = 1200L)

        assertFalse(tracker.update(observation(10, 0.8f), now = 2_000L))
        assertFalse(tracker.update(observation(20, 0.9f), now = 1_000L))
        assertEquals(0.0f, tracker.progress(now = 1_000L), 0.0f)
        assertTrue(tracker.update(observation(20, 0.9f), now = 2_200L))
    }

    @Test
    fun stabilityTrackerOwnsBestFrameAndResetWipesIt() {
        val tracker = EnrollmentStabilityTracker(stableMillis = 1200L)
        val source = observation(42, 0.9f)
        val originalBytes = source.enrollmentCapture!!.bytes

        assertFalse(tracker.update(source, now = 1_000L))
        originalBytes.fill(0)
        val owned = tracker.takeCapture()!!
        assertEquals(42, owned.bytes[0].toInt())
        owned.wipe()
        assertTrue(owned.bytes.all { it == 0.toByte() })
    }

    @Test
    fun authenticationStabilizationIsExactlyOneFrame() {
        assertEquals(1, AUTH_STABLE_FRAMES)
    }

    private fun face(x1: Int, y1: Int, x2: Int, y2: Int): FaceBox = FaceBox().apply {
        this.x1 = x1
        this.y1 = y1
        this.x2 = x2
        this.y2 = y2
    }

    private fun observation(code: Int, quality: Float): Observation = Observation(
        ok = true,
        message = "ok",
        template = ByteArray(0),
        quality = quality,
        frameTimestampMillis = code.toLong(),
        enrollmentCapture = RuntimeEnrollmentCapture(
            bytes = ByteArray(32).also { it[0] = code.toByte() },
            width = 640,
            height = 480,
            rotationDegrees = 90,
            frontFacing = true,
            quality = quality,
            capturedAtMillis = code.toLong()
        )
    )
}
