package main.components.world

import lib.AbstractMesh
import lib.Color3
import lib.CreatePlaneOptions
import lib.DynamicTexture
import lib.DynamicTextureOptions
import lib.Engine
import lib.Mesh
import lib.MeshBuilder
import lib.StandardMaterial
import lib.Texture
import lib.Scene
import lib.Vector3

/**
 * A class for rendering 2D text in a 3D scene that always faces the camera.
 * The text is rendered on a plane that acts as a billboard.
 * 
 * This class uses BabylonJS's built-in billboard mode (BILLBOARDMODE_ALL) to ensure
 * the text always faces the camera from any angle. The text is rendered onto a
 * dynamic texture which is applied to a plane mesh.
 * 
 * Usage example:
 * ```
 * // Create a text billboard
 * val textBillboard = TextBillboard(
 *     scene = scene,
 *     text = "Hello World",
 *     fontSize = 24,
 *     textColor = "white"
 * )
 * 
 * // Position the text billboard in the scene
 * textBillboard.position = Vector3(0f, 2f, 0f)
 * 
 * // Add it to the scene using WorldObjectManager
 * val handle = worldObjectManager.addObject(textBillboard)
 * 
 * // Update the text
 * textBillboard.text = "Updated text"
 * 
 * // Or use the helper method in WorldObjectManager
 * val handle = worldObjectManager.createTextBillboard(
 *     text = "Hello World",
 *     position = Vector3(0f, 2f, 0f),
 *     fontSize = 24,
 *     textColor = "white"
 * )
 * ```
 */
