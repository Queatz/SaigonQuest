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
    private var outlineOpacity = .9f
    private var outlineThicknessScale = 6f
    private var outlineColorMix = .9f

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
        // Use a unique name to ensure we're using the latest version of the shader
        val shaderName = "saigonOutlineV6PixelShader"
        
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

            // High-quality hash
            float hash12(vec2 p) {
                vec3 p3 = fract(vec3(p.xyx) * 0.1031);
                p3 += dot(p3, p3.yzx + 33.33);
                return fract((p3.x + p3.y) * p3.z);
            }

            // Smooth Value Noise
            float smoothNoise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                float a = hash12(i);
                float b = hash12(i + vec2(1.0, 0.0));
                float c = hash12(i + vec2(0.0, 1.0));
                float d = hash12(i + vec2(1.0, 1.0));
                return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
            }

            void main(void) {
                vec4 baseColor = texture2D(textureSampler, vUV);
                if (screenSize.x <= 0.0 || screenSize.y <= 0.0) {
                    gl_FragColor = baseColor;
                    return;
                }

                vec2 texelSize = 1.0 / screenSize;
                
                // Low frequency noise for smooth wobble and thickness variation (~60px scale)
                vec2 pixelCoord = gl_FragCoord.xy;
                float noiseS = smoothNoise(pixelCoord * 0.0175);
                float noiseS2 = smoothNoise(pixelCoord * 0.0175 + vec2(100.0, 50.0));
                
                // High frequency noise for ink grain
                float noiseG = hash12(pixelCoord);
                
                // Displace UV slightly for "hand-drawn" wobble
                vec2 uvWobble = vUV + (vec2(noiseS, noiseS2) - 0.5) * texelSize * 1.5 * thicknessScale;
                
                // Thickness varies smoothly
                float ts = thicknessScale * (0.7 + noiseS * 0.6);
                
                float l0 = getLuminance(texture2D(textureSampler, uvWobble).rgb);
                float d0 = hasDepth ? texture2D(depthSampler, uvWobble).r : 0.0;
                
                float colorEdge = 0.0;
                float depthEdge = 0.0;
                
                // Two perpendicular pairs for Laplacian (4 samples total)
                float baseAngle = noiseS * 2.0;
                for(int i = 0; i < 2; i++) {
                    float a = baseAngle + float(i) * 1.570796;
                    vec2 offset = vec2(cos(a), sin(a)) * texelSize * ts;
                    
                    vec2 uvP = uvWobble + offset;
                    vec2 uvM = uvWobble - offset;
                    
                    float lP = getLuminance(texture2D(textureSampler, uvP).rgb);
                    float lM = getLuminance(texture2D(textureSampler, uvM).rgb);
                    colorEdge = max(colorEdge, max(abs(l0 - lP), abs(l0 - lM)));
                    
                    if (hasDepth) {
                        float dP = texture2D(depthSampler, uvP).r;
                        float dM = texture2D(depthSampler, uvM).r;
                        // Laplacian: abs(dP + dM - 2.0 * d0)
                        depthEdge = max(depthEdge, abs(dP + dM - 2.0 * d0));
                    }
                }

                // Thresholds
                float colorThreshold = 0.12 + (noiseS - 0.5) * 0.05 + (noiseG - 0.5) * 0.02;
                colorEdge = smoothstep(colorThreshold, colorThreshold + 0.15, colorEdge);
                
                float depthThreshold = 0.007 + (noiseS - 0.5) * 0.005 + (noiseG - 0.5) * 0.003;
                depthEdge = smoothstep(depthThreshold, depthThreshold + 0.005, depthEdge);
                
                float edge = max(colorEdge, depthEdge);
                
                // Ink modulation: darker in the middle, slightly grainy at edges
                float inkAlpha = smoothstep(0.0, 0.4, edge) * (0.8 + noiseG * 0.2);
                float finalOpacity = opacity * inkAlpha;
                
                vec3 targetColor = vec3(0.0, 0.0, 0.0);
                vec3 outlineColor = mix(baseColor.rgb, targetColor, colorMix);
                
                gl_FragColor = vec4(mix(baseColor.rgb, outlineColor, finalOpacity * edge), 1.0);
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
                    fragmentUrl = "saigonOutlineV6",
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
