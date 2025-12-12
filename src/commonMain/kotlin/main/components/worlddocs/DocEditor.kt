package main.components.worlddocs

import androidx.compose.runtime.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.api.DocApi
import main.components.Translated
import main.components.translated
import main.components.chat.RichTextEditor
import main.components.chat.RichTextEditorController
import models.Doc
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import main.theme.WorldDocsStyles
import models.DocOperation
import models.WsMsg
import kotlinx.coroutines.flow.collect
import main.components.Md
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
    onLocalContentChange: (String) -> Unit = {},
) {
    var text by remember(doc.key) { mutableStateOf(doc.content) }
    var version by remember(doc.key) { mutableStateOf(doc.version) }
    var savingState by remember { mutableStateOf<SavingState>(SavingState.Idle) }
    val scope = rememberCoroutineScope()
    // Imperative controller for zero-flash remote updates
    val rtedController = remember(doc.key) { main.components.chat.RichTextEditorController() }
    var suppressSave by remember { mutableStateOf(false) }

    // Track local selection (caret) so we can preserve it across remote updates
    var selStart by remember(doc.key) { mutableStateOf(doc.content.length) }
    var selEnd by remember(doc.key) { mutableStateOf(doc.content.length) }
    // When we programmatically re-apply selection after remote updates, gate application
    // in the child on the intended target length to avoid clamping to an older value.
    var selTargetLen by remember(doc.key) { mutableStateOf<Int?>(doc.content.length) }

    fun currentCursor(): Int = selEnd.coerceIn(0, text.length)

    // Transform selection to account for remote operations applied to visible text
    // Transform selection using end-exclusive semantics: [start, end)
    fun transformSelection(range: IntRange, ops: List<DocOperation>): IntRange {
        var start = range.first
        var endExcl = range.last // treat as exclusive end
        ops.forEach { op ->
            when (op) {
                is DocOperation.Insert -> {
                    val pos = op.position
                    val len = op.text.length
                    when {
                        // Insert strictly before start -> shift both
                        pos < start -> { start += len; endExcl += len }
                        // Insert exactly at start
                        pos == start -> {
                            if (start == endExcl) {
                                // Collapsed caret: keep caret after the inserted text
                                start += len; endExcl += len
                            } else {
                                // Expanded selection: treat as before selection; do not extend
                                // no-op
                            }
                        }
                        // Insert inside selection (not at end boundary) -> extend end
                        pos in (start until endExcl) -> { endExcl += len }
                        // Insert at end boundary or after -> no change
                        else -> {}
                    }
                }
                is DocOperation.Delete -> {
                    val delStart = op.position
                    val delEndExcl = op.position + op.length
                    when {
                        // Deletion entirely after selection end — no change
                        endExcl <= delStart -> { /* no change */ }
                        // Deletion entirely before selection start — shift left
                        start >= delEndExcl -> { start -= op.length; endExcl -= op.length }
                        else -> {
                            // Overlap with selection: collapse to start of deletion
                            val caret = delStart.coerceAtLeast(0)
                            start = caret
                            endExcl = caret
                        }
                    }
                }
                is DocOperation.Format -> { /* no effect on indices */ }
            }
        }
        // Do not clamp to the old text length here; the caller will clamp to the NEW content length.
        if (start < 0) start = 0
        if (endExcl < 0) endExcl = 0
        if (start > endExcl) start = endExcl
        return start..endExcl
    }

    // Collaboration state
    var collaborators by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
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
        val filteredNames: Set<String> = collaborators
            .filter { (id, name) -> id.isNotBlank() && id != currentUserId && name.isNotBlank() && name != "Anonymous" }
            .values
            .toSet()
        onCollaboratorsChange(filteredNames)
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
        val fromLen = from.length
        val toLen = to.length
        var prefix = 0
        val minLen = kotlin.math.min(fromLen, toLen)
        while (prefix < minLen && from[prefix] == to[prefix]) prefix++
        var suffix = 0
        while (suffix < (minLen - prefix) && from[fromLen - 1 - suffix] == to[toLen - 1 - suffix]) suffix++
        val ops = mutableListOf<DocOperation>()
        val delLen = fromLen - prefix - suffix
        if (delLen > 0) {
            ops += DocOperation.Delete(position = prefix, length = delLen)
        }
        val insLen = toLen - prefix - suffix
        if (insLen > 0) {
            val insertText = to.substring(prefix, prefix + insLen)
            ops += DocOperation.Insert(position = prefix, text = insertText)
        }
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
        try { onLocalContentChange(doc.content) } catch (_: Throwable) {}
        version = doc.version
        lastWsText = doc.content
        savingState = SavingState.Idle
        inFlight = false
        pendingResend = false
        serverReadOnly = false
        selTargetLen = doc.content.length
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
                val cur = currentCursor()
                runCatching { c.send(WsMsg.Join(docId = doc.key, userId = currentUserId, cursor = cur)) }
            }
            // Presence heartbeat with current cursor
            scope.launch {
                try {
                    while (true) {
                        delay(2500)
                        val cur = currentCursor()
                        runCatching { c.send(WsMsg.Presence(userId = currentUserId, cursor = cur)) }
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
                                    val newLen = m.content.length
                                    // Try zero-flash snapshot application via controller (if editor is mounted and focused)
                                    val clampedStart = selStart.coerceIn(0, newLen)
                                    val clampedEnd = selEnd.coerceIn(0, newLen)
                                    val applied = rtedController.applySnapshot(m.content, clampedStart, clampedEnd)
                                    suppressSave = true
                                    try {
                                        // Sync state regardless of DOM path used
                                        text = m.content
                                        try { onLocalContentChange(m.content) } catch (_: Throwable) {}
                                        selTargetLen = newLen
                                        selStart = clampedStart
                                        selEnd = clampedEnd
                                    } finally {
                                        suppressSave = false
                                    }
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
                                        // Apply remote ops to visible text and adjust local selection
                                        val newTextContent = applyOps(text, m.ops)
                                        // Use anchored diff between current visible text and new content to transform selection robustly
                                        val opsForTransform = makeOps(text, newTextContent)
                                        val newSel = transformSelection(selStart..selEnd, opsForTransform)
                                        val max = newTextContent.length
                                        val clampedStart = newSel.first.coerceIn(0, max)
                                        val clampedEnd = newSel.last.coerceIn(0, max)
                                        // Try zero-flash DOM patch via controller
                                        val applied = rtedController.applyRemoteOps(m.ops, clampedStart, clampedEnd)
                                        suppressSave = true
                                        try {
                                            // Sync state regardless of DOM path used
                                            text = newTextContent
                                            try { onLocalContentChange(newTextContent) } catch (_: Throwable) {}
                                            selTargetLen = max
                                            if (clampedStart != selStart || clampedEnd != selEnd) {
                                                selStart = clampedStart
                                                selEnd = clampedEnd
                                            }
                                        } finally {
                                            suppressSave = false
                                        }
                                    } else {
                                        // Keep local edits; schedule resend against new baseline
                                        pendingResend = true
                                        if (!inFlight) scope.launch { trySendPending() }
                                    }
                                }
                            }
                            is WsMsg.Presence -> {
                                if (m.cursor >= 0) {
                                    val name = m.userName.ifBlank { m.userId }
                                    collaborators = collaborators + (m.userId to name)
                                } else {
                                    collaborators = collaborators - m.userId
                                }
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
                        is DocApi.WsEvent.Failed -> {
                            connectionIssue = ev.error
                            inFlight = false
                            try { ackTimeoutJob?.cancel() } catch (_: Throwable) {}
                            ackTimeoutJob = null
                            if (text != lastWsText) pendingResend = true
                            if (savingState is SavingState.Saving) savingState = SavingState.Error("Disconnected")
                            try { onConnectionStateChange(ConnectionState.Reconnecting) } catch (_: Throwable) {}
                        }
                        is DocApi.WsEvent.Closed -> {
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
                        try { onLocalContentChange(latest.content) } catch (_: Throwable) {}
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
            key(doc.key) {
                RichTextEditor(
                    value = text,
                    onValueChange = {
                        // When we update programmatically via the controller, suppress autosave
                        if (suppressSave) {
                            text = it
                            try { onLocalContentChange(it) } catch (_: Throwable) {}
                        } else {
                            text = it
                            try { onLocalContentChange(it) } catch (_: Throwable) {}
                            scheduleSave()
                        }
                    },
                    placeholder = translated("Empty doc"),
                    docName = doc.title,
                    selection = selStart..selEnd,
                    onSelectionChange = { s, e ->
                        selStart = s
                        selEnd = e
                        // Emit presence update immediately with the new cursor position
                        wsConn?.let { conn ->
                            scope.launch {
                                runCatching { conn.send(WsMsg.Presence(userId = currentUserId, cursor = e.coerceIn(0, text.length))) }
                            }
                        }
                    },
                    selectionTargetLength = selTargetLen,
                    controller = rtedController,
                )
            }
        } else {
            // Not editable or server has marked this doc read-only; show notice and fallback to viewer-like minimal
            P { Span { Translated("Cannot edit") } }
            Md(content = text.ifBlank { translated("Empty doc") })
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
