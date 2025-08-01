package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import main.theme.Styles
import org.jetbrains.compose.web.dom.*

/**
 * A two-button toggle for switching between languages.
 * 
 * @param id The ID for the component
 * @param onLanguageChange Callback function that is called when the language is changed
 */
@Composable
fun LanguageSwitcher(
    id: String = "languageSwitcher",
    onLanguageChange: (String) -> Unit
) {
    // Get the current language
    val isEnglish = remember { mutableStateOf(LanguageSettings.isEnglish()) }
    
    // Container for the language switcher
    Div(
        attrs = {
            id(id)
            classes(Styles.languageSwitcherContainer)
        }
    ) {
        // Vietnamese button (left)
        Div(
            attrs = {
                if (!isEnglish.value) {
                    classes(Styles.languageSwitcherOption, Styles.languageSwitcherOptionActive)
                } else {
                    classes(Styles.languageSwitcherOption, Styles.languageSwitcherOptionInactive)
                }
                onClick {
                    if (isEnglish.value) {
                        isEnglish.value = false
                        LanguageSettings.setLanguage(LanguageSettings.LANGUAGE_VIETNAMESE)
                        onLanguageChange(LanguageSettings.LANGUAGE_VIETNAMESE)
                    }
                }
            }
        ) {
            Text("🇻🇳 VI")
        }
        
        // English button (right)
        Div(
            attrs = {
                if (isEnglish.value) {
                    classes(Styles.languageSwitcherOption, Styles.languageSwitcherOptionActive)
                } else {
                    classes(Styles.languageSwitcherOption, Styles.languageSwitcherOptionInactive)
                }
                onClick {
                    if (!isEnglish.value) {
                        isEnglish.value = true
                        LanguageSettings.setLanguage(LanguageSettings.LANGUAGE_ENGLISH)
                        onLanguageChange(LanguageSettings.LANGUAGE_ENGLISH)
                    }
                }
            }
        ) {
            Text("🇬🇧 EN")
        }
    }
}
