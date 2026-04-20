package main.components.world

import lib.AbstractMesh
import lib.Color3
import lib.Material
import lib.Mesh
import lib.MeshBuilder
import lib.Node
import lib.PBRMaterial
import lib.Scene
import lib.Texture
import lib.TextureOptions
import lib.TransformNode
import lib.Vector3
import lib.CreatePlaneOptions
import models.Item

/**
 * Represents an inventory item that has been dropped in the world.
 * The item is rendered as a plane that always faces the camera with Y-axis locked.
 */
class InventoryObject(
    private val item: Item,
    initialPosition: Vector3,
    private val objectId: String
) : WorldObject() {
    private var rootNode: TransformNode? = null
    private var mesh: AbstractMesh? = null
    private var material: PBRMaterial? = null
    
    init {
        // Set the ID and position from constructor parameters
        id = objectId
        position = initialPosition
    }
    
    override fun render(scene: Scene): Node? {
        // Create a root transform node
        val root = TransformNode("inventory-root-${objectId}", scene)
        root.position = position
        
        // Create a plane that will always face the camera
        val plane = MeshBuilder.CreatePlane(
            "item-${objectId}",
            object : CreatePlaneOptions {
                override val width = .25f
                override val height = .25f
            },
            scene
        )
        
        // Set plane as child of root node
        plane.parent = root
        
        // Float the item above the ground (center of 0.25x0.25 plane + small offset)
        plane.position.y = 0.2f
        
        // Create material with item texture
        val material = PBRMaterial("item-material-${objectId}", scene)
        
        // Create texture with basic options
        val texture = Texture(
            url = "/raw/data/spritesheets/${item.file}.png",
            scene = scene,
            options = object : TextureOptions {
                override val samplingMode = Texture.NEAREST_SAMPLINGMODE
                override val hasAlpha = true
            }
        )
        
        // Set UV properties directly on the texture
        texture.uScale = 0.1f  // For 10x10 sprite sheet (320x320 / 32x32)
        texture.vScale = 0.1f
        texture.uOffset = (item.assetIndex % 10) * 0.1f
        texture.vOffset = (item.assetIndex / 10) * 0.1f

        // Assign the texture to the material
        material.albedoColor = Color3(1f, 1f, 1f)
        material.albedoTexture = texture
        material.emissiveTexture = texture

        // Make material transparent where texture is transparent
        material.backFaceCulling = false
        material.metallic = 0.0f       // Low metallic value for non-metallic appearance
        material.roughness = 0.4f      // Medium roughness for slight diffusion of light
        material.transparencyMode = Material.MATERIAL_ALPHATEST
        material.usePhysicalLightFalloff = true  // Enable physical light falloff

        // Apply material to plane
        plane.material = material
        
        // Set billboard mode using the robust anchor system
        setupAnchorBillboard(plane, scene)
        
        // Store references
        rootNode = root
        mesh = plane
        this.material = material
        
        return root
    }
    
    override fun update(deltaTime: Float, camera: lib.ArcRotateCamera?) {
    }
    
    override fun dispose() {
        mesh?.dispose()
        material?.dispose()
        rootNode?.dispose()
    }
}
