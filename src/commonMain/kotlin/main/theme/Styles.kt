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
import org.jetbrains.compose.web.css.maxWidth
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
import org.jetbrains.compose.web.css.flexWrap
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.FlexWrap
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.border
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.bottom
import org.jetbrains.compose.web.css.boxSizing
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.flexGrow
import org.jetbrains.compose.web.css.flexShrink
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.opacity
import org.jetbrains.compose.web.css.fontStyle
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.maxHeight
import org.jetbrains.compose.web.css.outline
import org.jetbrains.compose.web.css.overflow
import org.jetbrains.compose.web.css.overflowY
import org.jetbrains.compose.web.css.paddingTop
import org.jetbrains.compose.web.css.textAlign
import org.jetbrains.compose.web.css.vw
import org.jetbrains.compose.web.css.whiteSpace
import web.cssom.BorderStyle

object Styles : StyleSheet() {
    val dialogueButtons by style {
        display(DisplayStyle.Flex)
        flexWrap(FlexWrap.Wrap)
        gap(8.px)
        justifyContent(JustifyContent.Center)
        width(100.percent)

        mobile(self) {
            flexDirection(FlexDirection.Column)
        }
    }

    val characterRendererContainer by style {
        display(DisplayStyle.Flex)
        property("flex-direction", "column")
        alignItems(AlignItems.Center)
        marginBottom(20.px)
        padding(16.px)
        backgroundColor(Theme.colors.white20)
        property("border-radius", "8px")

        dark(self) {
            backgroundColor(Theme.colors.black20)
        }
    }

    val characterRenderer by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        padding(8.px)
        property("border-radius", "8px")
        property("transition", "all 0.2s ease")
        cursor("pointer")

