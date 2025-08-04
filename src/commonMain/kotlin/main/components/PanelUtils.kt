package main.components

import androidx.compose.runtime.State

/**
 * Extension property that checks if a SidePanelState is one that affects layout.
 * These are panels that, when open on desktop, require UI adjustments.
 */
val SidePanelState.isLayoutAffectingPanel: Boolean
    get() = this == SidePanelState.Chat || 
            this == SidePanelState.WorldEdit || 
            this == SidePanelState.Notifications || 
            this == SidePanelState.ObjectsPanel ||
            this == SidePanelState.Friends

/**
 * Utility function that checks if a layout-affecting panel is open and the device is not mobile.
 * This is used to determine when UI elements need to be repositioned to account for the side panel.
 *
 * @param currentPanel The current panel state
 * @param isMobile State boolean indicating if the device is mobile
 * @return True if a layout-affecting panel is open and the device is not mobile
 */
fun isLayoutAdjustmentNeeded(currentPanel: SidePanelState, isMobile: State<Boolean>): Boolean {
    return currentPanel.isLayoutAffectingPanel && !isMobile.value
}
