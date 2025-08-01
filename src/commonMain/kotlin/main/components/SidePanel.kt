package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import main.theme.Styles
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement

/**
 * A reusable side panel component for desktop view.
 * 
 * @param title The title to display in the panel header
 * @param onClose Callback when the panel is closed
 * @param content The content to display in the panel body
 */
@Composable
fun SidePanel(
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    // Reference to store the DOM element
    var panelElement: org.w3c.dom.HTMLElement? = null
    
    // Use LaunchedEffect to handle the animation timing
    LaunchedEffect(Unit) {
        // Wait for the element reference to be set and initial styles to be applied
        delay(10) // 10ms delay, equivalent to the setTimeout
        
        // Apply the animation by changing the right position
        panelElement?.style?.right = "0px"
    }
    
    Div(
        attrs = {
            classes(Styles.sidePanel)
            
            // Reference to the DOM element to animate it
            ref { element ->
                // Store the element reference for use in LaunchedEffect
                panelElement = element
                
                // Cleanup function
                onDispose {
                    // No cleanup needed
                }
            }
        }
    ) {
        // Panel header
        Div(
            attrs = {
                classes(Styles.panelHeader)
            }
        ) {
            H3(
                attrs = {
                    classes(Styles.panelTitle)
                }
            ) {
                Text(title)
            }
            
            Button(
                attrs = {
                    classes(Styles.closeButton)
                    onClick { onClose() }
                }
            ) {
                Text("×")
            }
        }
        
        // Panel content
        content()
    }
}
