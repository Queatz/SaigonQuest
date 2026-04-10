package main.components.camera

import lib.Scene
import lib.Vector3
import lib.Node
import lib.Camera
import main.components.world.getRadius
import main.components.world.getCameraName

data class ProximityCamera(
    val node: Node,
    val targetCamera: Camera,
    val radius: Float
)

/**
 * Manages proximity-based camera locking.
 * Scans the scene for cameras and associated proximity markers,
 * and determines when the camera should lock to a specific position.
 */
class ProximityCameraManager(private val scene: Scene) {
    private var proximityCameras = mutableListOf<ProximityCamera>()
    private var activeProximityCamera: ProximityCamera? = null
    private var lastActiveProximityCamera: ProximityCamera? = null
    
    // Animation state
    private var influence = 0f
    private val influenceSpeed = 0.003f // Linear increment per millisecond (approx 0.05 per frame at 60fps)
    
    /**
     * Scans all nodes in the scene to find proximity markers and associated cameras.
     */
    fun initialize() {
        proximityCameras.clear()
        
        val allNodes = mutableListOf<Node>()
        allNodes.addAll(scene.cameras)
        allNodes.addAll(scene.transformNodes)
        allNodes.addAll(scene.meshes)
        
        val sceneCameras = scene.cameras
        
        for (node in allNodes) {
            val radius = node.getRadius() ?: continue
            
            // 1. Try to find camera by explicit name in custom property 'camera'
            val cameraName = node.getCameraName()
            var targetCamera: Camera? = null
            
            if (cameraName != null) {
                targetCamera = sceneCameras.find { it.name == cameraName }
                if (targetCamera == null) {
                    console.warn("[ProximityCameraManager] Marker ${node.name} specifies camera '$cameraName', but no such camera exists in scene.")
                }
            }
            
            // 2. Fallback: if marker is a camera itself, it's its own target
            if (targetCamera == null && node is Camera) {
                targetCamera = node
            }
            
            // 3. Fallback: Search hierarchy up and down
            if (targetCamera == null) {
                // Search up (parents)
                var current: Node? = node
                while (current != null && targetCamera == null) {
                    if (current is Camera) {
                        targetCamera = current
                    }
                    current = current.parent
                }
                
                // Search down (children)
                if (targetCamera == null) {
                    val descendants = node.getChildren(null, false)
                    targetCamera = descendants.find { it is Camera } as? Camera
                }
            }
            
            if (targetCamera != null && targetCamera !== scene.activeCamera) {
                proximityCameras.add(
                    ProximityCamera(
                        node = node,
                        targetCamera = targetCamera,
                        radius = radius
                    )
                )
            }
        }
        
        if (proximityCameras.isNotEmpty()) {
            console.log("[ProximityCameraManager] Found ${proximityCameras.size} proximity-based cameras")
        } else {
            console.log("[ProximityCameraManager] No proximity cameras found in the scene")
        }
    }
    
    /**
     * Checks if the player is within range of any proximity cameras and updates the influence.
     * 
     * @param playerPosition The current position of the player
     */
    fun update(playerPosition: Vector3) {
        if (proximityCameras.isEmpty()) return
        
        var closest: ProximityCamera? = null
        var minDistance = Float.MAX_VALUE
        
        for (pc in proximityCameras) {
            val dist = Vector3.Distance(playerPosition, pc.node.getAbsolutePosition())
            if (dist <= pc.radius) {
                if (dist < minDistance) {
                    minDistance = dist
                    closest = pc
                }
            }
        }
        
        // Handle transitions between proximity cameras or in/out of lock
        if (closest != activeProximityCamera) {
            if (closest != null) {
                console.log("[ProximityCameraManager] Entering proximity camera: ${closest.targetCamera.name}")
                lastActiveProximityCamera = closest
            } else {
                console.log("[ProximityCameraManager] Leaving proximity camera")
            }
            activeProximityCamera = closest
        }
        
        // Animate influence linearly
        val targetInfluence = if (activeProximityCamera != null) 1f else 0f
        val deltaTime = scene.deltaTime
        val step = influenceSpeed * deltaTime
        
        if (influence < targetInfluence) {
            influence = (influence + step).coerceAtMost(targetInfluence)
        } else if (influence > targetInfluence) {
            influence = (influence - step).coerceAtLeast(targetInfluence)
        }
    }
    
    /**
     * Returns whether the camera is currently locked or transitioning to/from a lock.
     */
    fun isActive(): Boolean = influence > 0f
    
    /**
     * Returns whether the camera is fully locked (influence == 1.0).
     */
    fun isFullyLocked(): Boolean = influence >= 0.999f
    
    /**
     * Returns the current raw influence of the proximity camera (0.0 to 1.0).
     */
    fun getInfluence(): Float = influence

    /**
     * Returns the current eased influence of the proximity camera (0.0 to 1.0).
     * Uses a smoothstep function for ease-in-out effect.
     */
    fun getEasedInfluence(): Float {
        return influence * influence * (3f - 2f * influence)
    }
    
    /**
     * Returns the currently active proximity camera marker, if any.
     */
    fun getActiveCamera(): ProximityCamera? = activeProximityCamera

    /**
     * Returns the last active proximity camera marker, even if currently null.
     * Useful for smooth transitions when leaving a lock.
     */
    fun getLastActiveCamera(): ProximityCamera? = lastActiveProximityCamera
    
    /**
     * Clears all proximity cameras.
     */
    fun clear() {
        proximityCameras.clear()
        activeProximityCamera = null
        influence = 0f
    }
}
