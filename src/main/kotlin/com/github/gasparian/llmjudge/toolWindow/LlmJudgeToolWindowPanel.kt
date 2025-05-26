package com.github.gasparian.llmjudge.toolWindow

import com.github.gasparian.llmjudge.services.LlmJudgeProjectService
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel

class LlmJudgeToolWindowPanel(
    private val service: LlmJudgeProjectService,
) : JBPanel<LlmJudgeToolWindowPanel>(BorderLayout()) {
    private val LOG = Logger.getInstance(LlmJudgeToolWindowPanel::class.java)

    companion object {
        private const val DEFAULT_MAX_PARALLELISM = 5
        private val MAX_PARALLELISM: Int = System.getenv("LLM_JUDGE_MAX_PARALLEL")
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: DEFAULT_MAX_PARALLELISM

        @OptIn(ExperimentalCoroutinesApi::class)
        private val limitedIo = Dispatchers.IO.limitedParallelism(MAX_PARALLELISM)
    }

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + limitedIo)
    private val tableModel = object : DefaultTableModel(
        arrayOf("Input", "Reference", "Model Output", "Score"),
        0,
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }

    init {
        LOG.info("Initializing panel with maxParallelism=$MAX_PARALLELISM")

        val table = JBTable(tableModel).apply { fillsViewportHeight = true }
        add(JBScrollPane(table), BorderLayout.CENTER)

        val group = DefaultActionGroup()
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("LLMJudgeToolbar", group, true)
            .apply { targetComponent = this@LlmJudgeToolWindowPanel }
        add(toolbar.component, BorderLayout.NORTH)

        group.add(object : AnAction("Run", "Start evaluation", AllIcons.Actions.Execute) {
            private val jobs = mutableListOf<Job>()

            override fun actionPerformed(e: AnActionEvent) {
                val projectName = e.project?.name ?: "<unknown>"
                LOG.info("Run action triggered in project: $projectName")

                val p = e.presentation
                if (p.icon === AllIcons.Actions.Suspend) {
                    LOG.debug("Canceling in-flight evaluation jobs")
                    scope.coroutineContext.cancelChildren()
                    p.icon = AllIcons.Actions.Execute
                    return
                }
                p.icon = AllIcons.Actions.Suspend

                val config = try {
                    service.loadConfig()
                } catch (ex: Exception) {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("LLM Judge")
                        .createNotification("Error loading judge.json", ex.message ?: "", NotificationType.ERROR)
                        .notify(e.project)
                    p.icon = AllIcons.Actions.Execute
                    return
                }

                LOG.debug("Resetting result table")
                tableModel.rowCount = 0

                jobs.clear()
                config.data.forEachIndexed { row, entry ->
                    LOG.debug("Preparing row #$row for input='${entry.input.take(30)}…'")
                    tableModel.addRow(arrayOf(entry.input, entry.referenceOutput, "", ""))

                    val job = scope.launch {
                        try {
                            LOG.debug("Running Python script for row #$row")
                            entry.modelOutput = service.runModelScript(entry)
                            LOG.debug("Script output for row #$row: '${entry.modelOutput}'")
                        } catch (ex: Exception) {
                            LOG.error("Error running script for row #$row", ex)
                            entry.modelOutput = "<error>"
                        }

                        SwingUtilities.invokeLater {
                            tableModel.setValueAt(entry.modelOutput, row, 2)
                            tableModel.setValueAt("evaluating…", row, 3)
                        }

                        try {
                            LOG.debug("Starting LLM evaluation for row #$row")
                            val fb = service.evaluateWithLLM(entry)
                            LOG.debug("LLM evaluation complete for row #$row: score=${fb.score}")
                            SwingUtilities.invokeLater {
                                tableModel.setValueAt(fb.score, row, 3)
                            }
                        } catch (ex: CancellationException) {
                            LOG.debug("LLM evaluation canceled for row #$row")
                            SwingUtilities.invokeLater {
                                tableModel.setValueAt("Error", row, 3)
                            }
                            throw ex
                        } catch (ex: Exception) {
                            LOG.error("LLM evaluation failed for row #$row", ex)
                            SwingUtilities.invokeLater {
                                tableModel.setValueAt("Error", row, 3)
                            }
                        }
                    }
                    jobs += job
                }
                LOG.info("Launched ${jobs.size} evaluation job(s)")

                scope.launch {
                    jobs.joinAll()
                    SwingUtilities.invokeLater {
                        p.icon = AllIcons.Actions.Execute
                    }
                }
            }
        })
    }
}
