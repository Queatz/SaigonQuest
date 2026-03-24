package main.theme

import org.jetbrains.compose.web.css.*

object EditorStyles : StyleSheet() {
    // World Objects Content styles
    val worldObjectsContainer by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        flexGrow(1)
        minHeight(0.px)
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

    val worldObjectsSearchInput by style {
        marginBottom(16.px)
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
        height(0.px)
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
