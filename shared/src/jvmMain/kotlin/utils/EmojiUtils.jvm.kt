package utils

import java.util.regex.Pattern

actual object EmojiUtils {
    // In Java 21, \p{IsEmoji_Presentation} matches characters that are emojis by default.
    // This correctly excludes digits and other characters that are not presented as emoji by default.
    // We also include Variation Selector-16 (VS16) for cases where an emoji is not presentation by default
    // but becomes one with the selector (like the heart ❤️).
    private val EMOJI_PATTERN = Pattern.compile("[\\x{1F300}-\\x{1F9FF}\\x{2600}-\\x{27BF}\\x{1F1E6}-\\x{1F1FF}]")
    private val CLEAN_REGEX = Regex("""[\s\u200b\u200d\ufe0e\ufe0f]""")

    actual fun isEmojiOnly(text: String): Boolean {
        if (text.isBlank()) return false
        
        // Remove spaces, ZWJs, and variation selectors (VS15/VS16)
        val clean = text.replace(CLEAN_REGEX, "")
        if (clean.isEmpty()) return false
        
        // Check if every remaining character/code point is an emoji presentation character.
        var i = 0
        while (i < clean.length) {
            val codePoint = clean.codePointAt(i)
            val charStr = String(Character.toChars(codePoint))
            if (!EMOJI_PATTERN.matcher(charStr).matches()) {
                return false
            }
            i += Character.charCount(codePoint)
        }
        
        return true
    }
}
