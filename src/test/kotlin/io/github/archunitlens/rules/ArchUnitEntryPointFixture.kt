package io.github.archunitlens.rules

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import java.nio.file.Path

/** Installs only the declarations needed to resolve genuine ArchUnit roots in PSI fixtures. */
fun CodeInsightTestFixture.addArchUnitEntryPointDeclarations() {
    listOf(
        "lang/ArchRule.java",
        "lang/syntax/ArchRuleDefinition.java",
        "lang/syntax/elements/GivenClasses.java",
    ).forEach { relativePath ->
        addFileToProject(
            "src/test/java/com/tngtech/archunit/$relativePath",
            Path.of("src/test/testData/archunitEntryPoints/$relativePath").toFile().readText(),
        )
    }
}
