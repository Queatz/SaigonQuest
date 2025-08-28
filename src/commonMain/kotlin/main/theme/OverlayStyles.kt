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
import org.jetbrains.compose.web.css.boxSizing
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.opacity
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.textAlign
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.width

object OverlayStyles : StyleSheet() {
    // Fullscreen fade overlay for world transitions
    val fadeOverlay by style {
        position(Position.Fixed)
        top(0.px)
        left(0.px)
        width(100.percent)
        height(100.percent)
        backgroundColor(Theme.colors.backgroundDark)
        property("z-index", "2500")
        property("pointer-events", "auto")
        property("transition", "opacity 250ms ease, backdrop-filter 250ms ease, -webkit-backdrop-filter 250ms ease")
        property("backdrop-filter", "blur(8px)")
        property("-webkit-backdrop-filter", "blur(8px)")
        opacity(1)
    }

    // Loading screen styles
    val loadingScreenContainer by style {
        position(Position.Absolute)
        top(0.px)
        left(0.px)
        width(100.percent)
        height(100.vh)
        backgroundColor(Theme.colors.backgroundDark)
        color(Theme.colors.onBackgroundDark)
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
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
        backgroundColor(Theme.colors.loadingPrimaryBg)
        color(Theme.colors.onBackgroundDark)
        property("border", "0")
        property("border-radius", "4px")
        cursor("pointer")
        fontSize(16.px)
    }

    val loadingScreenSecondaryButton by style {
        padding(12.px, 24.px)
        backgroundColor(Theme.colors.loadingSecondaryBg)
        color(Theme.colors.onBackgroundDark)
        property("border", "0")
        property("border-radius", "4px")
        cursor("pointer")
        fontSize(16.px)
    }

    // Common styles for panels and dialogs overlay
    val overlayBase by style {
        position(Position.Fixed)
        top(0.px)
        left(0.px)
        width(100.percent)
        height(100.percent)
        backgroundColor(Theme.colors.overlayScrim)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        property("overflow-y", "auto")
        boxSizing("border-box")
        property("z-index", "1000")
        property("pointer-events", "auto")
        property("backdrop-filter", "blur(2px)")
        property("-webkit-backdrop-filter", "blur(2px)")
        property("isolation", "isolate")
        property("contain", "layout paint size")

        dark(self) {
            backgroundColor(Theme.colors.overlayScrimDark)
        }
    }
}
