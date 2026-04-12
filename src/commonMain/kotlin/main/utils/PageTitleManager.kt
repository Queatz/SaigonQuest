package main.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Manages the webpage title.
 */
object PageTitleManager {
    /**
     * The current world title. If null, the default title "Saigon Quest" is used.
     */
    var worldTitle by mutableStateOf<String?>(null)
}
