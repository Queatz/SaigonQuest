package main.components

import androidx.compose.runtime.Composable
import main.api.UserApi
import main.theme.OverlayStyles
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * A component that displays a loading screen or error message.
 * 
 * @param isLoaded Whether the content is loaded
 * @param loadingFailed Whether loading failed
 * @param errorMessage The error message to display if loading failed
 * @param showWorldEditPanel Whether to show the world edit panel
 * @param showLoginDialog Whether to show the login dialog
 * @param onShowWorldEditPanel Callback to show the world edit panel
 * @param onShowLoginDialog Callback to show the login dialog
 */
@Composable
fun LoadingScreen(
    showGoHome: Boolean,
    isLoaded: Boolean,
    loadingFailed: Boolean,
    errorMessage: String,
    showWorldEditPanel: Boolean,
    showLoginDialog: Boolean,
    onShowWorldEditPanel: () -> Unit,
    onShowLoginDialog: () -> Unit,
    onNavigateHome: () -> Unit,
    showClaimOption: Boolean,
    isNoAccess: Boolean,
    onRetry: () -> Unit,
    worldName: String? = null,
) {
    if (!isLoaded && !showWorldEditPanel && !showLoginDialog) {
        Div(
            attrs = {
                classes(OverlayStyles.loadingScreenContainer)
            }
        ) {
            LoadingBackground()

            if (loadingFailed) {
                // Error message when world loading fails
                H2 {
                    Text(errorMessage)
                }
                
                Div(
                    attrs = {
                        classes(OverlayStyles.loadingScreenButtonContainer)
                    }
                ) {
                    if (showClaimOption) {
                        val isPlayer = UserApi.isLoggedInAsPlayer()

                        // Create this world or Login button
                        DialogButton(
                            style = DialogButtonStyle.Primary,
                            onClick = {
                                // Only allow players to open the world editor
                                if (isPlayer) {
                                    onShowWorldEditPanel()
                                } else {
                                    onShowLoginDialog()
                                }
                            }
                        ) {
                            // Show different text based on login status
                            if (isPlayer) {
                                Translated("Claim this world")
                            } else {
                                Translated("Login and claim this world")
                            }
                        }
                    } else {
                        // Show retry button for non-404 and non-403 errors
                        if (!isNoAccess) {
                            DialogButton(
                                style = DialogButtonStyle.Primary,
                                onClick = {
                                    onRetry()
                                }
                            ) {
                                Translated("Retry")
                            }
                        }
                    }

                    // Go back button
                    if (showGoHome) {
                        DialogButton(
                            style = DialogButtonStyle.Secondary,
                            onClick = {
                                onNavigateHome()
                            }
                        ) {
                            Translated("Go home")
                        }
                    }
                }
            } else {
                // Normal loading message
                Span {
                    val displayedWorldName = if (worldName.isNullOrBlank()) {
                        "Saigon Quest"
                    } else {
                        worldName
                    }
                    Text(translated("Entering %s...").replace("%s", displayedWorldName).replace("...", ""))
                    AnimatedDots()
                }
            }
        }
    }
}
