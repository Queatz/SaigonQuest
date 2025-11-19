package models

import kotlinx.serialization.Serializable

@Serializable
enum class UserRequestType {
    Item,
    Wearable,
    Object,
    Node,
}
