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
import org.jetbrains.compose.web.css.paddingLeft
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.textAlign
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
        backgroundColor(Theme.colors.panelGlass)
        color(Theme.colors.onBackground)
        padding(8.px, 12.px)
        borderRadius(4.px)
        cursor("pointer")
        property("border", "none")

        dark(self) {
            backgroundColor(Theme.colors.panelGlassDark)
            color(Theme.colors.onBackgroundDark)
        }
    }
    
    val loadingContainer by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        padding(32.px)
        color(Theme.colors.textMutedDark)

        dark(self) { color(Theme.colors.textMuted) }
    }
    
    val errorContainer by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        padding(32.px)
        color(Theme.colors.error)
    }
    
    val emptyContainer by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems(AlignItems.Center)
        padding(32.px)
        color(Theme.colors.textMutedDark)

        dark(self) { color(Theme.colors.textMuted) }
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
        backgroundColor(Theme.colors.notifReadBg)

        dark(self) {
            backgroundColor(Theme.colors.notifReadBgDark)
        }
    }
    
    val notificationItemUnread by style {
        backgroundColor(Theme.colors.notifUnreadBg)

        dark(self) {
            backgroundColor(Theme.colors.notifUnreadBgDark)
        }
    }

    // System notification accent (e.g., subtle blue left border)
    val notificationItemSystem by style {
        property("border-left", "4px solid ${Theme.colors.accentInfoStrong}")
        paddingLeft(10.px) // compensate for left border
    }

    // System notification accent (e.g., subtle blue left border)
    val notificationItemChatNew by style {
        property("border-left", "4px solid ${Theme.colors.accentWarnStrong}")
        paddingLeft(10.px) // compensate for left border
    }

    // System notification accent (e.g., subtle blue left border)
    val notificationItemFriend by style {
        property("border-left", "4px solid ${Theme.colors.accentSuccessStrong}")
        paddingLeft(10.px) // compensate for left border
    }

    val notificationHeader by style {
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.SpaceBetween)
        alignItems(AlignItems.Center)
        marginBottom(4.px)
    }
    
    val notificationTitle by style {
        margin(0.px)
        color(Theme.colors.onBackground)
        fontSize(16.px)

        dark(self) { color(Theme.colors.onBackgroundDark) }
    }
    
    val notificationTimestamp by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        justifyContent(JustifyContent.FlexEnd)
        alignItems(AlignItems.FlexEnd)
        textAlign("right")

        color(Theme.colors.textMuted)
        fontSize(12.px)

        dark(self) { color(Theme.colors.textMutedDark) }
    }
    
    val notificationDescription by style {
        margin(0.px)
        color(Theme.colors.textSecondary)
        fontSize(14.px)

        dark(self) { color(Theme.colors.textSecondaryDark) }
    }

    // Small badge for system notifications
    val systemBadge by style {
        backgroundColor(Theme.colors.accentInfoSoft)
        color(Theme.colors.accentInfo)
        borderRadius(12.px)
        padding(2.px, 8.px)
        fontSize(12.px)
    }

    // Small badge for system notifications
    val chatBadge by style {
        backgroundColor(Theme.colors.accentWarnSoft)
        color(Theme.colors.accentWarn)
        borderRadius(12.px)
        padding(2.px, 8.px)
        fontSize(12.px)
        marginRight(8.px)
    }

    // Small badge for system notifications
    val friendBadge by style {
        backgroundColor(Theme.colors.accentSuccessSoft)
        color(Theme.colors.accentSuccess)
        borderRadius(12.px)
        padding(2.px, 8.px)
        fontSize(12.px)
        marginRight(8.px)
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
        color(Theme.colors.textMuted)

        dark(self) { color(Theme.colors.textMutedDark) }
    }
    
    val friendRequestActions by style {
        display(DisplayStyle.Flex)
        gap(8.px)
    }
    
    val acceptButton by style {
        backgroundColor(Theme.colors.success)
        color(Theme.colors.onPrimary)
        padding(6.px, 12.px)
        borderRadius(4.px)
        cursor("pointer")
        property("border", "none")
    }
    
    val rejectButton by style {
        backgroundColor(Theme.colors.error)
        color(Theme.colors.onPrimary)
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
        backgroundColor(Theme.colors.surface)
        borderRadius(16.px)
        property("box-shadow", "0 4px 8px ${Theme.colors.shadowLight}")
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
            backgroundColor(Theme.colors.surfaceDark)
            property("box-shadow", "0 4px 8px ${Theme.colors.shadowMedium}")
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
        color(Theme.colors.onSurface)
        
        dark(self) {
            color(Theme.colors.onSurfaceDark)
        }
    }
    
    val notificationCardDescription by style {
        fontSize(14.px)
        color(Theme.colors.onSurfaceVariant)
        
        dark(self) {
            color(Theme.colors.onSurfaceVariantDark)
        }
    }
    
    val notificationCloseButton by style {
        width(24.px)
        height(24.px)
        display(DisplayStyle.Flex)
        justifyContent(JustifyContent.Center)
        alignItems("center")
        borderRadius(50.percent)
        backgroundColor(Theme.colors.surfaceVariant)
        cursor("pointer")
        fontSize(14.px)
        color(Theme.colors.onSurfaceVariant)
        
        dark(self) {
            backgroundColor(Theme.colors.surfaceVariantDark)
            color(Theme.colors.onSurfaceVariantDark)
        }
    }
}
