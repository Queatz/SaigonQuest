package models

/**
 * Enum representing the specific type of a logic node
 */
enum class NodeId {
    Teleport,
    VisitorInteract,
    PlayerInteract,
    VisitorNearby,
    PlayerNearby,
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
    SetStatus,
    PlayerInput,
    StringTemplate,
    PlayerName,
    CreateDoc,
    DocContent,
    Ai,
    IsObjectOwner,
}
