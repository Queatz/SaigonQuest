package main.components.camera

import lib.*
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

    init {
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
                        bloomThreshold = 0.5f
                        bloomWeight = 0.5f
                        bloomKernel = 48f
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
            
            // Disabled due to issue with pre-pass depth map
            if (isPrettyEnabled && false) {
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
                    )
                    
                    ssaoPipeline?.apply {
                        radius = 2f
                        totalStrength = 0.25f
                        samples = 8
                        maxZ = camera.maxZ
                        minZAspect = 0.2f
                        base = 0f
                        epsilon = 0.02f
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
        } catch (e: Throwable) {
            console.error("[ERROR] Failed to remove camera from pipeline: ${e.message}")
        }
    }

    fun getPipeline(): DefaultRenderingPipeline = pipeline

    fun dispose() {
        ssaoPipeline?.dispose()
        ssaoPipeline = null
        // pipeline is usually disposed when the scene/engine is disposed,
        // but it doesn't hurt to be explicit if it had its own dispose method (it's a rendering pipeline).
    }
}
