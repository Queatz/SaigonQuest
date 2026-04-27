package main.components.tools

import androidx.compose.runtime.*
import app.softwork.routingcompose.Router
import kotlinx.browser.window
import main.api.AiApi
import main.api.LessonApi
import main.api.withRetry
import main.components.*
import models.*
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLInputElement
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun LessonEditorPage(id: String) {
    val router = Router.current
    val scope = rememberCoroutineScope()
    var scene by remember { mutableStateOf<LessonScene?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isGeneratingScene by remember { mutableStateOf(false) }
    var isSuggestingCards by remember { mutableStateOf(false) }
    var isSuggestingItems by remember { mutableStateOf(false) }
    var generatingCards by remember { mutableStateOf(setOf<String>()) }
    var generatingItems by remember { mutableStateOf(setOf<String>()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        try {
            scene = LessonApi.getScene(id)
        } catch (e: Throwable) {
            error = e.message ?: "Failed to load lesson"
        }
    }

    if (scene == null && error == null) return

    val currentScene = scene ?: LessonScene()
    val darkMode = rememberDarkMode()

    val onSave = { afterSave: () -> Unit ->
        val s = scene
        if (s != null) {
            scope.launch {
                isSaving = true
                error = null
                try {
                    LessonApi.saveScene(s)
                    afterSave()
                } catch (e: Throwable) {
                    error = e.message ?: "Failed to save lesson"
                } finally {
                    isSaving = false
                }
            }
        }
    }

    val descPlaceholder = translated("Describe the scene...")

    Div(attrs = {
        style {
            padding(24.px)
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            gap(24.px)
            maxWidth(1000.px)
            property("margin", "0 auto")
            height(100.vh)
            overflowY("auto")
            boxSizing("border-box")
            backgroundColor(if (darkMode) main.theme.Theme.colors.backgroundDark else main.theme.Theme.colors.background)
        }
    }) {
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                alignItems(AlignItems.Center)
                gap(16.px)
            }
        }) {
            DialogButton(
                small = true,
                style = DialogButtonStyle.Secondary,
                onClick = { router.navigate("/tools/scenes") },
            ) {
                Translated("Back")
            }
            H2(attrs = { style { color(if (darkMode) main.theme.Theme.colors.onSurfaceDark else main.theme.Theme.colors.onSurface) } }) { Translated("Edit lesson scene") }
        }

        error?.let {
            Div(attrs = {
                classes(main.theme.Styles.formErrorBanner)
            }) {
                Text(it)
            }
        }

        if (scene == null) return@Div

        Label(attrs = { style { color(if (darkMode) main.theme.Theme.colors.onSurfaceDark else main.theme.Theme.colors.onSurface) } }) { Translated("Lesson title") }
        Input(type = InputType.Text, attrs = {
            classes(main.theme.Styles.inputField)
            value(currentScene.title)
            onInput { scene = scene?.update { title = it.value } }
        })

        H3(attrs = { style { color(if (darkMode) main.theme.Theme.colors.onSurfaceDark else main.theme.Theme.colors.onSurface) } }) { Translated("Scene background") }
        currentScene.sceneImage?.let {
            Img(src = it, attrs = { style { width(100.percent); borderRadius(8.px) } })
        }
        
        Div(attrs = { style { display(DisplayStyle.Flex); gap(8.px) } }) {
            Input(type = InputType.Text, attrs = {
                classes(main.theme.Styles.inputField)
                placeholder(descPlaceholder)
                value(currentScene.scenePrompt)
                onInput { scene = scene?.update { scenePrompt = it.value } }
                style { flexGrow(1) }
            })
            DialogButton(
                isLoading = isGeneratingScene,
                onClick = {
                    scope.launch {
                        isGeneratingScene = true
                        error = null
                        try {
                            val url = withRetry {
                                AiApi.generateImage(
                                    prompt = "${currentScene.scenePrompt}. High quality, 16:9 aspect ratio.",
                                    size = "1280*720"
                                )
                            }
                            scene = scene?.update { sceneImage = url }
                        } catch (e: Throwable) {
                            error = e.message ?: "Failed to generate scene"
                        } finally {
                            isGeneratingScene = false
                        }
                    }
                },
            ) {
                Translated("Generate scene")
            }
        }

        H3(attrs = { style { color(if (darkMode) main.theme.Theme.colors.onSurfaceDark else main.theme.Theme.colors.onSurface) } }) { Translated("Event cards") }
        currentScene.eventCards.forEachIndexed { index, card ->
            Div(attrs = {
                style {
                    display(DisplayStyle.Flex); gap(16.px); padding(16.px)
                    backgroundColor(if (darkMode) main.theme.Theme.colors.surfaceVariantDark else main.theme.Theme.colors.surfaceVariant)
                    borderRadius(8.px); alignItems(AlignItems.Center)
                    color(if (darkMode) main.theme.Theme.colors.onSurfaceDark else main.theme.Theme.colors.onSurface)
                }
            }) {
                card.image?.let {
                    Img(src = it, attrs = { style { width(80.px); height(80.px); property("object-fit", "cover"); borderRadius(8.px); } })
                }
                Div(attrs = { style { display(DisplayStyle.Flex); flexDirection(FlexDirection.Column); flexGrow(1); gap(8.px) } }) {
                    Input(type = InputType.Text, attrs = {
                        classes(main.theme.Styles.inputField)
                        value(card.text)
                        onInput { 
                            scene = scene?.update { 
                                val newList = eventCards.toMutableList()
                                if (index < newList.size) {
                                    newList[index] = newList[index].copy(text = it.value)
                                }
                                eventCards = newList
                            }
                        }
                    })
                    DialogButton(
                        small = true,
                        isLoading = generatingCards.contains(card.id),
                        onClick = {
                            scope.launch {
                                generatingCards = generatingCards + card.id
                                error = null
                                try {
                                    val url = withRetry {
                                        AiApi.generateImage(
                                            prompt = "${card.text} in the context of ${currentScene.title}. High quality."
                                        )
                                    }
                                    scene = scene?.update { 
                                        val newList = eventCards.toMutableList()
                                        if (index < newList.size) {
                                            newList[index] = newList[index].copy(image = url)
                                        }
                                        eventCards = newList
                                    }
                                } catch (e: Throwable) {
                                    error = e.message ?: "Failed to generate card"
                                } finally {
                                    generatingCards = generatingCards - card.id
                                }
                            }
                        }
                    ) {
                        Translated("Generate card")
                    }
                }
                DialogButton(
                    small = true,
                    style = DialogButtonStyle.Danger,
                    onClick = {
                        scene = scene?.update { 
                            val newList = eventCards.toMutableList()
                            if (index < newList.size) {
                                newList.removeAt(index)
                            }
                            eventCards = newList
                        }
                    }
                ) {
                    Translated("Remove")
                }
            }
        }
        var cardBulkCount by remember { mutableStateOf(5) }
        Div(attrs = { style { display(DisplayStyle.Flex); gap(8.px); alignItems(AlignItems.Center) } }) {
            DialogButton(
                onClick = {
                    scene = scene?.update { 
                        val newList = eventCards.toMutableList()
                        newList.add(EventCard(id = "card_${Random.nextInt(1000000)}"))
                        eventCards = newList
                    }
                },
            ) {
                Translated("Add event card")
            }

            Input(type = InputType.Number, attrs = {
                classes(main.theme.Styles.inputField)
                style { width(60.px) }
                value(cardBulkCount.toString())
                onInput { ev ->
                    val v = (ev.target as HTMLInputElement).value
                    cardBulkCount = v.toIntOrNull() ?: 1
                }
            })

            DialogButton(
                isLoading = isSuggestingCards,
                onClick = {
                    scope.launch {
                        isSuggestingCards = true
                        error = null
                        try {
                            val existingCards = currentScene.eventCards.map { it.text }.filter { it.isNotBlank() }.joinToString(", ")
                            val excludePrompt = if (existingCards.isNotBlank()) " EXCLUDE these existing cards: $existingCards." else ""
                            val prompt = "Based on the lesson scene '${currentScene.title}' (${currentScene.scenePrompt}), suggest $cardBulkCount short event card descriptions (max 5 words each) that happen at the scene (e.g., environmental changes, unexpected visitors, or sudden occurrences) that are very specific so the student can react to them, NOT actions for the student to perform.$excludePrompt Use sentence case for each description. Return only a comma-separated list of descriptions."
                            val suggestionStr = AiApi.sendPrompt(prompt = prompt)
                            val suggestions = suggestionStr.split(",").map { it.trim() }.filter { it.isNotBlank() }.map { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } }
                            
                            scene = scene?.update {
                                val newList = eventCards.toMutableList()
                                suggestions.forEach { text ->
                                    newList.add(EventCard(id = "card_${Random.nextInt(1000000)}", text = text))
                                }
                                eventCards = newList
                            }
                        } catch (e: Throwable) {
                            error = e.message ?: "Failed to suggest cards"
                        } finally {
                            isSuggestingCards = false
                        }
                    }
                }
            ) {
                Translated("Suggest cards")
            }
        }

        H3(attrs = { style { color(if (darkMode) main.theme.Theme.colors.onSurfaceDark else main.theme.Theme.colors.onSurface) } }) { Translated("Items") }
        Div(attrs = { style { display(DisplayStyle.Flex); flexWrap(FlexWrap.Wrap); gap(16.px) } }) {
            currentScene.items.forEachIndexed { index, item ->
                Div(attrs = {
                    style {
                        width(120.px); display(DisplayStyle.Flex); flexDirection(FlexDirection.Column)
                        gap(8.px); padding(8.px); backgroundColor(if (darkMode) main.theme.Theme.colors.surfaceVariantDark else main.theme.Theme.colors.surfaceVariant)
                        borderRadius(8.px)
                        color(if (darkMode) main.theme.Theme.colors.onSurfaceDark else main.theme.Theme.colors.onSurface)
                    }
                }) {
                    item.image?.let {
                        Img(src = it, attrs = { style { width(100.percent); backgroundColor(Color.white); borderRadius(8.px); } })
                    }
                    Input(type = InputType.Text, attrs = {
                        classes(main.theme.Styles.inputField)
                        value(item.name)
                        onInput { 
                            scene = scene?.update { 
                                val newList = items.toMutableList()
                                if (index < newList.size) {
                                    newList[index] = newList[index].copy(name = it.value)
                                }
                                items = newList
                            }
                        }
                    })
                    DialogButton(
                        small = true,
                        isLoading = generatingItems.contains(item.id),
                        onClick = {
                            scope.launch {
                                generatingItems = generatingItems + item.id
                                error = null
                                try {
                                    val url = withRetry {
                                        AiApi.generateImage(
                                            prompt = "Isolated ${item.name} on a pure white background. NO shadows, NO textures, NO gradient, NO floor, pure (#ffffff) white background only. High quality, clear focus.",
                                        )
                                    }
                                    scene = scene?.update { 
                                        val newList = items.toMutableList()
                                        if (index < newList.size) {
                                            newList[index] = newList[index].copy(image = url)
                                        }
                                        items = newList
                                    }
                                } catch (e: Throwable) {
                                    error = e.message ?: "Failed to generate item"
                                } finally {
                                    generatingItems = generatingItems - item.id
                                }
                            }
                        }
                    ) {
                        Translated("Generate item")
                    }
                    DialogButton(
                        small = true,
                        style = DialogButtonStyle.Danger,
                        onClick = {
                            scene = scene?.update { 
                                val newList = items.toMutableList()
                                if (index < newList.size) {
                                    newList.removeAt(index)
                                }
                                items = newList
                            }
                        }
                    ) {
                        Text("Remove")
                    }
                }
            }
        }
        var itemBulkCount by remember { mutableStateOf(5) }
        Div(attrs = { style { display(DisplayStyle.Flex); gap(8.px); alignItems(AlignItems.Center) } }) {
            DialogButton(
                onClick = {
                    scene = scene?.update { 
                        val newList = items.toMutableList()
                        newList.add(LessonItem(id = "item_${Random.nextInt(1000000)}"))
                        items = newList
                    }
                },
            ) {
                Translated("Add item")
            }

            Input(type = InputType.Number, attrs = {
                classes(main.theme.Styles.inputField)
                style { width(60.px) }
                value(itemBulkCount.toString())
                onInput { ev ->
                    val v = (ev.target as HTMLInputElement).value
                    itemBulkCount = v.toIntOrNull() ?: 1
                }
            })

            DialogButton(
                isLoading = isSuggestingItems,
                onClick = {
                    scope.launch {
                        isSuggestingItems = true
                        error = null
                        try {
                            val existingItems = currentScene.items.map { it.name }.filter { it.isNotBlank() }.joinToString(", ")
                            val excludePrompt = if (existingItems.isNotBlank()) " EXCLUDE these existing items: $existingItems." else ""
                            val prompt = "Based on the lesson scene '${currentScene.title}' (${currentScene.scenePrompt}), suggest $itemBulkCount common items (nouns) that would be found there.$excludePrompt Use sentence case for each item. Return only a comma-separated list of items."
                            val suggestionStr = AiApi.sendPrompt(prompt = prompt)
                            val suggestions = suggestionStr.split(",").map { it.trim() }.filter { it.isNotBlank() }.map { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } }
                            
                            scene = scene?.update {
                                val newList = items.toMutableList()
                                suggestions.forEach { name ->
                                    newList.add(LessonItem(id = "item_${Random.nextInt(1000000)}", name = name))
                                }
                                items = newList
                            }
                        } catch (e: Throwable) {
                            error = e.message ?: "Failed to suggest items"
                        } finally {
                            isSuggestingItems = false
                        }
                    }
                }
            ) {
                Translated("Suggest items")
            }
        }

            Div(attrs = {
                style {
                    display(DisplayStyle.Flex); gap(16.px); marginTop(32.px)
                    position(org.jetbrains.compose.web.css.Position.Sticky); bottom(16.px)
                    backgroundColor(if (darkMode) main.theme.Theme.colors.backgroundDark else main.theme.Theme.colors.background)
                    padding(16.px)
                    property("z-index", "100")
                }
            }) {
            DialogButton(
                isLoading = isSaving,
                onClick = {
                    onSave {
                        router.navigate("/tools/scenes")
                    }
                },
            ) {
                Translated("Save lesson")
            }
            DialogButton(
                style = DialogButtonStyle.Secondary,
                isLoading = isSaving,
                onClick = {
                    onSave {
                        window.open("/tools/scenes/${currentScene.key}/print", "_blank")
                    }
                },
            ) {
                Translated("Print document")
            }
        }
    }
}
