package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLSelectElement

/**
 * A reusable dialog select component with consistent styling.
 *
 * @param options List of options, each with a value and a label
 * @param value The current selected value
 * @param onInput Callback when the value changes
 * @param label Optional label text displayed above the select
 * @param enabled Whether the select is enabled
 * @param attrs Additional attributes for the select element
 * @param wrapperAttrs Additional attributes for the inner FramedBorder container
 * @param containerAttrs Additional attributes for the outer container
 * @param content Optional content (e.g., error messages) below the select
 */
@Composable
fun DialogSelect(
    options: List<Pair<String, String>>,
    value: String,
    onInput: (String) -> Unit,
    label: String? = null,
    enabled: Boolean = true,
    attrs: AttrBuilderContext<HTMLSelectElement>? = null,
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
            Select(
                attrs = {
                    classes(Styles.dialogInputField)
                    onInput { onInput(it.value ?: "") }
                    if (!enabled) {
                        attr("disabled", "true")
                    }
                    attrs?.invoke(this)
                }
            ) {
                for ((optValue, optLabel) in options) {
                    Option(
                        value = optValue,
                        attrs = {
                            if (optValue == value) selected()
                        }
                    ) {
                        Translated(optLabel)
                    }
                }
            }
        }
        content()
    }
}
