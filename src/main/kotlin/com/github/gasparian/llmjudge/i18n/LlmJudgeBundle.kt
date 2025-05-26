// File: src/main/kotlin/com/github/gasparian/llmjudge/i18n/LlmJudgeBundle.kt
package com.github.gasparian.llmjudge.i18n

import com.intellij.AbstractBundle
import org.jetbrains.annotations.PropertyKey

/**
 * Centralizes access to messages/LlmJudgeBundle.properties.
 */
object LlmJudgeBundle : AbstractBundle("messages.LlmJudgeBundle") {
    /**
     * @param key    key in messages/LlmJudgeBundle.properties
     * @param params optional MessageFormat parameters like {0}, {1}, …
     */
    @JvmStatic
    fun message(
        @PropertyKey(resourceBundle = "messages.LlmJudgeBundle") key: String,
        vararg params: Any,
    ): String = getMessage(key, *params)
}
