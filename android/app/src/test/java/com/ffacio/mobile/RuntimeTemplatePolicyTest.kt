package com.ffacio.mobile

import org.junit.Assert.assertFalse
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
    fun mismatchedAuxiliarySampleFailsClosed() {
        val template = ByteArray(32) { 1 }
        val user = UserTemplate(
            "damaged-sample",
            template,
            samples = listOf(ByteArray(64) { 1 }),
            engineId = FACE_ENGINE_ID,
            templateSize = template.size
        )

        assertFalse(user.isCompatible())
    }

}
