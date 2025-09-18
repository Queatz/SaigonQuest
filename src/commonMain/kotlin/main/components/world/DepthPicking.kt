package main.components.world

import kotlinx.coroutines.await
import lib.DepthRenderer
import lib.Matrix
import lib.Scene
import lib.Vector3
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

    var depthRenderer: DepthRenderer? = null

    /**
     * Reads normalized linear depth [0..1] from the depth map at the given screen pixel.
     * Returns null if depth is unavailable (e.g., sky) or on error.
     */
    suspend fun readLinearDepthAt(x: Int, y: Int): Float? {
        try {
            val depthMap = depthRenderer?.getDepthMap() ?: return null

            // GPU readback origin is bottom-left; UI/pointer coordinates are top-left in render space
            // scene.pointerX/Y are already in render-space coordinates (respecting hardware scaling),
            // so we must not rescale them here. We only need to flip Y to GL's origin.
            val yGL = depthMap.getSize().height - y - 1
            val xGL = x

            console.error("[PICK] depthMap.getSize().height = ${depthMap.getSize().height}")

            // Read a single pixel at (xGL, yGL). Request raw values (no data conversion).
            val px: Float32Array = try {
                depthMap.readPixels(
                    noDataConversion = false,
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
}
