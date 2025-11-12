package models.voice

import kotlinx.serialization.Serializable

/**
 * Voice audio chunk sent from the browser to the backend over WebSocket.
 * Contains base64-encoded mono PCM16 (LE) at 24 kHz.
 */
@Serializable
data class VoiceAudioChunk(
    val type: String = "audio",
    val b64: String,
)
