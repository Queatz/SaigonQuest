package main.components.player

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class PlayerUiTest {
    @Test
    fun testIsEmojiOnly() {
        assertTrue(PlayerUi.isEmojiOnly("😀"))
        assertTrue(PlayerUi.isEmojiOnly("👨‍👩‍👧‍👦"))
        assertFalse(PlayerUi.isEmojiOnly("Hello 😀"))
        assertFalse(PlayerUi.isEmojiOnly(""))
    }
}
