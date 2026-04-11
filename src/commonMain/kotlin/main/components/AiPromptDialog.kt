package main.components

import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    var stopVoiceRequest by remember { mutableStateOf(false) }

    BaseDialog(
        onClose = {
            stopVoiceRequest = true
            onCancel()
        },
        title = title,
        headerActions = {
            DialogButton(
                onClick = onOpenHistory,
                style = DialogButtonStyle.Secondary,
            ) {
                Translated("History")
            }
        },
        content = {
            DialogInput(
                value = prompt,
                onInput = onPromptChange,
                multiline = true,
                attrs = {
                    ref {
                        scope.launch {
                            delay(100)
                            awaitAnimationFrame()
                            it.focus()
                        }

                        onDispose { }
                    }
                }
            )
        },
        actions = {
            VoiceInput(
                stopRequested = stopVoiceRequest,
                onText = { transcribed ->
                    val t = transcribed.trim()
                    if (t.isNotEmpty()) {
                        onPromptChange(
                            buildString {
                                append(prompt)
                                if (prompt.isNotEmpty() && !prompt.endsWith(" ")) append(" ")
                                append(t)
                            }
                        )
                    }
                },
            )

            DialogButton(
                onClick = {
                    stopVoiceRequest = true
                    onCancel()
                },
                style = DialogButtonStyle.Secondary,
            ) { Translated("Cancel") }
            DialogButton(
                onClick = {
                    if (prompt.trim().isNotEmpty()) {
                        stopVoiceRequest = true
                        onSubmit()
                    }
                },
                style = DialogButtonStyle.Primary,
                isDisabled = prompt.trim().isBlank(),
            ) { Translated("Send") }
        }
    )
}
