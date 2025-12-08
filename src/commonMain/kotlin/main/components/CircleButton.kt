package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.css.opacity
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement

/**
 * A circular button with an optional badge indicator.
 * 
 * @param onClick Callback for when the button is clicked
 * @param icon The icon to display in the button (used when iconIndex is null)
 * @param iconIndex Optional index for image-based icon from the UI sprite sheet
 * @param badgeCount Optional count to display in the badge. If greater than 0, a badge with the count is shown.
 *                   If 0 or null, no badge is shown. If -1, a simple red dot is shown without a count.
 * @param isChat Flag to indicate if this is a chat button, which will have a larger badge
 * @param id Optional ID for the button element
 * @param elementRef Optional callback to get a reference to the button element
 */
@Composable
fun CircleButton(
    onClick: () -> Unit,
    icon: String = "",
    iconIndex: Int? = null,
    badgeCount: Int? = -1,
    opacity: Double = 1.0,
    isChat: Boolean = false,
    badgeIsSuccess: Boolean = false,
    id: String? = null,
    title: String = "",
    elementRef: ((HTMLElement) -> Unit)? = null
) {
    Div(
        attrs = {
            classes(Styles.circleButton)
            onClick { onClick() }
            style { opacity(opacity) }
            if (title.isNotBlank()) {
                title(title)
            }
            id?.let { id(it) }
            if (elementRef != null) {
                ref { element -> 
                    elementRef(element)
                    onDispose { }
                }
            }
        }
    ) {
        // Button content (icon)
        if (iconIndex != null) {
            // Use IconRenderer with the UI sprite sheet
            IconRenderer(
                url = "raw/data/ui/ui.png",
                index = iconIndex
            )
        } else {
            // Use text icon
            Text(icon)
        }
        
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
                        if (badgeIsSuccess) {
                            classes(Styles.badgeSuccess)
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
