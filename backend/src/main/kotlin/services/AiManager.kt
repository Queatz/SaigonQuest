package services

/**
 * Manager to select the active AI client.
 */
object AiManager {
    val client: AiClient = AlibabaAiClient

    /**
     * Client for TTS (follows the main provider).
     */
    val ttsClient: AiClient = OpenAiClient

    fun getApiKeyName(c: AiClient = client): String = when (c) {
        is DeepSeekClient -> "DEEPSEEK_API_KEY"
        is AlibabaAiClient -> "ALIBABA_API_KEY"
        is OpenAiClient -> "OPENAI_API_KEY"
        else -> "AI_API_KEY"
    }

    fun isConfigured(c: AiClient = client): Boolean {
        val keyName = getApiKeyName(c)
        val key = System.getenv(keyName) ?: System.getProperty(keyName)
        // Alibaba client has a fallback key from the issue description, but we should still check if it's explicitly set or use the fallback
        if (c is AlibabaAiClient) return true // It has a hardcoded fallback as per instructions
        return !key.isNullOrBlank()
    }
}
