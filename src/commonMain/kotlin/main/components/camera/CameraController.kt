package main.components.camera

import lib.ArcRotateCamera
import lib.ArcRotateCameraKeyboardMoveInput
import lib.ArcRotateCameraMouseWheelInput
import lib.ArcRotateCameraPointersInput
import lib.Color4
import lib.ColorCorrectionPostProcess
import lib.CreateSphereOptions
import lib.DefaultRenderingPipeline
import lib.DepthOfFieldEffectBlurLevel
import lib.ImageProcessingPostProcess
import lib.Mesh
import lib.MeshBuilder
import lib.SSAO2RenderingPipeline
import lib.Scene
import lib.Vector3
import kotlin.math.PI
import main.components.GraphicsSettings
import main.components.SceneManager
import main.components.world.isSkybox

/**
 * Controller for managing the camera in the 3D scene.
 * 
 * This class encapsulates all camera-related functionality including:
 * - Camera creation and configuration
 * - Camera target management
 * - Camera movement and positioning
 * - Depth of field updates
 * - Smooth camera animations
 */
class CameraController(private val scene: Scene) {
    // Initial FOV value when closest to target
    private val baseFov = 0.5f

    // Create the main camera
    private val camera: ArcRotateCamera = ArcRotateCamera(
        name = "camera",
        alpha = 0f,
        beta = 0f,
        radius = 8f, // Starting radius between min and max
        target = Vector3.Zero(),
        scene = scene
    ).apply {
        // Set camera constraints
        this.lowerRadiusLimit = 4f // Min constraint
        this.upperRadiusLimit = 16f // Max constraint
        this.lowerBetaLimit = (15f * PI / 180f).toFloat() // 85 degrees in radians
        this.upperBetaLimit = (85f * PI / 180f).toFloat() // 85 degrees in radians

        this.wheelPrecision = 200f

        // Set a narrow FOV (default is ~0.8 radians/45 degrees)
        this.fov = 0.5f // ~28 degrees - narrower telephoto-like view
        this.minZ = 0.1f
        this.maxZ = 100f

        // Set initial position
        this.alpha = (-PI / 2).toFloat()
        this.beta = (PI / 3).toFloat()

        // Attach camera controls
        attachControl(true)

        // Remove keyboard controls to avoid conflicts with player movement
        inputs.removeByType("ArcRotateCameraKeyboardMoveInput")
    }

    // Camera target - separate from player to prevent player moving with camera during panning
    private var cameraTarget: Mesh? = null
    
    // Rendering pipeline for post-processing effects

    // Set up PBR rendering pipeline with depth texture enabled for better depth of field quality
    private val pipeline: DefaultRenderingPipeline = DefaultRenderingPipeline(
        name = "pbrPipeline",
        hdr = true,
        scene = scene,
        cameras = arrayOf(camera)
    )

    // Vignette post-process effect
    private var vignettePostProcess: ImageProcessingPostProcess
    
    // Color correction post-process effect
    private var colorCorrectionPostProcess: ColorCorrectionPostProcess? = null
    
    // Animation properties
    private var targetPosition: Vector3? = null
    private var isAnimating: Boolean = false
    private val animationSpeed: Float = 0.1f // Adjust this value to control animation speed (0.0-1.0)

    /**
     * Observer for graphics settings changes.
     */
    private val graphicsSettingsObserver: () -> Unit = {
        updatePipelineSettings()
    }

