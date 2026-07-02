package io.github.archunitlens.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.concurrent.TimeUnit

class ArchUnitLensToolWindowFactoryTest : BasePlatformTestCase() {
    fun testCurrentJavaPackageReadsPsiInsideReadAction() {
        val file = myFixture.addFileToProject(
            "src/test/java/com/example/ArchitectureRules.java",
            """
                package com.example;

                class ArchitectureRules {
                }
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(file.virtualFile)

        val packageName = ApplicationManager.getApplication()
            .executeOnPooledThread<String> { currentJavaPackage(project) }
            .get(10, TimeUnit.SECONDS)

        assertEquals("com.example", packageName)
    }
}
