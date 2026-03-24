package main.components

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import main.api.AssetApi
import main.api.AssetItemApi
import main.theme.Styles
import main.theme.Theme
import models.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun AdminEditAssetDialog(
    asset: Asset,
    onClose: () -> Unit,
    onDeleted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isDeleting by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Asset item state per type
    var itemCells by remember { mutableStateOf<List<ItemAssetItem>>(emptyList()) }
    var spriteItems by remember { mutableStateOf<List<SpriteSheetAssetItem>>(emptyList()) }
    var worldItems by remember { mutableStateOf<List<WorldAssetItem>>(emptyList()) }
    var objectItems by remember { mutableStateOf<List<ObjectAssetItem>>(emptyList()) }

    var isLoadingItems by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(asset.key, asset.type) {
        isLoadingItems = true
        errorMessage = ""
        try {
            when (asset.type) {
                AssetType.Items -> itemCells = AssetItemApi.getItemAssetItemsByAsset(asset.key)
                AssetType.SpriteSheet -> spriteItems = AssetItemApi.getSpriteSheetAssetItemsByAsset(asset.key)
                AssetType.World -> worldItems = AssetItemApi.getWorldAssetItemsByAsset(asset.key)
                AssetType.Objects -> objectItems = AssetItemApi.getObjectAssetItemsByAsset(asset.key)
            }
        } catch (t: Throwable) {
            console.error("Failed to load asset items", t)
            errorMessage = t.message ?: "Failed to load asset items"
        } finally {
            isLoadingItems = false
        }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Edit asset"),
        headerActions = {
            Button(attrs = {
                classes(Styles.baseDialogButtonSecondary)
                onClick { showDeleteConfirm = true }
            }) { Translated("Delete") }
        },
        content = {
            if (errorMessage.isNotEmpty()) {
                P(attrs = { classes(Styles.formErrorMessage) }) { Text(errorMessage) }
            }

            // Read-only details
            Div(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    gap(8.px)
                    width(100.percent)
                    marginBottom(12.px)
                }
            }) {
                Span { Translated("Type") }
                P { Text(asset.type.name) }
                Span { Translated("File") }
                P { Text(asset.file) }
            }

            // Per-type editor scaffold (placeholders for now)
            when (asset.type) {
                AssetType.Items -> {
                    var selectedCellIndex by remember { mutableStateOf<Int?>(null) }
                    fun getCellItem(idx: Int): ItemAssetItem? = itemCells.firstOrNull { it.index == idx }
                    fun ensureCellItem(idx: Int): ItemAssetItem {
                        val existing = getCellItem(idx)
                        if (existing != null) return existing
                        val newOne = ItemAssetItem(assetId = asset.key, index = idx, title = "", category = "")
                        itemCells = itemCells + newOne
                        return newOne
                    }
                    Div(attrs = { style { padding(8.px); gap(8.px); display(DisplayStyle.Flex); flexDirection(FlexDirection.Column) } }) {
                        // 320x320 grid of 32px cells (10x10)
                        Div(attrs = {
                            style {
                                width(320.px)
                                height(320.px)
                                display(DisplayStyle.Grid)
                                property("grid-template-columns", "repeat(10, 32px)")
                                property("grid-template-rows", "repeat(10, 32px)")
                                property("gap", "0")
                                property("border", "1px solid rgba(255,255,255,0.15)")
                                property("background-image", "url(\"/raw/data/spritesheets/${asset.file}.png\")")
                                property("background-size", "320px 320px")
                                property("background-position", "0 0")
                                property("image-rendering", "pixelated")
                            }
                        }) {
                            for (r in 0 until 10) {
                                for (c in 0 until 10) {
                                    val idx = (9 - r) * 10 + c
                                    val hasDef = getCellItem(idx) != null
                                    val isSel = selectedCellIndex == idx
                                    Div(attrs = {
                                        style {
                                            width(32.px)
                                            height(32.px)
                                            boxSizing("border-box")
                                            property("border", "1px solid rgba(255,255,255,0.08)")
                                            property("background", when {
                                                isSel -> "#4468"
                                                hasDef -> "#2a2a3a88"
                                                else -> "transparent"
                                            })
                                            property("cursor", "pointer")
                                        }
                                        onClick {
                                            selectedCellIndex = idx
                                            ensureCellItem(idx)
                                        }
                                    }) {}
                                }
                            }
                        }
                        // Inline editor for selected cell
                        if (selectedCellIndex != null) {
                            val idx = selectedCellIndex!!
                            val current = ensureCellItem(idx)
                            Label { Text("Selected cell #$idx") }
                            Label { Translated("Title") }
                            TextInput(
                                value = current.title,
                                attrs = {
                                    classes(Styles.inputField)
                                    onInput { ev ->
                                        itemCells = itemCells.map { if (it.index == idx) it.copy(title = ev.value) else it }
                                    }
                                }
                            )
                            Label { Translated("Category") }
                            TextInput(
                                value = current.category,
                                attrs = {
                                    classes(Styles.inputField)
                                    onInput { ev ->
                                        itemCells = itemCells.map { if (it.index == idx) it.copy(category = ev.value) else it }
                                    }
                                }
                            )
                            // Remove definition for this cell
                            Div {
                                Button(attrs = {
                                    classes(Styles.baseDialogButtonSecondary)
                                    onClick {
                                        itemCells = itemCells.filterNot { it.index == idx }
                                        selectedCellIndex = null
                                    }
                                }) { Translated("Delete") }
                            }
                        }
                        P { Text("Cells configured: ${itemCells.size}") }
                    }
                }
                AssetType.SpriteSheet -> {
                    // Ensure at least one editable item exists in state
                    if (spriteItems.isEmpty()) {
                        spriteItems = listOf(
                            SpriteSheetAssetItem(
                                assetId = asset.key,
                                title = "",
                                category = "",
                                customWeights = false,
                                weightsCsv = null,
                            )
                        )
                    }
                    val sItem = spriteItems.first()
                    Div(attrs = { classes(Styles.assetEditorSection) }) {
                        // Spritesheet preview (256x256)
                        Img(
                            src = "/raw/data/spritesheets/${asset.file}.png",
                            alt = asset.file,
                            attrs = { classes(Styles.spriteSheetPreview) }
                        )
                        // Simple editors for title and category
                        Label { Translated("Title") }
                        TextInput(
                            value = sItem.title,
                            attrs = {
                                classes(Styles.inputField)
                                onInput { ev ->
                                    spriteItems = listOf(sItem.copy(title = ev.value))
                                }
                            }
                        )
                        Label { Translated("Category") }
                        TextInput(
                            value = sItem.category,
                            attrs = {
                                classes(Styles.inputField)
                                onInput { ev ->
                                    spriteItems = listOf(sItem.copy(category = ev.value))
                                }
                            }
                        )
                        // Toggle for custom weights
                        LabeledCheckbox(
                            checked = sItem.customWeights,
                            enabled = true,
                            label = translated("Customize wind weights"),
                            onCheckedChange = { next ->
                                spriteItems = listOf(sItem.copy(customWeights = next))
                            },
                        )
                        if (sItem.customWeights) {
                            // Manage 5x5 grid state derived from CSV
                            var weightGrid by remember(sItem.weightsCsv, sItem.customWeights) {
                                mutableStateOf(
                                    sItem.weightsCsv?.let { csv ->
                                        val parts = csv.split(',', ' ', ';', '\n', '\t').mapNotNull { it.trim().takeIf { it.isNotEmpty() } }
                                        if (parts.size >= 25) {
                                            val nums = parts.take(25).mapNotNull { it.toDoubleOrNull() }
                                            if (nums.size == 25) List(5) { r -> List(5) { c -> nums[r * 5 + c].coerceIn(0.0, 1.0).toString() } } else List(5) { List(5) { "" } }
                                        } else List(5) { List(5) { "" } }
                                    } ?: List(5) { List(5) { "" } }
                                )
                            }
                            fun gridToCsv(g: List<List<String>>): String {
                                val nums = buildList {
                                    for (r in 0 until 5) for (c in 0 until 5) add((g[r][c].toDoubleOrNull() ?: 0.0).coerceIn(0.0, 1.0).toString())
                                }
                                return nums.joinToString(",")
                            }

                            WeightsGridEditor(
                                grid = weightGrid,
                                onGridChange = { g ->
                                    weightGrid = g
                                    spriteItems = listOf(sItem.copy(weightsCsv = gridToCsv(g)))
                                },
                                showReset = true,
                                onReset = {
                                    val rv = listOf("0", "0.0125", "0.025", "0.5", "1")
                                    val def = List(5) { j -> List(5) { rv[j] } }
                                    weightGrid = def
                                    spriteItems = listOf(sItem.copy(weightsCsv = gridToCsv(def)))
                                },
                                backgroundUrl = "/raw/data/spritesheets/${asset.file}.png",
                                isWearable = true,
                            )
                        }
                    }
                }
                AssetType.World -> {
                    // Ensure at least one world item exists
                    if (worldItems.isEmpty()) {
                        worldItems = listOf(
                            WorldAssetItem(
                                assetId = asset.key,
                                title = "",
                                category = "",
                            )
                        )
                    }
                    val wItem = worldItems.first()
                    Div(attrs = { style { padding(8.px); gap(8.px); display(DisplayStyle.Flex); flexDirection(FlexDirection.Column) } }) {
                        P { Translated("World preview") }
                        BasicSceneRenderer(filename = asset.file)
                        Label { Translated("Title") }
                        TextInput(
                            value = wItem.title,
                            attrs = {
                                classes(Styles.inputField)
                                onInput { ev ->
                                    worldItems = listOf(wItem.copy(title = ev.value))
                                }
                            }
                        )
                        Label { Translated("Category") }
                        TextInput(
                            value = wItem.category,
                            attrs = {
                                classes(Styles.inputField)
                                onInput { ev ->
                                    worldItems = listOf(wItem.copy(category = ev.value))
                                }
                            }
                        )
                    }
                }
                AssetType.Objects -> {
                    // Ensure at least one collection row exists for ease of editing
                    if (objectItems.isEmpty()) {
                        objectItems = listOf(
                            ObjectAssetItem(
                                assetId = asset.key,
                                title = "",
                                category = "",
                                collection = "",
                            )
                        )
                    }

                    var availableCollections by remember { mutableStateOf<List<String>>(emptyList()) }

                    Div(attrs = { style { padding(8.px); gap(8.px); display(DisplayStyle.Flex); flexDirection(FlexDirection.Column) } }) {
                        P { Translated("Objects preview") }
                        BasicSceneRenderer(
                            filename = asset.file,
                            onCollectionsLoaded = { availableCollections = it },
                        )

                        if (availableCollections.isNotEmpty()) {
                            Div(attrs = {
                                style {
                                    display(DisplayStyle.Flex)
                                    flexDirection(FlexDirection.Column)
                                    gap(4.px)
                                    padding(8.px)
                                    property("background-color", "rgba(255, 255, 255, 0.05)")
                                    borderRadius(8.px)
                                }
                            }) {
                                availableCollections.forEach { name ->
                                    val isAdded = objectItems.any { it.collection == name }
                                    Div(attrs = {
                                        style {
                                            display(DisplayStyle.Flex)
                                            property("gap", "8px")
                                            property("align-items", "center")
                                        }
                                    }) {
                                        Span(attrs = {
                                            style {
                                                if (isAdded) {
                                                    textDecoration("line-through")
                                                    opacity(0.6)
                                                }
                                            }
                                        }) { Text(name) }
                                        if (isAdded) {
                                            Span(attrs = {
                                                style {
                                                    color(Theme.colors.success)
                                                    fontWeight("bold")
                                                }
                                            }) { Text("✓") }
                                        }
                                    }
                                }

                                val remaining = availableCollections.filter { name ->
                                    objectItems.none { it.collection == name }
                                }
                                if (remaining.isNotEmpty()) {
                                    Button(attrs = {
                                        classes(Styles.baseDialogButtonPrimary)
                                        style { marginTop(8.px) }
                                        onClick {
                                            val newItems = remaining.map { name ->
                                                ObjectAssetItem(
                                                    assetId = asset.key,
                                                    title = name,
                                                    category = "",
                                                    collection = name,
                                                )
                                            }
                                            // Remove placeholder if it's the only item and empty
                                            val base = if (objectItems.size == 1 && objectItems[0].collection.isEmpty()) {
                                                emptyList()
                                            } else {
                                                objectItems
                                            }
                                            objectItems = base + newItems
                                        }
                                    }) { Translated("Add all remaining collections") }
                                }
                            }
                        }

                        // Rows
                        for ((idx, obj) in objectItems.withIndex()) {
                            Div(attrs = { style { display(DisplayStyle.Flex); flexDirection(FlexDirection.Column); gap(6.px) } }) {
                                Label { Translated("Collection") }
                                TextInput(
                                    value = obj.collection,
                                    attrs = {
                                        classes(Styles.inputField)
                                        onInput { ev ->
                                            val v = ev.value
                                            objectItems = objectItems.mapIndexed { i, old -> if (i == idx) old.copy(collection = v) else old }
                                        }
                                    }
                                )
                                Label { Translated("Title") }
                                TextInput(
                                    value = obj.title,
                                    attrs = {
                                        classes(Styles.inputField)
                                        onInput { ev ->
                                            objectItems = objectItems.mapIndexed { i, old -> if (i == idx) old.copy(title = ev.value) else old }
                                        }
                                    }
                                )
                                Label { Translated("Category") }
                                TextInput(
                                    value = obj.category,
                                    attrs = {
                                        classes(Styles.inputField)
                                        onInput { ev ->
                                            objectItems = objectItems.mapIndexed { i, old -> if (i == idx) old.copy(category = ev.value) else old }
                                        }
                                    }
                                )
                                Div {
                                    Button(attrs = {
                                        classes(Styles.baseDialogButtonSecondary)
                                        onClick {
                                            objectItems = objectItems.filterIndexed { i, _ -> i != idx }
                                            if (objectItems.isEmpty()) {
                                                objectItems = listOf(
                                                    ObjectAssetItem(
                                                        assetId = asset.key,
                                                        title = "",
                                                        category = "",
                                                        collection = "",
                                                    )
                                                )
                                            }
                                        }
                                    }) { Translated("Delete") }
                                }
                            }
                        }

                        // Add row
                        Div {
                            Button(attrs = {
                                classes(Styles.baseDialogButtonPrimary)
                                onClick {
                                    val next = ""
                                    objectItems = objectItems + ObjectAssetItem(
                                        assetId = asset.key,
                                        title = "",
                                        category = "",
                                        collection = next,
                                    )
                                }
                            }) { Translated("Add") }
                        }
                    }
                }
            }
        },
        actions = {
            DialogButton(onClick = onClose, isPrimary = false) { Translated("Close") }
            DialogButton(
                onClick = {
                    if (isSaving) return@DialogButton
                    isSaving = true
                    errorMessage = ""
                    scope.launch {
                        try {
                            when (asset.type) {
                                AssetType.Items -> AssetItemApi.replaceItemAssetItems(asset.key, itemCells)
                                AssetType.SpriteSheet -> AssetItemApi.replaceSpriteSheetAssetItems(asset.key, spriteItems)
                                AssetType.World -> AssetItemApi.replaceWorldAssetItems(asset.key, worldItems)
                                AssetType.Objects -> AssetItemApi.replaceObjectAssetItems(asset.key, objectItems)
                            }
                            onClose()
                        } catch (t: Throwable) {
                            console.error("Failed to save asset items", t)
                            errorMessage = t.message ?: "Failed to save asset items"
                        } finally {
                            isSaving = false
                        }
                    }
                },
                isPrimary = true,
            ) { Translated("Save") }
        }
    )

    if (showDeleteConfirm) {
        BasicConfirmDialog(
            message = translated("Are you sure you want to delete this asset?"),
            onConfirm = {
                if (!isDeleting) {
                    isDeleting = true
                    scope.launch {
                        try {
                            val ok = AssetApi.deleteAsset(asset.key)
                            if (ok) {
                                onDeleted()
                                onClose()
                            } else {
                                errorMessage = "Failed to delete asset"
                            }
                        } catch (t: Throwable) {
                            console.error("Failed to delete asset", t)
                            errorMessage = t.message ?: "Failed to delete asset"
                        } finally {
                            isDeleting = false
                        }
                    }
                }
                showDeleteConfirm = false
            },
            onCancel = { showDeleteConfirm = false }
        )
    }
}
