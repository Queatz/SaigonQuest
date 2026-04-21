package main.components.world

import lib.AbstractMesh
import lib.DepthRenderer
import lib.Gltf
import lib.Material
import lib.Matrix
import lib.MeshMetadata
import lib.PBRCustomMaterial
import lib.PBRMaterial
import lib.Scene
import lib.StandardMaterial
import lib.Vector2
import lib.Vector3
import lib.CustomMaterial
import main.components.GraphicsSettings
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
    private fun createDepthRenderer(): DepthRenderer {
        return scene.enableDepthRenderer(
            camera = scene.activeCamera,
            storeNonLinearDepth = false,
            force32bitsFloat = true
        ).apply {
            try { this.forceDepthWriteTransparentMeshes = true } catch (_: Throwable) {}
            DepthPicking.depthRenderer = this
        }
    }

    private fun recreateDepthRenderer() {
        try { scene.disableDepthRenderer() } catch (_: Throwable) {}
        val newRenderer = createDepthRenderer()
        depthRenderer = newRenderer
        // Reapply depth override materials for tracked meshes
        depthOverrideMeshes.forEach { m ->
            try {
                val depthMat = getDepthMaterialForMesh(m)
                depthRenderer.setMaterialForRendering(m, depthMat)
            } catch (t: Throwable) {
                console.warn("[OcclusionManager] Failed to reapply depth material for ${m.name}: ${t.message}")
            }
        }
        // Force post-process pipeline to refresh its depth/pre-pass bindings,
        // because disabling/enabling the scene's DepthRenderer can invalidate internal references.
        SceneManager.getCameraController()?.updatePipelineSettings()
    }
    // Don't initialize these at construction time, get them dynamically when needed
    private var camera: lib.Camera? = null
    private var player: lib.Mesh? = null
    private val occludedMeshes = mutableSetOf<AbstractMesh>()
    private val customMaterials = mutableMapOf<AbstractMesh, PBRCustomMaterial>()

    // Single source of truth for current time (seconds), updated once per frame in update()
    private var currentTimeSeconds: Float = 0f

    // Smoothed tracking of player world position (follows actual player over ~2s)
    private var trackedPlayerWorldPos: Vector3? = null
    private var lastSmoothingTimeSeconds: Float = 0f
    private var playerTrackTimeConstantSeconds: Float = 0.5f

    // Depth renderer and shared white depth material
    private var depthRenderer: DepthRenderer = createDepthRenderer()
    private var sharedWhiteDepthMaterial: CustomMaterial? = null
    private val depthMaterialCache = mutableMapOf<Material, CustomMaterial>()
    private val depthOverrideMeshes = mutableSetOf<AbstractMesh>()

    // Track engine render size to detect size changes (window resize, zoom, graphics mode)
    private var lastRenderWidth: Int = 0
    private var lastRenderHeight: Int = 0
    private var lastActiveCamera: lib.Camera? = null
    
    // Configurable parameters
    private var occlusionSoftness = 0.2f  // Default: softness ratio [0..1] of occlusionRadius (resolution independent)
    private var isEnabled = true
    private var isForceDisabled = false
    private var useRadius = true

    /**
     * Observer for graphics settings changes.
     */
    private val graphicsSettingsObserver: () -> Unit = {
        updateEnabledState()
    }

    init {
        GraphicsSettings.addObserver(graphicsSettingsObserver)
        updateEnabledState()
    }

    private fun updateEnabledState() {
        isEnabled = !isForceDisabled && !GraphicsSettings.isObserverModeEnabled()
    }

    // Custom shader name
    private val customShaderName = "occlusionFader"
    
    // Fragment shader code for uniform declarations (global scope)
    private val uniformDeclarations = """
    // Set default precision for float values
    precision mediump float;
    
    // Constants for optimization
    const float ALPHA_THRESHOLD = 0.5;  // Higher value = more fragments discarded
    
    // Forward declaration of the applyOcclusion function
    float applyOcclusion(float alpha);
    """

    // Fragment shader code for occlusion effect implementation
    private val occlusionShaderCode = """
    // Set default precision for float values
    precision mediump float;

    // Stochastic dithering noise in [0,1)
    // High-quality hash-based noise to eliminate visible waves/stripes
    float hash12(vec2 p) {
        // Dave Hoskins style 2D hash (good distribution, low cost)
        vec3 p3 = fract(vec3(p.xyx) * 0.1031);
        p3 += dot(p3, p3.yzx + 33.33);
        return fract((p3.x + p3.y) * p3.z);
    }

    float noise(vec2 p) {
        // Interleaved Gradient Noise (IGN) — decorrelates neighboring pixels
        float ig = fract(52.9829189 * fract(p.x * 0.06711056 + p.y * 0.00583715));
        // Cranley–Patterson rotation with time to avoid static patterns
        float t = fract(time * 0.61803398875);
        return fract(hash12(p + t) + ig);
    }

    float applyOcclusion(float alpha) {
        if (!isEnabled) {
            return alpha;
        }
        
        float bandWidthPx = max(0.0, occlusionSoftness * occlusionRadius);
        
        // 1. Calculate distance from this pixel to the player on screen
        vec2 playerScreenPixel = playerScreenPos.xy * screenSize;
        
        // 2. Depth check: is the pixel closer to the camera than the player?
        float fragmentDepth = gl_FragCoord.z;
        float playerDepth = playerScreenPos.z;
        float depthDiff = fragmentDepth - playerDepth + 0.001;
        
        // Dithered evaluation for depth comparison (soften hard thresholds)
        float DEPTH_BAND = occlusionSoftness * 0.001;
        bool isOccluding = (depthDiff < 0.0);
        if (!isOccluding && DEPTH_BAND > 0.0) {
            float depthWeight = smoothstep(DEPTH_BAND, 0.0, abs(depthDiff));
            float depthRnd = noise(gl_FragCoord.xy + vec2(13.7, 31.1));
            if (depthWeight > depthRnd) {
                isOccluding = true;
            }
        }
        
        // 3. Directional/Height Check: "The Flashlight Cone"
        // We only want to fade if the pixel is either:
        // - Above the player in screen space
        // - Well above the player in world space (tall objects)
        // - Inside an imaginary cone from the player towards the camera
        if (isOccluding) {
            float SOFTNESS_WORLD = occlusionSoftness * 0.4;
            
            // Screen Height Weight (soft transition from below to above player)
            float screenHeightWeight = smoothstep(-bandWidthPx * 0.5, bandWidthPx * 0.5, gl_FragCoord.y - playerScreenPixel.y);
            
            // World Height Weight (at feet vs tall object)
            float worldHeightWeight = smoothstep(0.1 - SOFTNESS_WORLD, 0.1 + SOFTNESS_WORLD, vPositionW.y - playerWorld.y);
            
            // Cone check: world-space alignment with player-camera axis
            vec3 toFrag = vPositionW - playerWorld;
            vec3 toCamDir = normalize(cameraWorld - playerWorld);
            float distAlongAxis = dot(toFrag, toCamDir);
            vec3 perp = toFrag - distAlongAxis * toCamDir;
            float distSqToAxis = dot(perp, perp);
            
            // Cone starts at player, grows slightly as it goes towards camera
            float coneRadius = 0.125 + clamp(distAlongAxis / 10.0, 0.0, 1.0) * 0.5;
            float coneRadiusSq = coneRadius * coneRadius;
            
            // Soften the cone edge and the cone start
            float coneWeight = smoothstep(coneRadiusSq * (1.0 + occlusionSoftness), coneRadiusSq * (1.0 - occlusionSoftness), distSqToAxis);
            coneWeight *= smoothstep(-0.2 - SOFTNESS_WORLD, -0.2 + SOFTNESS_WORLD, distAlongAxis);
            
            // Additional safety weights for "above player but far below world" and "below player but in cone"
            float floorWeight = smoothstep(-0.4 - SOFTNESS_WORLD, -0.4 + SOFTNESS_WORLD, vPositionW.y - playerWorld.y);
            float safetyWeight = smoothstep(-0.5 - SOFTNESS_WORLD, -0.5 + SOFTNESS_WORLD, vPositionW.y - playerWorld.y);

            // Logic:
            // if below player (screenHeightWeight=0.0): must be wellAbove OR (inCone AND floor)
            // if above player (screenHeightWeight=1.0): must be wellAbove OR inCone OR safety
            float weightBelow = max(worldHeightWeight, min(coneWeight, floorWeight));
            float weightAbove = max(worldHeightWeight, max(coneWeight, safetyWeight));
            
            float finalProb = mix(weightBelow, weightAbove, screenHeightWeight);
            
            // Apply dithering to these new checks
            if (finalProb < 1.0) {
                float rnd = noise(gl_FragCoord.xy + vec2(23.1, 61.7));
                if (rnd > finalProb) {
                    isOccluding = false;
                }
            }
        }

        // 4. Combine checks and apply radial mask with dithering
        // We only fade if the pixel is confirmed as occluding
        if (isOccluding) {
            vec2 delta = gl_FragCoord.xy - playerScreenPixel;
            float dist = length(delta);
            
            if (!useRadius) {
                discard;
            } else if (bandWidthPx <= 0.0) {
                if (dist < occlusionRadius) {
                    discard;
                }
            } else {
                float innerRadius = max(0.0, occlusionRadius - bandWidthPx);
                if (dist < innerRadius) {
                    discard;
                } else if (dist < occlusionRadius) {
                    float gradientPos = (dist - innerRadius) / max(0.0001, bandWidthPx);
                    float coverage = clamp(gradientPos, 0.0, 1.0);
                    float threshold = noise(gl_FragCoord.xy);
                    if (coverage < threshold) {
                        discard;
                    }
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
     * Sets the occlusion softness parameter.
     * Interpreted as a ratio [0..1] of the occlusion radius used to compute the gradient band width.
     * The actual pixel band width = occlusionSoftness * occlusionRadius, which keeps the visual width
     * resolution independent and proportional to the overall occlusion size.
     *
     * @param softnessRatio Ratio in [0..1]:
     *   - 0f: Sharp edge (no gradient)
     *   - (0,1]: Dithered gradient with width proportional to occlusion radius
     */
    fun setOcclusionSoftness(softnessRatio: Float) {
        occlusionSoftness = softnessRatio.coerceIn(0f, 1f)
    }

    fun setEnabled(enabled: Boolean) {
        isForceDisabled = !enabled
        updateEnabledState()
    }

    fun setUseRadius(enabled: Boolean) {
        useRadius = enabled
    }

    private fun createDepthMaterial(original: Material?): CustomMaterial {
        val matName = if (original != null) "${customShaderName}-depth-${original.name}" else "${customShaderName}-depth-shared"
        val mat = CustomMaterial(matName, scene)

        // Declare precision and uniforms for depth linearization
        mat.AddUniform("cameraNear", "float")
        mat.AddUniform("cameraFar", "float")
        mat.AddUniform("useReverseDepth", "bool")

        // Occlusion uniforms (match those used in PBR occlusion shader)
        mat.AddUniform("playerScreenPos", "vec3")
        mat.AddUniform("screenSize", "vec2")
        mat.AddUniform("occlusionRadius", "float")
        mat.AddUniform("playerWorld", "vec3")
        mat.AddUniform("cameraWorld", "vec3")
        mat.AddUniform("occlusionSoftness", "float")
        mat.AddUniform("useRadius", "bool")
        mat.AddUniform("isEnabled", "bool")
        mat.AddUniform("time", "float")

        // Depth linearization and any shared helpers
        mat.Fragment_Definitions(
            """
            $occlusionShaderCode
            
            float toLinearDepth(float depth01) {
                // Optionally handle reverse depth buffers
                float d = useReverseDepth ? (1.0 - depth01) : depth01;
                // Convert [0,1] depth to NDC [-1,1]
                float zNDC = d * 2.0 - 1.0;
                // Linearize using near/far
                float linear = (2.0 * cameraNear * cameraFar) / (cameraFar + cameraNear - zNDC * (cameraFar - cameraNear));
                // Normalize to [0,1] between near and far
                return clamp((linear - cameraNear) / max(1e-6, (cameraFar - cameraNear)), 0.0, 1.0);
            }
            """.trimIndent()
        )

        // Overwrite the final color with normalized linear depth in red channel
        // but first, apply occlusion discard via applyOcclusion (same logic as PBR material)
        mat.Fragment_MainEnd(
            """
            float depth01 = gl_FragCoord.z;
            float normDepth = toLinearDepth(depth01);
            
            // Apply occlusion discards
            applyOcclusion(alpha);
            
            gl_FragColor = vec4(normDepth, 0.0, 0.0, 1.0);
            """.trimIndent()
        )
        mat.disableLighting = true
        mat.fogEnabled = false
        mat.alpha = 1.0f

        // Copy settings from original material to support alpha testing and correct culling
        if (original != null) {
            mat.zOffset = original.zOffset
            mat.zOffsetUnits = original.zOffsetUnits
            mat.backFaceCulling = original.backFaceCulling
            if (original is PBRMaterial) {
                original.albedoTexture?.let { mat.diffuseTexture = it }
                mat.alphaTestCutoff = original.alphaTestCutoff
                mat.useAlphaFromDiffuseTexture = original.useAlphaFromAlbedoTexture
                mat.transparencyMode = original.transparencyMode
            } else if (original is StandardMaterial) {
                original.diffuseTexture?.let { mat.diffuseTexture = it }
                mat.alphaTestCutoff = original.alphaTestCutoff
                mat.useAlphaFromDiffuseTexture = original.useAlphaFromDiffuseTexture
                mat.transparencyMode = original.transparencyMode
            }
        } else {
            mat.backFaceCulling = false
        }

        // Bind camera near/far and occlusion uniforms each frame when material is bound
        mat.onBindObservable.add { _ ->
            val effect = mat.getEffect() ?: return@add
            val cam = scene.activeCamera ?: return@add
            try {
                // Depth uniforms
                effect.setFloat("cameraNear", cam.minZ)
                effect.setFloat("cameraFar", cam.maxZ)
                effect.setBool("useReverseDepth", false)

                // Skip occlusion binding if we don't have player/camera
                val playerMesh = player ?: SceneManager.getPlayerController()?.getCollider()
                if (playerMesh != null) {
                    val targetWorldPos = playerMesh.position.add(Vector3(0f, .5f, 0f))
                    val playerWorldPos = trackedPlayerWorldPos ?: targetWorldPos
                    val playerScreenPos = Vector3.Project(
                        vector = playerWorldPos,
                        world = Matrix.Identity(),
                        transform = scene.getTransformMatrix(),
                        viewport = cam.viewport,
                    ).apply { y = 1.0f - y }

                    val viewWidth = scene.getEngine().getRenderWidth()
                    val viewHeight = scene.getEngine().getRenderHeight()
                    val occRadius = min(viewWidth, viewHeight) / 3.0f
                    val screenSize = Vector2(viewWidth.toFloat(), viewHeight.toFloat())

                    effect.setVector3("playerScreenPos", playerScreenPos)
                    effect.setVector3("playerWorld", playerWorldPos)
                    effect.setVector3("cameraWorld", cam.globalPosition)
                    effect.setFloat("occlusionRadius", occRadius)
                    effect.setFloat("occlusionSoftness", occlusionSoftness)
                    effect.setVector2("screenSize", screenSize)
                    effect.setBool("useRadius", useRadius)
                    effect.setBool("isEnabled", isEnabled)
                    effect.setFloat("time", (currentTimeSeconds * 12).toInt() / 12f)
                }
            } catch (_: Throwable) {
                // avoid crashing if effect is not ready yet
            }
        }
        return mat
    }

    private fun getOrCreateSharedWhiteDepthMaterial(): CustomMaterial {
        return sharedWhiteDepthMaterial ?: createDepthMaterial(null).also { sharedWhiteDepthMaterial = it }
    }

    /**
     * Returns a depth material for the given mesh, accounting for its alpha cutoff settings.
     * Caches results per original material.
     */
    fun getDepthMaterialForMesh(mesh: AbstractMesh): CustomMaterial {
        val original = mesh.material ?: return getOrCreateSharedWhiteDepthMaterial()
        return depthMaterialCache.getOrPut(original) {
            createDepthMaterial(original)
        }
    }

    /** Public accessor for the shared white depth material so other systems (e.g., DepthPicking) can reuse it. */
    fun getSharedWhiteDepthMaterial(): CustomMaterial {
        return getOrCreateSharedWhiteDepthMaterial()
    }

    /**
     * Registers a mesh to be considered for occlusion.
     * Creates a PBRCustomMaterial for the mesh with occlusion shader code.
     *
     * @param mesh The mesh to register
     */
    fun registerMesh(mesh: AbstractMesh) {
        // Skip if mesh is not visible or is a helper object
        // Or if already registered
        if (!mesh.isVisible || occludedMeshes.contains(mesh)) {
            return
        }

        // Ensure we have a PBRCustomMaterial and add our effects to it
        val customMaterial = MaterialCustomizer.getOrCreateCustomMaterial(mesh, scene) ?: return

        // Register all required uniforms safely
        MaterialCustomizer.safeAddUniform(customMaterial, "playerScreenPos", "vec3")
        MaterialCustomizer.safeAddUniform(customMaterial, "screenSize", "vec2")
        MaterialCustomizer.safeAddUniform(customMaterial, "occlusionRadius", "float")
        MaterialCustomizer.safeAddUniform(customMaterial, "playerWorld", "vec3")
        MaterialCustomizer.safeAddUniform(customMaterial, "cameraWorld", "vec3")
        MaterialCustomizer.safeAddUniform(customMaterial, "occlusionSoftness", "float")
        MaterialCustomizer.safeAddUniform(customMaterial, "useRadius", "bool")
        MaterialCustomizer.safeAddUniform(customMaterial, "isEnabled", "bool")
        MaterialCustomizer.safeAddUniform(customMaterial, "time", "float")

        MaterialCustomizer.appendFragmentBegin(customMaterial, uniformDeclarations)
        MaterialCustomizer.appendFragmentDefinitions(customMaterial, occlusionShaderCode)

        // Use Fragment_Custom_Alpha instead of Fragment_Before_FragColor since we're only modifying the alpha
        MaterialCustomizer.appendFragmentAlphaCode(customMaterial, occlusionCallCode)

        // Add a handler for the onBindObservable to set uniforms when the material is bound
        // We only add the observer once
        val dyn = customMaterial.asDynamic()
        if (dyn._occlusionObserved == null) {
            dyn._occlusionObserved = true
            customMaterial.onBindObservable.add { _ ->
                // Get the effect after the material is bound
                val effect = customMaterial.getEffect()
                if (effect == null) {
                    return@add
                }

                try {
                    // Skip if no player or camera
                    val playerMesh = player ?: SceneManager.getPlayerController()?.getCollider() ?: return@add
                    val cam = camera ?: scene.activeCamera ?: return@add

                    // Get player's world position (smoothed)
                    val targetWorldPos = playerMesh.position.add(Vector3(0f, .5f, 0f))
                    val playerWorldPos = trackedPlayerWorldPos ?: targetWorldPos

                    // Get player's screen position (normalized [0,1] coordinates)
                    val playerScreenPos = Vector3.Project(
                        vector = playerWorldPos,
                        world = Matrix.Identity(),
                        transform = scene.getTransformMatrix(),
                        viewport = cam.viewport
                    ).apply {
                        y = 1.0f - y  // Invert Y coordinate
                    }

                    // Calculate occlusion radius based on minimum of view width/height
                    val viewWidth = scene.getEngine().getRenderWidth()
                    val viewHeight = scene.getEngine().getRenderHeight()
                    val occlusionRadius = min(viewWidth, viewHeight) / 3.0f

                    // Create screenSize vector once and reuse it
                    val screenSize = Vector2(viewWidth.toFloat(), viewHeight.toFloat())

                    // Set player position uniforms
                    effect.setVector3("playerScreenPos", playerScreenPos)
                    effect.setVector3("playerWorld", playerWorldPos)
                    effect.setVector3("cameraWorld", cam.globalPosition)

                    // Set occlusion parameters
                    effect.setFloat("occlusionRadius", occlusionRadius)
                    effect.setFloat("occlusionSoftness", occlusionSoftness)

                    // Set screen size
                    effect.setVector2("screenSize", screenSize)

                    effect.setBool("useRadius", useRadius)
                    effect.setBool("isEnabled", isEnabled)

                    // Set time uniform for animations
                    effect.setFloat("time", (currentTimeSeconds * 12).toInt() / 12f)
                } catch (e: Throwable) {
                    // ignore binding errors
                }
            }
        }

        customMaterial.needDepthPrePass = true

        // Store the custom material for later updates
        customMaterials[mesh] = customMaterial

        val depthMat = getDepthMaterialForMesh(mesh)
        depthRenderer.setMaterialForRendering(mesh, depthMat)
        depthOverrideMeshes.add(mesh)
        
        // Add to tracking set
        occludedMeshes.add(mesh)
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
        val currentCamera = scene.activeCamera
        camera = currentCamera
        player = SceneManager.getPlayerController()?.getCollider()

        if (currentCamera != lastActiveCamera) {
            console.log("[OcclusionManager] Active camera changed from ${lastActiveCamera?.name} to ${currentCamera?.name}. Recreating DepthRenderer.")
            recreateDepthRenderer()
            lastActiveCamera = currentCamera
        }

        // Update shared current time once per frame (seconds)
        currentTimeSeconds = (kotlinx.browser.window.performance.now() / 1000.0).toFloat()

        // Update smoothed player world position to track real player over ~2 seconds
        val playerMeshRef = player
        if (playerMeshRef != null) {
            val targetWorldPos = playerMeshRef.position.add(Vector3(0f, .5f, 0f))
            if (trackedPlayerWorldPos == null) {
                trackedPlayerWorldPos = targetWorldPos
                lastSmoothingTimeSeconds = currentTimeSeconds
            } else {
                val dt = (currentTimeSeconds - lastSmoothingTimeSeconds).coerceAtLeast(0f)
                lastSmoothingTimeSeconds = currentTimeSeconds
                val duration = playerTrackTimeConstantSeconds
                val alpha = if (duration <= 0f) 1f else (dt / duration).coerceIn(0f, 1f)
                val cur = trackedPlayerWorldPos!!
                trackedPlayerWorldPos = Vector3(
                    cur.x + (targetWorldPos.x - cur.x) * alpha,
                    cur.y + (targetWorldPos.y - cur.y) * alpha,
                    cur.z + (targetWorldPos.z - cur.z) * alpha,
                )
            }
        }

        // Detect engine render size changes and recreate depth renderer if needed
        try {
            val eng = scene.getEngine()
            val w = eng.getRenderWidth()
            val h = eng.getRenderHeight()
            if (w != lastRenderWidth || h != lastRenderHeight) {
                console.log("[OcclusionManager] Render size changed from $lastRenderWidth x $lastRenderHeight to $w x $h. Recreating DepthRenderer.")
                recreateDepthRenderer()
                lastRenderWidth = w
                lastRenderHeight = h
            }
            if (lastRenderWidth == 0 || lastRenderHeight == 0) {
                lastRenderWidth = w
                lastRenderHeight = h
            }
        } catch (_: Throwable) { }
    }
    
    /**
     * Disposes the occlusion manager and cleans up resources.
     * Restores original materials to meshes and clears tracking sets.
     */
    fun dispose() {
        GraphicsSettings.removeObserver(graphicsSettingsObserver)

        // Restore original materials
        occludedMeshes.forEach { mesh ->
            val metadata = mesh.metadata
            if (metadata?.originalMaterial != null) {
                mesh.material = metadata.originalMaterial!!
            }
            // Remove depth override
            try { depthRenderer.setMaterialForRendering(mesh, null) } catch (_: Throwable) {}
        }
        // Dispose depth materials
        depthMaterialCache.values.forEach { 
            try { it.dispose() } catch (_: Throwable) {}
        }
        depthMaterialCache.clear()
        try { sharedWhiteDepthMaterial?.dispose() } catch (_: Throwable) {}
        sharedWhiteDepthMaterial = null
        depthOverrideMeshes.clear()
        
        // Clear tracking sets
        occludedMeshes.clear()
        customMaterials.clear()
        
        // Disable and clear depth renderer
        try { scene.disableDepthRenderer() } catch (_: Throwable) {}
        try { DepthPicking.depthRenderer = null } catch (_: Throwable) {}
    }
}
