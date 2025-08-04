package main.components.world

import lib.AbstractMesh
import lib.Color3
import lib.CreatePlaneOptions
import lib.Material
import lib.Mesh
import lib.MeshBuilder
import lib.Node
import lib.PBRMaterial
import lib.Scene
import lib.Texture
import lib.TextureOptions
import lib.TransformNode
import lib.Vector3

/**
 * Base class for all objects that can be rendered in the world.
 */
sealed class WorldObject {
    /**
     * Unique identifier for this object
     */
    var id: String = ""
    
    /**
     * Position in the 3D world
     */
    var position: Vector3 = Vector3(0f, 0f, 0f)
    
    /**
     * Rotation in the 3D world
     */
    var rotation: Vector3 = Vector3(0f, 0f, 0f)
    
    /**
     * Scale in the 3D world
     */
    var scale: Vector3 = Vector3(1f, 1f, 1f)
    
    /**
     * Renders this object in the given scene
     * 
     * @param scene The scene to render in
     * @return The created node or null if rendering failed
     */
    abstract fun render(scene: Scene): Node?
    
    /**
     * Updates the object's state
     * 
     * @param deltaTime Time since last update in seconds
     * @param camera Optional camera to use for display direction calculation
     */
    open fun update(deltaTime: Float, camera: lib.ArcRotateCamera? = null) {}
    
    /**
     * Disposes resources used by this object
     */
    open fun dispose() {}
}

/**
 * A character that can be rendered with a sprite sheet and always faces the camera
 */
