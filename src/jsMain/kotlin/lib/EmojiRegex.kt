package lib

import kotlin.js.RegExp

@JsModule("emoji-regex")
@JsNonModule
private external val emojiRegexModule: dynamic

/**
 * Robust wrapper for the emoji-regex npm library.
 * It handles the different exports of the module across Node.js (CommonJS) 
 * and Browser (ES Modules/Webpack) environments.
 */
fun emojiRegex(): RegExp {
    // Check if the module itself is the function (CommonJS case, e.g. Node.js)
    if (jsTypeOf(emojiRegexModule) == "function") {
        return (emojiRegexModule)() as RegExp
    }
    
    // Check if the function is the default export (ES Module case, e.g. Webpack in Browser)
    // Some loaders might provide it this way if it's an ES module.
    if (emojiRegexModule != null && jsTypeOf(emojiRegexModule.default) == "function") {
        return (emojiRegexModule.default)() as RegExp
    }
    
    // Fallback: try calling it as a function anyway if we're not sure,
    // which will throw a clear error if it's not callable and doesn't have a default.
    return (emojiRegexModule)() as RegExp
}
