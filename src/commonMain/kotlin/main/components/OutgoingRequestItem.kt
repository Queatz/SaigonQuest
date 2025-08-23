package main.components

import androidx.compose.runtime.Composable
import models.FriendRequest
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import main.components.Translated

/**
 * Displays a single outgoing friend request with Cancel action.
 */
@Composable
fun OutgoingRequestItem(
    request: FriendRequest,
    onCancel: () -> Unit,
) {
    Div {
        // Text: "Friend request to <userId>" (we only have addresseeId here; username isn't stored)
        Span { Translated("Friend request to") }
        Span { Text(" ${request.addresseeId}") }
        Div {
            Button(attrs = { onClick { onCancel() } }) { Translated("Cancel") }
        }
    }
}
