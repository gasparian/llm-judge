package com.github.gasparian.llmjudge.toolWindow

import com.github.gasparian.llmjudge.services.LlmJudgeProjectService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class LlmJudgeToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = project.getService(LlmJudgeProjectService::class.java)
        val panel = LlmJudgeToolWindowPanel(service)
        val content = ContentFactory.getInstance()
            .createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }
    override fun shouldBeAvailable(project: Project): Boolean = true
}