    init {
        // Use the pipeline's built-in ImageProcessingPostProcess to ensure consistent gamma/tone mapping
        try {
            pipeline.imageProcessingEnabled = true
        } catch (_: Throwable) {}
        vignettePostProcess = pipeline.imageProcessing.apply {
            fromLinearSpace = true
        }

        // Enable and configure the vignette effect directly on the image processing pass
        try {
            vignettePostProcess.vignetteEnabled = true
            vignettePostProcess.vignetteStretch = 1.5f
            vignettePostProcess.vignetteColor = Color4(0f, 0f, 0f, 0f)
        } catch (_: Throwable) {}

        // Set initial vignette weight based on current radius
        updateVignetteWeight()

        println("[DEBUG] Vignette effect configured on pipeline imageProcessing")

        // Create the camera target (will be positioned later when player is created)
        createCameraTarget()

        // Register an observer with GraphicsSettings to update pipeline settings when graphics settings change
        GraphicsSettings.addObserver(graphicsSettingsObserver)

        // Initial setup of pipeline settings based on current settings
        // This will configure bloom, DOF, grain, image processing, tone mapping, and SSAO
        updatePipelineSettings()

        colorCorrectionPostProcess = ColorCorrectionPostProcess(
            name = "colorCorrection",
            colorTableUrl = "/raw/data/lut.png",
            ratio = 1.0f, // Full resolution
            camera = camera
        )
    }
    
    /**
     * Creates an invisible camera target object.
     * This decouples camera panning from player movement.
     */
    private fun createCameraTarget() {
        // Create a separate camera target (invisible)
        cameraTarget = MeshBuilder.CreateSphere(
            name = "cameraTarget",
            options = object : CreateSphereOptions {
                override val diameter = 0.1f
            },
            scene = scene
        ).apply {
            isVisible = false
        }
        
        println("[DEBUG] Created camera target with depth-texture-friendly invisible material")
    }
    
    /**
     * Updates the camera target position.
     * Sets the target position for smooth animation.
     * 
     * @param position The new position for the camera target
     */
    fun updateCameraTarget(position: Vector3) {
        // Set the target position for animation
        targetPosition = position.clone()
        isAnimating = true
    }
    
    /**
     * Updates the camera animation in each frame.
     * Should be called in the render loop.
     * 
     * @return True if animation is in progress, false otherwise
     */
    fun updateAnimation(): Boolean {
        updateDepthOfFieldFocus()
        updateVignetteWeight()

        // Update FOV based on camera radius
        if (GraphicsSettings.isWideAngleEnabled()) {
            val radiusRange = camera.upperRadiusLimit - camera.lowerRadiusLimit
            val radiusRatio = (camera.radius - camera.lowerRadiusLimit) / radiusRange
            camera.fov = baseFov * (1f + 1f * radiusRatio) // Scale from 1x to 4x base FOV
        } else {
            camera.fov = baseFov
        }

        if (!isAnimating || targetPosition == null || cameraTarget == null) {
            updateSkybox()
            return false
        }
        
        // Get current position
        val currentPosition = cameraTarget!!.position
        
        // Check if we're close enough to the target to end the animation
        if (Vector3.Distance(currentPosition, targetPosition!!) < 0.01f) {
            // Set final position
            cameraTarget!!.position = targetPosition!!
            
            // Update camera to target the camera target object
            updateCameraTargetImmediate(targetPosition!!)
            
            // Reset animation state
            isAnimating = false
            targetPosition = null

            updateSkybox()
            
            return false
        }
        
        // Interpolate between current position and target position
        val newPosition = Vector3.Lerp(currentPosition, targetPosition!!, animationSpeed)
        
        // Update the camera target object's position
        cameraTarget!!.position = newPosition
        
        // Update camera to target the camera target object
        updateCameraTargetImmediate(newPosition)

        updateSkybox()
        
        return true
    }

    private fun updateSkybox() {
        // Update skybox
        SceneManager.getObjectManager()?.getSkybox()?.let { skybox ->
            camera.position.let { skybox.position.copyFrom(it) }
            skybox.position.x = -skybox.position.x
        }
    }
    
