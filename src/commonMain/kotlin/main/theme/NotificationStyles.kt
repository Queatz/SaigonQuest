package main.theme

import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.StyleSheet
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.overflowY
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.vh

/**
 * Styles for notification components
 */
object NotificationStyles : StyleSheet() {
    val notificationsContainer by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        padding(16.px)
        property("flex-grow", "1")
        overflowY("auto")
    }
    
    val markAllAsReadContainer by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.FlexEnd)
        marginBottom(12.px)
    }
    
    val markAllAsReadButton by style {
        backgroundColor(rgba(60, 60, 60, 0.8))
        color(Color.white)
        padding(8.px, 12.px)
        borderRadius(4.px)
        cursor("pointer")
        property("border", "none")
    }
    
    val loadingContainer by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        padding(32.px)
        color(rgba(255, 255, 255, 0.7))
    }
    
    val errorContainer by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        padding(32.px)
        color(rgba(255, 0, 0, 0.7))
    }
    
    val emptyContainer by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        padding(32.px)
        color(rgba(255, 255, 255, 0.7))
    }
    
    val notificationItem by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        padding(12.px)
        marginBottom(8.px)
        borderRadius(4.px)
        cursor("pointer")
    }
    
    val notificationItemRead by style {
        backgroundColor(rgba(40, 40, 40, 0.6))
    }
    
    val notificationItemUnread by style {
        backgroundColor(rgba(60, 60, 60, 0.8))
    }
    
    val notificationHeader by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        marginBottom(4.px)
    }
    
    val notificationTitle by style {
        margin(0.px)
        color(Color.white)
        fontSize(16.px)
    }
    
    val notificationTimestamp by style {
        margin(0.px)
        color(rgba(255, 255, 255, 0.5))
        fontSize(12.px)
    }
    
    val notificationDescription by style {
        margin(0.px)
        color(rgba(255, 255, 255, 0.8))
        fontSize(14.px)
    }
    
    // Friend request notification specific styles
    val friendRequestContent by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        gap(8.px)
        marginTop(8.px)
    }
    
    val friendRequestStatus by style {
        fontSize(14.px)
        color(rgba(255, 255, 255, 0.7))
    }
    
    val friendRequestActions by style {
        display(DisplayStyle.Flex)
        gap(8.px)
    }
    
    val acceptButton by style {
        backgroundColor(rgba(0, 120, 0, 0.7))
        color(Color.white)
        padding(6.px, 12.px)
        borderRadius(4.px)
        cursor("pointer")
        property("border", "none")
    }
    
    val rejectButton by style {
        backgroundColor(rgba(120, 0, 0, 0.7))
        color(Color.white)
        padding(6.px, 12.px)
        borderRadius(4.px)
        cursor("pointer")
        property("border", "none")
    }
}
