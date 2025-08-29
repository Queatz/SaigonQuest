package main.theme

import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.StyleSheet
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.fontWeight
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.overflow
import org.jetbrains.compose.web.css.overflowY
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.border
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.alignItems

object FriendsStyles : StyleSheet() {
    // Root container for FriendsContent
    val friendsContentRoot by style {
        property("flex-grow", "1")
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        padding(16.px)
        height(100.percent)
        overflow("hidden")
        position(org.jetbrains.compose.web.css.Position.Relative)
    }

    // Loading & Empty container
    val centeredGrowContainer by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        property("flex-grow", "1")
    }

    // Error container
    val errorContainer by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        property("flex-grow", "1")
        color(Theme.colors.error)
        dark(self) { color(Theme.colors.errorDark) }
    }

    // Empty state text
    val emptyText by style {
        color(Theme.colors.textMuted)
        property("text-align", "center")
        dark(self) { color(Theme.colors.textMutedDark) }
    }

    // Friends list scroll container
    val friendsListContainer by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        gap(12.px)
        property("flex-grow", "1")
        overflowY("auto")
    }

    // Friend item card
    val friendListItem by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        padding(12.px)
        backgroundColor(Theme.colors.white04)
        borderRadius(8.px)
        border(1.px, LineStyle.Solid, Theme.colors.white08)
    }

    // Header row
    val friendHeader by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        marginBottom(8.px)
    }

    // Friend name
    val friendName by style {
        display(DisplayStyle.Flex)
        alignItems(AlignItems.Center)
        fontSize(16.px)
        fontWeight("bold")
        color(Theme.colors.onBackground)
        gap(8.px)
        dark(self) { color(Theme.colors.onBackgroundDark) }
    }

    // Online indicator dots
    val onlineDotBase by style {
        width(10.px)
        height(10.px)
        borderRadius(50.percent)
    }
    val onlineDotOnline by style {
        backgroundColor(Theme.colors.success)
        dark(self) { backgroundColor(Theme.colors.successDark) }
    }
    val onlineDotOffline by style {
        backgroundColor(Theme.colors.outline)
        dark(self) { backgroundColor(Theme.colors.outlineDark) }
    }

    // Last active text
    val lastActiveText by style {
        fontSize(12.px)
        color(Theme.colors.textMuted)
        dark(self) { color(Theme.colors.textMutedDark) }
    }

    // Current world text (online)
    val currentWorldOnline by style {
        fontSize(14.px)
        color(Theme.colors.success)
        marginBottom(8.px)
        dark(self) { color(Theme.colors.successDark) }
    }

    // Last world text (offline)
    val lastWorldOffline by style {
        fontSize(14.px)
        color(Theme.colors.textMuted)
        marginBottom(12.px)
        dark(self) { color(Theme.colors.textMutedDark) }
    }

    // Buttons row
    val buttonsRow by style {
        display(DisplayStyle.Flex)
        gap(8.px)
    }

    // Button variants
    val buttonJump by style {
        padding(8.px, 12.px)
        backgroundColor(Theme.colors.primary)
        color(Theme.colors.onPrimary)
        property("border", "none")
        borderRadius(4.px)
        property("cursor", "pointer")
        dark(self) { backgroundColor(Theme.colors.primaryDark) }
    }

    val buttonSend by style {
        padding(8.px, 12.px)
        backgroundColor(Theme.colors.success)
        color(Theme.colors.onPrimary)
        property("border", "none")
        borderRadius(4.px)
        property("cursor", "pointer")
        dark(self) { backgroundColor(Theme.colors.successDark) }
    }

    val buttonRemove by style {
        padding(8.px, 12.px)
        backgroundColor(Color.transparent)
        border(1.px, LineStyle.Solid, Theme.colors.error)
        color(Theme.colors.error)
        borderRadius(4.px)
        property("cursor", "pointer")
        dark(self) { border(1.px, LineStyle.Solid, Theme.colors.errorDark); color(Theme.colors.errorDark) }
    }
}
