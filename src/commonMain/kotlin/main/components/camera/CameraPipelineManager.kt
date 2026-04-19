package main.components.camera

import lib.ArcRotateCamera
import lib.Camera
import lib.Color4
import lib.DefaultRenderingPipeline
import lib.DepthOfFieldEffectBlurLevel
import lib.Effect
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
    private var outlineOpacity = .8f
    private var outlineThicknessScale = 1f
    private var outlineColorMix = .8f

    // Outline post-processes per camera
    private val outlinePostProcesses = mutableMapOf<Camera, PostProcess>()

    init {
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
        // Use a unique name to ensure we're using the latest version of the shader
        val shaderName = "saigonOutlineV4PixelShader"
        
        ShaderStore.ShadersStore[shaderName] = """
            precision highp float;
            varying vec2 vUV;
            uniform sampler2D textureSampler;
            uniform sampler2D depthSampler;
            uniform vec2 screenSize;
            uniform float opacity;
            uniform float thicknessScale;
            uniform float colorMix;
            uniform bool hasDepth;
            
            float getLuminance(vec3 color) {
                return dot(color, vec3(0.299, 0.587, 0.114));
            }

            // High-quality hash-based noise
            float hash12(vec2 p) {
                vec3 p3 = fract(vec3(p.xyx) * 0.1031);
                p3 += dot(p3, p3.yzx + 33.33);
                return fract((p3.x + p3.y) * p3.z);
            }

            void main(void) {
                vec4 baseColor = texture2D(textureSampler, vUV);
                
                if (screenSize.x <= 0.0 || screenSize.y <= 0.0) {
                    gl_FragColor = baseColor;
                    return;
                }

                vec2 texelSize = 1.0 / screenSize;
                
                // Use gl_FragCoord for stable per-pixel noise
                float noise = hash12(gl_FragCoord.xy);
                
                // Jittered thickness for brush stroke look
                float ts = thicknessScale * (0.7 + noise * 0.6);
                
                // Sample neighbors with angular jitter for "hand-drawn" feel
                // This also breaks up axis-aligned banding artifacts
                float angle = noise * 6.28318;
                vec2 dir = vec2(cos(angle), sin(angle)) * texelSize * ts;
                vec2 ortho = vec2(-dir.y, dir.x);
                
                vec2 uv1 = vUV + dir;
                vec2 uv2 = vUV - dir;
                vec2 uv3 = vUV + ortho;
                vec2 uv4 = vUV - ortho;

                float l0 = getLuminance(baseColor.rgb);
                float l1 = getLuminance(texture2D(textureSampler, uv1).rgb);
                float l2 = getLuminance(texture2D(textureSampler, uv2).rgb);
                float l3 = getLuminance(texture2D(textureSampler, uv3).rgb);
                float l4 = getLuminance(texture2D(textureSampler, uv4).rgb);
                
                // Color edge detection
                float colorEdge = max(max(abs(l0 - l1), abs(l0 - l2)), max(abs(l0 - l3), abs(l0 - l4)));
                // Dithered threshold to avoid color banding in smooth gradients
                float colorThreshold = 0.15 + (noise - 0.5) * 0.05;
                colorEdge = smoothstep(colorThreshold, colorThreshold + 0.1, colorEdge);

                float depthEdge = 0.0;
                if (hasDepth) {
                    float d0 = texture2D(depthSampler, vUV).r;
                    float d1 = texture2D(depthSampler, uv1).r;
                    float d2 = texture2D(depthSampler, uv2).r;
                    float d3 = texture2D(depthSampler, uv3).r;
                    float d4 = texture2D(depthSampler, uv4).r;
                    
                    // Use Laplacian (second derivative) to ignore linear depth gradients (planes/slopes)
                    // On a flat slope, d1+d2-2*d0 is zero.
                    float lap1 = abs(d1 + d2 - 2.0 * d0);
                    float lap2 = abs(d3 + d4 - 2.0 * d0);
                    depthEdge = max(lap1, lap2);
                    
                    // Dithered depth threshold is CRITICAL to fixing banding on low-precision mobile depth buffers
                    // It turns the "step" line into a noisy, textured transition
                    float depthThreshold = 0.008 + (noise - 0.5) * 0.006;
                    depthEdge = smoothstep(depthThreshold, depthThreshold + 0.004, depthEdge);
                }
                
                float edge = max(colorEdge, depthEdge);
                
                // Vary opacity for ink-like feel
                float finalOpacity = opacity * (0.75 + noise * 0.25);
                
                vec3 targetColor = vec3(0.0, 0.0, 0.0);
                vec3 outlineColor = mix(baseColor.rgb, targetColor, colorMix);
                
                gl_FragColor = vec4(mix(baseColor.rgb, outlineColor, edge * finalOpacity), 1.0);
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
                    fragmentUrl = "saigonOutlineV4",
                    parameters = arrayOf("screenSize", "opacity", "thicknessScale", "colorMix", "hasDepth"),
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
                    
                    val dr = DepthPicking.depthRenderer
                    val hasDepth = dr != null
                    effect.setBool("hasDepth", hasDepth)
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
            outlinePostProcesses.keys.toList().forEach { cam ->
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
        try {
            pipeline.dispose()
        } catch (_: Throwable) {
        }
    }
}
