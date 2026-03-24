package services

/**
 * Manager to select the active AI client.
 */
object AiManager {
    private val provider = System.getenv("AI_PROVIDER") ?: System.getProperty("AI_PROVIDER") ?: "ALIBABA"

    val client: AiClient = when (provider.uppercase()) {
        "DEEPSEEK" -> DeepSeekClient
        "ALIBABA" -> AlibabaAiClient
        else -> AlibabaAiClient
    }

    fun getApiKeyName(): String = when (client) {
        is DeepSeekClient -> "DEEPSEEK_API_KEY"
        is AlibabaAiClient -> "ALIBABA_API_KEY"
        else -> "AI_API_KEY"
    }

    fun isConfigured(): Boolean {
        val keyName = getApiKeyName()
        val key = System.getenv(keyName) ?: System.getProperty(keyName)
        // Alibaba client has a fallback key from the issue description, but we should still check if it's explicitly set or use the fallback
        if (client is AlibabaAiClient) return true // It has a hardcoded fallback as per instructions
        return !key.isNullOrBlank()
    }
}
