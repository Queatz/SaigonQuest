package main.components.chat

import Translations.str
import androidx.compose.runtime.Composable
import main.components.Md
import main.components.Paper
import main.components.Translated
import main.theme.Styles
import models.ChatMessage
import models.ChatSenderType
import org.jetbrains.compose.web.css.marginLeft
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import kotlin.js.Date


/**
 * A component that displays a single chat message.
 *
 * @param message The chat message to display
 * @param onMessageClick Callback when the message is clicked (only for user's own messages)
 */
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onMessageClick: (ChatMessage) -> Unit,
    isOwner: Boolean = false,
    isCurrentUser: Boolean = false,
    isPinned: Boolean = false,
    onPinToggle: (() -> Unit)? = null,
    onImageClick: (String) -> Unit = {},
    onImageLoaded: () -> Unit = {},
) {
    MessageWrapper(message.senderType) {
        Div(
            attrs = {
                classes(Styles.chatMessageItem)
                classes(if (isCurrentUser) Styles.chatMessageItemCurrentUser else Styles.chatMessageItemOtherUser)
                if (message.senderType == ChatSenderType.OBJECT) classes(Styles.chatMessageItemObject)
                if (message.senderType == ChatSenderType.SYSTEM) classes(Styles.chatMessageItemSystem)

                onClick { onMessageClick(message) }
            }
        ) {
            // Username and timestamp
            Div(
                attrs = {
                    classes(Styles.chatMessageHeader)
                }
            ) {
                // Username
                if (message.senderType != ChatSenderType.SYSTEM) {
                    Span(
                        attrs = {
                            classes(Styles.chatMessageUsername)
                            classes(if (isCurrentUser) Styles.chatMessageUsernameCurrentUser else Styles.chatMessageUsernameOtherUser)
                        }
                    ) {
                        Text(message.senderName)
                    }
                } else {
                    // System message content is treated as the header, e.g. Player joined, Player left
                    MessageContent(message = message)
                }

                // Timestamp with edited indicator
                Span(
                    attrs = {
                        classes(Styles.chatMessageTimestamp)
                    }
                ) {
                    // Show edited indicator if the message has been edited
                    if (message.edited) {
                        Span(
                            attrs = {
                                classes(Styles.chatMessageEditedIndicator)
                                title(str("Edited"))
                            }
                        ) {
                            Text("•")
                        }
                    }
                    // Show shout indicator if the message was shouted
                    if (message.shouted) {
                        Span(
                            attrs = {
                                classes(Styles.chatMessageEditedIndicator)
                                title(str("Shouted"))
                            }
                        ) {
                            Text("📣")
                        }
                    }
                    Text(formatTimestamp(message.timestamp.toEpochMilliseconds()))

                    // Owner-only pin/unpin control
                    if (isOwner && onPinToggle != null && message.senderType != ChatSenderType.SYSTEM) {
                        Span(attrs = {
                            classes(Styles.chatPinButton)

                            style {
                                marginLeft(8.px)
                            }

                            onClick {
                                it.stopPropagation()
                                onPinToggle()
                            }
                        }) {
                            Text(" ")
                            if (isPinned) Translated("Unpin") else Translated("Pin")
                        }
                    }
                }
            }

            if (message.senderType != ChatSenderType.SYSTEM) {
                // Message content
                MessageContent(message = message)
            }

            // Attachments
            if (!message.deleted && message.attachments.isNotEmpty()) {
                MessageAttachments(
                    attachments = message.attachments,
                    onImageClick = onImageClick,
                    onImageLoaded = onImageLoaded,
                )
            }
        }
    }
}

@Composable
private fun MessageWrapper(senderType: ChatSenderType, content: @Composable () -> Unit) {
    if (senderType == ChatSenderType.OBJECT) {
        Paper {
            content()
        }
    } else {
        content()
    }
}

@Composable
fun MessageContent(message: ChatMessage) {
    P(
        attrs = {
            classes(Styles.chatMessageContent)
            if (message.deleted) {
                classes(Styles.chatMessageContentDeleted)
            }
            if (message.senderType == ChatSenderType.SYSTEM) {
                classes(Styles.chatMessageContentSystem)
            }
        }
    ) {
        if (message.deleted) {
            Translated("Deleted")
        } else {
            Md(message.message)
        }
    }
}

/**
 * Formats a timestamp as a human-readable string.
 *
 * @param timestamp The timestamp in milliseconds
 * @return A formatted string like "Today, 2:30 PM" or "Yesterday, 3:45 PM" or "Jul 15, 4:20 PM"
 */
private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Date()
    val today = Date(now.getFullYear(), now.getMonth(), now.getDate())
    val yesterday = Date(today.getTime() - 86400000) // 24 hours in milliseconds

    val messageDate = Date(date.getFullYear(), date.getMonth(), date.getDate())

    // Format time as HH:MM using standard JavaScript methods
    val hours = date.getHours().toString().padStart(2, '0')
    val minutes = date.getMinutes().toString().padStart(2, '0')
    val timeString = "$hours:$minutes"

    return when {
        messageDate.getTime() == today.getTime() -> "Today, $timeString"
        messageDate.getTime() == yesterday.getTime() -> "Yesterday, $timeString"
        else -> {
            // Format date as MMM DD using standard JavaScript methods
            val months = arrayOf(
                str("Jan"),
                str("Feb"),
                str("Mar"),
                str("Apr"),
                str("May"),
                str("Jun"),
                str("Jul"),
                str("Aug"),
                str("Sep"),
                str("Oct"),
                str("Nov"),
                str("Dec"),
            )
            val month = months[date.getMonth()]
            val day = date.getDate().toString()
            "$month $day, $timeString"
        }
    }
}
