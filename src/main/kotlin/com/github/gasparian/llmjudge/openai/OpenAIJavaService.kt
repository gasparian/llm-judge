package com.github.gasparian.llmjudge.openai

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import java.time.Duration

/**
 * Factory for OpenAIClient using the OkHttp transport.
 */
object OpenAIJavaService {
    /**
     * @param apiKey Your OpenAI API key.
     * @param timeout How long to wait for network calls (default: 30 seconds).
     * @return Configured OpenAIClient instance.
     */
    fun create(
        apiKey: String,
        timeout: Duration = Duration.ofSeconds(30),
    ): OpenAIClient = OpenAIOkHttpClient.builder()
        .apiKey(apiKey)
        .timeout(timeout)
        .build()
}
