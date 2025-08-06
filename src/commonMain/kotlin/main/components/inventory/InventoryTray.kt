package main.components.inventory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.api.InventoryApi
import main.api.ItemApi
import main.api.UserApi
import main.components.SidePanelState
import main.components.isLayoutAdjustmentNeeded
import models.Item
import models.User
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.bottom
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.opacity
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

/**
 * A component that displays the inventory tray at the bottom center of the screen.
 * Shows the first 4 items from the player's inventory, with the last slot opening the inventory dialog.
 * The position is adjusted based on the side panel state on desktop.
 */
@Composable
fun InventoryTray(
    onOpenInventory: () -> Unit,
    currentPanel: SidePanelState = SidePanelState.None,
    isMobile: State<Boolean>
) {
    // State for feedback messages
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    
    // Coroutine scope for async operations
    val coroutineScope = rememberCoroutineScope()
    
    // Function to clear feedback message after a delay
    fun showFeedback(message: String, error: Boolean = false) {
        feedbackMessage = message
        isError = error
        
        coroutineScope.launch {
            delay(3000) // Show message for 3 seconds
            feedbackMessage = null
        }
    }
    
    // Load inventory when the component is first rendered
    LaunchedEffect(Unit) {
        // Load inventory data if not already loaded
        if (InventoryState.inventory == null || InventoryState.items.isEmpty()) {
            InventoryState.loadInventory(
                coroutineScope = coroutineScope,
                onError = { message -> showFeedback(message, true) }
            )
        }
    }
    
    // Observe inventory state changes
    val inventoryItems = remember(InventoryState.items, InventoryState.inventory) {
        InventoryState.getFirstNItems(4)
    }
    
    // Get current user from shared state
    val currentUser = InventoryState.currentUser
    
    // Render feedback message if present
    feedbackMessage?.let { message ->
        Div(
            attrs = {
                style {
                    position(Position.Absolute)
                    bottom(70.px)
                    left(50.percent)
                    property("transform", "translateX(-50%)")
                    padding(8.px, 12.px)
                    backgroundColor(if (isError) rgba(255, 0, 0, 0.7) else rgba(0, 150, 0, 0.7))
                    color(Color.white)
                    fontSize(14.px)
                    property("border-radius", "4px")
                    property("pointer-events", "none")
                    opacity(0.9)
                }
            }
        ) {
            Text(message)
        }
    }
    
    Div(
        attrs = {
            style {
                // Adjust left position when any side panel is open on desktop
                if (isLayoutAdjustmentNeeded(currentPanel, isMobile)) {
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
        // Display the first 4 inventory items (or fewer if inventory has less than 4 items)
        repeat(4) { index ->
            val item = if (index < inventoryItems.size) inventoryItems[index] else null
        
            InventoryBox(
                onClick = { 
                    // Use the item if we have one
                    if (item != null) {
                        ItemUsageHandler.useItem(
                            item = item,
                            currentUser = currentUser,
                            coroutineScope = coroutineScope,
                            onSuccess = { message -> showFeedback(message) },
                            onError = { message -> showFeedback(message, true) }
                        )
                    }
                },
                item = item,
                isLoading = InventoryState.isLoading && item == null && index < 4
            )
        }
        
        // Last box always opens inventory dialog
        InventoryBox(
            onClick = { onOpenInventory() },
            content = "➕",
            isLoading = false // Never show loading for the dialog opener
        )
    }
}
