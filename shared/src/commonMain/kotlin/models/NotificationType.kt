package models

import kotlinx.serialization.Serializable

/**
 * Represents the type of a notification.
 */
@Serializable
enum class NotificationType {
    /**
     * System notification (general announcements, etc.)
     */
    SYSTEM,
    
    /**
     * Friend request notification
     */
    FRIEND_REQUEST,

    /**
     * Friend request accepted notification
     */
    FRIEND_REQUEST_ACCEPTED,
    
    /**
     * Achievement notification
     */
    ACHIEVEMENT,
    
    /**
     * World update notification
     */
    WORLD_UPDATE,
    
    /**
     * Object update notification
     */
    OBJECT_UPDATE,

    /**
     * Object notification from logic node
     */
    OBJECT_NOTIFICATION,

    /**
     * New chat in a world since last visit
     */
    CHAT_NEW,
}
