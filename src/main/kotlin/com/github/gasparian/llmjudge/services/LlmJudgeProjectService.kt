package com.github.gasparian.llmjudge.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.gasparian.llmjudge.model.Config
import com.github.gasparian.llmjudge.model.Entry
import com.github.gasparian.llmjudge.model.Feedback
import com.github.gasparian.llmjudge.openai.OpenAIJavaService
import com.github.gasparian.llmjudge.prompt.PromptProvider
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.openai.client.OpenAIClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.nio.file.Paths
import java.time.Duration
import kotlin.collections.maxByOrNull

@Service(Service.Level.PROJECT)
class LlmJudgeProjectService(
    private val project: Project,
) {
    private val LOG = Logger.getInstance(LlmJudgeProjectService::class.java)

    companion object {
        private const val DEFAULT_MAX_API_CALLS = 5
        private val MAX_API_CALLS: Int = System.getenv("LLM_JUDGE_MAX_API_CALLS")
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: DEFAULT_MAX_API_CALLS
        private val apiSemaphore = Semaphore(MAX_API_CALLS)
    }

    private val mapper = jacksonObjectMapper()
    private val openAI: OpenAIClient by lazy {
        val apiKey = System.getenv("OPENAI_API_KEY")
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("OPENAI_API_KEY environment variable is not set")
        // you can override the timeout if needed:
        OpenAIJavaService.create(apiKey, timeout = Duration.ofSeconds(30))
    }

    fun loadConfig(): Config {
        LOG.debug("Loading judge.json from ${project.basePath}")
        try {
            val base = project.basePath
                ?: throw IllegalStateException("Project basePath is null")
            val file = Paths.get(base, "judge.json").toFile()
            return mapper.readValue(file)
        } catch (e: Exception) {
            LOG.error("Failed to load judge.json", e)
            throw e
        }
    }

    fun runModelScript(entry: Entry): String {
        LOG.debug("Running model script for input=\"${entry.input.take(30)}…\"")
        try {
            val base = project.basePath!!
            val script = Paths.get(base, loadConfig().modelPath).toFile()
            val proc = ProcessBuilder("python3", script.absolutePath, "--input", entry.input)
                .directory(File(base))
                .redirectErrorStream(true)
                .start()
            return proc.inputStream.bufferedReader().readText().trim().also { proc.waitFor() }
        } catch (e: Exception) {
            LOG.warn("Python script failed for input=\"${entry.input.take(30)}…\"", e)
            throw e
        }
    }

    suspend fun evaluateWithLLM(entry: Entry, calls: Int = 3): Feedback = coroutineScope {
        LOG.debug("Calling OpenAI $calls time(s) for input=\"${entry.input.take(30)}…\"")
        val jobs = (1..calls).map {
            async(Dispatchers.IO) {
                apiSemaphore.withPermit {
                    val params = ChatCompletionCreateParams.builder()
                        .model(ChatModel.GPT_4O)
                        .addUserMessage(PromptProvider.forEntry(entry))
                        .responseFormat(Feedback::class.java)
                        .build()
                    openAI.chat().completions().create(params)
                        .choices().first().message().content().orElseThrow()
                }
            }
        }
        val results: List<Feedback> = jobs.awaitAll()
        val majority = results.groupingBy { it.score }
            .eachCount().maxByOrNull { it.value }!!.key
        results.first { it.score == majority }
    }
}
