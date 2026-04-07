package main.components

import androidx.compose.runtime.*
import app.softwork.routingcompose.Router
import kotlinx.coroutines.*
import lib.Vector3
import main.api.*
import main.components.world.WorldLoader
import main.theme.FriendsStyles
import main.theme.Styles
import models.Friend
import models.Position
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.*
import Translations.str
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

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
    onSuccess: (String) -> Unit,
    onRemoveFriend: (String) -> Unit,
    onNoteUpdated: (String) -> Unit
) {
    // State to control the visibility of the confirmation dialog
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showSendDialog by remember { mutableStateOf(false) }
    var friendUser by remember { mutableStateOf<models.User?>(null) }
    
    val coroutineScope = rememberCoroutineScope()
    var editedNote by remember(friend.note) { mutableStateOf(friend.note) }
    var isUpdatingNote by remember { mutableStateOf(false) }
    val isNoteChanged = editedNote != friend.note

    LaunchedEffect(friend.friendId) {
        try {
            friendUser = UserApi.getUserById(friend.friendId)
        } catch (t: Throwable) {
            console.error("[Friends] Failed to load friend user: ${t.message}")
        }
    }
    
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
        
        // Friend status and status bars (if present)
        friendUser?.let { fu ->
            if (fu.status.isNotBlank()) {
                Div(
                    attrs = {
                        classes(Styles.userStatusText, FriendsStyles.statusText)
                    }
                ) {
                    Text(fu.status)
                }
            }
            Div {
                UserStatusBars(
                    health = fu.health,
                    mind = fu.mind,
                    spirit = fu.spirit,
                    readOnly = true,
                    size = 18,
                )
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
        
        // Friend Note
        Div(
            attrs = {
                classes(FriendsStyles.friendNoteContainer)
            }
        ) {
            DialogInput(
                value = editedNote,
                onInput = { editedNote = it },
                placeholder = str("Add a note about this friend..."),
                multiline = true,
                attrs = {
                    classes(FriendsStyles.friendNoteTextArea)
                }
            )
            
            if (isNoteChanged) {
                Div(
                    attrs = {
                        classes(FriendsStyles.buttonsRow)
                    }
                ) {
                    DialogButton(
                        onClick = {
                            coroutineScope.launch {
                                isUpdatingNote = true
                                try {
                                    val success = FriendApi.updateNote(friend.friendId, editedNote)
                                    if (success) {
                                        onNoteUpdated(editedNote)
                                    } else {
                                        onError(str("Failed to update note"))
                                    }
                                } catch (e: Throwable) {
                                    onError(str("Error updating note"))
                                } finally {
                                    isUpdatingNote = false
                                }
                            }
                        },
                        style = DialogButtonStyle.Alt,
                        isDisabled = isUpdatingNote,
                        attrs = {
                            // No custom classes needed here
                        }
                    ) {
                        Translated("Update")
                    }
                    
                    DialogButton(
                        onClick = {
                            editedNote = friend.note
                        },
                        style = DialogButtonStyle.Danger,
                        isDisabled = isUpdatingNote,
                        attrs = {
                            // No custom classes needed here
                        }
                    ) {
                        Translated("Cancel")
                    }
                }
            }
        }

        if(!isNoteChanged) {
            // Buttons
            Div(
                attrs = {
                    classes(FriendsStyles.buttonsRow)
                }
            ) {
                // Jump to friend button (only enabled if they have a world and position)
                DialogButton(
                    onClick = {
                        if (friend.lastWorldId.isNotEmpty()) {
                            onJumpToFriend(friend.friendId, friend.lastWorldId, friend.lastPosition)
                        } else {
                            console.log("[FRIEND] lastWorldId is null")
                            onError(str("Unable to jump to your friend right now"))
                        }
                    },
                    style = DialogButtonStyle.Primary,
                    attrs = {
                        // No custom classes needed here
                    }
                ) {
                    Translated("Jump to Friend")
                }

                // Invite to me button
                DialogButton(
                    onClick = {
                        inviteToFriend(
                            friendId = friend.friendId,
                            onError = onError,
                            onSuccess = onSuccess
                        )
                    },
                    style = DialogButtonStyle.Secondary,
                    attrs = {
                        // No custom classes needed here
                    }
                ) {
                    Translated("Invite to me")
                }

                // Send VND button
                DialogButton(
                    onClick = {
                        showSendDialog = true
                    },
                    style = DialogButtonStyle.Alt,
                    attrs = {
                        // No custom classes needed here
                    }
                ) {
                    Translated("Send VND")
                }

                // Remove friend button
                DialogButton(
                    onClick = {
                        // Show confirmation dialog instead of immediately removing
                        showConfirmDialog = true
                    },
                    style = DialogButtonStyle.Danger,
                    attrs = {
                        // No custom classes needed here
                    }
                ) {
                    Translated("Remove")
                }
            }
            
            // Confirmation dialog
            if (showConfirmDialog) {
                BasicConfirmDialog(
                    message = "${str("Are you sure you want to remove")} ${friend.friendUsername} ${str("from your friends list?")}",
                    positiveStyle = DialogButtonStyle.Danger,
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
internal fun formatLastActive(lastActive: kotlin.time.Instant): String {
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
internal suspend fun fetchFriends(
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
internal fun removeFriend(
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
internal fun jumpToFriend(
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

/**
 * Invite a friend to join the player's current world and position
 */
internal fun inviteToFriend(
    friendId: String,
    onError: (String) -> Unit,
    onSuccess: (String) -> Unit
) {
    CoroutineScope(Dispatchers.Default).launch {
        try {
            val pc = SceneManager.getPlayerController()
            val handle = pc?.getPlayerHandle()
            val world = WorldLoader.getActiveWorld()

            if (pc != null && handle != null && world != null) {
                val pos = handle.position
                val success = NotificationApi.sendWorldInvitation(
                    friendId = friendId,
                    worldId = world.key,
                    x = pos.x,
                    y = pos.y,
                    z = pos.z
                )
                if (success) {
                    onSuccess(str("Invitation sent!"))
                } else {
                    onError(str("Failed to send invitation"))
                }
            } else {
                onError(str("Unable to invite friend right now"))
            }
        } catch (e: Throwable) {
            console.error("Error inviting friend", e)
            onError("Error: ${e.message}")
        }
    }
}
