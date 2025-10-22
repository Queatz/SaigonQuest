package main.components.inventory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import Translations.str
import main.components.SidePanelState
import main.components.isLayoutAdjustmentNeeded
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.borderRadius
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
import org.w3c.dom.events.Event

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

        // Note: event listener is attached separately in DisposableEffect
    }

    // Listen for item collected events dispatched from InputHandler
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { evt ->
            try {
                val detail = (evt.asDynamic().detail)
                val title = detail?.title as? String ?: "Item"
                showFeedback(title + " " + str("collected"))
            } catch (e: dynamic) {
                console.error("Error handling itemCollected event", e)
            }
        }
        window.addEventListener("itemCollected", listener)

        onDispose {
            window.removeEventListener("itemCollected", listener)
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
                    property("transform", "translateX(-50%) translateY(-10px)")
                    padding(8.px, 12.px)
                    backgroundColor(if (isError) rgba(255, 0, 0, 0.7) else rgba(0, 150, 0, 0.7))
                    color(Color.white)
                    fontSize(14.px)
                    borderRadius(4.px)
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
                // Compute dynamic horizontal shift to avoid overlapping the bottom-right CircleButtons
                // Bottom-right column width ~48px, base right inset 20px; when a side panel is open on desktop,
                // the bottom-right column is shifted to right(340px)
                val rightOffsetPx = if (isLayoutAdjustmentNeeded(currentPanel, isMobile.value)) 360 else 20
                val bottomRightButtonWidthPx = 48
                val extraPaddingPx = 10 // breathing room so they never touch
                val reservedRightSpacePx = rightOffsetPx + bottomRightButtonWidthPx + extraPaddingPx
                val shiftPx = reservedRightSpacePx / 2

                // Adjust left position when any side panel is open on desktop
                if (isLayoutAdjustmentNeeded(currentPanel, isMobile.value)) {
                    // Center in the remaining space (excluding the 360px side panel), then shift by half of the
                    // reserved right space to keep clear of the CircleButtons column
                    property("left", "calc(50% - 2rem - ${shiftPx}px)")
                } else {
                    // Shift the overall center slightly left by half of the reserved right space
                    property("left", "calc(50% - ${shiftPx}px)")
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
        
            // Get the inventory item key for this item
            val inventoryItemKey = InventoryState.inventory?.itemOrder?.getOrNull(index)
            
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
                isLoading = InventoryState.isLoading && item == null && index < 4,
                quantity = if (inventoryItemKey != null) InventoryState.getQuantityForInventoryItemKey(inventoryItemKey) else 1,
                inventoryItemKey = inventoryItemKey
            )
        }
        
        // Last box always opens inventory dialog
        InventoryBox(
            onClick = { onOpenInventory() },
            content = "➕",
            isLoading = false, // Never show loading for the dialog opener
            quantity = 0 // No quantity for the dialog opener
        )
    }
}
