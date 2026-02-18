package repository

import com.arangodb.entity.DocumentCreateEntity
import database.CollectionsConfig
import database.ArangoConfig
import models.AiChatMessage
import models.AiChatSession

class AiChatRepository {
    private val coll = ArangoConfig.db.collection(CollectionsConfig.AI_CHAT_SESSIONS_COLLECTION)

    fun insert(session: AiChatSession): String {
        val res = coll.insertDocument(session)
        return res.key
    }

    fun getById(id: String): AiChatSession? =
        coll.getDocument(id, AiChatSession::class.java)

    fun appendMessage(id: String, msg: AiChatMessage) {
        val session = getById(id) ?: return
        val updated = session.copy(messages = session.messages + msg)
        coll.replaceDocument(id, updated)
    }
}
