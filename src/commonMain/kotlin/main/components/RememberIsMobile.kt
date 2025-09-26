package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.MutableState
import kotlinx.browser.window

/**
 * Remembers a MutableState<Boolean> that reflects whether the viewport is considered mobile.
 * Mobile breakpoint: <= 960px.
 *
 * This composable also installs a window resize listener and cleans it up automatically.
 */
@Composable
fun rememberIsMobile(): MutableState<Boolean> {
    val isMobile = remember { mutableStateOf(window.innerWidth <= 960) }

    DisposableEffect(Unit) {
        val resizeListener: (dynamic) -> Unit = {
            isMobile.value = window.innerWidth <= 960
        }
        window.addEventListener("resize", resizeListener)
        onDispose {
            window.removeEventListener("resize", resizeListener)
        }
    }

    return isMobile
}
