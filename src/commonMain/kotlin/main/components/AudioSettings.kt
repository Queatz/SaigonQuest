package main.components

import androidx.compose.runtime.mutableStateOf
import kotlinx.browser.localStorage

/**
 * Global audio settings manager.
 * Stores the user's preference for music playback and notifies observers on changes.
 */
object AudioSettings {
    private const val MUSIC_ENABLED_STORAGE_KEY = "saigon_quest_music_enabled"
    private const val MUSIC_VOLUME_STORAGE_KEY = "saigon_quest_music_volume"
    private const val SPEAK_DIALOGUE_ENABLED_STORAGE_KEY = "saigon_quest_speak_dialogue_enabled"

    private val observers = mutableListOf<() -> Unit>()

    val _isMusicEnabled = mutableStateOf(loadMusicEnabledFromStorage())
    val _musicVolume = mutableStateOf(loadMusicVolumeFromStorage())
    val _isSpeakDialogueEnabled = mutableStateOf(loadSpeakDialogueEnabledFromStorage())

    fun isMusicEnabled(): Boolean = _isMusicEnabled.value

    fun isSpeakDialogueEnabled(): Boolean = _isSpeakDialogueEnabled.value

    fun setMusicEnabled(enabled: Boolean) {
        val old = _isMusicEnabled.value
        _isMusicEnabled.value = enabled
        saveMusicEnabledToStorage(enabled)
        if (old != enabled) notifyObservers()
    }

    fun musicVolume(): Double = _musicVolume.value

    fun setMusicVolume(volume: Double) {
        val old = _musicVolume.value
        val clamped = volume.coerceIn(0.0, 1.0)
        _musicVolume.value = clamped
        saveMusicVolumeToStorage(clamped)
        if (old != clamped) notifyObservers()
    }

    fun setSpeakDialogueEnabled(enabled: Boolean) {
        val old = _isSpeakDialogueEnabled.value
        _isSpeakDialogueEnabled.value = enabled
        saveSpeakDialogueEnabledToStorage(enabled)
        if (old != enabled) notifyObservers()
    }

    fun toggleMusicEnabled(): Boolean {
        val newValue = !_isMusicEnabled.value
        _isMusicEnabled.value = newValue
        saveMusicEnabledToStorage(newValue)
        notifyObservers()
        return newValue
    }

    fun addObserver(observer: () -> Unit) {
        observers.add(observer)
    }

    fun removeObserver(observer: () -> Unit) {
        observers.remove(observer)
    }

    private fun notifyObservers() {
        try {
            observers.forEach { it() }
        } catch (t: Throwable) {
            console.error("[AudioSettings] Error notifying observers", t)
        }
    }

    private fun loadMusicEnabledFromStorage(): Boolean {
        return try {
            val stored = localStorage.getItem(MUSIC_ENABLED_STORAGE_KEY)
            // Default to true (music on) when not set
            stored?.toBoolean() ?: true
        } catch (t: Throwable) {
            console.error("[AudioSettings] Failed to load from localStorage", t)
            true
        }
    }

    private fun saveMusicEnabledToStorage(enabled: Boolean) {
        try {
            localStorage.setItem(MUSIC_ENABLED_STORAGE_KEY, enabled.toString())
        } catch (t: Throwable) {
            console.error("[AudioSettings] Failed to save to localStorage", t)
        }
    }

    private fun loadMusicVolumeFromStorage(): Double {
        return try {
            val stored = localStorage.getItem(MUSIC_VOLUME_STORAGE_KEY)
            // Default to 0.5 (50% volume) when not set
            stored?.toDoubleOrNull() ?: 0.5
        } catch (t: Throwable) {
            console.error("[AudioSettings] Failed to load volume from localStorage", t)
            0.5
        }
    }

    private fun saveMusicVolumeToStorage(volume: Double) {
        try {
            localStorage.setItem(MUSIC_VOLUME_STORAGE_KEY, volume.toString())
        } catch (t: Throwable) {
            console.error("[AudioSettings] Failed to save volume to localStorage", t)
        }
    }

    private fun loadSpeakDialogueEnabledFromStorage(): Boolean {
        return try {
            val stored = localStorage.getItem(SPEAK_DIALOGUE_ENABLED_STORAGE_KEY)
            // Default to true when not set
            stored?.toBoolean() ?: true
        } catch (t: Throwable) {
            console.error("[AudioSettings] Failed to load speak dialogue from localStorage", t)
            true
        }
    }

    private fun saveSpeakDialogueEnabledToStorage(enabled: Boolean) {
        try {
            localStorage.setItem(SPEAK_DIALOGUE_ENABLED_STORAGE_KEY, enabled.toString())
        } catch (t: Throwable) {
            console.error("[AudioSettings] Failed to save speak dialogue to localStorage", t)
        }
    }
}
