package main.components.chat

import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import main.components.BaseDialog
import main.components.DialogButton
import main.components.IconRenderer
import main.components.Translated
import main.components.translated
import main.theme.Styles
import main.utils.toggleFullscreen
import org.jetbrains.compose.web.css.flex
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div

/**
 * A rich text (markdown) editor dialog for composing chat messages.
 *
 * Toolbar functions insert markdown at the current cursor position in the editor.
 * A live preview below provides a WYSIWYG-like experience using the `marked` renderer.
 */
@Composable
fun RichTextEditorDialog(
    onClose: () -> Unit,
    onSend: (String) -> Unit,
    actions: (@Composable () -> Unit)? = null,
    initialText: String = "",
) {
    var text by remember { mutableStateOf(initialText) }
    var isFullscreen by remember { mutableStateOf(false) }

    // Keep in sync with fullscreen state to mirror LogicEditorDialog behavior
    DisposableEffect(Unit) {
        val listener: (dynamic) -> Unit = { isFullscreen = document.fullscreenElement != null }
        document.addEventListener("fullscreenchange", listener)
        onDispose { document.removeEventListener("fullscreenchange", listener) }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Write chat message"),
        maxWidth = "800px",
        isFullHeight = true,
        isFullscreen = isFullscreen,
        // Fullscreen toggle like LogicEditorDialog
        headerActions = {
            Button(
                attrs = {
                    classes(Styles.textButton)
                    onClick { toggleFullscreen() }
                }
            ) {
                IconRenderer(
                    url = "raw/data/ui/ui.png",
                    index = if (isFullscreen) 1 else 0,
                    size = 24
                )
            }
        },
        content = {
            RichTextEditor(
                value = text,
                onValueChange = { text = it },
                placeholder = translated("Write chat message"),
            )
        },
        actions = {
            actions?.let {
                it()
                Div(
                    {
                        style {
                            flex(1)
                        }
                    }
                )
            }
            DialogButton(
                onClick = onClose,
            ) { Translated("Cancel") }
            DialogButton(
                onClick = { if (text.isNotBlank()) onSend(text) },
                isPrimary = true,
            ) { Translated("Send") }
        }
    )
}
