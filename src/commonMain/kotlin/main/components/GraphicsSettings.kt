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
     * Storage keys for localStorage
     */
    private const val GRAPHICS_MODE_STORAGE_KEY = "saigon_quest_graphics_mode"
    private const val PRETTY_GRAPHICS_STORAGE_KEY = "saigon_quest_pretty_graphics"
    private const val WIDE_ANGLE_STORAGE_KEY = "saigon_quest_wide_angle"
    private const val OBSERVER_MODE_STORAGE_KEY = "saigon_quest_observer_mode"
    private const val ALWAYS_SHOW_QR_STORAGE_KEY = "saigon_quest_always_show_qr"
    private const val OUTLINE_ENABLED_STORAGE_KEY = "saigon_quest_outline_enabled"

    /**
     * State to track whether low graphics mode is enabled.
     * In low graphics mode, the world is rendered at 1/3 resolution.
     */
    val _isLowGraphicsModeEnabled = mutableStateOf(loadGraphicsModeFromStorage())
    
    /**
     * State to track whether pretty graphics mode is enabled.
     * In pretty graphics mode, additional visual effects are enabled.
     */
    val _isPrettyGraphicsEnabled = mutableStateOf(loadPrettyGraphicsFromStorage())

    val _isWideAngleEnabled = mutableStateOf(loadWideAngleFromStorage())
    val _isObserverModeEnabled = mutableStateOf(loadObserverModeFromStorage())
    val _isAlwaysShowQrEnabled = mutableStateOf(loadAlwaysShowQrFromStorage())
    val _isOutlineEnabled = mutableStateOf(loadOutlineEnabledFromStorage())
    
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

    fun isWideAngleEnabled(): Boolean {
        return _isWideAngleEnabled.value
    }

    fun isObserverModeEnabled(): Boolean {
        return _isObserverModeEnabled.value
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
        } catch (e: Throwable) {
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
        } catch (e: Throwable) {
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
        } catch (e: Throwable) {
            console.error("Error saving graphics mode to localStorage", e)
        }
    }
    
    /**
     * Get the current state of pretty graphics mode.
     * 
     * @return true if pretty graphics mode is enabled, false otherwise
     */
    fun isPrettyGraphicsEnabled(): Boolean {
        return _isPrettyGraphicsEnabled.value
    }
    
    /**
     * Set the state of pretty graphics mode.
     * 
     * @param enabled true to enable pretty graphics mode, false to disable it
     */
    fun setPrettyGraphics(enabled: Boolean) {
        val oldValue = _isPrettyGraphicsEnabled.value
        _isPrettyGraphicsEnabled.value = enabled
        
        // Save to localStorage
        savePrettyGraphicsToStorage(enabled)
        
        // If the value changed, notify observers
        if (oldValue != enabled) {
            notifyObservers()
        }
    }

    fun setWideAngle(enabled: Boolean) {
        val oldValue = _isWideAngleEnabled.value
        _isWideAngleEnabled.value = enabled

        // Save to localStorage
        saveWideAngleToStorage(enabled)

        // If the value changed, notify observers
        if (oldValue != enabled) {
            notifyObservers()
        }
    }
    
    /**
     * Toggle the state of pretty graphics mode.
     * 
     * @return the new state of pretty graphics mode after toggling
     */
    fun togglePrettyGraphics(): Boolean {
        val newValue = !_isPrettyGraphicsEnabled.value
        _isPrettyGraphicsEnabled.value = newValue
        
        // Save to localStorage
        savePrettyGraphicsToStorage(newValue)
        
        // Notify observers of the change
        notifyObservers()
        
        return _isPrettyGraphicsEnabled.value
    }
    
    /**
     * Load pretty graphics setting from localStorage.
     * 
     * @return true if pretty graphics mode is enabled in localStorage, false otherwise
     */
    private fun loadPrettyGraphicsFromStorage(): Boolean {
        return try {
            val storedValue = localStorage.getItem(PRETTY_GRAPHICS_STORAGE_KEY)
            storedValue == "true"
        } catch (e: Throwable) {
            console.error("Error loading pretty graphics mode from localStorage", e)
            false
        }
    }

    /**
     * Save pretty graphics setting to localStorage.
     * 
     * @param enabled true to save enabled state, false to save disabled state
     */
    private fun savePrettyGraphicsToStorage(enabled: Boolean) {
        try {
            localStorage.setItem(PRETTY_GRAPHICS_STORAGE_KEY, enabled.toString())
        } catch (e: Throwable) {
            console.error("Error saving pretty graphics mode to localStorage", e)
        }
    }

    private fun loadWideAngleFromStorage(): Boolean {
        return try {
            val storedValue = localStorage.getItem(WIDE_ANGLE_STORAGE_KEY)
            storedValue == "true"
        } catch (e: Throwable) {
            console.error("Error loading wide angle mode from localStorage", e)
            false
        }
    }

    private fun saveWideAngleToStorage(enabled: Boolean) {
        try {
            localStorage.setItem(WIDE_ANGLE_STORAGE_KEY, enabled.toString())
        } catch (e: Throwable) {
            console.error("Error saving wide angle mode to localStorage", e)
        }
    }

    fun setObserverMode(enabled: Boolean) {
        val oldValue = _isObserverModeEnabled.value
        _isObserverModeEnabled.value = enabled

        // Save to localStorage
        saveObserverModeToStorage(enabled)

        // If the value changed, notify observers
        if (oldValue != enabled) {
            notifyObservers()
        }
    }

    private fun loadObserverModeFromStorage(): Boolean {
        return try {
            val storedValue = localStorage.getItem(OBSERVER_MODE_STORAGE_KEY)
            storedValue == "true"
        } catch (e: Throwable) {
            console.error("Error loading observer mode from localStorage", e)
            false
        }
    }

    private fun saveObserverModeToStorage(enabled: Boolean) {
        try {
            localStorage.setItem(OBSERVER_MODE_STORAGE_KEY, enabled.toString())
        } catch (e: Throwable) {
            console.error("Error saving observer mode to localStorage", e)
        }
    }

    // Always-show QR flag
    fun isAlwaysShowQrEnabled(): Boolean {
        return _isAlwaysShowQrEnabled.value
    }

    fun isOutlineEnabled(): Boolean {
        return _isOutlineEnabled.value
    }

    fun setAlwaysShowQr(enabled: Boolean) {
        val oldValue = _isAlwaysShowQrEnabled.value
        _isAlwaysShowQrEnabled.value = enabled
        // Save to localStorage
        saveAlwaysShowQrToStorage(enabled)
        // If the value changed, notify observers
        if (oldValue != enabled) {
            notifyObservers()
        }
    }

    fun setOutlineEnabled(enabled: Boolean) {
        val oldValue = _isOutlineEnabled.value
        _isOutlineEnabled.value = enabled
        // Save to localStorage
        saveOutlineEnabledToStorage(enabled)
        // If the value changed, notify observers
        if (oldValue != enabled) {
            notifyObservers()
        }
    }

    private fun loadAlwaysShowQrFromStorage(): Boolean {
        return try {
            val storedValue = localStorage.getItem(ALWAYS_SHOW_QR_STORAGE_KEY)
            storedValue == "true"
        } catch (e: Throwable) {
            console.error("Error loading always-show-QR setting from localStorage", e)
            false
        }
    }

    private fun saveAlwaysShowQrToStorage(enabled: Boolean) {
        try {
            localStorage.setItem(ALWAYS_SHOW_QR_STORAGE_KEY, enabled.toString())
        } catch (e: Throwable) {
            console.error("Error saving always-show-QR setting to localStorage", e)
        }
    }

    private fun loadOutlineEnabledFromStorage(): Boolean {
        return try {
            val storedValue = localStorage.getItem(OUTLINE_ENABLED_STORAGE_KEY)
            // Default to false if not set
            storedValue == "true"
        } catch (e: Throwable) {
            console.error("Error loading outline setting from localStorage", e)
            true
        }
    }

    private fun saveOutlineEnabledToStorage(enabled: Boolean) {
        try {
            localStorage.setItem(OUTLINE_ENABLED_STORAGE_KEY, enabled.toString())
        } catch (e: Throwable) {
            console.error("Error saving outline setting to localStorage", e)
        }
    }
}
