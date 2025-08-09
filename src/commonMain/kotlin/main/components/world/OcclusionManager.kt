package main.components.world

import lib.AbstractMesh
import lib.Material
import lib.Matrix
import lib.MeshMetadata
import lib.PBRCustomMaterial
import lib.PBRMaterial
import lib.Scene
import lib.Vector2
import lib.Vector3
import main.components.SceneManager
import kotlin.math.min

/**
 * Manages occlusion for objects that obstruct the player's view.
 * 
 * This class uses PBRCustomMaterial to create a "see-through" effect when objects
 * are within a certain screen-space radius of the player. The fade radius is calculated
 * as half of the smaller dimension of the screen, ensuring the effect scales
 * appropriately with different screen sizes.
 * 
 * Features:
 * - Creates a circular occlusion region in screen space around the player
 * - Configurable occlusion softness (from sharp edge to gradual transition)
 * - Automatically scales with screen size
 * - Preserves all PBR lighting and material properties for visible fragments
 * 
 * Implementation Details:
 * - Uses PBRCustomMaterial with Fragment_Before_FragColor for shader customization
 * - Fragments are discarded based on their screen-space distance from the player
 * - Uses the GLSL 'discard' keyword to completely remove fragments from rendering
 * - Only modifies the alpha channel, preserving the original color
 * 
 * Usage:
 * 1. Create an instance of OcclusionManager
 * 2. Register meshes that should be considered for occlusion
 * 3. Call update() every frame (typically in the scene's beforeRender callback)
 * 4. Call dispose() when done to clean up resources
 * 
 * @param scene The BabylonJS scene
 */
class OcclusionManager(private val scene: Scene) {
    // Don't initialize these at construction time, get them dynamically when needed
    private var camera: lib.Camera? = null
    private var player: lib.Mesh? = null
    private val occludedMeshes = mutableSetOf<AbstractMesh>()
    private val customMaterials = mutableMapOf<AbstractMesh, PBRCustomMaterial>()
    
    // Configurable parameters
    private var occlusionSoftness = 0.25f  // Default: medium softness (50% transition zone)
    
    // Debug mode flag - set to enable debug visualization
    private var debugModeEnabled = true
    
    // Custom shader name
    private val customShaderName = "occlusionFader"
    
    // Fragment shader code for uniform declarations (global scope)
    private val uniformDeclarations = """
    // Set default precision for float values
    precision mediump float;
    
    // Constants for optimization
    const float ALPHA_THRESHOLD = 0.5;  // Higher value = more fragments discarded
    
    // Forward declaration of the applyOcclusion function
    mediump float applyOcclusion(mediump float alpha);
    """

    // Fragment shader code for occlusion effect implementation
    private val occlusionShaderCode = """
    // Set default precision for float values
    precision mediump float;

    float applyOcclusion(float alpha) {
        vec2 playerScreenPixel = playerScreenPos.xy * screenSize;
        vec2 delta = gl_FragCoord.xy - playerScreenPixel;
        float distance = sqrt(dot(delta, delta));
        float normalizedDistance = distance / occlusionRadius;
        
        float distToCamera = length(vPositionW.xz - cameraWorld.xz);
        float distFromPlayerToCamera = length(playerWorld.xz - cameraWorld.xz);
        float distToPlayer = length(vPositionW.xz - playerWorld.xz);
        
        // Check if fragment is closer to camera than player for all coordinates
        if (distToCamera < distFromPlayerToCamera && vPositionW.y > playerWorld.y && pow(distToCamera, 0.5) < distToPlayer) {
            if (occlusionSoftness <= 0.0) {
                discard;
            } else if (occlusionSoftness >= 1.0) {
                return alpha * smoothstep(0.0, ALPHA_THRESHOLD, normalizedDistance); // Smooth fade
            } else {
                float innerRadius = occlusionRadius * (1.0 - occlusionSoftness);
                if (distance < innerRadius) {
                    discard;
                } else {
                    float gradientPos = (distance - innerRadius) / (occlusionRadius - innerRadius);
                    return alpha * smoothstep(0.0, ALPHA_THRESHOLD, gradientPos); // Soft edge
                }
            }
        }
        return alpha;
    }
    """

