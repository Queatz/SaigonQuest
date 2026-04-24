package main.components.nodeeditor

import Translations.str
import androidx.compose.runtime.*
import main.components.*
import main.components.common.Spinner
import main.api.AiApi
import main.api.ContentApi
import main.api.UserApi
import main.components.media.UploadMediaDialog
import main.theme.NodeEditorStyles
import main.theme.Theme
import models.*
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import main.components.minigame.CoordinatePickerDialog
import utils.json
import kotlin.random.Random
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Composable
fun MinigameConfigEditor(
    type: MinigameType,
    configJson: String,
    onConfigChange: (String) -> Unit
) {
    val items = remember(configJson) {
        try {
            json.decodeFromString<List<MinigameItem>>(configJson)
        } catch (e: Throwable) {
            emptyList()
        }
    }

    val itemsToGenImage = remember(items, type) {
        items.filter { 
            val isBaseValid = (if (type == MinigameType.Scenario) it.hotspot != null else true) && it.image == null && !it.text.isNullOrBlank()
            if (type == MinigameType.Story) {
                isBaseValid && it.matchId == null
            } else {
                isBaseValid
            }
        }
    }

    val itemsToGenAudio = remember(items, type) {
        items.filter { (if (type == MinigameType.Scenario) it.hotspot != null else true) && it.audio == null && !it.text.isNullOrBlank() }
    }

    val semaphore = remember { Semaphore(3) }
    val mutex = remember { Mutex() }

    val heroImageUrl = if (type == MinigameType.Scenario) {
        items.find { it.image != null && it.hotspot == null }?.image
    } else null

    val latestOnConfigChange by rememberUpdatedState(onConfigChange)

    fun updateItems(newItems: List<MinigameItem>) {
        latestOnConfigChange(json.encodeToString(newItems))
    }

    var showAiPromptDialog by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var generatingImagesCount by remember { mutableStateOf(0) }
    var generatingAudioCount by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    Div({
        classes(NodeEditorStyles.propertyGroup)
        style {
            padding(12.px)
            border(1.px, LineStyle.Solid, Color.gray)
            borderRadius(8.px)
            marginTop(8.px)
        }
    }) {
        H4({
            style { margin(0.px, 0.px, 12.px, 0.px) }
        }) {
            Text(translated("Minigame Content"))
        }

        if (type == MinigameType.Scenario) {
            // Special handling for Scenario: Hero Image is the first item with no hotspot
            val heroItem = items.find { it.image != null && it.hotspot == null }
            
            Div({
                classes(NodeEditorStyles.propertyGroup)
            }) {
                Label { Text(translated("Hero Image (Background)")) }
                
                MediaSelector(
                    url = heroItem?.image ?: "",
                    type = MediaType.Image,
                    onSelect = { url ->
                        val newList = items.toMutableList()
                        val existingHeroIndex = items.indexOfFirst { it.image != null && it.hotspot == null }
                        if (existingHeroIndex != -1) {
                            newList[existingHeroIndex] = newList[existingHeroIndex].copy(image = url)
                        } else {
                            newList.add(0, MinigameItem(id = "hero_${Random.nextInt(1000)}", image = url))
                        }
                        updateItems(newList)
                    },
                    onClear = {
                        val newList = items.filterNot { it.image != null && it.hotspot == null }
                        updateItems(newList)
                    }
                )
            }
            
            Hr()
            H5 { Text(translated("Hotspots / Targets")) }
        }

        if (type == MinigameType.Story) {
            val steps = items.filter { it.matchId == null || it.matchId == "" }
            steps.forEachIndexed { index, step ->
                MinigameItemEditor(
                    type = type,
                    item = step,
                    index = index,
                    heroImageUrl = heroImageUrl,
                    onUpdate = { updatedItem ->
                        val newList = items.toMutableList()
                        val actualIndex = items.indexOfFirst { it.id == step.id }
                        if (actualIndex != -1) {
                            newList[actualIndex] = updatedItem
                            updateItems(newList)
                        }
                    },
                    onRemove = {
                        val newList = items.toMutableList()
                        newList.removeAll { it.id == step.id || it.matchId == step.id }
                        updateItems(newList)
                    }
                )

                // Choices for this step
                val choices = items.filter { it.matchId == step.id }
                choices.forEachIndexed { choiceIndex, choice ->
                    Div({ style { marginLeft(24.px); marginTop(8.px) } }) {
                        MinigameItemEditor(
                            type = type,
                            item = choice,
                            index = choiceIndex,
                            onUpdate = { updatedItem ->
                                val newList = items.toMutableList()
                                val actualIndex = items.indexOfFirst { it.id == choice.id }
                                if (actualIndex != -1) {
                                    newList[actualIndex] = updatedItem
                                    updateItems(newList)
                                }
                            },
                            onRemove = {
                                val newList = items.toMutableList()
                                newList.removeAll { it.id == choice.id }
                                updateItems(newList)
                            }
                        )
                    }
                }

                DialogButton(
                    onClick = {
                        val newList = items.toMutableList()
                        val id = "choice_${Random.nextInt(100000)}"
                        newList.add(MinigameItem(id = id, matchId = step.id))
                        updateItems(newList)
                    },
                    style = DialogButtonStyle.Secondary,
                    small = true,
                    attrs = { style { marginLeft(24.px); marginTop(8.px); marginBottom(16.px) } }
                ) {
                    Text(translated("Add choice"))
                }
                Hr()
            }
        } else {
            items.filter {
                if (type == MinigameType.Scenario) it.hotspot != null else true
            }.forEachIndexed { index, item ->
                MinigameItemEditor(
                    type = type,
                    item = item,
                    index = index,
                    heroImageUrl = heroImageUrl,
                    onUpdate = { updatedItem ->
                        val newList = items.toMutableList()
                        val actualIndex = items.indexOfFirst { it.id == item.id }
                        if (actualIndex != -1) {
                            newList[actualIndex] = updatedItem
                            updateItems(newList)
                        }
                    },
                    onRemove = {
                        val newList = items.toMutableList()
                        newList.removeAll { it.id == item.id }
                        updateItems(newList)
                    }
                )
                Hr()
            }
        }

        Div({
            style {
                display(DisplayStyle.Flex)
                gap(8.px)
                flexDirection(FlexDirection.Column)
            }
        }) {
            DialogButton(
                onClick = {
                    val newList = items.toMutableList()
                    val id = "item_${items.size}_${Random.nextInt(1000)}"
                    val newItem = when(type) {
                        MinigameType.Scenario -> MinigameItem(id = id, hotspot = Hotspot(0.5, 0.5))
                        MinigameType.Story -> MinigameItem(id = id)
                        else -> MinigameItem(id = id)
                    }
                    newList.add(newItem)
                    updateItems(newList)
                },
                style = DialogButtonStyle.Primary
            ) {
                Text(translated(if (type == MinigameType.Story) "Add Step" else "Add item"))
            }

            DialogButton(
                onClick = { showAiPromptDialog = true },
                style = DialogButtonStyle.Secondary,
                isLoading = isGenerating
            ) {
                Text(translated("Add items with AI"))
            }

            if (type != MinigameType.Scenario) {
                if (itemsToGenImage.isNotEmpty()) {
                    DialogButton(
                        onClick = {
                            scope.launch {
                                var currentList = json.decodeFromString<List<MinigameItem>>(configJson)
                                generatingImagesCount = itemsToGenImage.size
                                itemsToGenImage.forEach { item ->
                                    launch {
                                        try {
                                            semaphore.withPermit {
                                                val size = if (type == MinigameType.Story) "1280*720" else "1024*1024"
                                                val url = AiApi.generateImage(item.text!! + "\n\nStyle: Realistic photo, cute, natural colors, good framing.", size)
                                                mutex.withLock {
                                                    val idx = currentList.indexOfFirst { it.id == item.id }
                                                    if (idx != -1) {
                                                        val updated = currentList.toMutableList()
                                                        updated[idx] = updated[idx].copy(image = url)
                                                        currentList = updated
                                                        latestOnConfigChange(json.encodeToString(updated))
                                                    }
                                                }
                                            }
                                        } catch (t: Throwable) {
                                            t.printStackTrace()
                                        } finally {
                                            generatingImagesCount--
                                        }
                                    }
                                }
                            }
                        },
                        style = DialogButtonStyle.Secondary,
                        isLoading = generatingImagesCount > 0
                    ) {
                        Text(translated("Generate all images"))
                    }
                }

                if (itemsToGenAudio.isNotEmpty()) {
                    DialogButton(
                        onClick = {
                            scope.launch {
                                var currentList = json.decodeFromString<List<MinigameItem>>(configJson)
                                generatingAudioCount = itemsToGenAudio.size
                                val token = UserApi.getToken() ?: ""
                                itemsToGenAudio.forEach { item ->
                                    launch {
                                        try {
                                            semaphore.withPermit {
                                                val bytes = AiApi.generateTts(item.text!!)
                                                val filename = "audio_${item.id}.mp3"
                                                val upload = ContentApi.upload(bytes, filename, "audio/mpeg", token)
                                                val url = ContentApi.imageUrlFor(upload.uploadedFilePath)
                                                mutex.withLock {
                                                    val idx = currentList.indexOfFirst { it.id == item.id }
                                                    if (idx != -1) {
                                                        val updated = currentList.toMutableList()
                                                        updated[idx] = updated[idx].copy(audio = url)
                                                        currentList = updated
                                                        latestOnConfigChange(json.encodeToString(updated))
                                                    }
                                                }
                                            }
                                        } catch (t: Throwable) {
                                            t.printStackTrace()
                                        } finally {
                                            generatingAudioCount--
                                        }
                                    }
                                }
                            }
                        },
                        style = DialogButtonStyle.Secondary,
                        isLoading = generatingAudioCount > 0
                    ) {
                        Text(translated("Generate all audio"))
                    }
                }
            }
        }

        if (showAiPromptDialog) {
            BasicInputDialog(
                title = translated("Add items with AI prompt"),
                placeholder = translated("Describe what items you want to generate"),
                multiline = true,
                onConfirm = { prompt ->
                    showAiPromptDialog = false
                    isGenerating = true
                    scope.launch {
                        try {
                            val generatedItems = AiApi.generateMinigameItems(type, prompt)
                            val newList = items.toMutableList()
                            newList.addAll(generatedItems.map {
                                if (it.id.isBlank()) it.copy(id = "item_${Random.nextInt(1000000)}") else it
                            })
                            updateItems(newList)
                        } catch (e: Throwable) {
                            errorMessage = e.message ?: "Generation failed"
                        } finally {
                            isGenerating = false
                        }
                    }
                },
                onCancel = { showAiPromptDialog = false }
            )
        }

        if (isGenerating) {
            BaseDialog(
                onClose = {},
                showCloseButton = false,
                clickOutside = false,
                content = {
                    Spinner(message = translated("Generating items..."))
                }
            )
        }

        if (errorMessage != null) {
            BasicAlertDialog(
                message = errorMessage!!,
                onClose = { errorMessage = null }
            )
        }
    }
}

