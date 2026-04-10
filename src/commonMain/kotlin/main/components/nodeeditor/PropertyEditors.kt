package main.components.nodeeditor

import Translations.str
import androidx.compose.runtime.*
import main.api.DocApi
import main.api.ItemApi
import main.components.*
import main.components.chat.RichTextEditorDialog
import main.components.inventory.ItemRenderer
import main.components.inventory.SelectItemDialog
import main.components.media.UploadMediaDialog
import main.theme.NodeEditorStyles
import models.MediaType
import models.nodeeditor.PropertyKey
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun RichTextPropertyEditor(
    value: String,
    onUpdate: (String) -> Unit,
    title: String = str("Dialogue")
) {
    var showRichEditorDialog by remember { mutableStateOf(false) }

    DialogInput(
        value = value,
        onInput = onUpdate,
        multiline = true,
        placeholder = str("Enter text"),
        attrs = {
            attr("rows", "4")
        }
    )

    Div({
        classes(NodeEditorStyles.propertyValue)
        style {
            property("display", "flex")
            property("gap", "8px")
            padding(0.px)
        }
    }) {
        DialogButton(
            onClick = { showRichEditorDialog = true },
            style = DialogButtonStyle.Primary
        ) { Translated("Rich editor") }
    }

    if (showRichEditorDialog) {
        RichTextEditorDialog(
            onClose = { showRichEditorDialog = false },
            onSend = { markdown ->
                onUpdate(markdown)
                showRichEditorDialog = false
            },
            initialText = value,
            confirmButtonText = str("Save"),
            title = title,
            placeholder = ""
        )
    }
}

@Composable
fun DocPropertyEditor(
    worldId: String,
    currentId: String,
    onUpdate: (String) -> Unit
) {
    var selectedDoc by remember(currentId) { mutableStateOf<models.Doc?>(null) }
    var loadingDoc by remember(currentId) { mutableStateOf(false) }
    var showSelect by remember { mutableStateOf(false) }

    LaunchedEffect(currentId) {
        if (currentId.isNotBlank()) {
            loadingDoc = true
            try {
                selectedDoc = DocApi.getDoc(currentId)
            } catch (t: Throwable) {
                console.error("Failed to load doc by id", t)
                selectedDoc = null
            } finally {
                loadingDoc = false
            }
        } else {
            selectedDoc = null
        }
    }

    // Preview and info
    Div({
        classes(NodeEditorStyles.propertyValue)
        style {
            property("display", "flex")
            property("gap", "8px")
            property("align-items", "center")
        }
    }) {
        when {
            loadingDoc -> Text(translated("Loading..."))
            selectedDoc != null -> Text(selectedDoc!!.title.ifBlank { translated("Selected doc") })
            else -> Text(translated("No doc selected"))
        }
    }

    // Actions
    Div({
        classes(NodeEditorStyles.propertyValue)
        style {
            property("display", "flex")
            property("gap", "8px")
        }
    }) {
        DialogButton(
            onClick = { showSelect = true },
            style = DialogButtonStyle.Primary
        ) { Translated(if (currentId.isBlank()) "Select doc" else "Change") }

        if (currentId.isNotBlank()) {
            DialogButton(
                onClick = { onUpdate("") },
                style = DialogButtonStyle.Danger
            ) { Translated("Clear") }
        }
    }

    if (showSelect) {
        main.components.worlddocs.SelectDocDialog(
            worldId = worldId,
            onSelect = { d ->
                onUpdate(d.key)
                showSelect = false
            },
            onClose = { showSelect = false },
        )
    }
}

@Composable
fun ItemPropertyEditor(
    currentId: String,
    quantity: Int,
    onUpdate: (String, Int) -> Unit
) {
    var selectedItem by remember(currentId) { mutableStateOf<models.Item?>(null) }
    var loadingItem by remember(currentId) { mutableStateOf(false) }
    var showSelect by remember { mutableStateOf(false) }

    LaunchedEffect(currentId) {
        if (currentId.isNotBlank()) {
            loadingItem = true
            try {
                selectedItem = ItemApi.getItemById(currentId)
            } catch (t: Throwable) {
                console.error("Failed to load item by id", t)
                selectedItem = null
            } finally {
                loadingItem = false
            }
        } else {
            selectedItem = null
        }
    }

    // Preview and info
    Div({
        classes(NodeEditorStyles.propertyValue)
        style {
            property("display", "flex")
            property("gap", "8px")
            property("align-items", "center")
        }
    }) {
        if (selectedItem != null) {
            ItemRenderer(item = selectedItem, size = 32)
            Text(selectedItem!!.title.ifBlank { translated("Selected item") } + "  x" + quantity)
        } else if (loadingItem) {
            Text(translated("Loading..."))
        } else {
            Text(translated("No item selected"))
        }
    }

    // Actions
    Div({
        classes(NodeEditorStyles.propertyValue)
        style {
            property("display", "flex")
            property("gap", "8px")
        }
    }) {
        DialogButton(
            onClick = { showSelect = true },
            style = DialogButtonStyle.Primary
        ) { Translated("Select item") }

        if (currentId.isNotBlank()) {
            DialogButton(
                onClick = { onUpdate("", 1) },
                style = DialogButtonStyle.Danger
            ) { Translated("Clear") }
        }
    }

    if (showSelect) {
        SelectItemDialog(
            initial = if (selectedItem != null) listOf(selectedItem!! to quantity) else emptyList(),
            multiple = false,
            useQuantities = true,
            onConfirm = { list ->
                val choice = list.firstOrNull()
                if (choice != null) {
                    onUpdate(choice.first.key, choice.second)
                }
                showSelect = false
            },
            onClose = { showSelect = false },
        )
    }
}

