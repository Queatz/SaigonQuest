package main.components.nodeeditor

import main.theme.Theme
import models.NodeCategory
import org.jetbrains.compose.web.css.CSSColorValue

/**
 * Utility function to get the color for a node category.
 * This can be used for both node backgrounds and connection colors.
 */
fun getNodeColor(category: NodeCategory): CSSColorValue {
    return when (category) {
        NodeCategory.Event -> Theme.colors.nodeEvent
        NodeCategory.Action -> Theme.colors.nodeAction
        NodeCategory.Dialogue -> Theme.colors.nodeDialogue
        NodeCategory.Input -> Theme.colors.nodeInput
        NodeCategory.Quest -> Theme.colors.nodeQuest
    }
}