    /**
     * Updates the camera target position immediately without animation.
     * Used internally and for initial positioning.
     * 
     * @param position The new position for the camera target
     */
    private fun updateCameraTargetImmediate(position: Vector3) {
        // Store current camera settings
        val currentAlpha = camera.alpha
        val currentBeta = camera.beta
        val currentRadius = camera.radius
        
        // Update camera to target the camera target object
        camera.setTarget(position)
        
        // Restore camera settings to maintain relative position
        camera.alpha = currentAlpha
        camera.beta = currentBeta
        camera.radius = currentRadius
    }
    
    /**
     * Updates the depth of field focus to match the distance to the camera target.
     */
    private fun updateDepthOfFieldFocus() {
        if (pipeline.depthOfFieldEnabled && cameraTarget != null) {
            // Calculate distance between camera and camera target
            val distanceToTarget = Vector3.Distance(camera.position, cameraTarget!!.position)
            // Update DOF focus distance with the actual distance to target
            pipeline.depthOfField.focusDistance = distanceToTarget * 1_000f // in millimeters
        }
    }
    
    /**
     * Updates the vignette weight based on the camera radius.
     * - When radius=4 (min), vignetteWeight = 8
     * - When radius=12 (max), vignetteWeight = 2
     */
    private fun updateVignetteWeight() {
        // Constants for the linear interpolation
        val minRadius = camera.lowerRadiusLimit
        val maxRadius = camera.upperRadiusLimit
        val maxWeight = 4f // Weight when radius is at minimum
        val minWeight = 1f // Weight when radius is at maximum
        
        // Calculate the vignette weight using linear interpolation
        val weight = maxWeight - (camera.radius - minRadius) * (maxWeight - minWeight) / (maxRadius - minRadius)
        
        // Update the vignette weight
        vignettePostProcess.vignetteWeight = weight
    }
    
    /**
     * Gets the camera.
     * 
     * @return The camera
     */
    fun getCamera(): ArcRotateCamera {
        return camera
    }
    
    /**
     * Gets the camera target.
     * 
     * @return The camera target mesh
     */
    fun getCameraTarget(): Mesh? {
        return cameraTarget
    }
    
    /**
     * Sets the initial camera target position.
     * This is done immediately without animation.
     * Creates a separate copy of the position to decouple from player position.
     * 
     * @param position The initial position for the camera target
     */
    fun setInitialTargetPosition(position: Vector3) {
        // Create a clone of the position to decouple from player position
        val cameraTargetPosition = position.clone()
        cameraTarget?.position = cameraTargetPosition
        updateCameraTargetImmediate(cameraTargetPosition)
    }
    
    /**
     * Focuses the camera on a specific position in the scene.
     * This animates the camera movement to the target position.
     * 
     * @param position The position to focus on
     */
    fun focusOnPosition(position: Vector3) {
        // Ensure the camera target exists
        if (cameraTarget == null) {
            createCameraTarget()
        }
        
        // Reset animation state to ensure consistent behavior when called multiple times
        isAnimating = false
        targetPosition = null
        
        // Update the camera target with animation
        updateCameraTarget(position)
        
        // Set a fixed radius for consistent zoom level when focusing on objects
        // This ensures the camera is always at the same distance when locating objects
        camera.radius = 12.0f // Fixed distance that works well for viewing objects
        
        console.log("Camera focused on position (${position.x}, ${position.y}, ${position.z})")
    }
    
    /**
     * Gets the rendering pipeline.
     * 
     * @return The rendering pipeline
     */
    fun getPipeline(): DefaultRenderingPipeline {
        return pipeline
    }
    
    // Store reference to the SSAO pipeline
    private var ssaoPipeline: SSAO2RenderingPipeline? = null
    
