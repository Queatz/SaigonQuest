package main.theme

import org.jetbrains.compose.web.css.*

object QuestStyles : StyleSheet() {
    val leftContainer by style {
        position(Position.Fixed)
        top(20.px)
        left(20.px)
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        alignItems(AlignItems.FlexStart)
        gap(12.px)
        property("z-index", "900")
        property("pointer-events", "none")
    }

    val questTrackerItem by style {
        padding(8.px)
        borderRadius(4.px)
        cursor("pointer")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        gap(2.px)
        justifyContent(JustifyContent.Stretch)
        property("pointer-events", "all")
        
        hover(self) style {
            backgroundColor(rgba(255, 255, 255, 0.05))
        }
    }

    val questTrackerTitle by style {
        fontWeight("bold")
        fontSize(14.px)
        color(Color.white)
        property("text-shadow", "0 1px 2px rgba(0,0,0,0.5)")
    }

    val questTrackerStep by style {
        fontSize(12.px)
        color(Color("#cccccc"))
    }

    val questCompleted by style {
        fontSize(12.px)
        color(Color("#4caf50"))
        fontWeight("bold")
    }

    val progressBarContainer by style {
        height(4.px)
        backgroundColor(Color("#444444"))
        borderRadius(2.px)
        marginTop(4.px)
        width(100.percent)
        overflow("hidden")
    }

    val progressBarFill by style {
        height(100.percent)
        backgroundColor(Color("#4caf50"))
        property("transition", "width 0.3s ease-in-out")
    }
}
