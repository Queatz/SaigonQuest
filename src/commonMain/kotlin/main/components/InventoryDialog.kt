package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.json.Json
import main.api.ObjectApi
import models.Object
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.marginRight
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

/**
 * An inventory dialog that displays the user's objects and allows them to be dragged into the world.
 */
@Composable
fun InventoryDialog(onClose: () -> Unit) {
    // State for objects
    var objects by remember { mutableStateOf<List<Object>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Get current world ID from the URL path
    val currentWorldId = window.location.pathname.substring(1)
    
    // Load objects when the dialog is opened
    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = ""
        
        try {
            val objectList = ObjectApi.getObjectsByWorldId(currentWorldId)
            objects = objectList
            isLoading = false
        } catch (error: Exception) {
            console.error("Error loading objects", error)
            errorMessage = error.message ?: "Failed to load objects"
            isLoading = false
        }
    }
    
    // JSON serializer
    val json = Json { 
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }
    
    // Function to handle drag start
    fun handleDragStart(event: dynamic, obj: Object) {
        // Set the drag data
        event.dataTransfer.setData("application/json", json.encodeToString(Object.serializer(), obj))
        // Set a custom drag image (optional)
        val dragImage = document.createElement("div") as HTMLElement
        dragImage.style.width = "48px"
        dragImage.style.height = "48px"
        dragImage.style.backgroundColor = "rgba(0, 120, 255, 0.7)"
        dragImage.style.borderRadius = "4px"
        document.body?.appendChild(dragImage)
        event.dataTransfer.setDragImage(dragImage, 24, 24)
        // Remove the drag image element after it's been used
        window.setTimeout({ document.body?.removeChild(dragImage) }, 0)
    }
    
    Div(
        attrs = {
            style {
                position(Position.Fixed)
                top(0.px)
                left(0.px)
                width(100.percent)
                height(100.vh)
                backgroundColor(rgba(0, 0, 0, 0.7))
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.Center)
                alignItems(AlignItems.Center)
                // Make dialog interactive
                property("pointer-events", "auto")
            }
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
                    Text("Objects")
                }
                
                // Create new object button
                Button(
                    attrs = {
                        style {
                            backgroundColor(rgba(0, 120, 255, 0.7))
                            color(Color.white)
                            property("border", "0")
                            property("border-radius", "4px")
                            padding(4.px, 8.px)
                            marginRight(8.px)
                            cursor("pointer")
                            fontSize(14.px)
                        }
                        onClick { 
                            // Open object editor dialog
                            window.dispatchEvent(Event("openObjectEditor"))
                            onClose()
                        }
                    }
                ) {
                    Text("+ New")
                }
                
                Button(
                    attrs = {
                        style {
                            backgroundColor(Color.transparent)
                            property("border", "0")
                            color(Color.white)
                            cursor("pointer")
                            fontSize(20.px)
                        }
                        onClick { onClose() }
                    }
                ) {
                    Text("×")
                }
            }
            
            // Loading or error message
            if (isLoading) {
                Div(
                    attrs = {
                        style {
                            padding(16.px)
                            color(Color.white)
                            property("text-align", "center")
                        }
                    }
                ) {
                    Text("Loading objects...")
                }
            } else if (errorMessage.isNotEmpty()) {
                Div(
                    attrs = {
                        style {
                            padding(16.px)
                            color(Color.red)
                            property("text-align", "center")
                        }
                    }
                ) {
                    Text(errorMessage)
                }
            } else if (objects.isEmpty()) {
                Div(
                    attrs = {
                        style {
                            padding(16.px)
                            color(rgba(255, 255, 255, 0.7))
                            property("text-align", "center")
                        }
                    }
                ) {
                    Text("No objects yet. Click '+ New' to create one.")
                }
            } else {
                // Inventory grid
                Div(
                    attrs = {
                        style {
                            padding(16.px)
                            display(DisplayStyle.Grid)
                            property("grid-template-columns", "repeat(auto-fill, minmax(80px, 1fr))")
                            property("gap", "12px")
                            property("overflow-y", "auto")
                            property("flex-grow", "1")
                            property("align-content", "flex-start")
                        }
                    }
                ) {
                    // Display objects
                    objects.forEach { obj ->
                        Div(
                            attrs = {
                                style {
                                    display(DisplayStyle.Flex)
                                    flexDirection(FlexDirection.Column)
                                    alignItems(AlignItems.Center)
                                    property("gap", "4px")
                                }
                            }
                        ) {
                            // Object box (draggable)
                            Div(
                                attrs = {
                                    style {
                                        width(64.px)
                                        height(64.px)
                                        backgroundColor(rgba(0, 120, 255, 0.3))
                                        property("border", "1px solid rgba(0, 120, 255, 0.7)")
                                        property("border-radius", "4px")
                                        display(DisplayStyle.Flex)
                                        justifyContent(JustifyContent.Center)
                                        alignItems(AlignItems.Center)
                                        cursor("grab")
                                        // Make box interactive
                                        property("pointer-events", "auto")
                                        // Make box draggable
                                        property("draggable", "true")
                                    }
                                    // Set up drag events
                                    onDragStart { event ->
                                        // Use the handleDragStart function
                                        handleDragStart(event.nativeEvent, obj)
                                    }
                                    onClick { 
                                        // Open object editor dialog with this object
                                        window.asDynamic().currentEditingObject = obj
                                        window.dispatchEvent(Event("openObjectEditor"))
                                        onClose()
                                    }
                                }
                            ) {
                                // Display a cube icon
                                Span {
                                    Text("□")
                                }
                            }
                            
                            // Object title
                            Span(
                                attrs = {
                                    style {
                                        color(Color.white)
                                        fontSize(12.px)
                                        property("text-align", "center")
                                        property("word-break", "break-word")
                                        property("max-width", "80px")
                                    }
                                }
                            ) {
                                Text(obj.title.takeIf { it.isNotEmpty() } ?: "Untitled")
                            }
                        }
                    }
                }
            }
        }
    }
}
