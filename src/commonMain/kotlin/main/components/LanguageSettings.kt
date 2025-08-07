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

    init {
        Translations.setLanguage(_currentLanguage.value)
    }
    
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
        _currentLanguage.value = language
        
        // Save to localStorage
        saveLanguageToStorage(language)

        // Update UI
        Translations.setLanguage(language)
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
