package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import main.api.UserApi
import main.api.WebSocketApi
import main.api.addDialogueListener
import main.api.sendDialogueConfirmation
import models.DialogueData
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

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
    // State to track the current dialogue
    var currentDialogue by remember { mutableStateOf<DialogueData?>(null) }

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
        title = null,
        showCloseButton = false,
        isFullscreen = false,
        clickOutside = false,
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

            // Dialogue text
            P({
                style {
                    margin(0.px)
                    padding(10.px)
                    textAlign("center")
                    fontSize(16.px)
                    lineHeight("1.5")
                }
            }) {
                Text(dialogue.text)
            }
        },
        actions = {
            // Container to allow wrapping when many buttons
            Div({
                style {
                    display(DisplayStyle.Flex)
                    flexWrap(FlexWrap.Wrap)
                    gap(8.px)
                    justifyContent(JustifyContent.Center)
                    width(100.percent)
                }
            }) {
                dialogue.buttons.forEachIndexed { index, buttonText ->
                    DialogButton(
                        onClick = {
                            val scope = MainScope()
                            scope.launch {
                                try {
                                    val user = UserApi.getCurrentUser()
                                    val userId = user.key
                                    WebSocketApi.sendDialogueConfirmation(dialogue.dialogueId, userId, index)
                                } catch (e: Throwable) {
                                    console.error("Error sending dialogue confirmation", e)
                                } finally {
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
