package main.components.camera

import lib.ArcRotateCamera
import lib.CreateSphereOptions
import lib.DefaultRenderingPipeline
import lib.DepthOfFieldEffectBlurLevel
import lib.Mesh
import lib.MeshBuilder
import lib.SSAO2RenderingPipeline
import lib.Scene
import lib.StandardMaterial
import lib.Vector3
import kotlin.math.PI

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
    
    // The main camera
    private val camera: ArcRotateCamera

    // Camera target - separate from player to prevent player moving with camera during panning
    private var cameraTarget: Mesh? = null
    
    // Rendering pipeline for post-processing effects
    private val pipeline: DefaultRenderingPipeline
    
    // Animation properties
    private var targetPosition: Vector3? = null
    private var isAnimating: Boolean = false
    private val animationSpeed: Float = 0.1f // Adjust this value to control animation speed (0.0-1.0)
    
    init {
        // Create a camera with constraints (min 4, max 12)
        camera = ArcRotateCamera(
            name = "camera",
            alpha = 0f,
            beta = 0f,
            radius = 8f, // Starting radius between min and max
            target = Vector3.Zero(),
            scene = scene
        ).apply {
            // Set camera constraints
            this.lowerRadiusLimit = 4f // Min constraint
            this.upperRadiusLimit = 12f // Max constraint
            this.wheelPrecision = 100f

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

        // Set up PBR rendering pipeline with depth texture enabled for better depth of field quality
        pipeline = DefaultRenderingPipeline(
            name = "pbrPipeline",
            hdr = true,
            scene = scene,
            cameras = arrayOf(camera)
        )
        
        pipeline.apply {
            // Enable bloom for better lighting effects
            bloomEnabled = true
            bloomThreshold = 0.5f
            bloomWeight = 0.5f
            bloomKernel = 64f
        
            // Enable depth of field
            depthOfFieldEnabled = true
            depthOfField.focalLength = 120f
            depthOfField.fStop = 1.4f
            depthOfField.focusDistance = 10_000f // millimeters
            depthOfFieldBlurLevel = DepthOfFieldEffectBlurLevel.Medium // Set blur level to Low for better quality
        
            // Enable grain effect for subtle film grain appearance
            grainEnabled = true
            grain.intensity = 0.1f  // Set grain intensity to 0.05
        
            // Enable image processing with enhanced contrast and exposure
            imageProcessingEnabled = true
            imageProcessing.contrast = 1.25f
            imageProcessing.exposure = 1.25f
        
            // Enable tone mapping
            toneMappingEnabled = true

            println("[DEBUG] Rendering pipeline configured with depth texture, bloom, DOF, image processing, and tone mapping")
        }
        
        // Set up SSAO (Screen Space Ambient Occlusion) for subtle ambient occlusion
        // Create SSAO pipeline with subtle settings
        val ssao = SSAO2RenderingPipeline(
            name = "ssao",
            scene = scene,
            ratio = 1.0, // Full resolution
            cameras = arrayOf(camera)
        )

        // Configure SSAO for subtle effect
        ssao.apply {
            // Fine-tune SSAO settings
            radius = 2f  // Small radius for subtle effect (default is 2.0)
            totalStrength = 0.25f  // Subtle strength (default is 1.0)
            samples = 8  // Moderate sample count for quality/performance balance (default is 8)
            maxZ = camera.maxZ  // Match camera maxZ (default is 100.0)
            minZAspect = 0.2f  // Default value
            base = 0f  // Default value
            epsilon = 0.02f  // Default value

            println("[DEBUG] SSAO pipeline configured with subtle settings")
        }
        
        // Create the camera target (will be positioned later when player is created)
        createCameraTarget()
    }
    
    /**
     * Creates an invisible camera target object.
     * This decouples camera panning from player movement.
     */
    private fun createCameraTarget() {
        // Create a separate camera target (invisible)
        cameraTarget = MeshBuilder.CreateSphere("cameraTarget",
            object : CreateSphereOptions {
                override val diameter = 0.1f
            }, scene
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

        if (!isAnimating || targetPosition == null || cameraTarget == null) {
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
            
            return false
        }
        
        // Interpolate between current position and target position
        val newPosition = Vector3.Lerp(currentPosition, targetPosition!!, animationSpeed)
        
        // Update the camera target object's position
        cameraTarget!!.position = newPosition
        
        // Store current camera settings
        val currentAlpha = camera.alpha
        val currentBeta = camera.beta
        val currentRadius = camera.radius
        
        // Update camera to target the camera target object
        camera.setTarget(newPosition)
        
        // Restore camera settings to maintain relative position
        camera.alpha = currentAlpha
        camera.beta = currentBeta
        camera.radius = currentRadius
        
        return true
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
     * 
     * @param position The initial position for the camera target
     */
    fun setInitialTargetPosition(position: Vector3) {
        cameraTarget?.position = position
        updateCameraTargetImmediate(position)
    }
    
    /**
     * Gets the rendering pipeline.
     * 
     * @return The rendering pipeline
     */
    fun getPipeline(): DefaultRenderingPipeline {
        return pipeline
    }
    
    /**
     * Disposes of the camera controller resources.
     */
    fun dispose() {
        cameraTarget?.dispose()
    }
}
