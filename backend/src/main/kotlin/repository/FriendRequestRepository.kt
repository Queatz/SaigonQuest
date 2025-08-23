package repository

import com.arangodb.ArangoCollection
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.model.DocumentCreateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.Friend
import models.FriendRequest
import models.FriendRequestStatus
import kotlin.time.Clock

class FriendRequestRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.FRIEND_REQUESTS_COLLECTION)

    fun create(request: FriendRequest): FriendRequest {
        val result: DocumentCreateEntity<FriendRequest> = collection.insertDocument(
            request,
            DocumentCreateOptions().returnNew(true)
        )
        return result.new
    }

    // Atomically create or return the existing Pending request for the same direction
    fun upsertPending(
        requesterId: String,
        requesterUsername: String,
        addresseeId: String,
    ): FriendRequest {
        val query = """
            UPSERT { requesterId: @requesterId, addresseeId: @addresseeId, status: @pending }
            INSERT {
              requesterId: @requesterId,
              requesterUsername: @requesterUsername,
              addresseeId: @addresseeId,
              status: @pending,
              decidedAt: null,
              createdAt: @now
            }
            UPDATE { requesterUsername: @requesterUsername } IN @@collection
            RETURN NEW
        """.trimIndent()
        val bind = mapOf(
            "@collection" to CollectionsConfig.FRIEND_REQUESTS_COLLECTION,
            "requesterId" to requesterId,
            "requesterUsername" to requesterUsername,
            "addresseeId" to addresseeId,
            "pending" to FriendRequestStatus.Pending,
            "now" to Clock.System.now(),
        )
        val list = ArangoConfig.db.query(query, FriendRequest::class.java, bind).toList()
        return list.first()
    }

    fun getById(id: String): FriendRequest? = try {
        collection.getDocument(id, FriendRequest::class.java)
    } catch (_: Throwable) { null }

    fun updateStatus(id: String, status: FriendRequestStatus): FriendRequest? {
        val existing = getById(id) ?: return null
        existing.status = status
        existing.decidedAt = Clock.System.now()
        return try {
            collection.updateDocument(id, existing, com.arangodb.model.DocumentUpdateOptions().returnNew(true)).new
        } catch (_: Throwable) { null }
    }

    fun getPendingFromTo(requesterId: String, addresseeId: String): FriendRequest? {
        val query = """
            FOR r IN @@collection
            FILTER r.requesterId == @requesterId
              AND r.addresseeId == @addresseeId
              AND r.status == @status
            LIMIT 1
            RETURN r
        """.trimIndent()
        val bind = mapOf(
            "@collection" to CollectionsConfig.FRIEND_REQUESTS_COLLECTION,
            "requesterId" to requesterId,
            "addresseeId" to addresseeId,
            "status" to FriendRequestStatus.Pending,
        )
        return ArangoConfig.db.query(query, FriendRequest::class.java, bind).toList().firstOrNull()
    }

    fun getPendingBetween(a: String, b: String): FriendRequest? {
        val query = """
            FOR r IN @@collection
            FILTER r.status == @pending AND ((r.requesterId == @a AND r.addresseeId == @b) OR (r.requesterId == @b AND r.addresseeId == @a))
            LIMIT 1
            RETURN r
        """.trimIndent()
        val bind = mapOf(
            "@collection" to CollectionsConfig.FRIEND_REQUESTS_COLLECTION,
            "pending" to FriendRequestStatus.Pending,
            "a" to a,
            "b" to b,
        )
        return ArangoConfig.db.query(query, FriendRequest::class.java, bind).toList().firstOrNull()
    }

    fun acceptByParticipants(requesterId: String, addresseeId: String): FriendRequest? {
        val query = """
            FOR r IN @@collection
            FILTER r.requesterId == @requesterId
              AND r.addresseeId == @addresseeId
              AND r.status == @pending
            UPDATE r WITH { status: @accepted, decidedAt: @now } IN @@collection
            RETURN NEW
        """.trimIndent()
        val bind = mapOf(
            "@collection" to CollectionsConfig.FRIEND_REQUESTS_COLLECTION,
            "requesterId" to requesterId,
            "addresseeId" to addresseeId,
            "pending" to FriendRequestStatus.Pending,
            "accepted" to FriendRequestStatus.Accepted,
            "now" to Clock.System.now(),
        )
        return ArangoConfig.db.query(query, FriendRequest::class.java, bind).toList().firstOrNull()
    }

    fun getIncoming(userId: String): List<FriendRequest> {
        val query = """
            FOR r IN @@collection
            FILTER r.addresseeId == @userId AND r.status == @status
            SORT r.createdAt DESC
            RETURN r
        """.trimIndent()
        val bind = mapOf(
            "@collection" to CollectionsConfig.FRIEND_REQUESTS_COLLECTION,
            "userId" to userId,
            "status" to FriendRequestStatus.Pending,
        )
        return ArangoConfig.db.query(query, FriendRequest::class.java, bind).toList()
    }

    fun getOutgoing(userId: String): List<FriendRequest> {
        val query = """
            FOR r IN @@collection
            FILTER r.requesterId == @userId AND r.status == @status
            SORT r.createdAt DESC
            RETURN r
        """.trimIndent()
        val bind = mapOf(
            "@collection" to CollectionsConfig.FRIEND_REQUESTS_COLLECTION,
            "userId" to userId,
            "status" to FriendRequestStatus.Pending,
        )
        return ArangoConfig.db.query(query, FriendRequest::class.java, bind).toList()
    }

    // Atomic accept that also upserts friendships to avoid duplicates
    fun acceptAndUpsertFriendships(
        requesterId: String,
        addresseeId: String,
        requesterUsername: String,
        addresseeUsername: String,
    ): Friend? {
        val query = """
            WITH ${CollectionsConfig.FRIEND_REQUESTS_COLLECTION}, ${CollectionsConfig.FRIENDS_COLLECTION}
            LET req = FIRST(
              FOR r IN @@requests
                FILTER r.requesterId == @requesterId AND r.addresseeId == @addresseeId AND r.status == @pending
                LIMIT 1
                RETURN r
            )
            FILTER req != null
            UPDATE req WITH { status: @accepted, decidedAt: @now } IN @@requests
            LET f1 = FIRST(
              UPSERT { userId: @addresseeId, friendId: @requesterId }
              INSERT { userId: @addresseeId, friendId: @requesterId, friendUsername: @requesterUsername, lastActive: @now, lastWorldId: "", lastWorldName: "", createdAt: @now }
              UPDATE { friendUsername: @requesterUsername, lastActive: @now }
              IN @@friends
              RETURN NEW
            )
            LET f2 = FIRST(
              UPSERT { userId: @requesterId, friendId: @addresseeId }
              INSERT { userId: @requesterId, friendId: @addresseeId, friendUsername: @addresseeUsername, lastActive: @now, lastWorldId: "", lastWorldName: "", createdAt: @now }
              UPDATE { friendUsername: @addresseeUsername, lastActive: @now }
              IN @@friends
              RETURN NEW
            )
            RETURN f1
        """.trimIndent()
        val bind = mapOf(
            "@requests" to CollectionsConfig.FRIEND_REQUESTS_COLLECTION,
            "@friends" to CollectionsConfig.FRIENDS_COLLECTION,
            "requesterId" to requesterId,
            "addresseeId" to addresseeId,
            "requesterUsername" to requesterUsername,
            "addresseeUsername" to addresseeUsername,
            "pending" to FriendRequestStatus.Pending,
            "accepted" to FriendRequestStatus.Accepted,
            "now" to Clock.System.now(),
        )
        val list = ArangoConfig.db.query(query, Friend::class.java, bind).toList()
        return list.firstOrNull()
    }
}
