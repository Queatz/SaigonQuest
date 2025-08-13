package main.components.player

import lib.Color3
import lib.CreateDiscOptions
import lib.Mesh
import lib.MeshBuilder
import lib.Scene
import lib.StandardMaterial
import lib.Vector3
import kotlin.math.PI

/**
 * Creates a transient move-circle indicator that scales and fades out.
 */
object MoveCircleEffect {
    fun create(scene: Scene, position: Vector3): Mesh {
        val mesh = MeshBuilder.CreateDisc("moveCircle",
            object : CreateDiscOptions {
                override val radius = 0.5f
                override val tessellation = 32
            }, scene)
        mesh.position = Vector3(position.x, position.y + 0.01f, position.z)
        mesh.rotation = Vector3(PI.toFloat() / 2f, 0f, 0f)

        val material = StandardMaterial("moveCircleMaterial", scene)
        material.diffuseColor = Color3(0f, 0.8f, 1f)
        material.alpha = 0.7f
        material.backFaceCulling = false
        mesh.material = material

        var scaleValue = 1.0f
        val delta = FrameDeltaTracker()
        val speed = 1.2f
        scene.registerBeforeRender {
            if (scaleValue <= 0f) return@registerBeforeRender
            val dt = delta.nextDeltaSeconds()
            scaleValue -= (speed * dt).toFloat()
            if (scaleValue <= 0f) {
                mesh.dispose()
            } else {
                mesh.scaling = Vector3(scaleValue, scaleValue, scaleValue)
                (mesh.material as? StandardMaterial)?.alpha = scaleValue * 0.7f
            }
        }
        return mesh
    }
}
