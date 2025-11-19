package repository

import com.arangodb.ArangoCollection
import com.arangodb.entity.DocumentCreateEntity
import com.arangodb.model.DocumentCreateOptions
import com.arangodb.model.DocumentUpdateOptions
import database.ArangoConfig
import database.CollectionsConfig
import models.CreateUserRequestDto
import models.UserRequest
import models.UserRequestNote
import models.UserRequestStatus
import models.UserRequestType
import models.UserBalanceHistory
import models.UserBalanceHistoryEventType
import models.Notification
import models.NotificationType
import repository.UserRepository
import utils.CostsProvider
import kotlin.time.Clock

class UserRequestRepository {
    private val collection: ArangoCollection = ArangoConfig.db.collection(CollectionsConfig.USER_REQUESTS_COLLECTION)

    private val balances = UserBalanceRepository()
    private val balanceHistory = UserBalanceHistoryRepository()
    private val users = UserRepository()
    private val notifications = NotificationRepository()

    fun create(userId: String, dto: CreateUserRequestDto): UserRequest {
        val now = Clock.System.now()
        val type: UserRequestType = dto.type
        val fee = CostsProvider.userRequestFee(type)

        // Debit user balance (best effort transactional style)
        var debitedFrom: Long = 0
        var debitedTo: Long = 0
        balances.upsert(userId) { bal ->
            if (bal.balance < fee) {
                throw IllegalStateException("Insufficient funds")
            }
            debitedFrom = bal.balance
            bal.balance -= fee
            debitedTo = bal.balance
        }
        balanceHistory.add(
            UserBalanceHistory(
                userId = userId,
                eventType = UserBalanceHistoryEventType.Use,
                notes = "User request cost: $type",
                changeAmount = -fee,
                startBalance = debitedFrom,
                endBalance = debitedTo,
                attachments = emptyList(),
            )
        )

        val request = UserRequest(
            userId = userId,
            type = type,
            status = UserRequestStatus.Open,
            title = dto.title.trim(),
            content = dto.content.trim(),
            cost = fee,
            notes = emptyList(),
            updatedAt = now,
        )
        val result: DocumentCreateEntity<UserRequest> = collection.insertDocument(
            request,
            DocumentCreateOptions().returnNew(true)
        )
        val created = result.new

        // Notify all admins about the new user request (non-blocking best effort)
        try {
            val adminIds = users.listAdminIds()
            val requester = users.getById(userId)
            val requesterLabel = requester?.username?.takeIf { it.isNotBlank() } ?: userId
            val title = "New user request"
            val details = buildString {
                append("User: $requesterLabel\n")
                append("Type: ${created.type}\n")
                append("Title: ${created.title}\n")
            }
            adminIds.forEach { adminId ->
                try {
                    notifications.create(
                        Notification(
                            userId = adminId,
                            title = title,
                            description = details,
                            isRead = false,
                            type = NotificationType.SYSTEM,
                            otherUserId = created.userId,
                        )
                    )
                } catch (_: Throwable) { /* ignore per-admin errors */ }
            }
        } catch (_: Throwable) { /* ignore admin notify errors */ }

        return created
    }

    fun listByUser(userId: String, limit: Int = 50): List<UserRequest> {
        val query = """
            FOR r IN @@collection
            FILTER r.userId == @userId
            SORT r.updatedAt DESC
            LIMIT @limit
            RETURN r
        """.trimIndent()
        val bind = mapOf(
            "@collection" to CollectionsConfig.USER_REQUESTS_COLLECTION,
            "userId" to userId,
            "limit" to limit,
        )
        return ArangoConfig.db.query(query, UserRequest::class.java, bind).toList()
    }

