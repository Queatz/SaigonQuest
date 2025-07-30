package main.components.world

import kotlinx.coroutines.suspendCancellableCoroutine
import lib.AbstractMesh
import lib.Color3
import lib.DirectionalLight
import lib.HemisphericLight
import lib.PBRMaterial
import lib.Scene
import lib.SceneLoader
import lib.Vector3
import main.api.WebSocketApi
import main.api.WorldApi
import main.components.shadow.ShadowManager
import models.World
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Handles loading and initialization of the 3D world.
 * 
 * This class encapsulates all world-related functionality including:
 * - Loading world data from the API
 * - Loading 3D models
 * - Applying materials to world meshes
 */
class WorldLoader(private val scene: Scene) {
    
    // World meshes for collision detection and interaction
    private var worldMeshes: Array<AbstractMesh> = emptyArray()
    
    // Current world data
    private var currentWorld: World? = null
    
    // Lights for the scene
    private var hemisphericLight: HemisphericLight? = null
    private var directionalLight: DirectionalLight? = null
    
    // Shadow manager for global shadow handling
    private var shadowManager: ShadowManager? = null
    
    /**
     * Sets up lighting for the scene.
     * Creates a hemispheric light for ambient lighting and a directional light for shadows.
     * Initializes the shadow manager for global shadow handling.
     */
    private fun setupLighting() {
        println("[DEBUG] Setting up scene lighting")
        
        // Add a hemispheric light for ambient lighting
        hemisphericLight = HemisphericLight(
            name = "hemisphericLight",
            direction = Vector3(0f, 1f, 0f),
            scene = scene
        ).apply {
            this.intensity = 0.2f  // Further reduced intensity to make directional light shadows more visible
            this.groundColor = Color3(0.1f, 0.1f, 0.15f)  // Slightly blue ground color for better contrast
            println("[DEBUG] Created hemispheric light with intensity ${this.intensity}")
        }

        // Add a directional light for shadows
        directionalLight = DirectionalLight(
            name = "directionalLight",
            // Direction will be set by ShadowManager
            direction = Vector3(0f, -1f, 0f),
            scene = scene
        )
        
        println("[DEBUG] Created directional light (settings will be configured by ShadowManager)")
        
        // Initialize the shadow manager with the directional light
        // ShadowManager will configure the light for optimal shadow casting
        directionalLight?.let { light ->
            shadowManager = ShadowManager(scene, light)
            println("[DEBUG] Shadow manager initialized")
        }
    }
    
    /**
     * Loads a world by its URL.
     * 
     * @param url The URL of the world to load
     * @return The loaded world meshes
     */
    suspend fun loadWorld(url: String): Array<AbstractMesh> {
        // Set up lighting for the scene
        setupLighting()
        
        try {
            // First load the world data from the API
            val world = WorldApi.getWorldByUrl(url)
            
            // Store the world data
            currentWorld = world
            
            // Connect to WebSocket for real-time updates
            WebSocketApi.connect(url)
            
            // Determine the file to load
            val worldFile = if (world.file.isNotEmpty()) "${world.file}.glb" else "world.glb"
            
            // Load the 3D model using suspendCancellableCoroutine to convert callback to coroutine
            return suspendCancellableCoroutine { continuation ->
                SceneLoader.ImportMesh(
                    meshNames = "",
                    rootUrl = "/raw/data/",
                    sceneFilename = worldFile,
                    scene = scene,
                    onSuccess = { meshes, _, _, _ ->
                        // Store world meshes for collision detection
                        worldMeshes = meshes
                        
                        // Apply PBR materials to all meshes
                        applyMaterials(meshes)
                        
                        // Resume the coroutine with the loaded meshes
                        continuation.resume(meshes)
                    },
                    onError = { _, message, exception ->
                        // Resume the coroutine with an exception
                        continuation.resumeWithException(Exception("Error loading model: $message. $exception"))
                    }
                )
                
                // Register cancellation handler
                continuation.invokeOnCancellation {
                    // Clean up resources if needed when coroutine is cancelled
                    println("[DEBUG] World loading cancelled")
                }
            }
        } catch (error: Exception) {
            // Propagate the error
            println("[ERROR] Failed to load world: ${error.message}")
            throw error
        }
    }
    
