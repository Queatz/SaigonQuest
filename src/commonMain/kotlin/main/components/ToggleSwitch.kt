package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.attributes.*

/**
 * A reusable toggle switch component that can be used for boolean settings.
 * 
 * @param checked Whether the toggle switch is checked (on) or not (off)
 * @param onCheckedChange Callback function that is called when the toggle switch is toggled
 * @param id Optional ID for the input element (useful for accessibility)
 */
@Composable
fun ToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    id: String? = null
) {
    // Toggle switch container with label for the checkbox
    Label(
        attrs = {
            classes(Styles.toggleSwitchContainer)
        }
    ) {
        // Actual checkbox input (hidden visually but still accessible)
        Input(
            type = InputType.Checkbox,
            attrs = {
                classes(Styles.toggleSwitchInput)
                
                // Set checked state based on parameter
                checked(checked)
                
                // Handle change event
                onChange { 
                    onCheckedChange(!checked)
                }
                
                // Add ID if provided
                if (id != null) {
                    id(id)
                }
            }
        )
        
        // Switch track (styled span)
        Span(
            attrs = {
                classes(Styles.toggleSwitchTrack)
                if (checked) {
                    classes(Styles.toggleSwitchTrackOn)
                } else {
                    classes(Styles.toggleSwitchTrackOff)
                }
            }
        )
        
        // Switch knob (styled span)
        Span(
            attrs = {
                classes(Styles.toggleSwitchKnob)
                if (checked) {
                    classes(Styles.toggleSwitchKnobOn)
                } else {
                    classes(Styles.toggleSwitchKnobOff)
                }
            }
        )
    }
}
