package main.api

object ContentApiConstants {
    val CONTENT_BASE_URL: String by lazy {
        val fromEnv = try {
            js("process.env.CONTENT_SERVER_URL") as String?
        } catch (t: Throwable) {
            null
        }
        fromEnv?.trimEnd('/').orEmpty().ifBlank { "http://localhost:8090" }
    }
}