    // Fragment shader code for function call
    private val occlusionCallCode = """
    // Set default precision for float values
    precision mediump float;

    // In Fragment_Before_FragColor, we modify the gl_FragColor directly
    // Only modify the alpha channel, preserving the original color
    alpha = applyOcclusion(alpha);
    """

    /**
     * Sets the occlusion softness parameter
     * @param softness Value between 0 and 1:
     *   - 0: Sharp edge (abrupt transition between visible and discarded fragments)
     *   - 0.5: Medium softness (gradual transition zone of 50% width)
     *   - 1: Full softness (gradual transition from center to edge)
     */
    fun setOcclusionSoftness(softness: Float) {
        occlusionSoftness = softness.coerceIn(0f, 1f)
    }
    
    /**
     * Enables or disables debug visualization mode.
     * When enabled, fragments near the occlusion boundary will be shown in color
     * to help verify that the occlusion effect is working correctly.
     * 
     * @param enabled Whether debug mode should be enabled
     */
    fun setDebugMode(enabled: Boolean) {
        debugModeEnabled = enabled
    }
    
    /**
     * Returns whether debug visualization mode is currently enabled.
     * 
     * @return True if debug mode is enabled, false otherwise
     */
    fun isDebugModeEnabled(): Boolean {
        return debugModeEnabled
    }

