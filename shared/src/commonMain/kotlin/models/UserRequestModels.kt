package models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import models.annotations.Indexed
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
enum class UserRequestStatus {
    Open,
    InReview,
    Approved,
    Declined,
    Completed,
    Refunded,
}

@Serializable
data class UserRequestNote(
    var authorUserId: String = "",
    var text: String = "",
    @Contextual
    var at: Instant = Clock.System.now(),
)

@Serializable
data class UserRequest(
    @field:Indexed
    var userId: String = "",
    var type: UserRequestType = UserRequestType.Item,
    var status: UserRequestStatus = UserRequestStatus.Open,
    var title: String = "",
    var content: String = "",
    /** VND */
    var cost: Long = 0L,
    var notes: List<UserRequestNote> = emptyList(),
    @Contextual
    var updatedAt: Instant = Clock.System.now(),
) : BaseModel()

@Serializable
data class CreateUserRequestDto(
    var type: UserRequestType = UserRequestType.Item,
    var title: String = "",
    var content: String = "",
)

@Serializable
data class UpdateUserRequestStatusDto(
    var status: UserRequestStatus = UserRequestStatus.Open,
    var note: String = "",
)

@Serializable
data class AddUserRequestNoteDto(
    var note: String = "",
)
