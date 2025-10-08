package models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Represents a world the user has visited (has a ChatReadMarker).
 * Includes minimal info needed by UI.
 */
@Serializable
data class VisitedWorld(
    val world: World = World(),
    @Contextual
    val lastVisited: Instant = Instant.DISTANT_PAST,
    val subscribed: Boolean = true,
)
