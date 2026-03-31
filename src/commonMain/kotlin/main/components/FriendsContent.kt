package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.DisposableEffect
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
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import Translations.str
import androidx.compose.runtime.key
import kotlinx.coroutines.delay
import main.theme.Styles
import main.theme.FriendsStyles
import org.jetbrains.compose.web.css.Style
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
        // Banner feedback/error message
        val currentBannerMessage = feedbackMessage ?: error?.let { "${str("Error")}: $it" }
        val currentBannerIsError = feedbackMessage?.let { isToastError } ?: (error != null)

        if (currentBannerMessage != null) {
            key(currentBannerMessage) {
                Div(
                    attrs = {
                        classes(if (currentBannerIsError) Styles.formErrorBanner else Styles.formSuccessBanner)
                    }
                ) {
                    Text(if (currentBannerIsError) "⚠️ " else "✅ ")
                    Text(currentBannerMessage)
                }
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
                    key(friend.friendId) {
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
                            onSuccess = { successMsg ->
                                showFeedback(successMsg)
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
                            },
                            onNoteUpdated = { updatedNote ->
                                // Update locally immediately (optimistic)
                                friends = friends.map { f ->
                                    if (f.friendId == friend.friendId) f.copy(note = updatedNote) else f
                                }
                                refreshFriendsList()
                            }
                        )
                    }
                }
            }
        }
    }
}

