package main.components.world

import models.Sex

/**
 * Represents a sprite sheet for character animations
 */
class SpriteSheet(
    val imageUrl: String,
    val cellWidthPx: Int,
    val cellHeightPx: Int,
    val columns: Int,
    val framesPerSecond: Float = 8f,
    // Sorting: items with higher sorting values are rendered in front
    val sorting: Int = 0,
    // Wind animation options (disabled by default)
    val windEnabled: Boolean = false,
    // 5x5 weights in row-major order (top-left -> bottom-right). Null -> use defaults.
    val windWeights5x5: FloatArray? = null,
    // Optional orientation: flip vertically to swap pinned/loose edges for billboards
    val windFlipY: Boolean = false,
    // Per-item amplitude scale (meters)
    val windIntensityScale: Float = .25f,
    private val directionOrder: List<CharacterDirection> = listOf(
        CharacterDirection.S,
        CharacterDirection.SE,
        CharacterDirection.E,
        CharacterDirection.NE,
        CharacterDirection.N,
        CharacterDirection.NW,
        CharacterDirection.W,
        CharacterDirection.SW,
    )
) {
    /**
     * Gets the number of animation frames for a specific direction
     */
    fun getFrameCount() = 2
    
    /**
     * Gets the UV coordinates for a specific frame in a direction
     * 
     * @param direction The character direction
     * @param frameIndex The frame index for animation
     * @param activity The player activity, or null if standing
     * @return A quad of UV coordinates (uMin, vMin, uMax, vMax)
     */
    fun getFrameUV(
        direction: CharacterDirection,
        isMoving: Boolean,
        frameIndex: Int,
        activity: PlayerActivity? = null
    ): UVCoordinates {
        // Get the row based on the direction's position in the directionOrder list
        val directionIndex = directionOrder.indexOf(direction).takeIf { it >= 0 } ?: direction.ordinal
        val col = directionIndex
        
        // Set row = 1 when the player activity is Sit, otherwise use frameIndex
        val row = if (activity == PlayerActivity.Sit) 1 else if (isMoving) {
            2 + frameIndex % columns
        } else {
            0
        }

        // Calculate UV coordinates as fractions of the total image size
        val uMin = col * (cellWidthPx / imageWidth.toFloat())
        val vMin = row * (cellHeightPx / imageHeight.toFloat())
        
        return UVCoordinates(uMin, vMin)
    }
    
    /**
     * Sets the image dimensions after the texture loads
     */
    fun setImageDimensions(width: Int, height: Int) {
        _imageWidth = width
        _imageHeight = height
    }
    
    /**
     * Creates a sprite sheet for the player character
     * 
     * @param sex The sex of the player, which determines the sprite sheet to use
     */
    companion object {
        fun createPlayerSpriteSheet(sex: Sex? = null): SpriteSheet {
            // Choose the appropriate sprite sheet based on the player's sex
            val imageUrl = when (sex) {
                Sex.Girl -> "Player Girl Base Sprite Sheet"
                else -> "Player Boy Base Sprite Sheet"
            }
            
            return SpriteSheet(
                imageUrl = "/raw/data/spritesheets/$imageUrl.png",
                cellWidthPx = 32,
                cellHeightPx = 48,
                columns = 8
            )
        }

        /**
         * Creates a sprite sheet for the player's base based on body type and sex.
         * Body types: Skinny, Normal, Fat. Falls back to Normal when unknown.
         */
        fun createPlayerSpriteSheetForBodyType(sex: Sex?, bodyType: String?): SpriteSheet {
            val normalized = bodyType?.lowercase()?.trim()
            val fileName = when (sex) {
                Sex.Girl -> when (normalized) {
                    "skinny" -> "Player Skinny Girl Base Sprite Sheet"
                    "fat" -> "Player Fat Girl Base Sprite Sheet"
                    else -> "Player Girl Base Sprite Sheet" // Normal
                }
                else -> when (normalized) {
                    "skinny" -> "Player Skinny Boy Base Sprite Sheet"
                    "fat" -> "Player Fat Boy Base Sprite Sheet"
                    else -> "Player Boy Base Sprite Sheet" // Normal
                }
            }

            return SpriteSheet(
                imageUrl = "/raw/data/spritesheets/$fileName.png",
                cellWidthPx = 32,
                cellHeightPx = 48,
                columns = 8
            )
        }

        /**
         * Creates a sprite sheet for the player's hair based on the hair style.
         * Returns null if the style is unknown.
         */
        fun createHairSpriteSheet(sex: Sex, hairStyle: String?): SpriteSheet? {
            if (hairStyle.isNullOrBlank()) return null

            val fileName = when (hairStyle) {
                "Short" -> "short"
                "Medium" -> "medium"
                "Long" -> "long"
                else -> return null
            }

            return SpriteSheet(
                imageUrl = "/raw/data/spritesheets/${sex.spriteSheetPath()}/hair/$fileName.png",
                cellWidthPx = 32,
                cellHeightPx = 48,
                columns = 8,
                sorting = 200,
                windEnabled = true,
                // Hair: pinned at root (top), strong at tips (bottom) -> flip Y
                windFlipY = true,
                windIntensityScale = 0.065f,
            )
        }

        /**
         * Creates a sprite sheet for the player's eyes based on eye color and sex.
         * Returns null if inputs are unknown or blank.
         * Example filenames: "Blue Eyed Girl Base Sprite Sheet.png", "Brown Eyed Boy Base Sprite Sheet.png"
         */
        fun createEyesSpriteSheet(eyeColor: String?, sex: Sex?): SpriteSheet? {
            val color = when (eyeColor?.trim()?.lowercase()) {
                "black" -> "Black"
                "blue" -> "Blue"
                "brown" -> "Brown"
                "green" -> "Green"
                "gray" -> "Gray"
                else -> return null
            }

            // Todo
            val sexText = when (sex) {
                else -> "Boy"
            }
            val fileName = "$color Eyed $sexText Sprite Sheet"

            return SpriteSheet(
                imageUrl = "/raw/data/spritesheets/$fileName.png",
                cellWidthPx = 32,
                cellHeightPx = 48,
                columns = 8,
                sorting = 100,
            )
        }
    }
    
    // Image dimensions would be determined when the texture loads
    private var _imageWidth: Int = 1  // Default to 1 to avoid division by zero
    private var _imageHeight: Int = 1 // Default to 1 to avoid division by zero
    
    /**
     * Gets the width of the image in pixels
     */
    val imageWidth: Int
        get() = _imageWidth
        
    /**
     * Gets the height of the image in pixels
     */
    val imageHeight: Int
        get() = _imageHeight
}

/**
 * Represents UV coordinates for a texture
 */
data class UVCoordinates(
    val uMin: Float,
    val vMin: Float
)

fun Sex.spriteSheetPath() = if (this == Sex.Girl) "female" else "male"
