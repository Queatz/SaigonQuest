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
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.right
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.width

/**
 * Styles specific to inventory components.
 */
object InventoryStyles : StyleSheet() {
    // Drop zone states for character renderer border/shadow
    val characterDropZoneActive by style {
        property("border", "2px dashed")
        property("border-color", Theme.colors.accentInfoStrong.toString())
        property("box-shadow", "0 0 12px ${Theme.colors.accentInfoBlueGlow}")
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
        fontSize(14.px)
        property("margin-top", "8px")
        property("text-align", "center")
        color(Theme.colors.accentInfoStrong)

        dark(self) {
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
        borderRadius(12.px)

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
        borderRadius(12.px)
        alignItems(AlignItems.Center)
        cursor("grab")
        gap(8.px)

        dark(self) {
            backgroundColor(Theme.colors.white12)
        }
    }

    // Wearable chip/indicator
    val wearableChip by style {
        margin(2.px, 4.px)
        padding(2.px, 6.px)
        backgroundColor(Theme.colors.accentInfoSoft)
        color(Theme.colors.accentInfoStrong)
        fontSize(10.px)
        borderRadius(4.px)
    }

    val wearableChipBoy by style {
        backgroundColor(Theme.colors.accentBoySoft)
        color(Theme.colors.accentBoyStrong)
    }

    val wearableChipGirl by style {
        backgroundColor(Theme.colors.accentGirlSoft)
        color(Theme.colors.accentGirlStrong)
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
        position(Position.Relative)
    }

    // Section container and title
    val sectionContainer by style {
        property("margin-bottom", "20px")
    }

    // Style for inventory box
    val inventoryBox by style {
        width(48.px)
        height(48.px)
        backgroundColor(Theme.colors.surfaceVariant)
        border(1.px, LineStyle.Solid, Theme.colors.outline)
        borderRadius(8.px)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        position(Position.Relative)
        boxSizing("border-box")
        // Make box interactive
        property("pointer-events", "auto")

        dark(self) {
            backgroundColor(Theme.colors.surfaceVariantDark)
            border(1.px, LineStyle.Solid, Theme.colors.outlineDark)
        }
    }

    val inventoryBoxWornBoy by style {
        border(3.px, LineStyle.Solid, Theme.colors.accentBoyStrong)
    }

    val inventoryBoxWornGirl by style {
        border(3.px, LineStyle.Solid, Theme.colors.accentGirlStrong)
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
