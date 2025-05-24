package com.github.gasparian.llmjudge.toolWindow

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonAlias
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
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
//import kotlinx.coroutines.*

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
    @JsonProperty("input")            val input: String,
    @JsonProperty("reference_output") val referenceOutput: String,
    @JsonProperty("model_output")     val modelOutput: String? = null
)


data class Config @JsonCreator constructor(
    @JsonProperty("model_path") val modelPath: String,
    @JsonProperty("data")       val data: List<Entry>
)

object PromptProvider {
    private val promptTemplate: String by lazy {
        javaClass.getResource("/judge_prompt.txt")!!.readText()
    }

    /**
     * Renders the judge prompt for a given entry, ensuring that a null
     * model output is treated as an empty string.
     */
    fun forEntry(input: String, reference: String, output: String?): String {
        val safeOutput = output ?: ""
        return promptTemplate
            .replace("{input}", input)
            .replace("{reference}", reference)
            .replace("{output}", safeOutput)
    }
}


data class Feedback(
    val correctness: Int,
    val relevance: Int,
    val fluency: Int,
    val completeness: Int,
    val clarity: Int,
    @JsonProperty("total_score")
    @JsonAlias("totalScore")
    val totalScore: Int
)

// Suspended, so you can call it from your existing scope.launch { … }
suspend fun evaluateWithLLM(
    openai: OpenAIClient,
    entry: Entry,
    calls: Int = 3
): Feedback = coroutineScope {
    // Launch N independent judge calls
    val deferreds = (1..calls).map {
        async(Dispatchers.IO) {
            // 1) Build a structured-output chat completion request
            val params = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4O)
                .addUserMessage(PromptProvider.forEntry(
                    entry.input, entry.referenceOutput, entry.modelOutput
                ))
                .responseFormat(Feedback::class.java)
                .build()

            // 2) Send the request synchronously (we’re inside IO dispatcher)
            val chatCompletion = openai.chat().completions().create(params)

            // 3) Extract the single Feedback instance from the choice
            chatCompletion.choices().first().message().content().orElseThrow()
        }
    }

    // Await all responses
    val results: List<Feedback> = deferreds.awaitAll()

    // Majority vote on totalScore
    val majorityScore = results
        .groupingBy { it.totalScore }
        .eachCount()
        .maxByOrNull { it.value }!!
        .key

    // Return the first Feedback matching the majority totalScore
    results.first { it.totalScore == majorityScore }
}

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
            private val jobs = mutableListOf<Job>()

            override fun actionPerformed(e: AnActionEvent) {
                val presentation = e.presentation
                // If we're already running, cancel everything
                if (presentation.icon === AllIcons.Actions.Suspend) {
                    scope.coroutineContext.cancelChildren()                 // stop all in‐flight jobs
                    presentation.icon = AllIcons.Actions.Execute           // swap back to ▶️
                    return
                }

                presentation.icon = AllIcons.Actions.Suspend

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
                jobs.clear()
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

                    val job = scope.launch {
                        // Update model output immediately
                        SwingUtilities.invokeLater {
                            tableModel.setValueAt("evaluating...", rowIndex, 3)
                        }

                        try {
                            val feedback = evaluateWithLLM(openai, entry, calls = 5)
                            SwingUtilities.invokeLater {
                                tableModel.setValueAt(feedback.totalScore, rowIndex, 3)
                            }
                        } catch (e: Exception) {
                            // Log error message and full stacktrace
                            System.err.println("LLM Judge: Error evaluating row $rowIndex: ${e.message}")
//                            e.printStackTrace()

                            SwingUtilities.invokeLater {
                                tableModel.setValueAt("Error", rowIndex, 3)
                            }
                        }
                    }
                    jobs += job
                }
                // 5) Once _all_ row-jobs complete (or if they were cancelled), swap icon back
                scope.launch {
                    // wait for every per-row job
                    jobs.joinAll()
                    SwingUtilities.invokeLater {
                        presentation.icon = AllIcons.Actions.Execute    // green ▶️
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
