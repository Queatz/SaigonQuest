package main.components.worlddocs

import Translations
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.api.DocApi
import main.components.BaseDialog
import main.components.BasicConfirmDialog
import main.components.DialogButton
import main.components.IconRenderer
import main.components.Translated
import main.components.chat.ChatComposerController
import main.theme.Styles
import main.theme.WorldDocsStyles
import main.utils.toggleFullscreen
import models.Doc
import models.DocTree
import org.jetbrains.compose.web.attributes.autoFocus
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.flexGrow
import org.jetbrains.compose.web.css.flexShrink
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput
import org.w3c.dom.HTMLInputElement
import web.animations.awaitAnimationFrame

@Composable
fun WorldDocsDialog(
    worldId: String,
    worldName: String,
    isOwner: Boolean,
    currentUserId: String,
    onClose: () -> Unit,
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
    var isFullscreen by remember { mutableStateOf(false) }
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
            selectedDocId = docs.firstOrNull()?.doc?.key
        } catch (t: Throwable) {
            error = t.message ?: "Error"
        } finally {
            loading = false
        }
    }

    val flatDocs = remember(docs) { docs.flatMap { flatten(it) } }
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
        title = worldName.ifBlank { Translations.str("World docs") },
        isFullHeight = true,
        isFullWidth = true,
        isFullscreen = isFullscreen,
        headerActions = {
            Button(attrs = {
                classes(Styles.textButton)
                onClick { toggleFullscreen() }
            }) {
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
                                    ancestorsOf(docs, doc.key).all { anc -> anc.permissions.worldUsersCanEdit }
                            )
                }
            }

            Div(attrs = { classes(WorldDocsStyles.headerBar) }) {
                Input(type = org.jetbrains.compose.web.attributes.InputType.Text) {
                    classes(WorldDocsStyles.searchInput)
                    value(searchInput)
                    onInput { ev -> searchInput = ev.value }
                    placeholder(Translations.str("Search docs"))
                }
                if (isOwner) {
                    Button(attrs = {
                        classes(Styles.primaryButton)
                        onClick {
                            scope.launch {
                                val result = runCatching {
                                    DocApi.createDoc(
                                        worldId = worldId,
                                        title = Translations.str("New doc"),
                                        parentId = null,
                                    )
                                }
                                result.onSuccess { created ->
                                    docs = docs + DocTree(doc = created, children = emptyList())
                                    selectedDocId = created.key
                                    renameTitle = created.title
                                    requestFocusOnTitle = true
                                    error = null
                                }
                                result.onFailure { t ->
                                    error = t.message
                                }
                            }
                        }
                    }) {
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
                                serverSearchIds != null -> docs.mapNotNull { filterTreeByIds(it, serverSearchIds!!) }
                                else -> docs.mapNotNull { filterTree(it, search) }
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
                                                    ancestorsOf(
                                                        docs,
                                                        d.key
                                                    ).all { ancestor -> ancestor.permissions.worldUsersCanEdit }
                                            )
                                },
                                expandPathToId = if (serverSearchIds != null) selectedDocId else null,
                                showAddButtonForSelected = isOwner,
                                onCreateChild = { parentId ->
                                    scope.launch {
                                        val createdRes = runCatching {
                                            DocApi.createDoc(
                                                worldId = worldId,
                                                title = Translations.str("New doc"),
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
                                val ancestors = ancestorsOf(docs, selectedDoc.key)
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
                                                        docs = docs.map { updateNode(it, updated) }
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
                                            TextInput(value = renameTitle) {
                                                classes(WorldDocsStyles.titleInput)
                                                onInput { ev -> renameTitle = ev.value }
                                                placeholder(Translations.str("Title"))
                                                autoFocus()

                                                onKeyDown { ev ->
                                                    if (ev.key == "Enter") {
                                                        ev.preventDefault()
                                                        saveTitle()
                                                    }
                                                }

                                                ref {
                                                    titleInputEl = it
                                                    scope.launch {
                                                        delay(100)
                                                        awaitAnimationFrame()
                                                        it.focus()
                                                    }
                                                    onDispose { titleInputEl = null }
                                                }
                                            }
                                            // Collaborators indicator just under the title input
                                            if (collaborators.isNotEmpty()) {
                                                PresenceIndicator(users = collaborators)
                                            }
                                        }
                                        val trimmed = renameTitle.trim()
                                        if (trimmed != selectedDoc.title) {
                                            Button(attrs = {
                                                classes(Styles.primaryButton)
                                                if (trimmed.isBlank()) disabled()
                                                onClick {
                                                    saveTitle()
                                                }
                                            }) { Translated("Save") }
                                        }
                                    }

                                    if (isOwner) {
                                        // History button
                                        Button(attrs = {
                                            classes(Styles.secondaryButton)
                                            onClick { showHistoryDialog = true }
                                        }) {
                                            Translated("History")
                                        }

                                        // Delete
                                        val selectedNode = docs.firstNotNullOfOrNull { findNode(it, selectedDoc.key) }
                                        val hasChildren = selectedNode?.children?.isNotEmpty() == true
                                        Button(attrs = {
                                            classes(Styles.secondaryButton)
                                            if (hasChildren) {
                                                disabled()
                                                attr("title", Translations.str("Cannot delete: this doc has children"))
                                            }
                                            onClick {
                                                if (!hasChildren) showDeleteConfirm = true
                                            }
                                        }) { Translated("Delete") }
                                    }

                                    // Move up/down among siblings (editors and owners)
                                    if (canEditDoc) {
                                        val siblings = siblingsOf(docs, selectedDoc.parentId)
                                        val idx = siblings.indexOfFirst { it.key == selectedDoc.key }
                                        val canMoveUp = idx > 0
                                        val canMoveDown = idx >= 0 && idx < siblings.lastIndex

                                        Button(attrs = {
                                            classes(Styles.secondaryButton)
                                            if (!canMoveUp) disabled()
                                            onClick {
                                                if (canMoveUp) {
                                                    scope.launch {
                                                        val prev = siblings[idx - 1]
                                                        val beforePrev =
                                                            if (idx - 2 >= 0) siblings[idx - 2].orderIndex else null
                                                        val newIdx = newIndex(beforePrev, prev.orderIndex)
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
                                        }) { Translated("Move up") }

                                        Button(attrs = {
                                            classes(Styles.secondaryButton)
                                            if (!canMoveDown) disabled()
                                            onClick {
                                                if (canMoveDown) {
                                                    scope.launch {
                                                        val next = siblings[idx + 1]
                                                        val afterNext =
                                                            if (idx + 2 <= siblings.lastIndex) siblings[idx + 2].orderIndex else null
                                                        val newIdx = newIndex(next.orderIndex, afterNext)
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
                                        }) { Translated("Move down") }
                                    }
                                }

                                if (canEditDoc) {
                                    DocEditor(
                                        doc = selectedDoc,
                                        isEditable = true,
                                        currentUserId = currentUserId,
                                        onUpdated = { updated ->
                                            docs = docs.map { updateNode(it, updated) }
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
                                docs = removeNode(docs, id)
                                selectedDocId = docs.firstOrNull()?.doc?.key
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
                    isPrimary = true,
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
        val ancestors = ancestorsOf(docs, selectedDoc.key)
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
}

private fun flatten(node: DocTree): List<Doc> = listOf(node.doc) + node.children.flatMap { flatten(it) }

private fun updateNode(node: DocTree, updated: Doc): DocTree {
    return if (node.doc.key == updated.key) {
        node.copy(doc = updated)
    } else {
        node.copy(children = node.children.map { updateNode(it, updated) })
    }
}

private fun findNode(node: DocTree, id: String): DocTree? {
    if (node.doc.key == id) return node
    for (child in node.children) {
        val found = findNode(child, id)
        if (found != null) return found
    }
    return null
}

private fun removeNode(list: List<DocTree>, id: String): List<DocTree> {
    fun removeFrom(node: DocTree): DocTree? {
        if (node.doc.key == id) return null
        val newChildren = node.children.mapNotNull { removeFrom(it) }
        return node.copy(children = newChildren)
    }
    return list.mapNotNull { removeFrom(it) }
}

private fun filterTree(node: DocTree, query: String): DocTree? {
    if (query.isBlank()) return node
    val childMatches = node.children.mapNotNull { filterTree(it, query) }
    val selfMatches = node.doc.title.contains(query, ignoreCase = true)
    return if (selfMatches || childMatches.isNotEmpty()) node.copy(children = childMatches) else null
}

private fun filterTreeByIds(node: DocTree, ids: Set<String>): DocTree? {
    val childMatches = node.children.mapNotNull { filterTreeByIds(it, ids) }
    val selfMatches = node.doc.key in ids
    return if (selfMatches || childMatches.isNotEmpty()) node.copy(children = childMatches) else null
}

private fun siblingsOf(forest: List<DocTree>, parentId: String?): List<Doc> {
    fun findById(node: DocTree, id: String): DocTree? {
        if (node.doc.key == id) return node
        for (c in node.children) {
            val found = findById(c, id)
            if (found != null) return found
        }
        return null
    }

    val siblings: List<Doc> = if (parentId == null) {
        forest.map { it.doc }
    } else {
        val parent = forest.asSequence().mapNotNull { findById(it, parentId) }.firstOrNull()
        parent?.children?.map { it.doc } ?: emptyList()
    }
    return siblings.sortedBy { it.orderIndex }
}

private fun newIndex(before: Double?, after: Double?): Double = when {
    before == null && after == null -> 0.0
    before == null -> after!! - 1.0
    after == null -> before + 1.0
    else -> (before + after) / 2.0
}


// Ancestors utility for permission inheritance on the client
private fun ancestorsOf(forest: List<DocTree>, id: String): List<Doc> {
    // Find the node in the forest
    fun findById(node: DocTree, needle: String): DocTree? {
        if (node.doc.key == needle) return node
        for (c in node.children) {
            val found = findById(c, needle)
            if (found != null) return found
        }
        return null
    }

    val startDoc = forest.asSequence().mapNotNull { findById(it, id) }.firstOrNull()?.doc
        ?: return emptyList()
    val result = mutableListOf<Doc>()
    var parentId = startDoc.parentId
    while (parentId != null) {
        val parent = forest.asSequence().mapNotNull { findById(it, parentId!!) }.firstOrNull()
        if (parent != null) {
            result += parent.doc
            parentId = parent.doc.parentId
        } else {
            break
        }
    }
    return result
}
