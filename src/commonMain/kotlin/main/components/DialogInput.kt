package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.autoFocus
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.css.keywords.auto
import org.jetbrains.compose.web.dom.AttrBuilderContext
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.TextArea
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement

/**
 * A reusable dialog input component with consistent styling.
 *
 * @param value The current value of the input
 * @param onInput Callback when the value changes (provides the new string value)
 * @param label Optional label text displayed above the input
 * @param placeholder The placeholder text
 * @param type The input type (default to Text, ignored if multiline is true)
 * @param enabled Whether the input is enabled
 * @param multiline Whether to use a textarea instead of a text input
 * @param attrs Additional attributes for the input or textarea element
 * @param wrapperAttrs Additional attributes for the inner FramedBorder container
 * @param containerAttrs Additional attributes for the outer container
 */
@Composable
fun DialogInput(
    value: String,
    onInput: (String) -> Unit,
    label: String? = null,
    placeholder: String? = null,
    type: InputType<String> = InputType.Text,
    enabled: Boolean = true,
    multiline: Boolean = false,
    autoFocus: Boolean = false,
    attrs: AttrBuilderContext<out HTMLElement>? = null,
    wrapperAttrs: AttrBuilderContext<HTMLDivElement>? = null,
    containerAttrs: AttrBuilderContext<HTMLDivElement>? = null,
    content: @Composable () -> Unit = {},
) {
    Div(
        attrs = {
            if (!enabled) {
                style {
                    property("opacity", "0.7")
                    property("cursor", "not-allowed")
                }
            }
            containerAttrs?.invoke(this)
        }
    ) {
        if (label != null) {
            Label(
                attrs = {
                    classes(Styles.formLabel)
                }
            ) {
                Translated(label)
            }
        }
        FramedBorder(
            borderImage = "/raw/data/ui/borders/input.png",
            backgroundImage = "/raw/data/ui/backgrounds/whitestone.png",
            darkBackgroundImage = "/raw/data/ui/backgrounds/chalkboard.png",
            attrs = {
                classes(Styles.dialogInputWrapper)
                wrapperAttrs?.invoke(this)
            }
        ) {
            if (multiline) {
                TextArea(
                    attrs = {
                        classes(Styles.dialogInputField)
                        if (placeholder != null) placeholder(placeholder)
                        value(value)
                        onInput { onInput(it.value) }
                        if (!enabled) {
                            attr("disabled", "true")
                        }

                        if (autoFocus) {
                            autoFocus()
                        }

                        (attrs as? AttrBuilderContext<HTMLTextAreaElement>)?.invoke(this)
                    }
                )
            } else {
                Input(
                    type = type,
                    attrs = {
                        classes(Styles.dialogInputField)
                        if (placeholder != null) placeholder(placeholder)
                        value(value)
                        onInput { onInput(it.value) }
                        if (!enabled) {
                            attr("disabled", "true")
                        }

                        if (autoFocus) {
                            autoFocus()
                        }
                        (attrs as? AttrBuilderContext<HTMLInputElement>)?.invoke(this)
                    }
                )
            }
        }
        content()
    }
}
