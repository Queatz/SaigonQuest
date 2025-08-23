package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.FriendApi
import main.api.NotificationApi
import main.components.player.FriendshipStatus
import main.theme.NotificationStyles
import models.Notification
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/**
 * Content for a friend request notification.
 * This component displays the current friendship status and appropriate action buttons.
 *
 * @param notification The friend request notification
 * @param onStatusChange Callback when the friendship status changes
 */
@Composable
fun FriendRequestNotificationContent(
    notification: Notification,
    onStatusChange: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var friendshipStatus by remember { mutableStateOf(FriendshipStatus.UNKNOWN) }
    
    // Load the current friendship status when the component is first displayed
    LaunchedEffect(notification.otherUserId) {
        if (notification.otherUserId.isNotEmpty()) {
            try {
                val status = FriendApi.getFriendshipStatus(notification.otherUserId)
                friendshipStatus = status
            } catch (e: Throwable) {
                console.error("Error loading friendship status", e)
            }
        }
    }
    
    // Handle accepting a friend request
    fun acceptFriendRequest() {
        scope.launch {
            try {
                val result = NotificationApi.acceptFriendRequest(notification)
                if (result != null) {
                    friendshipStatus = FriendshipStatus.FRIENDS
                    onStatusChange()
                }
            } catch (e: Throwable) {
                console.error("Error accepting friend request", e)
            }
        }
    }
    
    // Handle rejecting a friend request
    fun rejectFriendRequest() {
        scope.launch {
            try {
                val result = NotificationApi.rejectFriendRequest(notification)
                if (result) {
                    friendshipStatus = FriendshipStatus.NOT_FRIENDS
                    onStatusChange()
                }
            } catch (e: Throwable) {
                console.error("Error rejecting friend request", e)
            }
        }
    }
    
    Div(attrs = { classes(NotificationStyles.friendRequestContent) }) {
        // Display the current friendship status
        P(attrs = { classes(NotificationStyles.friendRequestStatus) }) {
            when (friendshipStatus) {
                FriendshipStatus.UNKNOWN -> Text("Loading friendship...")
                FriendshipStatus.NOT_FRIENDS -> Text("Not friends.")
                FriendshipStatus.REQUEST_SENT -> Text("Friend request sent.")
                FriendshipStatus.REQUEST_RECEIVED -> Text("Friend request received.")
                FriendshipStatus.FRIENDS -> Text("You're friends!")
            }
        }
        
        // Show action buttons based on the friendship status
        if (friendshipStatus == FriendshipStatus.REQUEST_RECEIVED) {
            Div(attrs = { classes(NotificationStyles.friendRequestActions) }) {
                Button(
                    attrs = {
                        classes(NotificationStyles.acceptButton)
                        onClick {
                            it.stopPropagation()
                            it.preventDefault()
                            acceptFriendRequest()
                        }
                    }
                ) {
                    Text("Add friend")
                }
                
                Button(
                    attrs = {
                        classes(NotificationStyles.rejectButton)
                        onClick {
                            it.stopPropagation()
                            it.preventDefault()
                            rejectFriendRequest()
                        }
                    }
                ) {
                    Text("Ignore")
                }
            }
        }
    }
}
