package main.components.nodeeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.ObjectApi
import main.components.translated
import main.theme.NodeEditorStyles
import models.LogicNode
import models.NodeCategory
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Text

/**
 * Sidebar component for the node editor that contains draggable node items.
 * This component displays categorized lists of node types that can be dragged onto the canvas.
 * Node types are loaded from the backend.
 */
@Composable
fun NodeSidebar() {
    // State to store node types grouped by category
    var nodeTypesByCategory by remember { mutableStateOf<Map<NodeCategory, List<LogicNode>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Create a coroutine scope for async operations
    val coroutineScope = rememberCoroutineScope()
    
    // Load node types when the component is mounted
    DisposableEffect(Unit) {
        coroutineScope.launch {
            try {
                // Fetch node types from the backend
                nodeTypesByCategory = ObjectApi.getNodeTypesByCategory()
                isLoading = false
            } catch (e: Exception) {
                error = e.message
                isLoading = false
            }
        }
        
        onDispose { }
    }
    
    Div(
        attrs = {
            classes(NodeEditorStyles.sidebarContainer)
        }
    ) {
        if (isLoading) {
            // Show loading state
            Div {
                Text(translated("Loading node types..."))
            }
        } else if (error != null) {
            // Show error state
            Div {
                Text("${translated("Error loading node types")}: $error")
            }
        } else {
            // Render node types grouped by category
            nodeTypesByCategory.forEach { (category, nodes) ->
                // Skip empty categories
                if (nodes.isEmpty()) return@forEach
                
                // Category header
                H4(
                    attrs = {
                        classes(NodeEditorStyles.sectionHeader)
                    }
                ) {
                    Text(
                        when(category) {
                            NodeCategory.Action -> translated("Actions")
                            NodeCategory.Dialogue -> translated("Dialogues")
                            NodeCategory.Event -> translated("Events")
                            NodeCategory.Input -> translated("Inputs")
                        }
                    )
                }
                
                // List of draggable items for this category
                nodes.forEach { node ->
                    DraggableItem(node)
                }
            }
        }
    }
}
