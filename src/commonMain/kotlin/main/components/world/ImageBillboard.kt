package main.components.world

import lib.Mesh
import lib.MeshBuilder
import lib.Node
import lib.PBRMaterial
import lib.Scene
import lib.Texture
import lib.TransformNode
import lib.Material
import lib.CreatePlaneOptions

class ImageBillboard(
    private val imageUrl: String,
    private val scaleValue: Float,
) : WorldObject() {
    private var rootNode: TransformNode? = null
    private var plane: Mesh? = null

    override fun render(scene: Scene): Node? {
        val root = TransformNode("image-billboard-root-${id}", scene)
        root.position = position
        root.rotation = rotation
        root.scaling = scale
        
        // Create plane (1x1 base size)
        val planeMesh = MeshBuilder.CreatePlane(
            "image-plane-${id}",
            object : CreatePlaneOptions {
                override val size = 1f
            },
            scene
        )
        planeMesh.parent = root
        
        val material = PBRMaterial("image-mat-${id}", scene)
        // Requirement: Nearest filtering for pixel art
        val texture = Texture(imageUrl, scene, samplingMode = Texture.NEAREST_SAMPLINGMODE)
        
        // Auto-calculate aspect ratio once texture loads
        texture.onLoadObservable.add {
            val size = texture.getSize()
            val aspect = size.width.toFloat() / size.height.toFloat()
            // Apply scale and aspect ratio
            planeMesh.scaling.x = aspect * scaleValue
            planeMesh.scaling.y = scaleValue
            
            // Re-position plane to be grounded (Y axis is center-aligned by default in Babylon CreatePlane)
            planeMesh.position.y = scaleValue / 2f
        }

        material.albedoTexture = texture
        material.useAlphaFromAlbedoTexture = true
        material.alphaTestCutoff = 0.5f // Requirement: 0.5 alpha cutoff
        material.transparencyMode = Material.MATERIAL_ALPHATEST // Requirement: transparency
        material.metallic = 0f
        material.roughness = 1f
        material.backFaceCulling = false
        
        planeMesh.material = material
        
        // Requirement: Billboard (Y-axis)
        setupAnchorBillboard(planeMesh, scene, billboardMode = Mesh.BILLBOARDMODE_Y)
        
        this.rootNode = root
        this.plane = planeMesh
        return root
    }

    override fun dispose() {
        plane?.dispose()
        rootNode?.dispose()
    }
}
