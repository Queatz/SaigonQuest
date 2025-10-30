package lib

import kotlin.js.JsName

@JsModule("@babylonjs/core/Misc/tools.js")
@JsNonModule
@JsName("Tools")
external object BabylonTools {
    /**
     * Sets both the script base URL and the assets base URL used by Babylon to load scripts/assets.
     * When Babylon requests a URL that starts with the default CDN (https://cdn.babylonjs.com),
     * it will replace that prefix with this base URL.
     *
     * Example:
     *   CDNBaseUrl = "/babylon/Draco"
     *   "https://cdn.babylonjs.com/draco_wasm_wrapper_gltf.js" -> "/babylon/Draco/draco_wasm_wrapper_gltf.js"
     */
    var CDNBaseUrl: String

    /**
     * Optional URL preprocessing hook. If set, Babylon passes every script URL
     * to this function after applying the base URL logic. The function should return
     * the final URL to load.
     */
    var ScriptPreprocessUrl: ((String) -> String)?
}
