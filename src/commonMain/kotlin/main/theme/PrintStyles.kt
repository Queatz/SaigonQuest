package main.theme

import org.jetbrains.compose.web.css.*

object PrintStyles : StyleSheet() {
    init {
        "body.printable, html.printable" style {
            margin(0.px)
            padding(0.px)
            backgroundColor(Color.white)
            width(100.percent)
            property("height", "auto")
            overflow("auto")
        }

        media("print") {
            "body, html" style {
                margin(0.px)
                padding(0.px)
                backgroundColor(Color.white)
                width(100.percent)
                property("height", "auto")
                overflow("visible")
            }

            // Force A4 and no margins for borderless printing
            "@page" style {
                property("size", "A4")
                property("margin", "0mm")
            }

            // Ensure the root and any intermediate containers don't have margins/padding
            "#root" style {
                margin(0.px)
                padding(0.px)
            }
        }
    }

    val printContainer by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        alignItems(AlignItems.Center)
        width(100.percent)
        backgroundColor(Color.white)
    }

    val a4Page by style {
        property("width", "210mm")
        property("height", "297mm")
        property("page-break-after", "always")
        property("break-after", "page")
        property("overflow", "hidden")
        property("box-sizing", "border-box")
        backgroundColor(Color.white)
        color(Color.black)
        position(Position.Relative)

        self + lastChild style {
            property("page-break-after", "auto")
            property("break-after", "auto")
        }
        
        media("print") {
            property("margin", "0")
            property("border", "none")
        }
    }

    val cardsGrid by style {
        display(DisplayStyle.Grid)
        property("grid-template-columns", "repeat(3, 1fr)")
        property("grid-template-rows", "repeat(4, 1fr)")
        width(100.percent)
        height(100.percent)
        padding(5.mm)
        boxSizing("border-box")
        gap(2.mm)
    }

    val cardItem by style {
        border(0.1.px, LineStyle.Solid, Color.lightgray)
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.Center)
        padding(5.mm)
        gap(2.mm)
        overflow("hidden")
    }

    val itemsGrid by style {
        display(DisplayStyle.Grid)
        property("grid-template-columns", "repeat(3, 1fr)")
        property("grid-template-rows", "repeat(5, 1fr)")
        width(100.percent)
        height(100.percent)
        padding(10.mm)
        boxSizing("border-box")
        gap(10.mm)
    }

    val itemContainer by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.Center)
        width(100.percent)
        height(100.percent)
        overflow("hidden")
    }
}
