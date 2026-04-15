package models

import kotlinx.serialization.Serializable
import models.annotations.Indexed

@Serializable
data class QuestStep(
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var worldId: String? = null,
    var objectId: String? = null,
    var indicatorEmoji: String? = null,
    var statusOverride: String? = null,
)

@Serializable
data class Reward(
    var itemId: String = "",
    var quantity: Int = 1,
)

@Serializable
data class Quest(
    var name: String = "",
    var description: String = "",
    var steps: List<QuestStep> = emptyList(),
    var rewards: List<Reward> = emptyList(),
) : BaseModel()

@Serializable
data class QuestState(
    @Indexed(group = "quest_state_name", order = 0)
    var questId: String = "",
    @Indexed(group = "quest_state_name", order = 1)
    var name: String = "",
    var value: String = "",
) : BaseModel()

@Serializable
data class PlayerQuestState(
    @Indexed(group = "player_quest_state_name", order = 0)
    var questId: String = "",
    @Indexed(group = "player_quest_state_name", order = 1)
    var userId: String = "",
    @Indexed(group = "player_quest_state_name", order = 2)
    var name: String = "",
    var value: String = "",
) : BaseModel()

@Serializable
data class PlayerQuestProgress(
    @Indexed(group = "player_quest_progress", order = 0)
    var questId: String = "",
    @Indexed(group = "player_quest_progress", order = 1)
    var userId: String = "",
    var currentStepIndex: Int = 0,
    var completed: Boolean = false,
) : BaseModel()
