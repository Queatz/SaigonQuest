package main.components.world

import kotlinx.coroutines.await
import lib.DepthRenderer
import lib.Matrix
import lib.Scene
import lib.Vector3
import lib.AbstractMesh
import lib.StandardMaterial
import lib.ArcRotateCamera
import main.components.GraphicsSettings.getEngineScalingFactor
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get

/**
 * Depth-based picking utilities.
 *
 * Provides a way to derive a world-space position at a given screen pixel by sampling the
 * scene depth map (which is already configured to respect occlusion discards) and unprojecting
 * that depth via the active camera.
 */
object DepthPicking {

    // Detects if a mesh belongs to a TextBillboard (player/object status, chat, typing, usernames)
    // These meshes should not contribute to depth picking.
    private fun isStatusBillboardMesh(mesh: AbstractMesh): Boolean {
        return try {
            val n = mesh.name
            if (n.startsWith("text-plane-")) return true
            val parentName = try { mesh.parent?.name } catch (_: Throwable) { null }
            parentName?.startsWith("text_container_") == true
        } catch (_: Throwable) {
            false
        }
    }

    // Shared depth renderer (from OcclusionManager or elsewhere)
    var depthRenderer: DepthRenderer? = null

    // Temporary, dedicated depth renderer used during dragging only
    private var pickingDepthRenderer: DepthRenderer? = null

    // Temporary camera used solely for the picking depth renderer
    private var pickingCamera: ArcRotateCamera? = null

    // Synchronization callback for picking camera
    private var pickingCameraSyncCallback: (() -> Unit)? = null

    // Depth-pass material that disables depth writes for excluded meshes
    private var pickingExcludeDepthMaterial: lib.StandardMaterial? = null

    // Track which meshes have override materials applied for the picking depth renderer
    private val overriddenMeshesForPicking = mutableSetOf<lib.AbstractMesh>()

    /**
     * Reads normalized linear depth [0..1] from the depth map at the given screen pixel.
     * Returns null if depth is unavailable (e.g., sky) or on error.
     */
    suspend fun readLinearDepthAt(x: Int, y: Int): Float? {
        try {
            val renderer = pickingDepthRenderer ?: depthRenderer ?: return null
            val depthMap = renderer.getDepthMap()

            // GPU readback origin is bottom-left; UI/pointer coordinates are top-left in render space
            // scene.pointerX/Y are already in render-space coordinates (respecting hardware scaling),
            // so we must not rescale them here. We only need to flip Y to GL's origin.
            val yGL = depthMap.getSize().height - y - 1
            val xGL = x

            // Read a single pixel at (xGL, yGL). Request raw float values (no data conversion)
            val px: Float32Array = try {
                depthMap.readPixels(
                    noDataConversion = true,
                    x = xGL,
                    y = yGL,
                    width = 1,
                    height = 1
                )?.await()?.unsafeCast<Float32Array>() ?: return null.also {
                    console.error("Failed to read depth map pixel at $x,$yGL (null)")
                }
            } catch (e: Throwable) {
                console.error("Failed to read depth map pixel at $x,$yGL", e)
                e.printStackTrace()
                return null
            }

            // First component contains the depth value in [0,1].
            val depth = px[0]
            val clamped = depth.coerceIn(0f, 1f)
            return if (clamped.isFinite()) clamped else null
        } catch (e: Throwable) {
            console.error("Depth readLinearDepthAt error", e)
            e.printStackTrace()
            return null
        }
    }

    /**
     * Unprojects a screen pixel (x,y) at a given normalized linear depth [0..1] into world space.
     * Returns null if camera is missing or unprojection fails.
     */
    fun unprojectAtDepth(
        scene: Scene,
        x: Int,
        y: Int,
        z: Float,
    ): Vector3? {
        return try {
            Vector3.Unproject(
                source = Vector3(x.toFloat(), y.toFloat(), toNonLinearDepth(scene, z)),
                viewportWidth = scene.getEngine().getRenderWidth(),
                viewportHeight = scene.getEngine().getRenderHeight(),
                world = Matrix.Identity(),
                view = scene.getViewMatrix(),
                projection = scene.getProjectionMatrix(),
            )
        } catch (e: Throwable) {
            e.printStackTrace()
            console.error("Unprojection failed at $x,$y with depth $z")
            null
        }
    }

    fun toNonLinearDepth(scene: Scene, linearDepth: Float): Float {
        val zNear = scene.activeCamera?.minZ ?: return 1f
        val zFar = scene.activeCamera?.maxZ ?: return 1f
        return (zFar / (zFar - zNear)) * (1f - zNear / (linearDepth * (zFar - zNear) + zNear))
    }

    /**
     * Convenience: returns the visible world point at the given screen pixel, or null if none.
     */
    suspend fun getVisibleWorldPointAt(
        scene: Scene,
        x: Int,
        y: Int
    ): Vector3? {
        val sx = x / getEngineScalingFactor()
        val sy = y / getEngineScalingFactor()
        val d = readLinearDepthAt(
            x = sx,
            y = sy
        ) ?: return null.also {
            console.error("Failed to read depth map pixel at $x,$y")
        }
        console.error("Depth at $x,$y: $d")
        return unprojectAtDepth(
            scene = scene,
            x = sx,
            y = sy,
            z = d
        )?.also {
            console.error("Visible world point at $x,$y: $it")
        }
    }