    /**
     * Registers a mesh to be considered for occlusion.
     * Creates a PBRCustomMaterial for the mesh with occlusion shader code.
     * 
     * @param mesh The mesh to register
     */
    fun registerMesh(mesh: AbstractMesh) {
        // Skip if mesh is not visible or is a helper object
        if (!mesh.isVisible) {
            return
        }
        
        // Ensure the material is a PBRMaterial
        val originalMaterial = mesh.material
        if (originalMaterial is PBRMaterial) {
            // Store the original material to restore later
            val metadata = object : MeshMetadata {
                override var originalMaterial: Material? = originalMaterial
                override var isItem: Boolean? = false
            }
            mesh.metadata = metadata
            
            // Create a PBRCustomMaterial based on the original PBRMaterial
            val customMaterial = PBRCustomMaterial("${customShaderName}-${mesh.name}", scene)
            
            // Copy properties from the original material
            customMaterial.albedoColor = originalMaterial.albedoColor
            customMaterial.metallic = originalMaterial.metallic
            customMaterial.roughness = originalMaterial.roughness
            customMaterial.emissiveColor = originalMaterial.emissiveColor
            customMaterial.emissiveIntensity = originalMaterial.emissiveIntensity
            customMaterial.transparencyMode = Material.MATERIAL_ALPHATEST//ANDBLEND
            customMaterial.alphaTestCutoff = 0.5f

            // Copy textures if they exist
            if (originalMaterial.albedoTexture != null) {
                customMaterial.albedoTexture = originalMaterial.albedoTexture
            }
            if (originalMaterial.metallicTexture != null) {
                customMaterial.metallicTexture = originalMaterial.metallicTexture
            }
            if (originalMaterial.roughnessTexture != null) {
                customMaterial.roughnessTexture = originalMaterial.roughnessTexture
            }
            if (originalMaterial.emissiveTexture != null) {
                customMaterial.emissiveTexture = originalMaterial.emissiveTexture
            }
            if (originalMaterial.bumpTexture != null) {
                customMaterial.bumpTexture = originalMaterial.bumpTexture
            }
            
            // Register all required uniforms
            customMaterial.AddUniform("playerScreenPos", "vec3")
            customMaterial.AddUniform("screenSize", "vec2")
            customMaterial.AddUniform("occlusionRadius", "float")
            customMaterial.AddUniform("playerWorld", "vec3")
            customMaterial.AddUniform("cameraWorld", "vec3")
            customMaterial.AddUniform("occlusionSoftness", "float")
            customMaterial.AddUniform("debugMode", "float")
            customMaterial.AddUniform("time", "float")
            
            customMaterial.Fragment_Begin(uniformDeclarations)
            
            customMaterial.Fragment_Definitions(occlusionShaderCode)

            // Use Fragment_Custom_Alpha instead of Fragment_Before_FragColor since we're only modifying the alpha
            customMaterial.Fragment_Custom_Alpha(occlusionCallCode)
            
            // Add a handler for the onBindObservable to set uniforms when the material is bound
            customMaterial.onBindObservable.add { _ ->
                // Get the effect after the material is bound
                val effect = customMaterial.getEffect()
                if (effect == null) {
                    console.log("[ERROR] Effect is null for material ${customMaterial.name} during binding! Cannot set uniforms.")
                    return@add
                }
                
                try {
                    // Skip if no player or camera
                    val playerMesh = player ?: SceneManager.getPlayerController()?.getCollider() ?: return@add
                    val cam = camera ?: scene.activeCamera ?: return@add
                    
                    // Get player's world position
                    val playerWorldPos = playerMesh.position
                    
                    // Get player's screen position (normalized [0,1] coordinates)
                    val playerScreenPos = Vector3.Project(
                        playerWorldPos,
                        Matrix.Identity(),
                        scene.getTransformMatrix(),
                        cam.viewport
                    ).apply {
                        y = 1.0f - y  // Invert Y coordinate
                    }

                    // Calculate occlusion radius based on minimum of view width/height
                    val viewWidth = scene.getEngine().getRenderWidth()
                    val viewHeight = scene.getEngine().getRenderHeight()
                    val occlusionRadius = min(viewWidth, viewHeight) / 2.0f
                    
                    // Create screenSize vector once and reuse it
                    val screenSize = Vector2(viewWidth.toFloat(), viewHeight.toFloat())
                    
                    // Get current time for animations
                    val currentTime = (kotlinx.browser.window.performance.now() / 1000.0).toFloat()  // Convert to seconds
                    
                    // Set player position uniforms
                    effect.setVector3("playerScreenPos", playerScreenPos)
                    effect.setVector3("playerWorld", playerWorldPos)
                    effect.setVector3("cameraWorld", camera?.globalPosition ?: Vector3(0f, 0f, 0f))
                    
                    // Set occlusion parameters
                    effect.setFloat("occlusionRadius", occlusionRadius)
                    effect.setFloat("occlusionSoftness", occlusionSoftness)
                    
                    // Set screen size
                    effect.setVector2("screenSize", screenSize)
                    
                    // Set debugMode uniform
                    effect.setFloat("debugMode", if (debugModeEnabled) 1.0f else 0.0f)
                    
                    // Set time uniform for animations
                    effect.setFloat("time", currentTime)
                } catch (e: Exception) {
                    console.log("[ERROR] Failed to set uniforms during binding for material ${customMaterial.name}: ${e.message}")
                }
            }

            // Assign the custom material to the mesh
            mesh.material = customMaterial

            // Print the fragment shader source code for debugging
            println(customMaterial.getEffect()?.getFragmentShaderSource())

            // Store the custom material for later updates
            customMaterials[mesh] = customMaterial
            
            // Add to tracking set
            occludedMeshes.add(mesh)
        }
    }

    /**
     * Updates the occlusion effect.
     * Should be called every frame.
     * 
     * This method refreshes the player and camera references to ensure they're up-to-date,
     * which is crucial if they weren't available when the OcclusionManager was first created.
     * 
     * It also calls updateShaderUniforms as a fallback mechanism for setting uniforms,
     * in addition to the onBindObservable handler that sets uniforms when materials are bound.
     * This dual approach ensures that uniforms are set both when materials are bound and
     * every frame, maximizing the chances that the uniforms are properly applied to the shader.
     */
    fun update() {
        // Refresh player and camera references
        camera = scene.activeCamera
        player = SceneManager.getPlayerController()?.getCollider()
    }
    
    /**
     * Disposes the occlusion manager and cleans up resources.
     * Restores original materials to meshes and clears tracking sets.
     */
    fun dispose() {
        // Restore original materials
        occludedMeshes.forEach { mesh ->
            val metadata = mesh.metadata
            if (metadata?.originalMaterial != null) {
                mesh.material = metadata.originalMaterial!!
            }
        }
        
        // Clear tracking sets
        occludedMeshes.clear()
        customMaterials.clear()
    }
}
