package main.components

import androidx.compose.runtime.mutableStateOf
import kotlinx.browser.localStorage

/**
 * Singleton object to manage language settings for the application.
 * Provides a central place to store and retrieve language settings
 * that can be accessed from different components.
 * 
 * Uses the observer pattern to notify components when settings change,
 * allowing them to react accordingly without using global variables.
 */
object LanguageSettings {
    /**
     * Constants for language options
     */
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_VIETNAMESE = "vi"
    
    /**
     * Storage key for localStorage
     */
    private const val LANGUAGE_STORAGE_KEY = "saigon_quest_language"
    
    /**
     * State to track the current language.
     * Default is English if not set in localStorage.
     */
    private val _currentLanguage = mutableStateOf(loadLanguageFromStorage())
    
    /**
     * List of observer callbacks to be notified when language settings change.
     */
    private val observers = mutableListOf<() -> Unit>()
    
    /**
     * Get the current language.
     * 
     * @return the current language code (e.g., "en" or "vi")
     */
    fun getCurrentLanguage(): String {
        return _currentLanguage.value
    }
    
    /**
     * Set the current language.
     * 
     * @param language the language code to set (e.g., "en" or "vi")
     */
    fun setLanguage(language: String) {
        val oldValue = _currentLanguage.value
        _currentLanguage.value = language
        
        // Save to localStorage
        saveLanguageToStorage(language)
        
        // If the value changed, notify observers
        if (oldValue != language) {
            notifyObservers()
        }
    }
    
    /**
     * Toggle between English and Vietnamese.
     * 
     * @return the new language after toggling
     */
    fun toggleLanguage(): String {
        val newLanguage = if (_currentLanguage.value == LANGUAGE_ENGLISH) {
            LANGUAGE_VIETNAMESE
        } else {
            LANGUAGE_ENGLISH
        }
        
        _currentLanguage.value = newLanguage
        
        // Save to localStorage
        saveLanguageToStorage(newLanguage)
        
        // Notify observers of the change
        notifyObservers()
        
        return newLanguage
    }
    
    /**
     * Check if the current language is English.
     * 
     * @return true if the current language is English, false otherwise
     */
    fun isEnglish(): Boolean {
        return _currentLanguage.value == LANGUAGE_ENGLISH
    }
    
    /**
     * Check if the current language is Vietnamese.
     * 
     * @return true if the current language is Vietnamese, false otherwise
     */
    fun isVietnamese(): Boolean {
        return _currentLanguage.value == LANGUAGE_VIETNAMESE
    }
    
    /**
     * Register an observer to be notified when language settings change.
     * 
     * @param observer The callback function to be called when language settings change
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
     * Notify all registered observers of changes to language settings.
     */
    private fun notifyObservers() {
        try {
            // Call all registered observer functions
            observers.forEach { it() }
        } catch (e: Exception) {
            console.error("Error notifying language settings observers", e)
        }
    }
    
    /**
     * Load language setting from localStorage.
     * 
     * @return the language code from localStorage, or LANGUAGE_ENGLISH if not found
     */
    private fun loadLanguageFromStorage(): String {
        return try {
            localStorage.getItem(LANGUAGE_STORAGE_KEY) ?: LANGUAGE_ENGLISH
        } catch (e: Exception) {
            console.error("Error loading language from localStorage", e)
            LANGUAGE_ENGLISH
        }
    }
    
    /**
     * Save language setting to localStorage.
     * 
     * @param language the language code to save
     */
    private fun saveLanguageToStorage(language: String) {
        try {
            localStorage.setItem(LANGUAGE_STORAGE_KEY, language)
        } catch (e: Exception) {
            console.error("Error saving language to localStorage", e)
        }
    }
}
