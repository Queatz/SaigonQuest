package main.components.player

/**
 * Represents the friendship status between the current user and another user
 */
enum class FriendshipStatus {
    /**
     * The friendship status is unknown or still loading
     */
    UNKNOWN,
    
    /**
     * The users are not friends
     */
    NOT_FRIENDS,
    
    /**
     * The current user has sent a friend request to the other user
     */
    REQUEST_SENT,
    
    /**
     * The other user has sent a friend request to the current user
     */
    REQUEST_RECEIVED,
    
    /**
     * The users are friends
     */
    FRIENDS
}
