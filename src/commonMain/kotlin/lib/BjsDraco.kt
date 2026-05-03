package lib

import kotlin.js.JsName

@JsModule("@babylonjs/core/Meshes/Compression/dracoDecoder.js")
@JsNonModule
@JsName("DracoDecoder")
external object DracoDecoder {
    var DefaultConfiguration: DracoDecoderConfiguration
    val DefaultAvailable: Boolean
    fun ResetDefault(skipDispose: Boolean)
}

@JsModule("@babylonjs/core/Meshes/Compression/dracoCompression.js")
@JsNonModule
@JsName("DracoCompression")
external object DracoCompression {
    var Configuration: DracoCompressionConfiguration
    val DefaultNumWorkers: Int
    fun ResetDefault(skipDispose: Boolean)
}

external interface DracoCompressionConfiguration {
    var decoder: DracoDecoderConfiguration
}

external interface DracoDecoderConfiguration {
    var wasmUrl: String
    var wasmBinaryUrl: String
    var fallbackUrl: String
}

/**
 * Configure BabylonJS Draco decoder URLs to load locally instead of CDN.
 * Must be called before any glTF/GLB assets that require Draco are loaded.
 *
 * @param basePath Public URL path where the Draco files are served (no trailing slash).
 */
fun configureBabylonDracoDecoder(basePath: String) {
    val wrapper = "$basePath/draco_wasm_wrapper_gltf.js"
    val wasm = "$basePath/draco_decoder_gltf.wasm"
    val fallback = "$basePath/draco_decoder_gltf.js"

    // Preferred path: configure via DracoCompression.Configuration.decoder (maps to DracoDecoder.DefaultConfiguration under the hood)
    try {
        val dc = DracoCompression.Configuration
        val dec = dc.decoder
        dec.wasmUrl = wrapper
        dec.wasmBinaryUrl = wasm
        dec.fallbackUrl = fallback
        console.log("[DracoConfig] Configured via DracoCompression.Configuration.decoder", dec.wasmUrl, dec.wasmBinaryUrl, dec.fallbackUrl)
    } catch (t: Throwable) {
        console.warn("[DracoConfig] Failed to configure via DracoCompression.Configuration.decoder", t)
    }

    // Reset DracoCompression default so new instances pick up the updated configuration
    try {
        DracoCompression.ResetDefault(true)
        console.log("[DracoConfig] DracoCompression.ResetDefault(true) called")
    } catch (t: Throwable) {
        console.warn("[DracoConfig] DracoCompression.ResetDefault failed", t)
    }

    // Best-effort: also reset DracoDecoder default if available (safe no-op if mapping differs)
    try {
        DracoDecoder.ResetDefault(true)
        console.log("[DracoConfig] DracoDecoder.ResetDefault(true) called")
    } catch (t: Throwable) {
        console.warn("[DracoConfig] DracoDecoder.ResetDefault failed", t)
    }

    // Also set Babylon CDN base URL to our local base (affects script/asset helpers)
    try {
        BabylonTools.CDNBaseUrl = basePath
        console.log("[DracoConfig] Tools.CDNBaseUrl set to", basePath)
    } catch (t: Throwable) {
        console.warn("[DracoConfig] Failed to set Tools.CDNBaseUrl", t)
    }

    // Keep a guard ScriptPreprocessUrl rewrite in case something else constructs CDN URLs
    try {
        val rewrite: (String) -> String = { url: String ->
            if (url.contains("cdn.babylonjs.com/draco_")) {
                val filename = url.substringAfterLast('/')
                val local = "$basePath/$filename"
                console.log("[DracoConfig] Rewriting CDN URL", url, "->", local)
                local
            } else url
        }
        // Apply to Tools (scripts and wasm via GetBabylonScriptURL)
        BabylonTools.ScriptPreprocessUrl = rewrite
        // Apply to FileTools (generic loads, including XHR/fetch)
        try { BabylonFileToolsOptions.PreprocessUrl = rewrite } catch (_: Throwable) { }
        try { BabylonFileToolsOptions.ScriptPreprocessUrl = rewrite } catch (_: Throwable) { }
        console.log("[DracoConfig] Preprocess URL hooks installed for Draco URLs")
    } catch (t: Throwable) {
        console.warn("[DracoConfig] Failed to set URL preprocess hooks", t)
    }
}
