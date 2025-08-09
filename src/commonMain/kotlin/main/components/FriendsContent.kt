package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lib.Vector3
import main.api.FriendApi
import main.api.WorldApi
import main.components.world.WorldLoader
import main.components.BasicConfirmDialog
import models.Friend
import models.Position
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
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
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import main.components.Translated
import Translations.str
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

/**
 * Content for the friends panel/dialog.
 * This component contains the shared UI elements for both mobile and desktop views.
 */
@OptIn(ExperimentalTime::class)
@Composable
fun FriendsContent() {
    // State for friends list
    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Coroutine scope for async operations
    val coroutineScope = remember { CoroutineScope(Dispatchers.Default) }
    
    // Router for navigation
    val router = Router.current
    
    // Fetch friends when the component is first rendered
    LaunchedEffect(Unit) {
        fetchFriends(
            onSuccess = { fetchedFriends ->
                friends = fetchedFriends
                isLoading = false
            },
            onError = { errorMessage ->
                error = errorMessage
                isLoading = false
            }
        )
    }
    
    // Friends content
    Div(
        attrs = {
            style {
                property("flex-grow", "1")
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                padding(16.px)
                height(100.percent)
                overflow("hidden")
            }
        }
    ) {
        // Loading state
        if (isLoading) {
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.Center)
                        alignItems(AlignItems.Center)
                        property("flex-grow", "1")
                    }
                }
            ) {
                Translated("Loading friends...")
            }
        }
        // Error state
        else if (error != null) {
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.Center)
                        alignItems(AlignItems.Center)
                        property("flex-grow", "1")
                        color(rgba(255, 100, 100, 0.9))
                    }
                }
            ) {
                Text("${str("Error:")} $error")
            }
        }
        // Empty state
        else if (friends.isEmpty()) {
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.Center)
                        alignItems(AlignItems.Center)
                        property("flex-grow", "1")
                    }
                }
            ) {
                P(
                    attrs = {
                        style {
                            color(rgba(255, 255, 255, 0.7))
                            property("text-align", "center")
                        }
                    }
                ) {
                    Translated("You haven't made any friends yet!")
                }
            }
        }
        // Friends list
        else {
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        gap(12.px)
                        property("flex-grow", "1")
                        overflowY("auto")
                    }
                }
            ) {
                friends.forEach { friend ->
                    FriendListItem(
                        friend = friend,
                        onJumpToFriend = { friendId, worldId, position ->
                            jumpToFriend(
                                router = router,
                                friendId = friendId,
                                worldId = worldId,
                                position = position,
                                onError = { errorMsg ->
                                    error = errorMsg
                                }
                            )
                        },
                        onRemoveFriend = { friendId ->
                            removeFriend(
                                friendId = friendId,
                                onSuccess = {
                                    // Remove the friend from the list
                                    friends = friends.filter { it.friendId != friendId }
                                },
                                onError = { errorMsg ->
                                    error = errorMsg
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * A list item for displaying a friend
 * 
 * @param friend The friend to display
 * @param onJumpToFriend Callback when the Jump to Friend button is clicked
 * @param onRemoveFriend Callback when the Remove Friend button is clicked
 */
@OptIn(ExperimentalTime::class)
@Composable
fun FriendListItem(
    friend: Friend,
    onJumpToFriend: (String, String, Position?) -> Unit,
    onRemoveFriend: (String) -> Unit
) {
    // State to control the visibility of the confirmation dialog
    var showConfirmDialog by remember { mutableStateOf(false) }
    
    Div(
        attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                padding(12.px)
                backgroundColor(rgba(30, 30, 30, 0.6))
                borderRadius(8.px)
                property("border", "1px solid rgba(100, 100, 100, 0.3)")
            }
        }
    ) {
        // Friend name
        Div(
            attrs = {
                style {
                    display(DisplayStyle.Flex)
                    justifyContent(JustifyContent.SpaceBetween)
                    alignItems(AlignItems.Center)
                    marginBottom(8.px)
                }
            }
        ) {
            Span(
                attrs = {
                    style {
                        fontSize(16.px)
                        fontWeight("bold")
                        color(rgba(255, 255, 255, 0.9))
                    }
                }
            ) {
                Text(friend.friendUsername)
            }
            
            // Last active time
            Span(
                attrs = {
                    style {
                        fontSize(12.px)
                        color(rgba(200, 200, 200, 0.7))
                    }
                }
            ) {
                Text(formatLastActive(friend.lastActive))
            }
        }
        
        // Last world
        if (friend.lastWorldName.isNotEmpty()) {
            Div(
                attrs = {
                    style {
                        fontSize(14.px)
                        color(rgba(200, 200, 200, 0.8))
                        marginBottom(12.px)
                    }
                }
            ) {
                Text("${str("Last seen in:")} ${friend.lastWorldName}")
            }
        }
        
        // Buttons
        Div(
            attrs = {
                style {
                    display(DisplayStyle.Flex)
                    gap(8.px)
                }
            }
        ) {
            // Jump to friend button (only enabled if they have a world and position)
            Button(
                attrs = {
                    style {
                        padding(8.px, 12.px)
                        backgroundColor(rgba(60, 120, 200, 0.8))
                        color(rgba(255, 255, 255, 0.9))
                        property("border", "none")
                        borderRadius(4.px)
                        property("cursor", "pointer")
                    }
                    if (friend.lastWorldId.isEmpty()) {
                        attr("disabled", "")
                    }
                    onClick {
                        if (friend.lastWorldId.isNotEmpty()) {
                            onJumpToFriend(friend.friendId, friend.lastWorldId, friend.lastPosition)
                        }
                    }
                }
            ) {
                Translated("Jump to Friend")
            }
            
            // Remove friend button
            Button(
                attrs = {
                    style {
                        padding(8.px, 12.px)
                        backgroundColor(rgba(200, 60, 60, 0.8))
                        color(rgba(255, 255, 255, 0.9))
                        property("border", "none")
                        borderRadius(4.px)
                        property("cursor", "pointer")
                    }
                    onClick {
                        // Show confirmation dialog instead of immediately removing
                        showConfirmDialog = true
                    }
                }
            ) {
                Translated("Remove")
            }
            
            // Confirmation dialog
            if (showConfirmDialog) {
                BasicConfirmDialog(
                    message = "${str("Are you sure you want to remove")} ${friend.friendUsername} ${str("from your friends list?")}",
                    onConfirm = {
                        // Only remove friend if user confirms
                        onRemoveFriend(friend.friendId)
                        showConfirmDialog = false
                    },
                    onCancel = {
                        // Close dialog without removing friend
                        showConfirmDialog = false
                    }
                )
            }
        }
    }
}

/**
 * Format the last active time as a human-readable string
 * 
 * @param lastActive The last active time
 * @return A human-readable string
 */
@OptIn(ExperimentalTime::class)
private fun formatLastActive(lastActive: kotlin.time.Instant): String {
    val now = kotlin.time.Clock.System.now()
    val diff = now - lastActive
    
    return when {
        diff < 1.minutes -> "Just now"
        diff < 1.hours -> "${diff.inWholeMinutes} minutes ago"
        diff < 1.days -> "${diff.inWholeHours} hours ago"
        diff < 7.days -> "${diff.inWholeDays} days ago"
        else -> "Long time ago"
    }
}

/**
 * Fetch the list of friends
 * 
 * @param onSuccess Callback when the friends are successfully fetched
 * @param onError Callback when there's an error fetching the friends
 */
private suspend fun fetchFriends(
    onSuccess: (List<Friend>) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val friends = FriendApi.getFriends()
        onSuccess(friends.toList())
    } catch (e: Exception) {
        console.error("Error fetching friends", e)
        onError("Failed to fetch friends: ${e.message}")
    }
}

/**
 * Remove a friend
 * 
 * @param friendId The ID of the friend to remove
 * @param onSuccess Callback when the friend is successfully removed
 * @param onError Callback when there's an error removing the friend
 */
private fun removeFriend(
    friendId: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    CoroutineScope(Dispatchers.Default).launch {
        try {
            val success = FriendApi.removeFriend(friendId)
            
            if (success) {
                onSuccess()
            } else {
                onError("Failed to remove friend")
            }
        } catch (e: Exception) {
            console.error("Error removing friend", e)
            onError("Error: ${e.message}")
        }
    }
}

/**
 * Jump to a friend's location
 * 
 * @param router The router for navigation
 * @param friendId The ID of the friend
 * @param worldId The ID of the world
 * @param position The position in the world
 * @param onError Callback when there's an error jumping to the friend
 */
private fun jumpToFriend(
    router: Router,
    friendId: String,
    worldId: String,
    position: Position?,
    onError: (String) -> Unit
) {
    CoroutineScope(Dispatchers.Default).launch {
        try {
            // Get the world details
            val world = WorldApi.getWorld(worldId)

            // Navigate to the world
            router.navigate("/world/${world.key}")

            // Set the player position if available
            if (position != null) {
                // Wait for the world to load
                WorldLoader.onWorldLoaded {
                    // Get the player controller and set the target position
                    val playerController = SceneManager.getPlayerController()
                    // Convert Position to Vector3
                    val vector3Position = Vector3(position.x, position.y, position.z)
                    playerController?.setTargetPosition(vector3Position)
                }
            }
        } catch (e: Exception) {
            console.error("Error jumping to friend", e)
            onError("Error: ${e.message}")
        }
    }
}
