package main.components.worlddocs

import androidx.compose.runtime.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.api.DocApi
import main.components.Translated
import main.components.translated
import main.components.chat.RichTextEditor
import models.Doc
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import main.theme.WorldDocsStyles
import models.DocOperation
import models.WsMsg
import kotlinx.coroutines.flow.collect
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection

// Connection status for collaboration
sealed class ConnectionState {
    data object Connecting : ConnectionState()
    data object Online : ConnectionState()
    data object Reconnecting : ConnectionState()
    data object Closed : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

@Composable
fun DocEditor(
    doc: Doc,
    isEditable: Boolean,
    currentUserId: String,
    onUpdated: (Doc) -> Unit,
    onCollaboratorsChange: (Set<String>) -> Unit = {},
    onConnectionStateChange: (ConnectionState) -> Unit = {},
) {
    var text by remember(doc.key) { mutableStateOf(doc.content) }
    var version by remember(doc.key) { mutableStateOf(doc.version) }
    var savingState by remember { mutableStateOf<SavingState>(SavingState.Idle) }
    val scope = rememberCoroutineScope()

    // Collaboration state
    var collaborators by remember { mutableStateOf<Set<String>>(emptySet()) }
    var wsConn by remember(doc.key) { mutableStateOf<DocApi.CollaborationConnection?>(null) }
    var lastWsText by remember(doc.key) { mutableStateOf(doc.content) }
    var connectionIssue by remember { mutableStateOf<String?>(null) }

    // Notify parent about collaborator changes (filter out self and anon)
    LaunchedEffect(collaborators, currentUserId) {
        val filtered = collaborators.filter { it.isNotBlank() && it != currentUserId && it != "Anonymous" }.toSet()
        onCollaboratorsChange(filtered)
    }

    fun applyOps(base: String, ops: List<DocOperation>): String {
        var content = base
        ops.forEach { op ->
            when (op) {
                is DocOperation.Insert -> {
                    val pos = op.position.coerceIn(0, content.length)
                    content = content.substring(0, pos) + op.text + content.substring(pos)
                }
                is DocOperation.Delete -> {
                    if (op.length > 0) {
                        val start = op.position.coerceIn(0, content.length)
                        val end = (start + op.length).coerceAtMost(content.length)
                        if (start < end) content = content.removeRange(start, end)
                    }
                }
                is DocOperation.Format -> { /* ignore in minimal client */ }
            }
        }
        return content
    }

    // Keep local state in sync if parent provides new doc instance (selection changed or external update)
    LaunchedEffect(doc.key, doc.content, doc.version) {
        text = doc.content
        version = doc.version
        lastWsText = doc.content
        savingState = SavingState.Idle
    }

    // Connect collaboration when editable
    LaunchedEffect(doc.key, isEditable) {
        try { onConnectionStateChange(ConnectionState.Connecting) } catch (_: Throwable) {}
        wsConn?.close()
        wsConn = null
        if (isEditable) {
            val c = DocApi.collaborate(doc.key)
            wsConn = c
            // Send Join once connected by emitting after first event or immediately (best effort)
            scope.launch {
                // A small delay to let connection initialize
                delay(50)
                runCatching { c.send(WsMsg.Join(docId = doc.key, userId = currentUserId, cursor = 0)) }
            }
            // Presence heartbeat (cursor=0 for now)
            scope.launch {
                try {
                    while (true) {
                        delay(2500)
                        runCatching { c.send(WsMsg.Presence(userId = currentUserId, cursor = 0)) }
                    }
                } catch (_: Throwable) { /* cancelled when editor closes */ }
            }
            // Collect events
            scope.launch {
                c.events.collect { ev ->
                    when (ev) {
                        is main.api.DocApi.WsEvent.Message -> when (val m = ev.msg) {
                            is WsMsg.Snapshot -> {
                                text = m.content
                                lastWsText = m.content
                                version = m.version
                                connectionIssue = null
                                try { onConnectionStateChange(ConnectionState.Online) } catch (_: Throwable) {}
                            }
                            is WsMsg.Op -> {
                                // Apply ops only if they came from someone else
                                if (m.userId != currentUserId) {
                                    text = applyOps(text, m.ops)
                                    lastWsText = text
                                    version += 1
                                }
                            }
                            is WsMsg.Presence -> {
                                collaborators = if (m.cursor >= 0) collaborators + m.userId else collaborators - m.userId
                            }
                            is WsMsg.Ack -> {
                                version = m.version
                            }
                            is WsMsg.Error -> {
                                // On error, keep current text; snapshot should follow
                                connectionIssue = m.message
                                try { onConnectionStateChange(ConnectionState.Error(m.message)) } catch (_: Throwable) {}
                            }
                            is WsMsg.Join -> { /* ignore */ }
                        }
                        is main.api.DocApi.WsEvent.Failed -> {
                            connectionIssue = ev.error
                            try { onConnectionStateChange(ConnectionState.Reconnecting) } catch (_: Throwable) {}
                        }
                        is main.api.DocApi.WsEvent.Closed -> {
                            connectionIssue = connectionIssue ?: "Closed"
                            try { onConnectionStateChange(ConnectionState.Closed) } catch (_: Throwable) {}
                        }
                    }
                }
            }
        } else {
            try { onConnectionStateChange(ConnectionState.Closed) } catch (_: Throwable) {}
        }
    }

    // Autosave with debounce when editable
    var saveJob by remember { mutableStateOf<Job?>(null) }

    fun scheduleSave() {
        if (!isEditable) return
        saveJob?.cancel()
        saveJob = scope.launch {
            savingState = SavingState.Saving
            delay(800)
            // Send a coarse-grained Op replacing the whole content so collaborators see updates
            wsConn?.let { conn ->
                val ops = mutableListOf<DocOperation>()
                if (lastWsText.isNotEmpty()) ops += DocOperation.Delete(position = 0, length = lastWsText.length)
                if (text.isNotEmpty()) ops += DocOperation.Insert(position = 0, text = text)
                if (ops.isNotEmpty()) {
                    runCatching { conn.send(WsMsg.Op(userId = currentUserId, baseVersion = version, ops = ops)) }
                    lastWsText = text
                }
            }
            try {
                val updated = DocApi.updateDocContent(docId = doc.key, content = text, baseVersion = version)
                version = updated.version
                savingState = SavingState.Saved
                onUpdated(updated)
            } catch (e: DocApi.VersionConflictError) {
                // Refetch latest and reset editor
                try {
                    val latest = DocApi.getDoc(doc.key)
                    text = latest.content
                    lastWsText = latest.content
                    version = latest.version
                    savingState = SavingState.Conflict
                    onUpdated(latest)
                } catch (t: Throwable) {
                    savingState = SavingState.Error(t.message ?: "Error")
                }
            } catch (t: Throwable) {
                savingState = SavingState.Error(t.message ?: "Error")
            }
        }
    }

    // Ensure WS is closed when composable leaves
    DisposableEffect(doc.key, isEditable) {
        onDispose {
            try { wsConn?.close() } catch (_: Throwable) {}
            wsConn = null
            try { saveJob?.cancel() } catch (_: Throwable) {}
        }
    }


    Div(
        attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
            }
        }
    ) {
        if (isEditable) {
            androidx.compose.runtime.key(doc.key) {
                RichTextEditor(
                    value = text,
                    onValueChange = {
                        text = it
                        scheduleSave()
                    },
                    placeholder = translated("Empty doc"),
                )
            }
        } else {
            // Not editable; show notice and fallback to viewer-like minimal
            P { Span { Translated("Cannot edit") } }
            main.components.Md(content = text.ifBlank { translated("Empty doc") })
        }

        // Saving state indicator
        when (val s = savingState) {
            SavingState.Idle -> {}
            SavingState.Saving -> P({ classes(WorldDocsStyles.statusText) }) { Text(translated("Saving…")) }
            SavingState.Saved -> P({ classes(WorldDocsStyles.statusText) }) { Text(translated("Saved")) }
            SavingState.Conflict -> P({ classes(WorldDocsStyles.statusText) }) { Text(translated("Version conflict")) }
            is SavingState.Error -> P({ classes(WorldDocsStyles.statusText) }) { Text(s.message) }
        }

        // Presence indicator is rendered by parent near the title input
    }
}

private sealed class SavingState {
    data object Idle : SavingState()
    data object Saving : SavingState()
    data object Saved : SavingState()
    data object Conflict : SavingState()
    data class Error(val message: String) : SavingState()
}
