package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import main.theme.Styles
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.bottom
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Div

/**
 * A component that displays the inventory tray at the bottom center of the screen.
 * Contains 5 inventory boxes, with the last one opening the inventory dialog.
 * The position is adjusted based on the side panel state on desktop.
 */
@Composable
fun InventoryTray(
    onOpenInventory: () -> Unit,
    currentPanel: SidePanelState = SidePanelState.None,
    isMobile: State<Boolean>
) {
    Div(
        attrs = {
            style {
                // Adjust left position when any side panel is open on desktop
                if ((currentPanel == SidePanelState.Chat || 
                     currentPanel == SidePanelState.WorldEdit || 
                     currentPanel == SidePanelState.Notifications || 
                     currentPanel == SidePanelState.ObjectsPanel ||
                     currentPanel == SidePanelState.Friends) && !isMobile.value) {
                    // Center in the remaining space (excluding the 320px side panel)
                    property("left", "calc(50% - 160px)")
                } else {
                    left(50.percent)
                }
                position(Position.Absolute)
                bottom(20.px)
                property("transform", "translateX(-50%)")
                display(org.jetbrains.compose.web.css.DisplayStyle.Flex)
                property("gap", "4px")
                // Make elements interactive
                property("pointer-events", "auto")
            }
        }
    ) {
        // Create 5 inventory boxes
        repeat(5) { index ->
            InventoryBox(
                onClick = { 
                    if (index == 4) {
                        // Last box opens inventory dialog
                        onOpenInventory()
                    }
                },
                content = if (index == 4) "+" else ""
            )
        }
    }
}
