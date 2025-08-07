package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import main.components.isLayoutAdjustmentNeeded
import models.World
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import web.cssom.opacity
import main.components.Translated

/**
 * A component that displays the world title and subtitle at the center top of the UI.
 * Adjusts its position based on whether a side panel is open and whether the device is mobile.
 *
 * @param currentWorld The current world being displayed
 * @param isWorldLoading Whether the world is currently loading
 * @param worldErrorMessage Error message if world loading failed
 * @param currentPanel The current panel state
 * @param isMobile Whether the device is mobile
 */
@Composable
fun WorldTitle(
    currentWorld: World?,
    isWorldLoading: Boolean,
    worldErrorMessage: String,
    currentPanel: SidePanelState,
    isMobile: State<Boolean>
) {
    Div(
        attrs = {
            style {
                position(Position.Absolute)
                top(20.px)
                // Adjust left position when any side panel is open on desktop
                if (isLayoutAdjustmentNeeded(currentPanel, isMobile)) {
                    // Center in the remaining space (excluding the 320px side panel)
                    property("left", "calc(50% - 160px)")
                } else {
                    left(50.percent)
                }
                property("transform", "translateX(-50%)")
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                alignItems(AlignItems.Center)
                property("pointer-events", "none")
            }
        }
    ) {
        H2(
            attrs = {
                style {
                    margin(0.px)
                    color(Color.white)
                    property("text-shadow", "0px 0px 4px rgba(0, 0, 0, 0.5)")
                }
            }
        ) {
            if (isWorldLoading) {
                Translated("Loading...")
            } else if (worldErrorMessage.isNotEmpty()) {
                Translated("Error loading world")
            } else {
                if (currentWorld?.title != null) {
                    Text(currentWorld.title)
                } else {
                    Translated("Untitled World")
                }
            }
        }
        
        P(
            attrs = {
                style {
                    margin(0.px)
                    color(Color.white)
                    property("text-shadow", "0px 0px 4px rgba(0, 0, 0, 0.5)")
                    opacity(.5)
                }
            }
        ) {
            if (!isWorldLoading && worldErrorMessage.isEmpty()) {
                Text(currentWorld?.subtitle ?: "")
            }
        }
    }
}
