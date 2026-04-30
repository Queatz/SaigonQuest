package main.components.world

import io.ktor.client.plugins.ResponseException
import Translations.str
import kotlinx.coroutines.suspendCancellableCoroutine
import lib.AbstractMesh
import lib.Color3
import lib.CreateIcoSphereOptions
import lib.CreatePlaneOptions
import lib.DirectionalLight
import lib.HemisphericLight
import lib.IShadowLight
import lib.Mesh
import lib.MeshBuilder
import lib.PBRMaterial
import lib.Scene
import lib.SceneLoader
import lib.ShadowGenerator
import lib.Texture
import lib.Vector3
import main.api.UserApi
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
import lib.Light
import lib.Node
import models.ErrorResponse
import main.components.player.FrameDeltaTracker
import kotlin.random.Random

/**
 * Handles loading and initialization of the 3D world.
 * 
 * This class encapsulates all world-related functionality including:
 * - Loading world data from the API
 * - Loading 3D models
 * - Applying materials to world meshes
 */
class WorldLoader(private val scene: Scene) {
    
    private fun reloadWorldFileInternal(world: World) {
        try {
            // Update current world reference and outdoors flag
            currentWorld = world
            currentWorldIsOutdoors = world.isOutdoors

            // Dispose previous world meshes and cameras if any
            try {
                for (m in worldMeshes) {
                    try { m.dispose() } catch (_: Throwable) {}
                }
                // Dispose all cameras except our main camera to avoid leaks and confusion
                scene.cameras.filter { it.name != "camera" }.forEach { 
                    try { it.dispose() } catch (_: Throwable) {}
                }
                // Dispose all lights except our core lights to avoid leaks
                scene.lights.filter { it.name != "hemisphericLight" && it.name != "directionalLight" && it.name != "moonPhaseLight" }.forEach { 
                    try { it.dispose() } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
            worldMeshes = emptyArray()
            waterTextures.clear()
            waterDeltaTracker.reset()
            flickeringLights.clear()
            flickerDeltaTracker.reset()

            if (world.file.isBlank()) {
                console.log("[INFO] No world file specified after save; creating fallback plane")
                worldMeshes = createFallbackWorld()
                // Propagate to controllers
                SceneManager.getPlayerController()?.setWorldMeshes(worldMeshes)
                SceneManager.getPlayerController()?.setStartNodes(emptyArray())
                SceneManager.getObjectManager()?.setWorldMeshes(worldMeshes)
                SceneManager.getOtherPlayersCoordinator()?.setWorldMeshes(worldMeshes)
                // Apply lighting state
                applyWorldEditLighting(world)
                return
            }

            val sceneFilename = "${world.file}.glb"
            console.log("[DEBUG] Importing world asset '$sceneFilename' into scene. Current cameras: ${scene.cameras.size}")
            
            // Import new world asset
            SceneLoader.LoadAssetContainer(
                rootUrl = "/raw/data/",
                sceneFilename = sceneFilename,
                scene = scene,
                onSuccess = { container ->
                    try {
                        console.log("[DEBUG] World asset container load success. Meshes: ${container.meshes.size}, Nodes: ${container.transformNodes.size}, Cameras: ${container.cameras.size}")
                        container.cameras.forEach { cam ->
                            console.log("[DEBUG]   Camera in container: ${cam.name}")
                        }
                        
                        container.addAllToScene()
                        
                        // Play all animations in the container (e.g., for water or other animated world elements)
                        container.animationGroups.forEach { group ->
                            try { group.play(true) } catch (_: Throwable) {}
                        }
                        
                        val meshes = container.meshes
                        val nodes = container.transformNodes
                        console.log("[DEBUG] After addAllToScene - Total Scene Cameras: ${scene.cameras.size}")
                        scene.cameras.forEach { cam ->
                            console.log("[DEBUG]   Scene camera: ${cam.name}")
                        }
                        
                        worldMeshes = meshes
                        transformNodes = nodes
                        applyMaterials(meshes)
                        // Propagate to controllers
                        SceneManager.getPlayerController()?.setWorldMeshes(meshes)
                        SceneManager.getPlayerController()?.setStartNodes(nodes)
                        SceneManager.getObjectManager()?.setWorldMeshes(meshes)
                        SceneManager.getOtherPlayersCoordinator()?.setWorldMeshes(meshes)
                        
                        // Re-initialize proximity cameras after asset import
                        SceneManager.getCameraController()?.initializeProximityCameras()
                        
                        // Apply edited lighting immediately
                        applyWorldEditLighting(world)
                        console.log("[DEBUG] Reloaded world asset '$sceneFilename' successfully")
                    } catch (e: Throwable) {
                        console.error("[ERROR] Failed during world reload success handling: ${e.message}")
                    }
                },
                onError = { _, message, exception ->
                    console.error("[ERROR] Error reloading world model: $message. $exception")
                    try {
                        // Attempt fallback world on error
                        worldMeshes = createFallbackWorld()
                        SceneManager.getPlayerController()?.setWorldMeshes(worldMeshes)
                        SceneManager.getPlayerController()?.setStartNodes(emptyArray())
                        SceneManager.getObjectManager()?.setWorldMeshes(worldMeshes)
                        SceneManager.getOtherPlayersCoordinator()?.setWorldMeshes(worldMeshes)
                        applyWorldEditLighting(world)
                    } catch (_: Throwable) {}
                }
            )
        } catch (e: Throwable) {
            console.error("[ERROR] Failed to reload world file: ${e.message}")
        }
    }
    
    companion object {
        // Callbacks to be executed when the world is loaded
        private val onWorldLoadedCallbacks: MutableList<() -> Unit> = mutableListOf()

        // References to the currently active lights for global updates
        private var currentHemisphericLight: HemisphericLight? = null
        private var currentDirectionalLight: DirectionalLight? = null

        // Whether the currently loaded world is outdoors
        private var currentWorldIsOutdoors: Boolean = false

        // Reference to the current WorldLoader instance for cross-component notifications
        private var currentInstance: WorldLoader? = null
        
        /**
         * Register a callback to be executed when the world is loaded
         * 
         * @param callback The callback to execute
         */
        fun onWorldLoaded(callback: () -> Unit) {
            onWorldLoadedCallbacks.add(callback)
        }
        
        /**
         * Execute all onWorldLoaded callbacks, if any
         */
        private fun executeWorldLoadedCallback() {
            if (onWorldLoadedCallbacks.isEmpty()) return
            val snapshot = onWorldLoadedCallbacks.toList()
            onWorldLoadedCallbacks.clear()
            for (cb in snapshot) {
                try {
                    cb.invoke()
                } catch (e: Throwable) {
                    console.error("[ERROR] onWorldLoaded callback failed: ${e.message}")
                }
            }
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

        /** Returns the current OcclusionManager if a world is active. */
        fun getCurrentOcclusionManager(): OcclusionManager? {
            return currentInstance?.getOcclusionManager()
        }

        /** Returns the current ActivePerformanceEngine if a world is active. */
        fun getCurrentPerformanceEngine(): ActivePerformanceEngine? {
            return currentInstance?.getPerformanceEngine()
        }

        /** Reload the currently loaded world's asset file and propagate to controllers. */
        fun reloadWorldFile(world: World) {
            val loader = currentInstance
            if (loader == null) {
                console.warn("[WARN] No current WorldLoader instance; cannot reload world file")
                return
            }
            loader.reloadWorldFileInternal(world)
        }

        /**
         * Update the directional light color using a hex string.
         */
        fun updateDirectionalLightColorHex(hexColor: String) {
            try {
                val color = Color3.FromHexString(hexColor).toLinearSpace()
                val light = currentDirectionalLight
                if (light != null) {
                    light.diffuse = color
                    console.log("[DEBUG] Updated directional light color to $hexColor")
                } else {
                    console.warn("[WARN] Directional light not initialized; cannot update color")
                }
            } catch (e: Throwable) {
                console.error("[ERROR] Failed to update directional light color: ${e.message}")
            }
        }

        /**
         * Update the directional light intensity.
         */
        fun updateDirectionalLightIntensity(intensity: Float) {
            try {
                val light = currentDirectionalLight
                if (light != null) {
                    light.intensity = intensity
                    console.log("[DEBUG] Updated directional light intensity to $intensity")
                }
            } catch (e: Throwable) {
                console.error("[ERROR] Failed to update directional light intensity: ${e.message}")
            }
        }

        /**
         * Update the hemispheric light intensity.
         */
        fun updateHemisphericLightIntensity(intensity: Float) {
            try {
                val light = currentHemisphericLight
                if (light != null) {
                    light.intensity = intensity
                    console.log("[DEBUG] Updated hemispheric light intensity to $intensity")
                }
            } catch (e: Throwable) {
                console.error("[ERROR] Failed to update hemispheric light intensity: ${e.message}")
            }
        }

        /**
         * Update the hemispheric light color using a hex string.
         */
        fun updateHemisphericLightColorHex(hexColor: String) {
            try {
                val color = Color3.FromHexString(hexColor).toLinearSpace()
                updateHemisphericLightColor(color)
            } catch (e: Throwable) {
                console.error("[ERROR] Failed to update hemispheric light color: ${e.message}")
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
                console.log("[DEBUG] Updated hemispheric light color to diffuse=$color, ground=$ground")
            } else {
                console.warn("[WARN] Hemispheric light not initialized; cannot update color")
            }
        }

        /** Returns true if the active world is marked as outdoors. */
        fun isOutdoorsActive(): Boolean = currentWorldIsOutdoors

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

        /**
         * Reapply the currently active world's lighting to the scene.
         * Useful for restoring state after temporary UI previews.
         */
        fun reapplyCurrentWorld() {
            try {
                val loader = currentInstance
                if (loader == null) {
                    console.warn("[WARN] No current WorldLoader instance; cannot reapply world")
                    return
                }
                val world = loader.getCurrentWorld()
                if (world == null) {
                    console.warn("[WARN] No current world available to reapply")
                    return
                }
                loader.applyWorldEditLighting(world)
                console.log("[DEBUG] Reapplied current world lighting")
            } catch (e: Throwable) {
                console.error("[ERROR] Failed to reapply current world: ${e.message}")
            }
        }

        /**
         * Returns the currently active world from the active WorldLoader instance, if any.
         */
        fun getActiveWorld(): World? {
            val loader = currentInstance
            return loader?.getCurrentWorld()
        }
    }
    
    // World meshes for collision detection and interaction
    private var worldMeshes: Array<AbstractMesh> = emptyArray()
    private var transformNodes: Array<Node> = emptyArray()

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

    // Performance engine for culling small objects
    private var performanceEngine: ActivePerformanceEngine? = null

    // Distance culling system for massive meshes
    private var distanceCullingSystem: DistanceCullingSystem? = null

    // List of water textures to animate
    private val waterTextures = mutableListOf<Texture>()

    // Helper to compute frame delta time
    private val waterDeltaTracker = FrameDeltaTracker()

    // The animation callback for water
    private val waterAnimationCallback = {
        val dt = waterDeltaTracker.nextDeltaSeconds()
        if (dt > 0.0 && waterTextures.isNotEmpty()) {
            waterTextures.forEach { texture ->
                // Move 5% of the texture per second
//                texture.uOffset += (0.05f * dt).toFloat()
                texture.vOffset += (0.125f * dt).toFloat()
            }
        }
    }

    // Realtime sun mesh and material
    private var sunMesh: Mesh? = null

    // Realtime moon mesh and light
    private var moonMesh: Mesh? = null
    private var moonLight: DirectionalLight? = null

    // Realtime stars skybox mesh
    private var starsMesh: Mesh? = null

    // Interval ID for realtime sun/moon updates
    private var sunIntervalId: Int? = null

    // List of point lights to flicker
    private val flickeringLights = mutableListOf<FlickerData>()

    // Helper to compute frame delta time for flickering
    private val flickerDeltaTracker = FrameDeltaTracker()

    // Total elapsed time for flicker calculations
    private var flickerTime = 0.0

    // The animation callback for flickering lights
    private val flickerAnimationCallback = {
        val dt = flickerDeltaTracker.nextDeltaSeconds()
        flickerTime += dt

        if (dt > 0.0 && flickeringLights.isNotEmpty()) {
            flickeringLights.forEach { data ->
                val time = flickerTime + data.seed
                
                // Intensity flicker: 80% to 120% of original intensity
                // Use multiple sines for more natural look
                val intensityVar = (sin(time * 15.0) * 0.1 + sin(time * 22.0) * 0.05 + sin(time * 7.0) * 0.05).toFloat()
                data.light.intensity = data.originalIntensity * (1.0f + intensityVar)
                
                // Position flicker: small random offset (approx +/- 0.07 units)
                data.light.position.x = data.originalPosition.x + (sin(time * 12.0) * 0.05 + cos(time * 18.0) * 0.02).toFloat()
                data.light.position.y = data.originalPosition.y + (sin(time * 10.0) * 0.05 + sin(time * 25.0) * 0.02).toFloat()
                data.light.position.z = data.originalPosition.z + (cos(time * 14.0) * 0.05 + sin(time * 20.0) * 0.02).toFloat()
            }
        }
    }
    
    /**
     * Sets up lighting for the scene.
     * Creates a hemispheric light for ambient lighting and a directional light for shadows.
     * Initializes the shadow manager for global shadow handling.
     */
    private fun setupLighting() {
        console.log("[DEBUG] Setting up scene lighting")
        // Register this instance for edit notifications
        registerCurrentInstance(this)
        
        // Enable clustered lighting features for BabylonJS 9 performance
        // This allows handling many lights and shadows efficiently in a single pass
        scene.useClusteredForwardLighting = true
        scene.useClusteredShadows = true
        
        // Add a hemispheric light for ambient lighting
        hemisphericLight = HemisphericLight(
            name = "hemisphericLight",
            direction = Vector3(0f, 1f, 0f),
            scene = scene
        ).apply {
            this.intensity = 1f  // Further reduced intensity to make directional light shadows more visible
            console.log("[DEBUG] Created hemispheric light with intensity ${this.intensity}")
        }

        // Add a directional light for shadows
        directionalLight = DirectionalLight(
            name = "directionalLight",
            // Direction will be set by ShadowManager
            direction = Vector3(0f, -1f, 0f),
            scene = scene
        )
        
        console.log("[DEBUG] Created directional light (settings will be configured by ShadowManager)")
        
        // Initialize the shadow manager with the directional light
        // ShadowManager will configure the light for optimal shadow casting
        directionalLight?.let { light ->
            shadowManager = ShadowManager(scene, light)
            console.log("[DEBUG] Shadow manager initialized")
        }
        
        // Initialize the occlusion manager for fading objects that obstruct the player's view
        occlusionManager = OcclusionManager(scene)
        performanceEngine = ActivePerformanceEngine(scene)
        distanceCullingSystem = DistanceCullingSystem(scene)

        // Register current lights for global updates
        setCurrentLights(hemisphericLight, directionalLight)

        // Register the water animation callback
        scene.registerBeforeRender(waterAnimationCallback)

        // Register the flicker animation callback
        scene.registerBeforeRender(flickerAnimationCallback)
    }
    
    /**
     * Loads a world by its URL.
     * 
     * @param url The URL of the world to load
     * @return The loaded world meshes and start nodes
     */
    suspend fun loadWorld(
        url: String,
        onInfoLoaded: (World) -> Unit = {},
    ): Pair<Array<AbstractMesh>, Array<Node>> {
        // Set up lighting for the scene
        setupLighting()

        try {
            // First load the world data from the API
            var world = try {
                WorldApi.getWorldByUrl(url)
            } catch (error: Throwable) {
                // If unauthorized, attempt to refresh session and retry once
                val isUnauthorized = when (error) {
                    is ErrorResponse -> error.code == 401
                    is ResponseException -> error.response.status.value == 401
                    else -> false
                }

                if (isUnauthorized) {
                    console.log("[INFO] Unauthorized world load, refreshing session...")
                    try {
                        UserApi.getCurrentUser(forceReload = true)
                        WorldApi.getWorldByUrl(url)
                    } catch (retryError: Throwable) {
                        throw retryError
                    }
                } else {
                    throw error
                }
            }
            
            // Store the world data
            currentWorld = world
            onInfoLoaded(world)
            // Expose outdoors flag for global systems
            currentWorldIsOutdoors = world.isOutdoors

            // Set the scene fog density from the world model (applies in both modes)
            setSceneFogDensity(world.fogDensity)

            // Configure world music
            try {
                main.components.audio.MusicManager.setWorldMusic(
                    dayUrl = world.musicUrl,
                    nightUrl = world.nightMusicUrl,
                    isOutdoors = world.isOutdoors,
                )
            } catch (t: Throwable) {
                console.error("[WorldLoader] Failed to configure world music: ${t.message}")
            }

            // If outdoors with valid lat/lng, enable realtime sun and ignore background/directional colors
            val lat = world.latLng?.getOrNull(0)
            val lon = world.latLng?.getOrNull(1)
            if (world.isOutdoors && lat != null && lon != null) {
                console.log("[INFO] Outdoors mode with realtime sun enabled (lat=$lat, lon=$lon)")
                startRealtimeSun(lat, lon)
            } else {
                // Indoor or invalid lat/lng: preserve existing behavior
                setSceneBackgroundColor(world.backgroundColor)
                updateHemisphericLightIntensity(world.backgroundColorIntensity)
                updateDirectionalLightColorHex(
                    hexColor = world.directionalLightColor
                )
                updateDirectionalLightIntensity(world.directionalLightIntensity)
            }

            // Check if world file is specified
            if (world.file.isEmpty()) {
                // Create a fallback 10x10 plane
                console.log("[INFO] No world file specified, creating fallback 10x10 plane")
                return createFallbackWorld() to emptyArray()
            }

            // Determine the file to load
            val worldFile = "${world.file}.glb"
            console.log("[DEBUG] Loading world file: $worldFile. Initial scene cameras: ${scene.cameras.size}")
            
            // Load the 3D model using suspendCancellableCoroutine to convert callback to coroutine
            return suspendCancellableCoroutine { continuation ->
                SceneLoader.LoadAssetContainer(
                    rootUrl = "/raw/data/",
                    sceneFilename = worldFile,
                    scene = scene,
                    onSuccess = { container ->
                        console.log("[DEBUG] Scene container load success. Meshes: ${container.meshes.size}, Nodes: ${container.transformNodes.size}, Cameras: ${container.cameras.size}")
                        container.cameras.forEach { cam ->
                            console.log("[DEBUG]   Camera in container: ${cam.name}")
                        }

                        container.addAllToScene()
                        
                        // Play all animations in the container (e.g., for water or other animated world elements)
                        container.animationGroups.forEach { group ->
                            try { group.play(true) } catch (_: Throwable) {}
                        }
                        
                        val meshes = container.meshes
                        val nodes = container.transformNodes
                        console.log("[DEBUG] After addAllToScene - Total Scene Cameras: ${scene.cameras.size}")
                        scene.cameras.forEach { cam ->
                            console.log("[DEBUG]   Scene camera: ${cam.name}")
                        }

                        // Store world meshes for collision detection
                        worldMeshes = meshes
                        transformNodes = nodes

                        // Apply PBR materials to all meshes
                        applyMaterials(meshes)
                        
                        // Resume the coroutine with the loaded meshes and start nodes
                        continuation.resume(meshes to nodes)
                        
                        // Execute the world loaded callback
                        executeWorldLoadedCallback()
                    },
                    onError = { _, message, exception ->
                        console.error("[WORLD ERROR]: $message", exception)
                        // Resume the coroutine with an exception
                        continuation.resumeWithException(Throwable(str("Oh no! Failed to load world.")))
                    }
                )
                
                // Register cancellation handler
                continuation.invokeOnCancellation {
                    // Clean up resources if needed when coroutine is cancelled
                    console.log("[DEBUG] World loading cancelled")
                }
            }
        } catch (error: Throwable) {
            // Log the error; for 404 return fallback, otherwise rethrow so UI can show precise message
            console.error("[WORLD ERROR]", error)
            console.error("[ERROR] Failed to load world: ${error.message}")

            val apiCode = when (error) {
                is ErrorResponse -> error.code
                is ResponseException -> error.response.status.value
                else -> null
            }
            // Only treat 404 as API fallback (claimable). Propagate others (e.g., 403 access denied).
            isApiErrorFallback = apiCode == 404
            return if (apiCode == 404) {
                createFallbackWorld() to emptyArray()
            } else {
                throw error
            }
        }
    }
    
    /**
     * Creates a fallback world with a single 10x10 plane.
     * 
     * @param isApiError Whether this fallback world is being created due to an API error
     * @return An array containing the fallback world meshes
     */
    private fun createFallbackWorld(): Array<AbstractMesh> {
        console.log("[INFO] Creating fallback world with a 10x10 plane")
        
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
        plane.isPickable = true
        
        // Store the plane as the world mesh
        worldMeshes = arrayOf(plane)
        
        // Configure shadows for the plane
        shadowManager?.configureShadowReceiver(plane)
        
        // Propagate world meshes to controllers so they can react immediately (e.g., snap player to ground)
        try {
            SceneManager.getPlayerController()?.setWorldMeshes(worldMeshes)
            SceneManager.getPlayerController()?.setStartNodes(emptyArray())
            SceneManager.getObjectManager()?.setWorldMeshes(worldMeshes)
            SceneManager.getOtherPlayersCoordinator()?.setWorldMeshes(worldMeshes)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        
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
        console.log("[DEBUG] Applying materials and configuring shadows for ${meshes.size} meshes")
        waterTextures.clear()
        
        // Log information about each mesh for debugging
        console.log("[DEBUG] Mesh list from .glb file:")
        meshes.forEachIndexed { index, mesh ->
            console.log("[DEBUG] Mesh #$index: name=${mesh.name}, visible=${mesh.isVisible}, position=${mesh.position}, scaling=${mesh.scaling}")
        }
        
        // Count for logging
        var shadowCasterCount = 0
        var shadowReceiverCount = 0

        // Convert all materials to linear space
        meshes.map { it.material }.distinct().forEach { material ->
            (material as? PBRMaterial)?.apply {
                useAlphaFromAlbedoTexture = true
            }
        }
        
        meshes.forEach { mesh ->
            // Skip if mesh is not visible or is a helper object
            if (!mesh.isVisible || mesh.name.startsWith("__")) {
                console.log("[DEBUG] Skipping mesh: ${mesh.name} (invisible or helper)")
                return@forEach
            }

            // Handle billboard mode for meshes that should face the camera (e.g. grass, trees)
            if (mesh.isBillboard()) {
                setupAnchorBillboard(mesh, scene, useParent = false)
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
            } else {
                // Configure shadows for the mesh
                shadowManager?.let { manager ->
                    // Make ALL objects cast shadows (as per requirement)
                    manager.addShadowCaster(mesh)
                    shadowCasterCount++

                    // Make all objects receive shadows
                    manager.configureShadowReceiver(mesh)
                    shadowReceiverCount++
                }

                // Apply distance culling to massive meshes (like flower maps)
                // This allows them to be "partially visible" by culling far fragments
                // We exclude incullable meshes from distance culling as they should always be fully visible.
                if (mesh.getBoundingInfo().boundingSphere.radiusWorld > 100f && !mesh.isIncullable()) {
                    distanceCullingSystem?.registerMesh(mesh)
                }

                // Also apply occlusion manager to all world meshes (except skybox)
                // The systems now use a shared MaterialCustomizer to avoid overwriting each other
                occlusionManager?.registerMesh(mesh)
            }

            // Collect water textures for animation
            if (mesh.isWater()) {
                (mesh.material as? PBRMaterial)?.let { pbr ->
                    pbr.albedoTexture?.let { if (!waterTextures.contains(it)) waterTextures.add(it) }
                    pbr.bumpTexture?.let { if (!waterTextures.contains(it)) waterTextures.add(it) }
                }
            }
        }
        
        console.log("[DEBUG] Configured $shadowCasterCount shadow casters and $shadowReceiverCount shadow receivers")
        
        // Configure shadows for point lights loaded from the GLB
        configurePointLightShadows(meshes)

        // Final shadow configuration check
        if (shadowCasterCount == 0) {
            console.warn("[WARNING] No shadow casters configured! Shadows will not be visible.")
        }
        if (shadowReceiverCount == 0) {
            console.warn("[WARNING] No shadow receivers configured! Shadows will not be visible.")
        }

        // Register all world meshes with the performance engine
        performanceEngine?.setWorldMeshes(meshes)
        
        // Log directional light status
        directionalLight?.let { light ->
            console.log("[DEBUG] Directional light status:")
            console.log("[DEBUG] - position: ${light.position}")
            console.log("[DEBUG] - direction: ${light.direction}")
            console.log("[DEBUG] - intensity: ${light.intensity}")
            console.log("[DEBUG] - shadowMinZ: ${light.shadowMinZ}")
            console.log("[DEBUG] - shadowMaxZ: ${light.shadowMaxZ}")
        } ?: run {
            console.error("[ERROR] Directional light is null! Shadows will not be visible.")
        }
    }
    
    /**
     * Detects point lights in the scene and configures clustered shadows for them.
     */
    private fun configurePointLightShadows(meshes: Array<AbstractMesh>) {
        // Clear previous point light shadow generators to avoid memory leaks
        shadowManager?.clearPointLightShadowGenerators()
        flickeringLights.clear()
        
        var pointShadowGenerator: ShadowGenerator? = null
        
        scene.lights.forEach { light ->
            // Skip core lights that should not be dimmed
            if (light.name == "hemisphericLight" || light.name == "directionalLight" || light.name == "moonPhaseLight") {
                return@forEach
            }

            // Dim imported lights by 1000x to match Blender brightness
            light.intensity = light.intensity / 1000f

            // getTypeID() == 0 is PointLight, 2 is SpotLight in BabylonJS
            if (light.getTypeID() == 0 || light.getTypeID() == 2) {
                val shadowLight = light as? IShadowLight
                if (shadowLight != null) {
                    console.log("[DEBUG] Found light from GLB: ${light.name} (type ${light.getTypeID()}). Configuring clustered shadows.")
                    
                    if (pointShadowGenerator == null) {
                        // Create a single shadow generator for all point/spot lights
                        // Map size 384 is generally enough for clustered point light shadows
                        val sg = ShadowGenerator(
                            mapSize = 384,
                            light = shadowLight
                        )
                        
                        // Enable clustered shadows for this generator
                        sg.useClusteredShadows = true
                        
                        // Register with shadow manager so it can add players/objects later
                        shadowManager?.addPointLightShadowGenerator(sg)
                        pointShadowGenerator = sg
                    } else {
                        // Add this light to the existing shadow generator
                        pointShadowGenerator.addLight(light = shadowLight)
                    }

                    // Check for flicker effect
                    if (light.isFlicker()) {
                        flickeringLights.add(
                            FlickerData(
                                light = light,
                                originalIntensity = light.intensity,
                                originalPosition = light.position.clone(),
                                seed = (Random.nextFloat() * 1000f)
                            )
                        )
                    }
                }
            }
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
     * Returns the current ActivePerformanceEngine if a world is active.
     */
    fun getPerformanceEngine(): ActivePerformanceEngine? {
        return performanceEngine
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
            scene.clearColor = color4.toLinearSpace()
            
            // Sync hemispheric light color to match background
            updateHemisphericLightColor(color3)

            scene.fogColor = color3
        } catch (e: Throwable) {
            console.error("[ERROR] Failed to set background color: ${e.message}")
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
                
                // Enable fog in the rendering pipeline
                pipeline?.fogEnabled = true
                
                console.log("[DEBUG] Set scene fog density to $density and enabled fog in pipeline")
            } else {
                // Disable fog by setting density to 0
                scene.fogDensity = 0.0f
                
                // Disable fog in the rendering pipeline
                pipeline?.fogEnabled = false
                
                console.log("[DEBUG] Disabled scene fog and disabled fog in pipeline")
            }
        } catch (e: Throwable) {
            console.error("[ERROR] Failed to set fog density: ${e.message}")
        }
    }

    // Applies lighting changes immediately when the world is edited
    private fun applyWorldEditLighting(world: World) {
        try {
            currentWorld = world
            // Update outdoors flag so global systems (e.g., wind) react immediately during edits
            currentWorldIsOutdoors = world.isOutdoors
            // Always ensure fog matches edited value
            setSceneFogDensity(world.fogDensity)

            // Update world music immediately during edits
            try {
                main.components.audio.MusicManager.setWorldMusic(
                    dayUrl = world.musicUrl,
                    nightUrl = world.nightMusicUrl,
                    isOutdoors = world.isOutdoors,
                )
            } catch (t: Throwable) {
                console.warn("[WorldLoader] Failed to update music during edit: ${t.message}")
            }

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
                sunMesh?.dispose()
                sunMesh = null
                moonMesh?.dispose()
                moonMesh = null
                starsMesh?.dispose()
                starsMesh = null
                moonLight?.dispose()
                moonLight = null
                // Apply indoor lighting immediately
                setSceneBackgroundColor(world.backgroundColor)
                updateHemisphericLightIntensity(world.backgroundColorIntensity)
                updateDirectionalLightColorHex(world.directionalLightColor)
                updateDirectionalLightIntensity(world.directionalLightIntensity)
            }
        } catch (e: Throwable) {
            console.error("[ERROR] Failed to apply edited world lighting: ${e.message}")
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
        val date = kotlin.js.Date()
        val sun = Astronomy.computeSunPosition(date, lat, lon)
        val moon = Astronomy.computeMoonPosition(date, lat, lon)
        
        val alt = sun.altitudeRad
        val azi = sun.azimuthRad

        val dir = directionalLight
        val hemi = hemisphericLight

        // Night if sun below horizon
        val isNight = alt < (-0.833 * PI / 180.0)
        try {
            main.components.audio.MusicManager.setIsNight(isNight)
        } catch (t: Throwable) {
            console.warn("[WorldLoader] Failed to update MusicManager night state: ${t.message}")
        }

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
            scene.clearColor = color4.toLinearSpace()
            scene.fogColor = skyColor
        } catch (e: Throwable) {
            console.error("[ERROR] Failed to set outdoors background color: ${e.message}")
        }

        // Update hemispheric light
        try {
            if (hemi != null) {
                val hemiIntensity = if (isNight) 2f else 1f
                hemi.intensity = hemiIntensity
                if (isNight) {
                    updateHemisphericLightColor(nightHemi)
                } else {
                    updateHemisphericLightColor(skyColor)
                }
            }
        } catch (e: Throwable) {
            console.error("[ERROR] Failed to update hemispheric light: ${e.message}")
        }

        // Update directional light
        val x = sin(azi).toFloat() * cos(alt).toFloat()
        val y = -sin(alt).toFloat()
        val z = cos(azi).toFloat() * cos(alt).toFloat()

        try {
            if (dir != null) {
                if (isNight) {
                    dir.intensity = 0.0f
                } else {
                    dir.direction = Vector3(x, y, z)

                    // Intensity scales with altitude
                    val intensity = (0.3f + 0.7f * t)
                    dir.intensity = intensity

                    // Color transitions during sunrise/sunset
                    dir.diffuse = sunColor
                }
            }
        } catch (e: Throwable) {
            console.error("[ERROR] Failed to update directional light: ${e.message}")
        }

        // Update sun mesh
        try {
            if (isNight) {
                sunMesh?.isVisible = false
            } else {
                if (sunMesh == null) {
                    val mesh = MeshBuilder.CreateIcoSphere(
                        name = "realtimeSun",
                        options = object : CreateIcoSphereOptions {
                            override val radius = 1.0f
                            override val subdivisions = 4
                            override val flat = false
                        },
                        scene = scene
                    )
                    mesh.isPickable = false
                    mesh.infiniteDistance = true
                    mesh.material = PBRMaterial("realtimeSunMaterial", scene).apply {
                        unlit = true
                        emissiveColor = sunColor
                        emissiveIntensity = 10f
                        fogEnabled = false
                        zOffset = 1f
                    }
                    sunMesh = mesh
                    SceneManager.getObjectManager()?.excludeMeshFromHighlight(mesh)
                    console.log("[DEBUG] Created realtime sun mesh")
                }
                sunMesh?.let { mesh ->
                    mesh.isVisible = true
                    val maxZ = scene.activeCamera?.maxZ ?: 500f
                    val sunDistance = maxZ * 0.95f
                    // Maintain a reasonable apparent size (e.g., radius of 10 at distance 400)
                    val sunScale = sunDistance / 40f
                    mesh.scaling = Vector3(sunScale, sunScale, sunScale)
                    mesh.position = Vector3(-x, -y, -z).scale(sunDistance)
                    (mesh.material as? PBRMaterial)?.emissiveColor = sunColor
                }
            }
        } catch (e: Throwable) {
            console.error("[ERROR] Failed to update sun mesh: ${e.message}")
        }

        // Update moon mesh and its dedicated light for phase rendering
        try {
            if (moon.altitudeRad < 0) {
                moonMesh?.isVisible = false
            } else {
                if (moonMesh == null) {
                    val mesh = MeshBuilder.CreateIcoSphere(
                        name = "realtimeMoon",
                        options = object : CreateIcoSphereOptions {
                            override val radius = 1.0f
                            override val subdivisions = 4
                            override val flat = false
                        },
                        scene = scene
                    )
                    mesh.isPickable = false
                    mesh.infiniteDistance = true
                    mesh.material = PBRMaterial("realtimeMoonMaterial", scene).apply {
                        albedoColor = Color3(0.9f, 0.9f, 0.95f) // Pale lunar grey
                        emissiveColor = Color3(0.1f, 0.1f, 0.15f) // Slight earthshine glow
                        emissiveIntensity = 0.0125f
                        roughness = 0.8f
                        metallic = 0f
                        fogEnabled = false
                        zOffset = 1f
                    }
                    moonMesh = mesh
                    SceneManager.getObjectManager()?.excludeMeshFromHighlight(mesh)
                    
                    // Ensure world lights don't wash out the moon's shadow/phase
                    hemi?.excludedMeshes = (hemi.excludedMeshes ?: emptyArray()) + mesh
                    dir?.excludedMeshes = (dir.excludedMeshes ?: emptyArray()) + mesh
                    
                    console.log("[DEBUG] Created realtime moon mesh")
                }
                
                moonMesh?.let { mesh ->
                    mesh.isVisible = true
                    val maxZ = scene.activeCamera?.maxZ ?: 500f
                    val moonDistance = maxZ * 0.90f // Slightly closer than sun (0.95) to ensure it's within maxZ
                    val moonScale = moonDistance / 50f // Apparent size similar to sun
                    mesh.scaling = Vector3(moonScale, moonScale, moonScale)
                    
                    val mx = sin(moon.azimuthRad).toFloat() * cos(moon.altitudeRad).toFloat()
                    val my = -sin(moon.altitudeRad).toFloat()
                    val mz = cos(moon.azimuthRad).toFloat() * cos(moon.altitudeRad).toFloat()
                    
                    // The position is opposite to the Altitude/Azimuth vector to look up into the sky
                    mesh.position = Vector3(-mx, -my, -mz).scale(moonDistance)
                    
                    // Log positions for debugging
                    if (isNight) {
                        console.log("[DEBUG] Celestial: Sun alt=${(alt * 180 / PI).asDynamic().toFixed(2)} azi=${(azi * 180 / PI).asDynamic().toFixed(2)}, Moon alt=${(moon.altitudeRad * 180 / PI).asDynamic().toFixed(2)} azi=${(moon.azimuthRad * 180 / PI).asDynamic().toFixed(2)}")
                        console.log("[DEBUG] Moon mesh at ${mesh.position.x.asDynamic().toFixed(2)}, ${mesh.position.y.asDynamic().toFixed(2)}, ${mesh.position.z.asDynamic().toFixed(2)}, distance=$moonDistance, scale=$moonScale")
                    }

                    // Dedicated light to render moon phase by lighting it from the sun's direction
                    if (moonLight == null) {
                        moonLight = DirectionalLight("moonPhaseLight", Vector3(x, y, z), scene).apply {
                            includedOnlyMeshes = arrayOf(mesh)
                            intensity = 4f // Increased from 2f
                        }
                        console.log("[DEBUG] Created moon phase light")
                    } else {
                        moonLight?.direction = Vector3(x, y, z)
                    }
                }
            }
        } catch (e: Throwable) {
            console.error("[ERROR] Failed to update moon: ${e.message}")
        }

        // Update stars skybox (night-only)
        try {
            if (isNight) {
                if (starsMesh == null) {
                    val mesh = MeshBuilder.CreateSphere(
                        name = "realtimeStars",
                        options = object : lib.CreateSphereOptions {
                            override val diameter = 2.0f
                            override val segments = 64
                            override val updatable = false
                        },
                        scene = scene
                    )
                    mesh.isPickable = false
                    mesh.infiniteDistance = true
                    mesh.checkCollisions = false
                    val mat = PBRMaterial("realtimeStarsMaterial", scene).apply {
                        // Render as unlit emissive to avoid influence from scene lights
                        unlit = true
                        fogEnabled = false
                        backFaceCulling = false // render inside of sphere
                        forceDepthWrite = false
                        disableDepthWrite = true
                        zOffset = 1f
                        albedoTexture = Texture(
                            url = "raw/data/scene/stars.png",
                            scene = scene,
                            options = object : lib.TextureOptions {
                                override val invertY: Boolean? get() = true
                                override val wrapU: Int? get() = Texture.CLAMP_ADDRESSMODE
                                override val wrapV: Int? get() = Texture.CLAMP_ADDRESSMODE
                                override val samplingMode: Int? get() = Texture.BILINEAR_SAMPLINGMODE
                                override val hasAlpha: Boolean? get() = false
                                override val noMipmap: Boolean? get() = false
                                override val generateMipMaps: Boolean? get() = true
                                override val isCube: Boolean? get() = false
                                override val format: Int? get() = null
                                override val type: Int? get() = null
                            }
                        ).apply {
                            coordinatesMode = Texture.EQUIRECTANGULAR_MODE
                        }
                    }
                    mesh.material = mat
                    starsMesh = mesh
                    SceneManager.getObjectManager()?.excludeMeshFromHighlight(mesh)
                    console.log("[DEBUG] Created realtime stars skybox")
                }

                starsMesh?.let { mesh ->
                    mesh.isVisible = true
                    // Scale sphere based on camera range for robust rendering
                    val maxZ = scene.activeCamera?.maxZ ?: 500f
                    val starsDistance = maxZ * .99f
                    mesh.scaling = Vector3(starsDistance, starsDistance, starsDistance)

                    // Orient the starfield using local sidereal time and latitude tilt
                    val lst = Astronomy.computeSiderealTime(date, lon)
                    val latRad = (lat * PI / 180.0)
                    // Rotate around Y by -LST (approximate east-to-west motion) and tilt by latitude
                    mesh.rotation = Vector3((-latRad).toFloat(), (-lst).toFloat(), 0f)
                }
            } else {
                starsMesh?.isVisible = false
            }
        } catch (e: Throwable) {
            console.error("[ERROR] Failed to update stars skybox: ${e.message}")
        }
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
        
        sunMesh?.dispose()
        sunMesh = null
        moonMesh?.dispose()
        moonMesh = null
        starsMesh?.dispose()
        starsMesh = null
        moonLight?.dispose()
        moonLight = null

        // Dispose of shadow manager
        shadowManager?.dispose()
        shadowManager = null
        
        // Dispose of occlusion manager
        occlusionManager?.dispose()
        occlusionManager = null
        performanceEngine?.dispose()
        performanceEngine = null
        
        distanceCullingSystem?.dispose()
        distanceCullingSystem = null

        // Unregister the water animation callback
        scene.unregisterBeforeRender(waterAnimationCallback)
        waterTextures.clear()

        // Unregister the flicker animation callback
        scene.unregisterBeforeRender(flickerAnimationCallback)
        flickeringLights.clear()
        
        // Set lights to null to allow garbage collection
        hemisphericLight = null
        directionalLight = null
        
        // Clear global references to lights
        clearCurrentLights()
        // Clear current instance reference
        registerCurrentInstance(null)
        
        // Disconnect from WebSocket
        WebSocketApi.disconnect()
    }
}

private data class FlickerData(
    val light: Light,
    val originalIntensity: Float,
    val originalPosition: Vector3,
    val seed: Float
)
