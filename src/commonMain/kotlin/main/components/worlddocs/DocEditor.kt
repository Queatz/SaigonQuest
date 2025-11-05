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
    // Client-side op control to prevent version races
    var inFlight by remember(doc.key) { mutableStateOf(false) }
    var pendingResend by remember(doc.key) { mutableStateOf(false) }
    // If the server reports this client cannot edit, stop sending Ops and keep connection stable
    var serverReadOnly by remember(doc.key) { mutableStateOf(false) }
    var ackTimeoutJob by remember(doc.key) { mutableStateOf<Job?>(null) }

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

    fun makeOps(from: String, to: String): List<DocOperation> {
        if (from == to) return emptyList()
        val ops = mutableListOf<DocOperation>()
        if (from.isNotEmpty()) ops += DocOperation.Delete(position = 0, length = from.length)
        if (to.isNotEmpty()) ops += DocOperation.Insert(position = 0, text = to)
        return ops
    }

    suspend fun trySendPending() {
        if (serverReadOnly) return
        val conn = wsConn ?: return
        if (inFlight) return
        val ops = makeOps(lastWsText, text)
        if (ops.isEmpty()) {
            pendingResend = false
            return
        }
        inFlight = true
        pendingResend = false
        try {
            conn.send(WsMsg.Op(userId = currentUserId, baseVersion = version, ops = ops))
            // Mark as saving only after a successful send
            savingState = SavingState.Saving
            // Start an Ack timeout to prevent "Saving…" forever
            try { ackTimeoutJob?.cancel() } catch (_: Throwable) {}
            ackTimeoutJob = scope.launch {
                delay(10000)
                if (inFlight) {
                    inFlight = false
                    pendingResend = true
                    savingState = SavingState.Error("Timeout")
                    try { onConnectionStateChange(ConnectionState.Reconnecting) } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {
            // If send failed, mark to retry after reconnect and clear saving state
            inFlight = false
            pendingResend = true
            savingState = SavingState.Error("Send failed")
        }
    }

    // Keep local state in sync if parent provides new doc instance (selection changed or external update)
    LaunchedEffect(doc.key, doc.content, doc.version) {
        text = doc.content
        version = doc.version
        lastWsText = doc.content
        savingState = SavingState.Idle
        inFlight = false
        pendingResend = false
        serverReadOnly = false
        try { ackTimeoutJob?.cancel() } catch (_: Throwable) {}
        ackTimeoutJob = null
    }

    // Connect collaboration when editable
    LaunchedEffect(doc.key, isEditable) {
        try { onConnectionStateChange(ConnectionState.Connecting) } catch (_: Throwable) {}
        wsConn?.close()
        wsConn = null
        // Starting a new collaboration session: clear any prior read-only latch
        serverReadOnly = false
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
                                // Treat snapshot as new baseline; preserve local edits if any (unless server has made us read-only)
                                val localDirty = text != lastWsText
                                lastWsText = m.content
                                version = m.version
                                connectionIssue = null
                                // Any server response ends a pending Ack wait
                                try { ackTimeoutJob?.cancel() } catch (_: Throwable) {}
                                ackTimeoutJob = null
                                if (!localDirty || serverReadOnly) {
                                    // If read-only, always show the server content and do not attempt to resend
                                    text = m.content
                                    pendingResend = false
                                    inFlight = false
                                    if (serverReadOnly) savingState = SavingState.Error("Read-only") else if (savingState is SavingState.Saving) savingState = SavingState.Idle
                                } else {
                                    // We have unsent local edits; queue resend against new baseline
                                    pendingResend = true
                                    inFlight = false
                                    scope.launch { trySendPending() }
                                }
                                try { onConnectionStateChange(ConnectionState.Online) } catch (_: Throwable) {}
                            }
                            is WsMsg.Op -> {
                                // Update baseline and version for any op
                                val oldBaseline = lastWsText
                                val newBaseline = applyOps(oldBaseline, m.ops)
                                lastWsText = newBaseline
                                version += 1
                                // Only modify visible text for remote ops
                                if (m.userId != currentUserId) {
                                    val localDirty = text != oldBaseline
                                    if (!localDirty) {
                                        text = applyOps(text, m.ops)
                                    } else {
                                        // Keep local edits; schedule resend against new baseline
                                        pendingResend = true
                                        if (!inFlight) scope.launch { trySendPending() }
                                    }
                                }
                            }
                            is WsMsg.Presence -> {
                                collaborators = if (m.cursor >= 0) collaborators + m.userId else collaborators - m.userId
                            }
                            is WsMsg.Ack -> {
                                // Clear in-flight and mark saved; adopt server version
                                inFlight = false
                                try { ackTimeoutJob?.cancel() } catch (_: Throwable) {}
                                ackTimeoutJob = null
                                savingState = SavingState.Saved
                                // Server has accepted our latest text; advance baseline to the visible text
                                lastWsText = text
                                // Align version to server's authoritative version
                                version = if (version < m.version) m.version else version
                                // If there are more local edits, send them now
                                if (pendingResend) {
                                    scope.launch { trySendPending() }
                                }
                            }
                            is WsMsg.Error -> {
                                // Permission or version errors are not connection failures.
                                val msg = m.message
                                when {
                                    msg.contains("Cannot edit", ignoreCase = true) || msg.contains("Forbidden", ignoreCase = true) -> {
                                        // Switch client to read-only for this doc
                                        serverReadOnly = true
                                        inFlight = false
                                        pendingResend = false
                                        try { ackTimeoutJob?.cancel() } catch (_: Throwable) {}
                                        ackTimeoutJob = null
                                        savingState = SavingState.Error("Read-only")
                                        // Keep status Online; Snapshot will follow to align content
                                    }
                                    msg.contains("version mismatch", ignoreCase = true) || msg.contains("conflict", ignoreCase = true) -> {
                                        // Ignore; a Snapshot should arrive and we will reconcile
                                        try { ackTimeoutJob?.cancel() } catch (_: Throwable) {}
                                        ackTimeoutJob = null
                                        if (savingState is SavingState.Saving) savingState = SavingState.Idle
                                    }
                                    else -> {
                                        // Unknown error: record it but avoid flipping connection state
                                        connectionIssue = msg
                                        try { ackTimeoutJob?.cancel() } catch (_: Throwable) {}
                                        ackTimeoutJob = null
                                        if (savingState is SavingState.Saving) savingState = SavingState.Error(msg)
                                    }
                                }
                            }
                            is WsMsg.Join -> { /* ignore */ }
                        }
                        is main.api.DocApi.WsEvent.Failed -> {
                            connectionIssue = ev.error
                            inFlight = false
                            try { ackTimeoutJob?.cancel() } catch (_: Throwable) {}
                            ackTimeoutJob = null
                            if (text != lastWsText) pendingResend = true
                            if (savingState is SavingState.Saving) savingState = SavingState.Error("Disconnected")
                            try { onConnectionStateChange(ConnectionState.Reconnecting) } catch (_: Throwable) {}
                        }
                        is main.api.DocApi.WsEvent.Closed -> {
                            connectionIssue = connectionIssue ?: "Closed"
                            inFlight = false
                            try { ackTimeoutJob?.cancel() } catch (_: Throwable) {}
                            ackTimeoutJob = null
                            if (text != lastWsText) pendingResend = true
                            if (savingState is SavingState.Saving) savingState = SavingState.Error("Disconnected")
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
            delay(800)
            // Prefer WS: queue and try to send pending diff (only if server allows editing)
            if (!serverReadOnly) {
                wsConn?.let {
                    pendingResend = true
                    trySendPending()
                    return@launch
                }
            }
            // Fallback: if no WS connection or server denied editing, save via HTTP only if allowed
            if (!serverReadOnly) {
                try {
                    savingState = SavingState.Saving
                    val updated = DocApi.updateDocContent(docId = doc.key, content = text, baseVersion = version)
                    version = updated.version
                    savingState = SavingState.Saved
                    onUpdated(updated)
                    // Update baseline since HTTP write succeeded
                    lastWsText = updated.content
                } catch (e: DocApi.VersionConflictError) {
                    // Refetch latest and reset editor
                    try {
                        val latest = DocApi.getDoc(doc.key)
                        lastWsText = latest.content
                        text = latest.content
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
        if (isEditable && !serverReadOnly) {
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
            // Not editable or server has marked this doc read-only; show notice and fallback to viewer-like minimal
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
