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
import androidx.compose.runtime.DisposableEffect
import lib.Vector3
import main.api.FriendApi
import main.api.WorldApi
import main.api.WebSocketApi
import main.api.ListenerToken
import main.api.addUserJoinListener
import main.api.addUserLeaveListener
import main.api.addTeleportPlayerListener
import main.api.WebSocketApiGlobal
import main.api.addFriendStatusChangedListener
import main.api.addFriendRequestAcceptedListener
import main.components.world.WorldLoader
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
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import Translations.str
import kotlinx.coroutines.delay
import main.theme.Styles
import main.theme.FriendsStyles
import org.jetbrains.compose.web.css.Style
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.border
import org.jetbrains.compose.web.css.position
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes


/**
 * Content for the friends panel/dialog.
 * This component contains the shared UI elements for both mobile and desktop views.
 */

@Composable
fun FriendsContent() {
    // State for friends list
    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var onlineMap by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    
    // Coroutine scope for async operations
    val coroutineScope = remember { CoroutineScope(Dispatchers.Default) }
    
    // Router for navigation
    val router = Router.current

    // Toast-style feedback state (similar to InventoryTray)
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isToastError by remember { mutableStateOf(false) }

    // Helper to show transient feedback/toast
    fun showFeedback(message: String, errorToast: Boolean = false) {
        console.log("[FEEDBACK] $message")
        feedbackMessage = message
        isToastError = errorToast
    }

    LaunchedEffect(feedbackMessage) {
        delay(3000)
        console.log("[FEEDBACK] HIDE MESSAGE")
        feedbackMessage = null
    }

    // Fetch friends when the component is first rendered
    LaunchedEffect(Unit) {
        fetchFriends(
            onSuccess = { fetchedFriends ->
                friends = fetchedFriends
                isLoading = false
                // Fetch online status for these friends
                coroutineScope.launch {
                    try {
                        val ids = fetchedFriends.map { it.friendId }
                        onlineMap = FriendApi.getFriendsOnline(ids)
                    } catch (e: Throwable) {
                        console.error("Error fetching friends online", e)
                    }
                }
            },
            onError = { errorMessage ->
                error = errorMessage
                isLoading = false
            }
        )
    }

    // Register WebSocket listeners to update friends in realtime
    var worldListenerTokens by remember { mutableStateOf<List<ListenerToken>>(emptyList()) }
    var globalListenerTokens by remember { mutableStateOf<List<ListenerToken>>(emptyList()) }

    // Helper to refresh the friends list and online map
    fun refreshFriendsList() {
        coroutineScope.launch {
            try {
                fetchFriends(
                    onSuccess = { fetchedFriends ->
                        friends = fetchedFriends
                        // Also refresh online states
                        coroutineScope.launch {
                            try {
                                val ids = fetchedFriends.map { it.friendId }
                                onlineMap = FriendApi.getFriendsOnline(ids)
                            } catch (e: Throwable) {
                                console.error("[Friends] Error refreshing online map", e)
                            }
                        }
                    },
                    onError = { err ->
                        error = err
                    }
                )
            } catch (e: Throwable) {
                console.error("[Friends] Error refreshing friends list", e)
            }
        }
    }

    DisposableEffect(Unit) {
        // Ensure global socket is connected for cross-world friend updates
        try { WebSocketApiGlobal.connectGlobal() } catch (e: Throwable) {
            e.printStackTrace()
        }

        // Add listeners once
        val worldTokens = mutableListOf<ListenerToken>()
        val globalTokens = mutableListOf<ListenerToken>()

        // Listen for global friend status changes
        globalTokens += WebSocketApiGlobal.addFriendStatusChangedListener { data ->
            try {
                if (friends.any { it.friendId == data.userId }) {
                    // Update online map immediately
                    onlineMap = onlineMap.toMutableMap().apply { this[data.userId] = data.isOnline }
                    // Refresh to get latest world/position details
                    refreshFriendsList()
                }
            } catch (e: Throwable) {
                console.error("[Friends] Error handling FRIEND_STATUS_CHANGED", e)
            }
        }

        // Refresh friends list immediately when a friend request is accepted
        globalTokens += WebSocketApiGlobal.addFriendRequestAcceptedListener {
            try {
                refreshFriendsList()
            } catch (e: Throwable) {
                console.error("[Friends] Error handling FRIEND_REQUEST_ACCEPTED", e)
            }
        }

        // When any user joins a world
        worldTokens += WebSocketApi.addUserJoinListener { data ->
            try {
                if (friends.any { it.friendId == data.userId }) {
                    // Optimistically mark online
                    onlineMap = onlineMap.toMutableMap().apply { this[data.userId] = true }
                    refreshFriendsList()
                }
            } catch (e: Throwable) {
                console.error("[Friends] Error handling USER_JOIN for friends list", e)
            }
        }

        // When any user leaves a world
        worldTokens += WebSocketApi.addUserLeaveListener { data ->
            try {
                if (friends.any { it.friendId == data.userId }) {
                    // Optimistically mark offline
                    onlineMap = onlineMap.toMutableMap().apply { this[data.userId] = false }
                    refreshFriendsList()
                }
            } catch (e: Throwable) {
                console.error("[Friends] Error handling USER_LEAVE for friends list", e)
            }
        }

        // When any user teleports to another world (world change)
        worldTokens += WebSocketApi.addTeleportPlayerListener(router) { payload ->
            try {
                val userId = payload["userId"]
                if (userId != null && friends.any { it.friendId == userId }) {
                    refreshFriendsList()
                }
            } catch (e: Throwable) {
                console.error("[Friends] Error handling TELEPORT_PLAYER for friends list", e)
            }
        }

        worldListenerTokens = worldTokens
        globalListenerTokens = globalTokens

        onDispose {
            try {
                worldListenerTokens.forEach { token: ListenerToken ->
                    WebSocketApi.removeListener(token)
                }
                globalListenerTokens.forEach { token: ListenerToken ->
                    WebSocketApiGlobal.removeListener(token)
                }
            } catch (e: Throwable) {
                console.error("[Friends] Error cleaning up WebSocket listeners", e)
            }
        }
    }

    // Periodic refresh while component is active (cross-world updates)
    LaunchedEffect(Unit) {
        while (true) {
            try {
                delay(5000L)
                refreshFriendsList()
            } catch (e: Throwable) {
                console.error("[Friends] Polling refresh failed", e)
                if (e is CancellationException) {
                    break
                }
            }
        }
    }
    
    Style(FriendsStyles)
    
    // Friends content
    Div(
        attrs = {
            classes(main.theme.FriendsStyles.friendsContentRoot)
        }
    ) {
        // Toast feedback message (non-blocking)
        feedbackMessage?.let { message ->
            Div(
                attrs = {
                    classes(
                        Styles.toastMessage,
                        if (isToastError) {
                            Styles.toastError
                        } else {
                            Styles.toastSuccess
                        }
                    )
                }
            ) {
                Text(message)
            }
        }

        // Loading state
        if (isLoading) {
            Div(
                attrs = {
                    classes(FriendsStyles.centeredGrowContainer)
                }
            ) {
                Translated("Loading friends...")
            }
        }
        // Error state
        else if (error != null) {
            Div(
                attrs = {
                    classes(FriendsStyles.errorContainer)
                }
            ) {
                Text("${str("Error")}: $error")
            }
        }
        // Empty state
        else if (friends.isEmpty()) {
            Div(
                attrs = {
                    classes(FriendsStyles.centeredGrowContainer)
                }
            ) {
                P(
                    attrs = {
                        classes(FriendsStyles.emptyText)
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
                    classes(FriendsStyles.friendsListContainer)
                }
            ) {
                friends.forEach { friend ->
                    FriendListItem(
                        friend = friend,
                        isOnline = onlineMap[friend.friendId] == true,
                        onJumpToFriend = { friendId, worldId, position ->
                            jumpToFriend(
                                router = router,
                                friendId = friendId,
                                worldId = worldId,
                                position = position,
                                onError = { errorMsg ->
                                    console.log("[FEEDBACK] errorMsg=$errorMsg")
                                    showFeedback(errorMsg, true)
                                }
                            )
                        },
                        onError = { errorMsg ->
                            showFeedback(errorMsg, true)
                        },
                        onRemoveFriend = { friendId ->
                            removeFriend(
                                friendId = friendId,
                                onSuccess = {
                                    // Remove the friend from the list
                                    friends = friends.filter { it.friendId != friendId }
                                },
                                onError = { errorMsg ->
                                    showFeedback(errorMsg, true)
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

@Composable
fun FriendListItem(
    friend: Friend,
    isOnline: Boolean,
    onJumpToFriend: (String, String, Position?) -> Unit,
    onError: (String) -> Unit,
    onRemoveFriend: (String) -> Unit
) {
    // State to control the visibility of the confirmation dialog
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showSendDialog by remember { mutableStateOf(false) }
    
    Div(
        attrs = {
            classes(FriendsStyles.friendListItem)
        }
    ) {
        // Friend name
        Div(
            attrs = {
                classes(FriendsStyles.friendHeader)
            }
        ) {
            Span(
                attrs = {
                    classes(FriendsStyles.friendName)
                }
            ) {
                // Online indicator dot
                Span(
                    attrs = {
                        classes(
                            FriendsStyles.onlineDotBase,
                            if (isOnline) FriendsStyles.onlineDotOnline else FriendsStyles.onlineDotOffline,
                        )
                    }
                ) {}
                Text(friend.friendUsername)
            }
            
            // Last active time
            Span(
                attrs = {
                    classes(FriendsStyles.lastActiveText)
                }
            ) {
                Text(formatLastActive(friend.lastActive))
            }
        }
        
        // Current world if online
        if (isOnline && friend.lastWorldName.isNotEmpty()) {
            Div(
                attrs = {
                    classes(FriendsStyles.currentWorldOnline)
                }
            ) {
                Text("${translated("Currently at")} ${friend.lastWorldName}")
            }
        }

        // Last world when offline
        if (!isOnline && friend.lastWorldName.isNotEmpty()) {
            Div(
                attrs = {
                    classes(FriendsStyles.lastWorldOffline)
                }
            ) {
                Text("${str("Last seen in:")} ${friend.lastWorldName}")
            }
        }
        
        // Buttons
        Div(
            attrs = {
                classes(FriendsStyles.buttonsRow)
            }
        ) {
            // Jump to friend button (only enabled if they have a world and position)
            Button(
                attrs = {
                    classes(FriendsStyles.buttonJump)
                    onClick {
                        if (friend.lastWorldId.isNotEmpty()) {
                            onJumpToFriend(friend.friendId, friend.lastWorldId, friend.lastPosition)
                        } else {
                            console.log("[FRIEND] lastWorldId is null")
                            onError(str("Unable to jump to your friend right now"))
                        }
                    }
                }
            ) {
                Translated("Jump to Friend")
            }
            
            // Send VND button
            Button(
                attrs = {
                    classes(FriendsStyles.buttonSend)
                    onClick {
                        showSendDialog = true
                    }
                }
            ) {
                Translated("Send VND")
            }
            
            // Remove friend button
            Button(
                attrs = {
                    classes(FriendsStyles.buttonRemove)
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
        
        // Send VND dialog rendering
        if (showSendDialog) {
            SendVndDialog(
                friendUserId = friend.friendId,
                friendUsername = friend.friendUsername,
                onClose = { showSendDialog = false },
                onSent = { showSendDialog = false },
            )
        }
    }
}

/**
 * Format the last active time as a human-readable string
 * 
 * @param lastActive The last active time
 * @return A human-readable string
 */

@Composable
private fun formatLastActive(lastActive: kotlin.time.Instant): String {
    val now = kotlin.time.Clock.System.now()
    val diff = now - lastActive
    
    return when {
        diff < 1.minutes -> translated("Just now")
        diff < 1.hours -> "${diff.inWholeMinutes} ${translated("minutes ago")}"
        diff < 1.days -> "${diff.inWholeHours} ${translated("hours ago")}"
        diff < 7.days -> "${diff.inWholeDays} ${translated("days ago")}"
        else -> translated("A long time ago")
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
    } catch (e: Throwable) {
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
        } catch (e: Throwable) {
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
            // Get the target world details
            val targetWorld = WorldApi.getWorld(worldId)

            // Determine if we are already in the same world
            val currentWorld = WorldLoader.getActiveWorld()
            val sameWorld = currentWorld?.key == targetWorld.key

            if (sameWorld) {
                // Already in target world: relocate immediately if we have a position
                if (position != null) {
                    val playerController = SceneManager.getPlayerController()
                    val vector3Position = Vector3(position.x, position.y, position.z)
                    playerController?.teleportTo(vector3Position)
                } else {
                    // Provide user feedback when we can't teleport within the same world
                    onError(str("Unable to jump to your friend right now"))
                    console.warn("[Friends] No position available to jump within the current world")
                }
            } else {
                // Different world: set relocation callback BEFORE navigating
                if (position != null) {
                    WorldLoader.onWorldLoaded {
                        // Wait until PlayerController and its player character are ready
                        CoroutineScope(Dispatchers.Default).launch {
                            try {
                                var attempts = 0
                                val maxAttempts = 80 // ~4s at 50ms intervals
                                while (attempts < maxAttempts) {
                                    val pc = SceneManager.getPlayerController()
                                    val handle = pc?.getPlayerHandle()
                                    if (pc != null && handle != null) {
                                        val vector3Position = Vector3(position.x, position.y, position.z)
                                        pc.teleportTo(vector3Position)
                                        break
                                    }
                                    delay(50)
                                    attempts++
                                }
                                if (attempts >= maxAttempts) {
                                    console.warn("[Friends] Timed out waiting for player to initialize; jump-to-friend not applied")
                                }
                            } catch (e: Throwable) {
                                onError(str("Unable to determine your friend's exact location"))
                                console.error("[Friends] Error while waiting for player to initialize for jump-to-friend", e)
                            }
                        }
                    }
                } else {
                    // Provide user feedback when we can't place you precisely in the target world
                    onError(str("Unable to determine your friend's exact location"))
                }
                // Navigate to the world
                router.navigate("/${targetWorld.url}")
            }
        } catch (e: Throwable) {
            console.error("Error jumping to friend", e)
            onError("Error: ${e.message}")
        }
    }
}