class Character(
    var spriteSheet: SpriteSheet,
    var direction: CharacterDirection,
    var isMoving: Boolean = false,
    var activity: PlayerActivity? = null
) : WorldObject() {
    
    private var characterMesh: AbstractMesh? = null
    private var node: TransformNode? = null
    private var animationTime: Float = 0f
    // The direction to display based on camera angle
    private var displayDirection: CharacterDirection = direction
    
    override fun render(scene: Scene): Node? {
        // Create a transform node that will act as our container
        val container = TransformNode("character_container_${id}", scene)
        
        // Set the container's position, rotation, and scale
        container.position = position
        container.rotation = rotation
        container.scaling = scale
        
        // Create a plane mesh with the sprite sheet texture
        val material = PBRMaterial("characterMaterial_${id}", scene)
        // Create texture with NEAREST_SAMPLINGMODE for crisp pixel art rendering
        val texture = Texture(spriteSheet.imageUrl, scene, object : TextureOptions {
            override val samplingMode = Texture.NEAREST_SAMPLINGMODE
            override val hasAlpha = true
        })
        material.albedoColor = Color3(1f, 1f, 1f)
        material.albedoTexture = texture

        // Enable transparency for the material
        material.backFaceCulling = false
        material.transparencyMode = Material.MATERIAL_ALPHATEST
        material.metallic = 0.0f       // Low metallic value for non-metallic appearance
        material.roughness = 0.4f      // Medium roughness for slight diffusion of light
        material.usePhysicalLightFalloff = true  // Enable physical light falloff

        // Create a plane with the correct aspect ratio
        val aspectRatio = spriteSheet.cellHeightPx.toFloat() / spriteSheet.cellWidthPx.toFloat()
        val characterMesh = MeshBuilder.CreatePlane(
            "character_${id}",
            object : CreatePlaneOptions {
                override val width = 1f / aspectRatio
                override val height = 1f
            },
            scene
        )
        
        characterMesh.material = material
        
        // Set billboard mode to Y-axis only (rotate around Y-axis)
        characterMesh.billboardMode = Mesh.BILLBOARDMODE_Y
        
        // Make the character mesh a child of the container
        characterMesh.parent = container

        spriteSheet.setImageDimensions(
            spriteSheet.cellWidthPx * spriteSheet.columns,
            spriteSheet.cellWidthPx * spriteSheet.columns // assume square
        )
        
        this.characterMesh = characterMesh
        this.node = container
        return container
    }
    
    override fun update(deltaTime: Float, camera: lib.ArcRotateCamera?) {
        // Calculate the display direction based on camera angle
        if (camera != null) {
            // Use the provided camera
            calculateDisplayDirectionWithCamera(camera)
        } else {
            // Use the default camera from SceneManager
            calculateDisplayDirection()
        }
        
        // Update animation frame based on direction and movement state
        if (isMoving) {
            animationTime += deltaTime
        }
        
        // Always update the sprite animation to reflect the current display direction
        updateSpriteAnimation()
        
        // Log activity state changes for debugging
        if (activity == PlayerActivity.Sit) {
            console.log("[DEBUG_LOG] Character ${id} is sitting")
        }
    }
    
    /**
     * Calculates which direction sprite to display based on the relative angle
     * between the player's facing direction and the camera's facing direction.
     * 
     * This is the key method that implements the requirement to show the correct
     * sprite direction based on the camera's perspective. For example:
     * - If camera faces north and player faces south, we show south sprite
     * - If camera faces south and player faces north, we also show south sprite
     * 
     * The calculation works by finding the angle between the camera's facing direction
     * and the player's facing direction in the horizontal (XZ) plane, then mapping
     * this angle to one of the 8 sprite directions.
     */
    private fun calculateDisplayDirection() {
        // Get the camera controller from SceneManager
        val cameraController = main.components.SceneManager.getCameraController() ?: return
        val camera = cameraController.getCamera()
        
        // Use the common implementation with the obtained camera
        calculateDisplayDirectionWithCamera(camera)
    }
    
    /**
     * Calculates which direction sprite to display based on the relative angle
     * between the player's facing direction and the provided camera's facing direction.
     * 
     * This version allows passing in a specific camera instead of using the SceneManager.
     * 
     * @param camera The camera to use for calculation
     */
    private fun calculateDisplayDirectionWithCamera(camera: lib.ArcRotateCamera) {
        // Convert player direction to a vector
        val playerDirectionVector = directionToVector(direction)
        
        // Get camera direction (from camera to target)
        val cameraPosition = camera.position
        val cameraTarget = camera.target
        val cameraDirection = cameraTarget.subtract(cameraPosition).normalize()
        
        // We only care about the horizontal angle (in the XZ plane)
        // Project both vectors onto the XZ plane and normalize them
        val cameraDirectionXZ = Vector3(cameraDirection.x, 0f, cameraDirection.z).normalize()
        val playerDirectionXZ = Vector3(playerDirectionVector.x, 0f, playerDirectionVector.z).normalize()
        
        // Calculate the dot product of the two vectors
        // For unit vectors, dot product = cos(angle)
        val dotProduct = Vector3.Dot(cameraDirectionXZ, playerDirectionXZ)
        
        // Calculate the angle in radians using the dot product
        // acos(dot product) gives us the angle between the vectors
        val angle = kotlin.math.acos(dotProduct.toDouble()).toFloat()
        
        // Convert angle to degrees for easier mapping to directions
        val angleDegrees = angle * (180f / kotlin.math.PI.toFloat())
        
        // Determine if the angle is positive or negative (clockwise or counterclockwise)
        // We need to know if the player is to the left or right of the camera
        // This is equivalent to the Y component of the cross product
        val crossProductY = cameraDirectionXZ.x * playerDirectionXZ.z - cameraDirectionXZ.z * playerDirectionXZ.x
        val isNegativeAngle = crossProductY < 0
        
        // Adjust the angle based on the sign
        val signedAngleDegrees = if (isNegativeAngle) -angleDegrees else angleDegrees
        
        // Map the angle to one of the 8 directions
        // The sprite sheet has directions in this order: S, SE, E, NE, N, NW, W, SW
        // We divide the 360 degrees into 8 sectors of 45 degrees each
        displayDirection = when {
            signedAngleDegrees >= -22.5f && signedAngleDegrees < 22.5f -> CharacterDirection.S
            signedAngleDegrees >= 22.5f && signedAngleDegrees < 67.5f -> CharacterDirection.SE
            signedAngleDegrees >= 67.5f && signedAngleDegrees < 112.5f -> CharacterDirection.E
            signedAngleDegrees >= 112.5f && signedAngleDegrees < 157.5f -> CharacterDirection.NE
            signedAngleDegrees >= 157.5f || signedAngleDegrees < -157.5f -> CharacterDirection.N
            signedAngleDegrees >= -157.5f && signedAngleDegrees < -112.5f -> CharacterDirection.NW
            signedAngleDegrees >= -112.5f && signedAngleDegrees < -67.5f -> CharacterDirection.W
            signedAngleDegrees >= -67.5f && signedAngleDegrees < -22.5f -> CharacterDirection.SW
            else -> CharacterDirection.S // Default to South if something goes wrong
        }
    }
    
    /**
     * Converts a CharacterDirection enum to a direction vector.
     */
    private fun directionToVector(dir: CharacterDirection): Vector3 {
        return when (dir) {
            CharacterDirection.N -> Vector3(0f, 0f, 1f)
            CharacterDirection.NE -> Vector3(-0.7071f, 0f, 0.7071f)
            CharacterDirection.E -> Vector3(-1f, 0f, 0f)
            CharacterDirection.SE -> Vector3(-0.7071f, 0f, -0.7071f)
            CharacterDirection.S -> Vector3(0f, 0f, -1f)
            CharacterDirection.SW -> Vector3(0.7071f, 0f, -0.7071f)
            CharacterDirection.W -> Vector3(1f, 0f, 0f)
            CharacterDirection.NW -> Vector3(0.7071f, 0f, 0.7071f)
        }
    }

    /**
     * Updates the sprite animation to show the correct frame based on the current
     * display direction and animation time.
     * 
     * This method calculates which frame to show based on the animation time and
     * updates the texture's UV coordinates to display that frame from the sprite sheet.
     */
    private fun updateSpriteAnimation() {
        // Calculate current frame based on animationTime
        val frameIndex = (animationTime * spriteSheet.framesPerSecond).toInt() % spriteSheet.getFrameCount(displayDirection)
            
        // Update UV coordinates on the mesh to show the correct sprite
        characterMesh?.let { mesh ->
            val material = mesh.material as? PBRMaterial
            material?.albedoTexture?.let { texture ->
                // Get the UV coordinates for the current display direction and frame
                // Pass the activity parameter to use the correct sprite row for sitting
                val uvCoords = spriteSheet.getFrameUV(displayDirection, frameIndex, activity)
                
                // BabylonJS handles the UV mapping internally when we set these properties
                // Set the UV coordinates using the properly exposed properties
                texture.uOffset = uvCoords.uMin
                texture.vOffset = uvCoords.vMin
                texture.uScale = spriteSheet.cellWidthPx.toFloat() / spriteSheet.imageWidth.toFloat()
                texture.vScale = spriteSheet.cellHeightPx.toFloat() / spriteSheet.imageHeight.toFloat()
            }
        }
    }
    
    override fun dispose() {
        characterMesh?.dispose()
        node?.dispose()
    }
}

/**
 * A 3D model that can be rendered in the world
 */
class Model(
    var mesh: AbstractMesh
) : WorldObject() {
    
    private var modelMesh: AbstractMesh? = null
    private var node: TransformNode? = null
    
    override fun render(scene: Scene): Node? {
        // Create a transform node that will act as our container
        val container = TransformNode("model_container_${id}", scene)

        // Clone the mesh and make it a child of the container
        val clonedMesh = mesh.clone("model_${id}")
        clonedMesh.parent = container
        
        // Store references to the meshes
        modelMesh = clonedMesh
        node = container
        
        return container
    }
    
    override fun update(deltaTime: Float, camera: lib.ArcRotateCamera?) {
        // Model doesn't need camera for updates
    }
    
    override fun dispose() {
        modelMesh?.dispose()
        node?.dispose()
    }
}
