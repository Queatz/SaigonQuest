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
    

    val worldObjectsSearchInput by style {
        marginBottom(16.px)
    }
    
    val createObjectButton by style {
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
    }
    
    val objectTitle by style {
    }
    
    val actionButtonsContainer by style {
        display(DisplayStyle.Flex)
        flexWrap(FlexWrap.Wrap)
        gap(8.px)
    }
    
}
