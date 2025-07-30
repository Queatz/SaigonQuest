package main.theme

import org.jetbrains.compose.web.css.GenericStyleSheetBuilder
import org.jetbrains.compose.web.css.StyleSheet
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.fontWeight
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.media
import org.jetbrains.compose.web.css.mediaMaxWidth
import org.jetbrains.compose.web.css.mediaMinWidth
import org.jetbrains.compose.web.css.minHeight
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.selectors.CSSSelector
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.right
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.bottom
import org.jetbrains.compose.web.css.cursor

object Styles : StyleSheet() {
    // Common styles for panels and dialogs
    val overlayBase by style {
        position(Position.Fixed)
        top(0.px)
        left(0.px)
        width(100.percent)
        height(100.percent)
        backgroundColor(rgba(0, 0, 0, 0.5))
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        property("z-index", "1000")
        property("pointer-events", "auto")
        // Add background blur effect
        property("backdrop-filter", "blur(2px)")
        property("-webkit-backdrop-filter", "blur(2px)")
    }
    
    // WorldUI styles
    val worldUIContainer by style {
        position(Position.Fixed)
        top(0.px)
        left(0.px)
        width(100.percent)
        height(100.vh)
        // Make the div non-interactive for mouse events to allow interaction with the 3D scene
        property("pointer-events", "none")
    }
    
    val worldNameContainer by style {
        position(Position.Absolute)
        top(20.px)
        left(50.percent)
        property("transform", "translateX(-50%)")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        alignItems(AlignItems.Center)
        // Make text elements interactive
        property("pointer-events", "auto")
    }
    
    val worldTitle by style {
        margin(0.px)
        color(Color.white)
        property("text-shadow", "0px 0px 4px rgba(0, 0, 0, 0.5)")
    }
    
    val worldSubtitle by style {
        margin(0.px)
        color(Color.white)
        property("text-shadow", "0px 0px 4px rgba(0, 0, 0, 0.5)")
    }
    
    val topRightContainer by style {
        position(Position.Fixed) // Changed from Absolute to Fixed for consistent positioning
        top(20.px)
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("gap", "10px")
        // Make the container scrollable if there's not enough space
        // Calculate max height to leave space for bottom buttons and prevent overlap
        property("max-height", "calc(100vh - 180px)") // Increased space for bottom buttons
        property("overflow-y", "auto")
        // Add padding at the bottom to create more space between top and bottom buttons
        property("padding-bottom", "20px")
        // Style the scrollbar to be subtle and match the UI
        property("scrollbar-width", "thin")
        property("scrollbar-color", "rgba(255, 255, 255, 0.3) transparent")
        // Webkit scrollbar styling
        property("&::-webkit-scrollbar", "{ width: 6px; }")
        property("&::-webkit-scrollbar-track", "{ background: transparent; }")
        property("&::-webkit-scrollbar-thumb", "{ background-color: rgba(255, 255, 255, 0.3); border-radius: 3px; }")
        // Add z-index to ensure proper stacking
        property("z-index", "5")
    }
    
    val bottomRightContainer by style {
        position(Position.Fixed) // Changed from Absolute to Fixed for guaranteed visibility
        bottom(20.px)
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("gap", "10px")
        // Add z-index to ensure these buttons are below dialog backdrops but above other elements
        property("z-index", "900") // Higher than top container but below dialogs (z-index: 1000)
    }
    
    // Combined container for right side buttons (merging top and bottom sections)
    val rightButtonsContainer by style {
        position(Position.Fixed)
        top(20.px)
        bottom(20.px) // Ensures container doesn't extend beyond screen
        display(DisplayStyle.Flex)
        // Use column-reverse to prioritize bottom buttons
        flexDirection(FlexDirection.ColumnReverse)
        property("gap", "10px")
        // Make the container scrollable if needed
        property("overflow-y", "auto")
        // Use FlexStart with column-reverse to ensure bottom buttons are always visible
        justifyContent(JustifyContent.FlexStart)
        // Style the scrollbar to be subtle and match the UI
        property("scrollbar-width", "thin")
        property("scrollbar-color", "rgba(255, 255, 255, 0.3) transparent")
        // Webkit scrollbar styling
        property("&::-webkit-scrollbar", "{ width: 6px; }")
        property("&::-webkit-scrollbar-track", "{ background: transparent; }")
        property("&::-webkit-scrollbar-thumb", "{ background-color: rgba(255, 255, 255, 0.3); border-radius: 3px; }")
        // Add z-index to ensure buttons are always visible but below dialogs
        property("z-index", "10")
    }
    
