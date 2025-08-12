package main.components.world

import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.suspendCancellableCoroutine
import lib.AbstractMesh
import lib.Color3
import lib.CreatePlaneOptions
import lib.DirectionalLight
import lib.HemisphericLight
import lib.MeshBuilder
import lib.PBRMaterial
import lib.Scene
import lib.SceneLoader
import lib.Vector3
import main.api.WebSocketApi
import main.api.WorldApi
import main.components.SceneManager
import main.components.shadow.ShadowManager
import models.World
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.PI
import kotlin.math.*
import kotlinx.browser.window
import models.ErrorResponse

/**
 * Handles loading and initialization of the 3D world.
 * 
 * This class encapsulates all world-related functionality including:
 * - Loading world data from the API
 * - Loading 3D models
 * - Applying materials to world meshes
 */
class WorldLoader(private val scene: Scene) {
    
    companion object {
        // Callback to be executed when the world is loaded
        private var onWorldLoadedCallback: (() -> Unit)? = null

        // References to the currently active lights for global updates
        private var currentHemisphericLight: HemisphericLight? = null
        private var currentDirectionalLight: DirectionalLight? = null

        // Reference to the current WorldLoader instance for cross-component notifications
        private var currentInstance: WorldLoader? = null
        
        /**
         * Register a callback to be executed when the world is loaded
         * 
         * @param callback The callback to execute
         */
        fun onWorldLoaded(callback: () -> Unit) {
            onWorldLoadedCallback = callback
        }
        
        /**
         * Execute the onWorldLoaded callback if it exists
         */
        private fun executeWorldLoadedCallback() {
            onWorldLoadedCallback?.invoke()
            // Clear the callback after execution
            onWorldLoadedCallback = null
        }

        /**
         * Notify the active scene that the world has been edited so lighting can update immediately.
         */
        fun notifyWorldEdited(world: World) {
            currentInstance?.applyWorldEditLighting(world)
        }

        /** Register or clear the current WorldLoader instance */
        fun registerCurrentInstance(loader: WorldLoader?) {
            currentInstance = loader
        }

        /**
         * Update the directional light color using a hex string.
         */
        fun updateDirectionalLightColorHex(hexColor: String) {
            try {
                val color = Color3.FromHexString(hexColor)
                val light = currentDirectionalLight
                if (light != null) {
                    light.diffuse = color
                    println("[DEBUG] Updated directional light color to $hexColor")
                } else {
                    println("[WARN] Directional light not initialized; cannot update color")
                }
            } catch (e: Throwable) {
                println("[ERROR] Failed to update directional light color: ${e.message}")
            }
        }

        /**
         * Update the hemispheric light color using a hex string.
         */
        fun updateHemisphericLightColorHex(hexColor: String) {
            try {
                val color = Color3.FromHexString(hexColor)
                updateHemisphericLightColor(color)
            } catch (e: Throwable) {
                println("[ERROR] Failed to update hemispheric light color: ${e.message}")
            }
        }

        /**
         * Update the hemispheric light color using Color3.
         * Ground color is set to a darker variant for contrast.
         */
        fun updateHemisphericLightColor(color: Color3) {
            val light = currentHemisphericLight
            if (light != null) {
                light.diffuse = color
                val ground = color.scale(0.5f)
                light.groundColor = ground
                println("[DEBUG] Updated hemispheric light color to diffuse=$color, ground=$ground")
            } else {
                println("[WARN] Hemispheric light not initialized; cannot update color")
            }
        }

        /**
         * Internal: register current lights so they can be updated globally.
         */
        fun setCurrentLights(hemispheric: HemisphericLight?, directional: DirectionalLight?) {
            currentHemisphericLight = hemispheric
            currentDirectionalLight = directional
        }

        /**
         * Clear current light references.
         */
        fun clearCurrentLights() {
            currentHemisphericLight = null
            currentDirectionalLight = null
        }
    }
    
    // World meshes for collision detection and interaction
    private var worldMeshes: Array<AbstractMesh> = emptyArray()
    
    // Current world data
    private var currentWorld: World? = null
    
