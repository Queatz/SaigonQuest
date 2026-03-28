package models

import kotlinx.serialization.Serializable

@Serializable
data class AiPromptRequest(
    val prompt: String,
    val docContents: String,
    val caretStart: Int,
    val caretEnd: Int,
    val docId: String? = null,
    val docName: String? = null,
)

@Serializable
data class AiTtsRequest(
    val text: String,
)
