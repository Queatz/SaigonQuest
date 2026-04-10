package main.components.audio

import kotlinx.browser.document
import org.w3c.dom.HTMLAudioElement
import main.components.AudioSettings

/**
 * Global sound manager for one-shot sound effects.
 */
object SoundManager {
    private val activeSounds = mutableSetOf<HTMLAudioElement>()

    /**
     * Play a one-shot sound from a URL.
     * 
     * @param url The URL of the sound file to play.
     */
    fun playSound(url: String) {
        console.log("[SoundManager] playSound requested for URL: $url")
        // Always play sounds regardless of music setting
        // Music setting is specifically for background music
        
        try {
            val audio = document.createElement("audio") as HTMLAudioElement
            audio.src = url
            activeSounds.add(audio)
            
            audio.onended = {
                activeSounds.remove(audio)
                null
            }
            
            console.log("[SoundManager] Playing audio: $url")
            audio.play().then {
                console.log("[SoundManager] Audio playback started successfully: $url")
            }.catch { err ->
                activeSounds.remove(audio)
                console.warn("[SoundManager] Autoplay prevented or error playing sound: $err")
            }
        } catch (t: Throwable) {
            console.error("[SoundManager] Failed to play sound from URL: $url", t)
        }
    }

    /**
     * Stop all sounds currently playing through the SoundManager.
     */
    fun stopAllSounds() {
        console.log("[SoundManager] Stopping all sounds (${activeSounds.size} active)")
        activeSounds.forEach { 
            try {
                it.pause()
                it.currentTime = 0.0
            } catch (t: Throwable) {
                console.warn("[SoundManager] Failed to stop an audio element", t)
            }
        }
        activeSounds.clear()
    }
}