    fun addNote(id: String, note: UserRequestNote): UserRequest? {
        val existing = getById(id) ?: return null
        val updated = existing.copy(
            notes = existing.notes + note,
            updatedAt = Clock.System.now(),
        )
        val res = collection.updateDocument(id, updated, DocumentUpdateOptions().returnNew(true))

        // Notify requester about new note (best-effort)
        try {
            val requesterId = existing.userId
            notifications.create(
                Notification(
                    userId = requesterId,
                    title = "Request updated",
                    description = "An admin added a note to your request '${existing.title}': ${note.text}",
                    isRead = false,
                    type = NotificationType.SYSTEM,
                    otherUserId = note.authorUserId,
                )
            )
        } catch (_: Throwable) { }

        return res.new
    }

    fun getById(id: String): UserRequest? = try {
        collection.getDocument(id, UserRequest::class.java)
    } catch (_: Throwable) { null }

    fun updateStatus(
        id: String,
        status: UserRequestStatus,
        adminUserId: String,
        note: String?,
    ): UserRequest? {
        val existing = getById(id) ?: return null
        var updated = existing.copy(
            status = status,
            updatedAt = Clock.System.now(),
        )
        if (!note.isNullOrBlank()) {
            updated = updated.copy(
                notes = updated.notes + UserRequestNote(
                    authorUserId = adminUserId,
                    text = note,
                    at = Clock.System.now(),
                )
            )
        }

        // Handle refund on Declined/Refunded with idempotency
        val shouldRefund = when (status) {
            UserRequestStatus.Declined, UserRequestStatus.Refunded ->
                existing.status != UserRequestStatus.Declined && existing.status != UserRequestStatus.Refunded
            else -> false
        }
        if (shouldRefund) {
            // Credit back the exact cost
            val fee = existing.cost
            var start = 0L
            var end = 0L
            balances.upsert(existing.userId) { bal ->
                start = bal.balance
                bal.balance += fee
                end = bal.balance
            }
            balanceHistory.add(
                UserBalanceHistory(
                    userId = existing.userId,
                    eventType = UserBalanceHistoryEventType.Deposit,
                    notes = "Refund for declined user request: ${existing.type}",
                    changeAmount = fee,
                    startBalance = start,
                    endBalance = end,
                    attachments = emptyList(),
                )
            )
        }

        val res = collection.updateDocument(id, updated, DocumentUpdateOptions().returnNew(true))
        val saved = res.new

        // Notify requester based on status (best-effort)
        try {
            val title = when (status) {
                UserRequestStatus.InReview -> "Your request is in review"
                UserRequestStatus.Approved -> "Your request was approved"
                UserRequestStatus.Declined -> "Your request was declined"
                UserRequestStatus.Completed -> "Your request was completed"
                UserRequestStatus.Refunded -> "Your request was refunded"
                UserRequestStatus.Open -> "Your request was updated"
            }
            val desc = buildString {
                append("Type: ${existing.type}\n")
                append("Title: ${existing.title}\n")
                if (!note.isNullOrBlank()) append("Note: $note\n")
                if (shouldRefund) append("A refund of ${existing.cost} VND was issued.\n")
            }
            notifications.create(
                Notification(
                    userId = existing.userId,
                    title = title,
                    description = desc,
                    isRead = false,
                    type = NotificationType.SYSTEM,
                    otherUserId = adminUserId,
                )
            )
        } catch (_: Throwable) { }

        return saved
    }

    fun listAll(status: UserRequestStatus? = null, limit: Int = 100): List<UserRequest> {
        val filter = if (status == null) "" else "FILTER r.status == @status"
        val query = """
            FOR r IN @@collection
            $filter
            SORT r.updatedAt DESC
            LIMIT @limit
            RETURN r
        """.trimIndent()
        val bind = mutableMapOf<String, Any>(
            "@collection" to CollectionsConfig.USER_REQUESTS_COLLECTION,
            "limit" to limit,
        )
        if (status != null) bind["status"] = status
        return ArangoConfig.db.query(query, UserRequest::class.java, bind).toList()
    }
}
