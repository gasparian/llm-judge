package com.github.gasparian.llmjudge.services

import com.github.gasparian.llmjudge.LlmJudgeBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class LlmJudgeProjectService(project: Project) {

    init {
        thisLogger().info(LlmJudgeBundle.message("projectService", project.name))
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    fun getRandomNumber() = (1..100).random()
}
