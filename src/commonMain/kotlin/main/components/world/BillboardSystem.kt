package main.components.world

import lib.AbstractMesh
import lib.Mesh
import lib.Node
import lib.Scene
import lib.TransformNode
import lib.Vector3
import kotlin.math.abs

/**
 * Robustly check if a node or its metadata has a specific extra/property.
 */
fun Node.isBillboard(): Boolean = hasExtra("billboard")

fun Node.isSkybox(): Boolean = hasExtra("skybox")
fun Node.isWater(): Boolean = hasExtra("water")
fun Node.isFlicker(): Boolean = hasExtra("flicker")
fun Node.isStartPosition(): Boolean = hasExtra("start")
fun Node.isPassable(): Boolean = hasExtra("passable")

fun Node.getRadius(): Float? {
    val value = getExtraValue("radius") ?: return null
    return (value as? Number)?.toFloat() ?: (value as? String)?.toFloatOrNull()
}

fun Node.getCameraName(): String? {
    return getExtraValue("camera") as? String
}

/**
 * Configures billboard mode for a mesh using a robust anchor-based approach.
 * This avoids issues with parent transforms (like negative scaling) and 
 * provides a centered pivot point without moving the mesh.
 * 
 * @param mesh The mesh to billboard
 * @param scene The BabylonJS scene
 * @param useParent If true, the anchor will be parented to the mesh's original parent
 * @param billboardMode The billboard mode to use (default: BILLBOARDMODE_Y)
 */
fun setupAnchorBillboard(
    mesh: AbstractMesh,
    scene: Scene? = null,
    useParent: Boolean = true,
    billboardMode: Int = Mesh.BILLBOARDMODE_Y
) {
    // 1. Ensure world matrix is up to date to get accurate position and bounding box
    mesh.computeWorldMatrix(true)
    
    // 2. Get the world scaling and the world center
    val worldScaling = Vector3(1f, 1f, 1f)
    mesh.getWorldMatrix().decompose(scaling = worldScaling)
    
    // We use local center for positioning the mesh relative to anchor,
    // and world center for positioning the anchor itself.
    val worldCenter = mesh.getBoundingInfo().boundingBox.centerWorld.clone()
    val localCenter = mesh.getBoundingInfo().boundingBox.center.clone()
    
    // 3. Create or reuse a transform node to act as the billboard anchor
    // Check if the current parent is already an anchor we created
    val currentParent = mesh.parent
    val anchor = if (currentParent != null && currentParent.name.endsWith("_billboard_anchor")) {
        currentParent as TransformNode
    } else {
        TransformNode("${mesh.name}_billboard_anchor", scene)
    }
    
    if (useParent && mesh.parent != null && mesh.parent != anchor) {
        val originalParent = mesh.parent
        anchor.parent = originalParent
        // When parented, we use the local position of the mesh as the anchor position
        anchor.position = mesh.position.clone().add(localCenter)
    } else if (mesh.parent == null || mesh.parent != anchor) {
        anchor.setAbsolutePosition(worldCenter)
    }
    
    // Ensure positive scaling, especially for Y, to avoid "upside down" meshes
    anchor.scaling = Vector3(
        abs(worldScaling.x),
        abs(worldScaling.y),
        abs(worldScaling.z)
    )
    
    // 4. Set the billboard mode on the anchor
    anchor.billboardMode = billboardMode
    
    // 5. Parent the mesh to the anchor
    mesh.parent = anchor
    
    // 6. Center the mesh relative to the anchor
    // We want the bounding box center to be at the anchor's origin
    mesh.position = Vector3(-localCenter.x, -localCenter.y, -localCenter.z)
    
    // 7. Reset local rotation and scaling relative to anchor to ensure it faces correctly
    // This is crucial: the anchor handles the orientation now.
    mesh.rotationQuaternion = null
    mesh.rotation = Vector3.Zero()
    mesh.scaling = Vector3(1f, 1f, 1f)
    
    // 8. Reset billboard mode on the original mesh to avoid "double billboarding"
    mesh.billboardMode = Mesh.BILLBOARDMODE_NONE
    
    // 9. Diagnostic and quality flags
    mesh.alwaysSelectAsActiveMesh = true
    
    val mat = mesh.material.asDynamic()
    if (mat != null && mat != undefined) {
        mat.backFaceCulling = false
    }
}

/**
 * Robustly check if a node or its metadata has a specific extra/property.
 * Handles GLTF-style (gltf.extras), direct extras, or top-level metadata properties.
 */
fun Node.hasExtra(key: String): Boolean {
    val value = getExtraValue(key)
    return value == true || value == "true"
}

/**
 * Robustly get an extra/property from a node or its metadata.
 */
fun Node.getExtraValue(key: String): dynamic {
    // 1. Check current node metadata
    val meta = metadata?.asDynamic()
    if (meta != null) {
        val extras = meta.gltf?.extras ?: meta.extras ?: meta
        val value = extras[key]
        if (value != undefined && value != null) return value
    }

    // 2. If it's an instance, check the source mesh metadata recursively
    val sourceMesh = asDynamic().sourceMesh as? Node
    if (sourceMesh != null) {
        val sValue = sourceMesh.getExtraValue(key)
        if (sValue != undefined && sValue != null) return sValue
    }

    // 3. Check parent recursively
    return parent?.getExtraValue(key)
}