    /**
     * Updates the pipeline settings based on the pretty graphics setting.
     * This method can be called when the pretty graphics setting changes.
     */
    fun updatePipelineSettings() {
        val isPrettyEnabled = GraphicsSettings.isPrettyGraphicsEnabled()

        scene.imageProcessingConfiguration.exposure = 1.5f

        try {
            // Ensure image processing is always active so gamma/tone mapping are applied in all modes
            try {
                if (pipeline.imageProcessingEnabled) {
                    pipeline.imageProcessingEnabled = false
                }
                pipeline.imageProcessingEnabled = true
                try { pipeline.imageProcessing.fromLinearSpace = true } catch (_: Throwable) {}
            } catch (e: Throwable) {
                println("[ERROR] Failed to toggle imageProcessingEnabled: ${e.message}")
            }

            // First enable/disable features, then configure them
            // This ensures the components are created before we try to access their properties
            
            // Step 1: Enable/disable features
            pipeline.apply {
                // Enable/disable features one by one with proper error handling
                try {
                    // Enable/disable bloom
                    bloomEnabled = isPrettyEnabled
                } catch (e: Throwable) {
                    println("[ERROR] Failed to set bloomEnabled: ${e.message}")
                }
                
                try {
                    // Enable/disable grain effect
                    grainEnabled = isPrettyEnabled
                    if (isPrettyEnabled) {
                        grain.intensity = .667f
                    }
                } catch (e: Throwable) {
                    println("[ERROR] Failed to set grainEnabled: ${e.message}")
                }

                try {
                    // Enable/disable image processing
                    chromaticAberrationEnabled = isPrettyEnabled
                    if (isPrettyEnabled) {
                        chromaticAberration.radialIntensity = 3f
                    }
                } catch (e: Throwable) {
                    println("[ERROR] Failed to set imageProcessingEnabled: ${e.message}")
                }
            }
            
            // Step 2: Configure features if they're enabled
            // This ensures the components are created before we try to access their properties
            if (isPrettyEnabled) {
                pipeline.apply {
                    try {
                        // Check if bloom is available before configuring
                        if (isPrettyEnabled) {
                            // Configure bloom settings
                            bloomThreshold = 0.5f
                            bloomWeight = 0.5f
                            bloomKernel = 48f
                            bloomScale = .5f
                            println("[DEBUG] Bloom settings configured successfully")
                        }
                    } catch (e: Throwable) {
                        println("[ERROR] Failed to configure bloom: ${e.message}")
                    }
                    
                    try {
                        // Check if depth of field is available before configuring
                        if (depthOfFieldEnabled) {
                            // Configure depth of field settings
                            depthOfField.focalLength = 120f
                            depthOfField.fStop = 1.4f
                            depthOfField.focusDistance = 10_000f
                            depthOfFieldBlurLevel = DepthOfFieldEffectBlurLevel.Medium
                            println("[DEBUG] Depth of field settings configured successfully")
                        }
                    } catch (e: Throwable) {
                        println("[ERROR] Failed to configure depth of field: ${e.message}")
                    }
                }
            }
            
            // Handle SSAO pipeline
            // We'll recreate the SSAO pipeline each time to avoid potential issues
            // First, clean up any existing SSAO pipeline
            if (ssaoPipeline != null) {
                try {
                    // Just set to null to let garbage collection handle it
                    // The BabylonJS engine will clean up the pipeline resources
                    ssaoPipeline?.dispose()
                    ssaoPipeline = null
                    println("[DEBUG] Cleaned up existing SSAO pipeline")
                } catch (e: Throwable) {
                    println("[ERROR] Failed to clean up SSAO pipeline: ${e.message}")
                }
            }
            
            // Create new SSAO pipeline if pretty graphics are enabled
            // Disabled due to issue with pre-pass depth map
            if (isPrettyEnabled && false) {
                try {
                    // Create SSAO pipeline with subtle settings
                    ssaoPipeline = SSAO2RenderingPipeline(
                        name = "ssao",
                        scene = scene,
                        ratio = 1.0, // Full resolution
                        cameras = arrayOf(camera)
                    )
                    
                    // Configure SSAO for subtle effect
                    ssaoPipeline?.apply {
                        // Fine-tune SSAO settings
                        radius = 2f  // Small radius for subtle effect (default is 2.0)
                        totalStrength = 0.25f  // Subtle strength (default is 1.0)
                        samples = 8  // Moderate sample count for quality/performance balance (default is 8)
                        maxZ = camera.maxZ  // Match camera maxZ (default is 100.0)
                        minZAspect = 0.2f  // Default value
                        base = 0f  // Default value
                        epsilon = 0.02f  // Default value
                    }
                    println("[DEBUG] Created and configured new SSAO pipeline")
                } catch (e: Throwable) {
                    println("[ERROR] Failed to create SSAO pipeline: ${e.message}")
                    // Ensure ssaoPipeline is null if creation failed
                    ssaoPipeline = null
                }
            }

            // Force-refresh depth-dependent post-processes (like DOF) to rebind depth/pre-pass after renderer changes
            try {
                val shouldEnableDOF = isPrettyEnabled
                // Toggle DOF off then back on to force internal resources to rebuild
                if (pipeline.depthOfFieldEnabled) {
                    pipeline.depthOfFieldEnabled = false
                }
                pipeline.depthOfFieldEnabled = shouldEnableDOF
            } catch (e: Throwable) {
                println("[ERROR] Failed to force-refresh DOF: ${e.message}")
            }
            
            println("[DEBUG] Pipeline settings updated. Pretty graphics mode: $isPrettyEnabled")
        } catch (e: Throwable) {
            println("[ERROR] Failed to update pipeline settings: ${e.message}")
        }
    }
    
