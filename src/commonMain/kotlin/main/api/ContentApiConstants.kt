package main.api

@Suppress("UnsafeCastFromDynamic")
object ContentApiConstants {
    // Base URL for the content server (uploads/static)
    // Resolved from process.env at build time via webpack DefinePlugin; falls back to localhost:8090
    val CONTENT_BASE_URL: String by lazy {
        val fromEnv = try {
            js("typeof process !== 'undefined' && process.env ? process.env.CONTENT_SERVER_URL : null") as String?
        } catch (t: Throwable) {
            null
        }
        (fromEnv?.trimEnd('/') ?: "http://localhost:8090").ifBlank { "http://localhost:8090" }
    }
}

