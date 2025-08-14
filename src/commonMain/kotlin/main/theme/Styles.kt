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
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.bottom
import org.jetbrains.compose.web.css.boxSizing
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.opacity
import org.jetbrains.compose.web.css.fontStyle
import org.jetbrains.compose.web.css.overflowY

object Styles : StyleSheet() {
    // Fullscreen fade overlay for world transitions
    val fadeOverlay by style {
        position(Position.Fixed)
        top(0.px)
        left(0.px)
        width(100.percent)
        height(100.percent)
        backgroundColor(Color.black)
        // Start above most UI, below critical banners if needed
        property("z-index", "2500")
        // Block interactions while visible
        property("pointer-events", "auto")
        // Animate opacity and backdrop blur
        property("transition", "opacity 250ms ease, backdrop-filter 250ms ease, -webkit-backdrop-filter 250ms ease")
        // Default blur when shown; can be overridden inline
        property("backdrop-filter", "blur(8px)")
        property("-webkit-backdrop-filter", "blur(8px)")
        // Fully opaque by default; can be overridden inline
        opacity(1)
    }
    // Loading screen styles
    val loadingScreenContainer by style {
        position(Position.Absolute)
        top(0.px)
        left(0.px)
        width(100.percent)
        height(100.vh)
        backgroundColor(Color.black)
        color(Color.white)
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        // Ensure loading text appears above the fade overlay
        property("z-index", "3000")
        padding(16.px)
        property("text-align", "center")
        property("overflow", "hidden")
        property("box-sizing", "border-box")
    }
    
