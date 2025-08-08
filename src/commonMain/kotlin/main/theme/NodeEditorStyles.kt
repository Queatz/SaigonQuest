package main.theme

import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.StyleSheet
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.boxSizing
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flex
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.fontWeight
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginRight
import org.jetbrains.compose.web.css.marginLeft
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.outline
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.textAlign
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.width
import web.cssom.AtRules.Companion.property

/**
 * Styles for the Node Editor components
 */
object NodeEditorStyles : StyleSheet() {
    // Node styles
    val nodeContainer by style {
        position(Position.Absolute)
        width(180.px)
        padding(12.px)
        color(Color.white)
        property("border-radius", "6px")
        property("box-shadow", "0 2px 10px rgba(0, 0, 0, 0.3)")
        property("user-select", "none") // Prevent text selection during drag
    }
    
    val nodeSelected by style {
        property("border", "2px solid white")
        property("z-index", "10") // Bring to front when selected
    }
    
    val nodeUnselected by style {
        property("border", "2px solid transparent")
        property("z-index", "5")
    }
    
    val nodeDragging by style {
        cursor("grabbing")
        property("z-index", "10")
    }
    
    val nodeStatic by style {
        cursor("grab")
        property("z-index", "5")
    }
    
    // Node type colors
    val eventNodeColor by style {
        backgroundColor(rgba(70, 130, 180, 0.8)) // Steel blue
    }
    
    val dialogueNodeColor by style {
        backgroundColor(rgba(60, 179, 113, 0.8)) // Medium sea green
    }
    
    val actionNodeColor by style {
        backgroundColor(rgba(178, 34, 34, 0.8)) // Firebrick
    }
    
    val inputNodeColor by style {
        backgroundColor(rgba(128, 0, 128, 0.8)) // Purple
    }
    
    val defaultNodeColor by style {
        backgroundColor(rgba(100, 100, 100, 0.8)) // Gray
    }
    
    // Node header
    val nodeHeader by style {
        fontWeight("bold")
        marginBottom(8.px)
        textAlign("center")
    }
    // Node header
    val nodeDescription by style {
        fontSize(80.percent)
        marginBottom(8.px)
        textAlign("center")
    }
    
