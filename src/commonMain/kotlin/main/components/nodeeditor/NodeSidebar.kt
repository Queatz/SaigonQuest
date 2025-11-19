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
import Translations.str
import main.components.Translated
import main.theme.NodeEditorStyles
import main.theme.Styles
import models.LogicNode
import models.NodeCategory
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput
import models.UserRequestType
import main.components.UserRequestsDialog
import org.jetbrains.compose.web.dom.Button

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
    var searchQuery by remember { mutableStateOf("") }
    
    // Create a coroutine scope for async operations
    val coroutineScope = rememberCoroutineScope()
    
    // Load node types when the component is mounted
    DisposableEffect(Unit) {
        coroutineScope.launch {
            try {
                // Fetch node types from the backend
                nodeTypesByCategory = ObjectApi.getNodeTypesByCategory()
                isLoading = false
            } catch (e: Throwable) {
                error = e.message
                isLoading = false
            }
        }
        
        onDispose { }
    }
    
    var showRequestsDialog by remember { mutableStateOf(false) }

    Div(
        attrs = {
            classes(NodeEditorStyles.sidebarContainer)
        }
    ) {
        // Entry button to request new node type
        Div {
            Button(attrs = {
                classes(Styles.baseDialogButtonSecondary)
                onClick { showRequestsDialog = true }
            }) { Text("💡 "); Translated("Request new node type") }
        }

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
            // Search input
            Div {
                TextInput(
                    value = searchQuery,
                    attrs = {
                        classes(Styles.inputField)
                        placeholder(str("Search nodes"))
                        onInput { ev -> searchQuery = ev.value }
                    },
                )
            }
            
            // Compute filtered nodes by category
            val filteredByCategory: Map<NodeCategory, List<LogicNode>> =
                if (searchQuery.isBlank()) nodeTypesByCategory
                else nodeTypesByCategory
                    .mapValues { (_, nodes) ->
                        val q = searchQuery.trim()
                        nodes.filter { n ->
                            n.label.contains(q, ignoreCase = true) ||
                            n.description.contains(q, ignoreCase = true)
                        }
                    }
                    .filterValues { it.isNotEmpty() }
            
            // Render node types grouped by category
            filteredByCategory.forEach { (category, nodes) ->
                // Skip empty categories
                if (nodes.isEmpty()) return@forEach
                
                // Category header
                H4(
                    attrs = {
                        classes(NodeEditorStyles.sectionHeader)
                    },
                ) {
                    Text(
                        when (category) {
                            NodeCategory.Action -> translated("Actions")
                            NodeCategory.Dialogue -> translated("Dialogues")
                            NodeCategory.Event -> translated("Events")
                            NodeCategory.Input -> translated("Inputs")
                        },
                    )
                }
                
                // List of draggable items for this category
                nodes.forEach { node ->
                    DraggableNodeItem(node)
                }
            }
        }
    }

    if (showRequestsDialog) {
        UserRequestsDialog(
            preselectedType = UserRequestType.Node,
            onClose = { showRequestsDialog = false },
        )
    }
}