    // Spacer for pushing bottom buttons to the end of the container
    val buttonSpacer by style {
        // Create a flexible spacer that pushes bottom buttons to the end
        property("flex-grow", "1")
        // Minimum height to ensure some spacing even when there's not much vertical space
        property("min-height", "2rem")
    }
    
    val inventoryTray by style {
        position(Position.Absolute)
        bottom(20.px)
        left(50.percent)
        property("transform", "translateX(-50%)")
        display(DisplayStyle.Flex)
        property("gap", "4px")
    }
    
    val bottomText by style {
        position(Position.Absolute)
        bottom(4.px)
        left(4.px)
        fontSize(8.px)
        color(Color.white)
        property("text-shadow", "0px 0px 2px rgba(0, 0, 0, 0.7)")
        // Make text elements interactive
        property("pointer-events", "auto")
    }
    
    // CircleButton styles
    val circleButton by style {
        width(48.px)
        height(48.px)
        property("border-radius", "50%")
        backgroundColor(Color.white)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        position(Position.Relative)
        cursor("pointer")
        // Prevent button from shrinking in flex containers
        property("flex-shrink", "0")
        // Make button interactive
        property("pointer-events", "auto")
    }
    
    val badgeBase by style {
        position(Position.Absolute)
        top(0.px)
        right(0.px)
        backgroundColor(Color.red)
        color(Color.white)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        property("font-weight", "bold")
    }
    
    val normalBadge by style {
        property("min-width", "16px")
        height(16.px)
        padding(2.px)
        property("border-radius", "8px")
        fontSize(10.px)
    }
    
    val chatBadge by style {
        property("min-width", "20px")
        height(20.px)
        padding(2.px)
        property("border-radius", "10px")
        fontSize(12.px)
    }
    
    val redDotIndicator by style {
        position(Position.Absolute)
        top(4.px)
        right(4.px)
        width(8.px)
        height(8.px)
        property("border-radius", "50%")
        backgroundColor(Color.red)
    }
    
    // Panel styles
    val sidePanel by style {
        position(Position.Absolute)
        top(0.px)
        right(0.px)
        width(320.px)
        height(100.vh)
        backgroundColor(rgba(0, 0, 0, 0.7))
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("pointer-events", "auto")
        // Add background blur effect
        property("backdrop-filter", "blur(2px)")
        property("-webkit-backdrop-filter", "blur(2px)")
    }
    
    val panelHeader by style {
        padding(16.px)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        property("border-bottom", "1px solid rgba(255, 255, 255, 0.2)")
    }
    
    val panelTitle by style {
        margin(0.px)
        color(Color.white)
    }
    
    val closeButton by style {
        backgroundColor(Color.transparent)
        property("border", "0")
        color(Color.white)
        property("cursor", "pointer")
        fontSize(20.px)
    }
    
    // Responsive panel styles
    val dialogContainer by style {
        width(90.percent)
        property("max-width", "400px")
        height(80.vh)
        backgroundColor(rgba(30, 30, 30, 0.95))
        property("border-radius", "8px")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        // Add background blur effect
        property("backdrop-filter", "blur(2px)")
        property("-webkit-backdrop-filter", "blur(2px)")
    }
    