    val loadingScreenButtonContainer by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Row)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        property("gap", "16px")
        property("margin-top", "24px")
    }
    
    val loadingScreenPrimaryButton by style {
        padding(12.px, 24.px)
        backgroundColor(rgba(0, 120, 255, 0.7))
        color(Color.white)
        property("border", "0")
        property("border-radius", "4px")
        cursor("pointer")
        fontSize(16.px)
    }
    
    val loadingScreenSecondaryButton by style {
        padding(12.px, 24.px)
        backgroundColor(rgba(255, 255, 255, 0.2))
        color(Color.white)
        property("border", "0")
        property("border-radius", "4px")
        cursor("pointer")
        fontSize(16.px)
    }
    
    // Canvas styles
    val canvasBase by style {
        width(100.percent)
        height(100.vh)
        display(DisplayStyle.Block)
    }
    
    val canvasLowGraphics by style {
        property("image-rendering", "pixelated")
        property("image-rendering", "-moz-crisp-edges")
        property("image-rendering", "crisp-edges")
    }
    
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
        overflowY("auto")
        boxSizing("border-box")
        property("z-index", "1000")
        property("pointer-events", "auto")
        // Add background blur effect
        property("backdrop-filter", "blur(2px)")
        property("-webkit-backdrop-filter", "blur(2px)")
        property("isolation", "isolate") // Creates a new stacking context
        property("contain", "layout paint size") // Improves performance and isolation
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
        property("max-height", "calc(100vh - 280px)") // Increased space for bottom buttons
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

        dark(self) {
            backgroundColor(Color.black)
            color(Color.white)
        }
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
    
    // Icon renderer styles
    val iconRenderer by style {
        width(32.px)
        height(32.px)
        display(DisplayStyle.Block)
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
    
    // Base dialog styles (for the new BaseDialog component)
    val baseDialogContainer by style {
        width(90.percent)
        property("max-width", "400px")
        backgroundColor(rgba(30, 30, 30, 0.95))
        property("border-radius", "8px")
        property("box-shadow", "0 4px 16px rgba(0, 0, 0, 0.2)")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        overflowY("auto")
    }
    
    val baseDialogHeader by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        padding(16.px)
        property("border-bottom", "1px solid rgba(255, 255, 255, 0.1)")
    }
    
    val baseDialogTitle by style {
        margin(0.px)
        color(Color.white)
    }
    
    val baseDialogCloseButton by style {
        backgroundColor(Color.transparent)
        property("border", "0")
        color(Color.white)
        cursor("pointer")
        fontSize(20.px)
    }
    
    val baseDialogContent by style {
        padding(24.px)
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("flex-grow", "1")
    }
    
    val baseDialogFooter by style {
        padding(16.px)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.FlexEnd)
        property("gap", "8px")
        property("border-top", "1px solid rgba(255, 255, 255, 0.1)")
    }
    
    val baseDialogButtonPrimary by style {
        padding(8.px, 16.px)
        backgroundColor(Theme.colors.primary)
        color(Theme.colors.onPrimary)
        property("border", "0")
        property("border-radius", "4px")
        property("cursor", "pointer")
    }
    
    val baseDialogButtonSecondary by style {
        padding(8.px, 16.px)
        backgroundColor(Color.transparent)
        color(Theme.colors.primary)
        property("border", "0")
        property("border-radius", "4px")
        property("cursor", "pointer")
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
    
    // Toggle Switch styles
    val toggleSwitchContainer by style {
        display(DisplayStyle.InlineBlock)
        position(Position.Relative)
        width(40.px)
        height(24.px)
        cursor("pointer")
    }
    
    val toggleSwitchInput by style {
        // Hide the default checkbox but keep it accessible
        property("opacity", "0")
        width(0.px)
        height(0.px)
        position(Position.Absolute)
    }
    
    val toggleSwitchTrack by style {
        position(Position.Absolute)
        cursor("pointer")
        property("top", "0")
        property("left", "0")
        property("right", "0")
        property("bottom", "0")
        property("border-radius", "12px")
        property("transition", "background-color 0.3s")
    }
    
    val toggleSwitchKnob by style {
        position(Position.Absolute)
        property("content", "''")
        height(18.px)
        width(18.px)
        property("top", "3px")
        backgroundColor(Theme.colors.onPrimary)
        property("border-radius", "50%")
        property("transition", "left 0.3s")
    }
    
    // Language Switcher styles
    val languageSwitcherContainer by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Row)
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.SpaceBetween)
        borderRadius(18.px)
        backgroundColor(Theme.colors.surfaceVariant)
        property("overflow", "hidden")
        cursor("pointer")
    }
    
    val languageSwitcherOption by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.Center)
        padding(8.px, 16.px)  // Increased padding for better appearance
        cursor("pointer")
        property("transition", "background-color 0.3s, color 0.3s")
    }
    
    val languageSwitcherOptionActive by style {
        backgroundColor(Theme.colors.primary)
        color(Theme.colors.onPrimary)
    }
    
    val languageSwitcherOptionInactive by style {
        backgroundColor(Color.transparent)
        color(Theme.colors.onSurfaceVariant)
    }
    
    // Chat Messages styles
    val chatMessagesContainer by style {
        padding(16.px)
        property("overflow-y", "auto")
        property("flex-grow", "1")
        display(DisplayStyle.Flex)
        property("flex-direction", "column")
        height(0.cssRem)
    }
    
    val chatLoadingContainer by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        gap(16.px)
        width(100.percent)
    }
    
    val chatLoadingSpinner by style {
        width(40.px)
        height(40.px)
        property("border", "4px solid rgba(255, 255, 255, 0.1)")
        property("border-top", "4px solid rgba(255, 255, 255, 0.8)")
        property("border-radius", "50%")
        property("animation", "spin 1s linear infinite")
        property("@keyframes spin", "{0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); }}")
    }
    
    val chatLoadingText by style {
        color(rgba(255, 255, 255, 0.5))
        property("text-align", "center")
        margin(0.px)
    }
    
    val chatErrorText by style {
        color(rgba(255, 0, 0, 0.7))
        property("text-align", "center")
    }
    
    val chatEmptyText by style {
        color(rgba(255, 255, 255, 0.5))
        property("text-align", "center")
    }
    
    val chatMessageItem by style {
        marginBottom(12.px)
        padding(8.px)
        property("border-radius", "4px")
        property("word-break", "break-word")
    }
    
    val chatMessageItemCurrentUser by style {
        backgroundColor(rgba(0, 100, 200, 0.1))
        cursor("pointer")
    }
    
    val chatMessageItemOtherUser by style {
        backgroundColor(rgba(50, 50, 50, 0.2))
    }
    
    val chatMessageHeader by style {
        display(DisplayStyle.Flex)
        property("justify-content", "space-between")
        marginBottom(4.px)
    }
    
    val chatMessageUsername by style {
        fontWeight("bold")
    }
    
    val chatMessageUsernameCurrentUser by style {
        color(rgba(100, 200, 255, 1.0))
    }
    
    val chatMessageUsernameOtherUser by style {
        color(rgba(200, 200, 200, 1.0))
    }
    
    val chatMessageTimestamp by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        fontSize(12.px)
        color(rgba(150, 150, 150, 0.8))
    }
    
    val chatMessageEditedIndicator by style {
        property("margin-right", "4px")
        color(rgba(150, 150, 150, 0.8))
    }
    
    val chatMessageContent by style {
        margin(0.px)
        color(Color.white)
    }
    
    val chatMessageContentDeleted by style {
        opacity(0.5)
        fontStyle("italic")
    }
    
    // Dialog Form Styles
    val formFieldContainer by style {
        marginBottom(16.px)
    }
    
    val formLabel by style {
        display(DisplayStyle.Block)
        marginBottom(4.px)
        fontSize(14.px)
        color(Theme.colors.onBackground)
    }

    val formHelpText by style {
        fontSize(12.px)
        color(Theme.colors.onSurfaceVariant)
        property("margin-top", "4px")
    }
    
    val formErrorMessage by style {
        color(Color.red)
        marginBottom(16.px)
        fontSize(14.px)
        property("text-align", "center")
    }
    
    val formButtonsContainer by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        property("margin-top", "24px")
    }
    
    val primaryButton by style {
        padding(8.px, 16.px)
        backgroundColor(Theme.colors.primary)
        color(Theme.colors.onPrimary)
        property("border", "0")
        property("border-radius", "4px")
        property("cursor", "pointer")
    }
    
    val secondaryButton by style {
        padding(8.px, 16.px)
        backgroundColor(Color.transparent)
        color(Theme.colors.primary)
        property("border", "0")
        property("cursor", "pointer")
        borderRadius(8.px)
        property("border", "1px solid ${Theme.colors.primary}")
    }
    
    val logoutButton by style {
        width(100.percent)
        padding(8.px, 16.px)
        backgroundColor(Theme.colors.secondary)
        color(Theme.colors.onPrimary)
        property("border", "0")
        property("border-radius", "4px")
        property("cursor", "pointer")
        property("margin-top", "16px")
    }
    
    val radioGroupContainer by style {
        display(DisplayStyle.Flex)
        property("gap", "16px")
    }
    
    val radioOptionContainer by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
    }
    
    val radioOptionLabel by style {
        property("margin-left", "4px")
        fontSize(14.px)
        color(Theme.colors.onBackground)
    }
    
    val userInfoContainer by style {
        marginBottom(24.px)
        property("text-align", "center")
    }
    
    val usernameText by style {
        fontSize(18.px)
        property("font-weight", "bold")
        color(Theme.colors.onBackground)
        marginBottom(8.px)
    }
    
    val userStatusText by style {
        fontSize(14.px)
        color(Theme.colors.onSurfaceVariant)
    }
    
    val settingsSectionContainer by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        padding(8.px)
        marginBottom(16.px)
        property("border-radius", "4px")
    }
    
    val settingsSectionLabel by style {
        padding(8.px)
        color(Theme.colors.onSurfaceVariant)
    }
    
    val settingsControlContainer by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        padding(8.px)
    }
    
    // PlayerDialog two-column layout styles
    val playerDialogTwoColumnContainer by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        width(100.percent)
        
        // Use row layout on desktop
        desktop(self) {
            flexDirection(FlexDirection.Row)
            property("gap", "24px")
        }
    }
    
    val playerDialogLeftColumn by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        width(100.percent)
        
        desktop(self) {
            width(40.percent)
        }
    }
    
    val playerDialogRightColumn by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        width(100.percent)
        
        desktop(self) {
            width(60.percent)
        }
    }

    // Bye page container: centers content both vertically and horizontally
    val byePageContainer by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        minHeight(100.vh)
        gap(8.px)
    }
    
    // Reconnecting banner across the top
    val reconnectingBanner by style {
        position(Position.Fixed)
        top(16.px)
        left(16.px)
        borderRadius(12.px)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        padding(12.px, 16.px)
        backgroundColor(Theme.colors.error)
        color(Color.white)
        fontWeight("bold")
        property("z-index", "2000")
        // Blur the background slightly behind the banner
        property("backdrop-filter", "blur(2px)")
        property("-webkit-backdrop-filter", "blur(2px)")

        dark(self) {
            backgroundColor(Theme.colors.errorDark)
        }
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
            color(Theme.colors.onBackground)
        }

        media("(prefers-color-scheme: dark)") {
            "body" style {
                backgroundColor(Theme.colors.backgroundDark)
                color(Theme.colors.onBackgroundDark)
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
