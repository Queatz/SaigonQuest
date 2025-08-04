package main.components.world

import lib.Color3
import lib.CreatePlaneOptions
import lib.DynamicTexture
import lib.DynamicTextureOptions
import lib.Engine
import lib.Material
import lib.Mesh
import lib.MeshBuilder
import lib.Node
import lib.StandardMaterial
import lib.Texture
import lib.Scene
import lib.TransformNode

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
    private val fontSize: Int = 32,
    private val fontFamily: String = "Arial",
    private val textColor: String = "white",
    private val backgroundColor: String = "transparent"
) : WorldObject() {
    
    // The mesh that will display the text
    private var planeMesh: Mesh? = null
    
    // Container mesh that will act as the node
    private var node: TransformNode? = null
    
    // The dynamic texture for rendering text
    private var texture: DynamicTexture? = null
    
    // Current text value
    private var _text: String = text
    
    // Getter and setter for text
    var text: String
        get() = _text
        set(value) {
            if (_text != value) {
                _text = value
                // Clean up existing resources
                planeMesh?.dispose()
                texture?.dispose()
                // Recreate the plane with the new text
                createTextPlane()
            }
        }

    /**
     * Calculates the dimensions and aspect ratio for the text
     * @return Triple of (estimatedWidth, estimatedHeight, aspectRatio)
     */
    private fun calculateTextDimensions(): Triple<Float, Float, Float> {
        // Estimate the text width based on font size and text length
        val estimatedTextWidth = _text.length * fontSize * 0.6f
        val padding = fontSize * 0.2f
        val estimatedWidth = estimatedTextWidth + padding * 2
        val estimatedHeight = fontSize * 1.2f + padding * 2
        val aspectRatio = estimatedHeight / estimatedWidth
        
        return Triple(estimatedWidth, estimatedHeight, aspectRatio)
    }
    
    /**
     * Creates a plane with a dynamic texture for the text
     */
    private fun createTextPlane() {
        // Calculate texture dimensions with the correct aspect ratio
        val (estimatedWidth, estimatedHeight, aspectRatio) = calculateTextDimensions()
        
        // Create a dynamic texture with dimensions that match the estimated aspect ratio
        val textureWidth = estimatedWidth.toInt()
        val textureHeight = estimatedHeight.toInt()

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
        val planeWidth = (estimatedWidth / 100f).coerceAtLeast(0.1f)
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

        planeMesh!!.parent = node
        
        // Create a material for the plane
        val material = StandardMaterial("text-material-${id}", scene).apply {
            diffuseColor = Color3(1f, 1f, 1f)
            
            // Disable lighting effects
            disableLighting = true
            
            // Use the dynamic texture
            // Use asDynamic() to bypass type checking since DynamicTexture is a subclass of Texture
            diffuseTexture = texture!!
            emissiveTexture = texture!!

            // Set texture to have alpha
            texture?.hasAlpha = true
            
            // Enable transparency from the texture
            useAlphaFromDiffuseTexture = true
            
            // Set transparency mode to ALPHA_BLEND (2)
            transparencyMode = Material.MATERIAL_ALPHATEST
            
            // Disable backface culling for proper transparency
            backFaceCulling = false
        }
        
        // Apply the material to the plane
        planeMesh?.material = material
        
        // Set billboard mode to make the text always face the camera
        planeMesh?.billboardMode = Mesh.BILLBOARDMODE_ALL

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
                
                // Draw a rounded rectangle with 6px corner radius
                val cornerRadius = 6.0
                val width = textureWidth.toDouble()
                val height = textureHeight.toDouble()
                
                // Begin a new path
                context.beginPath()
                
                // Draw the rounded rectangle path
                context.moveTo(cornerRadius, 0.0)
                context.lineTo(width - cornerRadius, 0.0)
                context.quadraticCurveTo(width, 0.0, width, cornerRadius)
                context.lineTo(width, height - cornerRadius)
                context.quadraticCurveTo(width, height, width - cornerRadius, height)
                context.lineTo(cornerRadius, height)
                context.quadraticCurveTo(0.0, height, 0.0, height - cornerRadius)
                context.lineTo(0.0, cornerRadius)
                context.quadraticCurveTo(0.0, 0.0, cornerRadius, 0.0)
                
                // Fill the path
                context.fill()
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
        }
    }
    /**
     * Renders the text billboard in the scene
     */
    override fun render(scene: Scene): Node? {
        // Create a container mesh that will act as our node
        val container = TransformNode(
            "text_container_${id}",
            scene
        )
        
        // Set the container's position, rotation, and scale
        container.position = position
        container.rotation = rotation
        container.scaling = scale

        // Store the container mesh
        node = container

        createTextPlane()
        
        return container
    }
    
    /**
     * Updates the billboard (called every frame)
     * 
     * @param deltaTime Time since last update in seconds
     * @param camera Optional camera to use for display direction calculation
     */
    override fun update(deltaTime: Float, camera: lib.ArcRotateCamera?) {
        // TextBillboard doesn't need camera for updates
    }
    
    /**
     * Disposes resources used by the text billboard
     */
    override fun dispose() {
        texture?.dispose()
        planeMesh?.dispose()
        node?.dispose()
    }
}
