@file:Suppress("UnstableApiUsage")

import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.performanceTest.ProfilerName
import org.jetbrains.intellij.platform.gradle.tasks.TestIdePerformanceTask

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
    id("org.jlleitschuh.gradle.ktlint")
}

group = "io.github.archunitlens"

val archUnitReferenceVersion = providers.gradleProperty("archUnit.reference.version")
val archUnitReferenceSources by configurations.creating {
    description = "ArchUnit source JAR for local DSL reference only; not used on plugin compile/runtime classpaths."
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    archUnitReferenceSources("com.tngtech.archunit:archunit:${archUnitReferenceVersion.get()}:sources@jar")

    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.2.6.2")
        bundledPlugin("com.intellij.java")
        javaCompiler()
        testFramework(TestFrameworkType.Platform)
    }
}

/**
 * Unpacks selected ArchUnit source files for local DSL analysis without adding
 * ArchUnit to the plugin compile or runtime classpaths.
 */
tasks.register<Copy>("unpackArchUnitReferenceSources") {
    group = "documentation"
    description = "Unpack ArchUnit source reference into build/reference-sources/archunit/<version>."

    from({ archUnitReferenceSources.files.map { zipTree(it) } })
    include(
        "com/tngtech/archunit/lang/syntax/**",
        "com/tngtech/archunit/lang/conditions/**",
        "com/tngtech/archunit/lang/ArchRule*.java",
    )
    into(layout.buildDirectory.dir("reference-sources/archunit/${archUnitReferenceVersion.get()}"))
}

val testIdePerformanceArtifacts = layout.buildDirectory.dir("reports/testIdePerformance")

tasks.named<TestIdePerformanceTask>("testIdePerformance") {
    testDataDirectory.set(layout.projectDirectory.dir("src/test/performance"))
    artifactsDirectory.set(testIdePerformanceArtifacts)
    profilerName.set(ProfilerName.ASYNC)
}
