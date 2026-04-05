package main.components

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.api.QuestApi
import models.Quest
import models.QuestState
import models.PlayerQuestState
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import kotlinx.browser.window
import Translations.str

@Composable
fun AdminQuestsDialog(
    onClose: () -> Unit
) {
    var quests by remember { mutableStateOf<List<Quest>>(emptyList()) }
    var selectedQuest by remember { mutableStateOf<Quest?>(null) }
    var questStates by remember { mutableStateOf<List<QuestState>>(emptyList()) }
    var playerStates by remember { mutableStateOf<List<PlayerQuestState>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var confirmDeleteQuest by remember { mutableStateOf<Quest?>(null) }
    var confirmDeleteState by remember { mutableStateOf<QuestState?>(null) }
    var confirmDeletePlayerState by remember { mutableStateOf<PlayerQuestState?>(null) }
    var editState by remember { mutableStateOf<QuestState?>(null) }
    var editPlayerState by remember { mutableStateOf<PlayerQuestState?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            quests = QuestApi.getQuests()
        } catch (e: Throwable) {
            console.error("Failed to fetch quests", e)
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(selectedQuest) {
        selectedQuest?.let { quest ->
            try {
                questStates = QuestApi.getQuestStates(quest.key)
                playerStates = QuestApi.getAllPlayerQuestStates(quest.key)
            } catch (e: Throwable) {
                console.error("Failed to fetch quest details", e)
            }
        } ?: run {
            questStates = emptyList()
            playerStates = emptyList()
        }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Manage Quests"),
        showCloseButton = true,
        content = {
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Row)
                        gap(16.px)
                        height(500.px)
                        width(800.px)
                        maxWidth(90.vw)
                        maxHeight(70.vh)
                    }
                }
            ) {
                // Quest List
                Div(
                    attrs = {
                        style {
                            width(30.percent)
                            overflowY("auto")
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                            gap(8.px)
                            property("border-right", "1px solid #444")
                            paddingRight(8.px)
                        }
                    }
                ) {
                    H4 { Translated("Quests") }
                    if (isLoading) {
                        Text(translated("Loading..."))
                    } else {
                        quests.forEach { quest ->
                            DialogButton(
                                onClick = { selectedQuest = quest },
                                style = if (selectedQuest?.key == quest.key) DialogButtonStyle.Primary else DialogButtonStyle.Secondary,
                                small = true,
                                attrs = {
                                    style {
                                        width(100.percent)
                                        marginBottom(4.px)
                                    }
                                }
                            ) {
                                Text(quest.name)
                            }
                        }
                    }
                }

                // Quest Details
                Div(
                    attrs = {
                        style {
                            width(70.percent)
                            overflowY("auto")
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                            gap(16.px)
                        }
                    }
                ) {
                    selectedQuest?.let { quest ->
                        H3 { Text(quest.name) }
                        
                        DialogButton(
                            onClick = { confirmDeleteQuest = quest },
                            style = DialogButtonStyle.Danger,
                            small = true
                        ) {
                            Translated("Delete Quest")
                        }

                        // Global States
                        Div {
                            H4 { Translated("Global States") }
                            if (questStates.isEmpty()) {
                                Text(translated("No global states defined."))
                            } else {
                                Table(
                                    attrs = {
                                        style {
                                            width(100.percent)
                                            property("border-collapse", "collapse")
                                        }
                                    }
                                ) {
                                    Thead {
                                        Tr {
                                            Th { Translated("Name") }
                                            Th { Translated("Value") }
                                            Th { Translated("Actions") }
                                        }
                                    }
                                    Tbody {
                                        questStates.forEach { state ->
                                            Tr {
                                                Td { Text(state.name) }
                                                Td { Text(state.value) }
                                                Td {
                                                    DialogButton(
                                                        onClick = { editState = state },
                                                        style = DialogButtonStyle.Primary,
                                                        small = true,
                                                    ) {
                                                        Translated("Edit")
                                                    }
                                                    DialogButton(
                                                        onClick = { confirmDeleteState = state },
                                                        style = DialogButtonStyle.Danger,
                                                        small = true
                                                    ) {
                                                        Translated("Delete")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Player States
                        Div {
                            H4 { Translated("Player States") }
                            if (playerStates.isEmpty()) {
                                Text(translated("No player states found."))
                            } else {
                                Table(
                                    attrs = {
                                        style {
                                            width(100.percent)
                                            property("border-collapse", "collapse")
                                        }
                                    }
                                ) {
                                    Thead {
                                        Tr {
                                            Th { Translated("User ID") }
                                            Th { Translated("Name") }
                                            Th { Translated("Value") }
                                            Th { Translated("Actions") }
                                        }
                                    }
                                    Tbody {
                                        playerStates.forEach { ps ->
                                            Tr {
                                                Td { Text(ps.userId) }
                                                Td { Text(ps.name) }
                                                Td { Text(ps.value) }
                                                Td {
                                                    DialogButton(
                                                        onClick = { editPlayerState = ps },
                                                        style = DialogButtonStyle.Primary,
                                                        small = true,
                                                        attrs = {
                                                            style {
                                                                marginRight(4.px)
                                                            }
                                                        }
                                                    ) {
                                                        Translated("Edit")
                                                    }
                                                    DialogButton(
                                                        onClick = { confirmDeletePlayerState = ps },
                                                        style = DialogButtonStyle.Danger,
                                                        small = true
                                                    ) {
                                                        Translated("Reset")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } ?: run {
                        Div(
                            attrs = {
                                style {
                                    display(DisplayStyle.Flex)
                                    justifyContent(JustifyContent.Center)
                                    alignItems(AlignItems.Center)
                                    height(100.percent)
                                }
                            }
                        ) {
                            Text(translated("Select a quest to see details"))
                        }
                    }
                }
            }

            // Confirmation Dialogs
            confirmDeleteQuest?.let { quest ->
                BasicConfirmDialog(
                    message = translated("Are you sure you want to delete this quest and all its states?"),
                    onConfirm = {
                        confirmDeleteQuest = null
                        scope.launch {
                            try {
                                QuestApi.deleteQuest(quest.key)
                                quests = quests.filter { it.key != quest.key }
                                selectedQuest = null
                            } catch (e: Throwable) {
                                console.error("Failed to delete quest", e)
                            }
                        }
                    },
                    onCancel = { confirmDeleteQuest = null }
                )
            }

            confirmDeleteState?.let { state ->
                BasicConfirmDialog(
                    message = translated("Are you sure you want to delete this global state?"),
                    onConfirm = {
                        confirmDeleteState = null
                        selectedQuest?.let { quest ->
                            scope.launch {
                                try {
                                    QuestApi.deleteQuestState(quest.key, state.name)
                                    questStates = questStates.filter { it.name != state.name }
                                } catch (e: Throwable) {
                                    console.error("Failed to delete state", e)
                                }
                            }
                        }
                    },
                    onCancel = { confirmDeleteState = null }
                )
            }

            confirmDeletePlayerState?.let { ps ->
                BasicConfirmDialog(
                    message = translated("Are you sure you want to reset this player state?"),
                    onConfirm = {
                        confirmDeletePlayerState = null
                        scope.launch {
                            try {
                                QuestApi.deletePlayerQuestState(ps.key)
                                playerStates = playerStates.filter { it.key != ps.key }
                            } catch (e: Throwable) {
                                console.error("Failed to delete player state", e)
                            }
                        }
                    },
                    onCancel = { confirmDeletePlayerState = null }
                )
            }

            editState?.let { state ->
                BasicInputDialog(
                    title = translated("Edit global state"),
                    message = translated("Edit value for") + " ${state.name}",
                    initialValue = state.value,
                    onConfirm = { newValue ->
                        editState = null
                        selectedQuest?.let { quest ->
                            scope.launch {
                                try {
                                    val updated = QuestApi.updateQuestState(
                                        quest.key,
                                        state.copy(value = newValue)
                                    )
                                    questStates = questStates.map { if (it.name == state.name) updated else it }
                                } catch (e: Throwable) {
                                    console.error("Failed to update state", e)
                                }
                            }
                        }
                    },
                    onCancel = { editState = null }
                )
            }

            editPlayerState?.let { ps ->
                BasicInputDialog(
                    title = translated("Edit player state"),
                    message = translated("Edit value for") + " ${ps.name} (User: ${ps.userId})",
                    initialValue = ps.value,
                    onConfirm = { newValue ->
                        editPlayerState = null
                        scope.launch {
                            try {
                                val updated = QuestApi.updatePlayerQuestState(ps.copy(value = newValue))
                                playerStates = playerStates.map { if (it.key == ps.key) updated else it }
                            } catch (e: Throwable) {
                                console.error("Failed to update player state", e)
                            }
                        }
                    },
                    onCancel = { editPlayerState = null }
                )
            }
        },
        actions = {
            DialogButton(onClick = onClose) {
                Translated("Close")
            }
        }
    )
}
