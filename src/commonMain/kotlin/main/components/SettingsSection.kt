package main.components

import androidx.compose.runtime.Composable
import main.theme.Styles
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLDivElement

/**
 * A reusable section container for settings.
 *
 * @param label The label text to display for the section
 * @param attrs Additional attributes for the section container
 * @param content The content of the section
 */
@Composable
fun SettingsSection(
    label: String? = null,
    attrs: AttrBuilderContext<HTMLDivElement>? = null,
    content: @Composable () -> Unit
) {
    Div(
        attrs = {
            classes(Styles.settingsSectionContainer)
            attrs?.invoke(this)
        }
    ) {
        if (label != null) {
            Span(
                attrs = {
                    classes(Styles.settingsSectionLabel)
                }
            ) {
                Translated(label)
            }
        }
        content()
    }
}
