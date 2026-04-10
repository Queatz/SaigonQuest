package main.components.nodeeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.coroutines.launch
import main.api.ObjectApi
import main.components.BaseDialog
import main.components.DialogButton
import main.components.DialogButtonStyle
import main.components.IconRenderer
import main.components.Translated
import main.components.rememberIsMobile
import main.components.translated
import main.theme.NodeEditorStyles
import main.theme.Styles
import main.utils.toggleFullscreen
import models.Object
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.events.SyntheticKeyboardEvent

/**
 * A dialog for editing object logic.
 *
 * @param objectModel The object being edited
 * @param onClose Callback when the dialog is closed
 * @param onSave Callback when an object is saved
 */
@Composable
fun LogicEditorDialog(
    objectModel: Object,
    onClose: () -> Unit,
    onSave: (Object) -> Unit,
) {
    // State for form fields
    var title by remember { mutableStateOf(objectModel.title) }

    // State for fullscreen mode
    var isFullscreen by remember { mutableStateOf(document.fullscreenElement != null) }

    // State for form submission
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Set dialog title
    val dialogTitle =
        "${translated("Edit")} ${objectModel.title.ifBlank { translated("Object") }} ${translated("Logic")}"

    // Create a coroutine scope for async operations
    val coroutineScope = rememberCoroutineScope()

    // Initialize the NodeEditorController with initial nodes and connections
    val controller = remember {
        NodeEditorController(
            initialNodes = parseNodes(objectModel.data),
            initialConnections = parseConnections(objectModel.data),
        )
    }

    // Mobile detection and panel visibility state
    val isMobileState = rememberIsMobile()
    val isMobile = isMobileState.value
    var showSidebar by remember(isMobile) { mutableStateOf(!isMobile) }
    var showProperties by remember(isMobile) { mutableStateOf(!isMobile) }

    // State for node picker on double-click
    var showNodePicker by remember { mutableStateOf(false) }
    var pendingInsertPos by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    // Update fullscreen state when fullscreen changes
    DisposableEffect(Unit) {
        val fullscreenChangeListener: (dynamic) -> Unit = {
            isFullscreen = document.fullscreenElement != null
        }

        document.addEventListener("fullscreenchange", fullscreenChangeListener)

        onDispose {
            document.removeEventListener("fullscreenchange", fullscreenChangeListener)
        }
    }

    // Global keyboard handler to ensure Delete/Undo/Redo work regardless of focus (e.g., after dragging)
    DisposableEffect(controller) {
        val keydownListener = fun(ev: org.w3c.dom.events.Event) {
            val kev = ev as? org.w3c.dom.events.KeyboardEvent ?: return

            // Ignore when user is typing in an input/textarea or contentEditable element
            val targetEl = kev.target as? org.w3c.dom.Element
            val tag = targetEl?.tagName?.lowercase()
            val isTypingTarget = tag == "input" || tag == "textarea" ||
                (targetEl?.getAttribute("contenteditable")?.lowercase() == "true")
            if (isTypingTarget) return

            when {
                kev.key == "Delete" || kev.key == "Backspace" -> {
                    when {
                        controller.selectedNode != null -> {
                            controller.deleteNode(controller.selectedNode!!)
                            kev.preventDefault()
                        }
                        controller.selectedConnection != null -> {
                            controller.deleteConnection(controller.selectedConnection!!)
                            kev.preventDefault()
                        }
                    }
                }
                kev.key.lowercase() == "z" && kev.ctrlKey && !kev.shiftKey -> {
                    controller.undo()
                    kev.preventDefault()
                }
                (kev.key.lowercase() == "y" && kev.ctrlKey) ||
                (kev.key.lowercase() == "z" && kev.ctrlKey && kev.shiftKey) -> {
                    controller.redo()
                    kev.preventDefault()
                }
            }
        }

        document.addEventListener("keydown", keydownListener)
        onDispose {
            document.removeEventListener("keydown", keydownListener)
        }
    }

    // Function to handle form submission
    val handleSubmit = {
        isSubmitting = true
        errorMessage = ""

        // Serialize node editor data to JSON
        val nodeData = controller.serializeToJson()

        // Update existing object
        val newObject = objectModel.copy(
            title = title,
            data = nodeData
        )

        coroutineScope.launch {
            try {
                // Update the object
                val savedObject = ObjectApi.updateObject(objectModel.key, newObject)

                isSubmitting = false
                onSave(savedObject)
                onClose()
            } catch (error: Throwable) {
                isSubmitting = false
                errorMessage = error.message ?: Translations.str("An error occurred")
            }
        }
    }

    // Keyboard event handler for the node editor
    val handleKeyDown = { event: SyntheticKeyboardEvent ->
        // Check if delete key is pressed
        if (event.key == "Delete" || event.key == "Backspace") {
            // If a node is selected, delete it
            if (controller.selectedNode != null) {
                controller.deleteNode(controller.selectedNode!!)
                event.preventDefault()
            }
            // If a connection is selected, delete it
            else if (controller.selectedConnection != null) {
                controller.deleteConnection(controller.selectedConnection!!)
                event.preventDefault()
            }
        }
        // Check for undo (Ctrl+Z)
        else if (event.key.lowercase() == "z" && event.ctrlKey && !event.shiftKey) {
            controller.undo()
            event.preventDefault()
        }
        // Check for redo (Ctrl+Y or Ctrl+Shift+Z)
        else if ((event.key.lowercase() == "y" && event.ctrlKey) ||
            (event.key.lowercase() == "z" && event.ctrlKey && event.shiftKey)
        ) {
            controller.redo()
            event.preventDefault()
        }
    }

    // Use BaseDialog with isFullscreen parameter
    BaseDialog(
        onClose = onClose,
        title = dialogTitle,
        clickOutside = false,
        isFullHeight = true,
        isFullWidth = true,
        isFullscreen = isFullscreen,
        // Custom header actions for fullscreen toggle
        headerActions = {
            DialogButton(
                onClick = { toggleFullscreen() },
                style = DialogButtonStyle.Secondary,
                small = true
            ) {
                IconRenderer(
                    url = "raw/data/ui/ui.png",
                    index = if (isFullscreen) 1 else 0,
                    size = 24
                )
            }
        },
        // Main content
        content = {
            // Node editor content area
            Div(
                attrs = {
                    classes(NodeEditorStyles.mainContentArea)
                }
            ) {
                // Toolbar and panel toggles (especially for mobile)
                if (isMobile) {
                    Div(
                        attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                gap(8.px)
                            }
                        }
                    ) {
                        DialogButton(
                            style = DialogButtonStyle.Primary,
                            onClick = { showSidebar = !showSidebar },
                            small = true
                        ) { Translated(if (showSidebar) "Hide nodes" else "Nodes") }

                        DialogButton(
                            style = DialogButtonStyle.Primary,
                            isDisabled = controller.selectedNode == null,
                            onClick = { showProperties = !showProperties },
                            small = true
                        ) { Translated(if (showProperties) "Hide properties" else "Properties") }

                        // Delete button available on both desktop and mobile
                        DialogButton(
                            style = DialogButtonStyle.Danger,
                            isDisabled = controller.selectedNode == null && controller.selectedConnection == null,
                            onClick = {
                                when {
                                    controller.selectedNode != null -> controller.deleteNode(controller.selectedNode!!)
                                    controller.selectedConnection != null -> controller.deleteConnection(controller.selectedConnection!!)
                                }
                            },
                            small = true
                        ) { Translated("Delete") }
                    }
                }

                // Left panel with draggable items (conditionally shown on mobile)
                if (!isMobile || showSidebar) {
                    NodeSidebar()
                }

                // Node editor panel
                NodeEditorPanel(
                    controller = controller,
                    onKeyDown = handleKeyDown,
                    onNodeAdd = { type, id, label, x, y ->
                        controller.addNode(type, id, label, x, y)
                    },
                    onCanvasDoubleClick = { x, y ->
                        pendingInsertPos = x to y
                        showNodePicker = true
                    },
                )

                // Property panel (shown when a node is selected)
                if ((!isMobile || showProperties) && controller.selectedNode != null) {
                    val node = controller.nodes[controller.selectedNode]
                    if (node != null) {
                        PropertyPanel(
                            nodeId = controller.selectedNode!!,
                            node = node,
                            worldId = objectModel.worldId,
                            onNodeUpdate = { nodeId, updatedNode ->
                                controller.updateNode(nodeId, updatedNode)
                            }
                        )
                    }
                }
            }

            // Node picker dialog
            if (showNodePicker) {
                NodePickerDialog(
                    onSelect = { node ->
                        val pos = pendingInsertPos
                        if (pos != null) {
                            controller.addNode(node.category, node.id, node.label, pos.first, pos.second)
                        }
                        // close handled in onClose of dialog
                    },
                    onClose = {
                        showNodePicker = false
                        pendingInsertPos = null
                    },
                )
            }

            // Show error message if present
            if (errorMessage.isNotEmpty()) {
                Span(
                    attrs = {
                        classes(NodeEditorStyles.errorMessage)
                    }
                ) {
                    Text(errorMessage)
                }
            }
        },
        // Footer actions
        actions = {
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        gap(8.px)
                    }
                }
            ) {
                // Cancel button
                DialogButton(
                    onClick = onClose
                ) {
                    Translated("Cancel")
                }

                // Save button
                DialogButton(
                    onClick = { if (!isSubmitting) handleSubmit() },
                    style = DialogButtonStyle.Primary
                ) {
                    Text(if (isSubmitting) translated("Saving...") else translated("Save"))
                }
            }
        }
    )
}