    val panelContent by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("flex-grow", "1")
        property("overflow", "hidden")
    }
    
    // Dialog styles
    val dialogContent by style {
        width(320.px)
        backgroundColor(Theme.colors.background)
        property("border-radius", "8px")
        padding(16.px)
        property("box-shadow", "0 4px 8px rgba(0, 0, 0, 0.2)")
    }
    
    val dialogHeader by style {
        margin(0.px)
        marginBottom(16.px)
        color(Theme.colors.primary)
        property("text-align", "center")
    }
    
    // Common style for input elements with box-sizing: border-box
    val inputField by style {
        width(100.percent)
        padding(8.px)
        backgroundColor(rgba(255, 255, 255, 0.1))
        color(Color.white)
        property("border", "1px solid rgba(255, 255, 255, 0.2)")
        property("border-radius", "4px")
        property("outline", "none")
        property("box-sizing", "border-box")
    }
    
    // Style for inventory box
    val inventoryBox by style {
        width(48.px)
        height(48.px)
        backgroundColor(rgba(255, 255, 255, 0.2))
        property("border", "1px solid rgba(255, 255, 255, 0.5)")
        property("border-radius", "4px")
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        cursor("pointer")
        // Make box interactive
        property("pointer-events", "auto")
    }
    
    // ObjectEditorDialog styles
    val objectEditorDialogContent by style {
        width(90.percent)
        property("max-width", "600px")
        backgroundColor(rgba(30, 30, 30, 0.95))
        property("border-radius", "8px")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
    }
    
    val objectEditorDialogHeader by style {
        padding(16.px)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        property("border-bottom", "1px solid rgba(255, 255, 255, 0.2)")
    }
    
    val objectEditorDialogTitle by style {
        margin(0.px)
        color(Color.white)
    }
    
    val objectEditorDialogCloseButton by style {
        backgroundColor(Color.transparent)
        property("border", "0")
        color(Color.white)
        cursor("pointer")
        fontSize(20.px)
    }
    
    val objectEditorDialogFormContent by style {
        property("flex-grow", "1")
        padding(16.px)
        property("overflow-y", "auto")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("gap", "16px")
    }
    
    val objectEditorDialogLogicPreview by style {
        width(100.percent)
        height(200.px)
        backgroundColor(rgba(20, 20, 20, 0.5))
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        color(Color.white)
        property("border", "1px solid rgba(255, 255, 255, 0.3)")
        property("border-radius", "4px")
        property("margin-top", "16px")
        cursor("pointer")
        property("transition", "background-color 0.2s, border-color 0.2s")
    }
    
    val objectEditorDialogLogicTitle by style {
        margin(0.px)
        marginBottom(8.px)
        fontSize(16.px)
        fontWeight("bold")
    }
    
    val objectEditorDialogLogicDescription by style {
        fontSize(14.px)
        color(rgba(255, 255, 255, 0.7))
        property("text-align", "center")
        padding(0.px, 16.px)
    }
    
    val objectEditorDialogFooter by style {
        padding(16.px)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.FlexEnd)
        property("gap", "8px")
        property("border-top", "1px solid rgba(255, 255, 255, 0.2)")
    }
    
    val objectEditorDialogCancelButton by style {
        padding(8.px, 16.px)
        backgroundColor(rgba(255, 255, 255, 0.1))
        color(Color.white)
        property("border", "1px solid rgba(255, 255, 255, 0.2)")
        property("border-radius", "4px")
        cursor("pointer")
    }
    
    val objectEditorDialogSaveButton by style {
        padding(8.px, 16.px)
        color(Color.white)
        property("border", "0")
        property("border-radius", "4px")
        property("margin-left", "8px")
    }
    
    val objectEditorDialogSaveButtonActive by style {
        backgroundColor(rgba(0, 120, 255, 0.7))
        cursor("pointer")
    }
    
    val objectEditorDialogSaveButtonInactive by style {
        backgroundColor(rgba(100, 100, 100, 0.5))
        cursor("default")
    }
    
    val objectEditorDialogErrorMessage by style {
        color(Color.red)
        margin(0.px)
    }

    init {
        "body" style {
            margin(0.cssRem)
            padding(0.cssRem)
            property("font-family", "'Ysabeau Infant', sans-serif")
            property("-webkit-font-smoothing", "antialiased")
            property("-moz-osx-font-smoothing", "grayscale")
            minHeight(100.vh)
            backgroundColor(Theme.colors.background)

            dark(selector("body")) {
                backgroundColor(Theme.colors.backgroundDark)
            }
        }

        "a" {
            fontWeight("bold")
            property("color", Theme.colors.primary.toString())

            visited {
                property("color", Theme.colors.primary.toString())
            }
        }
    }
}

fun <TBuilder> GenericStyleSheetBuilder<TBuilder>.dark(self: CSSSelector, block: TBuilder.() -> Unit) {
    media("(prefers-color-scheme: dark)") {
        self style {
            block()
        }
    }
}

fun <TBuilder> GenericStyleSheetBuilder<TBuilder>.mobile(self: CSSSelector, block: TBuilder.() -> Unit) {
    media(mediaMaxWidth(640.px)) {
        self style {
            block()
        }
    }
}

fun <TBuilder> GenericStyleSheetBuilder<TBuilder>.desktop(self: CSSSelector, block: TBuilder.() -> Unit) {
    media(mediaMinWidth(641.px)) {
        self style {
            block()
        }
    }
}
