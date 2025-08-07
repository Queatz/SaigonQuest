package main.components.world

/**
 * Represents a sprite sheet for character animations
 */
class SpriteSheet(
    val imageUrl: String,
    val cellWidthPx: Int,
    val cellHeightPx: Int,
    val columns: Int,
    val framesPerSecond: Float = 8f,
    private val directionOrder: List<CharacterDirection> = CharacterDirection.entries
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
     */
    companion object {
        fun createPlayerSpriteSheet(): SpriteSheet {
            // The sprite sheet has directions in this order: S, SE, E, NE, N, NW, W, SW
            val directionOrder = listOf(
                CharacterDirection.S,
                CharacterDirection.SE,
                CharacterDirection.E,
                CharacterDirection.NE,
                CharacterDirection.N,
                CharacterDirection.NW,
                CharacterDirection.W,
                CharacterDirection.SW
            )
            
            return SpriteSheet(
                imageUrl = "/raw/data/spritesheets/Player Sprite Sheet.png",
                cellWidthPx = 32,
                cellHeightPx = 48,
                columns = 8,
                directionOrder = directionOrder
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
