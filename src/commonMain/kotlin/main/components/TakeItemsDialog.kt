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
import main.api.addTakeItemsListener
import main.api.sendTakeItemsConfirmation
import main.components.inventory.ItemRenderer
import main.theme.NodeStyles
import models.Item
import models.TakeItemsPrompt
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

@Composable
fun TakeItemsDialog() {
    var prompt by remember { mutableStateOf<TakeItemsPrompt?>(null) }

    LaunchedEffect(Unit) {
        WebSocketApi.addTakeItemsListener { data ->
            prompt = data
        }
    }

    val current = prompt ?: return

    Div({ classes(NodeStyles.dialogueOverlay) }) {
        Div({ classes(NodeStyles.dialogueContainer) }) {
            P({ classes(NodeStyles.dialogueText) }) { Translated("Give these items?") }

            // Items list
            Div({
                style {
                    display(DisplayStyle.Flex)
                    flexWrap(FlexWrap.Wrap)
                    gap(12.px)
                    marginTop(8.px)
                    marginBottom(12.px)
                }
            }) {
                current.items.forEach { entry ->
                    val item = Item(title = entry.title, description = "", file = entry.file, assetIndex = entry.assetIndex)
                    Div({
                        style {
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                            alignItems(AlignItems.Center)
                            padding(8.px)
                            backgroundColor(rgba(0, 0, 0, 0.1))
                            borderRadius(12.px)
                            minWidth(80.px)
                        }
                    }) {
                        ItemRenderer(item = item, size = 48)
                        P({ style { marginTop(6.px); textAlign("center") } }) { Text("x${entry.quantity}") }
                        P({ style { textAlign("center"); fontSize(12.px) } }) { Text(entry.title) }
                    }
                }
            }

            // Buttons
            Div({ classes(NodeStyles.dialogueButtonContainer) }) {
                Button({ classes(NodeStyles.dialogueButton); onClick {
                    val scope = MainScope()
                    scope.launch {
                        val user = UserApi.getCurrentUser()
                        WebSocketApi.sendTakeItemsConfirmation(current.requestId, user.key, false)
                        prompt = null
                    }
                } }) { Translated("Decline") }

                Button({ classes(NodeStyles.dialogueButton); onClick {
                    val scope = MainScope()
                    scope.launch {
                        val user = UserApi.getCurrentUser()
                        WebSocketApi.sendTakeItemsConfirmation(current.requestId, user.key, true)
                        prompt = null
                    }
                } }) { Translated("Give items") }
            }
        }
    }
}
