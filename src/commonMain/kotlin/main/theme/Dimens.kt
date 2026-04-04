package main.theme

import org.jetbrains.compose.web.css.*

object Dimens {
    const val SIDE_PANEL_WIDTH_PX = 480
    const val UI_BASE_MARGIN_PX = 20
    const val UI_GAP_PX = 20
    const val LAYOUT_SHIFT_PX = SIDE_PANEL_WIDTH_PX + UI_BASE_MARGIN_PX + UI_GAP_PX
    
    val sidePanelWidth = SIDE_PANEL_WIDTH_PX.px
    val sidePanelMargin = 1.cssRem
    val uiBaseMargin = UI_BASE_MARGIN_PX.px
    
    /**
     * Total offset from the right edge for UI elements when a side panel is open on desktop.
     * Calculated as: sidePanelWidth + uiBaseMargin + uiGap
     * Previous value: 360px (320 + 20 + 20)
     * New value: 520px (480 + 20 + 20)
     */
    val layoutShift = LAYOUT_SHIFT_PX.px

    /**
     * Maximum width for images inside chat messages.
     * We aim to keep some padding (e.g. 40px) inside the panel.
     * Previous value: 360px
     * New value: 440px (480 - 40)
     */
    val chatImageMaxWidth = (SIDE_PANEL_WIDTH_PX - 40).px
}
