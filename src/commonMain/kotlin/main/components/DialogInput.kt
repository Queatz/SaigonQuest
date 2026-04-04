package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.AttrBuilderContext
import org.jetbrains.compose.web.dom.Input
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLInputElement

/**
 * A reusable dialog input component with consistent styling.
 *
 * @param value The current value of the input
 * @param onInput Callback when the value changes (provides the new string value)
 * @param placeholder The placeholder text
 * @param type The input type (default to Text)
 * @param attrs Additional attributes for the input element
 * @param wrapperAttrs Additional attributes for the outer container (FramedBorder)
 */
@Composable
fun DialogInput(
    value: String,
    onInput: (String) -> Unit,
    placeholder: String? = null,
    type: InputType<String> = InputType.Text,
    attrs: AttrBuilderContext<HTMLInputElement>? = null,
    wrapperAttrs: AttrBuilderContext<HTMLDivElement>? = null,
) {
    FramedBorder(
        borderImage = "/raw/data/ui/borders/input.png",
        backgroundImage = "/raw/data/ui/backgrounds/whitestone.png",
        darkBackgroundImage = "/raw/data/ui/backgrounds/chalkboard.png",
        attrs = {
            classes(Styles.dialogInputWrapper)
            wrapperAttrs?.invoke(this)
        }
    ) {
        Input(
            type = type,
            attrs = {
                classes(Styles.dialogInputField)
                if (placeholder != null) placeholder(placeholder)
                value(value)
                onInput { onInput(it.value) }
                attrs?.invoke(this)
            }
        )
    }
}
