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
    OBJECT_UPDATE
}
