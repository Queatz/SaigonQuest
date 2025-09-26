package main.theme

import org.jetbrains.compose.web.css.StyleSheet
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.FlexWrap
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.flexWrap
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
        borderRadius(16.px)
        padding(8.px, 16.px)
        marginRight(8.px)
        cursor("pointer")
        fontSize(14.px)
        margin(0.px, 0.px, 16.px, 0.px)
    }
    
    val loadingMessage by style {
        color(Theme.colors.onBackgroundDark)
        property("text-align", "center")

        dark(self) { color(Theme.colors.onBackground) }
    }
    
    val errorMessage by style {
        color(Theme.colors.error)
        property("text-align", "center")

        dark(self) { color(Theme.colors.errorDark) }
    }
    
    val emptyMessage by style {
        color(Theme.colors.textMuted)
        property("text-align", "center")

        dark(self) { color(Theme.colors.textMutedDark) }
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
        flexDirection(FlexDirection.Column)
        alignItems(AlignItems.FlexStart)
        gap(8.px)
        padding(8.px)
        backgroundColor(Theme.colors.surfaceVariant)
        color(Theme.colors.onSurfaceVariant)
        borderRadius(16.px)

        dark(self) {
            backgroundColor(Theme.colors.surfaceVariantDark)
            color(Theme.colors.onSurfaceVariantDark)
        }
    }
    
    val objectTitle by style {
    }
    
    val actionButtonsContainer by style {
        display(DisplayStyle.Flex)
        flexWrap(FlexWrap.Wrap)
        gap(8.px)
    }
    
    val locateButton by style {
        backgroundColor(Theme.colors.primary)
        color(Theme.colors.onPrimary)
        property("border", "0")
        borderRadius(16.px)
        padding(4.px, 8.px)
        cursor("pointer")
        fontSize(12.px)
    }
    
    val editButton by style {
        backgroundColor(Theme.colors.success)
        color(Theme.colors.onPrimary)
        property("border", "0")
        borderRadius(16.px)
        padding(4.px, 8.px)
        cursor("pointer")
        fontSize(12.px)
    }

    val removeButton by style {
        backgroundColor(Theme.colors.error)
        color(Theme.colors.onPrimary)
        property("border", "0")
        borderRadius(16.px)
        padding(4.px, 8.px)
        cursor("pointer")
        fontSize(12.px)
    }
}
