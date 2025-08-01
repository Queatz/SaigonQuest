package main.components.nodeeditor

import androidx.compose.runtime.Composable
import main.theme.NodeEditorStyles
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Text

/**
 * Sidebar component for the node editor that contains draggable node items.
 * This component displays categorized lists of node types that can be dragged onto the canvas.
 */
@Composable
fun NodeSidebar() {
    Div(
        attrs = {
            classes(NodeEditorStyles.sidebarContainer)
        }
    ) {
        // Events section
        H4(
            attrs = {
                classes(NodeEditorStyles.sectionHeader)
            }
        ) {
            Text("Events")
        }
        
        // List of draggable events
        DraggableItem("Visitor nearby", "event", "visitor_nearby", "Triggers when a visitor comes within range")
        DraggableItem("Visitor interact", "event", "visitor_interact", "Triggers when a visitor interacts with this object")
        DraggableItem("Player nearby", "event", "player_nearby", "Triggers when the player comes within range")
        DraggableItem("Player interact", "event", "player_interact", "Triggers when the player interacts with this object")
        DraggableItem("Local time", "event", "local_time", "Triggers at specific times of day")
        
        // Dialogue section
        H4(
            attrs = {
                classes(NodeEditorStyles.sectionHeader)
            }
        ) {
            Text("Dialogue")
        }
        
        // List of draggable dialogue options
        DraggableItem("Present text", "dialogue", "present_text", "Display text message to the player")
        DraggableItem("Show buttons", "dialogue", "show_buttons", "Present multiple choice options to the player")
        
        // Actions section
        H4(
            attrs = {
                classes(NodeEditorStyles.sectionHeader)
            }
        ) {
            Text("Actions")
        }
        
        // List of draggable actions
        DraggableItem("Teleport player", "action", "teleport", "Move player to a different location")
        DraggableItem("Play sound", "action", "play_sound", "Play a sound effect or music")
        DraggableItem("Show effect", "action", "show_effect", "Display a visual effect in the world")
        
        // Inputs section
        H4(
            attrs = {
                classes(NodeEditorStyles.sectionHeader)
            }
        ) {
            Text("Inputs")
        }
        
        // List of draggable inputs
        DraggableItem("Player info", "input", "player_info", "Access player data like name, level, etc.")
        DraggableItem("Visitor info", "input", "visitor_info", "Access visitor data like type, preferences, etc.")
        DraggableItem("World info", "input", "world_info", "Access world data like time, weather, etc.")
        DraggableItem("Object info", "input", "object_info", "Access object properties and state")
    }
}
