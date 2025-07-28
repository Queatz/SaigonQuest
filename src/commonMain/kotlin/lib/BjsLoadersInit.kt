package lib

/**
 * This file provides initialization for BabylonJS loaders.
 * It imports the necessary modules to ensure the loaders are registered.
 */

// External interface to access the BabylonJS loaders module
@JsModule("@babylonjs/loaders")
@JsNonModule
external object BabylonJSLoaders

/**
 * Initializes the BabylonJS loaders.
 * Call this function before using SceneLoader to load 3D models.
 * 
 * This function ensures that the BabylonJS loaders are properly initialized
 * and the BABYLON global object is available.
 */
fun initializeBabylonJSLoaders() {
    // Access the loaders module to ensure it's loaded
    // This will initialize the loaders and make the BABYLON global object available
    // Just referencing the object is enough to ensure the module is loaded
    console.log("BabylonJS loaders initialized")
    BabylonJSLoaders
}
