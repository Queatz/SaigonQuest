package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import main.api.UserApi
import main.api.WebSocketApi
import main.api.addDialogueListener
import main.api.sendDialogueConfirmation
import main.theme.NodeStyles
import models.DialogueData
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/**
 * DialogueBox component
 *
 * This component displays a dialogue box with text and an OK button.
 * It's used to show dialogue messages from NPCs or objects.
 */
@Composable
fun DialogueBox() {
    // State to track the current dialogue
    var currentDialogue by remember { mutableStateOf<DialogueData?>(null) }
    
    // Add a listener for dialogue events
    LaunchedEffect(Unit) {
        WebSocketApi.addDialogueListener { dialogueData ->
            console.log("DialogueBox: Received dialogue: ${dialogueData.text}")
            currentDialogue = dialogueData
        }
    }
    
    // Only show the dialogue box if there's a dialogue to show
    currentDialogue?.let { dialogue ->
        // Overlay to darken the background
        Div({
            classes(NodeStyles.dialogueOverlay)
        }) {
            // Dialogue box
            Div({
                classes(NodeStyles.dialogueContainer)
            }) {
                // Dialogue text
                P({
                    classes(NodeStyles.dialogueText)
                }) {
                    Text(dialogue.text)
                }
                
                // Button container
                Div({
                    classes(NodeStyles.dialogueButtonContainer)
                }) {
                    // Render each button
                    dialogue.buttons.forEachIndexed { index, buttonText ->
                        Button({
                            classes(NodeStyles.dialogueButton)
                            onClick {
                                // Create a coroutine scope
                                val scope = MainScope()
                                
                                // Launch a coroutine to get the user ID and send confirmation
                                scope.launch {
                                    try {
                                        // Get the current user ID
                                        val user = UserApi.getCurrentUser()
                                        val userId = user.key
                                        
                                        // Send confirmation with button index
                                        WebSocketApi.sendDialogueConfirmation(dialogue.dialogueId, userId, index)
                                        
                                        // Clear the dialogue
                                        currentDialogue = null
                                    } catch (e: Throwable) {
                                        console.error("Error sending dialogue confirmation", e)
                                        // Still clear the dialogue to avoid blocking the UI
                                        currentDialogue = null
                                    }
                                }
                            }
                        }) {
                            Text(buttonText)
                        }
                    }
                }
            }
        }
    }
}
