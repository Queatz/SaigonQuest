package main.components.tools

import androidx.compose.runtime.*
import app.softwork.routingcompose.Router
import kotlinx.browser.window
import main.api.AiApi
import main.api.LessonApi
import main.api.withRetry
import main.components.*
import Translations.str
import models.*
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
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
    var suggestingRowsForPages by remember { mutableStateOf(setOf<String>()) }
    var generatingCards by remember { mutableStateOf(setOf<String>()) }
    var generatingItems by remember { mutableStateOf(setOf<String>()) }
    var generatingRows by remember { mutableStateOf(setOf<String>()) }
    var isGeneratingRemaining by remember { mutableStateOf(false) }
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

    fun generateCardImage(card: EventCard) {
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
                    val targetIndex = newList.indexOfFirst { it.id == card.id }
                    if (targetIndex != -1) {
                        newList[targetIndex] = newList[targetIndex].copy(image = url)
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

    fun generateItemImage(item: LessonItem) {
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
                    val targetIndex = newList.indexOfFirst { it.id == item.id }
                    if (targetIndex != -1) {
                        newList[targetIndex] = newList[targetIndex].copy(image = url)
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

    fun generateRowImage(page: ReferencePage, row: ReferenceRow) {
        scope.launch {
            generatingRows = generatingRows + row.id
            error = null
            try {
                val url = withRetry {
                    AiApi.generateImage(
                        prompt = "${row.text} in the context of ${currentScene.title}. High quality."
                    )
                }
                scene = scene?.update {
                    val newPages = referencePages.toMutableList()
                    val targetPageIndex = newPages.indexOfFirst { it.id == page.id }
                    if (targetPageIndex != -1) {
                        val newRows = newPages[targetPageIndex].rows.toMutableList()
                        val targetRowIndex = newRows.indexOfFirst { it.id == row.id }
                        if (targetRowIndex != -1) {
                            newRows[targetRowIndex] = newRows[targetRowIndex].copy(image = url)
                        }
                        newPages[targetPageIndex] = newPages[targetPageIndex].copy(rows = newRows)
                    }
                    referencePages = newPages
                }
            } catch (e: Throwable) {
                error = e.message ?: "Failed to generate row image"
            } finally {
                generatingRows = generatingRows - row.id
            }
        }
    }

    fun suggestRows(page: ReferencePage, suggestionType: String, count: Int) {
        scope.launch {
            suggestingRowsForPages = suggestingRowsForPages + page.id
            error = null
            try {
                val existingRows = page.rows.map { it.text }.filter { it.isNotBlank() }.joinToString(", ")
                val excludePrompt = if (existingRows.isNotBlank()) " EXCLUDE these existing items: $existingRows." else ""
                val typePrompt = when (suggestionType) {
                    "vocabulary" -> "suggest $count vocabulary words with simple definitions (format: `word: definition`) that are relevant to this scene/reference page."
                    "grammar" -> "suggest $count useful grammar points or sentence patterns (format: `pattern: explanation/example`) relevant to this scene/reference page."
                    else -> "suggest $count steps (in format: `title: details`) that are relevant to this reference page."
                }
                val prompt = "Based on the lesson scene '${currentScene.title}' (${currentScene.scenePrompt}) and specifically for the reference page titled '${page.title}', $typePrompt$excludePrompt Use sentence case. Return only a semicolon-separated list (;). Do not include new lines."
                val suggestionStr = AiApi.sendPrompt(prompt = prompt)
                val suggestions = suggestionStr.split(";").map { it.trim() }.filter { it.isNotBlank() }.map { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } }

                scene = scene?.update {
                    val newPages = referencePages.toMutableList()
                    val targetPageIndex = newPages.indexOfFirst { it.id == page.id }
                    if (targetPageIndex != -1) {
                        val newRows = newPages[targetPageIndex].rows.toMutableList()
                        suggestions.forEach { text ->
                            newRows.add(ReferenceRow(id = "row_${Random.nextInt(1000000)}", text = text))
                        }
                        newPages[targetPageIndex] = newPages[targetPageIndex].copy(rows = newRows)
                    }
                    referencePages = newPages
                }
            } catch (e: Throwable) {
                error = e.message ?: "Failed to suggest rows"
            } finally {
                suggestingRowsForPages = suggestingRowsForPages - page.id
            }
        }
    }

    fun suggestCards(count: Int) {
        scope.launch {
            isSuggestingCards = true
            error = null
            try {
                val existingCards = currentScene.eventCards.map { it.text }.filter { it.isNotBlank() }.joinToString(", ")
                val excludePrompt = if (existingCards.isNotBlank()) " EXCLUDE these existing cards: $existingCards." else ""
                val prompt = "Based on the lesson scene '${currentScene.title}' (${currentScene.scenePrompt}), suggest $count short event card descriptions (max 5 words each) that happen at the scene (e.g., environmental changes, unexpected visitors, or sudden occurrences) that are very specific so the student can react to them, NOT actions for the student to perform.$excludePrompt Use sentence case for each description. Return only a comma-separated list of descriptions."
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

    fun suggestItems(count: Int) {
        scope.launch {
            isSuggestingItems = true
            error = null
            try {
                val existingItems = currentScene.items.map { it.name }.filter { it.isNotBlank() }.joinToString(", ")
                val excludePrompt = if (existingItems.isNotBlank()) " EXCLUDE these existing items: $existingItems." else ""
                val prompt = "Based on the lesson scene '${currentScene.title}' (${currentScene.scenePrompt}), suggest $count common items (nouns) that would be found there.$excludePrompt Use sentence case for each item. Return only a comma-separated list of items."
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
        EditorHeader(
            darkMode = darkMode,
            onBack = { router.navigate("/tools/scenes") }
        )

        error?.let {
            Div(attrs = {
                classes(main.theme.Styles.formErrorBanner)
            }) {
                Text(it)
            }
        }

        if (scene == null) return@Div

        DialogInput(
            label = "Lesson title",
            value = currentScene.title,
            onInput = { scene = scene?.update { title = it } }
        )

        H3(attrs = { style { color(if (darkMode) main.theme.Theme.colors.onSurfaceDark else main.theme.Theme.colors.onSurface) } }) { Translated("Scene background") }
        currentScene.sceneImage?.let {
            Img(src = it, attrs = { style { width(100.percent); borderRadius(8.px) } })
        }
        
        Div(attrs = { style { display(DisplayStyle.Flex); gap(8.px) } }) {
            DialogInput(
                placeholder = translated("Describe the scene..."),
                value = currentScene.scenePrompt,
                onInput = { scene = scene?.update { scenePrompt = it } },
                containerAttrs = { style { flexGrow(1) } }
            )
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
            EventCardItem(
                card = card,
                darkMode = darkMode,
                generatingCards = generatingCards,
                onUpdateText = { newValue ->
                    scene = scene?.update {
                        val newList = eventCards.toMutableList()
                        if (index < newList.size) {
                            newList[index] = newList[index].copy(text = newValue)
                        }
                        eventCards = newList
                    }
                },
                onGenerateImage = { generateCardImage(card) },
                onRemove = {
                    scene = scene?.update {
                        val newList = eventCards.toMutableList()
                        if (index < newList.size) {
                            newList.removeAt(index)
                        }
                        eventCards = newList
                    }
                }
            )
        }

        var cardBulkCount by remember { mutableStateOf(6) }
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

            DialogInput(
                type = InputType.Number,
                value = cardBulkCount.toString(),
                onInput = { v -> cardBulkCount = v.toIntOrNull() ?: 1 },
                containerAttrs = { style { width(6.cssRem) } }
            )

            DialogButton(
                isLoading = isSuggestingCards,
                onClick = { suggestCards(cardBulkCount) }
            ) {
                Translated("Suggest cards")
            }
        }

        H3(attrs = { style { color(if (darkMode) main.theme.Theme.colors.onSurfaceDark else main.theme.Theme.colors.onSurface) } }) { Translated("Items") }
        Div(attrs = { style { display(DisplayStyle.Flex); flexWrap(FlexWrap.Wrap); gap(16.px) } }) {
            currentScene.items.forEachIndexed { index, item ->
                LessonItemView(
                    item = item,
                    darkMode = darkMode,
                    generatingItems = generatingItems,
                    onUpdateName = { newValue ->
                        scene = scene?.update {
                            val newList = items.toMutableList()
                            if (index < newList.size) {
                                newList[index] = newList[index].copy(name = newValue)
                            }
                            items = newList
                        }
                    },
                    onGenerateImage = { generateItemImage(item) },
                    onRemove = {
                        scene = scene?.update {
                            val newList = items.toMutableList()
                            if (index < newList.size) {
                                newList.removeAt(index)
                            }
                            items = newList
                        }
                    }
                )
            }
        }

        var itemBulkCount by remember { mutableStateOf(6) }
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

            DialogInput(
                type = InputType.Number,
                value = itemBulkCount.toString(),
                onInput = { v -> itemBulkCount = v.toIntOrNull() ?: 1 },
                containerAttrs = { style { width(6.cssRem) } }
            )

            DialogButton(
                isLoading = isSuggestingItems,
                onClick = { suggestItems(itemBulkCount) }
            ) {
                Translated("Suggest items")
            }
        }

        H3(attrs = { style { color(if (darkMode) main.theme.Theme.colors.onSurfaceDark else main.theme.Theme.colors.onSurface) } }) { Translated("Reference pages") }
        currentScene.referencePages.forEachIndexed { pageIndex, page ->
            ReferencePageItem(
                page = page,
                darkMode = darkMode,
                suggestingRowsForPages = suggestingRowsForPages,
                generatingRows = generatingRows,
                onUpdatePage = { updatedPage ->
                    scene = scene?.update {
                        val newList = referencePages.toMutableList()
                        newList[pageIndex] = updatedPage
                        referencePages = newList
                    }
                },
                onRemovePage = {
                    scene = scene?.update {
                        val newList = referencePages.toMutableList()
                        newList.removeAt(pageIndex)
                        referencePages = newList
                    }
                },
                onSuggestRows = { suggestionType, count -> suggestRows(page, suggestionType, count) },
                onGenerateRowImage = { row -> generateRowImage(page, row) }
            )
        }

        DialogButton(
            onClick = {
                scene = scene?.update {
                    val newList = referencePages.toMutableList()
                    newList.add(ReferencePage(id = "page_${Random.nextInt(1000000)}"))
                    referencePages = newList
                }
            }
        ) {
            Translated("Add reference page")
        }

        EditorBottomBar(
            darkMode = darkMode,
            isSaving = isSaving,
            isGeneratingRemaining = isGeneratingRemaining,
            currentScene = currentScene,
            onSave = { afterSave -> onSave(afterSave) },
            onPrint = {
                onSave {
                    window.open("/tools/scenes/${currentScene.key}/print", "_blank")
                }
            },
            onGenerateRemaining = {
                scope.launch {
                    isGeneratingRemaining = true
                    error = null
                    try {
                        val cardsToGenerate = currentScene.eventCards.filter { it.image == null && it.text.isNotBlank() }
                        val itemsToGenerate = currentScene.items.filter { it.image == null && it.name.isNotBlank() }

                        cardsToGenerate.forEach { generateCardImage(it) }
                        itemsToGenerate.forEach { generateItemImage(it) }

                        currentScene.referencePages.forEach { page ->
                            page.rows.forEach { row ->
                                if (row.image == null && row.text.isNotBlank()) {
                                    generateRowImage(page, row)
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        error = e.message ?: "Failed to generate some images"
                    } finally {
                        isGeneratingRemaining = false
                    }
                }
            }
        )
    }
}
