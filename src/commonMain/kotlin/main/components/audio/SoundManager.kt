package main.components.audio

import kotlinx.browser.document
import org.w3c.dom.HTMLAudioElement
import main.components.AudioSettings

/**
 * Global sound manager for one-shot sound effects.
 */
object SoundManager {
    /**
     * Play a one-shot sound from a URL.
     * 
     * @param url The URL of the sound file to play.
     */
    fun playSound(url: String) {
        if (!AudioSettings.isMusicEnabled()) {
            console.log("[SoundManager] Skipping sound playback: Audio is disabled in settings.")
            return
        }

        try {
            val audio = document.createElement("audio") as HTMLAudioElement
            audio.src = url
            audio.play().catch { err ->
                console.warn("[SoundManager] Autoplay prevented or error playing sound: $err")
            }
        } catch (t: Throwable) {
            console.error("[SoundManager] Failed to play sound from URL: $url", t)
        }
    }
}
