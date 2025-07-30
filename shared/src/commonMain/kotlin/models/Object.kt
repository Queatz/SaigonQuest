package models

import kotlinx.serialization.Serializable

/**
 * Represents an object that can be placed in a world.
 * Objects can contain logic that defines their behavior.
 */
@Serializable
data class Object(
    var title: String = "",
    var categories: List<String> = emptyList(),
    var data: String = "", // JSON string containing node editor data
    var worldId: String = "", // Reference to the world this object belongs to
    var file: String = "", // world file name without .glb
    var collection: String = "", // collection in file to render as the object
    var position: Position = Position(), // Position in the 3D world
    var rotation: Rotation = Rotation(), // Rotation in the 3D world
    var scale: Scale = Scale() // Scale in the 3D world
) : BaseModel()

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
