package main.components.friends

import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.FriendApi
import main.components.BaseDialog
import main.components.DialogButton
import main.components.DialogButtonStyle
import main.components.DialogInput
import main.components.Translated
import main.components.translated
import main.theme.Styles
import models.Friend
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput
import web.cssom.AtRules.Companion.property

/**
 * SelectFriendDialog
 * - Local search box
 * - Lists current user's friends (filtered)
 * - Click a row to choose immediately
 * - Cancel button closes without selection
 */
@Composable
fun SelectFriendDialog(
    onSelect: (Friend) -> Unit,
    onClose: () -> Unit,
    excludeIds: Set<String> = emptySet(),
) {
    val scope = rememberCoroutineScope()

    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var query by remember { mutableStateOf("") }

    fun loadFriends() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                val list = FriendApi.getFriends().toList()
                friends = list
            } catch (t: Throwable) {
                console.error("Failed to load friends", t)
                errorMessage = t.message ?: "Failed to load friends"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadFriends() }

    val filtered = remember(friends, query, excludeIds) {
        val q = query.trim().lowercase()
        friends
            .asSequence()
            .filter { it.friendId.isNotBlank() }
            .filter { excludeIds.isEmpty() || !excludeIds.contains(it.friendId) }
            .filter { f ->
                if (q.isBlank()) true else {
                    val name = f.friendUsername.ifBlank { f.friendId }
                    name.lowercase().contains(q) || f.friendId.lowercase().contains(q)
                }
            }
            .toList()
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Select friend"),
        maxWidth = "900px",
        content = {
            // Search and refresh row
            Div(attrs = {
                style {
                    property("display", "flex")
                    property("gap", "8px")
                    property("align-items", "center")
                }
            }) {
                DialogInput(
                    value = query,
                    onInput = { query = it },
                    placeholder = str("Search friends"),
                )
            }

            if (errorMessage != null) {
                P(attrs = { classes(Styles.formErrorMessage) }) { Text(errorMessage!!) }
            }

            when {
                isLoading -> P { Translated("Loading...") }
                filtered.isEmpty() -> P { Translated("No results") }
                else -> {
                    Div(attrs = {
                        style {
                            property("display", "flex")
                            property("flex-direction", "column")
                            property("gap", "8px")
                            property("margin-top", "12px")
                            property("max-height", "50vh")
                            property("overflow-y", "auto")
                        }
                    }) {
                        filtered.forEach { f ->
                            val name = f.friendUsername.ifBlank { f.friendId }
                            DialogButton(
                                onClick = { onSelect(f) },
                                style = DialogButtonStyle.Primary,
                                attrs = {
                                    style {
                                        width(100.percent)
                                        justifyContent(JustifyContent.Start)
                                    }
                                }
                            ) {
                                Text(name)
                            }
                        }
                    }
                }
            }
        },
        actions = {
            DialogButton(
                onClick = onClose,
            ) { Translated("Cancel") }
        }
    )
}
