package routes

import auth.JWTUtils
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.Routing
import models.*
import repository.NotificationRepository
import repository.UserBalanceChangeRequestRepository
import repository.UserBalanceHistoryRepository
import repository.UserBalanceRepository
import repository.UserRepository

fun Routing.configureBalanceRoutes() {
    val balances = UserBalanceRepository()
    val requests = UserBalanceChangeRequestRepository()
    val history = UserBalanceHistoryRepository()
    val notifications = NotificationRepository()
    val users = UserRepository()
    val friends = repository.FriendRepository()

    authenticate {
        route("/balance") {
            get {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
                val bal = balances.getByUserId(userId) ?: UserBalance(userId = userId, balance = 0)
                call.respond(bal)
            }

            get("/history") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 5
                call.respond(history.listRecent(userId, limit))
            }

            post("/requests") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                val body = try { call.receive<CreateBalanceChangeRequest>() } catch (e: Throwable) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Invalid request body", code = HttpStatusCode.BadRequest.value)
                    )
                }
                if (body.amount <= 0) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Amount must be positive", code = HttpStatusCode.BadRequest.value)
                    )
                }
                val atts: List<String> = body.attachments ?: emptyList()
                if (atts.isEmpty()) {
                    // Require an image for both deposit and withdraw per spec
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Attachment image is required", code = HttpStatusCode.BadRequest.value)
                    )
                }
                val req = UserBalanceChangeRequest(
                    userId = userId,
                    type = body.type,
                    status = UserBalanceChangeStatus.Received,
                    notes = body.notes.orEmpty(),
                    amount = body.amount,
                    attachments = atts,
                    resolved = false
                )
                val created = requests.create(req)

                // Notify all admins about the new balance change request
                try {
                    val adminIds = users.listAdminIds()
                    val requester = users.getById(userId)
                    val requesterLabel = requester?.username?.takeIf { it.isNotBlank() } ?: userId
                    val title = "New balance request: ${created.type}"
                    val details = buildString {
                        append("User: $requesterLabel\n")
                        append("Type: ${created.type}\n")
                        append("Amount: ${created.amount} VND\n")
                        if (created.notes.isNotBlank()) append("Notes: ${created.notes}\n")
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
                                    otherUserId = created.userId
                                )
                            )
                        } catch (e: Throwable) {
                            call.application.environment.log.warn("Failed to notify admin $adminId about balance request ${created.key}: ${e.message}")
                        }
                    }
                } catch (e: Throwable) {
                    call.application.environment.log.warn("Failed to prepare admin notifications for balance request ${created.key}: ${e.message}")
                }

                call.respond(HttpStatusCode.Created, created)
            }

            get("/requests") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
                val statusParam = call.request.queryParameters["status"]
                val statuses = statusParam?.split(',')
                    ?.mapNotNull { runCatching { UserBalanceChangeStatus.valueOf(it) }.getOrNull() }
                    ?.toSet()
                call.respond(requests.listForUser(userId, statuses))
            }

            // Send VND to a friend
            post("/send") {
                val principal = call.principal<JWTPrincipal>()
                val fromUserId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post

                val body = try { call.receive<SendVndRequest>() } catch (_: Throwable) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Invalid request body", code = HttpStatusCode.BadRequest.value)
                    )
                }

                if (body.amount <= 0) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Amount must be positive", code = HttpStatusCode.BadRequest.value)
                    )
                }

                val toUserId = body.toUserId
                if (toUserId.isBlank() || toUserId == fromUserId) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Invalid recipient", code = HttpStatusCode.BadRequest.value)
                    )
                }

                val fromUser = users.getById(fromUserId)
                    ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found", HttpStatusCode.NotFound.value))
                val toUser = users.getById(toUserId)
                    ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Recipient not found", HttpStatusCode.NotFound.value))

                val areFriends = try { friends.existsFriendship(fromUserId, toUserId) } catch (_: Throwable) { false }
                if (!areFriends) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "You can only send VND to friends", code = HttpStatusCode.Forbidden.value)
                    )
                }

                val fromBefore = balances.getByUserId(fromUserId)?.balance ?: 0L
                if (body.amount > fromBefore) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Insufficient funds", code = HttpStatusCode.BadRequest.value)
                    )
                }
                val toBefore = balances.getByUserId(toUserId)?.balance ?: 0L

                val fromAfter = fromBefore - body.amount
                val toAfter = toBefore + body.amount

                val savedFrom = balances.upsert(fromUserId) { it.balance = fromAfter }
                val savedTo = balances.upsert(toUserId) { it.balance = toAfter }

                // History entries
                val sendAttachments = body.attachments ?: emptyList()
                history.add(
                    UserBalanceHistory(
                        userId = fromUserId,
                        eventType = UserBalanceHistoryEventType.Send,
                        notes = body.note?.takeIf { it.isNotBlank() } ?: "To ${toUser.username}",
                        changeAmount = -body.amount,
                        startBalance = fromBefore,
                        endBalance = savedFrom.balance,
                        attachments = sendAttachments
                    )
                )
                history.add(
                    UserBalanceHistory(
                        userId = toUserId,
                        eventType = UserBalanceHistoryEventType.Receive,
                        notes = body.note?.takeIf { it.isNotBlank() } ?: "From ${fromUser.username}",
                        changeAmount = body.amount,
                        startBalance = toBefore,
                        endBalance = savedTo.balance,
                        attachments = sendAttachments
                    )
                )

                // Notify recipient
                val title = "VND received"
                val desc = "You received ${body.amount} VND from ${fromUser.username}"
                notifications.create(
                    Notification(
                        userId = toUserId,
                        title = title,
                        description = desc,
                        isRead = false,
                        type = NotificationType.VND_RECEIVED,
                        otherUserId = fromUserId
                    )
                )

                call.respond(HttpStatusCode.OK, mapOf("balance" to savedFrom.balance))
            }

            post("/requests/{id}/cancel") {
                val principal = call.principal<JWTPrincipal>()
                val userId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                val id = call.parameters["id"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing id", code = HttpStatusCode.BadRequest.value)
                )
                val req = requests.getById(id) ?: return@post call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "Request not found", code = HttpStatusCode.NotFound.value)
                )
                if (req.userId != userId) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "Not allowed to cancel this request", code = HttpStatusCode.Forbidden.value)
                    )
                }
                if (req.status != UserBalanceChangeStatus.Received) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(message = "Only pending requests can be cancelled", code = HttpStatusCode.BadRequest.value)
                    )
                }
                val updated = requests.updateStatus(id, UserBalanceChangeStatus.CancelledByUser)
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(message = "Request not found", code = HttpStatusCode.NotFound.value)
                    )
                call.respond(HttpStatusCode.OK, updated)
            }
        }
    }

    authenticate {
        route("/admin/balance-requests") {

            get {
                // Admin check inline (after JWT auth resolution)
                val principal = call.principal<JWTPrincipal>()
                val adminUserId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
                val adminUser = users.getById(adminUserId)
                if (adminUser == null || !adminUser.isAdmin) {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "Admin access required", code = HttpStatusCode.Forbidden.value)
                    )
                }
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                val statusParam = call.request.queryParameters["status"]
                val statuses = statusParam?.split(',')
                    ?.mapNotNull { runCatching { UserBalanceChangeStatus.valueOf(it) }.getOrNull() }
                    ?.toSet()
                val resolvedParam = call.request.queryParameters["resolved"]
                val resolved = when (resolvedParam?.lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
                // Default behavior preserved: no filters -> pending list
                if (statuses == null && resolved == null) {
                    call.respond(requests.listPending(limit))
                } else {
                    call.respond(requests.listAdmin(statuses, resolved, limit))
                }
            }

            // Fetch a user's current balance (admin helper)
            get("/balance/{userId}") {
                // Admin check inline (after JWT auth resolution)
                val principal = call.principal<JWTPrincipal>()
                val adminUserId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@get
                val adminUser = users.getById(adminUserId)
                if (adminUser == null || !adminUser.isAdmin) {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "Admin access required", code = HttpStatusCode.Forbidden.value)
                    )
                }
                val userId = call.parameters["userId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing userId", code = HttpStatusCode.BadRequest.value)
                )
                val bal = balances.getByUserId(userId) ?: UserBalance(userId = userId, balance = 0)
                call.respond(HttpStatusCode.OK, bal)
            }

            post("/{id}/approve") {
                // Admin check inline (after JWT auth resolution)
                val principal = call.principal<JWTPrincipal>()
                val adminUserId = JWTUtils.getUserIdOrRespondWithError(call, principal) ?: return@post
                val adminUser = users.getById(adminUserId)
                if (adminUser == null || !adminUser.isAdmin) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(message = "Admin access required", code = HttpStatusCode.Forbidden.value)
                    )
                }
                val id = call.parameters["id"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing id", code = HttpStatusCode.BadRequest.value)
                )
                val body = try { call.receive<AdminDecisionRequest>() } catch (_: Throwable) { AdminDecisionRequest(null) }

                val req = requests.getById(id) ?: return@post call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(message = "Request not found", code = HttpStatusCode.NotFound.value)
                )

                val userId = req.userId
                val before = balances.getByUserId(userId)?.balance ?: 0L
                if (req.type == UserBalanceChangeType.Withdraw && req.amount > before) {
                    val note = body.note?.takeIf { it.isNotBlank() } ?: "Insufficient funds"
                    val denied = requests.updateStatus(id, UserBalanceChangeStatus.Denied, note)
                        ?: return@post call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse(message = "Request not found", code = HttpStatusCode.NotFound.value)
                        )

                    notifications.create(
                        Notification(
                            userId = userId,
                            title = "Balance request ${denied.type} denied",
                            description = note,
                            isRead = false,
                            type = NotificationType.SYSTEM
                        )
                    )

                    return@post call.respond(denied)
                }

                val updated = requests.updateStatus(id, UserBalanceChangeStatus.Complete, body.note)
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(message = "Request not found", code = HttpStatusCode.NotFound.value)
                    )

                val after = when (updated.type) {
                    UserBalanceChangeType.Deposit -> before + updated.amount
                    UserBalanceChangeType.Withdraw -> before - updated.amount
                    UserBalanceChangeType.Use -> before - updated.amount
                }

                val savedBalance = balances.upsert(userId) { it.balance = after }
                history.add(
                    UserBalanceHistory(
                        userId = userId,
                        eventType = when (updated.type) {
                            UserBalanceChangeType.Deposit -> UserBalanceHistoryEventType.Deposit
                            UserBalanceChangeType.Withdraw -> UserBalanceHistoryEventType.Withdraw
                            UserBalanceChangeType.Use -> UserBalanceHistoryEventType.Use
                        },
                        notes = updated.notes,
                        changeAmount = if (updated.type == UserBalanceChangeType.Deposit) updated.amount else -updated.amount,
                        startBalance = before,
                        endBalance = savedBalance.balance,
                        attachments = updated.attachments
                    )
                )

                notifications.create(
                    Notification(
                        userId = userId,
                        title = "Balance request ${updated.type} approved",
                        description = "Amount: ${updated.amount} VND. ${body.note.orEmpty()}",
                        isRead = false,
                        type = NotificationType.SYSTEM
                    )
                )

                call.respond(updated)
            }

            post("/{id}/deny") {
                val id = call.parameters["id"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(message = "Missing id", code = HttpStatusCode.BadRequest.value)
                )
                val body = try { call.receive<AdminDecisionRequest>() } catch (_: Throwable) { AdminDecisionRequest(null) }
                val updated = requests.updateStatus(id, UserBalanceChangeStatus.Denied, body.note)
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(message = "Request not found", code = HttpStatusCode.NotFound.value)
                    )

                notifications.create(
                    Notification(
                        userId = updated.userId,
                        title = "Balance request ${updated.type} denied",
                        description = body.note ?: "",
                        isRead = false,
                        type = NotificationType.SYSTEM
                    )
                )

                call.respond(updated)
            }
        }
    }
}
