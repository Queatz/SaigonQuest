package main.components.world

import lib.AbstractMesh
import lib.ArcRotateCamera
import lib.BoundingBox
import lib.Mesh
import lib.Node
import lib.Scene
import lib.Vector3
import kotlinx.browser.window
import kotlin.math.max
import kotlin.math.tan

/**
 * Active performance engine that hides meshes that are too small on screen.
 */
class ActivePerformanceEngine(private val scene: Scene) {
    private var minSizeThreshold = 32f
    private var lastUpdateMs = 0.0
    private var updateIntervalMs = 250.0 // Run 4 times per second

    private var worldMeshes: Array<AbstractMesh> = emptyArray()
    private val objectMeshes = mutableSetOf<AbstractMesh>()
    private val hiddenByEngine = mutableSetOf<AbstractMesh>()

    // Stats
    private var _totalTracked = 0
    private var _visibleCount = 0

    /**
     * Returns the total number of meshes tracked by the performance engine.
     */
    fun getTotalTrackedCount(): Int = _totalTracked

    /**
     * Returns the number of meshes currently visible (not hidden by this engine).
     */
    fun getVisibleCount(): Int = _visibleCount

    /**
     * Sets the world meshes to be monitored by the performance engine.
     */
    fun setWorldMeshes(meshes: Array<AbstractMesh>) {
        // Restore visibility for previously hidden world meshes before replacing
        worldMeshes.forEach { mesh ->
            if (hiddenByEngine.contains(mesh)) {
                mesh.isVisible = true
            }
        }
        hiddenByEngine.removeAll(worldMeshes.toSet())
        
        worldMeshes = meshes
    }

    /**
     * Registers an object mesh to be monitored by the performance engine.
     */
    fun registerObjectMesh(mesh: AbstractMesh) {
        objectMeshes.add(mesh)
    }

    /**
     * Unregisters an object mesh from the performance engine.
     */
    fun unregisterObjectMesh(mesh: AbstractMesh) {
        if (hiddenByEngine.contains(mesh)) {
            mesh.isVisible = true
            hiddenByEngine.remove(mesh)
        }
        objectMeshes.remove(mesh)
    }

    /**
     * Sets the minimum size threshold in pixels.
     * Meshes smaller than this size on screen will be hidden.
     */
    fun setThreshold(size: Float) {
        minSizeThreshold = size
    }

    /**
     * Updates the performance engine, hiding or showing meshes based on their screen size.
     */
    fun update() {
        val now = window.performance.now()
        if (now - lastUpdateMs < updateIntervalMs) return
        lastUpdateMs = now

        val camera = scene.activeCamera as? ArcRotateCamera ?: return
        val engine = scene.getEngine()
        val renderHeight = engine.getRenderHeight().toFloat()
        
        if (renderHeight <= 0) return

        // Use a set to track processed meshes and avoid double-processing (e.g. source mesh and its instances)
        val processedMeshes = mutableSetOf<AbstractMesh>()

        // Combine world and object meshes
        val initialMeshes = worldMeshes.asSequence() + objectMeshes.asSequence()

        var currentTotal = 0
        var currentVisible = 0

        initialMeshes.forEach { mesh ->
            val result = processMeshRecursive(mesh, camera, renderHeight, processedMeshes)
            currentTotal += result.total
            currentVisible += result.visible
        }

        _totalTracked = currentTotal
        _visibleCount = currentVisible
    }

    private data class ProcessResult(val total: Int, val visible: Int)

