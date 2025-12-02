package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.api.UserApi
import main.api.WebSocketApi
import main.api.addDialogueListener
import main.api.sendDialogueConfirmation
import main.theme.Styles
import models.DialogueData
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.fontWeight
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.textAlign
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.autoFocus
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.TextInput
import org.w3c.dom.HTMLElement
import web.animations.awaitAnimationFrame
import web.cssom.HtmlAttributes.Companion.value
import kotlin.invoke

/**
 * DialogueBox component
 *
 * This component displays a dialogue box with text and an OK button.
 * It's used to show dialogue messages from NPCs or objects.
 */
@Composable
fun DialogueBox(
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    
    // State to track the current dialogue
    var currentDialogue by remember { mutableStateOf<DialogueData?>(null) }

    // Responsive helper
    val isMobile = rememberIsMobile()
    var textInput by remember { mutableStateOf("") }
    var inputEl by remember { mutableStateOf<HTMLElement?>(null) }

    LaunchedEffect(currentDialogue) {
        if (currentDialogue == null) {
            onClose()
        }
    }

    // Add a listener for dialogue events
    LaunchedEffect(Unit) {
        WebSocketApi.addDialogueListener { dialogueData ->
            console.log("DialogueBox: Received dialogue: ${dialogueData.text}")
            currentDialogue = dialogueData
        }
    }

    // Only show the dialogue box if there's a dialogue to show
    val dialogue = currentDialogue ?: return

    BaseDialog(
        onClose = { currentDialogue = null },
        title = dialogue.title,
        showCloseButton = false,
        isFullscreen = false,
        clickOutside = true,
        maxWidth = 800.px.toString(),
        anchorToBottom = isMobile.value,
        content = {
            // Optional image above the text
            if (!dialogue.imageUrl.isNullOrBlank()) {
                Img(
                    src = dialogue.imageUrl!!,
                    attrs = {
                        style {
                            width(100.percent)
                            borderRadius(8.px)
                            marginBottom(12.px)
                        }
                    }
                )
            }

            // Object title (optional)
            if (!dialogue.objectTitle.isNullOrBlank()) {
                P({
                    style {
                        margin(0.px)
                        padding(0.px)
                        textAlign("center")
                        fontSize(18.px)
                        fontWeight("bold")
                        marginBottom(6.px)
                    }
                }) {
                    Text(dialogue.objectTitle!!)
                }
            }

            // Dialogue text
            Md(dialogue.text) {
                if (!dialogue.text.trim().contains("\n")) {
                    style {
                        textAlign("center")
                    }
                }
            }

            if (dialogue.requiresInput) {
                Br()
                Div(
                    attrs = {
                        style {
                            display(DisplayStyle.Flex)
                            gap(8.px)
                        }
                    }
                ) {
                    TextInput(textInput) {
                        if (!dialogue.placeholder.isNullOrBlank()) {
                            placeholder(dialogue.placeholder!!)
                        }
                        classes(Styles.inputField)
                        autoFocus()
                        onInput { ev -> textInput = ev.value }
                        onKeyDown { ev ->
                            if (ev.key == "Enter") {
                                val scope = MainScope()
                                scope.launch {
                                    try {
                                        val user = UserApi.getCurrentUser()
                                        val userId = user.key
                                        WebSocketApi.sendDialogueConfirmation(dialogue.dialogueId, userId, 0, textInput)
                                    } catch (e: Throwable) {
                                        console.error("Error sending dialogue confirmation via Enter", e)
                                    } finally {
                                        textInput = ""
                                        currentDialogue = null
                                    }
                                }
                            }
                        }

                        ref {
                            it.focus()
                            
                            inputEl = it
                            
                            onDispose {
                                inputEl = null
                            }
                        }
                    }

                    // Voice input button - appends transcribed text to the end of textInput
                    VoiceInput(
                        onText = { transcribed ->
                            textInput = buildString {
                                append(textInput)
                                if (isNotEmpty() && !textInput.endsWith(" ")) append(' ')
                                append(transcribed.trim())
                            }
                            scope.launch {
                                delay(100)
                                awaitAnimationFrame()
                                inputEl?.let { element ->
                                    element.scrollLeft = element.scrollWidth.toDouble()
                                }
                            }
                        }
                    )
                }
            }
        },
        actions = {
            // Container to allow wrapping when many buttons
            Div({
                classes(Styles.dialogueButtons)
            }) {
                dialogue.buttons.forEachIndexed { index, buttonText ->
                    DialogButton(
                        onClick = {
                            val scope = MainScope()
                            scope.launch {
                                try {
                                    val user = UserApi.getCurrentUser()
                                    val userId = user.key
                                    val inputText = if (dialogue.requiresInput) textInput else null
                                    WebSocketApi.sendDialogueConfirmation(dialogue.dialogueId, userId, index, inputText)
                                } catch (e: Throwable) {
                                    console.error("Error sending dialogue confirmation", e)
                                } finally {
                                    // Reset
                                    textInput = ""
                                    currentDialogue = null
                                }
                            }
                        },
                        isPrimary = index == 0
                    ) { Text(buttonText) }
                }
            }
        }
    )
}