    /**
     * Enables camera inputs to allow camera movement
     * This re-attaches all controls and adds back specific input types
     */
    fun enableCameraInputs() {
        // Re-attach all controls with preventDefaultBehavior set to true
        // This prevents the browser's default behavior for these events
        camera.attachControl(true)
        
        try {
            // Add keyboard input
            val keyboardInput = ArcRotateCameraKeyboardMoveInput()
            camera.inputs.add(keyboardInput)
            
            // Add mouse wheel input
            val mouseWheelInput = ArcRotateCameraMouseWheelInput()
            camera.inputs.add(mouseWheelInput)
            
            // Add pointer input (mouse movement)
            val pointerInput = ArcRotateCameraPointersInput()
            camera.inputs.add(pointerInput)
            
            console.log("Camera inputs explicitly re-added: keyboard, mouse wheel, and pointers")
        } catch (e: Throwable) {
            console.error("Error re-adding camera inputs: ${e.message}")
        }

        camera.wheelPrecision = 200f
        
        console.log("Camera inputs re-enabled")
    }
    
    /**
     * Disables camera inputs to prevent camera movement during object manipulation
     */
    fun disableCameraInputs() {
        // First detach all controls
        camera.detachControl()
        
        // Also disable specific input types to ensure no camera movement during dragging
        camera.inputs.removeByType("ArcRotateCameraKeyboardMoveInput")
        camera.inputs.removeByType("ArcRotateCameraMouseWheelInput")
        camera.inputs.removeByType("ArcRotateCameraPointersInput")
        
        console.log("Camera inputs disabled for object manipulation")
    }

    /**
     * Disposes of the camera controller resources.
     */
    fun dispose() {
        // Remove the observer when disposing
        GraphicsSettings.removeObserver(graphicsSettingsObserver)
        
        // Clean up the SSAO pipeline
        ssaoPipeline = null
        
        // Clean up the ColorCorrectionPostProcess
        try {
            colorCorrectionPostProcess?.dispose()
            colorCorrectionPostProcess = null
            println("[DEBUG] ColorCorrectionPostProcess disposed")
        } catch (e: Throwable) {
            println("[ERROR] Failed to dispose ColorCorrectionPostProcess: ${e.message}")
        }
        
        // Clean up the camera target
        cameraTarget?.dispose()
        
        println("[DEBUG] Camera controller resources disposed")
    }
}
