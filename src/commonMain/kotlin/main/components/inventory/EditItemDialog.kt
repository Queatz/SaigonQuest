package main.components.inventory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.ItemApi
import main.components.AssetBrowserDialog
import main.components.BaseDialog
import main.components.DialogButton
import main.components.DialogButtonStyle
import main.components.DialogInput
import main.components.IconRenderer
import main.components.LabeledCheckbox
import main.components.Translated
import main.components.WeightsGridEditor
import main.components.translated
import main.theme.Styles
import models.AssetType
import models.Item
import models.ItemWearableLocation
import models.Sex
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.NumberInput
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Select

@Composable
fun EditItemDialog(
    item: Item,
    onClose: () -> Unit,
    onSaved: (Item) -> Unit,
) {
    // Prefilled form state
    var title by remember { mutableStateOf(item.title) }
    var description by remember { mutableStateOf(item.description) }
    var file by remember { mutableStateOf(item.file) }
    var cost by remember { mutableStateOf(item.cost) }
    var assetIndex by remember { mutableStateOf(item.assetIndex.toString()) }
    var wearable by remember { mutableStateOf(item.wearable) }
    var wearableFile by remember { mutableStateOf(item.wearableFile ?: "") }
    var wearableSex by remember { mutableStateOf(item.wearableSex ?: Sex.Boy) }
    var wearableLocation by remember { mutableStateOf(item.wearableLocation ?: ItemWearableLocation.Body) }
    var customizeWeights by remember { mutableStateOf(!item.wearableWeights.isNullOrBlank()) }

    fun parseWeightsCsv(csv: String?): List<List<String>> {
        if (csv.isNullOrBlank()) return List(5) { List(5) { "" } }
        val parts = csv.split(",")
        val padded = if (parts.size >= 25) parts.take(25) else parts + List(25 - parts.size) { "0" }
        val grid = mutableListOf<List<String>>()
        var idx = 0
        for (j in 0 until 5) {
            val row = mutableListOf<String>()
            for (i in 0 until 5) {
                row.add(padded[idx].trim())
                idx++
            }
            grid.add(row)
        }
        return grid
    }

    var weightGrid by remember { mutableStateOf(parseWeightsCsv(item.wearableWeights)) }

    var showAssetBrowser by remember { mutableStateOf(false) }
    var showWearableBrowser by remember { mutableStateOf(false) }

    // Validation state
    var titleError by remember { mutableStateOf("") }
    var fileError by remember { mutableStateOf("") }
    var assetIndexError by remember { mutableStateOf("") }
    var wearableFileError by remember { mutableStateOf("") }

    // Loading state
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    fun validateForm(): Boolean {
        var isValid = true
        if (title.trim().isEmpty()) { titleError = "Title is required"; isValid = false } else { titleError = "" }
        if (file.trim().isEmpty()) { fileError = "File path is required"; isValid = false } else { fileError = "" }
        try {
            val index = assetIndex.toInt()
            if (index < 0) { assetIndexError = "Asset index must be a positive number"; isValid = false } else { assetIndexError = "" }
        } catch (_: Throwable) {
            assetIndexError = "Asset index must be a number"; isValid = false
        }
        if (wearable) {
            if (wearableFile.trim().isEmpty()) { wearableFileError = "Wearable file path is required"; isValid = false } else { wearableFileError = "" }
        }
        return isValid
    }

    fun handleSubmit() {
        if (!validateForm()) return

        var weightsCsv: String? = null
        if (wearable && customizeWeights) {
            val nums = mutableListOf<String>()
            for (j in 0 until 5) {
                for (i in 0 until 5) {
                    val s = weightGrid[j][i].trim()
                    if (s.isEmpty()) {
                        nums.add("0")
                    } else {
                        val v = s.toDoubleOrNull() ?: run {
                            errorMessage = "Wind weights are invalid"; return
                        }
                        val clamped = v.coerceIn(0.0, 1.0)
                        nums.add(clamped.toString())
                    }
                }
            }
            if (nums.size == 25) {
                weightsCsv = nums.joinToString(",")
            }
        }

        isSubmitting = true
        errorMessage = ""

        coroutineScope.launch {
            try {
                val updatedItem = Item(
                    title = title.trim(),
                    description = description.trim(),
                    file = file.trim(),
                    cost = cost,
                    assetIndex = assetIndex.toInt(),
                    wearable = wearable,
                    wearableFile = if (wearable) wearableFile.trim() else null,
                    wearableSex = if (wearable) wearableSex else null,
                    wearableLocation = if (wearable) wearableLocation else null,
                    wearableWeights = if (wearable) weightsCsv else null,
                ).also { it.key = item.key }

                val saved = ItemApi.updateItem(item.key, updatedItem)
                onSaved(saved)
                onClose()
            } catch (t: Throwable) {
                console.error("Error updating item", t)
                errorMessage = t.message ?: "Failed to update item"
                isSubmitting = false
            }
        }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Edit item"),
        content = {
            if (errorMessage.isNotEmpty()) {
                P(attrs = { style { color(rgba(255, 100, 100, 0.9)); marginBottom(16.px) } }) { Translated(errorMessage) }
            }

            // Title
            DialogInput(
                label = "Title",
                value = title,
                onInput = { title = it },
                placeholder = translated("Enter item title"),
            ) {
                if (titleError.isNotEmpty()) {
                    P(attrs = { style { color(rgba(255, 100, 100, 0.9)); fontSize(12.px); marginTop(4.px) } }) { Translated(titleError) }
                }
            }

            // Description
            DialogInput(
                label = "Description",
                value = description,
                onInput = { description = it },
                placeholder = translated("Enter item description"),
                multiline = true,
                attrs = {
                    style { height(80.px) }
                }
            )

            // Asset selection
            Div(attrs = { style { marginBottom(16.px) } }) {
                Label(attrs = { style { display(DisplayStyle.Block); ; marginBottom(4.px) } }) { Translated("Asset") }

                // Preview of selected asset (if any)
                if (file.isNotBlank()) {
                    val idx = assetIndex.toIntOrNull() ?: 0
                    IconRenderer(
                        url = "/raw/data/spritesheets/$file.png",
                        index = idx,
                        size = 48,
                    )
                } else {
                    P { Translated("No results") }
                }

                DialogButton(
                    onClick = { showAssetBrowser = true },
                    style = DialogButtonStyle.Secondary,
                ) { Translated("Browse assets") }
            }

            // Cost
            Div(attrs = { style { marginBottom(16.px) } }) {
                Label(attrs = { style { display(DisplayStyle.Block); ; marginBottom(4.px) } }) { Translated("Cost (VND)") }
                NumberInput(value = cost, min = 0, attrs = {
                    classes(Styles.inputField)
                    onInput { cost = it.value?.toLong() ?: 0L }
                })
            }

            // Wearable toggle
            Div(attrs = { style { marginBottom(16.px) } }) {
                LabeledCheckbox(
                    checked = wearable,
                    enabled = true,
                    label = translated("Is this item wearable?"),
                    onCheckedChange = { next: Boolean -> wearable = next },
                )
            }

            if (wearable) {
                // Wearable sex
                Div(attrs = { style { marginBottom(16.px) } }) {
                    Label(attrs = { style { display(DisplayStyle.Block); marginBottom(4.px) } }) { Translated("Sex") }
                    Select(attrs = {
                        classes(Styles.inputField)
                        onInput { ev ->
                            try {
                                wearableSex = Sex.valueOf(ev.value ?: Sex.Boy.name)
                            } catch (_: Throwable) {}
                        }
                    }) {
                        Sex.entries.forEach { s ->
                            Option(s.name, attrs = { if (s == wearableSex) selected() }) { Translated(s.name) }
                        }
                    }
                }

                // Wearable location
                Div(attrs = { style { marginBottom(16.px) } }) {
                    Label(attrs = { style { display(DisplayStyle.Block); marginBottom(4.px) } }) { Translated("Location") }
                    Select(attrs = {
                        classes(Styles.inputField)
                        onInput { ev ->
                            try {
                                wearableLocation = ItemWearableLocation.valueOf(ev.value ?: ItemWearableLocation.Body.name)
                            } catch (_: Throwable) {}
                        }
                    }) {
                        ItemWearableLocation.entries.forEach { loc ->
                            Option(loc.name, attrs = { if (loc == wearableLocation) selected() }) { Translated(loc.name) }
                        }
                    }
                }

                // Wearable asset (preview + browser)
                Div(attrs = { style { marginBottom(16.px) } }) {
                    Label(attrs = { style { display(DisplayStyle.Block); ; marginBottom(4.px) } }) { Translated("Wearable asset") }

                    // Preview first 32x48 cell if set
                    if (wearableFile.isNotBlank()) {
                        Div(attrs = {
                            style {
                                display(DisplayStyle.Block)
                                marginTop(8.px)
                            }
                        }) {
                            Div(attrs = {
                                classes(Styles.iconRenderer)
                                style {
                                    property("width", "32px")
                                    property("height", "48px")
                                    property("background-image", "url(\"/raw/data/spritesheets/$wearableFile.png\")")
                                    property("background-size", "${32 * 8}px ${48 * 8}px")
                                    property("background-position", "0px 0px")
                                }
                            }) {}
                        }
                    }

                    // Browse button for wearable sprite sheet
                    DialogButton(
                        onClick = { showWearableBrowser = true },
                        style = DialogButtonStyle.Secondary,
                    ) { Translated("Browse assets") }

                    if (wearableFileError.isNotEmpty()) {
                        P(
                            attrs = {
                                style {
                                    color(rgba(255, 100, 100, 0.9))
                                    fontSize(12.px)
                                    marginTop(4.px)
                                }
                            }
                        ) {
                            Translated(wearableFileError)
                        }
                    }
                }

                // Customize weights checkbox
                Div(attrs = { style { marginBottom(8.px); display(DisplayStyle.Flex); property("align-items", "center") } }) {
                    LabeledCheckbox(
                        checked = customizeWeights,
                        enabled = true,
                        label = translated("Customize wind weights"),
                        onCheckedChange = { next -> customizeWeights = next },
                    )
                }

                if (customizeWeights) {
                    Label(attrs = { style { display(DisplayStyle.Block); ; marginBottom(4.px) } }) { Translated("Wind weights (5x5)") }

                    WeightsGridEditor(
                        grid = weightGrid,
                        onGridChange = { weightGrid = it },
                        showReset = true,
                        backgroundUrl = if (wearable && wearableFile.isNotBlank()) "/raw/data/spritesheets/$wearableFile.png" else null,
                        isWearable = wearable,
                    )
                }
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Translated("Cancel") }
            DialogButton(onClick = { handleSubmit() }, style = DialogButtonStyle.Primary) {
                if (isSubmitting) Translated("Saving...") else Translated("Save")
            }
        }
    )

    if (showAssetBrowser) {
        AssetBrowserDialog(
            type = AssetType.Items,
            onSelect = { r ->
                file = r.asset!!.file
                assetIndex = (r.index ?: 0).toString()
                showAssetBrowser = false
            },
            onClose = { showAssetBrowser = false },
        )
    }

    if (showWearableBrowser) {
        AssetBrowserDialog(
            type = AssetType.SpriteSheet,
            onSelect = { r ->
                wearableFile = r.asset!!.file
                showWearableBrowser = false
            },
            onClose = { showWearableBrowser = false },
        )
    }
}
