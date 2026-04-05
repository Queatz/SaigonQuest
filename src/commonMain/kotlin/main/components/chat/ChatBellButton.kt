package main.components.chat

import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.ChatApi
import main.api.UserApi
import main.components.BasicConfirmDialog
import main.components.DialogButton
import main.components.DialogButtonStyle
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Text

/**
 * A reusable bell button component for managing chat subscriptions.
 *
 * @param worldId The ID of the world to manage subscriptions for
 */
@Composable
fun ChatBellButton(worldId: String) {
    var isAuthenticated by remember { mutableStateOf(false) }
    var isSubscribed by remember { mutableStateOf(true) }
    var showSubscriptionDialog by remember { mutableStateOf(false) }
    var willSubscribe by remember { mutableStateOf<Boolean?>(null) }

    val scope = rememberCoroutineScope()

    // Check if user is authenticated
    LaunchedEffect(Unit) {
        try {
            val user = UserApi.getCurrentUser()
            isAuthenticated = user.key.isNotEmpty()
        } catch (e: Throwable) {
            console.error("Error checking authentication status", e)
            isAuthenticated = false
        }
    }

    // Fetch subscription status when authenticated
    LaunchedEffect(isAuthenticated, worldId) {
        if (isAuthenticated && worldId.isNotEmpty()) {
            try {
                isSubscribed = ChatApi.getSubscription(worldId)
            } catch (e: Throwable) {
                console.error("Failed to fetch subscription", e)
                isSubscribed = true
            }
        }
    }

    DialogButton(
        onClick = {
            if (isAuthenticated) {
                willSubscribe = !isSubscribed
                showSubscriptionDialog = true
            }
        },
        style = DialogButtonStyle.Secondary,
        isDisabled = !isAuthenticated,
        attrs = {
            title(str(if (isSubscribed) "Subscribed" else "Not subscribed"))
        }
    ) {
        Text(if (isSubscribed) "🔔" else "🔕")
    }

    // Subscription confirm dialog
    if (showSubscriptionDialog && willSubscribe != null) {
        BasicConfirmDialog(
            message = str(if (willSubscribe == true) "Subscribe to this world?" else "Unsubscribe from this world?"),
            onConfirm = {
                scope.launch {
                    try {
                        val ok = if (willSubscribe == true) ChatApi.subscribe(worldId) else ChatApi.unsubscribe(worldId)
                        if (ok) {
                            isSubscribed = (willSubscribe == true)
                        }
                    } catch (t: Throwable) {
                        console.error("Failed to update subscription", t)
                    } finally {
                        showSubscriptionDialog = false
                    }
                }
            },
            onCancel = {
                showSubscriptionDialog = false
            },
        )
    }
}
