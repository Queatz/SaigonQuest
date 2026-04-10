package main.components.minigame

import org.jetbrains.compose.web.css.*
import main.theme.Theme

object MinigameStyles : StyleSheet() {
    val gameContainer by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.Center)
        width(100.percent)
        height(100.percent)
        padding(20.px)
        boxSizing("border-box")
    }

    val cardContainer by style {
        position(Position.Relative)
        width(300.px)
        height(400.px)
        property("perspective", "1000px")
        cursor("pointer")
    }

    val cardInner by style {
        position(Position.Relative)
        width(100.percent)
        height(100.percent)
        textAlign("center")
        property("transition", "transform 0.6s")
        property("transform-style", "preserve-3d")
        
        (self + className("is-flipped")) style {
            property("transform", "rotateY(180deg)")
        }
    }

    val cardFace by style {
        position(Position.Absolute)
        width(100.percent)
        height(100.percent)
        property("backface-visibility", "hidden")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.Center)
        color(Theme.colors.onBackground)
        padding(24.px)
        boxSizing("border-box")

        media("(prefers-color-scheme: dark)") {
            self style {
                color(Theme.colors.onBackgroundDark)
            }
        }
    }

    val cardBack by style {
        property("transform", "rotateY(180deg)")
    }

    val matchGrid by style {
        display(DisplayStyle.Grid)
        gridTemplateColumns("repeat(auto-fit, minmax(180px, 1fr))")
        gap(16.px)
        width(100.percent)
        maxWidth(100.percent)
        padding(24.px)
        justifyContent(JustifyContent.Center)
        boxSizing("border-box")
    }

    val matchItem by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.Center)
        cursor("pointer")
        textAlign("center")
        property("transition", "all 0.2s")
        boxSizing("border-box")
        property("aspect-ratio", "1 / 1")
        maxWidth(180.px)
        property("margin-left", "auto")
        property("margin-right", "auto")
        width(100.percent)
        
        hover(self) style {
            property("transform", "translateY(-4px)")
            property("filter", "brightness(1.1)")
        }
    }

    val selectedMatchItem by style {
        property("transform", "scale(1.05) translateY(-2px)")
        property("filter", "brightness(1.2) contrast(1.1)")
        property("z-index", "10")

        hover(self) style {
            property("transform", "scale(1.05) translateY(-2px)")
        }
    }

    val scenarioBackground by style {
        position(Position.Relative)
        maxWidth(1000.px)
        property("width", "auto")
        cursor("crosshair")
        boxSizing("border-box")
    }

    val hotspotIndicator by style {
        position(Position.Absolute)
        borderRadius(50.percent)
        border(2.px, LineStyle.Dashed, Theme.colors.primary)
        backgroundColor(Color("rgba(255, 255, 255, 0.3)"))
        property("transform", "translate(-50%, -50%)")
    }

    val controlsContainer by style {
        display(DisplayStyle.Flex)
        gap(16.px)
        marginTop(24.px)
    }
}
