package main.components.player

import lib.emojiRegex

actual object PlayerUiJs {
    actual fun isEmojiOnly(text: String): Boolean {
        if (text.isBlank()) return false
        
        // Use emoji-regex npm library for robust detection
        val regex = emojiRegex()
        
        // Replace all emojis using the native String.replace(RegExp, String)
        // We use asDynamic here to access the JS String.replace(RegExp, String) overload
        // which is not directly available in the Kotlin String API but is required for
        // correct handling of the emoji regex.
        val withoutEmojis = (text.asDynamic()).replace(regex, "") as String
        
        // Also remove spaces, ZWJs, and variation selectors
        val clean = withoutEmojis.replace(Regex("""[\s\u200b\u200d\ufe0e\ufe0f]""", RegexOption.IGNORE_CASE), "")
        
        return clean.isEmpty()
    }
}