    // Flag to indicate if a fallback world was created due to an API error
    private var isApiErrorFallback: Boolean = false
    
    // Lights for the scene
    private var hemisphericLight: HemisphericLight? = null
    private var directionalLight: DirectionalLight? = null
    
    // Shadow manager for global shadow handling
    private var shadowManager: ShadowManager? = null
    
    // Occlusion manager for fading objects that obstruct the player's view
    private var occlusionManager: OcclusionManager? = null

    // Interval ID for realtime sun updates
    private var sunIntervalId: Int? = null
    
    /**
     * Sets up lighting for the scene.
     * Creates a hemispheric light for ambient lighting and a directional light for shadows.
     * Initializes the shadow manager for global shadow handling.
     */
    private fun setupLighting() {
        println("[DEBUG] Setting up scene lighting")
        // Register this instance for edit notifications
        WorldLoader.registerCurrentInstance(this)
        
        // Add a hemispheric light for ambient lighting
        hemisphericLight = HemisphericLight(
            name = "hemisphericLight",
            direction = Vector3(0f, 1f, 0f),
            scene = scene
        ).apply {
            this.intensity = 0.5f  // Further reduced intensity to make directional light shadows more visible
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
        
        // Initialize the occlusion manager for fading objects that obstruct the player's view
        occlusionManager = OcclusionManager(scene)

        // Register current lights for global updates
        WorldLoader.setCurrentLights(hemisphericLight, directionalLight)
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

            // Set the scene fog density from the world model (applies in both modes)
            setSceneFogDensity(world.fogDensity)

            // If outdoors with valid lat/lng, enable realtime sun and ignore background/directional colors
            val lat = world.latLng?.getOrNull(0)
            val lon = world.latLng?.getOrNull(1)
            if (world.isOutdoors && lat != null && lon != null) {
                println("[INFO] Outdoors mode with realtime sun enabled (lat=$lat, lon=$lon)")
                startRealtimeSun(lat, lon)
            } else {
                // Indoor or invalid lat/lng: preserve existing behavior
                setSceneBackgroundColor(world.backgroundColor)
                WorldLoader.updateDirectionalLightColorHex(
                    hexColor = world.directionalLightColor
                )
            }

            // Check if world file is specified
            if (world.file.isEmpty()) {
                // Create a fallback 10x10 plane
                println("[INFO] No world file specified, creating fallback 10x10 plane")
                return createFallbackWorld()
            }

            // Determine the file to load
            val worldFile = "${world.file}.glb"
            
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
                        
                        // Execute the world loaded callback
                        executeWorldLoadedCallback()
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
        } catch (error: Throwable) {
            // Log the error but don't re-throw it
            console.error("[WORLD ERROR]", error)
            println("[ERROR] Failed to load world: ${error.message}")

            // Set the API error fallback flag
            isApiErrorFallback = (error as? ErrorResponse)?.code == 404

            // Create a fallback world due to API error
            return createFallbackWorld()
        }
    }
    
