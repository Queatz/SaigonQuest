package main.theme

import org.jetbrains.compose.web.css.StyleSheet
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginRight
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.gap

object EditorStyles : StyleSheet() {
    // World Objects Content styles
    val worldObjectsContainer by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        height(100.percent)
        padding(16.px)
    }
    
    val createObjectButton by style {
        backgroundColor(Theme.colors.primary)
        color(Theme.colors.onPrimary)
        property("border", "0")
        property("border-radius", "4px")
        padding(8.px, 16.px)
        marginRight(8.px)
        cursor("pointer")
        fontSize(14.px)
        margin(0.px, 0.px, 16.px, 0.px)
    }
    
    val loadingMessage by style {
        color(Color.white)
        property("text-align", "center")
    }
    
    val errorMessage by style {
        color(Color.red)
        property("text-align", "center")
    }
    
    val emptyMessage by style {
        color(rgba(255, 255, 255, 0.7))
        property("text-align", "center")
    }
    
    val objectsListContainer by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        gap(8.px)
        property("overflow-y", "auto")
        property("flex-grow", "1")
    }
    
    val objectItem by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        padding(8.px)
        backgroundColor(rgba(255, 255, 255, 0.1))
        property("border-radius", "4px")
    }
    
    val objectTitle by style {
        color(Color.white)
    }
    
    val actionButtonsContainer by style {
        display(DisplayStyle.Flex)
        gap(4.px)
    }
    
    val locateButton by style {
        backgroundColor(rgba(0, 120, 255, 0.7))
        color(Color.white)
        property("border", "0")
        property("border-radius", "4px")
        padding(4.px, 8.px)
        cursor("pointer")
        fontSize(12.px)
    }
    
    val editButton by style {
        backgroundColor(rgba(0, 180, 0, 0.7))
        color(Color.white)
        property("border", "0")
        property("border-radius", "4px")
        padding(4.px, 8.px)
        cursor("pointer")
        fontSize(12.px)
    }
    
    val removeButton by style {
        backgroundColor(rgba(255, 0, 0, 0.7))
    }
}
