package main.components.minigame

import org.jetbrains.compose.web.css.*
import main.theme.Theme

object MinigameStyles : StyleSheet() {
    val gameContainer by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        alignItems(AlignItems.Center)
        width(100.percent)
        height(100.percent)
        padding(20.px)
        boxSizing("border-box")
        property("overflow-y", "auto")
    }

    val gameContentWrapper by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        alignItems(AlignItems.Center)
        property("margin", "auto")
        width(100.percent)
        boxSizing("border-box")
    }

    val cardContainer by style {
        position(Position.Relative)
        width(400.px)
        maxWidth(100.percent)
        maxHeight(80.vh)
        property("aspect-ratio", "3/4")
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
        padding(32.px)
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

    val flashcardImage by style {
        maxWidth(100.percent)
        maxHeight(260.px)
        borderRadius(12.px)
        marginBottom(16.px)
        property("object-fit", "contain")
        flexShrink(1)
        minHeight(0.px)
    }

    val matchGrid by style {
        display(DisplayStyle.Grid)
        gridTemplateColumns("repeat(auto-fit, minmax(280px, 1fr))")
        gap(16.px)
        width(100.percent)
        maxWidth(100.percent)
        padding(24.px)
        justifyContent(JustifyContent.Center)
        boxSizing("border-box")
    }

    val matchItem by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.Center)
        gap(12.px)
        padding(16.px)
        cursor("pointer")
        textAlign("center")
        property("transition", "all 0.2s")
        boxSizing("border-box")
        minHeight(280.px)
        maxWidth(280.px)
        property("margin-left", "auto")
        property("margin-right", "auto")
        width(100.percent)
        
        hover(self) style {
            property("transform", "translateY(-4px)")
            property("filter", "brightness(1.1)")
        }
    }

    val matchItemImage by style {
        width(100.percent)
        height(100.percent)
        maxWidth(160.px)
        maxHeight(160.px)
        property("object-fit", "cover")
        borderRadius(12.px)
    }

    val matchItemText by style {
        margin(0.px)
        fontSize(24.px)
        fontWeight("bold")
        color(Theme.colors.onBackground)
        textAlign("center")
        maxWidth(100.percent)
        lineHeight(1.2.em)
        property("word-wrap", "break-word")
        
        media("(prefers-color-scheme: dark)") {
            self style {
                color(Theme.colors.onBackgroundDark)
            }
        }
    }

    val matchItemAudioIcon by style {
        fontSize(32.px)
        opacity(0.8)
    }

    val matchOut by keyframes {
        from {
            property("transform", "scale(1)")
            property("opacity", "1")
        }
        20.percent {
            property("transform", "scale(1.1) rotate(-5deg)")
        }
        to {
            property("transform", "scale(0) rotate(15deg)")
            property("opacity", "0")
        }
    }

    val matchedMatchItem by style {
        animation(matchOut) {
            duration(0.6.s)
            timingFunction(AnimationTimingFunction.EaseInOut)
            fillMode(AnimationFillMode.Forwards)
        }
        property("pointer-events", "none")
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

    val scenarioPrompt by style {
        position(Position.Absolute)
        top(20.px)
        left(20.px)
        margin(0.px)
        padding(.5.cssRem, 1.cssRem)
        property("z-index", "10")
        property("pointer-events", "none")
    }

    val scenarioPromptText by style {
        margin(0.px)
        fontSize(28.px)
        fontWeight("bold")
        color(Theme.colors.onBackground)
        
        media("(prefers-color-scheme: dark)") {
            self style {
                color(Theme.colors.onBackgroundDark)
            }
        }
    }

    val flashcardText by style {
        fontSize(32.px)
        fontWeight("bold")
        textAlign("center")
        margin(16.px, 0.px)
        flexShrink(0)
    }

    val flashcardTranslation by style {
        fontSize(24.px)
        fontWeight("bold")
        textAlign("center")
        margin(8.px, 0.px)
        flexShrink(0)
    }

    val flashcardDefinition by style {
        fontSize(20.px)
        textAlign("center")
        margin(8.px, 0.px)
        opacity(0.9)
        flexShrink(0)
    }

    val controlsContainer by style {
        display(DisplayStyle.Flex)
        gap(16.px)
        marginTop(24.px)
    }

    val sequencingWorkbench by style {
        display(DisplayStyle.Flex)
        flexWrap(FlexWrap.Wrap)
        gap(12.px)
        padding(20.px)
        minHeight(220.px)
        width(100.percent)
        maxWidth(800.px)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        marginBottom(32.px)
        boxSizing("border-box")
    }

    val sequencingItem by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.Center)
        padding(12.px)
        width(180.px)
        minHeight(180.px)
        cursor("pointer")
        property("transition", "all 0.2s")
        boxSizing("border-box")
        position(Position.Relative)

        hover(self) style {
            property("transform", "translateY(-2px)")
            property("filter", "brightness(1.05)")
        }
    }

    val sequencingItemImage by style {
        width(120.px)
        height(120.px)
        property("object-fit", "contain")
        marginBottom(8.px)
        borderRadius(12.px)
    }

    val sequencingItemText by style {
        fontSize(20.px)
        fontWeight("bold")
        textAlign("center")
        margin(0.px)
        width(100.percent)
        property("word-wrap", "break-word")
    }

    val sequencingPool by style {
        display(DisplayStyle.Flex)
        flexWrap(FlexWrap.Wrap)
        gap(16.px)
        justifyContent(JustifyContent.Center)
        width(100.percent)
        maxWidth(800.px)
    }

    val scenarioHeroImage by style {
        maxWidth(100.percent)
        maxHeight(80.vh)
        display(DisplayStyle.Block)
        property("object-fit", "contain")
        borderRadius(12.px)
        boxSizing("border-box")
    }

    val categorizationItemContainer by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        marginBottom(40.px)
        width(100.percent)
    }

    val categorizationItem by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        alignItems(AlignItems.Center)
        padding(24.px)
        width(320.px)
        minHeight(320.px)
        boxSizing("border-box")
    }

    val categorizationItemImage by style {
        width(200.px)
        height(200.px)
        property("object-fit", "contain")
        borderRadius(12.px)
        marginBottom(16.px)
    }

    val categorizationItemText by style {
        fontSize(28.px)
        fontWeight("bold")
        margin(0.px)
        textAlign("center")
        width(100.percent)
    }

    val categorizationBasketsContainer by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        flexWrap(FlexWrap.Wrap)
        gap(32.px)
        width(100.percent)
    }

    val categorizationBasket by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.Center)
        padding(24.px)
        width(240.px)
        minHeight(120.px)
        cursor("pointer")
        property("transition", "all 0.2s")
        boxSizing("border-box")
        
        hover(self) style {
            property("transform", "scale(1.05)")
            property("filter", "brightness(1.1)")
        }
    }

    val categorizationBasketText by style {
        fontSize(24.px)
        fontWeight("bold")
        margin(0.px)
        textAlign("center")
    }

    val identifyPromptContainer by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        alignItems(AlignItems.Center)
        marginBottom(40.px)
        gap(16.px)
    }

    val identifyAudioButton by style {
        fontSize(64.px)
        cursor("pointer")
        property("transition", "transform 0.2s")
        
        hover(self) style {
            property("transform", "scale(1.1)")
        }
    }

    val identifyOptionsGrid by style {
        display(DisplayStyle.Grid)
        gridTemplateColumns("repeat(2, 1fr)")
        gap(24.px)
        maxWidth(600.px)
        width(100.percent)
    }

    val identifyOption by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        alignItems(AlignItems.Center)
        padding(16.px)
        cursor("pointer")
        property("transition", "all 0.2s")
        boxSizing("border-box")
        
        hover(self) style {
            property("transform", "translateY(-4.px)")
            property("filter", "brightness(1.05)")
        }
    }

    val identifyOptionImage by style {
        width(160.px)
        height(160.px)
        property("object-fit", "contain")
        borderRadius(12.px)
        marginBottom(8.px)
    }

    val identifyOptionText by style {
        fontSize(20.px)
        fontWeight("bold")
        margin(0.px)
    }

    val shakeError by style {
        property("animation", "shake 0.4s ease-in-out")
    }

    val shake by keyframes {
        0.percent { property("transform", "translateX(0)") }
        20.percent { property("transform", "translateX(-8px)") }
        40.percent { property("transform", "translateX(8px)") }
        60.percent { property("transform", "translateX(-8px)") }
        80.percent { property("transform", "translateX(8px)") }
        100.percent { property("transform", "translateX(0)") }
    }
}