@Composable
fun ItemListPropertyEditor(
    itemsCsv: String,
    quantitiesCsv: String,
    onUpdate: (String, String) -> Unit
) {
    var previewPairs by remember(itemsCsv, quantitiesCsv) {
        mutableStateOf<List<Pair<models.Item, Int>>>(emptyList())
    }
    var loadingList by remember(itemsCsv) { mutableStateOf(false) }
    var showSelect by remember { mutableStateOf(false) }

    LaunchedEffect(itemsCsv, quantitiesCsv) {
        val ids = itemsCsv.split(',').map { it.trim() }.filter { it.isNotBlank() }
        if (ids.isEmpty()) {
            previewPairs = emptyList()
            return@LaunchedEffect
        }
        loadingList = true
        try {
            val qtys = quantitiesCsv.split(',').map { it.trim().toIntOrNull() ?: 1 }
            val fetched = ItemApi.getItemsByIds(ids)
            val byId = fetched.associateBy { it.key }
            val pairs = ids.mapIndexedNotNull { index, id ->
                val item = byId[id]
                item?.let { it to (qtys.getOrNull(index) ?: 1) }
            }
            previewPairs = pairs
        } catch (t: Throwable) {
            console.error("Failed to load items for preview", t)
            previewPairs = emptyList()
        } finally {
            loadingList = false
        }
    }

    // Preview list
    Div({
        classes(NodeEditorStyles.propertyValue)
        style {
            property("display", "flex")
            property("flex-direction", "column")
            property("gap", "6px")
        }
    }) {
        when {
            loadingList -> Text(translated("Loading..."))
            previewPairs.isEmpty() -> Text(translated("No item selected"))
            else -> {
                for ((item, qty) in previewPairs) {
                    Div({
                        style {
                            property("display", "flex")
                            property("gap", "8px")
                            property("align-items", "center")
                        }
                    }) {
                        ItemRenderer(item = item, size = 24)
                        Text(item.title.ifBlank { translated("Selected item") } + "  x" + qty)
                    }
                }
            }
        }
    }

    // Actions
    Div({
        classes(NodeEditorStyles.propertyValue)
        style {
            property("display", "flex")
            property("gap", "8px")
        }
    }) {
        DialogButton(
            onClick = { showSelect = true },
            style = DialogButtonStyle.Primary
        ) { Translated("Select items") }

        if (itemsCsv.isNotBlank()) {
            DialogButton(
                onClick = { onUpdate("", "") },
                style = DialogButtonStyle.Danger
            ) { Translated("Clear") }
        }
    }

    if (showSelect) {
        SelectItemDialog(
            initial = previewPairs,
            multiple = true,
            useQuantities = true,
            onConfirm = { list ->
                val itemsJoined = list.joinToString(",") { it.first.key }
                val quantitiesJoined = list.joinToString(",") { it.second.toString() }
                onUpdate(itemsJoined, quantitiesJoined)
                showSelect = false
            },
            onClose = { showSelect = false },
        )
    }
}

@Composable
fun MediaSelector(
    url: String,
    type: models.MediaType,
    onSelect: (String) -> Unit,
    onClear: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Div({
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            gap(4.px)
        }
    }) {
        if (url.isNotBlank()) {
            if (type == models.MediaType.Image) {
                FramedBorder(
                    attrs = {
                        style {
                            width(100.percent)
                            boxSizing("border-box")
                            marginBottom(8.px)
                        }
                    }
                ) {
                    Img(src = url, attrs = {
                        style {
                            width(100.percent)
                            display(DisplayStyle.Block)
                            property("object-fit", "contain")
                        }
                    })
                }
            } else {
                Audio(attrs = {
                    attr("src", url)
                    attr("controls", "true")
                    style { width(100.percent); height(32.px); marginBottom(8.px) }
                })
            }
        }

        Div({
            style {
                display(DisplayStyle.Flex)
                gap(4.px)
            }
        }) {
            DialogButton(
                onClick = { showDialog = true },
                style = DialogButtonStyle.Primary
            ) {
                Text(if (url.isBlank()) str("Add ${type.name}") else "✎")
            }
            if (url.isNotBlank()) {
                DialogButton(
                    onClick = onClear,
                    style = DialogButtonStyle.Danger
                ) {
                    Text("✕")
                }
            }
        }

        if (showDialog) {
            UploadMediaDialog(
                onClose = { showDialog = false },
                onSelect = { selectedUrl, _, _ ->
                    onSelect(selectedUrl)
                    showDialog = false
                },
                filter = type
            )
        }
    }
}
