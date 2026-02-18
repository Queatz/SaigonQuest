package models

import kotlinx.serialization.Serializable
import models.annotations.CompositeIndex
import models.annotations.CompositeIndexes
import models.annotations.Indexed
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
data class AiChatMessage(
    var role: AiRole = AiRole.User,
    var content: String = "",
    var createdAt: Instant = Clock.System.now(),
)

@CompositeIndexes(
    value = [
        CompositeIndex(fields = ["userId", "createdAt"]),
    ]
)
@Serializable
data class AiChatSession(
    @field:Indexed
    var userId: String = "",
    var messages: List<AiChatMessage> = emptyList(),
    var isClosed: Boolean = false,
) : BaseModel()
