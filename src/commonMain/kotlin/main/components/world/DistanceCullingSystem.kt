package main.components.world

import lib.AbstractMesh
import lib.Matrix
import lib.PBRCustomMaterial
import lib.PBRMaterial
import lib.Scene
import lib.Vector3
import main.components.SceneManager

/**
 * System that manages distance-based culling for massive meshes.
 * This allows "partially visible" single meshes by culling fragments that are too far from the camera.
 */
class DistanceCullingSystem(private val scene: Scene) {
    private val massiveMeshes = mutableSetOf<AbstractMesh>()
    private var maxDistance = 150f // Default max visibility distance

    /**
     * Registers a mesh for distance-based culling.
     */
    fun registerMesh(mesh: AbstractMesh, customMaxDistance: Float? = null) {
        val dist = customMaxDistance ?: maxDistance

        // Ensure we have a PBRCustomMaterial and add our effects to it
        val customMaterial = MaterialCustomizer.getOrCreateCustomMaterial(mesh, scene) ?: return
        
        // Add uniforms safely
        MaterialCustomizer.safeAddUniform(customMaterial, "maxVisibilityDistance", "float")
        MaterialCustomizer.safeAddUniform(customMaterial, "cameraWorldPos", "vec3")
        
        // Add shader code
        MaterialCustomizer.appendFragmentDefinitions(customMaterial, """
            precision mediump float;
            
            // Hash function for dithering
            float hash(vec2 p) {
                vec3 p3 = fract(vec3(p.xyx) * 0.1031);
                p3 += dot(p3, p3.yzx + 33.33);
                return fract((p3.x + p3.y) * p3.z);
            }

            float applyDistanceCulling(float alpha) {
                float dist = distance(vPositionW, cameraWorldPos);
                float startDist = maxVisibilityDistance * 0.9; // Start fading at 90% of max distance
                
                if (dist > maxVisibilityDistance) {
                    discard;
                } else if (dist > startDist) {
                    // Smooth dithered transition
                    float coverage = 1.0 - (dist - startDist) / (maxVisibilityDistance - startDist);
                    float threshold = hash(gl_FragCoord.xy);
                    if (coverage < threshold) {
                        discard;
                    }
                }
                return alpha;
            }
        """.trimIndent())
        
        MaterialCustomizer.appendFragmentAlphaCode(customMaterial, """
            alpha = applyDistanceCulling(alpha);
        """.trimIndent())
        
        // Bind uniforms
        // We only add the observer once
        val dyn = customMaterial.asDynamic()
        if (dyn._distanceCullingObserved == null) {
            dyn._distanceCullingObserved = true
            customMaterial.onBindObservable.add { _ ->
                val effect = customMaterial.getEffect() ?: return@add
                val camera = scene.activeCamera ?: return@add
                
                effect.setFloat("maxVisibilityDistance", dist)
                effect.setVector3("cameraWorldPos", camera.globalPosition)
            }
        }
        
        massiveMeshes.add(mesh)
    }

    /**
     * Disposes the system and restores original materials.
     */
    fun dispose() {
        massiveMeshes.forEach { mesh ->
            val metadata = mesh.metadata
            if (metadata?.originalMaterial != null) {
                mesh.material = metadata.originalMaterial!!
            }
        }
        massiveMeshes.clear()
    }
}
