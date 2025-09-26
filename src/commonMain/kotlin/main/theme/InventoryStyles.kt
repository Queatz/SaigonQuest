package main.theme

import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.StyleSheet
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.border
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.boxSizing
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.fontWeight
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.letterSpacing
import org.jetbrains.compose.web.css.minHeight
import org.jetbrains.compose.web.css.minWidth
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.opacity
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.flexGrow
import org.jetbrains.compose.web.css.right
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.width

/**
 * Styles specific to inventory components.
 */
object InventoryStyles : StyleSheet() {
    // Drop zone states for character renderer border/shadow
    val characterDropZoneActive by style {
        property("border", "3px dashed")
        property("border-color", Theme.colors.accentInfoStrong.toString())
        property("box-shadow", "0 0 10px ${Theme.colors.accentInfoBlueGlow}")
    }

    val characterDropZoneInactive by style {
        property("border", "2px dashed")
        property("border-color", Theme.colors.onSurfaceVariant.toString())

        dark(self) {
            property("border-color", Theme.colors.onSurfaceVariantDark.toString())
        }
    }

    // Hint bubble over character when dragging wearable
    val characterDropHint by style {
        position(Position.Absolute)
        property("bottom", "8px")
        backgroundColor(Theme.colors.overlayScrim)
        color(Theme.colors.accentInfoStrong)
        padding(4.px, 8.px)
        borderRadius(4.px)
        fontSize(12.px)
        property("text-align", "center")

        dark(self) {
            backgroundColor(Theme.colors.overlayScrimDark)
            color(Theme.colors.accentInfoBlueStrong)
        }
    }

    // Instructional helper text
    val instructionText by style {
        color(Theme.colors.textSecondary)
        fontSize(14.px)
        property("margin-top", "8px")
        property("text-align", "center")

        dark(self) {
            color(Theme.colors.textSecondaryDark)
        }
    }

    // Empty worn message panel
    val emptyWornMessage by style {
        color(Theme.colors.onSurface)
        fontSize(14.px)
        property("text-align", "center")
        padding(12.px)
        backgroundColor(Theme.colors.white06)
        borderRadius(4.px)

        dark(self) {
            color(Theme.colors.onSurfaceDark)
            backgroundColor(Theme.colors.white12)
        }
    }

    // Feedback (toast) styles shown at top of dialog content
    val feedbackBase by style {
        position(Position.Absolute)
        top(0.px)
        left(50.percent)
        property("transform", "translateX(-50%)")
        padding(8.px, 12.px)
        fontSize(14.px)
        borderRadius(4.px)
        property("z-index", "10")
        opacity(0.9)
    }

    val feedbackSuccess by style {
        backgroundColor(Theme.colors.toastSuccessBg)
    }

    val feedbackError by style {
        backgroundColor(Theme.colors.toastErrorBg)
    }

    // Inline error text color
    val errorText by style {
        color(Theme.colors.error)
        property("text-align", "center")

        dark(self) {
            color(Theme.colors.errorDark)
        }
    }

    // Worn item card (clickable)
    val wornItemCard by style {
        display(DisplayStyle.Flex)
        property("flex-direction", "column")
        alignItems(AlignItems.Center)
        padding(8.px)
        backgroundColor(Theme.colors.white08)
        borderRadius(4.px)
        cursor("pointer")

        dark(self) {
            backgroundColor(Theme.colors.white12)
        }
    }

    // Inventory list row
    val inventoryRow by style {
        display(DisplayStyle.Flex)
        padding(12.px)
        property("margin-bottom", "8px")
        backgroundColor(Theme.colors.white06)
        borderRadius(4.px)
        alignItems(AlignItems.Center)
        cursor("grab")

        dark(self) {
            backgroundColor(Theme.colors.white12)
        }
    }

    // Wearable chip/indicator
    val wearableChip by style {
        margin(8.px)
        padding(2.px, 6.px)
        backgroundColor(Theme.colors.accentInfoSoft)
        color(Theme.colors.accentInfoStrong)
        fontSize(10.px)
        borderRadius(4.px)
    }

    // Item title
    val itemTitle by style {
        fontSize(16.px)
        fontWeight("bold")
    }

    // Description text under title
    val descriptionText by style {
        color(Theme.colors.textSecondary)
        fontSize(14.px)
        property("margin-top", "4px")

        dark(self) {
            color(Theme.colors.textSecondaryDark)
        }
    }

    // Small remove text under worn item card
    val removeTextSmall by style {
        color(Theme.colors.error)
        fontSize(10.px)
        property("margin-top", "2px")

        dark(self) {
            color(Theme.colors.errorDark)
        }
    }

    // Dialog content container
    val dialogContent by style {
        padding(16.px)
        height(100.percent)
        property("overflow-y", "auto")
        position(Position.Relative)
    }

    // Centered text helper
    val centerText by style {
        property("text-align", "center")
    }

    // Section container and title
    val sectionContainer by style {
        property("margin-bottom", "20px")
    }

    val sectionTitle by style {
        property("margin-bottom", "12px")
        fontSize(18.px)
    }

    // Worn items list container
    val wornItemsList by style {
        display(DisplayStyle.Flex)
        property("flex-direction", "row")
        property("flex-wrap", "wrap")
        property("gap", "8px")
    }

    // Worn item name text
    val wornItemName by style {
        fontSize(12.px)
        property("margin-top", "4px")
        property("text-align", "center")
    }

    // Item details area in row
    val itemDetails by style {
        flexGrow(1)
        property("margin-left", "12px")
    }

    // Header row for title + chip
    val itemHeaderRow by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
    }

    // Action buttons row
    val actionButtonsRow by style {
        display(DisplayStyle.Flex)
        property("gap", "8px")
    }
    // Style for inventory box
    val inventoryBox by style {
        width(48.px)
        height(48.px)
        backgroundColor(Theme.colors.surfaceVariant)
        border(1.px, LineStyle.Solid, Theme.colors.outline)
        borderRadius(4.px)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        position(Position.Relative)
        // Make box interactive
        property("pointer-events", "auto")

        dark(self) {
            backgroundColor(Theme.colors.surfaceVariantDark)
            border(1.px, LineStyle.Solid, Theme.colors.outlineDark)
        }
    }
    
    // Style for inventory quantity indicator
    val quantityIndicator by style {
        position(Position.Absolute)
        top(2.px)
        right(2.px)
        backgroundColor(Theme.colors.controlBg)
        color(Theme.colors.onBackground)
        fontSize(10.px)
        fontWeight("bold")
        borderRadius(8.px)
        padding(2.px, 4.px)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        minWidth(14.px)
        minHeight(14.px)
        boxSizing("border-box")
        property("z-index", "1")

        dark(self) {
            backgroundColor(Theme.colors.controlBgDark)
            color(Theme.colors.onBackgroundDark)
        }
    }
    
    // Style for loading indicator
    val loadingIndicator by style {
        width(100.percent)
        height(100.percent)
        backgroundColor(Theme.colors.loadingMask)
        borderRadius(4.px)
        position(Position.Relative)
    }
    
    // Style for loading dots
    val loadingDots by style {
        position(Position.Absolute)
        top(50.percent)
        left(50.percent)
        property("transform", "translate(-50%, -50%)")
        fontSize(16.px)
        letterSpacing(2.px)
        color(Theme.colors.loadingDots)
    }
}
