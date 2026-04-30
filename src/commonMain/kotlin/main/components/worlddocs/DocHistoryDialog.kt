package main.components.worlddocs

import Translations.str
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.api.DocApi
import main.components.*
import main.components.common.formatTimestampLikeChat
import main.theme.Styles
import main.theme.WorldDocsStyles
import models.DocHistory
import main.api.UserApi
import org.jetbrains.compose.web.dom.*

@Composable
fun DocHistoryDialog(
    docId: String,
    canRestore: Boolean,
    onClose: () -> Unit,
) {
    var history by remember { mutableStateOf<List<DocHistory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRestoreForId by remember { mutableStateOf<String?>(null) }
    var userNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            isLoading = true
            error = null
            try {
                val list = DocApi.getDocHistory(docId)
                history = list
                // Resolve usernames for all unique userIds
                val ids = list.flatMap { it.userIds }.distinct()
                val map = mutableMapOf<String, String>()
                ids.forEach { id ->
                    val u = runCatching { UserApi.getUserById(id) }.getOrNull()
                    val name = u?.username?.takeIf { it.isNotBlank() } ?: str("Unknown")
                    map[id] = name
                }
                userNames = map
            } catch (t: Throwable) {
                error = t.message ?: Translations.str("Failed to load history")
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(docId) { load() }

    BaseDialog(
        onClose = onClose,
        title = translated("Doc history"),
        maxWidth = "900px",
        isFullHeight = true,
        content = {
            when {
                isLoading -> P { Translated("Loading...") }
                error != null -> P(attrs = { classes(Styles.formErrorMessage) }) { Text(error!!) }
                history.isEmpty() -> P { Translated("No history yet") }
                else -> {
                    Div({ classes(WorldDocsStyles.historyList) }) {
                        history.forEach { h ->
                            Paper({ classes(WorldDocsStyles.historyItem) }) {
                                // Header with users and created timestamp
                                Div({ classes(WorldDocsStyles.historyHeader) }) {
                                    Div({ classes(WorldDocsStyles.historyUsers) }) {
                                        val names = h.userIds.map { uid -> userNames[uid] ?: str("Unknown") }
                                        if (names.isEmpty()) {
                                            Span { Text(translated("Unknown")) }
                                        } else {
                                            Text(names.joinToString(", "))
                                        }
                                    }
                                    Span({ classes(WorldDocsStyles.historyTimestamp) }) {
                                        Text(formatTimestampLikeChat(h.createdAt))
                                    }
                                }
                                // Patch block
                                if (h.patch.isNotBlank()) {
                                    Div({ classes(WorldDocsStyles.diffBlock) }) {
                                        val lines = remember(h.patch) { h.patch.split('\n') }
                                        lines.forEach { line ->
                                            val cls = when {
                                                line.startsWith("+") -> WorldDocsStyles.diffAdd
                                                line.startsWith("-") -> WorldDocsStyles.diffRemove
                                                line.startsWith("@@") || line.startsWith("---") || line.startsWith("+++") -> WorldDocsStyles.diffHeader
                                                else -> WorldDocsStyles.diffContext
                                            }
                                            Div({ classes(cls) }) { Text(line) }
                                        }
                                    }
                                }
                                // Restore button
                                if (canRestore) {
                                    Div({ classes(WorldDocsStyles.historyRestoreRow, Styles.dialogueButtons) }) {
                                        DialogButton(onClick = { confirmRestoreForId = h.key }) { Translated("Restore") }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Confirm restore
            confirmRestoreForId?.let { id ->
                BasicConfirmDialog(
                    message = str("Are you sure you want to restore this version?"),
                    onCancel = { confirmRestoreForId = null },
                    onConfirm = {
                        confirmRestoreForId = null
                        scope.launch {
                            val res = runCatching { DocApi.restoreDocVersion(docId, id) }
                            res.onSuccess {
                                // Close the dialog after a successful restore
                                onClose()
                            }
                            res.onFailure { t -> error = t.message }
                        }
                    }
                )
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Translated("Close") }
        }
    )
}
