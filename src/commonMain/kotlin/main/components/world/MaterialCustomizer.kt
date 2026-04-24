package main.components.world

import lib.AbstractMesh
import lib.Material
import lib.MeshMetadata
import lib.PBRCustomMaterial
import lib.PBRMaterial
import lib.Scene
import lib.Gltf

/**
 * Helper to manage PBRCustomMaterial augmentations without overwriting previous ones.
 */
object MaterialCustomizer {
    /**
     * Ensures the mesh has a PBRCustomMaterial and allows adding snippets to it.
     */
    fun getOrCreateCustomMaterial(mesh: AbstractMesh, scene: Scene): PBRCustomMaterial? {
        val currentMaterial = mesh.material
        
        if (currentMaterial is PBRCustomMaterial) {
            return currentMaterial
        }

        if (currentMaterial is PBRMaterial) {
            // Store original material in metadata if not already there
            if (mesh.metadata?.originalMaterial == null) {
                val oldMeta = mesh.metadata
                val newMeta = object : MeshMetadata {
                    override var originalMaterial: Material? = currentMaterial
                    override var isItem: Boolean? = oldMeta?.isItem
                    override var gltf: Gltf? = oldMeta?.gltf
                    override var extras: dynamic = oldMeta?.extras
                    override var incullable: Boolean? = oldMeta?.incullable
                }
                mesh.metadata = newMeta
            }
            
            val customMaterial = PBRCustomMaterial("${currentMaterial.name}-customized", scene)
            
            // Copy properties from original
            customMaterial.albedoColor = currentMaterial.albedoColor
            customMaterial.albedoTexture = currentMaterial.albedoTexture
            customMaterial.metallic = currentMaterial.metallic
            customMaterial.roughness = currentMaterial.roughness
            customMaterial.metallicTexture = currentMaterial.metallicTexture
            customMaterial.roughnessTexture = currentMaterial.roughnessTexture
            customMaterial.bumpTexture = currentMaterial.bumpTexture
            customMaterial.emissiveColor = currentMaterial.emissiveColor
            customMaterial.emissiveIntensity = currentMaterial.emissiveIntensity
            customMaterial.emissiveTexture = currentMaterial.emissiveTexture
            customMaterial.opacityTexture = currentMaterial.opacityTexture
            customMaterial.alpha = currentMaterial.alpha
            customMaterial.transparencyMode = currentMaterial.transparencyMode
            customMaterial.alphaTestCutoff = currentMaterial.alphaTestCutoff
            customMaterial.useAlphaFromAlbedoTexture = currentMaterial.useAlphaFromAlbedoTexture
            customMaterial.unlit = currentMaterial.unlit
            customMaterial.backFaceCulling = currentMaterial.backFaceCulling
            customMaterial.fogEnabled = currentMaterial.fogEnabled
            customMaterial.disableLighting = currentMaterial.disableLighting
            customMaterial.zOffset = currentMaterial.zOffset
            customMaterial.zOffsetUnits = currentMaterial.zOffsetUnits
            customMaterial.needDepthPrePass = true

            mesh.material = customMaterial
            return customMaterial
        }
        
        return null
    }

    /**
     * Appends a snippet to the beginning of the fragment shader.
     */
    fun appendFragmentBegin(material: PBRCustomMaterial, code: String) {
        val dyn = material.asDynamic()
        val existing = dyn._customFragmentBegin as? String ?: ""
        if (!existing.contains(code)) {
            val newCode = existing + "\n" + code
            dyn._customFragmentBegin = newCode
            material.Fragment_Begin(newCode)
        }
    }

    /**
     * Appends a definition snippet to the fragment shader.
     */
    fun appendFragmentDefinitions(material: PBRCustomMaterial, code: String) {
        val dyn = material.asDynamic()
        val existing = dyn._customFragmentDefinitions as? String ?: ""
        if (!existing.contains(code)) {
            val newCode = existing + "\n" + code
            dyn._customFragmentDefinitions = newCode
            material.Fragment_Definitions(newCode)
        }
    }

    /**
     * Appends an alpha snippet to the fragment shader.
     */
    fun appendFragmentAlphaCode(material: PBRCustomMaterial, code: String) {
        val dyn = material.asDynamic()
        val existing = dyn._customFragmentAlphaCode as? String ?: ""
        if (!existing.contains(code)) {
            val newCode = existing + "\n" + code
            dyn._customFragmentAlphaCode = newCode
            material.Fragment_Custom_Alpha(newCode)
        }
    }

    /**
     * Safely adds a uniform to the material, avoiding duplicates.
     */
    fun safeAddUniform(material: PBRCustomMaterial, name: String, kind: String) {
        val dyn = material.asDynamic()
        if (dyn._customUniforms == null) {
            dyn._customUniforms = mutableSetOf<String>()
        }
        val set = dyn._customUniforms as MutableSet<String>
        if (!set.contains(name)) {
            material.AddUniform(name, kind)
            set.add(name)
        }
    }
}
