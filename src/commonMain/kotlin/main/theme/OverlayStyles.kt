package main.theme

import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.AnimationTimingFunction
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.StyleSheet
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.animation
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.boxSizing
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.delay
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.duration
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.ms
import org.jetbrains.compose.web.css.opacity
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.textAlign
import org.jetbrains.compose.web.css.timingFunction
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.width

object OverlayStyles : StyleSheet() {
    // Keyframes for shout jiggle animation
    val shoutJiggle by keyframes {
        0.percent { property("transform", "scale(0.92) rotate(0deg)") }
        25.percent { property("transform", "scale(1.06) rotate(1deg)") }
        50.percent { property("transform", "scale(0.98) rotate(-1deg)") }
        75.percent { property("transform", "scale(1.03) rotate(0.5deg)") }
        100.percent { property("transform", "scale(1.0) rotate(0deg)") }
    }

    // Simple scale+fade in animation used at start
    val shoutIn by keyframes {
        0.percent {
            opacity(0)
            property("transform", "scale(0.9)")
        }
        100.percent {
            opacity(1)
            property("transform", "scale(1)")
        }
    }

    val shoutOverlay by style {
        property("transition", "opacity 300ms ${AnimationTimingFunction.EaseOut}, scale 300ms ${AnimationTimingFunction.EaseOut}")
        position(Position.Fixed)
        top(0.px)
        left(0.px)
        width(100.percent)
        height(100.percent)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        property("z-index", "4000")
        property("pointer-events", "none")
    }

    val shoutMessageBox by style {
        backgroundColor(Theme.colors.background)
        color(Theme.colors.onBackground)
        padding(24.px)
        borderRadius(24.px)
        fontSize(84.px)
        textAlign("center")
        property("box-shadow", "0 8px 24px ${Theme.colors.shadowMedium}")
        animation(shoutIn) {
            duration(120.ms)
            timingFunction(AnimationTimingFunction.EaseOut)
        }
        animation(shoutJiggle) {
            duration(420.ms)
            timingFunction(AnimationTimingFunction.EaseInOut)
            delay(120.ms)
        }

        dark(self) {
            backgroundColor(Theme.colors.backgroundDark)
            color(Theme.colors.onBackgroundDark)
        }
    }

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
        boxSizing("border-box")
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
        borderRadius(4.px)
        cursor("pointer")
        fontSize(16.px)
    }

    val loadingScreenSecondaryButton by style {
        padding(12.px, 24.px)
        backgroundColor(Theme.colors.loadingSecondaryBg)
        color(Theme.colors.onBackgroundDark)
        property("border", "0")
        borderRadius(4.px)
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