@Composable
fun MinigameItemEditor(
    type: MinigameType,
    item: MinigameItem,
    index: Int,
    heroImageUrl: String? = null,
    onUpdate: (MinigameItem) -> Unit,
    onRemove: () -> Unit
) {
    val isStoryStep = type == MinigameType.Story && (item.matchId == null || item.matchId == "")
    val isStoryChoice = type == MinigameType.Story && (item.matchId != null && item.matchId != "")

    Div({
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            gap(8.px)
            padding(8.px)
            backgroundColor(rgba(255, 255, 255, 0.05))
            borderRadius(4.px)
            if (isStoryChoice) {
                property("border-left", "4px solid ${Theme.colors.secondary}")
            }
        }
    }) {
        Div({
            style {
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.SpaceBetween)
                alignItems(AlignItems.Center)
            }
        }) {
            if (type == MinigameType.Sequencing || isStoryStep) {
                Span({
                    style {
                        fontWeight("bold")
                        color(Theme.colors.primary)
                    }
                }) {
                    Text("${str("Step")} ${index + 1}")
                }
            } else if (isStoryChoice) {
                Span({
                    style {
                        fontWeight("bold")
                        color(Theme.colors.secondary)
                    }
                }) {
                    Text("${str("Choice")} ${index + 1}")
                }
            } else {
                Div()
            }

            DialogButton(
                onClick = onRemove,
                style = DialogButtonStyle.Danger,
                small = true
            ) {
                Text("✕")
            }
        }

        // Common Text field
        DialogInput(
            value = item.text ?: "",
            onInput = { onUpdate(item.copy(text = it)) },
            placeholder = str(
                when {
                    type == MinigameType.Scenario -> "What to find"
                    type == MinigameType.Sequencing -> "Step description / Item name"
                    type == MinigameType.Categorization -> "Item name"
                    type == MinigameType.Identify -> "Identify target name"
                    type == MinigameType.Spelling -> "Word to spell"
                    type == MinigameType.Story && isStoryStep -> "Step narration"
                    type == MinigameType.Story && isStoryChoice -> "Choice text"
                    else -> "Text (Front)"
                }
            )
        )

        if (isStoryChoice) {
            val isCorrect = item.definition == "true" || item.definition == "correct"
            DialogButton(
                onClick = {
                    onUpdate(item.copy(definition = if (isCorrect) null else "true"))
                },
                style = if (isCorrect) DialogButtonStyle.Primary else DialogButtonStyle.Secondary,
                small = true
            ) {
                Text(translated(if (isCorrect) "Correct Option" else "Mark as Correct"))
            }
        }

        if (type == MinigameType.Flashcards || type == MinigameType.Spelling) {
            DialogInput(
                value = item.translation ?: "",
                onInput = { onUpdate(item.copy(translation = it)) },
                placeholder = str("Translation (Back)")
            )
            DialogInput(
                value = item.definition ?: "",
                onInput = { onUpdate(item.copy(definition = it)) },
                placeholder = str("Definition")
            )
        }

        if (type == MinigameType.MatchGame || type == MinigameType.Categorization) {
            DialogInput(
                value = item.matchId ?: "",
                onInput = { onUpdate(item.copy(matchId = it)) },
                placeholder = str(if (type == MinigameType.Categorization) "Category" else "Match Group ID")
            )
        }

        if (type == MinigameType.Scenario && item.hotspot != null) {
            var showPicker by remember { mutableStateOf(false) }

            Div({
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    gap(4.px)
                }
            }) {
                DialogButton(
                    onClick = { showPicker = true },
                    style = DialogButtonStyle.Primary
                ) {
                    Text(translated(if (heroImageUrl != null) "Select Position" else "X=${item.hotspot!!.x.toString().take(4)} Y=${item.hotspot!!.y.toString().take(4)}"))
                }

                Label { Text(translated("Radius (0..1)")) }
                DialogInput(
                    value = item.hotspot!!.radius.toString(),
                    onInput = { onUpdate(item.copy(hotspot = item.hotspot!!.copy(radius = it.toDoubleOrNull() ?: 0.05))) },
                    type = InputType.Number
                )
            }

            if (showPicker && heroImageUrl != null) {
                CoordinatePickerDialog(
                    imageUrl = heroImageUrl,
                    initialX = item.hotspot!!.x,
                    initialY = item.hotspot!!.y,
                    initialRadius = item.hotspot!!.radius,
                    onConfirm = { x, y, radius ->
                        onUpdate(item.copy(hotspot = item.hotspot!!.copy(x = x, y = y, radius = radius)))
                        showPicker = false
                    },
                    onClose = { showPicker = false }
                )
            } else if (showPicker) {
                // Fallback for manual entry if no hero image is set yet
                Div({
                    style {
                        display(DisplayStyle.Flex)
                        gap(8.px)
                    }
                }) {
                    Div({ style { flex(1) } }) {
                        Label { Text("X") }
                        DialogInput(
                            value = item.hotspot!!.x.toString(),
                            onInput = { onUpdate(item.copy(hotspot = item.hotspot!!.copy(x = it.toDoubleOrNull() ?: 0.5))) },
                            type = InputType.Number
                        )
                    }
                    Div({ style { flex(1) } }) {
                        Label { Text("Y") }
                        DialogInput(
                            value = item.hotspot!!.y.toString(),
                            onInput = { onUpdate(item.copy(hotspot = item.hotspot!!.copy(y = it.toDoubleOrNull() ?: 0.5))) },
                            type = InputType.Number
                        )
                    }
                }
            }
        }

        // Media selectors - stacked to save space as requested
        Div({
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                gap(8.px)
            }
        }) {
            MediaSelector(
                url = item.image ?: "",
                type = MediaType.Image,
                onSelect = { onUpdate(item.copy(image = it)) },
                onClear = { onUpdate(item.copy(image = null)) },
                preferredSize = if (type == MinigameType.Story) "1280*720" else null
            )
            MediaSelector(
                url = item.audio ?: "",
                type = MediaType.Audio,
                onSelect = { onUpdate(item.copy(audio = it)) },
                onClear = { onUpdate(item.copy(audio = null)) }
            )
        }
    }
}
