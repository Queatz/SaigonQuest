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
import org.jetbrains.compose.web.css.bottom
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.opacity
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.right
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.width

object PanelStyles : StyleSheet() {
    // Panel styles
    val sidePanel by style {
        position(Position.Absolute)
        top(1.cssRem)
        bottom(1.cssRem)
        right(1.cssRem)
        width(320.px)
        borderRadius(1.cssRem)
        backgroundColor(Theme.colors.panelGlass)
        color(Theme.colors.onBackground)
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("pointer-events", "auto")
        property("backdrop-filter", "blur(8px)")
        property("-webkit-backdrop-filter", "blur(8px)")

        dark(self) {
            backgroundColor(Theme.colors.panelGlassDark)
            color(Theme.colors.onBackgroundDark)
        }
    }

    val panelHeader by style {
        padding(16.px)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        property("border-bottom", "1px solid ${Theme.colors.panelBorderSubtle}")

        dark(self) {
            property("border-bottom", "1px solid ${Theme.colors.panelBorderSubtleDark}")
        }
    }

    val panelTitle by style {
        margin(0.px)
    }

    val closeButton by style {
        backgroundColor(Color.transparent)
        property("border", "0")
        color(Theme.colors.onBackgroundDark)
        property("cursor", "pointer")
        fontSize(20.px)

        dark(self) { color(Theme.colors.onBackground) }
    }

    // Header right container and hint
    val panelHeaderRight by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        gap(8.px)
    }

    val panelHeaderHint by style {
        opacity(0.65)
        cursor("pointer")
    }
}
