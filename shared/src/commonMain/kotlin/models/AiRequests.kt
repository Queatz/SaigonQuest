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

@Serializable
data class AiImageRequest(
    val prompt: String,
    val size: String = "1024*1024",
)

@Serializable
data class AiImageResponse(
    val imageUrl: String,
)

@Serializable
data class AiBackgroundRemovalRequest(
    val imageUrl: String,
)
