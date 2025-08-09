package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.browser.document
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.Element
import org.w3c.dom.HTMLDivElement

/**
 * A Portal component that renders its content at a different location in the DOM tree.
 * This is useful for rendering dialogs, tooltips, and other overlay components that
 * should appear above all other content regardless of their position in the component hierarchy.
 *
 * @param key An optional key to control recomposition. When the key changes, the portal will be recreated.
 * @param target The DOM element where the content should be rendered (defaults to document.body)
 * @param content The content to be rendered in the portal
 */
@Composable
fun Portal(
    key: Any? = null,
    target: Element = document.body!!,
    content: @Composable () -> Unit
) {
    // Create a container element for the portal content
    // Use the key parameter to control when the container is recreated
    val container = remember(key) { document.createElement("div") as HTMLDivElement }
    
    // Add and remove the container from the target element
    // The DisposableEffect will re-run when key or target changes
    DisposableEffect(key, target) {
        // Add the container to the target element
        target.appendChild(container)
        
        // Create a composition for the container
        val composition = renderComposable(container) {
            content()
        }
        
        // Remove the container and dispose the composition when the composable is disposed
        onDispose {
            // Dispose the composition to prevent memory leaks
            composition.dispose()
            
            // Remove the container from the DOM
            if (container.parentNode != null) {
                target.removeChild(container)
            }
        }
    }
}
