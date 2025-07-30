package main.components

/**
 * Represents the state of the side panel in the UI.
 */
sealed class SidePanelState {
    object None : SidePanelState()
    object Chat : SidePanelState()
    object Inventory : SidePanelState()
    object Profile : SidePanelState()
    object WorldEdit : SidePanelState()
    object ObjectEditor : SidePanelState()
    object ObjectsPanel : SidePanelState()
    object LogicEditor : SidePanelState()
    object Notifications : SidePanelState()
    object Friends : SidePanelState()
}
