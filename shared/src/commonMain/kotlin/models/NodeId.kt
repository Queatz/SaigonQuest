package models

/**
 * Enum representing the specific type of a logic node
 */
enum class NodeId {
    Teleport,
    VisitorInteract,
    PlayerInteract,
    Dialogue,
    UserLanguage,
    HasItem,
    OpenUrl,
    ObjectNotification
}
