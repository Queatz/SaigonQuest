package main.components

import androidx.compose.runtime.Composable
import models.FriendRequest
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import main.components.Translated

/**
 * Displays a single incoming friend request with Accept/Decline actions.
 */
@Composable
fun IncomingRequestItem(
    request: FriendRequest,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Div {
        // Text: "<username> sent you a friend request"
        Span { Text("${request.requesterUsername} ") }
        Span { Translated("sent you a friend request") }
        Div {
            Button(attrs = { onClick { onAccept() } }) { Translated("Accept") }
            Button(attrs = { onClick { onDecline() } }) { Translated("Decline") }
        }
    }
}
