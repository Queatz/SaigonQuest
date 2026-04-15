package main.components

import androidx.compose.runtime.*
import main.api.QuestApi
import main.api.WebSocketApi
import main.api.addQuestProgressListener
import main.components.FramedBorder
import main.theme.QuestStyles
import main.theme.Styles
import models.PlayerQuestProgress
import models.Quest
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.css.*
import kotlinx.coroutines.launch

@Composable
fun QuestTracker() {
    val scope = rememberCoroutineScope()
    var progressList by remember { mutableStateOf<List<PlayerQuestProgress>>(emptyList()) }
    var questsById by remember { mutableStateOf<Map<String, Quest>>(emptyMap()) }
    var selectedQuestId by remember { mutableStateOf<String?>(null) }
    var showAllDialog by remember { mutableStateOf(false) }

    fun refreshQuests() {
        scope.launch {
            try {
                val progress = QuestApi.getAllPlayerQuestProgress()
                val sortedProgress = progress.sortedWith(
                    compareBy<PlayerQuestProgress> { it.completed }
                        .thenByDescending { it.createdAt }
                )
                
                val currentQuestsById = questsById.toMutableMap()
                // Only fetch definitions for the quests we are actually showing (top 3)
                val top3 = sortedProgress.take(3)
                
                for (p in top3) {
                    if (!currentQuestsById.containsKey(p.questId)) {
                        try {
                            val q = QuestApi.getQuest(p.questId)
                            currentQuestsById[p.questId] = q
                        } catch (e: Throwable) {
                            console.error("Failed to fetch quest ${p.questId} for tracker", e)
                        }
                    }
                }
                
                questsById = currentQuestsById
                progressList = sortedProgress
            } catch (e: Throwable) {
                console.error("Error loading quests for tracker", e)
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshQuests()
    }

    DisposableEffect(Unit) {
        val token = WebSocketApi.addQuestProgressListener {
            refreshQuests()
        }
        onDispose {
            WebSocketApi.removeListener(token)
        }
    }

    Div(attrs = { classes(QuestStyles.leftContainer) }) {
        if (progressList.isNotEmpty()) {
            FramedBorder(attrs = {
                style {
                    property("pointer-events", "all")
                }
            }) {
                Div(attrs = {
                    style {
                        padding(12.px)
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        gap(8.px)
                        minWidth(200.px)
                    }
                }) {
                    progressList.take(3).forEach { progress ->
                        val quest = questsById[progress.questId] ?: return@forEach
                        val currentStep = quest.steps.getOrNull(progress.currentStepIndex)

                        Div(attrs = {
                            classes(QuestStyles.questTrackerItem)
                            onClick { selectedQuestId = quest.key }
                        }) {
                            // Quest Title
                            Div(attrs = { classes(QuestStyles.questTrackerTitle) }) {
                                Text(quest.name)
                            }

                            // Current Step
                            if (progress.completed) {
                                Div(attrs = { classes(QuestStyles.questCompleted) }) {
                                    Translated("Completed")
                                }
                                
                                // Full Progress Bar
                                Div(attrs = { classes(QuestStyles.progressBarContainer) }) {
                                    Div(attrs = {
                                        classes(QuestStyles.progressBarFill)
                                        style { width(100.percent) }
                                    })
                                }
                            } else if (currentStep != null) {
                                Div(attrs = { classes(QuestStyles.questTrackerStep) }) {
                                    Text(currentStep.title)
                                }

                                // Progress Bar
                                val totalSteps = quest.steps.size
                                if (totalSteps > 0) {
                                    val percent = ((progress.currentStepIndex + 1).toDouble() / totalSteps.toDouble() * 100).toInt()
                                    Div(attrs = { classes(QuestStyles.progressBarContainer) }) {
                                        Div(attrs = {
                                            classes(QuestStyles.progressBarFill)
                                            style { width(percent.percent) }
                                        })
                                    }
                                }
                            }
                        }
                    }

                    if (progressList.size > 3) {
                        Button(attrs = {
                            classes(Styles.secondaryButton)
                            style {
                                marginTop(4.px)
                                property("pointer-events", "all")
                            }
                            onClick { showAllDialog = true }
                        }) {
                            Translated("View all quests")
                        }
                    }
                }
            }
        }
    }

    if (selectedQuestId != null) {
        val quest = questsById[selectedQuestId]
        if (quest != null) {
            QuestDetailsDialog(
                quest = quest,
                progress = progressList.find { it.questId == selectedQuestId },
                onClose = { 
                    selectedQuestId = null
                    refreshQuests()
                }
            )
        }
    }
    
    // showAllDialog is not implemented yet, but for now we'll just show the top 3
}
