package main.components.shadow

import lib.AbstractMesh
import lib.ArcRotateCamera
import lib.CascadedShadowGenerator
import lib.Color3
import lib.DirectionalLight
import lib.Scene
import lib.ShadowGenerator
import lib.Vector3
import main.components.GraphicsSettings
import kotlin.js.Console
import kotlin.math.abs

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
    private var shadowGenerator: CascadedShadowGenerator
    
    // List of point light shadow generators (from GLB files)
    private val pointLightShadowGenerators = mutableListOf<ShadowGenerator>()
    
    // Task 4: Shadow Map Downscaling
    // Default size is now 2048, reduced to 1024 in Low Graphics mode
    private var shadowMapSize = if (GraphicsSettings.isLowGraphicsModeEnabled()) 1024 else 2048
    
    // List of meshes that cast shadows
    private val shadowCasters = mutableListOf<AbstractMesh>()
    
    // Task 2: Shadow Distance Culling
    // Meshes currently added to the generators
    private val activeShadowCasters = mutableSetOf<AbstractMesh>()
    private val shadowDistanceThreshold = 250f
    private val shadowDistanceThresholdSq = shadowDistanceThreshold * shadowDistanceThreshold
    
    // Observer for graphics settings changes
    private val graphicsSettingsObserver: () -> Unit = {
        if (GraphicsSettings.isShadowsEnabled()) {
            val isLow = GraphicsSettings.isLowGraphicsModeEnabled()
            val newSize = if (isLow) 512 else 2048
            // Recreate if size changed OR if we changed pretty mode which affects PCSS
            if (newSize != shadowMapSize) {
                println("[DEBUG] Shadow map size changed: $shadowMapSize -> $newSize. Recreating shadow generator...")
                recreateShadowGenerator(newSize)
            } else {
                // Just re-apply settings (like PCSS)
                applySoftShadowSettings(shadowGenerator)
            }
        } else {
            // If shadows are disabled, ensure active shadow casters are cleared
            if (activeShadowCasters.isNotEmpty()) {
                println("[DEBUG] Shadows disabled. Clearing all active shadow casters.")
                activeShadowCasters.forEach { mesh ->
                    shadowGenerator.removeShadowCaster(mesh)
                    pointLightShadowGenerators.forEach { it.removeShadowCaster(mesh) }
                }
                activeShadowCasters.clear()
            }
        }
    }
    
    init {
        // Configure the light for shadows
        configureLightForShadows()
        
        // Create the cascaded shadow generator with the specified light
        shadowGenerator = CascadedShadowGenerator(
            mapSize = shadowMapSize,
            light = light,
            useFullFloatEngine = true
        )
        
        // Configure shadow generator for optimal performance with many moving objects
        configureShadowGenerator()
        
        // Listen for graphics settings changes to update shadow map resolution if needed
        GraphicsSettings.addObserver(graphicsSettingsObserver)

        // Log shadow setup for debugging
        println("[DEBUG] Shadow manager initialized with cascaded shadow generator (size=$shadowMapSize)")
    }

    /**
     * Recreates the shadow generator with a new map size.
     * This is called when graphics settings change.
     */
    private fun recreateShadowGenerator(newSize: Int) {
        shadowMapSize = newSize
        
        // Clean up old generator
        shadowGenerator.dispose()
        
        // Create new one
        shadowGenerator = CascadedShadowGenerator(
            mapSize = shadowMapSize,
            light = light,
            useFullFloatEngine = true
        )
        configureShadowGenerator()
        
        // Re-add active shadow casters
        activeShadowCasters.forEach { mesh ->
            shadowGenerator.addShadowCaster(mesh)
        }
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
        light.shadowMaxZ = 500f // Matches camera maxZ
        
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
            bias = 0.001f  // Reduced bias for better contact shadows with PCF at higher resolution
            normalBias = 0.005f // Adjust for PCF
            
            // Enable transparent shadows
            transparencyShadow = true
            
            // Stabilize cascades for smoother shadows during camera movement
            stabilizeCascades = true
            
            // Auto calculate depth bounds for better cascade distribution in big scenes
            autoCalcDepthBounds = true
            
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
        val isLowGraphics = GraphicsSettings.isLowGraphicsModeEnabled()
        val isPretty = GraphicsSettings.isPrettyGraphicsEnabled()

        // Use PCF for soft shadows
        generator.usePercentageCloserFiltering = true
        generator.filteringQuality = if (isLowGraphics) {
            ShadowGenerator.FILTERINGQUALITY_LOW
        } else {
            ShadowGenerator.FILTERINGQUALITY_MEDIUM
        }

        // Only use PCSS (Contact Hardening Shadows) in Pretty mode, as it's very expensive
        // PCSS provides realistic contact hardening but has significant GPU cost.
        if (isPretty && !isLowGraphics) {
            generator.useContactHardeningShadow = true
            generator.contactHardeningLightSizeUVRatio = 0.05f
        } else {
            generator.useContactHardeningShadow = false
        }
    }
    
    private var updateCounter = 0
    
    /**
     * Updates shadow casters based on distance to camera (Task 2)
     */
    fun update() {
        // If shadows are disabled, clear active shadow casters and return early
        if (!GraphicsSettings.isShadowsEnabled()) {
            if (activeShadowCasters.isNotEmpty()) {
                activeShadowCasters.forEach { mesh ->
                    shadowGenerator.removeShadowCaster(mesh)
                    pointLightShadowGenerators.forEach { it.removeShadowCaster(mesh) }
                }
                activeShadowCasters.clear()
            }
            return
        }

        val camera = scene.activeCamera ?: return
        
        // Performance Improvement: Throttle shadow distance check to every 10 frames
        updateCounter++
        if (updateCounter % 15 != 0) return
        
        val cameraPos = camera.position
        
        // Task 2: Shadow Distance Culling (Optimized for big scenes)
        // Calculate dynamic threshold based on camera distance (radius) for ArcRotateCamera
        val cameraRadius = (camera as? ArcRotateCamera)?.radius ?: 0f
        val effectiveThresholdSq = if (cameraRadius > 0) {
            // Ensure threshold is at least 150% of camera radius to avoid culling visible objects
            val dynamicThreshold = if (shadowDistanceThreshold > cameraRadius * 1.5f) shadowDistanceThreshold else cameraRadius * 1.5f
            dynamicThreshold * dynamicThreshold
        } else {
            shadowDistanceThresholdSq
        }
        
        shadowCasters.forEach { mesh ->
            val dx = mesh.position.x - cameraPos.x
            val dy = mesh.position.y - cameraPos.y
            val dz = mesh.position.z - cameraPos.z
            val distanceSq = dx * dx + dy * dy + dz * dz
            val isWithinDistance = distanceSq <= effectiveThresholdSq
            val isActive = activeShadowCasters.contains(mesh)
            
            if (isWithinDistance && !isActive) {
                // Add to generators
                shadowGenerator.addShadowCaster(mesh)
                pointLightShadowGenerators.forEach { it.addShadowCaster(mesh) }
                activeShadowCasters.add(mesh)
            } else if (!isWithinDistance && isActive) {
                // Remove from generators
                shadowGenerator.removeShadowCaster(mesh)
                pointLightShadowGenerators.forEach { it.removeShadowCaster(mesh) }
                activeShadowCasters.remove(mesh)
            }
        }
    }

    /**
     * Adds a mesh as a shadow caster.
     * 
     * @param mesh The mesh to add as a shadow caster
     */
    fun addShadowCaster(mesh: AbstractMesh) {
        // Skip if already tracked
        if (shadowCasters.contains(mesh)) {
            return
        }
        
        // Add to tracking list
        shadowCasters.add(mesh)
        
        // Let update() handle adding it to the actual generator based on distance
    }
    
    /**
     * Adds multiple meshes as shadow casters.
     * 
     * @param meshes The array of meshes to add as shadow casters
     */
    fun addShadowCasters(meshes: Array<AbstractMesh>) {
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
        // Remove from trackers
        if (!shadowCasters.remove(mesh)) {
            return
        }
        
        // If it was active, remove from generators
        if (activeShadowCasters.remove(mesh)) {
            shadowGenerator.removeShadowCaster(mesh)
            pointLightShadowGenerators.forEach { it.removeShadowCaster(mesh) }
        }
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
        
        // Add all active shadow casters to this new generator
        activeShadowCasters.forEach { mesh ->
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
        // Remove graphics settings observer
        GraphicsSettings.removeObserver(graphicsSettingsObserver)
        
        // Remove all shadow casters from main generator
        activeShadowCasters.forEach { mesh ->
            shadowGenerator.removeShadowCaster(mesh)
        }
        
        // Dispose main generator
        shadowGenerator.dispose()
        
        // Dispose and clear point light shadow generators
        clearPointLightShadowGenerators()
        
        // Clear shadow casters lists
        shadowCasters.clear()
        activeShadowCasters.clear()
        
        println("[DEBUG] Shadow manager disposed")
    }
}
