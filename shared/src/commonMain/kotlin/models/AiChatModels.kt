package models

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.time.Clock

@Serializable
enum class AiRole {
    System,
    User,
    Assistant,
}

@Serializable
data class AiChatMessageDto(
    var role: AiRole = AiRole.User,
    var content: String = "",
    var createdAt: Instant = Clock.System.now(),
)

@Serializable
sealed class AiChatClientEvent {
    @Serializable
    data class Start(
        val system: String? = null,
        val initialMessages: List<AiChatMessageDto> = emptyList(),
    ) : AiChatClientEvent()

    @Serializable
    data class UserMessage(
        val text: String = "",
    ) : AiChatClientEvent()

    @Serializable
    data class End(
        val reason: String = "",
    ) : AiChatClientEvent()
}

@Serializable
sealed class AiChatServerEvent {
    @Serializable
    data class SessionOpened(
        val sessionId: String = "",
    ) : AiChatServerEvent()

    @Serializable
    data class AssistantMessage(
        val text: String = "",
    ) : AiChatServerEvent()

    @Serializable
    data class Error(
        val message: String = "",
    ) : AiChatServerEvent()
}
