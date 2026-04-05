package main.components.nodeeditor

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.api.QuestApi
import main.components.*
import Translations.str
import models.Quest
import models.QuestState
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Div

@Composable
fun QuestPropertySelector(
    value: String,
    onInput: (String) -> Unit
) {
    var quests by remember { mutableStateOf<List<Quest>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            quests = QuestApi.getQuests()
        } catch (e: Throwable) {
            console.error("Failed to fetch quests", e)
        } finally {
            isLoading = false
        }
    }

    val selectOptions = remember(quests, value) {
        val list = quests.map { it.key to it.name }
        if (value.isNotBlank() && quests.none { it.key == value }) {
            listOf(value to "Unknown quest ($value)") + list
        } else if (value.isBlank() && quests.none { it.key == value }) {
            listOf("" to "Select quest...") + list
        } else list
    }

    Div {
        DialogSelect(
            options = selectOptions,
            value = value,
            onInput = onInput,
            enabled = !isLoading,
            label = null
        )

        DialogButton(
            onClick = { showCreateDialog = true },
            style = DialogButtonStyle.Secondary,
            small = true,
            attrs = {
                style {
                    marginTop(8.px)
                }
            }
        ) {
            Translated("Create new quest")
        }
    }

    if (showCreateDialog) {
        CreateQuestDialog(
            onClose = { showCreateDialog = false },
            onCreated = { newQuest ->
                quests = quests + newQuest
                onInput(newQuest.key)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun CreateQuestDialog(
    onClose: () -> Unit,
    onCreated: (Quest) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BaseDialog(
        onClose = onClose,
        title = translated("Create new quest"),
        content = {
            DialogInput(
                value = name,
                onInput = { name = it },
                label = "Quest name",
                placeholder = str("Enter quest name")
            )
        },
        actions = {
            DialogButton(
                onClick = onClose,
                isDisabled = isSaving
            ) {
                Translated("Cancel")
            }
            DialogButton(
                onClick = {
                    if (name.isNotBlank()) {
                        isSaving = true
                        scope.launch {
                            try {
                                val newQuest = QuestApi.createQuest(Quest(name = name))
                                onCreated(newQuest)
                            } catch (e: Throwable) {
                                console.error("Failed to create quest", e)
                            } finally {
                                isSaving = false
                            }
                        }
                    }
                },
                style = DialogButtonStyle.Primary,
                isDisabled = name.isBlank() || isSaving
            ) {
                Translated("Create")
            }
        }
    )
}

@Composable
fun QuestStatePropertySelector(
    questId: String,
    value: String,
    onInput: (String) -> Unit
) {
    var states by remember(questId) { mutableStateOf<List<QuestState>>(emptyList()) }
    var isLoading by remember(questId) { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(questId) {
        if (questId.isBlank()) {
            states = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        try {
            states = QuestApi.getQuestStates(questId)
        } catch (e: Throwable) {
            console.error("Failed to fetch quest states", e)
        } finally {
            isLoading = false
        }
    }

    val selectOptions = remember(states, value) {
        val list = states.map { it.name to it.name }
        if (value.isNotBlank() && states.none { it.name == value }) {
            listOf(value to "Unknown state ($value)") + list
        } else if (value.isBlank() && states.none { it.name == value }) {
            listOf("" to "Select state...") + list
        } else list
    }

    Div {
        DialogSelect(
            options = selectOptions,
            value = value,
            onInput = onInput,
            enabled = !isLoading && questId.isNotBlank(),
            label = null
        )

        DialogButton(
            onClick = { showCreateDialog = true },
            style = DialogButtonStyle.Secondary,
            small = true,
            isDisabled = questId.isBlank(),
            attrs = {
                style {
                    marginTop(8.px)
                }
            }
        ) {
            Translated("Create new state")
        }
    }

    if (showCreateDialog) {
        CreateQuestStateDialog(
            questId = questId,
            onClose = { showCreateDialog = false },
            onCreated = { newState ->
                states = states + newState
                onInput(newState.name)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun CreateQuestStateDialog(
    questId: String,
    onClose: () -> Unit,
    onCreated: (QuestState) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var initialValue by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BaseDialog(
        onClose = onClose,
        title = translated("Create new state"),
        content = {
            DialogInput(
                value = name,
                onInput = { name = it },
                label = "State name",
                placeholder = str("Enter state name")
            )
            DialogInput(
                value = initialValue,
                onInput = { initialValue = it },
                label = "Initial value",
                placeholder = str("Enter initial value")
            )
        },
        actions = {
            DialogButton(
                onClick = onClose,
                isDisabled = isSaving
            ) {
                Translated("Cancel")
            }
            DialogButton(
                onClick = {
                    if (name.isNotBlank()) {
                        isSaving = true
                        scope.launch {
                            try {
                                val newState = QuestApi.updateQuestState(
                                    questId = questId,
                                    state = QuestState(questId = questId, name = name, value = initialValue)
                                )
                                onCreated(newState)
                            } catch (e: Throwable) {
                                console.error("Failed to create quest state", e)
                            } finally {
                                isSaving = false
                            }
                        }
                    }
                },
                style = DialogButtonStyle.Primary,
                isDisabled = name.isBlank() || isSaving
            ) {
                Translated("Create")
            }
        }
    )
}
