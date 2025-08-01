package main.components

import androidx.compose.runtime.mutableStateOf
import kotlinx.browser.localStorage

/**
 * Singleton object to manage graphics settings for the application.
 * Provides a central place to store and retrieve graphics settings
 * that can be accessed from different components.
 * 
 * Uses the observer pattern to notify components when settings change,
 * allowing them to react accordingly without using global variables.
 */
object GraphicsSettings {
    /**
     * Storage key for localStorage
     */
    private const val GRAPHICS_MODE_STORAGE_KEY = "saigon_quest_graphics_mode"
    
    /**
     * State to track whether low graphics mode is enabled.
     * In low graphics mode, the world is rendered at 1/3 resolution.
     */
    private val _isLowGraphicsModeEnabled = mutableStateOf(loadGraphicsModeFromStorage())
    
    /**
     * List of observer callbacks to be notified when graphics settings change.
     */
    private val observers = mutableListOf<() -> Unit>()
    
    /**
     * Get the current state of low graphics mode.
     * 
     * @return true if low graphics mode is enabled, false otherwise
     */
    fun isLowGraphicsModeEnabled(): Boolean {
        return _isLowGraphicsModeEnabled.value
    }
    
    /**
     * Set the state of low graphics mode.
     * 
     * @param enabled true to enable low graphics mode, false to disable it
     */
    fun setLowGraphicsMode(enabled: Boolean) {
        val oldValue = _isLowGraphicsModeEnabled.value
        _isLowGraphicsModeEnabled.value = enabled
        
        // Save to localStorage
        saveGraphicsModeToStorage(enabled)
        
        // If the value changed, notify observers
        if (oldValue != enabled) {
            notifyObservers()
        }
    }
    
    /**
     * Toggle the state of low graphics mode.
     * 
     * @return the new state of low graphics mode after toggling
     */
    fun toggleLowGraphicsMode(): Boolean {
        val newValue = !_isLowGraphicsModeEnabled.value
        _isLowGraphicsModeEnabled.value = newValue
        
        // Save to localStorage
        saveGraphicsModeToStorage(newValue)
        
        // Notify observers of the change
        notifyObservers()
        
        return _isLowGraphicsModeEnabled.value
    }
    
    /**
     * Get the scaling factor for the engine based on the current graphics mode.
     * 
     * @return 3 for low graphics mode (1/3 resolution), 1 for normal mode
     */
    fun getEngineScalingFactor(): Int {
        return if (_isLowGraphicsModeEnabled.value) 3 else 1
    }
    
    /**
     * Register an observer to be notified when graphics settings change.
     * 
     * @param observer The callback function to be called when graphics settings change
     */
    fun addObserver(observer: () -> Unit) {
        observers.add(observer)
    }
    
    /**
     * Unregister an observer so it no longer receives notifications.
     * 
     * @param observer The callback function to be removed
     */
    fun removeObserver(observer: () -> Unit) {
        observers.remove(observer)
    }
    
    /**
     * Notify all registered observers of changes to graphics settings.
     */
    private fun notifyObservers() {
        try {
            // Call all registered observer functions
            observers.forEach { it() }
        } catch (e: Exception) {
            console.error("Error notifying graphics mode observers", e)
        }
    }
    
    /**
     * Load graphics mode setting from localStorage.
     * 
     * @return true if low graphics mode is enabled in localStorage, false otherwise
     */
    private fun loadGraphicsModeFromStorage(): Boolean {
        return try {
            val storedValue = localStorage.getItem(GRAPHICS_MODE_STORAGE_KEY)
            storedValue == "true"
        } catch (e: Exception) {
            console.error("Error loading graphics mode from localStorage", e)
            false
        }
    }
    
    /**
     * Save graphics mode setting to localStorage.
     * 
     * @param enabled true to save enabled state, false to save disabled state
     */
    private fun saveGraphicsModeToStorage(enabled: Boolean) {
        try {
            localStorage.setItem(GRAPHICS_MODE_STORAGE_KEY, enabled.toString())
        } catch (e: Exception) {
            console.error("Error saving graphics mode to localStorage", e)
        }
    }
}
