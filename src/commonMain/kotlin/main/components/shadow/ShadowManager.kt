package main.components.shadow

import lib.AbstractMesh
import lib.CascadedShadowGenerator
import lib.Color3
import lib.DirectionalLight
import lib.Scene
import lib.ShadowGenerator
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
    // The main shadow generator instance (directional light)
    private val shadowGenerator: CascadedShadowGenerator
    
    // List of point light shadow generators (from GLB files)
    private val pointLightShadowGenerators = mutableListOf<ShadowGenerator>()
    
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
        light.shadowMaxZ = 100f // 500f
        
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
            bias = 0.005f  // Reduced bias for better contact shadows with PCF
            normalBias = 0.01f // Adjust for PCF
            
            // Enable transparent shadows
            transparencyShadow = true
            
            // Stabilize cascades for smoother shadows during camera movement
            stabilizeCascades = true
            
            // Set shadow max Z to match light
            shadowMaxZ = light.shadowMaxZ
            
            // Apply soft shadow settings
            applySoftShadowSettings(this)
            
            // Split the frustum for cascaded shadows
            splitFrustum()
            
            // Set cascade blend percentage to soften transitions
            cascadeBlendPercentage = 0.1f
        }
        
        println("[DEBUG] Shadow generator configured with enhanced settings:")
        println("[DEBUG] - bias: ${shadowGenerator.bias}")
        println("[DEBUG] - normalBias: ${shadowGenerator.normalBias}")
        println("[DEBUG] - lambda: ${shadowGenerator.lambda}")
        println("[DEBUG] - shadowMaxZ: ${shadowGenerator.shadowMaxZ}")
        println("[DEBUG] - usePercentageCloserFiltering: ${shadowGenerator.usePercentageCloserFiltering}")
        println("[DEBUG] - filteringQuality: ${shadowGenerator.filteringQuality}")
    }
    
    /**
     * Applies soft shadow settings to a shadow generator.
     * Uses Percentage Closer Filtering (PCF) for a balance of performance and quality.
     * 
     * @param generator The shadow generator to configure
     */
    private fun applySoftShadowSettings(generator: ShadowGenerator) {
        // Use PCF for soft shadows
        generator.usePercentageCloserFiltering = true
        generator.filteringQuality = ShadowGenerator.FILTERINGQUALITY_MEDIUM
        
        // Alternatively, PCSS (Contact Hardening Shadows) can be used for even softer, more realistic shadows:
        generator.useContactHardeningShadow = true
        generator.contactHardeningLightSizeUVRatio = 0.05f
        
        // Set darkness to make shadows less harsh (0.0 = fully dark, 1.0 = fully lit)
        // generator.darkness = 0.2f
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
        
        // Add to main shadow generator
        shadowGenerator.addShadowCaster(mesh)
        
        // Add to all point light shadow generators
        pointLightShadowGenerators.forEach { it.addShadowCaster(mesh) }
        
        // Add to tracking list
        shadowCasters.add(mesh)
        
        println("[DEBUG] Added shadow caster: ${mesh.name} to main and ${pointLightShadowGenerators.size} point light generators")
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
        
        // Remove from main shadow generator
        shadowGenerator.removeShadowCaster(mesh)
        
        // Remove from all point light shadow generators
        pointLightShadowGenerators.forEach { it.removeShadowCaster(mesh) }
        
        // Remove from tracking list
        shadowCasters.remove(mesh)
        
        println("[DEBUG] Removed shadow caster: ${mesh.name}")
    }
    
    /**
     * Registers a point light shadow generator.
     * 
     * @param generator The point light shadow generator to register
     */
    fun addPointLightShadowGenerator(generator: ShadowGenerator) {
        // Apply consistent soft shadow settings
        applySoftShadowSettings(generator)
        
        pointLightShadowGenerators.add(generator)
        
        // Add all existing shadow casters to this new generator
        shadowCasters.forEach { mesh ->
            generator.addShadowCaster(mesh)
        }
        
        println("[DEBUG] Registered point light shadow generator and added ${shadowCasters.size} existing casters")
    }
    
    /**
     * Clears all point light shadow generators.
     */
    fun clearPointLightShadowGenerators() {
        pointLightShadowGenerators.forEach { it.dispose() }
        pointLightShadowGenerators.clear()
        println("[DEBUG] Cleared all point light shadow generators")
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
        // Remove all shadow casters from main generator
        shadowCasters.forEach { mesh ->
            shadowGenerator.removeShadowCaster(mesh)
        }
        
        // Dispose and clear point light shadow generators
        clearPointLightShadowGenerators()
        
        // Clear shadow casters list
        shadowCasters.clear()
        
        println("[DEBUG] Shadow manager disposed")
    }
}
