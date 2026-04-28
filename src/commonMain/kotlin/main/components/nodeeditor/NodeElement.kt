package main.components.nodeeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.window
import main.api.DocApi
import main.api.QuestApi
import main.components.translated
import main.theme.NodeEditorStyles
import main.utils.ellipsize
import models.LogicNode
import models.NodeCategory
import models.nodeeditor.NodeData
import models.nodeeditor.PortData
import models.nodeeditor.PropertyBasedNodeData
import models.nodeeditor.PropertyKey
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement
import org.w3c.dom.Touch
import org.w3c.dom.TouchEvent
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent

/**
 * A node element in the node editor.
 * 
 * @param id The unique identifier of the node
 * @param node The node data
 * @param isSelected Whether the node is currently selected
 * @param onSelect Callback when the node is selected
 * @param onEdit Callback when the node is edited
 * @param onPortClick Callback when a port on the node is clicked
 * @param onPortDragStart Callback when dragging starts from an output port
 * @param onPortDragMove Callback when dragging moves from an output port
 * @param onPortDragEnd Callback when dragging ends from an output port
 * @param onDrag Callback when the node is dragged to a new position
 */
@Composable
fun NodeElement(
    id: String,
    node: NodeData,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onPortClick: (String, PortData) -> Unit,
    onPortDragStart: (String, PortData, Float, Float) -> Unit = { _, _, _, _ -> },
    onPortDragMove: (Float, Float) -> Unit = { _, _ -> },
    onPortDragEnd: (Float, Float) -> Unit = { _, _ -> },
    onDrag: (String, Float, Float) -> Unit,
    controller: NodeEditorController
) {
    // Use the NodeData properties
    val type = node.type
    val label = node.label
    val inputs = node.inputs
    val outputs = node.outputs

    // Track the latest node position in state so event listeners always see current values
    var currentX by remember(id) { mutableStateOf(node.x) }
    var currentY by remember(id) { mutableStateOf(node.y) }
    // Keep in sync with incoming node updates
    androidx.compose.runtime.LaunchedEffect(node.x, node.y) {
        currentX = node.x
        currentY = node.y
    }

    // State for tracking drag operation
    var isDragging by remember { mutableStateOf(false) }
    // Pointer offset inside the node captured at drag start (in canvas coordinates)
    var pointerOffsetX by remember { mutableStateOf(0f) }
    var pointerOffsetY by remember { mutableStateOf(0f) }

    // State for document title (if this is a Doc Content node)
    var docTitle by remember(id) { mutableStateOf<String?>(null) }

    // State for quest name (if this node has a Quest ID)
    var questName by remember(id) { mutableStateOf<String?>(null) }

    // State for object name (if this node has an Object ID)
    var objectName by remember(id) { mutableStateOf<String?>(null) }

    // State for world name (if this node has a Target World ID)
    var worldName by remember(id) { mutableStateOf<String?>(null) }

    // Get doc title for Doc Content, Create Doc and Open Doc nodes
    LaunchedEffect(node.data) {
        if (type is LogicNode.DocContent || type is LogicNode.CreateDoc || type is LogicNode.OpenDoc) {
            val nodeData = NodeDataParser.parseNodeData(node.type, node.data)
            val docId = nodeData.properties.find { it.key == PropertyKey.ID }?.value
            if (!docId.isNullOrBlank()) {
                try {
                    val doc = DocApi.getDoc(docId)
                    docTitle = doc.title.ifBlank { "Selected doc" }
                } catch (t: Throwable) {
                    console.error("Failed to load doc title for NodeElement", t)
                    docTitle = null
                }
            } else {
                docTitle = null
            }
        }
    }

    // Get quest name
    LaunchedEffect(node.data) {
        val nodeData = NodeDataParser.parseNodeData(node.type, node.data)
        val questId = nodeData.properties.find { it.key == PropertyKey.QUEST_ID }?.value
        if (!questId.isNullOrBlank() && questId != "Quest ID") {
            try {
                val quest = QuestApi.getQuest(questId)
                questName = quest.name.ifBlank { "Selected quest" }
            } catch (t: Throwable) {
                // Only log if it's not a 404, which is expected during editing if ID is being typed or invalid
                val is404 = (t as? models.ErrorResponse)?.code == 404
                if (!is404) {
                    console.error("Failed to load quest name for NodeElement", t)
                }
                questName = null
            }
        } else {
            questName = null
        }
    }

    // Get object name
    LaunchedEffect(node.data) {
        if (type is LogicNode.LocateObject) {
            val nodeData = NodeDataParser.parseNodeData(node.type, node.data)
            val objectId = nodeData.properties.find { it.key == PropertyKey.ID }?.value
            if (!objectId.isNullOrBlank()) {
                try {
                    val obj = main.api.ObjectApi.getObject(objectId)
                    objectName = obj.title.ifBlank { "Selected object" }
                } catch (t: Throwable) {
                    // Only log if it's not a 404
                    val is404 = (t as? models.ErrorResponse)?.code == 404
                    if (!is404) {
                        console.error("Failed to load object name for NodeElement", t)
                    }
                    objectName = null
                }
            } else {
                objectName = null
            }
        }
    }

    // Get world name
    LaunchedEffect(node.data) {
        val nodeData = NodeDataParser.parseNodeData(node.type, node.data)
        val worldId = nodeData.properties.find { it.key == PropertyKey.TARGET_WORLD_ID }?.value
        if (!worldId.isNullOrBlank()) {
            try {
                val world = main.api.WorldApi.getWorld(worldId)
                worldName = world.title.ifBlank { "Selected world" }
            } catch (t: Throwable) {
                // Only log if it's not a 404
                val is404 = (t as? models.ErrorResponse)?.code == 404
                if (!is404) {
                    console.error("Failed to load world name for NodeElement", t)
                }
                worldName = null
            }
        } else {
            worldName = null
        }
    }

    // Global event handlers for mouse move and mouse up
    val handleGlobalMouseMove = remember {
        { event: Event ->
            if (isDragging && event is MouseEvent) {
                val (canvasX, canvasY) = controller.screenToCanvasCoordinates(
                    event.clientX.toFloat(),
                    event.clientY.toFloat(),
                )
                val newX = canvasX - pointerOffsetX
                val newY = canvasY - pointerOffsetY
                onDrag(id, newX, newY)
                event.stopPropagation()
                event.preventDefault()
            }
        }
    }

    val handleGlobalMouseUp = remember {
        { event: Event ->
            if (isDragging && event is MouseEvent) {
                isDragging = false
                controller.endNodeDrag()
                event.stopPropagation()
                event.preventDefault()
            }
        }
    }

    var nodeElementRef by remember { mutableStateOf<HTMLElement?>(null) }

    // Add/remove global event listeners when dragging state changes
    DisposableEffect(isDragging) {
        if (isDragging) {
            // Add global event listeners when dragging starts
            document.addEventListener("mousemove", handleGlobalMouseMove)
            document.addEventListener("mouseup", handleGlobalMouseUp)
        }

        // Cleanup function to remove event listeners
        onDispose {
            document.removeEventListener("mousemove", handleGlobalMouseMove)
            document.removeEventListener("mouseup", handleGlobalMouseUp)
        }
    }

    // Determine node color class based on type category
    val nodeColorClass = when (type.category) {
        NodeCategory.Event -> NodeEditorStyles.eventNodeColor
        NodeCategory.Dialogue -> NodeEditorStyles.dialogueNodeColor
        NodeCategory.Action -> NodeEditorStyles.actionNodeColor
        NodeCategory.Input -> NodeEditorStyles.inputNodeColor
        NodeCategory.Quest -> NodeEditorStyles.questNodeColor
    }

    // Effect to register the node element with the controller
    DisposableEffect(nodeElementRef) {
        // Use a small delay to ensure the DOM element is rendered
        if (nodeElementRef == null) return@DisposableEffect onDispose { }

        val nodeElementRef = nodeElementRef

        println("[NODE_CONNECTIONS] Registering node element: $id")
        // Register the node element with the controller
        controller.registerNodeElement(id, nodeElementRef!!)

        onDispose {
            println("[NODE_CONNECTIONS] Disposing node element: $id with ${inputs.size} inputs and ${outputs.size} outputs")
            // Unregister the node element when the component is disposed
            controller.unregisterNodeElement(id, nodeElementRef)
        }
    }

    Div(
        attrs = {
            classes(NodeEditorStyles.nodeContainer)
            classes(nodeColorClass)
            classes(if (isSelected) NodeEditorStyles.nodeSelected else NodeEditorStyles.nodeUnselected)
            classes(if (isDragging) NodeEditorStyles.nodeDragging else NodeEditorStyles.nodeStatic)

            style {
                left(currentX.px)
                top(currentY.px)
            }

            onClick { event ->
                onSelect()
                event.stopPropagation()
            }

            // Add mouse event handler for starting the drag operation
            onMouseDown { event ->
                if (event.button.toInt() == 0) { // Left mouse button
                    isDragging = true
                    val (canvasX, canvasY) = controller.screenToCanvasCoordinates(
                        event.clientX.toFloat(),
                        event.clientY.toFloat(),
                    )
                    pointerOffsetX = canvasX - currentX
                    pointerOffsetY = canvasY - currentY
                    controller.beginNodeDrag()
                    event.stopPropagation()
                    event.preventDefault()
                }
            }

            // Note: We no longer need onMouseMove and onMouseUp handlers on the node element
            // as we now have global event listeners that handle these events at the document level.
            // This allows the drag operation to continue even when the mouse leaves the node element.

            // Add node data attributes
            attr("data-node-id", id)
            attr("data-node-type", type.category.name)

            ref { ref ->
                nodeElementRef = ref

                // Touch interactions: distinguish tap to select vs drag to move
                var onWindowTouchMove: ((Event) -> Unit)? = null
                var onWindowTouchEnd: ((Event) -> Unit)? = null

                // Gesture state
                var touchStartX = 0f
                var touchStartY = 0f
                var dragStarted = false
                val dragThreshold = 8f // pixels

                val touchStartHandler: (Event) -> Unit = { ev ->
                    ev.preventDefault()
                    ev.stopPropagation()
                    val te = ev as? TouchEvent
                    if (te != null && te.touches.length == 1) {
                        val t: Touch = te.touches.item(0)!!
                        dragStarted = false
                        isDragging = false
                        touchStartX = t.clientX.toFloat()
                        touchStartY = t.clientY.toFloat()

                        val move: (Event) -> Unit = { mev ->
                            mev.stopPropagation()
                            mev.preventDefault()
                            val mte = mev as TouchEvent
                            val touch = mte.touches.item(0)
                            if (touch != null) {
                                val dx = touch.clientX.toFloat() - touchStartX
                                val dy = touch.clientY.toFloat() - touchStartY
                                val dist2 = dx * dx + dy * dy
                                if (!dragStarted && dist2 > dragThreshold * dragThreshold) {
                                    // Start drag
                                    dragStarted = true
                                    isDragging = true
                                    controller.beginNodeDrag()

                                    val (canvasX, canvasY) = controller.screenToCanvasCoordinates(
                                        touch.clientX.toFloat(),
                                        touch.clientY.toFloat(),
                                    )
                                    pointerOffsetX = canvasX - currentX
                                    pointerOffsetY = canvasY - currentY

                                    // Prevent native gestures while dragging the node
                                    ref.style.setProperty("touch-action", "none")
                                    ref.style.setProperty("user-select", "none")
                                    // Also prevent the canvas wrapper from scrolling during drag
                                    controller.wrapperElement?.style?.setProperty("touch-action", "none")
                                    controller.wrapperElement?.style?.setProperty("overscroll-behavior", "contain")
                                    controller.canvasElement?.style?.setProperty("touch-action", "none")
                                }

                                if (dragStarted) {
                                    val (cX, cY) = controller.screenToCanvasCoordinates(
                                        touch.clientX.toFloat(),
                                        touch.clientY.toFloat(),
                                    )
                                    val newX = cX - pointerOffsetX
                                    val newY = cY - pointerOffsetY
                                    onDrag(id, newX, newY)
                                    mev.preventDefault()
                                    mev.stopPropagation()
                                }
                            }
                        }
                        val ender: (Event) -> Unit = { e2 ->
                            e2.stopPropagation()
                            e2.preventDefault()
                            // If we never started a drag, treat as a tap selection
                            if (!dragStarted) {
                                onSelect()
                            }
                            // End any drag state and cleanup
                            isDragging = false
                            if (dragStarted) controller.endNodeDrag()
                            ref.style.removeProperty("touch-action")
                            ref.style.removeProperty("user-select")
                            controller.wrapperElement?.style?.removeProperty("touch-action")
                            controller.wrapperElement?.style?.removeProperty("overscroll-behavior")
                            controller.canvasElement?.style?.removeProperty("touch-action")
                            onWindowTouchMove?.let { window.removeEventListener("touchmove", it, false) }
                            onWindowTouchEnd?.let {
                                window.removeEventListener("touchend", it, false)
                                window.removeEventListener("touchcancel", it, false)
                            }
                            onWindowTouchMove = null
                            onWindowTouchEnd = null
                            e2.preventDefault()
                            e2.stopPropagation()
                        }

                        onWindowTouchMove = move
                        onWindowTouchEnd = ender
                        window.addEventListener("touchmove", move, false)
                        window.addEventListener("touchend", ender, false)
                        window.addEventListener("touchcancel", ender, false)
                        // Do not preventDefault here to allow taps to work naturally; we handle it on end
                    }
                }

                ref.addEventListener("touchstart", touchStartHandler, false)

                onDispose {
                    ref.removeEventListener("touchstart", touchStartHandler, false)
                    onWindowTouchMove?.let { window.removeEventListener("touchmove", it, false) }
                    onWindowTouchEnd?.let {
                        window.removeEventListener("touchend", it, false)
                        window.removeEventListener("touchcancel", it, false)
                    }
                    onWindowTouchMove = null
                    onWindowTouchEnd = null
                    // Restore styles just in case
                    ref.style.removeProperty("touch-action")
                    ref.style.removeProperty("user-select")
                    controller.wrapperElement?.style?.removeProperty("touch-action")
                    controller.wrapperElement?.style?.removeProperty("overscroll-behavior")
                    controller.canvasElement?.style?.removeProperty("touch-action")
                    if (isDragging) controller.endNodeDrag()
                    nodeElementRef = null
                }
            }
        }
    ) {
        // Node header
        Div(
            attrs = {
                classes(NodeEditorStyles.nodeHeader)
            }
        ) {
            Text(translated(label))
        }

        // Node description
        Div(
            attrs = {
                classes(NodeEditorStyles.nodeDescription)
            }
        ) {
            val nodeData = remember(node.type, node.data) {
                NodeDataParser.parseNodeData(node.type, node.data)
            }

            Div {
                nodeData.properties.forEach { property ->
                    when (property.key) {
                        PropertyKey.TEXT_CONTENT, PropertyKey.TITLE -> {
                            if (property.value.isNotBlank()) {
                                Div {
                                    Text((property.value).ellipsize())
                                }
                            }
                        }

                        PropertyKey.TARGET_WORLD_ID -> {
                            Div {
                                val displayWorld = when {
                                    worldName != null -> worldName
                                    property.value.isBlank() -> "Not selected"
                                    else -> "..."
                                }
                                Text("To \"${displayWorld}\"")
                            }
                        }

                        PropertyKey.SOUND_URL -> {
                            Div {
                                Text("🎵 " + property.value.substringAfterLast('/').ellipsize())
                            }
                        }

                        PropertyKey.ITEM_ID -> {
                            if (property.value.isNotBlank()) {
                                Div {
                                    Text(property.value)
                                }
                            }
                        }

                        PropertyKey.ID -> {
                            if (type is LogicNode.DocContent || type is LogicNode.CreateDoc || type is LogicNode.OpenDoc || type is LogicNode.LocateObject) {
                                Div {
                                    val displayTitle = when {
                                        type is LogicNode.LocateObject -> if (objectName != null) objectName else if (property.value.isBlank()) "Not selected" else "..."
                                        docTitle != null -> docTitle
                                        property.value.isBlank() -> if (type is LogicNode.CreateDoc) "None" else "Not selected"
                                        else -> "..."
                                    }
                                    Text(displayTitle?.ellipsize().orEmpty())
                                }
                            }
                        }

                        PropertyKey.QUEST_ID -> {
                            if (property.value.isNotBlank() && property.value != "Quest ID") {
                                Div {
                                    val displayQuest = when {
                                        questName != null -> questName
                                        property.value.isBlank() -> "Not selected"
                                        else -> "..."
                                    }

                                    val stepSuffix = if (type is LogicNode.QuestStep || type is LogicNode.UpdatePlayerQuestStep) {
                                        val stepIndex = nodeData.properties.find { it.key == PropertyKey.STEP_INDEX }?.value ?: "0"
                                        " (Step $stepIndex)"
                                    } else ""

                                    Text("Quest: ${displayQuest?.ellipsize().orEmpty()}$stepSuffix")
                                }
                            }
                        }

                        PropertyKey.QUEST_STATE_ID -> {
                            if (property.value.isNotBlank()) {
                                Div {
                                    Text("State: ${property.value}")
                                }
                            }
                        }

                        PropertyKey.VALUE -> {
                            if (property.value.isNotBlank() && (type is LogicNode.UpdateQuestState || type is LogicNode.UpdatePlayerQuestState)) {
                                Div {
                                    Text("To: ${property.value}")
                                }
                            }
                        }

                        PropertyKey.OPERATOR, PropertyKey.MATH_OPERATOR -> {
                            if (property.value.isNotBlank()) {
                                Div {
                                    Text("Op: ${property.value}")
                                }
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }

        // Node content with inputs and outputs
        Div(
            attrs = {
                classes(NodeEditorStyles.nodeContent)
            }
        ) {
            // Input ports
            inputs.forEach { port ->
                key(port) {
                    PortElement(
                        port = port,
                        nodeId = id,
                        onPortClick = onPortClick,
                        onPortElementCreated = { element ->
                            println("[NODE_CONNECTIONS] NodeElement registering input port: $id:${port.id}")
                            // Register the port element with the controller
                            controller.registerPortElement(id, port.id, port.type, element)
                        },
                        onPortElementDisposed = {
                            controller.unregisterPortElement(id, port.id, port.type, it)
                        }
                    )
                }
            }

            // Divider if both inputs and outputs exist
            if (inputs.isNotEmpty() && outputs.isNotEmpty()) {
                Div(
                    attrs = {
                        classes(NodeEditorStyles.nodeDivider)
                    }
                )
            }

            // Output ports
            outputs.forEach { port ->
                key(port) {
                    PortElement(
                        port = port,
                        nodeId = id,
                        onPortClick = onPortClick,
                        onPortDragStart = onPortDragStart,
                        onPortDragMove = onPortDragMove,
                        onPortDragEnd = onPortDragEnd,
                        onPortElementCreated = { element ->
                            println("[NODE_CONNECTIONS] NodeElement registering output port: $id:${port.id}")
                            // Register the port element with the controller
                            controller.registerPortElement(id, port.id, port.type, element)
                        },
                        onPortElementDisposed = {
                            controller.unregisterPortElement(id, port.id, port.type, it)
                        }
                    )
                }
            }
        }
    }
}