    /**
     * Begins a dedicated, world-only depth renderer session for picking during object drag.
     * The provided meshes will be excluded from writing to the picking depth map.
     * Safe to call multiple times; subsequent calls while active are ignored.
     */
    fun beginPickingDepth(
        scene: Scene,
        excludeMeshes: Array<AbstractMesh>,
    ) {
        if (pickingDepthRenderer != null) return
        try {
            // Create a dedicated camera mirroring the main camera for the picking depth pass
            val active = scene.activeCamera
            val renderer = if (active is ArcRotateCamera) {
                val depthCam = ArcRotateCamera(
                    name = "DepthPickingCamera",
                    alpha = active.alpha,
                    beta = active.beta,
                    radius = active.radius,
                    target = active.target,
                    scene = scene
                )
                // Mirror critical camera parameters so unprojection math remains valid
                try { depthCam.targetScreenOffset = active.targetScreenOffset } catch (_: Throwable) {}
                try { depthCam.minZ = active.minZ } catch (_: Throwable) {}
                try { depthCam.maxZ = active.maxZ } catch (_: Throwable) {}
                try { depthCam.fov = active.fov } catch (_: Throwable) {}
                try { depthCam.viewport = active.viewport } catch (_: Throwable) {}
                pickingCamera = depthCam

                // Sync picking camera with active camera
                val sync = {
                    val active = scene.activeCamera
                    if (active is ArcRotateCamera) {
                        depthCam.alpha = active.alpha
                        depthCam.beta = active.beta
                        depthCam.radius = active.radius
                        depthCam.target = active.target
                        depthCam.minZ = active.minZ
                        depthCam.maxZ = active.maxZ
                        depthCam.fov = active.fov
                    }
                }
                scene.registerBeforeRender(sync)
                pickingCameraSyncCallback = sync

                scene.enableDepthRenderer(
                    camera = depthCam,
                    storeNonLinearDepth = false,
                    force32bitsFloat = true,
                )
            } else {
                // Fallback: bind to whatever active camera exists
                scene.enableDepthRenderer(
                    camera = active,
                    storeNonLinearDepth = false,
                    force32bitsFloat = true,
                )
            }

            try { renderer.forceDepthWriteTransparentMeshes = true } catch (_: Throwable) {}
            pickingDepthRenderer = renderer

            // Create a depth-pass material that disables depth writes (thus excluding meshes from depth map)
            val excludeMat = StandardMaterial("PickingDepth_Exclude", scene).also { m ->
                try { m.disableDepthWrite = true } catch (_: Throwable) {}
                try { m.disableColorWrite = true } catch (_: Throwable) {}
                try { m.alpha = 0f } catch (_: Throwable) {}
            }
            pickingExcludeDepthMaterial = excludeMat

            val occlusionManager = WorldLoader.getCurrentOcclusionManager()
            // Try to get the same shared white depth material used by OcclusionManager
            val sharedDepthMat = try {
                occlusionManager?.getSharedWhiteDepthMaterial()
            } catch (_: Throwable) { null }

            // Build an exclusion set for quick lookup
            val excludeSet = excludeMeshes.toSet()

            // Apply overrides: exclude listed meshes, skybox, water, and status billboards;
            // use shared depth material (or per-mesh depth material) for others
            scene.meshes.forEach { mesh ->
                try {
                    if (!mesh.isVisible) return@forEach
                    val shouldExclude = excludeSet.contains(mesh) || mesh.isSkybox() || mesh.isWater() || isStatusBillboardMesh(mesh)
                    if (shouldExclude) {
                        renderer.setMaterialForRendering(mesh, excludeMat)
                        overriddenMeshesForPicking.add(mesh)
                    } else {
                        // Use per-mesh depth material from occlusion manager if available to respect alpha testing
                        val depthMat = occlusionManager?.getDepthMaterialForMesh(mesh) ?: sharedDepthMat
                        if (depthMat != null) {
                            renderer.setMaterialForRendering(mesh, depthMat)
                            overriddenMeshesForPicking.add(mesh)
                        }
                    }
                } catch (t: Throwable) {
                    console.warn("[DepthPicking] Failed to set picking depth material for ${mesh.name}: ${t.message ?: ""}")
                }
            }

            console.log("[DepthPicking] Picking depth renderer created. Overrides applied to " + overriddenMeshesForPicking.size + " meshes.")
        } catch (t: Throwable) {
            console.error("[DepthPicking] Failed to create picking depth renderer", t)
            // Ensure cleanup on failure
            endPickingDepth()
        }
    }

    /**
     * Ends the dedicated picking depth session and restores original state.
     */
    fun endPickingDepth() {
        val renderer = pickingDepthRenderer
        // Restore per-mesh depth materials
        if (renderer != null) {
            overriddenMeshesForPicking.forEach { mesh ->
                try { renderer.setMaterialForRendering(mesh, null) } catch (_: Throwable) {}
            }
        }
        overriddenMeshesForPicking.clear()

        // Unregister sync callback
        pickingCameraSyncCallback?.let {
            try { pickingDepthRenderer?.getCamera()?.getScene()?.unregisterBeforeRender(it) } catch (_: Throwable) {}
        }
        pickingCameraSyncCallback = null

        // Dispose temporary material
        try { pickingExcludeDepthMaterial?.dispose() } catch (_: Throwable) {}
        pickingExcludeDepthMaterial = null

        // Dispose temporary renderer
        try { renderer?.dispose() } catch (_: Throwable) {}
        if (renderer == depthRenderer) {
            // Do not null out the shared renderer reference unexpectedly
        }
        pickingDepthRenderer = null

        // Release temporary camera reference (camera will be GC'd; not using it for rendering)
        pickingCamera = null

        console.log("[DepthPicking] Picking depth renderer disposed.")
    }
}
