package main.components

import androidx.compose.runtime.*
import main.components.*
import main.api.QuestApi
import main.api.WebSocketApi
import main.api.addQuestProgressListener
import main.theme.QuestStyles
import main.theme.Styles
import models.PlayerQuestProgress
import models.Quest
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.css.*
import kotlinx.coroutines.launch

@Composable
fun QuestTracker(userId: String? = null) {
    val scope = rememberCoroutineScope()
    var progressList by remember { mutableStateOf<List<PlayerQuestProgress>>(emptyList()) }
    var questsById by remember { mutableStateOf<Map<String, Quest>>(emptyMap()) }
    var selectedQuestId by remember { mutableStateOf<String?>(null) }
    var showAllDialog by remember { mutableStateOf(false) }
    val isMobile by rememberIsMobile()

    fun refreshQuests() {
        scope.launch {
            try {
                val progress = QuestApi.getAllPlayerQuestProgress()
                val sortedProgress = progress.sortedWith(
                    compareBy<PlayerQuestProgress> { it.completed }
                        .thenByDescending { it.createdAt }
                )
                
                val currentQuestsById = questsById.toMutableMap()
                // Only fetch definitions for the quests we are actually showing (top 3 incomplete, or all if dialog is open)
                val incompleteQuests = sortedProgress.filter { !it.completed }
                val questsToFetch = if (showAllDialog) sortedProgress else incompleteQuests.take(3)
                
                for (p in questsToFetch) {
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

    LaunchedEffect(userId) {
        refreshQuests()
    }

    LaunchedEffect(showAllDialog) {
        if (showAllDialog) {
            refreshQuests()
        }
    }

    DisposableEffect(Unit) {
        val token = WebSocketApi.addQuestProgressListener {
            refreshQuests()
        }
        onDispose {
            WebSocketApi.removeListener(token)
        }
    }

    val incompleteQuests = progressList.filter { !it.completed }
    val completedQuests = progressList.filter { it.completed }

    Div(attrs = { classes(QuestStyles.leftContainer) }) {
        if (progressList.isNotEmpty()) {
            if (isMobile) {
                if (incompleteQuests.isNotEmpty() || completedQuests.isNotEmpty()) {
                    CircleButton(
                        onClick = { showAllDialog = true },
                        icon = "📜",
                        title = translated("Quests"),
                        badgeCount = 0,
                    )
                }
            } else {
                if (incompleteQuests.isNotEmpty() || completedQuests.isNotEmpty()) {
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
                            incompleteQuests.take(3).forEach { progress ->
                                val quest = questsById[progress.questId] ?: return@forEach
                                QuestTrackerItem(
                                    quest = quest,
                                    progress = progress,
                                    onClick = { selectedQuestId = quest.key }
                                )
                            }

                            if (incompleteQuests.size > 3 || completedQuests.isNotEmpty()) {
                                DialogButton(
                                    onClick = { showAllDialog = true },
                                    style = DialogButtonStyle.Secondary,
                                    attrs = {
                                        style {
                                            marginTop(4.px)
                                            property("pointer-events", "all")
                                        }
                                    }
                                ) {
                                    Translated("View all quests")
                                }
                            }
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
    
    if (showAllDialog) {
        AllQuestsDialog(
            progressList = progressList,
            questsById = questsById,
            onClose = { showAllDialog = false },
            onQuestClick = { selectedQuestId = it }
        )
    }
}

@Composable
fun QuestTrackerItem(
    quest: Quest,
    progress: PlayerQuestProgress,
    onClick: () -> Unit,
) {
    val currentStep = quest.steps.getOrNull(progress.currentStepIndex)

    Div(attrs = {
        classes(QuestStyles.questTrackerItem)
        onClick { onClick() }
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
                // start at 0 when step 1 is not yet complete, and become fully full after the last step is complete
                val percent = (progress.currentStepIndex.toDouble() / totalSteps.toDouble() * 100).toInt()
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

@Composable
fun AllQuestsDialog(
    progressList: List<PlayerQuestProgress>,
    questsById: Map<String, Quest>,
    onClose: () -> Unit,
    onQuestClick: (String) -> Unit,
) {
    BaseDialog(
        title = translated("All quests"),
        onClose = onClose,
        content = {
            Div(attrs = {
                style {
                    padding(16.px)
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    gap(12.px)
                    minWidth(300.px)
                    maxWidth(500.px)
                    maxHeight(60.vh)
                    overflow("auto")
                }
            }) {
                progressList.forEach { progress ->
                    val quest = questsById[progress.questId]
                    if (quest != null) {
                        Paper {
                            QuestTrackerItem(
                                quest = quest,
                                progress = progress,
                                onClick = { 
                                    onQuestClick(quest.key)
                                    onClose()
                                }
                            )
                        }
                    } else {
                        // Still loading or failed to load
                        Div(attrs = {
                            style {
                                padding(8.px)
                                color(rgba(255, 255, 255, 0.5))
                            }
                        }) {
                            Text("Loading quest: ${progress.questId}")
                        }
                    }
                }
            }
        },
        actions = {
            DialogButton(
                onClick = onClose,
                style = DialogButtonStyle.Secondary
            ) {
                Translated("Close")
            }
        }
    )
}
