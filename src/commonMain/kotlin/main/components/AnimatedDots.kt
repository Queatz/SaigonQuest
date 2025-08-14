package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.jetbrains.compose.web.css.opacity
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
fun AnimatedDots(dotCount: Int = 3, intervalMillis: Long = 1000L) {
    var visibleCount by remember { mutableStateOf(0) }

    LaunchedEffect(dotCount, intervalMillis) {
        while (true) {
            delay(intervalMillis)
            visibleCount = (visibleCount + 1) % (dotCount + 1)
        }
    }

    repeat(dotCount) { index ->
        Span(attrs = {
            style { opacity(if (index < visibleCount) 1 else 0) }
        }) { Text(".") }
    }
}
