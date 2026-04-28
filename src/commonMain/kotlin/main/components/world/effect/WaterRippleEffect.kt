package main.components.world.effect

import lib.Color3
import lib.CreatePlaneOptions
import lib.Material
import lib.MeshBuilder
import lib.Scene
import lib.StandardMaterial
import lib.Texture
import lib.TextureOptions
import lib.Vector3
import main.components.audio.SpatialSoundManager
import main.components.player.FrameDeltaTracker
import kotlin.math.PI

/**
 * Creates a transient water ripple effect that scales up and fades out.
 */
object WaterRippleEffect {
    fun create(scene: Scene, position: Vector3) {
        val mesh = MeshBuilder.CreatePlane("waterRipple",
            object : CreatePlaneOptions {
                override val size = 1f
                override val updatable = false
            }, scene)
        
        // Place slightly above water surface to avoid Z-fighting
        mesh.position.copyFromFloats(position.x, position.y + 0.05f, position.z)
        mesh.rotation.copyFromFloats(PI.toFloat() / 2f, 0f, 0f)
        mesh.isPickable = false

        val material = StandardMaterial("waterRippleMaterial", scene)
        material.diffuseTexture = Texture(
            "raw/data/scene/water ripple.png",
            scene,
            object : TextureOptions {
                override val samplingMode = Texture.NEAREST_SAMPLINGMODE
                override val hasAlpha = true
            }
        )
        material.useAlphaFromDiffuseTexture = true
        material.backFaceCulling = false
        material.alphaMode = Material.MATERIAL_ALPHATEST
        material.diffuseColor = Color3(1f, 1f, 1f)
        material.specularColor = Color3(0f, 0f, 0f)
        material.disableDepthWrite = true
        
        // Add spatial sound for the splash
        val splashSound = SpatialSoundManager.playOneShot(
            name = "water_splash",
            url = "/raw/sounds/water_splash.wav",
            scene = scene,
            mesh = mesh,
            volume = 0.4,
            maxDistance = 30.0,
        )
        
        var scaleValue = 0.5f
        var alphaValue = 0.8f
        material.alpha = alphaValue
        mesh.material = material
        val delta = FrameDeltaTracker()
        val expandSpeed = 2.5f
        val fadeSpeed = 2f

        var callback: (() -> Unit)? = null
        callback = {
            val dt = delta.nextDeltaSeconds()
            scaleValue += (expandSpeed * dt).toFloat()
            alphaValue -= (fadeSpeed * dt).toFloat()

            if (alphaValue <= 0f) {
                callback?.let { scene.unregisterBeforeRender(it) }
                mesh.dispose()
                material.dispose()
                SpatialSoundManager.safeDispose(splashSound)
            } else {
                mesh.scaling.set(scaleValue, scaleValue, scaleValue)
                material.alpha = alphaValue
            }
        }
        scene.registerBeforeRender(callback)
    }
}
