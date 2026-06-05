package io.github.archunitlens

import org.junit.Assert.assertEquals
import org.junit.Test

class ArchUnitLensTest {
    @Test
    fun pluginIdentityIsArchUnitLens() {
        assertEquals("io.github.archunitlens", ArchUnitLens.PLUGIN_ID)
        assertEquals("ArchUnit Lens", ArchUnitLens.PLUGIN_NAME)
    }
}
