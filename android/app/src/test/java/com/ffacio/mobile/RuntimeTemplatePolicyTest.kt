package com.ffacio.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeTemplatePolicyTest {
    @Test
    fun runtimeTemplateWithMatchingMetadataIsCompatible() {
        val template = ByteArray(32) { 1 }
        assertTrue(UserTemplate("runtime", template, engineId = FACE_ENGINE_ID, templateSize = template.size).isCompatible())
    }

    @Test
    fun legacyEngineTemplateFailsClosed() {
        val template = ByteArray(32) { 1 }
        assertFalse(UserTemplate("legacy", template, engineId = "insightface.legacy", templateSize = template.size).isCompatible())
    }

    @Test
    fun mismatchedDeclaredTemplateSizeFailsClosed() {
        val template = ByteArray(32) { 1 }
        assertFalse(UserTemplate("damaged", template, engineId = FACE_ENGINE_ID, templateSize = 64).isCompatible())
    }

    @Test
    fun previousEnrollmentStoreIsDiscardedForSingleTemplatePolicy() {
        assertTrue(needsUserStorePolicyReset(storedVersion = 0))
        assertTrue(needsUserStorePolicyReset(storedVersion = USER_STORE_POLICY_VERSION - 1))
        assertFalse(needsUserStorePolicyReset(storedVersion = USER_STORE_POLICY_VERSION))
    }

    @Test
    fun persistenceSnapshotOwnsIndependentTemplateBytes() {
        val original = UserTemplate("runtime", ByteArray(32) { 1 }, engineId = FACE_ENGINE_ID, templateSize = 32)
        val snapshot = original.copyForRuntimeDecision()

        assertNotSame(original.template, snapshot.template)
        snapshot.template[0] = 9
        assertEquals(1, original.template[0].toInt())
        listOf(snapshot).wipeTemplates()
        assertTrue(snapshot.template.all { it == 0.toByte() })
        assertEquals(1, original.template[0].toInt())
    }

    @Test
    fun futureOrUnknownStorePolicyAlsoResetsFailClosed() {
        assertTrue(needsUserStorePolicyReset(storedVersion = USER_STORE_POLICY_VERSION + 1))
    }

}
