package utils

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class EmojiUtilsTest {
    @Test
    fun testIsEmojiOnly() {
        assertTrue(EmojiUtils.isEmojiOnly("👋"))
        assertTrue(EmojiUtils.isEmojiOnly("👋👋"))
        assertTrue(EmojiUtils.isEmojiOnly("👋 👋"))
        assertTrue(EmojiUtils.isEmojiOnly("👨‍👩‍👧‍👦")) // Multi-person emoji (ZWJ)
        assertTrue(EmojiUtils.isEmojiOnly("❤️")) // Heart with variation selector
        
        assertFalse(EmojiUtils.isEmojiOnly("Hello 👋"))
        assertFalse(EmojiUtils.isEmojiOnly("👋 Hello"))
        assertFalse(EmojiUtils.isEmojiOnly("123"))
        assertFalse(EmojiUtils.isEmojiOnly("."))
        assertFalse(EmojiUtils.isEmojiOnly(""))
        assertFalse(EmojiUtils.isEmojiOnly(" "))
    }
}
