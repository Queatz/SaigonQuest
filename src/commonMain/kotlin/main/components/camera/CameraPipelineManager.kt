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
        val shaderName = "saigonOutlinePixelShader"
        
        ShaderStore.ShadersStore[shaderName] = """
            precision mediump float;
            varying vec2 vUV;
            uniform sampler2D textureSampler;
            uniform vec2 screenSize;
            
            void main(void) {
                vec4 baseColor = texture2D(textureSampler, vUV);
                
                if (screenSize.x <= 0.0 || screenSize.y <= 0.0) {
                    gl_FragColor = baseColor;
                    return;
                }

                vec2 texelSize = 1.0 / screenSize;
                
                // Sample 4 cardinal neighbors
                vec3 n1 = texture2D(textureSampler, vUV + vec2(0.0,  texelSize.y)).rgb;
                vec3 n2 = texture2D(textureSampler, vUV + vec2(0.0, -texelSize.y)).rgb;
                vec3 n3 = texture2D(textureSampler, vUV + vec2( texelSize.x, 0.0)).rgb;
                vec3 n4 = texture2D(textureSampler, vUV + vec2(-texelSize.x, 0.0)).rgb;
                
                // Perceptual luminance weights
                vec3 lumWeights = vec3(0.299, 0.587, 0.114);
                
                float l0 = dot(baseColor.rgb, lumWeights);
                float l1 = dot(n1, lumWeights);
                float l2 = dot(n2, lumWeights);
                float l3 = dot(n3, lumWeights);
                float l4 = dot(n4, lumWeights);
                
                // Find maximum difference in luminance with neighbors
                float diff = max(max(abs(l0 - l1), abs(l0 - l2)), max(abs(l0 - l3), abs(l0 - l4)));
                
                // Threshold with smooth transition for a "playful" hand-drawn look
                // 0.1 to 0.2 is a good range for LDR colors
                float edge = smoothstep(0.1, 0.2, diff);
                
                // Mix base color with black for the outline (not 100% black to keep some detail)
                vec3 outlineColor = vec3(0.0, 0.0, 0.0);
                gl_FragColor = vec4(mix(baseColor.rgb, outlineColor, edge * 0.85), 1.0);
            }
        """.trimIndent()
    }

    /**
     * Creates an outline post-process for the given camera.
     */
    private fun createOutlinePostProcess(cam: Camera) {
        var pp = outlinePostProcesses[cam]
        if (pp == null) {
            try {
                pp = PostProcess(
                    name = "saigonOutline",
                    fragmentUrl = "saigonOutline",
                    parameters = arrayOf("screenSize"),
                    samplers = null,
                    options = 1.0,
                    camera = cam,
                    samplingMode = Texture.BILINEAR_SAMPLINGMODE,
                )

                pp.onApplyObservable.add { effect ->
                    val w = if (pp.width > 0) pp.width.toFloat() else scene.getEngine().getRenderWidth().toFloat()
                    val h = if (pp.height > 0) pp.height.toFloat() else scene.getEngine().getRenderHeight().toFloat()
                    effect.setVector2("screenSize", Vector2(w, h))
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
