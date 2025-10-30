package lib

import kotlin.js.JsName

@JsModule("@babylonjs/core/Misc/fileTools.js")
@JsNonModule
@JsName("FileToolsOptions")
external object BabylonFileToolsOptions {
    /**
     * Gets or sets a function used to pre-process url before using them to load assets
     * (applies to general file loads, including fetch/XHR)
     */
    var PreprocessUrl: ((String) -> String)?

    /**
     * Gets or sets the base URL to use to load assets
     */
    var BaseUrl: String

    /**
     * Gets or sets the base URL to use to load scripts (JS and WASM)
     */
    var ScriptBaseUrl: String

    /**
     * Gets or sets a function used to pre-process script url before using them to load (JS and WASM)
     */
    var ScriptPreprocessUrl: ((String) -> String)?
}
