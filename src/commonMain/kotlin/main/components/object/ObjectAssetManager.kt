package main.components.`object`

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lib.AbstractMesh
import lib.Mesh
import lib.Node
import lib.Scene
import lib.SceneLoader
import lib.TransformNode
import lib.Vector3

/**
 * Manages 3D model assets for objects in the scene.
 * Handles loading, caching, and providing access to 3D models from GLB files.
 *
 * @param scene The BabylonJS scene
 */
class ObjectAssetManager(private val scene: Scene) {
    
    // Map of file paths to loaded meshes
    private val modelCache = mutableMapOf<String, Map<String, AbstractMesh>>()
    
    // Coroutine scope for async operations
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    // Base URL for model files
    private val baseUrl = "/raw/data/"
    
    /**
     * Gets a node from a file and collection.
     * If the file is not loaded yet, it will be loaded asynchronously.
     * The node will contain all child meshes from the requested collection.
     *
     * @param file The file name without .glb extension
     * @param collection The collection name in the file
     * @param onSuccess Callback when the node is successfully retrieved
     * @param onError Callback when an error occurs
     */
    fun getNode(
        file: String,
        collection: String,
        onSuccess: (Node) -> Unit,
        onError: (String) -> Unit
    ) {
        // If file is empty, call onError
        if (file.isEmpty()) {
            onError("File name is empty")
            return
        }
        
        // Check if the file is already loaded
        if (modelCache.containsKey(file)) {
            val collections = modelCache[file]
            
            // Log all available collections for debugging
            val availableCollections = collections?.keys?.joinToString(", ") ?: "none"
            console.log("[ASSET_MANAGER] Available cached collections in $file: $availableCollections")
            
            // Check if the collection exists in the file (case-sensitive check)
            if (collections != null && collections.containsKey(collection)) {
                console.log("[ASSET_MANAGER] Found requested collection in cache: $collection")
                // Return a clone of the mesh
                val mesh = collections[collection]
                if (mesh != null) {
                    // Create a parent mesh to hold the cloned mesh
                    val parentMesh = mesh.clone("parent_${collection}_${js("Date.now()")}")
                    parentMesh.isVisible = false
                    
                    // Store the original position, rotation, and scaling before cloning
                    val originalPosition = mesh.position.clone()
                    val originalRotation = mesh.rotation.clone()
                    val originalScaling = mesh.scaling.clone()
                    
                    console.log("[ASSET_MANAGER] Original cached mesh position: (${originalPosition.x}, ${originalPosition.y}, ${originalPosition.z})")
                    
                    // Create a clone or instance of the mesh
                    val clonedMesh = if (mesh is Mesh) {
                        console.log("[ASSET_MANAGER] Creating instance of cached mesh: ${mesh.name}")
                        mesh.createInstance("instance_${collection}_${js("Date.now()")}")
                    } else {
                        console.log("[ASSET_MANAGER] Creating clone of cached mesh: ${mesh.name}")
                        mesh.clone("clone_${collection}_${js("Date.now()")}")
                    }
                    
                    // Make the cloned mesh a child of the parent mesh
                    clonedMesh.parent = parentMesh
                    
                    // Preserve the original position, rotation, and scaling relative to the parent
                    clonedMesh.position = Vector3.Zero()
                    clonedMesh.rotation = Vector3.Zero()
                    clonedMesh.scaling = Vector3(1f, 1f, 1f)
                    
                    console.log("[ASSET_MANAGER] Set cloned cached mesh position: (${clonedMesh.position.x}, ${clonedMesh.position.y}, ${clonedMesh.position.z})")
                    
                    // Make sure the cloned mesh is visible
                    clonedMesh.isVisible = true
                    console.log("[ASSET_MANAGER] Created visible clone/instance for cached mesh: ${mesh.name}")
                    
                    // Log the properties of the cloned mesh
                    console.log("[ASSET_MANAGER] Cloned mesh properties: name=${clonedMesh.name}, visible=${clonedMesh.isVisible}, position=${clonedMesh.position}, scaling=${clonedMesh.scaling}")
                    
                    console.log("[ASSET_MANAGER] Created parent node with child mesh")
                    
                    onSuccess(parentMesh)
                } else {
                    onError("Collection '$collection' found but mesh is null")
                }
            } else if (collections != null) {
                onError("Collection '$collection' not found in file '$file'. Available collections: $availableCollections")
            } else {
                onError("No collections found for file '$file'")
            }
        } else {
            // Load the file
            loadFile(file, collection, onSuccess, onError)
        }
    }
    
