package main.theme

import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.StyleSheet
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.bottom
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.right
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.css.rgba

object NodeStyles : StyleSheet() {
    // DialogueBox styles
    val dialogueOverlay by style {
        position(Position.Fixed)
        top(0.px)
        left(0.px)
        right(0.px)
        bottom(0.px)
        backgroundColor(Theme.colors.overlayScrim)
        property("z-index", "1000")
        display(DisplayStyle.Flex)
        property("align-items", "center")
        justifyContent(JustifyContent.Center)

        dark(self) { backgroundColor(Theme.colors.overlayScrimDark) }
    }
    
    val dialogueContainer by style {
        width(500.px)
        padding(20.px)
        backgroundColor(Theme.colors.surface)
        property("border-radius", "8px")
        property("box-shadow", "0 4px 8px ${Theme.colors.shadowLight}")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        property("align-items", "center")

        dark(self) {
            backgroundColor(Theme.colors.surfaceDark)
            color(Theme.colors.onSurfaceDark)
            property("box-shadow", "0 4px 8px ${Theme.colors.shadowStrong}")
        }
    }
    
    val dialogueText by style {
        margin(0.px)
        padding(10.px)
        property("text-align", "center")
        property("font-size", "16px")
        property("line-height", "1.5")
        color(Theme.colors.onSurface)
        
        dark(self) { color(Theme.colors.onSurfaceDark) }
    }
    
    val dialogueButtonContainer by style {
        display(DisplayStyle.Flex)
        property("gap", "8px")
        justifyContent(JustifyContent.Center)
        marginTop(16.px)
    }

    val dialogueButtonContainerVertical by style {
        flexDirection(FlexDirection.Column)
    }
    
    val dialogueButton by style {
        padding(10.px, 20.px)
        backgroundColor(Theme.colors.success)
        color(Theme.colors.onPrimary)
        property("border", "none")
        property("border-radius", "4px")
        property("cursor", "pointer")
        property("font-size", "16px")
        
        dark(self) {
            backgroundColor(Theme.colors.successDark)
            property("box-shadow", "0 2px 4px ${Theme.colors.shadowMedium}")
        }
    }
}
