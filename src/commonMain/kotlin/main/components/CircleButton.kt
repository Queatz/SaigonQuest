package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

/**
 * A circular button with an optional badge indicator.
 * 
 * @param onClick Callback for when the button is clicked
 * @param icon The icon to display in the button
 * @param badgeCount Optional count to display in the badge. If greater than 0, a badge with the count is shown.
 *                   If 0 or null, no badge is shown. If -1, a simple red dot is shown without a count.
 * @param isChat Flag to indicate if this is a chat button, which will have a larger badge
 */
@Composable
fun CircleButton(
    onClick: () -> Unit,
    icon: String,
    badgeCount: Int? = -1,
    isChat: Boolean = false,
    id: String? = null
) {
    Div(
        attrs = {
            classes(Styles.circleButton)
            onClick { onClick() }
            id?.let { id(it) }
        }
    ) {
        // Button content (icon)
        Text(icon)
        
        // Badge indicator
        if (badgeCount != null && badgeCount != 0) {
            if (badgeCount > 0) {
                // Badge with count
                Div(
                    attrs = {
                        classes(Styles.badgeBase)
                        if (isChat) {
                            classes(Styles.chatBadge)
                        } else {
                            classes(Styles.normalBadge)
                        }
                    }
                ) {
                    // Display count, but cap at 99+
                    Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                }
            } else {
                // Simple red dot indicator (when badgeCount is -1)
                Div(
                    attrs = {
                        classes(Styles.redDotIndicator)
                    }
                )
            }
        }
    }
}
