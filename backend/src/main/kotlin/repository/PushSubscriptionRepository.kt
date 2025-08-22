

package repository

import com.arangodb.ArangoCollection
import com.arangodb.model.DocumentCreateOptions
import com.arangodb.model.DocumentReadOptions
import com.arangodb.model.DocumentReplaceOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.PushSubscription
import kotlin.time.Clock


class PushSubscriptionRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.PUSH_SUBSCRIPTIONS_COLLECTION)

    fun upsert(subscription: PushSubscription): PushSubscription {
        // Try to find by endpoint
        val existing = getByEndpoint(subscription.endpoint)
        return if (existing != null) {
            val updated = existing.copy(
                userId = subscription.userId,
                p256dh = subscription.p256dh,
                auth = subscription.auth,
                userAgent = subscription.userAgent.ifBlank { existing.userAgent },
                lastSeenAt = Clock.System.now()
            ).also { it.key = existing.key }

            collection.replaceDocument(updated.key, updated, DocumentReplaceOptions().returnNew(true)).new
        } else {
            // Insert new
            collection.insertDocument(subscription, DocumentCreateOptions().returnNew(true)).new
        }
    }

    fun deleteByEndpoint(userId: String, endpoint: String): Boolean {
        val sub = getByEndpoint(endpoint) ?: return false
        return if (sub.userId == userId) {
            collection.deleteDocument(sub.key)
            true
        } else false
    }

    fun getByEndpoint(endpoint: String): PushSubscription? {
        val query = """
            FOR s IN @@collection
            FILTER s.endpoint == @endpoint
            LIMIT 1
            RETURN s
        """.trimIndent()
        val bindVars = mapOf("@collection" to CollectionsConfig.PUSH_SUBSCRIPTIONS_COLLECTION, "endpoint" to endpoint)
        return ArangoConfig.db.query(query, PushSubscription::class.java, bindVars).firstOrNull()
    }

    fun getByUserId(userId: String): List<PushSubscription> {
        val query = """
            FOR s IN @@collection
            FILTER s.userId == @userId
            RETURN s
        """.trimIndent()
        val bindVars = mapOf("@collection" to CollectionsConfig.PUSH_SUBSCRIPTIONS_COLLECTION, "userId" to userId)
        return ArangoConfig.db.query(query, PushSubscription::class.java, bindVars).toList()
    }
}