    /**
     * Applies PBR materials to the world meshes and configures shadows.
     * 
     * @param meshes The meshes to apply materials to
     */
    private fun applyMaterials(meshes: Array<AbstractMesh>) {
        println("[DEBUG] Applying materials and configuring shadows for ${meshes.size} meshes")
        
        // Log information about each mesh for debugging
        println("[DEBUG] Mesh list from .glb file:")
        meshes.forEachIndexed { index, mesh ->
            println("[DEBUG] Mesh #$index: name=${mesh.name}, visible=${mesh.isVisible}, position=${mesh.position}, scaling=${mesh.scaling}")
        }
        
        // Count for logging
        var materialCount = 0
        var shadowCasterCount = 0
        var shadowReceiverCount = 0
        
        meshes.forEach { mesh ->
            // Skip if mesh is not visible or is a helper object
            if (!mesh.isVisible || mesh.name.startsWith("__")) {
                println("[DEBUG] Skipping mesh: ${mesh.name} (invisible or helper)")
                return@forEach
            }
            
            // Create a PBR material
            val material = PBRMaterial("pbr-${mesh.name}", scene).apply {
                metallic = 0.3f
                roughness = 0.6f
                
                // Ensure material is fully opaque for proper depth texture rendering
                alpha = 1.0f
                disableDepthWrite = false  // Enable depth writing
                transparencyMode = 0       // OPAQUE mode (0)
                alphaMode = 0              // ALPHA_DISABLE mode (0)
                
                // Use the existing material's colors if available
                val existingMaterial = mesh.material
                if (existingMaterial is PBRMaterial) {
                    albedoColor = existingMaterial.albedoColor
                    emissiveColor = existingMaterial.emissiveColor
                    
                    println("[DEBUG] Using existing material colors for ${mesh.name}")
                } else {
                    println("[DEBUG] Creating new material for ${mesh.name}")
                }
                
                println("[DEBUG] Material settings for ${mesh.name}: alpha=${alpha}, disableDepthWrite=${disableDepthWrite}, transparencyMode=${transparencyMode}, alphaMode=${alphaMode}")
            }
            
            // Assign the PBR material to the mesh
            mesh.material = material
            materialCount++
            
            // Configure shadows for the mesh
            shadowManager?.let { manager ->
                // Make ALL objects cast shadows (as per requirement)
                manager.addShadowCaster(mesh)
                shadowCasterCount++
                
                // Make all objects receive shadows
                manager.configureShadowReceiver(mesh)
                shadowReceiverCount++
                
                // Log the mesh configuration
                println("[DEBUG] Configured mesh for shadows: ${mesh.name} (both caster and receiver)")
            } ?: run {
                println("[ERROR] Shadow manager is null! Cannot configure shadows for ${mesh.name}")
            }
        }
        
        println("[DEBUG] Applied materials to $materialCount meshes")
        println("[DEBUG] Configured $shadowCasterCount shadow casters and $shadowReceiverCount shadow receivers")
        
        // Final shadow configuration check
        if (shadowCasterCount == 0) {
            println("[WARNING] No shadow casters configured! Shadows will not be visible.")
        }
        if (shadowReceiverCount == 0) {
            println("[WARNING] No shadow receivers configured! Shadows will not be visible.")
        }
        
        // Log directional light status
        directionalLight?.let { light ->
            println("[DEBUG] Directional light status:")
            println("[DEBUG] - position: ${light.position}")
            println("[DEBUG] - direction: ${light.direction}")
            println("[DEBUG] - intensity: ${light.intensity}")
            println("[DEBUG] - shadowMinZ: ${light.shadowMinZ}")
            println("[DEBUG] - shadowMaxZ: ${light.shadowMaxZ}")
        } ?: run {
            println("[ERROR] Directional light is null! Shadows will not be visible.")
        }
    }
    
    /**
     * Gets the current world data.
     * 
     * @return The current world data
     */
    fun getCurrentWorld(): World? {
        return currentWorld
    }
    
    /**
     * Gets the world meshes.
     * 
     * @return The array of world meshes
     */
    fun getWorldMeshes(): Array<AbstractMesh> {
        return worldMeshes
    }
    
    /**
     * Finds a valid starting position on the world ground.
     * 
     * @return A valid position Vector3, or null if none found
     */
    fun findValidStartPosition(): Vector3? {
        // This is a placeholder - the actual implementation would use
        // raycasting to find a valid position on the ground
        return Vector3.Zero()
    }
    
    /**
     * Gets the shadow manager for global shadow handling.
     * 
     * @return The shadow manager, or null if not initialized
     */
    fun getShadowManager(): ShadowManager? {
        return shadowManager
    }
    
    /**
     * Disposes of the world loader resources.
     * Cleans up lights, shadow manager, and disconnects from WebSocket.
     */
    fun dispose() {
        // Dispose of shadow manager
        shadowManager?.dispose()
        shadowManager = null
        
        // Set lights to null to allow garbage collection
        hemisphericLight = null
        directionalLight = null
        
        // Disconnect from WebSocket
        WebSocketApi.disconnect()
    }
}
