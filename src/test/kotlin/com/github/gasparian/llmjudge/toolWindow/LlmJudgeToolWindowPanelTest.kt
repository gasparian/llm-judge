package com.github.gasparian.llmjudge.toolWindow

import com.github.gasparian.llmjudge.services.LlmJudgeProjectService
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBScrollPane
import javax.swing.JComponent

class LlmJudgeToolWindowPanelTest : BasePlatformTestCase() {
    // no testData needed here
    override fun getTestDataPath(): String = ""

    fun testPanelInitialization() {
        // 1) Get the real service (it won't hit judge.json here)
        val service = project.service<LlmJudgeProjectService>()
        // 2) Instantiate your panel
        val panel = LlmJudgeToolWindowPanel(service)

        // It should have exactly two children: the JBScrollPane (table) and the toolbar component
        assertEquals("Wrong number of components", 2, panel.componentCount)
        assertTrue(
            "First component should be the scroll pane for the table",
            panel.getComponent(0) is JBScrollPane,
        )
        assertTrue(
            "Second component should be the toolbar (a JComponent)",
            panel.getComponent(1) is JComponent,
        )
    }
}
