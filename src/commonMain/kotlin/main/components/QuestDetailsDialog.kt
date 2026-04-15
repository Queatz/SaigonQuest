package main.components

import androidx.compose.runtime.*
import main.api.ItemApi
import main.api.WorldApi
import main.theme.Styles
import models.Item
import models.PlayerQuestProgress
import models.Quest
import models.World
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.css.*
import app.softwork.routingcompose.Router
import main.components.inventory.ItemIcon
import main.components.DialogButton
import main.components.DialogButtonStyle

/**
 * Dialog that shows detailed information about a quest
 */
@Composable
fun QuestDetailsDialog(
    quest: Quest,
    progress: PlayerQuestProgress?,
    onClose: () -> Unit
) {
    val router = Router.current
    val scope = rememberCoroutineScope()
    
    var itemsById by remember { mutableStateOf<Map<String, Item>>(emptyMap()) }
    var targetWorld by remember { mutableStateOf<World?>(null) }
    
    val currentStep = progress?.let { quest.steps.getOrNull(it.currentStepIndex) }
    
    // Load item details and target world details
    LaunchedEffect(quest) {
        if (quest.rewards.isNotEmpty()) {
            try {
                val itemIds = quest.rewards.map { it.itemId }
                val items = ItemApi.getItemsByIds(itemIds)
                itemsById = items.associateBy { it.key }
            } catch (t: Throwable) {
                console.error("Failed to load quest reward items", t)
            }
        }
    }
    
    LaunchedEffect(currentStep?.worldId) {
        val worldId = currentStep?.worldId
        if (!worldId.isNullOrBlank()) {
            try {
                targetWorld = WorldApi.getWorld(worldId)
            } catch (t: Throwable) {
                console.error("Failed to load target world details", t)
                targetWorld = null
            }
        } else {
            targetWorld = null
        }
    }
    
    BaseDialog(
        title = quest.name,
        onClose = onClose,
        content = {
            Div(attrs = { 
                style { 
                    padding(16.px)
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    gap(16.px)
                    maxWidth(500.px)
                } 
            }) {
                // Quest Description
                Div {
                    P(attrs = { 
                        style { 
                            lineHeight(1.5.em)
                            color(rgba(255, 255, 255, 0.8))
                        } 
                    }) {
                        Text(quest.description)
                    }
                }
                
                // Current Step Information
                if (progress?.completed == true) {
                    Div(attrs = {
                        style {
                            backgroundColor(rgba(76, 175, 80, 0.1))
                            padding(12.px)
                            borderRadius(8.px)
                            border(1.px, LineStyle.Solid, rgba(76, 175, 80, 0.3))
                            textAlign("center")
                        }
                    }) {
                        H3(attrs = { 
                            style { 
                                marginTop(0.px)
                                color(Color("#4caf50"))
                                fontSize(18.px)
                                marginBottom(0.px)
                            } 
                        }) {
                            Translated("Completed")
                        }
                    }
                } else if (currentStep != null) {
                    Div(attrs = {
                        style {
                            backgroundColor(rgba(255, 255, 255, 0.05))
                            padding(12.px)
                            borderRadius(8.px)
                            border(1.px, LineStyle.Solid, rgba(255, 255, 255, 0.1))
                        }
                    }) {
                        H3(attrs = { 
                            style { 
                                marginTop(0.px)
                                fontSize(16.px)
                                marginBottom(8.px)
                            } 
                        }) {
                            Translated("Next Step")
                            Text(": ${currentStep.title}")
                        }
                        
                        P(attrs = { 
                            style { 
                                marginBottom(0.px)
                                color(rgba(255, 255, 255, 0.7))
                            } 
                        }) {
                            Text(currentStep.description)
                        }
                        
                        // "Continue" Button to teleport to world
                        if (!currentStep.worldId.isNullOrBlank()) {
                            DialogButton(
                                onClick = {
                                    onClose()
                                    // Use world URL for navigation if available, otherwise fallback to ID
                                    val destination = if (targetWorld != null) "/${targetWorld!!.url}" else "/${currentStep.worldId}"
                                    router.navigate(destination)
                                },
                                style = DialogButtonStyle.Primary,
                                attrs = {
                                    style { marginTop(16.px) }
                                }
                            ) {
                                Translated("Continue")
                            }
                        }
                    }
                }
                
                // Rewards
                if (quest.rewards.isNotEmpty()) {
                    Div {
                        H3(attrs = { 
                            style { 
                                fontSize(16.px)
                                marginBottom(12.px)
                            } 
                        }) {
                            Translated("Rewards")
                        }
                        Div(attrs = { 
                            style { 
                                display(DisplayStyle.Flex)
                                gap(12.px)
                                flexWrap(FlexWrap.Wrap)
                            } 
                        }) {
                            quest.rewards.forEach { reward ->
                                val item = itemsById[reward.itemId]
                                Div(attrs = {
                                    style {
                                        display(DisplayStyle.Flex)
                                        flexDirection(FlexDirection.Column)
                                        alignItems(AlignItems.Center)
                                        gap(4.px)
                                        width(80.px)
                                    }
                                    title(item?.title ?: reward.itemId)
                                }) {
                                    if (item != null) {
                                        ItemIcon(item, size = 48.px)
                                        Div(attrs = {
                                            style {
                                                fontSize(12.px)
                                                textAlign("center")
                                                property("text-overflow", "ellipsis")
                                                overflow("hidden")
                                                property("white-space", "nowrap")
                                                width(100.percent)
                                            }
                                        }) {
                                            Text(item.title)
                                        }
                                        if (reward.quantity > 1) {
                                            Div(attrs = {
                                                style {
                                                    fontSize(11.px)
                                                    color(rgba(255, 255, 255, 0.6))
                                                }
                                            }) {
                                                Text("x${reward.quantity}")
                                            }
                                        }
                                    } else {
                                        Div(attrs = {
                                            style {
                                                width(48.px)
                                                height(48.px)
                                                backgroundColor(rgba(255, 255, 255, 0.1))
                                                borderRadius(8.px)
                                                display(DisplayStyle.Flex)
                                                alignItems(AlignItems.Center)
                                                justifyContent(JustifyContent.Center)
                                                fontSize(24.px)
                                            }
                                        }) {
                                            Text("🎁")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
