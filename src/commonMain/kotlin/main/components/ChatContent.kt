package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.theme.Styles
import main.theme.Theme
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

/**
 * Content for the chat panel/dialog.
 * This component contains the shared UI elements for both mobile and desktop views.
 */
@Composable
fun ChatContent() {
    var messageText by remember { mutableStateOf("") }
    
    // Chat messages area
    Div(
        attrs = {
            style {
                property("flex-grow", "1")
                padding(16.px)
                property("overflow-y", "auto")
            }
        }
    ) {
        // Empty chat message placeholder
        P(
            attrs = {
                style {
                    color(rgba(255, 255, 255, 0.5))
                    property("text-align", "center")
                }
            }
        ) {
            Text("No messages yet")
        }
    }
    
    // Chat input area
    Div(
        attrs = {
            style {
                padding(16.px)
                display(DisplayStyle.Flex)
                property("gap", "8px")
                property("border-top", "1px solid rgba(255, 255, 255, 0.2)")
            }
        }
    ) {
        // Emote button
        Div(
            attrs = {
                style {
                    width(40.px)
                    height(40.px)
                    backgroundColor(rgba(255, 255, 255, 0.1))
                    property("border-radius", "4px")
                    display(DisplayStyle.Flex)
                    justifyContent(JustifyContent.Center)
                    alignItems(AlignItems.Center)
                    cursor("pointer")
                }
            }
        ) {
            Text("😀")
        }
        
        // Text input
        Input(
            type = InputType.Text,
            attrs = {
                classes(Styles.inputField)
                style {
                    property("flex-grow", "1")
                    property("border", "0")
                }
                placeholder("Type a message...")
                value(messageText)
                onInput { messageText = it.value }
            }
        )
        
        // Send button
        Button(
            attrs = {
                style {
                    padding(8.px, 16.px)
                    backgroundColor(Theme.colors.primary)
                    color(Theme.colors.onPrimary)
                    property("border", "0")
                    property("border-radius", "4px")
                    cursor("pointer")
                }
                onClick {
                    if (messageText.isNotEmpty()) {
                        // Send message logic would go here
                        messageText = ""
                    }
                }
            }
        ) {
            Text("Send")
        }
    }
}
