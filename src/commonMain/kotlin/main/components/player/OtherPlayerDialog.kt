package main.components.player

import androidx.compose.runtime.Composable
import main.components.BaseDialog
import main.components.DialogButton
import main.theme.Styles
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/**
 * Dialog shown when clicking on another player
 * 
 * @param username The username of the player
 * @param isPlayer Whether the other user is a player (true) or visitor (false)
 * @param isCurrentUserPlayer Whether the current user is a player (true) or visitor (false)
 * @param friendshipStatus The friendship status with this player (null if unknown)
 * @param isLoading Whether a friend request is in progress
 * @param errorMessage Error message to display, if any
 * @param onAddFriend Callback when the Add Friend button is clicked
 * @param onRemoveFriend Callback when the Remove Friend button is clicked
 * @param onTrade Callback when the Trade button is clicked
 * @param onClose Callback when the Close button is clicked
 */
@Composable
fun OtherPlayerDialog(
    username: String,
    isPlayer: Boolean,
    isCurrentUserPlayer: Boolean,
    friendshipStatus: FriendshipStatus = FriendshipStatus.UNKNOWN,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onAddFriend: () -> Unit,
    onRemoveFriend: () -> Unit,
    onTrade: () -> Unit,
    onClose: () -> Unit
) {
    BaseDialog(
        onClose = onClose,
        title = username,
        content = {
            // Account type
            P({
                style {
                    property("margin", "0 0 16px 0")
                }
            }) {
                Text(if (isPlayer) "Player Account" else "Visitor Account")
            }

            if (isPlayer && isCurrentUserPlayer) {
                // Error message if any
                if (errorMessage != null) {
                    P({
                        style {
                            color(org.jetbrains.compose.web.css.rgba(255, 85, 85, 1.0))
                            property("margin", "0 0 16px 0")
                        }
                    }) {
                        Text(errorMessage)
                    }
                }
                
                // Buttons for player interactions
                Div({
                    style {
                        property("display", "flex")
                        property("flex-direction", "column")
                        property("gap", "8px")
                    }
                }) {
                    // Friend button (changes based on friendship status)
                    when (friendshipStatus) {
                        FriendshipStatus.UNKNOWN -> {
                            DialogButton(
                                onClick = {},
                                isPrimary = true
                            ) {
                                Text("Checking friendship status...")
                            }
                        }
                        FriendshipStatus.NOT_FRIENDS -> {
                            DialogButton(
                                onClick = { if (!isLoading) onAddFriend() },
                                isPrimary = true
                            ) {
                                if (isLoading) {
                                    Text("Sending request...")
                                } else {
                                    Text("Add Friend")
                                }
                            }
                        }
                        FriendshipStatus.REQUEST_SENT -> {
                            DialogButton(
                                onClick = {},
                                isPrimary = true
                            ) {
                                Text("Friend request sent")
                            }
                        }
                        FriendshipStatus.REQUEST_RECEIVED -> {
                            DialogButton(
                                onClick = { if (!isLoading) onAddFriend() },
                                isPrimary = true
                            ) {
                                if (isLoading) {
                                    Text("Accepting request...")
                                } else {
                                    Text("Accept Friend Request")
                                }
                            }
                        }
                        FriendshipStatus.FRIENDS -> {
                            DialogButton(
                                onClick = { if (!isLoading) onRemoveFriend() },
                                isPrimary = false
                            ) {
                                if (isLoading) {
                                    Text("Removing friend...")
                                } else {
                                    Text("Remove Friend")
                                }
                            }
                        }
                    }

                    // Trade button
                    DialogButton(
                        onClick = { onTrade() },
                        isPrimary = true
                    ) {
                        Text("Trade")
                    }
                }
            }
        },
        actions = {
            // Close button in footer
            DialogButton(
                onClick = { onClose() },
                isPrimary = false
            ) {
                Text("Close")
            }
        }
    )
}
