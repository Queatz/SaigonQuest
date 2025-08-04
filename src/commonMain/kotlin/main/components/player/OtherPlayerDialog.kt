package main.components.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.browser.document
import main.theme.Styles
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/**
 * Dialog shown when clicking on another player
 * 
 * @param username The username of the player
 * @param isPlayer Whether the user is a player (true) or visitor (false)
 * @param onAddFriend Callback when the Add Friend button is clicked
 * @param onTrade Callback when the Trade button is clicked
 * @param onClose Callback when the Close button is clicked
 */
@Composable
fun OtherPlayerDialog(
    username: String,
    isPlayer: Boolean,
    onAddFriend: () -> Unit,
    onTrade: () -> Unit,
    onClose: () -> Unit
) {
    // Dialog overlay
    Div({
        classes(Styles.overlayBase)
    }) {
        // Dialog container
        Div({
            classes(Styles.baseDialogContainer)
        }) {
            // Header
            Div({
                classes(Styles.baseDialogHeader)
            }) {
                H3({
                    classes(Styles.baseDialogTitle)
                }) {
                    Text(username)
                }
                
                Button({
                    classes(Styles.baseDialogCloseButton)
                    onClick { onClose() }
                }) {
                    Text("×")
                }
            }
            
            // Content
            Div({
                classes(Styles.baseDialogContent)
            }) {
                // Account type
                P({
                    style {
                        property("margin", "0 0 16px 0")
                    }
                }) {
                    Text(if (isPlayer) "Player Account" else "Visitor Account")
                }

                if (isPlayer) {
                    // Buttons
                    Div({
                        style {
                            property("display", "flex")
                            property("flex-direction", "column")
                            property("gap", "8px")
                        }
                    }) {
                        // Add Friend button
                        Button({
                            classes(Styles.baseDialogButtonPrimary)
                            onClick { onAddFriend() }
                        }) {
                            Text("Add Friend")
                        }

                        // Trade button
                        Button({
                            classes(Styles.baseDialogButtonPrimary)
                            onClick { onTrade() }
                        }) {
                            Text("Trade")
                        }
                    }
                }
            }

            // Footer
            Div({
                classes(Styles.baseDialogFooter)
            }) {
                // Close button
                Button({
                    classes(Styles.baseDialogButtonSecondary)
                    onClick { onClose() }
                }) {
                    Text("Close")
                }
            }
        }
    }
}
