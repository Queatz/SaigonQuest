package main.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/**
 * Content for the friends panel/dialog.
 * This component contains the shared UI elements for both mobile and desktop views.
 */
@Composable
fun FriendsContent() {
    // Friends content
    Div(
        attrs = {
            style {
                property("flex-grow", "1")
                padding(16.px)
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.Center)
                alignItems(AlignItems.Center)
            }
        }
    ) {
        // Empty friends message
        P(
            attrs = {
                style {
                    color(rgba(255, 255, 255, 0.7))
                    property("text-align", "center")
                }
            }
        ) {
            Text("You haven't made any friends yet!")
        }
    }
}
