package io.github.archunitlens.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackagePatternTest {
    @Test
    fun matchesSegmentBoundedMiddlePattern() {
        assertTrue(PackagePattern.matches("..domain..", "com.example.domain.order"))
        assertFalse(PackagePattern.matches("..domain..", "com.example.notdomain.order"))
        assertTrue(PackagePattern.matches("..service..", "com.example.service"))
        assertTrue(PackagePattern.matches("..service..", "com.example.service.order"))
        assertFalse(PackagePattern.matches("..service..", "com.example.services"))
        assertFalse(PackagePattern.matches("..service..", "com.example.notservice"))
    }

    @Test
    fun matchesPrefixAndSuffixPatterns() {
        assertTrue(PackagePattern.matches("org.springframework.web..", "org.springframework.web.client.RestClient"))
        assertFalse(PackagePattern.matches("org.springframework.web..", "com.example.org.springframework.web"))

        assertTrue(PackagePattern.matches("..controller", "com.example.presentation.controller"))
        assertFalse(PackagePattern.matches("..controller", "com.example.presentation.controller.api"))
    }
}