    /**
     * Loads a GLB file and caches its meshes.
     *
     * @param file The file name without .glb extension
     * @param requestedCollection The collection name to return after loading
     * @param onSuccess Callback when the node is successfully loaded
     * @param onError Callback when an error occurs
     */
    private fun loadFile(
        file: String,
        requestedCollection: String,
        onSuccess: (Node) -> Unit,
        onError: (String) -> Unit
    ) {
        console.log("[ASSET_MANAGER] Loading file: $file.glb")
        
        coroutineScope.launch {
            try {
                // Use LoadAssetContainerAsync instead of SceneLoader.Load
                val assetContainerPromise = SceneLoader.LoadAssetContainerAsync(
                    baseUrl,
                    "$file.glb",
                    scene
                )
                
                // Handle the Promise result
                assetContainerPromise.then { assetContainer ->
                    console.log("[ASSET_MANAGER] Asset container loaded successfully")

                    // First, try to find the requested node by name
                    var foundRequestedNode = false

                    // Try to find the node in rootNodes
                    val allNodes = assetContainer.getNodes()
                    console.log("[ASSET_MANAGER] Found ${allNodes.size} root nodes in asset container")

                    // Look for the requested node by name
                    val requestedNode = allNodes.find { node -> node.name == requestedCollection }

                    if (requestedNode != null) {
                        console.log("[ASSET_MANAGER] Found node by name: ${requestedNode.name}")
                        console.log("[ASSET_MANAGER]", requestedNode)

                        // Check if the node is an AbstractMesh
                        if (requestedNode is AbstractMesh) {
                            val meshNode = requestedNode

                            // Make the original mesh invisible
                            meshNode.isVisible = false

                            // Create a clone or instance
                            val clonedMesh = if (meshNode is Mesh) {
                                console.log("[ASSET_MANAGER] Creating instance of mesh: ${meshNode.name}")
                                meshNode.createInstance("instance_${requestedCollection}_${js("Date.now()")}")
                            } else {
                                console.log("[ASSET_MANAGER] Creating clone of mesh: ${meshNode.name}")
                                meshNode.clone("clone_${requestedCollection}_${js("Date.now()")}")
                            }

                            // Make sure the cloned mesh is visible
                            clonedMesh.isVisible = true
                            console.log("[ASSET_MANAGER] Created visible clone/instance for: $requestedCollection")

                            // Return the cloned mesh
                            onSuccess(clonedMesh)
                            foundRequestedNode = true
                        } else {
                            console.log("[ASSET_MANAGER] Node is not an AbstractMesh: ${requestedNode.name}")

                            // Try to find child meshes
                            val childMeshes = requestedNode.getChildMeshes()

                            if (childMeshes.size > 0) {
                                console.log("[ASSET_MANAGER] Found ${childMeshes.size} child meshes for node: ${requestedNode.name}")

                                val basePosition = childMeshes[0].position.clone()
                                val baseRotation = childMeshes[0].rotation.clone()
                                val baseScaling = childMeshes[0].scaling.clone()
                                
                                // Create a parent mesh to hold all child meshes
                                val node = TransformNode("parent_${requestedCollection}_${js("Date.now()")}", scene)

                                // Process all child meshes, not just the first one
                                childMeshes.forEach { childMesh ->
                                    console.log("[ASSET_MANAGER] Processing child mesh: ${childMesh.name}")

                                    // Make the original mesh invisible
                                    childMesh.isVisible = false

                                    // Create a clone or instance
                                    console.log("[ASSET_MANAGER] Creating clone of child mesh: ${childMesh.name}")
                                    val clonedMesh = childMesh.clone("clone_${requestedCollection}_child_${js("Date.now()")}")
                                    
                                    // Store the original position, rotation, and scaling before parenting
                                    val originalPosition = childMesh.position.clone()
                                    val originalRotation = childMesh.rotation.clone()
                                    val originalScaling = childMesh.scaling.clone()
                                    
                                    console.log("[ASSET_MANAGER] Original mesh position: (${originalPosition.x}, ${originalPosition.y}, ${originalPosition.z})")
                                    
                                    // Make the cloned mesh a child of the parent mesh
                                    clonedMesh.parent = node
                                    
                                    // Preserve the original position, rotation, and scaling relative to the parent
                                    clonedMesh.position = Vector3.Zero()
                                    clonedMesh.rotation = Vector3.Zero()
                                    clonedMesh.scaling = Vector3(1f, 1f, 1f)
                                    
                                    console.log("[ASSET_MANAGER] Set cloned mesh position: (${clonedMesh.position.x}, ${clonedMesh.position.y}, ${clonedMesh.position.z})")

                                    // Make sure the cloned mesh is visible
                                    clonedMesh.isVisible = true
                                    console.log("[ASSET_MANAGER] Added cloned mesh to parent: ${childMesh.name}")
                                }
                                
                                console.log("[ASSET_MANAGER] Created parent node with ${childMeshes.size} child meshes")
                                
                                // Return the parent mesh as the node
                                onSuccess(node)
                                foundRequestedNode = true
                            } else {
                                console.log("[ASSET_MANAGER] No child meshes found for node: ${requestedNode.name}")
                            }
                        }
                    }

                    // If we found and processed the requested node, return early
                    if (foundRequestedNode) {
                        return@then
                    } else {
                        console.error("[ASSET_MANAGER] Did not find node: $requestedCollection")
                    }
                }.catch { error ->
                    console.error("[ASSET_MANAGER] Error loading file", error)
                    onError("Failed to load file '$file.glb': ${error.message ?: "Unknown error"}")
                }
            } catch (e: Exception) {
                console.error("[ASSET_MANAGER] Exception loading file", e)
                onError("Exception loading file '$file.glb': ${e.message ?: "Unknown error"}")
            }
        }
    }
    
    /**
     * Disposes of all cached meshes and clears the cache.
     */
    fun dispose() {
        modelCache.forEach { (_, collections) ->
            collections.forEach { (_, mesh) ->
                mesh.dispose()
            }
        }
        modelCache.clear()
        console.log("[ASSET_MANAGER] Disposed all cached meshes")
    }
}
