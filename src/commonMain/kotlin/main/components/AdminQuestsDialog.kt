package main.components

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.api.QuestApi
import main.api.ItemApi
import models.*
import main.theme.Styles
import main.components.Translated
import main.components.DialogInput
import main.components.DialogButton
import main.components.DialogButtonStyle
import main.components.BasicConfirmDialog
import main.components.Portal
import main.components.BaseDialog
import main.components.SelectWorldDialog
import main.components.SelectObjectDialog
import main.components.inventory.SelectItemDialog
import main.theme.OverlayStyles
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
    var playerProgress by remember { mutableStateOf<List<PlayerQuestProgress>>(emptyList()) }
    var allItems by remember { mutableStateOf<List<Item>>(emptyList()) }
    
    var userMap by remember { mutableStateOf<Map<String, User>>(emptyMap()) }
    var worldMap by remember { mutableStateOf<Map<String, World>>(emptyMap()) }
    var objectMap by remember { mutableStateOf<Map<String, Object>>(emptyMap()) }
    
    var showSelectItemDialog by remember { mutableStateOf(false) }
    var selectingWorldForStepIndex by remember { mutableStateOf<Int?>(null) }
    var selectingObjectForStepIndex by remember { mutableStateOf<Int?>(null) }
    var stepToDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var confirmDeleteQuest by remember { mutableStateOf<Quest?>(null) }
    var confirmDeleteState by remember { mutableStateOf<QuestState?>(null) }
    var confirmDeletePlayerState by remember { mutableStateOf<PlayerQuestState?>(null) }
    var confirmDeletePlayerProgress by remember { mutableStateOf<PlayerQuestProgress?>(null) }
    var editState by remember { mutableStateOf<QuestState?>(null) }
    var editPlayerState by remember { mutableStateOf<PlayerQuestState?>(null) }
    var editPlayerProgress by remember { mutableStateOf<PlayerQuestProgress?>(null) }
    val scope = rememberCoroutineScope()
    val isMobile by rememberIsMobile()

    LaunchedEffect(Unit) {
        try {
            quests = QuestApi.getQuests()
            allItems = ItemApi.getAllItemsAdmin()
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
                playerProgress = QuestApi.getAllPlayerQuestProgress(quest.key)

                // Fetch users for player progress and player states
                val userIds = (playerProgress.map { it.userId } + playerStates.map { it.userId }).distinct()
                userIds.forEach { userId ->
                    if (!userMap.containsKey(userId)) {
                        scope.launch {
                            try {
                                val user = main.api.UserApi.getUserById(userId)
                                if (user != null) {
                                    userMap = userMap + (userId to user)
                                }
                            } catch (e: Throwable) {
                                // Silent error
                            }
                        }
                    }
                }

                // Fetch worlds for steps
                quest.steps.mapNotNull { it.worldId }.distinct().forEach { worldId ->
                    if (!worldMap.containsKey(worldId)) {
                        scope.launch {
                            try {
                                val world = main.api.WorldApi.getWorld(worldId)
                                worldMap = worldMap + (worldId to world)
                            } catch (e: Throwable) {
                                // Silent error
                            }
                        }
                    }
                }

                // Fetch objects for steps
                quest.steps.mapNotNull { it.objectId }.distinct().forEach { objectId ->
                    if (!objectMap.containsKey(objectId)) {
                        scope.launch {
                            try {
                                val obj = main.api.ObjectApi.getObject(objectId)
                                objectMap = objectMap + (objectId to obj)
                            } catch (e: Throwable) {
                                // Silent error
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                console.error("Failed to fetch quest details", e)
            }
        } ?: run {
            questStates = emptyList()
            playerStates = emptyList()
            playerProgress = emptyList()
        }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Manage Quests"),
        showCloseButton = true,
        banner = {
            if (successMessage != null || errorMessage != null) {
                val isError = errorMessage != null
                val msg = errorMessage ?: successMessage!!
                key(msg) {
                    Div(attrs = {
                        classes(if (isError) Styles.formErrorBanner else Styles.formSuccessBanner)
                    }) {
                        Text(if (isError) "⚠️ " else "✅ ")
                        Text(msg)
                        // Auto-hide after 3 seconds
                        LaunchedEffect(msg) {
                            kotlinx.coroutines.delay(3000)
                            successMessage = null
                            errorMessage = null
                        }
                    }
                }
            }
        },
        content = {
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(if (isMobile) FlexDirection.Column else FlexDirection.Row)
                        gap(16.px)
                        if (isMobile) {
                            width(100.percent)
                            property("height", "auto")
                        } else {
                            height(500.px)
                            width(800.px)
                        }
                        maxWidth(if (isMobile) 95.vw else 90.vw)
                        maxHeight(if (isMobile) 90.vh else 70.vh)
                    }
                }
            ) {
                // Quest List
                Div(
                    attrs = {
                        style {
                            width(if (isMobile) 100.percent else 30.percent)
                            if (isMobile) {
                                maxHeight(200.px)
                                property("border-bottom", "1px solid #444")
                                paddingBottom(8.px)
                            } else {
                                property("border-right", "1px solid #444")
                                paddingRight(8.px)
                            }
                            overflowY("auto")
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                            gap(8.px)
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

                    DialogButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val newQuest = Quest(name = str("New Quest"), description = "")
                                    val created = QuestApi.createQuest(newQuest)
                                    quests = quests + created
                                    selectedQuest = created
                                } catch (e: Throwable) {
                                    console.error("Failed to create quest", e)
                                    errorMessage = str("Failed to create quest")
                                }
                            }
                        },
                        style = DialogButtonStyle.Primary,
                        small = true,
                        attrs = {
                            style {
                                width(100.percent)
                                marginTop(8.px)
                            }
                        }
                    ) {
                        Translated("Add Quest")
                    }
                }

                // Quest Details
                Div(
                    attrs = {
                        style {
                            width(if (isMobile) 100.percent else 70.percent)
                            overflowY("auto")
                            overflowX("auto")
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                            gap(16.px)
                            if (isMobile) {
                                flex(1)
                                property("min-height", "0")
                            }
                        }
                    }
                ) {
                    selectedQuest?.let { quest ->
                        H3 { Text(quest.name) }
                        
                        Div(attrs = { style { display(DisplayStyle.Flex); gap(8.px) } }) {
                            DialogButton(
                                onClick = { confirmDeleteQuest = quest },
                                style = DialogButtonStyle.Danger,
                                small = true
                            ) {
                                Translated("Delete Quest")
                            }
                        }

                        // Basic Info
                        Div {
                            H4 { Translated("Quest Details") }
                            DialogInput(label = "Name", value = quest.name, onInput = { n ->
                                val updated = quest.copy(name = n)
                                updated.key = quest.key
                                updated.createdAt = quest.createdAt
                                selectedQuest = updated
                            })
                            DialogInput(label = "Description", value = quest.description, onInput = { d ->
                                val updated = quest.copy(description = d)
                                updated.key = quest.key
                                updated.createdAt = quest.createdAt
                                selectedQuest = updated
                            })
                        }

                        // Rewards
                        Div {
                            H4 { Translated("Rewards") }
                            Div(attrs = { style { display(DisplayStyle.Flex); flexWrap(FlexWrap.Wrap); gap(8.px) } }) {
                                quest.rewards.forEach { reward ->
                                    val item = allItems.find { it.key == reward.itemId }
                                    Div(attrs = { 
                                        style { 
                                            display(DisplayStyle.Flex)
                                            alignItems(AlignItems.Center)
                                            gap(8.px)
                                            padding(8.px)
                                            border(1.px, LineStyle.Solid, Color("#444"))
                                            borderRadius(8.px)
                                        } 
                                    }) {
                                        Text("${item?.title ?: reward.itemId} (x${reward.quantity})")
                                        DialogButton(
                                            onClick = {
                                                val newRewards = quest.rewards.toMutableList()
                                                newRewards.remove(reward)
                                                val updated = quest.copy(rewards = newRewards)
                                                updated.key = quest.key
                                                updated.createdAt = quest.createdAt
                                                selectedQuest = updated
                                            },
                                            style = DialogButtonStyle.Danger,
                                            small = true
                                        ) {
                                            Text("X")
                                        }
                                    }
                                }
                                DialogButton(
                                    onClick = { showSelectItemDialog = true },
                                    style = DialogButtonStyle.Secondary,
                                    small = true
                                ) {
                                    Translated("Add Reward")
                                }
                            }
                        }

                        // Steps
                        Div {
                            H4 { Translated("Steps") }
                            quest.steps.forEachIndexed { index, step ->
                                Div(attrs = { 
                                    style { 
                                        border(1.px, LineStyle.Solid, Color("#444"))
                                        padding(12.px)
                                        marginBottom(12.px)
                                        borderRadius(8.px)
                                        display(DisplayStyle.Flex)
                                        flexDirection(FlexDirection.Column)
                                        gap(8.px)
                                    } 
                                }) {
                                    Div(attrs = { style { display(DisplayStyle.Flex); justifyContent(JustifyContent.SpaceBetween) } }) {
                                        H5(attrs = { style { margin(0.px) } }) { Text("Step ${index + 1}") }
                                        DialogButton(
                                            onClick = {
                                                stepToDeleteIndex = index
                                            },
                                            style = DialogButtonStyle.Danger,
                                            small = true
                                        ) {
                                            Translated("Remove")
                                        }
                                    }
                                    DialogInput(label = str("Title"), value = step.title, onInput = { t ->
                                        val newSteps = quest.steps.toMutableList()
                                        newSteps[index] = step.copy(title = t)
                                        val updated = quest.copy(steps = newSteps)
                                        updated.key = quest.key
                                        updated.createdAt = quest.createdAt
                                        selectedQuest = updated
                                    })
                                    DialogInput(label = str("Description"), value = step.description, onInput = { d ->
                                        val newSteps = quest.steps.toMutableList()
                                        newSteps[index] = step.copy(description = d)
                                        val updated = quest.copy(steps = newSteps)
                                        updated.key = quest.key
                                        updated.createdAt = quest.createdAt
                                        selectedQuest = updated
                                    })

                                    // World Selection
                                    Div(attrs = { style { display(DisplayStyle.Flex); gap(8.px); alignItems(AlignItems.Center) } }) {
                                        Div(attrs = { style { flex(1); display(DisplayStyle.Flex); flexDirection(FlexDirection.Column) } }) {
                                            Label { Translated("World (optional)") }
                                            val world = step.worldId?.let { worldMap[it] }
                                            if (world != null) {
                                                Text("${world.title} (${world.url})")
                                            } else if (!step.worldId.isNullOrBlank()) {
                                                Text(step.worldId!!)
                                            } else {
                                                Text(translated("No world selected"))
                                            }
                                        }
                                        DialogButton(onClick = { selectingWorldForStepIndex = index }, style = DialogButtonStyle.Secondary, small = true) {
                                            Translated("Select World")
                                        }
                                    }

                                    // Object Selection
                                    Div(attrs = { style { display(DisplayStyle.Flex); gap(8.px); alignItems(AlignItems.Center) } }) {
                                        Div(attrs = { style { flex(1); display(DisplayStyle.Flex); flexDirection(FlexDirection.Column) } }) {
                                            Label { Translated("Object (optional)") }
                                            val obj = step.objectId?.let { objectMap[it] }
                                            if (obj != null) {
                                                Text("${obj.title}")
                                            } else if (!step.objectId.isNullOrBlank()) {
                                                Text(step.objectId!!)
                                            } else {
                                                Text(translated("No object selected"))
                                            }
                                        }
                                        DialogButton(onClick = { selectingObjectForStepIndex = index }, style = DialogButtonStyle.Secondary, small = true) {
                                            Translated("Select Object")
                                        }
                                    }

                                    DialogInput(label = str("Indicator Emoji"), value = step.indicatorEmoji ?: "", onInput = { e ->
                                        val newSteps = quest.steps.toMutableList()
                                        newSteps[index] = step.copy(indicatorEmoji = e.ifBlank { null })
                                        val updated = quest.copy(steps = newSteps)
                                        updated.key = quest.key
                                        updated.createdAt = quest.createdAt
                                        selectedQuest = updated
                                    })
                                    DialogInput(label = str("Status Override"), value = step.statusOverride ?: "", onInput = { s ->
                                        val newSteps = quest.steps.toMutableList()
                                        newSteps[index] = step.copy(statusOverride = s.ifBlank { null })
                                        val updated = quest.copy(steps = newSteps)
                                        updated.key = quest.key
                                        updated.createdAt = quest.createdAt
                                        selectedQuest = updated
                                    })
                                }
                            }
                            DialogButton(
                                onClick = {
                                    val newSteps = quest.steps.toMutableList()
                                    newSteps.add(QuestStep(title = str("New Step")))
                                    val updated = quest.copy(steps = newSteps)
                                    updated.key = quest.key
                                    updated.createdAt = quest.createdAt
                                    selectedQuest = updated
                                },
                                style = DialogButtonStyle.Secondary,
                                small = true
                            ) {
                                Translated("Add Step")
                            }
                        }

                        // Player Progress
                        Div {
                            H4 { Translated("Player Progress") }
                            if (playerProgress.isEmpty()) {
                                Text(translated("No player progress recorded yet."))
                            } else {
                                Table(attrs = { style { width(100.percent); property("border-collapse", "collapse") } }) {
                                    Thead { Tr { Th { Translated("Player") }; Th { Translated("Step") }; Th { Translated("Completed") }; Th { Translated("Actions") } } }
                                    Tbody {
                                        playerProgress.forEach { p ->
                                            val user = userMap[p.userId]
                                            Tr {
                                                Td { Text(user?.username ?: p.userId) }
                                                Td { Text(p.currentStepIndex.toString()) }
                                                Td { Text(if (p.completed) translated("Yes") else translated("No")) }
                                                Td {
                                                    DialogButton(
                                                        onClick = { editPlayerProgress = p },
                                                        style = DialogButtonStyle.Primary,
                                                        small = true,
                                                    ) {
                                                        Translated("Edit")
                                                    }
                                                    DialogButton(
                                                        onClick = { confirmDeletePlayerProgress = p },
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
                                            Th { Translated("Player") }
                                            Th { Translated("Name") }
                                            Th { Translated("Value") }
                                            Th { Translated("Actions") }
                                        }
                                    }
                                    Tbody {
                                        playerStates.forEach { ps ->
                                            val user = userMap[ps.userId]
                                            Tr {
                                                Td { Text(user?.username ?: ps.userId) }
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
            stepToDeleteIndex?.let { index ->
                BasicConfirmDialog(
                    message = translated("Are you sure you want to remove this step?"),
                    onConfirm = {
                        selectedQuest?.let { quest ->
                            val newSteps = quest.steps.toMutableList()
                            newSteps.removeAt(index)
                            val updated = quest.copy(steps = newSteps)
                            updated.key = quest.key
                            updated.createdAt = quest.createdAt
                            selectedQuest = updated
                        }
                        stepToDeleteIndex = null
                    },
                    onCancel = { stepToDeleteIndex = null }
                )
            }

            if (showSelectItemDialog) {
                SelectItemDialog(
                    multiple = true,
                    useQuantities = true,
                    minQuantity = 1,
                    initial = selectedQuest?.rewards?.mapNotNull { r ->
                        val item = allItems.find { it.key == r.itemId }
                        if (item != null) item to r.quantity else null
                    } ?: emptyList(),
                    onConfirm = { itemsWithQty ->
                        selectedQuest?.let { quest ->
                            val updated = quest.copy(rewards = itemsWithQty.map { Reward(it.first.key, it.second) })
                            updated.key = quest.key
                            updated.createdAt = quest.createdAt
                            selectedQuest = updated
                        }
                        showSelectItemDialog = false
                    },
                    onClose = { showSelectItemDialog = false }
                )
            }

            selectingWorldForStepIndex?.let { index ->
                SelectWorldDialog(
                    onClose = { selectingWorldForStepIndex = null },
                    onSelect = { world ->
                        selectedQuest?.let { quest ->
                            val newSteps = quest.steps.toMutableList()
                            val step = newSteps[index]
                            newSteps[index] = step.copy(worldId = world.key)
                            val updated = quest.copy(steps = newSteps)
                            updated.key = quest.key
                            updated.createdAt = quest.createdAt
                            selectedQuest = updated
                        }
                    }
                )
            }

            selectingObjectForStepIndex?.let { index ->
                selectedQuest?.steps?.getOrNull(index)?.let { step ->
                    SelectObjectDialog(
                        worldId = step.worldId ?: "",
                        onClose = { selectingObjectForStepIndex = null },
                        onSelect = { obj ->
                            selectedQuest?.let { quest ->
                                val newSteps = quest.steps.toMutableList()
                                newSteps[index] = step.copy(objectId = obj.key)
                                val updated = quest.copy(steps = newSteps)
                                updated.key = quest.key
                                updated.createdAt = quest.createdAt
                                selectedQuest = updated
                            }
                        }
                    )
                } ?: run { selectingObjectForStepIndex = null }
            }

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

            confirmDeletePlayerProgress?.let { p ->
                BasicConfirmDialog(
                    message = translated("Are you sure you want to reset this player's quest progress?"),
                    onConfirm = {
                        confirmDeletePlayerProgress = null
                        scope.launch {
                            try {
                                QuestApi.deletePlayerQuestProgress(p.key)
                                playerProgress = playerProgress.filter { it.key != p.key }
                                successMessage = str("Player quest progress reset.")
                            } catch (e: Throwable) {
                                console.error("Failed to reset player progress", e)
                                errorMessage = str("Failed to reset player progress")
                            }
                        }
                    },
                    onCancel = { confirmDeletePlayerProgress = null }
                )
            }

            editPlayerProgress?.let { p ->
                val user = userMap[p.userId]
                val userName = user?.username ?: p.userId
                
                BaseDialog(
                    title = translated("Edit Player Progress") + ": $userName",
                    onClose = { editPlayerProgress = null },
                    content = {
                        Div(attrs = { style { padding(16.px); display(DisplayStyle.Flex); flexDirection(FlexDirection.Column); gap(16.px); width(300.px) } }) {
                            // Step Index
                            Div {
                                Label { Translated("Step Index") }
                                DialogInput(
                                    value = p.currentStepIndex.toString(),
                                    onInput = { newVal ->
                                        newVal.toIntOrNull()?.let { editPlayerProgress = p.copy(currentStepIndex = it) }
                                    },
                                    placeholder = str("Step index")
                                )
                            }
                            
                            // Completed checkbox-ish
                            Div(attrs = { style { display(DisplayStyle.Flex); alignItems(AlignItems.Center); gap(8.px) } }) {
                                Input(type = org.jetbrains.compose.web.attributes.InputType.Checkbox) {
                                    checked(p.completed)
                                    onInput { event ->
                                        editPlayerProgress = p.copy(completed = event.value)
                                    }
                                }
                                Label { Translated("Completed") }
                            }
                            
                            DialogButton(
                                onClick = {
                                    val progressToSave = editPlayerProgress!!
                                    editPlayerProgress = null
                                    scope.launch {
                                        try {
                                            val updated = QuestApi.updatePlayerQuestProgress(progressToSave)
                                            playerProgress = playerProgress.map { if (it.key == updated.key) updated else it }
                                            successMessage = str("Player progress updated.")
                                        } catch (e: Throwable) {
                                            console.error("Failed to update player progress", e)
                                            errorMessage = str("Failed to update player progress")
                                        }
                                    }
                                },
                                style = DialogButtonStyle.Good
                            ) {
                                Translated("Save")
                            }
                        }
                    }
                )
            }
        },
        actions = {
            selectedQuest?.let { quest ->
                DialogButton(
                    onClick = {
                        scope.launch {
                            try {
                                val updated = QuestApi.updateQuest(quest)
                                quests = quests.map { if (it.key == updated.key) updated else it }
                                selectedQuest = updated
                                successMessage = str("Quest saved successfully!")
                            } catch (e: Throwable) {
                                console.error("Failed to save quest", e)
                                errorMessage = str("Failed to save quest") + ": ${e.message}"
                            }
                        }
                    },
                    style = DialogButtonStyle.Good
                ) {
                    Translated("Save Quest")
                }
            }
            DialogButton(onClick = onClose) {
                Translated("Close")
            }
        }
    )
}