    /**
     * Creates a fallback world with a single 10x10 plane.
     * 
     * @param isApiError Whether this fallback world is being created due to an API error
     * @return An array containing the fallback world meshes
     */
    private fun createFallbackWorld(): Array<AbstractMesh> {
        println("[INFO] Creating fallback world with a 10x10 plane")
        
        // Create a 10x10 plane
        val plane = MeshBuilder.CreatePlane(
            name = "fallbackPlane",
            options = object : CreatePlaneOptions {
                override val width = 10.0f
                override val height = 10.0f
            },
            scene = scene
        )
        
        // Position the plane at the origin, flat on the ground
        plane.position = Vector3(0f, 0f, 0f)
        plane.rotation = Vector3(PI.toFloat() / 2, 0f, 0f)
        
        // Create a material for the plane
        val material = PBRMaterial("fallbackPlaneMaterial", scene).apply {
            metallic = 0.0f
            roughness = 1.0f
            albedoColor = Color3(0.5f, 0.5f, 0.5f)
        }
        
        // Apply the material to the plane
        plane.material = material
        
        // Store the plane as the world mesh
        worldMeshes = arrayOf(plane)
        
        // Configure shadows for the plane
        shadowManager?.configureShadowReceiver(plane)
        
        // Execute the world loaded callback
        executeWorldLoadedCallback()
        
        return worldMeshes
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
        var shadowCasterCount = 0
        var shadowReceiverCount = 0
        
        meshes.forEach { mesh ->
            // Skip if mesh is not visible or is a helper object
            if (!mesh.isVisible || mesh.name.startsWith("__")) {
                println("[DEBUG] Skipping mesh: ${mesh.name} (invisible or helper)")
                return@forEach
            }

            // Check for skybox custom property and set infinite distance
            if (mesh.isSkybox()) {
                mesh.infiniteDistance = true
                mesh.material.fogEnabled = false
                mesh.material.backFaceCulling = true
                mesh.material.disableLighting = true
                (mesh.material as? PBRMaterial)?.let { mat: PBRMaterial ->
                    mat.emissiveTexture = mat.albedoTexture
                    mat.emissiveIntensity = 1f
                    mat.emissiveColor = Color3.White()
                }
                console.log("[MESH] Set infinite distance for skybox mesh: ${mesh.name}")
                console.log("[MESH]", mesh.parent)
            }

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
            
            // Register mesh with occlusion manager for fading when it obstructs the player's view
            if (!mesh.isSkybox()) {
                occlusionManager?.registerMesh(mesh)
            }
        }
        
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
     * Gets the occlusion manager for fading objects that obstruct the player's view.
     * 
     * @return The occlusion manager, or null if not initialized
     */
    fun getOcclusionManager(): OcclusionManager? {
        return occlusionManager
    }
    
    /**
     * Checks if a fallback world was created due to an API error.
     * 
     * @return True if a fallback world was created due to an API error, false otherwise
     */
    fun isApiErrorFallback(): Boolean {
        return isApiErrorFallback
    }
    
    /**
     * Sets the scene background color from a hex color string.
     * 
     * @param hexColor The hex color string (e.g., "#FF0000" for red)
     */
    private fun setSceneBackgroundColor(hexColor: String) {
        try {
            // Convert hex color string to Color3
            val color3 = Color3.FromHexString(hexColor)
            
            // Convert Color3 to Color4 with alpha=1 (fully opaque)
            val color4 = color3.toColor4()
            color4.a = 1.0f
            
            // Set the scene's clear color
            scene.clearColor = color4
            
            // Sync hemispheric light color to match background
            WorldLoader.updateHemisphericLightColor(color3)
            
            // If fog is enabled (fogDensity > 0), update the fog color to match the background color
            if (scene.fogDensity > 0.0f) {
                scene.fogColor = color3
                println("[DEBUG] Set scene background color to $hexColor and synced fog color")
            } else {
                println("[DEBUG] Set scene background color to $hexColor")
            }
        } catch (e: Throwable) {
            println("[ERROR] Failed to set background color: ${e.message}")
        }
    }
    
    /**
     * Sets the scene fog density.
     * 
     * @param density The fog density value (0.0 to 1.0)
     */
    private fun setSceneFogDensity(density: Float) {
        try {
            // Get the rendering pipeline from the CameraController
            val cameraController = SceneManager.getCameraController()
            val pipeline = cameraController?.getPipeline()
            
            if (density > 0.0f) {
                // Enable fog in the scene
                scene.fogMode = Scene.Companion.FOGMODE_EXP2
                
                // Scale the density for better visual effect (0.0-1.0 to 0.0-0.1)
                scene.fogDensity = density * 0.1f
                
                // Set fog color to match background color
                val clearColor = scene.clearColor
                scene.fogColor = Color3(clearColor.r, clearColor.g, clearColor.b)
                
                // Enable fog in the rendering pipeline
                pipeline?.fogEnabled = true
                
                println("[DEBUG] Set scene fog density to $density and enabled fog in pipeline")
            } else {
                // Disable fog by setting density to 0
                scene.fogDensity = 0.0f
                
                // Disable fog in the rendering pipeline
                pipeline?.fogEnabled = false
                
                println("[DEBUG] Disabled scene fog and disabled fog in pipeline")
            }
        } catch (e: Throwable) {
            println("[ERROR] Failed to set fog density: ${e.message}")
        }
    }

    // Applies lighting changes immediately when the world is edited
    private fun applyWorldEditLighting(world: World) {
        try {
            currentWorld = world
            // Always ensure fog matches edited value
            setSceneFogDensity(world.fogDensity)

            val lat = world.latLng?.getOrNull(0)
            val lon = world.latLng?.getOrNull(1)

            if (world.isOutdoors && lat != null && lon != null) {
                // Start or restart realtime sun (applies immediately)
                startRealtimeSun(lat, lon)
            } else {
                // Stop realtime sun updates if running
                sunIntervalId?.let { id ->
                    try { window.clearInterval(id) } catch (_: Throwable) {}
                    sunIntervalId = null
                }
                // Apply indoor lighting immediately
                setSceneBackgroundColor(world.backgroundColor)
                WorldLoader.updateDirectionalLightColorHex(world.directionalLightColor)
            }
        } catch (e: Throwable) {
            println("[ERROR] Failed to apply edited world lighting: ${'$'}{e.message}")
        }
    }
    
    // --- Realtime sun lighting for outdoors worlds ---
    private fun startRealtimeSun(lat: Double, lon: Double) {
        // Clear previous interval if any
        sunIntervalId?.let { id ->
            try { window.clearInterval(id) } catch (_: Throwable) {}
            sunIntervalId = null
        }
        // Apply immediately and then every minute
        applyRealtimeSun(lat, lon)
        sunIntervalId = window.setInterval({ applyRealtimeSun(lat, lon) }, 60_000)
    }

    private fun applyRealtimeSun(lat: Double, lon: Double) {
        val d = kotlin.js.Date()
        val sun = computeSunPosition(d, lat, lon)
        val alt = sun.altitudeRad
        val azi = sun.azimuthRad

        val dir = directionalLight
        val hemi = hemisphericLight

        // Night if sun below horizon
        val isNight = alt < (-0.833 * PI / 180.0)

        // Colors
        val daySky = Color3(0.53f, 0.81f, 0.98f)
        val sunsetSky = Color3(0.95f, 0.50f, 0.20f)
        val nightSky = Color3(0.02f, 0.02f, 0.05f)

        val daySun = Color3(1.00f, 0.98f, 0.90f)
        val sunriseSun = Color3(1.00f, 0.65f, 0.30f)
        val nightHemi = Color3(0.05f, 0.07f, 0.10f)

        val altDeg = (alt * 180.0 / PI).toFloat()
        val t = ((altDeg + 6f) / 36f).coerceIn(0f, 1f) // -6..30 maps to 0..1

        // Compute sky and sun colors based on altitude
        val skyColor = if (isNight) nightSky else lerpColor(sunsetSky, daySky, t)
        val sunColor = if (isNight) sunriseSun else lerpColor(sunriseSun, daySun, t)

        // Update scene background color (ignore world background in outdoors mode)
        try {
            val color4 = skyColor.toColor4().also { it.a = 1.0f }
            scene.clearColor = color4
            if (scene.fogDensity > 0.0f) {
                scene.fogColor = skyColor
            }
        } catch (e: Throwable) {
            println("[ERROR] Failed to set outdoors background color: ${'$'}{e.message}")
        }

        // Update hemispheric light
        try {
            if (hemi != null) {
                val hemiIntensity = if (isNight) 0.25f else 0.5f
                hemi.intensity = hemiIntensity
                if (isNight) {
                    updateHemisphericLightColor(nightHemi)
                } else {
                    updateHemisphericLightColor(skyColor)
                }
            }
        } catch (e: Throwable) {
            println("[ERROR] Failed to update hemispheric light: ${'$'}{e.message}")
        }

        // Update directional light
        try {
            if (dir != null) {
                if (isNight) {
                    dir.intensity = 0.0f
                } else {
                    // Calculate direction vector from azimuth/altitude
                    val x = sin(azi).toFloat() * cos(alt).toFloat()
                    val y = -sin(alt).toFloat()
                    val z = cos(azi).toFloat() * cos(alt).toFloat()
                    dir.direction = Vector3(x, y, z)

                    // Intensity scales with altitude
                    val intensity = (0.3f + 0.7f * t)
                    dir.intensity = intensity

                    // Color transitions during sunrise/sunset
                    dir.diffuse = sunColor
                }
            }
        } catch (e: Throwable) {
            println("[ERROR] Failed to update directional light: ${'$'}{e.message}")
        }
    }

    private data class SunPosition(val altitudeRad: Double, val azimuthRad: Double)

    private fun computeSunPosition(date: kotlin.js.Date, latDeg: Double, lonDeg: Double): SunPosition {
        // Day of year
        val startOfYear = kotlin.js.Date(date.getFullYear(), 0, 0)
        val day = floor((date.getTime() - startOfYear.getTime()) / 86_400_000.0) + 1

        val hour = date.getHours()
        val minute = date.getMinutes()
        val second = date.getSeconds()

        // Fractional year (radians)
        val gamma = 2 * PI / 365 * (day - 1 + ((hour - 12) / 24.0) + (minute / 1440.0) + (second / 86400.0))

        // Equation of time (minutes)
        val eqTime = 229.18 * (
            0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
            0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma)
        )

        // Solar declination (radians)
        val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
                0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
                0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)

