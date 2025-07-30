package lib

import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import kotlinx.browser.window

/**
 * Utility functions for working with binary data
 */
object BinaryUtils {
    /**
     * Converts a Uint8Array to a base64 data URL
     * 
     * @param data The Uint8Array to convert
     * @param mimeType The MIME type of the data (e.g., "image/gif")
     * @return A base64 data URL
     */
    fun uint8ArrayToDataUrl(data: Uint8Array, mimeType: String): String {
        // Convert Uint8Array to a byte array
        val byteArray = ByteArray(data.length) { i -> data[i] }
        
        // Convert byte array to base64
        val base64 = window.btoa(toByteString(byteArray))
        
        // Return data URL
        return "data:$mimeType;base64,$base64"
    }
    
    /**
     * Converts a ByteArray to a string that can be used with btoa
     * 
     * @param bytes The ByteArray to convert
     * @return A string representation of the ByteArray
     */
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
}
