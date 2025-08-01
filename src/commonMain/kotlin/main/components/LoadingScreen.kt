package main.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import app.softwork.routingcompose.Router
import main.api.UserApi
import main.theme.Styles

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
    isLoaded: Boolean,
    loadingFailed: Boolean,
    errorMessage: String,
    showWorldEditPanel: Boolean,
    showLoginDialog: Boolean,
    onShowWorldEditPanel: () -> Unit,
    onShowLoginDialog: () -> Unit,
    onNavigateHome: () -> Unit
) {
    if (!isLoaded && !showWorldEditPanel && !showLoginDialog) {
        Div(
            attrs = {
                classes(Styles.loadingScreenContainer)
            }
        ) {
            if (loadingFailed) {
                // Error message when world loading fails
                H2 {
                    Text(errorMessage)
                }
                
                Div(
                    attrs = {
                        classes(Styles.loadingScreenButtonContainer)
                    }
                ) {
                    // Create this world or Login button
                    Button(
                        attrs = {
                            classes(Styles.loadingScreenPrimaryButton)
                            onClick { 
                                // Check if user is logged in
                                if (UserApi.getToken() != null) {
                                    // User is logged in, show world edit panel
                                    onShowWorldEditPanel()
                                } else {
                                    // User is not logged in, show login dialog
                                    onShowLoginDialog()
                                }
                            }
                        }
                    ) {
                        // Show different text based on login status
                        Text(if (UserApi.getToken() != null) "Claim this World" else "Login and Claim this World")
                    }
                    
                    // Go back button
                    Button(
                        attrs = {
                            classes(Styles.loadingScreenSecondaryButton)
                            onClick { 
                                onNavigateHome()
                            }
                        }
                    ) {
                        Text("Go home")
                    }
                }
            } else {
                // Normal loading message
                Span {
                    Text("Loading Saigon.Quest world...")
                }
            }
        }
    }
}