        dark(self) {
            backgroundColor(Theme.colors.black20)
        }
    }

    val playerAttrStatusHeader by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        fontSize(14.px)
        fontWeight("bold")
        whiteSpace("nowrap")
    }

    // Asset editor: SpriteSheet-specific styles
    val assetEditorSection by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        gap(8.px)
        padding(8.px)
    }

    val spriteSheetPreview by style {
        width(256.px)
        height(256.px)
        property("object-fit", "contain")
        property("image-rendering", "pixelated")
        marginBottom(8.px)
    }

    val weightsPreviewGrid by style {
        display(DisplayStyle.Grid)
        property("grid-template-columns", "repeat(5, 20px)")
        property("grid-template-rows", "repeat(5, 20px)")
        gap(4.px)
        marginBottom(8.px)
    }

    val weightsPreviewCell by style {
        width(20.px)
        height(20.px)
        border(1.px, LineStyle.Solid, Theme.colors.white12)
        borderRadius(2.px)
    }
    val emoteButton by style {
        width(48.px)
        height(48.px)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        fontSize(32.px)
        cursor("pointer")
        borderRadius(8.px)
        property("transition", "background-color 0.2s")
    }

    // moved to OverlayStyles.kt

    // Canvas styles
    val canvasBase by style {
        width(100.percent)
        height(100.vh)
        display(DisplayStyle.Block)
        outline("none")
        property("touch-action", "none")
        property("user-select", "none")
    }

    val canvasLowGraphics by style {
        property("image-rendering", "pixelated")
        property("image-rendering", "-moz-crisp-edges")
        property("image-rendering", "crisp-edges")
    }

    // moved to OverlayStyles.kt

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

    val topRightContainer by style {
        position(Position.Fixed) // Changed from Absolute to Fixed for consistent positioning
        top(20.px)
        // Provide a minimal bottom inset so there is always some spacing if inline override isn't applied
        bottom(20.px)
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("gap", "10px")
        // Allow scrolling when constrained between top and bottom to avoid overlap with bottom buttons
        property("overflow-y", "auto")
        // Add padding at the bottom to create more space between top and bottom buttons
        property("padding-bottom", "20px")
        // Style the scrollbar to be subtle and match the UI
        property("scrollbar-width", "thin")
        property("scrollbar-color", "${Theme.colors.white30} transparent")
        // Webkit scrollbar styling
        property("&::-webkit-scrollbar", "{ width: 6px; }")
        property("&::-webkit-scrollbar-track", "{ background: transparent; }")
        property("&::-webkit-scrollbar-thumb", "{ background-color: ${Theme.colors.white30}; border-radius: 3px; }")
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

    val bottomText by style {
        position(Position.Absolute)
        bottom(4.px)
        left(4.px)
        fontSize(8.px)
        color(Theme.colors.onBackground)
        property("text-shadow", "0px 0px 2px ${Theme.colors.black70}")
        // Make text elements interactive
        property("pointer-events", "auto")

        dark(self) { color(Theme.colors.onBackgroundDark) }
    }

    // CircleButton styles
    val circleButton by style {
        width(48.px)
        height(48.px)
        property("border-radius", "50%")
        backgroundColor(Theme.colors.controlBg)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        position(Position.Relative)
        cursor("pointer")
        // Prevent button from shrinking in flex containers
        property("flex-shrink", "0")
        // Make button interactive
        property("pointer-events", "auto")
        color(Theme.colors.onBackground)

        dark(self) {
            backgroundColor(Theme.colors.controlBgDark)
            color(Theme.colors.onBackgroundDark)
        }
    }

    val badgeBase by style {
        position(Position.Absolute)
        top(0.px)
        right(0.px)
        backgroundColor(Theme.colors.error)
        color(Theme.colors.onBackgroundDark)
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
        backgroundColor(Theme.colors.error)
    }

    // Icon renderer styles
    val iconRenderer by style {
        width(32.px)
        height(32.px)
        display(DisplayStyle.Block)
        property("image-rendering", "pixelated")
        property("image-rendering", "-moz-crisp-edges")
        property("image-rendering", "crisp-edges")
    }

    // moved to PanelStyles.kt

    // Responsive panel styles
    val dialogContainer by style {
        width(90.percent)
        property("max-width", "400px")
        height(80.vh)
        backgroundColor(Theme.colors.surface)
        color(Theme.colors.onSurface)
        property("border-radius", "8px")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("backdrop-filter", "blur(2px)")
        property("-webkit-backdrop-filter", "blur(2px)")

        dark(self) {
            backgroundColor(Theme.colors.surfaceDark)
            color(Theme.colors.onSurfaceDark)
        }
    }

    val panelContent by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("flex-grow", "1")
        property("overflow", "hidden")
    }

    // Base dialog styles (for the new BaseDialog component)
    val baseDialogContainer by style {
        backgroundColor(Theme.colors.surface)
        borderRadius(16.px)
        property("box-shadow", "0 4px 16px ${Theme.colors.shadowLight}")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        overflowY("auto")

        dark(self) {
            backgroundColor(Theme.colors.surfaceDark)
        }
    }

    val baseDialogHeader by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        padding(16.px)
        property("border-bottom", "1px solid ${Theme.colors.panelBorderSubtle}")

        dark(self) { property("border-bottom", "1px solid ${Theme.colors.panelBorderSubtleDark}") }
    }

    val baseDialogTitle by style {
        margin(0.px)
    }

    val baseDialogCloseButton by style {
        backgroundColor(Color.transparent)
        property("border", "0")
        cursor("pointer")
        fontSize(20.px)
    }

    val baseDialogContent by style {
        padding(24.px)
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        overflowY("auto")
        property("flex-grow", "1")
    }

    val baseDialogFooter by style {
        padding(16.px)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.FlexEnd)
        property("gap", "8px")
        property("border-top", "1px solid ${Theme.colors.panelBorderSubtle}")
        dark(self) { property("border-top", "1px solid ${Theme.colors.panelBorderSubtleDark}") }
    }

    val baseDialogButtonPrimary by style {
        padding(8.px, 16.px)
        backgroundColor(Theme.colors.primary)
        color(Theme.colors.onPrimary)
        property("border", "0")
        property("border-radius", "16px")
        property("cursor", "pointer")

        self + disabled style {
            opacity(.25f)
        }
    }

    val baseDialogButtonSecondary by style {
        padding(8.px, 16.px)
        backgroundColor(Color.transparent)
        color(Theme.colors.primary)
        property("border", "1px solid ${Theme.colors.primary}")
        property("border-radius", "16px")
        property("cursor", "pointer")

        self + disabled style {
            opacity(.25f)
        }
    }

    // Common style for input elements with box-sizing: border-box
    val inputField by style {
        width(100.percent)
        padding(8.px)
        backgroundColor(Theme.colors.surfaceVariant)
        color(Theme.colors.onSurface)
        property("border", "1px solid ${Theme.colors.outline}")
        borderRadius(8.px)
        property("outline", "none")
        boxSizing("border-box")

        dark(self) {
            backgroundColor(Theme.colors.surfaceVariantDark)
            color(Theme.colors.onSurfaceDark)
            property("border", "1px solid ${Theme.colors.outlineDark}")
        }
    }

    val objectEditorDialogLogicPreview by style {
        width(100.percent)
        height(200.px)
        backgroundColor(Theme.colors.surfaceVariant)
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        color(Theme.colors.onSurface)
        property("border", "1px solid ${Theme.colors.outline}")
        borderRadius(8.px)
        property("margin-top", "16px")
        cursor("pointer")
        property("transition", "background-color 0.2s, border-color 0.2s")

        dark(self) {
            backgroundColor(Theme.colors.surfaceVariantDark)
            color(Theme.colors.onSurfaceDark)
            property("border", "1px solid ${Theme.colors.outlineDark}")
        }
    }

    val objectEditorDialogLogicTitle by style {
        margin(0.px)
        marginBottom(8.px)
        fontSize(16.px)
        fontWeight("bold")
    }

    val objectEditorDialogLogicDescription by style {
        fontSize(14.px)
        color(Theme.colors.textMuted)
        property("text-align", "center")
        padding(0.px, 16.px)

        dark(self) { color(Theme.colors.textMutedDark) }
    }

    val objectEditorDialogErrorMessage by style { color(Theme.colors.error); margin(0.px) }

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
        property("transition", "background-color 0.3s, border-color 0.3s")
    }

    // Track state: ON
    val toggleSwitchTrackOn by style {
        backgroundColor(Theme.colors.primary)
        property("border", "1px solid ${Theme.colors.primary}")

        dark(self) {
            backgroundColor(Theme.colors.primaryDark)
            property("border", "1px solid ${Theme.colors.primaryDark}")
        }
    }

    // Track state: OFF
    val toggleSwitchTrackOff by style {
        backgroundColor(Theme.colors.surfaceVariant)
        property("border", "1px solid ${Theme.colors.outline}")

        dark(self) {
            backgroundColor(Theme.colors.surfaceVariantDark)
            property("border", "1px solid ${Theme.colors.outlineDark}")
        }
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

    // Knob position: OFF (left)
    val toggleSwitchKnobOff by style {
        left(3.px)
    }

    // Knob position: ON (right)
    val toggleSwitchKnobOn by style {
        left(20.px)
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

        dark(self) {
            backgroundColor(Theme.colors.surfaceVariantDark)
        }
    }

    val languageSwitcherOption by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.Center)
        padding(8.px, 16.px)  // Increased padding for better appearance
        cursor("pointer")
        flexGrow(1)
        property("transition", "background-color 0.3s, color 0.3s")
    }

    val languageSwitcherOptionActive by style {
        backgroundColor(Theme.colors.primary)
        color(Theme.colors.onPrimary)
    }

    val languageSwitcherOptionInactive by style {
        backgroundColor(Color.transparent)
        color(Theme.colors.onSurfaceVariant)

        dark(self) {
            color(Theme.colors.onSurfaceVariantDark)
        }
    }

    // Chat Messages styles
    // Section header used for grouping (e.g., Pinned messages)
    val chatSectionHeader by style { color(Theme.colors.textMuted); fontWeight("bold"); dark(self) { color(Theme.colors.textMutedDark) } }

    // Generic chat message text (used in simple list items like pins)
    val chatMessageText by style { margin(0.px); color(Theme.colors.onBackground); dark(self) { color(Theme.colors.onBackgroundDark) } }

    // Sticky container for pinned messages at the top of the list
    val chatPinnedSection by style {
        position(Position.Sticky)
        top(0.px)
        // Keep above scrolling content
        property("z-index", "2")
        // Subtle separation from the rest of the messages
        property("border-bottom", "1px solid ${Theme.colors.panelBorderSubtle}")
        // Optional: soften with a slight blur
        padding(16.px, 0.px)
        maxHeight(25.percent)
        overflowY("auto")
    }

    // Clickable pin/unpin action style
    val chatPinButton by style {
        display(DisplayStyle.Flex)
        padding(2.px, 8.px)

        cursor("pointer")

        color(Theme.colors.textSecondary)
        border(1.px, LineStyle.Solid, Theme.colors.outline)

        borderRadius(6.px)

        dark(self) {
            color(Theme.colors.textSecondaryDark)
            border(1.px, LineStyle.Solid, Theme.colors.outlineDark)
        }
    }

    // Outer wrapper for chat area; holds pinned header and scroll area
    val chatMessagesWrapper by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("flex-grow", "1")
        // Only side/bottom padding so no content scrolls under top padding
        property("padding", "0 16px 16px")
        // Prevent outer from scrolling; only inner container scrolls
        property("overflow", "hidden")
    }

    // Scroll container for messages only
    val chatMessagesContainer by style {
        property("overflow-y", "auto")
        property("flex-grow", "1")
        display(DisplayStyle.Flex)
        property("flex-direction", "column")
        paddingTop(8.px)
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
        property("border", "4px solid ${Theme.colors.white10}")
        property("border-top", "4px solid ${Theme.colors.white80}")
        property("border-radius", "50%")
        property("animation", "spin 1s linear infinite")
        property("@keyframes spin", "{0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); }}")
    }

    val chatLoadingText by style {
        color(Theme.colors.textMuted); property("text-align", "center"); margin(0.px); dark(
        self
    ) { color(Theme.colors.textMutedDark) }
    }

    val chatErrorText by style { color(Theme.colors.error); property("text-align", "center") }

    val chatEmptyText by style {
        color(Theme.colors.textMuted); property("text-align", "center"); dark(self) {
        color(
            Theme.colors.textMutedDark
        )
    }
    }

    val chatMessageItem by style {
        marginBottom(12.px)
        padding(8.px)
        cursor("pointer")
        borderRadius(8.px)
        property("word-break", "break-word")
    }

    val chatMessageItemCurrentUser by style { backgroundColor(Theme.colors.accentInfoSoft) }

    val chatMessageItemOtherUser by style {
        backgroundColor(Theme.colors.surfaceVariant); dark(self) {
        backgroundColor(
            Theme.colors.surfaceVariantDark
        )
    }
    }

    // Stylistic variations for non-user messages
    val chatMessageItemObject by style {
        // Subtle left accent to denote object-originated message
        property("border-left", "4px solid ${Theme.colors.outline}")
        dark(self) { property("border-left", "4px solid ${Theme.colors.outlineDark}") }
    }

    val chatMessageItemSystem by style {
        // Dashed border to denote system message
        border(1.px, LineStyle.Dashed, Theme.colors.outline)
        dark(self) { border(1.px, LineStyle.Dashed, Theme.colors.outlineDark) }
    }

    val chatMessageHeader by style {
        display(DisplayStyle.Flex)
        property("justify-content", "space-between")
        marginBottom(4.px)
    }

    val chatMessageUsername by style {
        fontWeight("bold")
    }

    val chatMessageUsernameCurrentUser by style { color(Theme.colors.accentInfo) }

    val chatMessageUsernameOtherUser by style { color(Theme.colors.textSecondary); dark(self) { color(Theme.colors.textSecondaryDark) } }

    val chatMessageTimestamp by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        fontSize(14.px)
        textAlign("right")
    }

    val endAmount by style {
        fontWeight("bold")
        fontSize(18.px)
    }

    val chatMessageEditedIndicator by style {
        property(
            "margin-right", "4px"
        ); color(Theme.colors.textMuted); dark(self) { color(Theme.colors.textMutedDark) }
    }

    val chatMessageContent by style {
        margin(0.px)
        color(Theme.colors.onBackground)
        dark(self) { color(Theme.colors.onBackgroundDark) }
    }

    val chatMessageContentDeleted by style {
        opacity(0.5)
        fontStyle("italic")
    }

    // Image attachments inside chat messages
    val chatImageAttachment by style {
        display(DisplayStyle.Block)
        maxWidth(360.px)
        width(100.percent)
        property("height", "auto")
        borderRadius(6.px)
        marginTop(6.px)
        border(1.px, LineStyle.Solid, Theme.colors.white08)
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
        dark(self) { color(Theme.colors.onBackgroundDark) }
    }

    val container by style {
        backgroundColor(Theme.colors.surface)
        color(Theme.colors.onSurface)
        padding(16.px)
        borderRadius(8.px)
        border(1.px, LineStyle.Solid, Theme.colors.outline)

        dark(self) {
            backgroundColor(Theme.colors.surfaceDark)
            color(Theme.colors.onSurfaceDark)
            border(1.px, LineStyle.Solid, Theme.colors.outlineDark)
        }
    }

    val formHelpText by style {
        fontSize(12.px)
        color(Theme.colors.onSurfaceVariant)
        dark(self) { color(Theme.colors.onBackgroundDark) }
        property("margin-top", "4px")
    }

    val formErrorMessage by style {
        color(Theme.colors.error)
        marginBottom(16.px)
        fontSize(14.px)
        property("text-align", "center")
    }

    val formSuccessMessage by style {
        color(Theme.colors.success)
        marginBottom(16.px)
        fontSize(14.px)
        property("text-align", "center")
        backgroundColor(Theme.colors.successSoftBg)
        property("border", "1px solid ${Theme.colors.successSoftBorder}")
        padding(8.px, 12.px)
        property("border-radius", "6px")
    }

    val formButtonsContainer by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.FlexEnd)
    }

    // Balance sections styling
    val balanceSectionTitle by style {
        marginTop(16.px)
        marginBottom(8.px)
        color(Theme.colors.onBackground)
        dark(self) { color(Theme.colors.onBackgroundDark) }
        fontSize(16.px)
        property("font-weight", "bold")
    }

    // Balance summary box
    val balanceSummaryBox by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        padding(16.px)
        property("border-radius", "8px")
        backgroundColor(Theme.colors.white50)
        property("border", "1px solid ${Theme.colors.white15}")
        marginBottom(16.px)
        property("text-align", "center")

        dark(self) {
            backgroundColor(
                Theme.colors.white06
            )
        }
    }

    val clickable by style {
        cursor("pointer")
    }

    val balanceAmountText by style {
        fontSize(28.px)
        fontWeight("bold")
        color(Theme.colors.onBackground)
        dark(self) { color(Theme.colors.onBackgroundDark) }
    }

    val balanceSubtext by style {
        marginTop(4.px)
        fontSize(13.px)
        color(Theme.colors.onSurfaceVariant)
        dark(self) { color(Theme.colors.onBackgroundDark) }
    }

    // Assets list styles
    val assetsGridHeader by style {
        display(DisplayStyle.Grid)
        property("grid-template-columns", "1fr 2fr")
        property("gap", "8px")
        fontSize(12.px)
        marginBottom(8.px)
    }

    val assetsGridRow by style {
        display(DisplayStyle.Grid)
        property("grid-template-columns", "1fr 2fr")
        property("gap", "8px")
        alignItems(AlignItems.Center)
        padding(8.px)
        property("border", "1px solid ${Theme.colors.white08}")
        property("border-radius", "6px")
        marginBottom(6.px)
    }

    // Admin Items dialog styles
    val adminItemsContent by style {
        padding(16.px)
        height(100.percent)
        overflowY("auto")
    }

    val adminItemsErrorText by style {
        color(Theme.colors.error)
    }

    val adminItemsGridHeader by style {
        display(DisplayStyle.Grid)
        property("grid-template-columns", "48px 2fr 1fr 1fr 1fr auto")
        property("gap", "8px")
        fontSize(12.px)
        marginBottom(8.px)
    }

    val adminItemsGridRow by style {
        display(DisplayStyle.Grid)
        property("grid-template-columns", "48px 2fr 1fr 1fr 1fr auto")
        property("gap", "8px")
        alignItems(AlignItems.Center)
        padding(8.px)
        property("border", "1px solid ${Theme.colors.white08}")
        property("border-radius", "6px")
        marginBottom(6.px)
    }

    val adminItemsRowActions by style {
        display(DisplayStyle.Flex)
        property("gap", "8px")
        justifyContent(JustifyContent.FlexEnd)
    }

    // Form grouping
    val formGroup by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("gap", "12px")
        marginBottom(16.px)
    }

    // Thumbnails row for attachments
    val imageThumbRow by style {
        display(DisplayStyle.Flex)
        flexWrap(FlexWrap.Wrap)
        gap(8.px)
    }

    // Small square image preview (used for proof image)
    val imageThumb by style {
        width(96.px)
        height(96.px)
        property("object-fit", "cover")
        borderRadius(8.px)
        property("border", "1px solid ${Theme.colors.white20}")
        cursor("pointer")
    }

    // Smaller square image preview (used for proof image)
    val imageThumbSmall by style {
        width(48.px)
        height(48.px)
        property("object-fit", "cover")
        borderRadius(4.px)
        property("border", "1px solid ${Theme.colors.white20}")
        cursor("pointer")
    }

    // Event row styles
    val eventRow by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        padding(10.px, 12.px)
        property("border-radius", "6px")
        backgroundColor(Theme.colors.white04)
        property("border", "1px solid ${Theme.colors.white08}")
        marginBottom(8.px)
        property("gap", "12px")
        alignItems(alignItems = AlignItems.Center)
    }

    val eventTypeBadge by style {
        fontSize(12.px)
        fontWeight("bold")
        padding(4.px, 8.px)
        property("border-radius", "100px")
        flexShrink(0)
    }

    val eventTypeDeposit by style {
        backgroundColor(Theme.colors.successSoftBg)
        color(Theme.colors.success)
        property("border", "1px solid ${Theme.colors.successSoftBorder}")
    }

    val eventTypeWithdraw by style {
        backgroundColor(Theme.colors.errorSoftBg)
        color(Theme.colors.error)
        property("border", "1px solid ${Theme.colors.errorSoftBorder}")
    }

    val eventAmounts by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        property("gap", "10px")
    }

    val amountPositive by style { color(Theme.colors.success) }
    val amountNegative by style { color(Theme.colors.error) }

    val eventNotes by style {
        color(Theme.colors.onSurfaceVariant)
    }

    // Column container for timestamp and end balance on the right side of event row
    val eventMetaColumn by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        flexGrow(1)
    }

    // Column container for timestamp and end balance on the right side of event row
    val eventMetaColumnEnd by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        alignItems(AlignItems.FlexEnd)
        flexGrow(1)
    }

    // Button variants
    val baseDialogButtonDangerPrimary by style {
        padding(8.px, 16.px)
        backgroundColor(Theme.colors.error)
        color(Theme.colors.onPrimary)
        property("border", "0")
        borderRadius(16.px)
        property("cursor", "pointer")
    }

    // Request row styles (pending requests)
    val requestRow by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        padding(12.px)
        property("border-radius", "8px")
        backgroundColor(Theme.colors.white04)
        property("border", "1px solid ${Theme.colors.white08}")
        marginBottom(10.px)
        property("gap", "8px")
    }

    val requestHeader by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        property("gap", "12px")
    }

    val requestMeta by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        property("gap", "12px")
        flexGrow(1)
    }

    val statusChip by style {
        fontSize(12.px)
        fontWeight("bold")
        padding(4.px, 8.px)
        property("border-radius", "999px")
    }

    val statusReceived by style {
        backgroundColor(Theme.colors.accentInfoSoft); color(Theme.colors.accentInfo); property(
        "border", "1px solid ${Theme.colors.accentInfoBorder}"
    )
    }

    val statusApproved by style {
        backgroundColor(Theme.colors.successSoftBg); color(Theme.colors.success); property(
        "border", "1px solid ${Theme.colors.successSoftBorder}"
    )
    }

    val statusDenied by style {
        backgroundColor(Theme.colors.errorSoftBg); color(Theme.colors.error); property(
        "border", "1px solid ${Theme.colors.errorSoftBorder}"
    )
    }

    val requestActions by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.FlexEnd)
        property("gap", "8px")
        marginTop(8.px)
    }

    val requestUserId by style {
        color(Theme.colors.onSurfaceVariant)
        fontSize(12.px)
    }

    val primaryButton by style {
        padding(8.px, 16.px)
        backgroundColor(Theme.colors.primary)
        color(Theme.colors.onPrimary)
        property("border", "0")
        borderRadius(16.px)
        property("cursor", "pointer")

        self + disabled style {
            opacity(0.5)
            cursor("not-allowed")
        }
    }

    val secondaryButton by style {
        padding(8.px, 16.px)
        backgroundColor(Color.transparent)
        color(Theme.colors.primary)
        property("border", "0")
        property("cursor", "pointer")
        borderRadius(16.px)
        property("border", "1px solid ${Theme.colors.primary}")

        self + disabled style {
            opacity(0.5)
            cursor("not-allowed")
        }
    }

    val logoutButton by style {
        width(100.percent)
        padding(8.px, 16.px)
        backgroundColor(Theme.colors.secondary)
        color(Theme.colors.onPrimary)
        property("border", "0")
        borderRadius(16.px)
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
        dark(self) { color(Theme.colors.onBackgroundDark) }
    }

    val userInfoContainer by style {
        marginBottom(24.px)
        property("text-align", "center")
    }

    val usernameText by style {
        fontSize(18.px)
        property("font-weight", "bold")
        color(Theme.colors.onBackground)
        dark(self) { color(Theme.colors.onBackgroundDark) }
        marginBottom(8.px)
    }

    val userStatusText by style {
        fontSize(14.px)
        color(Theme.colors.onSurfaceVariant)
        dark(self) { color(Theme.colors.onBackgroundDark) }
    }

    val settingsSectionContainer by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        padding(8.px)
        marginBottom(16.px)
        borderRadius(4.px)
    }

    val settingsSectionLabel by style {
        padding(8.px)
        color(Theme.colors.onSurfaceVariant)
        dark(self) { color(Theme.colors.onBackgroundDark) }
    }

    val settingsControlContainer by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        padding(8.px)
    }

    // Graphics toggle styles
    val graphicsToggleContainer by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        padding(8.px)
        marginBottom(16.px)
        backgroundColor(Theme.colors.surfaceVariant)
        borderRadius(16.px)

        dark(self) {
            backgroundColor(Theme.colors.surfaceVariantDark)
        }
    }

    val graphicsToggleLabel by style {
        padding(8.px)
        color(Theme.colors.onSurfaceVariant)

        dark(self) {
            color(Theme.colors.onSurfaceVariantDark)
        }
    }

    val graphicsToggleControl by style {
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

    val playerDialogColumn by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
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
        color(Theme.colors.onPrimary)
        fontWeight("bold")
        property("z-index", "2000")
        // Blur the background slightly behind the banner
        property("backdrop-filter", "blur(2px)")
        property("-webkit-backdrop-filter", "blur(2px)")

        dark(self) {
            backgroundColor(Theme.colors.errorDark)
        }
    }

    // Transient toast message centered at the bottom of the viewport
    val toastMessage by style {
        position(Position.Fixed)
        top(84.px)
        left(50.percent)
        property("transform", "translateX(-50%) translateY(-10px)")
        padding(8.px, 12.px)
        color(Theme.colors.white95)
        fontSize(14.px)
        borderRadius(8.px)
        property("pointer-events", "none")
        // Ensure above general UI, below dialogs if needed
        property("z-index", "1500")
    }

    val toastSuccess by style {
        backgroundColor(Theme.colors.toastSuccessBg)
    }

    val toastError by style {
        backgroundColor(Theme.colors.toastErrorBg)
    }

    init {
        "body, html" style {
            margin(0.cssRem)
            padding(0.cssRem)
            property("font-family", "'Ysabeau Infant', sans-serif")
            property("-webkit-font-smoothing", "antialiased")
            property("-moz-osx-font-smoothing", "grayscale")
            minHeight(100.vh)
            width(100.vw)
            height(100.vh)
            overflow("hidden")
            backgroundColor(Theme.colors.background)
            color(Theme.colors.onBackground)
        }

        media("(prefers-color-scheme: dark)") {
            "body, html" style {
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

        "textarea" {
            property("resize", "vertical")
        }

        "button" {
            property("color", "inherit")
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
    media(mediaMaxWidth(960.px)) {
        self style {
            block()
        }
    }
}

fun <TBuilder> GenericStyleSheetBuilder<TBuilder>.desktop(self: CSSSelector, block: TBuilder.() -> Unit) {
    media(mediaMinWidth(961.px)) {
        self style {
            block()
        }
    }
}
