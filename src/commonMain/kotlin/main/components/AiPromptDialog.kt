package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.autoFocus
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Text
import main.theme.Styles
import org.jetbrains.compose.web.dom.TextArea
import org.jetbrains.compose.web.dom.TextInput
import web.animations.awaitAnimationFrame

/**
 * AI Prompt dialog with a header history button.
 */
@Composable
fun AiPromptDialog(
    title: String = translated("AI prompt"),
    prompt: String,
    onPromptChange: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    BaseDialog(
        onClose = onCancel,
        title = title,
        headerActions = {
            DialogButton(onClick = onOpenHistory, style = DialogButtonStyle.Secondary) {
                Translated("History")
            }
        },
        content = {
            Div {
                TextArea(
                    value = prompt,
                    attrs = {
                    classes(Styles.inputField)
                    onInput { onPromptChange(it.value) }
                    autoFocus()
                    ref {
                        scope.launch {
                            delay(100)
                            awaitAnimationFrame()
                            it.focus()
                        }

                        onDispose { }
                    }
                })
            }
        },
        actions = {
            DialogButton(
                onClick = onCancel,
                style = DialogButtonStyle.Secondary,
            ) { Translated("Cancel") }
            DialogButton(
                onClick = { if (prompt.trim().isNotEmpty()) onSubmit() },
                style = DialogButtonStyle.Primary,
                isDisabled = prompt.trim().isBlank(),
            ) { Translated("Send") }
        }
    )
}
