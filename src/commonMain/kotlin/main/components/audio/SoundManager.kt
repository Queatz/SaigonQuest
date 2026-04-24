package main.components.audio

import kotlinx.browser.document
import org.w3c.dom.HTMLAudioElement
import main.components.AudioSettings
import models.MinigameItem

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
    fun playSound(url: String, onEnd: () -> Unit = {}): kotlin.js.Promise<Unit>? {
        console.log("[SoundManager] playSound requested for URL: $url")
        // Always play sounds regardless of music setting
        // Music setting is specifically for background music
        
        try {
            val audio = document.createElement("audio") as org.w3c.dom.HTMLAudioElement
            audio.src = url
            activeSounds.add(audio)
            
            audio.onended = {
                activeSounds.remove(audio)
                onEnd()
                null
            }
            
            console.log("[SoundManager] Playing audio: $url")
            return audio.play().then {
                console.log("[SoundManager] Audio playback started successfully: $url")
            }.catch { err ->
                activeSounds.remove(audio)
                onEnd() // Call onEnd even if it failed to play
                console.warn("[SoundManager] Autoplay prevented or error playing sound: $err")
                throw err.unsafeCast<Throwable>()
            }
        } catch (t: Throwable) {
            console.error("[SoundManager] Failed to play sound from URL: $url", t)
            onEnd()
            return null
        }
    }

    /**
     * Reusable logic to play a MinigameItem's audio with fallback to TTS.
     */
    fun playItemAudio(item: MinigameItem, onEnd: () -> Unit = {}) {
        val audio = item.audio
        val text = item.text
        
        val wrappedOnEnd = {
            MusicManager.setIsAiSpeaking(false)
            onEnd()
        }

        MusicManager.setIsAiSpeaking(true)

        if (audio != null) {
            console.log("[SoundManager] Playing item audio URL: $audio")
            playSound(audio, wrappedOnEnd)?.catch { _ ->
                if (text != null) {
                    console.log("[SoundManager] Audio playback failed, falling back to TTS: $text")
                    TtsManager.speak(text, wrappedOnEnd)
                } else {
                    wrappedOnEnd()
                }
            }
        } else if (text != null) {
            console.log("[SoundManager] No audio URL, falling back to TTS: $text")
            TtsManager.speak(text, wrappedOnEnd)
        } else {
            console.warn("[SoundManager] Cannot play audio: both audio and text are null")
            wrappedOnEnd()
        }
    }

    /**
     * Stop all sounds currently playing through the SoundManager.
     */
    fun stopAllSounds() {
        console.log("[SoundManager] Stopping all sounds (${activeSounds.size} active)")
        TtsManager.stop()
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
