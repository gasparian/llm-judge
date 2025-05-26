package com.github.gasparian.llmjudge.prompt

import com.github.gasparian.llmjudge.model.Entry

object PromptProvider {
    private val template by lazy {
        javaClass.getResource("/judge_prompt.txt")!!.readText()
    }

    fun forEntry(entry: Entry): String {
        val out = entry.modelOutput ?: ""
        return template
            .replace("{input}", entry.input)
            .replace("{reference}", entry.referenceOutput)
            .replace("{output}", out)
    }
}
