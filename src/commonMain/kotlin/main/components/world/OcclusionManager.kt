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
import lib.Vector2
import lib.Vector3
import lib.CustomMaterial
import main.components.SceneManager
import kotlin.math.min
import kotlin.math.roundToInt

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
        val depthMat = sharedWhiteDepthMaterial ?: getOrCreateSharedWhiteDepthMaterial()
        depthOverrideMeshes.forEach { m ->
            try { depthRenderer.setMaterialForRendering(m, depthMat) } catch (t: Throwable) {
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
    private val depthOverrideMeshes = mutableSetOf<AbstractMesh>()

    // Track engine render size to detect size changes (window resize, zoom, graphics mode)
    private var lastRenderWidth: Int = 0
    private var lastRenderHeight: Int = 0
    
    // Configurable parameters
    private var occlusionSoftness = 0.05f  // Default: softness ratio [0..1] of occlusionRadius (resolution independent)

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
        vec2 playerScreenPixel = playerScreenPos.xy * screenSize;
        vec2 delta = gl_FragCoord.xy - playerScreenPixel;
        float distance = sqrt(dot(delta, delta));
        float normalizedDistance = distance / occlusionRadius;
        
        float fragmentDepth = gl_FragCoord.z;
        float playerDepth = playerScreenPos.z;
        float depthDiff = fragmentDepth - playerDepth + 0.001;

        // Dithered evaluation for depth and height comparisons (soften hard thresholds)
        // Drive bands by occlusionSoftness ratio for uniform blur across resolutions
        float DEPTH_BAND = occlusionSoftness * 0.001;
        float HEIGHT_BAND = occlusionSoftness;

        bool depthCloser = (depthDiff < 0.0);
        if (!depthCloser) {
            float depthWeight = smoothstep(DEPTH_BAND, 0.0, abs(depthDiff));
            float depthRnd = noise(gl_FragCoord.xy + vec2(13.7, 31.1));
            if (depthWeight > depthRnd) {
                depthCloser = true;
            }
        }

        bool abovePlayer = (vPositionW.y > playerWorld.y);
        if (!abovePlayer) {
            float dy = vPositionW.y - playerWorld.y;
            float heightWeight = smoothstep(HEIGHT_BAND, 0.0, abs(dy));
            float heightRnd = noise(gl_FragCoord.xy + vec2(91.3, 7.9));
            if (heightWeight > heightRnd) {
                abovePlayer = true;
            }
        }

        // Proceed if both (dithered) conditions indicate occluder
        if (depthCloser && abovePlayer) {
            float bandWidthPx = max(0.0, occlusionSoftness * occlusionRadius);
            if (bandWidthPx <= 0.0) {
                // Hard edge: discard everything within the occlusion radius
                if (normalizedDistance < 1.0) {
                    discard;
                }
            } else {
                float innerRadius = max(0.0, occlusionRadius - bandWidthPx);
                if (!useRadius || distance < innerRadius) {
                    discard;
                } else if (distance < occlusionRadius) {
                    float gradientPos = (distance - innerRadius) / max(0.0001, bandWidthPx);
                    float coverage = clamp(gradientPos, 0.0, 1.0);
                    float threshold = noise(gl_FragCoord.xy);
                    if (coverage < threshold) {
                        discard;
                    }
                    return alpha;
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

    private fun getOrCreateSharedWhiteDepthMaterial(): CustomMaterial {
        val mat = sharedWhiteDepthMaterial ?: CustomMaterial("${customShaderName}-depth", scene).apply {
            // Declare precision and uniforms for depth linearization
            AddUniform("cameraNear", "float")
            AddUniform("cameraFar", "float")
            AddUniform("useReverseDepth", "bool")

            // Occlusion uniforms (match those used in PBR occlusion shader)
            AddUniform("playerScreenPos", "vec3")
            AddUniform("screenSize", "vec2")
            AddUniform("occlusionRadius", "float")
            AddUniform("playerWorld", "vec3")
            AddUniform("occlusionSoftness", "float")
            AddUniform("useRadius", "bool")
            AddUniform("time", "float")

            // Depth linearization and any shared helpers
            Fragment_Definitions(
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
            Fragment_MainEnd(
                """
                float depth01 = gl_FragCoord.z;
                float normDepth = toLinearDepth(depth01);
                
                // Apply occlusion discards
                applyOcclusion(alpha);
                
                gl_FragColor = vec4(normDepth, 0.0, 0.0, 1.0);
                """.trimIndent()
            )
            disableLighting = true
            fogEnabled = false
            alpha = 1.0f
            backFaceCulling = false // todo to follow the meshes

            // Bind camera near/far and occlusion uniforms each frame when material is bound
            onBindObservable.add { _ ->
                val effect = getEffect() ?: return@add
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
                        val occRadius = min(viewWidth, viewHeight) / 2.0f
                        val screenSize = Vector2(viewWidth.toFloat(), viewHeight.toFloat())

                        effect.setVector3("playerScreenPos", playerScreenPos)
                        effect.setVector3("playerWorld", playerWorldPos)
                        effect.setFloat("occlusionRadius", occRadius)
                        effect.setFloat("occlusionSoftness", occlusionSoftness)
                        effect.setVector2("screenSize", screenSize)
                        effect.setBool("useRadius", false)
                        effect.setFloat("time", (currentTimeSeconds * 8).toInt() / 8f)
                    }
                } catch (_: Throwable) {
                    // avoid crashing if effect is not ready yet
                }
            }
        }
        sharedWhiteDepthMaterial = mat
        return mat
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
        if (!mesh.isVisible) {
            return
        }

        // Ensure the material is a PBRMaterial
        val originalMaterial = mesh.material
        if (originalMaterial is PBRMaterial) {
            // Store the original material to restore later
            val metadata = object : MeshMetadata {
                override var originalMaterial: Material? = originalMaterial
                override var isItem: Boolean? = mesh.metadata?.isItem
                override var gltf: Gltf? = mesh.metadata?.gltf
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
            customMaterial.transparencyMode = originalMaterial.transparencyMode//Material.MATERIAL_ALPHATEST//ANDBLEND
            customMaterial.alphaTestCutoff = originalMaterial.alphaTestCutoff
            customMaterial.alpha = originalMaterial.alpha
            customMaterial.fogEnabled = originalMaterial.fogEnabled
            customMaterial.disableLighting = originalMaterial.disableLighting
            customMaterial.backFaceCulling = originalMaterial.backFaceCulling
            customMaterial.needDepthPrePass = true
            customMaterial.useAlphaFromAlbedoTexture = originalMaterial.useAlphaFromAlbedoTexture

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
            customMaterial.AddUniform("useRadius", "bool")
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
                    val occlusionRadius = min(viewWidth, viewHeight) / 2.0f

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

                    effect.setBool("useRadius", false)

                    // Set time uniform for animations
                    effect.setFloat("time", (currentTimeSeconds * 8).toInt() / 8f)
                } catch (e: Throwable) {
                    console.log("[ERROR] Failed to set uniforms during binding for material ${customMaterial.name}: ${e.message}")
                }
            }

            customMaterial.needDepthPrePass = true

            // Assign the custom material to the mesh
            mesh.material = customMaterial

            // Store the custom material for later updates
            customMaterials[mesh] = customMaterial

            val depthMat = getOrCreateSharedWhiteDepthMaterial()
            depthRenderer.setMaterialForRendering(mesh, depthMat)
            depthOverrideMeshes.add(mesh)
            
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
        // Restore original materials
        occludedMeshes.forEach { mesh ->
            val metadata = mesh.metadata
            if (metadata?.originalMaterial != null) {
                mesh.material = metadata.originalMaterial!!
            }
            // Remove depth override
            try { depthRenderer.setMaterialForRendering(mesh, null) } catch (_: Throwable) {}
        }
        // Dispose shared depth material
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
