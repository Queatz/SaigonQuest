package models

import kotlinx.serialization.Serializable

/**
 * Supported minigame types
 */
@Serializable
enum class MinigameType {
    Flashcards,
    MatchGame,
    Scenario,
}

/**
 * Coordinate-based hotspot for "Scenario" minigames
 */
@Serializable
data class Hotspot(
    val x: Double,
    val y: Double,
    val radius: Double = 0.05, // Normalized 0..1 relative to image size
)

/**
 * Flexible item model for all minigame types
 */
@Serializable
data class MinigameItem(
    val id: String = "",
    val text: String? = null,
    val translation: String? = null,
    val image: String? = null, // URL for image
    val audio: String? = null, // URL for audio
    val definition: String? = null,
    val matchId: String? = null, // Used to pair items in MatchGame
    val hotspot: Hotspot? = null, // Used for hotspot selection in Scenario
)

/**
 * Configuration sent to the client to start a minigame
 */
@Serializable
data class MinigameConfig(
    val minigameId: String,
    val type: MinigameType,
    val title: String,
    val items: List<MinigameItem>,
    val timed: Boolean = true,
)

/**
 * Result sent from the client back to the server upon completion
 */
@Serializable
data class MinigameResult(
    val minigameId: String,
    val userId: String = "",
    val type: MinigameType,
    val reflex: Double, // Average response time in seconds
    val accuracy: Double, // Success rate (0.0 to 1.0)
    val completed: Boolean = true,
)

/**
 * Request to generate minigame items using AI
 */
@Serializable
data class MinigameGenerateRequest(
    val type: MinigameType,
    val prompt: String,
)

/**
 * Response containing generated minigame items
 */
@Serializable
data class MinigameItemsResponse(
    val items: List<MinigameItem>,
)
