package main.components.chat

import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.browser.window
import main.components.common.Spinner
import main.components.VoiceInput
import main.theme.Styles
import org.jetbrains.compose.web.attributes.autoFocus
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput
import org.w3c.dom.events.KeyboardEvent

@Composable
fun GlobalAiChat() {
    if (!GlobalAiChatStore.isOpen) return

    DisposableEffect(Unit) {
        val listener: (org.w3c.dom.events.Event) -> Unit = { ev ->
            if (ev is KeyboardEvent && ev.key == "Escape") {
                GlobalAiChatStore.close()
            }
        }
        window.addEventListener("keydown", listener)
        onDispose { window.removeEventListener("keydown", listener) }
    }

    Div({ classes(Styles.aiChatOverlay) }) {
        // Header
        Div({ classes(Styles.aiChatHeader) }) {
            P({ classes(Styles.aiChatTitle) }) { Text(str("AI chat")) }
            Button({
                classes(Styles.aiChatClose)
                onClick { GlobalAiChatStore.close() }
            }) { Text("×") }
        }

        // Messages
        Div({ classes(Styles.aiChatMessages) }) {
            GlobalAiChatStore.messages.forEach { m ->
                val roleClass = when (m.role) {
                    models.AiRole.System -> Styles.aiChatMsgSystem
                    models.AiRole.User -> Styles.aiChatMsgUser
                    models.AiRole.Assistant -> Styles.aiChatMsgAssistant
                }
                Div({ classes(Styles.aiChatMsg, roleClass) }) { Text(m.content) }
            }

            if (GlobalAiChatStore.isLoading) {
                Div({ classes(Styles.aiChatSpinnerRow) }) {
                    Spinner(message = null)
                }
            }
        }

        // Input row
        Div({ classes(Styles.aiChatInputRow) }) {
            TextInput(GlobalAiChatStore.input) {
                classes(Styles.inputField, Styles.aiChatInput)
                placeholder(str("Type a message…"))
                autoFocus()
                onInput { ev -> GlobalAiChatStore.setInput(ev.value) }
                onKeyDown { ev ->
                    if (ev.key == "Enter") {
                        GlobalAiChatStore.send(GlobalAiChatStore.input)
                    } else if (ev.key == "Escape") {
                        GlobalAiChatStore.close()
                    }
                }
            }

            // Voice input button (appends to input)
            VoiceInput(
                small = true,
                onText = { transcribed ->
                    val t = transcribed.trim()
                    if (t.isNotEmpty()) {
                        GlobalAiChatStore.setInput(
                            buildString {
                                append(GlobalAiChatStore.input)
                                if (isNotEmpty() && !GlobalAiChatStore.input.endsWith(" ")) append(' ')
                                append(t)
                            }
                        )
                    }
                },
            )

            Button({
                classes(Styles.primaryButton, Styles.aiChatSend)
                onClick { GlobalAiChatStore.send(GlobalAiChatStore.input) }
            }) { Text(str("Send")) }
        }
    }
}
