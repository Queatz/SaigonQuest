package main.components.tools

import androidx.compose.runtime.Composable
import main.components.DialogButton
import main.components.DialogButtonStyle
import main.components.Translated
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2

@Composable
fun EditorHeader(
    darkMode: Boolean,
    onBack: () -> Unit
) {
    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            alignItems(AlignItems.Center)
            gap(16.px)
        }
    }) {
        DialogButton(
            small = true,
            style = DialogButtonStyle.Secondary,
            onClick = onBack,
        ) {
            Translated("Back")
        }
        H2(attrs = { style { color(if (darkMode) main.theme.Theme.colors.onSurfaceDark else main.theme.Theme.colors.onSurface) } }) { Translated("Edit lesson scene") }
    }
}
