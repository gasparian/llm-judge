package com.github.gasparian.llmjudge.services

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class LlmJudgeProjectServiceTest : BasePlatformTestCase() {
    /**
     * Before calling loadConfig(), write our test JSON
     * into the in-memory project root so that
     * LlmJudgeProjectService will find it.
     */
    private fun writeFixtureJson() {
        // Load judge.json from test resources (src/test/resources/judge.json)
        val text = javaClass.classLoader
            .getResourceAsStream("judge.json")
            ?.bufferedReader()
            ?.readText()
            ?: error("Test resource judge.json not found on classpath")

        // Write into the sandbox project directory
        val base = project.basePath
            ?: error("Project basePath is null")
        File(base, "judge.json").writeText(text)
    }

    fun testLoadConfigReadsJudgeJsonCorrectly() {
        // 1) Copy the fixture into the project
        writeFixtureJson()

        // 2) Invoke our service
        val svc = project.service<LlmJudgeProjectService>()
        val cfg = svc.loadConfig()

        // 3) Assertions
        assertEquals("script.py", cfg.modelPath)
        assertEquals(2, cfg.data.size)

        with(cfg.data[0]) {
            assertEquals("input1", input)
            assertEquals("ref1", referenceOutput)
        }
        with(cfg.data[1]) {
            assertEquals("input2", input)
            assertEquals("ref2", referenceOutput)
        }
    }
}
