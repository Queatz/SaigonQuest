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
import org.jetbrains.compose.web.css.right
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.width

/**
 * Styles specific to inventory components.
 */
object InventoryStyles : StyleSheet() {
    // Style for inventory box
    val inventoryBox by style {
        width(48.px)
        height(48.px)
        backgroundColor(rgba(255, 255, 255, 0.2))
        border(1.px, LineStyle.Solid, rgba(255, 255, 255, 0.5))
        property("border-radius", "4px")
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        position(Position.Relative)
        // Make box interactive
        property("pointer-events", "auto")
    }
    
    // Style for inventory quantity indicator
    val quantityIndicator by style {
        position(Position.Absolute)
        top(2.px)
        right(2.px)
        backgroundColor(Color.white)
        color(Color.black)
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
    }
    
    // Style for loading indicator
    val loadingIndicator by style {
        width(100.percent)
        height(100.percent)
        backgroundColor(rgba(200, 200, 200, 0.5))
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
        color(rgba(80, 80, 80, 1.0))
    }
}
