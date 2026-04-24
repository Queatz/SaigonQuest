package main.components.world

import lib.Vector3

/**
 * Utility for converting movement vectors into 8-way CharacterDirection values.
 * Shared between PlayerController and OtherPlayersCoordinator to avoid duplication.
 */
fun calculateFacingFromVector(
    direction: Vector3,
    current: CharacterDirection,
): CharacterDirection {
    // Clone and normalize to avoid mutating original
    val normalized = direction.clone()
    if (normalized.length() > 0f) {
        normalized.normalize()
    } else {
        // No movement, keep current facing
        return current
    }

    val x = normalized.x
    val z = normalized.z

    return when {
        // North (z < 0)
        z < -0.7f && x > -0.3f && x < 0.3f -> CharacterDirection.N
        // Northeast
        z < -0.3f && x > 0.3f -> CharacterDirection.NE
        // East (x > 0)
        x > 0.7f && z > -0.3f && z < 0.3f -> CharacterDirection.E
        // Southeast
        z > 0.3f && x > 0.3f -> CharacterDirection.SE
        // South (z > 0)
        z > 0.7f && x > -0.3f && x < 0.3f -> CharacterDirection.S
        // Southwest
        z > 0.3f && x < -0.3f -> CharacterDirection.SW
        // West (x < 0)
        x < -0.7f && z > -0.3f && z < 0.3f -> CharacterDirection.W
        // Northwest
        z < -0.3f && x < -0.3f -> CharacterDirection.NW
        // Default to current direction if movement is ambiguous
        else -> current
    }
}
