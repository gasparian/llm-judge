package com.github.gasparian.llmjudge.toolWindow

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.io.File
import java.nio.file.Paths
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationGroupManager

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import java.time.Duration

object OpenAIJavaService {
    /**
     * Create an OpenAIClient using the OkHttp transport.
     */
    fun create(apiKey: String): OpenAIClient =
    OpenAIOkHttpClient.builder()
        .apiKey(apiKey)
        .timeout(Duration.ofSeconds(30))
        .build();
}

data class Entry @JsonCreator constructor(
    @JsonProperty("input")           val input: String,
    @JsonProperty("reference_output") val referenceOutput: String
)

data class Config @JsonCreator constructor(
    @JsonProperty("model_path") val modelPath: String,
    @JsonProperty("data")       val data: List<Entry>
)

class MyToolWindowFactory : ToolWindowFactory {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())

        // Setup table with four columns
        val columns = arrayOf("Input", "Reference Output", "Model Output", "Score")
        val tableModel = DefaultTableModel(columns, 0)
        val table = JBTable(tableModel).apply { fillsViewportHeight = true }
        panel.add(JBScrollPane(table), BorderLayout.CENTER)

        // Toolbar and Run action
        val actionGroup = DefaultActionGroup()
        val toolbar: ActionToolbar = ActionManager.getInstance()
            .createActionToolbar("LLMJudgeToolbar", actionGroup, true)
            .apply { targetComponent = panel }
        panel.add(toolbar.component, BorderLayout.NORTH)

        val runAction = object : AnAction("Run", "Start evaluation", AllIcons.Actions.Execute) {
            override fun actionPerformed(e: AnActionEvent) {
                // Check API key
                val apiKey = System.getenv("OPENAI_API_KEY")

                if (apiKey.isNullOrBlank()) {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("LLM Judge")
                        .createNotification(
                            "Missing API Key",
                            "Please set the OPENAI_API_KEY environment variable to use LLM scoring.",
                            NotificationType.ERROR
                        )
                        .notify(project)
                    return
                }
                val openai = OpenAIJavaService.create(apiKey)

                // Reset table
                tableModel.rowCount = 0

                // Load JSON config
                val base = project.basePath ?: return
                val configFile = Paths.get(base, "judge.json").toFile()
                val mapper = jacksonObjectMapper()
                val config: Config = mapper.readValue(configFile)

                // Launch evaluation coroutines per entry
                config.data.forEachIndexed { rowIndex, entry ->
                    // Initial row: empty model output & score placeholder
                    tableModel.addRow(arrayOf(entry.input, entry.referenceOutput, "", ""))
                    // 1) Invoke model script
                    val scriptFile = Paths.get(base, config.modelPath).toFile()
                    val process = ProcessBuilder(
                        "python3",
                        scriptFile.absolutePath,
                        "--input", entry.input
                    )
                        .directory(File(base))
                        .redirectErrorStream(true)
                        .start()
                    val output = process.inputStream.bufferedReader().readText().trim()
                    process.waitFor()
                    tableModel.setValueAt(output, rowIndex, 2)

                    scope.launch {
                        // Update model output immediately
                        SwingUtilities.invokeLater {
                            tableModel.setValueAt("evaluating...", rowIndex, 3)
                        }

                        // 2) Simulate scoring delay
                        delay((500..2000).random().toLong())
                        val score = (1..10).random()

                        // Update score when ready
                        SwingUtilities.invokeLater {
                            tableModel.setValueAt(score, rowIndex, 3)
                        }
                    }
                }
            }
        }
        actionGroup.add(runAction)

        // Add content to the tool window
        val content = ContentFactory.getInstance()
            .createContent(panel as JPanel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