    /**
     * Processes a mesh and its instances.
     * @return ProcessResult containing counts of total and visible meshes in this branch
     */
    private fun processMeshRecursive(
        mesh: AbstractMesh,
        camera: ArcRotateCamera,
        renderHeight: Float,
        processedMeshes: MutableSet<AbstractMesh>
    ): ProcessResult {
        if (processedMeshes.contains(mesh)) return ProcessResult(0, 0)
        processedMeshes.add(mesh)

        var total = 1
        var visible = 0

        // Skip skybox, helper meshes, and already disabled nodes
        // These are not "real" game objects we want to track in performance stats.
        if (mesh.isSkybox() || 
            mesh.sourceMesh?.isSkybox() == true || 
            mesh.name.contains("helper", ignoreCase = true)) {
            return ProcessResult(0, 0)
        }
        
        // Ground meshes should ALWAYS be visible and counted in stats, but never culled.
        // We check both the mesh itself and its source mesh (for instances).
        if (mesh.isIncullable() || mesh.sourceMesh?.isIncullable() == true) {
            if (hiddenByEngine.contains(mesh)) {
                mesh.isVisible = true
                hiddenByEngine.remove(mesh)
            }
            
            var totalCount = 1
            var visibleCount = 1
            
            // Process instances if this is a source mesh to ensure they are also tracked and visible
            if (mesh is Mesh) {
                mesh.instances.forEach { instance ->
                    val res = processMeshRecursive(instance, camera, renderHeight, processedMeshes)
                    totalCount += res.total
                    visibleCount += res.visible
                }
            }
            return ProcessResult(totalCount, visibleCount)
        }
        
        // If the mesh was manually disabled by another system, we should ignore it
        // unless we were the ones who hid it.
        if (!mesh.isVisible && !hiddenByEngine.contains(mesh)) {
            // Even if hidden, we might want to process its instances if it's a source Mesh
            var instancesTotal = 0
            var instancesVisible = 0
            if (mesh is Mesh) {
                mesh.instances.forEach { instance ->
                    val res = processMeshRecursive(instance, camera, renderHeight, processedMeshes)
                    instancesTotal += res.total
                    instancesVisible += res.visible
                }
            }
            return ProcessResult(instancesTotal, instancesVisible)
        }

        val isVisibleByPerformance = checkMeshVisibleFast(mesh, camera, renderHeight)
        
        if (!isVisibleByPerformance) {
            if (mesh.isVisible) {
                mesh.isVisible = false
                hiddenByEngine.add(mesh)
            }
        } else {
            visible = 1
            if (hiddenByEngine.contains(mesh)) {
                mesh.isVisible = true
                hiddenByEngine.remove(mesh)
            }
        }

        // Process instances if this is a source mesh
        if (mesh is Mesh) {
            mesh.instances.forEach { instance ->
                val res = processMeshRecursive(instance, camera, renderHeight, processedMeshes)
                total += res.total
                visible += res.visible
            }
        }

        return ProcessResult(total, visible)
    }

    /**
     * Fast visibility check based on bounding sphere projection.
     */
    private fun checkMeshVisibleFast(
        mesh: AbstractMesh,
        camera: ArcRotateCamera,
        screenHeight: Float
    ): Boolean {
        // Ensure we have an up-to-date world matrix for accurate bounding info,
        // especially for instances, billboarded meshes, or moving objects.
        try {
            mesh.computeWorldMatrix(true)
        } catch (_: Throwable) {
            // Ignore errors during matrix computation
        }

        val boundingInfo = try { mesh.getBoundingInfo() } catch (_: Throwable) { return true }
        
        // Final safety check: if it's ground, it's always visible.
        // (This is already handled in processMeshRecursive, but added here for robustness).
        if (mesh.isIncullable() || mesh.sourceMesh?.isIncullable() == true) return true
        
        val sphere = boundingInfo.boundingSphere
        
        val center = sphere.centerWorld
        val radius = sphere.radiusWorld
        
        // Massive mesh handling (e.g. ground map, flower map)
        if (radius > 100f) {
            // Check if camera is inside bounding box
            if (boundingInfo.boundingBox.intersectsPoint(camera.position)) return true
            
            // Otherwise check distance to bounding box. 
            // We cull the entire mesh only if it's quite far away.
            val distToBox = distanceToBoundingBox(camera.position, boundingInfo.boundingBox)
            return distToBox < 200f // 200 units is a safe distance for massive meshes
        }

        // Distance from camera to sphere center
        val dist = Vector3.Distance(center, camera.position)
        
        // If we're inside the sphere or very close, it should be visible
        if (dist <= radius) return true
        
        // Projected size = (WorldSize * FocalLength) / Distance
        // FocalLength in pixels = (ScreenHeight / 2) / tan(FOV / 2)
        val focalLength = (screenHeight / 2.0f) / tan(camera.fov / 2.0).toFloat()
        
        // Sphere diameter is 2 * radius
        val projectedSize = (radius * 2.0f * focalLength) / dist
        
        return projectedSize >= minSizeThreshold
    }

    /**
     * Calculates the distance from a point to an axis-aligned bounding box.
     */
    private fun distanceToBoundingBox(point: Vector3, box: BoundingBox): Float {
        val min = box.minimumWorld
        val max = box.maximumWorld
        
        val dx = max(0f, max(min.x - point.x, point.x - max.x))
        val dy = max(0f, max(min.y - point.y, point.y - max.y))
        val dz = max(0f, max(min.z - point.z, point.z - max.z))
        
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * Disposes the performance engine and restores visibility to all meshes.
     */
    fun dispose() {
        hiddenByEngine.forEach { it.isVisible = true }
        hiddenByEngine.clear()
        objectMeshes.clear()
        worldMeshes = emptyArray()
    }
}
