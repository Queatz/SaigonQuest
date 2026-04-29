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
                property("size", "A4 portrait")
                property("margin", "0mm")
            }

            // Ensure the root and any intermediate containers don't have margins/padding
            "#root" style {
                margin(0.px)
                padding(0.px)
                width(100.percent)
            }
        }
    }

    val printContainer by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        alignItems(AlignItems.Center)
        width(100.percent)
        backgroundColor(Color.white)

        media("print") {
            display(DisplayStyle.Block)
        }
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

        media("print") {
            property("margin", "0mm")
            property("border", "none")
            property("overflow", "visible")
            property("height", "auto")
            property("min-height", "297mm")
        }

        self + lastChild style {
            property("page-break-after", "auto")
            property("break-after", "auto")
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
//        gap(2.mm)
    }

    val cardItem by style {
        property("break-inside", "avoid")
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
        property("break-inside", "avoid")
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        justifyContent(JustifyContent.Center)
        width(100.percent)
        height(100.percent)
        overflow("hidden")
    }

    val referenceTable by style {
        width(100.percent)
        property("border-collapse", "collapse")
        backgroundColor(Color.white)
    }

    val referenceCell by style {
        border(0.1.px, LineStyle.Solid, Color.black)
        padding(4.mm)
        property("vertical-align", "middle")
    }

    val referenceTitle by style {
        fontSize(18.pt)
        fontWeight("bold")
        marginBottom(5.mm)
        textAlign("center")
    }
}
