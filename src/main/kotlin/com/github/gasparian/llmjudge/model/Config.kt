package com.github.gasparian.llmjudge.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class Config @JsonCreator constructor(
    @JsonProperty("model_path") val modelPath: String,
    @JsonProperty("data") val data: List<Entry>,
)
