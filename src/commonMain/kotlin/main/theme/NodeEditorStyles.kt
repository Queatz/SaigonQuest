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
import org.jetbrains.compose.web.css.overflow
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
        color(Theme.colors.onBackground)
        borderRadius(12.px)
        property("box-shadow", "0 2px 10px ${Theme.colors.shadowMedium}")
        property("user-select", "none") // Prevent text selection during drag

        dark(self) {
            color(Theme.colors.onBackgroundDark)
        }

        mobile(self) {
            width(120.px)
        }
    }
    
    val nodeSelected by style {
        property("border", "2px solid ${Theme.colors.accentInfoBlueStrong}")
        property("z-index", "10") // Bring to front when selected

        dark(self) {
            property("border", "2px solid ${Theme.colors.onBackgroundDark}")
        }
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
    val eventNodeColor by style { backgroundColor(Theme.colors.nodeEvent) }
    
    val dialogueNodeColor by style { backgroundColor(Theme.colors.nodeDialogue) }
    
    val actionNodeColor by style { backgroundColor(Theme.colors.nodeAction) }
    
    val inputNodeColor by style { backgroundColor(Theme.colors.nodeInput) }
    
    val defaultNodeColor by style { backgroundColor(Theme.colors.nodeDefault) }
    
    // Node header
    val nodeHeader by style {
        fontWeight("bold")
        marginBottom(8.px)
        textAlign("center")
        overflow("hidden")
        property("text-overflow", "ellipsis")
    }
    // Node header
    val nodeDescription by style {
        fontSize(80.percent)
        marginBottom(8.px)
        textAlign("center")
        overflow("hidden")
        property("text-overflow", "ellipsis")
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
        backgroundColor(Theme.colors.panelBorderSubtle)
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
        backgroundColor(Theme.colors.white90)
        property("border-radius", "50%")
        property("border", "2px solid ${Theme.colors.black30}")
        property("transition", "all 0.2s ease-in-out")
        property("box-shadow", "0 0 0 ${Theme.colors.white04}")

        mobile(self) {
            width(24.px)
            height(24.px)
        }
    }
    
    val portConnectorHover by style {
        self + hover style {
            property("box-shadow", "0 0 8px ${Theme.colors.white80}")
        }
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
        color(Theme.colors.onBackground)
        fontSize(12.px)

        dark(self) {
            color(Theme.colors.onBackgroundDark)
        }
    }
    
    // Canvas wrapper styles
    val nodeEditorCanvasWrapper by style {
        property("flex-grow", "1")
        property("width", "0") // Set width to 0 to prevent squishing side panels
        backgroundColor(Theme.colors.panelGlass) // Slightly different tint for the padded area
        padding(16.px) // Reduce padding for better use of space
        position(Position.Relative) // Important: This makes absolute positioning of children work
        property("overflow", "auto") // Enable scrollbars
        borderRadius(12.px)

        dark(self) {
            backgroundColor(Theme.colors.panelGlassDark)
        }
        mobile(self) {
            // On mobile, let it size naturally in a column layout and use tighter padding
            property("width", "auto")
            padding(8.px)
        }
    }

    // Canvas styles
    val nodeEditorCanvas by style {
        width(100.percent) // Reasonable width that allows scrolling
        height(100.percent) // Reasonable height that allows scrolling
        position(Position.Relative) // Changed to relative positioning
        flex(1)
        property("overflow", "visible")
        property("border", "2px dotted ${Theme.colors.panelBorderSubtle}") // Dotted border for usable area
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
        color(Theme.colors.onBackgroundDark)
    }
    
    // Sidebar styles
    val sidebarContainer by style {
        width(250.px)
        backgroundColor(Theme.colors.surface)
        color(Theme.colors.onSurface)
        padding(16.px)
        property("overflow-y", "auto")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("gap", "16px")
        boxSizing("border-box")

        dark(self) {
            backgroundColor(Theme.colors.surfaceDark)
            color(Theme.colors.onSurfaceDark)
        }
        mobile(self) {
            width(100.percent)
            padding(12.px)
            property("max-height", "40vh")
            property("border-bottom", "1px solid ${Theme.colors.panelBorderSubtle}")
        }
    }

    val sectionHeader by style {
        margin(0.px)
        marginTop(16.px)
        marginBottom(8.px)
    }

    // Draggable item styles
    val draggableItem by style {
        padding(8.px, 12.px)
        backgroundColor(Theme.colors.surfaceVariant)
        color(Theme.colors.onSurfaceVariant)
        borderRadius(12.px)
        cursor("grab")
        property("user-select", "none")

        dark(self) {
            backgroundColor(Theme.colors.surfaceVariantDark)
            color(Theme.colors.onSurfaceVariantDark)
        }
    }
    
    // Connection styles
    val connectionLine by style {
        position(Position.Absolute)
        property("pointer-events", "none") // Let only the SVG path capture events
        property("z-index", "1")
        left(0.px)
        top(0.px)
        width(100.percent)
        height(100.percent)
        cursor("pointer") // Show pointer cursor on hover
    }
    
    val connectionSvgContainer by style {
        position(Position.Absolute)
        left(0.px)
        top(0.px)
        width(100.percent)
        height(100.percent)
    }
    
    val connectionSelected by style {
        property("z-index", "3") // Bring selected connection to front
    }

    val connectionSourceIndicator by style {
        position(Position.Absolute)
        width(12.px)
        height(12.px)
        property("border-radius", "50%")
        property("z-index", "10") // Ensure it's above other elements
        property("box-shadow", "0 0 8px ${Theme.colors.accentInfoBlueStrong}")
    }
    val closeButton by style {
        backgroundColor(Color.transparent)
        property("border", "0")
        cursor("pointer")
        fontSize(20.px)
    }
    
    val mainContentArea by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Row)
        property("flex-grow", "1")
        property("overflow", "hidden")
        property("gap", "16px")

        mobile(self) {
            flexDirection(FlexDirection.Column)
            property("gap", "8px")
            property("overflow", "auto")
        }
    }

    val errorMessage by style {
        color(Theme.colors.error)
        property("flex-grow", "1")
    }
    // Property panel styles
    val propertyPanel by style {
        width(250.px)
        backgroundColor(Theme.colors.surfaceVariant)
        color(Theme.colors.onSurfaceVariant)
        padding(16.px)
        property("border-left", "1px solid ${Theme.colors.panelBorderSubtle}")
        property("overflow-y", "auto")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("gap", "16px")
        boxSizing("border-box")
        borderRadius(12.px)

        dark(self) {
            backgroundColor(Theme.colors.surfaceVariantDark)
            color(Theme.colors.onSurfaceVariantDark)
        }
        mobile(self) {
            width(100.percent)
            padding(12.px)
            property("border-left", "none")
            property("border-top", "1px solid ${Theme.colors.panelBorderSubtle}")
            property("max-height", "45vh")
        }
    }
    
    val propertyPanelHeader by style {
        margin(0.px)
        marginBottom(16.px)
        fontSize(16.px)
        property("border-bottom", "1px solid ${Theme.colors.panelBorderSubtle}")
        padding(0.px, 0.px, 8.px, 0.px) // top, right, bottom, left
    }
    
    val propertyGroup by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("gap", "4px")
        marginBottom(12.px)
    }
    
    val propertyLabel by style {
        fontSize(14.px)
        marginBottom(4.px)
    }
    
    val propertyValue by style {
        color(Theme.colors.textSecondary)
        fontSize(14.px)
        padding(8.px)
        borderRadius(4.px)
    }
    
    val propertyInput by style {
        width(100.percent)
        padding(8.px)
        backgroundColor(Theme.colors.surfaceVariant)
        color(Theme.colors.onSurfaceVariant)
        property("border", "1px solid ${Theme.colors.outlineDark}")
        borderRadius(4.px)
        outline("none")
        fontSize(14.px)
        boxSizing("border-box")
        borderRadius(8.px)

        dark(self) {
            backgroundColor(Theme.colors.surfaceVariantDark)
            color(Theme.colors.onSurfaceVariantDark)
        }
    }
    
    val propertyTextArea by style {
        width(100.percent)
        padding(8.px)
        backgroundColor(Theme.colors.surfaceVariant)
        color(Theme.colors.onSurfaceVariant )
        property("border", "1px solid ${Theme.colors.outlineDark}")
        borderRadius(4.px)
        outline("none")
        fontSize(14.px)
        property("resize", "vertical")
        property("min-height", "80px")
        boxSizing("border-box")
        borderRadius(8.px)

        dark(self) {
            backgroundColor(Theme.colors.surfaceVariantDark)
            color(Theme.colors.onSurfaceVariantDark)
        }
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
        padding(8.px)
        backgroundColor(Theme.colors.surfaceVariantDark)
        borderRadius(4.px)
    }

    val removeButton by style { backgroundColor(Theme.colors.error) }

    val addButtonContainer by style {
        display(DisplayStyle.Flex)
        property("gap", "8px")
        marginTop(8.px)
    }

    val addButton by style { backgroundColor(Theme.colors.success) }
    
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

    // Utility class to disable native touch scrolling and scroll chaining during drags
    val disableTouchScroll by style {
        property("touch-action", "none")
        property("overscroll-behavior", "contain")
    }
}
