package main

import androidx.compose.runtime.mutableStateOf

/**
 * Holds pause/resume state across routes.
 */
object PausedState {
    val lastWorldUrl = mutableStateOf("")
    val lastWorldName = mutableStateOf("")

    fun set(worldUrl: String, worldName: String) {
        lastWorldUrl.value = worldUrl
        lastWorldName.value = worldName
    }

    fun clear() {
        lastWorldUrl.value = ""
        lastWorldName.value = ""
    }
}

