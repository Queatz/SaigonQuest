package repository

import com.arangodb.ArangoCollection
import com.arangodb.model.DocumentCreateOptions
import com.arangodb.model.DocumentReadOptions
import com.arangodb.model.DocumentUpdateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.Quest
import models.QuestState
import models.PlayerQuestState
import models.PlayerQuestProgress

class QuestRepository {
    private val questColl: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.QUESTS_COLLECTION)
    private val stateColl: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.QUEST_STATES_COLLECTION)
    private val playerStateColl: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.PLAYER_QUEST_STATES_COLLECTION)
    private val progressColl: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.PLAYER_QUEST_PROGRESS_COLLECTION)

    // Quest CRUD
    fun createQuest(quest: Quest): Quest {
        return questColl.insertDocument(quest, DocumentCreateOptions().returnNew(true)).new
    }

    fun updateQuest(quest: Quest): Quest {
        return questColl.replaceDocument(quest.key, quest, com.arangodb.model.DocumentReplaceOptions().returnNew(true)).new
    }

    fun getQuestById(id: String): Quest? {
        return try {
            questColl.getDocument(id, Quest::class.java, DocumentReadOptions())
        } catch (e: Throwable) {
            null
        }
    }

    fun getAllQuests(): List<Quest> {
        val query = "FOR q IN @@collection RETURN q"
        return ArangoConfig.db.query(query, Quest::class.java, mapOf("@collection" to CollectionsConfig.QUESTS_COLLECTION)).toList()
    }

    fun deleteQuest(id: String) {
        try {
            questColl.deleteDocument(id)
            // Also delete associated states
            val query = "FOR s IN @@collection FILTER s.questId == @questId REMOVE s IN @@collection"
            ArangoConfig.db.query(query, Void::class.java, mapOf("@collection" to CollectionsConfig.QUEST_STATES_COLLECTION, "questId" to id))
            val playerQuery = "FOR ps IN @@collection FILTER ps.questId == @questId REMOVE ps IN @@collection"
            ArangoConfig.db.query(playerQuery, Void::class.java, mapOf("@collection" to CollectionsConfig.PLAYER_QUEST_STATES_COLLECTION, "questId" to id))
        } catch (e: Throwable) {
            // Ignore if already deleted
        }
    }

    // QuestState CRUD (Global)
    fun createQuestState(state: QuestState): QuestState {
        return stateColl.insertDocument(state, DocumentCreateOptions().returnNew(true)).new
    }

    fun getQuestState(questId: String, name: String): QuestState? {
        val query = "FOR s IN @@collection FILTER s.questId == @questId AND s.name == @name RETURN s"
        val bindVars = mapOf("@collection" to CollectionsConfig.QUEST_STATES_COLLECTION, "questId" to questId, "name" to name)
        return ArangoConfig.db.query(query, QuestState::class.java, bindVars).toList().firstOrNull()
    }

    fun getQuestStatesByQuestId(questId: String): List<QuestState> {
        val query = "FOR s IN @@collection FILTER s.questId == @questId RETURN s"
        val bindVars = mapOf("@collection" to CollectionsConfig.QUEST_STATES_COLLECTION, "questId" to questId)
        return ArangoConfig.db.query(query, QuestState::class.java, bindVars).toList()
    }

    fun updateQuestState(questId: String, name: String, value: String): QuestState {
        val existing = getQuestState(questId, name)
        return if (existing != null) {
            existing.value = value
            stateColl.updateDocument(existing.key, existing, DocumentUpdateOptions().returnNew(true)).new
        } else {
            createQuestState(QuestState(questId = questId, name = name, value = value))
        }
    }

    fun deleteQuestState(questId: String, name: String) {
        val query = "FOR s IN @@collection FILTER s.questId == @questId AND s.name == @name REMOVE s IN @@collection"
        ArangoConfig.db.query(query, Void::class.java, mapOf("@collection" to CollectionsConfig.QUEST_STATES_COLLECTION, "questId" to questId, "name" to name))
    }

    // PlayerQuestState CRUD
    fun getPlayerQuestState(questId: String, userId: String, name: String): PlayerQuestState? {
        val query = "FOR ps IN @@collection FILTER ps.questId == @questId AND ps.userId == @userId AND ps.name == @name RETURN ps"
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.PLAYER_QUEST_STATES_COLLECTION,
            "questId" to questId,
            "userId" to userId,
            "name" to name
        )
        return ArangoConfig.db.query(query, PlayerQuestState::class.java, bindVars).toList().firstOrNull()
    }

    fun updatePlayerQuestState(questId: String, userId: String, name: String, value: String): PlayerQuestState {
        val existing = getPlayerQuestState(questId, userId, name)
        return if (existing != null) {
            existing.value = value
            playerStateColl.updateDocument(existing.key, existing, DocumentUpdateOptions().returnNew(true)).new
        } else {
            val newState = PlayerQuestState(questId = questId, userId = userId, name = name, value = value)
            playerStateColl.insertDocument(newState, DocumentCreateOptions().returnNew(true)).new
        }
    }

    fun getAllPlayerQuestStates(questId: String): List<PlayerQuestState> {
        val query = "FOR ps IN @@collection FILTER ps.questId == @questId RETURN ps"
        val bindVars = mapOf("@collection" to CollectionsConfig.PLAYER_QUEST_STATES_COLLECTION, "questId" to questId)
        return ArangoConfig.db.query(query, PlayerQuestState::class.java, bindVars).toList()
    }

    fun deletePlayerQuestStatesForUser(userId: String) {
        val query = "FOR ps IN @@collection FILTER ps.userId == @userId REMOVE ps IN @@collection"
        ArangoConfig.db.query(query, Void::class.java, mapOf("@collection" to CollectionsConfig.PLAYER_QUEST_STATES_COLLECTION, "userId" to userId))
    }

    fun deletePlayerQuestState(playerStateId: String) {
        playerStateColl.deleteDocument(playerStateId)
    }

    // PlayerQuestProgress CRUD
    fun getPlayerQuestProgress(questId: String, userId: String): PlayerQuestProgress? {
        val query = "FOR p IN @@collection FILTER p.questId == @questId AND p.userId == @userId RETURN p"
        val bindVars = mapOf(
            "@collection" to CollectionsConfig.PLAYER_QUEST_PROGRESS_COLLECTION,
            "questId" to questId,
            "userId" to userId
        )
        return ArangoConfig.db.query(query, PlayerQuestProgress::class.java, bindVars).toList().firstOrNull()
    }

    fun updatePlayerQuestProgress(questId: String, userId: String, stepIndex: Int, completed: Boolean = false): PlayerQuestProgress {
        val existing = getPlayerQuestProgress(questId, userId)
        return if (existing != null) {
            existing.currentStepIndex = stepIndex
            existing.completed = completed
            progressColl.updateDocument(existing.key, existing, DocumentUpdateOptions().returnNew(true)).new
        } else {
            val newProgress = PlayerQuestProgress(questId = questId, userId = userId, currentStepIndex = stepIndex, completed = completed)
            progressColl.insertDocument(newProgress, DocumentCreateOptions().returnNew(true)).new
        }
    }

    fun getAllPlayerQuestProgress(userId: String): List<PlayerQuestProgress> {
        val query = "FOR p IN @@collection FILTER p.userId == @userId RETURN p"
        val bindVars = mapOf("@collection" to CollectionsConfig.PLAYER_QUEST_PROGRESS_COLLECTION, "userId" to userId)
        return ArangoConfig.db.query(query, PlayerQuestProgress::class.java, bindVars).toList()
    }

    fun getAllPlayerQuestProgressForQuest(questId: String): List<PlayerQuestProgress> {
        val query = "FOR p IN @@collection FILTER p.questId == @questId RETURN p"
        val bindVars = mapOf("@collection" to CollectionsConfig.PLAYER_QUEST_PROGRESS_COLLECTION, "questId" to questId)
        return ArangoConfig.db.query(query, PlayerQuestProgress::class.java, bindVars).toList()
    }

    fun deletePlayerQuestProgress(id: String) {
        progressColl.deleteDocument(id)
    }
}
