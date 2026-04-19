package main.components.camera

import lib.ArcRotateCamera
import lib.Camera
import lib.Color4
import lib.DefaultRenderingPipeline
import lib.DepthOfFieldEffectBlurLevel
import lib.ImageProcessingPostProcess
import lib.Mesh
import lib.PostProcess
import lib.SSAO2RenderingPipeline
import lib.Scene
import lib.ShaderStore
import lib.Texture
import lib.Vector2
import lib.Vector3
import main.components.GraphicsSettings
import main.components.world.DepthPicking

/**
 * Manages the post-processing pipeline for the camera.
 * 
 * Handles effects like:
 * - Bloom
 * - Grain
 * - Chromatic Aberration
 * - Depth of Field
 * - Vignette
 * - SSAO (Ambient Occlusion)
 */
class CameraPipelineManager(
    private val scene: Scene,
    private val camera: ArcRotateCamera,
) {
    // Rendering pipeline for post-processing effects
    private val pipeline: DefaultRenderingPipeline = DefaultRenderingPipeline(
        name = "pbrPipeline",
        hdr = true,
        scene = scene,
        cameras = arrayOf(camera),
    )

    // Vignette post-process effect
    private val vignettePostProcess: ImageProcessingPostProcess = pipeline.imageProcessing.apply {
        fromLinearSpace = true
    }

    // SSAO pipeline
    private var ssaoPipeline: SSAO2RenderingPipeline? = null

    // Outline settings
    private var outlineOpacity = 1f
    private var outlineThicknessScale = .5f
    private var outlineColorMix = .8f

    // Outline post-processes per camera
    private val outlinePostProcesses = mutableMapOf<Camera, PostProcess>()

    // Cameras registered to the pipeline to ensure effects can be toggled reliably
    private val registeredCameras = mutableSetOf<Camera>()

    init {
        // Track the main camera
        registeredCameras.add(camera)

        // Register outline shader
        registerOutlineShader()

        // Enable and configure the vignette effect directly on the image processing pass
        try {
            vignettePostProcess.vignetteEnabled = true
            vignettePostProcess.vignetteStretch = 1.5f
            vignettePostProcess.vignetteColor = Color4(0f, 0f, 0f, 0f)
        } catch (_: Throwable) {}

        // Set initial vignette weight based on current radius
        updateVignetteWeight()

        // Initial setup of pipeline and camera settings
        updatePipelineSettings()

        // Create initial outline post-process for the main camera
        // This is done after updatePipelineSettings to ensure it's at the end of the chain
        createOutlinePostProcess(camera)
    }

    /**
     * Registers the outline shader in the ShaderStore.
     */
    private fun registerOutlineShader() {
        val shaderName = "saigonOutlineV14PixelShader"

        ShaderStore.ShadersStore[shaderName] = """
            precision highp float;
            varying vec2 vUV;
            uniform sampler2D textureSampler;
            uniform sampler2D depthSampler;
            uniform vec2 screenSize;
            uniform float opacity;
            uniform float thicknessScale;
            uniform float colorMix;
            uniform mat4 invViewProjection;
            uniform mat4 viewProjection;
            uniform vec2 depthRange;
            uniform vec3 cameraPosition;
            uniform vec3 cameraRight;
            uniform vec3 cameraUp;
            
            float getLuminance(vec3 color) {
                return dot(color, vec3(0.299, 0.587, 0.114));
            }

            // High-quality 3D hash for stability
            float hash3(vec3 p) {
                p = fract(p * 0.1031);
                p += dot(p, p.yzx + 33.33);
                return fract((p.x + p.y) * p.z);
            }

            // Smooth 3D Value Noise for organic variations
            float smoothNoise3D(vec3 p) {
                vec3 i = floor(p);
                vec3 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                
                float n000 = hash3(i + vec3(0, 0, 0));
                float n100 = hash3(i + vec3(1, 0, 0));
                float n010 = hash3(i + vec3(0, 1, 0));
                float n110 = hash3(i + vec3(1, 1, 0));
                float n001 = hash3(i + vec3(0, 0, 1));
                float n101 = hash3(i + vec3(1, 0, 1));
                float n011 = hash3(i + vec3(0, 1, 1));
                float n111 = hash3(i + vec3(1, 1, 1));
                
                return mix(
                    mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
                    mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y),
                    f.z
                );
            }

            void main(void) {
                vec4 baseColor = texture2D(textureSampler, vUV);
                if (screenSize.x <= 0.0 || screenSize.y <= 0.0) {
                    gl_FragColor = baseColor;
                    return;
                }

                vec2 texelSize = 1.0 / screenSize;
                float d0 = texture2D(depthSampler, vUV).r;
                
                // 0. Skip sky/background
                if (d0 >= 1.0) {
                    gl_FragColor = baseColor;
                    return;
                }
                
                // 1. Reconstruct ABSOLUTE world position for stability
                // Using absolute world space ensures the brush strokes stay pinned to the objects
                // as the camera moves through the world.
                float n = depthRange.x;
                float f = depthRange.y;
                
                // STABILITY IMPROVEMENT: Sample neighborhood to find foreground depth.
                // This ensures the noise used for the outline is pinned to the object creating the edge,
                // rather than jumping to the background depth.
                float d1 = texture2D(depthSampler, vUV + vec2(texelSize.x, 0.0)).r;
                float d2 = texture2D(depthSampler, vUV + vec2(-texelSize.x, 0.0)).r;
                float d3 = texture2D(depthSampler, vUV + vec2(0.0, texelSize.y)).r;
                float d4 = texture2D(depthSampler, vUV + vec2(0.0, -texelSize.y)).r;
                float dStable = min(d0, min(min(d1, d2), min(d3, d4)));

                float zView0 = dStable * (f - n) + n;
                float nonLinearDepth0 = (f / (f - n)) * (1.0 - n / zView0);
                
                // Standard NDC (-1 to 1 for XY, -1 to 1 for Z in standard WebGL/Babylon)
                // Note: nonLinearDepth0 is in [0,1], so we must scale to [-1,1] for the inverse matrix
                vec4 ndc0 = vec4(vUV * 2.0 - 1.0, nonLinearDepth0 * 2.0 - 1.0, 1.0);
                vec4 worldPos0 = invViewProjection * ndc0;
                vec3 stablePos = worldPos0.xyz / worldPos0.w;
                
                // 2. Drive all variations from 3D world space (pinned to objects)
                // Path wobble: very low frequency for long, elegant strokes
                float noise_path = smoothNoise3D(stablePos * 0.125);
                float noise_path2 = smoothNoise3D(stablePos * 0.125 + vec3(31.0, 71.0, 19.0));
                
                // 3. Ink variation (Weight/Opacity): medium frequency
                // Splatter: additional high-frequency noise for edge "splatter"
                float noise_var = smoothNoise3D(stablePos * 0.75);
                float noise_splat = smoothNoise3D(stablePos * 2.5);
                
                // 4. World-space UV Wobble
                // Perturb the world position by the noise and re-project to get the new UV.
                // Using cameraRight and cameraUp for a stable basis across the screen.
                vec3 perturbation = (cameraRight * (noise_path - 0.5) + cameraUp * (noise_path2 - 0.5)) * 0.05 * thicknessScale;
                vec3 perturbedWorldPos = stablePos + perturbation;
                vec4 perturbedNDC = viewProjection * vec4(perturbedWorldPos, 1.0);
                vec2 uvWobble = (perturbedNDC.xy / perturbedNDC.w) * 0.5 + 0.5;
                
                // thickness modulation ranges from 0.4 to 1.6
                float ts = thicknessScale * (0.4 + noise_var * 1.2 + noise_splat * 0.4);
                // opacity modulation ranges from 0.6 to 1.4
                float varOpacity = opacity * (0.6 + noise_var * 0.8);
                
                float l0 = getLuminance(texture2D(textureSampler, uvWobble).rgb);
                float dCenter = texture2D(depthSampler, uvWobble).r;
                float colorEdge = 0.0;
                float depthEdge = 0.0;
                
                // Kernel rotation: stable world-space seed
                float baseAngle = noise_path * 6.28318;
                for(int i = 0; i < 2; i++) {
                    float a = baseAngle + float(i) * 1.570796;
                    
                    // 5. World-space edge detection
                    // Using cameraRight and cameraUp for stable offsets relative to the screen plane
                    vec3 worldOffset = (cameraRight * cos(a) + cameraUp * sin(a)) * 0.01 * ts;
                    
                    vec3 posP = stablePos + worldOffset;
                    vec3 posM = stablePos - worldOffset;
                    
                    vec4 ndcP = viewProjection * vec4(posP, 1.0);
                    vec4 ndcM = viewProjection * vec4(posM, 1.0);
                    
                    vec2 uvP = (ndcP.xy / ndcP.w) * 0.5 + 0.5;
                    vec2 uvM = (ndcM.xy / ndcM.w) * 0.5 + 0.5;
                    
                    float lP = getLuminance(texture2D(textureSampler, uvP).rgb);
                    float lM = getLuminance(texture2D(textureSampler, uvM).rgb);
                    colorEdge = max(colorEdge, max(abs(l0 - lP), abs(l0 - lM)));
                    
                    float dP = texture2D(depthSampler, uvP).r;
                    float dM = texture2D(depthSampler, uvM).r;
                    // Use dCenter for Laplacian-style edge detection centered at uvWobble
                    depthEdge = max(depthEdge, abs(dP + dM - 2.0 * dCenter));
                }

                // Thresholds use world-stable noise to eliminate screen-space crawling
                float colorThreshold = 0.12 + (noise_var - 0.5) * 0.1;
                colorEdge = smoothstep(colorThreshold, colorThreshold + 0.12, colorEdge);
                
                float dThreshold = 0.008 + (noise_var - 0.5) * 0.007;
                depthEdge = smoothstep(dThreshold, dThreshold + 0.004, depthEdge);
                
                float edge = max(colorEdge, depthEdge);
                // Sharper ink transition for "bolder" look
                float inkAlpha = smoothstep(0.0, 0.25, edge);
                
                // Add a bit of fine grain to the ink to make it look like real charcoal/ink on paper
                // WORLD-SPACE ONLY: Use smoothNoise3D for sub-pixel stability
                float grain = smoothNoise3D(stablePos * 60.0);
                inkAlpha *= (0.85 + grain * 0.25);
                
                vec3 outlineColor = mix(baseColor.rgb, vec3(0.0), colorMix);
                
                vec3 finalColor = mix(baseColor.rgb, outlineColor, inkAlpha * varOpacity);
                gl_FragColor = vec4(finalColor, baseColor.a);
            }
        """.trimIndent()
    }

    /**
     * Creates an outline post-process for the given camera.
     */
    private fun createOutlinePostProcess(cam: Camera) {
        val isEnabled = GraphicsSettings.isOutlineEnabled()
        var pp = outlinePostProcesses[cam]

        if (!isEnabled) {
            if (pp != null) {
                pp.dispose()
                outlinePostProcesses.remove(cam)
                console.log("[DEBUG] Disposed outline post-process for camera: ${cam.name} (disabled in settings)")
            }
            return
        }

        if (pp == null) {
            try {
                pp = PostProcess(
                    name = "saigonOutline",
                    fragmentUrl = "saigonOutlineV14",
                    parameters = arrayOf("screenSize", "opacity", "thicknessScale", "colorMix", "invViewProjection", "viewProjection", "depthRange", "cameraPosition", "cameraRight", "cameraUp"),
                    samplers = arrayOf("depthSampler"),
                    options = 1.0,
                    camera = cam,
                    samplingMode = Texture.BILINEAR_SAMPLINGMODE,
                )

                pp.onApplyObservable.add { effect ->
                    val w = if (pp.width > 0) pp.width.toFloat() else scene.getEngine().getRenderWidth().toFloat()
                    val h = if (pp.height > 0) pp.height.toFloat() else scene.getEngine().getRenderHeight().toFloat()
                    effect.setVector2("screenSize", Vector2(w, h))
                    effect.setFloat("opacity", outlineOpacity)
                    effect.setFloat("thicknessScale", outlineThicknessScale)
                    effect.setFloat("colorMix", outlineColorMix)
                    
                    // Force update of camera matrices to ensure stable unprojection
                    try {
                        cam.getTransformationMatrix()
                    } catch (_: Throwable) {}
                    
                    effect.setMatrix("invViewProjection", cam.getTransformationMatrix().clone().invert())
                    effect.setMatrix("viewProjection", cam.getTransformationMatrix())
                    effect.setVector2("depthRange", Vector2(cam.minZ, cam.maxZ))
                    effect.setVector3("cameraPosition", cam.globalPosition)
                    effect.setVector3("cameraRight", cam.getDirection(Vector3.Right()))
                    effect.setVector3("cameraUp", cam.getDirection(Vector3.Up()))
                    
                    val dr = DepthPicking.depthRenderer
                    if (dr != null) {
                        effect.setTexture("depthSampler", dr.getDepthMap())
                    }
                }

                outlinePostProcesses[cam] = pp
                console.log("[DEBUG] Created outline post-process for camera: ${cam.name}")
            } catch (e: Throwable) {
                console.error("[ERROR] Failed to create outline post-process: ${e.message}")
            }
        }

        // Move to the end of the post-process chain to ensure it runs after tone mapping (LDR)
        if (pp != null) {
            try {
                cam.detachPostProcess(pp)
                cam.attachPostProcess(pp)
            } catch (_: Throwable) {}
        }
    }

    /**
     * Updates the pipeline settings based on the graphics settings.
     */
    fun updatePipelineSettings() {
        val isPrettyEnabled = GraphicsSettings.isPrettyGraphicsEnabled()

        scene.imageProcessingConfiguration.exposure = 2f

        try {
            // Ensure image processing is always active so gamma/tone mapping are applied in all modes
            try {
                if (pipeline.imageProcessingEnabled) {
                    pipeline.imageProcessingEnabled = false
                }
                pipeline.imageProcessingEnabled = true
                try { pipeline.imageProcessing.fromLinearSpace = true } catch (_: Throwable) {}
            } catch (e: Throwable) {
                console.error("[ERROR] Failed to toggle imageProcessingEnabled: ${e.message}")
            }

            // Step 1: Enable/disable features
            pipeline.apply {
                try {
                    bloomEnabled = isPrettyEnabled
                } catch (e: Throwable) {
                    console.error("[ERROR] Failed to set bloomEnabled: ${e.message}")
                }
                
                try {
                    grainEnabled = isPrettyEnabled
                    if (isPrettyEnabled) {
                        grain.intensity = .667f
                    }
                } catch (e: Throwable) {
                    console.error("[ERROR] Failed to set grainEnabled: ${e.message}")
                }

                try {
                    chromaticAberrationEnabled = isPrettyEnabled
                    if (isPrettyEnabled) {
                        chromaticAberration.radialIntensity = 3f
                    }
                } catch (e: Throwable) {
                    console.error("[ERROR] Failed to set chromaticAberrationEnabled: ${e.message}")
                }
            }
            
            // Step 2: Configure features if they're enabled
            if (isPrettyEnabled) {
                pipeline.apply {
                    try {
                        bloomThreshold = 0.25f
                        bloomWeight = 0.25f
                        bloomKernel = 32f
                        bloomScale = .5f
                        console.log("[DEBUG] Bloom settings configured successfully")
                    } catch (e: Throwable) {
                        console.error("[ERROR] Failed to configure bloom: ${e.message}")
                    }
                    
                    try {
                        if (depthOfFieldEnabled) {
                            depthOfField.focalLength = 120f
                            depthOfField.fStop = 1f
                            depthOfField.focusDistance = 10_000f
                            depthOfFieldBlurLevel = DepthOfFieldEffectBlurLevel.Medium
                            console.log("[DEBUG] Depth of field settings configured successfully")
                        }
                    } catch (e: Throwable) {
                        console.error("[ERROR] Failed to configure depth of field: ${e.message}")
                    }
                }
            }
            
            // Handle SSAO pipeline
            if (ssaoPipeline != null) {
                try {
                    ssaoPipeline?.dispose()
                    ssaoPipeline = null
                    console.log("[DEBUG] Cleaned up existing SSAO pipeline")
                } catch (e: Throwable) {
                    console.error("[ERROR] Failed to clean up SSAO pipeline: ${e.message}")
                }
            }
            
            // Reenable SSAO
            if (isPrettyEnabled) {
                try {
                    val allCameras = mutableListOf<Camera>(camera)
                    scene.activeCamera?.let {
                        if (it != camera) {
                            allCameras.add(it)
                        }
                    }

                    ssaoPipeline = SSAO2RenderingPipeline(
                        name = "ssao",
                        scene = scene,
                        ratio = 1.0,
                        cameras = allCameras.toTypedArray(),
                        forceGeometryBuffer = true,
                    )
                    
                    ssaoPipeline?.apply {
                        samples = 16
                        maxZ = 500f
                        radius = 0.5f
                        totalStrength = 1.0f
                    }
                    console.log("[DEBUG] Created and configured new SSAO pipeline")
                } catch (e: Throwable) {
                    console.error("[ERROR] Failed to create SSAO pipeline: ${e.message}")
                    ssaoPipeline = null
                }
            }

            // Force-refresh depth-dependent post-processes
            try {
                val shouldEnableDOF = isPrettyEnabled
                if (pipeline.depthOfFieldEnabled) {
                    pipeline.depthOfFieldEnabled = false
                }
                pipeline.depthOfFieldEnabled = shouldEnableDOF
            } catch (e: Throwable) {
                console.error("[ERROR] Failed to force-refresh DOF: ${e.message}")
            }

            // Re-ensure outline is at the end of the post-process chain for all cameras
            registeredCameras.toList().forEach { cam ->
                createOutlinePostProcess(cam)
            }
        } catch (e: Throwable) {
            console.error("[ERROR] Failed to update pipeline settings: ${e.message}")
        }
    }

    /**
     * Updates the depth of field focus to match the distance to the camera target.
     */
    fun updateDepthOfFieldFocus(cameraTarget: Mesh?) {
        if (pipeline.depthOfFieldEnabled && cameraTarget != null) {
            val currentCamera = scene.activeCamera ?: camera
            
            if (currentCamera != camera) {
                return
            }

            val distanceToTarget = Vector3.Distance(currentCamera.position, cameraTarget.position)
            pipeline.depthOfField.focusDistance = distanceToTarget * 1_000f // in millimeters
        }
    }

    /**
     * Updates the vignette weight based on the camera radius.
     */
    fun updateVignetteWeight() {
        val minRadius = camera.lowerRadiusLimit
        val maxRadius = camera.upperRadiusLimit
        val maxWeight = 4f
        val minWeight = 1f
        
        val activeCamera = scene.activeCamera ?: camera
        val currentRadius = (activeCamera as? ArcRotateCamera)?.radius ?: camera.radius
        
        val weight = maxWeight - (currentRadius - minRadius) * (maxWeight - minWeight) / (maxRadius - minRadius)
        vignettePostProcess.vignetteWeight = weight
    }

    /**
     * Adds a camera to the post-processing pipeline.
     */
    fun addCameraToPipeline(cameraToAdd: Camera) {
        try {
            registeredCameras.add(cameraToAdd)
            pipeline.addCamera(cameraToAdd)
            ssaoPipeline?.addCamera(cameraToAdd)
            createOutlinePostProcess(cameraToAdd)
        } catch (e: Throwable) {
            console.error("[ERROR] Failed to add camera to pipeline: ${e.message}")
        }
    }

    /**
     * Removes a camera from the post-processing pipeline.
     */
    fun removeCameraFromPipeline(cameraToRemove: Camera) {
        try {
            registeredCameras.remove(cameraToRemove)
            pipeline.removeCamera(cameraToRemove)
            ssaoPipeline?.removeCamera(cameraToRemove)
            outlinePostProcesses.remove(cameraToRemove)?.dispose()
        } catch (e: Throwable) {
            console.error("[ERROR] Failed to remove camera from pipeline: ${e.message}")
        }
    }

    fun getPipeline(): DefaultRenderingPipeline = pipeline

    /**
     * Updates the outline settings.
     */
    fun updateOutlineSettings(
        opacity: Float? = null,
        thicknessScale: Float? = null,
        colorMix: Float? = null,
    ) {
        opacity?.let { outlineOpacity = it }
        thicknessScale?.let { outlineThicknessScale = it }
        colorMix?.let { outlineColorMix = it }
    }

    fun dispose() {
        ssaoPipeline?.dispose()
        ssaoPipeline = null
        outlinePostProcesses.values.forEach { it.dispose() }
        outlinePostProcesses.clear()
        registeredCameras.clear()
        try {
            pipeline.dispose()
        } catch (_: Throwable) {
        }
    }
}
