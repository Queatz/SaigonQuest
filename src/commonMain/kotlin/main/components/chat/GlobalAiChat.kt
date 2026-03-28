package main.components.chat

import Translations.str
import androidx.compose.runtime.*
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.components.AnimatedDots
import main.components.IconRenderer
import main.components.Md
import main.components.Translated
import main.components.VoiceInput
import main.components.common.Spinner
import main.theme.Styles
import models.AiRole
import org.jetbrains.compose.web.attributes.autoFocus
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput
import org.w3c.dom.SMOOTH
import org.w3c.dom.ScrollBehavior
import org.w3c.dom.ScrollToOptions
import org.w3c.dom.events.KeyboardEvent
import web.animations.awaitAnimationFrame

@Composable
fun GlobalAiChat() {
    if (!GlobalAiChatStore.isOpen) return

    val scope = rememberCoroutineScope()
    var messagesEl by remember { mutableStateOf<org.w3c.dom.HTMLElement?>(null) }

    // Local visibility to drive enter/exit animations without instantly unmounting
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        awaitAnimationFrame()
        visible = true
    }

    LaunchedEffect(GlobalAiChatStore.messages.size, GlobalAiChatStore.isLoading) {
        scope.launch {
            awaitAnimationFrame()
            messagesEl?.let { el ->
                val lastChild = el.lastElementChild as? org.w3c.dom.HTMLElement
                if (lastChild != null) {
                    el.scrollTo(
                        ScrollToOptions(
                            top = lastChild.offsetTop.toDouble(),
                            behavior = ScrollBehavior.SMOOTH,
                        )
                    )
                }
            }
        }
    }

    fun closeWithAnimation() {
        scope.launch {
            visible = false
            awaitAnimationFrame()
            delay(200)
            GlobalAiChatStore.close()
        }
    }

    DisposableEffect(Unit) {
        val listener: (org.w3c.dom.events.Event) -> Unit = { ev ->
            if (ev is KeyboardEvent && ev.key == "Escape") {
                closeWithAnimation()
            }
        }
        window.addEventListener("keydown", listener)
        onDispose { window.removeEventListener("keydown", listener) }
    }

    Div({
        classes(Styles.aiChatOverlay)
        if (visible) classes(Styles.aiChatOverlayOpen)
        if (GlobalAiChatStore.isFullscreen) classes(Styles.aiChatOverlayFullscreen)
    }) {
        // Messages
        Div({
            classes(Styles.aiChatMessages)
            ref {
                messagesEl = it
                onDispose { messagesEl = null }
            }
        }) {
            GlobalAiChatStore.messages.forEachIndexed { index, m ->
                if (m.role == AiRole.System) return@forEachIndexed
                if (index < GlobalAiChatStore.initialMessageCount && m.role == AiRole.User) return@forEachIndexed

                val roleClass = when (m.role) {
                    AiRole.System -> Styles.aiChatMsgSystem
                    AiRole.User -> Styles.aiChatMsgUser
                    AiRole.Assistant -> Styles.aiChatMsgAssistant
                }
                Div({ classes(Styles.aiChatMsg, roleClass) }) {
                    Md(m.content)

                    if (m.role == AiRole.Assistant) {
                        Div({
                            classes(Styles.aiChatSpeak)
                            onClick { GlobalAiChatStore.speak(index) }
                        }) {
                            if (GlobalAiChatStore.speakingIndex == index) {
                                if (GlobalAiChatStore.isSpeechPaused) {
                                    Span { Text("▶️") }
                                } else if (GlobalAiChatStore.isSpeechLoading) {
                                    Spinner(
                                        message = null,
                                        spinnerClass = Styles.aiChatLoadingSpinner,
                                    )
                                } else {
                                    Span { Text("⏸️") }
                                }
                            } else {
                                Span { Text("▶️") }
                            }
                        }
                    }
                }
            }

            if (GlobalAiChatStore.isLoading) {
                Div({ classes(Styles.aiChatSpinnerRow) }) {
                    Spinner(
                        message = null,
                        spinnerClass = Styles.aiChatLoadingSpinner,
                    )
                    Span {
                        Translated("Thinking")
                        AnimatedDots()
                    }
                }
            }
        }

        // Input row
        Div({ classes(Styles.aiChatInputRow) }) {
            IconRenderer(
                url = "raw/data/ui/ui.png",
                index = if (GlobalAiChatStore.isFullscreen) 1 else 0,
                size = 24,
                onClick = { GlobalAiChatStore.toggleFullscreen() },
                attrs = { classes(Styles.aiChatFullscreenToggle) }
            )
            TextInput(GlobalAiChatStore.input) {
                classes(Styles.inputField, Styles.aiChatInput)
                placeholder(str("Type a message…"))
                autoFocus()
                onInput { ev -> GlobalAiChatStore.setInput(ev.value) }
                onKeyDown { ev ->
                    if (ev.key == "Enter") {
                        ev.preventDefault()
                        ev.stopPropagation()
                        GlobalAiChatStore.send(GlobalAiChatStore.input)
                    } else if (ev.key == "Escape") {
                        ev.preventDefault()
                        ev.stopPropagation()
                        closeWithAnimation()
                    }
                }
                ref {
                    it.focus()
                    onDispose { }
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
                val isBlank = GlobalAiChatStore.input.isBlank()
                onClick {
                    if (!isBlank) {
                        GlobalAiChatStore.send(GlobalAiChatStore.input)
                    } else {
                        closeWithAnimation()
                    }
                }
            }) { Translated(if (GlobalAiChatStore.input.isEmpty()) "Close" else "Send") }
        }
    }
}
