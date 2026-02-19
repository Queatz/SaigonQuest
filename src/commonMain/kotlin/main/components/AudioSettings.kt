package main.components

import androidx.compose.runtime.mutableStateOf
import kotlinx.browser.localStorage

/**
 * Global audio settings manager.
 * Stores the user's preference for music playback and notifies observers on changes.
 */
object AudioSettings {
    private const val MUSIC_ENABLED_STORAGE_KEY = "saigon_quest_music_enabled"

    private val observers = mutableListOf<() -> Unit>()

    val _isMusicEnabled = mutableStateOf(loadMusicEnabledFromStorage())

    fun isMusicEnabled(): Boolean = _isMusicEnabled.value

    fun setMusicEnabled(enabled: Boolean) {
        val old = _isMusicEnabled.value
        _isMusicEnabled.value = enabled
        saveMusicEnabledToStorage(enabled)
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
}
