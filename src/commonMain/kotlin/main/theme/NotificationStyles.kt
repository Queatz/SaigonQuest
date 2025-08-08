package main.theme

import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.StyleSheet
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.fontWeight
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginLeft
import org.jetbrains.compose.web.css.marginRight
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.maxWidth
import org.jetbrains.compose.web.css.opacity
import org.jetbrains.compose.web.css.overflowY
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.transform
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.width

/**
 * Styles for notification components
 */
object NotificationStyles : StyleSheet() {
    // Notification panel styles
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
    
    // NotificationLayout styles
    val notificationLayoutContainer by style {
        position(Position.Fixed)
        top(20.px)
        width(100.percent)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        property("z-index", "1000")
    }
    
    val notificationCard by style {
        backgroundColor(Color("#ffffff"))
        borderRadius(8.px)
        property("box-shadow", "0 4px 8px rgba(0, 0, 0, 0.1)")
        padding(16.px)
        maxWidth(400.px)
        width(100.percent)
        margin(0.px, 16.px)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems("center")
        cursor("pointer")
        property("pointer-events", "auto")
        property("animation", "fadeIn 0.3s ease-in-out")
        
        dark(self) {
            backgroundColor(Color("#333333"))
            property("box-shadow", "0 4px 8px rgba(0, 0, 0, 0.3)")
        }
    }
    
    val notificationContentContainer by style {
        display(DisplayStyle.Flex)
        property("flex-direction", "column")
        property("flex-grow", "1")
        marginRight(16.px)
    }
    
    val notificationCardTitle by style {
        fontWeight("bold")
        fontSize(16.px)
        property("margin-bottom", "4px")
        color(Color("#333333"))
        
        dark(self) {
            color(Color("#ffffff"))
        }
    }
    
    val notificationCardDescription by style {
        fontSize(14.px)
        color(Color("#666666"))
        
        dark(self) {
            color(Color("#cccccc"))
        }
    }
    
    val notificationCloseButton by style {
        width(24.px)
        height(24.px)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems("center")
        borderRadius(50.percent)
        backgroundColor(Color("#f0f0f0"))
        cursor("pointer")
        fontSize(14.px)
        color(Color("#666666"))
        
        dark(self) {
            backgroundColor(Color("#555555"))
            color(Color("#cccccc"))
        }
    }
}
