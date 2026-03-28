package services

import kotlin.test.Test
import kotlin.test.assertTrue

class AiManagerTest {
    @Test
    fun testDefaultClientIsOpenAi() {
        assertTrue(AiManager.client is OpenAiClient)
    }
}
