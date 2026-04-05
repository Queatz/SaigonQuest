package models

import kotlinx.serialization.Serializable
import models.annotations.Indexed

@Serializable
data class Quest(
    var name: String = "",
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