class TextBillboard(
    private val scene: Scene,
    text: String = "",
    private val width: Int = 256,
    private val height: Int = 64,
    private val fontSize: Int = 32,
    private val fontFamily: String = "Arial",
    private val textColor: String = "white",
    private val backgroundColor: String = "transparent"
) : WorldObject() {
    
    // The mesh that will display the text
    private var planeMesh: Mesh? = null
    
    // The dynamic texture for rendering text
    private var texture: DynamicTexture? = null
    
    // Store texture dimensions for later use
    private var textureWidth: Int = 512
    private var textureHeight: Int = 128
    
    // Current text value
    private var _text: String = text
    
    // Getter and setter for text
    var text: String
        get() = _text
        set(value) {
            _text = value
            updateTexture()
        }
    
    init {
        // Initialize with the provided text
        createTextPlane()
    }
    
    /**
     * Creates a plane with a dynamic texture for the text
     */
    private fun createTextPlane() {
        // Calculate texture dimensions with the correct aspect ratio
        // We want the texture to have the same aspect ratio as the final plane
        // First, estimate the text width based on font size and text length
        val estimatedTextWidth = _text.length * fontSize * 0.6f
        val padding = fontSize * 0.2f
        val estimatedWidth = estimatedTextWidth + padding * 2
        val estimatedHeight = fontSize * 1.2f + padding * 2
        val aspectRatio = estimatedHeight / estimatedWidth
        
        // Create a dynamic texture with dimensions that match the estimated aspect ratio
        this.textureWidth = 512 // Base width for good resolution
        this.textureHeight = (this.textureWidth * aspectRatio).toInt()
        
        // Create the dynamic texture with the calculated dimensions
        texture = DynamicTexture(
            "text-texture-${id}",
            object : DynamicTextureOptions {
                override val width = textureWidth
                override val height = textureHeight
            },
            scene,
            false, // generateMipMaps
            Texture.NEAREST_SAMPLINGMODE, // Use NEAREST sampling for crisp text
            Engine.TEXTUREFORMAT_ALPHA // Use alpha format for transparency
        )
        
        // Now create a plane with the same aspect ratio as the texture
        val planeWidth = (estimatedWidth / 10f).coerceAtLeast(0.1f)
        val planeHeight = planeWidth * aspectRatio
        
        planeMesh = MeshBuilder.CreatePlane(
            "text-plane-${id}",
            object : CreatePlaneOptions {
                override val width = planeWidth
                override val height = planeHeight
                override val updatable = true
            },
            scene
        )
        
        // Create a material for the plane
        val material = StandardMaterial("text-material-${id}", scene).apply {
            diffuseColor = Color3(1f, 1f, 1f)
            
            // Set emissive color to white to ensure text is visible regardless of lighting
            emissiveColor = Color3(1f, 1f, 1f)
            
            // Disable lighting effects
            disableLighting = true
            
            // Use the dynamic texture
            // Use asDynamic() to bypass type checking since DynamicTexture is a subclass of Texture
            asDynamic().diffuseTexture = texture
            
            // Set texture to have alpha
            texture?.hasAlpha = true
            
            // Enable transparency from the texture
            useAlphaFromDiffuseTexture = true
            
            // Set transparency mode to ALPHA_BLEND (2)
            transparencyMode = 2
            
            // Disable backface culling for proper transparency
            backFaceCulling = false
        }
        
        // Apply the material to the plane
        planeMesh?.material = material
        
        // Set billboard mode to make the text always face the camera
        planeMesh?.billboardMode = Mesh.BILLBOARDMODE_ALL
        
        // Update the texture with the initial text
        updateTexture()
    }
    
    /**
     * Updates the texture with the current text
     */
    private fun updateTexture() {
        texture?.let { tex ->
            // Get the canvas context
            val context = tex.getContext()
            
            // Clear the canvas with transparent background
            context.clearRect(0.0, 0.0, textureWidth.toDouble(), textureHeight.toDouble())
            
            // Explicitly set the background to transparent if specified
            if (backgroundColor == "transparent") {
                // Do nothing - clearRect already gives us transparency
            } else {
                // Fill with the specified background color
                context.asDynamic().fillStyle = backgroundColor
                context.fillRect(0.0, 0.0, textureWidth.toDouble(), textureHeight.toDouble())
            }
            
            // Set up the text style
            context.font = "${fontSize}px ${fontFamily}"
            
            // Use asDynamic() to set text alignment and baseline
            context.asDynamic().textAlign = "center"
            context.asDynamic().textBaseline = "middle"
            
            // Set the text color
            context.asDynamic().fillStyle = textColor
            
            // Draw the text in the center of the texture
            context.fillText(_text, textureWidth / 2.0, textureHeight / 2.0)
            
            // Update the texture
            tex.update(true)
            
            // Update the plane dimensions based on the new text
            updatePlaneDimensions()
        }
    }
    
    /**
     * Updates the plane dimensions based on the current text
     */
    private fun updatePlaneDimensions() {
        planeMesh?.let { mesh ->
            // Estimate the text width based on font size and text length
            val estimatedTextWidth = _text.length * fontSize * 0.6f
            
            // Calculate the aspect ratio based on estimated text dimensions
            val padding = fontSize * 0.2f
            val estimatedWidth = estimatedTextWidth + padding * 2
            val estimatedHeight = fontSize * 1.2f + padding * 2
            val aspectRatio = estimatedHeight / estimatedWidth
            
            // Update the stored texture dimensions (for future texture updates)
            this.textureHeight = (this.textureWidth * aspectRatio).toInt()
            
            // Scale the width based on text length
            val planeWidth = (estimatedWidth / 10f).coerceAtLeast(0.1f)
            val planeHeight = planeWidth * aspectRatio
            
            // Update the mesh scaling
            mesh.scaling = Vector3(planeWidth, planeHeight, 1f)
        }
    }
    
    /**
     * Renders the text billboard in the scene
     */
    override fun render(scene: Scene): AbstractMesh? {
        // The mesh is already created in init, just return it
        return planeMesh
    }
    
    /**
     * Updates the billboard (called every frame)
     */
    override fun update(deltaTime: Float) {
        // Position updates are handled by the WorldObjectHandle
        // Billboard rotation is handled automatically by BabylonJS
        planeMesh?.let { mesh ->
            mesh.position = position
            mesh.rotation = rotation
            mesh.scaling = scale
        }
    }
    
    /**
     * Disposes resources used by the text billboard
     */
    override fun dispose() {
        texture?.dispose()
        planeMesh?.dispose()
    }
}
