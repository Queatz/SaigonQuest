package models

import kotlinx.serialization.Serializable

@Serializable
enum class FriendshipState {
    NotFriends,
    Friends,
    IncomingRequest,
    OutgoingRequest,
}

@Serializable
data class FriendshipStatusDto(
    val status: FriendshipState,
    val requestId: String? = null,
)
