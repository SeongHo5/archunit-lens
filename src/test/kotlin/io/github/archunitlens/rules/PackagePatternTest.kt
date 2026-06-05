package io.github.archunitlens.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackagePatternTest {
    @Test
    fun matchesSegmentBoundedMiddlePattern() {
        assertTrue(PackagePattern.matches("..domain..", "com.example.domain.order"))
        assertFalse(PackagePattern.matches("..domain..", "com.example.notdomain.order"))
    }

    @Test
    fun matchesPrefixAndSuffixPatterns() {
        assertTrue(PackagePattern.matches("org.springframework.web..", "org.springframework.web.client.RestClient"))
        assertFalse(PackagePattern.matches("org.springframework.web..", "com.example.org.springframework.web"))

        assertTrue(PackagePattern.matches("..controller", "com.example.presentation.controller"))
        assertFalse(PackagePattern.matches("..controller", "com.example.presentation.controller.api"))
    }
}
