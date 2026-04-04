package main.components.worlddocs

import Translations
import Translations.str
import androidx.compose.runtime.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.api.DocApi
import main.components.*
import main.components.chat.ChatComposerController
import main.theme.Styles
import main.theme.WorldDocsStyles
import main.utils.createCustomEvent
import main.utils.toggleFullscreen
import models.DocTree
import org.jetbrains.compose.web.attributes.autoFocus
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLInputElement
import web.animations.awaitAnimationFrame

@Composable
fun WorldDocsDialog(
    worldId: String,
    worldName: String,
    isOwner: Boolean,
    currentUserId: String,
    onClose: () -> Unit,
    initialDocId: String? = null,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // Auto-hide errors after 3 seconds like FriendsContent toast
    LaunchedEffect(error) {
        if (error != null) {
            delay(3000)
            error = null
        }
    }
    var docs by remember { mutableStateOf<List<DocTree>>(emptyList()) }

    // Fullscreen state (like RichTextEditorDialog)
    var isFullscreen by remember { mutableStateOf(document.fullscreenElement != null) }
    DisposableEffect(Unit) {
        val listener: (dynamic) -> Unit = { isFullscreen = document.fullscreenElement != null }
        document.addEventListener("fullscreenchange", listener)
        onDispose { document.removeEventListener("fullscreenchange", listener) }
    }

    // Debounced search
    var searchInput by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    // Server search result IDs (null when not using server search)
    var serverSearchIds by remember { mutableStateOf<Set<String>?>(null) }
    val largeTreeThreshold = 400
    LaunchedEffect(searchInput) {
        delay(300)
        search = searchInput
        // Decide between client-side and server-side search
        if (search.isBlank()) {
            serverSearchIds = null
        } else if (docs.size > largeTreeThreshold) {
            // Use server search when tree is large
            val query = search
            val res = runCatching { DocApi.searchDocs(worldId = worldId, query = query, limit = 50) }
            res.onSuccess { list ->
                serverSearchIds = list.map { it.key }.toSet()
                error = null
            }
            res.onFailure { t ->
                serverSearchIds = null
                error = t.message
            }
        } else {
            serverSearchIds = null
        }
    }

    var selectedDocId by remember { mutableStateOf<String?>(null) }
    // When using server-side search, auto-select the first result
    LaunchedEffect(serverSearchIds) {
        val ids = serverSearchIds
        if (ids != null && ids.isNotEmpty()) {
            if (selectedDocId !in ids) selectedDocId = ids.first()
        }
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(worldId) {
        try {
            loading = true
            error = null
            docs = DocApi.getDocTree(worldId)
            if (initialDocId != null) {
                selectedDocId = initialDocId
            } else {
                selectedDocId = docs.firstOrNull()?.doc?.key
            }
        } catch (t: Throwable) {
            error = t.message ?: "Error"
        } finally {
            loading = false
        }
    }

    // Handle external updates to initialDocId
    LaunchedEffect(initialDocId) {
        if (initialDocId != null) {
            selectedDocId = initialDocId
        }
    }

    val flatDocs = remember(docs) { docs.flatMap { WorldDocsUtils.flatten(it) } }
    val selectedDoc = flatDocs.firstOrNull { it.key == selectedDocId }

    // Local edited content for currently selected doc (freshest value from editor)
    var editedContent by remember(selectedDoc?.key) { mutableStateOf(selectedDoc?.content ?: "") }
    LaunchedEffect(selectedDoc?.key) { editedContent = selectedDoc?.content ?: "" }

    // Rename state
    var renameTitle by remember(selectedDoc?.key) { mutableStateOf(selectedDoc?.title ?: "") }
    LaunchedEffect(selectedDoc?.key) { renameTitle = selectedDoc?.title ?: "" }

    // Delete confirmation state
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // History dialog state
    var showHistoryDialog by remember { mutableStateOf(false) }

    // Move dialog state
    var showMoveDialog by remember { mutableStateOf(false) }

    // Collaborators shown under the title input
    var collaborators by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(selectedDocId) { collaborators = emptySet() }

    // Title input focus management
    var requestFocusOnTitle by remember { mutableStateOf(false) }
    var titleInputEl by remember { mutableStateOf<HTMLInputElement?>(null) }
    LaunchedEffect(selectedDocId, requestFocusOnTitle) {
        if (requestFocusOnTitle) {
            try {
                titleInputEl?.focus()
                titleInputEl?.select()
            } catch (_: Throwable) {
            }
            requestFocusOnTitle = false
        }
    }

    BaseDialog(
        onClose = onClose,
        title = worldName.ifBlank { str("Docs") },
        isFullHeight = true,
        isFullWidth = true,
        isFullscreen = isFullscreen,
        headerActions = {
            DialogButton(
                onClick = { toggleFullscreen() },
                style = DialogButtonStyle.Secondary,
            ) {
                IconRenderer(
                    url = "raw/data/ui/ui.png",
                    index = if (isFullscreen) 1 else 0,
                    size = 24,
                )
            }
        },
        content = {
            // Toast error banner (auto-hidden)
            if (error != null) {
                Div(attrs = {
                    classes(Styles.toastMessage, Styles.toastError)
                }) { Text(error!!) }
            }
            // Header actions: create and search
            // Connection status state (for header chip)
            var connState by remember { mutableStateOf<ConnectionState>(ConnectionState.Closed) }
            // Only show connection status when the selected doc is editable for this user
            val canEditSelected = remember(selectedDocId, docs, isOwner, currentUserId) {
                val doc = flatDocs.firstOrNull { it.key == selectedDocId }
                if (doc == null) false else {
                    isOwner || (
                            currentUserId != "Anonymous" &&
                                    doc.permissions.worldUsersCanEdit &&
                                    WorldDocsUtils.ancestorsOf(docs, doc.key).all { anc -> anc.permissions.worldUsersCanEdit }
                            )
                }
            }

            Div(attrs = { classes(WorldDocsStyles.headerBar) }) {
                DialogInput(
                    value = searchInput,
                    onInput = { searchInput = it },
                    placeholder = str("Search docs"),
                    containerAttrs = {
                        style {
                            width(200.px)
                        }
                    }
                )
                if (isOwner) {
                DialogButton(
                    style = DialogButtonStyle.Primary,
                    onClick = {
                        scope.launch {
                            val result = runCatching {
                                DocApi.createDoc(
                                    worldId = worldId,
                                    title = str("New doc"),
                                    parentId = null,
                                )
                            }
                            result.onSuccess { created ->
                                docs = docs + DocTree(doc = created, children = emptyList())
                                selectedDocId = created.key
                                renameTitle = created.title
                                requestFocusOnTitle = true
                                error = null
                                // Notify listeners that world docs changed (for counts)
                                val detail: dynamic = object {}
                                detail.worldId = worldId
                                val evt = createCustomEvent("worldDocsChanged", detail)
                                window.dispatchEvent(evt)
                            }
                            result.onFailure { t ->
                                error = t.message
                            }
                        }
                    }
                ) {
                    Translated("Create doc")
                }
                }
                Div(
                    attrs = {
                        style { flexGrow(1); flexShrink(1) }
                    }
                )
                if (canEditSelected) {
                    // Connection status chip
                    Div(attrs = {
                        classes(
                            WorldDocsStyles.statusChip,
                            when (connState) {
                                is ConnectionState.Online -> WorldDocsStyles.statusChipOnline
                                is ConnectionState.Reconnecting, is ConnectionState.Error -> WorldDocsStyles.statusChipReconnecting
                                else -> WorldDocsStyles.statusChipReconnecting
                            }
                        )
                    }) {
                        when (connState) {
                            is ConnectionState.Online -> Text(Translations.str("Online"))
                            is ConnectionState.Reconnecting -> Text(Translations.str("Reconnecting…"))
                            is ConnectionState.Connecting -> Text(Translations.str("Reconnecting…"))
                            is ConnectionState.Error -> Text(Translations.str("Reconnecting…"))
                            is ConnectionState.Closed -> Text(Translations.str("Reconnecting…"))
                        }
                    }
                }
            }

            when {
                loading -> Div { Translated("Loading…") }
                docs.isEmpty() -> Div { Translated("World has no docs.") }
                else -> {
                    // Two-column content
                    Div(attrs = { classes(WorldDocsStyles.content, WorldDocsStyles.contentMobile) }) {
                        // Left: tree
                        Div(attrs = { classes(WorldDocsStyles.leftPanel) }) {
                            val filteredDocs = when {
                                search.isBlank() -> docs
                                serverSearchIds != null -> docs.mapNotNull { WorldDocsUtils.filterTreeByIds(it, serverSearchIds!!) }
                                else -> docs.mapNotNull { WorldDocsUtils.filterTree(it, search) }
                            }
                            DocTreeView(
                                docs = filteredDocs,
                                selectedDocId = selectedDocId,
                                onSelect = { id -> selectedDocId = id },
                                canDrag = isOwner || filteredDocs.any { it.doc.permissions.worldUsersCanEdit },
                                onMoveRequest = { movingId, newParentId, newIndex ->
                                    scope.launch {
                                        val res = runCatching { DocApi.moveDoc(movingId, newParentId, newIndex) }
                                        res.onSuccess {
                                            val refreshed = runCatching { DocApi.getDocTree(worldId) }
                                            refreshed.onSuccess { docs = it }
                                            refreshed.onFailure { t -> error = t.message }
                                        }
                                        res.onFailure { t -> error = t.message }
                                    }
                                },
                                canEditDoc = { d ->
                                    isOwner || (
                                            currentUserId != "Anonymous" &&
                                                    d.permissions.worldUsersCanEdit &&
                                                    WorldDocsUtils.ancestorsOf(
                                                        docs,
                                                        d.key
                                                    ).all { ancestor -> ancestor.permissions.worldUsersCanEdit }
                                            )
                                },
                                expandPathToId = selectedDocId,
                                showAddButtonForSelected = isOwner,
                                onCreateChild = { parentId ->
                                    scope.launch {
                                        val createdRes = runCatching {
                                            DocApi.createDoc(
                                                worldId = worldId,
                                                title = str("New doc"),
                                                parentId = parentId,
                                            )
                                        }
                                        createdRes.onSuccess { created ->
                                            // Refresh tree to include new child at proper order
                                            val refreshed = runCatching { DocApi.getDocTree(worldId) }
                                            refreshed.onSuccess {
                                                docs = it
                                                selectedDocId = created.key
                                                renameTitle = created.title
                                                requestFocusOnTitle = true
                                                error = null
                                            }
                                            refreshed.onFailure { t -> error = t.message }
                                        }
                                        createdRes.onFailure { t -> error = t.message }
                                    }
                                },
                            )
                        }

                        // Right: editor/viewer
                        Div(attrs = { classes(WorldDocsStyles.rightPanel) }) {
                            if (selectedDoc != null) {
                                val ancestors = WorldDocsUtils.ancestorsOf(docs, selectedDoc.key)
                                val canEditDoc = isOwner || (
                                        currentUserId != "Anonymous" &&
                                                selectedDoc.permissions.worldUsersCanEdit &&
                                                ancestors.all { it.permissions.worldUsersCanEdit }
                                        )

                                // Controls: title rename (owners and editors), delete (owner-only), move (owners and editors)
                                Div(attrs = { classes(WorldDocsStyles.controlsRow) }) {
                                    // Title input + Save for editors/owners
                                    if (canEditDoc) {
                                        val saveTitle: () -> Unit = {
                                            val id = selectedDoc.key
                                            val newTitle = renameTitle.trim()
                                            if (newTitle.isBlank() || newTitle == selectedDoc.title) {
                                                // no-op when blank or unchanged
                                            } else {
                                                scope.launch {
                                                    val result = runCatching {
                                                        DocApi.updateDoc(
                                                            docId = id,
                                                            update = DocApi.UpdateDocRequest(
                                                                title = newTitle,
                                                                parentId = selectedDoc.parentId,
                                                                orderIndex = selectedDoc.orderIndex,
                                                            )
                                                        )
                                                    }
                                                    result.onSuccess { updated ->
                                                        docs = docs.map { WorldDocsUtils.updateNode(it, updated) }
                                                    }
                                                    result.onFailure { t -> error = t.message }
                                                }
                                            }
                                        }
                                        Div(
                                            attrs = {
                                                style {
                                                    display(DisplayStyle.Flex)
                                                    flexDirection(FlexDirection.Column)
                                                    gap(4.px)
                                                }
                                            }
                                        ) {
                                            DialogInput(
                                                value = renameTitle,
                                                onInput = { renameTitle = it },
                                                placeholder = Translations.str("Title"),
                                                containerAttrs = {
                                                    style {
                                                        property("min-width", "200px")
                                                        flexGrow(1)
                                                    }
                                                },
                                                autoFocus = true,
                                                attrs = {
                                                    onKeyDown { ev ->
                                                        if (ev.key == "Enter") {
                                                            ev.preventDefault()
                                                            saveTitle()
                                                        }
                                                    }

                                                    ref {
                                                        titleInputEl = it as HTMLInputElement
                                                        scope.launch {
                                                            delay(100)
                                                            awaitAnimationFrame()
                                                            it.focus()
                                                        }
                                                        onDispose { titleInputEl = null }
                                                    }
                                                }
                                            )
                                            // Collaborators indicator just under the title input
                                            if (collaborators.isNotEmpty()) {
                                                PresenceIndicator(users = collaborators)
                                            }
                                        }
                                        val trimmed = renameTitle.trim()
                                        DialogButton(
                                            style = DialogButtonStyle.Primary,
                                            isDisabled = trimmed.isBlank(),
                                            onClick = {
                                                saveTitle()
                                            }
                                        ) { Translated("Save") }
                                    }

                                    if (isOwner) {
                                        // History button
                                        DialogButton(
                                            onClick = { showHistoryDialog = true }
                                        ) {
                                            Translated("History")
                                        }

                                        val selectedNode = docs.firstNotNullOfOrNull { WorldDocsUtils.findNode(it, selectedDoc.key) }
                                        val hasChildren = selectedNode?.children?.isNotEmpty() == true
                                        DialogButton(
                                            style = DialogButtonStyle.Danger,
                                            isDisabled = hasChildren,
                                            attrs = {
                                                if (hasChildren) {
                                                    title(str("Cannot delete: this doc has children"))
                                                }
                                            },
                                            onClick = {
                                                showDeleteConfirm = true
                                            }
                                        ) { Translated("Delete") }
                                    }

                                    // Move up/down among siblings (editors and owners)
                                    if (canEditDoc) {
                                        val siblings = WorldDocsUtils.siblingsOf(docs, selectedDoc.parentId)
                                        val idx = siblings.indexOfFirst { it.key == selectedDoc.key }
                                        val canMoveUp = idx > 0
                                        val canMoveDown = idx >= 0 && idx < siblings.lastIndex

                                        DialogButton(
                                            isDisabled = !canMoveUp,
                                            attrs = {
                                                title(str("[Ctrl] Move to top"))
                                                if (canMoveUp) {
                                                    onClick { event ->
                                                        scope.launch {
                                                            val newIdx = if (event.ctrlKey) {
                                                                // Move all the way to the top
                                                                val first = siblings.first()
                                                                WorldDocsUtils.newIndex(before = null, after = first.orderIndex)
                                                            } else {
                                                                val prev = siblings[idx - 1]
                                                                val beforePrev =
                                                                    if (idx - 2 >= 0) siblings[idx - 2].orderIndex else null
                                                                WorldDocsUtils.newIndex(beforePrev, prev.orderIndex)
                                                            }
                                                            val res = runCatching {
                                                                DocApi.moveDoc(
                                                                    selectedDoc.key,
                                                                    selectedDoc.parentId,
                                                                    newIdx
                                                                )
                                                            }
                                                            res.onSuccess {
                                                                // Reload tree for consistent ordering
                                                                val refreshed = runCatching { DocApi.getDocTree(worldId) }
                                                                refreshed.onSuccess { docs = it }
                                                                refreshed.onFailure { t -> error = t.message }
                                                            }
                                                            res.onFailure { t -> error = t.message }
                                                        }
                                                    }
                                                }
                                            },
                                            onClick = {}
                                        ) { Translated("Move up") }

                                        DialogButton(
                                            isDisabled = !canMoveDown,
                                            attrs = {
                                                title(str("[Ctrl] Move to bottom"))
                                                if (canMoveDown) {
                                                    onClick { event ->
                                                        scope.launch {
                                                            val newIdx = if (event.ctrlKey) {
                                                                // Move all the way to the bottom
                                                                val last = siblings.last()
                                                                WorldDocsUtils.newIndex(before = last.orderIndex, after = null)
                                                            } else {
                                                                val next = siblings[idx + 1]
                                                                val afterNext =
                                                                    if (idx + 2 <= siblings.lastIndex) siblings[idx + 2].orderIndex else null
                                                                WorldDocsUtils.newIndex(next.orderIndex, afterNext)
                                                            }
                                                            val res = runCatching {
                                                                DocApi.moveDoc(
                                                                    selectedDoc.key,
                                                                    selectedDoc.parentId,
                                                                    newIdx
                                                                )
                                                            }
                                                            res.onSuccess {
                                                                val refreshed = runCatching { DocApi.getDocTree(worldId) }
                                                                refreshed.onSuccess { docs = it }
                                                                refreshed.onFailure { t -> error = t.message }
                                                            }
                                                            res.onFailure { t -> error = t.message }
                                                        }
                                                    }
                                                }
                                            },
                                            onClick = {}
                                        ) { Translated("Move down") }

                                        DialogButton(
                                            onClick = {
                                                showMoveDialog = true
                                            }
                                        ) { Translated("Move") }
                                    }
                                }

                                if (canEditDoc) {
                                    DocEditor(
                                        doc = selectedDoc,
                                        isEditable = true,
                                        currentUserId = currentUserId,
                                        onUpdated = { updated ->
                                            docs = docs.map { WorldDocsUtils.updateNode(it, updated) }
                                            // Keep local edited content in sync with server-updated content
                                            if (updated.key == selectedDoc.key) {
                                                editedContent = updated.content
                                            }
                                        },
                                        onCollaboratorsChange = { set ->
                                            // Filter out self and anon here as a safety net
                                            collaborators =
                                                set.filter { it != currentUserId && it != "Anonymous" }.toSet()
                                        },
                                        onConnectionStateChange = { state ->
                                            connState = state
                                        },
                                        onLocalContentChange = { latest ->
                                            editedContent = latest
                                        },
                                    )
                                } else {
                                    DocViewer(doc = selectedDoc)
                                }
                            }
                        }
                    }
                }
            }

            if (showDeleteConfirm && selectedDoc != null) {
                BasicConfirmDialog(
                    message = Translations.str("Are you sure you want to delete this doc?"),
                    onConfirm = {
                        showDeleteConfirm = false
                        val id = selectedDoc.key
                        scope.launch {
                            val result = runCatching { DocApi.deleteDoc(id) }
                            result.onSuccess {
                                docs = WorldDocsUtils.removeNode(docs, id)
                                selectedDocId = docs.firstOrNull()?.doc?.key
                                // Notify listeners that world docs changed (for counts)
                                val detail: dynamic = object {}
                                detail.worldId = worldId
                                val evt = createCustomEvent("worldDocsChanged", detail)
                                window.dispatchEvent(evt)
                            }
                            result.onFailure { t -> error = t.message }
                        }
                    },
                    onCancel = { showDeleteConfirm = false }
                )
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Translated("Close") }
            if (selectedDoc != null) {
                DialogButton(
                    style = DialogButtonStyle.Primary,
                    onClick = {
                        val content = editedContent
                        ChatComposerController.openRichEditor(
                            worldId = worldId,
                            initialText = content,
                        )
                        onClose()
                    }
                ) { Translated("Copy to chat") }
            }
        },
    )

    // History dialog
    if (showHistoryDialog && selectedDoc != null) {
        val ancestors = WorldDocsUtils.ancestorsOf(docs, selectedDoc.key)
        val canRestore = isOwner || (
                currentUserId != "Anonymous" &&
                        selectedDoc.permissions.worldUsersCanEdit &&
                        ancestors.all { it.permissions.worldUsersCanEdit }
                )
        DocHistoryDialog(
            docId = selectedDoc.key,
            canRestore = canRestore,
            onClose = { showHistoryDialog = false },
        )
    }

    // Move dialog
    if (showMoveDialog && selectedDoc != null) {
        // Exclude moving into itself or any of its descendants
        val selectedNode = docs.firstNotNullOfOrNull { WorldDocsUtils.findNode(it, selectedDoc.key) }
        val excludeIds: Set<String> = buildSet {
            fun walk(node: DocTree?) {
                if (node == null) return
                add(node.doc.key)
                node.children.forEach { child -> walk(child) }
            }
            walk(selectedNode)
        }

        SelectDocDialog(
            worldId = worldId,
            onSelect = { targetParent ->
                showMoveDialog = false
                val movingId = selectedDoc.key
                scope.launch {
                    // Compute new order index to append as last child of the target parent
                    val targetNode = docs.firstNotNullOfOrNull { WorldDocsUtils.findNode(it, targetParent.key) }
                    val lastChildOrder = targetNode?.children?.maxByOrNull { it.doc.orderIndex }?.doc?.orderIndex
                    val idx = WorldDocsUtils.newIndex(before = lastChildOrder, after = null)
                    val res = runCatching { DocApi.moveDoc(movingId, targetParent.key, idx) }
                    res.onSuccess {
                        val refreshed = runCatching { DocApi.getDocTree(worldId) }
                        refreshed.onSuccess {
                            docs = it
                            // Keep selection on the moved doc
                            selectedDocId = movingId
                            error = null
                        }
                        refreshed.onFailure { t -> error = t.message }
                    }
                    res.onFailure { t -> error = t.message }
                }
            },
            onClose = { showMoveDialog = false },
            excludeIds = excludeIds,
            confirmButtonLabel = str("Move"),
        )
    }
}

