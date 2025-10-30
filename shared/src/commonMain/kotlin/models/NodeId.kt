package models

/**
 * Enum representing the specific type of a logic node
 */
enum class NodeId {
    Teleport,
    VisitorInteract,
    PlayerInteract,
    PlayerJoined,
    VisitorJoined,
    Dialogue,
    UserLanguage,
    UserSex,
    UserAge,
    HasItem,
    OpenUrl,
    ObjectNotification,
    ObjectOwnerNotification,
    Delay,
    TakeItems,
    GiveItems,
    SendChat,
    PlayerInput,
    StringTemplate,
    PlayerName,
}
