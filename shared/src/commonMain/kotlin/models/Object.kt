package models

import kotlinx.serialization.Serializable
import models.annotations.Indexed

/**
 * Represents an object that can be placed in a world.
 * Objects can contain logic that defines their behavior.
 */
@Serializable
data class Object(
    @field:Indexed
    var title: String = "",
    var data: String = "", // JSON string containing node editor data
    @field:Indexed
    var worldId: String = "", // Reference to the world this object belongs to
    var file: String = "", // file name without .glb
    var collection: String = "", // collection in file to render as the object
    var position: Position = Position(), // Position in the 3D world
    var rotation: Rotation = Rotation(), // Rotation in the 3D world
    var scale: Scale = Scale(), // Scale in the 3D world
    var status: String = "", // Status text to display above the object
    @field:Indexed
    var kind: ObjectKind = ObjectKind.World,
) : BaseModel()

enum class ObjectKind {
    World,
    Inventory,
}

/**
 * Represents a 3D position.
 */
@Serializable
data class Position(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
)

/**
 * Represents a 3D rotation in radians.
 */
@Serializable
data class Rotation(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
)

/**
 * Represents a 3D scale.
 */
@Serializable
data class Scale(
    var x: Float = 1f,
    var y: Float = 1f,
    var z: Float = 1f
)
