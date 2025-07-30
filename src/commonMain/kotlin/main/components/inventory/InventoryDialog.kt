package main.components.inventory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.launch
import main.api.InventoryApi
import main.theme.Styles
import main.theme.Theme
import models.Inventory
import models.InventoryItem
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.bottom
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginRight
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.right
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.css.textAlign
import org.jetbrains.compose.web.css.flexGrow
import org.jetbrains.compose.web.css.fontWeight
import org.jetbrains.compose.web.css.marginLeft
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * An inventory dialog that displays the user's inventory items.
 */
@Composable
fun InventoryDialog(onClose: () -> Unit) {
    // State for inventory
    var inventory by remember { mutableStateOf<Inventory?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Load inventory when the dialog is opened
    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = ""
        
        try {
            val userInventory = InventoryApi.getUserInventory()
            inventory = userInventory
            isLoading = false
        } catch (error: Exception) {
            console.error("Error loading inventory", error)
            errorMessage = error.message ?: "Failed to load inventory"
            isLoading = false
        }
    }
    
    // Function to handle using an item
    fun handleUseItem(item: InventoryItem) {
        // Implement item usage logic here
        console.log("Using item: ${item.name}")
    }
    
    Div(
        attrs = {
            classes(Styles.overlayBase)
            // Add click outside to close
            onClick { onClose() }
        }
    ) {
        Div(
            attrs = {
                style {
                    width(90.percent)
                    property("max-width", "600px")
                    height(80.vh)
                    backgroundColor(rgba(30, 30, 30, 0.95))
                    property("border-radius", "8px")
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                }
                // Stop propagation to prevent closing when clicking inside the dialog
                onClick { it.stopPropagation() }
            }
        ) {
            // Inventory header
            Div(
                attrs = {
                    style {
                        padding(16.px)
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.SpaceBetween)
                        alignItems(AlignItems.Center)
                        property("border-bottom", "1px solid rgba(255, 255, 255, 0.2)")
                    }
                }
            ) {
                H3(
                    attrs = {
                        style {
                            margin(0.px)
                            color(Color.white)
                        }
                    }
                ) {
                    Text("Inventory")
                }
                
                // Close button
                Span(
                    attrs = {
                        style {
                            fontSize(24.px)
                            color(rgba(255, 255, 255, 0.7))
                            cursor("pointer")
                        }
                        onClick { onClose() }
                    }
                ) {
                    Text("×")
                }
            }
            
            // Inventory content
            Div(
                attrs = {
                    style {
                        padding(16.px)
                        height(100.percent)
                        property("overflow-y", "auto")
                    }
                }
            ) {
                if (isLoading) {
                    P(
                        attrs = {
                            style {
                                color(Color.white)
                                textAlign("center")
                            }
                        }
                    ) {
                        Text("Loading inventory...")
                    }
                } else if (errorMessage.isNotEmpty()) {
                    P(
                        attrs = {
                            style {
                                color(rgba(255, 100, 100, 0.9))
                                textAlign("center")
                            }
                        }
                    ) {
                        Text("Error: $errorMessage")
                    }
                } else if (inventory?.items?.isEmpty() == true) {
                    P(
                        attrs = {
                            style {
                                color(Color.white)
                                textAlign("center")
                            }
                        }
                    ) {
                        Text("Your inventory is empty.")
                    }
                } else {
                    // Display inventory items
                    inventory?.items?.forEach { item ->
                        Div(
                            attrs = {
                                style {
                                    display(DisplayStyle.Flex)
                                    padding(12.px)
                                    marginBottom(8.px)
                                    backgroundColor(rgba(50, 50, 50, 0.5))
                                    property("border-radius", "4px")
                                    alignItems(AlignItems.Center)
                                }
                            }
                        ) {
                            // Item image (placeholder)
                            Div(
                                attrs = {
                                    style {
                                        width(48.px)
                                        height(48.px)
                                        backgroundColor(rgba(100, 100, 100, 0.5))
                                        marginRight(12.px)
                                        property("border-radius", "4px")
                                        
                                        // If item has an image URL, use it
                                        if (item.imageUrl.isNotEmpty()) {
                                            property("background-image", "url(${item.imageUrl})")
                                            property("background-size", "cover")
                                            property("background-position", "center")
                                        }
                                    }
                                }
                            ) {}
                            
                            // Item details
                            Div(
                                attrs = {
                                    style {
                                        flexGrow(1)
                                    }
                                }
                            ) {
                                // Item name
                                Div(
                                    attrs = {
                                        style {
                                            color(Color.white)
                                            fontSize(16.px)
                                            fontWeight("bold")
                                        }
                                    }
                                ) {
                                    Text(item.name)
                                    
                                    // Show quantity if more than 1
                                    if (item.quantity > 1) {
                                        Span(
                                            attrs = {
                                                style {
                                                    color(rgba(200, 200, 200, 0.8))
                                                    marginLeft(8.px)
                                                }
                                            }
                                        ) {
                                            Text("x${item.quantity}")
                                        }
                                    }
                                }
                                
                                // Item description
                                Div(
                                    attrs = {
                                        style {
                                            color(rgba(200, 200, 200, 0.8))
                                            fontSize(14.px)
                                            marginTop(4.px)
                                        }
                                    }
                                ) {
                                    Text(item.description)
                                }
                            }
                            
                            // Use button
                            Button(
                                attrs = {
                                    style {
                                        padding(6.px, 12.px)
                                        backgroundColor(Theme.colors.primary)
                                        color(Theme.colors.onPrimary)
                                        property("border", "0")
                                        property("border-radius", "4px")
                                        cursor("pointer")
                                    }
                                    onClick { handleUseItem(item) }
                                }
                            ) {
                                Text("Use")
                            }
                        }
                    }
                }
            }
        }
    }
}
