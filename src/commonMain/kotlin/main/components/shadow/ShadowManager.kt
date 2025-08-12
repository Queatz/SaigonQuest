package main.components.shadow

import lib.AbstractMesh
import lib.CascadedShadowGenerator
import lib.Color3
import lib.DirectionalLight
import lib.Scene
import lib.Vector3
import kotlin.js.Console

/**
 * Manages shadows globally for the entire scene.
 * 
 * This class is optimized for small scenes with many moving objects:
 * - Uses a cascaded shadow map for better quality shadows
 * - Configures optimal shadow settings for performance and quality
 * - Provides methods to add/remove shadow casters dynamically
 * - Handles shadow bias and filtering to reduce artifacts
 */
class ShadowManager(
    private val scene: Scene,
    private val light: DirectionalLight
) {
    // The shadow generator instance
    private val shadowGenerator: CascadedShadowGenerator
    
    // Shadow map size (power of 2, higher = better quality but lower performance)
    private val shadowMapSize = 384
    
    // List of meshes that cast shadows
    private val shadowCasters = mutableListOf<AbstractMesh>()
    
    init {
        // Configure the light for shadows
        configureLightForShadows()
        
        // Create the cascaded shadow generator with the specified light
        shadowGenerator = CascadedShadowGenerator(shadowMapSize, light, true)
        
        // Configure shadow generator for optimal performance with many moving objects
        configureShadowGenerator()
        
        // Log shadow setup for debugging
        println("[DEBUG] Shadow manager initialized with cascaded shadow generator")
    }
    
    /**
     * Configures the light for optimal shadow casting.
     * Sets position, shadowMinZ, and shadowMaxZ.
     */
    private fun configureLightForShadows() {
        // Set light position (important for directional light shadows)
        // Position closer to the scene for more visible shadows
        light.position = Vector3(10f, 20f, 10f)
        
        // Set shadow frustum limits
        light.shadowMinZ = 0.1f  // Reduced to match camera minZ
        light.shadowMaxZ = 100f
        
        // Adjust direction to point at the center of the scene
        // Slight angle for better shadow projection
        light.direction = Vector3(-0.2f, -1f, -0.2f).normalize()
        
        // Increase intensity for more visible shadows
        light.intensity = 1.5f  // Increased for more pronounced shadows
        
        // Set diffuse color to warm white for better visibility
        light.diffuse = Color3(1f, 0.95f, 0.8f)
        
        println("[DEBUG] Light configured for shadows: position=${light.position}, direction=${light.direction}, intensity=${light.intensity}")
        println("[DEBUG] Light shadow range: minZ=${light.shadowMinZ}, maxZ=${light.shadowMaxZ}")
    }
    
    /**
     * Configures the shadow generator with optimal settings for small scenes with many moving objects.
     */
    private fun configureShadowGenerator() {
        // Configure shadow generator properties
        shadowGenerator.apply {
            lambda = .667f
            bias = 0.0125f
            normalBias = 0.005f
            
            // Enable transparent shadows
            transparencyShadow = true
            
            // Stabilize cascades for smoother shadows during camera movement
            stabilizeCascades = true
            
            // Set shadow max Z to match light
            shadowMaxZ = light.shadowMaxZ
            
            // Split the frustum for cascaded shadows
            splitFrustum()
        }
        
        println("[DEBUG] Shadow generator configured with enhanced settings:")
        println("[DEBUG] - bias: ${shadowGenerator.bias}")
        println("[DEBUG] - normalBias: ${shadowGenerator.normalBias}")
        println("[DEBUG] - lambda: ${shadowGenerator.lambda}")
        println("[DEBUG] - shadowMaxZ: ${shadowGenerator.shadowMaxZ}")
    }
    
    /**
     * Adds a mesh as a shadow caster.
     * 
     * @param mesh The mesh to add as a shadow caster
     */
    fun addShadowCaster(mesh: AbstractMesh) {
        // Skip if already a shadow caster
        if (shadowCasters.contains(mesh)) {
            return
        }
        
        // Add to shadow generator
        shadowGenerator.addShadowCaster(mesh)
        
        // Add to tracking list
        shadowCasters.add(mesh)
        
        println("[DEBUG] Added shadow caster: ${mesh.name}")
    }
    
    /**
     * Adds multiple meshes as shadow casters.
     * 
     * @param meshes The array of meshes to add as shadow casters
     */
    fun addShadowCasters(meshes: Array<AbstractMesh>) {
        println("[DEBUG] Adding ${meshes.size} meshes as shadow casters")
        meshes.forEach { mesh ->
            addShadowCaster(mesh)
        }
    }
    
    /**
     * Removes a mesh from shadow casters.
     * 
     * @param mesh The mesh to remove from shadow casters
     */
    fun removeShadowCaster(mesh: AbstractMesh) {
        // Skip if not a shadow caster
        if (!shadowCasters.contains(mesh)) {
            return
        }
        
        // Remove from shadow generator
        shadowGenerator.removeShadowCaster(mesh)
        
        // Remove from tracking list
        shadowCasters.remove(mesh)
        
        println("[DEBUG] Removed shadow caster: ${mesh.name}")
    }
    
    /**
     * Configures a mesh to receive shadows.
     * 
     * @param mesh The mesh that should receive shadows
     */
    fun configureShadowReceiver(mesh: AbstractMesh) {
        mesh.receiveShadows = true
        println("[DEBUG] Configured shadow receiver: ${mesh.name}")
    }
    
    /**
     * Configures multiple meshes to receive shadows.
     * 
     * @param meshes The array of meshes that should receive shadows
     */
    fun configureShadowReceivers(meshes: Array<AbstractMesh>) {
        println("[DEBUG] Configuring ${meshes.size} meshes as shadow receivers")
        meshes.forEach { mesh ->
            configureShadowReceiver(mesh)
        }
    }
    
    /**
     * Updates the light direction to optimize shadow coverage.
     * 
     * @param direction The new direction for the light
     */
    fun updateLightDirection(direction: Vector3) {
        light.direction = direction
    }
    
    /**
     * Gets the current shadow generator.
     * 
     * @return The shadow generator
     */
    fun getShadowGenerator(): CascadedShadowGenerator {
        return shadowGenerator
    }
    
    /**
     * Gets the list of shadow casters.
     * 
     * @return The list of shadow casters
     */
    fun getShadowCasters(): List<AbstractMesh> {
        return shadowCasters
    }
    
    /**
     * Disposes of the shadow manager resources.
     * Cleans up the shadow generator and clears the shadow casters list.
     */
    fun dispose() {
        // The shadow generator doesn't have a dispose method in our wrapper,
        // but we should remove all references to allow garbage collection
        
        // Remove all shadow casters
        shadowCasters.forEach { mesh ->
            shadowGenerator.removeShadowCaster(mesh)
        }
        
        // Clear shadow casters list
        shadowCasters.clear()
        
        println("[DEBUG] Shadow manager disposed")
    }
}
