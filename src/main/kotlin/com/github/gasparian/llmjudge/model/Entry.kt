package com.github.gasparian.llmjudge.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class Entry @JsonCreator constructor(
    @JsonProperty("input") val input: String,
    @JsonProperty("reference_output") val referenceOutput: String,
    @JsonProperty("model_output") var modelOutput: String? = null,
)