        // Time offset (minutes)
        val timeZone = -date.getTimezoneOffset() / 60.0 // hours east of UTC
        val timeOffset = eqTime + 4.0 * lonDeg - 60.0 * timeZone

        // True solar time (minutes)
        val tst = (hour * 60.0) + minute + (second / 60.0) + timeOffset

        // Hour angle (radians)
        val ha = ((tst / 4.0) - 180.0) * PI / 180.0

        // Convert to radians
        val lat = latDeg * PI / 180.0

        // Solar altitude
        val sinAlt = sin(lat) * sin(decl) + cos(lat) * cos(decl) * cos(ha)
        val alt = asin(sinAlt)

        // Solar azimuth (from North, eastward) in range [0, 2π)
        val y = sin(ha)
        val x = cos(ha) * sin(lat) - tan(decl) * cos(lat)
        var az = atan2(y, x)
        az += PI // convert from [-π, π] to [0, 2π]
        if (az < 0) az += 2 * PI
        if (az >= 2 * PI) az -= 2 * PI

        return SunPosition(altitudeRad = alt, azimuthRad = az)
    }

    private fun lerpColor(a: Color3, b: Color3, t: Float): Color3 {
        val clamped = t.coerceIn(0f, 1f)
        val ac = a.toColor4()
        val bc = b.toColor4()
        val r = ac.r + (bc.r - ac.r) * clamped
        val g = ac.g + (bc.g - ac.g) * clamped
        val bl = ac.b + (bc.b - ac.b) * clamped
        return Color3(r, g, bl)
    }

    /**
     * Disposes of the world loader resources.
     * Cleans up lights, shadow manager, occlusion manager, and disconnects from WebSocket.
     */
    fun dispose() {
        // Stop realtime sun updates if running
        sunIntervalId?.let { id ->
            try { window.clearInterval(id) } catch (_: Throwable) {}
            sunIntervalId = null
        }
        
        // Dispose of shadow manager
        shadowManager?.dispose()
        shadowManager = null
        
        // Dispose of occlusion manager
        occlusionManager?.dispose()
        occlusionManager = null
        
        // Set lights to null to allow garbage collection
        hemisphericLight = null
        directionalLight = null
        
        // Clear global references to lights
        WorldLoader.clearCurrentLights()
        // Clear current instance reference
        WorldLoader.registerCurrentInstance(null)
        
        // Disconnect from WebSocket
        WebSocketApi.disconnect()
    }
}

fun AbstractMesh.isSkybox(): Boolean = metadata?.gltf?.extras?.skybox as? Boolean == true
