# QR Code Generation Fix

## Issue Description

The application was experiencing a runtime error when attempting to display a QR code:

```
ERROR
String contains an invalid character
@webpack-internal:///./kotlin/saigon-quest.js:411:25
QrDialog@webpack-internal:///./kotlin/saigon-quest.js:20706:49
```

This error occurred in the `QrDialog` component when trying to convert binary QR code data to a base64 data URL.

## Root Cause

The issue was in the `toByteString` method in `BinaryUtils.kt`, which was directly converting bytes to characters using `byte.toChar()`. This approach can cause problems when byte values don't correspond to valid characters in JavaScript's string encoding (UTF-16).

JavaScript's `btoa()` function expects a binary string where each character's code point is between 0 and 255. When we directly convert a byte to a character using `toChar()`, we might get invalid characters, especially for negative byte values (since Kotlin's Byte is signed, ranging from -128 to 127).

## Solution

The fix was to modify the `toByteString` method to ensure all byte values are properly converted to valid characters that `btoa()` can handle:

```kotlin
private fun toByteString(bytes: ByteArray): String {
    val sb = StringBuilder(bytes.size)
    for (byte in bytes) {
        // Convert to unsigned byte value (0-255) before creating a character
        // This ensures all characters are valid for btoa
        val unsignedByte = byte.toInt() and 0xFF
        sb.append(unsignedByte.toChar())
    }
    return sb.toString()
}
```

By using `byte.toInt() and 0xFF`, we convert each byte to its unsigned value (0-255) before creating a character. This ensures that all characters are valid for the `btoa()` function, preventing the "String contains an invalid character" error.

## Technical Details

1. **Kotlin's Byte vs. JavaScript's expectations**:
   - Kotlin's Byte is signed (-128 to 127)
   - JavaScript's `btoa()` expects characters with code points 0-255

2. **The bitwise AND operation (`and 0xFF`)**:
   - Converts negative bytes to their unsigned equivalent
   - Example: -1 in signed byte becomes 255 in unsigned byte

3. **Impact on QR code generation**:
   - The QR code data from the `@paulmillr/qr` library is a `Uint8Array`
   - This data needs to be properly converted to a string for `btoa()`
   - The fix ensures all byte values are correctly represented in the resulting string

This fix ensures that the QR code functionality works correctly without throwing the "String contains an invalid character" error.
