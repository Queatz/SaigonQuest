package main.components.inventory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.InventoryApi
import main.api.ItemApi
import main.components.SidePanelState
import main.components.isLayoutAdjustmentNeeded
import models.Item
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
 * Shows the first 4 items from the player's inventory, with the last slot opening the inventory dialog.
 * The position is adjusted based on the side panel state on desktop.
 */
@Composable
fun InventoryTray(
    onOpenInventory: () -> Unit,
    currentPanel: SidePanelState = SidePanelState.None,
    isMobile: State<Boolean>
) {
    // State for inventory items
    var inventoryItems by remember { mutableStateOf<List<Item>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Coroutine scope for async operations
    val coroutineScope = rememberCoroutineScope()
    
    // Load inventory when the component is first rendered
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                // Get user inventory
                val userInventory = InventoryApi.getUserInventory()
                
                // Get the first 4 items (or fewer if inventory has less than 4 items)
                val itemIds = userInventory.itemOrder.take(4)
                
                if (itemIds.isNotEmpty()) {
                    // Get items by their IDs
                    val items = ItemApi.getItemsByIds(itemIds)
                    inventoryItems = items
                }
                
                isLoading = false
            } catch (error: Exception) {
                console.error("Error loading inventory for tray", error)
                isLoading = false
            }
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
                    // If we have an item, we could implement item usage here
                    // For now, just log that the item was clicked
                    if (item != null) {
                        console.log("Clicked item: ${item.title}")
                    }
                },
                item = item
            )
        }
        
        // Last box always opens inventory dialog
        InventoryBox(
            onClick = { onOpenInventory() },
            content = "+"
        )
    }
}