    // Node content
    val nodeContent by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("gap", "4px")
    }
    
    // Node divider
    val nodeDivider by style {
        height(1.px)
        backgroundColor(rgba(255, 255, 255, 0.2))
        margin(8.px, 0.px)
    }
    
    // Port styles
    val portContainer by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        marginBottom(4.px)
        marginTop(4.px)
        property("gap", "8px")
    }
    
    val portContainerInput by style {
        flexDirection(FlexDirection.Row)
    }
    
    val portContainerOutput by style {
        flexDirection(FlexDirection.RowReverse)
    }
    
    val portConnector by style {
        width(16.px)
        height(16.px)
        backgroundColor(rgba(255, 255, 255, 0.9))
        property("border-radius", "50%")
        property("border", "2px solid rgba(0, 0, 0, 0.3)")
        property("transition", "all 0.2s ease-in-out")
        property("box-shadow", "0 0 0 rgba(255, 255, 255, 0)")
    }
    
    val portConnectorHover by style {
        property(":hover", "{box-shadow: 0 0 8px rgba(255, 255, 255, 0.8);}")
    }
    
    val portConnectorInput by style {
        cursor("crosshair")
        property("margin-left", "-20px") // Position input ports on the left edge
    }
    
    val portConnectorOutput by style {
        cursor("grab")
        property("margin-right", "-20px") // Position output ports on the right edge
    }
    
    val portLabel by style {
        color(Color.white)
        fontSize(12.px)
    }
    
    // Canvas wrapper styles
    val nodeEditorCanvasWrapper by style {
        property("flex-grow", "1")
        property("width", "0") // Set width to 0 to prevent squishing side panels
        backgroundColor(rgba(35, 35, 40, 0.85)) // Slightly different tint for the padded area
        padding(50.px) // Set consistent padding to 16px on all sides
        position(Position.Relative) // Important: This makes absolute positioning of children work
        property("overflow", "auto") // Enable scrollbars
        // Grid background removed as requested
    }

    // Canvas styles
    val nodeEditorCanvas by style {
        width(100.percent) // Reasonable width that allows scrolling
        height(100.percent) // Reasonable height that allows scrolling
        position(Position.Relative) // Changed to relative positioning
        flex(1)
        property("overflow", "visible")
        backgroundColor(rgba(40, 40, 40, 0.8)) // Original background color for the usable area
        property("border", "2px dotted rgba(255, 255, 255, 0.3)") // Dotted border for usable area
        // Ensure the canvas never gets smaller than the available space
        // Account for padding (16px on each side) and border (2px on each side)
        property("min-width", "calc(100% - 36px)") // 100% - (16px*2 + 2px*2)
        property("min-height", "calc(100% - 36px)") // 100% - (16px*2 + 2px*2)
        borderRadius(8.px)
        boxSizing("border-box")
    }

    val emptyCanvasMessage by style {
        width(100.percent)
        height(100.percent)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        color(Color.white)
    }
    
    // Sidebar styles
    val sidebarContainer by style {
        width(250.px)
        backgroundColor(rgba(20, 20, 20, 0.8))
        padding(16.px)
        property("overflow-y", "auto")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("gap", "16px")
    }
    
    val sidebarSection by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("gap", "8px")
        marginBottom(16.px)
    }
    
    val sectionHeader by style {
        color(Color.white)
        margin(0.px)
        marginTop(16.px)
        marginBottom(8.px)
    }
    
    // Form styles
    val formLabel by style {
        color(Color.white)
        fontSize(14.px)
        display(DisplayStyle.Block)
        marginBottom(4.px)
    }
    
    val formInput by style {
        width(100.percent)
        padding(8.px)
        backgroundColor(rgba(255, 255, 255, 0.1))
        color(Color.white)
        property("border", "1px solid rgba(255, 255, 255, 0.2)")
        property("border-radius", "4px")
        outline("none")
    }
    
    // Draggable item styles
    val draggableItem by style {
        padding(8.px, 12.px)
        backgroundColor(rgba(60, 60, 60, 0.8))
        color(Color.white)
        property("border-radius", "4px")
        cursor("grab")
        marginBottom(8.px)
        property("user-select", "none")
    }
    
    // Connection styles
    val connectionLine by style {
        position(Position.Absolute)
        property("pointer-events", "auto") // Enable mouse events
        property("z-index", "1")
        left(0.px)
        top(0.px)
        width(0.px)
        height(0.px)
        cursor("pointer") // Show pointer cursor on hover
    }
    
    val connectionSvgContainer by style {
        position(Position.Absolute)
        left(0.px)
        top(0.px)
        width(0.px)
        height(0.px)
    }
    
    val connectionSelected by style {
        property("z-index", "3") // Bring selected connection to front
    }
    
    val connectionPathNormal by style {
        property("stroke-width", "2")
        property("stroke", "rgba(255, 255, 255, 0.7)")
        property("transition", "stroke 0.2s, stroke-width 0.2s")
        property(":hover", "{stroke: rgba(255, 255, 255, 0.9); stroke-width: 3;}")
    }
    
    val connectionPathSelected by style {
        property("stroke-width", "3")
        property("stroke", "rgba(100, 200, 255, 0.9)")
        property("filter", "drop-shadow(0 0 3px rgba(100, 200, 255, 0.5))")
    }
    
    val connectionSourceIndicator by style {
        position(Position.Absolute)
        width(12.px)
        height(12.px)
        property("border-radius", "50%")
        property("z-index", "10") // Ensure it's above other elements
        property("box-shadow", "0 0 8px rgba(100, 200, 255, 0.8)")
    }
    
    // Dialog styles
    val dialogContent by style {
        width(90.percent)
        property("max-width", "1200px")
        height(90.vh)
        backgroundColor(rgba(30, 30, 30, 0.95))
        property("border-radius", "8px")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
    }
    
    val dialogContentFullscreen by style {
        width(100.percent)
        height(100.vh)
        property("max-width", "100vw")
        property("border-radius", "0")
        backgroundColor(rgba(30, 30, 30, 0.95))
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
    }
    
    val dialogHeader by style {
        padding(16.px)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        property("border-bottom", "1px solid rgba(255, 255, 255, 0.2)")
    }
    
    val dialogTitle by style {
        margin(0.px)
        color(Color.white)
    }
    
    val closeButton by style {
        backgroundColor(Color.transparent)
        property("border", "0")
        color(Color.white)
        cursor("pointer")
        fontSize(20.px)
    }
    
    val mainContentArea by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Row)
        property("flex-grow", "1")
        property("overflow", "hidden")
    }
    
    val dialogFooter by style {
        padding(16.px)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        property("gap", "8px")
        property("border-top", "1px solid rgba(255, 255, 255, 0.2)")
    }
    
    val errorMessage by style {
        color(Color.red)
        property("flex-grow", "1")
    }
    
    val cancelButton by style {
        padding(8.px, 16.px)
        backgroundColor(rgba(255, 255, 255, 0.1))
        color(Color.white)
        property("border", "1px solid rgba(255, 255, 255, 0.2)")
        property("border-radius", "4px")
        cursor("pointer")
        marginRight(8.px)
    }
    
    val saveButton by style {
        padding(8.px, 16.px)
        color(Color.white)
        property("border", "0")
        property("border-radius", "4px")
    }
    
    val saveButtonEnabled by style {
        backgroundColor(rgba(0, 120, 255, 0.7))
        cursor("pointer")
    }
    
    val saveButtonDisabled by style {
        backgroundColor(rgba(100, 100, 100, 0.5))
        cursor("default")
    }
    
    // Property panel styles
    val propertyPanel by style {
        width(250.px)
        backgroundColor(rgba(30, 30, 30, 0.9))
        padding(16.px)
        property("border-left", "1px solid rgba(255, 255, 255, 0.2)")
        property("overflow-y", "auto")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("gap", "16px")
    }
    
    val propertyPanelHeader by style {
        color(Color.white)
        margin(0.px)
        marginBottom(16.px)
        fontSize(16.px)
        property("border-bottom", "1px solid rgba(255, 255, 255, 0.2)")
        padding(0.px, 0.px, 8.px, 0.px) // top, right, bottom, left
    }
    
    val propertyGroup by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("gap", "4px")
        marginBottom(12.px)
    }
    
    val propertyLabel by style {
        color(Color.white)
        fontSize(14.px)
        marginBottom(4.px)
    }
    
    val propertyValue by style {
        color(rgba(255, 255, 255, 0.8))
        fontSize(14.px)
        padding(8.px)
        backgroundColor(rgba(50, 50, 50, 0.5))
        property("border-radius", "4px")
    }
    
    val propertyInput by style {
        width(100.percent)
        padding(8.px)
        backgroundColor(rgba(50, 50, 50, 0.5))
        color(Color.white)
        property("border", "1px solid rgba(255, 255, 255, 0.2)")
        property("border-radius", "4px")
        outline("none")
        fontSize(14.px)
        property("box-sizing", "border-box")
    }
    
    val propertyTextArea by style {
        width(100.percent)
        padding(8.px)
        backgroundColor(rgba(50, 50, 50, 0.5))
        color(Color.white)
        property("border", "1px solid rgba(255, 255, 255, 0.2)")
        property("border-radius", "4px")
        outline("none")
        fontSize(14.px)
        property("resize", "vertical")
        property("min-height", "80px")
        property("box-sizing", "border-box")
    }
    
    // Button management styles
    val buttonList by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("gap", "8px")
        marginBottom(8.px)
    }

    val buttonItem by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.SpaceBetween)
        padding(4.px, 8.px)
        backgroundColor(rgba(0, 0, 0, 0.1))
        borderRadius(4.px)
    }

    val removeButton by style {
        padding(2.px, 6.px)
        backgroundColor(rgba(255, 0, 0, 0.2))
        borderRadius(4.px)
        cursor("pointer")
        marginLeft(8.px)
        
        property(":hover", "{background-color: rgba(255, 0, 0, 0.4);}")
    }

    val addButtonContainer by style {
        display(DisplayStyle.Flex)
        property("gap", "8px")
        marginTop(8.px)
    }

    val addButton by style {
        padding(4.px, 8.px)
        backgroundColor(rgba(0, 128, 0, 0.2))
        borderRadius(4.px)
        cursor("pointer")
        
        property(":hover", "{background-color: rgba(0, 128, 0, 0.4);}")
    }
    
    val propertyButton by style {
        padding(8.px, 16.px)
        backgroundColor(rgba(0, 120, 255, 0.7))
        color(Color.white)
        property("border", "0")
        property("border-radius", "4px")
        cursor("pointer")
        marginTop(8.px)
        property("align-self", "flex-end")
        
        property(":hover", "{background-color: rgba(0, 140, 255, 0.8);}")
        property(":active", "{background-color: rgba(0, 100, 255, 0.9);}")
    }
    
    // Connection preview styles
    val connectionPreviewCursor by style {
        position(Position.Absolute)
        width(16.px)
        height(16.px)
        property("border-radius", "50%")
        property("border", "2px solid")
        property("z-index", "10") // Ensure it's above other elements
        property("box-shadow", "0 0 12px")
        property("pointer-events", "none") // Don't interfere with mouse events
    }
    
    val connectionPreviewPulse by style {
        property("animation", "pulse 1.5s infinite")
    }
}
